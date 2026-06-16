"""
FastAPI middleware：每个 HTTP 请求解析入站 `traceparent` header，绑定到
contextvars + OTel Context，处理完 `finally` 清空。

Phase 2 小刀 5b 改造：用 OTel `propagators.extract` 作为权威入站解析（OTel
SDK 内置 W3C TraceContextPropagator），保留 `parse_traceparent` 作为非法
header 兜底（生成新 trace_id，行为与 OTel invalid span context 互补）：

- 合法入站 header → OTel propagator 提取 → attach OTel Context，**然后**
  同步写 contextvar（让 logging filter / 旧调用方读到 hex 字符串）
- 非法 / missing header → 走 `bind_from_traceparent` 兜底生成新 trace_id

跳过 `/health` / `/v1/health`（与后端 `TraceIdFilter.shouldNotFilter` 行为一致），
避免健康检查拖带 trace context。

调用契约：注册时**最后** `app.add_middleware(...)`（Starlette LIFO：最后
add = 最外层 = 最早看到请求），见 `app/main.py` 注释。
"""
from __future__ import annotations

from typing import Any, Dict, Set

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.core.trace_context import bind_from_traceparent, bind_raw, clear
from app.core.w3c_trace import TRACEPARENT_HEADER

# OTel 包缺失时降级 no-op：直接走 `bind_from_traceparent`（保留旧行为）
try:
    from opentelemetry import trace as _otel_trace
    from opentelemetry.propagate import extract as _otel_extract
    _OTEL_PROPAGATOR_AVAILABLE = True
except ImportError:  # pragma: no cover - 兜底
    _OTEL_PROPAGATOR_AVAILABLE = False
    _otel_trace = None  # type: ignore[assignment]
    _otel_extract = None  # type: ignore[assignment]


class TraceContextMiddleware(BaseHTTPMiddleware):
    """W3C traceparent inbound → contextvar + OTel Context。"""

    # 健康检查与版本探针不绑 trace，避免 noise。
    SKIP_PATHS: Set[str] = {"/health", "/v1/health"}

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        path = request.url.path
        if path in self.SKIP_PATHS:
            return await call_next(request)

        # 1) OTel propagator 提取（W3C TraceContextPropagator 是 OTel 1.42 默认注册）
        #    对非法 / missing header 返 `INVALID` SpanContext（trace_id=0），
        #    由下面 step 2 的 `sc.is_valid` 检查走到兜底。
        bound_via_otel = False
        if _OTEL_PROPAGATOR_AVAILABLE:
            # 构造 plain dict carrier —— propagator 不依赖 Cython Headers
            carrier: Dict[str, str] = {k: v for k, v in request.headers.items()}
            otel_ctx_obj = _otel_extract(carrier)
            # 直接从提取结果读取 span，不先 attach。`bind_raw` 会完成唯一一次
            # attach，避免两个 token 非 LIFO detach 后把入站 context 泄漏到请求外。
            current_span = _otel_trace.get_current_span(otel_ctx_obj)
            sc = current_span.get_span_context()
            if sc is not None and sc.is_valid:
                bind_raw(
                    f"{sc.trace_id:032x}",
                    f"{sc.span_id:016x}",
                    remote=True,
                )
                bound_via_otel = True

        # 2) OTel 路径未生效（包缺失 / propagator 返 invalid）：走原 `bind_from_traceparent`
        #    兜底，保留严格 parse_traceparent 语义（拒绝大写 / 长度错 / 版本错）。
        if not bound_via_otel:
            header = request.headers.get(TRACEPARENT_HEADER)
            bind_from_traceparent(header)

        try:
            response: Response = await call_next(request)
            return response
        finally:
            clear()
