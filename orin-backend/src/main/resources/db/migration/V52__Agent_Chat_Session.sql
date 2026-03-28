-- 智能体对话会话表
CREATE TABLE IF NOT EXISTS `agent_chat_session` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `session_id` VARCHAR(255) NOT NULL,
  `agent_id` VARCHAR(255) NOT NULL,
  `title` VARCHAR(255),
  `history` TEXT,
  `created_at` DATETIME,
  `updated_at` DATETIME,
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;