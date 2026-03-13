-- 添加知识库检索配置字段
ALTER TABLE knowledge_bases ADD COLUMN chunk_size INT;
ALTER TABLE knowledge_bases ADD COLUMN chunk_overlap INT;
ALTER TABLE knowledge_bases ADD COLUMN top_k INT;
ALTER TABLE knowledge_bases ADD COLUMN similarity_threshold DOUBLE PRECISION;
ALTER TABLE knowledge_bases ADD COLUMN alpha DOUBLE PRECISION DEFAULT 0.7;
