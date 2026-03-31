package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.GatewayRouteRequest;
import com.adlin.orin.modules.gateway.dto.GatewayRouteResponse;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayRouteRepository;
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
public class GatewayRouteService {

    private final GatewayRouteRepository routeRepository;

    public List<GatewayRouteResponse> getAllRoutes() {
        return routeRepository.findAllByOrderByPriorityDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GatewayRouteResponse> getActiveRoutes() {
        return routeRepository.findActiveRoutesOrderByPriority().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public GatewayRouteResponse getRoute(Long id) {
        GatewayRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found: " + id));
        return toResponse(route);
    }

    @Transactional
    public GatewayRouteResponse createRoute(GatewayRouteRequest request) {
        GatewayRoute route = GatewayRoute.builder()
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
    public GatewayRouteResponse updateRoute(Long id, GatewayRouteRequest request) {
        GatewayRoute route = routeRepository.findById(id)
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
        routeRepository.deleteById(id);
        log.info("Deleted gateway route: {}", id);
    }

    @Transactional
    public GatewayRouteResponse patchRoute(Long id, Map<String, Object> updates) {
        GatewayRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found: " + id));
        final GatewayRoute finalRoute = route;
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

    public Optional<GatewayRoute> findMatchingRoute(String path, String method) {
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

    private GatewayRouteResponse toResponse(GatewayRoute route) {
        return GatewayRouteResponse.fromEntity(route);
    }
}
