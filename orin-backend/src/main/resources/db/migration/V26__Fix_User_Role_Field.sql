-- V26__Fix_User_Role_Field.sql
-- 同步 sys_user.role 字段基于 sys_user_role 表
UPDATE sys_user u
SET u.role = (
    SELECT r.role_code
    FROM sys_user_role ur
    JOIN sys_role r ON ur.role_id = r.role_id
    WHERE ur.user_id = u.user_id
    LIMIT 1
)
WHERE u.role IS NULL OR u.role = '';
