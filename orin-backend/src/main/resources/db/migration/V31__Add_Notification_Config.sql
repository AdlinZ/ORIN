-- 通知配置表
CREATE TABLE IF NOT EXISTS alert_notification_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    email_recipients VARCHAR(500),
    dingtalk_enabled BOOLEAN DEFAULT FALSE,
    dingtalk_webhook VARCHAR(500),
    wecom_enabled BOOLEAN DEFAULT FALSE,
    wecom_webhook VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 初始化默认配置
INSERT INTO alert_notification_config (email_enabled, email_recipients, dingtalk_enabled, wecom_enabled)
VALUES (TRUE, '', FALSE, FALSE);
