package com.adlin.orin.modules.apikey.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一网关密钥中心实体。
 */
@Entity
@Table(name = "gateway_secrets", indexes = {
        @Index(name = "idx_gateway_secret_type_provider_status", columnList = "secretType,provider,status"),
        @Index(name = "idx_gateway_secret_user", columnList = "userId"),
        @Index(name = "idx_gateway_secret_key_hash", columnList = "keyHash")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewaySecret {

    public enum SecretType {
        CLIENT_ACCESS,
        PROVIDER_CREDENTIAL,
        MCP_ENV
    }

    public enum SecretStatus {
        ACTIVE,
        DISABLED,
        DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "secret_id", nullable = false, unique = true, length = 100)
    private String secretId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "secret_type", nullable = false, length = 40)
    private SecretType secretType;

    @Column(length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SecretStatus status = SecretStatus.ACTIVE;

    @Column(name = "key_hash", length = 256)
    private String keyHash;

    @Column(name = "key_prefix", length = 40)
    private String keyPrefix;

    @Column(name = "encrypted_secret", nullable = false, length = 2048)
    private String encryptedSecret;

    @Column(name = "last4", length = 10)
    private String last4;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "user_id", length = 120)
    private String userId;

    @Column(length = 500)
    private String description;

    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay;

    @Column(name = "monthly_token_quota")
    private Long monthlyTokenQuota;

    @Column(name = "used_tokens")
    private Long usedTokens;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "rotation_at")
    private LocalDateTime rotationAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SecretStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isClientAccess() {
        return secretType == SecretType.CLIENT_ACCESS;
    }

    public boolean isProviderCredential() {
        return secretType == SecretType.PROVIDER_CREDENTIAL;
    }

    public boolean isMcpEnv() {
        return secretType == SecretType.MCP_ENV;
    }

    public boolean isActive() {
        return status == SecretStatus.ACTIVE;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isQuotaExceeded() {
        return monthlyTokenQuota != null && usedTokens != null && usedTokens >= monthlyTokenQuota;
    }
}
