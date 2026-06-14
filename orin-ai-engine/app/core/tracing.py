"""
业务 trace 客户端（Langfuse-style 内存 mirror + OTel 影子 span）。

Phase 2 小刀 5b 改造 —— 业务 span 影子化 + 修 `_current_span` 并发 bug：

- 保留原 `TracingClient` 公共 API（start_trace / start_span / finish_span /
  record_event / get_trace_summary / clear）和 `Span` dataclass，executor.py
  11 处调用方**零改动**。
- 内部**双栈**管理：
  1. `self.spans: List[Span]` —— 旧 dataclass mirror，给 `get_trace_summary()`
     聚合用（前端 Langfuse summary 走这里）
  2. `self._otel_tokens: List[Token]` + `self._otel_spans: List[Span]` ——
     OTel 真正上报的 span + attach token 栈，**LIFO** 配对 detach
- OTel 影子 span 的 trace_id 与入参 trace_id 一致（通过 `bind_raw` 把入参
  trace_id 同步到 OTel Context，OTel 自动继承）；这保证 OTel SDK 上报的
  trace_id 与 contextvar / logging filter / 后端 trace_id join 零偏差。
- **删 `_current_span` 单例指针** —— 之前 `asyncio.gather` 并发跑多个
  node handler 时被抢写（顺序：handler A start → handler B start → handler
  A finish 时读 _current_span 是 B），OTel 的 ContextVar 天然隔离，且栈式
  token 管理替代单例指针。
"""
from __future__ import annotations

import logging
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional

from app.core.otel_setup import get_tracer
from app.core.trace_context import bind_raw

logger = logging.getLogger(__name__)


@dataclass
class Span:
    """Represents a single span in a trace (Langfuse-style mirror).

    `id` 字段为兼容旧 `executor.py:478` 的 `if tracing_client._current_span:`
    形态而保留；`trace_id` 仍是入参 hex 字符串（与 OTel 影子 span 的
    trace_id 一致）。
    """
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    trace_id: str = ""
    parent_id: Optional[str] = None
    name: str = ""
    start_time: float = field(default_factory=time.time)
    end_time: Optional[float] = None
    duration: Optional[float] = None
    status: str = "running"  # running, success, error
    metadata: Dict[str, Any] = field(default_factory=dict)
    error: Optional[str] = None

    def finish(self, status: str = "success", error: Optional[str] = None):
        self.end_time = time.time()
        self.duration = self.end_time - self.start_time
        self.status = status
        if error:
            self.error = error


