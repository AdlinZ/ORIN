-- V9__Add_ZeroClaw_Integration.sql
-- ZeroClaw 轻量化 Agent 集成模块

-- ZeroClaw 配置表
CREATE TABLE IF NOT EXISTS zeroclaw_configs (
    id VARCHAR(36) PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    endpoint_url VARCHAR(500) NOT NULL,
    access_token VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enable_analysis BOOLEAN DEFAULT TRUE,
    enable_self_healing BOOLEAN DEFAULT TRUE,
    heartbeat_interval INT DEFAULT 60,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- ZeroClaw 分析报告表
CREATE TABLE IF NOT EXISTS zeroclaw_analysis_reports (
    id VARCHAR(36) PRIMARY KEY,
    agent_id VARCHAR(36),
    report_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    details LONGTEXT,
    root_cause VARCHAR(1000),
    recommendations VARCHAR(1000),
    severity VARCHAR(20),
    analysis_start TIMESTAMP,
    analysis_end TIMESTAMP,
    data_start_time BIGINT,
    data_end_time BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ZeroClaw 主动维护操作记录表
CREATE TABLE IF NOT EXISTS zeroclaw_self_healing_logs (
    id VARCHAR(36) PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    target_resource VARCHAR(200),
    trigger_reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execution_details LONGTEXT,
    error_message TEXT,
    before_snapshot LONGTEXT,
    after_snapshot LONGTEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    auto_executed BOOLEAN DEFAULT TRUE,
    executed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_zeroclaw_configs_enabled ON zeroclaw_configs(enabled);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_reports_agent_id ON zeroclaw_analysis_reports(agent_id);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_reports_type ON zeroclaw_analysis_reports(report_type);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_reports_created ON zeroclaw_analysis_reports(created_at);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_logs_action_type ON zeroclaw_self_healing_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_logs_status ON zeroclaw_self_healing_logs(status);
CREATE INDEX IF NOT EXISTS idx_zeroclaw_logs_created ON zeroclaw_self_healing_logs(created_at);
