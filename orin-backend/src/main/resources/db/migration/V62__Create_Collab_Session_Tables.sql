CREATE TABLE IF NOT EXISTS `collab_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` VARCHAR(64) NOT NULL UNIQUE,
    `title` VARCHAR(255),
    `status` VARCHAR(30) DEFAULT 'ACTIVE',
    `main_agent_policy` VARCHAR(30) DEFAULT 'STATIC_THEN_BID',
    `quality_threshold` DOUBLE DEFAULT 0.82,
    `max_critique_rounds` INT DEFAULT 3,
    `draft_parallelism` INT DEFAULT 4,
    `main_agent_static_default` VARCHAR(255),
    `bid_whitelist` JSON,
    `created_by` VARCHAR(255),
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    INDEX idx_collab_session_created_by (`created_by`),
    INDEX idx_collab_session_updated_at (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `collab_turn` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `turn_id` VARCHAR(64) NOT NULL UNIQUE,
    `session_id` VARCHAR(64) NOT NULL,
    `package_id` VARCHAR(64),
    `trace_id` VARCHAR(64),
    `user_message` TEXT,
    `status` VARCHAR(30) DEFAULT 'RUNNING',
    `error_message` TEXT,
    `selection_meta` JSON,
    `started_at` DATETIME NOT NULL,
    `completed_at` DATETIME,
    INDEX idx_collab_turn_session (`session_id`),
    INDEX idx_collab_turn_status (`status`),
    INDEX idx_collab_turn_started (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `collab_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` VARCHAR(64) NOT NULL,
    `turn_id` VARCHAR(64),
    `role` VARCHAR(20) NOT NULL,
    `stage` VARCHAR(50),
    `content` TEXT,
    `metadata` JSON,
    `created_at` DATETIME NOT NULL,
    INDEX idx_collab_message_session (`session_id`),
    INDEX idx_collab_message_turn (`turn_id`),
    INDEX idx_collab_message_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
