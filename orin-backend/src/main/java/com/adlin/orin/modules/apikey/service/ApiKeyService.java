package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * API密钥管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String KEY_PREFIX = "sk-orin-";
    private static final int KEY_LENGTH = 32;

    /**
     * 生成新的API密钥
     */
    @Transactional
    public ApiKeyWithSecret createApiKey(String userId, String name, String description,
            Integer rateLimitPerMinute, Integer rateLimitPerDay,
            Long monthlyTokenQuota, LocalDateTime expiresAt) {
        // 生成密钥
        String secretKey = generateSecretKey();
        String keyHash = passwordEncoder.encode(secretKey);
        String keyPrefix = KEY_PREFIX + secretKey.substring(0, 8);

        // 创建密钥实体
        ApiKey apiKey = ApiKey.builder()
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .userId(userId)
                .name(name)
                .description(description)
                .enabled(true)
                .rateLimitPerMinute(rateLimitPerMinute != null ? rateLimitPerMinute : 100)
                .rateLimitPerDay(rateLimitPerDay != null ? rateLimitPerDay : 10000)
                .monthlyTokenQuota(monthlyTokenQuota != null ? monthlyTokenQuota : 1000000L)
                .usedTokens(0L)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        apiKey = apiKeyRepository.save(apiKey);
        log.info("Created API key for user: {}, keyId: {}", userId, apiKey.getId());

        // 返回包含明文密钥的对象（仅此一次）
        return new ApiKeyWithSecret(apiKey, KEY_PREFIX + secretKey);
    }

    /**
     * 验证API密钥
     */
    public Optional<ApiKey> validateApiKey(String apiKeyString) {
        if (apiKeyString == null || !apiKeyString.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }

        try {
            // 查找所有密钥并验证
            List<ApiKey> allKeys = apiKeyRepository.findAll();
            for (ApiKey key : allKeys) {
                if (passwordEncoder.matches(apiKeyString.substring(KEY_PREFIX.length()),
                        key.getKeyHash().substring(KEY_PREFIX.length()))) {
                    // 检查密钥是否有效
                    if (!key.isValid()) {
                        log.warn("API key is invalid: {}", key.getId());
                        return Optional.empty();
                    }

                    // 检查配额
                    if (key.isQuotaExceeded()) {
                        log.warn("API key quota exceeded: {}", key.getId());
                        return Optional.empty();
                    }

                    // 更新最后使用时间
                    key.setLastUsedAt(LocalDateTime.now());
                    apiKeyRepository.save(key);

                    return Optional.of(key);
                }
            }
        } catch (Exception e) {
            log.error("Error validating API key: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 获取用户的所有密钥
     */
    public List<ApiKey> getUserApiKeys(String userId) {
        return apiKeyRepository.findByUserId(userId);
    }

    /**
     * 获取用户的所有启用密钥
     */
    public List<ApiKey> getUserActiveApiKeys(String userId) {
        return apiKeyRepository.findByUserIdAndEnabledTrue(userId);
    }

    /**
     * 禁用密钥
     */
    @Transactional
    public boolean disableApiKey(String keyId, String userId) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent() && keyOpt.get().getUserId().equals(userId)) {
            ApiKey key = keyOpt.get();
            key.setEnabled(false);
            apiKeyRepository.save(key);
            log.info("Disabled API key: {}", keyId);
            return true;
        }
        return false;
    }

    /**
     * 启用密钥
     */
    @Transactional
    public boolean enableApiKey(String keyId, String userId) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent() && keyOpt.get().getUserId().equals(userId)) {
            ApiKey key = keyOpt.get();
            key.setEnabled(true);
            apiKeyRepository.save(key);
            log.info("Enabled API key: {}", keyId);
            return true;
        }
        return false;
    }

    /**
     * 删除密钥
     */
    @Transactional
    public boolean deleteApiKey(String keyId, String userId) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent() && keyOpt.get().getUserId().equals(userId)) {
            apiKeyRepository.deleteById(keyId);
            log.info("Deleted API key: {}", keyId);
            return true;
        }
        return false;
    }

    /**
     * 更新Token使用量
     */
    @Transactional
    public void updateTokenUsage(String keyId, long tokensUsed) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            key.setUsedTokens(key.getUsedTokens() + tokensUsed);
            apiKeyRepository.save(key);
        }
    }

    /**
     * 重置月度配额
     */
    @Transactional
    public void resetMonthlyQuota(String keyId) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            key.setUsedTokens(0L);
            apiKeyRepository.save(key);
            log.info("Reset monthly quota for API key: {}", keyId);
        }
    }

    /**
     * 生成随机密钥
     */
    private String generateSecretKey() {
        byte[] randomBytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
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
