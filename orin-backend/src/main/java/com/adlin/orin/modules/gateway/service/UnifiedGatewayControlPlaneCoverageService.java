package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayControlPlaneCoverageResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class UnifiedGatewayControlPlaneCoverageService {

    public static final String STATUS_BASELINE_GOVERNED = "BASELINE_GOVERNED";
    public static final String STATUS_POLICY_ENFORCED = "POLICY_ENFORCED";
    public static final String STATUS_RESCUE_RESERVED = "RESCUE_RESERVED";
    public static final String STATUS_ATTENTION_REQUIRED = "ATTENTION_REQUIRED";

    private static final List<String> RESCUE_PATTERNS = List.of(
            "/api/v1/auth/**",
            "/api/v1/health",
            "/api/v1/system/gateway/**");

    private final RequestMappingHandlerMapping handlerMapping;
    private final UnifiedGatewayRouteRepository routeRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public UnifiedGatewayControlPlaneCoverageService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            UnifiedGatewayRouteRepository routeRepository) {
        this.handlerMapping = handlerMapping;
        this.routeRepository = routeRepository;
    }

    public UnifiedGatewayControlPlaneCoverageResponse getCoverage() {
        List<UnifiedGatewayRoute> localRoutes = routeRepository.findActiveRoutesOrderByPriority().stream()
                .filter(this::isLocalRoute)
                .toList();

        List<UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage> endpoints = handlerMapping.getHandlerMethods()
                .keySet()
                .stream()
                .flatMap(info -> endpointRows(info, localRoutes).stream())
                .sorted(Comparator.comparing(UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage::getPathPattern)
                        .thenComparing(row -> String.join(",", row.getMethods())))
                .toList();

        long total = endpoints.size();
        long baseline = endpoints.stream().filter(row -> STATUS_BASELINE_GOVERNED.equals(row.getStatus())).count();
        long policyEnforced = endpoints.stream().filter(row -> STATUS_POLICY_ENFORCED.equals(row.getStatus())).count();
        long attention = endpoints.stream().filter(row -> STATUS_ATTENTION_REQUIRED.equals(row.getStatus())).count();
        long rescue = endpoints.stream().filter(row -> STATUS_RESCUE_RESERVED.equals(row.getStatus())).count();
        double explicitPolicyCoverageRate = total == 0 ? 0 : Math.round((policyEnforced * 10000.0) / total) / 100.0;

        return UnifiedGatewayControlPlaneCoverageResponse.builder()
                .summary(UnifiedGatewayControlPlaneCoverageResponse.Summary.builder()
                        .totalEndpoints(total)
                        .baselineGovernedEndpoints(baseline)
                        .policyEnforcedEndpoints(policyEnforced)
                        .attentionRequiredEndpoints(attention)
                        .rescueReservedEndpoints(rescue)
                        .explicitPolicyCoverageRate(explicitPolicyCoverageRate)
                        .managedEndpoints(policyEnforced)
                        .observedOnlyEndpoints(attention)
                        .managedRate(explicitPolicyCoverageRate)
                        .build())
                .endpoints(endpoints)
                .build();
    }

    private List<UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage> endpointRows(
            RequestMappingInfo info,
            List<UnifiedGatewayRoute> localRoutes) {
        List<String> patterns = patterns(info);
        List<String> methods = methods(info);
        List<UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage> rows = new ArrayList<>();

        for (String pattern : patterns) {
            if (!pattern.startsWith("/api/v1/")) {
                continue;
            }

            if (isRescuePath(pattern)) {
                rows.add(row(pattern, methods, STATUS_RESCUE_RESERVED, null,
                        "救援入口保留最低可用直连能力，同时记录 trace 与审计"));
                continue;
            }

            Optional<UnifiedGatewayRoute> route = localRoutes.stream()
                    .filter(candidate -> methods.stream().anyMatch(method -> methodMatches(candidate.getMethod(), method)))
                    .filter(candidate -> pathOverlaps(candidate.getPathPattern(), pattern))
                    .findFirst();

            if (route.isPresent()) {
                UnifiedGatewayRoute matchedRoute = route.get();
                if (requiresAttention(matchedRoute)) {
                    rows.add(row(pattern, methods, STATUS_ATTENTION_REQUIRED, matchedRoute,
                            "已配置入口但缺少认证或显式治理策略，建议补充访问策略"));
                } else {
                    rows.add(row(pattern, methods, STATUS_POLICY_ENFORCED, matchedRoute,
                            "已绑定显式入口策略"));
                }
            } else {
                rows.add(row(pattern, methods, STATUS_BASELINE_GOVERNED, null,
                        "默认经过统一网关基础链路，记录 trace、审计与运行状态"));
            }
        }
        return rows;
    }

    private List<String> patterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues().stream().sorted().toList();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns().stream().sorted().toList();
        }
        return List.of();
    }

    private List<String> methods(RequestMappingInfo info) {
        Set<org.springframework.web.bind.annotation.RequestMethod> requestMethods = info.getMethodsCondition().getMethods();
        if (requestMethods == null || requestMethods.isEmpty()) {
            return List.of("ALL");
        }
        return requestMethods.stream().map(Enum::name).sorted().toList();
    }

    private UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage row(
            String pattern,
            List<String> methods,
            String status,
            UnifiedGatewayRoute route,
            String reason) {
        return UnifiedGatewayControlPlaneCoverageResponse.EndpointCoverage.builder()
                .pathPattern(pattern)
                .methods(methods)
                .status(status)
                .routeId(route != null ? route.getId() : null)
                .routeName(route != null ? route.getName() : null)
                .reason(reason)
                .build();
    }

    private boolean isLocalRoute(UnifiedGatewayRoute route) {
        return (route.getTargetUrl() == null || route.getTargetUrl().isBlank())
                && route.getServiceId() == null;
    }

    private boolean requiresAttention(UnifiedGatewayRoute route) {
        return !Boolean.TRUE.equals(route.getAuthRequired())
                && route.getRateLimitPolicyId() == null
                && route.getCircuitBreakerPolicyId() == null
                && route.getRetryPolicyId() == null;
    }

    public boolean isRescuePath(String path) {
        return RESCUE_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean methodMatches(String routeMethod, String endpointMethod) {
        return routeMethod == null
                || routeMethod.isBlank()
                || "ALL".equalsIgnoreCase(routeMethod)
                || "ALL".equalsIgnoreCase(endpointMethod)
                || routeMethod.equalsIgnoreCase(endpointMethod);
    }

    private boolean pathOverlaps(String routePattern, String endpointPattern) {
        if (routePattern == null || endpointPattern == null) {
            return false;
        }
        return Objects.equals(routePattern, endpointPattern)
                || pathMatcher.match(routePattern, endpointPattern)
                || pathMatcher.match(endpointPattern, routePattern);
    }
}
