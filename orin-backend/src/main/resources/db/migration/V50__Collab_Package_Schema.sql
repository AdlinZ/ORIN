-- 多智能体协作任务包表
CREATE TABLE IF NOT EXISTS `collab_package` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `package_id` VARCHAR(64) NOT NULL UNIQUE,
    `root_task_id` BIGINT,
    `intent` TEXT NOT NULL,
    `intent_category` VARCHAR(50),
    `intent_priority` VARCHAR(20),
    `intent_complexity` VARCHAR(30),
    `need_review` BOOLEAN DEFAULT FALSE,
    `need_consensus` BOOLEAN DEFAULT FALSE,
    `collaboration_mode` VARCHAR(30) DEFAULT 'SEQUENTIAL',
    `shared_context` JSON,
    `strategy` JSON,
    `status` VARCHAR(30) DEFAULT 'PLANNING',
    `result` TEXT,
    `error_message` TEXT,
    `trace_id` VARCHAR(64),
    `created_by` VARCHAR(255),
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `completed_at` DATETIME,
    `timeout_at` DATETIME,
    INDEX idx_package_id (`package_id`),
    INDEX idx_status (`status`),
    INDEX idx_trace_id (`trace_id`),
    INDEX idx_created_by (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务分解表
CREATE TABLE IF NOT EXISTS `collab_subtask` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `package_id` VARCHAR(64) NOT NULL,
    `sub_task_id` VARCHAR(64) NOT NULL,
    `description` TEXT NOT NULL,
    `expected_role` VARCHAR(30),
    `depends_on` JSON,
    `input_data` JSON,
    `output_data` JSON,
    `confidence` DOUBLE,
    `status` VARCHAR(30) DEFAULT 'PENDING',
    `result` TEXT,
    `executed_by` VARCHAR(255),
    `retry_count` INT DEFAULT 0,
    `error_message` TEXT,
    `started_at` DATETIME,
    `completed_at` DATETIME,
    INDEX idx_package_id (`package_id`),
    INDEX idx_status (`status`),
    UNIQUE KEY uk_subtask (`package_id`, `sub_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 参与角色表
CREATE TABLE IF NOT EXISTS `collab_participant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `package_id` VARCHAR(64) NOT NULL,
    `role_type` VARCHAR(30) NOT NULL,
    `agent_id` VARCHAR(255) NOT NULL,
    `agent_name` VARCHAR(255),
    `capabilities` JSON,
    `cost_level` VARCHAR(20),
    `status` VARCHAR(20) DEFAULT 'IDLE',
    INDEX idx_package_id (`package_id`),
    INDEX idx_agent_id (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 协作事件日志表
CREATE TABLE IF NOT EXISTS `collab_event_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `package_id` VARCHAR(64) NOT NULL,
    `event_type` VARCHAR(50) NOT NULL,
    `sub_task_id` VARCHAR(64),
    `agent_id` VARCHAR(255),
    `event_data` JSON,
    `trace_id` VARCHAR(64),
    `created_at` DATETIME NOT NULL,
    INDEX idx_package_id (`package_id`),
    INDEX idx_event_type (`event_type`),
    INDEX idx_trace_id (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;