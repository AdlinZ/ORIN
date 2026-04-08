"""
MQ Producer for Collaboration - Used by AI Engine to publish tasks
This is primarily a stub since the backend publishes tasks to the queue.
The AI engine mainly consumes from the queue and produces results.

However, this module can be used for:
1. Publishing result acknowledgment
2. Publishing retry requests
3. Any AI engine-initiated collaboration tasks
"""
import asyncio
import json
import logging
from dataclasses import dataclass
from typing import Optional, Dict, Any

import aio_pika
from aio_pika import Message, DeliveryMode

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass
class CollabTaskResult:
    """Result message to send back to backend"""
    package_id: str
    sub_task_id: str
    trace_id: Optional[str]
    attempt: int
    status: str  # COMPLETED, FAILED, TIMEOUT
    result: Optional[str] = None
    error_message: Optional[str] = None
    started_at: Optional[int] = None
    completed_at: Optional[int] = None
    latency_ms: Optional[int] = None
    executed_by: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None

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
            "metadata": self.metadata or {},
        }


class CollabMQProducer:
    """Async RabbitMQ producer for collaboration results"""

    def __init__(self):
        self.connection: Optional[aio_pika.RobustConnection] = None
        self.channel: Optional[aio_pika.Channel] = None

        # Configuration
        self.rabbitmq_url = getattr(settings, "RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
        self.reply_exchange_name = getattr(settings, "COLLAB_REPLY_EXCHANGE_NAME", "collaboration-reply-exchange")
        self.reply_routing_key = getattr(settings, "COLLAB_REPLY_ROUTING_KEY", "collaboration.task.result")

    async def connect(self):
        """Establish RabbitMQ connection"""
        if not self.connection:
            self.connection = await aio_pika.connect_robust(self.rabbitmq_url)
            self.channel = await self.connection.channel()
            logger.info("MQ Producer connected to RabbitMQ")

    async def close(self):
        """Close connection"""
        if self.connection:
            await self.connection.close()
            self.connection = None
            self.channel = None

    async def publish_result(self, result: CollabTaskResult):
        """Publish task result to the result queue"""
        await self.connect()

        try:
            message_body = json.dumps(result.to_dict(), ensure_ascii=False)

            message = Message(
                body=message_body.encode(),
                delivery_mode=DeliveryMode.PERSISTENT,
            )

            reply_exchange = await self.channel.get_exchange(self.reply_exchange_name)
            await reply_exchange.publish(
                message,
                routing_key=self.reply_routing_key
            )

            logger.info(
                "Published result: %s/%s -> %s",
                result.package_id, result.sub_task_id, result.status
            )

        except Exception as e:
            logger.error("Failed to publish result: %s", e, exc_info=True)
            raise


# Global producer instance
collab_mq_producer = CollabMQProducer()
