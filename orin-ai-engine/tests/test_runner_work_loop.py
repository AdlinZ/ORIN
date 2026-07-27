"""Vertical Runner work-loop tests using the real deterministic TaskRuntime path.

F04 update: richer event timeline (started → config → secrets → execution → completed → finished).
"""

from __future__ import annotations

import asyncio
from typing import Any, Dict, List

import pytest

from app.runner.client import AuthError, LeaseTerminalError
from app.runner.work_loop import WorkLoop


class RecordingControlPlane:
    def __init__(self) -> None:
        self.results: List[Dict[str, Any]] = []
        self.event_batches: List[List[Dict[str, Any]]] = []

    def claim_lease(self, runner_id: str, credential: str) -> Dict[str, Any]:
        return {
            "acquired": True,
            "runId": "run-e2e",
            "assignmentId": "assignment-e2e",
            "leaseId": "lease-e2e",
            "input": "hello runner",
            "configSnapshot": '{"model":{"providerType":"ORIN_DETERMINISTIC"}}',
            "traceId": "trace-e2e",
        }

    def bind_secrets(
        self, runner_id: str, credential: str, run_id: str, assignment_id: str,
    ) -> Dict[str, Any]:
        return {"materializedSecrets": {}}

    def renew_lease(self, runner_id: str, credential: str, lease_id: str) -> Dict[str, Any]:
        return {"action": "no_op"}

    def submit_events(
        self, runner_id: str, credential: str, run_id: str, lease_id: str,
        events: List[Dict[str, Any]],
    ) -> None:
        self.event_batches.append(events)

    def submit_result(
        self, runner_id: str, credential: str, run_id: str, lease_id: str,
        status: str, output: str = None, error_message: str = None,
        error_code: str = None,
    ) -> None:
        self.results.append({"status": status, "output": output, "errorCode": error_code})


@pytest.mark.asyncio
async def test_work_loop_claims_executes_flushes_events_then_submits_result():
    """F04: expects 6 events (started, config, secrets, execution_started, execution_completed, finished)."""
    control_plane = RecordingControlPlane()
    loop = WorkLoop(
        control_plane, "runner-e2e", "credential-e2e",
        renew_interval_sec=60, event_flush_sec=60,
    )
    loop._running = True

    await loop._claim_and_execute()

    assert control_plane.results == [{
        "status": "COMPLETED",
        "output": "ORIN deterministic runner result: hello runner",
        "errorCode": None,
    }]
    # F04: 6 events instead of 2
    event_seqs = [event["seq"] for batch in control_plane.event_batches for event in batch]
    assert len(event_seqs) == 6
    assert event_seqs == [1, 2, 3, 4, 5, 6]


class RejectedControlPlane(RecordingControlPlane):
    def claim_lease(self, runner_id: str, credential: str) -> Dict[str, Any]:
        raise AuthError("revoked")


@pytest.mark.asyncio
async def test_work_loop_stops_after_runner_credential_rejection():
    loop = WorkLoop(RejectedControlPlane(), "runner-e2e", "bad-credential")
    loop._running = True

    await loop._claim_and_execute()

    assert loop._running is False


class ExpiredLeaseControlPlane(RecordingControlPlane):
    def renew_lease(self, runner_id: str, credential: str, lease_id: str) -> Dict[str, Any]:
        raise LeaseTerminalError(410, "140005", "expired")


@pytest.mark.asyncio
async def test_work_loop_cancels_execution_when_renew_reports_expired_lease():
    control_plane = ExpiredLeaseControlPlane()
    loop = WorkLoop(
        control_plane, "runner-e2e", "credential-e2e",
        renew_interval_sec=0.01, event_flush_sec=60,
    )

    async def slow_execution(*args, **kwargs):
        await asyncio.sleep(10)
        return "must not complete"

    loop._task_runtime.execute_agent_task = slow_execution
    loop._running = True

    await asyncio.wait_for(loop._claim_and_execute(), timeout=1)

    assert control_plane.results == []
