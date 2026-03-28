-- 智能体会话知识库配置持久化
ALTER TABLE agent_chat_session ADD COLUMN attached_kb_ids JSON;
ALTER TABLE agent_chat_session ADD COLUMN kb_doc_filters JSON;
