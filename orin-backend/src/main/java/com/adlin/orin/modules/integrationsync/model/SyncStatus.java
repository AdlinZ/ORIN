package com.adlin.orin.modules.integrationsync.model;

public enum SyncStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    SKIPPED,
    CONFLICT,
    EXTERNAL_DRIFT
}
