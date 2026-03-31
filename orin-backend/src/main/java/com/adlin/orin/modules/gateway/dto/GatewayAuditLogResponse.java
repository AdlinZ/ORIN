package com.adlin.orin.modules.gateway.dto;

import com.adlin.orin.modules.gateway.entity.GatewayAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayAuditLogResponse {

    private Long id;
    private Long routeId;
    private String routeName;
    private String traceId;
    private String method;
    private String path;
    private String targetService;
    private String targetUrl;
    private Integer statusCode;
    private Long latencyMs;
    private String clientIp;
    private String userAgent;
    private String apiKeyId;
    private String result;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static GatewayAuditLogResponse fromEntity(GatewayAuditLog entity) {
        return GatewayAuditLogResponse.builder()
                .id(entity.getId())
                .routeId(entity.getRouteId())
                .traceId(entity.getTraceId())
                .method(entity.getMethod())
                .path(entity.getPath())
                .targetService(entity.getTargetService())
                .targetUrl(entity.getTargetUrl())
                .statusCode(entity.getStatusCode())
                .latencyMs(entity.getLatencyMs())
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .apiKeyId(entity.getApiKeyId())
                .result(entity.getResult())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
