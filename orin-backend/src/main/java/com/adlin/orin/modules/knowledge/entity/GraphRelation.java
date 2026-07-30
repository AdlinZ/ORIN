package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识图谱关系
 * 表示图谱中两个实体之间的关系（边）
 */
@Entity
@Table(name = "graph_relations", indexes = {
        @Index(name = "idx_graph_id", columnList = "graph_id"),
        @Index(name = "idx_source_entity", columnList = "source_entity_id"),
        @Index(name = "idx_target_entity", columnList = "target_entity_id"),
        @Index(name = "idx_relation_type", columnList = "relation_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 所属图谱ID
     */
    @Column(name = "graph_id", nullable = false)
    private String graphId;

    /**
     * 源实体ID
     */
    @Column(name = "source_entity_id", nullable = false)
    private String sourceEntityId;

    /**
     * 目标实体ID
     */
    @Column(name = "target_entity_id", nullable = false)
    private String targetEntityId;

    /**
     * 关系类型（如：毕业于、工作于、位于、相关等）
     */
    @Column(name = "relation_type", nullable = false, length = 100)
    private String relationType;

    /**
     * 关系描述/摘要
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 来源文档ID
     */
    @Column(name = "source_document_id", length = 100)
    private String sourceDocumentId;

    /**
     * 来源chunk ID
     */
    @Column(name = "source_chunk_id", length = 100)
    private String sourceChunkId;

    /**
     * 关系属性（JSON格式存储额外属性）
     */
    @Column(name = "properties", columnDefinition = "TEXT")
    private String properties;

    /**
     * 权重/置信度
     */
    @Column(name = "weight")
    @Builder.Default
    private Double weight = 1.0;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
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
}
