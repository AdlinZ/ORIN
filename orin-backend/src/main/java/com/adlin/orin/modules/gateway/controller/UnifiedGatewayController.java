package com.adlin.orin.modules.gateway.controller;

import com.adlin.orin.modules.gateway.dto.*;
import com.adlin.orin.modules.gateway.service.*;
import com.adlin.orin.modules.gateway.config.UnifiedGatewayStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/gateway")
@RequiredArgsConstructor
@Tag(name = "Unified Gateway Management", description = "统一网关管理")
public class UnifiedGatewayController {

    private final UnifiedGatewayRouteService routeService;
    private final UnifiedGatewayServiceManagementService serviceManagementService;
    private final UnifiedGatewayAclService aclService;
    private final UnifiedGatewayPolicyService policyService;
    private final UnifiedGatewayStatsService statsService;
    private final UnifiedGatewayRuntimeRoutingService runtimeRoutingService;
    private final UnifiedGatewayWorkbenchService workbenchService;
    private final UnifiedGatewayControlPlaneCoverageService controlPlaneCoverageService;

    // ==================== Overview ====================

    @GetMapping("/overview")
    @Operation(summary = "获取网关概览统计")
    public UnifiedGatewayOverviewResponse getOverview() {
        return statsService.getOverview();
    }

    @GetMapping("/overview/trends")
    @Operation(summary = "获取请求趋势")
    public List<Map<String, Object>> getTrends(
            @RequestParam(defaultValue = "1") int hours) {
        return statsService.getTrends(hours);
    }

    @GetMapping("/workbench")
    @Operation(summary = "获取网关工作台聚合数据")
    public UnifiedGatewayWorkbenchResponse getWorkbench() {
        return workbenchService.getWorkbench();
    }

    @GetMapping("/control-plane/coverage")
    @Operation(summary = "获取控制面入口统一网关治理状态")
    public UnifiedGatewayControlPlaneCoverageResponse getControlPlaneCoverage() {
        return controlPlaneCoverageService.getCoverage();
    }

    // ==================== Routes ====================

    @GetMapping("/routes")
    @Operation(summary = "获取所有路由")
    public List<UnifiedGatewayRouteResponse> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @GetMapping("/routes/{id}")
    @Operation(summary = "获取路由详情")
    public UnifiedGatewayRouteResponse getRoute(@PathVariable Long id) {
        return routeService.getRoute(id);
    }

    @GetMapping("/routes/{id}/effective-config")
    @Operation(summary = "获取路由最终生效配置")
    public UnifiedGatewayEffectiveConfigResponse getRouteEffectiveConfig(@PathVariable Long id) {
        return workbenchService.getEffectiveConfig(id);
    }

    @PostMapping("/routes")
    @Operation(summary = "创建路由")
    public UnifiedGatewayRouteResponse createRoute(@RequestBody UnifiedGatewayRouteRequest request) {
        return routeService.createRoute(request);
    }

    @PutMapping("/routes/{id}")
    @Operation(summary = "更新路由")
    public UnifiedGatewayRouteResponse updateRoute(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayRouteRequest request) {
        return routeService.updateRoute(id, request);
    }

