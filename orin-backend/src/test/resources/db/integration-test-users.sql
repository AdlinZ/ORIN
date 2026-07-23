-- ============================================================
-- F02 E2E 测试账号种子数据
-- ============================================================
-- 运行前确保数据库已通过 Flyway 迁移到最新版本。
-- 密码已用 BCrypt (cost=10) 编码为 "test123"。
--
-- 应用方式（本地）:
--   mysql -h 127.0.0.1 -u orin -p orindb < orin-backend/src/test/resources/db/integration-test-users.sql
--
-- 账号:
--   test-creator / test123 — 仅 ROLE_USER（普通 Creator）
--   test-operator / test123 — ROLE_OPERATOR（业务运营）
--   test-creator-2 / test123 — 仅 ROLE_USER（第二个普通用户，用于跨用户权限测试）
-- ============================================================

-- Roles (idempotent)
INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES
    ('ROLE_USER', '普通用户', '基础访问权限'),
    ('ROLE_OPERATOR', '业务运营', '智能体业务配置、知识资产管理、工作流编排运营');

-- Users (idempotent — 使用固定 user_id 以便测试断言)
-- BCrypt hash of "test123" with cost 10 — deterministic for test use only
-- The actual hash will differ per runtime; use a known hash or insert programmatically.
-- For manual seed: generate the hash at runtime, or use the backend's UserService.
--
-- NOTE: These INSERTs will fail if the user already exists with a different password.
-- Use the programmatic approach in BaseIntegrationTest.java instead for automated tests.
-- This file is for documentation and manual DB seeding before browser E2E runs.

-- To create test users via API (preferred for browser E2E setup):
-- 1. Login as admin
-- 2. POST /api/v1/users with { "username": "test-creator", "password": "test123", "role": "ROLE_USER" }
-- 3. POST /api/v1/users with { "username": "test-operator", "password": "test123", "role": "ROLE_OPERATOR" }
-- 4. POST /api/v1/users with { "username": "test-creator-2", "password": "test123", "role": "ROLE_USER" }

-- Alternative: direct SQL INSERT with BCrypt-encoded password
-- Replace $2a$10$... below with the output of:
--   mvn exec:java -Dexec.mainClass="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder" -Dexec.args="test123"
--
-- INSERT IGNORE INTO sys_user (user_id, username, password, status) VALUES
--     (100, 'test-creator', '$2a$10$REPLACE_WITH_ACTUAL_HASH', 'ENABLED'),
--     (101, 'test-operator', '$2a$10$REPLACE_WITH_ACTUAL_HASH', 'ENABLED'),
--     (102, 'test-creator-2', '$2a$10$REPLACE_WITH_ACTUAL_HASH', 'ENABLED');
--
-- INSERT IGNORE INTO sys_user_role (user_id, role_id)
--     SELECT 100, role_id FROM sys_role WHERE role_code = 'ROLE_USER'
--     UNION ALL
--     SELECT 101, role_id FROM sys_role WHERE role_code = 'ROLE_OPERATOR'
--     UNION ALL
--     SELECT 102, role_id FROM sys_role WHERE role_code = 'ROLE_USER';
