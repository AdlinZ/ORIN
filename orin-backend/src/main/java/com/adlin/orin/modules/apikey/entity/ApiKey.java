package com.adlin.orin.modules.apikey.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API密钥实体
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_key_hash", columnList = "keyHash"),
        @Index(name = "idx_user_id", columnList = "userId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 密钥哈希（不存储明文）
     */
    @Column(nullable = false, unique = true, length = 256)
    private String keyHash;

    /**
     * 密钥前缀（用于显示识别，如 sk-xxx）
     */
    @Column(nullable = false, length = 20)
    private String keyPrefix;

    /**
     * 所属用户ID
     */
    @Column(nullable = false)
    private String userId;

    /**
     * 密钥名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 密钥描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 权限范围（JSON格式，如：["chat", "embedding"]）
     */
    @Column(columnDefinition = "TEXT")
    private String permissions;

    /**
     * 每分钟请求限制
     */
    @Builder.Default
    private Integer rateLimitPerMinute = 100;

    /**
     * 每日请求限制
     */
    @Builder.Default
    private Integer rateLimitPerDay = 10000;

    /**
     * 每月Token配额
     */
    @Builder.Default
    private Long monthlyTokenQuota = 1000000L;

    /**
     * 已使用Token数
     */
    @Builder.Default
    private Long usedTokens = 0L;

    /**
     * 过期时间（null表示永不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查密钥是否有效
     */
    public boolean isValid() {
        if (!enabled) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * 检查是否超出配额
     */
    public boolean isQuotaExceeded() {
        return usedTokens >= monthlyTokenQuota;
    }
}
