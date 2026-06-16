"""
contextvars 存储层 —— 把 W3C traceId / spanId 绑定到当前 asyncio task / 同步线程。

与 `app/core/w3c_trace.py` 解耦：
- `w3c_trace.py` 只做格式（parse / build / is_valid / generate）
- `trace_context.py` 只做存储（bind / clear / current）

Phase 2 小刀 5b 改造：contextvar 与 OTel Context 双向桥（OTel SDK 作为
权威跨进程 trace_id 存储，contextvar 仍保留 hex 字符串给 logging filter
和旧调用方用）：

- `bind_raw()` 时同时 `otel_context.attach(NonRecordingSpan(SpanContext(...)))`，
  后续 `trace.get_current_span()` / `propagator.inject()` 自然能拿到；
- `clear()` 时 `otel_context.detach(token)`；
- 嵌套调用（middleware → handler → outbound）通过 token 栈管理，detach
  顺序 LIFO；
- OTel 包缺失时所有 OTel 桥接代码走 no-op 分支，contextvar 行为不变。
- `current()` 优先读 OTel Context（用 OTel SpanContext 当 source of truth），
  回退 contextvar —— 让 OTel 路径**单向**主导，避免双源不一致。

调用方约定：使用 `bind_*` 后**必须**包 `try/finally clear()`，本模块不自动
清理（与后端 `CollaborationResultListener.bindMdcFromMessage` 行为一致）。
"""
from __future__ import annotations

import contextvars
from typing import Optional, Tuple

from app.core.w3c_trace import (
    Traceparent,
    generate_span_id,
    generate_trace_id,
    parse_traceparent,
)

# ---- contextvars ----

#: 命名遵循 logging 字段名约定（与后端 MDC key 同步）。
trace_id_var: contextvars.ContextVar[Optional[str]] = contextvars.ContextVar(
    "trace_id", default=None
)
span_id_var: contextvars.ContextVar[Optional[str]] = contextvars.ContextVar(
    "span_id", default=None
)

#: OTel `otel_context.attach` 返回的 token。存 token 让 `clear()` 能 detach。
#: 默认 None 表示"未 attach OTel"，clear 时跳过 detach 分支。
otel_token_var: contextvars.ContextVar[Optional[object]] = contextvars.ContextVar(
    "otel_token", default=None
)


# ---- OTel 桥接（顶层 try-import，缺失时降级 no-op）----

try:
    from opentelemetry import context as _otel_context
    from opentelemetry.trace import (
        NonRecordingSpan as _NonRecordingSpan,
        SpanContext as _SpanContext,
        TraceFlags as _TraceFlags,
        set_span_in_context as _set_span_in_context,
    )
    _OTEL_BRIDGE_AVAILABLE = True
except ImportError:  # pragma: no cover - 兜底分支
    _OTEL_BRIDGE_AVAILABLE = False
    _otel_context = None  # type: ignore[assignment]
    _NonRecordingSpan = None  # type: ignore[assignment]
    _SpanContext = None  # type: ignore[assignment]
    _TraceFlags = None  # type: ignore[assignment]
    _set_span_in_context = None  # type: ignore[assignment]


def _is_valid_hex(s: str, expected_len: int) -> bool:
    """校验 32/16 长度全 hex 字符串。空 / 长度错 / 非 hex 返 False。"""
    if not s or len(s) != expected_len:
        return False
    try:
        int(s, 16)
        return True
    except ValueError:
        return False


