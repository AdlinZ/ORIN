"""
协作节点处理器 - 支持多智能体协作的 DSL v2 节点
"""
import asyncio
import json
from typing import Any, Dict, List, Optional
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler


class PlannerNodeHandler(BaseNodeHandler):
    """规划节点 - 负责任务分解和计划生成"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}
        intent = node_data.get("intent", context.get("intent", ""))
        task_type = node_data.get("taskType", "general")

        # 根据任务类型生成计划
        subtasks = self._decompose_task(intent, task_type)

        # 存储到上下文中供后续使用
        context["__planner_subtasks"] = subtasks
        context["__planner_current_index"] = 0

        return NodeExecutionOutput(
            outputs={
                "plan": subtasks,
                "currentIndex": 0,
                "total": len(subtasks)
            },
            selected_handle="next"
        )

    def _decompose_task(self, intent: str, task_type: str) -> List[Dict[str, Any]]:
        """根据任务类型分解任务"""
        if task_type in ["analysis", "research"]:
            return [
                {"id": "1", "description": "收集信息", "role": "specialist", "dependsOn": []},
                {"id": "2", "description": "分析数据", "role": "specialist", "dependsOn": ["1"]},
                {"id": "3", "description": "总结结论", "role": "reviewer", "dependsOn": ["2"]}
            ]
        elif task_type in ["generation", "coding"]:
            return [
                {"id": "1", "description": "制定计划", "role": "planner", "dependsOn": []},
                {"id": "2", "description": "执行生成", "role": "specialist", "dependsOn": ["1"]},
                {"id": "3", "description": "审查结果", "role": "reviewer", "dependsOn": ["2"]}
            ]
        elif task_type == "testing":
            return [
                {"id": "1", "description": "执行测试", "role": "specialist", "dependsOn": []},
                {"id": "2", "description": "分析结果", "role": "specialist", "dependsOn": ["1"]},
                {"id": "3", "description": "修复问题", "role": "specialist", "dependsOn": ["2"]},
                {"id": "4", "description": "验证修复", "role": "reviewer", "dependsOn": ["3"]}
            ]
        else:
            # 默认计划
            return [
                {"id": "1", "description": "理解需求", "role": "planner", "dependsOn": []},
                {"id": "2", "description": "执行任务", "role": "specialist", "dependsOn": ["1"]},
                {"id": "3", "description": "审查结果", "role": "reviewer", "dependsOn": ["2"]}
            ]


class DelegateNodeHandler(BaseNodeHandler):
    """委托节点 - 将任务分派给指定角色的智能体"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        # 获取当前子任务索引
        current_index = context.get("__planner_current_index", 0)
        subtasks = context.get("__planner_subtasks", [])

        if current_index >= len(subtasks):
            return NodeExecutionOutput(
                outputs={"status": "no_more_tasks"},
                selected_handle="complete"
            )

        current_task = subtasks[current_index]

        # 执行子任务
        role = current_task.get("role", "specialist")
        description = current_task.get("description", "")

        # 模拟智能体执行
        result = await self._execute_delegate(role, description, context)

        # 更新上下文
        context["__planner_current_index"] = current_index + 1
        context[f"task_{current_task['id']}_result"] = result

        return NodeExecutionOutput(
            outputs={
                "taskId": current_task["id"],
                "role": role,
                "result": result,
                "nextIndex": current_index + 1
            },
            selected_handle="next"
        )

    async def _execute_delegate(self, role: str, description: str, context: Dict[str, Any]) -> str:
        """模拟智能体执行任务"""
        # 这里可以集成实际的 Agent 执行逻辑
        # 暂时返回模拟结果
        return f"[{role.upper()}] executed: {description}"


