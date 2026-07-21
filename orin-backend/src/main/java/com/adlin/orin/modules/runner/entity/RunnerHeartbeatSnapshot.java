package com.adlin.orin.modules.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Runner Heartbeat Snapshot 实体 — Runner 每次心跳写一行最新资源快照。
 *
 * <p>仅保留每 Runner 最近 N 条（默认 20），由 {@code RunnerSnapshotRetentionService} 周期清理。
 * F01 MVP 不构建历史时序曲线，仅作最近状态展示与"最近一次资源上报"快照。
 */
@Entity
@Table(name = "runner_heartbeat_snapshots",
        indexes = {
                @Index(name = "idx_runner_hb_runner_time", columnList = "runner_id, reported_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerHeartbeatSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "runner_id", nullable = false, length = 40)
    private String runnerId;

    @Column(name = "cpu_usage", precision = 5, scale = 2)
    private BigDecimal cpuUsage;

    @Column(name = "memory_used")
    private Long memoryUsed;

    @Column(name = "disk_used")
    private Long diskUsed;

    @Column(name = "gpu_usage", precision = 5, scale = 2)
    private BigDecimal gpuUsage;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "disk_total")
    private Long diskTotal;

    @Column(name = "dependency_health", length = 20)
    private String dependencyHealth;

    @Column(name = "reported_at", nullable = false)
    private Long reportedAt;

    /** 完整请求体 JSON；用于审计与回放。 */
    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
