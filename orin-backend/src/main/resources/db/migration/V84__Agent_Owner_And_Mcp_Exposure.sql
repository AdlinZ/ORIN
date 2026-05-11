-- Phase 0.5 PR2a: Agent ownership foundation for external MCP exposure.
-- Historical Agent rows lack audit ownership fields, so the original creator cannot be restored.
-- Existing rows are assigned to the system administrator account to keep ownership non-null.

ALTER TABLE agent_metadata
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE agent_metadata
SET owner_user_id = (
    SELECT admin_user_id FROM (
        SELECT u.user_id AS admin_user_id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.user_id
        JOIN sys_role r ON r.role_id = ur.role_id
        WHERE r.role_code IN ('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')
        ORDER BY CASE r.role_code WHEN 'ROLE_SUPER_ADMIN' THEN 0 ELSE 1 END, u.user_id
        LIMIT 1
    ) admin_owner
)
WHERE owner_user_id IS NULL;

UPDATE agent_metadata
SET owner_user_id = (
    SELECT fallback_user_id FROM (
        SELECT MIN(user_id) AS fallback_user_id FROM sys_user
    ) fallback_owner
)
WHERE owner_user_id IS NULL;

ALTER TABLE agent_metadata
    MODIFY COLUMN owner_user_id BIGINT NOT NULL;

CREATE INDEX idx_agent_metadata_owner_user_id ON agent_metadata(owner_user_id);
CREATE INDEX idx_agent_metadata_owner_mcp_exposed ON agent_metadata(owner_user_id, mcp_exposed);
