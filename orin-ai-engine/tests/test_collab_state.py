"""
F2.5 Collaboration State Pause/Resume 关键测试

测试目标：验证协作执行器的暂停/恢复能力

pause/resume 是协作执行的重要控制能力，支持：
- 暂停协作包执行（节点 await wait_if_paused 会阻塞）
- 恢复协作包执行（解除阻塞，继续运行）
- 超时处理（暂停过久的任务可以超时中断）

运行方式：
    cd orin-ai-engine && python -m pytest tests/test_collab_state.py -v
"""

import pytest
import asyncio
from unittest.mock import patch, MagicMock
from app.core.collab_state import (
    set_paused,
    set_resumed,
    is_paused,
    wait_if_paused,
    _pause_events,
    _get_pause_event,
)


class TestPauseResume:
    """pause/resume 功能测试"""

    TEST_PACKAGE_IDS = [
        "test_pause_001",
        "test_resume_001",
        "test_sync_001",
        "test_not_paused_001",
        "test_resume_wait_001",
        "test_timeout_001",
        "test_multi_a",
        "test_multi_b",
    ]

    def setup_method(self):
        """每个测试前清理 pause events 和持久化控制标志"""
        _pause_events.clear()
        for package_id in self.TEST_PACKAGE_IDS:
            set_resumed(package_id)
        _pause_events.clear()

    def test_set_paused_and_is_paused(self):
        """设置暂停后 is_paused 应返回 True"""
        package_id = "test_pause_001"

        # 初始状态不应是暂停
        assert not is_paused(package_id)

        # 设置暂停
        set_paused(package_id)

        # 现在应该是暂停状态
        assert is_paused(package_id)

    def test_set_resumed_after_paused(self):
        """暂停后恢复，is_paused 应返回 False"""
        package_id = "test_resume_001"

        # 先暂停
        set_paused(package_id)
        assert is_paused(package_id)

        # 再恢复
        set_resumed(package_id)

        # 现在应该不是暂停状态
        assert not is_paused(package_id)

    def test_is_paused_memory_and_redis_sync(self):
        """is_paused 应同时检查内存 Event 和 Redis 标志"""
        package_id = "test_sync_001"

        # 模拟 Redis 中有暂停标志，但内存 Event 没有记录的情况
        # 这测试多实例部署后的状态同步
        with patch('app.core.collab_state.shared_memory') as mock_sm:
            mock_sm.get.return_value = {"paused": True, "requested_at": 1234567890.0}

            # 内存 Event 默认是 set()（运行中）
            ev = _get_pause_event(package_id)
            assert ev.is_set()

            # is_paused 应该检测到 Redis 标志并返回 True
            result = is_paused(package_id)
            assert result is True
            # 同时内存 Event 应该被同步清空
            assert not ev.is_set()

    @pytest.mark.asyncio
    async def test_wait_if_paused_when_not_paused(self):
        """未暂停时 wait_if_paused 应立即返回 True"""
        package_id = "test_not_paused_001"

        result = await wait_if_paused(package_id, timeout=1.0)

        assert result is True

    @pytest.mark.asyncio
    async def test_wait_if_paused_resumed_before_timeout(self):
        """暂停后恢复，wait_if_paused 应在恢复后返回 True"""
        package_id = "test_resume_wait_001"

        # 先暂停
        set_paused(package_id)

        async def resume_after_delay():
            """延迟 0.5 秒后恢复"""
            await asyncio.sleep(0.5)
            set_resumed(package_id)

        async def wait_task():
            result = await wait_if_paused(package_id, timeout=5.0)
            return result

        # 同时执行恢复和等待
        results = await asyncio.gather(
            resume_after_delay(),
            wait_task()
        )

        # wait_if_paused 应该在恢复后返回 True
        assert results[1] is True

    @pytest.mark.asyncio
    async def test_wait_if_paused_timeout(self):
        """暂停超时时 wait_if_paused 应返回 False"""
        package_id = "test_timeout_001"

        # 暂停但不恢复
        set_paused(package_id)

        # 等待超时（0.3秒超时）
        result = await wait_if_paused(package_id, timeout=0.3)

        # 应该返回 False（超时）
        assert result is False

    @pytest.mark.asyncio
    async def test_multiple_packages_independent(self):
        """多个协作包暂停状态应相互独立"""
        package_a = "test_multi_a"
        package_b = "test_multi_b"

        # 只暂停 package_a
        set_paused(package_a)

        # package_a 应该暂停，package_b 不应该
        assert is_paused(package_a)
        assert not is_paused(package_b)

        # 恢复 package_a，只影响自己
        set_resumed(package_a)

        assert not is_paused(package_a)
        assert not is_paused(package_b)


# =============================================================================
# 运行入口
# =============================================================================

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
