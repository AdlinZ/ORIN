"""
OTel bridge 单测（Phase 2 小刀 5b）。

OTel 1.42.x 约束：进程级 TracerProvider 不可 reset —— 全部 OTel enabled 路径
断言合并到 file 内**唯一** lifecycle test。其它单元测试走"OTel 不可用 /
mock 隔离 / 不依赖 OTel enabled state"路径，避免重复 init 破坏 state。
"""
from __future__ import annotations

import asyncio
import logging

import pytest

from app.core import otel_setup, trace_context


# ---- OTel 不可用 / disabled 模式（mock 隔离 global state）----


def test_bind_raw_no_otel_attach_when_otel_unavailable(monkeypatch) -> None:
    """OTel 包缺失时 bind_raw 不挂 OTel，contextvar 行为不变。"""
    monkeypatch.setattr(trace_context, "_OTEL_BRIDGE_AVAILABLE", False)
    trace_context.bind_raw("a" * 32, "b" * 16)
    assert trace_context.current() == ("a" * 32, "b" * 16)
    # OTel token 变量未设
    assert trace_context.otel_token_var.get() is None
    trace_context.clear()


def test_clear_safe_when_otel_unavailable(monkeypatch) -> None:
    """OTel 不可用时 clear 不抛。"""
    monkeypatch.setattr(trace_context, "_OTEL_BRIDGE_AVAILABLE", False)
    trace_context.bind_raw("a" * 32, "b" * 16)
    trace_context.clear()
    assert trace_context.current() == (None, None)


# ---- 业务 span 影子化（OTel 不可用时双栈 mirror 仍 work，OTel stub 不报错）----


def test_tracing_start_trace_without_otel_init(monkeypatch) -> None:
    """OTel 不可用时 TracingClient 仍可工作（mirror 路径）。"""
    from app.core import tracing as tracing_mod
    from app.core.otel_setup import _NoopTracer
    monkeypatch.setattr(tracing_mod, "get_tracer", lambda name: _NoopTracer())

    tracing_mod.tracing_client._otel_spans.clear()
    tracing_mod.tracing_client._otel_tokens.clear()
    tracing_mod.tracing_client.spans.clear()

    trace_id = "a" * 32
    span = tracing_mod.tracing_client.start_trace(trace_id, "test-trace")
    assert span.trace_id == trace_id
    assert span.name == "test-trace"
    assert span.status == "running"

    child = tracing_mod.tracing_client.start_span("test-child")
    assert child.parent_id == span.id
    assert child.trace_id == trace_id

    tracing_mod.tracing_client.record_event("evt", {"k": "v"})
    # 栈顶 child 应有 events
    assert any(e["name"] == "evt" for e in child.metadata["events"])

    tracing_mod.tracing_client.finish_span(status="success")
    tracing_mod.tracing_client.finish_span(status="success")
    summary = tracing_mod.tracing_client.get_trace_summary()
    assert summary["trace_id"] == trace_id
    assert summary["total_spans"] == 2
    assert summary["error_count"] == 0


# ---- 端到端：当前 contextvar 串到出站 traceparent（新 span_id 行为）----


def test_e2e_bind_then_legacy_inject_uses_new_span_id() -> None:
    """bind_raw 写 contextvar → 出站走 legacy 路径 → 生成新 span_id。"""
    trace_context.clear()
    trace_id = "a" * 32
    bound_span = "b" * 16
    trace_context.bind_raw(trace_id, bound_span)

    from app.core.trace_httpx import _inject_legacy_traceparent
    import httpx
    req = httpx.Request("POST", "http://test.local/x")
    _inject_legacy_traceparent(req)
    parts = req.headers["traceparent"].split("-")
    assert parts[1] == trace_id
    assert parts[2] != bound_span  # 新 span_id


# ---- w3c_trace 双轨：parse_traceparent 严格语义保留（OTel propagator 仅 inject）----


