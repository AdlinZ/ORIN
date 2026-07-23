package com.adlin.orin.modules.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Runner 实体 — 接入 ORIN 控制面的远程执行进程。
 *
 * <p>仅作为业务持久化的"画像"，Runner 不连接业务数据库（ADR-001 §8）；其最新心跳与资源快照在
 * {@link RunnerHeartbeatSnapshot} 中按时间序列保留。
 *
 * <p>字段命名与 V94 迁移一致；timestamp 统一用 epoch millis（与 AGENTS.md §5.2 既有模块口径一致）。
 */
@Entity
@Table(name = "runners",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_runner_name_owner", columnNames = {"name", "created_by"})
        },
        indexes = {
                @Index(name = "idx_runner_status_last_hb", columnList = "status, last_heartbeat_at"),
                @Index(name = "idx_runner_created_by", columnList = "created_by")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Runner {

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunnerStatus status;

    @Column(length = 40)
    private String version;

    @Column(length = 255)
    private String hostname;

    @Column(length = 40)
    private String os;

    @Column(length = 40)
    private String arch;

    /** Runner 自身声明的标签，JSON 字符串。 */
    @Column(columnDefinition = "JSON")
    private String labels;

    /** Runner 能力声明（如支持模型、工具等），JSON 字符串。 */
    @Column(columnDefinition = "JSON")
    private String capabilities;

    /** GPU 信息（型号 + 显存），JSON 字符串；F01 MVP 允许为空。 */
    @Column(name = "gpu_info", columnDefinition = "JSON")
    private String gpuInfo;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "disk_total")
    private Long diskTotal;

    @Column(name = "max_concurrency", nullable = false)
    private Integer maxConcurrency;

    @Column(name = "active_runs", nullable = false)
    private Integer activeRuns;

    @Column(name = "queued_runs", nullable = false)
    private Integer queuedRuns;

    @Column(name = "last_heartbeat_at")
    private Long lastHeartbeatAt;

    @Column(name = "last_dependency_health", length = 20)
    private String lastDependencyHealth;

    /** 调度意图与在线状态分离；离线期间也保留 Drain 请求。 */
    @Column(name = "drain_requested", nullable = false)
    private Boolean drainRequested;

    /** Runner 确认收到 DRAINING 控制帧并切换为不接新 Run 的时间。 */
    @Column(name = "drain_ack_at")
    private Long drainAckAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

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
        if (maxConcurrency == null) {
            maxConcurrency = 1;
        }
        if (activeRuns == null) {
            activeRuns = 0;
        }
        if (queuedRuns == null) {
            queuedRuns = 0;
        }
        if (drainRequested == null) {
            drainRequested = false;
        }
        if (status == null) {
            status = RunnerStatus.NEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().toEpochMilli();
    }
}
