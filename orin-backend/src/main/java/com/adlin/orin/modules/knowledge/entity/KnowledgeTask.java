package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_tasks", indexes = {
        @Index(name = "idx_task_status", columnList = "status"),
        @Index(name = "idx_asset_ref", columnList = "asset_id, asset_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "asset_type", nullable = false, length = 50)
    private String assetType; // MULTIMODAL_FILE, DOCUMENT

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType; // CAPTIONING, EMBEDDING, INDEXING

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(name = "retry_count", columnDefinition = "INT DEFAULT 0")
    private Integer retryCount;

    @Column(name = "max_retries", columnDefinition = "INT DEFAULT 3")
    private Integer maxRetries;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (retryCount == null)
            retryCount = 0;
        if (maxRetries == null)
            maxRetries = 3;
        if (status == null)
            status = "PENDING";
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
