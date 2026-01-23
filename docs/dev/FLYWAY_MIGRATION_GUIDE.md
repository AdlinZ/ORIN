# Flyway Database Migration Guide

## 概述

ORIN项目使用Flyway进行数据库版本管理，确保数据库变更的可追溯性和一致性。

---

## 目录结构

```
src/main/resources/db/migration/
├── V1__Initial_schema.sql          # 初始数据库结构
├── V2__Add_xxx_table.sql           # 添加新表
├── V3__Alter_xxx_column.sql        # 修改表结构
└── R__Create_views.sql             # 可重复执行的脚本
```

---

## 命名规范

### 版本化迁移 (Versioned Migrations)

格式: `V{version}__{description}.sql`

- **V**: 固定前缀，表示版本化迁移
- **version**: 版本号，使用数字，可以是 `1`, `2`, `1.1`, `2.5` 等
- **__**: 两个下划线分隔版本号和描述
- **description**: 描述性名称，使用下划线分隔单词

**示例**:
```
V1__Initial_schema.sql
V2__Add_user_profile_table.sql
V2.1__Add_user_avatar_column.sql
V3__Create_indexes_for_performance.sql
```

### 可重复迁移 (Repeatable Migrations)

格式: `R__{description}.sql`

- **R**: 固定前缀，表示可重复迁移
- **__**: 两个下划线
- **description**: 描述性名称

**示例**:
```
R__Create_views.sql
R__Update_stored_procedures.sql
```

---

## 迁移脚本编写规范

### 1. 使用IF NOT EXISTS

```sql
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL
);
```

### 2. 添加索引

```sql
CREATE INDEX idx_username ON user(username);
CREATE INDEX idx_created_at ON user(created_at);
```

### 3. 修改表结构

```sql
-- 添加列
ALTER TABLE user ADD COLUMN email VARCHAR(255);

-- 修改列
ALTER TABLE user MODIFY COLUMN username VARCHAR(500);

-- 删除列（谨慎使用）
ALTER TABLE user DROP COLUMN temp_column;
```

### 4. 数据迁移

```sql
-- 更新现有数据
UPDATE user SET status = 'ACTIVE' WHERE status IS NULL;

-- 插入默认数据
INSERT INTO role (role_name, description) VALUES
('ROLE_ADMIN', 'Administrator'),
('ROLE_USER', 'Regular User')
ON DUPLICATE KEY UPDATE role_name=role_name;
```

### 5. 添加注释

```sql
-- ============================================
-- User Management Tables
-- ============================================

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(255) NOT NULL COMMENT '用户名',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

---

## 迁移流程

### 开发环境

1. **创建新的迁移脚本**
   ```bash
   # 在 src/main/resources/db/migration/ 目录下创建
   touch V2__Add_new_feature.sql
   ```

2. **编写SQL脚本**
   ```sql
   CREATE TABLE IF NOT EXISTS new_table (
       id BIGINT AUTO_INCREMENT PRIMARY KEY,
       name VARCHAR(255) NOT NULL
   );
   ```

3. **启动应用**
   ```bash
   mvn spring-boot:run
   ```
   Flyway会自动执行新的迁移脚本。

4. **验证迁移**
   ```sql
   -- 查看迁移历史
   SELECT * FROM flyway_schema_history ORDER BY installed_rank;
   ```

### 生产环境

1. **备份数据库**
   ```bash
   mysqldump -u root -p orindb > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **验证迁移脚本**
   - 在测试环境先执行
   - 确认无错误

3. **部署新版本**
   ```bash
   # 设置环境变量
   export SPRING_PROFILES_ACTIVE=prod
   
   # 启动应用，Flyway自动执行迁移
   java -jar orin-backend.jar
   ```

4. **监控迁移日志**
   ```bash
   tail -f backend.log | grep Flyway
   ```

---

## Flyway命令

### 查看迁移状态

```bash
mvn flyway:info
```

### 验证迁移

```bash
mvn flyway:validate
```

### 清理数据库（仅开发环境）

```bash
mvn flyway:clean
```

> ⚠️ **警告**: `flyway:clean` 会删除所有数据，仅在开发环境使用！

