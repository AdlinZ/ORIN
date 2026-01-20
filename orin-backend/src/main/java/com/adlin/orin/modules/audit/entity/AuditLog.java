package com.adlin.orin.modules.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_api_key_id", columnList = "apiKeyId"),
        @Index(name = "idx_created_at", columnList = "createdAt"),
        @Index(name = "idx_endpoint", columnList = "endpoint"),
        @Index(name = "idx_conversation_id", columnList = "conversationId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 用户ID
     */
    @Column(nullable = false)
    private String userId;

    /**
     * API密钥ID
     */
    private String apiKeyId;

    /**
     * Provider ID
     */
    private String providerId;

    /**
     * Conversation ID (for grouping messages in the same conversation session)
     */
    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    /**
     * Workflow ID (if part of a workflow execution)
     */
    @Column(name = "workflow_id")
    private String workflowId;

    /**
     * Provider类型
     */
    private String providerType;

    /**
     * 请求端点
     */
    @Column(nullable = false, length = 200)
    private String endpoint;

    /**
     * HTTP方法
     */
    @Column(length = 10)
    private String method;

    /**
     * 请求模型
     */
    @Column(length = 100)
    private String model;

    /**
     * 请求IP
     */
    @Column(length = 50)
    private String ipAddress;

    /**
     * User Agent
     */
    @Column(length = 500)
    private String userAgent;

    /**
     * 请求参数（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String requestParams;

    /**
     * 响应内容（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String responseContent;

    /**
     * 响应状态码
     */
    private Integer statusCode;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 提示词Token数
     */
    @Builder.Default
    private Integer promptTokens = 0;

    /**
     * 完成Token数
     */
    @Builder.Default
    private Integer completionTokens = 0;

    /**
     * 总Token数
     */
    @Builder.Default
    private Integer totalTokens = 0;

    /**
     * 估算成本（美元）
     */
    @Builder.Default
    private Double estimatedCost = 0.0;

    /**
     * 是否成功
     */
    @Builder.Default
    private Boolean success = true;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
