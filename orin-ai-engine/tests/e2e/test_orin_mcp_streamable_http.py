import os

import httpx
import pytest
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client


@pytest.mark.asyncio
async def test_orin_mcp_streamable_http_roundtrip():
    if os.getenv("ORIN_MCP_E2E") != "1":
        return
    url = os.environ["ORIN_MCP_E2E_URL"].rstrip("/") + "/v1/mcp"
    api_key = os.environ["ORIN_MCP_E2E_API_KEY"]
    async with httpx.AsyncClient(headers={"Authorization": f"Bearer {api_key}"}) as client:
        async with streamable_http_client(url, http_client=client) as (read, write, _):
            async with ClientSession(read, write) as session:
                await session.initialize()
                tools = await session.list_tools()
                assert tools.tools
                result = await session.call_tool(tools.tools[0].name, {"message": "ping"})
                assert result.content
