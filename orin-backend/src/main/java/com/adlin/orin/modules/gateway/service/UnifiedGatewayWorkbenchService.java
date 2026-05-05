package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.config.UnifiedGatewayStatsService;
import com.adlin.orin.modules.gateway.dto.*;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UnifiedGatewayWorkbenchService {

    private final UnifiedGatewayStatsService statsService;
    private final UnifiedGatewayRouteRepository routeRepository;
    private final UnifiedGatewayAuditLogRepository auditLogRepository;
    private final UnifiedGatewayServiceManagementService serviceManagementService;
    private final UnifiedGatewayPolicyService policyService;
    private final UnifiedGatewayAclService aclService;
    private final UnifiedGatewayControlPlaneCoverageService controlPlaneCoverageService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public UnifiedGatewayWorkbenchResponse getWorkbench() {
        UnifiedGatewayOverviewResponse overview = statsService.getOverview();
        UnifiedGatewayPoliciesResponse policies = policyService.getAllPolicies();
        List<UnifiedGatewayAuditLogResponse> recentFailures = getRecentFailures();
        UnifiedGatewayControlPlaneCoverageResponse controlPlaneCoverage = controlPlaneCoverageService.getCoverage();
        List<UnifiedGatewayWorkbenchResponse.RouteSummary> routes = routeRepository.findAllByOrderByPriorityDesc()
                .stream()
                .map(this::toRouteSummary)
                .toList();

        return UnifiedGatewayWorkbenchResponse.builder()
                .overview(overview)
                .serviceHealth(overview.getServiceHealth())
                .routes(routes)
                .policyCounts(Map.of(
                        "rateLimit", (long) policies.getRateLimitPolicies().size(),
                        "circuitBreaker", (long) policies.getCircuitBreakerPolicies().size(),
                        "retry", (long) policies.getRetryPolicies().size(),
                        "acl", (long) aclService.getAllRules().size()))
                .recentFailures(recentFailures)
                .nextActions(buildNextActions(overview, routes, recentFailures, controlPlaneCoverage))
                .controlPlaneCoverage(controlPlaneCoverage)
                .build();
    }

    public UnifiedGatewayEffectiveConfigResponse getEffectiveConfig(Long routeId) {
        UnifiedGatewayRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found: " + routeId));
        UnifiedGatewayPoliciesResponse policies = policyService.getAllPolicies();
        UnifiedGatewayPolicyResponse rateLimit = findPolicy(policies.getRateLimitPolicies(), route.getRateLimitPolicyId());
        UnifiedGatewayPolicyResponse circuitBreaker = findPolicy(policies.getCircuitBreakerPolicies(), route.getCircuitBreakerPolicyId());
        UnifiedGatewayPolicyResponse retry = findPolicy(policies.getRetryPolicies(), route.getRetryPolicyId());
        UnifiedGatewayServiceResponse service = resolveService(route.getServiceId());
        List<UnifiedGatewayServiceInstanceResponse> allInstances = route.getServiceId() == null
                || service == null ? List.of()
                : serviceManagementService.getInstances(route.getServiceId());
        List<UnifiedGatewayServiceInstanceResponse> healthyInstances = allInstances.stream()
                .filter(instance -> Boolean.TRUE.equals(instance.getEnabled()))
                .filter(instance -> "UP".equalsIgnoreCase(instance.getStatus()))
                .toList();
        List<UnifiedGatewayAclRuleResponse> aclRules = aclService.getAllRules().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .filter(rule -> ruleAppliesToRoute(rule, route))
                .toList();
        String targetType = targetType(route, healthyInstances);
        List<String> warnings = buildWarnings(route, targetType, service, healthyInstances, rateLimit, circuitBreaker, retry);

        return UnifiedGatewayEffectiveConfigResponse.builder()
                .route(enrichRoute(route, service, rateLimit, circuitBreaker, retry))
                .targetType(targetType)
                .service(service)
                .allInstances(allInstances)
                .healthyInstances(healthyInstances)
                .authRequired(route.getAuthRequired())
                .aclRules(aclRules)
                .rateLimitPolicy(rateLimit)
                .circuitBreakerPolicy(circuitBreaker)
                .retryPolicy(retry)
                .chain(buildChain(route, targetType, service, healthyInstances, aclRules, rateLimit, circuitBreaker, retry))
                .warnings(warnings)
                .build();
    }

    public List<UnifiedGatewayAuditLogResponse> getRecentFailures() {
        return auditLogRepository.findTop20ByResultNotOrderByCreatedAtDesc("SUCCESS").stream()
                .filter(log -> "ERROR".equalsIgnoreCase(log.getResult()) || "DENY".equalsIgnoreCase(log.getResult()))
                .sorted(Comparator.comparing(UnifiedGatewayAuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAuditResponse)
                .toList();
    }

    private UnifiedGatewayWorkbenchResponse.RouteSummary toRouteSummary(UnifiedGatewayRoute route) {
        return UnifiedGatewayWorkbenchResponse.RouteSummary.builder()
                .id(route.getId())
                .name(route.getName())
                .pathPattern(route.getPathPattern())
                .method(route.getMethod())
                .targetType(route.getTargetUrl() != null && !route.getTargetUrl().isBlank()
                        ? "DIRECT"
                        : route.getServiceId() != null ? "SERVICE" : "LOCAL")
                .target(route.getTargetUrl() != null && !route.getTargetUrl().isBlank()
                        ? route.getTargetUrl()
                        : route.getServiceId() != null ? "Service#" + route.getServiceId() : "ORIN 后台处理")
                .enabled(route.getEnabled())
                .priority(route.getPriority())
                .authRequired(route.getAuthRequired())
                .policyCount(policyCount(route))
                .build();
    }

    private UnifiedGatewayServiceResponse resolveService(Long serviceId) {
        if (serviceId == null) {
            return null;
        }
        try {
            return serviceManagementService.getService(serviceId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<String> buildNextActions(UnifiedGatewayOverviewResponse overview,
                                          List<UnifiedGatewayWorkbenchResponse.RouteSummary> routes,
                                          List<UnifiedGatewayAuditLogResponse> recentFailures,
                                          UnifiedGatewayControlPlaneCoverageResponse controlPlaneCoverage) {
        List<String> actions = new ArrayList<>();
        long attentionRequired = controlPlaneCoverage != null && controlPlaneCoverage.getSummary() != null
                ? valueOrZero(controlPlaneCoverage.getSummary().getAttentionRequiredEndpoints())
                : 0;
        if (overview.getUnhealthyInstances() != null && overview.getUnhealthyInstances() > 0) {
            actions.add("检查异常实例并触发健康检查");
        }
        if (attentionRequired > 0) {
            actions.add("处理需要关注的入口，补充认证、ACL 或限流策略");
        }
        if (!recentFailures.isEmpty()) {
            actions.add("查看最近失败请求并定位入口链路");
        }
        if (routes.stream().noneMatch(route -> Boolean.TRUE.equals(route.getEnabled()))) {
            actions.add("创建或启用第一个开放入口");
        }
        if (actions.isEmpty()) {
            actions.add("使用入口测试验证核心路径");
        }
        return actions;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private UnifiedGatewayRouteResponse enrichRoute(UnifiedGatewayRoute route,
                                             UnifiedGatewayServiceResponse service,
                                             UnifiedGatewayPolicyResponse rateLimit,
                                             UnifiedGatewayPolicyResponse circuitBreaker,
                                             UnifiedGatewayPolicyResponse retry) {
        UnifiedGatewayRouteResponse response = UnifiedGatewayRouteResponse.fromEntity(route);
        if (service != null) {
            response.setServiceName(service.getServiceName());
        }
        if (rateLimit != null) {
            response.setRateLimitPolicyName(rateLimit.getName());
        }
        if (circuitBreaker != null) {
            response.setCircuitBreakerPolicyName(circuitBreaker.getName());
        }
        if (retry != null) {
            response.setRetryPolicyName(retry.getName());
        }
        return response;
    }

    private List<UnifiedGatewayEffectiveConfigResponse.ChainStep> buildChain(
            UnifiedGatewayRoute route,
            String targetType,
            UnifiedGatewayServiceResponse service,
            List<UnifiedGatewayServiceInstanceResponse> healthyInstances,
            List<UnifiedGatewayAclRuleResponse> aclRules,
            UnifiedGatewayPolicyResponse rateLimit,
            UnifiedGatewayPolicyResponse circuitBreaker,
            UnifiedGatewayPolicyResponse retry) {
        return List.of(
                step("match", "请求匹配", Boolean.TRUE.equals(route.getEnabled()) ? "ACTIVE" : "DISABLED",
                        route.getMethod() + " " + route.getPathPattern()),
                step("acl", "访问控制", aclRules.isEmpty() ? "PASS" : "GOVERNED",
                        aclRules.isEmpty() ? "未匹配专用 ACL，使用默认放行" : aclRules.size() + " 条 ACL 规则会参与判断"),
                step("auth", "认证", Boolean.TRUE.equals(route.getAuthRequired()) ? "REQUIRED" : "OPTIONAL",
                        Boolean.TRUE.equals(route.getAuthRequired()) ? "需要 JWT 或 API Key" : "不强制认证"),
                step("rateLimit", "限流", rateLimit == null ? "SKIPPED" : enabledStatus(rateLimit),
                        rateLimit == null ? "未绑定限流策略" : rateLimit.getName()),
                step("resilience", "熔断/重试", circuitBreaker == null && retry == null ? "SKIPPED" : "GOVERNED",
                        resilienceDetail(circuitBreaker, retry)),
                step("target", "目标", targetType, targetDetail(targetType, route, service, healthyInstances))
        );
    }

    private UnifiedGatewayEffectiveConfigResponse.ChainStep step(String key, String label, String status, String detail) {
        return UnifiedGatewayEffectiveConfigResponse.ChainStep.builder()
                .key(key)
                .label(label)
                .status(status)
                .detail(detail)
                .build();
    }

    private List<String> buildWarnings(UnifiedGatewayRoute route,
                                       String targetType,
                                       UnifiedGatewayServiceResponse service,
                                       List<UnifiedGatewayServiceInstanceResponse> healthyInstances,
                                       UnifiedGatewayPolicyResponse rateLimit,
                                       UnifiedGatewayPolicyResponse circuitBreaker,
                                       UnifiedGatewayPolicyResponse retry) {
        List<String> warnings = new ArrayList<>();
        if (!Boolean.TRUE.equals(route.getEnabled())) {
            warnings.add("路由当前未启用，不会参与运行时匹配");
        }
        if ("BROKEN_SERVICE".equals(targetType)) {
            warnings.add(service == null ? "路由关联的服务不存在" : "路由关联服务没有健康实例");
        }
        if (route.getRateLimitPolicyId() != null && rateLimit == null) {
            warnings.add("绑定的限流策略不存在");
        }
        if (route.getCircuitBreakerPolicyId() != null && circuitBreaker == null) {
            warnings.add("绑定的熔断策略不存在");
        }
        if (route.getRetryPolicyId() != null && retry == null) {
            warnings.add("绑定的重试策略不存在");
        }
        if (healthyInstances.isEmpty() && route.getServiceId() != null && service != null) {
            warnings.add("服务存在但没有可用健康实例");
        }
        return warnings;
    }

    private String targetDetail(String targetType,
                                UnifiedGatewayRoute route,
                                UnifiedGatewayServiceResponse service,
                                List<UnifiedGatewayServiceInstanceResponse> healthyInstances) {
        return switch (targetType) {
            case "LOCAL" -> "请求由 ORIN 本地 Controller 处理";
            case "DIRECT" -> route.getTargetUrl();
            case "SERVICE" -> service.getServiceName() + "，健康实例 " + healthyInstances.size() + " 个";
            default -> "目标服务不可用";
        };
    }

    private String resilienceDetail(UnifiedGatewayPolicyResponse circuitBreaker, UnifiedGatewayPolicyResponse retry) {
        if (circuitBreaker == null && retry == null) {
            return "未绑定熔断或重试策略";
        }
        List<String> parts = new ArrayList<>();
        if (circuitBreaker != null) {
            parts.add("熔断：" + circuitBreaker.getName());
        }
        if (retry != null) {
            parts.add("重试：" + retry.getName());
        }
        return String.join("，", parts);
    }

    private String enabledStatus(UnifiedGatewayPolicyResponse policy) {
        return Boolean.TRUE.equals(policy.getEnabled()) ? "GOVERNED" : "DISABLED";
    }

    private boolean ruleAppliesToRoute(UnifiedGatewayAclRuleResponse rule, UnifiedGatewayRoute route) {
        if (rule.getPathPattern() == null || rule.getPathPattern().isBlank()) {
            return true;
        }
        return pathMatcher.match(rule.getPathPattern(), route.getPathPattern())
                || pathMatcher.match(route.getPathPattern(), rule.getPathPattern())
                || Objects.equals(rule.getPathPattern(), route.getPathPattern());
    }

    private String targetType(UnifiedGatewayRoute route, List<UnifiedGatewayServiceInstanceResponse> healthyInstances) {
        if (route.getTargetUrl() != null && !route.getTargetUrl().isBlank()) {
            return "DIRECT";
        }
        if (route.getServiceId() == null) {
            return "LOCAL";
        }
        return healthyInstances.isEmpty() ? "BROKEN_SERVICE" : "SERVICE";
    }

    private UnifiedGatewayPolicyResponse findPolicy(List<UnifiedGatewayPolicyResponse> policies, Long id) {
        if (id == null) {
            return null;
        }
        Optional<UnifiedGatewayPolicyResponse> policy = policies.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst();
        return policy.orElse(null);
    }

    private int policyCount(UnifiedGatewayRoute route) {
        int count = 0;
        if (route.getRateLimitPolicyId() != null) count++;
        if (route.getCircuitBreakerPolicyId() != null) count++;
        if (route.getRetryPolicyId() != null) count++;
        if (Boolean.TRUE.equals(route.getAuthRequired())) count++;
        return count;
    }

    private UnifiedGatewayAuditLogResponse toAuditResponse(UnifiedGatewayAuditLog log) {
        UnifiedGatewayAuditLogResponse response = UnifiedGatewayAuditLogResponse.fromEntity(log);
        if (log.getRouteId() != null) {
            routeRepository.findById(log.getRouteId()).ifPresent(route -> response.setRouteName(route.getName()));
        }
        return response;
    }
}
