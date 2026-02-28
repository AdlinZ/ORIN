package com.adlin.orin.modules.apikey.controller;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
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
 * API密钥管理控制器
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Key Management", description = "API密钥管理")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ProviderKeyService providerKeyService;

    /**
     * 创建API密钥
     */
    @Operation(summary = "创建API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody CreateApiKeyRequest request,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {

        ApiKeyService.ApiKeyWithSecret result = apiKeyService.createApiKey(
                userId,
                request.getName(),
                request.getDescription(),
                request.getRateLimitPerMinute(),
                request.getRateLimitPerDay(),
                request.getMonthlyTokenQuota(),
                request.getExpiresAt());

        Map<String, Object> response = new HashMap<>();
        response.put("apiKey", toApiKeyResponse(result.getApiKey()));
        response.put("secretKey", result.getSecretKey());
        response.put("warning", "请妥善保存此密钥，它只会显示一次！");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户的所有API密钥
     */
    @Operation(summary = "获取API密钥列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> getApiKeys(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {

        List<ApiKey> keys = apiKeyService.getUserApiKeys(userId);
        List<ApiKeyResponse> response = keys.stream()
                .map(this::toApiKeyResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 禁用API密钥
     */
    @Operation(summary = "禁用API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{keyId}/disable")
    public ResponseEntity<Map<String, Object>> disableApiKey(
            @PathVariable String keyId,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {

        boolean success = apiKeyService.disableApiKey(keyId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "API密钥已禁用" : "操作失败");

        return ResponseEntity.ok(response);
    }

    /**
     * 启用API密钥
     */
    @Operation(summary = "启用API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{keyId}/enable")
    public ResponseEntity<Map<String, Object>> enableApiKey(
            @PathVariable String keyId,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {

        boolean success = apiKeyService.enableApiKey(keyId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "API密钥已启用" : "操作失败");

        return ResponseEntity.ok(response);
    }

    /**
     * 删除API密钥
     */
    @Operation(summary = "删除API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Map<String, Object>> deleteApiKey(
            @PathVariable String keyId,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {

        boolean success = apiKeyService.deleteApiKey(keyId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "API密钥已删除" : "操作失败");

        return ResponseEntity.ok(response);
    }

    /**
     * 重置月度配额
     */
    @Operation(summary = "重置月度配额")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{keyId}/reset-quota")
    public ResponseEntity<Map<String, Object>> resetQuota(
            @PathVariable String keyId) {

        apiKeyService.resetMonthlyQuota(keyId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "配额已重置");

        return ResponseEntity.ok(response);
    }

    /**
     * 转换为响应DTO
     */
    private ApiKeyResponse toApiKeyResponse(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .keyPrefix(apiKey.getKeyPrefix())
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .enabled(apiKey.getEnabled())
                .rateLimitPerMinute(apiKey.getRateLimitPerMinute())
                .rateLimitPerDay(apiKey.getRateLimitPerDay())
                .monthlyTokenQuota(apiKey.getMonthlyTokenQuota())
                .usedTokens(apiKey.getUsedTokens())
                .quotaPercentage((double) apiKey.getUsedTokens() / apiKey.getMonthlyTokenQuota() * 100)
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    /**
     * 创建API密钥请求
     */
    @Data
    public static class CreateApiKeyRequest {
        private String name;
        private String description;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerDay;
        private Long monthlyTokenQuota;
        private LocalDateTime expiresAt;
    }

    /**
     * API密钥响应
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class ApiKeyResponse {
        private String id;
        private String keyPrefix;
        private String name;
        private String description;
        private Boolean enabled;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerDay;
        private Long monthlyTokenQuota;
        private Long usedTokens;
        private Double quotaPercentage;
        private LocalDateTime expiresAt;
        private LocalDateTime lastUsedAt;
        private LocalDateTime createdAt;
    }

    // --- External Provider Keys ---

    @Operation(summary = "获取外部供应商密钥列表")
    @GetMapping("/external")
    public List<ExternalProviderKey> listExternalKeys() {
        return providerKeyService.getAllKeys();
    }

    @Operation(summary = "新建/编辑外部供应商密钥")
    @PostMapping("/external")
    public ExternalProviderKey saveExternalKey(@RequestBody ExternalProviderKey key) {
        return providerKeyService.saveKey(key);
    }

    @Operation(summary = "删除外部供应商密钥")
    @DeleteMapping("/external/{id}")
    public void deleteExternalKey(@PathVariable Long id) {
        providerKeyService.deleteKey(id);
    }

    @Operation(summary = "切换外部密钥启用状态")
    @PatchMapping("/external/{id}/toggle")
    public ExternalProviderKey toggleExternalStatus(@PathVariable Long id) {
        return providerKeyService.toggleStatus(id);
    }
}
