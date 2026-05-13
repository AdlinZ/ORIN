"""
LangGraph 协作节点实现
"""
import asyncio
import logging
from typing import Dict, Any, List, Optional
from langgraph.graph import END
from langchain_core.messages import HumanMessage, AIMessage

from . import state as collaboration_state
from .state import CollaborationState, CollaborationStatus, SubTask
from app.core.collab_state import wait_if_paused, write_status

logger = logging.getLogger(__name__)


# ==================== 规划节点 ====================

async def planner_node(state: CollaborationState) -> Dict[str, Any]:
    """
    规划节点 - 任务读取（不做 LLM 分解）

    Java CollaborationExecutor 已将任务分解结果写入 collab:{packageId}:ctx['sub_tasks']。
    这里只读取 + 基础校验，不再调 LLM。
    """
    package_id = state.get("package_id", "")

    # ── pause check ──────────────────────────────────────────────────────
    if not await wait_if_paused(package_id):
        return {**state, "status": CollaborationStatus.FAILED.value,
                "error_message": "planner_node: paused timeout"}

    logger.info(f"[Planner] 读取已分解任务: {package_id}")

    # 从 Java 上下文中读取已分解的 sub_tasks
    from app.core.collab_state import read_sub_tasks_from_ctx

    sub_tasks_data = read_sub_tasks_from_ctx(package_id)

    if sub_tasks_data is None:
        logger.error(f"[Planner] ctx 中无 sub_tasks，package={package_id}")
        return {
            "status": CollaborationStatus.FAILED.value,
            "error_message": "planner_node: missing sub_tasks in collab runtime context",
            "sub_tasks": [],
            "completed_subtasks": [],
            "current_task_index": 0,
        }
    if not isinstance(sub_tasks_data, list) or len(sub_tasks_data) == 0:
        logger.error(f"[Planner] sub_tasks 为空或格式错误，package={package_id}")
        return {
            "status": CollaborationStatus.FAILED.value,
            "error_message": "planner_node: empty or invalid sub_tasks in collab runtime context",
            "sub_tasks": [],
            "completed_subtasks": [],
            "current_task_index": 0,
        }

    # 标准化为 SubTask 结构
    sub_tasks: List[SubTask] = []
    for i, st in enumerate(sub_tasks_data):
        sub_tasks.append({
            "id": st.get("id", st.get("subTaskId", f"task_{i}")),
            "description": st.get("description", st.get("desc", "")),
            "role": st.get("role", st.get("expectedRole", "SPECIALIST")),
            "dependsOn": st.get("dependsOn", st.get("depends_on", [])),
            "promptTemplate": st.get("promptTemplate", "{description}"),
            "inputData": st.get("inputData", st.get("input_data", {})),
            "status": "pending",
            "result": None
        })

    logger.info(f"[Planner] 读取到 {len(sub_tasks)} 个子任务，package={package_id}")

    return {
        "sub_tasks": sub_tasks,
        "completed_subtasks": [],
        "current_task_index": 0,
        "status": CollaborationStatus.EXECUTING.value
    }


# ==================== 委托节点 ====================

