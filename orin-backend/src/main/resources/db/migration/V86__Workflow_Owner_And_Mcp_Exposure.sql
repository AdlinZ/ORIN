-- Phase 0.5 PR4: Workflow ownership foundation for external MCP exposure.
-- Historical rows are backfilled from created_by when possible, then system admin fallback.

ALTER TABLE workflows
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE workflows w
JOIN sys_user u ON w.created_by IS NOT NULL
    AND (w.created_by = CAST(u.user_id AS CHAR) OR w.created_by = u.username)
SET w.owner_user_id = u.user_id
WHERE w.owner_user_id IS NULL;

UPDATE workflows
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

UPDATE workflows
SET owner_user_id = (
    SELECT fallback_user_id FROM (
        SELECT MIN(user_id) AS fallback_user_id FROM sys_user
    ) fallback_owner
)
WHERE owner_user_id IS NULL;

ALTER TABLE workflows
    MODIFY COLUMN owner_user_id BIGINT NOT NULL;

CREATE INDEX idx_workflows_owner_mcp_exposed ON workflows(owner_user_id, mcp_exposed);
