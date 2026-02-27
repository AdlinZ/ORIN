-- ZeroClaw 技能注册脚本
-- 运行此脚本以允许 AI 助手调用 ZeroClaw 的自愈和诊断能力

USE orindb;

-- 1. 系统状态扫描技能
INSERT INTO skills (
    skill_name, skill_type, description, 
    api_endpoint, api_method, 
    input_schema, output_schema,
    status, created_by
) VALUES (
    'ZeroClaw_Status', 'API', '获取系统实时运行状态，包括 CPU、内存、数据库连通性以及 ZeroClaw 活跃配置。',
    'http://localhost:8080/api/zeroclaw/status', 'GET',
    '{"type": "object", "properties": {}}',
    '{"type": "object", "properties": {"connected": {"type": "boolean"}, "configName": {"type": "string"}, "analysisEnabled": {"type": "boolean"}}}',
    'ACTIVE', 'System_ZeroClaw'
) ON DUPLICATE KEY UPDATE description=VALUES(description);

-- 2. 智能诊断分析技能
INSERT INTO skills (
    skill_name, skill_type, description, 
    api_endpoint, api_method, 
    input_schema, output_schema,
    status, created_by
) VALUES (
    'ZeroClaw_Analyze', 'API', '对特定 Agent 或系统模块进行多维度的智能瓶颈诊断并生成报告。',
    'http://localhost:8080/api/zeroclaw/analyze', 'POST',
    '{
        "type": "object", 
        "required": ["analysisType"], 
        "properties": {
            "agentId": {"type": "string", "description": "目标 Agent ID"},
            "analysisType": {"type": "string", "enum": ["PERFORMANCE", "RESOURCE_LEAK", "TREND_FORECAST"], "description": "分析类型"},
            "context": {"type": "string", "description": "分析上下文（可选）"}
        }
    }',
    '{"type": "object", "properties": {"title": {"type": "string"}, "summary": {"type": "string"}, "rootCause": {"type": "string"}, "recommendations": {"type": "string"}}}',
    'ACTIVE', 'System_ZeroClaw'
) ON DUPLICATE KEY UPDATE description=VALUES(description);

-- 3. 自愈指令执行技能
INSERT INTO skills (
    skill_name, skill_type, description, 
    api_endpoint, api_method, 
    input_schema, output_schema,
    status, created_by
) VALUES (
    'ZeroClaw_SelfHealing', 'API', '对指定的系统资源执行自愈修复动作，如清理日志目录、重启异常模块。',
    'http://localhost:8080/api/zeroclaw/self-healing', 'POST',
    '{
        "type": "object", 
        "required": ["actionType", "targetResource"], 
        "properties": {
            "actionType": {"type": "string", "enum": ["DISK_CLEANUP", "RESTART_MODULE", "CLEAR_CACHE"], "description": "自愈操作类型"},
            "targetResource": {"type": "string", "description": "操作目标路径或服务名"},
            "reason": {"type": "string", "description": "执行原因说明"},
            "forceExecute": {"type": "boolean", "default": true}
        }
    }',
    '{"type": "object", "properties": {"status": {"type": "string"}, "executionDetails": {"type": "string"}}}',
    'ACTIVE', 'System_ZeroClaw'
) ON DUPLICATE KEY UPDATE description=VALUES(description);

-- 4. 每日趋势报表查询
INSERT INTO skills (
    skill_name, skill_type, description, 
    api_endpoint, api_method, 
    input_schema, output_schema,
    status, created_by
) VALUES (
    'ZeroClaw_DailyReport', 'API', '获取过去 24 小时的系统健康趋势与预测报告。',
    'http://localhost:8080/api/zeroclaw/reports/daily', 'POST',
    '{"type": "object", "properties": {}}',
    '{"type": "object", "properties": {"title": {"type": "string"}, "summary": {"type": "string"}}}',
    'ACTIVE', 'System_ZeroClaw'
) ON DUPLICATE KEY UPDATE description=VALUES(description);
