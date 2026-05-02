package com.adlin.orin.modules.integrationsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_sync_job", indexes = {
        @Index(name = "idx_sync_job_integration", columnList = "integration_id"),
        @Index(name = "idx_sync_job_status", columnList = "status"),
        @Index(name = "idx_sync_job_started_at", columnList = "started_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "resource_scope", length = 40)
    private String resourceScope;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_count")
    @Builder.Default
    private Integer totalCount = 0;

    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "failure_count")
    @Builder.Default
    private Integer failureCount = 0;

    @Column(name = "conflict_count")
    @Builder.Default
    private Integer conflictCount = 0;

    @Column(name = "cursor_value", length = 255)
    private String cursorValue;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
