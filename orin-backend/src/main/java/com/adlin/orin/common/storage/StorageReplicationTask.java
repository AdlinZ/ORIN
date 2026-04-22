package com.adlin.orin.common.storage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage_replication_tasks", indexes = {
        @Index(name = "idx_storage_replication_status", columnList = "status"),
        @Index(name = "idx_storage_replication_next_retry", columnList = "next_retry_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageReplicationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "object_key", length = 500, nullable = false)
    private String objectKey;

    @Column(name = "source_backend", length = 20, nullable = false)
    private String sourceBackend;

    @Column(name = "target_backend", length = 20, nullable = false)
    private String targetBackend;

    @Column(name = "source_locator", length = 1000, nullable = false)
    private String sourceLocator;

    @Column(name = "target_locator", length = 1000)
    private String targetLocator;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = ReplicationStatus.PENDING_REPAIR.name();

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 8;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (nextRetryAt == null) {
            nextRetryAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

