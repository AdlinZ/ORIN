package com.adlin.orin.modules.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Runner Enrollment Token 实体。
 *
 * <p>一次性、短 TTL 的接入令牌；明文 token 仅在 {@code com.adlin.orin.modules.runner.controller.EnrollmentTokenController#create} 响应中
 * 返回一次，DB 与审计只保留 BCrypt hash。Runner 凭 token 调
 * {@code /api/system/runners/enroll} 成功后此行标记 {@code usedAt} 与 {@code runnerId}。
 */
@Entity
@Table(name = "runner_enrollment_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_runner_enroll_token", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_runner_enroll_exp", columnList = "expires_at"),
                @Index(name = "idx_runner_enroll_active", columnList = "used_at, expires_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerEnrollmentToken {

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    /** BCrypt 哈希；明文 token 仅在创建响应中返回。 */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;

    @Column(name = "used_at")
    private Long usedAt;

    @Column(name = "runner_id", length = 40)
    private String runnerId;

    @Column(name = "note", length = 200)
    private String note;

    @PrePersist
    protected void onCreate() {
        long now = Instant.now().toEpochMilli();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public boolean isActive(long nowMillis) {
        return usedAt == null && expiresAt > nowMillis;
    }
}
