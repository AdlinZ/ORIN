from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from mcp import ClientSession
from mcp.client.stdio import StdioServerParameters, stdio_client

from orin_mcp_bridge.bridge import (
    BridgeConfig,
    ConfigError,
    build_headers,
    call_remote_tool,
    load_config,
    list_remote_tools,
)


class FakeOrin:
    def __init__(self, call_error: bool = False):
        self.call_error = call_error
        self.requests: list[dict[str, object]] = []
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers.get("Content-Length", "0"))
                body = json.loads(self.rfile.read(length) or b"{}")
                owner.requests.append({"headers": dict(self.headers), "body": body})
                method = body.get("method")
                if method == "initialize":
                    self._json(body, {"protocolVersion": "2025-06-18", "capabilities": {"tools": {}}, "serverInfo": {"name": "fake", "version": "0"}})
                elif method == "tools/list":
                    self._json(body, {"tools": [{"name": "orin_agent_demo", "description": "demo", "inputSchema": {"type": "object"}}]})
                elif method == "tools/call":
                    text = "remote failed" if owner.call_error else "remote ok"
                    self._json(body, {"content": [{"type": "text", "text": text}], "isError": owner.call_error})
                else:
                    self._json(body, {})

            def do_DELETE(self):
                self.send_response(200)
                self.end_headers()

            def log_message(self, *_):
                pass

            def _json(self, body, result):
                payload = json.dumps({"jsonrpc": "2.0", "id": body.get("id"), "result": result}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    @property
    def base_url(self) -> str:
        return f"http://127.0.0.1:{self.server.server_port}"

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_):
        self.server.shutdown()
        self.server.server_close()


class BridgeUnitTest(unittest.IsolatedAsyncioTestCase):
    def test_env_validation_and_origin_derivation(self):
        with self.assertRaises(ConfigError):
            load_config({})
        cfg = load_config({"ORIN_BASE_URL": "http://localhost:8080/", "ORIN_API_KEY": "k"})
        self.assertEqual(cfg.base_url, "http://localhost:8080")
        self.assertEqual(cfg.origin, "http://localhost:8080")
        override = load_config({"ORIN_BASE_URL": "http://localhost:8080", "ORIN_API_KEY": "k", "ORIN_MCP_ORIGIN": "http://localhost:5173"})
        self.assertEqual(override.origin, "http://localhost:5173")

    def test_headers_include_api_key_and_origin(self):
        headers = build_headers(BridgeConfig("http://localhost:8080", "secret", "http://localhost:8080"))
        self.assertEqual(headers["Authorization"], "Bearer secret")
        self.assertEqual(headers["Origin"], "http://localhost:8080")

    async def test_tools_list_proxies_remote_schema(self):
        with FakeOrin() as fake:
            tools = await list_remote_tools(BridgeConfig(fake.base_url, "secret", fake.base_url))
        self.assertEqual(tools[0].name, "orin_agent_demo")
        self.assertEqual(fake.requests[-1]["headers"]["Authorization"], "Bearer secret")
        self.assertEqual(fake.requests[-1]["headers"]["Origin"], fake.base_url)

    async def test_tools_call_preserves_remote_error_result(self):
        with FakeOrin(call_error=True) as fake:
            result = await call_remote_tool(BridgeConfig(fake.base_url, "secret", fake.base_url), "orin_agent_demo", {"message": "hi"})
        self.assertTrue(result.isError)
        self.assertEqual(result.content[0].text, "remote failed")


class BridgeStdioE2ETest(unittest.IsolatedAsyncioTestCase):
    async def test_stdio_bridge_lists_and_calls_fake_orin(self):
        root = Path(__file__).resolve().parents[1]
        env = dict(os.environ)
        with FakeOrin() as fake:
            env.update({"ORIN_BASE_URL": fake.base_url, "ORIN_API_KEY": "secret"})
            params = StdioServerParameters(
                command=sys.executable,
                args=["-m", "orin_mcp_bridge"],
                env=env,
                cwd=root,
            )
            async with stdio_client(params) as (read_stream, write_stream):
                async with ClientSession(read_stream, write_stream) as session:
                    await session.initialize()
                    tools = await session.list_tools()
                    result = await session.call_tool("orin_agent_demo", {"message": "hi"})
        self.assertEqual(tools.tools[0].name, "orin_agent_demo")
        self.assertEqual(result.content[0].text, "remote ok")

    async def test_stdio_bridge_maps_unreachable_call_to_error_result(self):
        root = Path(__file__).resolve().parents[1]
        env = dict(os.environ)
        env.update({"ORIN_BASE_URL": "http://127.0.0.1:9", "ORIN_API_KEY": "secret"})
        params = StdioServerParameters(
            command=sys.executable,
            args=["-m", "orin_mcp_bridge"],
            env=env,
            cwd=root,
        )
        async with stdio_client(params) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                result = await session.call_tool("orin_agent_demo", {"message": "hi"})
        self.assertTrue(result.isError)
        self.assertIn("ORIN MCP endpoint unavailable", result.content[0].text)


if __name__ == "__main__":
    unittest.main()
