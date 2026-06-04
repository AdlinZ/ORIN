package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API密钥管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final GatewaySecretService gatewaySecretService;
    private final UnifiedGatewayAuditLogRepository gatewayAuditLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 生成新的API密钥
     */
    @Transactional
    public ApiKeyWithSecret createApiKey(String userId, String name, String description,
            Integer rateLimitPerMinute, Integer rateLimitPerDay,
            Long monthlyTokenQuota, LocalDateTime expiresAt) {
        GatewaySecretService.ClientAccessSecretWithValue result = gatewaySecretService.createClientAccessSecret(
                userId,
                name,
                description,
                rateLimitPerMinute,
                rateLimitPerDay,
                monthlyTokenQuota,
                expiresAt,
                userId);
        ApiKey mapped = toLegacyApiKey(result.getSecret());
        log.info("Created API key for user: {}, keyId: {}", userId, mapped.getId());
        return new ApiKeyWithSecret(mapped, result.getSecretValue());
    }

    /**
     * 验证API密钥
     */
    public Optional<ApiKey> validateApiKey(String apiKeyString) {
        return gatewaySecretService.validateClientAccessSecret(apiKeyString).map(this::toLegacyApiKey);
    }

    /**
     * 获取用户的所有密钥
     */
    public List<ApiKey> getUserApiKeys(String userId) {
        return gatewaySecretService.listByType(GatewaySecret.SecretType.CLIENT_ACCESS).stream()
                .filter(secret -> userId.equals(secret.getUserId()))
                .map(this::toLegacyApiKey)
                .collect(Collectors.toList());
    }

    public List<ApiKey> getApiKeysForActor(String userId, boolean canManageAll) {
        return canManageAll ? getAllApiKeys() : getUserApiKeys(userId);
    }

    /**
     * 获取所有密钥（管理员用）
     */
    public List<ApiKey> getAllApiKeys() {
        return gatewaySecretService.listByType(GatewaySecret.SecretType.CLIENT_ACCESS).stream()
                .map(this::toLegacyApiKey)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取密钥
     */
    public Optional<ApiKey> getApiKeyById(String keyId) {
        return gatewaySecretService.findBySecretId(keyId)
                .filter(GatewaySecret::isClientAccess)
                .map(this::toLegacyApiKey);
    }

    /**
     * 获取密钥明文（管理员受控回显）
     */
    public Optional<String> getSecretKeyForAdmin(String keyId) {
        return gatewaySecretService.findBySecretId(keyId)
                .filter(GatewaySecret::isClientAccess)
                .flatMap(secret -> gatewaySecretService.revealSecret(keyId));
    }

    /**
     * 获取用户的所有启用密钥
     */
    public List<ApiKey> getUserActiveApiKeys(String userId) {
        return gatewaySecretService.listByType(GatewaySecret.SecretType.CLIENT_ACCESS).stream()
                .filter(secret -> userId.equals(secret.getUserId()))
                .filter(secret -> secret.getStatus() == GatewaySecret.SecretStatus.ACTIVE)
                .map(this::toLegacyApiKey)
                .collect(Collectors.toList());
    }

    /**
     * 禁用密钥
     */
    @Transactional
    public boolean disableApiKey(String keyId, String userId) {
        return disableApiKey(keyId, userId, false);
    }

    @Transactional
    public boolean disableApiKey(String keyId, String userId, boolean canManageAll) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (canManage(keyOpt, userId, canManageAll)) {
            return gatewaySecretService.updateStatus(keyId, GatewaySecret.SecretStatus.DISABLED, userId);
        }
        return false;
    }

    /**
     * 启用密钥
     */
    @Transactional
    public boolean enableApiKey(String keyId, String userId) {
        return enableApiKey(keyId, userId, false);
    }

    @Transactional
    public boolean enableApiKey(String keyId, String userId, boolean canManageAll) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (canManage(keyOpt, userId, canManageAll)) {
            return gatewaySecretService.updateStatus(keyId, GatewaySecret.SecretStatus.ACTIVE, userId);
        }
        return false;
    }

    /**
     * 删除密钥
     */
    @Transactional
    public boolean deleteApiKey(String keyId, String userId) {
        return deleteApiKey(keyId, userId, false);
    }

    @Transactional
    public boolean deleteApiKey(String keyId, String userId, boolean canManageAll) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (canManage(keyOpt, userId, canManageAll)) {
            return gatewaySecretService.deleteBySecretId(keyId, userId);
        }
        return false;
    }

    /**
     * 更新Token使用量
     */
    @Transactional
    public void updateTokenUsage(String keyId, long tokensUsed) {
        gatewaySecretService.updateTokenUsage(keyId, tokensUsed);
    }

    /**
     * 重置月度配额
     */
    @Transactional
    public void resetMonthlyQuota(String keyId) {
        gatewaySecretService.resetMonthlyQuota(keyId);
        log.info("Reset monthly quota for API key: {}", keyId);
    }

    public Optional<ApiKeyWithSecret> rotateApiKey(String keyId, String userId) {
        return rotateApiKey(keyId, userId, false);
    }

    public Optional<ApiKeyWithSecret> rotateApiKey(String keyId, String userId, boolean canManageAll) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (!canManage(keyOpt, userId, canManageAll)) {
            return Optional.empty();
        }

        return gatewaySecretService.rotateClientAccessSecret(keyId, userId)
                .map(rotated -> new ApiKeyWithSecret(toLegacyApiKey(rotated.getSecret()), rotated.getSecretValue()));
    }

    public Optional<ApiKeyUsageResponse> getApiKeyUsage(String keyId, String userId, int limit) {
        return getApiKeyUsage(keyId, userId, limit, false);
    }

    public Optional<ApiKeyUsageResponse> getApiKeyUsage(String keyId, String userId, int limit, boolean canManageAll) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (!canManage(keyOpt, userId, canManageAll)) {
            return Optional.empty();
        }

        GatewaySecret secret = keyOpt.get();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        long gatewayTotal = gatewayAuditLogRepository.countByApiKeyIdAndCreatedAtAfter(keyId, since);
        long gatewaySuccess = gatewayAuditLogRepository.countByApiKeyIdAndResultAndCreatedAtAfter(keyId, "SUCCESS", since);
        long auditTotal = auditLogRepository.countByApiKeyIdAndCreatedAtAfter(keyId, since);
        long auditSuccess = auditLogRepository.countByApiKeyIdAndSuccessAndCreatedAtAfter(keyId, true, since);
        long total = gatewayTotal + auditTotal;
        long success = gatewaySuccess + auditSuccess;
        long failure = Math.max(0, total - success);
        Long tokenUsage = auditLogRepository.sumTokensByApiKeyIdAndCreatedAtAfter(keyId, since);
        Double gatewayAvgLatency = gatewayAuditLogRepository.findAverageLatencyByApiKeySince(keyId, since);
        Double auditAvgLatency = auditLogRepository.avgResponseTimeByApiKeyIdAndCreatedAtAfter(keyId, since);

        List<ApiKeyUsageEvent> events = new ArrayList<>();
        PageRequest page = PageRequest.of(0, safeLimit);
        gatewayAuditLogRepository.findByApiKeyIdOrderByCreatedAtDesc(keyId, page).forEach(log ->
                events.add(ApiKeyUsageEvent.builder()
                        .source("GATEWAY")
                        .traceId(log.getTraceId())
                        .method(log.getMethod())
                        .path(log.getPath())
                        .statusCode(log.getStatusCode())
                        .success("SUCCESS".equalsIgnoreCase(log.getResult()))
                        .latencyMs(log.getLatencyMs())
                        .errorSummary(truncate(log.getErrorMessage(), 160))
                        .createdAt(log.getCreatedAt())
                        .build()));
        auditLogRepository.findByApiKeyIdOrderByCreatedAtDesc(keyId, page).forEach(log ->
                events.add(ApiKeyUsageEvent.builder()
                        .source("AUDIT")
                        .traceId(log.getTraceId())
                        .method(log.getMethod())
                        .path(log.getEndpoint())
                        .statusCode(log.getStatusCode())
                        .success(Boolean.TRUE.equals(log.getSuccess()))
                        .latencyMs(log.getResponseTime())
                        .tokens(log.getTotalTokens())
                        .model(log.getModel())
                        .errorSummary(truncate(log.getErrorMessage(), 160))
                        .createdAt(log.getCreatedAt())
                        .build()));

        List<ApiKeyUsageEvent> recentEvents = events.stream()
                .sorted(Comparator.comparing(ApiKeyUsageEvent::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .collect(Collectors.toList());

        return Optional.of(ApiKeyUsageResponse.builder()
                .keyId(keyId)
                .status(secret.getStatus() != null ? secret.getStatus().name() : null)
                .lastUsedAt(secret.getLastUsedAt())
                .usedTokens(secret.getUsedTokens() != null ? secret.getUsedTokens() : 0L)
                .monthlyTokenQuota(secret.getMonthlyTokenQuota())
                .windowDays(30)
                .totalCalls(total)
                .successCalls(success)
                .failedCalls(failure)
                .failureRate(total == 0 ? 0.0 : (failure * 100.0 / total))
                .tokensInWindow(tokenUsage != null ? tokenUsage : 0L)
                .averageLatencyMs(resolveAverage(gatewayAvgLatency, auditAvgLatency))
                .recentEvents(recentEvents)
                .build());
    }

    private boolean canManage(Optional<GatewaySecret> keyOpt, String userId, boolean canManageAll) {
        if (keyOpt.isEmpty() || !keyOpt.get().isClientAccess()) {
            return false;
        }
        return canManageAll || userId.equals(keyOpt.get().getUserId());
    }

    private ApiKey toLegacyApiKey(GatewaySecret secret) {
        return ApiKey.builder()
                .id(secret.getSecretId())
                .keyHash(secret.getKeyHash())
                .keyPrefix(secret.getKeyPrefix())
                .encryptedSecret(secret.getEncryptedSecret())
                .userId(secret.getUserId())
                .name(secret.getName())
                .description(secret.getDescription())
                .enabled(secret.getStatus() == GatewaySecret.SecretStatus.ACTIVE)
                .rateLimitPerMinute(secret.getRateLimitPerMinute())
                .rateLimitPerDay(secret.getRateLimitPerDay())
                .monthlyTokenQuota(secret.getMonthlyTokenQuota())
                .usedTokens(secret.getUsedTokens())
                .expiresAt(secret.getExpiresAt())
                .lastUsedAt(secret.getLastUsedAt())
                .createdAt(secret.getCreatedAt())
                .updatedAt(secret.getUpdatedAt())
                .build();
    }

    private Double resolveAverage(Double first, Double second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return (first + second) / 2.0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * API密钥和明文密钥的包装类
     */
    public static class ApiKeyWithSecret {
        private final ApiKey apiKey;
        private final String secretKey;

        public ApiKeyWithSecret(ApiKey apiKey, String secretKey) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;
        }

        public ApiKey getApiKey() {
            return apiKey;
        }

        public String getSecretKey() {
            return secretKey;
        }
    }

    @Data
    @Builder
    public static class ApiKeyUsageResponse {
        private String keyId;
        private String status;
        private LocalDateTime lastUsedAt;
        private Long usedTokens;
        private Long monthlyTokenQuota;
        private Integer windowDays;
        private Long totalCalls;
        private Long successCalls;
        private Long failedCalls;
        private Double failureRate;
        private Long tokensInWindow;
        private Double averageLatencyMs;
        private List<ApiKeyUsageEvent> recentEvents;
    }

    @Data
    @Builder
    public static class ApiKeyUsageEvent {
        private String source;
        private String traceId;
        private String method;
        private String path;
        private Integer statusCode;
        private Boolean success;
        private Long latencyMs;
        private Integer tokens;
        private String model;
        private String errorSummary;
        private LocalDateTime createdAt;
    }
}
