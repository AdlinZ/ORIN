"""Tests for app.runner.enrollment — one-shot enrollment flow."""

from __future__ import annotations

from unittest.mock import ANY, patch

from app.runner.client import RunnerClient
from app.runner.enrollment import RUNNER_VERSION, enroll


class TestEnroll:
    def test_enroll_delegates_to_client_with_static_info(self):
        client = RunnerClient("https://orin.example.com")
        with patch.object(client, "enroll") as mock_enroll:
            mock_enroll.return_value = {
                "runnerId": "run_abc",
                "credentialId": "rcred_xyz",
                "credential": "sk-runner-AAA",
                "status": "ONLINE",
                "heartbeatIntervalSec": 15,
                "serverTime": 1,
            }

            resp = enroll(client, token="sk-enroll-TOKEN", name="my-runner")

            assert resp["runnerId"] == "run_abc"
            mock_enroll.assert_called_once()
            kwargs = mock_enroll.call_args.kwargs
            assert kwargs["token"] == "sk-enroll-TOKEN"
            assert kwargs["name"] == "my-runner"
            assert kwargs["hostname"]  # real platform.node()
            assert kwargs["os_name"]  # real platform.system()
            assert kwargs["arch"]  # real platform.machine()
            assert kwargs["version"] == RUNNER_VERSION
