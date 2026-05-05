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
public class UnifiedGatewayControlPlaneCoverageResponse {

    private Summary summary;
    private List<EndpointCoverage> endpoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long totalEndpoints;
        private Long baselineGovernedEndpoints;
        private Long policyEnforcedEndpoints;
        private Long attentionRequiredEndpoints;
        private Long rescueReservedEndpoints;
        private Double explicitPolicyCoverageRate;
        /**
         * Backward compatible aliases for older frontend/test callers.
         */
        private Long managedEndpoints;
        private Long observedOnlyEndpoints;
        private Double managedRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointCoverage {
        private String pathPattern;
        private List<String> methods;
        private String status;
        private Long routeId;
        private String routeName;
        private String reason;
    }
}
