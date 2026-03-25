-- V48: Update existing users with department assignments
-- 根据用户名或其他方式分配部门
UPDATE sys_user SET department_id = 1 WHERE username = 'admin';
UPDATE sys_user SET department_id = 2 WHERE username = 'user';
UPDATE sys_user SET department_id = 1 WHERE username = 'dept99';
UPDATE sys_user SET department_id = 3 WHERE username = 'lomboktest';
UPDATE sys_user SET department_id = 4 WHERE username = 'dtotest';
UPDATE sys_user SET department_id = 5 WHERE username = 'finaltest';
UPDATE sys_user SET department_id = 1 WHERE username = 'deptuser';