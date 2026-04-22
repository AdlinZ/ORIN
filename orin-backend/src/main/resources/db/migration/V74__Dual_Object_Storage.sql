-- Dual object storage metadata and async replication repair queue

CREATE TABLE IF NOT EXISTS storage_replication_tasks (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(60),
    entity_id VARCHAR(64),
    object_key VARCHAR(500) NOT NULL,
    source_backend VARCHAR(20) NOT NULL,
    target_backend VARCHAR(20) NOT NULL,
    source_locator VARCHAR(1000) NOT NULL,
    target_locator VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REPAIR',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 8,
    last_error TEXT,
    last_attempt_at DATETIME,
    next_retry_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_storage_replication_status (status),
    INDEX idx_storage_replication_next_retry (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- kb_documents extension
ALTER TABLE kb_documents
    ADD COLUMN object_key VARCHAR(500),
    ADD COLUMN primary_backend VARCHAR(20),
    ADD COLUMN replica_backends VARCHAR(100),
    ADD COLUMN replication_status VARCHAR(30),
    ADD COLUMN last_replicated_at DATETIME,
    ADD COLUMN last_replication_error TEXT,
    ADD COLUMN checksum VARCHAR(64),
    ADD COLUMN content_type VARCHAR(120);

-- multimodal_files extension
ALTER TABLE multimodal_files
    ADD COLUMN object_key VARCHAR(500),
    ADD COLUMN primary_backend VARCHAR(20),
    ADD COLUMN replica_backends VARCHAR(100),
    ADD COLUMN replication_status VARCHAR(30),
    ADD COLUMN last_replicated_at DATETIME,
    ADD COLUMN last_replication_error TEXT,
    ADD COLUMN checksum VARCHAR(64);
