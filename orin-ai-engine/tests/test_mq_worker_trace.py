"""
MQ worker `_process_message` W3C traceparent 透传测试。

策略：mock `AbstractIncomingMessage`，patch `_is_duplicate` / `_execute_task`
/ `_write_result` 为 AsyncMock 并在 `_is_duplicate` 内 snapshot `current()`，
验证 inbound header 是否被抽到、缺 header 是否生成、finally 是否清空、
JSON DTO 的 `trace_id` 字段是否不受影响。
"""
from __future__ import annotations

import json
from contextlib import asynccontextmanager
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.core import trace_context
from app.core.trace_context import bind_from_traceparent
from app.core.w3c_trace import build_traceparent
from app.engine.mq_worker import CollabMQWorker, CollabTaskMessage


# ---- helpers ----


def make_message(headers: dict[str, Any] | None, body_dict: dict[str, Any]) -> MagicMock:
    """构造 mock AbstractIncomingMessage，body 是 json bytes。"""
    msg = MagicMock()
    msg.body = json.dumps(body_dict).encode()
    msg.headers = headers if headers is not None else None

    # aio-pika message.process(requeue=...) 是 async context manager
    @asynccontextmanager
    async def _process(requeue: bool = False):
        yield

    msg.process = _process
    return msg


VALID_TASK_BODY = {
    "packageId": "pkg-trace",
    "subTaskId": "sub-trace",
    "attempt": 0,
    "collaborationMode": "PARALLEL",
    "expectedRole": "WORKFLOW",
    "description": "trace test",
    "inputData": "{}",
    "traceId": "json-dto-trace-id",  # JSON 通道的 trace_id
    "maxRetries": 0,
    "timeoutMillis": 60000,
    "executionStrategy": "AGENT",
}


@pytest.fixture(autouse=True)
def reset_context() -> None:
    trace_context.clear()
    yield
    trace_context.clear()


# ---- 1. inbound 抽到 ----


@pytest.mark.asyncio
async def test_process_extractsTraceparentFromMessageHeaders() -> None:
    inbound_trace = "a" * 32
    inbound_span = "b" * 16
    header = build_traceparent(inbound_trace, inbound_span)

    worker = CollabMQWorker()
    snapshot: dict[str, str | None] = {}

    async def fake_is_dup(task: CollabTaskMessage) -> bool:
        snapshot["traceId"], snapshot["spanId"] = trace_context.current()
        return True  # duplicate → 直接返回，不进 _execute_task

    worker._is_duplicate = fake_is_dup  # type: ignore[assignment]
    worker._execute_task = AsyncMock()  # type: ignore[assignment]
    worker._write_result = AsyncMock()  # type: ignore[assignment]

    msg = make_message(headers={"traceparent": header}, body_dict=VALID_TASK_BODY)
    await worker._process_message(msg)

    assert snapshot["traceId"] == inbound_trace
    assert snapshot["spanId"] == inbound_span


# ---- 2. 缺 header 生成 ----


@pytest.mark.asyncio
async def test_process_missingHeader_generates() -> None:
    worker = CollabMQWorker()
    snapshot: dict[str, str | None] = {}

    async def fake_is_dup(task: CollabTaskMessage) -> bool:
        snapshot["traceId"], snapshot["spanId"] = trace_context.current()
        return True

    worker._is_duplicate = fake_is_dup  # type: ignore[assignment]
    worker._execute_task = AsyncMock()  # type: ignore[assignment]
    worker._write_result = AsyncMock()  # type: ignore[assignment]

    # headers=None
    msg_none = make_message(headers=None, body_dict=VALID_TASK_BODY)
    await worker._process_message(msg_none)
    assert snapshot["traceId"] is not None
    assert len(snapshot["traceId"]) == 32
    assert snapshot["spanId"] is not None
    assert len(snapshot["spanId"]) == 16


@pytest.mark.asyncio
async def test_process_emptyHeadersDict_generates() -> None:
    worker = CollabMQWorker()
    snapshot: dict[str, str | None] = {}

    async def fake_is_dup(task: CollabTaskMessage) -> bool:
        snapshot["traceId"], snapshot["spanId"] = trace_context.current()
        return True

    worker._is_duplicate = fake_is_dup  # type: ignore[assignment]
    worker._execute_task = AsyncMock()  # type: ignore[assignment]
    worker._write_result = AsyncMock()  # type: ignore[assignment]

    msg = make_message(headers={}, body_dict=VALID_TASK_BODY)
    await worker._process_message(msg)
    assert snapshot["traceId"] is not None


# ---- 3. finally clear ----


@pytest.mark.asyncio
async def test_process_clearsAfterHandling() -> None:
    worker = CollabMQWorker()
    worker._is_duplicate = AsyncMock(return_value=True)  # type: ignore[assignment]
    worker._execute_task = AsyncMock()  # type: ignore[assignment]
    worker._write_result = AsyncMock()  # type: ignore[assignment]

    header = build_traceparent("c" * 32, "d" * 16)
    msg = make_message(headers={"traceparent": header}, body_dict=VALID_TASK_BODY)

    await worker._process_message(msg)

    # finally 清理
    assert trace_context.current() == (None, None)


@pytest.mark.asyncio
async def test_process_clearsEvenOnException() -> None:
    """handler 抛异常时 finally 也要清。注意：_process_message 内部已有
    `except Exception` 吞掉 RuntimeError，但 finally 仍要清 contextvar。"""
    worker = CollabMQWorker()

    async def boom(task: CollabTaskMessage) -> bool:
        raise RuntimeError("kaboom")

    worker._is_duplicate = boom  # type: ignore[assignment]

    header = build_traceparent("e" * 32, "f" * 16)
    msg = make_message(headers={"traceparent": header}, body_dict=VALID_TASK_BODY)

    # 不应抛 —— 外层 except 已吞
    await worker._process_message(msg)

    # 但 finally 必须清空
    assert trace_context.current() == (None, None)


# ---- 4. JSON DTO trace_id 字段不受影响 ----


@pytest.mark.asyncio
async def test_process_legacyDtoTraceIdUntouched() -> None:
    """JSON body 里的 trace_id 字段是独立通道，不应被 W3C header 覆盖。"""
    worker = CollabMQWorker()
    captured_task: dict[str, Any] = {}

    async def fake_is_dup(task: CollabTaskMessage) -> bool:
        captured_task["trace_id"] = task.trace_id
        return True

    worker._is_duplicate = fake_is_dup  # type: ignore[assignment]
    worker._execute_task = AsyncMock()  # type: ignore[assignment]
    worker._write_result = AsyncMock()  # type: ignore[assignment]

    # header 用 W3C，但 body 里 trace_id 字段是另一条路径
    header = build_traceparent("9" * 32, "0" * 16)
    msg = make_message(headers={"traceparent": header}, body_dict=VALID_TASK_BODY)

    await worker._process_message(msg)

    # body 的 traceId 字段保留为原值，未被 W3C header 改写
    assert captured_task["trace_id"] == "json-dto-trace-id"
