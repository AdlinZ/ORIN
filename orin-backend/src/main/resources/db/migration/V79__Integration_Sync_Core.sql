-- External platform full-resource synchronization core.
-- ORIN remains source of truth; external resources are tracked as mappings/snapshots.

SET @dbname = DATABASE();
SET @tablename = 'knowledge_sync_change_log';

SET @columnname = 'integration_id';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN integration_id BIGINT NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'platform_type';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN platform_type VARCHAR(30) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'orin_resource_type';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN orin_resource_type VARCHAR(40) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'orin_resource_id';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN orin_resource_id VARCHAR(128) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'resource_name';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN resource_name VARCHAR(255) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'payload_hash';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN payload_hash VARCHAR(64) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'payload_snapshot';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN payload_snapshot TEXT NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'change_source';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN change_source VARCHAR(20) DEFAULT ''ORIN'''));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'sync_status';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN sync_status VARCHAR(20) DEFAULT ''PENDING'''));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'retry_count';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN retry_count INT DEFAULT 0'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'error_message';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_change_log ADD COLUMN error_message VARCHAR(1000) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @tablename = 'knowledge_sync_record';

SET @columnname = 'integration_id';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_record ADD COLUMN integration_id BIGINT NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'platform_type';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_record ADD COLUMN platform_type VARCHAR(30) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'sync_job_id';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_record ADD COLUMN sync_job_id BIGINT NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'trigger_type';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_record ADD COLUMN trigger_type VARCHAR(30) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @columnname = 'resource_scope';
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@dbname AND TABLE_NAME=@tablename AND COLUMN_NAME=@columnname) > 0, 'SELECT 1', 'ALTER TABLE knowledge_sync_record ADD COLUMN resource_scope VARCHAR(40) NULL'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS integration_external_resource_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    platform_type VARCHAR(30) NOT NULL,
    orin_resource_type VARCHAR(40) NOT NULL,
    orin_resource_id VARCHAR(128) NOT NULL,
    external_resource_type VARCHAR(80),
    external_resource_id VARCHAR(128),
    external_version VARCHAR(128),
    external_updated_at DATETIME NULL,
    last_synced_hash VARCHAR(64),
    sync_direction VARCHAR(20),
    sync_status VARCHAR(20) DEFAULT 'PENDING',
    raw_snapshot TEXT,
    compatibility_report TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mapping_integration_orin (integration_id, orin_resource_type, orin_resource_id),
    KEY idx_mapping_orin_resource (orin_resource_type, orin_resource_id),
    KEY idx_mapping_external_resource (platform_type, external_resource_type, external_resource_id),
    KEY idx_mapping_integration (integration_id)
);

CREATE TABLE IF NOT EXISTS integration_sync_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    platform_type VARCHAR(30) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    resource_scope VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    conflict_count INT DEFAULT 0,
    cursor_value VARCHAR(255),
    error_message VARCHAR(1000),
    details TEXT,
    KEY idx_sync_job_integration (integration_id),
    KEY idx_sync_job_status (status),
    KEY idx_sync_job_started_at (started_at)
);

CREATE TABLE IF NOT EXISTS integration_sync_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_job_id BIGINT NOT NULL,
    orin_resource_type VARCHAR(40),
    orin_resource_id VARCHAR(128),
    external_resource_type VARCHAR(80),
    external_resource_id VARCHAR(128),
    change_log_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    content_hash VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sync_item_job (sync_job_id),
    KEY idx_sync_item_orin_resource (orin_resource_type, orin_resource_id),
    KEY idx_sync_item_status (status)
);

CREATE TABLE IF NOT EXISTS integration_sync_cursor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    cursor_value VARCHAR(255),
    last_seen_hash VARCHAR(64),
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sync_cursor (integration_id, resource_type, direction)
);

CREATE TABLE IF NOT EXISTS integration_sync_conflict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    platform_type VARCHAR(30) NOT NULL,
    orin_resource_type VARCHAR(40) NOT NULL,
    orin_resource_id VARCHAR(128) NOT NULL,
    conflict_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    local_hash VARCHAR(64),
    external_hash VARCHAR(64),
    message VARCHAR(1000),
    local_snapshot TEXT,
    external_snapshot TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    KEY idx_sync_conflict_integration (integration_id),
    KEY idx_sync_conflict_resource (orin_resource_type, orin_resource_id),
    KEY idx_sync_conflict_status (status)
);
