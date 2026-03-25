"""
协作执行器 - 支持多智能体协作的增强执行模式
"""
import asyncio
import time
from typing import List, Dict, Set, Any, Optional, Callable
from app.models.workflow import WorkflowDSL, ExecutionResult, Node, NodeStatus, WorkflowStatus, NodeTrace, NodeExecutionOutput
from app.engine.executor import GraphExecutor


class CollaborationExecutor:
    """协作执行器 - 支持动态子图注入、并行分支、合流投票、协作级重试"""

    def __init__(self):
        self.base_executor = GraphExecutor()

        # 协作执行配置
        self.config = {
            "max_parallel_branches": 5,
            "branch_timeout": 60.0,
            "consensus_timeout": 30.0,
            "default_retry_budget": 3,
            "retry_backoff_multiplier": 2.0,
            "max_context_window": 128000,  # token 限制
            "enable_checkpoints": True,
        }

        # 子图注入缓存
        self.subgraph_cache: Dict[str, WorkflowDSL] = {}

        # 协作状态
        self.collaboration_state: Dict[str, Any] = {}

    async def execute_collaboration(
        self,
        dsl: WorkflowDSL,
        initial_inputs: Dict[str, Any],
        trace_id: Optional[str] = None,
        collaboration_config: Optional[Dict] = None
    ) -> ExecutionResult:
        """
        执行协作工作流

        Args:
            dsl: 工作流 DSL 定义
            initial_inputs: 初始输入
            trace_id: 追踪 ID
            collaboration_config: 协作配置覆盖
        """
        # 合并配置
        if collaboration_config:
            config = {**self.config, **collaboration_config}
        else:
            config = self.config

        # 初始化协作状态
        self._init_collaboration_state(dsl, trace_id)

        # 查找协作入口节点
        collab_entry = self._find_collaboration_entry(dsl)

        if collab_entry:
            # 使用协作执行模式
            return await self._execute_collaborative(dsl, initial_inputs, trace_id, config)
        else:
            # 使用基础执行模式
            return await self.base_executor.execute(dsl, initial_inputs, trace_id)

    def _init_collaboration_state(self, dsl: WorkflowDSL, trace_id: Optional[str]):
        """初始化协作状态"""
        self.collaboration_state = {
            "trace_id": trace_id,
            "start_time": time.time(),
            "branches": {},  # branch_id -> status
            "consensus_results": {},
            "retry_budgets": {},  # node_id -> remaining retries
            "checkpoints": {},  # checkpoint_id -> context snapshot
            "subtask_results": {},  # subtask_id -> result
        }

    def _find_collaboration_entry(self, dsl: WorkflowDSL) -> Optional[Node]:
        """查找协作入口节点"""
        collab_types = {"planner", "parallel_fork", "consensus"}
        for node in dsl.nodes:
            if node.type.lower() in collab_types:
                return node
        return None

    async def _execute_collaborative(
        self,
        dsl: WorkflowDSL,
        initial_inputs: Dict[str, Any],
        trace_id: Optional[str],
        config: Dict
    ) -> ExecutionResult:
        """执行协作工作流"""

        # 首先执行非协作部分
        result = await self.base_executor.execute(dsl, initial_inputs, trace_id)

        # 检查是否需要协作后处理
        if self.collaboration_state.get("branches"):
            # 处理并行分支结果
            result = await self._process_branch_results(result, config)

        return result

    async def _process_branch_results(
        self,
        result: ExecutionResult,
        config: Dict
    ) -> ExecutionResult:
        """处理并行分支结果"""
        branches = self.collaboration_state.get("branches", {})

        # 汇总所有分支输出
        all_outputs = {}
        for branch_id, branch_data in branches.items():
            if branch_data.get("status") == "completed":
                all_outputs[branch_id] = branch_data.get("outputs", {})

        # 合并到最终输出
        result.outputs["__branch_summary"] = all_outputs

        return result

    async def execute_parallel_branches(
        self,
        branches: List[Dict[str, Any]],
        context: Dict[str, Any],
        trace_id: Optional[str] = None,
        max_parallel: int = 3
    ) -> Dict[str, Any]:
        """
        并行执行多个分支

        Args:
            branches: 分支列表，每个包含子 DSL 或执行配置
            context: 执行上下文
            trace_id: 追踪 ID
            max_parallel: 最大并行数
        """
        max_parallel = min(max_parallel, self.config["max_parallel_branches"])

        results = {}
        errors = {}

        # 创建并发任务
        async def execute_branch(branch: Dict[str, Any], branch_id: str):
            start_time = time.time()

            try:
                # 检查是否有子图
                subgraph = branch.get("subgraph")
                if subgraph:
                    # 执行子图
                    branch_result = await self.base_executor.execute(
                        subgraph,
                        context,
                        trace_id and f"{trace_id}_branch_{branch_id}"
                    )
                else:
                    # 模拟执行（实际可调用其他 handler）
                    await asyncio.sleep(0.1)
                    branch_result = {"status": "completed", "output": branch.get("description", "")}

                duration = time.time() - start_time

                self.collaboration_state["branches"][branch_id] = {
                    "status": "completed",
                    "outputs": branch_result.outputs if hasattr(branch_result, 'outputs') else branch_result,
                    "duration": duration
                }

                return branch_id, branch_result

            except Exception as e:
                duration = time.time() - start_time
                errors[branch_id] = str(e)

                self.collaboration_state["branches"][branch_id] = {
                    "status": "failed",
                    "error": str(e),
                    "duration": duration
                }

                return branch_id, None

        # 限制并发数
        branch_tasks = []
        for i, branch in enumerate(branches[:max_parallel]):
            task = asyncio.create_task(execute_branch(branch, f"branch_{i}"))
            branch_tasks.append(task)

        # 等待所有分支完成
        await asyncio.gather(*branch_tasks, return_exceptions=True)

        return {
            "completed": len(results),
            "failed": len(errors),
            "results": results,
            "errors": errors
        }

    async def execute_with_retry(
        self,
        node: Node,
        context: Dict[str, Any],
        handler: Any,
        trace_id: Optional[str] = None,
        max_retries: Optional[int] = None,
        retry_strategy: str = "exponential"
    ) -> NodeExecutionOutput:
        """
        带重试的执行

        Args:
            node: 节点定义
            context: 执行上下文
            handler: 节点处理器
            trace_id: 追踪 ID
            max_retries: 最大重试次数
            retry_strategy: 重试策略 (exponential, linear, fixed)
        """
        max_retries = max_retries or self.config["default_retry_budget"]

        # 初始化重试预算
        node_id = node.id
        if node_id not in self.collaboration_state["retry_budgets"]:
            self.collaboration_state["retry_budgets"][node_id] = max_retries

        last_error = None

        for attempt in range(max_retries + 1):
            try:
                # 执行节点
                result = await handler.run(node, context)

                # 成功，清除重试预算记录
                if node_id in self.collaboration_state["retry_budgets"]:
                    del self.collaboration_state["retry_budgets"][node_id]

                return result

            except Exception as e:
                last_error = e
                remaining_retries = max_retries - attempt

                if remaining_retries > 0:
                    # 计算延迟
                    delay = self._calculate_retry_delay(attempt, retry_strategy)
                    await asyncio.sleep(delay)

                    # 记录重试
                    print(f"Retry {attempt + 1}/{max_retries} for node {node_id} after {delay}s: {str(e)}")
                else:
                    # 重试耗尽
                    raise

        raise last_error

    def _calculate_retry_delay(self, attempt: int, strategy: str) -> float:
        """计算重试延迟"""
        if strategy == "exponential":
            return min(self.config["retry_backoff_multiplier"] ** attempt, 60.0)
        elif strategy == "linear":
            return min(attempt * 2, 60.0)
        else:  # fixed
            return 2.0

    async def reach_consensus(
        self,
        inputs: Dict[str, Any],
        strategy: str = "majority",
        threshold: float = 0.5,
        timeout: Optional[float] = None
    ) -> Optional[Any]:
        """
        达成共识

        Args:
            inputs: 输入的多个结果
            strategy: 共识策略 (majority, unanimous, weighted, human)
            threshold: 阈值
            timeout: 超时时间
        """
        timeout = timeout or self.config["consensus_timeout"]

        try:
            if strategy == "majority":
                # 多数投票
                return await self._majority_consensus(inputs, threshold)
            elif strategy == "unanimous":
                # 全体一致
                return await self._unanimous_consensus(inputs, timeout)
            elif strategy == "weighted":
                # 加权投票
                return await self._weighted_consensus(inputs)
            elif strategy == "human":
                # 需要人工介入
                return None  # 需要前端处理
            else:
                return self._simple_consensus(inputs)

        except asyncio.TimeoutError:
            print(f"Consensus timeout after {timeout}s")
            return None

    async def _majority_consensus(self, inputs: Dict[str, Any], threshold: float) -> Any:
        """多数投票共识"""
        # 统计各结果出现次数
        from collections import Counter
        values = [str(v) for v in inputs.values()]
        counts = Counter(values)

        total = len(values)
        most_common_value, count = counts.most_common(1)[0]

        if count / total >= threshold:
            return most_common_value
        return None

    async def _unanimous_consensus(self, inputs: Dict[str, Any], timeout: float) -> Any:
        """全体一致共识"""
        unique_values = set(inputs.values())

        if len(unique_values) == 1:
            return list(unique_values)[0]

        # 返回 None 表示未达成共识
        return None

    async def _weighted_consensus(self, inputs: Dict[str, Any]) -> Any:
        """加权投票共识"""
        # 简单实现：返回第一个非空值
        for v in inputs.values():
            if v is not None:
                return v
        return None

    def _simple_consensus(self, inputs: Dict[str, Any]) -> Any:
        """简单共识：返回第一个值"""
        return list(inputs.values())[0] if inputs else None

    def save_checkpoint(self, checkpoint_id: str, context: Dict[str, Any]):
        """保存检查点"""
        if self.config["enable_checkpoints"]:
            self.collaboration_state["checkpoints"][checkpoint_id] = {
                "context": context.copy(),
                "timestamp": time.time()
            }

    def restore_checkpoint(self, checkpoint_id: str) -> Optional[Dict[str, Any]]:
        """恢复检查点"""
        checkpoint = self.collaboration_state.get("checkpoints", {}).get(checkpoint_id)
        if checkpoint:
            return checkpoint.get("context")
        return None

    def get_retry_budget(self, node_id: str) -> int:
        """获取节点剩余重试次数"""
        return self.collaboration_state.get("retry_budgets", {}).get(node_id, 0)

    def set_retry_budget(self, node_id: str, budget: int):
        """设置节点重试次数"""
        self.collaboration_state["retry_budgets"][node_id] = budget

    def decrement_retry_budget(self, node_id: str) -> int:
        """递减并返回剩余重试次数"""
        current = self.get_retry_budget(node_id)
        new_budget = max(0, current - 1)
        self.set_retry_budget(node_id, new_budget)
        return new_budget


# 全局实例
collaboration_executor = CollaborationExecutor()