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
public class UnifiedGatewayPoliciesResponse {

    private List<UnifiedGatewayPolicyResponse> rateLimitPolicies;
    private List<UnifiedGatewayPolicyResponse> circuitBreakerPolicies;
    private List<UnifiedGatewayPolicyResponse> retryPolicies;
}
