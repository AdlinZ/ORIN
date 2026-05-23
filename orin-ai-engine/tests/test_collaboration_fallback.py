from unittest.mock import AsyncMock

import pytest
from langgraph.graph import END, StateGraph

from app.engine.collaboration_langgraph import state as collaboration_state
from app.engine.collaboration_langgraph.nodes import (
    critic_node,
    fallback_prepare_node,
    should_continue_critic,
    should_continue_fallback_prepare,
)
from app.engine.collaboration_langgraph.state import CollaborationState, CollaborationStatus
from app.models.workflow import NodeExecutionOutput


def test_should_continue_critic_routes_fallback_before_limit(monkeypatch):
    monkeypatch.setattr(collaboration_state, "MAX_FALLBACK_ATTEMPTS", 3)

    route = should_continue_critic({
        "status": CollaborationStatus.FALLBACK.value,
        "fallback_attempts": 2,
    })

    assert route == "fallback_prepare"


def test_should_continue_fallback_prepare_routes_by_mode():
    assert should_continue_fallback_prepare({
        "status": CollaborationStatus.EXECUTING.value,
        "collaboration_mode": "SEQUENTIAL",
    }) == "delegate"
    assert should_continue_fallback_prepare({
        "status": CollaborationStatus.EXECUTING.value,
        "collaboration_mode": "PARALLEL",
    }) == "parallel_fork"
    assert should_continue_fallback_prepare({
        "status": CollaborationStatus.FAILED.value,
        "collaboration_mode": "PARALLEL",
    }) == "end_failed"


@pytest.mark.asyncio
async def test_fallback_prepare_node_resets_local_state_and_calls_backend(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        text = "{}"

        def json(self):
            return {
                "packageId": "pkg-fallback",
                "status": "EXECUTING",
                "attempt": 1,
                "resetSubTaskIds": ["sub-1"],
            }

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def post(self, url, json, headers):
            calls.append({"url": url, "json": json, "headers": headers})
            return FakeResponse()

    monkeypatch.setattr("httpx.AsyncClient", FakeClient)

    result = await fallback_prepare_node({
        "package_id": "pkg-fallback",
        "status": CollaborationStatus.FALLBACK.value,
        "fallback_attempts": 1,
        "collaboration_mode": "SEQUENTIAL",
        "completed_subtasks": ["sub-1"],
        "branch_results": {"sub-1": "old result"},
        "final_result": "old final",
        "shared_context": {
            "__review": "是否通过: NO\n改进建议: revise",
            "__consensus_summary": "old summary",
            "task_sub-1_result": "old result",
            "keep": "value",
        },
    })

    assert calls[0]["url"].endswith("/api/v1/collaboration/packages/pkg-fallback/fallback/retry")
    assert calls[0]["json"]["attempt"] == 1
    assert calls[0]["json"]["review"].startswith("是否通过: NO")
    assert result["status"] == CollaborationStatus.EXECUTING.value
    assert result["completed_subtasks"] == []
    assert result["branch_results"] == {}
    assert result["final_result"] is None
    assert "task_sub-1_result" not in result["shared_context"]
    assert "__consensus_summary" not in result["shared_context"]
    assert result["shared_context"]["__fallback_attempt"] == 1
    assert result["shared_context"]["keep"] == "value"


@pytest.mark.asyncio
async def test_critic_node_fails_when_fallback_attempts_exceeded(monkeypatch):
    monkeypatch.setattr(collaboration_state, "MAX_FALLBACK_ATTEMPTS", 1)
    mock_handler = type(
        "MockHandler",
        (),
        {"run": AsyncMock(return_value=NodeExecutionOutput(outputs={"text": "是否通过: NO\n改进建议: fail"}))},
    )
    monkeypatch.setattr("app.engine.handlers.llm.RealLLMNodeHandler", lambda: mock_handler())

    result = await critic_node({
        "package_id": "pkg-fallback",
        "final_result": "bad result",
        "shared_context": {},
        "fallback_attempts": 0,
    })

    assert result["status"] == CollaborationStatus.FAILED.value
    assert result["fallback_attempts"] == 1
    assert result["error_message"] == "FALLBACK exceeded max attempts (1/1)"
    assert should_continue_critic(result) == "end_failed"


@pytest.mark.asyncio
async def test_fallback_exceeded_ends_graph_without_delegate(monkeypatch):
    monkeypatch.setattr(collaboration_state, "MAX_FALLBACK_ATTEMPTS", 1)
    critic_calls = 0
    delegate_calls = 0

    async def critic(state: CollaborationState):
        nonlocal critic_calls
        critic_calls += 1
        return {
            "status": CollaborationStatus.FAILED.value,
            "fallback_attempts": 1,
            "error_message": "FALLBACK exceeded max attempts (1/1)",
        }

    async def delegate(state: CollaborationState):
        nonlocal delegate_calls
        delegate_calls += 1
        return {"status": CollaborationStatus.EXECUTING.value}

    workflow = StateGraph(CollaborationState)
    workflow.add_node("critic", critic)
    workflow.add_node("delegate", delegate)
    workflow.add_edge("__start__", "critic")
    workflow.add_conditional_edges(
        "critic",
        should_continue_critic,
        {
            "delegate": "delegate",
            "memory_write": END,
            "end_failed": END,
        },
    )
    graph = workflow.compile()

    result = await graph.ainvoke({
        "package_id": "pkg-fallback-graph",
        "status": CollaborationStatus.EXECUTING.value,
        "fallback_attempts": 0,
    })

    assert result["status"] == CollaborationStatus.FAILED.value
    assert result["error_message"] == "FALLBACK exceeded max attempts (1/1)"
    assert critic_calls == 1
    assert delegate_calls == 0
