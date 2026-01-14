-- 创建角色表
CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(50) UNIQUE NOT NULL COMMENT '角色代码: ROLE_ADMIN, ROLE_USER',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 创建用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 初始化默认角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ROLE_ADMIN', '系统管理员', '拥有系统所有权限,可管理用户、配置、API等')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);

INSERT INTO sys_role (role_code, role_name, description) VALUES
('ROLE_USER', '普通用户', '基础访问权限,可查看监控、知识库等')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);

-- 为现有用户分配默认角色(如果sys_user表已有数据)
-- 假设user_id=1是管理员
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT 1, role_id FROM sys_role WHERE role_code = 'ROLE_ADMIN';

-- 其他用户默认为普通用户
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id 
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.role_code = 'ROLE_USER'
AND u.user_id > 1
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id
);
