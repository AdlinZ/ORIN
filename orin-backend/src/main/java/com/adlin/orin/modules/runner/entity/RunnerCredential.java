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
 * Runner Credential 实体 — 独立于用户 JWT、Endpoint API Key 与 Provider 凭据的机器身份凭据。
 *
 * <p>状态机（[ADR-001 §8][docs/Runner架构设计.md]）：ACTIVE / REVOKED。Runner 撤销后凭据 hash 仍
 * 保留（用于审计追溯）；校验服务仍返回已匹配的 REVOKED 行，使鉴权层可以稳定区分 401 与 403。R3 落地 AEAD 时由 {@code encryptedValue} 字段承载
 * envelope（v2:&lt;keyId&gt;:...），并把当前 BCrypt hash 路径迁移到 envelope。F01 暂时不写入
 * encryptedValue 字段。
 *
 * <p>明文凭据仅在 enroll 响应中返回一次，DB 与审计不落明文。
 */
@Entity
@Table(name = "runner_credentials",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_runner_cred_credid", columnNames = "credential_id")
        },
        indexes = {
                @Index(name = "idx_runner_cred_runner", columnList = "runner_id"),
                @Index(name = "idx_runner_cred_status", columnList = "status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerCredential {

    public enum Status {
        ACTIVE,
        REVOKED
    }

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(name = "runner_id", nullable = false, length = 40)
    private String runnerId;

    @Column(name = "credential_id", nullable = false, length = 80)
    private String credentialId;

    @Column(name = "credential_hash", nullable = false, length = 255)
    private String credentialHash;

    @Column(name = "key_prefix", length = 40)
    private String keyPrefix;

    @Column(name = "last4", length = 10)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "revoked_at")
    private Long revokedAt;

    @Column(name = "revoked_by", length = 120)
    private String revokedBy;

    /** ADR-002 §D-2.11 R3 目标位：AEAD envelope。当前 F01 不写。 */
    @Column(name = "encrypted_value", columnDefinition = "TEXT")
    private String encryptedValue;

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
            status = Status.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().toEpochMilli();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