async def delegate_node(state: CollaborationState) -> Dict[str, Any]:
    """
    委托节点 - 串行分派子任务
    1. 查找下一个可执行子任务（满足依赖）
    2. 发 MQ 消息到 collaboration-task-queue（camelCase 格式）
    3. 轮询等待 branch_result:{subTaskId} 出现
    """
    package_id = state.get("package_id", "")

    # ── pause check ──────────────────────────────────────────────────────
    if not await wait_if_paused(package_id):
        return {**state, "status": CollaborationStatus.FAILED.value,
                "error_message": "delegate_node: paused timeout"}

    sub_tasks = state.get("sub_tasks", [])
    completed_subtasks = state.get("completed_subtasks", [])
    current_index = state.get("current_task_index", 0)

    logger.info(f"[Delegate] 检查子任务，completed={len(completed_subtasks)}/{len(sub_tasks)}")

    # 查找下一个可执行的子任务
    current_task = None
    for i, st in enumerate(sub_tasks):
        st_id = st.get("id", f"task_{i}")
        if st_id in completed_subtasks:
            continue
        depends_on = st.get("dependsOn", [])
        if all(dep_id in completed_subtasks for dep_id in depends_on):
            current_task = st
            current_index = i
            break

    if current_task is None:
        logger.info(f"[Delegate] 所有子任务已完成，进入 consensus")
        return {"status": CollaborationStatus.CONSENSUS.value}

    task_id = current_task.get("id")
    role = current_task.get("role", "SPECIALIST")
    description = current_task.get("description", "")
    input_data = current_task.get("inputData", {})
    prompt_template = current_task.get("promptTemplate", "{description}")

    logger.info(f"[Delegate] 发送子任务到 MQ: {task_id}, role: {role}")

    # ── HTTP 触发子任务（由 Java 侧执行+回调写入 Redis）─────────────────────
    from app.core.config import settings
    import httpx

    execute_url = (
        f"{settings.ORIN_BACKEND_URL or 'http://localhost:8080'}"
        f"/api/v1/collaboration/packages/{package_id}/subtasks/{task_id}/execute"
    )
    trigger_payload = {
        "expectedRole": role,
        "description": description,
        "inputData": input_data,
        "contextSnapshot": {
            "intent": state.get("intent", ""),
            "shared_context": state.get("shared_context", {}),
        },
    }

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                execute_url,
                json=trigger_payload,
                headers={"X-Orchestrator-Mode": "LANGGRAPH"},
            )
            if resp.status_code >= 400:
                logger.error(
                    f"[Delegate] HTTP 触发失败 {resp.status_code}: {resp.text[:100]}"
                )
                return {
                    "error_message": f"触发子任务 {task_id} 失败: HTTP {resp.status_code}",
                    "status": CollaborationStatus.FAILED.value,
                }
            logger.info(f"[Delegate] HTTP 触发成功: {task_id}, status={resp.status_code}")
    except Exception as e:
        logger.error(f"[Delegate] HTTP 触发异常: {e}")
        return {
            "error_message": f"触发子任务 {task_id} 异常: {str(e)}",
            "status": CollaborationStatus.FAILED.value,
        }

    # ── 轮询等待 branch_result:{subTaskId} ─────────────────────────────────────
    from app.core.collab_state import poll_branch_result

    result = await poll_branch_result(
        package_id=package_id,
        sub_task_id=task_id,
        timeout=300.0,
        poll_interval=1.0,
    )

    if result is None:
        logger.error(f"[Delegate] 等待结果超时: {task_id}")
        return {
            "error_message": f"子任务 {task_id} 执行超时",
            "status": CollaborationStatus.FAILED.value
        }

    # 更新状态
    completed_subtasks.append(task_id)
    shared_context = state.get("shared_context", {})
    shared_context[f"task_{task_id}_result"] = result

    logger.info(f"[Delegate] 子任务完成: {task_id}")

    return {
        "completed_subtasks": completed_subtasks,
        "shared_context": shared_context,
        "current_task_index": current_index + 1,
        "status": CollaborationStatus.EXECUTING.value
    }


# ==================== 并行分叉节点 ====================

