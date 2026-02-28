package com.adlin.orin.modules.apikey.controller;

import com.adlin.orin.modules.apikey.entity.ApiEndpoint;
import com.adlin.orin.modules.apikey.service.ApiEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API端点管理控制器
 */
@RestController
@RequestMapping("/api/v1/api-endpoints")
@Tag(name = "API Endpoint Management", description = "API端点管理")
@RequiredArgsConstructor
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;

    /**
     * 获取所有API端点
     */
    @Operation(summary = "获取API端点列表")
    @GetMapping
    public ResponseEntity<List<ApiEndpointResponse>> getAllEndpoints() {
        List<ApiEndpoint> endpoints = apiEndpointService.getAllEndpoints();
        List<ApiEndpointResponse> response = endpoints.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * 根据ID获取API端点
     */
    @Operation(summary = "获取API端点详情")
    @GetMapping("/{id}")
    public ResponseEntity<ApiEndpointResponse> getEndpointById(@PathVariable String id) {
        return apiEndpointService.getEndpointById(id)
                .map(endpoint -> ResponseEntity.ok(toResponse(endpoint)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建API端点
     */
    @Operation(summary = "创建API端点")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createEndpoint(
            @RequestBody CreateEndpointRequest request) {
        try {
            ApiEndpoint endpoint = ApiEndpoint.builder()
                    .path(request.getPath())
                    .method(request.getMethod())
                    .name(request.getName())
                    .description(request.getDescription())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .requireAuth(request.getRequireAuth() != null ? request.getRequireAuth() : true)
                    .permissionRequired(request.getPermissionRequired())
                    .rateLimitPerMinute(request.getRateLimitPerMinute() != null ? request.getRateLimitPerMinute() : 100)
                    .rateLimitPerHour(request.getRateLimitPerHour() != null ? request.getRateLimitPerHour() : 5000)
                    .rateLimitPerDay(request.getRateLimitPerDay() != null ? request.getRateLimitPerDay() : 100000)
                    .build();

            ApiEndpoint created = apiEndpointService.createEndpoint(endpoint);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "API端点创建成功");
            response.put("endpoint", toResponse(created));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 更新API端点
     */
    @Operation(summary = "更新API端点")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateEndpoint(
            @PathVariable String id,
            @RequestBody UpdateEndpointRequest request) {
        try {
            ApiEndpoint endpoint = ApiEndpoint.builder()
                    .path(request.getPath())
                    .method(request.getMethod())
                    .name(request.getName())
                    .description(request.getDescription())
                    .enabled(request.getEnabled())
                    .requireAuth(request.getRequireAuth())
                    .permissionRequired(request.getPermissionRequired())
                    .rateLimitPerMinute(request.getRateLimitPerMinute())
                    .rateLimitPerHour(request.getRateLimitPerHour())
                    .rateLimitPerDay(request.getRateLimitPerDay())
                    .build();

            ApiEndpoint updated = apiEndpointService.updateEndpoint(id, endpoint);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "API端点更新成功");
            response.put("endpoint", toResponse(updated));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 删除API端点
     */
    @Operation(summary = "删除API端点")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteEndpoint(@PathVariable String id) {
        try {
            apiEndpointService.deleteEndpoint(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "API端点删除成功");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 启用/禁用API端点
     */
    @Operation(summary = "启用/禁用API端点")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleEndpoint(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        try {
            ApiEndpoint endpoint = apiEndpointService.toggleEndpoint(id, enabled);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", enabled ? "API端点已启用" : "API端点已禁用");
            response.put("endpoint", toResponse(endpoint));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取API统计信息
     */
    @Operation(summary = "获取API统计信息")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = apiEndpointService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 初始化默认API端点
     */
    @Operation(summary = "初始化默认API端点")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeDefaultEndpoints() {
        apiEndpointService.initializeDefaultEndpoints();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "默认API端点初始化成功");

        return ResponseEntity.ok(response);
    }

    /**
     * 转换为响应DTO
     */
    private ApiEndpointResponse toResponse(ApiEndpoint endpoint) {
        return ApiEndpointResponse.builder()
                .id(endpoint.getId())
                .path(endpoint.getPath())
                .method(endpoint.getMethod())
                .name(endpoint.getName())
                .description(endpoint.getDescription())
                .enabled(endpoint.getEnabled())
                .requireAuth(endpoint.getRequireAuth())
                .permissionRequired(endpoint.getPermissionRequired())
                .rateLimitPerMinute(endpoint.getRateLimitPerMinute())
                .rateLimitPerHour(endpoint.getRateLimitPerHour())
                .rateLimitPerDay(endpoint.getRateLimitPerDay())
                .totalCalls(endpoint.getTotalCalls())
                .successCalls(endpoint.getSuccessCalls())
                .failedCalls(endpoint.getFailedCalls())
                .successRate(endpoint.getSuccessRate())
                .avgResponseTimeMs(endpoint.getAvgResponseTimeMs())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();
    }

    /**
     * 创建API端点请求
     */
    @Data
    public static class CreateEndpointRequest {
        private String path;
        private String method;
        private String name;
        private String description;
        private Boolean enabled;
        private Boolean requireAuth;
        private String permissionRequired;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerHour;
        private Integer rateLimitPerDay;
    }

    /**
     * 更新API端点请求
     */
    @Data
    public static class UpdateEndpointRequest {
        private String path;
        private String method;
        private String name;
        private String description;
        private Boolean enabled;
        private Boolean requireAuth;
        private String permissionRequired;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerHour;
        private Integer rateLimitPerDay;
    }

    /**
     * API端点响应
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class ApiEndpointResponse {
        private String id;
        private String path;
        private String method;
        private String name;
        private String description;
        private Boolean enabled;
        private Boolean requireAuth;
        private String permissionRequired;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerHour;
        private Integer rateLimitPerDay;
        private Long totalCalls;
        private Long successCalls;
        private Long failedCalls;
        private Double successRate;
        private Integer avgResponseTimeMs;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
