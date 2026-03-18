package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 限流配置实体
 * 支持按用户/API Key/Agent维度的限流配置
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mon_rate_limit_config")
public class RateLimitConfig {

    @Id
    private String id; // 默认使用 "DEFAULT"

    /**
     * 启用限流
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 每分钟请求次数限制
     */
    @Builder.Default
    private Integer requestsPerMinute = 60;

    /**
     * 每天请求次数限制
     */
    @Builder.Default
    private Integer requestsPerDay = 10000;

    /**
     * 令牌桶容量
     */
    @Builder.Default
    private Integer bucketSize = 60;

    /**
     * 令牌补充速率（每秒钟补充的令牌数）
     */
    @Builder.Default
    private Double refillRate = 1.0;

    /**
     * 是否启用用户级别限流
     */
    @Builder.Default
    private Boolean enableUserLimit = true;

    /**
     * 是否启用API Key级别限流
     */
    @Builder.Default
    private Boolean enableApiKeyLimit = true;

    /**
     * 是否启用Agent级别限流
     */
    @Builder.Default
    private Boolean enableAgentLimit = false;

    /**
     * 限流算法: TOKEN_BUCKET, SLIDING_WINDOW
     */
    @Builder.Default
    private String algorithm = "TOKEN_BUCKET";

    /**
     * 注释/描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = "DEFAULT";
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
