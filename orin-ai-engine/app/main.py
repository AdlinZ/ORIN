import logging

from fastapi import FastAPI

from app.api.workflow import router as workflow_router
from app.api.collaboration import router as collaboration_router
from app.api.collaboration import ensure_worker_started, ensure_worker_stopped
from app.api.playground_runtime import router as playground_runtime_router
from app.api.mcp import router as mcp_router
from app.core.config import settings
from app.core.logging_filter import TraceContextFilter
from app.core.trace_middleware import TraceContextMiddleware
from app.engine.mq_worker import get_mq_dependency_status

app = FastAPI(title="ORIN AI Engine", version="0.1.0")

# 全局 logging filter：让每条 LogRecord 自带 traceId/spanId 字段（来自当前
# contextvar）。在 root logger 上挂一次即可，子 logger 自动继承。
logging.getLogger().addFilter(TraceContextFilter())

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
