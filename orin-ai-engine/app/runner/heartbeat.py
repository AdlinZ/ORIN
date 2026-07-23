"""
Periodic heartbeat loop — the Runner's main control loop after enrollment.

Sends ``POST /api/system/runners/{id}/heartbeat`` every N seconds with
current resource snapshots.  Reads the ``drainAck`` flag from the
response and triggers a graceful drain when the Control Plane requests it.

Error handling:
- 401 / 403 → exit immediately (credential invalid or revoked)
- Network errors → exponential backoff with jitter (capped at 5 min)
"""

from __future__ import annotations

import logging
import random
import signal
import time
from typing import Any, Dict, Optional

import httpx

from app.runner.client import AuthError, RunnerClient
from app.runner.collector import check_dependency_health, collect_heartbeat_payload

logger = logging.getLogger(__name__)

# Module-level flag so the signal handler can reach it without a class instance.
_drain_requested = False

# Backoff config
_BACKOFF_BASE_SEC = 1.0
_BACKOFF_MAX_SEC = 300.0  # 5 min cap
_BACKOFF_MULTIPLIER = 2.0


def _on_signal(signum: int, _frame: Any) -> None:
    global _drain_requested
    logger.info("Received signal %s — requesting drain", signum)
    _drain_requested = True


class HeartbeatLoop:
    """Runs the machine channel until signalled or authentication is rejected."""

    def __init__(
        self,
        client: RunnerClient,
        runner_id: str,
        credential: str,
        interval_sec: int = 15,
        version: Optional[str] = None,
    ):
        self._client = client
        self._runner_id = runner_id
        self._credential = credential
        self._interval = interval_sec
        self._version = version
        self._consecutive_failures = 0
        self._draining = False
        self._drain_ack_sent = False

    def run(self) -> None:
        """Block while connected; a drain command only disables new work claims."""
        global _drain_requested
        _drain_requested = False

        # Register signal handlers for graceful shutdown (best-effort; fail soft
        # so the loop remains testable in environments that block signal changes).
        try:
            signal.signal(signal.SIGTERM, _on_signal)
            signal.signal(signal.SIGINT, _on_signal)
        except Exception:
            pass

        logger.info(
            "Heartbeat loop started: runnerId=%s interval=%ds",
            self._runner_id,
            self._interval,
        )

        while not _drain_requested:
            try:
                response = self._send_heartbeat()
                self._consecutive_failures = 0  # reset on success
                drain_requested = bool(response.get("commands", {}).get("drainAck"))
                if drain_requested:
                    self._draining = True
                    if not self._drain_ack_sent:
                        logger.info(
                            "Control Plane requested drain — stop claiming work and keep heartbeating"
                        )
                        try:
                            self._client.ack(self._runner_id, self._credential, "DRAIN")
                            self._drain_ack_sent = True
                        except AuthError:
                            logger.error("Drain ack rejected by Control Plane — exiting")
                            return
                        except Exception:
                            logger.warning("Failed to ack DRAIN command", exc_info=True)
                elif self._draining:
                    logger.info("Control Plane cleared drain — Runner may claim work again")
                    self._draining = False
                    self._drain_ack_sent = False
            except AuthError:
                logger.error(
                    "Authentication failed (401/403) — exiting. "
                    "Credential may be invalid or revoked."
                )
                return
            except (httpx.HTTPStatusError, httpx.RequestError, OSError) as exc:
                self._consecutive_failures += 1
                base_delay = min(
                    _BACKOFF_BASE_SEC * (_BACKOFF_MULTIPLIER ** (self._consecutive_failures - 1)),
                    _BACKOFF_MAX_SEC,
                )
                delay = min(
                    base_delay + random.uniform(0, base_delay * 0.5),
                    _BACKOFF_MAX_SEC,
                )
                logger.warning(
                    "Heartbeat failed (attempt %d) — retrying in %.1fs: %s",
                    self._consecutive_failures, delay, exc,
                )
                # Sleep with signal awareness
                deadline = time.monotonic() + delay
                while time.monotonic() < deadline and not _drain_requested:
                    time.sleep(min(1.0, deadline - time.monotonic()))
                continue
            except Exception:
                logger.warning("Heartbeat failed — will retry", exc_info=True)

            # Sleep in 1 s chunks so signals are handled promptly
            deadline = time.monotonic() + self._interval
            while time.monotonic() < deadline and not _drain_requested:
                time.sleep(min(1.0, deadline - time.monotonic()))

        logger.info("Heartbeat loop exiting (drain requested)")

    def _send_heartbeat(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "dependencyHealth": check_dependency_health(),
        }
        if self._version:
            payload["version"] = self._version

        # Merge resource snapshot
        snap = collect_heartbeat_payload()
        payload.update(snap)

        return self._client.heartbeat(self._runner_id, self._credential, payload)
