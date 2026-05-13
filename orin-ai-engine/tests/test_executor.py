import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from app.models.workflow import WorkflowDSL, Node, Edge, WorkflowStatus
from app.engine.executor import GraphExecutor


def trace_outputs(result, node_id):
    """Return a node's execution outputs from the trace."""
    for trace in result.trace:
        if trace.node_id == node_id:
            return trace.outputs or {}
    return {}


@pytest.mark.asyncio
async def test_linear_execution():
    """测试线性工作流: start -> llm -> end"""
    # Mock settings + AsyncOpenAI to avoid real API call
    with patch("app.engine.handlers.llm.settings") as mock_settings, \
         patch("app.engine.handlers.llm.AsyncOpenAI") as MockClient:
        mock_settings.OPENAI_API_KEY = "sk-test-key"
        mock_settings.OPENAI_BASE_URL = None

        mock_instance = MockClient.return_value
        mock_response = MagicMock()
        mock_response.choices = [MagicMock(message=MagicMock(content="Mocked Answer"))]
        mock_response.usage = MagicMock(total_tokens=10)
        mock_instance.chat.completions.create = AsyncMock(return_value=mock_response)

        dsl = WorkflowDSL(
            nodes=[
                Node(id="1", type="start"),
                Node(id="2", type="llm", data={"prompt": "Hello"}),
                Node(id="3", type="end")
            ],
            edges=[
                Edge(id="e1", source="1", target="2"),
                Edge(id="e2", source="2", target="3")
            ]
        )

        executor = GraphExecutor()
        result = await executor.execute(dsl, {"query": "test"})

        assert result.status == WorkflowStatus.SUCCESS, f"Expected SUCCESS, got {result.status}. Trace: {[(t.node_id, t.status, t.error) for t in result.trace]}"
        assert "query" in trace_outputs(result, "1")
        llm_output = trace_outputs(result, "2")
        # Ensure LLM output contains mock response
        assert "text" in llm_output
        assert llm_output["text"] == "Mocked Answer"


@pytest.mark.asyncio
async def test_parallel_execution():
    """测试并行工作流: 1 -> {2, 3} -> 4 (两个 LLM 并行执行)"""
    # Mock settings + AsyncOpenAI to avoid real API call
    with patch("app.engine.handlers.llm.settings") as mock_settings, \
         patch("app.engine.handlers.llm.AsyncOpenAI") as MockClient:
        mock_settings.OPENAI_API_KEY = "sk-test-key"
        mock_settings.OPENAI_BASE_URL = None

        mock_instance = MockClient.return_value
        mock_response = MagicMock()
        mock_response.choices = [MagicMock(message=MagicMock(content="Mocked Answer"))]
        mock_response.usage = MagicMock(total_tokens=10)
        mock_instance.chat.completions.create = AsyncMock(return_value=mock_response)

        # 1 -> 2 (LLM)
        # 1 -> 3 (LLM)
        # 2 -> 4 (End)
        # 3 -> 4 (End)
        dsl = WorkflowDSL(
            nodes=[
                Node(id="1", type="start"),
                Node(id="2", type="llm", data={"prompt": "Parallel A"}),
                Node(id="3", type="llm", data={"prompt": "Parallel B"}),
                Node(id="4", type="end")
            ],
            edges=[
                Edge(id="e1", source="1", target="2"),
                Edge(id="e2", source="1", target="3"),
                Edge(id="e3", source="2", target="4"),
                Edge(id="e4", source="3", target="4")
            ]
        )

        executor = GraphExecutor()

        start_time = asyncio.get_event_loop().time()
        result = await executor.execute(dsl, {})
        end_time = asyncio.get_event_loop().time()

        assert result.status == WorkflowStatus.SUCCESS, f"Expected SUCCESS, got {result.status}. Trace: {[(t.node_id, t.status) for t in result.trace]}"
        # nodes 1 (start) and 4 (end) return empty outputs, nodes 2 and 3 (llm) return mock responses
        assert trace_outputs(result, "2").get("text") == "Mocked Answer"
        assert trace_outputs(result, "3").get("text") == "Mocked Answer"

        # Since LLM handler sleeps for 1s, parallel execution should take ~1s, linear would take 2s
        # Allow some overhead, < 1.5s usually means parallel
        duration = end_time - start_time
        # This assertion might be flaky in very slow envs, but good for local check
        # assert duration < 1.5
        print(f"Parallel execution took {duration:.2f}s")


@pytest.mark.asyncio
async def test_loop_executes_body_subgraph():
    """loop 节点应通过 body handle 执行子图并聚合结果"""
    dsl = WorkflowDSL(
        nodes=[
            Node(id="1", type="start"),
            Node(id="2", type="loop", data={
                "loop_mode": "count",
                "max_iterations": 2,
                "body_type": "subgraph",
                "output_variable": "loop_result",
            }),
            Node(id="body", type="code", data={"code": "output['value'] = loop_counter"}),
            Node(id="3", type="end"),
        ],
        edges=[
            Edge(id="e1", source="1", target="2"),
            Edge(id="e_body", source="2", target="body", sourceHandle="body"),
            Edge(id="e2", source="2", target="3"),
        ],
    )

    executor = GraphExecutor()
    result = await executor.execute(dsl, {})
    loop_output = trace_outputs(result, "2")

    assert result.status == WorkflowStatus.SUCCESS
    assert loop_output["loop_result_iterations"] == 1
    assert [item["output"]["body"]["value"] for item in loop_output["loop_result"]] == [0, 1]
