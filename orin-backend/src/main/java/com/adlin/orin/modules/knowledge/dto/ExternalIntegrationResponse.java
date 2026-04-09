package com.adlin.orin.modules.knowledge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部集成响应DTO，authConfig 已脱敏
 */
@Data
@Builder
public class ExternalIntegrationResponse {

    private Long id;
    private String name;
    private String integrationType;
    private String knowledgeBaseId;
    private String authType;
    /** 脱敏后的 authConfig，API Key 显示为 **** */
    private String authConfigMasked;
    private String baseUrl;
    private String syncDirection;
    private String status;
    private String healthStatus;
    private LocalDateTime lastSyncTime;
    private LocalDateTime lastHealthCheck;
    private Integer consecutiveFailures;
    private String capabilities;
    private String extraConfig;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将 authConfig 中的敏感字段替换为掩码
     * apiKey -> ************(后4位)
     */
    @SuppressWarnings("unchecked")
    public static String maskSensitiveConfig(String authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return authConfig;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> config =
                    mapper.readValue(authConfig, java.util.Map.class);
            maskMap(config);
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            return "***";
        }
    }

    @SuppressWarnings("unchecked")
    private static void maskMap(java.util.Map<String, Object> map) {
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("key") || key.contains("token") || key.contains("secret") || key.contains("password")) {
                Object value = entry.getValue();
                if (value instanceof String str && !str.isEmpty()) {
                    int len = str.length();
                    if (len > 4) {
                        entry.setValue("****" + str.substring(len - 4));
                    } else {
                        entry.setValue("****");
                    }
                }
            } else if (entry.getValue() instanceof java.util.Map) {
                maskMap((java.util.Map<String, Object>) entry.getValue());
            }
        }
    }
}
