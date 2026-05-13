from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.engine.mcp_client_manager import mcp_client_manager
router = APIRouter(prefix="/api/mcp", tags=["mcp"])


class McpToolCallRequest(BaseModel):
    arguments: dict[str, object] | None = None


@router.get("/services/{service_id}/tools")
async def list_mcp_tools(service_id: int) -> dict[str, object]:
    try:
        return {"serviceId": service_id, "tools": await mcp_client_manager.list_tools(service_id)}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.post("/services/{service_id}/tools/{tool_name}/call")
async def call_mcp_tool(service_id: int, tool_name: str, request: McpToolCallRequest) -> dict[str, object]:
    try:
        return {
            "serviceId": service_id,
            "toolName": tool_name,
            "result": await mcp_client_manager.call_tool(service_id, tool_name, request.arguments or {}),
        }
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
