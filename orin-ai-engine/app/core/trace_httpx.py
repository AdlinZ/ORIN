"""
Outbound httpx factory: every request from AI Engine to the backend (or any
other service) auto-carries a W3C `traceparent` header, generated from the
current contextvar (bound by Phase 2 小刀 2's `TraceContextMiddleware` and
`mq_worker._process_message`).

设计要点：
- `httpx.event_hooks={"request": [...]}` 是 httpx 官方跨切面扩展点
  （auth / retry / tracing 都用它）。一行 factory，零 per-call wrapper 噪音。
- 工厂内部仍调 `httpx.AsyncClient`，所以 test 通过
  `patch("app.engine.<module>.httpx.AsyncClient", ...)` 拦截类时
  factory 链路不断。
- 关闭开关 `Settings.OUTBOUND_TRACEPARENT_DISABLED` 用于本地开发与
  不想注入 W3C header 的旧测试。
"""
from __future__ import annotations

from typing import Any

import httpx

from app.core.config import settings
from app.core.w3c_trace import TRACEPARENT_HEADER, build_from_current


async def _inject_traceparent_hook(request: httpx.Request) -> None:
    """Pre-send hook: 从当前 contextvar 取 trace_id 生成新 span-id，注入 header。

    httpx 的 `event_hooks["request"]` 在 `AsyncClient._send_handling_redirects`
    里通过 `await hook(request)` 调用 —— 必须是 coroutine function。
    复用 `w3c_trace.build_from_current()` —— 它读 `trace_context.current()[0]`、
    生成新 span-id、缺 trace_id 时 UUID 兜底，与日志注入行为一致。
    """
    request.headers[TRACEPARENT_HEADER] = build_from_current()


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
    existing_hooks = list(kwargs.pop("event_hooks", {}).get("request", []))
    existing_hooks.append(_inject_traceparent_hook)
    return httpx.AsyncClient(event_hooks={"request": existing_hooks}, **kwargs)
