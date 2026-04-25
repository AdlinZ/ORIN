from app.api import playground_runtime
from app.api.playground_runtime import build_graph, run_playground_workflow, PlaygroundRunRequest
import pytest


def test_builds_all_playground_graph_types():
    agents = [{"id": "agent_a", "name": "Researcher"}, {"id": "agent_b", "name": "Reviewer"}]
    for workflow_type in [
        "single_agent_chat",
        "router_specialists",
        "planner_executor",
        "supervisor_dynamic",
        "peer_handoff",
    ]:
        graph = build_graph({"type": workflow_type, "finalizer_enabled": True}, agents)
        assert graph["nodes"]
        assert graph["edges"]


def test_planner_task_split_keeps_role_and_constraints_as_global_context():
    user_input = (
        "你是总协调。请让研究员、审稿人、产品经理协作：先给3种方案，再互评优缺点，最后输出折中方案。"
        "要求每个角色至少发言一次，并明确标注来源角色。"
    )
    tasks = playground_runtime.plan_tasks(user_input, max_tasks=6)
    assert len(tasks) >= 3
    assert all("你是总协调" not in task for task in tasks)
    assert all("要求每个角色至少发言一次" in task for task in tasks)
    assert not any(task.strip() == "你是总协调" for task in tasks)


def test_normalize_subtask_payload_supports_camel_and_snake_case():
    raw = {
        "id": "task_1",
        "description": "demo",
        "role": "SPECIALIST",
        "dependsOn": ["task_0"],
        "inputData": {"preferred_agent_id": "agent_a"},
    }
    normalized = playground_runtime._normalize_subtask_payload(raw)
    assert normalized["depends_on"] == ["task_0"]
    assert normalized["input_data"]["preferred_agent_id"] == "agent_a"


@pytest.mark.asyncio
async def test_run_playground_workflow_returns_trace_and_final_answer(monkeypatch):
    async def fake_bootstrap(state):
        workflow_type = state["workflow"]["type"]
        subtasks = playground_runtime._workflow_tasks(workflow_type, state["user_input"], state["agents"])
        return "pkg_test", subtasks

    async def fake_trigger(package_id, subtask, trace_id):
        return None

    async def fake_poll_branch_result(package_id, sub_task_id, timeout=300.0, poll_interval=1.0):
        return {"result": f"done:{sub_task_id}"}

    monkeypatch.setattr(playground_runtime, "_bootstrap_collaboration_package", fake_bootstrap)
    monkeypatch.setattr(playground_runtime, "_trigger_subtask", fake_trigger)
    monkeypatch.setattr(playground_runtime, "poll_branch_result", fake_poll_branch_result)

    request = PlaygroundRunRequest(
        run_id="run_test",
        workflow={
            "id": "workflow_test",
            "name": "Planner Demo",
            "type": "planner_executor",
            "finalizer_enabled": True,
        },
        agents=[
            {"id": "agent_a", "name": "Researcher", "description": "research"},
            {"id": "agent_b", "name": "Reviewer", "description": "review"},
        ],
        user_input="research the topic then review the result",
        conversation_id="conversation_test",
    )

    result = await run_playground_workflow(request)

    assert result["workflow_id"] == "workflow_test"
    assert result["conversation_id"] == "conversation_test"
    assert result["assistant_message"]
    assert any(event["type"] == "run_finished" for event in result["trace"])
    assert isinstance(result["artifacts"].get("task_reports"), list)
    assert len(result["artifacts"]["task_reports"]) >= 1
    node_ids = {node["id"] for node in result["graph"]["nodes"]}
    assert {"start", "planner", "end"}.issubset(node_ids)
    assert any(node_id.startswith("task_") for node_id in node_ids)
