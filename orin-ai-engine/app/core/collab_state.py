"""
协作状态管理 - Redis 持久化 + asyncio 内存事件
两层设计：
  - Redis: 跨进程可见的持久状态（status、检查点快照）
  - asyncio.Event: 同进程内 pause/resume 低延迟信号
Redis 命名空间与 Java 端 CollaborationRedisService 保持一致：
  collab:{packageId}:status      - 执行状态 JSON
  collab:{packageId}:control     - 控制标志 JSON（paused / rollback_to）
  collab:{packageId}:checkpoint:{id} - 已由 Java 端管理；Python 侧只写 initial
"""
import asyncio
import logging
import time
from typing import Any, Dict, List, Optional

from app.core.shared_memory import shared_memory

logger = logging.getLogger(__name__)

# ── asyncio 内存 pause 事件（per package_id）────────────────────────────────
# Event.set()  = 运行中（默认）
# Event.clear() = 已暂停（节点 await event.wait() 会阻塞）
_pause_events: Dict[str, asyncio.Event] = {}


def _get_pause_event(package_id: str) -> asyncio.Event:
    if package_id not in _pause_events:
        ev = asyncio.Event()
        ev.set()  # 默认运行
        _pause_events[package_id] = ev
    return _pause_events[package_id]


# ── Redis 状态读写 ──────────────────────────────────────────────────────────

_STATUS_NS = "collab"      # key 格式: {packageId}:status
_CONTROL_NS = "collab"     # key 格式: {packageId}:control

# 与 Java 端 CHECKPOINT_PREFIX 保持一致: collab:{packageId}:checkpoint:{id}
_CHECKPOINT_NS = "collab"


def _status_key(package_id: str) -> str:
    return f"{package_id}:status"


def _control_key(package_id: str) -> str:
    return f"{package_id}:control"


def _checkpoint_key(package_id: str, checkpoint_id: str) -> str:
    return f"{package_id}:checkpoint:{checkpoint_id}"


# ── Status ──────────────────────────────────────────────────────────────────

def write_status(
    package_id: str,
    status: str,
    *,
    final_result: Optional[str] = None,
    error_message: Optional[str] = None,
    extra: Optional[Dict[str, Any]] = None,
) -> None:
    """写入/更新协作执行状态到 Redis（TTL 24 h）。"""
    payload: Dict[str, Any] = {
        "package_id": package_id,
        "status": status,
        "updated_at": time.time(),
    }
    if final_result is not None:
        payload["final_result"] = final_result
    if error_message is not None:
        payload["error_message"] = error_message
    if extra:
        payload.update(extra)

    shared_memory.set(_status_key(package_id), payload, ttl=86400, namespace=_STATUS_NS)
    logger.debug("[CollabState] status=%s package=%s", status, package_id)


def read_status(package_id: str) -> Optional[Dict[str, Any]]:
    """从 Redis 读取协作状态，不存在返回 None。"""
    return shared_memory.get(_status_key(package_id), namespace=_STATUS_NS)


# ── Pause / Resume ──────────────────────────────────────────────────────────

def set_paused(package_id: str) -> None:
    """标记为已暂停：写 Redis 控制标志 + 清 asyncio Event。"""
    shared_memory.set(
        _control_key(package_id),
        {"paused": True, "requested_at": time.time()},
        ttl=86400,
        namespace=_CONTROL_NS,
    )
    _get_pause_event(package_id).clear()
    logger.info("[CollabState] paused package=%s", package_id)


def set_resumed(package_id: str) -> None:
    """取消暂停：清 Redis 控制标志 + 设 asyncio Event。"""
    shared_memory.set(
        _control_key(package_id),
        {"paused": False, "requested_at": time.time()},
        ttl=86400,
        namespace=_CONTROL_NS,
    )
    _get_pause_event(package_id).set()
    logger.info("[CollabState] resumed package=%s", package_id)


def is_paused(package_id: str) -> bool:
    """检查是否处于暂停状态（先查内存，再查 Redis）。"""
    ev = _get_pause_event(package_id)
    if not ev.is_set():
        return True
    # 同步 Redis（多实例部署下，本机可能没有 Event 记录）
    ctrl = shared_memory.get(_control_key(package_id), namespace=_CONTROL_NS)
    if ctrl and ctrl.get("paused"):
        ev.clear()  # 补同步
        return True
    return False


