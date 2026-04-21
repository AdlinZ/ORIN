CREATE TABLE IF NOT EXISTS tool_catalog (
    tool_id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    schema_json JSON NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    runtime_mode VARCHAR(32) NOT NULL DEFAULT 'context_only',
    health_status VARCHAR(32) NULL,
    version VARCHAR(32) NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_tool_binding (
    agent_id VARCHAR(128) PRIMARY KEY,
    tool_ids JSON NULL,
    kb_ids JSON NULL,
    skill_ids JSON NULL,
    mcp_ids JSON NULL,
    enable_suggestions TINYINT(1) NOT NULL DEFAULT 1,
    show_retrieved_context TINYINT(1) NOT NULL DEFAULT 1,
    auto_rename_session TINYINT(1) NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS session_tool_binding (
    session_id VARCHAR(128) PRIMARY KEY,
    agent_id VARCHAR(128) NOT NULL,
    tool_ids JSON NULL,
    kb_ids JSON NULL,
    skill_ids JSON NULL,
    mcp_ids JSON NULL,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_tool_binding_agent_id (agent_id)
);

CREATE TABLE IF NOT EXISTS tool_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(128) NULL,
    agent_id VARCHAR(128) NULL,
    tool_id VARCHAR(128) NOT NULL,
    runtime_mode VARCHAR(32) NULL,
    success TINYINT(1) NOT NULL DEFAULT 1,
    error_code VARCHAR(128) NULL,
    latency_ms BIGINT NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_execution_log_session_id (session_id),
    INDEX idx_tool_execution_log_agent_id (agent_id),
    INDEX idx_tool_execution_log_tool_id (tool_id),
    INDEX idx_tool_execution_log_created_at (created_at)
);
