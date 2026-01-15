# ORIN MySQL 配置说明

## 数据库要求

项目使用 MySQL 作为持久化数据库，确保数据不会丢失。

### 前置条件

1. **安装 MySQL**
   ```bash
   # macOS (使用 Homebrew)
   brew install mysql
   
   # 启动 MySQL 服务
   brew services start mysql
   ```

2. **初始化数据库**
   ```bash
   # 登录 MySQL
   mysql -u root -p
   
   # 执行初始化脚本
   source src/main/resources/db/init.sql
   ```

### 配置说明

在 `application.properties` 中的数据库配置：

```properties
# 数据库连接
spring.datasource.url=jdbc:mysql://localhost:3306/orindb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=password

# JPA 配置
spring.jpa.hibernate.ddl-auto=update  # 自动更新表结构
spring.jpa.show-sql=true              # 显示 SQL 语句
```

### 修改数据库密码

如果你的 MySQL root 密码不是 `password`，请修改 `application.properties` 中的：
```properties
spring.datasource.password=你的密码
```

### 数据持久性

- ✅ 所有数据存储在 MySQL 数据库中
- ✅ 重启应用后数据不会丢失
- ✅ 表结构自动创建和更新
- ✅ 支持数据备份和恢复

### 数据备份

```bash
# 备份数据库
mysqldump -u root -p orindb > backup.sql

# 恢复数据库
mysql -u root -p orindb < backup.sql
```
