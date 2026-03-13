-- 知识库同步记录表
CREATE TABLE IF NOT EXISTS knowledge_sync_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(64),
    sync_type VARCHAR(20), -- FULL, INCREMENTAL
    status VARCHAR(20), -- RUNNING, COMPLETED, FAILED
    start_time DATETIME,
    end_time DATETIME,
    added_count INT DEFAULT 0,
    updated_count INT DEFAULT 0,
    deleted_count INT DEFAULT 0,
    error_message VARCHAR(1000),
    details TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_id (agent_id),
    INDEX idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
