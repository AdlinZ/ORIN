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
from app.core.trace_httpx import httpx_client
from app.engine.executor import GraphExecutor
from app.engine.handlers.llm import RealLLMNodeHandler
from app.engine.mcp_client_manager import mcp_client_manager
from app.models.workflow import Node, NodeExecutionOutput

logger = logging.getLogger(__name__)


def _resolve_backend_authorization(context: Optional[Dict[str, Any]] = None) -> str:
    """Resolve authorization for protected backend calls."""
    context = context or {}
    authorization = context.get("_authorization") or getattr(settings, "BACKEND_AUTHORIZATION", None)
    if isinstance(authorization, str) and authorization.strip():
        return authorization.strip()
    raise RuntimeError(
        "Backend authorization is required for workflow task execution. "
        "Provide context['_authorization'] or set ORIN_BACKEND_AUTHORIZATION."
    )


def _bounded_int(value: Any, fallback: int, minimum: int = 256, maximum: int = 16000) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = fallback
    return max(minimum, min(maximum, parsed))


def _build_headers(materialized_secrets: Dict[str, str]) -> Dict[str, str]:
    """Build HTTP headers from materialized secrets (ADR-002 /secret-bind).

    Keys ending with ``_API_KEY`` are also sent as ``Authorization: Bearer <value>``.
    """
    headers: Dict[str, str] = {"Content-Type": "application/json"}
    for inject_as, value in materialized_secrets.items():
        if value:
            headers[f"X-ORIN-Secret-{inject_as}"] = value
            if inject_as.upper().endswith("_API_KEY"):
                headers["Authorization"] = f"Bearer {value}"
    return headers


def _decode_config_snapshot(context: Dict[str, Any]) -> Dict[str, Any]:
    """Decode a Runner config snapshot, including H2 JSON-string wrappers."""
    raw_snapshot = context.get("config_snapshot")
    if not isinstance(raw_snapshot, str) or not raw_snapshot.strip():
        return {}
    snapshot: Any = raw_snapshot
    # A JSON document stored through Hibernate as ``String`` can come back
    # quoted on H2.  The AgentVersion -> Run copy may add a second layer.
    # Unwrap only a small, bounded number of JSON-string layers so malformed
    # or ordinary provider snapshots still follow the real execution path.
    for _ in range(8):
        if not isinstance(snapshot, str) or not snapshot.strip():
            break
        try:
            decoded = json.loads(snapshot)
        except (TypeError, ValueError):
            return {}
        if decoded == snapshot:
            return {}
        snapshot = decoded
    return snapshot if isinstance(snapshot, dict) else {}


def _deterministic_runner_output(description: str, context: Dict[str, Any]) -> Optional[str]:
    """Return a stable output only for the explicit F03 smoke-test provider.

    ``ORIN_DETERMINISTIC`` is intentionally opt-in and is not a fallback for a
    missing provider.  It lets the Runner E2E path exercise the real
    TaskRuntime without introducing an external LLM credential into CI.
    """
    snapshot = _decode_config_snapshot(context)
    model = snapshot.get("model") if isinstance(snapshot, dict) else None
    if not isinstance(model, dict) or model.get("providerType") != "ORIN_DETERMINISTIC":
        return None
    return f"ORIN deterministic runner result: {description}"


class TaskRuntime:
    """Single execution kernel for collaboration subtasks."""

    def __init__(self, executor: Optional[GraphExecutor] = None):
        self.executor = executor or GraphExecutor()

    async def execute_agent_task(
        self,
        description: str,
        expected_role: str,
        context: Optional[Dict[str, Any]] = None,
        materialized_secrets: Optional[Dict[str, str]] = None,
    ) -> str:
        """Execute one agent-style (LLM) subtask.

        When ``materialized_secrets`` is provided (from ADR-002 /secret-bind),
        inject-as keys are added as HTTP headers on outbound calls to the Java
        backend.  Keys ending with ``_API_KEY`` are also sent as
        ``Authorization: Bearer <value>``.
        """
        context = context or {}
        materialized_secrets = materialized_secrets or {}

        deterministic_output = _deterministic_runner_output(description, context)
        if deterministic_output is not None:
            return deterministic_output

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
            headers = _build_headers(materialized_secrets)
            # trace_id 由 `app.core.trace_httpx.httpx_client` 注入 W3C
            # `traceparent` header，无需手动设 `X-Trace-Id` legacy。

            timeout_seconds = float(getattr(settings, "PLAYGROUND_AGENT_CHAT_TIMEOUT_SECONDS", 90.0))
            async with httpx_client(timeout=timeout_seconds) as client:
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
            headers = _build_headers(materialized_secrets)
            # trace_id 由 `app.core.trace_httpx.httpx_client` 注入 W3C
            # `traceparent` header，无需手动设 `X-Trace-Id` legacy。

            timeout_seconds = float(getattr(settings, "PLAYGROUND_AGENT_CHAT_TIMEOUT_SECONDS", 90.0))
            async with httpx_client(timeout=timeout_seconds) as client:
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

        snapshot = _decode_config_snapshot(context)
        snapshot_model = snapshot.get("model") if isinstance(snapshot.get("model"), dict) else {}
        snapshot_config = snapshot.get("config") if isinstance(snapshot.get("config"), dict) else {}
        provider_type = str(snapshot_model.get("providerType") or "").strip().upper()

        node_data: Dict[str, Any] = {
            "prompt": description,
            "model": context.get("model", "default"),
            "expectedRole": expected_role,
            "max_tokens": agent_max_tokens,
        }
        if provider_type in {"OLLAMA", "LOCAL_OLLAMA"}:
            system_prompt = str(snapshot_config.get("systemPrompt") or "").strip()
            node_data.update({
                "prompt": (
                    f"{system_prompt}\n\n用户请求：\n{description}"
                    if system_prompt else description
                ),
                "model": snapshot_model.get("modelName") or "qwen2.5:0.5b",
                "temperature": snapshot_config.get("temperature", 0.7),
                "max_tokens": _bounded_int(
                    snapshot_config.get("maxTokens"),
                    agent_max_tokens,
                ),
                # Ollama exposes an OpenAI-compatible API and accepts any
                # non-empty local placeholder as the SDK api_key.
                "api_key": "ollama",
                "base_url": (
                    snapshot_model.get("baseUrl")
                    or context.get("ollama_base_url")
                    or "http://127.0.0.1:11434/v1"
                ),
            })

        node = Node(
            id="runtime_single_task",
            type="llm",
            data=node_data,
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
        headers["Authorization"] = _resolve_backend_authorization(context)
        # trace_id 由 `app.core.trace_httpx.httpx_client` 注入 W3C
        # `traceparent` header，无需手动设 `X-Trace-Id` legacy。

        params = {"triggeredBy": triggered_by}

        async with httpx_client(timeout=timeout_seconds) as client:
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
