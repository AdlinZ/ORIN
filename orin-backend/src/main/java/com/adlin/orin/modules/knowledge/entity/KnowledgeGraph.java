package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识图谱
 * 表示一个完整的知识图谱
 */
@Entity
@Table(name = "knowledge_graphs", indexes = {
        @Index(name = "idx_graph_name", columnList = "name"),
        @Index(name = "idx_graph_status", columnList = "build_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联知识库ID（可选）
     */
    @Column(name = "knowledge_base_id", length = 100)
    private String knowledgeBaseId;

    /**
     * 图谱名称
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 图谱描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 图谱状态
     */
    @Column(name = "build_status", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GraphBuildState buildStatus = GraphBuildState.PENDING;

    /**
     * 实体数量
     */
    @Column(name = "entity_count")
    @Builder.Default
    private Integer entityCount = 0;

    /**
     * 关系数量
     */
    @Column(name = "relation_count")
    @Builder.Default
    private Integer relationCount = 0;

    /**
     * 最后构建时间
     */
    @Column(name = "last_build_at")
    private LocalDateTime lastBuildAt;

    /**
     * 最后成功构建时间
     */
    @Column(name = "last_success_build_at")
    private LocalDateTime lastSuccessBuildAt;

    /**
     * 构建失败原因（最近一次失败时记录）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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
