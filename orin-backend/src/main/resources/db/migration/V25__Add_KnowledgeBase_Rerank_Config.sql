-- 添加知识库 Rerank 配置字段
ALTER TABLE knowledge_bases ADD COLUMN enable_rerank BOOLEAN DEFAULT false;
ALTER TABLE knowledge_bases ADD COLUMN rerank_model VARCHAR(255);
