"""
Outbound httpx factory: every request from AI Engine to the backend (or any
other service) auto-carries a W3C `traceparent` header。

Phase 2 小刀 5b 改造：OTel `propagators.inject` 作为权威出站注入，保留
`build_from_current` 兜底（OTel 不可用 / 未 init / 当前 span 失效时）：

- OTel 已 init + 当前 span valid → 在 hook 内部起一个**新** CLIENT span
  （继承父 trace_id + 生成新 span_id），propagator.inject 注入这个新 CLIENT
  span —— 与 W3C 跨进程惯例一致（出站跳是新 span，trace_id 延续）
- OTel 不可用 / disabled / 当前 span invalid → 退回 `build_from_current()`
  （读 contextvar，保留旧行为）
- `Settings.OUTBOUND_TRACEPARENT_DISABLED=True` → 完全跳过注入（dev /
  旧测试）

设计要点：
- `httpx.event_hooks={"request": [...]}` 是 httpx 官方跨切面扩展点
  （auth / retry / tracing 都用它）。一行 factory，零 per-call wrapper 噪音。
- 工厂内部仍调 `httpx.AsyncClient`，所以 test 通过
  `patch("app.engine.<module>.httpx.AsyncClient", ...)` 拦截类时
  factory 链路不断。
"""
from __future__ import annotations

from typing import Any, Dict, List

import httpx

from app.core.config import settings
from app.core.otel_setup import get_tracer
from app.core.w3c_trace import TRACEPARENT_HEADER, build_from_current

# OTel 包缺失时降级 no-op
try:
    from opentelemetry import context as _otel_context
    from opentelemetry import trace as _otel_trace
    from opentelemetry.propagate import inject as _otel_inject
    from opentelemetry.trace import SpanKind as _SpanKind
    from opentelemetry.trace import set_span_in_context as _set_span_in_context
    _OTEL_PROPAGATOR_AVAILABLE = True
except ImportError:  # pragma: no cover - 兜底
    _OTEL_PROPAGATOR_AVAILABLE = False
    _otel_context = None  # type: ignore[assignment]
    _otel_trace = None  # type: ignore[assignment]
    _otel_inject = None  # type: ignore[assignment]
    _SpanKind = None  # type: ignore[assignment]
    _set_span_in_context = None  # type: ignore[assignment]


# 单例 OTel Tracer（app.core.httpx 命名空间）
_tracer = get_tracer("app.core.httpx")


def _inject_otel_traceparent(request: httpx.Request) -> None:
    """OTel propagator 注入：起一个新 CLIENT span，propagator.inject 注入。

    关键行为：propagator 注入的是**这个新 span**（其 span_id 是新生成的），
    与 W3C 跨进程惯例一致 —— 出站跳是新 span，trace_id 延续。

    OTel 1.42.1 SDK 行为陷阱：NonRecordingSpan 作为 parent 时
    `tracer.start_span` 会**复用 parent span_id**（OTel 把 NonRecordingSpan
    当 transit 转发，不期望它产生 child）。要拿到新 span_id 必须手动构造
    `SpanContext(span_id=generate_span_id())` + `NonRecordingSpan` + attach，
    propagator.inject 再从这个新 span 抽。
    """
    if not _OTEL_PROPAGATOR_AVAILABLE:
        _inject_legacy_traceparent(request)
        return

    try:
        # 1) 读当前父 span（middleware / handler / task_runtime 灌进去的）
        from opentelemetry.trace import (
            NonRecordingSpan,
            SpanContext,
            TraceFlags,
        )
        from app.core.w3c_trace import generate_span_id
        parent = _otel_trace.get_current_span()
        parent_sc = parent.get_span_context()
        if parent_sc is None or not parent_sc.is_valid:
            _inject_legacy_traceparent(request)
            return

        # 2) 手工构造新的 SpanContext：trace_id 继承父，span_id 全新生成
        new_sc = SpanContext(
            trace_id=parent_sc.trace_id,
            span_id=int(generate_span_id(), 16),  # 16 字节 hex → int
            is_remote=False,
            trace_flags=parent_sc.trace_flags or TraceFlags(0x01),
        )
        new_span = NonRecordingSpan(new_sc)

        # 3) attach 到当前 OTel Context，让 propagator.inject 能拿到
        token = _otel_context.attach(_set_span_in_context(new_span))
        try:
            # 4) propagator.inject 写 traceparent + tracestate + baggage
            carrier: Dict[str, str] = {}
            _otel_inject(carrier)
            for key, value in carrier.items():
                request.headers[key] = value
        finally:
            # 5) 立即 detach —— 这个 span 是"出站跳"，不参与调用栈
            _otel_context.detach(token)
    except Exception:  # pragma: no cover - OTel 状态异常走 legacy
        _inject_legacy_traceparent(request)


def _inject_legacy_traceparent(request: httpx.Request) -> None:
    """Legacy 兜底（pre-OTel 行为）：从 contextvar 抽 trace_id 生成新 span-id
    注入 header。当 OTel 不可用 / 当前 span 失效时使用，行为与 5a 之前一致。
    """
    request.headers[TRACEPARENT_HEADER] = build_from_current()


async def _inject_traceparent_hook(request: httpx.Request) -> None:
    """Pre-send hook: OTel 优先，legacy 兜底。

    httpx 的 `event_hooks["request"]` 在 `AsyncClient._send_handling_redirects`
    里通过 `await hook(request)` 调用 —— 必须是 coroutine function（5a 修过）。
    """
    if not _OTEL_PROPAGATOR_AVAILABLE:
        _inject_legacy_traceparent(request)
        return

    # 检查 OTel 当前 span 是否 valid
    try:
        current_span = _otel_trace.get_current_span()
        sc = current_span.get_span_context()
        if sc is None or not sc.is_valid:
            _inject_legacy_traceparent(request)
            return
    except Exception:  # pragma: no cover
        _inject_legacy_traceparent(request)
        return

    _inject_otel_traceparent(request)


def httpx_client(**kwargs: Any) -> httpx.AsyncClient:
    """`httpx.AsyncClient` 的薄包装，预装 traceparent request hook。

    透传所有构造参数（`timeout` / `follow_redirects` / `headers` / `base_url` /
    `event_hooks` 等）。若调用方已传入 `event_hooks["request"]`，新 hook 追加
    到列表末尾，不覆盖原 hook。

    关闭注入：`Settings.OUTBOUND_TRACEPARENT_DISABLED=True`，直接返回裸
    `httpx.AsyncClient(**kwargs)`，不做任何注入。
    """
    if getattr(settings, "OUTBOUND_TRACEPARENT_DISABLED", False):
        return httpx.AsyncClient(**kwargs)

    # 透传 caller 自己的 event_hooks['request']，新 hook 追加在末尾
    existing_hooks: List[Any] = list(kwargs.pop("event_hooks", {}).get("request", []))
    existing_hooks.append(_inject_traceparent_hook)
    return httpx.AsyncClient(event_hooks={"request": existing_hooks}, **kwargs)
