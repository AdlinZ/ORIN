"""
FastAPI middleware：每个 HTTP 请求解析入站 `traceparent` header，绑定到
contextvars，处理完 `finally` 清空。

跳过 `/health` / `/v1/health`（与后端 `TraceIdFilter.shouldNotFilter` 行为一致），
避免健康检查拖带 trace context。

调用契约：注册时**最后** `app.add_middleware(...)`（Starlette LIFO：最后
add = 最外层 = 最早看到请求），见 `app/main.py` 注释。
"""
from __future__ import annotations

from typing import Set

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.core.trace_context import bind_from_traceparent, clear
from app.core.w3c_trace import TRACEPARENT_HEADER


class TraceContextMiddleware(BaseHTTPMiddleware):
    """W3C traceparent inbound → contextvar。"""

    # 健康检查与版本探针不绑 trace，避免 noise。
    SKIP_PATHS: Set[str] = {"/health", "/v1/health"}

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        path = request.url.path
        if path in self.SKIP_PATHS:
            return await call_next(request)

        # Starlette Headers 大小写不敏感，traceparent / Traceparent 都行。
        header = request.headers.get(TRACEPARENT_HEADER)
        bind_from_traceparent(header)
        try:
            response: Response = await call_next(request)
            return response
        finally:
            clear()
