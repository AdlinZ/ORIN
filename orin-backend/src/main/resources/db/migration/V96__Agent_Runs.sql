-- V96: Agent Runs — F03 执行记录
-- Run = 已冻结 AgentVersion + Runner → 执行实例
-- 状态机：QUEUED → LEASED → RUNNING → COMPLETED / FAILED / CANCELLED

CREATE TABLE IF NOT EXISTS runs (
    id              VARCHAR(40)  NOT NULL,
    agent_id        VARCHAR(50)  NOT NULL COMMENT 'FK → agent_metadata.agent_id',
    agent_version_id VARCHAR(40) NOT NULL COMMENT 'FK → agent_versions.id',
    runner_id       VARCHAR(40)  NULL     COMMENT 'FK → runners.id，LEASE 后赋值',
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    config_snapshot JSON         NOT NULL COMMENT 'AgentVersion.config_snapshot 副本',
    input           TEXT         NULL     COMMENT '用户输入 / prompt',
    output          TEXT         NULL     COMMENT '执行结果',
    error_message   TEXT         NULL,
    lease_token     VARCHAR(128) NULL     COMMENT 'lease 验证令牌',
    leased_at       BIGINT       NULL     COMMENT 'epoch millis',
    lease_expires_at BIGINT      NULL     COMMENT 'epoch millis',
    started_at      BIGINT       NULL     COMMENT 'epoch millis',
    completed_at    BIGINT       NULL     COMMENT 'epoch millis',
    created_by      VARCHAR(120) NOT NULL,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    original_run_id VARCHAR(40)  NULL     COMMENT 'retry 来源 Run id',
    PRIMARY KEY (id),
    INDEX idx_runs_agent_id (agent_id),
    INDEX idx_runs_agent_version_id (agent_version_id),
    INDEX idx_runs_runner_id (runner_id),
    INDEX idx_runs_status (status),
    INDEX idx_runs_status_created (status, created_at),
    INDEX idx_runs_created_by (created_by),
    CONSTRAINT fk_runs_agent FOREIGN KEY (agent_id) REFERENCES agent_metadata(agent_id),
    CONSTRAINT fk_runs_agent_version FOREIGN KEY (agent_version_id) REFERENCES agent_versions(id),
    CONSTRAINT fk_runs_runner FOREIGN KEY (runner_id) REFERENCES runners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
