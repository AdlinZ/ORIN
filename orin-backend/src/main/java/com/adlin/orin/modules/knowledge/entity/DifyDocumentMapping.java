package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dify 文档映射表
 * 维护本地 docId ↔ Dify dataset/docId 的对应关系
 * 用于精确追踪同步链路，避免靠名称匹配
 */
@Entity
@Table(name = "knowledge_dify_document_mapping", indexes = {
        @Index(name = "idx_local_doc_id", columnList = "local_doc_id"),
        @Index(name = "idx_dify_dataset_doc", columnList = "dify_dataset_id, dify_doc_id"),
        @Index(name = "idx_integration_id", columnList = "integration_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyDocumentMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 集成 ID（指向 knowledge_external_integration.id）
     */
    @Column(name = "integration_id")
    private Long integrationId;

    /**
     * 本地文档 ID（指向 kb_documents.id）
     */
    @Column(name = "local_doc_id", length = 64)
    private String localDocId;

    /**
     * 本地知识库 ID
     */
    @Column(name = "local_kb_id", length = 64)
    private String localKbId;

    /**
     * Dify 数据集 ID
     */
    @Column(name = "dify_dataset_id", length = 64)
    private String difyDatasetId;

    /**
     * Dify 文档 ID
     */
    @Column(name = "dify_doc_id", length = 64)
    private String difyDocId;

    /**
     * 幂等键（由 localDocId + integrationId + changeType 生成）
     * 用于防重复推送/入库
     */
    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    /**
     * 同步状态: SYNCED, PENDING, FAILED, DELETED
     */
    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private String syncStatus = "PENDING";

    /**
     * 本地文档版本
     */
    @Column(name = "local_version")
    private Integer localVersion;

    /**
     * Dify 文档版本
     */
    @Column(name = "dify_version")
    private String difyVersion;

    /**
     * 内容哈希（上次同步时的本地 hash）
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Dify 端是否已删除（用于软删除→外部删除确认）
     */
    @Column(name = "deleted_on_dify")
    @Builder.Default
    private Boolean deletedOnDify = false;

    /**
     * 最后同步时间
     */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 同步失败原因
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (idempotencyKey == null && localDocId != null && integrationId != null) {
            idempotencyKey = generateIdempotencyKey(localDocId, integrationId);
        }
    }

    /**
     * 生成幂等键
     */
    public static String generateIdempotencyKey(String localDocId, Long integrationId) {
        return String.format("%s_%d", localDocId, integrationId);
    }
}
