package com.adlin.orin.modules.agent.freeze.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Freeze Idempotency 主表（ADR-002 §D-2.3.2）。
 *
 * <p>DB-primary 真相源；Redis 仅做加速（本切片不接 Redis）。
 * 同 (agent_id, idempotency_key_hash) + 相同 request_digest → 返回历史 agent_version_id；
 * 同 key + 不同 request_digest → 抛 IDEMPOTENCY_KEY_CONFLICT。
 *
 * <p>expires_at 由后台 cleanup job 清理；MVP 默认 24h 物理 retention。
 */
@Entity
@Table(name = "agent_version_freeze_idempotency")
@IdClass(AgentVersionFreezeIdempotency.PK.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentVersionFreezeIdempotency {

    @Id
    @Column(name = "agent_id", nullable = false, length = 40)
    private String agentId;

    @Id
    @Column(name = "idempotency_key_hash", nullable = false, length = 64)
    private String idempotencyKeyHash;

    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @Column(name = "agent_version_id", nullable = false, length = 40)
    private String agentVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Composite PK class for {@link IdClass}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String agentId;
        private String idempotencyKeyHash;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(agentId, pk.agentId)
                    && Objects.equals(idempotencyKeyHash, pk.idempotencyKeyHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentId, idempotencyKeyHash);
        }
    }
}
