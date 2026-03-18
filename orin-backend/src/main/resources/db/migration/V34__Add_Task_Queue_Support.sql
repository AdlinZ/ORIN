-- V34__Add_Task_Queue_Support.sql
-- Task Queue Management - Priority and Queue Governance

CREATE TABLE IF NOT EXISTS task_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    workflow_id BIGINT NOT NULL,
    workflow_instance_id BIGINT,
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    input_data JSON,
    output_data JSON,
    triggered_by VARCHAR(100),
    trigger_source VARCHAR(50),
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    next_retry_at DATETIME,
    error_message TEXT,
    error_stack TEXT,
    dead_letter_reason TEXT,
    queued_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    duration_ms BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    INDEX idx_task_id (task_id),
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_workflow_instance_id (workflow_instance_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_created_at (created_at),
    INDEX idx_status_priority (status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
