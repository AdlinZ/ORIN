-- V95: F02 创建并冻结 Agent — AgentVersion 不可变边界 (ADR-002 v4.1 Accepted)
-- 在 V1..V94 之上追加列与表，不改写已发布迁移。
-- 落点：
--   1. agent_metadata 加 active_version_id（**真 FK** → agent_versions.id，R3 单一指针）
--                    + pending_secret_refs（JSON 草稿持久化 draft 上的 SecretReference）
--   2. agent_versions 加 status / content_digest / snapshot_schema_version / frozen_* / deprecation_*
--   3. 新表 agent_version_secret_refs（CONTROL_PLANE/RUNNER_LOCAL 桥接行；
--      MVP 仅 CONTROL_PLANE 路径被业务触发）
--   4. 新表 agent_version_freeze_idempotency（freeze Idempotency-Key 主表）
-- 时间戳沿用 agent 表族 TIMESTAMP，不沿用 V94 Runner 的 BIGINT epoch-ms 约定。
-- 启动期兜底：若 active_version_id 为空但存在 is_active=true 历史行，
-- service 层读取兼容帮助指向 version_number 最大者；F02 自身写入始终遵循新管线。

-- ============================================================
-- 1. agent_metadata: 新增 active_version_id (FK) + pending_secret_refs
-- ============================================================

-- 1a. active_version_id 列（可能已部分 DB 已加 - 用 IF NOT EXISTS 守护）
SET @am_av = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_metadata ADD COLUMN active_version_id VARCHAR(40) NULL AFTER view_type',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND column_name = 'active_version_id'
);
PREPARE stmt FROM @am_av;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1b. pending_secret_refs 列 - 草稿上保存的 SecretReference JSON；freeze 读取它
SET @am_psr = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_metadata ADD COLUMN pending_secret_refs JSON NULL AFTER active_version_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND column_name = 'pending_secret_refs'
);
PREPARE stmt FROM @am_psr;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1c. 删除历史普通索引（若存在），改为加 FK
SET @drop_idx = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE agent_metadata DROP INDEX idx_am_active_version',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND index_name = 'idx_am_active_version'
);
PREPARE stmt FROM @drop_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_metadata ADD INDEX idx_am_active_version (active_version_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_metadata'
      AND index_name = 'idx_am_active_version'
);
PREPARE stmt FROM @add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1d. 真外键 constraint (ADR-002 §D-2.1)
-- 注意：仅当历史库未建过同名 FK 时添加；启动期不报错
SET @fk_name = 'fk_agent_metadata_active_version';
SET @fk_check = (
    SELECT COUNT(*)
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name = @fk_name
);
SET @fk_sql = IF(
    @fk_check = 0,
    'ALTER TABLE agent_metadata ADD CONSTRAINT fk_agent_metadata_active_version FOREIGN KEY (active_version_id) REFERENCES agent_versions(id) ON DELETE NO ACTION ON UPDATE NO ACTION',
    'SELECT 1'
);
PREPARE stmt FROM @fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. agent_versions: 新增 ADR-002 R3 字段
-- ============================================================

SET @av_status = (
    SELECT IF(
        COUNT(*) = 0,
        "ALTER TABLE agent_versions ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'FROZEN' AFTER is_active",
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'status'
);
PREPARE stmt FROM @av_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_digest = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN content_digest CHAR(64) NOT NULL DEFAULT '''' AFTER config_snapshot',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'content_digest'
);
PREPARE stmt FROM @av_digest;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_sv = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN snapshot_schema_version SMALLINT NOT NULL DEFAULT 1 AFTER content_digest',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'snapshot_schema_version'
);
PREPARE stmt FROM @av_sv;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_frozen_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN frozen_at TIMESTAMP NULL AFTER created_by',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'frozen_at'
);
PREPARE stmt FROM @av_frozen_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_frozen_by = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN frozen_by VARCHAR(120) NULL AFTER frozen_at',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'frozen_by'
);
PREPARE stmt FROM @av_frozen_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_dep_reason = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN deprecation_reason VARCHAR(255) NULL AFTER frozen_by',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'deprecation_reason'
);
PREPARE stmt FROM @av_dep_reason;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_dep_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN deprecated_at TIMESTAMP NULL AFTER deprecation_reason',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'deprecated_at'
);
PREPARE stmt FROM @av_dep_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_dep_by = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD COLUMN deprecated_by VARCHAR(120) NULL AFTER deprecated_at',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND column_name = 'deprecated_by'
);
PREPARE stmt FROM @av_dep_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_idx_st = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD INDEX idx_agent_version_status (status)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND index_name = 'idx_agent_version_status'
);
PREPARE stmt FROM @av_idx_st;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @av_idx_dg = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_versions ADD INDEX idx_agent_version_content_digest (content_digest)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_versions'
      AND index_name = 'idx_agent_version_content_digest'
);
PREPARE stmt FROM @av_idx_dg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. agent_version_secret_refs (FROZEN bridge 行)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_version_secret_refs (
    agent_version_id   VARCHAR(40)  NOT NULL,
    alias              VARCHAR(64)  NOT NULL,
    source             VARCHAR(16)  NOT NULL,
    secret_id          VARCHAR(100) NULL,
    local_key          VARCHAR(120) NULL,
    required           BOOLEAN      NOT NULL DEFAULT TRUE,
    inject_as          VARCHAR(64)  NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (agent_version_id, alias),
    INDEX idx_avsr_secret (secret_id),
    INDEX idx_avsr_source (source),
    CONSTRAINT chk_avsr_source CHECK (source IN ('CONTROL_PLANE', 'RUNNER_LOCAL')),
    CONSTRAINT chk_avsr_value CHECK (
        (source = 'CONTROL_PLANE' AND secret_id IS NOT NULL) OR
        (source = 'RUNNER_LOCAL' AND local_key IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. agent_version_freeze_idempotency (DB-primary idempotency 主表)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_version_freeze_idempotency (
    agent_id              VARCHAR(40) NOT NULL,
    idempotency_key_hash  CHAR(64)    NOT NULL,
    request_digest        CHAR(64)    NOT NULL,
    agent_version_id      VARCHAR(40) NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP   NOT NULL,
    PRIMARY KEY (agent_id, idempotency_key_hash),
    INDEX idx_avfi_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
