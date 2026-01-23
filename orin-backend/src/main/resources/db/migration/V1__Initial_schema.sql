-- ============================================
-- ORIN Database Initial Schema
-- Version: 1.0.0 (Consolidated Baseline)
-- Description: Initial database schema for ORIN system
-- Matches JPA Entities: SysUser, SysRole, AgentMetadata, etc.
-- ============================================

-- ============================================
-- 1. System Management (sys_user, sys_role)
-- ============================================

CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    email VARCHAR(255),
    avatar VARCHAR(255),
    status VARCHAR(50) DEFAULT 'ENABLED',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 2. Agent Management
-- ============================================

CREATE TABLE IF NOT EXISTS agent_metadata (
    agent_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    icon VARCHAR(255),
    mode VARCHAR(50),
    model_name VARCHAR(255),
    provider_type VARCHAR(50),
    temperature DOUBLE,
    top_p DOUBLE,
    max_tokens INT,
    system_prompt TEXT,
    sync_time TIMESTAMP,
    INDEX idx_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_access_profiles (
    agent_id VARCHAR(255) PRIMARY KEY,
    endpoint_url VARCHAR(500),
    api_key VARCHAR(500),
    dataset_api_key VARCHAR(500),
    connection_status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_connection_status (connection_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_health_status (
    agent_id VARCHAR(255) PRIMARY KEY,
    agent_name VARCHAR(255),
    health_score INT,
    status VARCHAR(50),
    last_heartbeat BIGINT,
    provider_type VARCHAR(50),
    mode VARCHAR(50),
    model_name VARCHAR(255),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_versions (
    id VARCHAR(255) PRIMARY KEY,
    agent_id VARCHAR(50) NOT NULL,
    version_number INT NOT NULL,
    version_tag VARCHAR(50),
    config_snapshot JSON NOT NULL,
    change_description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_agent_id (agent_id),
    UNIQUE KEY uk_agent_version (agent_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 3. Knowledge Base (Dify Integration)
-- ============================================

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(50) DEFAULT 'DOCUMENT',
    description VARCHAR(255),
    doc_count INT,
    total_size_mb DOUBLE,
    status VARCHAR(255),
    source_agent_id VARCHAR(255),
    sync_time TIMESTAMP,
    created_at TIMESTAMP,
    configuration TEXT,
    INDEX idx_source_agent_id (source_agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kb_documents (
    id VARCHAR(255) PRIMARY KEY,
    knowledge_base_id VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    file_size BIGINT,
    storage_path VARCHAR(500),
    content_preview TEXT,
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_index_id VARCHAR(100),
    chunk_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP,
    uploaded_by VARCHAR(100),
    metadata TEXT,
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_vector_status (vector_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 4. Model Config
-- ============================================

CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_url VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    api_path VARCHAR(255),
    timeout INT,
    llama_factory_path VARCHAR(255),
    llama_factory_webui VARCHAR(255),
    model_save_path VARCHAR(255),
    remark TEXT,
    dify_endpoint VARCHAR(255),
    dify_api_key VARCHAR(255),
    silicon_flow_endpoint VARCHAR(255),
    silicon_flow_api_key VARCHAR(255),
    silicon_flow_model VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 5. API Keys & Audit
-- ============================================

CREATE TABLE IF NOT EXISTS api_keys (
    id VARCHAR(255) PRIMARY KEY,
    key_hash VARCHAR(256) NOT NULL UNIQUE,
    key_prefix VARCHAR(20) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    permissions TEXT,
    rate_limit_per_minute INT DEFAULT 100,
    rate_limit_per_day INT DEFAULT 10000,
    monthly_token_quota BIGINT DEFAULT 1000000,
    used_tokens BIGINT DEFAULT 0,
    expires_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_key_hash (key_hash),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    api_key_id VARCHAR(255),
    provider_id VARCHAR(255),
    conversation_id VARCHAR(100),
    workflow_id VARCHAR(255),
    provider_type VARCHAR(255),
    endpoint VARCHAR(200) NOT NULL,
    method VARCHAR(10),
    model VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    request_params TEXT,
    response_content TEXT,
    status_code INT,
    response_time BIGINT,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    estimated_cost DOUBLE DEFAULT 0.0,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================
-- 6. Skill & Workflow Hub (Consolidated from V1.0)
-- ============================================

-- Knowledge Configs (For Skills)
CREATE TABLE IF NOT EXISTS knowledge_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    vector_db_type VARCHAR(50) NOT NULL,
    vector_db_host VARCHAR(200),
    vector_db_port INT,
    vector_db_api_key VARCHAR(500),
    collection_name VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(100),
    dimension INT,
    top_k INT DEFAULT 5,
    similarity_threshold DECIMAL(3,2) DEFAULT 0.70,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_name (config_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库配置表';

-- Workflows
CREATE TABLE IF NOT EXISTS workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_name VARCHAR(100) NOT NULL,
    description TEXT,
    workflow_type VARCHAR(50) DEFAULT 'SEQUENTIAL',
    workflow_definition JSON NOT NULL,
    timeout_seconds INT DEFAULT 300,
    retry_policy JSON,
    status VARCHAR(20) DEFAULT 'DRAFT',
    version VARCHAR(20) DEFAULT '1.0.0',
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_name (workflow_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- Skills
CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL,
    skill_type VARCHAR(50) NOT NULL,
    description TEXT,
    mcp_metadata JSON,
    skill_md_content TEXT,
    api_endpoint VARCHAR(500),
    api_method VARCHAR(10),
    api_headers JSON,
    knowledge_config_id BIGINT,
    workflow_id BIGINT,
    external_platform VARCHAR(50),
    external_reference VARCHAR(500),
    input_schema JSON,
    output_schema JSON,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    version VARCHAR(20) DEFAULT '1.0.0',
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_skill_name (skill_name),
    CONSTRAINT fk_skills_knowledge_config FOREIGN KEY (knowledge_config_id) REFERENCES knowledge_configs(id) ON DELETE SET NULL,
    CONSTRAINT fk_skills_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能注册表';

-- Workflow Steps (Depends on Skills)
CREATE TABLE IF NOT EXISTS workflow_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    skill_id BIGINT NOT NULL,
    input_mapping JSON,
    output_mapping JSON,
    condition_expression VARCHAR(500),
    depends_on JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_id (workflow_id),
    CONSTRAINT fk_workflow_steps_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_steps_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流步骤表';

-- Workflow Instances (Runtime)
CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_data JSON,
    output_data JSON,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    duration_ms BIGINT,
    error_message TEXT,
    error_stack TEXT,
    triggered_by VARCHAR(100),
    trigger_source VARCHAR(50),
    INDEX idx_workflow_id (workflow_id),
    CONSTRAINT fk_workflow_instances_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行实例表';

-- Workflow Traces (Runtime)
CREATE TABLE IF NOT EXISTS workflow_traces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    instance_id BIGINT NOT NULL,
    step_id BIGINT,
    step_name VARCHAR(100),
    skill_id BIGINT,
    skill_name VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    input_data JSON,
    output_data JSON,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    duration_ms BIGINT,
    error_code VARCHAR(50),
    error_message TEXT,
    error_details JSON,
    cpu_usage DECIMAL(5,2),
    memory_usage BIGINT,
    INDEX idx_trace_id (trace_id),
    CONSTRAINT fk_workflow_traces_instance FOREIGN KEY (instance_id) REFERENCES workflow_instances(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全链路追踪表';

-- Platform Adapters
CREATE TABLE IF NOT EXISTS platform_adapters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_name VARCHAR(50) NOT NULL,
    adapter_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    auth_type VARCHAR(50),
    auth_config JSON,
    adapter_config JSON,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_health_check TIMESTAMP NULL,
    health_status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_adapter (platform_name, adapter_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台适配器配置表';

-- ============================================
-- 7. Default Data
-- ============================================

-- Insert default roles
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ROLE_ADMIN', 'Administrator', 'Administrator with full access'),
('ROLE_USER', 'User', 'Standard user')
ON DUPLICATE KEY UPDATE role_name=role_name;

-- Insert default admin user (password: admin123)
-- Using ID 1 for relationships
INSERT INTO sys_user (username, password, nickname, email, status) VALUES
('admin', '$2a$10$SInbX6pqbyB40ZDCLYJgl..PihHe2h3KXgZFcfoZ7X7sQmai3vm2.', 'Admin', 'admin@orin.local', 'ENABLED')
ON DUPLICATE KEY UPDATE username=username;

-- Assign ROLE_ADMIN to admin user (assuming IDs are 1 since auto-increment and clean insert)
-- Using subqueries to be safe
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN'
ON DUPLICATE KEY UPDATE id=id;
