"""
MQ Worker for Collaboration Subtask Execution
Consumes tasks from RabbitMQ and writes results back
"""
import asyncio
import json
import logging
from dataclasses import asdict
from datetime import datetime
from typing import Optional, Dict, Any

import aio_pika
import httpx
from aio_pika import Message, DeliveryMode
from aio_pika.abc import AbstractIncomingMessage

from app.core.config import settings
from app.engine.collaboration_executor import collaboration_executor
from app.core.shared_memory import shared_memory

logger = logging.getLogger(__name__)


class CollabTaskMessage:
    """Collaboration task message from backend"""
    def __init__(
        self,
        package_id: str,
        sub_task_id: str,
        trace_id: Optional[str],
        attempt: int,
        collaboration_mode: str,
        expected_role: str,
        description: str,
        input_data: Optional[str] = None,
        depends_on: Optional[list] = None,
        reply_to: Optional[str] = None,
        correlation_id: Optional[str] = None,
        context_snapshot: Optional[dict] = None,
        max_retries: Optional[int] = 3,
        timeout_millis: Optional[int] = 300000,
        execution_strategy: Optional[str] = "AGENT",
        enqueued_at: Optional[int] = None,
    ):
        self.package_id = package_id
        self.sub_task_id = sub_task_id
        self.trace_id = trace_id
        self.attempt = attempt
        self.collaboration_mode = collaboration_mode
        self.expected_role = expected_role
        self.description = description
        self.input_data = input_data
        self.depends_on = depends_on or []
        self.reply_to = reply_to
        self.correlation_id = correlation_id
        self.context_snapshot = context_snapshot or {}
        self.max_retries = max_retries
        self.timeout_millis = timeout_millis
        self.execution_strategy = execution_strategy
        self.enqueued_at = enqueued_at

    @classmethod
    def from_dict(cls, data: dict) -> "CollabTaskMessage":
        return cls(
            package_id=data.get("packageId", ""),
            sub_task_id=data.get("subTaskId", ""),
            trace_id=data.get("traceId"),
            attempt=data.get("attempt", 0),
            collaboration_mode=data.get("collaborationMode", "PARALLEL"),
            expected_role=data.get("expectedRole", "SPECIALIST"),
            description=data.get("description", ""),
            input_data=data.get("inputData"),
            depends_on=data.get("dependsOn"),
            reply_to=data.get("replyTo"),
            correlation_id=data.get("correlationId"),
            context_snapshot=data.get("contextSnapshot", {}),
            max_retries=data.get("maxRetries", 3),
            timeout_millis=data.get("timeoutMillis", 300000),
            execution_strategy=data.get("executionStrategy", "AGENT"),
            enqueued_at=data.get("enqueuedAt"),
        )


class CollabTaskResult:
    """Collaboration task result to send back to backend"""
    def __init__(
        self,
        package_id: str,
        sub_task_id: str,
        trace_id: Optional[str],
        attempt: int,
        status: str,
        result: Optional[str] = None,
        error_message: Optional[str] = None,
        started_at: Optional[int] = None,
        completed_at: Optional[int] = None,
        latency_ms: Optional[int] = None,
        executed_by: Optional[str] = None,
        metadata: Optional[dict] = None,
    ):
        self.package_id = package_id
        self.sub_task_id = sub_task_id
        self.trace_id = trace_id
        self.attempt = attempt
        self.status = status
        self.result = result
        self.error_message = error_message
        self.started_at = started_at
        self.completed_at = completed_at
        self.latency_ms = latency_ms
        self.executed_by = executed_by
        self.metadata = metadata or {}

    def to_dict(self) -> dict:
        return {
            "packageId": self.package_id,
            "subTaskId": self.sub_task_id,
            "traceId": self.trace_id,
            "attempt": self.attempt,
            "status": self.status,
            "result": self.result,
            "errorMessage": self.error_message,
            "startedAt": self.started_at,
            "completedAt": self.completed_at,
            "latencyMs": self.latency_ms,
            "executedBy": self.executed_by,
            "metadata": self.metadata,
        }


