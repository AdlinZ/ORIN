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
    CollaborationStatus
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/collaboration", tags=["collaboration"])


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


@router.post("/run")
async def execute_collaboration(request: CollaborationRequest):
    """执行协作任务"""
    logger.info(f"[API] 收到协作请求: {request.package_id}")
    
    result = await run_collaboration(
        package_id=request.package_id,
        intent=request.intent,
        collaboration_mode=request.collaboration_mode,
        trace_id=request.trace_id
    )
    
    return CollaborationResponse(
        package_id=result.get("package_id"),
        status=result.get("status", CollaborationStatus.FAILED.value),
        final_result=result.get("final_result"),
        error_message=result.get("error_message")
    )


@router.get("/status/{package_id}")
async def get_collaboration_status(package_id: str):
    """获取协作状态"""
    # TODO: 从 Redis 获取状态
    return {
        "package_id": package_id,
        "status": "UNKNOWN"
    }


@router.post("/pause")
async def pause_collaboration(request: dict):
    """暂停协作"""
    package_id = request.get("package_id")
    logger.info(f"[API] 暂停协作: {package_id}")
    # TODO: 实现暂停
    return {"status": "ok"}


@router.post("/resume")
async def resume_collaboration(request: dict):
    """恢复协作"""
    package_id = request.get("package_id")
    logger.info(f"[API] 恢复协作: {package_id}")
    # TODO: 实现恢复
    return {"status": "ok"}


@router.post("/rollback")
async def rollback_collaboration(request: dict):
    """回滚到检查点"""
    package_id = request.get("package_id")
    checkpoint_id = request.get("checkpoint_id")
    logger.info(f"[API] 回滚协作: {package_id} -> {checkpoint_id}")
    # TODO: 实现回滚
    return {"status": "ok"}


# Worker 管理
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
