from __future__ import annotations

import asyncio
import os
import sys
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Any, Mapping
from urllib.parse import urlparse

from mcp import ClientSession
from mcp.client.streamable_http import create_mcp_http_client, streamable_http_client
from mcp.server import NotificationOptions, Server
from mcp.server.models import InitializationOptions
from mcp.server.stdio import stdio_server
import mcp.types as types

VERSION = "0.1.0"


class ConfigError(ValueError):
    pass


@dataclass(frozen=True)
class BridgeConfig:
    base_url: str
    api_key: str
    origin: str

    @property
    def endpoint(self) -> str:
        return f"{self.base_url}/v1/mcp"


def load_config(env: Mapping[str, str] | None = None) -> BridgeConfig:
    values = os.environ if env is None else env
    base_url = _required(values, "ORIN_BASE_URL").rstrip("/")
    api_key = _required(values, "ORIN_API_KEY")
    origin = values.get("ORIN_MCP_ORIGIN", "").strip() or derive_origin(base_url)
    return BridgeConfig(base_url=base_url, api_key=api_key, origin=origin)


def derive_origin(base_url: str) -> str:
    parsed = urlparse(base_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ConfigError("ORIN_BASE_URL must be an absolute http(s) URL")
    return f"{parsed.scheme}://{parsed.netloc}"


def build_headers(config: BridgeConfig) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {config.api_key}",
        "Origin": config.origin,
    }


async def list_remote_tools(config: BridgeConfig) -> list[types.Tool]:
    async with remote_session(config) as session:
        result = await session.list_tools()
        return [types.Tool.model_validate(tool.model_dump()) for tool in result.tools]


async def call_remote_tool(
    config: BridgeConfig,
    name: str,
    arguments: dict[str, Any] | None,
) -> types.CallToolResult:
    async with remote_session(config) as session:
        result = await session.call_tool(name, arguments or {})
        return types.CallToolResult.model_validate(result.model_dump())


def error_result(message: str) -> types.CallToolResult:
    return types.CallToolResult(
        isError=True,
        content=[types.TextContent(type="text", text=message)],
    )


@asynccontextmanager
async def remote_session(config: BridgeConfig):
    async with create_mcp_http_client(headers=build_headers(config)) as http_client:
        async with streamable_http_client(config.endpoint, http_client=http_client) as (
            read_stream,
            write_stream,
            _,
        ):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                yield session


def create_server(config: BridgeConfig) -> Server:
    app = Server("orin-mcp-bridge", version=VERSION)

    @app.list_tools()
    async def handle_list_tools() -> list[types.Tool]:
        try:
            return await list_remote_tools(config)
        except Exception as exc:
            _log(f"tools/list failed: {exc}")
            raise RuntimeError(f"ORIN MCP endpoint unavailable: {exc}") from exc

    @app.call_tool(validate_input=False)
    async def handle_call_tool(name: str, arguments: dict[str, Any] | None) -> types.CallToolResult:
        try:
            return await call_remote_tool(config, name, arguments)
        except Exception as exc:
            _log(f"tools/call failed: {exc}")
            return error_result(f"ORIN MCP endpoint unavailable: {exc}")

    return app


async def run_stdio(config: BridgeConfig) -> None:
    app = create_server(config)
    options = InitializationOptions(
        server_name="orin-mcp-bridge",
        server_version=VERSION,
        capabilities=app.get_capabilities(
            NotificationOptions(tools_changed=False),
            experimental_capabilities={},
        ),
    )
    _log(f"starting bridge: endpoint={config.endpoint} origin={config.origin}")
    async with stdio_server() as (read_stream, write_stream):
        await app.run(read_stream, write_stream, options)


async def async_main() -> int:
    try:
        config = load_config()
    except ConfigError as exc:
        _log(str(exc))
        return 2
    await run_stdio(config)
    return 0


def main() -> None:
    raise SystemExit(asyncio.run(async_main()))


def _required(env: Mapping[str, str], name: str) -> str:
    value = env.get(name, "").strip()
    if not value:
        raise ConfigError(f"{name} is required")
    return value


def _log(message: str) -> None:
    print(f"[orin-mcp-bridge] {message}", file=sys.stderr, flush=True)
