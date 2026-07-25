-- V104: Run → Endpoint binding
-- F05 P0 fix: persist endpoint_id on runs table so that
-- GET /v1/endpoints/{endpointId}/runs/{runId} can verify ownership
-- without relying solely on created_by heuristic.

ALTER TABLE runs
    ADD COLUMN endpoint_id VARCHAR(40) NULL COMMENT 'FK → agent_endpoints.id',
    ADD INDEX idx_runs_endpoint_id (endpoint_id);
