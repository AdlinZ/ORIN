-- 补齐系统默认角色（幂等）
-- 仅在角色不存在时新增，不覆盖已有角色定义

INSERT INTO sys_role (role_code, role_name, description)
VALUES
    ('ROLE_OPERATOR', '业务运营', '负责智能体业务配置、知识资产管理与工作流编排运营'),
    ('ROLE_PLATFORM_ADMIN', '平台管理员', '负责平台运行配置、监控治理与系统问题排查'),
    ('ROLE_SUPER_ADMIN', '超级管理员', '拥有全局控制权限，可管理组织与平台全部能力')
ON DUPLICATE KEY UPDATE role_id = role_id;

