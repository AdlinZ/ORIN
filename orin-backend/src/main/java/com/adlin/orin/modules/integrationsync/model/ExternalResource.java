package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ExternalResource {
    private SyncResourceType orinResourceType;
    private String orinResourceId;
    private String externalResourceType;
    private String externalResourceId;
    private String name;
    private String externalVersion;
    private LocalDateTime externalUpdatedAt;
    private String contentHash;
    private Map<String, Object> canonicalSnapshot;
    private Map<String, Object> rawSnapshot;
    private boolean partial;
    private String compatibilityMessage;
}
