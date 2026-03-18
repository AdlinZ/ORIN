-- 多智能体协作任务表
CREATE TABLE IF NOT EXISTS `collab_task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL,
    `description` TEXT,
    `status` VARCHAR(50) DEFAULT 'PENDING',
    `task_type` VARCHAR(50) DEFAULT 'SEQUENTIAL',
    `current_agent_index` INT DEFAULT 0,
    `result` TEXT,
    `error_message` TEXT,
    `created_by` VARCHAR(255),
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `completed_at` DATETIME,
    INDEX idx_status (`status`),
    INDEX idx_created_by (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 多智能体协作任务参与的 Agent 表
CREATE TABLE IF NOT EXISTS `collab_task_agents` (
    `task_id` BIGINT NOT NULL,
    `agent_id` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`task_id`, `agent_id`),
    FOREIGN KEY (`task_id`) REFERENCES `collab_task`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