    @PatchMapping("/routes/{id}")
    @Operation(summary = "更新路由部分字段")
    public UnifiedGatewayRouteResponse patchRoute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        return routeService.patchRoute(id, updates);
    }

    @DeleteMapping("/routes/{id}")
    @Operation(summary = "删除路由")
    public Map<String, Object> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return Map.of("success", true, "message", "Route deleted");
    }

    @PostMapping("/routes/test")
    @Operation(summary = "测试路由配置")
    public UnifiedGatewayTestRouteResponse testRoute(@RequestBody UnifiedGatewayTestRouteRequest request) {
        long start = System.currentTimeMillis();
        String method = request.getMethod() != null ? request.getMethod() : "GET";

        return runtimeRoutingService.resolveRoute(request.getPath(), method, null)
                .map(resolved -> UnifiedGatewayTestRouteResponse.builder()
                        .success(true)
                        .statusCode(200)
                        .latencyMs(System.currentTimeMillis() - start)
                        .matchedRoute(resolved.getRoute().getName())
                        .routeType(resolved.getTargetUrl() == null ? "LOCAL" : "PROXY")
                        .targetUrl(resolved.getTargetUrl())
                        .targetService(resolved.getTargetService())
                        .authRequired(resolved.getRoute().getAuthRequired())
                        .message("Route matched successfully")
                        .build())
                .orElseGet(() -> UnifiedGatewayTestRouteResponse.builder()
                        .success(false)
                        .statusCode(404)
                        .latencyMs(System.currentTimeMillis() - start)
                        .matchedRoute(null)
                        .routeType(null)
                        .targetUrl(null)
                        .targetService(null)
                        .authRequired(null)
                        .message("No matching route found")
                        .build());
    }

    // ==================== Services ====================

    @GetMapping("/services")
    @Operation(summary = "获取所有服务")
    public List<UnifiedGatewayServiceResponse> getAllServices(
            @RequestParam(defaultValue = "false") boolean includeInstances) {
        return serviceManagementService.getAllServices(includeInstances);
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "获取服务详情")
    public UnifiedGatewayServiceResponse getService(@PathVariable Long id) {
        return serviceManagementService.getService(id);
    }

    @PostMapping("/services")
    @Operation(summary = "创建服务")
    public UnifiedGatewayServiceResponse createService(@RequestBody UnifiedGatewayServiceRequest request) {
        return serviceManagementService.createService(request);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "更新服务")
    public UnifiedGatewayServiceResponse updateService(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayServiceRequest request) {
        return serviceManagementService.updateService(id, request);
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "删除服务")
    public Map<String, Object> deleteService(@PathVariable Long id) {
        serviceManagementService.deleteService(id);
        return Map.of("success", true, "message", "Service deleted");
    }

    // ==================== Service Instances ====================

    @GetMapping("/services/{serviceId}/instances")
    @Operation(summary = "获取服务实例列表")
    public List<UnifiedGatewayServiceInstanceResponse> getInstances(@PathVariable Long serviceId) {
        return serviceManagementService.getInstances(serviceId);
    }

    @PostMapping("/services/{serviceId}/instances")
    @Operation(summary = "创建服务实例")
    public UnifiedGatewayServiceInstanceResponse createInstance(
            @PathVariable Long serviceId,
            @RequestBody UnifiedGatewayServiceInstanceRequest request) {
        return serviceManagementService.createInstance(serviceId, request);
    }

    @PutMapping("/services/{serviceId}/instances/{instanceId}")
    @Operation(summary = "更新服务实例")
    public UnifiedGatewayServiceInstanceResponse updateInstance(
            @PathVariable Long serviceId,
            @PathVariable Long instanceId,
            @RequestBody UnifiedGatewayServiceInstanceRequest request) {
        return serviceManagementService.updateInstance(serviceId, instanceId, request);
    }

    @DeleteMapping("/services/{serviceId}/instances/{instanceId}")
    @Operation(summary = "删除服务实例")
    public Map<String, Object> deleteInstance(
            @PathVariable Long serviceId,
            @PathVariable Long instanceId) {
        serviceManagementService.deleteInstance(serviceId, instanceId);
        return Map.of("success", true, "message", "Instance deleted");
    }

    @PostMapping("/services/{serviceId}/instances/{instanceId}/health-check")
    @Operation(summary = "手动触发实例健康检查")
    public Map<String, Object> triggerHealthCheck(
            @PathVariable Long serviceId,
            @PathVariable Long instanceId) {
        return serviceManagementService.triggerHealthCheck(serviceId, instanceId);
    }

    // ==================== ACL Rules ====================

    @GetMapping("/acl")
    @Operation(summary = "获取所有ACL规则")
    public List<UnifiedGatewayAclRuleResponse> getAllAclRules() {
        return aclService.getAllRules();
    }

    @GetMapping("/acl/{id}")
    @Operation(summary = "获取ACL规则详情")
    public UnifiedGatewayAclRuleResponse getAclRule(@PathVariable Long id) {
        return aclService.getRule(id);
    }

    @PostMapping("/acl")
    @Operation(summary = "创建ACL规则")
    public UnifiedGatewayAclRuleResponse createAclRule(@RequestBody UnifiedGatewayAclRuleRequest request) {
        return aclService.createRule(request);
    }

    @PutMapping("/acl/{id}")
    @Operation(summary = "更新ACL规则")
    public UnifiedGatewayAclRuleResponse updateAclRule(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayAclRuleRequest request) {
        return aclService.updateRule(id, request);
    }

    @DeleteMapping("/acl/{id}")
    @Operation(summary = "删除ACL规则")
    public Map<String, Object> deleteAclRule(@PathVariable Long id) {
        aclService.deleteRule(id);
        return Map.of("success", true, "message", "ACL rule deleted");
    }

    @PostMapping("/acl/test")
    @Operation(summary = "测试IP是否匹配规则")
    public Map<String, Object> testAclRule(@RequestBody Map<String, String> testRequest) {
        String ip = testRequest.get("ip");
        String path = testRequest.get("path");
        return aclService.testIp(ip, path);
    }

    // ==================== Policies ====================

    @GetMapping("/policies")
    @Operation(summary = "获取所有策略")
    public UnifiedGatewayPoliciesResponse getAllPolicies() {
        return policyService.getAllPolicies();
    }

    @GetMapping("/policies/rate-limit")
    @Operation(summary = "获取限流策略列表")
    public List<UnifiedGatewayPolicyResponse> getRateLimitPolicies() {
        return policyService.getRateLimitPolicies();
    }

    @PostMapping("/policies/rate-limit")
    @Operation(summary = "创建限流策略")
    public UnifiedGatewayPolicyResponse createRateLimitPolicy(@RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.createRateLimitPolicy(request);
    }

    @PutMapping("/policies/rate-limit/{id}")
    @Operation(summary = "更新限流策略")
    public UnifiedGatewayPolicyResponse updateRateLimitPolicy(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.updateRateLimitPolicy(id, request);
    }

    @DeleteMapping("/policies/rate-limit/{id}")
    @Operation(summary = "删除限流策略")
    public Map<String, Object> deleteRateLimitPolicy(@PathVariable Long id) {
        policyService.deleteRateLimitPolicy(id);
        return Map.of("success", true, "message", "Rate limit policy deleted");
    }

    @GetMapping("/policies/circuit-breaker")
    @Operation(summary = "获取熔断策略列表")
    public List<UnifiedGatewayPolicyResponse> getCircuitBreakerPolicies() {
        return policyService.getCircuitBreakerPolicies();
    }

    @PostMapping("/policies/circuit-breaker")
    @Operation(summary = "创建熔断策略")
    public UnifiedGatewayPolicyResponse createCircuitBreakerPolicy(@RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.createCircuitBreakerPolicy(request);
    }

    @PutMapping("/policies/circuit-breaker/{id}")
    @Operation(summary = "更新熔断策略")
    public UnifiedGatewayPolicyResponse updateCircuitBreakerPolicy(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.updateCircuitBreakerPolicy(id, request);
    }

    @DeleteMapping("/policies/circuit-breaker/{id}")
    @Operation(summary = "删除熔断策略")
    public Map<String, Object> deleteCircuitBreakerPolicy(@PathVariable Long id) {
        policyService.deleteCircuitBreakerPolicy(id);
        return Map.of("success", true, "message", "Circuit breaker policy deleted");
    }

    @GetMapping("/policies/retry")
    @Operation(summary = "获取重试策略列表")
    public List<UnifiedGatewayPolicyResponse> getRetryPolicies() {
        return policyService.getRetryPolicies();
    }

    @PostMapping("/policies/retry")
    @Operation(summary = "创建重试策略")
    public UnifiedGatewayPolicyResponse createRetryPolicy(@RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.createRetryPolicy(request);
    }

    @PutMapping("/policies/retry/{id}")
    @Operation(summary = "更新重试策略")
    public UnifiedGatewayPolicyResponse updateRetryPolicy(
            @PathVariable Long id,
            @RequestBody UnifiedGatewayPolicyRequest request) {
        return policyService.updateRetryPolicy(id, request);
    }

    @DeleteMapping("/policies/retry/{id}")
    @Operation(summary = "删除重试策略")
    public Map<String, Object> deleteRetryPolicy(@PathVariable Long id) {
        policyService.deleteRetryPolicy(id);
        return Map.of("success", true, "message", "Retry policy deleted");
    }

    // ==================== Audit Logs ====================

    @GetMapping("/audit-logs")
    @Operation(summary = "获取网关访问日志")
    public Page<UnifiedGatewayAuditLogResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return statsService.getAuditLogs(routeId, result, startDate, endDate, PageRequest.of(page, size));
    }

    @GetMapping("/diagnostics/recent-failures")
    @Operation(summary = "获取最近失败请求")
    public List<UnifiedGatewayAuditLogResponse> getRecentFailures() {
        return workbenchService.getRecentFailures();
    }
}