class ParallelForkNodeHandler(BaseNodeHandler):
    """并行分支节点 - 启动多个并行子任务"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        branches = node_data.get("branches", [])
        max_parallel = node_data.get("maxParallel", 3)

        # 限制并行数
        branches = branches[:max_parallel]

        # 并行执行所有分支
        tasks = [self._execute_branch(branch, context) for branch in branches]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # 汇总结果
        branch_results = {}
        for i, branch in enumerate(branches):
            branch_id = branch.get("id", f"branch_{i}")
            result = results[i]
            branch_results[branch_id] = result if not isinstance(result, Exception) else str(result)

        context["__parallel_results"] = branch_results

        return NodeExecutionOutput(
            outputs={
                "branches": branch_results,
                "count": len(branches)
            },
            selected_handle="merge"
        )

    async def _execute_branch(self, branch: Dict[str, Any], context: Dict[str, Any]) -> Any:
        """执行单个分支"""
        branch_type = branch.get("type", "llm")

        # 根据分支类型执行
        if branch_type == "llm":
            # 模拟 LLM 调用
            await asyncio.sleep(0.1)
            return f"Branch {branch.get('id')} completed"
        elif branch_type == "tool":
            return f"Tool {branch.get('tool')} executed"
        else:
            return f"Branch {branch.get('id')} done"


class ConsensusNodeHandler(BaseNodeHandler):
    """共识节点 - 收集多方意见并达成共识"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        strategy = node_data.get("strategy", "majority")
        threshold = node_data.get("threshold", 0.5)

        # 获取并行执行的结果
        parallel_results = context.get("__parallel_results", {})

        if not parallel_results:
            return NodeExecutionOutput(
                outputs={"status": "no_results", "consensus": None},
                selected_handle="fallback"
            )

        # 根据策略达成共识
        consensus = self._reach_consensus(parallel_results, strategy, threshold)

        context["__consensus_result"] = consensus

        return NodeExecutionOutput(
            outputs={
                "consensus": consensus,
                "strategy": strategy,
                "inputs": parallel_results
            },
            selected_handle="agreed" if consensus else "disagree"
        )

    def _reach_consensus(self, results: Dict[str, Any], strategy: str, threshold: float) -> Optional[Any]:
        """达成共识"""
        if strategy == "majority":
            # 多数投票
            values = list(results.values())
            # 简单的多数逻辑
            return values[0] if values else None
        elif strategy == "unanimous":
            # 全体一致
            unique_values = set(results.values())
            return list(unique_values)[0] if len(unique_values) == 1 else None
        elif strategy == "weighted":
            # 加权投票
            return list(results.values())[0] if results else None
        else:
            return list(results.values())[0] if results else None


class CriticNodeHandler(BaseNodeHandler):
    """批评节点 - 对结果进行审查和批评"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        review_criteria = node_data.get("criteria", ["accuracy", "completeness"])

        # 获取前一个节点的结果
        previous_output = context.get("__planner_subtasks", [{}])[-1] if context.get("__planner_subtasks") else {}

        # 模拟审查过程
        criticism = self._review_output(previous_output, review_criteria)

        context["__criticism"] = criticism

        # 根据审查结果决定是否需要重试
        if criticism.get("needs_revision", False):
            return NodeExecutionOutput(
                outputs=criticism,
                selected_handle="retry"
            )
        else:
            return NodeExecutionOutput(
                outputs=criticism,
                selected_handle="approve"
            )

    def _review_output(self, output: Dict[str, Any], criteria: List[str]) -> Dict[str, Any]:
        """审查输出"""
        # 简单的审查逻辑
        return {
            "approved": True,
            "needs_revision": False,
            "issues": [],
            "score": 0.9,
            "criteria_scores": {c: 0.9 for c in criteria}
        }


class MemoryReadNodeHandler(BaseNodeHandler):
    """记忆读取节点 - 从共享记忆存储中读取数据"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        key = node_data.get("key", "")
        default = node_data.get("default", None)

        # 从上下文中读取（实际应该从 Redis 等存储读取）
        value = context.get(f"__memory_{key}", default)

        return NodeExecutionOutput(
            outputs={
                "key": key,
                "value": value,
                "found": value is not None
            },
            selected_handle="found" if value else "not_found"
        )


