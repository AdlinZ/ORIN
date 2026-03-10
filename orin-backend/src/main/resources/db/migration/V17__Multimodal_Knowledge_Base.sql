-- V17__Multimodal_Knowledge_Base.sql
-- Add multimodal support to knowledge base system

-- 1. Add multimodal fields to kb_documents table
ALTER TABLE kb_documents
ADD COLUMN storage_root VARCHAR(500) COMMENT '存储根目录',
ADD COLUMN original_filename VARCHAR(255) COMMENT '原始文件名',
ADD COLUMN media_type VARCHAR(50) COMMENT '媒体类型: image/pdf/audio/video/text',
ADD COLUMN parse_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '解析状态: PENDING/PARSING/SUCCESS/FAILED',
ADD COLUMN parsed_path VARCHAR(500) COMMENT '解析后文本路径',
ADD COLUMN parse_error TEXT COMMENT '解析错误信息';

-- Update existing documents to have default values
UPDATE kb_documents SET parse_status = 'SUCCESS' WHERE parse_status IS NULL OR parse_status = '';

-- 2. Create parsing tasks table for async processing
CREATE TABLE IF NOT EXISTS kb_parsing_tasks (
    id VARCHAR(255) PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    knowledge_base_id VARCHAR(50) NOT NULL,
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型: OCR/ASR/PDF_EXTRACT/TEXT_EXTRACT',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '任务状态: PENDING/PROCESSING/SUCCESS/FAILED',
    input_path VARCHAR(500) NOT NULL,
    output_path VARCHAR(500),
    priority INT DEFAULT 0 COMMENT '优先级',
    error_message TEXT,
    retry_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id),
    INDEX idx_knowledge_base_id (knowledge_base_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Add hybrid search configuration to knowledge_configs
ALTER TABLE knowledge_configs
ADD COLUMN config_key VARCHAR(100),
ADD COLUMN config_value TEXT;

-- Create index for config lookup
CREATE INDEX idx_config_key ON knowledge_configs(config_key);
