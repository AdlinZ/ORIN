"""
结构化 JSON 日志 formatter 单测：

- JsonFormatter 基本字段（含 traceId/spanId 来自 TraceContextFilter）
- 未绑定 trace 时 traceId/spanId 为空串而非 null
- `extra={...}` 业务字段透传到顶层
- `exc_info` 解析进 `exception` 字段
- 中文 / 特殊字符按 `ensure_ascii=False` 直出
- configure_logging 幂等（重复调用 handler 不累加）
- configure_logging json_format=False 走文本 formatter
"""
from __future__ import annotations

import io
import json
import logging
import re

import pytest

from app.core import trace_context
from app.core.logging_filter import TraceContextFilter
from app.core.logging_formatter import (
    JsonFormatter,
    _safe_json_value,
    configure_logging,
)


@pytest.fixture(autouse=True)
def reset_context() -> None:
    trace_context.clear()
    yield
    trace_context.clear()


def _make_record(
    name: str = "test.logger",
    level: int = logging.INFO,
    msg: str = "hello",
    extra: dict | None = None,
    exc_info=None,
) -> logging.LogRecord:
    """构造一条 LogRecord，模拟业务调用 `logger.info(msg, extra=...)`。"""
    # Python `Logger.makeRecord(name, level, fn, lno, msg, args, exc_info, func, extra)`:
    # 位置参数第 3 个 `fn` 实际是 `pathname` 字段；函数名走关键字 `func=`。
    record = logging.getLogger(name).makeRecord(
        name=name,
        level=level,
        fn="app/test.py",  # pathname
        lno=42,
        msg=msg,
        args=(),
        exc_info=exc_info,
        func="do_thing",  # function name
        extra=extra,
    )
    # 模拟 main.py 的全局 filter 注入 traceId/spanId
    TraceContextFilter().filter(record)
    return record


# ---- JsonFormatter ----


def test_json_formatter_basic_fields() -> None:
    trace_context.bind_raw("0" * 32, "1" * 16)
    record = _make_record(msg="hello world", extra={"agentId": "a-1"})

    line = JsonFormatter().format(record)
    payload = json.loads(line)

    assert payload["level"] == "INFO"
    assert payload["logger"] == "test.logger"
    assert payload["message"] == "hello world"
    assert payload["traceId"] == "0" * 32
    assert payload["spanId"] == "1" * 16
    assert payload["func"] == "do_thing"
    assert payload["line"] == 42
    assert payload["agentId"] == "a-1"
    # ISO 8601 with Z
    assert re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$", payload["timestamp"])


def test_json_formatter_unbound_trace_yields_empty_strings() -> None:
    """未绑定 trace 时 traceId/spanId 为空串（与后端 logback 一致），非 null。"""
    trace_context.clear()
    record = _make_record()

    payload = json.loads(JsonFormatter().format(record))

    assert payload["traceId"] == ""
    assert payload["spanId"] == ""


def test_json_formatter_extra_fields_promoted() -> None:
    """`logger.info(..., extra={"agentId": ..., "userId": ...})` 进顶层 JSON。"""
    record = _make_record(extra={"userId": "u-1", "agentId": "a-2", "tokens": 128})

    payload = json.loads(JsonFormatter().format(record))

    assert payload["userId"] == "u-1"
    assert payload["agentId"] == "a-2"
    assert payload["tokens"] == 128


def test_json_formatter_exc_info_goes_to_exception_field() -> None:
    try:
        raise ValueError("boom")
    except ValueError:
        import sys
        record = _make_record(
            msg="something failed",
            exc_info=sys.exc_info(),
        )

    payload = json.loads(JsonFormatter().format(record))

    assert payload["message"] == "something failed"
    assert "exception" in payload
    assert "ValueError: boom" in payload["exception"]


def test_json_formatter_unicode_preserved() -> None:
    """ensure_ascii=False：中文 / emoji 不被 \\uXXXX 转义。"""
    record = _make_record(msg="中文 + 🚀")

    line = JsonFormatter().format(record)

    assert "中文 + 🚀" in line
    payload = json.loads(line)
    assert payload["message"] == "中文 + 🚀"


