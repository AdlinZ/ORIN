import asyncio
from typing import Any, Dict, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class StartNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Start node usually just passes initial inputs or does nothing
        return NodeExecutionOutput(outputs=context.get("inputs", {}))

class EndNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # End node collects final outputs.
        # It might pick specific variables from context based on configuration
        return NodeExecutionOutput(outputs={}) # For now, return empty, the engine collects context

