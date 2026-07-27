package com.adlin.orin.modules.run.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Run 事件（ADR-001 D-1.4.1）——Runner 机器通道 /events 的幂等存储。
 *
 * <p>幂等键：UNIQUE(run_id, lease_id, run_attempt, event_seq)。
 * 同一键 + 相同 payload → 200 no-op；同一键 + 不同 payload → 409 RESULT_CONFLICT。
 * DB UNIQUE 约束是最终保证；id 是自增 surrogate PK。
 */
@Entity
@Table(name = "run_events", indexes = {
        @Index(name = "idx_re_run_id", columnList = "run_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 40)
    private String runId;

    @Column(name = "lease_id", nullable = false, length = 128)
    private String leaseId;

    @Column(name = "run_attempt", nullable = false)
    private Integer runAttempt;

    @Column(name = "event_seq", nullable = false)
    private Integer eventSeq;

    @Column(length = 10, nullable = false)
    @Builder.Default
    private String level = "INFO";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column
    private Long timestamp;

    /** SHA-256 of the canonical event payload for replay-versus-conflict detection. */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().toEpochMilli();
        }
    }
}
