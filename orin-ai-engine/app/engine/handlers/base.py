from abc import ABC, abstractmethod
from typing import Any, Dict
from app.models.workflow import Node, NodeExecutionOutput

class BaseNodeHandler(ABC):
    @abstractmethod
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Execute the node logic.
        :param node: The node definition
        :param context: The global execution context (including previous node outputs)
        :return: The output of this node execution, including outputs and selected handle
        """
        pass
