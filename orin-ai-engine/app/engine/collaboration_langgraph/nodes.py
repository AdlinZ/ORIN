"""
LangGraph 协作节点实现
"""
import asyncio
import logging
from typing import Dict, Any, List, Optional
from langgraph.graph import END
from langchain_core.messages import HumanMessage, AIMessage

from .state import CollaborationState, CollaborationStatus, SubTask

logger = logging.getLogger(__name__)


# ==================== 规划节点 ====================

async def planner_node(state: CollaborationState) -> Dict[str, Any]:
    """
    规划节点 - 任务分解
    调用 LLM 将用户意图分解为子任务
    """
    intent = state.get("intent", "")
    package_id = state.get("package_id", "")
    
    logger.info(f"[Planner] 开始分解任务: {package_id}, intent: {intent[:50]}...")
    
    # 调用 LLM 进行任务分解
    from app.engine.handlers.llm import RealLLMNodeHandler
    from app.models.workflow import Node
    
    prompt = f"""请将以下任务分解为子任务列表：
任务：{intent}

请以 JSON 数组格式返回，每个子任务包含：
- id: 子任务ID
- description: 描述
- role: 角色 (PLANNER/SPECIALIST/REVIEWER/CRITIC)
- dependsOn: 依赖的子任务ID列表
- promptTemplate: 执行提示词模板

直接返回 JSON，不要其他内容。"""

    llm_node = Node(
        id="planner_llm",
        type="llm",
        data={
            "prompt": prompt,
            "model": "default",
            "temperature": 0.3,
        }
    )
    
    llm_handler = RealLLMNodeHandler()
    
    try:
        from app.models.workflow import NodeExecutionOutput
        output: NodeExecutionOutput = await llm_handler.run(llm_node, {"intent": intent})
        result_text = output.outputs.get("text", "") if output.outputs else ""
        
        # 解析 JSON
        import json
        try:
            # 尝试从结果中提取 JSON
            if "[" in result_text:
                start = result_text.index("[")
                end = result_text.rindex("]") + 1
                sub_tasks_data = json.loads(result_text[start:end])
            else:
                sub_tasks_data = json.loads(result_text)
            
            sub_tasks: List[SubTask] = []
            for i, st in enumerate(sub_tasks_data):
                sub_tasks.append({
                    "id": st.get("id", f"task_{i}"),
                    "description": st.get("description", ""),
                    "role": st.get("role", "SPECIALIST"),
                    "dependsOn": st.get("dependsOn", []),
                    "promptTemplate": st.get("promptTemplate", "{description}"),
                    "inputData": st.get("inputData", {}),
                    "status": "pending",
                    "result": None
                })
            
            logger.info(f"[Planner] 分解完成，共 {len(sub_tasks)} 个子任务")
            
            return {
                "sub_tasks": sub_tasks,
                "completed_subtasks": [],
                "current_task_index": 0,
                "status": CollaborationStatus.DECOMPOSING.value
            }
        except json.JSONDecodeError as e:
            logger.error(f"[Planner] JSON 解析失败: {e}")
            raise
            
    except Exception as e:
        logger.error(f"[Planner] 分解失败: {e}")
        return {
            "status": CollaborationStatus.FAILED.value,
            "error_message": f"任务分解失败: {str(e)}"
        }


# ==================== 委托节点 ====================

