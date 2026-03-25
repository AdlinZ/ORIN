-- V47: Add department_id to sys_role table
ALTER TABLE sys_role ADD COLUMN department_id BIGINT;

-- Assign default roles to different departments
-- ROLE_ADMIN -> 技术部 (DEPT_TECH = 1)
UPDATE sys_role SET department_id = 1 WHERE role_code = 'ROLE_ADMIN';

-- ROLE_USER -> 产品部 (DEPT_PRODUCT = 2)
UPDATE sys_role SET department_id = 2 WHERE role_code = 'ROLE_USER';