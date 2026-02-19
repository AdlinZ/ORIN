from fastapi import FastAPI
from app.api.workflow import router as workflow_router

app = FastAPI(title="ORIN AI Engine", version="0.1.0")

app.include_router(workflow_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {"status": "ok"}
