-- V27__Add_Default_User.sql
-- 添加默认用户账号
INSERT INTO sys_user (username, password, nickname, email, status, create_time, role)
SELECT 'user', 'user123', '普通用户', 'user@orin.com', 'ENABLED', NOW(), 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'user');

-- 为用户分配 ROLE_USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'user' AND r.role_code = 'ROLE_USER';
