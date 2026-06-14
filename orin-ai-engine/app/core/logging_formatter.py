"""
结构化 JSON 日志 formatter + 启动期配置入口。

与 `logging_filter.TraceContextFilter` 配套：filter 在 LogRecord 创建阶段注入
`record.traceId` / `record.spanId`，formatter 序列化时把这两个字段写进 JSON，
与后端 `logstash-logback-encoder` 的 `traceId` 字段同名，便于 ELK / Loki
统一查询。

设计要点：
- 纯 std-lib `json`，不引第三方依赖（python-json-logger / structlog 都拒）。
- 字段名对齐业界事实标准（logstash-logback-encoder / logfmt 风格）：
  `timestamp` ISO 8601 with Z / `level` / `logger` / `message` / `traceId` / `spanId`
  / `module` / `func` / `line`。
- `extra` 透传：`logger.info("...", extra={"k": "v"})` 的 `k` 会作为顶层字段
  进入 JSON（值先尝试 JSON 序列化，失败回落 `str()`）。
- `exc_info` / `stack_info` 走标准 `format` 产出再嵌入 `exception` / `stack` 字段。
- `configure_logging()` 幂等：重复调用只重建 root handler，不累加。
"""
from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any


# 哪些 LogRecord 内置属性需要排除（不要把 logger / levelno 这种 infra 字段当业务字段）
_RESERVED_LOGRECORD_ATTRS: frozenset[str] = frozenset({
    "args", "asctime", "created", "exc_info", "exc_text", "filename",
    "funcName", "levelname", "levelno", "lineno", "message", "module",
    "msecs", "msg", "name", "pathname", "process", "processName",
    "relativeCreated", "stack_info", "thread", "threadName",
    # 我们自己 filter 注入的也要排除，避免业务字段里重复
    "traceId", "spanId",
})


class JsonFormatter(logging.Formatter):
    """单行 JSON 输出的 `logging.Formatter`。"""

    def format(self, record: logging.LogRecord) -> str:
        # 标准 `format()` 把 exc_info / stack_info 解析到 record.exc_text / record.stack_text
        super().format(record)

        payload: dict[str, Any] = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc)
                .isoformat(timespec="milliseconds")
                .replace("+00:00", "Z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "traceId": getattr(record, "traceId", ""),
            "spanId": getattr(record, "spanId", ""),
            "module": record.module,
            "func": record.funcName,
            "line": record.lineno,
            "process": record.process,
            "thread": record.thread,
        }

        # 透传 `logger.info("...", extra={"k": "v"})` 注入的字段
        extras = {
            key: value
            for key, value in record.__dict__.items()
            if key not in _RESERVED_LOGRECORD_ATTRS
            and not key.startswith("_")
        }
        for key, value in extras.items():
            payload[key] = _safe_json_value(value)

        if record.exc_text:
            payload["exception"] = record.exc_text
        if record.stack_info:
            payload["stack"] = record.stack_info

        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def _safe_json_value(value: Any) -> Any:
    """业务字段值 JSON 序列化兜底：基本类型直传，其它回落 `str()`。"""
    if isinstance(value, (str, int, float, bool, type(None))):
        return value
    try:
        json.dumps(value)
        return value
    except (TypeError, ValueError):
        return str(value)


def configure_logging(json_format: bool, level: str = "INFO") -> None:
    """在 FastAPI 启动期调用一次：把 root logger 的 handler 替换为 JSON / 文本，
    并把 `TraceContextFilter` 挂到 handler 上（**不是** logger 上 —— 子 logger
    propagate 时 Logger.callHandlers 不调 parent filter，handler 才会跑）。

    幂等：先清空 root logger 已有 handler，再装新 handler。重复调用不累加。
    `json_format=False` 走 std-lib 默认 `Formatter("%(asctime)s %(levelname)s ...")`，
    保留与历史 dev 输出兼容。
    """
    # 导入放函数内避免 logging_filter ↔ logging_formatter 循环依赖
    from app.core.logging_filter import TraceContextFilter

    root = logging.getLogger()
    # 清掉已有 handler（uvicorn / pytest / 历史配置可能塞过）
    for handler in list(root.handlers):
        root.removeHandler(handler)

    handler = logging.StreamHandler(stream=sys.stdout)
    if json_format:
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(
            logging.Formatter(
                fmt="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
                datefmt="%Y-%m-%dT%H:%M:%S%z",
            )
        )
    # 关键：filter 挂 handler 不挂 logger —— Python `Logger.callHandlers` 在
    # propagate 链上只调 handler.filter，不调 logger.filter（除非该 logger 自己
    # 调 .handle()）。见 https://docs.python.org/3/library/logging.html
    handler.addFilter(TraceContextFilter())
    root.addHandler(handler)
    root.setLevel(level.upper() if isinstance(level, str) else level)