def _attach_otel(trace_id_hex: str, span_id_hex: str, *, remote: bool) -> None:
    """把 hex trace_id/span_id 包成 OTel NonRecordingSpan，attach 到当前 OTel
    Context。非法 hex / OTel 包缺失时走 no-op（让 contextvar 路径继续工作）。

    `remote=True` 标识入站头解析（OTel 标记 is_remote，propagator 知道这是
    上游传的）；`remote=False` 标识本进程生成（缺省值）。

    嵌套 attach 顺序（**关键**）：先 detach 旧 token，**再** attach 新 token。
    OTel context 是栈结构，先 attach 会让当前 context 指向新 span，但随后
    detach 旧 token 会让 context 回到更早的层，**新 span 立即失效**。
    """
    if not _OTEL_BRIDGE_AVAILABLE:
        return
    if not _is_valid_hex(trace_id_hex, 32) or not _is_valid_hex(span_id_hex, 16):
        return

    # 1) 先 detach 旧 token（如果有）
    prev_token = otel_token_var.get()
    if prev_token is not None:
        try:
            _otel_context.detach(prev_token)
        except Exception:  # pragma: no cover
            pass
        otel_token_var.set(None)

    # 2) 再 attach 新 token
    span_context = _SpanContext(
        trace_id=int(trace_id_hex, 16),
        span_id=int(span_id_hex, 16),
        is_remote=remote,
        trace_flags=_TraceFlags(0x01),  # SAMPLED —— 与 AI Engine 现状对齐
    )
    non_rec_span = _NonRecordingSpan(span_context)
    token = _otel_context.attach(_set_span_in_context(non_rec_span))
    otel_token_var.set(token)


def _detach_otel() -> None:
    """detach 之前 `_attach_otel` 挂的 token，幂等。"""
    if not _OTEL_BRIDGE_AVAILABLE:
        return
    token = otel_token_var.get()
    if token is None:
        return
    try:
        _otel_context.detach(token)
    except Exception:  # pragma: no cover
        # token 已被 detach（双重 clear）—— 静默吞
        pass
    otel_token_var.set(None)


# ---- 绑定 / 清理 / 读取 ----


def bind_raw(trace_id: str, span_id: str, *, remote: bool = False) -> None:
    """显式写入 trace_id / span_id + attach OTel NonRecordingSpan。

    同步双写：contextvar 给 logging filter / 旧调用方用，OTel Context 给
    OTel Tracer / propagator / span tree 用。
    """
    trace_id_var.set(trace_id)
    span_id_var.set(span_id)
    _attach_otel(trace_id, span_id, remote=remote)


def bind_from_traceparent(header_value: Optional[str]) -> Traceparent:
    """解析 header 并写入 contextvars + OTel Context。非法或 missing 时
    **生成新** trace_id/span_id 兜底。

    兜底逻辑与后端 `TraceIdFilter.parseTraceId` L91 / `TraceContext.buildFromMdc()`
    一致：MQ 跨服务/定时任务/未知上游场景需要继续 trace。

    返回最终的 `Traceparent`（含生成值），方便 HTTP middleware 写回响应头。
    调用方**必须**在作用域结束时调用 `clear()`。
    """
    tp = parse_traceparent(header_value)
    if tp is None:
        tp = Traceparent(generate_trace_id(), generate_span_id())
    # remote=True 标识入站头解析 —— propagator 知道这是上游传的
    trace_id_var.set(tp.trace_id)
    span_id_var.set(tp.span_id)
    _attach_otel(tp.trace_id, tp.span_id, remote=True)
    return tp


def clear() -> None:
    """清空 contextvars + detach OTel Context。幂等：未 set 时不抛。"""
    trace_id_var.set(None)
    span_id_var.set(None)
    _detach_otel()


def current() -> Tuple[Optional[str], Optional[str]]:
    """读取当前 (trace_id, span_id)。

    优先 OTel Context（OTel SpanContext 走 contextvars，权威 source of truth），
    回退 contextvar。这样 OTel Tracer / propagator 写入的 span 也能被
    logging filter 读到 hex 字符串。
    """
    if _OTEL_BRIDGE_AVAILABLE:
        try:
            from opentelemetry import trace as _otel_trace
            span = _otel_trace.get_current_span()
            sc = span.get_span_context()
            if sc is not None and sc.is_valid:
                return f"{sc.trace_id:032x}", f"{sc.span_id:016x}"
        except Exception:  # pragma: no cover - OTel 状态异常时回退
            pass
    return trace_id_var.get(), span_id_var.get()
