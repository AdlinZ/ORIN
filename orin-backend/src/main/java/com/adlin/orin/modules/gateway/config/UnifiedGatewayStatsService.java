package com.adlin.orin.modules.gateway.config;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayAuditLogResponse;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayOverviewResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceRepository;
import com.adlin.orin.gateway.service.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayStatsService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UnifiedGatewayRouteRepository routeRepository;
    private final UnifiedGatewayServiceRepository serviceRepository;
    private final UnifiedGatewayServiceInstanceRepository instanceRepository;
    private final UnifiedGatewayAuditLogRepository auditLogRepository;
    private final ProviderRegistry providerRegistry;

    private static final String STATS_TOTAL_REQUESTS = "gateway:stats:total_requests";
    private static final String STATS_TOTAL_ERRORS = "gateway:stats:total_errors";
    private static final String STATS_LAST_MINUTE = "gateway:stats:minute:";
    private static final String STATS_LATENCY_SUM = "gateway:stats:latency_sum";
    private static final String STATS_LATENCY_COUNT = "gateway:stats:latency_count";
    private static final String STATS_ROUTE_COUNT = "gateway:stats:route:";
    private static final String STATS_ROUTE_LATENCY_SUM = "gateway:stats:route_latency_sum:";
    private static final String STATS_ROUTE_LATENCY_COUNT = "gateway:stats:route_latency_count:";
    private static final DateTimeFormatter MINUTE_BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm");
    private static final DateTimeFormatter TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public UnifiedGatewayOverviewResponse getOverview() {
        long totalRequests = getTotalRequests();
        double qps = calculateQPS();
        double errorRate = getErrorRate();
        Double avgLatency = getAverageLatency();

        List<UnifiedGatewayService> services = serviceRepository.findAll();
        List<UnifiedGatewayServiceInstance> instances = instanceRepository.findAll();
        int configuredHealthyInstances = (int) instances.stream().filter(i -> "UP".equals(i.getStatus())).count();
        int activeRoutes = routeRepository.findActiveRoutesOrderByPriority().size();
        int activeServices = serviceRepository.findByEnabledOrderByServiceName(true).size();
        Map<String, Object> providerStats = providerRegistry.getStatistics();
        int providerCount = asInt(providerStats.get("totalProviders"));
        int healthyProviderCount = asInt(providerStats.get("healthyProviders"));
        int unhealthyProviderCount = asInt(providerStats.get("unhealthyProviders"));
        int healthyInstances = instances.isEmpty() ? healthyProviderCount : configuredHealthyInstances;
        int unhealthyInstances = instances.isEmpty() ? unhealthyProviderCount : instances.size() - configuredHealthyInstances;

        List<UnifiedGatewayOverviewResponse.ServiceHealthSummary> serviceHealthSummaries = services.stream()
                .map(s -> {
                    List<UnifiedGatewayServiceInstance> serviceInstances = instances.stream()
                            .filter(i -> Objects.equals(i.getServiceId(), s.getId()))
                            .collect(Collectors.toList());
                    int healthy = (int) serviceInstances.stream().filter(i -> "UP".equals(i.getStatus())).count();
                    String status = serviceInstances.isEmpty() ? "NO_INSTANCES" :
                                    healthy == serviceInstances.size() ? "HEALTHY" :
                                    healthy > 0 ? "DEGRADED" : "UNHEALTHY";
                    return UnifiedGatewayOverviewResponse.ServiceHealthSummary.builder()
                            .serviceId(s.getId())
                            .serviceName(s.getServiceName())
                            .status(status)
                            .instanceCount(serviceInstances.size())
                            .healthyCount(healthy)
                            .build();
                })
                .collect(Collectors.toList());
        if (serviceHealthSummaries.isEmpty()) {
            serviceHealthSummaries = providerRegistry.getProviderDetails().stream()
                    .map(provider -> {
                        boolean healthy = Boolean.TRUE.equals(provider.get("healthy"));
                        return UnifiedGatewayOverviewResponse.ServiceHealthSummary.builder()
                                .serviceId(null)
                                .serviceName(String.valueOf(provider.getOrDefault("name", provider.get("id"))))
                                .status(healthy ? "HEALTHY" : "UNHEALTHY")
                                .instanceCount(1)
                                .healthyCount(healthy ? 1 : 0)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        List<UnifiedGatewayOverviewResponse.RouteTrafficSummary> topRoutes = getTopRoutes(5);

        return UnifiedGatewayOverviewResponse.builder()
                .totalRequests(totalRequests)
                .qps(Math.round(qps * 100.0) / 100.0)
                .avgLatencyMs(avgLatency != null ? avgLatency.longValue() : 0L)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .activeRoutes(activeRoutes > 0 ? activeRoutes : countUnifiedRoutes())
                .activeServices(activeServices > 0 ? activeServices : providerCount)
                .healthyInstances(healthyInstances)
                .unhealthyInstances(unhealthyInstances)
                .serviceHealth(serviceHealthSummaries)
                .topRoutes(topRoutes)
                .build();
    }

    public List<Map<String, Object>> getTrends(int hours) {
        List<Map<String, Object>> trends = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = hours * 4; i >= 0; i--) {
            LocalDateTime time = now.minusMinutes(i * 15L);
            String minuteKey = minuteKey(time);
            String countStr = redisTemplate.opsForValue().get(minuteKey);

            Map<String, Object> point = new HashMap<>();
            point.put("time", time.format(TIME_LABEL_FORMATTER));
            point.put("requests", countStr != null ? Integer.parseInt(countStr) : 0);
            trends.add(point);
        }
        return trends;
    }

    public void incrementRequestCount() {
        redisTemplate.opsForValue().increment(STATS_TOTAL_REQUESTS);
        String minuteKey = minuteKey(LocalDateTime.now());
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, 7200, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void incrementErrorCount() {
        redisTemplate.opsForValue().increment(STATS_TOTAL_ERRORS);
    }

    public void recordUnifiedRoute(String routeName, int statusCode, long latencyMs) {
        incrementRequestCount();
        if (statusCode >= 400) {
            incrementErrorCount();
        }

        long safeLatency = Math.max(0L, latencyMs);
        redisTemplate.opsForValue().increment(STATS_LATENCY_SUM, safeLatency);
        redisTemplate.opsForValue().increment(STATS_LATENCY_COUNT);
        redisTemplate.opsForValue().increment(STATS_ROUTE_COUNT + routeName);
        redisTemplate.opsForValue().increment(STATS_ROUTE_LATENCY_SUM + routeName, safeLatency);
        redisTemplate.opsForValue().increment(STATS_ROUTE_LATENCY_COUNT + routeName);
    }

    private long getTotalRequests() {
        String val = redisTemplate.opsForValue().get(STATS_TOTAL_REQUESTS);
        return val != null ? Long.parseLong(val) : 0L;
    }

    private double calculateQPS() {
        LocalDateTime now = LocalDateTime.now();
        int requestsInLastMinute = 0;

        for (int i = 0; i < 6; i++) {
            String minuteKey = minuteKey(now.minusMinutes(i));
            String val = redisTemplate.opsForValue().get(minuteKey);
            if (val != null) {
                requestsInLastMinute += Integer.parseInt(val);
            }
        }
        return requestsInLastMinute / 60.0;
    }

    private String minuteKey(LocalDateTime time) {
        return STATS_LAST_MINUTE + time.format(MINUTE_BUCKET_FORMATTER);
    }

    private double getErrorRate() {
        long total = getTotalRequests();
        if (total == 0) return 0.0;
        String errorsStr = redisTemplate.opsForValue().get(STATS_TOTAL_ERRORS);
        long errors = errorsStr != null ? Long.parseLong(errorsStr) : 0;
        return Math.min(errors, total) * 100.0 / total;
    }

    private Double getAverageLatency() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        Double auditAverage = auditLogRepository.findAverageLatencySince(since);
        long latencyCount = parseLong(redisTemplate.opsForValue().get(STATS_LATENCY_COUNT));
        if (latencyCount <= 0) {
            return auditAverage;
        }

        long latencySum = parseLong(redisTemplate.opsForValue().get(STATS_LATENCY_SUM));
        double unifiedAverage = latencySum * 1.0 / latencyCount;
        if (auditAverage == null) {
            return unifiedAverage;
        }
        return (auditAverage + unifiedAverage) / 2;
    }

    public Page<UnifiedGatewayAuditLogResponse> getAuditLogs(Long routeId, String result,
                                                        LocalDateTime startDate, LocalDateTime endDate,
                                                        Pageable pageable) {
        Page<com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog> logs =
            auditLogRepository.findByFilters(routeId, result, startDate, endDate, pageable);
        return logs.map(UnifiedGatewayAuditLogResponse::fromEntity);
    }

    private List<UnifiedGatewayOverviewResponse.RouteTrafficSummary> getTopRoutes(int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Object[]> results = auditLogRepository.findAll()
                .stream()
                .filter(a -> a.getRouteId() != null)
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(since))
                .collect(Collectors.groupingBy(a -> a.getRouteId()))
                .entrySet().stream()
                .map(e -> {
                    Long routeId = e.getKey();
                    var logs = e.getValue();
                    long count = logs.size();
                    double avgLat = logs.stream()
                            .filter(l -> l.getLatencyMs() != null)
                            .mapToLong(l -> l.getLatencyMs())
                            .average().orElse(0);
                    return new Object[]{routeId, count, avgLat};
                })
                .sorted((a, b) -> Long.compare((Long) b[1], (Long) a[1]))
                .limit(limit)
                .collect(Collectors.toList());

        List<UnifiedGatewayOverviewResponse.RouteTrafficSummary> auditRoutes = results.stream()
                .map(r -> {
                    Long routeId = (Long) r[0];
                    String routeName = routeRepository.findById(routeId)
                            .map(route -> route.getName())
                            .orElse("Unknown");
                    return UnifiedGatewayOverviewResponse.RouteTrafficSummary.builder()
                            .routeId(routeId)
                            .routeName(routeName)
                            .requestCount((Long) r[1])
                            .avgLatencyMs((Double) r[2])
                            .build();
                })
                .collect(Collectors.toList());
        if (!auditRoutes.isEmpty()) {
            return auditRoutes;
        }

        return getUnifiedTopRoutes(limit);
    }

    private List<UnifiedGatewayOverviewResponse.RouteTrafficSummary> getUnifiedTopRoutes(int limit) {
        Set<String> routeKeys = redisTemplate.keys(STATS_ROUTE_COUNT + "*");
        if (routeKeys == null || routeKeys.isEmpty()) {
            return List.of();
        }

        return routeKeys.stream()
                .map(key -> {
                    String routeName = key.substring(STATS_ROUTE_COUNT.length());
                    long count = parseLong(redisTemplate.opsForValue().get(key));
                    long latencySum = parseLong(redisTemplate.opsForValue().get(STATS_ROUTE_LATENCY_SUM + routeName));
                    long latencyCount = parseLong(redisTemplate.opsForValue().get(STATS_ROUTE_LATENCY_COUNT + routeName));
                    double avgLatency = latencyCount > 0 ? latencySum * 1.0 / latencyCount : 0;
                    return UnifiedGatewayOverviewResponse.RouteTrafficSummary.builder()
                            .routeId(null)
                            .routeName(routeName)
                            .pathPattern(routeName)
                            .requestCount(count)
                            .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                            .build();
                })
                .sorted(Comparator.comparing(UnifiedGatewayOverviewResponse.RouteTrafficSummary::getRequestCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int countUnifiedRoutes() {
        Set<String> routeKeys = redisTemplate.keys(STATS_ROUTE_COUNT + "*");
        return routeKeys == null ? 0 : routeKeys.size();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
