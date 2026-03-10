-- V10__Add_ZeroClaw_AI_Config.sql
-- ZeroClaw AI 配置字段 - 绑定 Agent

-- 添加 Agent ID 字段到 zeroclaw_configs 表（忽略已存在的错误）
ALTER TABLE zeroclaw_configs ADD COLUMN agent_id VARCHAR(36);
-- 忽略 Duplicate column 错误
