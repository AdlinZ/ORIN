package com.adlin.orin.modules.integrationsync.model;

import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntegrationConnection {
    private Long integrationId;
    private String name;
    private PlatformType platformType;
    private String baseUrl;
    private String authType;
    private String authConfig;
    private SyncDirectionMode syncDirection;
    private Map<String, Object> extraConfig;

    public static IntegrationConnection from(ExternalIntegration integration, Map<String, Object> extraConfig) {
        return IntegrationConnection.builder()
                .integrationId(integration.getId())
                .name(integration.getName())
                .platformType(parsePlatform(integration.getIntegrationType()))
                .baseUrl(trimTrailingSlash(integration.getBaseUrl()))
                .authType(integration.getAuthType())
                .authConfig(integration.getAuthConfig())
                .syncDirection(parseDirection(integration.getSyncDirection()))
                .extraConfig(extraConfig)
                .build();
    }

    private static PlatformType parsePlatform(String value) {
        if (value == null || value.isBlank()) {
            return PlatformType.CUSTOM;
        }
        try {
            return PlatformType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PlatformType.CUSTOM;
        }
    }

    private static SyncDirectionMode parseDirection(String value) {
        if (value == null || value.isBlank()) {
            return SyncDirectionMode.PUSH;
        }
        try {
            return SyncDirectionMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SyncDirectionMode.PUSH;
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
