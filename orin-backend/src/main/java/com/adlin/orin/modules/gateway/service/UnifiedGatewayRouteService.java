package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayRouteRequest;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayRouteResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayRouteService {

    private final UnifiedGatewayRouteRepository routeRepository;
    private final UnifiedGatewayCircuitBreakerService circuitBreakerService;
    private final UnifiedGatewayRateLimiterService rateLimiterService;

    public List<UnifiedGatewayRouteResponse> getAllRoutes() {
        return routeRepository.findAllByOrderByPriorityDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UnifiedGatewayRouteResponse> getActiveRoutes() {
        return routeRepository.findActiveRoutesOrderByPriority().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UnifiedGatewayRouteResponse getRoute(Long id) {
        UnifiedGatewayRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found: " + id));
        return toResponse(route);
    }

    @Transactional
    public UnifiedGatewayRouteResponse createRoute(UnifiedGatewayRouteRequest request) {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .name(request.getName())
                .pathPattern(request.getPathPattern())
                .method(request.getMethod() != null ? request.getMethod() : "ALL")
                .serviceId(request.getServiceId())
                .targetUrl(request.getTargetUrl())
                .stripPrefix(request.getStripPrefix() != null ? request.getStripPrefix() : false)
                .rewritePath(request.getRewritePath())
                .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000)
                .retryCount(request.getRetryCount() != null ? request.getRetryCount() : 0)
                .loadBalance(request.getLoadBalance() != null ? request.getLoadBalance() : "ROUND_ROBIN")
                .authRequired(request.getAuthRequired() != null ? request.getAuthRequired() : true)
                .rateLimitPolicyId(request.getRateLimitPolicyId())
                .circuitBreakerPolicyId(request.getCircuitBreakerPolicyId())
                .retryPolicyId(request.getRetryPolicyId())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .description(request.getDescription())
                .build();
        route = routeRepository.save(route);
        log.info("Created gateway route: {} ({})", route.getName(), route.getId());
        return toResponse(route);
    }

    @Transactional
    public UnifiedGatewayRouteResponse updateRoute(Long id, UnifiedGatewayRouteRequest request) {
        UnifiedGatewayRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found: " + id));
        if (request.getName() != null) route.setName(request.getName());
        if (request.getPathPattern() != null) route.setPathPattern(request.getPathPattern());
        if (request.getMethod() != null) route.setMethod(request.getMethod());
        if (request.getServiceId() != null) route.setServiceId(request.getServiceId());
        if (request.getTargetUrl() != null) route.setTargetUrl(request.getTargetUrl());
        if (request.getStripPrefix() != null) route.setStripPrefix(request.getStripPrefix());
        if (request.getRewritePath() != null) route.setRewritePath(request.getRewritePath());
        if (request.getTimeoutMs() != null) route.setTimeoutMs(request.getTimeoutMs());
        if (request.getRetryCount() != null) route.setRetryCount(request.getRetryCount());
        if (request.getLoadBalance() != null) route.setLoadBalance(request.getLoadBalance());
        if (request.getAuthRequired() != null) route.setAuthRequired(request.getAuthRequired());
        if (request.getRateLimitPolicyId() != null) route.setRateLimitPolicyId(request.getRateLimitPolicyId());
        if (request.getCircuitBreakerPolicyId() != null) route.setCircuitBreakerPolicyId(request.getCircuitBreakerPolicyId());
        if (request.getRetryPolicyId() != null) route.setRetryPolicyId(request.getRetryPolicyId());
        if (request.getPriority() != null) route.setPriority(request.getPriority());
        if (request.getEnabled() != null) route.setEnabled(request.getEnabled());
        if (request.getDescription() != null) route.setDescription(request.getDescription());
        route = routeRepository.save(route);
        log.info("Updated gateway route: {} ({})", route.getName(), route.getId());
        return toResponse(route);
    }

    @Transactional
    public void deleteRoute(Long id) {
        if (!routeRepository.existsById(id)) {
            throw new RuntimeException("Route not found: " + id);
        }
        // 清理路由关联的熔断器和限流器状态，防止内存泄漏
        circuitBreakerService.removeRouteState(id);
        rateLimiterService.removeRouteBuckets(id);
        routeRepository.deleteById(id);
        log.info("Deleted gateway route: {}", id);
    }

    @Transactional
    public UnifiedGatewayRouteResponse patchRoute(Long id, Map<String, Object> updates) {
        UnifiedGatewayRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found: " + id));
        final UnifiedGatewayRoute finalRoute = route;
        updates.forEach((key, value) -> {
            switch (key) {
                case "enabled" -> finalRoute.setEnabled((Boolean) value);
                case "priority" -> finalRoute.setPriority((Integer) value);
                case "description" -> finalRoute.setDescription((String) value);
            }
        });
        route = routeRepository.save(route);
        return toResponse(route);
    }

    public Optional<UnifiedGatewayRoute> findMatchingRoute(String path, String method) {
        return routeRepository.findActiveRoutesOrderByPriority().stream()
                .filter(r -> matchesPath(r.getPathPattern(), path))
                .filter(r -> "ALL".equalsIgnoreCase(r.getMethod()) ||
                             r.getMethod().equalsIgnoreCase(method))
                .findFirst();
    }

    private boolean matchesPath(String pattern, String path) {
        if (pattern.equals(path)) return true;
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            int slashIdx = path.indexOf('/', prefix.length());
            return path.startsWith(prefix) && (slashIdx == -1 || slashIdx == path.length());
        }
        return false;
    }

    private UnifiedGatewayRouteResponse toResponse(UnifiedGatewayRoute route) {
        return UnifiedGatewayRouteResponse.fromEntity(route);
    }
}
