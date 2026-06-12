"""
logging filter 测试：bind 后 LogRecord 自带 traceId/spanId，未 bind 时空串。
"""
from __future__ import annotations

import logging

import pytest

from app.core import trace_context
from app.core.logging_filter import TraceContextFilter


@pytest.fixture(autouse=True)
def reset_context() -> None:
    trace_context.clear()
    yield
    trace_context.clear()


def test_filter_whenUnbound_setsEmptyStrings(caplog) -> None:
    logger = logging.getLogger("test.empty")
    # 确保没残留 filter
    logger.addFilter(TraceContextFilter())
    try:
        with caplog.at_level(logging.INFO, logger="test.empty"):
            logger.info("hello")
        rec = caplog.records[0]
        assert rec.traceId == ""
        assert rec.spanId == ""
    finally:
        logger.removeFilter(TraceContextFilter())


def test_filter_whenBound_setsTraceIdAndSpanId(caplog) -> None:
    logger = logging.getLogger("test.bound")
    trace_context.bind_raw("a" * 32, "b" * 16)
    logger.addFilter(TraceContextFilter())
    try:
        with caplog.at_level(logging.INFO, logger="test.bound"):
            logger.info("hello")
        rec = caplog.records[0]
        assert rec.traceId == "a" * 32
        assert rec.spanId == "b" * 16
    finally:
        logger.removeFilter(TraceContextFilter())


def test_filter_doesNotSwallowLogRecords(caplog) -> None:
    logger = logging.getLogger("test.pass")
    logger.addFilter(TraceContextFilter())
    try:
        with caplog.at_level(logging.INFO, logger="test.pass"):
            logger.warning("warn-line")
        assert any(r.levelname == "WARNING" and r.getMessage() == "warn-line"
                   for r in caplog.records)
    finally:
        logger.removeFilter(TraceContextFilter())
