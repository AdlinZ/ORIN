package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayPolicyRequest {

    private Long id;

    private String name;

    private String type;

    private String dimension;

    private Integer capacity;

    private Integer windowSeconds;

    private Integer burst;

    private Integer failureThreshold;

    private Integer successThreshold;

    private Integer timeoutSeconds;

    private Integer halfOpenMaxRequests;

    private Integer maxAttempts;

    private String retryOnStatusCodes;

    private String retryOnExceptions;

    private Double backoffMultiplier;

    private Integer initialIntervalMs;

    private String description;

    private Boolean enabled;
}
