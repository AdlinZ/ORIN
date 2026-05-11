from fastapi import FastAPI
from app.api.workflow import router as workflow_router
from app.api.collaboration import router as collaboration_router
from app.api.collaboration import ensure_worker_started, ensure_worker_stopped
from app.api.playground_runtime import router as playground_runtime_router
from app.api.mcp import router as mcp_router
from app.core.config import settings
from app.engine.mq_worker import get_mq_dependency_status

app = FastAPI(title="ORIN AI Engine", version="0.1.0")

app.include_router(workflow_router, prefix="/api/v1")
app.include_router(collaboration_router)
app.include_router(playground_runtime_router)
app.include_router(mcp_router)


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
