package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class SyncPlanner {

    public Set<SyncResourceType> supportedPullResources(PlatformConnector connector, Set<SyncResourceType> requested) {
        Set<SyncResourceType> scope = requested == null || requested.isEmpty()
                ? EnumSet.allOf(SyncResourceType.class)
                : EnumSet.copyOf(requested);
        scope.removeIf(resourceType -> !connector.capabilities().supports(resourceType, SyncCapability.PULL));
        return scope;
    }

    public Set<SyncResourceType> supportedPushResources(PlatformConnector connector, Set<SyncResourceType> requested) {
        Set<SyncResourceType> scope = requested == null || requested.isEmpty()
                ? EnumSet.allOf(SyncResourceType.class)
                : EnumSet.copyOf(requested);
        scope.removeIf(resourceType -> !connector.capabilities().supports(resourceType, SyncCapability.PUSH)
                && !connector.capabilities().supports(resourceType, SyncCapability.EXPORT));
        return scope;
    }
}
