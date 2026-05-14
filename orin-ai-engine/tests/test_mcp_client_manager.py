import sys
from pathlib import Path
from unittest.mock import AsyncMock

import pytest

from app.engine import mcp_client_manager as mcp_module
from app.engine.mcp_client_manager import MCPClientManager


@pytest.mark.asyncio
async def test_list_tools_retries_after_transient_failure():
    async def config_provider(service_id: int):
        return {"id": service_id, "type": "STDIO", "command": "time"}

    manager = MCPClientManager(config_provider=config_provider, retries=1)
    manager._run_with_service = AsyncMock(side_effect=[RuntimeError("boom"), [{"name": "now"}]])

    tools = await manager.list_tools(1)

    assert tools == [{"name": "now"}]
    assert manager._run_with_service.await_count == 2


def test_stdio_command_must_use_allowed_template():
    manager = MCPClientManager()

    params = manager._build_stdio_params({"command": "fetch --user-agent ORIN"})

    assert params.command == "uvx"
    assert params.args == ["mcp-server-fetch", "--user-agent", "ORIN"]
    with pytest.raises(ValueError, match="not allowed"):
        manager._build_stdio_params({"command": "python arbitrary.py"})


def test_builtin_stdio_templates_are_registered():
    assert {"filesystem", "github", "fetch", "sqlite", "time"} <= set(mcp_module.STDIO_TEMPLATES)


def test_parse_env_accepts_sensitive_keys_resolved_by_backend():
    # 敏感 key 拦截已上移到后端；env 经可信 internal 接口到达，明文是必需的
    manager = MCPClientManager()

    env = manager._parse_env(
        "GITHUB_PERSONAL_ACCESS_TOKEN=ghp_realtoken\nWORKDIR=/srv\n# comment\nNOEQUALS"
    )

    assert env == {"GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_realtoken", "WORKDIR": "/srv"}


@pytest.mark.asyncio
async def test_stdio_fake_server_lists_and_calls_tool(tmp_path: Path, monkeypatch):
    server = tmp_path / "fake_mcp_server.py"
    server.write_text(
        'from mcp.server.fastmcp import FastMCP\n'
        'mcp = FastMCP("fake")\n'
        '@mcp.tool()\n'
        'def echo(text: str) -> str:\n    return text\n'
        'if __name__ == "__main__":\n    mcp.run(transport="stdio")\n'
    )
    monkeypatch.setitem(mcp_module.STDIO_TEMPLATES, "time", (sys.executable, [str(server)]))

    async def config_provider(service_id: int):
        return {"id": service_id, "type": "STDIO", "command": "time"}

    manager = MCPClientManager(config_provider=config_provider)
    tools = await manager.list_tools(1)
    result = await manager.call_tool(1, "echo", {"text": "hello"})

    assert [tool["name"] for tool in tools] == ["echo"]
    assert result["content"][0]["text"] == "hello"
