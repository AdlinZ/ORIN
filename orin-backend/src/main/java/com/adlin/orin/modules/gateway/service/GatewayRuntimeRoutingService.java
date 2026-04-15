package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.entity.GatewayService;
import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.GatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayRuntimeRoutingService {

    private final GatewayRouteRepository routeRepository;
    private final GatewayServiceRepository serviceRepository;
    private final GatewayServiceInstanceRepository instanceRepository;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<Long, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public Optional<ResolvedRoute> resolveRoute(String requestPath, String requestMethod, String queryString) {
        List<GatewayRoute> routes = routeRepository.findActiveRoutesOrderByPriority();
        for (GatewayRoute route : routes) {
            if (!matchesMethod(route.getMethod(), requestMethod)) {
                continue;
            }
            if (!matchesPath(route.getPathPattern(), requestPath)) {
                continue;
            }

            Optional<String> targetBaseUrl = resolveTargetBaseUrl(route);
            if (targetBaseUrl.isEmpty()) {
                log.warn("Skip route {}({}) due to missing target", route.getName(), route.getId());
                continue;
            }

            String targetPath = resolveTargetPath(route, requestPath);
            String targetUrl = appendQuery(joinUrl(targetBaseUrl.get(), targetPath), queryString);
            return Optional.of(ResolvedRoute.builder()
                    .route(route)
                    .targetUrl(targetUrl)
                    .targetService(resolveTargetServiceName(route))
                    .build());
        }
        return Optional.empty();
    }

    public Optional<GatewayRoute> findMatchingRoute(String requestPath, String requestMethod) {
        return routeRepository.findActiveRoutesOrderByPriority().stream()
                .filter(route -> matchesMethod(route.getMethod(), requestMethod))
                .filter(route -> matchesPath(route.getPathPattern(), requestPath))
                .findFirst();
    }

    private boolean matchesMethod(String routeMethod, String requestMethod) {
        if (routeMethod == null || routeMethod.isBlank()) {
            return true;
        }
        return "ALL".equalsIgnoreCase(routeMethod) || routeMethod.equalsIgnoreCase(requestMethod);
    }

    private boolean matchesPath(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        return pathMatcher.match(pattern, path);
    }

    private Optional<String> resolveTargetBaseUrl(GatewayRoute route) {
        if (route.getTargetUrl() != null && !route.getTargetUrl().isBlank()) {
            return Optional.of(trimTrailingSlash(route.getTargetUrl().trim()));
        }

        Long serviceId = route.getServiceId();
        if (serviceId == null) {
            return Optional.empty();
        }

        List<GatewayServiceInstance> instances = instanceRepository.findByServiceIdAndEnabledOrderByHost(serviceId, true)
                .stream()
                .filter(instance -> "UP".equalsIgnoreCase(instance.getStatus()))
                .toList();
        if (instances.isEmpty()) {
            return Optional.empty();
        }

        GatewayServiceInstance selected = selectInstance(serviceId, route.getLoadBalance(), instances);
        return Optional.of(formatInstanceBaseUrl(selected));
    }

    private GatewayServiceInstance selectInstance(Long serviceId, String loadBalance, List<GatewayServiceInstance> instances) {
        if ("RANDOM".equalsIgnoreCase(loadBalance)) {
            int randomIndex = (int) (Math.random() * instances.size());
            return instances.get(randomIndex);
        }

        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceId, id -> new AtomicInteger(0));
        int index = Math.floorMod(counter.getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private String formatInstanceBaseUrl(GatewayServiceInstance instance) {
        String host = instance.getHost() == null ? "" : instance.getHost().trim();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        return trimTrailingSlash(host) + ":" + instance.getPort();
    }

    private String resolveTargetPath(GatewayRoute route, String requestPath) {
        if (route.getRewritePath() != null && !route.getRewritePath().isBlank()) {
            return ensureLeadingSlash(route.getRewritePath().trim());
        }

        String targetPath = requestPath;
        if (Boolean.TRUE.equals(route.getStripPrefix())) {
            String staticPrefix = extractStaticPrefix(route.getPathPattern());
            if (!staticPrefix.isBlank() && requestPath.startsWith(staticPrefix)) {
                targetPath = requestPath.substring(staticPrefix.length());
            }
            if (targetPath.isBlank()) {
                targetPath = "/";
            }
        }
        return ensureLeadingSlash(targetPath);
    }

    private String extractStaticPrefix(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        int wildcardIndex = pattern.indexOf('*');
        String prefix = wildcardIndex >= 0 ? pattern.substring(0, wildcardIndex) : pattern;
        if (prefix.endsWith("/")) {
            return prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private String resolveTargetServiceName(GatewayRoute route) {
        if (route.getServiceId() == null) {
            return null;
        }
        return serviceRepository.findById(route.getServiceId())
                .map(GatewayService::getServiceName)
                .orElse(null);
    }

    private String ensureLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }

    private String trimTrailingSlash(String baseUrl) {
        String normalized = baseUrl;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinUrl(String baseUrl, String path) {
        if (path.startsWith("/")) {
            return baseUrl + path;
        }
        return baseUrl + "/" + path;
    }

    private String appendQuery(String url, String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return url;
        }
        if (url.contains("?")) {
            return url + "&" + queryString;
        }
        return url + "?" + queryString;
    }

    @Data
    @Builder
    public static class ResolvedRoute {
        private GatewayRoute route;
        private String targetUrl;
        private String targetService;
    }
}
