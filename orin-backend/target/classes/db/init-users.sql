-- 初始化默认用户
-- 管理员账号: admin / admin123
-- 普通用户账号: user / user123

INSERT INTO sys_user (username, password, nickname, email, status, create_time) VALUES
('admin', 'admin123', '系统管理员', 'admin@orin.com', 'ENABLED', NOW())
ON DUPLICATE KEY UPDATE password = VALUES(password), nickname = VALUES(nickname);

INSERT INTO sys_user (username, password, nickname, email, status, create_time) VALUES
('user', 'user123', '普通用户', 'user@orin.com', 'ENABLED', NOW())
ON DUPLICATE KEY UPDATE password = VALUES(password), nickname = VALUES(nickname);

-- 为管理员分配ROLE_ADMIN角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN';

-- 为普通用户分配ROLE_USER角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'user' AND r.role_code = 'ROLE_USER';