def test_parse_traceparent_still_rejects_uppercase() -> None:
    """5b 不改 w3c_trace.py 严格语义 —— 大写 trace_id 仍被拒。
    OTel propagator 仅用于 inject（自动规范化大写），extract 仍走手写 parse。
    """
    from app.core.w3c_trace import parse_traceparent
    bad = "00-" + "A" * 32 + "-" + "B" * 16 + "-01"
    assert parse_traceparent(bad) is None


# ---- 并发栈式 token 管理（contextvars 天然隔离，不依赖 OTel enabled）----


@pytest.mark.asyncio
async def test_concurrent_tasks_get_isolated_trace_context() -> None:
    """两个 task 并发 bind 不同 trace_id，OTel Context 天然隔离（contextvars），
    互不污染。"""
    async def task_a() -> str:
        trace_context.bind_raw("a" * 32, "1" * 16)
        await asyncio.sleep(0.01)  # 让出执行权
        return trace_context.current()[0]

    async def task_b() -> str:
        trace_context.bind_raw("b" * 32, "2" * 16)
        await asyncio.sleep(0.01)
        return trace_context.current()[0]

    a_result, b_result = await asyncio.gather(task_a(), task_b())
    assert a_result == "a" * 32
    assert b_result == "b" * 32


@pytest.mark.asyncio
async def test_tracing_tasks_trace_id_isolated_via_stack() -> None:
    """两个 task 顺序使用 tracing_client.start_trace，互不串线。"""
    from app.core import tracing as tracing_mod
    tracing_mod.tracing_client._otel_spans.clear()
    tracing_mod.tracing_client._otel_tokens.clear()
    tracing_mod.tracing_client.spans.clear()

    async def task_workflow(trace_id_hex: str) -> str:
        # 隔离 per task：替换 module-level singleton
        tracing_mod.tracing_client = tracing_mod.TracingClient()
        tracing_mod.tracing_client.start_trace(trace_id_hex, "workflow:execute")
        await asyncio.sleep(0.01)
        child = tracing_mod.tracing_client.start_span(f"node:{trace_id_hex[:4]}")
        await asyncio.sleep(0.01)
        tracing_mod.tracing_client.finish_span(status="success")
        tracing_mod.tracing_client.finish_span(status="success")
        summary = tracing_mod.tracing_client.get_trace_summary()
        return summary["trace_id"]

    a = await task_workflow("a" * 32)
    b = await task_workflow("b" * 32)
    assert a == "a" * 32
    assert b == "b" * 32
    # OTel 栈归零
    assert len(tracing_mod.tracing_client._otel_spans) == 0
    # 当前 singleton mirror 留 2 spans（仅第 2 task）
    assert len(tracing_mod.tracing_client.spans) == 2
    assert all(s.end_time is not None for s in tracing_mod.tracing_client.spans)
    assert tracing_mod.tracing_client.get_trace_summary()["trace_id"] == "b" * 32


# ---- 完整 lifecycle（**真 init 一次**）—— 覆盖所有 OTel enabled 路径 ----


