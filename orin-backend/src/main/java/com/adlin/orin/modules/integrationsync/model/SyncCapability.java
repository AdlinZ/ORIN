package com.adlin.orin.modules.integrationsync.model;

public enum SyncCapability {
    PULL,
    PUSH,
    INVOKE,
    IMPORT,
    EXPORT,
    EXECUTION_READ,
    WEBHOOK_INBOUND,
    WEBHOOK_OUTBOUND,
    CREDENTIAL_REFERENCE
}
