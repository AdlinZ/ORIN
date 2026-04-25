-- Playground module tables
CREATE TABLE IF NOT EXISTS playground_workflows (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(64) NOT NULL,
    specialist_agent_ids_json TEXT,
    router_prompt TEXT,
    finalizer_enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS playground_conversations (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    title VARCHAR(240),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS playground_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    agent_name VARCHAR(120),
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS playground_runs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    trace_id VARCHAR(64),
    user_id VARCHAR(64),
    workflow_type VARCHAR(64),
    status VARCHAR(32),
    user_input TEXT,
    assistant_message TEXT,
    trace_json TEXT,
    graph_json TEXT,
    artifacts_json TEXT,
    duration_ms BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);