package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class SyncPullRequest {
    private IntegrationConnection connection;
    private Set<SyncResourceType> resourceTypes;
    private String cursor;
    private boolean fullSync;
}
