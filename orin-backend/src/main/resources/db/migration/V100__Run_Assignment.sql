-- V100__Run_Assignment.sql
-- ADR-001 D-1.4.2: run_assignment is the single truth source for Runner assignment,
-- lease, attempt, and terminal reason. Every Run-to-Runner binding creates one row.
-- Status machine: ASSIGNED → ACKED → COMPLETED | FAILED | CANCELLED | EXPIRED
-- (independent of runs.status which is a read-only projection from the assignment).

CREATE TABLE IF NOT EXISTS run_assignment (
    id                VARCHAR(40)  NOT NULL,
    run_id            VARCHAR(40)  NOT NULL COMMENT 'FK → runs.id',
    runner_id         VARCHAR(40)  NOT NULL COMMENT 'FK → runners.id',
    lease_id          VARCHAR(128) NOT NULL COMMENT 'opaque lease token (same value as runs.lease_token projection)',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ASSIGNED'
                      COMMENT 'ASSIGNED | ACKED | COMPLETED | FAILED | CANCELLED | EXPIRED',
    lease_expires_at  BIGINT       NOT NULL COMMENT 'epoch millis',
    run_attempt       INT          NOT NULL DEFAULT 1 COMMENT 'incremented each reassignment (ADR-001 D-1.4.3)',
    terminal_reason   VARCHAR(64)  NULL
                      COMMENT 'USER_CANCELLED | NETWORK_LOST | CREDENTIAL_REVOKED | RUNNER_REVOKED | SECRET_REVOKED | RUNNER_LOCAL_SECRET_MISSING',
    trace_id          VARCHAR(64)  NULL COMMENT 'W3C traceId (ADR-001 D-1.5)',
    created_at        BIGINT       NOT NULL,
    updated_at        BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ra_run_id (run_id),
    INDEX idx_ra_runner_id (runner_id),
    INDEX idx_ra_lease_id (lease_id),
    INDEX idx_ra_status_expires (status, lease_expires_at),
    CONSTRAINT fk_ra_run FOREIGN KEY (run_id) REFERENCES runs(id),
    CONSTRAINT fk_ra_runner FOREIGN KEY (runner_id) REFERENCES runners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
