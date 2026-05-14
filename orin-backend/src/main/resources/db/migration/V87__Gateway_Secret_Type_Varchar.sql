-- V87: 让 gateway_secrets.secret_type 支持 MCP_ENV
--
-- 背景：V72 声明该列为 VARCHAR(40)，但 V72 用的是 CREATE TABLE IF NOT EXISTS，
-- 在表已由早期 ddl-auto 建出的环境里被跳过，真实列可能漂移成 MySQL ENUM，
-- 插入新值 MCP_ENV 时报 "Data truncated for column 'secret_type'"。
--
-- 修复：把列收敛为 V72 声明的 VARCHAR(40)，与实体 @Enumerated(EnumType.STRING)
-- 对齐。在已是 VARCHAR(40) 的环境里此语句是无害的 no-op，两类环境最终一致；
-- 后续新增 SecretType 枚举值不再需要 DDL。
ALTER TABLE gateway_secrets MODIFY COLUMN secret_type VARCHAR(40) NOT NULL;
