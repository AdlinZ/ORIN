-- V3: 添加默认系统用户
-- 该迁移创建默认的管理员和普通用户账号
-- 注意: 实际生产环境应使用加密后的密码

-- 初始化默认用户
-- 管理员账号: admin / admin123
-- 普通用户账号: user / user123
INSERT INTO sys_user (username, password, nickname, email, status, create_time) VALUES
    ('admin', 'admin123', '系统管理员', 'admin@orin.com', 'ENABLED', NOW()),
    ('user', 'user123', '普通用户', 'user@orin.com', 'ENABLED', NOW())
ON DUPLICATE KEY UPDATE 
    password = VALUES(password), 
    nickname = VALUES(nickname),
    email = VALUES(email);

-- 为管理员分配 ROLE_ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN';

-- 为普通用户分配 ROLE_USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'user' AND r.role_code = 'ROLE_USER';

-- 为其他已存在的用户默认分配普通用户角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.role_code = 'ROLE_USER'
AND u.user_id > 2
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id
);
