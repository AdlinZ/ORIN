package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.security.EncryptionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 统一网关密钥中心服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewaySecretService {

    private static final String CLIENT_KEY_PREFIX = "sk-orin-";
    private static final int KEY_LENGTH = 32;

    private final GatewaySecretRepository gatewaySecretRepository;
    private final EncryptionUtil encryptionUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ClientAccessSecretWithValue createClientAccessSecret(String userId,
                                                                String name,
                                                                String description,
                                                                Integer rateLimitPerMinute,
                                                                Integer rateLimitPerDay,
                                                                Long monthlyTokenQuota,
                                                                LocalDateTime expiresAt,
                                                                String operator) {
        String secretValue = CLIENT_KEY_PREFIX + generateSecretKey();
        String rawSecret = secretValue.substring(CLIENT_KEY_PREFIX.length());

        GatewaySecret secret = GatewaySecret.builder()
                .secretId("gsec_" + UUID.randomUUID().toString().replace("-", ""))
                .name(name)
                .secretType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .status(GatewaySecret.SecretStatus.ACTIVE)
                .keyHash(passwordEncoder.encode(rawSecret))
                .keyPrefix(secretValue.substring(0, Math.min(16, secretValue.length())))
                .encryptedSecret(encryptionUtil.encrypt(secretValue))
                .last4(secretValue.substring(Math.max(0, secretValue.length() - 4)))
                .userId(userId)
                .description(description)
                .rateLimitPerMinute(rateLimitPerMinute != null ? rateLimitPerMinute : 100)
                .rateLimitPerDay(rateLimitPerDay != null ? rateLimitPerDay : 10000)
                .monthlyTokenQuota(monthlyTokenQuota != null ? monthlyTokenQuota : 1_000_000L)
                .usedTokens(0L)
                .expiresAt(expiresAt)
                .rotationAt(LocalDateTime.now())
                .createdBy(operator)
                .updatedBy(operator)
                .build();

        secret = gatewaySecretRepository.save(secret);
        return new ClientAccessSecretWithValue(secret, secretValue);
    }

    public Optional<GatewaySecret> validateClientAccessSecret(String apiKeyString) {
        if (apiKeyString == null || !apiKeyString.startsWith(CLIENT_KEY_PREFIX)) {
            return Optional.empty();
        }

        String rawSecret = apiKeyString.substring(CLIENT_KEY_PREFIX.length());
        List<GatewaySecret> candidates = gatewaySecretRepository.findBySecretTypeAndStatus(
                GatewaySecret.SecretType.CLIENT_ACCESS,
                GatewaySecret.SecretStatus.ACTIVE);

        for (GatewaySecret candidate : candidates) {
            if (candidate.getKeyHash() == null || candidate.getKeyHash().isBlank()) {
                continue;
            }
            if (!matchesHash(rawSecret, candidate.getKeyHash())) {
                continue;
            }
            if (candidate.isExpired() || candidate.isQuotaExceeded()) {
                return Optional.empty();
            }

            candidate.setLastUsedAt(LocalDateTime.now());
            gatewaySecretRepository.save(candidate);
            return Optional.of(candidate);
        }

        return Optional.empty();
    }

    @Transactional
    public GatewaySecret upsertProviderCredential(String provider,
                                                  String name,
                                                  String secretValue,
                                                  String baseUrl,
                                                  String description,
                                                  boolean active,
                                                  String operator) {
        String normalizedProvider = normalizeProvider(provider);
        Optional<GatewaySecret> existingOpt = gatewaySecretRepository
                .findBySecretTypeOrderByUpdatedAtDesc(GatewaySecret.SecretType.PROVIDER_CREDENTIAL)
                .stream()
                .filter(secret -> normalizedProvider.equals(normalizeProvider(secret.getProvider())))
                .filter(secret -> secret.getStatus() != GatewaySecret.SecretStatus.DELETED)
                .findFirst();

        GatewaySecret secret = existingOpt.orElseGet(() -> GatewaySecret.builder()
                .secretId("gsec_provider_" + System.currentTimeMillis())
                .secretType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL)
                .provider(normalizedProvider)
                .createdBy(operator)
                .build());

        secret.setName(name != null && !name.isBlank() ? name : (normalizedProvider + " credential"));
        if (secretValue != null && !secretValue.isBlank()) {
            secret.setEncryptedSecret(encryptionUtil.encrypt(secretValue));
            secret.setLast4(secretValue.substring(Math.max(0, secretValue.length() - 4)));
            secret.setRotationAt(LocalDateTime.now());
        }
        secret.setBaseUrl(baseUrl);
        secret.setDescription(description);
        secret.setStatus(active ? GatewaySecret.SecretStatus.ACTIVE : GatewaySecret.SecretStatus.DISABLED);
        secret.setUpdatedBy(operator);

        return gatewaySecretRepository.save(secret);
    }

    public Optional<ResolvedProviderCredential> resolveProviderCredential(String provider) {
        String normalizedProvider = normalizeProvider(provider);
        return gatewaySecretRepository
                .findBySecretTypeAndProviderIgnoreCaseAndStatusOrderByUpdatedAtDesc(
                        GatewaySecret.SecretType.PROVIDER_CREDENTIAL,
                        normalizedProvider,
                        GatewaySecret.SecretStatus.ACTIVE)
                .stream()
                .findFirst()
                .filter(secret -> secret.getEncryptedSecret() != null && !secret.getEncryptedSecret().isBlank())
                .map(secret -> ResolvedProviderCredential.builder()
                        .secretId(secret.getSecretId())
                        .provider(normalizedProvider)
                        .apiKey(encryptionUtil.decrypt(secret.getEncryptedSecret()))
                        .baseUrl(secret.getBaseUrl())
                        .build());
    }

    public List<GatewaySecret> listByType(GatewaySecret.SecretType secretType) {
        return gatewaySecretRepository.findBySecretTypeOrderByUpdatedAtDesc(secretType);
    }

    public List<GatewaySecret> listAll() {
        return gatewaySecretRepository.findAll();
    }

    public Optional<GatewaySecret> findBySecretId(String secretId) {
        return gatewaySecretRepository.findBySecretId(secretId);
    }

    @Transactional
    public boolean updateStatus(String secretId, GatewaySecret.SecretStatus status, String operator) {
        Optional<GatewaySecret> secretOpt = gatewaySecretRepository.findBySecretId(secretId);
        if (secretOpt.isEmpty()) {
            return false;
        }

        GatewaySecret secret = secretOpt.get();
        secret.setStatus(status);
        secret.setUpdatedBy(operator);
        gatewaySecretRepository.save(secret);
        return true;
    }

    @Transactional
    public boolean deleteBySecretId(String secretId, String operator) {
        Optional<GatewaySecret> secretOpt = gatewaySecretRepository.findBySecretId(secretId);
        if (secretOpt.isEmpty()) {
            return false;
        }

        GatewaySecret secret = secretOpt.get();
        secret.setStatus(GatewaySecret.SecretStatus.DELETED);
        secret.setUpdatedBy(operator);
        gatewaySecretRepository.save(secret);
        return true;
    }

    @Transactional
    public Optional<String> revealSecret(String secretId) {
        return gatewaySecretRepository.findBySecretId(secretId)
                .map(GatewaySecret::getEncryptedSecret)
                .filter(v -> v != null && !v.isBlank())
                .map(encryptionUtil::decrypt);
    }

    @Transactional
    public Optional<String> rotateProviderCredential(String secretId, String newPlainSecret, String operator) {
        Optional<GatewaySecret> secretOpt = gatewaySecretRepository.findBySecretId(secretId);
        if (secretOpt.isEmpty() || newPlainSecret == null || newPlainSecret.isBlank()) {
            return Optional.empty();
        }

        GatewaySecret secret = secretOpt.get();
        secret.setEncryptedSecret(encryptionUtil.encrypt(newPlainSecret));
        secret.setLast4(newPlainSecret.substring(Math.max(0, newPlainSecret.length() - 4)));
        secret.setRotationAt(LocalDateTime.now());
        secret.setUpdatedBy(operator);
        gatewaySecretRepository.save(secret);
        return Optional.of(secret.getSecretId());
    }

    @Transactional
    public Optional<ClientAccessSecretWithValue> rotateClientAccessSecret(String secretId, String operator) {
        Optional<GatewaySecret> secretOpt = gatewaySecretRepository.findBySecretId(secretId);
        if (secretOpt.isEmpty()) {
            return Optional.empty();
        }

        GatewaySecret secret = secretOpt.get();
        if (!secret.isClientAccess()) {
            return Optional.empty();
        }

        String secretValue = CLIENT_KEY_PREFIX + generateSecretKey();
        String rawSecret = secretValue.substring(CLIENT_KEY_PREFIX.length());

        secret.setKeyHash(passwordEncoder.encode(rawSecret));
        secret.setKeyPrefix(secretValue.substring(0, Math.min(16, secretValue.length())));
        secret.setEncryptedSecret(encryptionUtil.encrypt(secretValue));
        secret.setLast4(secretValue.substring(Math.max(0, secretValue.length() - 4)));
        secret.setRotationAt(LocalDateTime.now());
        secret.setUpdatedBy(operator);
        gatewaySecretRepository.save(secret);

        return Optional.of(new ClientAccessSecretWithValue(secret, secretValue));
    }

    @Transactional
    public void updateTokenUsage(String secretId, long tokensUsed) {
        gatewaySecretRepository.findBySecretId(secretId).ifPresent(secret -> {
            Long used = secret.getUsedTokens() != null ? secret.getUsedTokens() : 0L;
            secret.setUsedTokens(used + tokensUsed);
            gatewaySecretRepository.save(secret);
        });
    }

    @Transactional
    public void resetMonthlyQuota(String secretId) {
        gatewaySecretRepository.findBySecretId(secretId).ifPresent(secret -> {
            secret.setUsedTokens(0L);
            gatewaySecretRepository.save(secret);
        });
    }

    private String generateSecretKey() {
        byte[] randomBytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private boolean matchesHash(String rawSecret, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }

        if (passwordEncoder.matches(rawSecret, storedHash)) {
            return true;
        }

        // 兼容历史错误逻辑
        if (storedHash.length() > CLIENT_KEY_PREFIX.length()) {
            String legacyTrimmedHash = storedHash.substring(CLIENT_KEY_PREFIX.length());
            return passwordEncoder.matches(rawSecret, legacyTrimmedHash);
        }

        return false;
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ResolvedProviderCredential {
        private String secretId;
        private String provider;
        private String apiKey;
        private String baseUrl;
    }

    @Data
    @AllArgsConstructor
    public static class ClientAccessSecretWithValue {
        private GatewaySecret secret;
        private String secretValue;
    }
}
