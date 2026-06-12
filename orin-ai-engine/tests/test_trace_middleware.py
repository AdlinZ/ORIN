"""
FastAPI middleware 测试：探针路由读 `current()`，验证入站 traceparent
传播、缺 header 时生成、finally 清空、/health 跳过、log 注入。
"""
from __future__ import annotations

import logging
import re

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core import trace_context
from app.core.logging_filter import TraceContextFilter
from app.core.trace_middleware import TraceContextMiddleware


@pytest.fixture
def app_with_probe() -> FastAPI:
    """构造一个最小 FastAPI app，仅含探针路由 + middleware。"""
    app = FastAPI()
    # 注册到 root logger（与生产 main.py 一致），覆盖探针 logger
    root = logging.getLogger()
    root.addFilter(TraceContextFilter())
    app.add_middleware(TraceContextMiddleware)

    @app.get("/probe")
    @app.post("/probe")
    async def probe() -> dict[str, str | None]:
        tid, sid = trace_context.current()
        return {"traceId": tid, "spanId": sid}

    @app.get("/_log")
    async def log_something() -> dict[str, str]:
        logging.getLogger("test.middleware").info("inside-handler")
        return {"ok": "ok"}

    return app


@pytest.fixture
def client(app_with_probe: FastAPI) -> TestClient:
    return TestClient(app_with_probe)


@pytest.fixture(autouse=True)
def reset_context() -> None:
    trace_context.clear()
    yield
    trace_context.clear()


# ---- 1. inbound traceparent 透传 ----


def test_middleware_propagatesInboundTraceparent(client: TestClient) -> None:
    trace_id = "a" * 32
    span_id = "b" * 16
    header = f"00-{trace_id}-{span_id}-01"

    res = client.get("/probe", headers={"traceparent": header})

    assert res.status_code == 200
    assert res.json() == {"traceId": trace_id, "spanId": span_id}


def test_middleware_traceparentHeaderCaseInsensitive(client: TestClient) -> None:
    # Starlette Headers 大小写不敏感
    res = client.get("/probe", headers={"TraceParent": "00-" + "c" * 32 + "-" + "d" * 16 + "-01"})
    assert res.json() == {"traceId": "c" * 32, "spanId": "d" * 16}


# ---- 2. 缺 header 生成 ----


def test_middleware_missingHeader_generatesFresh(client: TestClient) -> None:
    res = client.get("/probe")
    body = res.json()
    assert body["traceId"] is not None
    assert body["spanId"] is not None
    assert re.match(r"^[0-9a-f]{32}$", body["traceId"])
    assert re.match(r"^[0-9a-f]{16}$", body["spanId"])


def test_middleware_invalidHeader_generatesFresh(client: TestClient) -> None:
    res = client.get("/probe", headers={"traceparent": "garbage"})
    body = res.json()
    assert re.match(r"^[0-9a-f]{32}$", body["traceId"])


# ---- 3. 第二次请求 trace 不串（验证 finally clear） ----


def test_middleware_clearsBetweenRequests(client: TestClient) -> None:
    # 第一次带 header
    res1 = client.get("/probe", headers={"traceparent": "00-" + "1" * 32 + "-" + "2" * 16 + "-01"})
    # 第二次无 header —— 应该是新生成的 traceId，与第一次不同
    res2 = client.get("/probe")
    t1 = res1.json()["traceId"]
    t2 = res2.json()["traceId"]
    assert t1 != t2


# ---- 4. /health 跳过 ----


def test_middleware_skipsHealthRoute(app_with_probe: FastAPI) -> None:
    # 单独构造一个含 health 路由的 app
    @app_with_probe.get("/health")
    async def health() -> dict[str, str]:
        # 在 health 路由里读 contextvar 应该是 None（被跳过）
        tid, sid = trace_context.current()
        return {"traceId": tid or "skipped", "spanId": sid or "skipped"}

    c = TestClient(app_with_probe)
    res = c.get("/health", headers={"traceparent": "00-" + "a" * 32 + "-" + "b" * 16 + "-01"})
    assert res.status_code == 200
    # health 路径不在 middleware 覆盖范围，contextvar 仍是未绑定
    assert res.json() == {"traceId": "skipped", "spanId": "skipped"}


# ---- 5. 注入 logging filter ----


def test_middleware_loggingRecordCarriesTraceContext(client: TestClient, caplog) -> None:
    trace_id = "f" * 32
    span_id = "e" * 16

    # 在目标 logger 上**显式**挂 filter（与 main.py 全局挂等价；这里显式挂
    # 是为了不被 caplog 的内部 handler 链路绕过）
    target_logger = logging.getLogger("test.middleware")
    target_logger.addFilter(TraceContextFilter())
    try:
        with caplog.at_level(logging.INFO, logger="test.middleware"):
            client.get(
                "/_log",
                headers={"traceparent": f"00-{trace_id}-{span_id}-01"},
            )
        matched = [r for r in caplog.records
                   if r.name == "test.middleware" and r.message == "inside-handler"]
        assert len(matched) >= 1
        rec = matched[0]
        assert rec.traceId == trace_id
        assert rec.spanId == span_id
    finally:
        target_logger.removeFilter(TraceContextFilter())
