package com.adlin.orin.modules.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体版本实体（F02 R3）。
 *
 * <p>创建即 {@link Status#FROZEN}，不可变；唯一受控可变字段为 status（FROZEN→DEPRECATED）
 * 与 deprecated_* 元数据。backed by ADR-002 v4.1 §D-2.1 / §D-2.2 / §D-2.3。
 *
 * <p>{@code is_active} 列保留向后兼容（被 {@code AgentMetadata.active_version_id} 替代），
 * 新写入一律 {@code false}；前端不再依赖该列读取 active 状态。
 */
@Entity
@Table(name = "agent_versions", indexes = {
        @Index(name = "idx_agent_id", columnList = "agent_id"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_agent_version_status", columnList = "status"),
        @Index(name = "idx_agent_version_content_digest", columnList = "content_digest")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_version", columnNames = { "agent_id", "version_number" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentVersion {

    /** AgentVersion lifecycle（ADR-002 §D-2.1：仅 FROZEN / DEPRECATED 两态）。 */
    public enum Status {
        FROZEN, DEPRECATED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false, length = 50)
    private String agentId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "version_tag", length = 50)
    private String versionTag;

    @Column(name = "config_snapshot", columnDefinition = "JSON", nullable = false)
    private String configSnapshot;

    @Column(name = "change_description", columnDefinition = "TEXT")
    private String changeDescription;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Deprecated since F02 R3. Use {@code AgentMetadata.active_version_id} instead.
     * Kept for backward column compatibility only.
     */
    @Deprecated
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    // ===== F02 R3 ADR-002 字段 =====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.FROZEN;

    @Column(name = "content_digest", nullable = false, length = 64)
    private String contentDigest;

    @Column(name = "snapshot_schema_version", nullable = false)
    @Builder.Default
    private Short snapshotSchemaVersion = 1;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "frozen_by", length = 120)
    private String frozenBy;

    @Column(name = "deprecation_reason", length = 255)
    private String deprecationReason;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;

    @Column(name = "deprecated_by", length = 120)
    private String deprecatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (frozenAt == null && status == Status.FROZEN) {
            frozenAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.FROZEN;
        }
    }
}
