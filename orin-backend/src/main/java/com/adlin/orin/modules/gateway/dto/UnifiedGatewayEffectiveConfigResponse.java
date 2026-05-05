package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayEffectiveConfigResponse {

    private UnifiedGatewayRouteResponse route;
    private String targetType;
    private UnifiedGatewayServiceResponse service;
    private List<UnifiedGatewayServiceInstanceResponse> healthyInstances;
    private List<UnifiedGatewayServiceInstanceResponse> allInstances;
    private Boolean authRequired;
    private List<UnifiedGatewayAclRuleResponse> aclRules;
    private UnifiedGatewayPolicyResponse rateLimitPolicy;
    private UnifiedGatewayPolicyResponse circuitBreakerPolicy;
    private UnifiedGatewayPolicyResponse retryPolicy;
    private List<ChainStep> chain;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChainStep {
        private String key;
        private String label;
        private String status;
        private String detail;
    }
}
