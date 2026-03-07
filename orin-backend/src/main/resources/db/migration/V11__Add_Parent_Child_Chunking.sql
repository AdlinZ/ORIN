-- V11__Add_Parent_Child_Chunking.sql
-- Parent-Child Hierarchical Chunking schema migration
-- This migration adds support for hierarchical chunking in the knowledge base

-- Add new columns to kb_document_chunks table
ALTER TABLE kb_document_chunks
ADD COLUMN chunk_type VARCHAR(20) DEFAULT 'child' COMMENT '分片类型: child 或 parent',
ADD COLUMN parent_id VARCHAR(64) COMMENT '父分片ID (仅child类型使用)',
ADD COLUMN children_ids TEXT COMMENT '子分片ID列表 (仅parent类型使用, JSON格式)',
ADD COLUMN title VARCHAR(500) COMMENT '标题/摘要',
ADD COLUMN source VARCHAR(500) COMMENT '来源文档标题',
ADD COLUMN position INT COMMENT '位置索引';

-- Create indexes for Parent-Child queries
CREATE INDEX idx_parent_id ON kb_document_chunks(parent_id);
CREATE INDEX idx_chunk_type ON kb_document_chunks(chunk_type);

-- Note: This migration is for existing data
-- Existing chunks will have chunk_type='child' by default
-- Parent chunks are created during vectorization with the new HierarchicalTextSplitter
