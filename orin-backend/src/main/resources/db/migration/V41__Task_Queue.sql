-- 任务队列表
CREATE TABLE IF NOT EXISTS `task_queue` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL,
    `task_type` VARCHAR(50) DEFAULT 'GENERAL',
    `content` TEXT,
    `priority` INT DEFAULT 5,
    `status` VARCHAR(50) DEFAULT 'PENDING',
    `result` TEXT,
    `error_message` TEXT,
    `retry_count` INT DEFAULT 0,
    `max_retry` INT DEFAULT 3,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `created_by` VARCHAR(255),
    `executor_node` VARCHAR(255),
    INDEX idx_status (`status`),
    INDEX idx_priority (`priority`),
    INDEX idx_created_by (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
