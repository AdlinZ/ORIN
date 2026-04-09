-- V57__Add_integration_audit_log.sql
-- Audit log for external integration configuration changes

CREATE TABLE IF NOT EXISTS knowledge_integration_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT,
    integration_name VARCHAR(100),
    action VARCHAR(20) NOT NULL COMMENT 'CREATE, UPDATE, DELETE, HEALTH_CHECK, SYNC',
    operator VARCHAR(100),
    before_state TEXT COMMENT 'Masked config before change',
    after_state TEXT COMMENT 'Masked config after change',
    created_at DATETIME NOT NULL,
    INDEX idx_integration_id (integration_id),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
