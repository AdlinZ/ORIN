-- V19: Add Provider Configuration Table
-- 统一管理API供应商的显示顺序和配置

CREATE TABLE IF NOT EXISTS sys_provider_config (
    provider_key VARCHAR(50) PRIMARY KEY COMMENT '供应商唯一标识',
    provider_name VARCHAR(100) NOT NULL COMMENT '供应商显示名称',
    display_order INT NOT NULL DEFAULT 0 COMMENT '显示顺序，数字越小越靠前',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    description VARCHAR(500) COMMENT '供应商描述',
    icon VARCHAR(100) COMMENT '图标标识',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '系统供应商配置表';

-- 插入默认供应商配置（按推荐顺序）
INSERT INTO sys_provider_config (provider_key, provider_name, display_order, enabled, description, icon) VALUES
('openai', 'OpenAI', 1, TRUE, 'GPT-4o, GPT-4, GPT-3.5', 'Cpu'),
('anthropic', 'Anthropic (Claude)', 2, TRUE, 'Claude 3.5 Sonnet, Opus', 'Cpu'),
('deepseek', 'DeepSeek', 3, TRUE, 'DeepSeek Coder, Chat', 'Cpu'),
('siliconflow', 'SiliconFlow', 4, TRUE, '200+ 模型，便宜稳定', 'Cpu'),
('dify', 'Dify (本地/私有)', 5, TRUE, '自部署 Dify 应用', 'Cpu'),
('moonshot', 'Moonshot (Kimi)', 6, TRUE, '超长文本处理专家', 'Moon'),
('zhipu', '智谱 AI (Zhipu)', 7, TRUE, '中英双语性能领先大模型', 'Connection'),
('google', 'Google Gemini', 8, TRUE, '谷歌多模态大模型系列', 'Star'),
('ollama', 'Ollama (Local)', 9, TRUE, '本地大模型服务', 'Cpu'),
('groq', 'Groq', 10, TRUE, '高速推理服务', 'Cpu'),
('minimax', 'MiniMax', 11, TRUE, '国产大模型服务商', 'Cpu'),
('azure', 'Azure OpenAI', 12, TRUE, '微软 Azure OpenAI 服务', 'Cpu')
ON DUPLICATE KEY UPDATE display_order = VALUES(display_order), provider_name = VALUES(provider_name);
