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
 * Run 执行日志行（F04 观察控制）。
 *
 * <p>Runner 在执行期间逐行推送到 /api/system/runners/{id}/runs/{runId}/log；
 * 前端轮询 GET /api/v1/runs/{runId}/logs?afterSeq=N 增量拉取。
 */
@Entity
@Table(name = "run_logs", indexes = {
        @Index(name = "idx_run_logs_run_id_seq", columnList = "run_id, sequence")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 40)
    private String runId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String level = "INFO";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().toEpochMilli();
        }
    }
}
