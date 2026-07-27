-- V99__Run_Trace_And_Terminal_Reason.sql
-- ADR-001 alignment: add trace_id, run_attempt, terminal_reason to runs table.
-- No new tables yet — run_assignment and run_events come in R2.

ALTER TABLE runs
    ADD COLUMN trace_id VARCHAR(64) NULL,
    ADD COLUMN run_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN terminal_reason VARCHAR(64) NULL;
