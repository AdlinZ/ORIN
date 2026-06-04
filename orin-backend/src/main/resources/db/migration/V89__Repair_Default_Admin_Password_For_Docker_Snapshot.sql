-- Repair default local admin data for Docker schema snapshot databases.
-- The snapshot path may contain the development admin user with a plaintext
-- password but without role rows because historical seed migrations are marked
-- as already applied. Keep this migration narrow and idempotent.

INSERT INTO sys_role (role_code, role_name, description)
VALUES
    ('ROLE_ADMIN', '系统管理员', '拥有系统所有权限,可管理用户、配置、API等'),
    ('ROLE_USER', '普通用户', '普通用户权限'),
    ('ROLE_SUPER_ADMIN', '超级管理员', '拥有全局控制权限，可管理组织与平台全部能力')
ON DUPLICATE KEY UPDATE role_code = role_code;

UPDATE sys_user
SET password = '$2a$10$SInbX6pqbyB40ZDCLYJgl..PihHe2h3KXgZFcfoZ7X7sQmai3vm2.'
WHERE username = 'admin' AND password = 'admin123';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ROLE_ADMIN'
WHERE u.username = 'admin';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ROLE_SUPER_ADMIN'
WHERE u.username = 'admin';

UPDATE sys_user
SET role = 'ROLE_SUPER_ADMIN'
WHERE username = 'admin';
