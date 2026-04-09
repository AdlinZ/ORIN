-- V60__Add_task_category_to_task_info.sql
-- Add task_category column to distinguish WORKFLOW vs SYNC tasks

SET @dbname = DATABASE();
SET @tablename = 'task_info';
SET @columnname = 'task_category';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE task_info ADD COLUMN task_category VARCHAR(20) DEFAULT ''WORKFLOW'' COMMENT ''WORKFLOW, SYNC'''
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;