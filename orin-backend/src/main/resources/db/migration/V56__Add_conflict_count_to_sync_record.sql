-- V56__Add_conflict_count_to_sync_record.sql
-- Add conflict_count column to knowledge_sync_record for conflict tracking

SET @dbname = DATABASE();
SET @tablename = 'knowledge_sync_record';
SET @columnname = 'conflict_count';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE knowledge_sync_record ADD COLUMN conflict_count INT DEFAULT 0 COMMENT ''Number of conflicts detected during sync''
'));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;