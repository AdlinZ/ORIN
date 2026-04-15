package com.adlin.orin.modules.gateway.controller;

import com.adlin.orin.modules.gateway.dto.*;
import com.adlin.orin.modules.gateway.service.*;
import com.adlin.orin.modules.gateway.config.GatewayStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/gateway")
@RequiredArgsConstructor
@Tag(name = "Gateway Management", description = "API网关管理")
public class GatewayController {

    private final GatewayRouteService routeService;
    private final GatewayServiceManagementService serviceManagementService;
    private final GatewayAclService aclService;
    private final GatewayPolicyService policyService;
    private final GatewayStatsService statsService;
    private final GatewayRuntimeRoutingService runtimeRoutingService;

    // ==================== Overview ====================

    @GetMapping("/overview")
    @Operation(summary = "获取网关概览统计")
    public GatewayOverviewResponse getOverview() {
        return statsService.getOverview();
    }

    @GetMapping("/overview/trends")
    @Operation(summary = "获取请求趋势")
    public List<Map<String, Object>> getTrends(
            @RequestParam(defaultValue = "1") int hours) {
        return statsService.getTrends(hours);
    }

    // ==================== Routes ====================

    @GetMapping("/routes")
    @Operation(summary = "获取所有路由")
    public List<GatewayRouteResponse> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @GetMapping("/routes/{id}")
    @Operation(summary = "获取路由详情")
    public GatewayRouteResponse getRoute(@PathVariable Long id) {
        return routeService.getRoute(id);
    }

    @PostMapping("/routes")
    @Operation(summary = "创建路由")
    public GatewayRouteResponse createRoute(@RequestBody GatewayRouteRequest request) {
        return routeService.createRoute(request);
    }

    @PutMapping("/routes/{id}")
    @Operation(summary = "更新路由")
    public GatewayRouteResponse updateRoute(
            @PathVariable Long id,
            @RequestBody GatewayRouteRequest request) {
        return routeService.updateRoute(id, request);
    }

    @PatchMapping("/routes/{id}")
    @Operation(summary = "更新路由部分字段")
    public GatewayRouteResponse patchRoute(
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
    public GatewayTestRouteResponse testRoute(@RequestBody GatewayTestRouteRequest request) {
        long start = System.currentTimeMillis();
        String method = request.getMethod() != null ? request.getMethod() : "GET";

        return runtimeRoutingService.resolveRoute(request.getPath(), method, null)
                .map(resolved -> GatewayTestRouteResponse.builder()
                        .success(true)
                        .statusCode(200)
                        .latencyMs(System.currentTimeMillis() - start)
                        .matchedRoute(resolved.getRoute().getName())
                        .targetUrl(resolved.getTargetUrl())
                        .message("Route matched successfully")
                        .build())
                .orElseGet(() -> GatewayTestRouteResponse.builder()
                        .success(false)
                        .statusCode(404)
                        .latencyMs(System.currentTimeMillis() - start)
                        .matchedRoute(null)
                        .targetUrl(null)
                        .message("No matching route found")
                        .build());
    }

    // ==================== Services ====================

    @GetMapping("/services")
    @Operation(summary = "获取所有服务")
    public List<GatewayServiceResponse> getAllServices() {
        return serviceManagementService.getAllServices();
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "获取服务详情")
    public GatewayServiceResponse getService(@PathVariable Long id) {
        return serviceManagementService.getService(id);
    }

    @PostMapping("/services")
    @Operation(summary = "创建服务")
    public GatewayServiceResponse createService(@RequestBody GatewayServiceRequest request) {
        return serviceManagementService.createService(request);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "更新服务")
    public GatewayServiceResponse updateService(
            @PathVariable Long id,
            @RequestBody GatewayServiceRequest request) {
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
    public List<GatewayServiceInstanceResponse> getInstances(@PathVariable Long serviceId) {
        return serviceManagementService.getInstances(serviceId);
    }

    @PostMapping("/services/{serviceId}/instances")
    @Operation(summary = "创建服务实例")
    public GatewayServiceInstanceResponse createInstance(
            @PathVariable Long serviceId,
            @RequestBody GatewayServiceInstanceRequest request) {
        return serviceManagementService.createInstance(serviceId, request);
    }

    @PutMapping("/services/{serviceId}/instances/{instanceId}")
    @Operation(summary = "更新服务实例")
    public GatewayServiceInstanceResponse updateInstance(
            @PathVariable Long serviceId,
            @PathVariable Long instanceId,
            @RequestBody GatewayServiceInstanceRequest request) {
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
    public List<GatewayAclRuleResponse> getAllAclRules() {
        return aclService.getAllRules();
    }

    @GetMapping("/acl/{id}")
    @Operation(summary = "获取ACL规则详情")
    public GatewayAclRuleResponse getAclRule(@PathVariable Long id) {
        return aclService.getRule(id);
    }

    @PostMapping("/acl")
    @Operation(summary = "创建ACL规则")
    public GatewayAclRuleResponse createAclRule(@RequestBody GatewayAclRuleRequest request) {
        return aclService.createRule(request);
    }

    @PutMapping("/acl/{id}")
    @Operation(summary = "更新ACL规则")
    public GatewayAclRuleResponse updateAclRule(
            @PathVariable Long id,
            @RequestBody GatewayAclRuleRequest request) {
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
    public GatewayPoliciesResponse getAllPolicies() {
        return policyService.getAllPolicies();
    }

    @GetMapping("/policies/rate-limit")
    @Operation(summary = "获取限流策略列表")
    public List<GatewayPolicyResponse> getRateLimitPolicies() {
        return policyService.getRateLimitPolicies();
    }

    @PostMapping("/policies/rate-limit")
    @Operation(summary = "创建限流策略")
    public GatewayPolicyResponse createRateLimitPolicy(@RequestBody GatewayPolicyRequest request) {
        return policyService.createRateLimitPolicy(request);
    }

    @PutMapping("/policies/rate-limit/{id}")
    @Operation(summary = "更新限流策略")
    public GatewayPolicyResponse updateRateLimitPolicy(
            @PathVariable Long id,
            @RequestBody GatewayPolicyRequest request) {
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
    public List<GatewayPolicyResponse> getCircuitBreakerPolicies() {
        return policyService.getCircuitBreakerPolicies();
    }

    @PostMapping("/policies/circuit-breaker")
    @Operation(summary = "创建熔断策略")
    public GatewayPolicyResponse createCircuitBreakerPolicy(@RequestBody GatewayPolicyRequest request) {
        return policyService.createCircuitBreakerPolicy(request);
    }

    @PutMapping("/policies/circuit-breaker/{id}")
    @Operation(summary = "更新熔断策略")
    public GatewayPolicyResponse updateCircuitBreakerPolicy(
            @PathVariable Long id,
            @RequestBody GatewayPolicyRequest request) {
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
    public List<GatewayPolicyResponse> getRetryPolicies() {
        return policyService.getRetryPolicies();
    }

    @PostMapping("/policies/retry")
    @Operation(summary = "创建重试策略")
    public GatewayPolicyResponse createRetryPolicy(@RequestBody GatewayPolicyRequest request) {
        return policyService.createRetryPolicy(request);
    }

    @PutMapping("/policies/retry/{id}")
    @Operation(summary = "更新重试策略")
    public GatewayPolicyResponse updateRetryPolicy(
            @PathVariable Long id,
            @RequestBody GatewayPolicyRequest request) {
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
    public Page<GatewayAuditLogResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return statsService.getAuditLogs(routeId, result, startDate, endDate, PageRequest.of(page, size));
    }
}
