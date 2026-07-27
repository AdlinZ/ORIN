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
 * RunAssignment — Runner 分配、lease、attempt、终态原因的<b>唯一事实</b>（ADR-001 D-1.4.2）。
 *
 * <p>每个 Run 每次被分配给 Runner 都会创建一行。状态机独立于 Run 维度：
 * <pre>
 *   ASSIGNED → ACKED → COMPLETED | FAILED | CANCELLED | EXPIRED
 * </pre>
 *
 * <p>runs 表中的 lease_token / lease_expires_at / leased_at / run_attempt
 * 是本表状态的只读投影，不再作为独立事实。
 *
 * <p>timestamp 统一用 epoch millis（与 Run / Runner 实体口径一致）。
 */
@Entity
@Table(name = "run_assignment", indexes = {
        @Index(name = "idx_ra_run_id", columnList = "run_id"),
        @Index(name = "idx_ra_runner_id", columnList = "runner_id"),
        @Index(name = "idx_ra_lease_id", columnList = "lease_id"),
        @Index(name = "idx_ra_status_expires", columnList = "status, lease_expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunAssignment {

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(name = "run_id", nullable = false, length = 40)
    private String runId;

    @Column(name = "runner_id", nullable = false, length = 40)
    private String runnerId;

    @Column(name = "lease_id", nullable = false, length = 128)
    private String leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(name = "lease_expires_at", nullable = false)
    private Long leaseExpiresAt;

    @Column(name = "run_attempt", nullable = false)
    @Builder.Default
    private Integer runAttempt = 1;

    @Column(name = "terminal_reason", length = 64)
    private String terminalReason;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    /** SHA-256 of the accepted final-result payload; enables idempotent result replay. */
    @Column(name = "result_payload_hash", length = 64)
    private String resultPayloadHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

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
            status = AssignmentStatus.ASSIGNED;
        }
        if (runAttempt == null) {
            runAttempt = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().toEpochMilli();
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    public boolean isActive() {
        return status != null && status.isActive();
    }
}