async def parallel_fork_node(state: CollaborationState) -> Dict[str, Any]:
    """
    并行分叉节点 - 按依赖分层触发任务，并行等待每一层结果
    """
    sub_tasks = state.get("sub_tasks", [])
    package_id = state.get("package_id", "")

    logger.info(f"[ParallelFork] 开始并行执行，共 {len(sub_tasks)} 个任务")

    if not await wait_if_paused(package_id):
        return {**state, "status": CollaborationStatus.FAILED.value,
                "error_message": "parallel_fork_node: paused timeout"}

    from app.core.config import settings
    import httpx
    from app.core.collab_state import poll_branch_result

    backend_url = settings.ORIN_BACKEND_URL or "http://localhost:8080"

    async def trigger_one(st: Dict) -> Optional[str]:
        task_id = st.get("id", "")
        role = st.get("role", "SPECIALIST")
        description = st.get("description", "")
        input_data = st.get("inputData", {})

        execute_url = (
            f"{backend_url}"
            f"/api/v1/collaboration/packages/{package_id}/subtasks/{task_id}/execute"
        )
        trigger_payload = {
            "expectedRole": role,
            "description": description,
            "inputData": input_data,
            "contextSnapshot": {
                "intent": state.get("intent", ""),
                "shared_context": state.get("shared_context", {}),
            },
        }

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                resp = await client.post(
                    execute_url,
                    json=trigger_payload,
                    headers={"X-Orchestrator-Mode": "LANGGRAPH"},
                )
                if resp.status_code >= 400:
                    logger.error(
                        f"[ParallelFork] HTTP 触发失败 {task_id}: {resp.status_code}"
                    )
                    return None
                logger.info(
                    f"[ParallelFork] HTTP 触发成功 {task_id}: status={resp.status_code}"
                )
                return task_id
        except Exception as e:
            logger.warning(f"[ParallelFork] HTTP 触发失败 {task_id}: {e}")
            return None

    async def poll_one(task_id: str) -> tuple:
        result = await poll_branch_result(
            package_id=package_id,
            sub_task_id=task_id,
            timeout=300.0,
            poll_interval=1.0,
        )
        return task_id, result

    branch_results = {}
    completed_subtasks = set(state.get("completed_subtasks", []))
    remaining = {st.get("id", f"task_{idx}"): st for idx, st in enumerate(sub_tasks)}

    while remaining:
        if not await wait_if_paused(package_id):
            return {
                "branch_results": branch_results,
                "completed_subtasks": list(completed_subtasks),
                "status": CollaborationStatus.FAILED.value,
                "error_message": "parallel_fork_node: paused timeout"
            }

        ready_tasks = [
            st for task_id, st in remaining.items()
            if all(dep_id in completed_subtasks for dep_id in st.get("dependsOn", []))
        ]

        if not ready_tasks:
            blocked_ids = ", ".join(remaining.keys())
            logger.error("[ParallelFork] 依赖无法满足或存在环: %s", blocked_ids)
            return {
                "branch_results": branch_results,
                "completed_subtasks": list(completed_subtasks),
                "status": CollaborationStatus.FAILED.value,
                "error_message": f"并行任务依赖无法满足: {blocked_ids}"
            }

        logger.info(f"[ParallelFork] 准备并发执行本层 {len(ready_tasks)} 个任务")
        trigger_results = await asyncio.gather(
            *[trigger_one(st) for st in ready_tasks],
            return_exceptions=True,
        )
        sent_tasks = [r for r in trigger_results if isinstance(r, str)]
        if len(sent_tasks) < len(ready_tasks):
            failed_count = len(ready_tasks) - len(sent_tasks)
            return {
                "branch_results": branch_results,
                "completed_subtasks": list(completed_subtasks),
                "status": CollaborationStatus.FAILED.value,
                "error_message": f"{failed_count} 个并行任务触发失败"
            }

        poll_results = await asyncio.gather(
            *[poll_one(tid) for tid in sent_tasks],
            return_exceptions=True,
        )

        poll_errors = [r for r in poll_results if isinstance(r, Exception)]
        if poll_errors:
            logger.warning("[ParallelFork] 本层任务轮询异常: %s", poll_errors[0])
            return {
                "branch_results": branch_results,
                "completed_subtasks": list(completed_subtasks),
                "status": CollaborationStatus.FAILED.value,
                "error_message": f"{len(poll_errors)} 个并行任务结果轮询异常"
            }

        for r in poll_results:
            task_id, result = r
            if result is not None:
                branch_results[task_id] = result
                completed_subtasks.add(task_id)
                remaining.pop(task_id, None)

        if any((not isinstance(r, Exception)) and r[1] is None for r in poll_results) or len(completed_subtasks) == 0:
            missing = [tid for tid in sent_tasks if tid not in branch_results]
            logger.warning("[ParallelFork] 本层任务未拿到结果: %s", missing)
            return {
                "branch_results": branch_results,
                "completed_subtasks": list(completed_subtasks),
                "status": CollaborationStatus.FAILED.value,
                "error_message": f"{len(missing)} 个并行任务执行失败或超时"
            }

    shared_context = state.get("shared_context", {})
    shared_context["__parallel_results"] = branch_results

    logger.info(f"[ParallelFork] 并行执行完成，{len(branch_results)}/{len(sub_tasks)} 个结果")

    return {
        "branch_results": branch_results,
        "shared_context": shared_context,
        "completed_subtasks": list(completed_subtasks),
        "status": CollaborationStatus.CONSENSUS.value
    }


