-- 用户设置表
CREATE TABLE IF NOT EXISTS `user_settings` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` VARCHAR(255) NOT NULL,
    `setting_type` VARCHAR(100) DEFAULT 'general',
    `setting_key` VARCHAR(255) NOT NULL,
    `setting_value` TEXT,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    UNIQUE KEY `uk_user_key` (`user_id`, `setting_key`),
    INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
