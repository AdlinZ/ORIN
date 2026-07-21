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
 * AgentVersion SecretReference 桥接行（ADR-002 §D-2.1 / §D-2.4 / §D-2.6）。
 *
 * <p>每个 FROZEN AgentVersion 持有 N 条 SecretReference；FK → gateway_secrets.secret_id
 * 由 service 层校验（R3 暂不建跨模块 FK，避免耦合；后续 PR 引入时迁移一并加）。
 * <ul>
 *   <li>source = CONTROL_PLANE：必填 secretId（指向 gateway_secrets.secret_id）；</li>
 *   <li>source = RUNNER_LOCAL：必填 localKey（MVP 拒绝 freeze，本切片不暴露）；</li>
 *   <li>alias 在同一 agent_version_id 内唯一；同名 secretRef alias 共享会导致 digest 不一致。</li>
 * </ul>
 */
@Entity
@Table(name = "agent_version_secret_refs")
@IdClass(AgentVersionSecretRef.PK.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentVersionSecretRef {

    @Id
    @Column(name = "agent_version_id", nullable = false, length = 40)
    private String agentVersionId;

    @Id
    @Column(nullable = false, length = 64)
    private String alias;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "secret_id", length = 100)
    private String secretId;

    @Column(name = "local_key", length = 120)
    private String localKey;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "inject_as", nullable = false, length = 64)
    private String injectAs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Composite PK class for {@link IdClass}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String agentVersionId;
        private String alias;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(agentVersionId, pk.agentVersionId)
                    && Objects.equals(alias, pk.alias);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentVersionId, alias);
        }
    }
}
