-- V93: Gateway MVP audit_logs 三列补齐
-- 为 audit_logs 与 gateway_audit_logs 增加 model_alias / provider_model / error_code
-- 三列均为可空；老数据保持 NULL 视为 not recorded。
-- 创建 model_alias 单列索引（高基数），provider_model 与 error_code 不单独建索引。

-- ============================================================
-- 1. audit_logs
-- ============================================================

SET @al_add_ma = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE audit_logs ADD COLUMN model_alias VARCHAR(100) NULL AFTER model',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_logs'
      AND column_name = 'model_alias'
);
PREPARE stmt FROM @al_add_ma;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @al_add_pm = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE audit_logs ADD COLUMN provider_model VARCHAR(100) NULL AFTER model_alias',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_logs'
      AND column_name = 'provider_model'
);
PREPARE stmt FROM @al_add_pm;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @al_add_ec = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE audit_logs ADD COLUMN error_code VARCHAR(32) NULL AFTER error_message',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_logs'
      AND column_name = 'error_code'
);
PREPARE stmt FROM @al_add_ec;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @al_add_idx_ma = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_audit_model_alias ON audit_logs(model_alias)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_logs'
      AND index_name = 'idx_audit_model_alias'
);
PREPARE stmt FROM @al_add_idx_ma;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. gateway_audit_logs
-- ============================================================

SET @gal_add_ma = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE gateway_audit_logs ADD COLUMN model_alias VARCHAR(100) NULL AFTER path',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'gateway_audit_logs'
      AND column_name = 'model_alias'
);
PREPARE stmt FROM @gal_add_ma;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @gal_add_pm = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE gateway_audit_logs ADD COLUMN provider_model VARCHAR(100) NULL AFTER model_alias',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'gateway_audit_logs'
      AND column_name = 'provider_model'
);
PREPARE stmt FROM @gal_add_pm;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @gal_add_ec = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE gateway_audit_logs ADD COLUMN error_code VARCHAR(32) NULL AFTER error_message',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'gateway_audit_logs'
      AND column_name = 'error_code'
);
PREPARE stmt FROM @gal_add_ec;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @gal_add_idx_ma = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_gal_model_alias ON gateway_audit_logs(model_alias)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'gateway_audit_logs'
      AND index_name = 'idx_gal_model_alias'
);
PREPARE stmt FROM @gal_add_idx_ma;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
