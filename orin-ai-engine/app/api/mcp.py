from fastapi import APIRouter, HTTPException

from app.engine.mcp_client_manager import mcp_client_manager
router = APIRouter(prefix="/api/mcp", tags=["mcp"])

@router.get("/services/{service_id}/tools")
async def list_mcp_tools(service_id: int) -> dict[str, object]:
    try:
        return {"serviceId": service_id, "tools": await mcp_client_manager.list_tools(service_id)}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
