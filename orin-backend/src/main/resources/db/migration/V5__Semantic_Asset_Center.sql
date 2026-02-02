-- 1. Upgrade multimodal_files table for semantic assets
ALTER TABLE multimodal_files 
ADD COLUMN ai_summary TEXT COMMENT 'AI generated semantic summary',
ADD COLUMN embedding_status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'Vector embedding status: PENDING, PROCESSING, COMPLETED, FAILED',
ADD COLUMN task_retry_count INT DEFAULT 0 COMMENT 'Number of retries for async tasks';

-- 2. Create knowledge_tasks table for async processing state machine
CREATE TABLE knowledge_tasks (
    id VARCHAR(36) PRIMARY KEY,
    asset_id VARCHAR(36) NOT NULL COMMENT 'ID of the related asset (file, chunk, etc)',
    asset_type VARCHAR(50) NOT NULL COMMENT 'Type of asset: MULTIMODAL_FILE, DOCUMENT',
    task_type VARCHAR(50) NOT NULL COMMENT 'Type of task: CAPTIONING, EMBEDDING, INDEXING',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Task status',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_status (status),
    INDEX idx_asset_ref (asset_id, asset_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Upgrade agent_memories for better structured storage
ALTER TABLE agent_memories
ADD COLUMN metadata TEXT COMMENT 'JSON metadata for context',
ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
