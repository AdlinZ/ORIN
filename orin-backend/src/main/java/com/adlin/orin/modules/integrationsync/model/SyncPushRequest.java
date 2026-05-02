package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SyncPushRequest {
    private IntegrationConnection connection;
    private List<ExternalResource> resources;
    private boolean dryRun;
}
