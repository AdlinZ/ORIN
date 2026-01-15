-- ORIN 数据库初始化脚本
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS orindb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE orindb;

-- 注意：表结构由 JPA 自动创建（spring.jpa.hibernate.ddl-auto=update）
-- 此脚本主要用于确保数据库存在和字符集正确

-- 可选：创建默认管理员用户（如果需要）
-- INSERT INTO users (username, password, email, role, created_at, updated_at) 
-- VALUES ('admin', '$2a$10$...', 'admin@orin.com', 'ADMIN', NOW(), NOW())
-- ON DUPLICATE KEY UPDATE username=username;
