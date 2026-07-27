"""
Work loop — claims Agent Runs from Control Plane and executes them via TaskRuntime.

Runs concurrently with :class:`HeartbeatLoop`. The Runner maintains one
heartbeat goroutine (sync) and one work-loop goroutine (async).  Together
they form the complete Runner lifecycle per ADR-001 / ADR-002.

Flow per iteration::

    claim → secret-bind → execute (TaskRuntime) + renew (periodic) +
    events (periodic flush) → result

Design constraints:
- Execution ALWAYS goes through ``TaskRuntime.execute_agent_task()``
  (ADR-001 D-1.3).  No second execution kernel.
- Materialized secrets live only in an in-memory dict scoped to the
  current assignment; cleared on termination (ADR-002 D-2.7).
- Simple polling for /lease/claim (MVP — no long-poll); backoff when idle.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Dict, List, Optional

from app.engine.task_runtime import TaskRuntime
from app.runner.client import AuthError, LeaseTerminalError, RunnerClient

logger = logging.getLogger(__name__)

# Default intervals
_DEFAULT_CLAIM_POLL_SEC = 5.0
_DEFAULT_RENEW_INTERVAL_SEC = 10.0
_DEFAULT_EVENT_FLUSH_INTERVAL_SEC = 3.0
_DEFAULT_CLAIM_BACKOFF_MAX_SEC = 60.0


class WorkLoop:
    """Async loop that claims work, executes it, and reports results."""

    def __init__(
        self,
        client: RunnerClient,
        runner_id: str,
        credential: str,
        *,
        max_concurrency: int = 1,
        claim_poll_sec: float = _DEFAULT_CLAIM_POLL_SEC,
        renew_interval_sec: float = _DEFAULT_RENEW_INTERVAL_SEC,
        event_flush_sec: float = _DEFAULT_EVENT_FLUSH_INTERVAL_SEC,
    ):
        self._client = client
        self._runner_id = runner_id
        self._credential = credential
        self._max_concurrency = max_concurrency
        self._claim_poll_sec = claim_poll_sec
        self._renew_interval_sec = renew_interval_sec
        self._event_flush_sec = event_flush_sec
        self._running = False
        self._task_runtime = TaskRuntime()

    # ------------------------------------------------------------------
    # public API
    # ------------------------------------------------------------------

    async def run(self) -> None:
        """Main work loop.  Runs until cancelled or a fatal auth error occurs.

        Spawns at most ``max_concurrency`` concurrent assignment executors.
        """
        self._running = True
        logger.info(
            "Work loop started: runnerId=%s maxConcurrency=%d",
            self._runner_id,
            self._max_concurrency,
        )

        pending: List[asyncio.Task] = []

        try:
            while self._running:
                # Clean up finished tasks
                pending = [t for t in pending if not t.done()]

                if len(pending) < self._max_concurrency:
                    task = asyncio.create_task(self._claim_and_execute())
                    pending.append(task)

                # Wait a bit before checking for capacity again, but also
                # keep an eye on the first task finishing.
                if len(pending) >= self._max_concurrency:
                    # At capacity — wait for any task to finish
                    if pending:
                        try:
                            await asyncio.wait(
                                pending,
                                return_when=asyncio.FIRST_COMPLETED,
                                timeout=self._claim_poll_sec,
                            )
                        except Exception:
                            pass
                else:
                    await asyncio.sleep(self._claim_poll_sec)

        except asyncio.CancelledError:
            logger.info("Work loop cancelled")
        finally:
            self._running = False
            # Cancel remaining tasks
            for task in pending:
                if not task.done():
                    task.cancel()
            if pending:
                await asyncio.gather(*pending, return_exceptions=True)
            logger.info("Work loop stopped")

    # ------------------------------------------------------------------
    # internal
    # ------------------------------------------------------------------

    async def _claim_and_execute(self) -> None:
        """Claim one lease, execute it to completion."""
        assignment_id: Optional[str] = None
        run_id: Optional[str] = None
        lease_id: Optional[str] = None
        secrets: Dict[str, str] = {}
        event_seq = 0
        backoff_sec = 0.0

        try:
            # 1) Claim a lease -------------------------------------------------
            while self._running:
                try:
                    resp = self._client.claim_lease(
                        self._runner_id, self._credential,
                    )
                except AuthError as exc:
                    logger.error("Runner credential rejected; stopping work loop: %s", exc)
                    self._running = False
                    return
                except Exception as exc:
                    logger.warning("Claim lease failed: %s", exc)
                    backoff_sec = min(
                        (backoff_sec + self._claim_poll_sec) * 1.5,
                        _DEFAULT_CLAIM_BACKOFF_MAX_SEC,
                    )
                    await asyncio.sleep(max(backoff_sec, self._claim_poll_sec))
                    continue

                if not resp.get("acquired"):
                    backoff_sec = min(
                        backoff_sec + self._claim_poll_sec,
                        _DEFAULT_CLAIM_BACKOFF_MAX_SEC,
                    )
                    await asyncio.sleep(max(backoff_sec, self._claim_poll_sec))
                    continue

                backoff_sec = 0.0
                assignment_id = resp.get("assignmentId")
                run_id = resp.get("runId")
                lease_id = resp.get("leaseId") or resp.get("leaseToken")
                if not run_id or not lease_id:
                    logger.warning("Claim response missing runId or leaseId: %s", resp)
                    continue

                logger.info(
                    "Lease claimed: run=%s assignment=%s leaseExpiresAt=%s",
                    run_id,
                    assignment_id,
                    resp.get("leaseExpiresAt"),
                )
                break  # got a lease — proceed to execution

            if not run_id:
                return  # loop stopped or no work

            # 2) Bind secrets ---------------------------------------------------
            try:
                secret_resp = self._client.bind_secrets(
                    self._runner_id, self._credential, run_id, assignment_id,
                )
                secrets = secret_resp.get("materializedSecrets", {})
                logger.info(
                    "Secrets bound: run=%s count=%d", run_id, len(secrets),
                )
            except Exception as exc:
                logger.error("Secret bind failed for run=%s: %s", run_id, exc)
                await self._fail_run(
                    run_id, lease_id, "SECRET_BIND_FAILED",
                    f"Failed to bind secrets: {exc}",
                )
                return

            # 3) Execute + renew + events (concurrent) -------------------------
            input_text = resp.get("input", "")
            config_snapshot = resp.get("configSnapshot", "{}")

            # Build execution context
            context: Dict[str, Any] = {
                "run_id": run_id,
                "assignment_id": assignment_id,
                "config_snapshot": config_snapshot,
            }

            # Start renew + event flush as background tasks
            renew_stop = asyncio.Event()
            event_queue: asyncio.Queue = asyncio.Queue()
            event_buffer: List[Dict[str, Any]] = []
            event_flush_lock = asyncio.Lock()

            async def _renew_loop() -> Optional[str]:
                """Returns cancel reason if lease was cancelled, else None."""
                while not renew_stop.is_set():
                    try:
                        await asyncio.sleep(self._renew_interval_sec)
                        if renew_stop.is_set():
                            break
                        renew_resp = self._client.renew_lease(
                            self._runner_id, self._credential, lease_id,
                        )
                        action = renew_resp.get("action", "no_op")
                        if action == "cancel":
                            reason = renew_resp.get("reason", action)
                            logger.warning(
                                "Lease renew returned action=%s reason=%s run=%s",
                                action, reason, run_id,
                            )
                            return reason
                        if action == "drain":
                            logger.info(
                                "Runner drain acknowledged; current run continues: %s",
                                run_id,
                            )
                    except asyncio.CancelledError:
                        break
                    except AuthError as exc:
                        logger.error("Runner credential rejected during renew: %s", exc)
                        self._running = False
                        return "RUNNER_CREDENTIAL_REJECTED"
                    except LeaseTerminalError as exc:
                        logger.warning("Lease is no longer writable run=%s: %s", run_id, exc)
                        return exc.code
                    except Exception as exc:
                        logger.warning(
                            "Lease renew error run=%s: %s", run_id, exc,
                        )
                return None

            async def _flush_events_once() -> None:
                async with event_flush_lock:
                    while not event_queue.empty():
                        try:
                            event_buffer.append(event_queue.get_nowait())
                        except asyncio.QueueEmpty:
                            break
                    if event_buffer and lease_id:
                        self._client.submit_events(
                            self._runner_id, self._credential, run_id, lease_id,
                            list(event_buffer),
                        )
                        event_buffer.clear()

            async def _event_flusher():
                while not renew_stop.is_set():
                    try:
                        await asyncio.sleep(self._event_flush_sec)
                        await _flush_events_once()
                    except asyncio.CancelledError:
                        break
                    except Exception as exc:
                        logger.warning("Event flush failed run=%s: %s", run_id, exc)

            async def _enqueue_event(level: str, message: str):
                nonlocal event_seq
                event_seq += 1
                await event_queue.put({
                    "seq": event_seq,
                    "level": level,
                    "message": message,
                    "timestamp": int(time.time() * 1000),
                })

            renew_task = asyncio.create_task(_renew_loop())
            flush_task = asyncio.create_task(_event_flusher())

            try:
                # F04: richer event timeline
                await _enqueue_event("INFO", f"Run started: {run_id}")
                await _enqueue_event("INFO",
                    f"Config snapshot loaded: {len(config_snapshot)} bytes")
                await _enqueue_event("INFO",
                    f"Secrets bound: {len(secrets)} materialized")

                # 4) Execute via TaskRuntime (the SOLE execution kernel) -----
                await _enqueue_event("INFO", "Execution started via TaskRuntime")
                execution_task = asyncio.create_task(
                    self._task_runtime.execute_agent_task(
                        description=input_text or "Execute agent task",
                        expected_role="agent",
                        context=context,
                        materialized_secrets=secrets,
                    )
                )
                done, _ = await asyncio.wait(
                    {execution_task, renew_task},
                    return_when=asyncio.FIRST_COMPLETED,
                )
                if renew_task in done:
                    cancel_reason = renew_task.result()
                    if cancel_reason is not None:
                        execution_task.cancel()
                        await asyncio.gather(execution_task, return_exceptions=True)
                        logger.warning(
                            "Run execution stopped by lease control: run=%s reason=%s",
                            run_id,
                            cancel_reason,
                        )
                        return
                output = await execution_task

                await _enqueue_event("INFO", f"Execution completed: {len(output) if output else 0} chars output")
                await _enqueue_event("INFO", f"Run finished: {run_id}")

                # Events must be accepted while the assignment is still active;
                # submitting result first would make the final buffered events invalid.
                await _flush_events_once()

                # 5) Submit result (COMPLETED) ---------------------------------
                self._client.submit_result(
                    self._runner_id, self._credential, run_id, lease_id,
                    status="COMPLETED",
                    output=output,
                )
                logger.info("Run completed: %s", run_id)

            except asyncio.CancelledError:
                logger.info("Execution cancelled for run=%s", run_id)
                await self._fail_run(
                    run_id, lease_id, "CANCELLED",
                    "Execution cancelled",
                )
            except Exception as exc:
                logger.error("Run failed: %s — %s", run_id, exc)
                await _enqueue_event("ERROR", f"Execution error: {exc}")
                try:
                    await _flush_events_once()
                except Exception as flush_exc:
                    logger.warning("Final event flush failed run=%s: %s", run_id, flush_exc)
                await self._fail_run(
                    run_id, lease_id, "RUNNER_FAILED",
                    f"Execution error: {exc}",
                )
            finally:
                # Stop background tasks
                renew_stop.set()
                renew_task.cancel()
                flush_task.cancel()
                try:
                    await asyncio.gather(
                        renew_task, flush_task, return_exceptions=True,
                    )
                except Exception:
                    pass
                # Clear secrets from memory (ADR-002 D-2.7)
                secrets.clear()

        except Exception as exc:
            logger.error("Claim-execute loop error: %s", exc)

    async def _fail_run(
        self, run_id: str, lease_id: str, error_code: str, message: str,
    ) -> None:
        """Best-effort failure report."""
        try:
            self._client.submit_result(
                self._runner_id, self._credential, run_id, lease_id,
                status="FAILED",
                error_message=message,
                error_code=error_code,
            )
        except Exception as exc:
            logger.error(
                "Failed to report failure for run=%s: %s", run_id, exc,
            )
