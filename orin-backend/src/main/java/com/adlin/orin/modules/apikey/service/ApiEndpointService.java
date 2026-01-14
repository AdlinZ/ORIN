package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ApiEndpoint;
import com.adlin.orin.modules.apikey.repository.ApiEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API端点管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;

    /**
     * 创建API端点
     */
    @Transactional
    public ApiEndpoint createEndpoint(ApiEndpoint endpoint) {
        // 检查是否已存在
        if (apiEndpointRepository.existsByPathAndMethod(endpoint.getPath(), endpoint.getMethod())) {
            throw new IllegalArgumentException("API endpoint already exists: " +
                    endpoint.getMethod() + " " + endpoint.getPath());
        }

        endpoint.setCreatedAt(LocalDateTime.now());
        endpoint.setUpdatedAt(LocalDateTime.now());

        ApiEndpoint saved = apiEndpointRepository.save(endpoint);
        log.info("Created API endpoint: {} {}", saved.getMethod(), saved.getPath());
        return saved;
    }

    /**
     * 更新API端点
     */
    @Transactional
    public ApiEndpoint updateEndpoint(String id, ApiEndpoint updatedEndpoint) {
        ApiEndpoint existing = apiEndpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API endpoint not found: " + id));

        // 如果路径或方法改变,检查是否与其他端点冲突
        if (!existing.getPath().equals(updatedEndpoint.getPath()) ||
                !existing.getMethod().equals(updatedEndpoint.getMethod())) {
            if (apiEndpointRepository.existsByPathAndMethod(
                    updatedEndpoint.getPath(), updatedEndpoint.getMethod())) {
                throw new IllegalArgumentException("API endpoint already exists: " +
                        updatedEndpoint.getMethod() + " " + updatedEndpoint.getPath());
            }
        }

        existing.setPath(updatedEndpoint.getPath());
        existing.setMethod(updatedEndpoint.getMethod());
        existing.setName(updatedEndpoint.getName());
        existing.setDescription(updatedEndpoint.getDescription());
        existing.setEnabled(updatedEndpoint.getEnabled());
        existing.setRequireAuth(updatedEndpoint.getRequireAuth());
        existing.setPermissionRequired(updatedEndpoint.getPermissionRequired());
        existing.setRateLimitPerMinute(updatedEndpoint.getRateLimitPerMinute());
        existing.setRateLimitPerHour(updatedEndpoint.getRateLimitPerHour());
        existing.setRateLimitPerDay(updatedEndpoint.getRateLimitPerDay());
        existing.setUpdatedAt(LocalDateTime.now());

        ApiEndpoint saved = apiEndpointRepository.save(existing);
        log.info("Updated API endpoint: {} {}", saved.getMethod(), saved.getPath());
        return saved;
    }

    /**
     * 删除API端点
     */
    @Transactional
    public void deleteEndpoint(String id) {
        if (!apiEndpointRepository.existsById(id)) {
            throw new IllegalArgumentException("API endpoint not found: " + id);
        }
        apiEndpointRepository.deleteById(id);
        log.info("Deleted API endpoint: {}", id);
    }

    /**
     * 获取所有API端点
     */
    public List<ApiEndpoint> getAllEndpoints() {
        return apiEndpointRepository.findAll();
    }

    /**
     * 获取所有启用的API端点
     */
    public List<ApiEndpoint> getEnabledEndpoints() {
        return apiEndpointRepository.findByEnabledTrue();
    }

    /**
     * 根据ID获取API端点
     */
    public Optional<ApiEndpoint> getEndpointById(String id) {
        return apiEndpointRepository.findById(id);
    }

    /**
     * 根据路径和方法获取API端点
     */
    public Optional<ApiEndpoint> getEndpointByPathAndMethod(String path, String method) {
        return apiEndpointRepository.findByPathAndMethod(path, method);
    }

    /**
     * 启用/禁用API端点
     */
    @Transactional
    public ApiEndpoint toggleEndpoint(String id, boolean enabled) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API endpoint not found: " + id));

        endpoint.setEnabled(enabled);
        endpoint.setUpdatedAt(LocalDateTime.now());

        ApiEndpoint saved = apiEndpointRepository.save(endpoint);
        log.info("{} API endpoint: {} {}", enabled ? "Enabled" : "Disabled",
                saved.getMethod(), saved.getPath());
        return saved;
    }

    /**
     * 更新调用统计
     */
    @Transactional
    public void updateCallStatistics(String path, String method, boolean success, long responseTimeMs) {
        Optional<ApiEndpoint> endpointOpt = apiEndpointRepository.findByPathAndMethod(path, method);
        if (endpointOpt.isPresent()) {
            ApiEndpoint endpoint = endpointOpt.get();
            endpoint.updateCallStats(success, responseTimeMs);
            apiEndpointRepository.save(endpoint);
        }
    }

    /**
     * 获取API统计信息
     */
    public Map<String, Object> getStatistics() {
        List<ApiEndpoint> allEndpoints = apiEndpointRepository.findAll();

        long totalCalls = allEndpoints.stream().mapToLong(ApiEndpoint::getTotalCalls).sum();
        long successCalls = allEndpoints.stream().mapToLong(ApiEndpoint::getSuccessCalls).sum();
        long failedCalls = allEndpoints.stream().mapToLong(ApiEndpoint::getFailedCalls).sum();
        long enabledCount = allEndpoints.stream().filter(ApiEndpoint::getEnabled).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEndpoints", allEndpoints.size());
        stats.put("enabledEndpoints", enabledCount);
        stats.put("totalCalls", totalCalls);
        stats.put("successCalls", successCalls);
        stats.put("failedCalls", failedCalls);
        stats.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0.0);

        return stats;
    }

    /**
     * 初始化默认API端点
     */
    @Transactional
    public void initializeDefaultEndpoints() {
        // 聊天完成接口
        createEndpointIfNotExists("/v1/chat/completions", "POST", "聊天完成",
                "OpenAI兼容的聊天完成接口", "chat");

        // 文本嵌入接口
        createEndpointIfNotExists("/v1/embeddings", "POST", "文本嵌入",
                "OpenAI兼容的文本嵌入接口", "embedding");

        // 模型列表接口
        createEndpointIfNotExists("/v1/models", "GET", "模型列表",
                "获取所有可用模型列表", "models");

        // 健康检查接口
        createEndpointIfNotExists("/v1/health", "GET", "健康检查",
                "检查所有Provider的健康状态", null);

        // Provider信息接口
        createEndpointIfNotExists("/v1/providers", "GET", "Provider列表",
                "获取所有Provider的详细信息", null);

        log.info("Default API endpoints initialized");
    }

    /**
     * 如果不存在则创建端点
     */
    private void createEndpointIfNotExists(String path, String method, String name,
            String description, String permission) {
        if (!apiEndpointRepository.existsByPathAndMethod(path, method)) {
            ApiEndpoint endpoint = ApiEndpoint.builder()
                    .path(path)
                    .method(method)
                    .name(name)
                    .description(description)
                    .enabled(true)
                    .requireAuth(permission != null)
                    .permissionRequired(permission)
                    .rateLimitPerMinute(100)
                    .rateLimitPerHour(5000)
                    .rateLimitPerDay(100000)
                    .totalCalls(0L)
                    .successCalls(0L)
                    .failedCalls(0L)
                    .avgResponseTimeMs(0)
                    .build();
            apiEndpointRepository.save(endpoint);
        }
    }
}
