from abc import ABC, abstractmethod
from typing import Any, Dict, Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class BaseNodeHandler(ABC):
    """
    Base class for all node handlers.

    Args:
        executor: Optional reference to the GraphExecutor for executing subgraphs
                 (used by iteration/loop nodes to execute their body nodes)
    """

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        self._executor = executor

    @property
    def executor(self) -> Optional["GraphExecutor"]:
        """Get the executor instance"""
        return self._executor

    @abstractmethod
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Execute the node logic.
        :param node: The node definition
        :param context: The global execution context (including previous node outputs)
        :return: The output of this node execution, including outputs and selected handle
        """
        pass
