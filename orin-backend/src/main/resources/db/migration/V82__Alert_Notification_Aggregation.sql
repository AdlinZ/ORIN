-- Alert instance aggregation and notification-center dedupe metadata.
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN dedupe_key VARCHAR(160) NULL AFTER scope',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'dedupe_key'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN fingerprint VARCHAR(160) NULL AFTER dedupe_key',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'fingerprint'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN source_type VARCHAR(40) NULL AFTER fingerprint',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'source_type'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN status VARCHAR(20) NULL AFTER source_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN repeat_count INT DEFAULT 1 AFTER status',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'repeat_count'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN last_occurred_at DATETIME NULL AFTER repeat_count',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'last_occurred_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN resolved_at DATETIME NULL AFTER last_occurred_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'resolved_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE system_message ADD COLUMN summary TEXT NULL AFTER resolved_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND column_name = 'summary'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE system_message
SET repeat_count = 1
WHERE repeat_count IS NULL OR repeat_count < 1;

UPDATE system_message
SET last_occurred_at = created_at
WHERE last_occurred_at IS NULL;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_system_message_dedupe_status ON system_message (dedupe_key, status)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND index_name = 'idx_system_message_dedupe_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_system_message_last_occurred ON system_message (last_occurred_at)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'system_message'
      AND index_name = 'idx_system_message_last_occurred'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_history ADD COLUMN fingerprint VARCHAR(160) NULL AFTER trace_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_history'
      AND column_name = 'fingerprint'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_history ADD COLUMN repeat_count INT DEFAULT 1 AFTER resolved_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_history'
      AND column_name = 'repeat_count'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_history ADD COLUMN last_triggered_at DATETIME NULL AFTER repeat_count',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_history'
      AND column_name = 'last_triggered_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE alert_history
SET repeat_count = 1
WHERE repeat_count IS NULL OR repeat_count < 1;

UPDATE alert_history
SET last_triggered_at = triggered_at
WHERE last_triggered_at IS NULL;

UPDATE alert_history
SET fingerprint = CONCAT(COALESCE(rule_id, 'SYSTEM_DEFAULT'), ':', COALESCE(agent_id, 'GLOBAL'))
WHERE fingerprint IS NULL OR fingerprint = '';

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_alert_history_fingerprint_status ON alert_history (fingerprint, status)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_history'
      AND index_name = 'idx_alert_history_fingerprint_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
