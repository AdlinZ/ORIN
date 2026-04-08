"""
F2.4 Collaboration Executor 结果汇总回归测试

测试目标：验证协作执行器对并行分支、串行任务的结果汇总能力

运行方式：
    cd orin-ai-engine && python -m pytest tests/test_collaboration_executor.py -v
"""

import pytest
import asyncio
from unittest.mock import patch, MagicMock, AsyncMock
from app.engine.collaboration_executor import CollaborationExecutor
from app.models.workflow import (
    Node, Edge, WorkflowDSL, WorkflowContext,
    WorkflowStatus, NodeStatus
)


class TestCollaborationExecutorResultAggregation:
    """协作执行器结果汇总测试"""

    @pytest.mark.asyncio
    async def test_parallel_branches_all_success(self):
        """所有并行分支成功时，分支状态应正确记录"""
        executor = CollaborationExecutor()

        # 初始化协作状态（execute_parallel_branches 不会自动初始化）
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        branches = [
            {"id": "b1", "description": "Branch 1", "subgraph": None},
            {"id": "b2", "description": "Branch 2", "subgraph": None},
            {"id": "b3", "description": "Branch 3", "subgraph": None},
        ]

        await executor.execute_parallel_branches(
            branches=branches,
            context={"query": "test"},
            max_parallel=3
        )

        # 验证分支状态（通过 collaboration_state 验证）
        # 注意：execute_parallel_branches 返回值已修复，gather 结果正确填充到 results 字典
        for i in range(3):
            branch_id = f"branch_{i}"
            assert executor.collaboration_state["branches"][branch_id]["status"] == "completed"

    @pytest.mark.asyncio
    async def test_parallel_branches_with_subgraph_failure(self):
        """当提供 subgraph 时，分支失败应被正确记录"""
        executor = CollaborationExecutor()

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        # Mock base_executor.execute to raise error
        async def mock_execute(*args, **kwargs):
            raise RuntimeError("Subgraph execution failed")

        with patch.object(executor.base_executor, 'execute', side_effect=mock_execute):
            # 提供 subgraph 这样 base_executor.execute 会被调用
            branches = [
                {"description": "Branch with subgraph", "subgraph": WorkflowDSL(
                    version="1.0",
                    nodes=[Node(id="1", type="start")],
                    edges=[]
                )},
            ]

            await executor.execute_parallel_branches(
                branches=branches,
                context={},
                max_parallel=1
            )

            # 验证分支失败状态
            assert executor.collaboration_state["branches"]["branch_0"]["status"] == "failed"
            assert "error" in executor.collaboration_state["branches"]["branch_0"]
            assert "Subgraph execution failed" in executor.collaboration_state["branches"]["branch_0"]["error"]

    @pytest.mark.asyncio
    async def test_branch_result_contains_duration(self):
        """每个分支结果应包含执行时长"""
        executor = CollaborationExecutor()

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        branches = [
            {"description": "Quick branch"},
        ]

        await executor.execute_parallel_branches(
            branches=branches,
            context={},
            max_parallel=1
        )

        branch_0 = executor.collaboration_state["branches"]["branch_0"]
        assert "duration" in branch_0
        assert branch_0["duration"] >= 0

    @pytest.mark.asyncio
    async def test_max_parallel_limit(self):
        """超过最大并行数时应该被限制"""
        executor = CollaborationExecutor()
        executor.config["max_parallel_branches"] = 2

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        branches = [
            {"description": f"Branch {i}"} for i in range(5)
        ]

        await executor.execute_parallel_branches(
            branches=branches,
            context={},
            max_parallel=5  # 请求5个，但配置限制为2
        )

        # 实际只执行了2个
        assert len(executor.collaboration_state["branches"]) == 2

    @pytest.mark.asyncio
    async def test_collaboration_state_initialization(self):
        """协作状态应正确初始化"""
        executor = CollaborationExecutor()

        dsl = WorkflowDSL(
            version="1.0",
            nodes=[Node(id="1", type="start")],
            edges=[]
        )

        await executor.execute_collaboration(
            dsl=dsl,
            initial_inputs={"query": "test"},
            trace_id="test_trace_123"
        )

        # 验证协作状态
        assert executor.collaboration_state["trace_id"] == "test_trace_123"
        assert "start_time" in executor.collaboration_state
        assert "branches" in executor.collaboration_state
        assert "retry_budgets" in executor.collaboration_state
        assert "checkpoints" in executor.collaboration_state

    @pytest.mark.asyncio
    async def test_branch_summary_in_output(self):
        """并行分支结果应汇总到输出的 __branch_summary"""
        executor = CollaborationExecutor()

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        branches = [
            {"description": "Branch 1"},
            {"description": "Branch 2"},
        ]

        # 先执行分支
        await executor.execute_parallel_branches(
            branches=branches,
            context={},
            max_parallel=2
        )

        # 模拟一个最终的执行结果
        mock_result = MagicMock()
        mock_result.outputs = {}

        # 处理分支结果
        processed = await executor._process_branch_results(mock_result, executor.config)

        # 验证分支汇总被添加到输出
        assert "__branch_summary" in processed.outputs
        assert len(processed.outputs["__branch_summary"]) == 2


class TestCollaborationRetryLogic:
    """协作重试逻辑测试"""

    @pytest.mark.asyncio
    async def test_retry_budget_initialization(self):
        """重试预算应正确初始化"""
        executor = CollaborationExecutor()

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        assert executor.collaboration_state["retry_budgets"] == {}

    @pytest.mark.asyncio
    async def test_default_retry_budget_config(self):
        """默认重试预算应从配置读取"""
        executor = CollaborationExecutor()

        assert executor.config["default_retry_budget"] == 3
        assert executor.config["retry_backoff_multiplier"] == 2.0


class TestCollaborationCheckpoint:
    """协作检查点测试"""

    @pytest.mark.asyncio
    async def test_checkpoint_storage(self):
        """检查点应能正确存储和检索"""
        executor = CollaborationExecutor()

        # 初始化协作状态
        executor._init_collaboration_state(
            dsl=WorkflowDSL(version="1.0", nodes=[], edges=[]),
            trace_id="test"
        )

        # 存储一个检查点
        executor.collaboration_state["checkpoints"]["cp1"] = {
            "context": {"x": 1, "y": 2},
            "timestamp": 1234567890.0
        }

        # 验证检查点可检索
        assert "cp1" in executor.collaboration_state["checkpoints"]
        assert executor.collaboration_state["checkpoints"]["cp1"]["context"]["x"] == 1


# =============================================================================
# 运行入口
# =============================================================================

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
