-- Align knowledge base ownership with agent_metadata.owner_user_id.
-- Existing rows are stamped to a system admin account; list filtering / ACL remains a later phase.

ALTER TABLE knowledge_bases
    ADD COLUMN owner_user_id BIGINT NULL;

UPDATE knowledge_bases
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

UPDATE knowledge_bases
SET owner_user_id = (
    SELECT fallback_user_id FROM (
        SELECT MIN(user_id) AS fallback_user_id FROM sys_user
    ) fallback_owner
)
WHERE owner_user_id IS NULL;

ALTER TABLE knowledge_bases
    MODIFY COLUMN owner_user_id BIGINT NOT NULL;

CREATE INDEX idx_knowledge_bases_owner_user_id ON knowledge_bases(owner_user_id);
