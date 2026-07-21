"""Tests for app.runner.heartbeat — heartbeat loop."""

from __future__ import annotations

from unittest.mock import patch

from app.runner.client import AuthError, RunnerClient
from app.runner.heartbeat import HeartbeatLoop


class TestHeartbeatLoop:
    def test_keeps_heartbeating_during_drain_and_resumes(self):
        """Drain stops new work but never disconnects the machine channel."""
        client = RunnerClient("https://orin.example.com")
        call_count = [0]

        def _fake_heartbeat(runner_id, credential, payload):
            call_count[0] += 1
            if call_count[0] in (2, 3):
                return {
                    "status": "DRAINING",
                    "commands": {"drainAck": True, "expectedIntervalSec": 15},
                    "config": {"heartbeatIntervalSec": 15},
                    "serverTime": 1,
                    "credentialId": "rcred_xyz",
                }
            if call_count[0] == 4:
                return {
                    "status": "ONLINE",
                    "commands": {"drainAck": False, "expectedIntervalSec": 15},
                    "config": {"heartbeatIntervalSec": 15},
                    "serverTime": 1,
                    "credentialId": "rcred_xyz",
                }
            if call_count[0] >= 5:
                raise AuthError("401")
            return {
                "status": "ONLINE",
                "commands": {"drainAck": False, "expectedIntervalSec": 15},
                "config": {"heartbeatIntervalSec": 15},
                "serverTime": 1,
                "credentialId": "rcred_xyz",
            }

        with patch.object(client, "heartbeat", side_effect=_fake_heartbeat), \
             patch.object(client, "ack") as mock_ack:
            loop = HeartbeatLoop(
                client=client,
                runner_id="run_abc",
                credential="sk-runner-AAA",
                interval_sec=0,
            )
            loop.run()

        assert call_count[0] == 5
        mock_ack.assert_called_once_with("run_abc", "sk-runner-AAA", "DRAIN")
        assert loop._draining is False
        assert loop._drain_ack_sent is False

    def test_acks_repeated_drain_command_only_once(self):
        client = RunnerClient("https://orin.example.com")
        with patch.object(client, "heartbeat") as mock_hb, \
             patch.object(client, "ack") as mock_ack:
            draining = {
                "status": "DRAINING",
                "commands": {"drainAck": True, "expectedIntervalSec": 15},
                "config": {"heartbeatIntervalSec": 15},
                "serverTime": 1,
                "credentialId": "rcred_xyz",
            }
            mock_hb.side_effect = [draining, draining, AuthError("403")]

            loop = HeartbeatLoop(
                client=client,
                runner_id="run_abc",
                credential="sk-runner-AAA",
                interval_sec=0,
            )
            loop.run()

            mock_ack.assert_called_once_with("run_abc", "sk-runner-AAA", "DRAIN")

    def test_continues_on_heartbeat_failure(self):
        """Heartbeat HTTP errors should not crash the loop — uses backoff."""
        client = RunnerClient("https://orin.example.com")
        call_count = [0]

        def _flakey(runner_id, credential, payload):
            call_count[0] += 1
            if call_count[0] == 1:
                raise ConnectionError("network down")
            if call_count[0] == 2:
                return {
                    "status": "ONLINE",
                    "commands": {"drainAck": False, "expectedIntervalSec": 15},
                    "config": {"heartbeatIntervalSec": 15},
                    "serverTime": 1,
                    "credentialId": "rcred_xyz",
                }
            raise AuthError("401")

        with patch.object(client, "heartbeat", side_effect=_flakey), \
             patch("app.runner.heartbeat._BACKOFF_BASE_SEC", 0):
            loop = HeartbeatLoop(
                client=client,
                runner_id="run_abc",
                credential="sk-runner-AAA",
                interval_sec=0,
            )
            loop.run()

        assert call_count[0] == 3

    def test_exits_immediately_on_401(self):
        """Runner must exit immediately on 401 (credential invalid)."""
        client = RunnerClient("https://orin.example.com")

        with patch.object(client, "heartbeat", side_effect=AuthError("401")):
            loop = HeartbeatLoop(
                client=client,
                runner_id="run_abc",
                credential="sk-runner-AAA",
                interval_sec=0.1,
            )
            loop.run()  # should exit without raising

    def test_exits_immediately_on_403(self):
        """Runner must exit immediately on 403 (credential revoked)."""
        client = RunnerClient("https://orin.example.com")

        with patch.object(client, "heartbeat", side_effect=AuthError("403")):
            loop = HeartbeatLoop(
                client=client,
                runner_id="run_abc",
                credential="sk-runner-AAA",
                interval_sec=0.1,
            )
            loop.run()  # should exit without raising
