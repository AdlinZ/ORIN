"""
Runner enrollment — one-shot exchange of an Enrollment Token for a
long-lived Runner Credential.
"""

from __future__ import annotations

import logging
from typing import Any, Dict

from app.runner.client import RunnerClient
from app.runner.collector import collect_static_info

logger = logging.getLogger(__name__)

RUNNER_VERSION = "0.1.0"


def enroll(
    client: RunnerClient,
    token: str,
    name: str,
    max_concurrency: int = 1,
) -> Dict[str, Any]:
    """Exchange an Enrollment Token for a Runner Credential.

    Returns the parsed JSON response from ``POST /api/system/runners/enroll``,
    which includes ``runnerId``, ``credentialId``, ``credential``,
    ``heartbeatIntervalSec``, etc.
    """
    static = collect_static_info()
    logger.info(
        "Enrolling runner name=%s hostname=%s os=%s arch=%s",
        name,
        static["hostname"],
        static["os"],
        static["arch"],
    )
    return client.enroll(
        token=token,
        name=name,
        hostname=str(static["hostname"]),
        os_name=str(static["os"]),
        arch=str(static["arch"]),
        version=RUNNER_VERSION,
        cpu_cores=int(static.get("cpu_cores") or 1),
        max_concurrency=max_concurrency,
    )
