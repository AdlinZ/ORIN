package com.adlin.orin.modules.run.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Lease Secret Binding（ADR-002 D-2.8.2）——per-assignment 的 secret 物化追踪。
 *
 * <p>assignment_id 是唯一事实键；lease_id / run_id / runner_id 由 run_assignment 派生，
 * 不在此表重复落列。
 *
 * <p>TODO R3: FK → gateway_secret_revisions(secret_id, revision)。
 * R2 MVP 使用 revision='v1' 占位。
 */
@Entity
@Table(name = "lease_secret_binding")
@IdClass(LeaseSecretBinding.PK.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseSecretBinding {

    @Id
    @Column(name = "assignment_id", nullable = false, length = 50)
    private String assignmentId;

    @Id
    @Column(name = "inject_as", nullable = false, length = 255)
    private String injectAs;

    @Column(name = "secret_id", nullable = false, length = 100)
    private String secretId;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String revision = "v1";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BindingStatus status = BindingStatus.ACTIVE;

    @Column(name = "bound_at", nullable = false)
    private Long boundAt;

    @Column(name = "invalidated_at")
    private Long invalidatedAt;

    @Column(name = "invalidation_reason", length = 64)
    private String invalidationReason;

    @PrePersist
    protected void onCreate() {
        if (boundAt == null) {
            boundAt = Instant.now().toEpochMilli();
        }
    }

    /** Composite PK for {@link IdClass}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String assignmentId;
        private String injectAs;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(assignmentId, pk.assignmentId)
                    && Objects.equals(injectAs, pk.injectAs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assignmentId, injectAs);
        }
    }
}
