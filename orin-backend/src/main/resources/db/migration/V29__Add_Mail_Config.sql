-- 邮件服务配置表
CREATE TABLE IF NOT EXISTS sys_mail_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    smtp_host VARCHAR(100) DEFAULT 'smtp.mailersend.net',
    smtp_port INT DEFAULT 587,
    username VARCHAR(100),
    password VARCHAR(200),
    from_email VARCHAR(100),
    from_name VARCHAR(100),
    ssl_enabled BOOLEAN DEFAULT TRUE,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 初始化数据
INSERT INTO sys_mail_config (smtp_host, smtp_port, ssl_enabled, enabled) VALUES
('smtp.mailersend.net', 587, TRUE, FALSE);
