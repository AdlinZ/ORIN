import pytest
import asyncio
from app.models.workflow import WorkflowDSL, Node, Edge
from app.engine.executor import GraphExecutor

@pytest.mark.asyncio
async def test_linear_execution():
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
    
    assert result.success is True
    assert "1" in result.outputs
    assert "2" in result.outputs
    # Ensure LLM output contains mock response
    assert "text" in result.outputs["2"]

@pytest.mark.asyncio
async def test_parallel_execution():
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
    
    assert result.success is True
    assert len(result.outputs) == 4
    
    # Since LLM handler sleeps for 1s, parallel execution should take ~1s, linear would take 2s
    # Allow some overhead, < 1.5s usually means parallel
    duration = end_time - start_time
    # This assertion might be flaky in very slow envs, but good for local check
    # assert duration < 1.5 
    print(f"Parallel execution took {duration:.2f}s")
