from fastapi import FastAPI
from app.api.workflow import router as workflow_router
from app.api.collaboration import router as collaboration_router

app = FastAPI(title="ORIN AI Engine", version="0.1.0")

app.include_router(workflow_router, prefix="/api/v1")
app.include_router(collaboration_router)

@app.get("/health")
async def health_check():
    return {"status": "ok"}
