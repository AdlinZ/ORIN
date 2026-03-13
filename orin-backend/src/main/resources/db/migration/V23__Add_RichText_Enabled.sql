-- 添加知识库富文本解析字段
ALTER TABLE knowledge_bases ADD COLUMN rich_text_enabled BOOLEAN DEFAULT TRUE;
