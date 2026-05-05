-- Notification center per-user read/dismiss state.
CREATE TABLE IF NOT EXISTS system_message_user_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    read_at DATETIME NULL,
    dismissed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_system_message_user_state (message_id, user_id),
    INDEX idx_system_message_user_state_user (user_id),
    INDEX idx_system_message_user_state_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN scope VARCHAR(255) DEFAULT ''USER'' AFTER type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'scope'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE system_message
SET scope = CASE
    WHEN receiver_id IS NULL OR receiver_id = '' THEN 'BROADCAST'
    ELSE 'USER'
END
WHERE scope IS NULL OR scope = '';

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_system_message_scope ON system_message (scope)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND index_name = 'idx_system_message_scope'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Alert notification preferences used by AlertNotificationConfig.
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN critical_only BOOLEAN DEFAULT FALSE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'critical_only'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN instant_push BOOLEAN DEFAULT TRUE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'instant_push'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN merge_interval_minutes INT DEFAULT 0',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'merge_interval_minutes'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN desktop_notification BOOLEAN DEFAULT TRUE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'desktop_notification'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN notify_email BOOLEAN DEFAULT TRUE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'notify_email'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_notification_config ADD COLUMN notify_inapp BOOLEAN DEFAULT TRUE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_notification_config'
      AND column_name = 'notify_inapp'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Mail provider and IMAP fields required by MailConfigEntity in production validate mode.
ALTER TABLE sys_mail_config MODIFY COLUMN api_key VARCHAR(500);

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_mail_config ADD COLUMN imap_host VARCHAR(100)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_mail_config'
      AND column_name = 'imap_host'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_mail_config ADD COLUMN imap_port INT DEFAULT 993',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_mail_config'
      AND column_name = 'imap_port'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_mail_config ADD COLUMN imap_username VARCHAR(100)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_mail_config'
      AND column_name = 'imap_username'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_mail_config ADD COLUMN imap_password VARCHAR(200)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_mail_config'
      AND column_name = 'imap_password'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_mail_config ADD COLUMN imap_enabled BOOLEAN DEFAULT FALSE',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_mail_config'
      AND column_name = 'imap_enabled'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
