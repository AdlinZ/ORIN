#!/usr/bin/env python3
"""
Serve the ORIN Gateway Checker UI and proxy browser checks to the backend.

The proxy keeps the MVP usable when ORIN backend CORS only allows the main
frontend dev origin or when this tool runs on a different local port.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from check_orin_gateway import OrinGatewayChecker


ROOT = Path(__file__).resolve().parent


class GatewayCheckerHandler(BaseHTTPRequestHandler):
    server_version = "ORINGatewayChecker/0.1"

    def do_GET(self) -> None:
        if self.path == "/api/health":
            self.write_json({"status": "ok"})
            return

        route = self.path.split("?", 1)[0]
        if route == "/":
            route = "/index.html"
        target = (ROOT / route.lstrip("/")).resolve()
        if not str(target).startswith(str(ROOT)) or not target.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return
        self.serve_file(target)

    def do_POST(self) -> None:
        if self.path != "/api/request":
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return

        try:
            payload = self.read_json()
            result = self.proxy_request(payload)
            self.write_json(result)
        except ValueError as exc:
            self.write_json({"error": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:  # noqa: BLE001 - return useful MVP diagnostics
            self.write_json({"error": str(exc)}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            raise ValueError("Missing JSON body")
        raw = self.rfile.read(length).decode("utf-8")
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ValueError("JSON body must be an object")
        return data

    def proxy_request(self, payload: dict[str, Any]) -> dict[str, Any]:
        base_url = str(payload.get("baseUrl") or "http://127.0.0.1:8080")
        api_key = payload.get("apiKey")
        timeout = float(payload.get("timeout") or 8)
        method = str(payload.get("method") or "GET").upper()
        path = str(payload.get("path") or "/")
        body = payload.get("payload")
        auth = bool(payload.get("auth"))

        checker = OrinGatewayChecker(base_url, str(api_key) if api_key else None, timeout)
        response = checker.request(method, path, body, auth)
        return {
            "status": response.status,
            "elapsedMs": response.elapsed_ms,
            "headers": response.headers,
            "body": response.body,
            "rawBody": response.raw_body,
            "error": response.error,
        }

    def serve_file(self, target: Path) -> None:
        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        body = target.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def write_json(self, data: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("%s - %s\n" % (self.log_date_time_string(), format % args))


def main() -> int:
    parser = argparse.ArgumentParser(description="Serve ORIN Gateway Checker MVP UI.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5174)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), GatewayCheckerHandler)
    print(f"ORIN Gateway Checker running at http://{args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
