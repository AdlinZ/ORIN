-- MCP 服务管理表
CREATE TABLE IF NOT EXISTS mcp_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'STDIO',
    command TEXT,
    url VARCHAR(500),
    env_vars TEXT,
    description TEXT,
    status VARCHAR(20) DEFAULT 'DISCONNECTED',
    last_connected DATETIME,
    last_error TEXT,
    health_score INT DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_type (type),
    INDEX idx_status (status)
);