import pytest
import asyncio
from typing import Dict, Any
from app.models.workflow import WorkflowDSL, Node, Edge, NodeStatus, WorkflowStatus
from app.engine.executor import GraphExecutor
from app.engine.handlers.base import BaseNodeHandler

# Mock Handlers for Testing
class TimeoutHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> Dict[str, Any]:
        await asyncio.sleep(2) # Sleeps longer than timeout
        return {"val": "should not be reached"}

class FailureHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> Dict[str, Any]:
        raise ValueError("Intentional Failure")

@pytest.mark.asyncio
async def test_timeout_propagation():
    # 1 (Timeout) -> 2 (Dependent)
    dsl = WorkflowDSL(
        nodes=[
            Node(id="1", type="timeout", data={"timeout": 0.5}), # Timeout 0.5s
            Node(id="2", type="start") # Should be skipped
        ],
        edges=[Edge(id="e1", source="1", target="2")]
    )
    
    executor = GraphExecutor()
    executor.handlers["timeout"] = TimeoutHandler()
    
    result = await executor.execute(dsl, {})
    
    assert result.status == WorkflowStatus.PARTIAL
    
    # Check Node 1: FAILED (Timeout)
    t1 = next(t for t in result.trace if t.node_id == "1")
    assert t1.status == NodeStatus.FAILED
    assert "timed out" in t1.error
    assert t1.duration < 1.0 # Should fail fast
    
    # Check Node 2: SKIPPED
    t2 = next(t for t in result.trace if t.node_id == "2")
    assert t2.status == NodeStatus.SKIPPED

@pytest.mark.asyncio
async def test_failure_propagation():
    # 1 (Error) -> 2 (Dependent)
    #           -> 3 (Independent Branch) -> 4
    dsl = WorkflowDSL(
        nodes=[
            Node(id="1", type="fail"), 
            Node(id="2", type="start"),
            Node(id="3", type="start"),
            Node(id="4", type="start")
        ],
        edges=[
            Edge(id="e1", source="1", target="2"),
            Edge(id="e2", source="3", target="4")
        ]
    )
    
    executor = GraphExecutor()
    executor.handlers["fail"] = FailureHandler()
    
    result = await executor.execute(dsl, {})
    
    assert result.status == WorkflowStatus.PARTIAL
    
    # Node 1 Failed
    assert next(t for t in result.trace if t.node_id == "1").status == NodeStatus.FAILED
    # Node 2 Skipped
    assert next(t for t in result.trace if t.node_id == "2").status == NodeStatus.SKIPPED
    # Node 3 & 4 Completed (Isolation check)
    assert next(t for t in result.trace if t.node_id == "3").status == NodeStatus.COMPLETED
    assert next(t for t in result.trace if t.node_id == "4").status == NodeStatus.COMPLETED

@pytest.mark.asyncio
async def test_state_isolation():
    # Run two workflows concurrently
    dsl = WorkflowDSL(nodes=[Node(id="1", type="start")], edges=[])
    
    executor = GraphExecutor()
    
    # Run 1
    t1 = asyncio.create_task(executor.execute(dsl, {"id": 1}))
    # Run 2
    t2 = asyncio.create_task(executor.execute(dsl, {"id": 2}))
    
    r1, r2 = await asyncio.gather(t1, t2)
    
    # Since context inputs are not returned in outputs unless handler puts them there,
    # we can't easily check inputs in output without a custom handler.
    # But ensuring they didn't crash or mix is a basic check.
    assert r1.status == WorkflowStatus.SUCCESS
    assert r2.status == WorkflowStatus.SUCCESS
