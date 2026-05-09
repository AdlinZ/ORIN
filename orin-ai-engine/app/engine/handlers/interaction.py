from typing import Any, Dict, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.standard import resolve_answer_value

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class AnswerNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Collects a final chat answer from a configured expression or upstream text.
        """
        return NodeExecutionOutput(outputs={
            "answer": resolve_answer_value(node.data or {}, context)
        })
