-- V4: 修复密码加密
-- 将之前迁移中可能存在的明文密码更新为 BCrypt 加密后的版本

UPDATE sys_user 
SET password = '$2a$10$SInbX6pqbyB40ZDCLYJgl..PihHe2h3KXgZFcfoZ7X7sQmai3vm2.' 
WHERE username = 'admin' AND password = 'admin123';

UPDATE sys_user
SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMye4qDy7iRKKFhp8hN.VnXqDvSZvHrW7C'
WHERE username = 'user' AND password = 'user123';