### 修复迁移

```bash
mvn flyway:repair
```

---

## 常见场景

### 场景1: 添加新表

**V2__Add_notification_table.sql**:
```sql
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    read_status BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_read_status (read_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 场景2: 添加列

**V3__Add_user_phone_column.sql**:
```sql
ALTER TABLE user ADD COLUMN phone VARCHAR(20) AFTER email;
CREATE INDEX idx_phone ON user(phone);
```

### 场景3: 修改列类型

**V4__Modify_description_column.sql**:
```sql
ALTER TABLE agent_metadata MODIFY COLUMN description LONGTEXT;
```

### 场景4: 创建索引

**V5__Create_performance_indexes.sql**:
```sql
CREATE INDEX idx_agent_status ON agent_metadata(status, created_at);
CREATE INDEX idx_workflow_execution ON workflow_execution(workflow_id, status, start_time);
```

### 场景5: 数据迁移

**V6__Migrate_old_data.sql**:
```sql
-- 迁移旧数据到新表
INSERT INTO new_user_profile (user_id, profile_data)
SELECT user_id, CONCAT('{"name":"', username, '"}')
FROM user
WHERE NOT EXISTS (
    SELECT 1 FROM new_user_profile WHERE new_user_profile.user_id = user.user_id
);
```

---

## 最佳实践

### 1. 永远不要修改已执行的迁移

❌ **错误做法**:
```sql
-- V1__Initial_schema.sql (已执行)
-- 后来修改了这个文件
CREATE TABLE user (
    id BIGINT,
    name VARCHAR(100)  -- 修改了字段
);
```

✅ **正确做法**:
```sql
-- V2__Modify_user_table.sql (新建文件)
ALTER TABLE user MODIFY COLUMN name VARCHAR(255);
```

### 2. 使用事务

```sql
-- 开启事务
START TRANSACTION;

-- 执行变更
ALTER TABLE user ADD COLUMN status VARCHAR(50);
UPDATE user SET status = 'ACTIVE';

-- 提交事务
COMMIT;
```

### 3. 向后兼容

```sql
-- 添加列时设置默认值
ALTER TABLE user ADD COLUMN status VARCHAR(50) DEFAULT 'ACTIVE';

-- 而不是
ALTER TABLE user ADD COLUMN status VARCHAR(50) NOT NULL;  -- 可能导致现有数据失败
```

### 4. 测试迁移

- 在开发环境测试
- 在测试环境验证
- 准备回滚方案

### 5. 文档化

```sql
-- ============================================
-- Migration: V5__Add_notification_system
-- Author: Developer Name
-- Date: 2026-01-22
-- Description: Add notification tables and triggers
-- ============================================
```

---

## 故障排查

### 问题1: 迁移失败

**错误信息**:
```
Migration V2__xxx.sql failed
```

**解决方案**:
1. 检查SQL语法
2. 查看详细错误日志
3. 使用 `flyway:repair` 修复
4. 手动修正数据库状态

### 问题2: 校验和不匹配

**错误信息**:
```
Migration checksum mismatch for migration version 2
```

**原因**: 已执行的迁移文件被修改

**解决方案**:
```bash
# 修复迁移历史
mvn flyway:repair
```

### 问题3: 迁移被跳过

**原因**: 版本号不连续或已存在

**解决方案**:
- 检查版本号顺序
- 使用 `flyway:info` 查看状态

---

## 监控和维护

### 查看迁移历史

```sql
SELECT 
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 清理旧的迁移记录（谨慎）

```sql
-- 仅在确认迁移已成功且不再需要时
DELETE FROM flyway_schema_history WHERE version = '1.0';
```

---

## 相关资源

- [Flyway官方文档](https://flywaydb.org/documentation/)
- [SQL最佳实践](https://www.sqlstyle.guide/)
- [MySQL性能优化](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)

---

## 总结

Flyway为ORIN项目提供了：
- ✅ 数据库版本控制
- ✅ 自动化迁移
- ✅ 团队协作支持
- ✅ 生产环境安全部署

遵循本指南的规范，可以确保数据库变更的安全性和可维护性。
