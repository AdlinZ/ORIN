from unittest.mock import AsyncMock

import pytest
from langgraph.graph import END, StateGraph

from app.engine.collaboration_langgraph import state as collaboration_state
from app.engine.collaboration_langgraph.nodes import critic_node, should_continue_critic
from app.engine.collaboration_langgraph.state import CollaborationState, CollaborationStatus
from app.models.workflow import NodeExecutionOutput


def test_should_continue_critic_routes_fallback_before_limit(monkeypatch):
    monkeypatch.setattr(collaboration_state, "MAX_FALLBACK_ATTEMPTS", 3)

    route = should_continue_critic({
        "status": CollaborationStatus.FALLBACK.value,
        "fallback_attempts": 2,
    })

    assert route == "delegate"


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