# ==================== 共识节点 ====================

async def consensus_node(state: CollaborationState) -> Dict[str, Any]:
    """
    共识节点 - 汇总结果，达成共识
    支持 majority / unanimous / weighted 策略
    """
    branch_results = state.get("branch_results", {})
    shared_context = state.get("shared_context", {})
    package_id = state.get("package_id", "")
    
    logger.info(f"[Consensus] 开始共识，共 {len(branch_results)} 个分支结果")
    
    if not branch_results:
        # 串行模式，直接用 shared_context 中的结果
        return {"status": CollaborationStatus.COMPLETED.value}
    
    # 简单的结果拼接（后续可扩展为真正的共识算法）
    results_text = "\n\n".join([
        f"=== {task_id} ===\n{result}"
        for task_id, result in branch_results.items()
    ])
    
    # 可选：调用 LLM 生成最终总结
    from app.engine.handlers.llm import RealLLMNodeHandler
    from app.models.workflow import Node, NodeExecutionOutput
    
    summary_prompt = f"""请总结以下多个智能体的协作结果：

{results_text}

请提供一个综合的总结报告。"""

    try:
        llm_node = Node(
            id="consensus_summary",
            type="llm",
            data={
                "prompt": summary_prompt,
                "model": "default",
                "temperature": 0.5,
            }
        )
        
        llm_handler = RealLLMNodeHandler()
        output: NodeExecutionOutput = await llm_handler.run(llm_node, state)
        final_result = output.outputs.get("text", "") if output.outputs else results_text
    except Exception as e:
        logger.warning(f"[Consensus] 总结生成失败，使用原始结果: {e}")
        final_result = results_text
    
    return {
        "final_result": final_result,
        "shared_context": {**shared_context, "__consensus_summary": final_result},
        "status": CollaborationStatus.COMPLETED.value
    }


# ==================== 评审节点 ====================

