"""
W3C traceparent 编解码工具单测，镜像后端 `TraceContextTest.java` 的覆盖。
"""
from __future__ import annotations

import re

import pytest

from app.core import w3c_trace
from app.core.w3c_trace import (
    Traceparent,
    build_from_current,
    build_traceparent,
    generate_span_id,
    generate_trace_id,
    is_valid,
    parse_traceparent,
)


# ---- build ----


def test_build_givenTraceIdAndSpanId_producesValidTraceparent() -> None:
    tp = build_traceparent("0" * 32, "1" * 16)
    assert tp == "00-" + "0" * 32 + "-" + "1" * 16 + "-01"
    assert is_valid(tp)


# ---- parse ----


def test_parse_validHeader_returnsTraceparent() -> None:
    header = "00-" + "a" * 32 + "-" + "b" * 16 + "-01"
    result = parse_traceparent(header)
    assert result == Traceparent(trace_id="a" * 32, span_id="b" * 16, flags="01")


def test_parse_invalidHeader_returnsNone() -> None:
    assert parse_traceparent(None) is None
    assert parse_traceparent("") is None
    assert parse_traceparent("not-a-traceparent") is None
    # 非 hex
    assert parse_traceparent("00-" + "z" * 32 + "-" + "b" * 16 + "-01") is None
    # 缺 flags
    assert parse_traceparent("00-" + "a" * 32 + "-" + "b" * 16) is None
    # 非 00 版本
    assert parse_traceparent("01-" + "a" * 32 + "-" + "b" * 16 + "-01") is None
    # traceId 长度错
    assert parse_traceparent("00-" + "a" * 31 + "-" + "b" * 16 + "-01") is None
    assert parse_traceparent("00-" + "a" * 33 + "-" + "b" * 16 + "-01") is None


def test_parse_uppercase_rejected() -> None:
    # W3C spec 仅小写 hex；本实现拒绝大写
    assert parse_traceparent("00-" + "A" * 32 + "-" + "B" * 16 + "-01") is None


# ---- is_valid ----


@pytest.mark.parametrize(
    "header,expected",
    [
        ("00-" + "a" * 32 + "-" + "b" * 16 + "-01", True),
        ("00-" + "0" * 32 + "-" + "0" * 16 + "-00", True),
        ("", False),
        (None, False),
        ("00-" + "z" * 32 + "-" + "b" * 16 + "-01", False),
        ("00-" + "a" * 32 + "-" + "b" * 16, False),
        ("01-" + "a" * 32 + "-" + "b" * 16 + "-01", False),
    ],
)
def test_is_valid_tableDriven(header, expected) -> None:
    assert is_valid(header) is expected


# ---- ID 生成 ----


def test_generateSpanId_is16LowerHex() -> None:
    s1 = generate_span_id()
    s2 = generate_span_id()
    assert len(s1) == 16
    assert re.match(r"^[0-9a-f]{16}$", s1)
    assert s1 != s2


def test_generateTraceId_is32LowerHex() -> None:
    seen = set()
    for _ in range(100):
        seen.add(generate_trace_id())
    assert len(seen) == 100, "100 次生成应全部唯一"
    for t in seen:
        assert len(t) == 32
        assert re.match(r"^[0-9a-f]{32}$", t)


# ---- build_from_current ----


def test_build_from_current_whenContextUnset_generatesFresh() -> None:
    # 确保未绑定（其他测试可能污染了 contextvar）
    from app.core import trace_context

    trace_context.clear()
    tp = build_from_current()
    assert is_valid(tp)
    # 兜底生成的 trace_id 是 32 hex
    parsed = parse_traceparent(tp)
    assert parsed is not None
    assert re.match(r"^[0-9a-f]{32}$", parsed.trace_id)


def test_build_from_current_whenContextSet_preservesTraceIdNewSpan() -> None:
    from app.core import trace_context

    trace_context.clear()
    trace_context.bind_raw("c" * 32, "d" * 16)

    tp1 = build_from_current()
    tp2 = build_from_current()

    assert parse_traceparent(tp1).trace_id == "c" * 32
    assert parse_traceparent(tp2).trace_id == "c" * 32
    # span-id 每次新生成
    assert parse_traceparent(tp1).span_id != parse_traceparent(tp2).span_id


def test_build_roundtrip() -> None:
    original = Traceparent(trace_id="e" * 32, span_id="f" * 16, flags="01")
    header = build_traceparent(original.trace_id, original.span_id, original.flags)
    assert parse_traceparent(header) == original


def test_w3c_trace_module_constants() -> None:
    # 防止有人不小心改了常量名导致与后端 / 日志字段脱节
    assert w3c_trace.TRACEPARENT_HEADER == "traceparent"
    assert w3c_trace.TRACE_ID_KEY == "traceId"
    assert w3c_trace.SPAN_ID_KEY == "spanId"
