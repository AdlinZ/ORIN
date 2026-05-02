package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库同步变更日志
 * 用于追踪增量变更
 */
@Entity
@Table(name = "knowledge_sync_change_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Agent ID
     */
    @Column(name = "agent_id", length = 64)
    private String agentId;

    /**
     * 外部集成 ID。为空时表示旧知识库同步记录或尚未绑定具体平台。
     */
    @Column(name = "integration_id")
    private Long integrationId;

    /**
     * 平台类型: DIFY, N8N, COZE, RAGFLOW, CUSTOM
     */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /**
     * ORIN 通用资源类型: WORKFLOW, AGENT, TOOL, KNOWLEDGE_BASE, DOCUMENT, EXECUTION,
     * VARIABLE, CREDENTIAL_REF, PUBLISH_STATUS
     */
    @Column(name = "orin_resource_type", length = 40)
    private String orinResourceType;

    /**
     * ORIN 通用资源 ID。新同步层优先使用该字段；documentId 保留给旧知识库同步。
     */
    @Column(name = "orin_resource_id", length = 128)
    private String orinResourceId;

    @Column(name = "resource_name", length = 255)
    private String resourceName;

    /**
     * 文档ID
     */
    @Column(name = "document_id", length = 64)
    private String documentId;

    /**
     * 知识库ID
     */
    @Column(name = "knowledge_base_id", length = 64)
    private String knowledgeBaseId;

    /**
     * 变更类型: ADDED, UPDATED, DELETED
     */
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    /**
     * 文档版本号
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 文档内容hash
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Canonical resource snapshot hash. 通用同步层使用该字段判断幂等和外部漂移。
     */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /**
     * 脱敏后的资源快照 JSON。不得包含 credential 明文。
     */
    @Column(name = "payload_snapshot", columnDefinition = "TEXT")
    private String payloadSnapshot;

    /**
     * 变更时间
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * 是否已同步
     */
    @Column(name = "synced")
    @Builder.Default
    private Boolean synced = false;

    /**
     * 变更来源: ORIN, EXTERNAL, WEBHOOK
     */
    @Column(name = "change_source", length = 20)
    @Builder.Default
    private String changeSource = "ORIN";

    /**
     * 通用同步状态: PENDING, SYNCED, FAILED, CONFLICT
     */
    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private String syncStatus = "PENDING";

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 幂等键（用于防重复同步）
     * 格式: agentId_documentId_changeType
     */
    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}
