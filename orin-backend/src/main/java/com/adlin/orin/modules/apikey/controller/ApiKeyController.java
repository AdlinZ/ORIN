package com.adlin.orin.modules.apikey.controller;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final AuditHelper auditHelper;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

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

        auditHelper.log("SYSTEM", "API_KEY_CREATE", "/api/v1/api-keys",
                "创建API密钥: " + request.getName() + ", 用户: " + userId, true, null);

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

        // 获取当前认证用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        List<ApiKey> keys;
        if (isAdmin) {
            // 管理员返回所有密钥
            keys = apiKeyService.getAllApiKeys();
        } else {
            // 普通用户只返回自己的密钥
            keys = apiKeyService.getUserApiKeys(userId);
        }

        List<ApiKeyResponse> response = keys.stream()
                .map(this::toApiKeyResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 管理员查看API密钥明文（受控回显）
     */
    @Operation(summary = "管理员查看API密钥明文")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{keyId}/secret")
    public ResponseEntity<Map<String, Object>> getApiKeySecret(
            @PathVariable String keyId,
            @RequestBody RevealSecretRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long currentUserId;
        try {
            currentUserId = Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SysUser currentUser = sysUserRepository.findById(currentUserId).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request == null || request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            auditHelper.log(String.valueOf(currentUserId), "API_KEY_REVEAL", "/api/v1/api-keys/" + keyId + "/secret",
                    "管理员查看API密钥明文失败（密码校验失败）: " + keyId, false, "INVALID_PASSWORD");
            Map<String, Object> response = new HashMap<>();
            response.put("message", "当前密码错误");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        return apiKeyService.getSecretKeyForAdmin(keyId)
                .map(secret -> {
                    auditHelper.log(String.valueOf(currentUserId), "API_KEY_REVEAL", "/api/v1/api-keys/" + keyId + "/secret",
                            "管理员查看API密钥明文: " + keyId, true, null);

                    Map<String, Object> response = new HashMap<>();
                    response.put("keyId", keyId);
                    response.put("secretKey", secret);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    return ResponseEntity.notFound().build();
                });
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

        auditHelper.log("SYSTEM", "API_KEY_DELETE", "/api/v1/api-keys/" + keyId,
                "删除API密钥: " + keyId + ", 用户: " + userId, success, success ? null : "删除失败");

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

        auditHelper.log("SYSTEM", "API_KEY_RESET_QUOTA", "/api/v1/api-keys/" + keyId + "/reset-quota",
                "重置API密钥配额: " + keyId, true, null);

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
                .canRevealSecret(apiKey.getEncryptedSecret() != null && !apiKey.getEncryptedSecret().isBlank())
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
                .userId(apiKey.getUserId())
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

    @Data
    public static class RevealSecretRequest {
        private String currentPassword;
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
        private Boolean canRevealSecret;
        private String name;
        private String description;
        private String userId;
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
        ExternalProviderKey saved = providerKeyService.saveKey(key);
        auditHelper.log("SYSTEM", "EXTERNAL_KEY_SAVE", "/api/v1/api-keys/external",
                "保存外部供应商密钥: " + key.getProvider(), true, null);
        return saved;
    }

    @Operation(summary = "删除外部供应商密钥")
    @DeleteMapping("/external/{id}")
    public void deleteExternalKey(@PathVariable Long id) {
        providerKeyService.deleteKey(id);
        auditHelper.log("SYSTEM", "EXTERNAL_KEY_DELETE", "/api/v1/api-keys/external/" + id,
                "删除外部供应商密钥ID: " + id, true, null);
    }

    @Operation(summary = "切换外部密钥启用状态")
    @PatchMapping("/external/{id}/toggle")
    public ExternalProviderKey toggleExternalStatus(@PathVariable Long id) {
        ExternalProviderKey toggled = providerKeyService.toggleStatus(id);
        auditHelper.log("SYSTEM", "EXTERNAL_KEY_TOGGLE", "/api/v1/api-keys/external/" + id + "/toggle",
                "切换外部密钥启用状态: " + toggled.getProvider() + ", enabled=" + toggled.getEnabled(), true, null);
        return toggled;
    }
}
