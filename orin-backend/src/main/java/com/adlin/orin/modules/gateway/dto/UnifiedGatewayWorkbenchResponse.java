package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayWorkbenchResponse {

    private UnifiedGatewayOverviewResponse overview;
    private List<UnifiedGatewayOverviewResponse.ServiceHealthSummary> serviceHealth;
    private List<RouteSummary> routes;
    private Map<String, Long> policyCounts;
    private List<UnifiedGatewayAuditLogResponse> recentFailures;
    private List<String> nextActions;
    private UnifiedGatewayControlPlaneCoverageResponse controlPlaneCoverage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSummary {
        private Long id;
        private String name;
        private String pathPattern;
        private String method;
        private String targetType;
        private String target;
        private Boolean enabled;
        private Integer priority;
        private Boolean authRequired;
        private Integer policyCount;
    }
}
