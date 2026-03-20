-- 帮助文档表
CREATE TABLE IF NOT EXISTS `help_article` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT,
    `category` VARCHAR(100) DEFAULT 'general',
    `tags` VARCHAR(500),
    `sort_order` INT DEFAULT 0,
    `enabled` TINYINT(1) DEFAULT 1,
    `view_count` INT DEFAULT 0,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    INDEX idx_category (`category`),
    INDEX idx_enabled (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认帮助文档
INSERT INTO `help_article` (`title`, `content`, `category`, `tags`, `sort_order`, `enabled`, `created_at`, `updated_at`) VALUES
('快速开始', '# 快速开始\n\n欢迎使用 ORIN 智能体管理系统！\n\n## 1. 创建智能体\n\n点击"应用列表" -> "新建智能体"，填写基本信息后即可创建。\n\n## 2. 配置知识库\n\n为智能体绑定知识库，可以提升回答的准确性。\n\n## 3. 开始对话\n\n在智能体详情页，点击"开始对话"即可与智能体交互。', 'getting-started', '入门,快速开始', 1, 1, NOW(), NOW()),
('智能体管理', '# 智能体管理\n\n本系统支持对智能体进行全面管理。\n\n## 功能特性\n\n- 创建、编辑、删除智能体\n- 配置智能体参数\n- 绑定知识库\n- 查看对话历史\n\n## 最佳实践\n\n1. 为每个业务场景创建专属智能体\n2. 定期更新知识库内容\n3. 监控智能体调用量', 'user-guide', '智能体,管理', 2, 1, NOW(), NOW()),
('知识库使用', '# 知识库使用\n\n知识库是智能体的"大脑"。\n\n## 上传文档\n\n支持 PDF、Word、Markdown 等格式。\n\n## 配置检索\n\n可以设置检索参数，优化检索效果。\n\n## 知识图谱\n\n系统会自动构建知识图谱，提升检索准确性。', 'user-guide', '知识库,检索', 3, 1, NOW(), NOW()),
('工作流编排', '# 工作流编排\n\n通过可视化界面编排复杂工作流。\n\n## 支持的节点类型\n\n- LLM 节点\n- 知识库检索节点\n- 条件判断节点\n- 工具调用节点\n\n## 发布工作流\n\n编排完成后，点击"发布"即可生效。', 'user-guide', '工作流,编排', 4, 1, NOW(), NOW()),
('API 接口', '# API 接口\n\n系统提供完整的 REST API。\n\n## 认证方式\n\n使用 JWT Token 进行认证。\n\n## 调用示例\n\n```bash\ncurl -X POST https://api.example.com/chat \\\n  -H "Authorization: Bearer <token>" \\\n  -d "{\\"message\\": \\"你好\\"}"\n```', 'developer', 'API,开发', 5, 1, NOW(), NOW());
