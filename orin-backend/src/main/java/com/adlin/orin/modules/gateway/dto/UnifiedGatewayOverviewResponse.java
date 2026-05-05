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
public class UnifiedGatewayOverviewResponse {

    private Long totalRequests;
    private Double qps;
    private Long avgLatencyMs;
    private Double errorRate;
    private Integer activeRoutes;
    private Integer activeServices;
    private Integer healthyInstances;
    private Integer unhealthyInstances;
    private List<Map<String, Object>> requestTrends;
    private List<Map<String, Object>> errorDistribution;
    private List<ServiceHealthSummary> serviceHealth;
    private List<RouteTrafficSummary> topRoutes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealthSummary {
        private Long serviceId;
        private String serviceName;
        private String status;
        private Integer instanceCount;
        private Integer healthyCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteTrafficSummary {
        private Long routeId;
        private String routeName;
        private String pathPattern;
        private Long requestCount;
        private Double avgLatencyMs;
    }
}
