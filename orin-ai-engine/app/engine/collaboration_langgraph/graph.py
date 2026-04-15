"""
LangGraph 协作图定义
"""
import logging
from typing import Dict, Any
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver

from .state import CollaborationState, CollaborationStatus
from app.core.collab_state import (
    write_status,
    save_checkpoint,
    wait_if_paused,
)
from .nodes import (
    planner_node,
    delegate_node,
    parallel_fork_node,
    consensus_node,
    critic_node,
    memory_read_node,
    memory_write_node,
    should_continue_delegate,
    should_continue_critic
)

logger = logging.getLogger(__name__)


def build_collaboration_graph() -> StateGraph:
    """
    构建协作图
    
    流程：
    START -> planner -> (delegate / parallel_fork) -> consensus -> critic -> memory_write -> END
    
    路由逻辑：
    - delegate 可以循环回到自己，直到所有子任务完成
    - delegate 完成 -> consensus
    - parallel_fork 完成 -> consensus
    - critic 通过 -> memory_write
    - critic 驳回 -> delegate (重新执行)
    """
    
    # 创建图
    workflow = StateGraph(CollaborationState)
    
    # 添加节点
    workflow.add_node("planner", planner_node)
    workflow.add_node("delegate", delegate_node)
    workflow.add_node("parallel_fork", parallel_fork_node)
    workflow.add_node("consensus", consensus_node)
    workflow.add_node("critic", critic_node)
    workflow.add_node("memory_read", memory_read_node)
    workflow.add_node("memory_write", memory_write_node)
    
    # 设置边
    workflow.add_edge("__start__", "planner")
    
    # planner 根据模式选择路由
    workflow.add_conditional_edges(
        "planner",
        lambda state: "parallel_fork" if state.get("collaboration_mode") == "PARALLEL" else "delegate",
        {
            "delegate": "delegate",
            "parallel_fork": "parallel_fork"
        }
    )
    
    # delegate 循环
    workflow.add_conditional_edges(
        "delegate",
        should_continue_delegate,
        {
            "delegate": "delegate",
            "consensus": "consensus"
        }
    )
    
    # parallel_fork 直接到 consensus
    workflow.add_edge("parallel_fork", "consensus")
    
    # consensus 到 critic
    workflow.add_edge("consensus", "critic")
    
    # critic 条件路由
    workflow.add_conditional_edges(
        "critic",
        should_continue_critic,
        {
            "delegate": "delegate",
            "memory_write": "memory_write"
        }
    )
    
    # memory_write 结束
    workflow.add_edge("memory_write", END)
    
    return workflow


# 全局协作图实例
_collaboration_graph = None


def get_collaboration_graph(checkpoint: bool = True):
    """
    获取协作图实例
    
    Args:
        checkpoint: 是否启用检查点（需要 Redis）
    """
    global _collaboration_graph
    
    if _collaboration_graph is None:
        workflow = build_collaboration_graph()
        
        if checkpoint:
            # 使用 MemorySaver（生产环境应使用 RedisCheckpointer）
            checkpointer = MemorySaver()
            _collaboration_graph = workflow.compile(checkpointer=checkpointer)
        else:
            _collaboration_graph = workflow.compile()
    
    return _collaboration_graph


