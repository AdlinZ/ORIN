package com.adlin.orin.modules.run.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Run 实体（F03） — AgentVersion + Runner → 执行记录。
 *
 * <p>与 V96__Agent_Runs.sql 一一对应；timestamp 沿用 epoch millis 口径
 * （与 Runner / AGENTS.md §5.2 一致）。
 */
@Entity
@Table(name = "runs", indexes = {
        @Index(name = "idx_runs_agent_id", columnList = "agent_id"),
        @Index(name = "idx_runs_agent_version_id", columnList = "agent_version_id"),
        @Index(name = "idx_runs_runner_id", columnList = "runner_id"),
        @Index(name = "idx_runs_status", columnList = "status"),
        @Index(name = "idx_runs_status_created", columnList = "status, created_at"),
        @Index(name = "idx_runs_created_by", columnList = "created_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Run {

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(name = "agent_id", nullable = false, length = 50)
    private String agentId;

    @Column(name = "agent_version_id", nullable = false, length = 40)
    private String agentVersionId;

    @Column(name = "runner_id", length = 40)
    private String runnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RunStatus status = RunStatus.QUEUED;

    @Column(name = "config_snapshot", columnDefinition = "JSON", nullable = false)
    private String configSnapshot;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "lease_token", length = 128)
    private String leaseToken;

    @Column(name = "leased_at")
    private Long leasedAt;

    @Column(name = "lease_expires_at")
    private Long leaseExpiresAt;

    @Column(name = "started_at")
    private Long startedAt;

    @Column(name = "completed_at")
    private Long completedAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "original_run_id", length = 40)
    private String originalRunId;

    @PrePersist
    protected void onCreate() {
        long now = Instant.now().toEpochMilli();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = RunStatus.QUEUED;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().toEpochMilli();
    }

    /** 是否处于终态。 */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /** 是否可取消。 */
    public boolean isCancellable() {
        return status == RunStatus.QUEUED || status == RunStatus.LEASED || status == RunStatus.RUNNING;
    }

    /** 是否可重试。 */
    public boolean isRetryable() {
        return (status == RunStatus.FAILED || status == RunStatus.CANCELLED)
                && retryCount < maxRetries;
    }
}
