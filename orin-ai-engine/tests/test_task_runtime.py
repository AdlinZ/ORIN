"""
TaskRuntime unit tests.

Run:
    cd orin-ai-engine && python -m pytest tests/test_task_runtime.py -v
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.engine.task_runtime import TaskRuntime


class TestTaskRuntimeAgent:
    @pytest.mark.asyncio
    async def test_execute_agent_task_returns_text_output(self):
        runtime = TaskRuntime()
        mock_output = MagicMock()
        mock_output.outputs = {"text": "ok"}

        with patch("app.engine.task_runtime.RealLLMNodeHandler") as mock_handler_cls:
            mock_handler = mock_handler_cls.return_value
            mock_handler.run = AsyncMock(return_value=mock_output)

            result = await runtime.execute_agent_task(
                description="hello",
                expected_role="SPECIALIST",
                context={"model": "gpt-test"},
            )

        assert result == "ok"
        mock_handler.run.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_execute_agent_task_returns_no_output_when_empty(self):
        runtime = TaskRuntime()
        mock_output = MagicMock()
        mock_output.outputs = {}

        with patch("app.engine.task_runtime.RealLLMNodeHandler") as mock_handler_cls:
            mock_handler = mock_handler_cls.return_value
            mock_handler.run = AsyncMock(return_value=mock_output)

            result = await runtime.execute_agent_task(
                description="hello",
                expected_role="SPECIALIST",
                context={},
            )

        assert result == "No output"


class TestTaskRuntimeWorkflow:
    @pytest.mark.asyncio
    async def test_execute_workflow_task_calls_backend(self):
        runtime = TaskRuntime()

        mock_response = MagicMock()
        mock_response.json.return_value = {"instanceId": 123}
        mock_response.raise_for_status.return_value = None

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        mock_async_client = AsyncMock()
        mock_async_client.__aenter__.return_value = mock_client
        mock_async_client.__aexit__.return_value = None

        with patch("app.engine.task_runtime.settings.ORIN_BACKEND_URL", "http://backend.test"), \
             patch("app.engine.task_runtime.httpx.AsyncClient", return_value=mock_async_client):
            result = await runtime.execute_workflow_task(
                package_id="pkg-1",
                sub_task_id="sub-1",
                trace_id="trace-1",
                timeout_millis=60000,
                description="run",
                input_data_raw='{"workflowId": 42, "inputs": {"k": "v"}}',
                context={},
            )

        assert result == "Workflow executed: instanceId=123"
        post_call = mock_client.post.await_args
        assert post_call.args[0] == "http://backend.test/api/workflows/42/execute"
        assert post_call.kwargs["params"] == {"triggeredBy": "collab_mq_worker"}
        assert post_call.kwargs["json"]["k"] == "v"

    @pytest.mark.asyncio
    async def test_execute_workflow_task_requires_workflow_id(self):
        runtime = TaskRuntime()

        with pytest.raises(ValueError, match="No workflowId found"):
            await runtime.execute_workflow_task(
                package_id="pkg-1",
                sub_task_id="sub-1",
                trace_id=None,
                timeout_millis=60000,
                description="run",
                input_data_raw='{"inputs": {"k": "v"}}',
                context={},
            )
