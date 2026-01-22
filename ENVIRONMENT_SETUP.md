# ORIN 环境配置指南

本文档详细说明如何配置 ORIN 项目的环境变量和多环境部署。

---

## 目录

- [快速开始](#快速开始)
- [环境变量配置](#环境变量配置)
- [多环境部署](#多环境部署)
- [安全最佳实践](#安全最佳实践)
- [常见问题](#常见问题)

---

## 快速开始

### 1. 复制环境变量示例文件

```bash
cd orin-backend
cp .env.example .env
```

### 2. 编辑 `.env` 文件

使用文本编辑器打开 `.env` 文件，填入实际的配置值：

```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_NAME=orindb
DB_USERNAME=root
DB_PASSWORD=your_actual_password

# JWT密钥（生产环境必须修改）
JWT_SECRET=$(openssl rand -base64 64)
```

### 3. 启动应用

```bash
# 开发环境（默认）
mvn spring-boot:run

# 或指定环境
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

---

## 环境变量配置

### 必需的环境变量

以下环境变量在**生产环境**中必须设置：

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `DB_HOST` | 数据库主机地址 | `localhost` 或 `mysql.example.com` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_NAME` | 数据库名称 | `orindb` |
| `DB_USERNAME` | 数据库用户名 | `orin_user` |
| `DB_PASSWORD` | 数据库密码 | `secure_password_here` |
| `JWT_SECRET` | JWT签名密钥（至少256位） | 使用 `openssl rand -base64 64` 生成 |
| `REDIS_HOST` | Redis主机地址 | `localhost` 或 `redis.example.com` |
| `REDIS_PORT` | Redis端口 | `6379` |

### 可选的环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 激活的配置文件 | `dev` |
| `SERVER_PORT` | 服务器端口 | `8080` |
| `REDIS_PASSWORD` | Redis密码 | 空 |
| `JWT_EXPIRATION` | JWT过期时间（毫秒） | `86400000` (24小时) |
| `DIFY_DEFAULT_ENDPOINT` | Dify API端点 | `http://localhost:3000/v1` |
| `RATE_LIMIT_REQUESTS` | 速率限制请求数 | `100` |
| `RATE_LIMIT_PERIOD` | 速率限制周期（秒） | `60` |

---

## 多环境部署

ORIN 支持三种环境配置：

### 开发环境 (dev)

**配置文件**: `application-dev.properties`

**特点**:
- 启用详细的SQL日志
- DEBUG级别日志
- JPA使用 `update` 模式（自动更新数据库结构）
- 宽松的速率限制
- 暴露所有Actuator端点

**启动方式**:
```bash
# 方式1：使用Maven
mvn spring-boot:run

# 方式2：使用环境变量
SPRING_PROFILES_ACTIVE=dev java -jar target/orin-backend-1.0.0-SNAPSHOT.jar

# 方式3：在.env文件中设置
SPRING_PROFILES_ACTIVE=dev
```

### 生产环境 (prod)

**配置文件**: `application-prod.properties`

**特点**:
- 关闭SQL日志
- WARN级别日志
- JPA使用 `validate` 模式（仅验证，不修改数据库）
- 严格的速率限制
- 仅暴露必要的Actuator端点
- 强制使用环境变量（无默认值）
- 启用安全Cookie设置

**启动方式**:
```bash
# 设置环境变量后启动
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=your-db-host
export DB_PASSWORD=your-secure-password
export JWT_SECRET=$(openssl rand -base64 64)

java -jar target/orin-backend-1.0.0-SNAPSHOT.jar
```

### 测试环境 (test)

可以创建 `application-test.properties` 用于自动化测试。

---

## 安全最佳实践

### 1. 保护敏感信息

❌ **不要这样做**:
```properties
# application.properties
spring.datasource.password=password123
jwt.secret=my-secret-key
```

✅ **应该这样做**:
```properties
# application-prod.properties
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

### 2. 生成安全的JWT密钥

```bash
# 生成256位随机密钥
openssl rand -base64 64

# 或使用在线工具
# https://www.grc.com/passwords.htm
```

### 3. 使用环境变量管理工具

**推荐工具**:
- **Docker**: 使用 `docker-compose.yml` 的 `environment` 或 `.env` 文件
- **Kubernetes**: 使用 `ConfigMap` 和 `Secret`
- **云平台**: AWS Parameter Store, Azure Key Vault, Google Secret Manager
- **本地开发**: `direnv` 或 `.env` 文件

### 4. 版本控制规则

**`.gitignore` 中必须包含**:
```
.env
*.env
!.env.example
application-local.properties
```

### 5. 数据库安全

生产环境数据库配置建议：
- 使用专用数据库用户（不要使用root）
- 限制用户权限（仅授予必要的权限）
- 启用SSL连接
- 定期更换密码
- 使用强密码（至少16位，包含大小写字母、数字和特殊字符）

---

## 常见问题

### Q1: 如何在Docker中使用环境变量？

**docker-compose.yml**:
```yaml
version: '3.8'
services:
  orin-backend:
    image: orin-backend:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
    env_file:
      - .env.prod
```

### Q2: 启动时提示"Could not resolve placeholder"错误

**原因**: 生产环境配置中的环境变量未设置。

**解决方案**:
```bash
# 检查哪个环境变量缺失
grep '\${' src/main/resources/application-prod.properties

# 设置缺失的环境变量
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret
```

### Q3: 如何验证当前使用的配置？

启动应用后，查看日志：
```
The following profiles are active: prod
```

或访问Actuator端点（开发环境）：
```bash
curl http://localhost:8080/actuator/env
```

### Q4: 开发环境可以使用生产配置吗？

不建议。开发环境应该：
- 使用本地数据库
- 启用详细日志便于调试
- 使用宽松的安全设置

### Q5: 如何在IDE中设置环境变量？

**IntelliJ IDEA**:
1. Run → Edit Configurations
2. 选择你的Spring Boot配置
3. Environment variables: 添加 `SPRING_PROFILES_ACTIVE=dev;DB_PASSWORD=xxx`

**VS Code**:
在 `launch.json` 中添加：
```json
{
  "configurations": [
    {
      "type": "java",
      "env": {
        "SPRING_PROFILES_ACTIVE": "dev",
        "DB_PASSWORD": "password"
      }
    }
  ]
}
```

---

## 配置验证清单

部署前请确认：

- [ ] 所有必需的环境变量已设置
- [ ] JWT_SECRET使用了强随机密钥（生产环境）
- [ ] 数据库密码足够强壮
- [ ] `.env` 文件已添加到 `.gitignore`
- [ ] 生产环境使用 `validate` JPA模式
- [ ] 日志级别设置正确（生产环境为WARN或INFO）
- [ ] Redis连接信息正确
- [ ] Actuator端点配置安全（生产环境限制暴露）

---

## 相关文档

- [部署指南](docs/部署指南.md)
- [API文档](docs/API文档.md)
- [数据库迁移](DATABASE.md)

---

## 支持

如有问题，请提交Issue或联系开发团队。
