-- V58__Add_dify_document_mapping.sql
-- Document mapping table: local docId <-> Dify dataset/docId
-- Supports idempotency key for dedup

CREATE TABLE IF NOT EXISTS knowledge_dify_document_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT,
    local_doc_id VARCHAR(64),
    local_kb_id VARCHAR(64),
    dify_dataset_id VARCHAR(64),
    dify_doc_id VARCHAR(64),
    idempotency_key VARCHAR(128) UNIQUE,
    sync_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'SYNCED, PENDING, FAILED, DELETED',
    local_version INT,
    dify_version VARCHAR(64),
    content_hash VARCHAR(64),
    deleted_on_dify BOOLEAN DEFAULT FALSE,
    last_synced_at DATETIME,
    created_at DATETIME,
    error_message TEXT,
    INDEX idx_local_doc_id (local_doc_id),
    INDEX idx_dify_dataset_doc (dify_dataset_id, dify_doc_id),
    INDEX idx_integration_id (integration_id),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
