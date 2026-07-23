"""
Lightweight HTTP client for Runner ↔ Control Plane communication.

All calls are synchronous (the Runner is a single-purpose CLI daemon,
not a web server).  Every request carries a W3C traceparent header.

Auth-semantic helpers:
- ``ClientError``: base for all Runner client errors
- ``AuthError``: 401/403 — credential invalid or revoked (caller should exit)
"""

from __future__ import annotations

import uuid
from typing import Any, Dict, Optional

import httpx

from app.core.w3c_trace import TRACEPARENT_HEADER, build_traceparent

class ClientError(Exception):
    """Base exception for Runner client errors."""


class AuthError(ClientError):
    """401 or 403 — credential is invalid or revoked.  Runner must exit."""


def _check_auth(resp: httpx.Response) -> None:
    if resp.status_code in (401, 403):
        raise AuthError(
            f"Control Plane returned {resp.status_code}: "
            + (resp.text[:200] if resp.text else "no body")
        )


class RunnerClient:
    """Thin wrapper around ``httpx.Client`` with Runner-specific helpers."""

    def __init__(self, control_plane_url: str, timeout_sec: float = 30.0):
        self._base = control_plane_url.rstrip("/")
        self._client = httpx.Client(
            base_url=self._base,
            timeout=timeout_sec,
            headers={"User-Agent": "ORIN-Runner/0.1.0"},
        )

    # ------------------------------------------------------------------
    # enrollment
    # ------------------------------------------------------------------

    def enroll(
        self, token: str, name: str, hostname: str, os_name: str,
        arch: str, version: str, cpu_cores: int, max_concurrency: int = 1,
    ) -> Dict[str, Any]:
        """POST /api/system/runners/enroll with Enrollment Token auth."""
        body: Dict[str, Any] = {
            "name": name,
            "hostname": hostname,
            "os": os_name,
            "arch": arch,
            "version": version,
            "cpuCores": cpu_cores,
            "maxConcurrency": max_concurrency,
        }
        resp = self._client.post(
            "/api/system/runners/enroll",
            json=body,
            headers={
                "Authorization": f"Enrollment {token}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()
        return resp.json()

    # ------------------------------------------------------------------
    # heartbeat
    # ------------------------------------------------------------------

    def heartbeat(
        self,
        runner_id: str,
        credential: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """POST /api/system/runners/{runnerId}/heartbeat."""
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/heartbeat",
            json=payload,
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()
        return resp.json()

    # ------------------------------------------------------------------
    # command-ack
    # ------------------------------------------------------------------

    def ack(self, runner_id: str, credential: str, command: str) -> None:
        """POST /api/system/runners/{runnerId}/command-ack."""
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/command-ack",
            json={"command": command.upper()},
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()


def _new_traceparent() -> str:
    """Generate a fresh traceparent for outbound Runner requests.

    Uses ``build_traceparent`` from the core w3c_trace module so the
    format stays consistent with the rest of the AI Engine.
    """
    trace_id = uuid.uuid4().hex[:32]
    span_id = uuid.uuid4().hex[:16]
    try:
        return build_traceparent(
            trace_id=trace_id,
            span_id=span_id,
        )
    except Exception:
        # Never block the Runner on tracing, but keep the fallback W3C-valid:
        # all-zero trace/span IDs are explicitly invalid.
        return f"00-{trace_id}-{span_id}-01"