def test_json_formatter_excludes_logrecord_infra_attrs() -> None:
    """LogRecord 内置 infra 字段（args / levelno / msecs / filename 等）不进业务字段。"""
    # 注意：`pathname` / `processName` / `message` / `asctime` 是 LogRecord 的
    # 保留字段，`makeRecord(extra=...)` 不允许覆盖（抛 KeyError），所以这里
    # 用普通业务字段名。formatter 内部通过 `_RESERVED_LOGRECORD_ATTRS`
    # 黑名单排除所有 LogRecord 内置字段，与 extra 字段名无关。
    record = _make_record(extra={"agentId": "a-x"})

    payload = json.loads(JsonFormatter().format(record))

    # LogRecord 默认的内置字段不应"自动"进入 payload
    assert "args" not in payload
    assert "levelno" not in payload
    assert "msecs" not in payload
    assert "filename" not in payload
    assert "pathname" not in payload
    assert "processName" not in payload
    # 业务字段仍在
    assert payload["agentId"] == "a-x"


# ---- _safe_json_value ----


def test_safe_json_value_primitives_passthrough() -> None:
    assert _safe_json_value("s") == "s"
    assert _safe_json_value(1) == 1
    assert _safe_json_value(1.5) == 1.5
    assert _safe_json_value(True) is True
    assert _safe_json_value(None) is None


def test_safe_json_value_falls_back_to_str() -> None:
    class NotJson:
        def __repr__(self) -> str:
            return "<NotJson>"

    assert _safe_json_value(NotJson()) == "<NotJson>"


# ---- configure_logging ----


def _root_handlers() -> list[logging.Handler]:
    return list(logging.getLogger().handlers)


def test_configure_logging_idempotent(monkeypatch) -> None:
    """重复调用 configure_logging 不会累加 handler。"""
    monkeypatch.setattr(logging.getLogger(), "handlers", [], raising=False)
    configure_logging(json_format=True, level="INFO")
    n1 = len(_root_handlers())
    configure_logging(json_format=True, level="INFO")
    n2 = len(_root_handlers())
    assert n1 == n2 == 1


def test_configure_logging_json_format_attaches_json_formatter(monkeypatch) -> None:
    """json_format=True 时 root handler 的 formatter 是 JsonFormatter。"""
    monkeypatch.setattr(logging.getLogger(), "handlers", [], raising=False)
    configure_logging(json_format=True, level="INFO")

    handler = _root_handlers()[0]
    assert isinstance(handler.formatter, JsonFormatter)
    assert logging.getLogger().level == logging.INFO
    # TraceContextFilter 应挂在 handler（不是 root logger）上 —— 见
    # `configure_logging` 注释，否则子 logger 冒泡时不会经过 filter。
    from app.core.logging_filter import TraceContextFilter
    assert any(isinstance(f, TraceContextFilter) for f in handler.filters)


def test_configure_logging_text_format_attaches_text_formatter(monkeypatch) -> None:
    """json_format=False 时走 std-lib Formatter（非 JsonFormatter）。"""
    monkeypatch.setattr(logging.getLogger(), "handlers", [], raising=False)
    configure_logging(json_format=False, level="DEBUG")

    handler = _root_handlers()[0]
    assert not isinstance(handler.formatter, JsonFormatter)
    assert logging.getLogger().level == logging.DEBUG


def test_configure_logging_end_to_end_emits_valid_json(monkeypatch) -> None:
    """端到端：configure_logging(json_format=True) → 子 logger.info 落 stdout
    一行合法 JSON，含 traceId 字段。

    关键覆盖：`e2e.info(...)` propagate 到 root，root.filter 不会跑 —— 但
    handler.filter 跑。TraceContextFilter 必须挂在 handler 上才能让子 logger
    发出的 record 拿到 traceId。
    """
    monkeypatch.setattr(logging.getLogger(), "handlers", [], raising=False)
    buf = io.StringIO()
    configure_logging(json_format=True, level="INFO")
    handler = _root_handlers()[0]
    handler.stream = buf

    trace_context.bind_raw("abc123", "def456")
    logging.getLogger("e2e").info("user signed in", extra={"userId": "u-1"})

    line = buf.getvalue().strip()
    payload = json.loads(line)
    assert payload["message"] == "user signed in"
    assert payload["traceId"] == "abc123"
    assert payload["spanId"] == "def456"
    assert payload["userId"] == "u-1"
