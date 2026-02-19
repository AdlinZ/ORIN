from abc import ABC, abstractmethod
from typing import Any, Dict
from app.models.workflow import Node

class BaseNodeHandler(ABC):
    @abstractmethod
    async def run(self, node: Node, context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Execute the node logic.
        :param node: The node definition
        :param context: The global execution context (including previous node outputs)
        :return: The output of this node execution
        """
        pass
