package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayRouteRequest {

    private Long id;

    private String name;

    private String pathPattern;

    private String method;

    private Long serviceId;

    private String targetUrl;

    private Boolean stripPrefix;

    private String rewritePath;

    private Integer timeoutMs;

    private Integer retryCount;

    private String loadBalance;

    private Boolean authRequired;

    private Long rateLimitPolicyId;

    private Long circuitBreakerPolicyId;

    private Long retryPolicyId;

    private Integer priority;

    private Boolean enabled;

    private String description;
}
