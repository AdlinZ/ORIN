package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识图谱实体
 * 表示图谱中的一个节点（实体）
 */
@Entity
@Table(name = "graph_entities", indexes = {
        @Index(name = "idx_graph_id", columnList = "graph_id"),
        @Index(name = "idx_entity_type", columnList = "entity_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 所属图谱ID
     */
    @Column(name = "graph_id", nullable = false)
    private String graphId;

    /**
     * 实体名称
     */
    @Column(name = "name", nullable = false, length = 500)
    private String name;

    /**
     * 实体类型（如：人物、地点、机构、概念等）
     */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /**
     * 实体描述/摘要
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 来源文档ID（用于追溯）
     */
    @Column(name = "source_document_id", length = 100)
    private String sourceDocumentId;

    /**
     * 来源chunk ID
     */
    @Column(name = "source_chunk_id", length = 100)
    private String sourceChunkId;

    /**
     * 实体属性（JSON格式存储额外属性）
     */
    @Column(name = "properties", columnDefinition = "TEXT")
    private String properties;

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
