-- V59__Add_idempotency_key_to_sync_change_log.sql
-- Add idempotency_key column to prevent duplicate sync log entries

SET @dbname = DATABASE();
SET @tablename = 'knowledge_sync_change_log';
SET @columnname = 'idempotency_key';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE knowledge_sync_change_log ADD COLUMN idempotency_key VARCHAR(128) UNIQUE'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;