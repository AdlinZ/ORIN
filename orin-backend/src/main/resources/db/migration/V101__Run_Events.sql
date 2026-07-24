-- V101__Run_Events.sql
-- ADR-001 D-1.4.1: idempotent event storage for Runner machine channel /events.
-- Idempotency key: UNIQUE(run_id, lease_id, run_attempt, event_seq).
-- Same key + same payload → 200 no-op; same key + different payload → 409 RESULT_CONFLICT.
-- Also continue writing to run_logs for backward compatibility with GET /api/v1/runs/{runId}/logs.

CREATE TABLE IF NOT EXISTS run_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id       VARCHAR(40)  NOT NULL COMMENT 'FK → runs.id',
    lease_id     VARCHAR(128) NOT NULL COMMENT 'opaque lease token from run_assignment.lease_id',
    run_attempt  INT          NOT NULL COMMENT 'matches run_assignment.run_attempt',
    event_seq    INT          NOT NULL COMMENT 'Runner-side auto-increment sequence per assignment',
    level        VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    message      TEXT         NOT NULL,
    timestamp    BIGINT       NULL COMMENT 'epoch millis from Runner',
    created_at   BIGINT       NOT NULL,
    UNIQUE KEY uq_re_idemp (run_id, lease_id, run_attempt, event_seq),
    INDEX idx_re_run_id (run_id),
    CONSTRAINT fk_re_run FOREIGN KEY (run_id) REFERENCES runs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
