"""
Local credential storage for the Runner.

Writes the enrollment response to ``~/.orin/credentials.json`` with
permission ``0o600``.  The file contains the runnerId, credentialId,
credential (plaintext), control plane URL, and heartbeat interval —
everything the Runner needs to restart without re-enrolling.
"""

from __future__ import annotations

import json
import os
import tempfile
from typing import Any, Dict, Optional

CREDENTIAL_DIR = os.path.join(os.path.expanduser("~"), ".orin")
CREDENTIAL_FILE = os.path.join(CREDENTIAL_DIR, "credentials.json")


def save(control_plane_url: str, enroll_response: Dict[str, Any]) -> None:
    """Persist the enrollment response next to the control-plane URL."""
    os.makedirs(CREDENTIAL_DIR, mode=0o700, exist_ok=True)
    os.chmod(CREDENTIAL_DIR, 0o700)
    payload = {
        "control_plane_url": control_plane_url.rstrip("/"),
        "runner_id": enroll_response["runnerId"],
        "credential_id": enroll_response["credentialId"],
        "credential": enroll_response["credential"],
        "key_prefix": enroll_response.get("keyPrefix"),
        "last4": enroll_response.get("last4"),
        "heartbeat_interval_sec": enroll_response.get("heartbeatIntervalSec", 15),
        "status": enroll_response.get("status", "ONLINE"),
    }
    # mkstemp creates the secret-bearing temporary file as 0o600 before any byte is written.
    fd, tmp = tempfile.mkstemp(prefix=".credentials-", suffix=".tmp", dir=CREDENTIAL_DIR)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
        os.replace(tmp, CREDENTIAL_FILE)
        os.chmod(CREDENTIAL_FILE, 0o600)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


def load() -> Optional[Dict[str, Any]]:
    """Read persisted credentials, or ``None`` if not enrolled yet."""
    if not os.path.isfile(CREDENTIAL_FILE):
        return None
    with open(CREDENTIAL_FILE, "r", encoding="utf-8") as fh:
        return json.load(fh)


def clear() -> None:
    """Remove the local credential file (used on revoke / uninstall)."""
    if os.path.isfile(CREDENTIAL_FILE):
        os.remove(CREDENTIAL_FILE)
