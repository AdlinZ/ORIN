-- Ensure server_hardware_metrics has snake_case columns expected by JPA mappings.
-- Some historical environments were created with camelCase columns (serverId/serverName),
-- which causes runtime SQL errors on read/write.

SET @table_exists := (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
);

SET @has_server_id := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
      AND column_name = 'server_id'
);

SET @sql := IF(
    @table_exists > 0 AND @has_server_id = 0,
    'ALTER TABLE server_hardware_metrics ADD COLUMN server_id VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_name := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
      AND column_name = 'server_name'
);

SET @sql := IF(
    @table_exists > 0 AND @has_server_name = 0,
    'ALTER TABLE server_hardware_metrics ADD COLUMN server_name VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_serverId := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
      AND column_name = 'serverId'
);

SET @sql := IF(
    @table_exists > 0 AND @has_serverId > 0,
    'UPDATE server_hardware_metrics
        SET server_id = serverId
      WHERE (server_id IS NULL OR server_id = '''')
        AND serverId IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_serverName := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
      AND column_name = 'serverName'
);

SET @sql := IF(
    @table_exists > 0 AND @has_serverName > 0,
    'UPDATE server_hardware_metrics
        SET server_name = serverName
      WHERE (server_name IS NULL OR server_name = '''')
        AND serverName IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_index := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'server_hardware_metrics'
      AND index_name = 'idx_hardware_server_id_time'
);

SET @sql := IF(
    @table_exists > 0 AND @has_index = 0,
    'CREATE INDEX idx_hardware_server_id_time ON server_hardware_metrics(server_id, timestamp)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
