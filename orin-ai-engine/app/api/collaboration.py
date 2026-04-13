"""
LangGraph 协作 API
"""
import logging
from typing import Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.engine.collaboration_langgraph import (
    run_collaboration,
    start_langgraph_worker,
    stop_langgraph_worker,
    CollaborationStatus,
)
from app.core.collab_state import (
    read_status,
    set_paused,
    set_resumed,
    load_checkpoint,
    write_status,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/collaboration", tags=["collaboration"])


# ── Request / Response 模型 ──────────────────────────────────────────────────

class CollaborationRequest(BaseModel):
    """协作请求"""
    package_id: str
    intent: str
    collaboration_mode: str = "SEQUENTIAL"
    trace_id: Optional[str] = None


class CollaborationResponse(BaseModel):
    """协作响应"""
    package_id: str
    status: str
    final_result: Optional[str] = None
    error_message: Optional[str] = None


class PackageIdRequest(BaseModel):
    package_id: str


class RollbackRequest(BaseModel):
    package_id: str
    checkpoint_id: Optional[str] = "initial"  # 默认回滚到初始快照


# ── 执行 ─────────────────────────────────────────────────────────────────────

@router.post("/run")
async def execute_collaboration(request: CollaborationRequest):
    """执行协作任务"""
    logger.info(f"[API] 收到协作请求: {request.package_id}")

    result = await run_collaboration(
        package_id=request.package_id,
        intent=request.intent,
        collaboration_mode=request.collaboration_mode,
        trace_id=request.trace_id,
    )

    return CollaborationResponse(
        package_id=result.get("package_id", request.package_id),
        status=result.get("status", CollaborationStatus.FAILED.value),
        final_result=result.get("final_result"),
        error_message=result.get("error_message"),
    )


# ── Status ────────────────────────────────────────────────────────────────────

@router.get("/status/{package_id}")
async def get_collaboration_status(package_id: str):
    """
    获取协作状态 - 从 Redis 读取真实状态。
    key: collab:{package_id}:status（与 Java CollaborationRedisService 命名空间一致）
    """
    state = read_status(package_id)
    if state is None:
        return {
            "package_id": package_id,
            "status": "UNKNOWN",
            "message": "No status record found. Task may not have started yet.",
        }
    return state


# ── Pause ─────────────────────────────────────────────────────────────────────

@router.post("/pause")
async def pause_collaboration(request: PackageIdRequest):
    """
    暂停协作：
    1. 写 Redis 控制标志 collab:{packageId}:control = {paused: true}
    2. 清 asyncio.Event（同进程内的节点 await wait_if_paused() 会阻塞）
    3. 更新 Redis status 为 PAUSED（扩展状态，非 CollaborationStatus 枚举内标准值）
    """
    package_id = request.package_id
    logger.info(f"[API] 暂停协作: {package_id}")

    # 读取当前状态，只有执行中才允许暂停
    current = read_status(package_id)
    if current is None:
        raise HTTPException(status_code=404, detail=f"package {package_id} not found")

    current_status = current.get("status", "")
    if current_status in (CollaborationStatus.COMPLETED.value,
                          CollaborationStatus.FAILED.value):
        raise HTTPException(
            status_code=409,
            detail=f"Cannot pause: current status is {current_status}",
        )

    set_paused(package_id)
    write_status(package_id, "PAUSED")
    return {"package_id": package_id, "status": "PAUSED"}


# ── Resume ────────────────────────────────────────────────────────────────────

@router.post("/resume")
async def resume_collaboration(request: PackageIdRequest):
    """
    恢复协作：
    1. 清 Redis 控制标志（paused=false）
    2. 设 asyncio.Event（解除节点阻塞）
    3. 更新 Redis status 恢复为 EXECUTING
    """
    package_id = request.package_id
    logger.info(f"[API] 恢复协作: {package_id}")

    current = read_status(package_id)
    if current is None:
        raise HTTPException(status_code=404, detail=f"package {package_id} not found")

    set_resumed(package_id)
    # 恢复为 EXECUTING，如果当前不是 PAUSED 也无害（等幂）
    write_status(package_id, CollaborationStatus.EXECUTING.value)
    return {"package_id": package_id, "status": "EXECUTING"}


# ── Rollback ──────────────────────────────────────────────────────────────────

@router.post("/rollback")
async def rollback_collaboration(request: RollbackRequest):
    """
    回滚到检查点快照并重新执行协作。
    默认 checkpoint_id="initial"，即回到任务起始参数重跑。
    """
    package_id = request.package_id
    checkpoint_id = request.checkpoint_id or "initial"
    logger.info(f"[API] 回滚协作: {package_id} -> checkpoint={checkpoint_id}")

    # 验证检查点存在
    snapshot = load_checkpoint(package_id, checkpoint_id)
    if snapshot is None:
        raise HTTPException(
            status_code=404,
            detail=f"Checkpoint '{checkpoint_id}' not found for package {package_id}",
        )

    # 如果当前处于暂停，先恢复信号再重跑（避免死锁）
    set_resumed(package_id)

    # 写入 PLANNING 状态（让调用方知道正在回滚重跑）
    write_status(package_id, CollaborationStatus.PLANNING.value,
                 extra={"rolling_back_from": checkpoint_id})

    # 异步重跑（不阻塞当前请求），使用 asyncio 后台任务
    import asyncio
    asyncio.create_task(
        run_collaboration(
            package_id=package_id,
            intent=snapshot.get("intent", ""),
            collaboration_mode=snapshot.get("collaboration_mode", "SEQUENTIAL"),
            trace_id=snapshot.get("trace_id"),
            restore_from_checkpoint=checkpoint_id,
        )
    )

    return {
        "package_id": package_id,
        "checkpoint_id": checkpoint_id,
        "status": "ROLLING_BACK",
        "message": "Collaboration re-queued from checkpoint. Poll /status for updates.",
    }


# ── Worker 管理 ───────────────────────────────────────────────────────────────

_worker_started = False


@router.post("/worker/start")
async def start_worker():
    """启动 MQ Worker"""
    global _worker_started

    if not _worker_started:
        await start_langgraph_worker()
        _worker_started = True

    return {"status": "started"}


@router.post("/worker/stop")
async def stop_worker():
    """停止 MQ Worker"""
    global _worker_started

    if _worker_started:
        await stop_langgraph_worker()
        _worker_started = False

    return {"status": "stopped"}
