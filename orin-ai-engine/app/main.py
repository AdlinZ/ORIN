import logging

from fastapi import FastAPI

from app.api.workflow import router as workflow_router
from app.api.collaboration import router as collaboration_router
from app.api.collaboration import ensure_worker_started, ensure_worker_stopped
from app.api.playground_runtime import router as playground_runtime_router
from app.api.mcp import router as mcp_router
from app.core.config import settings
from app.core.logging_formatter import configure_logging
from app.core.otel_setup import setup_tracing, shutdown_tracing
from app.core.trace_middleware import TraceContextMiddleware
from app.engine.mq_worker import get_mq_dependency_status

app = FastAPI(title="ORIN AI Engine", version="0.1.0")

# 启动期根据 ORIN_LOG_JSON_FORMAT 选 JSON / 文本 formatter；幂等，
# 重复 import（pytest reload / uvicorn --reload）不会累加 handler。
# TraceContextFilter 在 `configure_logging` 内部挂到 handler 上（不挂 logger
# 自身 —— Python `callHandlers` 在 propagate 链上只调 handler.filter）。
configure_logging(
    json_format=settings.LOG_JSON_FORMAT,
    level=settings.LOG_LEVEL,
)

# OTel SDK init（Phase 2 小刀 5a）。`OTEL_SDK_DISABLED=true` 走 no-op；未设
# `OTEL_EXPORTER_OTLP_ENDPOINT` 时降级 ConsoleSpanExporter（stdout，方便本地
# 启动看 span）。setup_tracing 内部幂等，uvicorn --reload 双 import 安全。
setup_tracing()

app.include_router(workflow_router, prefix="/api/v1")
app.include_router(collaboration_router)
app.include_router(playground_runtime_router)
app.include_router(mcp_router)

# W3C traceparent middleware。Starlette add_middleware 是 LIFO：最后 add =
# 最外层 = 最早看到请求。所以放最后注册，让它在所有 router 之前拦截入站
# traceparent 并绑到 contextvar。
app.add_middleware(TraceContextMiddleware)


@app.on_event("startup")
async def startup_event():
    if settings.MQ_WORKER_AUTO_START:
        await ensure_worker_started()


@app.on_event("shutdown")
async def shutdown_event():
    await ensure_worker_stopped()
    # 强制 flush OTel BatchSpanProcessor 队列里未导出的 span，再 shutdown
    # provider。K8s rolling upgrade / SIGTERM 时不调会丢尾段 spans
    # （OTel SDK 默认后台 schedule thread，进程退出不保证 join）。
    shutdown_tracing()

def build_health_response():
    rabbitmq = get_mq_dependency_status()
    status = "ok" if rabbitmq["status"] in ("up", "disabled", "not_started", "stopped") else "degraded"
    return {
        "status": status,
        "service": "orin-ai-engine",
        "dependencies": {
            "rabbitmq": rabbitmq,
        },
    }


@app.get("/health")
@app.get("/v1/health")
async def health_check():
    return build_health_response()
