-- Seed default LOCAL routes for the OpenAI-compatible /v1 surface.
-- LOCAL routes have no service_id and no target_url: the gateway filter enforces
-- ACL/auth/rate-limit/audit, then passes the request to local Spring controllers.

INSERT INTO gateway_rate_limit_policies (
    name, dimension, capacity, window_seconds, burst, description, enabled, created_at, updated_at
)
SELECT
    'default-v1-local-rate-limit',
    'API_KEY',
    120,
    60,
    20,
    'Default rate limit for local /v1 unified gateway routes',
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM gateway_rate_limit_policies WHERE name = 'default-v1-local-rate-limit'
);

INSERT INTO gateway_routes (
    name, path_pattern, method, service_id, target_url, strip_prefix, rewrite_path,
    timeout_ms, retry_count, load_balance, auth_required, rate_limit_policy_id,
    circuit_breaker_policy_id, retry_policy_id, priority, enabled, description,
    created_at, updated_at
)
SELECT route_name, path_pattern, method, NULL, NULL, FALSE, NULL,
       30000, 0, 'ROUND_ROBIN', auth_required,
       (SELECT id FROM gateway_rate_limit_policies WHERE name = 'default-v1-local-rate-limit'),
       NULL, NULL, 1000, TRUE, description, NOW(), NOW()
FROM (
    SELECT 'v1-api-index' AS route_name, '/v1' AS path_pattern, 'GET' AS method, FALSE AS auth_required, 'Public /v1 API index' AS description
    UNION ALL SELECT 'v1-health', '/v1/health', 'GET', FALSE, 'Public /v1 health check'
    UNION ALL SELECT 'v1-models', '/v1/models', 'GET', FALSE, 'Public /v1 model list'
    UNION ALL SELECT 'v1-providers', '/v1/providers', 'GET', FALSE, 'Public /v1 provider list'
    UNION ALL SELECT 'v1-docs', '/v1/docs', 'GET', FALSE, 'Public /v1 docs entry'
    UNION ALL SELECT 'v1-capabilities', '/v1/capabilities', 'GET', FALSE, 'Public /v1 capabilities'
    UNION ALL SELECT 'v1-chat-completions', '/v1/chat/completions', 'POST', TRUE, 'Protected OpenAI-compatible chat completions'
    UNION ALL SELECT 'v1-embeddings', '/v1/embeddings', 'POST', TRUE, 'Protected OpenAI-compatible embeddings'
    UNION ALL SELECT 'v1-workflows', '/v1/workflows/**', 'ALL', TRUE, 'Protected workflow API'
    UNION ALL SELECT 'v1-tasks', '/v1/tasks/**', 'ALL', TRUE, 'Protected task API'
) default_routes
WHERE NOT EXISTS (
    SELECT 1 FROM gateway_routes WHERE name = default_routes.route_name
);
