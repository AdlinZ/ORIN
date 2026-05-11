-- Protected LOCAL routes for ORIN's MCP Streamable HTTP endpoint.
-- The gateway filter validates GatewaySecret before Spring handles /v1/mcp.

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
