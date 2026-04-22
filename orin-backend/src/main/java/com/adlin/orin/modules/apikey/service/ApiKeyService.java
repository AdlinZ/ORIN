package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        return gatewaySecretService.findBySecretId(keyId).map(this::toLegacyApiKey);
    }

    /**
     * 获取密钥明文（管理员受控回显）
     */
    public Optional<String> getSecretKeyForAdmin(String keyId) {
        return gatewaySecretService.revealSecret(keyId);
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
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (keyOpt.isPresent() && userId.equals(keyOpt.get().getUserId())) {
            return gatewaySecretService.updateStatus(keyId, GatewaySecret.SecretStatus.DISABLED, userId);
        }
        return false;
    }

    /**
     * 启用密钥
     */
    @Transactional
    public boolean enableApiKey(String keyId, String userId) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (keyOpt.isPresent() && userId.equals(keyOpt.get().getUserId())) {
            return gatewaySecretService.updateStatus(keyId, GatewaySecret.SecretStatus.ACTIVE, userId);
        }
        return false;
    }

    /**
     * 删除密钥
     */
    @Transactional
    public boolean deleteApiKey(String keyId, String userId) {
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (keyOpt.isPresent() && userId.equals(keyOpt.get().getUserId())) {
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
        Optional<GatewaySecret> keyOpt = gatewaySecretService.findBySecretId(keyId);
        if (keyOpt.isEmpty() || !userId.equals(keyOpt.get().getUserId())) {
            return Optional.empty();
        }

        return gatewaySecretService.rotateClientAccessSecret(keyId, userId)
                .map(rotated -> new ApiKeyWithSecret(toLegacyApiKey(rotated.getSecret()), rotated.getSecretValue()));
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
}
