-- 系统消息表
CREATE TABLE IF NOT EXISTS `system_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT,
    `type` VARCHAR(50) DEFAULT 'INFO',
    `receiver_id` VARCHAR(255),
    `sender_id` VARCHAR(255),
    `read` TINYINT(1) DEFAULT 0,
    `expire_at` DATETIME,
    `created_at` DATETIME NOT NULL,
    INDEX idx_receiver_id (`receiver_id`),
    INDEX idx_created_at (`created_at`),
    INDEX idx_read (`read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
