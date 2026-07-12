ALTER TABLE mcp_services
    ADD COLUMN owner_user_id BIGINT NULL AFTER id;

CREATE INDEX idx_mcp_services_owner_user_id
    ON mcp_services(owner_user_id);