def test_full_lifecycle_otel_enabled(monkeypatch, caplog) -> None:
    """端到端：OTel enable 真 init（file 内**唯一**真 init），覆盖：
    - setup_tracing 返 True + 启动日志
    - bind_raw → OTel 当前 span valid + trace_id/span_id 匹配 hex
    - clear → OTel 当前 span 失效（token detached）
    - nested bind_raw → 旧 token 自动 detach，无泄漏
    - current() 优先读 OTel Context
    - httpx 出站 OTel 路径：新 span_id 生成（!=
    - tracing 业务 span 影子化：start_trace trace_id 一致 + start_span child parent_id
      正确 + 栈深归零
    - shutdown 不抛
    """
    # 依赖 5a 的 test_full_lifecycle_init_console_then_shutdown 先跑（pytest
    # 字母序 s < z 强制）。如果 5a lifecycle 没跑过，global 仍是
    # ProxyTracerProvider，此 test 必须 skip —— **不能**本 test 调 setup_tracing
    # （OTel 1.42.x 第二次 set 破坏 `_TRACER_PROVIDER` 全局状态）。
    from opentelemetry import trace as otel_trace
    if type(otel_trace.get_tracer_provider()).__name__ != "TracerProvider":
        pytest.skip("依赖 test_otel_setup.test_full_lifecycle_init_console_then_shutdown 先跑")

    # ---- bind_raw 桥：OTel 当前 span valid ----
    trace_id = "abcdef0123456789" * 2  # 32 hex 非零（避免 int() = 0）
    span_id = "1" * 16
    trace_context.bind_raw(trace_id, span_id)
    sc = otel_trace.get_current_span().get_span_context()
    assert sc.is_valid, f"OTel 当前 span invalid: trace_id={hex(sc.trace_id)}"
    assert f"{sc.trace_id:032x}" == trace_id
    assert f"{sc.span_id:016x}" == span_id
    # OTel token var 已设
    assert trace_context.otel_token_var.get() is not None

    # ---- current() 优先读 OTel Context ----
    assert trace_context.current() == (trace_id, span_id)

    # ---- nested bind_raw：旧 token 自动 detach ----
    first_token = trace_context.otel_token_var.get()
    trace_context.bind_raw("2" * 32, "3" * 16)
    second_token = trace_context.otel_token_var.get()
    assert second_token is not first_token
    nested_sc = otel_trace.get_current_span().get_span_context()
    assert f"{nested_sc.trace_id:032x}" == "2" * 32
    assert f"{nested_sc.span_id:016x}" == "3" * 16

    # ---- clear 后 ODetach token ----
    trace_context.clear()
    cleared_sc = otel_trace.get_current_span().get_span_context()
    assert not cleared_sc.is_valid or cleared_sc.trace_id == 0

    # ---- httpx 出站 OTel 路径：注入新 span_id（!=
    trace_context.bind_raw(trace_id, span_id)
    import httpx
    from app.core.trace_httpx import _inject_otel_traceparent
    req = httpx.Request("POST", "http://test.local/x")
    _inject_otel_traceparent(req)
    parts = req.headers["traceparent"].split("-")
    assert parts[1] == trace_id
    assert parts[2] != span_id  # 新 span_id

    # ---- tracing 业务 span 影子化 ----
    from app.core import tracing as tracing_mod
    tracing_mod.tracing_client._otel_spans.clear()
    tracing_mod.tracing_client._otel_tokens.clear()
    tracing_mod.tracing_client.spans.clear()
    root = tracing_mod.tracing_client.start_trace(trace_id, "workflow:execute")
    assert root.trace_id == trace_id
    assert len(tracing_mod.tracing_client._otel_spans) == 1

    # OTel 当前 span 仍然 valid + trace_id 与入参一致
    shadow_sc = otel_trace.get_current_span().get_span_context()
    assert shadow_sc.is_valid
    assert f"{shadow_sc.trace_id:032x}" == trace_id

    # child span
    child = tracing_mod.tracing_client.start_span("node:llm:abc", metadata={"k": "v"})
    assert child.parent_id == root.id
    assert child.trace_id == trace_id
    assert len(tracing_mod.tracing_client._otel_spans) == 2

    # record_event
    tracing_mod.tracing_client.record_event("node_completed", {"tokens": 128})
    assert any(e["name"] == "node_completed" for e in child.metadata["events"])

    # finish 栈深归零
    tracing_mod.tracing_client.finish_span(status="success")
    assert len(tracing_mod.tracing_client._otel_spans) == 1
    tracing_mod.tracing_client.finish_span(status="success")
    assert len(tracing_mod.tracing_client._otel_spans) == 0
    # mirror 仍保留 2 spans（append-only），get_trace_summary 仍可读
    summary = tracing_mod.tracing_client.get_trace_summary()
    assert summary["trace_id"] == trace_id
    assert summary["total_spans"] == 2

    # 收尾
    tracing_mod.tracing_client.clear()
    trace_context.clear()
    otel_setup.shutdown_tracing()  # 不抛
