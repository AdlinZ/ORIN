package com.adlin.orin.modules.integrationsync.spi;

import com.adlin.orin.modules.integrationsync.model.*;

import java.util.List;

public interface PlatformConnector {
    PlatformType platform();

    PlatformCapabilities capabilities();

    List<ExternalResource> pull(SyncPullRequest request);

    PushResult push(SyncPushRequest request);

    HealthCheckResult healthCheck(IntegrationConnection connection);
}