async def run_collaboration(
    package_id: str,
    intent: str,
    collaboration_mode: str = "SEQUENTIAL",
    trace_id: str = None,
    restore_from_checkpoint: str = None,
    **kwargs
) -> Dict[str, Any]:
    """
    运行协作图

    Args:
        package_id: 任务包 ID
        intent: 用户意图
        collaboration_mode: 协作模式
        trace_id: 追踪 ID
        restore_from_checkpoint: 如提供则从指定检查点快照恢复参数后重跑

    Returns:
        最终状态
    """
    logger.info(f"[LangGraph] 开始协作: {package_id}, mode: {collaboration_mode}")

    # ── 若有检查点快照，以快照参数覆盖传入参数 ───────────────────────────
    if restore_from_checkpoint:
        from app.core.collab_state import load_checkpoint
        snapshot = load_checkpoint(package_id, restore_from_checkpoint)
        if snapshot:
            logger.info(
                f"[LangGraph] 从检查点恢复: {restore_from_checkpoint}, package={package_id}"
            )
            intent = snapshot.get("intent", intent)
            collaboration_mode = snapshot.get("collaboration_mode", collaboration_mode)
            trace_id = snapshot.get("trace_id", trace_id)
            kwargs = {k: v for k, v in snapshot.items()
                      if k not in ("intent", "collaboration_mode", "trace_id", "package_id")}
        else:
            logger.warning(
                f"[LangGraph] 检查点 {restore_from_checkpoint} 不存在, 从头执行"
            )

    # ── 等待 pause 解除（多实例重启后可能残留暂停标志）────────────────────
    resumed = await wait_if_paused(package_id, timeout=10.0)
    if not resumed:
        write_status(package_id, CollaborationStatus.FAILED.value,
                     error_message="start timeout: still paused")
        return {
            "package_id": package_id,
            "status": CollaborationStatus.FAILED.value,
            "error_message": "start timeout: still paused",
        }

    # ── 构建初始状态 ─────────────────────────────────────────────────────
    initial_state: CollaborationState = {
        "package_id": package_id,
        "intent": intent,
        "trace_id": trace_id,
        "sub_tasks": [],
        "completed_subtasks": [],
        "current_task_index": 0,
        "collaboration_mode": collaboration_mode,
        "shared_context": {},
        "branch_results": {},
        "final_result": None,
        "status": CollaborationStatus.PLANNING.value,
        "error_message": None,
        "savepoint_id": None,
        **kwargs,
    }

    # ── 保存初始快照作为 "initial" 检查点，支持后续回滚 ──────────────────
    initial_snapshot = {
        "package_id": package_id,
        "intent": intent,
        "collaboration_mode": collaboration_mode,
        "trace_id": trace_id,
    }
    save_checkpoint(package_id, "initial", initial_snapshot)

    # ── 写入 PLANNING 状态 ────────────────────────────────────────────────
    write_status(package_id, CollaborationStatus.PLANNING.value)

    # ── 运行图 ────────────────────────────────────────────────────────────
    graph = get_collaboration_graph(checkpoint=False)

    # 写入 EXECUTING 状态（图开始执行前）
    write_status(package_id, CollaborationStatus.EXECUTING.value)

    try:
        result = await graph.ainvoke(initial_state)
        final_status = result.get("status", CollaborationStatus.COMPLETED.value)
        write_status(
            package_id,
            final_status,
            final_result=result.get("final_result"),
            error_message=result.get("error_message"),
        )
        logger.info(f"[LangGraph] 协作完成: {package_id}, status: {final_status}")
        return result
    except Exception as e:
        logger.error(f"[LangGraph] 协作失败: {e}")
        write_status(package_id, CollaborationStatus.FAILED.value, error_message=str(e))
        return {
            **initial_state,
            "status": CollaborationStatus.FAILED.value,
            "error_message": str(e),
        }


# ==================== MQ 集成 ====================

async def publish_collaboration_task(
    package_id: str,
    intent: str,
    collaboration_mode: str = "SEQUENTIAL",
    **kwargs
):
    """
    发布协作任务到 MQ
    """
    import json
    import time
    from aio_pika import Message, DeliveryMode
    from app.core.config import settings
    from app.engine.mq_worker import get_rabbitmq_channel

    channel = await get_rabbitmq_channel()
    queue_name = getattr(settings, "COLLAB_QUEUE_NAME", "collaboration-task-queue")
    await channel.declare_queue(queue_name, durable=True)

    payload = {
        "packageId": package_id,
        "subTaskId": kwargs.get("sub_task_id", f"{package_id}:root"),
        "traceId": kwargs.get("trace_id"),
        "attempt": kwargs.get("attempt", 0),
        "collaborationMode": collaboration_mode,
        "expectedRole": kwargs.get("expected_role", "SPECIALIST"),
        "description": intent,
        "inputData": json.dumps(kwargs.get("input_data", {}), ensure_ascii=False),
        "dependsOn": kwargs.get("depends_on", []),
        "contextSnapshot": kwargs.get("context_snapshot", {}),
        "maxRetries": kwargs.get("max_retries", 3),
        "timeoutMillis": kwargs.get("timeout_millis", 300000),
        "executionStrategy": kwargs.get("execution_strategy", "AGENT"),
        "enqueuedAt": int(time.time() * 1000),
    }

    await channel.default_exchange.publish(
        Message(
            body=json.dumps(payload, ensure_ascii=False).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=queue_name,
    )
    logger.info("[MQ] 已发布协作任务: package=%s queue=%s", package_id, queue_name)


async def subscribe_collaboration_tasks():
    """
    订阅协作任务
    """
    from app.engine.mq_worker import start_worker
    logger.info("[MQ] 使用统一 mq_worker 订阅协作任务")
    await start_worker()
