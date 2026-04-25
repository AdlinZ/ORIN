"""
Unified task runtime for collaboration execution.

This module is the single execution kernel used by both:
- MQ worker task consumption
- Legacy collaboration executor compatibility layer
"""

import json
import logging
from typing import Any, Dict, Optional

import httpx

from app.core.config import settings
from app.engine.executor import GraphExecutor
from app.engine.handlers.llm import RealLLMNodeHandler
from app.models.workflow import Node, NodeExecutionOutput

logger = logging.getLogger(__name__)


def _bounded_int(value: Any, fallback: int, minimum: int = 256, maximum: int = 16000) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = fallback
    return max(minimum, min(maximum, parsed))


class TaskRuntime:
    """Single execution kernel for collaboration subtasks."""

    def __init__(self, executor: Optional[GraphExecutor] = None):
        self.executor = executor or GraphExecutor()

    async def execute_agent_task(
        self,
        description: str,
        expected_role: str,
        context: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Execute one agent-style (LLM) subtask."""
        context = context or {}

        # Prefer ORIN native agent runtime when a specific agent is provided.
        # This path avoids requiring OPENAI_API_KEY in ai-engine.
        preferred_agent_id = context.get("preferred_agent_id") or context.get("preferredAgentId")
        agent_max_tokens = _bounded_int(
            context.get("agent_max_tokens") or context.get("agentMaxTokens"),
            int(getattr(settings, "PLAYGROUND_AGENT_MAX_TOKENS", 1200)),
        )
        if preferred_agent_id:
            backend_base = (settings.ORIN_BACKEND_URL or "http://localhost:8080").rstrip("/")
            url = f"{backend_base}/api/v1/agents/{preferred_agent_id}/chat"
            payload: Dict[str, Any] = {
                "message": description,
                # Keep subtask outputs bounded to reduce long-tail latency in collaborative runs.
                "max_tokens": agent_max_tokens,
            }
            trace_id = context.get("_trace_id")
            headers = {"Content-Type": "application/json"}
            if trace_id:
                headers["X-Trace-Id"] = str(trace_id)

            timeout_seconds = float(getattr(settings, "PLAYGROUND_AGENT_CHAT_TIMEOUT_SECONDS", 90.0))
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()
                data = response.json() if response.headers.get("content-type", "").startswith("application/json") else response.text

            if isinstance(data, str):
                return data
            if isinstance(data, dict):
                for key in ("answer", "text", "content", "response", "message"):
                    value = data.get(key)
                    if isinstance(value, str) and value.strip():
                        return value.strip()
            return str(data)

        node = Node(
            id="runtime_single_task",
            type="llm",
            data={
                "prompt": description,
                "model": context.get("model", "default"),
                "expectedRole": expected_role,
                "max_tokens": agent_max_tokens,
            },
        )

        llm_handler = RealLLMNodeHandler(executor=self.executor)
        output: NodeExecutionOutput = await llm_handler.run(node, context)

        if output and output.outputs:
            result = output.outputs.get("text", "")
            return str(result) if result else "No result"
        return "No output"

    async def execute_workflow_task(
        self,
        *,
        package_id: str,
        sub_task_id: str,
        trace_id: Optional[str],
        timeout_millis: Optional[int],
        description: str,
        input_data_raw: Optional[str],
        context: Optional[Dict[str, Any]] = None,
        triggered_by: str = "collab_mq_worker",
    ) -> str:
        """Execute one workflow-style subtask by calling backend workflow API."""
        context = context or {}

        workflow_id = None
        workflow_inputs: Dict[str, Any] = {
            "description": description,
            "packageId": package_id,
            "subTaskId": sub_task_id,
        }

        if input_data_raw:
            try:
                input_data = json.loads(input_data_raw)
                if isinstance(input_data, dict):
                    workflow_id = input_data.get("workflowId")
                    input_inputs = input_data.get("inputs")
                    if isinstance(input_inputs, dict):
                        workflow_inputs.update(input_inputs)
            except json.JSONDecodeError:
                logger.warning("Invalid input_data JSON for workflow task: %s", sub_task_id)

        if workflow_id is None:
            workflow_id = context.get("workflowId")

        if workflow_id is None:
            raise ValueError(f"No workflowId found for workflow task: {sub_task_id}")

        try:
            workflow_id = int(workflow_id)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"Invalid workflowId: {workflow_id}") from exc

        backend_base = (settings.ORIN_BACKEND_URL or "http://localhost:8080").rstrip("/")
        url = f"{backend_base}/api/workflows/{workflow_id}/execute"
        timeout_seconds = max(10.0, (timeout_millis or 300000) / 1000.0)

        headers = {"Content-Type": "application/json"}
        if trace_id:
            headers["X-Trace-Id"] = trace_id

        params = {"triggeredBy": triggered_by}

        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(url, json=workflow_inputs, params=params, headers=headers)
            response.raise_for_status()
            payload = response.json()

        if isinstance(payload, dict):
            instance_id = payload.get("instanceId")
            if instance_id is not None:
                return f"Workflow executed: instanceId={instance_id}"
            return json.dumps(payload, ensure_ascii=False)

        return str(payload)
