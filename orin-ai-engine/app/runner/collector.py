"""
System resource collection for Runner heartbeat payloads.

Uses psutil when available; falls back to stdlib `platform` / `os` for
basic host identification.  Fields that can't be collected without
psutil are simply omitted (they are all optional in the heartbeat
contract).
"""

from __future__ import annotations

import os
import platform as _platform
from typing import Any, Dict, Optional

try:
    import psutil

    _PSUTIL_AVAILABLE = True
except ImportError:  # pragma: no cover
    psutil = None  # type: ignore[assignment]
    _PSUTIL_AVAILABLE = False


def collect_static_info() -> Dict[str, Any]:
    """Return hostname, os, arch and cpu_cores — sent once at enrollment."""
    return {
        "hostname": _platform.node(),
        "os": _platform.system(),
        "arch": _platform.machine(),
        "cpu_cores": os.cpu_count(),
    }


def collect_heartbeat_payload() -> Dict[str, Any]:
    """Return the subset of HeartbeatRequest fields that can be gathered.

    All fields are optional; the Control Plane will only update what we
    send.  This keeps the Runner functional even on constrained systems.
    """
    payload: Dict[str, Any] = {}

    if not _PSUTIL_AVAILABLE:
        return payload

    try:
        cpu = psutil.cpu_percent(interval=0.5)
        payload["cpuUsage"] = round(cpu, 2)
    except Exception:
        pass

    try:
        mem = psutil.virtual_memory()
        payload["memoryTotal"] = mem.total
        payload["memoryUsed"] = mem.used
    except Exception:
        pass

    try:
        disk = psutil.disk_usage("/")
        payload["diskTotal"] = disk.total
        payload["diskUsed"] = disk.used
    except Exception:
        pass

    return payload


def check_dependency_health() -> str:
    """Heuristic dependency health check.

    Returns one of ``"HEALTHY"``, ``"DEGRADED"``, or ``"UNKNOWN"``.
    F01 MVP only reports HEALTHY when psutil is available (the only
    hard dependency for resource reporting).
    """
    if _PSUTIL_AVAILABLE:
        return "HEALTHY"
    return "UNKNOWN"