async def delegate_node(state: CollaborationState) -> Dict[str, Any]:
    """
    委托节点 - 串行分派子任务
    按顺序执行子任务，支持依赖链
    """
    sub_tasks = state.get("sub_tasks", [])
    completed_subtasks = state.get("completed_subtasks", [])
    current_index = state.get("current_task_index", 0)
    package_id = state.get("package_id", "")
    
    logger.info(f"[Delegate] 开始执行子任务，当前索引: {current_index}")
    
    # 查找下一个可执行的子任务
    current_task = None
    for i, st in enumerate(sub_tasks):
        st_id = st.get("id", f"task_{i}")
        if st_id in completed_subtasks:
            continue
        
        # 检查依赖是否满足
        depends_on = st.get("dependsOn", [])
        if all(dep_id in completed_subtasks for dep_id in depends_on):
            current_task = st
            current_index = i
            break
    
    if current_task is None:
        logger.info(f"[Delegate] 所有子任务已完成")
        return {
            "status": CollaborationStatus.CONSENSUS.value
        }
    
    # 执行当前子任务
    task_id = current_task.get("id")
    role = current_task.get("role", "SPECIALIST")
    description = current_task.get("description", "")
    prompt_template = current_task.get("promptTemplate", "{description}")
    input_data = current_task.get("inputData", {})
    
    # 填充模板
    resolved_inputs = {**input_data, "description": description}
    prompt = prompt_template.format(**resolved_inputs)
    
    logger.info(f"[Delegate] 执行子任务: {task_id}, role: {role}")
    
    # 调用 LLM 执行
    from app.engine.handlers.llm import RealLLMNodeHandler
    from app.models.workflow import Node, NodeExecutionOutput
    
    llm_node = Node(
        id=f"delegate_{task_id}",
        type="llm",
        data={
            "prompt": prompt,
            "model": "default",
            "temperature": 0.7,
            "expectedRole": role
        }
    )
    
    llm_handler = RealLLMNodeHandler()
    
    try:
        output: NodeExecutionOutput = await llm_handler.run(llm_node, state)
        result = output.outputs.get("text", "") if output.outputs else ""
        
        # 更新状态
        completed_subtasks.append(task_id)
        
        # 写入共享上下文
        shared_context = state.get("shared_context", {})
        shared_context[f"task_{task_id}_result"] = result
        
        logger.info(f"[Delegate] 子任务完成: {task_id}")
        
        return {
            "completed_subtasks": completed_subtasks,
            "shared_context": shared_context,
            "current_task_index": current_index + 1,
            "status": CollaborationStatus.EXECUTING.value
        }
        
    except Exception as e:
        logger.error(f"[Delegate] 子任务执行失败: {e}")
        return {
            "error_message": f"子任务 {task_id} 执行失败: {str(e)}",
            "status": CollaborationStatus.FAILED.value
        }


# ==================== 并行分叉节点 ====================

async def parallel_fork_node(state: CollaborationState) -> Dict[str, Any]:
    """
    并行分叉节点 - 并行执行多个子任务
    """
    sub_tasks = state.get("sub_tasks", [])
    package_id = state.get("package_id", "")
    
    logger.info(f"[ParallelFork] 开始并行执行，共 {len(sub_tasks)} 个任务")
    
    # 获取没有依赖的子任务（可以并行执行）
    ready_tasks = [st for st in sub_tasks if not st.get("dependsOn")]
    
    if not ready_tasks:
        return {
            "status": CollaborationStatus.CONSENSUS.value,
            "error_message": "没有可并行执行的任务"
        }
    
    # 并行执行
    async def execute_task(st: Dict) -> tuple:
        task_id = st.get("id", "")
        role = st.get("role", "SPECIALIST")
        description = st.get("description", "")
        prompt_template = st.get("promptTemplate", "{description}")
        
        prompt = prompt_template.format(description=description)
        
        from app.engine.handlers.llm import RealLLMNodeHandler
        from app.models.workflow import Node, NodeExecutionOutput
        
        llm_node = Node(
            id=f"parallel_{task_id}",
            type="llm",
            data={
                "prompt": prompt,
                "model": "default",
                "temperature": 0.7,
            }
        )
        
        llm_handler = RealLLMNodeHandler()
        
        try:
            output: NodeExecutionOutput = await llm_handler.run(llm_node, state)
            result = output.outputs.get("text", "") if output.outputs else ""
            return task_id, result
        except Exception as e:
            return task_id, f"[ERROR] {str(e)}"
    
    # 并发执行
    tasks = [execute_task(st) for st in ready_tasks]
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    branch_results = {}
    for r in results:
        if isinstance(r, Exception):
            continue
        task_id, result = r
        branch_results[task_id] = result
    
    # 更新共享上下文
    shared_context = state.get("shared_context", {})
    shared_context["__parallel_results"] = branch_results
    
    logger.info(f"[ParallelFork] 并行执行完成，{len(branch_results)} 个结果")
    
    return {
        "branch_results": branch_results,
        "shared_context": shared_context,
        "completed_subtasks": list(branch_results.keys()),
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
        
        return {
            "shared_context": {**state.get("shared_context", {}), "__review": review_result},
            "status": CollaborationStatus.COMPLETED.value if approved else CollaborationStatus.FALLBACK.value
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
    """委托节点的路由决策"""
    sub_tasks = state.get("sub_tasks", [])
    completed_subtasks = state.get("completed_subtasks", [])
    
    if len(completed_subtasks) >= len(sub_tasks):
        return "consensus"
    
    return "delegate"


def should_continue_critic(state: CollaborationState) -> str:
    """评审节点的路由决策"""
    status = state.get("status", "")
    
    if status == CollaborationStatus.FALLBACK.value:
        return "delegate"  # 驳回，重新执行
    
    return "memory_write"
