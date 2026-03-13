-- 邮件发送日志表
CREATE TABLE IF NOT EXISTS sys_mail_send_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(200) NOT NULL,
    recipients VARCHAR(1000) NOT NULL,
    content TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message VARCHAR(500),
    mailer_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_mail_log_status ON sys_mail_send_log(status);
CREATE INDEX idx_mail_log_created_at ON sys_mail_send_log(created_at);