async def critic_node(state: CollaborationState) -> Dict[str, Any]:
    """
    评审节点 - 评审协作结果
    """
    final_result = state.get("final_result", "")
    package_id = state.get("package_id", "")
    
    logger.info(f"[Critic] 开始评审结果: {package_id[:20]}...")
    
    from app.engine.handlers.llm import RealLLMNodeHandler
    from app.models.workflow import Node, NodeExecutionOutput
    
    review_prompt = f"""请评审以下协作结果，给出改进建议：

{final_result}

请以以下格式回复：
- 是否通过: YES/NO
- 改进建议: (如有)"""

    llm_node = Node(
        id="critic_review",
        type="llm",
        data={
            "prompt": review_prompt,
            "model": "default",
            "temperature": 0.3,
        }
    )
    
    llm_handler = RealLLMNodeHandler()
    
    try:
        output: NodeExecutionOutput = await llm_handler.run(llm_node, state)
        review_result = output.outputs.get("text", "") if output.outputs else ""
        
        # 判断是否通过
        approved = "YES" in review_result.upper() and "NO" not in review_result.upper()[:50]
        if not approved:
            fallback_attempts = int(state.get("fallback_attempts", 0) or 0) + 1
            max_fallback_attempts = collaboration_state.MAX_FALLBACK_ATTEMPTS
            if fallback_attempts >= max_fallback_attempts:
                error_message = f"FALLBACK exceeded max attempts ({fallback_attempts}/{max_fallback_attempts})"
                return {
                    "shared_context": {**state.get("shared_context", {}), "__review": review_result},
                    "fallback_attempts": fallback_attempts,
                    "status": CollaborationStatus.FAILED.value,
                    "error_message": error_message,
                }
            return {
                "shared_context": {**state.get("shared_context", {}), "__review": review_result},
                "fallback_attempts": fallback_attempts,
                "status": CollaborationStatus.FALLBACK.value,
            }
        
        return {
            "shared_context": {**state.get("shared_context", {}), "__review": review_result},
            "status": CollaborationStatus.COMPLETED.value,
        }
    except Exception as e:
        logger.error(f"[Critic] 评审失败: {e}")
        return {
            "status": CollaborationStatus.COMPLETED.value,
            "error_message": str(e)
        }


# ==================== 内存读写节点 ====================

async def memory_read_node(state: CollaborationState) -> Dict[str, Any]:
    """读取共享内存"""
    shared_context = state.get("shared_context", {})
    
    # 从 Redis 读取
    from app.core.shared_memory import shared_memory
    
    package_id = state.get("package_id", "")
    
    try:
        redis = shared_memory._get_redis()
        if redis:
            key = f"collaboration:memory:{package_id}"
            data = redis.get(key)
            
            if data:
                import json
                memory_data = json.loads(data)
                shared_context = {**shared_context, **memory_data}
    except Exception as e:
        logger.warning(f"[MemoryRead] 读取失败，使用内存: {e}")
    
    return {"shared_context": shared_context}


async def memory_write_node(state: CollaborationState) -> Dict[str, Any]:
    """写入共享内存"""
    shared_context = state.get("shared_context", {})
    package_id = state.get("package_id", "")
    
    # 写入 Redis
    from app.core.shared_memory import shared_memory
    
    try:
        redis = shared_memory._get_redis()
        if redis:
            key = f"collaboration:memory:{package_id}"
            
            import json
            redis.set(key, json.dumps(shared_context), ex=3600)  # 1小时过期
            
            logger.info(f"[MemoryWrite] 已写入内存: {package_id}")
    except Exception as e:
        logger.warning(f"[MemoryWrite] 写入失败: {e}")
    
    return {"status": CollaborationStatus.COMPLETED.value}


# ==================== 路由函数 ====================

def should_continue_delegate(state: CollaborationState) -> str:
    """
    委托节点路由决策。

    失败安全：任意节点设置 FAILED 状态后立即停止重试，
    防止已失败状态下图仍回 delegate 形成无限循环。
    """
    status = state.get("status", "")
    if status == CollaborationStatus.FAILED.value:
        return END  # 失败后直接终止，不继续 delegate 循环

    sub_tasks = state.get("sub_tasks", [])
    completed_subtasks = state.get("completed_subtasks", [])

    if len(completed_subtasks) >= len(sub_tasks):
        return "consensus"

    return "delegate"


def should_continue_critic(state: CollaborationState) -> str:
    """评审节点的路由决策"""
    status = state.get("status", "")

    if status == CollaborationStatus.FAILED.value:
        return "end_failed"

    if status == CollaborationStatus.FALLBACK.value:
        fallback_attempts = int(state.get("fallback_attempts", 0) or 0)
        if fallback_attempts >= collaboration_state.MAX_FALLBACK_ATTEMPTS:
            return "end_failed"
        return "delegate"  # 驳回，重新执行

    return "memory_write"
