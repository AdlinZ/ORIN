-- Docker schema snapshot now covers V1..V87. This migration keeps fresh
-- snapshot databases and older drifted databases converged without rewriting
-- historical migrations.

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_metadata ADD COLUMN owner_user_id BIGINT NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND column_name = 'owner_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_metadata ADD COLUMN mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND column_name = 'mcp_exposed'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_agent_metadata_owner_user_id ON agent_metadata(owner_user_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND index_name = 'idx_agent_metadata_owner_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_agent_metadata_owner_mcp_exposed ON agent_metadata(owner_user_id, mcp_exposed)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND index_name = 'idx_agent_metadata_owner_mcp_exposed'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO gateway_routes (
    name, path_pattern, method, service_id, target_url, strip_prefix, rewrite_path,
    timeout_ms, retry_count, load_balance, auth_required, rate_limit_policy_id,
    circuit_breaker_policy_id, retry_policy_id, priority, enabled, description,
    created_at, updated_at
)
SELECT route_name, path_pattern, 'ALL', NULL, NULL, FALSE, NULL,
       300000, 0, 'ROUND_ROBIN', TRUE,
       (SELECT id FROM gateway_rate_limit_policies WHERE name = 'default-v1-local-rate-limit'),
       NULL, NULL, 1000, TRUE, description, NOW(), NOW()
FROM (
    SELECT 'v1-mcp-streamable-http' AS route_name, '/v1/mcp' AS path_pattern, 'Protected MCP Streamable HTTP endpoint' AS description
    UNION ALL SELECT 'v1-mcp-streamable-http-wildcard', '/v1/mcp/**', 'Protected MCP Streamable HTTP endpoint subtree'
) routes
WHERE NOT EXISTS (
    SELECT 1 FROM gateway_routes WHERE name = routes.route_name
);

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE workflows ADD COLUMN owner_user_id BIGINT NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'workflows'
      AND column_name = 'owner_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE workflows ADD COLUMN mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'workflows'
      AND column_name = 'mcp_exposed'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE workflows w
JOIN sys_user u ON w.created_by IS NOT NULL
    AND (
        w.created_by COLLATE utf8mb4_unicode_ci = CAST(u.user_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
        OR w.created_by COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci
    )
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

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_workflows_owner_mcp_exposed ON workflows(owner_user_id, mcp_exposed)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'workflows'
      AND index_name = 'idx_workflows_owner_mcp_exposed'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE gateway_secrets MODIFY COLUMN secret_type VARCHAR(40) NOT NULL;
