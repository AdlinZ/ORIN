"""
contextvars 存储层测试：bind/clear 序列、bind_from_traceparent 兜底、
asyncio task 隔离、clear 幂等。
"""
from __future__ import annotations

import asyncio
import re

import pytest

from app.core import trace_context
from app.core.trace_context import (
    bind_from_traceparent,
    bind_raw,
    clear,
    current,
)


@pytest.fixture(autouse=True)
def reset_context() -> None:
    """每个测试前后清空 contextvar，避免污染。"""
    clear()
    yield
    clear()


# ---- bind/clear 序列 ----


def test_current_whenUnset_returnsNoneTuple() -> None:
    assert current() == (None, None)


def test_bindRaw_andCurrent_roundTrip() -> None:
    bind_raw("a" * 32, "b" * 16)
    assert current() == ("a" * 32, "b" * 16)


def test_clear_whenSet_resetsToNone() -> None:
    bind_raw("a" * 32, "b" * 16)
    clear()
    assert current() == (None, None)


def test_clear_whenUnset_doesNotRaise() -> None:
    # 幂等
    clear()
    clear()
    assert current() == (None, None)


# ---- bind_from_traceparent ----


def test_bindFromTraceparent_validHeader_setsContextvars() -> None:
    header = "00-" + "a" * 32 + "-" + "b" * 16 + "-01"
    tp = bind_from_traceparent(header)
    assert tp.trace_id == "a" * 32
    assert tp.span_id == "b" * 16
    assert current() == ("a" * 32, "b" * 16)


def test_bindFromTraceparent_invalidHeader_generatesFresh() -> None:
    for bad in [None, "", "garbage", "00-" + "z" * 32 + "-" + "b" * 16 + "-01"]:
        clear()
        tp = bind_from_traceparent(bad)
        # 兜底生成新 trace_id / span_id，且各自满足长度
        assert re.match(r"^[0-9a-f]{32}$", tp.trace_id)
        assert re.match(r"^[0-9a-f]{16}$", tp.span_id)
        assert current() == (tp.trace_id, tp.span_id)


def test_bindFromTraceparent_returnsBoundForResponseHeaderUse() -> None:
    # 即使 input 是合法 header，返回值也应是 Traceparent（用于 HTTP
    # middleware 在响应头里回传）
    header = "00-" + "c" * 32 + "-" + "d" * 16 + "-01"
    tp = bind_from_traceparent(header)
    assert tp.trace_id == "c" * 32
    assert tp.span_id == "d" * 16


# ---- asyncio task 隔离 ----


@pytest.mark.asyncio
async def test_concurrentTasks_isolated() -> None:
    """两个 task 各自 bind 后，current() 只看到自己的，验证 ContextVar 隔离。"""

    async def task_a() -> tuple[str, str]:
        bind_raw("a" * 32, "a" * 16)
        await asyncio.sleep(0.01)  # 让出控制权
        return current()

    async def task_b() -> tuple[str, str]:
        bind_raw("b" * 32, "b" * 16)
        await asyncio.sleep(0.01)
        return current()

    res_a, res_b = await asyncio.gather(task_a(), task_b())
    assert res_a == ("a" * 32, "a" * 16)
    assert res_b == ("b" * 32, "b" * 16)


@pytest.mark.asyncio
async def test_clearInSubTask_doesNotLeakToParent() -> None:
    """子 task 内 clear 不影响父 task 的 trace context（asyncio.create_task
    会 copy 当前 context，子 task set/clear 不会反写到父 task）。"""

    async def child() -> None:
        bind_raw("c" * 32, "c" * 16)
        clear()

    bind_raw("p" * 32, "p" * 16)
    # create_task 会 copy context，child 的 set/clear 都在 copy 上
    await asyncio.create_task(child())
    # 父 task 自己的值还在
    assert current() == ("p" * 32, "p" * 16)
