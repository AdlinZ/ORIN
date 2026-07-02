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
        @Index(name = "idx_conversation_id", columnList = "conversationId"),
        @Index(name = "idx_trace_id", columnList = "traceId")
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
     * 链路追踪ID (用于调用链监控)
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

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
     * 用户请求里写的 model 字符串（路由前的别名）。
     * 与 model/resolvedModel 区别在于：alias = 用户原始字符串，providerModel = 实际发往下游的字符串。
     * 多数 provider 透传 → alias == providerModel；Dify 等不透传场景下两者不同。
     */
    @Column(name = "model_alias", length = 100)
    private String modelAlias;

    /**
     * 实际转发到 Provider 的模型名称（路由后）。
     */
    @Column(name = "provider_model", length = 100)
    private String providerModel;

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
     * 租户ID (用于区分不同客户)
     */
    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    /**
     * 内部成本 (实际支付给供应商)
     */
    @Column(name = "internal_cost", precision = 19, scale = 8)
    private java.math.BigDecimal internalCost;

    /**
     * 外部报价 (向用户展示/收取)
     */
    @Column(name = "external_price", precision = 19, scale = 8)
    private java.math.BigDecimal externalPrice;

    /**
     * 利润 (External - Internal)
     */
    @Column(precision = 19, scale = 8)
    private java.math.BigDecimal profit;

    /**
     * 估算成本（兼容旧字段，映射到 external_price）
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
     * 结构化错误码（来自 ErrorCode 枚举或 HTTP status 映射，如 70005/100003/50003）。
     * 与 errorMessage 互为补充：errorCode 便于统计与告警，errorMessage 保留自由文本定位上下文。
     */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    /**
     * 生成文件的ID (用于音频、图片、视频等)
     */
    @Column(name = "file_id", length = 100)
    private String fileId;

    /**
     * 文件下载URL
     */
    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    /**
     * 创建时间
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
