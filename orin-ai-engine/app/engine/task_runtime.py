"""
Unified task runtime for collaboration execution.

This module is the single execution kernel used by both:
- MQ worker task consumption
- Legacy collaboration executor compatibility layer
"""

import asyncio
import json
import logging
from typing import Any, Dict, Optional

import httpx

from app.core.config import settings
from app.engine.executor import GraphExecutor
from app.engine.handlers.llm import RealLLMNodeHandler
from app.engine.mcp_client_manager import mcp_client_manager
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
        if preferred_agent_id and str(preferred_agent_id).startswith("ephemeral:"):
            ephemeral_agents = context.get("ephemeral_agents") if isinstance(context.get("ephemeral_agents"), list) else []
            ephemeral_agent = next(
                (agent for agent in ephemeral_agents if isinstance(agent, dict) and str(agent.get("id")) == str(preferred_agent_id)),
                None,
            )
            if not ephemeral_agent:
                raise ValueError(f"Ephemeral agent not found: {preferred_agent_id}")
            system_prompt = str(ephemeral_agent.get("system_prompt") or "").strip()
            backend_base = (settings.ORIN_BACKEND_URL or "http://localhost:8080").rstrip("/")
            url = f"{backend_base}/api/playground/llm"
            payload: Dict[str, Any] = {
                "system_prompt": system_prompt or f"You are {expected_role}.",
                "user_input": description,
                "model": ephemeral_agent.get("model", context.get("model")),
                "temperature": ephemeral_agent.get("temperature", 0.45),
                "max_tokens": _bounded_int(ephemeral_agent.get("max_tokens"), agent_max_tokens),
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
                error = data.get("error")
                if isinstance(error, str) and error.strip():
                    raise RuntimeError(f"Ephemeral agent {ephemeral_agent.get('name') or preferred_agent_id} failed: {error.strip()}")
                text = data.get("text")
                if isinstance(text, str) and text.strip():
                    return text.strip()
            return "No output"

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
            task_id = payload.get("taskId") or payload.get("task_id")
            instance_id = (
                payload.get("workflowInstanceId")
                or payload.get("workflow_instance_id")
                or payload.get("instanceId")
            )
            if task_id is not None or instance_id is not None:
                parts = []
                if task_id is not None:
                    parts.append(f"taskId={task_id}")
                if instance_id is not None:
                    parts.append(f"workflowInstanceId={instance_id}")
                return "Workflow enqueued: " + ", ".join(parts)
            return json.dumps(payload, ensure_ascii=False)

        return str(payload)

    async def execute_mcp_task(
        self,
        *,
        package_id: str,
        sub_task_id: str,
        trace_id: Optional[str],
        description: str,
        input_data_raw: Optional[str],
        context: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if input_data_raw:
            try:
                parsed = json.loads(input_data_raw)
                if isinstance(parsed, dict):
                    payload.update(parsed)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid MCP input_data JSON for task: {sub_task_id}") from exc
        if context:
            payload = {**context, **payload}

        service_id = payload.get("mcpServiceId") or payload.get("serviceId")
        tool_name = payload.get("toolName") or payload.get("name")
        arguments = payload.get("arguments") or payload.get("args") or {}
        if service_id is None:
            raise ValueError(f"No MCP serviceId found for MCP task: {sub_task_id}")
        if not isinstance(arguments, dict):
            raise ValueError("MCP arguments must be an object")

        started = asyncio.get_running_loop().time()
        result = await mcp_client_manager.call_tool(int(service_id), str(tool_name), arguments)
        duration_ms = int((asyncio.get_running_loop().time() - started) * 1000)
        return {
            "text": self._stringify_mcp_result(result),
            "toolTrace": {
                "type": "MCP_TOOL_CALL",
                "kbId": f"mcp:{service_id}:{tool_name}",
                "message": description or f"MCP tool call: {tool_name}",
                "status": "success",
                "durationMs": duration_ms,
                "detail": {
                    "tool_type": "mcp",
                    "packageId": package_id,
                    "subTaskId": sub_task_id,
                    "traceId": trace_id,
                    "serviceId": service_id,
                    "toolName": tool_name,
                },
            },
        }

    def _stringify_mcp_result(self, result: Dict[str, Any]) -> str:
        content = result.get("content") if isinstance(result, dict) else None
        if isinstance(content, list):
            parts = []
            for item in content:
                if isinstance(item, dict) and isinstance(item.get("text"), str):
                    parts.append(item["text"])
            if parts:
                return "\n".join(parts)
        return json.dumps(result, ensure_ascii=False)
