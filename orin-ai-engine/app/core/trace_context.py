"""
contextvars 存储层 —— 把 W3C traceId / spanId 绑定到当前 asyncio task / 同步线程。

与 `app/core/w3c_trace.py` 解耦：
- `w3c_trace.py` 只做格式（parse / build / is_valid / generate）
- `trace_context.py` 只做存储（bind / clear / current）

调用方约定：使用 `bind_*` 后**必须**包 `try/finally clear()`，本模块不自动
清理（与后端 `CollaborationResultListener.bindMdcFromMessage` 行为一致：
绑定只动 state，清理是作用域拥有者的责任）。

asyncio task 间天然隔离（ContextVar 语义）；同步线程需自行保证。
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


# ---- 绑定 / 清理 / 读取 ----


def bind_raw(trace_id: str, span_id: str) -> None:
    """显式写入 trace_id / span_id。"""
    trace_id_var.set(trace_id)
    span_id_var.set(span_id)


def bind_from_traceparent(header_value: Optional[str]) -> Traceparent:
    """解析 header 并写入 contextvars。非法或 missing 时**生成新** trace_id/span_id 兜底。

    兜底逻辑与后端 `TraceIdFilter.parseTraceId` L91 / `TraceContext.buildFromMdc()`
    一致：MQ 跨服务/定时任务/未知上游场景需要继续 trace。

    返回最终的 `Traceparent`（含生成值），方便 HTTP middleware 写回响应头。
    调用方**必须**在作用域结束时调用 `clear()`。
    """
    tp = parse_traceparent(header_value)
    if tp is None:
        tp = Traceparent(generate_trace_id(), generate_span_id())
    bind_raw(tp.trace_id, tp.span_id)
    return tp


def clear() -> None:
    """清空 contextvars。幂等：未 set 时调 set(None) 不抛错。"""
    trace_id_var.set(None)
    span_id_var.set(None)


def current() -> Tuple[Optional[str], Optional[str]]:
    """读取当前 (trace_id, span_id)，未绑定时返回 (None, None)。"""
    return trace_id_var.get(), span_id_var.get()
