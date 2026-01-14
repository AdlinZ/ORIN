package com.adlin.orin.modules.apikey.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API端点实体
 * 用于管理对外开放的API端点配置
 */
@Entity
@Table(name = "api_endpoints", indexes = {
        @Index(name = "idx_path_method", columnList = "path,method", unique = true),
        @Index(name = "idx_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * API路径 (如: /v1/chat/completions)
     */
    @Column(nullable = false, length = 255)
    private String path;

    /**
     * HTTP方法 (GET, POST, PUT, DELETE, PATCH)
     */
    @Column(nullable = false, length = 10)
    private String method;

    /**
     * 端点名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 端点描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 是否需要认证
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean requireAuth = true;

    /**
     * 所需权限标识 (如: chat, embedding, models)
     */
    @Column(length = 100)
    private String permissionRequired;

    /**
     * 每分钟请求限制
     */
    @Builder.Default
    private Integer rateLimitPerMinute = 100;

    /**
     * 每小时请求限制
     */
    @Builder.Default
    private Integer rateLimitPerHour = 5000;

    /**
     * 每日请求限制
     */
    @Builder.Default
    private Integer rateLimitPerDay = 100000;

    /**
     * 总调用次数
     */
    @Builder.Default
    private Long totalCalls = 0L;

    /**
     * 成功调用次数
     */
    @Builder.Default
    private Long successCalls = 0L;

    /**
     * 失败调用次数
     */
    @Builder.Default
    private Long failedCalls = 0L;

    /**
     * 平均响应时间(毫秒)
     */
    @Builder.Default
    private Integer avgResponseTimeMs = 0;

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
     * 计算成功率
     */
    public double getSuccessRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) successCalls / totalCalls * 100;
    }

    /**
     * 更新调用统计
     */
    public void updateCallStats(boolean success, long responseTimeMs) {
        this.totalCalls++;
        if (success) {
            this.successCalls++;
        } else {
            this.failedCalls++;
        }

        // 计算移动平均响应时间
        if (this.avgResponseTimeMs == 0) {
            this.avgResponseTimeMs = (int) responseTimeMs;
        } else {
            this.avgResponseTimeMs = (int) ((this.avgResponseTimeMs * 0.9) + (responseTimeMs * 0.1));
        }
    }
}
