"""
Lightweight HTTP client for Runner ↔ Control Plane communication.

All calls are synchronous (the Runner is a single-purpose CLI daemon,
not a web server).  Every request carries a W3C traceparent header.

Auth-semantic helpers:
- ``ClientError``: base for all Runner client errors
- ``AuthError``: 401/403 — credential invalid or revoked (caller should exit)
"""

from __future__ import annotations

import ipaddress
import uuid
from typing import Any, Dict, Optional
from urllib.parse import urlsplit

import httpx

from app.core.w3c_trace import TRACEPARENT_HEADER, build_traceparent

class ClientError(Exception):
    """Base exception for Runner client errors."""


class AuthError(ClientError):
    """401 or 403 — credential is invalid or revoked.  Runner must exit."""


class LeaseTerminalError(ClientError):
    """409/410 from renew — the current assignment can no longer execute."""

    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(f"{status_code} {code}: {message}")
        self.status_code = status_code
        self.code = code


def _check_auth(resp: httpx.Response) -> None:
    if resp.status_code in (401, 403):
        raise AuthError(
            f"Control Plane returned {resp.status_code}: "
            + (resp.text[:200] if resp.text else "no body")
        )


def _check_lease_terminal(resp: httpx.Response) -> None:
    if resp.status_code not in (409, 410):
        return
    try:
        payload = resp.json()
    except ValueError:
        payload = {}
    code = str(payload.get("code") or "LEASE_TERMINATED")
    message = str(payload.get("message") or resp.text[:200] or "lease terminated")
    raise LeaseTerminalError(resp.status_code, code, message)


def _should_trust_env(control_plane_url: str) -> bool:
    """Use environment proxies for remote Control Planes, never loopback ones."""
    hostname = (urlsplit(control_plane_url).hostname or "").lower()
    if hostname == "localhost" or hostname.endswith(".localhost"):
        return False
    try:
        return not ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return True


class RunnerClient:
    """Thin wrapper around ``httpx.Client`` with Runner-specific helpers."""

    def __init__(self, control_plane_url: str, timeout_sec: float = 30.0):
        self._base = control_plane_url.rstrip("/")
        self._client = httpx.Client(
            base_url=self._base,
            timeout=timeout_sec,
            headers={"User-Agent": "ORIN-Runner/0.3.0-rc.1"},
            # macOS / corporate proxy settings can otherwise intercept
            # localhost Control Plane traffic and return a misleading 502.
            trust_env=_should_trust_env(self._base),
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

    # ------------------------------------------------------------------
    # F03 / R2 — lease claim / renew / events / result / secret-bind
    # ------------------------------------------------------------------

    def claim_lease(
        self, runner_id: str, credential: str,
    ) -> Dict[str, Any]:
        """POST /api/system/runners/{runnerId}/lease/claim.

        Returns the parsed JSON response which includes ``acquired``,
        ``runId``, ``assignmentId``, ``leaseId``, ``leaseToken``,
        ``configSnapshot``, ``input``, ``leaseExpiresAt``, ``traceId``.

        When ``acquired`` is false, only that field is present.
        """
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/lease/claim",
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()
        return resp.json()

    def submit_result(
        self,
        runner_id: str,
        credential: str,
        run_id: str,
        lease_id: str,
        status: str,
        output: str = None,
        error_message: str = None,
        error_code: str = None,
    ) -> None:
        """POST /api/system/runners/{runnerId}/runs/{runId}/result.

        ``status`` must be ``"COMPLETED"`` or ``"FAILED"``.
        """
        body: Dict[str, Any] = {
            "leaseId": lease_id,
            "status": status,
        }
        if output is not None:
            body["output"] = output
        if error_message is not None:
            body["errorMessage"] = error_message
        if error_code is not None:
            body["errorCode"] = error_code
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/runs/{run_id}/result",
            json=body,
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()

    def submit_events(
        self,
        runner_id: str,
        credential: str,
        run_id: str,
        lease_id: str,
        events,
    ) -> None:
        """POST /api/system/runners/{runnerId}/runs/{runId}/events.

        ``events`` is a list of dicts with keys ``seq``, ``level``, ``message``,
        and optional ``timestamp``.
        """
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/runs/{run_id}/events",
            json={"leaseId": lease_id, "events": events},
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()

    def renew_lease(
        self, runner_id: str, credential: str, lease_id: str,
    ) -> Dict[str, Any]:
        """POST /api/system/runners/{runnerId}/lease/{leaseId}/renew.

        Returns ``{"action": "no_op"|"cancel"|"drain", "reason": ...,
        "leaseExpiresAt": ..., "traceId": ...}``.
        """
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/lease/{lease_id}/renew",
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        _check_lease_terminal(resp)
        resp.raise_for_status()
        return resp.json()

    def bind_secrets(
        self, runner_id: str, credential: str, run_id: str, assignment_id: str,
    ) -> Dict[str, Any]:
        """POST /api/system/runners/{runnerId}/runs/{runId}/secret-bind.

        Request body: ``{"assignmentId": "..."}``.
        Returns ``{"leaseId", "runId", "materializedSecrets": {...},
        "secretRevisionBindings": {...}, "expiresAtEpochMs": ...}``.
        """
        resp = self._client.post(
            f"/api/system/runners/{runner_id}/runs/{run_id}/secret-bind",
            json={"assignmentId": assignment_id},
            headers={
                "Authorization": f"Runner {credential}",
                TRACEPARENT_HEADER: _new_traceparent(),
            },
        )
        _check_auth(resp)
        resp.raise_for_status()
        return resp.json()


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
