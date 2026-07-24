-- V98: Agent Endpoints — F05 发布 API / MCP
-- 将已冻结的 AgentVersion 发布为 REST API 或 MCP Server 端点。

CREATE TABLE IF NOT EXISTS agent_endpoints (
    id               VARCHAR(40)  NOT NULL,
    agent_id         VARCHAR(50)  NOT NULL COMMENT 'FK → agent_metadata.agent_id',
    agent_version_id VARCHAR(40)  NOT NULL COMMENT 'FK → agent_versions.id',
    name             VARCHAR(120) NOT NULL,
    endpoint_type    VARCHAR(20)  NOT NULL DEFAULT 'REST_API' COMMENT 'REST_API / MCP_SERVER',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE / ERROR',
    endpoint_path    VARCHAR(255) NOT NULL COMMENT 'URL 路径，如 /api/endpoints/ep_xxx',
    config           JSON         NULL     COMMENT '限流、鉴权等配置',
    description      VARCHAR(500) NULL,
    created_by       VARCHAR(120) NOT NULL,
    created_at       BIGINT       NOT NULL,
    updated_at       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_endpoint_path (endpoint_path),
    INDEX idx_agent_endpoints_agent (agent_id),
    INDEX idx_agent_endpoints_type_status (endpoint_type, status),
    CONSTRAINT fk_agent_endpoints_agent FOREIGN KEY (agent_id) REFERENCES agent_metadata(agent_id),
    CONSTRAINT fk_agent_endpoints_version FOREIGN KEY (agent_version_id) REFERENCES agent_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
