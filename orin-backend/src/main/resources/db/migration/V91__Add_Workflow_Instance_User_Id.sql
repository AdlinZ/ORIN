SET @add_workflow_instance_user_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE workflow_instances ADD COLUMN user_id BIGINT NULL AFTER triggered_by',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'workflow_instances'
      AND column_name = 'user_id'
);
PREPARE stmt FROM @add_workflow_instance_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_workflow_instance_user_id_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_workflow_instances_user_id ON workflow_instances(user_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'workflow_instances'
      AND index_name = 'idx_workflow_instances_user_id'
);
PREPARE stmt FROM @add_workflow_instance_user_id_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
