from typing import Any, Dict, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class VariableAssignerNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Assigns a value to a target variable in the execution context.
        """
        target_var = node.data.get("target_variable")
        value = node.data.get("value")
        write_mode = node.data.get("write_mode", "overwrite")

        if target_var:
            # Note: In the GraphExecutor, 'context' is actually {**ctx, **node_outputs}
            # The executor itself manages the persistent context.
            # Here we just return the result, and GraphExecutor writes it to 'node_outputs'
            # and potentially flattens standard variables if needed.
            
            # For now, we return the assignment result
            return NodeExecutionOutput(outputs={
                "assigned_variable": target_var,
                "value": value,
                "mode": write_mode
            })
        
        return NodeExecutionOutput(outputs={"status": "skipped", "reason": "No target variable defined"})
