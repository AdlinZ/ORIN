-- V99__Run_Trace_And_Terminal_Reason.sql
-- ADR-001 alignment: add trace_id, run_attempt, terminal_reason to runs table.
-- No new tables yet — run_assignment and run_events come in R2.

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS run_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS terminal_reason VARCHAR(64) NULL;
