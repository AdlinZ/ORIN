-- V45: Add department_id to sys_user table
-- MySQL 语法：需要先检查列是否存在
ALTER TABLE sys_user ADD COLUMN department_id BIGINT;
