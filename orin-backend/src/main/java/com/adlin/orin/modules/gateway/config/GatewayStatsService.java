package com.adlin.orin.modules.gateway.config;

import com.adlin.orin.modules.gateway.dto.GatewayAuditLogResponse;
import com.adlin.orin.modules.gateway.dto.GatewayOverviewResponse;
import com.adlin.orin.modules.gateway.entity.GatewayService;
import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.GatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.GatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceRepository;
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
public class GatewayStatsService {

    private final RedisTemplate<String, String> redisTemplate;
    private final GatewayRouteRepository routeRepository;
    private final GatewayServiceRepository serviceRepository;
    private final GatewayServiceInstanceRepository instanceRepository;
    private final GatewayAuditLogRepository auditLogRepository;

    private static final String STATS_TOTAL_REQUESTS = "gateway:stats:total_requests";
    private static final String STATS_TOTAL_ERRORS = "gateway:stats:total_errors";
    private static final String STATS_LAST_MINUTE = "gateway:stats:minute:";

    public GatewayOverviewResponse getOverview() {
        long totalRequests = getTotalRequests();
        double qps = calculateQPS();
        double errorRate = getErrorRate();
        Double avgLatency = getAverageLatency();

        List<GatewayService> services = serviceRepository.findAll();
        List<GatewayServiceInstance> instances = instanceRepository.findAll();
        int healthyInstances = (int) instances.stream().filter(i -> "UP".equals(i.getStatus())).count();
        int activeRoutes = routeRepository.findActiveRoutesOrderByPriority().size();
        int activeServices = serviceRepository.findByEnabledOrderByServiceName(true).size();

        List<GatewayOverviewResponse.ServiceHealthSummary> serviceHealthSummaries = services.stream()
                .map(s -> {
                    List<GatewayServiceInstance> serviceInstances = instances.stream()
                            .filter(i -> i.getServiceId().equals(s.getId()))
                            .collect(Collectors.toList());
                    int healthy = (int) serviceInstances.stream().filter(i -> "UP".equals(i.getStatus())).count();
                    String status = healthy == serviceInstances.size() ? "HEALTHY" :
                                    healthy > 0 ? "DEGRADED" : "UNHEALTHY";
                    return GatewayOverviewResponse.ServiceHealthSummary.builder()
                            .serviceId(s.getId())
                            .serviceName(s.getServiceName())
                            .status(status)
                            .instanceCount(serviceInstances.size())
                            .healthyCount(healthy)
                            .build();
                })
                .collect(Collectors.toList());

        List<GatewayOverviewResponse.RouteTrafficSummary> topRoutes = getTopRoutes(5);

        return GatewayOverviewResponse.builder()
                .totalRequests(totalRequests)
                .qps(Math.round(qps * 100.0) / 100.0)
                .avgLatencyMs(avgLatency != null ? avgLatency.longValue() : 0L)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .activeRoutes(activeRoutes)
                .activeServices(activeServices)
                .healthyInstances(healthyInstances)
                .unhealthyInstances(instances.size() - healthyInstances)
                .serviceHealth(serviceHealthSummaries)
                .topRoutes(topRoutes)
                .build();
    }

    public List<Map<String, Object>> getTrends(int hours) {
        List<Map<String, Object>> trends = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int i = hours * 4; i >= 0; i--) {
            LocalDateTime time = now.minusMinutes(i * 15L);
            String minuteKey = STATS_LAST_MINUTE + (time.toLocalDate().toString() + ":" + time.format(formatter));
            String countStr = redisTemplate.opsForValue().get(minuteKey);

            Map<String, Object> point = new HashMap<>();
            point.put("time", time.format(formatter));
            point.put("requests", countStr != null ? Integer.parseInt(countStr) : 0);
            trends.add(point);
        }
        return trends;
    }

    public void incrementRequestCount() {
        redisTemplate.opsForValue().increment(STATS_TOTAL_REQUESTS);
        String minuteKey = STATS_LAST_MINUTE + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, 7200, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void incrementErrorCount() {
        redisTemplate.opsForValue().increment(STATS_TOTAL_ERRORS);
    }

    private long getTotalRequests() {
        String val = redisTemplate.opsForValue().get(STATS_TOTAL_REQUESTS);
        return val != null ? Long.parseLong(val) : 0L;
    }

    private double calculateQPS() {
        LocalDateTime now = LocalDateTime.now();
        int requestsInLastMinute = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int i = 0; i < 6; i++) {
            String minuteKey = STATS_LAST_MINUTE + now.minusMinutes(i).format(formatter);
            String val = redisTemplate.opsForValue().get(minuteKey);
            if (val != null) {
                requestsInLastMinute += Integer.parseInt(val);
            }
        }
        return requestsInLastMinute / 60.0;
    }

    private double getErrorRate() {
        long total = getTotalRequests();
        if (total == 0) return 0.0;
        String errorsStr = redisTemplate.opsForValue().get(STATS_TOTAL_ERRORS);
        long errors = errorsStr != null ? Long.parseLong(errorsStr) : 0;
        return errors * 100.0 / total;
    }

    private Double getAverageLatency() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        return auditLogRepository.findAverageLatencySince(since);
    }

    public Page<GatewayAuditLogResponse> getAuditLogs(Long routeId, String result,
                                                        LocalDateTime startDate, LocalDateTime endDate,
                                                        Pageable pageable) {
        Page<com.adlin.orin.modules.gateway.entity.GatewayAuditLog> logs =
            auditLogRepository.findByFilters(routeId, result, startDate, endDate, pageable);
        return logs.map(GatewayAuditLogResponse::fromEntity);
    }

    private List<GatewayOverviewResponse.RouteTrafficSummary> getTopRoutes(int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Object[]> results = auditLogRepository.findAll()
                .stream()
                .filter(a -> a.getCreatedAt().isAfter(since))
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

        return results.stream()
                .map(r -> {
                    Long routeId = (Long) r[0];
                    String routeName = routeRepository.findById(routeId)
                            .map(route -> route.getName())
                            .orElse("Unknown");
                    return GatewayOverviewResponse.RouteTrafficSummary.builder()
                            .routeId(routeId)
                            .routeName(routeName)
                            .requestCount((Long) r[1])
                            .avgLatencyMs((Double) r[2])
                            .build();
                })
                .collect(Collectors.toList());
    }
}
