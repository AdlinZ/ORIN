"""
Legacy compatibility shim for collaboration execution.

Architecture note:
- Collaboration orchestration is owned by LangGraph + MQ.
- This module is kept only for backward-compatible imports.
- Do not add orchestration logic here.
"""

from typing import Any, Dict, Optional

from app.engine.task_runtime import TaskRuntime


class CollaborationExecutor:
    """Compatibility wrapper delegating to the unified TaskRuntime."""

    def __init__(self):
        self.task_runtime = TaskRuntime()

    async def execute_single_task(
        self,
        description: str,
        expected_role: str,
        context: Optional[Dict[str, Any]] = None,
    ) -> str:
        context = context or {}
        try:
            return await self.task_runtime.execute_agent_task(
                description=description,
                expected_role=expected_role,
                context=context,
            )
        except Exception as exc:
            return f"Task failed: {str(exc)}"

    async def execute_collaboration(self, *args: Any, **kwargs: Any):
        raise NotImplementedError(
            "execute_collaboration is removed. Use app.engine.collaboration_langgraph.run_collaboration instead."
        )


collaboration_executor = CollaborationExecutor()
