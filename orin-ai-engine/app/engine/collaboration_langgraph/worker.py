"""
LangGraph 协作 MQ Worker
基于现有 mq_worker 集成 LangGraph 执行
"""
import logging
import asyncio
from typing import Dict, Any, Optional
from aio_pika import IncomingMessage
from aio_pika.abc import AbstractIncomingMessage

from app.engine.mq_worker import CollabTaskMessage, CollabTaskResult
from app.engine.collaboration_langgraph.graph import run_collaboration
from app.engine.collaboration_langgraph.state import CollaborationStatus

logger = logging.getLogger(__name__)


class LangGraphCollabWorker:
    """基于 LangGraph 的协作 Worker"""
    
    def __init__(self):
        self._running = False
    
    async def connect(self):
        """连接 MQ"""
        from app.engine.mq_worker import get_rabbitmq_channel
        self.channel = await get_rabbitmq_channel()
        
        # 声明队列
        await self.channel.declare_queue(
            "collaboration.langgraph.tasks",
            durable=True
        )
        
        # 结果队列
        await self.channel.declare_queue(
            "collaboration.langgraph.results",
            durable=True
        )
        
        # 事件队列
        await self.channel.declare_queue(
            "collaboration.langgraph.events",
            durable=True
        )
        
        logger.info("[LangGraph Worker] MQ 连接成功")
    
    async def start_consuming(self):
        """开始消费"""
        self._running = True
        
        queue = await self.channel.declare_queue(
            "collaboration.langgraph.tasks",
            durable=True
        )
        
        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                if not self._running:
                    break
                
                await self._process_message(message)
    
    async def stop_consuming(self):
        """停止消费"""
        self._running = False
        logger.info("[LangGraph Worker] 已停止")
    
    async def _process_message(self, message: AbstractIncomingMessage):
        """处理消息"""
        async with message.process(requeue=True):
            try:
                data = message.body.decode()
                import json
                task_data = json.loads(data)
                
                # 执行协作
                result = await self._execute_collaboration(task_data)
                
                # 发送结果
                await self._publish_result(result)
                
                # 发送事件
                await self._publish_event(task_data.get("package_id"), result)
                
            except Exception as e:
                logger.error(f"[LangGraph Worker] 处理失败: {e}")
                raise
    
    async def _execute_collaboration(self, task_data: Dict) -> Dict[str, Any]:
        """执行协作"""
        package_id = task_data.get("package_id")
        intent = task_data.get("intent")
        collaboration_mode = task_data.get("collaboration_mode", "SEQUENTIAL")
        
        logger.info(f"[LangGraph Worker] 执行协作: {package_id}")
        
        result = await run_collaboration(
            package_id=package_id,
            intent=intent,
            collaboration_mode=collaboration_mode,
            trace_id=task_data.get("trace_id")
        )
        
        return result
    
    async def _publish_result(self, result: Dict[str, Any]):
        """发布结果"""
        import json
        
        await self.channel.declare_queue(
            "collaboration.langgraph.results",
            durable=True
        )
        
        message = {
            "package_id": result.get("package_id"),
            "status": result.get("status"),
            "final_result": result.get("final_result"),
            "error_message": result.get("error_message")
        }
        
        await self.channel.default_exchange.publish(
            message=json.dumps(message).encode(),
            routing_key="collaboration.langgraph.results"
        )
        
        logger.info(f"[LangGraph Worker] 结果已发布: {result.get('package_id')}")
    
    async def _publish_event(self, package_id: str, result: Dict[str, Any]):
        """发布事件"""
        import json
        from datetime import datetime
        
        status = result.get("status", "")
        
        event = {
            "package_id": package_id,
            "type": "status_changed",
            "status": status,
            "timestamp": datetime.utcnow().isoformat(),
            "data": {
                "completed_subtasks": result.get("completed_subtasks", []),
                "branch_results": list(result.get("branch_results", {}).keys())
            }
        }
        
        await self.channel.default_exchange.publish(
            message=json.dumps(event).encode(),
            routing_key="collaboration.langgraph.events"
        )


# 全局 Worker
_worker: Optional[LangGraphCollabWorker] = None


async def start_langgraph_worker():
    """启动 LangGraph Worker"""
    global _worker
    
    _worker = LangGraphCollabWorker()
    await _worker.connect()
    
    # 后台运行
    asyncio.create_task(_worker.start_consuming())
    
    logger.info("[LangGraph Worker] 已启动")


async def stop_langgraph_worker():
    """停止 Worker"""
    global _worker
    
    if _worker:
        await _worker.stop_consuming()
        _worker = None