class MemoryWriteNodeHandler(BaseNodeHandler):
    """记忆写入节点 - 将数据写入共享记忆存储"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        key = node_data.get("key", "")
        value = node_data.get("value")
        merge = node_data.get("merge", False)

        # 写入到上下文中（实际应该写入 Redis）
        if merge and context.get(f"__memory_{key}"):
            # 合并模式
            existing = context.get(f"__memory_{key}")
            if isinstance(existing, dict) and isinstance(value, dict):
                existing.update(value)
                value = existing

        context[f"__memory_{key}"] = value

        return NodeExecutionOutput(
            outputs={
                "key": key,
                "stored": True,
                "value": value
            },
            selected_handle="done"
        )


class EventEmitNodeHandler(BaseNodeHandler):
    """事件发射节点 - 发送协作事件"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        event_type = node_data.get("eventType", "custom")
        event_data = node_data.get("eventData", {})

        # 记录事件
        event = {
            "type": event_type,
            "data": event_data,
            "timestamp": context.get("__execution_start_time")
        }

        # 存储到上下文中（实际应该发送到事件总线）
        context.setdefault("__events", []).append(event)

        return NodeExecutionOutput(
            outputs={
                "event": event,
                "emitted": True
            },
            selected_handle="done"
        )


class EventListenNodeHandler(BaseNodeHandler):
    """事件监听节点 - 等待特定事件"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        event_type = node_data.get("eventType", "")
        timeout = node_data.get("timeout", 30)

        # 检查是否有匹配的事件
        events = context.get("__events", [])
        matching_events = [e for e in events if e.get("type") == event_type]

        if matching_events:
            return NodeExecutionOutput(
                outputs={
                    "event": matching_events[-1],
                    "received": True
                },
                selected_handle="triggered"
            )
        else:
            return NodeExecutionOutput(
                outputs={
                    "eventType": event_type,
                    "received": False
                },
                selected_handle="timeout"
            )


class RetryPolicyNodeHandler(BaseNodeHandler):
    """重试策略节点 - 实现协作级重试机制"""

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        max_retries = node_data.get("maxRetries", 3)
        retry_strategy = node_data.get("strategy", "exponential")

        # 获取当前重试次数
        current_retry = context.get("__retry_count", 0)

        if current_retry < max_retries:
            # 计算延迟
            delay = self._calculate_delay(current_retry, retry_strategy)

            context["__retry_count"] = current_retry + 1
            context["__retry_delay"] = delay

            return NodeExecutionOutput(
                outputs={
                    "retry": True,
                    "attempt": current_retry + 1,
                    "maxRetries": max_retries,
                    "delay": delay
                },
                selected_handle="retry"
            )
        else:
            return NodeExecutionOutput(
                outputs={
                    "retry": False,
                    "reason": "max_retries_exceeded",
                    "attempts": current_retry
                },
                selected_handle="exhausted"
            )

    def _calculate_delay(self, attempt: int, strategy: str) -> float:
        """计算重试延迟"""
        if strategy == "exponential":
            return min(2 ** attempt, 30)
        elif strategy == "linear":
            return min(attempt * 2, 30)
        else:
            return 1.0


# 注册所有协作处理器
COLLABORATION_HANDLERS = {
    "planner": PlannerNodeHandler,
    "delegate": DelegateNodeHandler,
    "parallel_fork": ParallelForkNodeHandler,
    "consensus": ConsensusNodeHandler,
    "critic": CriticNodeHandler,
    "memory_read": MemoryReadNodeHandler,
    "memory_write": MemoryWriteNodeHandler,
    "event_emit": EventEmitNodeHandler,
    "event_listen": EventListenNodeHandler,
    "retry_policy": RetryPolicyNodeHandler,
}