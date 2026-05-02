package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PushResult {
    private boolean success;
    private SyncStatus status;
    private String message;
    private List<PushResultItem> items;

    @Data
    @Builder
    public static class PushResultItem {
        private SyncResourceType orinResourceType;
        private String orinResourceId;
        private String externalResourceType;
        private String externalResourceId;
        private String externalVersion;
        private SyncStatus status;
        private String message;
        private String contentHash;
    }
}
