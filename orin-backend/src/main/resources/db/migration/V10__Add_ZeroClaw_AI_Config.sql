-- V10__Add_ZeroClaw_AI_Config.sql
-- ZeroClaw AI 配置字段 - 绑定 Agent

-- 添加 Agent ID 字段到 zeroclaw_configs 表
ALTER TABLE zeroclaw_configs ADD COLUMN agent_id VARCHAR(36);
