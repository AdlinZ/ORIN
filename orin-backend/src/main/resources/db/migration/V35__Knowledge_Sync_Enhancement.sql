-- V35__Knowledge_Sync_Enhancement.sql
-- Knowledge Base Sync Enhancement for Side-Client Sync Support
-- Adds version control, content hash, deleted flag, direction, checkpoint, duration
--
-- 变更目的: 为知识库同步功能增加版本控制、内容哈希、删除标记、同步方向、检查点、耗时等字段
-- 影响表: kb_documents, knowledge_sync_record, knowledge_sync_webhook, knowledge_sync_change_log
-- 回滚策略: 需要手动删除新增字段，或使用 Flyway 回滚
-- 幂等策略: 使用 MySQL 8.0+ 的 IF NOT EXISTS 检查语法

-- ============================================================
-- 1. Add version control fields to kb_documents
-- ============================================================

-- Add version column (MySQL 8.0.16+ IF NOT EXISTS)
ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS version INT DEFAULT 1;

-- Add content_hash column
ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- Add deleted_flag column
ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS deleted_flag BOOLEAN DEFAULT FALSE;

-- Add sync_checkpoint column
ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS sync_checkpoint VARCHAR(100);

-- Add indexes to kb_documents (with IGNORE to handle duplicates)
ALTER TABLE kb_documents
    ADD INDEX IF NOT EXISTS idx_kb_documents_version (version),
    ADD INDEX IF NOT EXISTS idx_kb_documents_content_hash (content_hash),
    ADD INDEX IF NOT EXISTS idx_kb_documents_deleted_flag (deleted_flag),
    ADD INDEX IF NOT EXISTS idx_kb_documents_last_modified (last_modified);

-- ============================================================
-- 2. Add fields to knowledge_sync_record
-- ============================================================

ALTER TABLE knowledge_sync_record
    ADD COLUMN IF NOT EXISTS direction VARCHAR(10) DEFAULT 'PULL' COMMENT 'PULL (from Dify), PUSH (to Dify)',
    ADD COLUMN IF NOT EXISTS checkpoint VARCHAR(100) COMMENT 'Incremental sync checkpoint',
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT COMMENT 'Sync duration in milliseconds',
    ADD COLUMN IF NOT EXISTS total_docs INT DEFAULT 0 COMMENT 'Total documents processed',
    ADD COLUMN IF NOT EXISTS sync_direction VARCHAR(10) DEFAULT 'INBOUND' COMMENT 'INBOUND (pull), OUTBOUND (push)';

ALTER TABLE knowledge_sync_record
    ADD INDEX IF NOT EXISTS idx_sync_record_direction (direction),
    ADD INDEX IF NOT EXISTS idx_sync_record_checkpoint (checkpoint),
    ADD INDEX IF NOT EXISTS idx_sync_record_sync_direction (sync_direction);

-- ============================================================
-- 3. Create knowledge_sync_webhook table if not exists
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_sync_webhook (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    webhook_url VARCHAR(500) NOT NULL,
    webhook_secret VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    event_types VARCHAR(500) COMMENT 'Comma-separated events: document_added,document_updated,document_deleted,sync_completed',
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    created_by VARCHAR(100),
    INDEX idx_webhook_agent_id (agent_id),
    INDEX idx_webhook_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. Create knowledge_sync_change_log table if not exists
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_sync_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    knowledge_base_id VARCHAR(64) NOT NULL,
    change_type VARCHAR(20) NOT NULL COMMENT 'ADDED, UPDATED, DELETED',
    version INT NOT NULL,
    content_hash VARCHAR(64),
    changed_at DATETIME NOT NULL,
    synced BOOLEAN DEFAULT FALSE,
    INDEX idx_change_log_agent_document (agent_id, document_id),
    INDEX idx_change_log_changed_at (changed_at),
    INDEX idx_change_log_synced (synced)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
