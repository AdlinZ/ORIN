-- 邮件模板表
CREATE TABLE IF NOT EXISTS sys_mail_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '模板代码',
    subject VARCHAR(200) NOT NULL COMMENT '邮件主题',
    content TEXT NOT NULL COMMENT '邮件内容',
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否默认模板',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_is_default (is_default)
) COMMENT '邮件模板表';
