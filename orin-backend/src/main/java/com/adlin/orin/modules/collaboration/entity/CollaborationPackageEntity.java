package com.adlin.orin.modules.collaboration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 多智能体协作任务包实体
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collab_package")
public class CollaborationPackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", unique = true, nullable = false, length = 64)
    private String packageId;

    @Column(name = "root_task_id")
    private Long rootTaskId;

    @Column(name = "intent", columnDefinition = "TEXT", nullable = false)
    private String intent;

    @Column(name = "intent_category", length = 50)
    private String intentCategory;

    @Column(name = "intent_priority", length = 20)
    private String intentPriority;

    @Column(name = "intent_complexity", length = 30)
    private String intentComplexity;

    @Column(name = "need_review")
    private Boolean needReview;

    @Column(name = "need_consensus")
    private Boolean needConsensus;

    @Column(name = "collaboration_mode", length = 30)
    @Builder.Default
    private String collaborationMode = "SEQUENTIAL";

    @Column(name = "shared_context", columnDefinition = "JSON")
    private String sharedContext;

    @Column(name = "strategy", columnDefinition = "JSON")
    private String strategy;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "PLANNING";

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}