-- V18__Add_Knowledge_Parsing_Config.sql
-- Add parsing configuration fields to knowledge_bases table

ALTER TABLE knowledge_bases
ADD COLUMN parsing_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用多模态解析',
ADD COLUMN ocr_provider VARCHAR(20) DEFAULT 'local' COMMENT 'OCR提供商: local/cloud',
ADD COLUMN asr_provider VARCHAR(20) DEFAULT 'local' COMMENT 'ASR提供商: local/cloud',
ADD COLUMN ocr_model VARCHAR(100) COMMENT 'OCR模型名称(云服务用)',
ADD COLUMN asr_model VARCHAR(20) DEFAULT 'base' COMMENT 'ASR模型: tiny/base/small/medium/large';

-- Set default values for existing records
UPDATE knowledge_bases SET parsing_enabled = TRUE WHERE parsing_enabled IS NULL;
UPDATE knowledge_bases SET ocr_provider = 'local' WHERE ocr_provider IS NULL;
UPDATE knowledge_bases SET asr_provider = 'local' WHERE asr_provider IS NULL;
UPDATE knowledge_bases SET asr_model = 'base' WHERE asr_model IS NULL;
