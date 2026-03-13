-- V17__Add_Agent_Metrics.sql
-- 智能体监控指标表
-- 用于记录 AI 智能体的运行状态指标

CREATE TABLE IF NOT EXISTS `agent_metrics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `agent_id` VARCHAR(255) NOT NULL,
    `timestamp` BIGINT NOT NULL,
    `cpu_usage` DOUBLE,
    `memory_usage` DOUBLE,
    `response_latency` INT,
    `token_cost` INT,
    `daily_requests` INT,
    PRIMARY KEY (`id`),
    INDEX `idx_agent_time` (`agent_id`, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
