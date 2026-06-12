"""
std-lib `logging.Filter`：从 contextvar 注入 `LogRecord.traceId` / `LogRecord.spanId`。

为何用 Filter 而非自定义 Formatter：
- Filter 在 `LogRecord` 创建阶段注入字段，所有现有/未来 formatter
  （含 std-lib `Formatter`、structlog、JSON encoder）都能直接读
  `record.traceId` / `record.spanId`。
- 不侵入 `logger.info(...)` 调用点（Java MDC 同理，调用方无需改）。
- 与后端 `logback-spring.xml` 的 `<includeMdcKeyName>traceId</includeMdcKeyName>`
  行为一致：未绑定时为空串，避免下游 JSON 输出 null/undefined。

挂载点：在 `app/main.py` 的 `logging.getLogger().addFilter(...)` 一次注册，
子 logger 自动继承。
"""
from __future__ import annotations

import logging
from typing import Optional

from app.core.trace_context import current


class TraceContextFilter(logging.Filter):
    """把当前 contextvar 的 traceId/spanId 写入每条 LogRecord。"""

    def filter(self, record: logging.LogRecord) -> bool:
        tid: Optional[str]
        sid: Optional[str]
        tid, sid = current()
        # 关键：未绑定时空串而非 None，让下游 formatter 永远拿到字符串。
        record.traceId = tid or ""
        record.spanId = sid or ""
        return True
