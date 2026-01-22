-- ============================================================
-- Skill-Hub 系统数据库表结构
-- 版本: 1.0
-- 创建时间: 2026-01-22
-- ============================================================

-- 1. 技能注册表
CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL COMMENT '技能名称',
    skill_type VARCHAR(50) NOT NULL COMMENT '技能类型: API, KNOWLEDGE, COMPOSITE',
    description TEXT COMMENT '技能描述',
    
    -- MCP 标准字段
    mcp_metadata JSON COMMENT 'MCP 标准元数据',
    skill_md_content TEXT COMMENT '生成的 SKILL.md 内容',
    
    -- API 类型技能配置
    api_endpoint VARCHAR(500) COMMENT 'API 端点',
    api_method VARCHAR(10) COMMENT 'HTTP 方法',
    api_headers JSON COMMENT 'API 请求头',
    
    -- 知识库类型技能配置
    knowledge_config_id BIGINT COMMENT '关联的知识库配置 ID',
    
    -- 复合技能配置 (引用其他工作流)
    workflow_id BIGINT COMMENT '关联的工作流 ID',
    
    -- 外部平台引用
    external_platform VARCHAR(50) COMMENT '外部平台: n8n, dify, coze',
    external_reference VARCHAR(500) COMMENT '外部平台引用 (Workflow ID / API Path)',
    
    -- 输入输出定义
    input_schema JSON COMMENT '输入参数 JSON Schema',
    output_schema JSON COMMENT '输出参数 JSON Schema',
    
    -- 状态和元信息
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, INACTIVE, DEPRECATED',
    version VARCHAR(20) DEFAULT '1.0.0' COMMENT '版本号',
    created_by VARCHAR(100) COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_skill_name (skill_name),
    INDEX idx_skill_type (skill_type),
    INDEX idx_status (status),
    INDEX idx_external_platform (external_platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能注册表';

-- 2. 工作流定义表
CREATE TABLE IF NOT EXISTS workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '工作流描述',
    
    -- 工作流配置
    workflow_type VARCHAR(50) DEFAULT 'SEQUENTIAL' COMMENT '类型: SEQUENTIAL, PARALLEL, DAG',
    workflow_definition JSON NOT NULL COMMENT '工作流定义 (DAG 结构)',
    
    -- 全局配置
    timeout_seconds INT DEFAULT 300 COMMENT '超时时间(秒)',
    retry_policy JSON COMMENT '重试策略配置',
    
    -- 状态和元信息
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT, ACTIVE, ARCHIVED',
    version VARCHAR(20) DEFAULT '1.0.0' COMMENT '版本号',
    created_by VARCHAR(100) COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_workflow_name (workflow_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- 3. 工作流步骤表
CREATE TABLE IF NOT EXISTS workflow_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL COMMENT '所属工作流 ID',
    step_order INT NOT NULL COMMENT '步骤顺序',
    step_name VARCHAR(100) NOT NULL COMMENT '步骤名称',
    
    -- 关联技能
    skill_id BIGINT NOT NULL COMMENT '执行的技能 ID',
    
    -- 参数映射
    input_mapping JSON COMMENT '输入参数映射 (从上游步骤或工作流输入)',
    output_mapping JSON COMMENT '输出参数映射 (传递给下游步骤)',
    
    -- 条件执行
    condition_expression VARCHAR(500) COMMENT '条件表达式 (SpEL)',
    
    -- 依赖关系
    depends_on JSON COMMENT '依赖的步骤 ID 列表',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_step_order (workflow_id, step_order),
    CONSTRAINT fk_workflow_steps_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_steps_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流步骤表';

-- 4. 工作流执行实例表
CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL COMMENT '工作流 ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '全链路追踪 ID',
    
    -- 执行状态
    status VARCHAR(20) NOT NULL COMMENT '状态: RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED',
    
    -- 输入输出
    input_data JSON COMMENT '工作流输入数据',
    output_data JSON COMMENT '工作流输出数据',
    
    -- 执行信息
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    completed_at TIMESTAMP NULL COMMENT '完成时间',
    duration_ms BIGINT COMMENT '执行时长(毫秒)',
    
    -- 错误信息
    error_message TEXT COMMENT '错误信息',
    error_stack TEXT COMMENT '错误堆栈',
    
    -- 触发信息
    triggered_by VARCHAR(100) COMMENT '触发人/系统',
    trigger_source VARCHAR(50) COMMENT '触发来源: API, SCHEDULE, WEBHOOK',
    
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at),
    CONSTRAINT fk_workflow_instances_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行实例表';

-- 5. 全链路追踪表
CREATE TABLE IF NOT EXISTS workflow_traces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL COMMENT '追踪 ID',
    instance_id BIGINT NOT NULL COMMENT '工作流实例 ID',
    
    -- 步骤信息
    step_id BIGINT COMMENT '步骤 ID',
    step_name VARCHAR(100) COMMENT '步骤名称',
    skill_id BIGINT COMMENT '技能 ID',
    skill_name VARCHAR(100) COMMENT '技能名称',
    
    -- 执行状态
    status VARCHAR(20) NOT NULL COMMENT '状态: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED',
    
    -- 执行详情
    input_data JSON COMMENT '步骤输入数据',
    output_data JSON COMMENT '步骤输出数据',
    
    -- 时间信息
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    completed_at TIMESTAMP NULL COMMENT '完成时间',
    duration_ms BIGINT COMMENT '执行时长(毫秒)',
    
    -- 错误信息
    error_code VARCHAR(50) COMMENT '错误代码',
    error_message TEXT COMMENT '错误信息',
    error_details JSON COMMENT '错误详情',
    
    -- 性能指标
    cpu_usage DECIMAL(5,2) COMMENT 'CPU 使用率(%)',
    memory_usage BIGINT COMMENT '内存使用(字节)',
    
    INDEX idx_trace_id (trace_id),
    INDEX idx_instance_id (instance_id),
    INDEX idx_step_id (step_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at),
    CONSTRAINT fk_workflow_traces_instance FOREIGN KEY (instance_id) REFERENCES workflow_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_traces_step FOREIGN KEY (step_id) REFERENCES workflow_steps(id) ON DELETE SET NULL,
    CONSTRAINT fk_workflow_traces_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全链路追踪表';

-- 6. 知识库配置表
CREATE TABLE IF NOT EXISTS knowledge_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL COMMENT '配置名称',
    
    -- 向量数据库配置
    vector_db_type VARCHAR(50) NOT NULL COMMENT '向量数据库类型: MILVUS, PINECONE, QDRANT',
    vector_db_host VARCHAR(200) COMMENT '数据库主机',
    vector_db_port INT COMMENT '数据库端口',
    vector_db_api_key VARCHAR(500) COMMENT 'API 密钥',
    
    -- 集合配置
    collection_name VARCHAR(100) NOT NULL COMMENT '集合名称',
    embedding_model VARCHAR(100) COMMENT '嵌入模型',
    dimension INT COMMENT '向量维度',
    
    -- RAG 配置
    top_k INT DEFAULT 5 COMMENT '检索 Top-K 结果数',
    similarity_threshold DECIMAL(3,2) DEFAULT 0.70 COMMENT '相似度阈值',
    
    -- 状态
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, INACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_config_name (config_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库配置表';

-- 7. 平台适配器配置表
CREATE TABLE IF NOT EXISTS platform_adapters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_name VARCHAR(50) NOT NULL COMMENT '平台名称: n8n, dify, coze',
    adapter_name VARCHAR(100) NOT NULL COMMENT '适配器名称',
    
    -- 平台连接配置
    base_url VARCHAR(500) NOT NULL COMMENT '平台基础 URL',
    api_key VARCHAR(500) COMMENT 'API 密钥',
    auth_type VARCHAR(50) COMMENT '认证类型: BEARER, API_KEY, OAUTH',
    auth_config JSON COMMENT '认证配置',
    
    -- 适配器配置
    adapter_config JSON COMMENT '适配器特定配置',
    
    -- 状态
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, INACTIVE',
    last_health_check TIMESTAMP NULL COMMENT '最后健康检查时间',
    health_status VARCHAR(20) COMMENT '健康状态: HEALTHY, UNHEALTHY',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_platform_name (platform_name),
    INDEX idx_status (status),
    UNIQUE KEY uk_platform_adapter (platform_name, adapter_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台适配器配置表';

-- 添加外键约束 (延迟添加以避免循环依赖)
ALTER TABLE skills 
    ADD CONSTRAINT fk_skills_knowledge_config 
    FOREIGN KEY (knowledge_config_id) REFERENCES knowledge_configs(id) ON DELETE SET NULL;

ALTER TABLE skills 
    ADD CONSTRAINT fk_skills_workflow 
    FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE SET NULL;
