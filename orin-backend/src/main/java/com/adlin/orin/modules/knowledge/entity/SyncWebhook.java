package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库同步Webhook配置
 * 用于通知端侧知识库变更
 */
@Entity
@Table(name = "knowledge_sync_webhook")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Agent ID
     */
    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    /**
     * Webhook URL
     */
    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    /**
     * Webhook 签名密钥
     */
    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    /**
     * 是否启用
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 事件类型 (逗号分隔): document_added, document_updated, document_deleted, sync_completed
     */
    @Column(name = "event_types", length = 500)
    private String eventTypes;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 创建者
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 失败次数
     */
    @Column(name = "failure_count")
    @Builder.Default
    private Integer failureCount = 0;

    /**
     * 最后失败时间
     */
    @Column(name = "last_failure_time")
    private LocalDateTime lastFailureTime;

    /**
     * 最后失败原因
     */
    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    /**
     * 是否失效 (连续失败3次后标记为失效)
     */
    @Column(name = "disabled")
    @Builder.Default
    private Boolean disabled = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
