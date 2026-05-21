package com.adlin.orin.modules.setup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class SetupDtos {

    private SetupDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetupCheck {
        private String key;
        private String name;
        private String status;
        private String severity;
        private boolean required;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetupStatusResponse {
        private boolean completed;
        private boolean setupEnabled;
        private boolean canInitialize;
        private String environment;
        private List<SetupCheck> dependencies;
        private List<SetupCheck> security;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSetupRequest {
        private String provider;
        private String endpoint;
        private String apiKey;
        private String model;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminSetupRequest {
        private String username;
        private String password;
        private String nickname;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientAccessSetupRequest {
        private boolean create;
        private String name;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitializeSetupRequest {
        private AdminSetupRequest admin;
        private ProviderSetupRequest provider;
        private ClientAccessSetupRequest clientAccess;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecretSummary {
        private String id;
        private String provider;
        private String last4;
        private String keyPrefix;
        private String secretKey;
        private String warning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitializeSetupResponse {
        private boolean success;
        private String message;
        private String adminUsername;
        private SecretSummary providerSecret;
        private SecretSummary clientAccessKey;
    }
}
