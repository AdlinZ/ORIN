package com.adlin.orin.modules.gateway.dto;

import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayRouteResponse {

    private Long id;
    private String name;
    private String pathPattern;
    private String method;
    private Long serviceId;
    private String serviceName;
    private String targetUrl;
    private Boolean stripPrefix;
    private String rewritePath;
    private Integer timeoutMs;
    private Integer retryCount;
    private String loadBalance;
    private Boolean authRequired;
    private Long rateLimitPolicyId;
    private String rateLimitPolicyName;
    private Long circuitBreakerPolicyId;
    private String circuitBreakerPolicyName;
    private Long retryPolicyId;
    private String retryPolicyName;
    private Integer priority;
    private Boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GatewayRouteResponse fromEntity(GatewayRoute entity) {
        return GatewayRouteResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .pathPattern(entity.getPathPattern())
                .method(entity.getMethod())
                .serviceId(entity.getServiceId())
                .targetUrl(entity.getTargetUrl())
                .stripPrefix(entity.getStripPrefix())
                .rewritePath(entity.getRewritePath())
                .timeoutMs(entity.getTimeoutMs())
                .retryCount(entity.getRetryCount())
                .loadBalance(entity.getLoadBalance())
                .authRequired(entity.getAuthRequired())
                .rateLimitPolicyId(entity.getRateLimitPolicyId())
                .circuitBreakerPolicyId(entity.getCircuitBreakerPolicyId())
                .retryPolicyId(entity.getRetryPolicyId())
                .priority(entity.getPriority())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
