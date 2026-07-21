"""Tests for app.runner.client — Runner HTTP client."""

from __future__ import annotations

import pytest
from unittest.mock import ANY, patch

from app.runner.client import AuthError, RunnerClient, _new_traceparent


class TestRunnerClientEnroll:
    def test_enroll_sends_correct_headers_and_body(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            mock_resp = mock_http.return_value.post.return_value
            mock_resp.raise_for_status.return_value = None
            mock_resp.json.return_value = {
                "runnerId": "run_abc",
                "credentialId": "rcred_xyz",
                "credential": "sk-runner-AAA",
                "status": "ONLINE",
                "heartbeatIntervalSec": 15,
                "serverTime": 1,
            }

            client = RunnerClient("https://orin.example.com")
            resp = client.enroll(
                token="sk-enroll-TOKEN123",
                name="my-runner",
                hostname="host1",
                os_name="linux",
                arch="x86_64",
                version="0.1.0",
                cpu_cores=8,
                max_concurrency=4,
            )

            assert resp["runnerId"] == "run_abc"
            call_kwargs = mock_http.return_value.post.call_args
            assert call_kwargs.kwargs["headers"]["Authorization"] == "Enrollment sk-enroll-TOKEN123"
            body = call_kwargs.kwargs["json"]
            assert body["name"] == "my-runner"
            assert body["hostname"] == "host1"
            assert body["os"] == "linux"
            assert body["arch"] == "x86_64"
            assert body["cpuCores"] == 8
            assert body["maxConcurrency"] == 4

    def test_enroll_raises_on_http_error(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            import httpx
            mock_http.return_value.post.side_effect = httpx.HTTPStatusError(
                "unauthorized", request=ANY, response=ANY
            )
            client = RunnerClient("https://orin.example.com")
            with pytest.raises(httpx.HTTPStatusError):
                client.enroll(
                    token="bad", name="x", hostname="x", os_name="x",
                    arch="x", version="0.1.0", cpu_cores=1,
                )


class TestRunnerClientHeartbeat:
    def test_heartbeat_sends_correct_auth(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            mock_resp = mock_http.return_value.post.return_value
            mock_resp.raise_for_status.return_value = None
            mock_resp.json.return_value = {
                "status": "ONLINE",
                "commands": {"drainAck": False, "expectedIntervalSec": 15},
                "config": {"heartbeatIntervalSec": 15},
                "serverTime": 1,
                "credentialId": "rcred_xyz",
            }

            client = RunnerClient("https://orin.example.com")
            resp = client.heartbeat("run_abc", "sk-runner-AAA", {"cpuUsage": 42.5})

            assert resp["status"] == "ONLINE"
            call_kwargs = mock_http.return_value.post.call_args
            assert call_kwargs.kwargs["headers"]["Authorization"] == "Runner sk-runner-AAA"
            assert "traceparent" in call_kwargs.kwargs["headers"]


class TestRunnerClientAck:
    def test_ack_sends_command(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            client = RunnerClient("https://orin.example.com")
            client.ack("run_abc", "sk-runner-AAA", "DRAIN")

            call_kwargs = mock_http.return_value.post.call_args
            assert call_kwargs.kwargs["json"]["command"] == "DRAIN"
            assert "traceparent" in call_kwargs.kwargs["headers"]


class TestRunnerClientBaseUrl:
    def test_strips_trailing_slash(self):
        client = RunnerClient("https://orin.example.com/")
        assert client._base == "https://orin.example.com"

    def test_traceparent_fallback_never_uses_invalid_zero_ids(self):
        with patch("app.runner.client.build_traceparent", side_effect=ValueError("boom")):
            traceparent = _new_traceparent()

        version, trace_id, span_id, flags = traceparent.split("-")
        assert version == "00"
        assert len(trace_id) == 32 and trace_id != "0" * 32
        assert len(span_id) == 16 and span_id != "0" * 16
        assert flags == "01"


class TestRunnerClientAuthErrors:
    def test_enroll_raises_auth_error_on_401(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            import httpx
            mock_resp = mock_http.return_value.post.return_value
            mock_resp.status_code = 401
            mock_resp.text = "unauthorized"

            client = RunnerClient("https://orin.example.com")
            with pytest.raises(AuthError, match="401"):
                client.enroll(
                    token="bad", name="x", hostname="x", os_name="x",
                    arch="x", version="0.1.0", cpu_cores=1,
                )

    def test_heartbeat_raises_auth_error_on_403(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            mock_resp = mock_http.return_value.post.return_value
            mock_resp.status_code = 403
            mock_resp.text = "revoked"

            client = RunnerClient("https://orin.example.com")
            with pytest.raises(AuthError, match="403"):
                client.heartbeat("run_abc", "sk-runner-AAA", {})

    def test_ack_raises_auth_error_on_403(self):
        with patch("app.runner.client.httpx.Client") as mock_http:
            mock_resp = mock_http.return_value.post.return_value
            mock_resp.status_code = 403
            mock_resp.text = "revoked"

            client = RunnerClient("https://orin.example.com")
            with pytest.raises(AuthError, match="403"):
                client.ack("run_abc", "sk-runner-AAA", "DRAIN")
