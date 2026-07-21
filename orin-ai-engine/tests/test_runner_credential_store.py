"""Tests for app.runner.credential_store — local credential persistence."""

from __future__ import annotations

import json
import os
import tempfile

from app.runner import credential_store


class TestCredentialStore:
    def setup_method(self):
        self._tmp = tempfile.mkdtemp()
        # Redirect credential paths to temp dir
        self._orig_dir = credential_store.CREDENTIAL_DIR
        credential_store.CREDENTIAL_DIR = self._tmp
        credential_store.CREDENTIAL_FILE = os.path.join(self._tmp, "credentials.json")

    def teardown_method(self):
        credential_store.CREDENTIAL_DIR = self._orig_dir
        credential_store.CREDENTIAL_FILE = os.path.join(
            self._orig_dir, "credentials.json"
        )

    def test_save_and_load_roundtrip(self):
        enroll_resp = {
            "runnerId": "run_abc",
            "credentialId": "rcred_xyz",
            "credential": "sk-runner-mW2H...",
            "keyPrefix": "sk-runner-mW2H",
            "last4": "mW2H",
            "heartbeatIntervalSec": 10,
            "status": "ONLINE",
        }
        credential_store.save("https://orin.example.com", enroll_resp)

        loaded = credential_store.load()
        assert loaded is not None
        assert loaded["runner_id"] == "run_abc"
        assert loaded["credential"] == "sk-runner-mW2H..."
        assert loaded["control_plane_url"] == "https://orin.example.com"
        assert loaded["heartbeat_interval_sec"] == 10

        # Permissions check (0o600)
        st = os.stat(credential_store.CREDENTIAL_FILE)
        assert st.st_mode & 0o777 == 0o600
        directory_st = os.stat(credential_store.CREDENTIAL_DIR)
        assert directory_st.st_mode & 0o777 == 0o700

    def test_load_returns_none_when_not_enrolled(self):
        assert credential_store.load() is None

    def test_clear_removes_file(self):
        credential_store.save("https://orin.example.com", {
            "runnerId": "run_abc",
            "credentialId": "rcred_xyz",
            "credential": "sk-runner-mW2H...",
            "heartbeatIntervalSec": 15,
        })
        assert os.path.isfile(credential_store.CREDENTIAL_FILE)

        credential_store.clear()
        assert not os.path.isfile(credential_store.CREDENTIAL_FILE)
