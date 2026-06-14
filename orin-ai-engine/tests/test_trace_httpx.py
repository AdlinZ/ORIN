"""
Outbound httpx `traceparent` 注入测试：

- hook 在 contextvar 已绑定时复用 trace_id + 新 span-id
- hook 在 contextvar 未绑定时兜底生成新 trace_id
- 工厂默认挂上 request hook
- 工厂在 `OUTBOUND_TRACEPARENT_DISABLED=True` 时跳过
- 端到端：经 `httpx.MockTransport` 实际送出的请求 header 含 W3C traceparent
"""
from __future__ import annotations

import pytest
import httpx

from app.core import trace_context
from app.core.config import settings
from app.core.trace_httpx import _inject_traceparent_hook, httpx_client
from app.core.w3c_trace import TRACEPARENT_HEADER, TRACEPARENT_REGEX, generate_trace_id


@pytest.fixture(autouse=True)
def reset_context() -> None:
    trace_context.clear()
    yield
    trace_context.clear()


# ---- hook 单测 ----


@pytest.mark.asyncio
async def test_hook_sets_traceparent_when_context_bound() -> None:
    trace_id = generate_trace_id()
    bound_span = "0123456789abcdef"
    trace_context.bind_raw(trace_id, bound_span)

    req = httpx.Request("POST", "http://test.local/x")
    await _inject_traceparent_hook(req)

    header = req.headers[TRACEPARENT_HEADER]
    assert TRACEPARENT_REGEX.match(header)
    parts = header.split("-")
    # 复用同 trace_id
    assert parts[1] == trace_id
    # span-id 每次新生成 —— 不应与 bound span 相同
    assert parts[2] != bound_span


@pytest.mark.asyncio
async def test_hook_when_context_unbound_generates_new() -> None:
    trace_context.clear()
    req = httpx.Request("GET", "http://test.local/x")
    await _inject_traceparent_hook(req)

    header = req.headers[TRACEPARENT_HEADER]
    assert TRACEPARENT_REGEX.match(header)
    parts = header.split("-")
    # 32 hex 兜底生成
    assert len(parts[1]) == 32
    assert all(c in "0123456789abcdef" for c in parts[1])


# ---- 工厂单测（monkeypatch 拦截 AsyncClient 构造）----


def test_factory_attaches_event_hook_by_default(monkeypatch) -> None:
    monkeypatch.setattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", False)
    captured: dict = {}

    original_init = httpx.AsyncClient.__init__

    def spy(self, **kw):
        captured.update(kw)
        original_init(self, **kw)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", spy)
    httpx_client(timeout=5.0)

    assert "event_hooks" in captured
    assert "request" in captured["event_hooks"]
    assert _inject_traceparent_hook in captured["event_hooks"]["request"]
    # caller 的 timeout 必须透传
    assert captured.get("timeout") == 5.0


def test_factory_skips_hook_when_disabled(monkeypatch) -> None:
    monkeypatch.setattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", True)
    captured: dict = {}

    original_init = httpx.AsyncClient.__init__

    def spy(self, **kw):
        captured.update(kw)
        original_init(self, **kw)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", spy)
    httpx_client(timeout=5.0)

    # 关闭时直接走裸 AsyncClient，不传 event_hooks
    assert "event_hooks" not in captured


def test_factory_preserves_caller_event_hooks(monkeypatch) -> None:
    """caller 已有 event_hooks['request'] 时，新 hook 追加到末尾，不覆盖。"""
    monkeypatch.setattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", False)
    captured: dict = {}

    def caller_hook(req: httpx.Request) -> None:  # pragma: no cover - 标识
        pass

    original_init = httpx.AsyncClient.__init__

    def spy(self, **kw):
        captured.update(kw)
        original_init(self, **kw)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", spy)
    httpx_client(timeout=5.0, event_hooks={"request": [caller_hook]})

    hooks = captured["event_hooks"]["request"]
    # caller_hook 在前，新注入 hook 追加在末尾
    assert hooks[0] is caller_hook
    assert _inject_traceparent_hook in hooks


# ---- 端到端：用 MockTransport 抓出站请求的 byte 实际 header ----


@pytest.mark.asyncio
async def test_integration_traceparent_sent_through_real_request(monkeypatch) -> None:
    monkeypatch.setattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", False)
    trace_context.clear()

    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["traceparent"] = request.headers.get(TRACEPARENT_HEADER)
        return httpx.Response(200, json={"ok": True})

    transport = httpx.MockTransport(handler)

    # 用 factory 创建 client，**构造时**直接传 transport，避免 aenter 后改
    # `_transport` 时内部连接池已经建好的时序问题
    async with httpx_client(timeout=5.0, transport=transport) as client:
        r = await client.post("http://test.local/x", json={"k": "v"})

    assert r.status_code == 200
    assert seen["traceparent"]
    assert TRACEPARENT_REGEX.match(seen["traceparent"])


@pytest.mark.asyncio
async def test_integration_preserves_trace_id_across_outbound_hop(monkeypatch) -> None:
    """端到端：绑定的 trace_id 应在出站请求上保留（span-id 换新）。"""
    monkeypatch.setattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", False)
    bound_trace = "0" * 32
    bound_span = "1" * 16
    trace_context.bind_raw(bound_trace, bound_span)

    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["traceparent"] = request.headers.get(TRACEPARENT_HEADER)
        return httpx.Response(200, json={"ok": True})

    transport = httpx.MockTransport(handler)

    async with httpx_client(timeout=5.0, transport=transport) as client:
        await client.get("http://test.local/x")

    parts = seen["traceparent"].split("-")
    assert parts[1] == bound_trace
    assert parts[2] != bound_span
