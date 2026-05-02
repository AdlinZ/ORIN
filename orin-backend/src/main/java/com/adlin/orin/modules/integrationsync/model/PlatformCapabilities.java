package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
@Builder
public class PlatformCapabilities {
    private PlatformType platformType;
    private Map<SyncResourceType, Set<SyncCapability>> resourceCapabilities;
    private boolean secretValueSyncSupported;
    private String notes;

    public boolean supports(SyncResourceType resourceType, SyncCapability capability) {
        return resourceCapabilities != null
                && resourceCapabilities.getOrDefault(resourceType, Set.of()).contains(capability);
    }
}
