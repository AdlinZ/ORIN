USE orindb;

INSERT INTO knowledge_bases (id, name, type, description, doc_count, total_size_mb, status, created_at, sync_time) VALUES
('kb-1', '核心产品文档库', 'UNSTRUCTURED', '包含 ORIN 2026 技术规格说明、用户手册及接入指南。', 124, 45.2, 'ENABLED', NOW(), NOW()),
('kb-2', '生产运行数据库', 'STRUCTURED', '实时同步生产环境的核心业务表结构与元数据。', 0, 0, 'ENABLED', NOW(), NOW()),
('kb-3', '标准作业 SOP 集', 'PROCEDURAL', '自动化执行流程与专家经验的程序化抽象。', 42, 12.5, 'ENABLED', NOW(), NOW()),
('kb-4', '用户画像与意图记忆', 'META_MEMORY', '基于多轮对话动态沉淀的用户长期偏好记忆。', 1258, 2.1, 'ENABLED', NOW(), NOW());

-- Add some mock documents for kb-1 to make it look active
INSERT INTO kb_documents (id, knowledge_base_id, file_name, file_type, file_size, vector_status, char_count, upload_time, last_modified, uploaded_by) VALUES
('doc-1', 'kb-1', 'Architecture_Orin_V2.pdf', 'pdf', 1048576, 'INDEXED', 45000, NOW(), NOW(), 'admin'),
('doc-2', 'kb-1', 'Product_Manual_EN.docx', 'docx', 524288, 'INDEXED', 25000, NOW(), NOW(), 'admin'),
('doc-3', 'kb-1', 'API_Reference_v1.md', 'md', 102400, 'INDEXED', 15000, NOW(), NOW(), 'admin');

-- Add some mock chunks for doc-1 so DocumentDetail works
INSERT INTO kb_document_chunks (id, document_id, chunk_index, content, char_count) VALUES
('chunk-1-1', 'doc-1', 0, 'ORIN 是一个先进的 AI 助手平台，集成了多模态能力和知识库管理功能。它能够处理文本、图像和语音输入，为用户提供智能化的交互体验。', 85),
('chunk-1-2', 'doc-1', 1, '系统架构采用微服务设计，前端使用 Vue 3 + Element Plus，后端基于 Spring Boot。数据库使用 PostgreSQL 存储结构化数据，向量数据库用于语义检索。', 92),
('chunk-1-3', 'doc-1', 2, '知识库支持四种类型：非结构化文档、结构化数据、程序化技能和元记忆。每种类型都有专门的处理流程和检索策略。', 78);
