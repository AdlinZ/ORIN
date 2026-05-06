-- Structured alert rule target and metric-window settings.
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_rules ADD COLUMN target_scope VARCHAR(30) DEFAULT ''ALL'' AFTER threshold_value',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_rules'
      AND column_name = 'target_scope'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_rules ADD COLUMN target_id VARCHAR(100) NULL AFTER target_scope',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_rules'
      AND column_name = 'target_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_rules ADD COLUMN metric_window_minutes INT DEFAULT 5 AFTER target_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_rules'
      AND column_name = 'metric_window_minutes'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE alert_rules ADD COLUMN min_sample_count INT DEFAULT 1 AFTER metric_window_minutes',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_rules'
      AND column_name = 'min_sample_count'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE alert_rules
SET target_scope = 'ALL'
WHERE target_scope IS NULL OR target_scope = '';

UPDATE alert_rules
SET metric_window_minutes = 5
WHERE metric_window_minutes IS NULL OR metric_window_minutes < 1;

UPDATE alert_rules
SET min_sample_count = 1
WHERE min_sample_count IS NULL OR min_sample_count < 1;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_alert_rules_type_target_enabled ON alert_rules (rule_type, target_scope, target_id, enabled)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'alert_rules'
      AND index_name = 'idx_alert_rules_type_target_enabled'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
