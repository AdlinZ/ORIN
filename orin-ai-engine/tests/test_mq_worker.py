"""
F2.5 MQ Worker Workflow Smoke Test

测试目标：验证 MQ worker 在 WORKFLOW 策略下的最小真实链路
- 能正确调用后端 workflow execute 接口
- 缺少 workflowId 时给出明确错误
- 执行结果能封装为回写 payload

运行方式：
    cd orin-ai-engine && python -m pytest tests/test_mq_worker.py -v
"""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.engine.mq_worker import CollabMQWorker, CollabTaskMessage, CollabTaskResult


class TestMqWorkerWorkflowSmoke:
    @pytest.mark.asyncio
    async def test_execute_workflow_calls_backend_execute_api(self):
        worker = CollabMQWorker()
        task = CollabTaskMessage(
            package_id="pkg-smoke-001",
            sub_task_id="sub-workflow-001",
            trace_id="trace-smoke-001",
            attempt=0,
            collaboration_mode="PARALLEL",
            expected_role="WORKFLOW",
            description="run workflow",
            input_data=json.dumps({
                "workflowId": 42,
                "inputs": {
                    "topic": "MQ workflow smoke"
                }
            }),
            execution_strategy="WORKFLOW",
        )

        mock_response = MagicMock()
        mock_response.json.return_value = {"instanceId": 98765}
        mock_response.raise_for_status.return_value = None

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        mock_async_client = AsyncMock()
        mock_async_client.__aenter__.return_value = mock_client
        mock_async_client.__aexit__.return_value = None

        with patch("app.engine.task_runtime.settings.ORIN_BACKEND_URL", "http://backend.test"), \
             patch("app.engine.task_runtime.httpx.AsyncClient", return_value=mock_async_client):
            result = await worker._execute_workflow(task, {})

        assert result == "Workflow executed: instanceId=98765"
        mock_client.post.assert_awaited_once()

        call_args = mock_client.post.await_args
        assert call_args.args[0] == "http://backend.test/api/workflows/42/execute"
        assert call_args.kwargs["params"] == {"triggeredBy": "collab_mq_worker"}
        assert call_args.kwargs["headers"]["X-Trace-Id"] == "trace-smoke-001"
        assert call_args.kwargs["json"]["description"] == "run workflow"
        assert call_args.kwargs["json"]["packageId"] == "pkg-smoke-001"
        assert call_args.kwargs["json"]["subTaskId"] == "sub-workflow-001"
        assert call_args.kwargs["json"]["topic"] == "MQ workflow smoke"

    @pytest.mark.asyncio
    async def test_execute_workflow_requires_workflow_id(self):
        worker = CollabMQWorker()
        task = CollabTaskMessage(
            package_id="pkg-smoke-002",
            sub_task_id="sub-workflow-002",
            trace_id="trace-smoke-002",
            attempt=0,
            collaboration_mode="PARALLEL",
            expected_role="WORKFLOW",
            description="run workflow without id",
            input_data=json.dumps({"inputs": {"topic": "missing id"}}),
            execution_strategy="WORKFLOW",
        )

        with pytest.raises(ValueError, match="No workflowId found"):
            await worker._execute_workflow(task, {})

    @pytest.mark.asyncio
    async def test_execute_task_wraps_workflow_result_for_callback(self):
        worker = CollabMQWorker()
        task = CollabTaskMessage(
            package_id="pkg-smoke-003",
            sub_task_id="sub-workflow-003",
            trace_id="trace-smoke-003",
            attempt=1,
            collaboration_mode="PARALLEL",
            expected_role="WORKFLOW",
            description="wrap workflow result",
            input_data=json.dumps({"workflowId": 99}),
            execution_strategy="WORKFLOW",
        )

        with patch.object(worker, "_execute_workflow", AsyncMock(return_value="Workflow executed: instanceId=111")):
            result = await worker._execute_task(task)

        assert isinstance(result, CollabTaskResult)
        assert result.status == "COMPLETED"
        assert result.package_id == "pkg-smoke-003"
        assert result.sub_task_id == "sub-workflow-003"
        assert result.result == "Workflow executed: instanceId=111"
        assert result.executed_by == "mq_worker:WORKFLOW"
        assert result.metadata["expectedRole"] == "WORKFLOW"

    @pytest.mark.asyncio
    async def test_write_result_publishes_json_payload(self):
        worker = CollabMQWorker()
        exchange = AsyncMock()
        channel = AsyncMock()
        channel.get_exchange = AsyncMock(return_value=exchange)
        worker.channel = channel

        result = CollabTaskResult(
            package_id="pkg-smoke-004",
            sub_task_id="sub-workflow-004",
            trace_id="trace-smoke-004",
            attempt=0,
            status="COMPLETED",
            result="Workflow executed: instanceId=222",
            metadata={"expectedRole": "WORKFLOW"},
        )

        await worker._write_result(result)

        exchange.publish.assert_awaited_once()
        publish_call = exchange.publish.await_args
        message = publish_call.args[0]
        routing_key = publish_call.kwargs["routing_key"]

        payload = json.loads(message.body.decode())
        assert payload["packageId"] == "pkg-smoke-004"
        assert payload["subTaskId"] == "sub-workflow-004"
        assert payload["status"] == "COMPLETED"
        assert payload["result"] == "Workflow executed: instanceId=222"
        assert routing_key == worker.reply_routing_key