async def wait_if_paused(package_id: str, timeout: float = 300.0) -> bool:
    """
    节点调用：如果当前已暂停则等待，直到 resume 或超时。
    返回 True 表示正常继续，False 表示超时中断。
    """
    ev = _get_pause_event(package_id)
    # 同步 Redis 状态（处理重启后内存 Event 被重置的情况）
    ctrl = shared_memory.get(_control_key(package_id), namespace=_CONTROL_NS)
    if ctrl and ctrl.get("paused"):
        ev.clear()
    if ev.is_set():
        return True  # 未暂停，直接通过
    logger.info("[CollabState] package=%s is paused, waiting...", package_id)
    try:
        await asyncio.wait_for(ev.wait(), timeout=timeout)
        return True
    except asyncio.TimeoutError:
        logger.warning("[CollabState] package=%s pause timeout after %.0fs", package_id, timeout)
        return False


# ── Checkpoint（初始状态快照）─────────────────────────────────────────────

def save_checkpoint(package_id: str, checkpoint_id: str, data: Dict[str, Any]) -> None:
    """保存执行快照到 Redis（TTL 24 h）。"""
    shared_memory.set(
        _checkpoint_key(package_id, checkpoint_id),
        data,
        ttl=86400,
        namespace=_CHECKPOINT_NS,
    )
    logger.debug("[CollabState] saved checkpoint %s for package=%s", checkpoint_id, package_id)


def load_checkpoint(package_id: str, checkpoint_id: str) -> Optional[Dict[str, Any]]:
    """加载快照，不存在返回 None。"""
    return shared_memory.get(
        _checkpoint_key(package_id, checkpoint_id),
        namespace=_CHECKPOINT_NS,
    )


# ── Collab Ctx（Java 写入的上下文，sub_tasks / branch_results）────────────

_CTX_NS = "collab"


def _ctx_key(package_id: str) -> str:
    """collab:{packageId}:ctx"""
    return f"{package_id}:ctx"


def read_collab_ctx(package_id: str) -> Optional[Dict[str, Any]]:
    """
    读取 collab:{packageId}:ctx 完整 JSON 对象。
    Java CollaborationRedisService 在这里写入 sub_tasks、branch_results 等字段。
    """
    return shared_memory.get(_ctx_key(package_id), namespace=_CTX_NS)


def read_branch_result(package_id: str, sub_task_id: str) -> Optional[Any]:
    """
    轮询读取 collab:{packageId}:ctx['branch_result:{subTaskId}']。
    Java callback 成功完成后写入此字段。
    """
    ctx = read_collab_ctx(package_id)
    if ctx is None:
        return None
    field_key = f"branch_result:{sub_task_id}"
    return ctx.get(field_key)


async def poll_branch_result(
    package_id: str,
    sub_task_id: str,
    timeout: float = 300.0,
    poll_interval: float = 1.0,
) -> Optional[Any]:
    """
    轮询等待 branch_result:{subTaskId} 出现。
    用于 LangGraph 节点同步等待 Java MQ Worker 执行结果。

    Returns:
        找到的结果值（str）或者 None（超时）
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        # pause check
        if not await wait_if_paused(package_id, timeout=poll_interval):
            logger.warning("[poll_branch_result] pause timeout package=%s", package_id)
            return None

        result = read_branch_result(package_id, sub_task_id)
        if result is not None:
            logger.info(
                "[poll_branch_result] found result for package=%s sub_task=%s",
                package_id, sub_task_id
            )
            return result

        await asyncio.sleep(poll_interval)

    logger.warning(
        "[poll_branch_result] timeout after %.0fs package=%s sub_task=%s",
        timeout, package_id, sub_task_id
    )
    return None


def read_sub_tasks_from_ctx(package_id: str) -> Optional[List[Dict[str, Any]]]:
    """
    从 collab:{packageId}:ctx 读取 Java 已分解好的 sub_tasks 列表。
   Planner 节点不再自己做 LLM 分解，直接用这个。
    """
    ctx = read_collab_ctx(package_id)
    if ctx is None:
        return None
    return ctx.get("sub_tasks")
