#!/usr/bin/env python3
"""
Standalone ORIN API and unified gateway MVP checker.

This script intentionally uses only Python standard-library modules so it can
run outside the ORIN backend/frontend projects.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any


DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_CHAT_MODEL = "Qwen/Qwen2.5-7B-Instruct"


@dataclass
class HttpResult:
    method: str
    url: str
    status: int | None
    elapsed_ms: int
    headers: dict[str, str] = field(default_factory=dict)
    body: Any = None
    raw_body: str = ""
    error: str | None = None


@dataclass
class CheckResult:
    name: str
    ok: bool
    severity: str
    message: str
    detail: dict[str, Any] = field(default_factory=dict)


class OrinGatewayChecker:
    def __init__(self, base_url: str, api_key: str | None, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout
        self.results: list[CheckResult] = []

    def run(self, args: argparse.Namespace) -> list[CheckResult]:
        self.check_api_index()
        self.check_docs()
        self.check_gateway_health()
        self.check_legacy_health()
        self.check_capabilities()
        self.check_providers()
        self.check_auth_gate()

        if self.api_key:
            self.check_models_with_key()
            if args.gateway_route_test:
                self.check_management_route_test(args.route_test_path, args.route_test_method)
            if args.run_chat:
                self.check_chat_completion(args.chat_model)
        else:
            self.add(
                "authenticated checks",
                True,
                "warn",
                "Skipped API-key checks because no API key was supplied.",
                {"hint": "Set ORIN_API_KEY or pass --api-key to test /v1/models and optional chat."},
            )

        return self.results

    def check_api_index(self) -> None:
        result = self.request("GET", "/v1")
        if result.status == 200 and isinstance(result.body, dict):
            endpoints = result.body.get("endpoints", {})
            required = {"health", "models", "chatCompletions", "embeddings", "capabilities"}
            missing = sorted(required.difference(endpoints.keys())) if isinstance(endpoints, dict) else sorted(required)
            self.add(
                "api index",
                not missing,
                "fail",
                "Unified API index is reachable." if not missing else "Unified API index is missing expected endpoints.",
                {"status": result.status, "missing": missing, "trace_id": header(result, "x-trace-id")},
            )
            return
        self.add_http_failure("api index", result, "GET /v1 should return a JSON API index.")

    def check_docs(self) -> None:
        result = self.request("GET", "/v1/docs")
        ok = result.status == 200 and isinstance(result.body, dict)
        self.add(
            "api docs",
            ok,
            "fail",
            "Unified API docs endpoint is reachable." if ok else "GET /v1/docs did not return JSON metadata.",
            {"status": result.status, "body": compact_body(result.body or result.raw_body)},
        )

    def check_gateway_health(self) -> None:
        result = self.request("GET", "/v1/health")
        ok = result.status == 200 and isinstance(result.body, dict) and result.body.get("status") == "ok"
        stats = result.body.get("statistics") if isinstance(result.body, dict) else None
        providers = result.body.get("providers") if isinstance(result.body, dict) else None
        self.add(
            "gateway health",
            ok,
            "fail",
            "Unified gateway health is OK." if ok else "GET /v1/health did not report status=ok.",
            {"status": result.status, "statistics": stats, "providers": providers, "elapsed_ms": result.elapsed_ms},
        )

    def check_legacy_health(self) -> None:
        result = self.request("GET", "/api/v1/health")
        ok = result.status == 200
        self.add(
            "legacy health",
            ok,
            "fail",
            "Legacy backend health endpoint is reachable."
            if ok
            else "GET /api/v1/health is unavailable; backend may not expose the documented compatibility endpoint.",
            {"status": result.status, "body": compact_body(result.body or result.raw_body)},
        )

    def check_capabilities(self) -> None:
        result = self.request("GET", "/v1/capabilities")
        capabilities = result.body.get("capabilities") if isinstance(result.body, dict) else None
        required = {"chat", "embeddings", "models"}
        missing = sorted(required.difference(capabilities.keys())) if isinstance(capabilities, dict) else sorted(required)
        self.add(
            "capabilities",
            result.status == 200 and not missing,
            "fail",
            "Gateway capability list includes OpenAI-compatible primitives."
            if not missing
            else "Gateway capability list is missing expected primitives.",
            {"status": result.status, "missing": missing, "capabilities": capabilities},
        )

    def check_providers(self) -> None:
        result = self.request("GET", "/v1/providers")
        ok = result.status == 200 and isinstance(result.body, dict) and "statistics" in result.body
        detail: dict[str, Any] = {"status": result.status}
        if isinstance(result.body, dict):
            detail["statistics"] = result.body.get("statistics")
            providers = result.body.get("providers")
            detail["provider_count"] = len(providers) if isinstance(providers, list) else None
        self.add(
            "providers",
            ok,
            "fail",
            "Provider registry endpoint is reachable." if ok else "GET /v1/providers did not return provider metadata.",
            detail,
        )

    def check_auth_gate(self) -> None:
        if self.api_key:
            self.add(
                "auth gate",
                True,
                "warn",
                "Skipped unauthenticated /v1/models probe because --api-key was supplied for this run.",
            )
            return

        result = self.request("GET", "/v1/models")
        ok = result.status == 401
        self.add(
            "auth gate",
            ok,
            "fail",
            "Protected model endpoint rejects missing API key."
            if ok
            else "GET /v1/models without API key should normally return 401.",
            {"status": result.status, "body": compact_body(result.body or result.raw_body)},
        )

    def check_models_with_key(self) -> None:
        result = self.request("GET", "/v1/models", auth=True)
        ok = result.status == 200 and isinstance(result.body, dict) and result.body.get("object") == "list"
        severity = "fail" if result.status in {401, 403} else "warn"
        data = result.body.get("data") if isinstance(result.body, dict) else None
        self.add(
            "models with api key",
            ok,
            severity,
            "Authenticated /v1/models returned an OpenAI-compatible list."
            if ok
            else "Authenticated /v1/models did not return an OpenAI-compatible list.",
            {"status": result.status, "model_groups": len(data) if isinstance(data, list) else None, "body": compact_body(result.body or result.raw_body)},
        )

    def check_management_route_test(self, path: str, method: str) -> None:
        payload = {"path": path, "method": method.upper()}
        result = self.request("POST", "/api/v1/system/gateway/routes/test", payload, auth=True)
        body = result.body if isinstance(result.body, dict) else {}
        ok = result.status == 200 and body.get("success") is True
        severity = "fail" if result.status in {401, 403, 500} else "warn"
        self.add(
            "management route resolver",
            ok,
            severity,
            "Gateway management route resolver matched the probe route."
            if ok
            else "Gateway management route resolver did not match the probe route.",
            {"status": result.status, "request": payload, "response": compact_body(body or result.raw_body)},
        )

    def check_chat_completion(self, model: str) -> None:
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": "请用一句话回答：ORIN 网关连通性检查是什么？"}],
            "temperature": 0.1,
            "max_tokens": 64,
            "stream": False,
        }
        result = self.request("POST", "/v1/chat/completions", payload, auth=True)
        ok = result.status == 200 and isinstance(result.body, dict) and "choices" in result.body
        if result.status == 503:
            severity = "warn"
            message = "Chat endpoint is routed, but no healthy provider is currently available."
        elif result.status in {401, 403}:
            severity = "fail"
            message = "Chat endpoint rejected the supplied API key."
        elif ok:
            severity = "fail"
            message = "Chat completion returned an OpenAI-compatible response."
        else:
            severity = "fail"
            message = "Chat completion did not return an OpenAI-compatible response."

        self.add(
            "chat completion",
            ok or result.status == 503,
            severity,
            message,
            {"status": result.status, "trace_id": header(result, "x-trace-id"), "body": compact_body(result.body or result.raw_body)},
        )

    def request(self, method: str, path: str, payload: Any = None, auth: bool = False) -> HttpResult:
        url = urllib.parse.urljoin(self.base_url + "/", path.lstrip("/"))
        body_bytes = None
        headers = {"Accept": "application/json", "X-Trace-Id": f"mvp-{int(time.time() * 1000)}"}
        if payload is not None:
            body_bytes = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if auth and self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        request = urllib.request.Request(url, data=body_bytes, headers=headers, method=method.upper())
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8", errors="replace")
                return HttpResult(
                    method=method.upper(),
                    url=url,
                    status=response.status,
                    elapsed_ms=elapsed_ms(started),
                    headers=dict(response.headers.items()),
                    body=parse_json(raw),
                    raw_body=raw,
                )
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            return HttpResult(
                method=method.upper(),
                url=url,
                status=exc.code,
                elapsed_ms=elapsed_ms(started),
                headers=dict(exc.headers.items()),
                body=parse_json(raw),
                raw_body=raw,
                error=str(exc),
            )
        except urllib.error.URLError as exc:
            return HttpResult(
                method=method.upper(),
                url=url,
                status=None,
                elapsed_ms=elapsed_ms(started),
                error=str(exc.reason),
            )
        except TimeoutError as exc:
            return HttpResult(
                method=method.upper(),
                url=url,
                status=None,
                elapsed_ms=elapsed_ms(started),
                error=str(exc),
            )

    def add(self, name: str, ok: bool, severity: str, message: str, detail: dict[str, Any] | None = None) -> None:
        self.results.append(CheckResult(name=name, ok=ok, severity=severity, message=message, detail=detail or {}))

    def add_http_failure(self, name: str, result: HttpResult, message: str) -> None:
        self.add(
            name,
            False,
            "fail",
            message,
            {"status": result.status, "error": result.error, "body": compact_body(result.body or result.raw_body)},
        )


def parse_json(raw: str) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def compact_body(body: Any, limit: int = 700) -> Any:
    if body is None:
        return None
    if isinstance(body, (dict, list)):
        return body
    text = str(body)
    if len(text) > limit:
        return text[:limit] + "...<truncated>"
    return text


def elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


def header(result: HttpResult, name: str) -> str | None:
    target = name.lower()
    for key, value in result.headers.items():
        if key.lower() == target:
            return value
    return None


def print_text_report(results: list[CheckResult]) -> None:
    print("ORIN API Gateway MVP Check")
    print("")
    for item in results:
        if item.ok and item.severity != "warn":
            marker = "PASS"
        elif item.ok and item.severity == "warn":
            marker = "SKIP"
        elif item.severity == "warn":
            marker = "WARN"
        else:
            marker = "FAIL"
        print(f"[{marker}] {item.name}: {item.message}")
        if item.detail:
            print(f"       {json.dumps(item.detail, ensure_ascii=False, default=str)}")
    print("")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Standalone MVP checker for ORIN API service and unified gateway.",
    )
    parser.add_argument("--base-url", default=os.getenv("ORIN_BASE_URL", DEFAULT_BASE_URL), help="ORIN backend base URL.")
    parser.add_argument("--api-key", default=os.getenv("ORIN_API_KEY"), help="Gateway API key. Defaults to ORIN_API_KEY.")
    parser.add_argument("--timeout", type=float, default=float(os.getenv("ORIN_CHECK_TIMEOUT", "8")), help="HTTP timeout in seconds.")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    parser.add_argument("--run-chat", action="store_true", help="Run an authenticated /v1/chat/completions probe.")
    parser.add_argument("--chat-model", default=os.getenv("ORIN_MVP_CHAT_MODEL", DEFAULT_CHAT_MODEL), help="Model name for --run-chat.")
    parser.add_argument("--gateway-route-test", action="store_true", help="Run /api/v1/system/gateway/routes/test with the supplied API key.")
    parser.add_argument("--route-test-path", default="/v1/health", help="Path used by --gateway-route-test.")
    parser.add_argument("--route-test-method", default="GET", help="HTTP method used by --gateway-route-test.")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    checker = OrinGatewayChecker(args.base_url, args.api_key, args.timeout)
    results = checker.run(args)

    if args.json:
        print(json.dumps([result.__dict__ for result in results], ensure_ascii=False, indent=2, default=str))
    else:
        print_text_report(results)

    has_failures = any((not result.ok) and result.severity == "fail" for result in results)
    return 1 if has_failures else 0


if __name__ == "__main__":
    sys.exit(main())
