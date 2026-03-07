-- V14__Add_Chunk_Method_And_Size.sql
-- Add chunk_method and chunk_size columns to kb_documents table

ALTER TABLE kb_documents
ADD COLUMN chunk_method VARCHAR(20) DEFAULT 'auto' COMMENT '切分模式: auto, manual, smart',
ADD COLUMN chunk_size INT DEFAULT 500 COMMENT '切分长度',
ADD COLUMN chunk_overlap INT DEFAULT 50 COMMENT '重叠长度';

-- Update existing documents to have auto chunk method
UPDATE kb_documents SET chunk_method = 'auto', chunk_size = 500, chunk_overlap = 50 WHERE chunk_method IS NULL;
