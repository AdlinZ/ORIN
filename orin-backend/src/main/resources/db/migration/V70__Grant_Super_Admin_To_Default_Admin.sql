-- 为默认 admin 账号补充超级管理员角色（幂等）

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ROLE_SUPER_ADMIN'
WHERE u.username = 'admin';

-- 同步 sys_user.role 字段，兼容仍读取单角色字段的场景
UPDATE sys_user
SET role = 'ROLE_SUPER_ADMIN'
WHERE username = 'admin';

