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
    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    /**
     * 文档ID
     */
    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    /**
     * 知识库ID
     */
    @Column(name = "knowledge_base_id", nullable = false, length = 64)
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