class TracingClient:
    """
    Langfuse-style 业务 trace 客户端，OTel SDK 影子化（Phase 2 小刀 5b）。

    用法（保持原 API，executor.py 不改）：
        tracing_client.start_trace(trace_id_hex, "workflow:execute")
        node_span = tracing_client.start_span("node:llm:abc", metadata={...})
        tracing_client.record_event("node_completed", {...})
        tracing_client.finish_span(status="success")
        ...
        summary = tracing_client.get_trace_summary()
        tracing_client.clear()
    """

    def __init__(self, service_name: str = "ai-engine"):
        self.service_name = service_name
        # Langfuse-style mirror（append-only：start push，finish 仅 set
        # end_time，**不** pop —— 保持 `get_trace_summary()` 能读到所有历史
        # spans 的旧 API 行为）
        self.spans: List[Span] = []
        # OTel 影子 span 栈（LIFO：start push / finish pop + detach）
        self._otel_spans: List[Any] = []  # type: ignore[name-defined]
        self._otel_tokens: List[Any] = []  # type: ignore[name-defined]
        # 单例 OTel Tracer（app.core.tracing 命名空间）
        self._tracer = get_tracer("app.core.tracing")

    # ---- 内部 OTel helper（顶层 try-import，OTel 缺失时降级 no-op）----

    def _otel_start_span(self, name: str, attributes: Optional[Dict[str, Any]] = None) -> tuple:
        """起 OTel span + attach 当前 Context。返 (otel_span, token) 元组。

        OTel 包缺失时返 (None, None)，调用方按 (None, None) 走 no-op 路径。
        """
        try:
            from opentelemetry import context as otel_context
            from opentelemetry.trace import set_span_in_context
            otel_span = self._tracer.start_span(name, attributes=attributes or {})
            token = otel_context.attach(set_span_in_context(otel_span))
            return otel_span, token
        except Exception:  # pragma: no cover
            return None, None

    def _otel_finish_span(self, otel_span: Any, token: Any, status: str, error: Optional[str]) -> None:
        """OTel span 收尾：set_status + end + detach。"""
        if otel_span is None or token is None:
            return
        try:
            from opentelemetry import context as otel_context
            from opentelemetry.trace import Status, StatusCode
            if status == "error":
                otel_span.set_status(Status(StatusCode.ERROR, error or ""))
                if error:
                    try:
                        otel_span.record_exception(Exception(error))
                    except Exception:  # pragma: no cover
                        pass
            otel_span.end()
            otel_context.detach(token)
        except Exception:  # pragma: no cover
            pass

    def _otel_record_event(self, event_name: str, metadata: Optional[Dict[str, Any]]) -> None:
        """OTel 当前 span 记事件。"""
        if not self._otel_spans:
            return
        try:
            # 用栈顶 span（最近 start 未 finish 的）
            current = self._otel_spans[-1]
            if current is not None:
                attrs = {
                    f"event.{k}": _safe_primitive(v)
                    for k, v in (metadata or {}).items()
                }
                current.add_event(event_name, attributes=attrs)
        except Exception:  # pragma: no cover
            pass

    # ---- 公共 API ----

    def start_trace(self, trace_id: str, name: str = "workflow") -> Span:
        """Start a new trace + OTel 影子根 span。

        OTel 影子 span 的 trace_id 与入参 `trace_id` 一致：通过
        `bind_raw(trace_id, generate_span_id())` 把 trace_id 同步到 OTel
        Context，OTel tracer.start_span 自动继承（生成的 span_id 也跟着
        contextvar 走）。
        """
        # 1) 同步 trace_id 到 OTel Context（让 OTel span trace_id 与入参一致）
        from app.core.w3c_trace import generate_span_id
        bind_raw(trace_id, generate_span_id())

        # 2) Langfuse mirror
        span = Span(
            trace_id=trace_id,
            name=name,
            metadata={"service": self.service_name}
        )
        self.spans.append(span)

        # 3) OTel 影子
        otel_span, token = self._otel_start_span(
            name, attributes={"service": self.service_name, "trace.id": trace_id}
        )
        self._otel_spans.append(otel_span)
        self._otel_tokens.append(token)

        logger.info(f"[Tracing] Started trace: trace_id={trace_id}, name={name}")
        return span

    def start_span(self, name: str, metadata: Optional[Dict[str, Any]] = None) -> Span:
        """Start a child span + OTel 影子 child span（自动继承父 trace_id）。

        若无 active trace，懒起一个（保留旧行为）。
        """
        if not self._otel_spans:
            logger.warning("[Tracing] No active trace, creating a new one")
            self.start_trace(str(uuid.uuid4()), name)

        # 1) Langfuse mirror
        # parent_id 用栈顶 mirror span（与 OTel 栈平行，executor 不读 _current_span，
        # 改读 self.spans 栈顶）
        parent = self.spans[-1] if self.spans else None
        span = Span(
            trace_id=parent.trace_id if parent else "",
            parent_id=parent.id if parent else None,
            name=name,
            metadata=metadata or {}
        )
        self.spans.append(span)

        # 2) OTel 影子（attributes 仅放 primitive，复杂值 str 化）
        safe_attrs = {
            k: _safe_primitive(v) for k, v in (metadata or {}).items()
        }
        otel_span, token = self._otel_start_span(name, attributes=safe_attrs)
        self._otel_spans.append(otel_span)
        self._otel_tokens.append(token)

        logger.debug(f"[Tracing] Started span: name={name}, trace_id={span.trace_id}")
        return span

    def finish_span(self, status: str = "success", error: Optional[str] = None):
        """Finish 当前栈顶 span（Langfuse mirror + OTel 影子双收尾）。

        - OTel 影子栈：LIFO pop + detach（start/finish 必须配对）
        - Langfuse mirror：append-only，**不** pop —— 仅 set end_time 标记
          收尾；保持 `get_trace_summary()` 能读到所有历史 spans
        - 栈顶 mirror（给 record_event / get_trace_summary 用）= 最后一个
          `end_time is None` 的 span
        """
        # OTel 影子收尾（先 OTel，ReadableSpan.end() 之后字段才有值）
        if self._otel_spans:
            otel_span = self._otel_spans.pop()
            token = self._otel_tokens.pop()
            self._otel_finish_span(otel_span, token, status, error)

        # Langfuse mirror 收尾：找**最后一个**未 finish 的 span 设 end_time
        for s in reversed(self.spans):
            if s.end_time is None:
                s.finish(status=status, error=error)
                logger.debug(
                    f"[Tracing] Finished span: name={s.name}, "
                    f"duration={s.duration:.3f}s, status={status}"
                )
                break

    def record_event(self, event_name: str, metadata: Optional[Dict[str, Any]] = None):
        """Record an event in the current span（mirror + OTel 双写）。

        mirror 栈顶 = 最后一个 `end_time is None` 的 span（与 finish_span
        找法一致）。
        """
        # OTel 当前 span 记事件
        self._otel_record_event(event_name, metadata)

        # Langfuse mirror —— 找**最后一个**未 finish 的 span
        current = None
        for s in reversed(self.spans):
            if s.end_time is None:
                current = s
                break
        if current is not None:
            event = {
                "name": event_name,
                "timestamp": datetime.now().isoformat(),
                "metadata": metadata or {}
            }
            if "events" not in current.metadata:
                current.metadata["events"] = []
            current.metadata["events"].append(event)
            logger.debug(f"[Tracing] Recorded event: {event_name}")

    def get_trace_summary(self) -> Dict[str, Any]:
        """Get summary of the current trace（仅读 Langfuse mirror，保持旧 API）。"""
        if not self.spans:
            return {}

        total_duration = sum(s.duration for s in self.spans if s.duration)
        error_count = sum(1 for s in self.spans if s.status == "error")

        return {
            "trace_id": self.spans[0].trace_id if self.spans else None,
            "total_spans": len(self.spans),
            "total_duration": total_duration,
            "error_count": error_count,
            "spans": [
                {
                    "id": s.id,
                    "name": s.name,
                    "duration": s.duration,
                    "status": s.status,
                    "error": s.error
                }
                for s in self.spans
            ]
        }

    def clear(self):
        """Clear all spans。OTel 栈先 detach 全部（防泄漏），mirror 再清。"""
        # OTel 收尾：所有未 finish 的 token 都要 detach
        while self._otel_tokens:
            token = self._otel_tokens.pop()
            try:
                from opentelemetry import context as otel_context
                otel_context.detach(token)
            except Exception:  # pragma: no cover
                pass
        self._otel_spans.clear()
        # Langfuse mirror
        self.spans.clear()


def _safe_primitive(value: Any) -> Any:
    """OTel Span.add_event attributes 只接受 primitive（str/int/float/bool/序列）。
    复杂对象回落 str()。"""
    if isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, (list, tuple)):
        return [str(v) for v in value]
    return str(value)


# Global tracing client instance
tracing_client = TracingClient()
