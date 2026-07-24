-- V102__Lease_Secret_Binding.sql
-- ADR-002 D-2.8.2: per-assignment secret materialization tracking.
-- assignment_id is the sole truth key; lease_id / run_id / runner_id are
-- derived from run_assignment (not duplicated here).
-- status: ACTIVE → INVALIDATED (revoked) / RELEASED (terminal cleanup).
-- TODO R3: FK to gateway_secret_revisions(secret_id, revision) once that table exists.
-- TODO R3: upgrade EncryptionUtil from AES/ECB to AES/GCM per ADR-002 D-2.11.

CREATE TABLE IF NOT EXISTS lease_secret_binding (
    assignment_id       VARCHAR(50)  NOT NULL COMMENT 'FK → run_assignment.id (sole truth key)',
    inject_as           VARCHAR(255) NOT NULL COMMENT 'env-var name injected into TaskRuntime',
    secret_id           VARCHAR(100) NOT NULL COMMENT 'FK → gateway_secrets.secret_id',
    revision            VARCHAR(64)  NOT NULL DEFAULT 'v1' COMMENT 'TODO R3: FK → gateway_secret_revisions.revision',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        COMMENT 'ACTIVE | INVALIDATED | RELEASED',
    bound_at            BIGINT       NOT NULL COMMENT 'epoch millis',
    invalidated_at      BIGINT       NULL,
    invalidation_reason VARCHAR(64)  NULL
                        COMMENT 'REVOKED | LEASE_EXPIRED | ASSIGNMENT_TERMINATED',
    PRIMARY KEY (assignment_id, inject_as),
    INDEX idx_lsb_assignment_status (assignment_id, status),
    INDEX idx_lsb_secret (secret_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
