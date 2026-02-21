import asyncio
from typing import Any, Dict
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

class StartNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Start node usually just passes initial inputs or does nothing
        return NodeExecutionOutput(outputs=context.get("inputs", {}))

class EndNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # End node collects final outputs.
        # It might pick specific variables from context based on configuration
        return NodeExecutionOutput(outputs={}) # For now, return empty, the engine collects context

class LLMNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Mock LLM Execution
        print(f"Mocking LLM Call for node {node.id}")
        await asyncio.sleep(1) # Simulate network latency
        
        prompt_template = node.data.get("prompt", "")
        # Very simple variable substitution (mock)
        # In real impl, use Jinja2
        prompt = prompt_template
        
        return NodeExecutionOutput(outputs={
            "text": f"Mock response for prompt: {prompt}",
            "tokens": 10
        })
