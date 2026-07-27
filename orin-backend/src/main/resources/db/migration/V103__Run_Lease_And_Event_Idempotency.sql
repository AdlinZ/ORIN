-- R2 protocol corrections:
-- 1) one opaque lease_id resolves exactly one assignment;
-- 2) event/result replays can distinguish identical payloads from conflicts.

ALTER TABLE run_assignment
    ADD UNIQUE INDEX uq_ra_lease_id (lease_id);

ALTER TABLE run_assignment
    ADD COLUMN result_payload_hash VARCHAR(64) NULL
        COMMENT 'SHA-256 of accepted /result payload for idempotent replay';

ALTER TABLE run_events
    ADD COLUMN payload_hash VARCHAR(64) NOT NULL DEFAULT ''
        COMMENT 'SHA-256 of canonical event payload; empty only for pre-V103 rows';
