import asyncio
import contextlib
import logging
import shlex
from collections.abc import Awaitable, Callable
from datetime import timedelta
from typing import Any

import httpx
from mcp import ClientSession
from mcp.client.sse import sse_client
from mcp.client.stdio import StdioServerParameters, stdio_client
from mcp.client.streamable_http import streamablehttp_client

from app.core.config import settings

logger = logging.getLogger(__name__)

STDIO_TEMPLATES: dict[str, tuple[str, list[str]]] = {
    "filesystem": ("npx", ["-y", "@modelcontextprotocol/server-filesystem"]),
    "github": ("npx", ["-y", "@modelcontextprotocol/server-github"]),
    "fetch": ("uvx", ["mcp-server-fetch"]),
    "sqlite": ("uvx", ["mcp-server-sqlite"]),
    "time": ("uvx", ["mcp-server-time"]),
}


class MCPClientManager:
    def __init__(
        self,
        config_provider: Callable[[int], Awaitable[dict[str, Any]]] | None = None,
        retries: int = 1,
        timeout_seconds: float = 30.0,
    ):
        self.config_provider = config_provider or self._load_service_config
        self.retries = retries
        self.timeout_seconds = timeout_seconds

    async def list_tools(self, service_id: int) -> list[dict[str, Any]]:
        config = await self.config_provider(service_id)

        async def op(session: ClientSession) -> list[dict[str, Any]]:
            result = await session.list_tools()
            return [self._dump(tool) for tool in result.tools]

        return await self._with_retry(lambda: self._run_with_service(config, op))

    async def call_tool(self, service_id: int, tool_name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        if not tool_name or not tool_name.strip():
            raise ValueError("tool_name is required")
        config = await self.config_provider(service_id)

        async def op(session: ClientSession) -> dict[str, Any]:
            result = await session.call_tool(tool_name, arguments or {})
            return self._dump(result)

        return await self._with_retry(lambda: self._run_with_service(config, op))

    async def _load_service_config(self, service_id: int) -> dict[str, Any]:
        backend_base = (settings.ORIN_BACKEND_URL or "http://localhost:8080").rstrip("/")
        url = f"{backend_base}/api/system/mcp/internal/enabled/{service_id}"
        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            response = await client.get(url)
            response.raise_for_status()
            return response.json()

    async def _with_retry(self, operation: Callable[[], Awaitable[Any]]) -> Any:
        last_error: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                return await operation()
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                last_error = exc
                if attempt >= self.retries:
                    break
                await asyncio.sleep(0.15 * (attempt + 1))
        raise RuntimeError(f"MCP operation failed after {self.retries + 1} attempts: {last_error}") from last_error

    async def _run_with_service(self, config: dict[str, Any], operation: Callable[[ClientSession], Awaitable[Any]]) -> Any:
        async with self._connect(config) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream, read_timeout_seconds=timedelta(seconds=self.timeout_seconds)) as session:
                await session.initialize()
                return await operation(session)

    @contextlib.asynccontextmanager
    async def _connect(self, config: dict[str, Any]):
        service_type = str(config.get("type") or "STDIO").upper()
        if service_type == "STDIO":
            async with stdio_client(self._build_stdio_params(config)) as streams:
                yield streams
            return

        url = str(config.get("url") or "").strip()
        if not url.startswith(("http://", "https://")):
            raise ValueError("SSE/HTTP MCP service requires http(s) URL")
        if service_type == "SSE" or url.endswith("/sse"):
            async with sse_client(url, timeout=self.timeout_seconds) as streams:
                yield streams
            return
        async with streamablehttp_client(url, timeout=self.timeout_seconds) as (read_stream, write_stream, _):
            yield read_stream, write_stream

    def _build_stdio_params(self, config: dict[str, Any]) -> StdioServerParameters:
        command_text = str(config.get("command") or "").strip()
        parts = shlex.split(command_text)
        if not parts:
            raise ValueError("STDIO MCP service requires a template command")
        template = parts[0].lower()
        if template not in STDIO_TEMPLATES:
            raise ValueError(f"STDIO MCP template is not allowed: {template}")
        command, base_args = STDIO_TEMPLATES[template]
        return StdioServerParameters(
            command=command,
            args=[*base_args, *parts[1:]],
            env=self._parse_env(str(config.get("envVars") or "")),
        )

    def _parse_env(self, raw: str) -> dict[str, str]:
        # 敏感 env（token/key/secret）的拦截已上移到后端：写入时 validateEnvVars
        # 强制敏感 key 必须用 ${secret:<id>} 引用，下发时 resolveEnvVars 在服务端
        # 解析为明文。env 只经由可信的 internal 接口到达这里，敏感值是被刻意放进
        # 来、且 MCP Server 启动必需的，无需也不应在此再拒绝。
        env: dict[str, str] = {}
        for line in raw.splitlines():
            if not line.strip() or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if not key:
                continue
            env[key] = value.strip()
        return env

    def _dump(self, value: Any) -> dict[str, Any]:
        if hasattr(value, "model_dump"):
            return value.model_dump(mode="json", by_alias=True, exclude_none=True)
        if isinstance(value, dict):
            return value
        return {"value": str(value)}


mcp_client_manager = MCPClientManager()
