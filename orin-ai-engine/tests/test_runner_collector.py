"""Tests for app.runner.collector — system resource collection."""

from __future__ import annotations

import platform
from unittest.mock import patch

from app.runner.collector import (
    check_dependency_health,
    collect_heartbeat_payload,
    collect_static_info,
)


class TestCollectStaticInfo:
    def test_returns_hostname_os_arch_cpu(self):
        info = collect_static_info()
        assert info["hostname"] == platform.node()
        assert info["os"] == platform.system()
        assert info["arch"] == platform.machine()
        assert isinstance(info["cpu_cores"], int)


class TestCollectHeartbeatPayload:
    def test_returns_empty_when_psutil_missing(self):
        """Without psutil we can't collect any dynamic metrics."""
        with patch("app.runner.collector._PSUTIL_AVAILABLE", False):
            payload = collect_heartbeat_payload()
            assert payload == {}

    def test_collects_cpu_memory_disk_when_psutil_available(self):
        with patch("app.runner.collector.psutil") as mock_psutil:
            mock_psutil.cpu_percent.return_value = 42.5
            mock_psutil.virtual_memory.return_value.total = 16_000_000_000
            mock_psutil.virtual_memory.return_value.used = 8_000_000_000
            mock_psutil.disk_usage.return_value.total = 500_000_000_000
            mock_psutil.disk_usage.return_value.used = 200_000_000_000

            payload = collect_heartbeat_payload()

            assert payload["cpuUsage"] == 42.5
            assert payload["memoryTotal"] == 16_000_000_000
            assert payload["memoryUsed"] == 8_000_000_000
            assert payload["diskTotal"] == 500_000_000_000
            assert payload["diskUsed"] == 200_000_000_000

    def test_survives_exceptions(self):
        """Individual collection failures should not crash the whole snapshot."""
        with patch("app.runner.collector.psutil") as mock_psutil:
            mock_psutil.cpu_percent.side_effect = OSError("boom")
            mock_psutil.virtual_memory.return_value.total = 16_000
            mock_psutil.virtual_memory.return_value.used = 8_000
            mock_psutil.disk_usage.side_effect = PermissionError("denied")

            payload = collect_heartbeat_payload()

            # cpu + disk failed but memory still made it
            assert "cpuUsage" not in payload
            assert "memoryTotal" in payload
            assert "diskTotal" not in payload


class TestCheckDependencyHealth:
    def test_healthy_when_psutil_available(self):
        assert check_dependency_health() == "HEALTHY"

    def test_unknown_when_psutil_missing(self):
        with patch("app.runner.collector._PSUTIL_AVAILABLE", False):
            assert check_dependency_health() == "UNKNOWN"