class CollabMQWorker:
    """Async RabbitMQ consumer for collaboration subtasks"""

    def __init__(self):
        self.connection: Optional[aio_pika.RobustConnection] = None
        self.channel: Optional[aio_pika.Channel] = None
        self._running = False
        self._redis = None

        # Configuration from settings
        self.rabbitmq_url = getattr(settings, "RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
        self.queue_name = getattr(settings, "COLLAB_QUEUE_NAME", "collaboration-task-queue")
        self.result_queue_name = getattr(settings, "COLLAB_RESULT_QUEUE_NAME", "collaboration-task-result-queue")
        self.reply_exchange_name = getattr(settings, "COLLAB_REPLY_EXCHANGE_NAME", "collaboration-reply-exchange")
        self.reply_routing_key = getattr(settings, "COLLAB_REPLY_ROUTING_KEY", "collaboration.task.result")

    async def connect(self):
        """Establish RabbitMQ connection"""
        try:
            self.connection = await aio_pika.connect_robust(self.rabbitmq_url)
            self.channel = await self.connection.channel()
            await self.channel.set_qos(prefetch_count=10)

            # Initialize Redis for idempotency
            if hasattr(shared_memory, '_redis') and shared_memory._redis:
                self._redis = shared_memory._redis
            elif hasattr(shared_memory, 'redis') and shared_memory.redis:
                self._redis = shared_memory.redis

            logger.info("MQ Worker connected to RabbitMQ at %s", self.rabbitmq_url)
        except Exception as e:
            logger.error("Failed to connect to RabbitMQ: %s", e)
            raise

    async def start_consuming(self):
        """Start consuming messages from the queue"""
        if not self.channel:
            await self.connect()

        self._running = True

        # Declare queue (idempotent)
        queue = await self.channel.declare_queue(
            self.queue_name,
            durable=True,
            arguments={
                "x-dead-letter-exchange": "",
                "x-message-ttl": 300000  # 5 min
            }
        )

        logger.info("Starting to consume from queue: %s", self.queue_name)

        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                if not self._running:
                    break
                await self._process_message(message)

    async def stop_consuming(self):
        """Gracefully stop consuming"""
        self._running = False
        if self.connection:
            await self.connection.close()
            logger.info("MQ Worker disconnected")

    async def _process_message(self, message: AbstractIncomingMessage):
        """Process a single message from the queue"""
        async with message.process(requeue=False):
            try:
                # Parse message
                body = json.loads(message.body.decode())
                task = CollabTaskMessage.from_dict(body)

                logger.info(
                    "Processing collab task: package=%s, subtask=%s, attempt=%s",
                    task.package_id, task.sub_task_id, task.attempt
                )

                # Idempotency check
                if await self._is_duplicate(task):
                    logger.warning(
                        "Duplicate task ignored: %s:%s:%s",
                        task.package_id, task.sub_task_id, task.attempt
                    )
                    return

                # Execute task
                result = await self._execute_task(task)

                # Write result back
                await self._write_result(result)

            except json.JSONDecodeError as e:
                logger.error("Invalid message format: %s", e)
            except Exception as e:
                logger.error("Task processing failed: %s", e, exc_info=True)

    async def _is_duplicate(self, task: CollabTaskMessage) -> bool:
        """Check if task was already processed (idempotency)"""
        if not self._redis:
            return False

        idempotency_key = f"collab:idemp:{task.package_id}:{task.sub_task_id}:{task.attempt}"

        try:
            # Use SETNX semantics
            existed = await asyncio.to_thread(
                self._redis.setnx, idempotency_key, "1"
            )
            if existed:
                # Set TTL (10 minutes for idempotency window)
                await asyncio.to_thread(
                    self._redis.expire, idempotency_key, 600
                )
                return False  # Was not duplicate
            return True  # Already existed = duplicate
        except Exception as e:
            logger.warning("Failed to check idempotency: %s", e)
            return False

    async def _execute_task(self, task: CollabTaskMessage) -> CollabTaskResult:
        """Execute the collaboration subtask"""
        started_at = datetime.utcnow()
        context = task.context_snapshot.copy() if task.context_snapshot else {}
        context["package_id"] = task.package_id
        context["sub_task_id"] = task.sub_task_id
        context["_trace_id"] = task.trace_id

        # Parse input data if provided
        if task.input_data:
            try:
                input_data = json.loads(task.input_data)
                context.update(input_data)
            except json.JSONDecodeError:
                pass

        try:
            # Execute based on strategy
            if task.execution_strategy == "WORKFLOW":
                result = await self._execute_workflow(task, context)
            else:
                result = await self._execute_agent(task, context)

            completed_at = datetime.utcnow()
            latency_ms = int((completed_at - started_at).total_seconds() * 1000)

            return CollabTaskResult(
                package_id=task.package_id,
                sub_task_id=task.sub_task_id,
                trace_id=task.trace_id,
                attempt=task.attempt,
                status="COMPLETED",
                result=result,
                started_at=int(started_at.timestamp() * 1000),
                completed_at=int(completed_at.timestamp() * 1000),
                latency_ms=latency_ms,
                executed_by=f"mq_worker:{task.expected_role}",
                metadata={"expectedRole": task.expected_role}
            )

        except asyncio.TimeoutError:
            completed_at = datetime.utcnow()
            latency_ms = int((completed_at - started_at).total_seconds() * 1000)
            return CollabTaskResult(
                package_id=task.package_id,
                sub_task_id=task.sub_task_id,
                trace_id=task.trace_id,
                attempt=task.attempt,
                status="TIMEOUT",
                error_message=f"Task timed out after {task.timeout_millis}ms",
                started_at=int(started_at.timestamp() * 1000),
                completed_at=int(completed_at.timestamp() * 1000),
                latency_ms=latency_ms,
                metadata={"expectedRole": task.expected_role}
            )

        except Exception as e:
            completed_at = datetime.utcnow()
            latency_ms = int((completed_at - started_at).total_seconds() * 1000)
            return CollabTaskResult(
                package_id=task.package_id,
                sub_task_id=task.sub_task_id,
                trace_id=task.trace_id,
                attempt=task.attempt,
                status="FAILED",
                error_message=str(e),
                started_at=int(started_at.timestamp() * 1000),
                completed_at=int(completed_at.timestamp() * 1000),
                latency_ms=latency_ms,
                metadata={"expectedRole": task.expected_role}
            )

    async def _execute_agent(self, task: CollabTaskMessage, context: Dict[str, Any]) -> str:
        """Execute via agent (delegation)"""
        # For now, use the collaboration executor's single task execution
        # This eventually should use the handler path
        try:
            result = await collaboration_executor.execute_single_task(
                description=task.description,
                expected_role=task.expected_role,
                context=context
            )
            return str(result) if result else "No result"
        except Exception as e:
            logger.error("Agent execution failed: %s", e)
            raise

    async def _execute_workflow(self, task: CollabTaskMessage, context: Dict[str, Any]) -> str:
        """Execute via workflow"""
        workflow_id = None
        workflow_inputs: Dict[str, Any] = {
            "description": task.description,
            "packageId": task.package_id,
            "subTaskId": task.sub_task_id,
        }

        # Try to parse workflowId / inputs from task.input_data JSON
        if task.input_data:
            try:
                input_data = json.loads(task.input_data)
                if isinstance(input_data, dict):
                    workflow_id = input_data.get("workflowId")
                    input_inputs = input_data.get("inputs")
                    if isinstance(input_inputs, dict):
                        workflow_inputs.update(input_inputs)
            except json.JSONDecodeError:
                logger.warning("Invalid input_data JSON for workflow task: %s", task.sub_task_id)

        # Fallback: support context snapshot payloads
        if workflow_id is None:
            workflow_id = context.get("workflowId")

        if workflow_id is None:
            raise ValueError(f"No workflowId found for workflow task: {task.sub_task_id}")

        # Normalize workflow id type
        try:
            workflow_id = int(workflow_id)
        except (TypeError, ValueError):
            raise ValueError(f"Invalid workflowId: {workflow_id}")

        backend_base = (settings.ORIN_BACKEND_URL or "http://localhost:8080").rstrip("/")
        url = f"{backend_base}/api/workflows/{workflow_id}/execute"
        timeout_seconds = max(10.0, (task.timeout_millis or 300000) / 1000.0)

        headers = {"Content-Type": "application/json"}
        if task.trace_id:
            headers["X-Trace-Id"] = task.trace_id

        params = {"triggeredBy": "collab_mq_worker"}

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

    async def _write_result(self, result: CollabTaskResult):
        """Write task result back to result queue"""
        if not self.channel:
            logger.error("No channel available for writing result")
            return

        try:
            result_dict = result.to_dict()
            message_body = json.dumps(result_dict, ensure_ascii=False)

            # Publish to reply exchange
            message = Message(
                body=message_body.encode(),
                delivery_mode=DeliveryMode.PERSISTENT,
                correlation_id=result.correlation_id if hasattr(result, 'correlation_id') else None
            )

            reply_exchange = await self.channel.get_exchange(self.reply_exchange_name)
            await reply_exchange.publish(
                message,
                routing_key=self.reply_routing_key
            )

            logger.info(
                "Result written: %s/%s -> %s",
                result.package_id, result.sub_task_id, result.status
            )

        except Exception as e:
            logger.error("Failed to write result: %s", e, exc_info=True)


# Global worker instance
collab_mq_worker = CollabMQWorker()

# ── 共享 RabbitMQ 连接 ──────────────────────────────────────────────────────

_rabbitmq_channel: Optional[aio_pika.Channel] = None
_rabbitmq_connection: Optional[aio_pika.RobustConnection] = None


async def get_rabbitmq_channel() -> aio_pika.Channel:
    """
    获取共享的 RabbitMQ channel（延迟初始化，全局复用）。
    LangGraph 节点通过这个发 MQ 消息。
    """
    global _rabbitmq_channel, _rabbitmq_connection

    if _rabbitmq_channel is None or _rabbitmq_connection is None or _rabbitmq_connection.is_closed:
        rabbitmq_url = getattr(settings, "RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
        _rabbitmq_connection = await aio_pika.connect_robust(rabbitmq_url)
        _rabbitmq_channel = await _rabbitmq_connection.channel()
        logger.info("[MQ] 共享 channel 已初始化")

    return _rabbitmq_channel


async def close_rabbitmq_connection():
    """关闭共享连接（应用退出时调用）"""
    global _rabbitmq_channel, _rabbitmq_connection

    if _rabbitmq_channel:
        await _rabbitmq_channel.close()
        _rabbitmq_channel = None
    if _rabbitmq_connection:
        await _rabbitmq_connection.close()
        _rabbitmq_connection = None


async def start_worker():
    """Start the MQ worker"""
    await collab_mq_worker.start_consuming()


async def stop_worker():
    """Stop the MQ worker"""
    await collab_mq_worker.stop_consuming()
