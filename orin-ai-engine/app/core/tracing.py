"""
Tracing client for distributed tracing (Langfuse-style)
"""
import time
import uuid
import logging
from typing import Dict, Any, List, Optional
from dataclasses import dataclass, field
from datetime import datetime

logger = logging.getLogger(__name__)


@dataclass
class Span:
    """Represents a single span in a trace"""
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
    Simple tracing client that can be extended to support Langfuse SDK
    """

    def __init__(self, service_name: str = "ai-engine"):
        self.service_name = service_name
        self.spans: List[Span] = []
        self._current_span: Optional[Span] = None

    def start_trace(self, trace_id: str, name: str = "workflow") -> Span:
        """Start a new trace"""
        span = Span(
            trace_id=trace_id,
            name=name,
            metadata={"service": self.service_name}
        )
        self.spans.append(span)
        self._current_span = span
        logger.info(f"[Tracing] Started trace: trace_id={trace_id}, name={name}")
        return span

    def start_span(self, name: str, metadata: Optional[Dict[str, Any]] = None) -> Span:
        """Start a new span within the current trace"""
        if not self._current_span:
            logger.warning("[Tracing] No active trace, creating a new one")
            self.start_trace(str(uuid.uuid4()), name)

        span = Span(
            trace_id=self._current_span.trace_id,
            parent_id=self._current_span.id,
            name=name,
            metadata=metadata or {}
        )
        self.spans.append(span)
        self._current_span = span
        logger.debug(f"[Tracing] Started span: name={name}, trace_id={span.trace_id}")
        return span

    def finish_span(self, status: str = "success", error: Optional[str] = None):
        """Finish the current span"""
        if self._current_span:
            self._current_span.finish(status=status, error=error)
            logger.debug(f"[Tracing] Finished span: name={self._current_span.name}, "
                        f"duration={self._current_span.duration:.3f}s, status={status}")

            # Return to parent span if exists
            parent = next((s for s in self.spans if s.id == self._current_span.parent_id), None)
            self._current_span = parent

    def record_event(self, event_name: str, metadata: Optional[Dict[str, Any]] = None):
        """Record an event in the current span"""
        if self._current_span:
            event = {
                "name": event_name,
                "timestamp": datetime.now().isoformat(),
                "metadata": metadata or {}
            }
            if "events" not in self._current_span.metadata:
                self._current_span.metadata["events"] = []
            self._current_span.metadata["events"].append(event)
            logger.debug(f"[Tracing] Recorded event: {event_name}")

    def get_trace_summary(self) -> Dict[str, Any]:
        """Get summary of the current trace"""
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
        """Clear all spans"""
        self.spans.clear()
        self._current_span = None


# Global tracing client instance
tracing_client = TracingClient()
