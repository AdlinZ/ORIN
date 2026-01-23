# ORIN - 智能体监控与管理系统

ORIN (Advanced Agent Management & Monitoring System) 是一个基于前后端分离架构的企业级AI智能体治理平台，连接Dify与业务系统的桥梁，实现全链路监控、知识库同步与模型智能调度。

## 项目结构

```
├── orin-backend/           # 后端 Spring Boot 项目
│   ├── src/main/
│   │   ├── java/com/adlin/orin/
│   │   │   ├── config/           # 配置类
│   │   │   ├── modules/          # 业务模块
│   │   │   │   ├── agent/        # 智能体管理模块
│   │   │   │   ├── knowledge/    # 知识库管理模块
│   │   │   │   ├── model/        # 模型管理模块
│   │   │   │   ├── monitor/      # 监控模块
│   │   │   │   ├── runtime/      # 运行时管理模块
│   │   │   │   └── system/       # 系统管理模块
│   │   │   └── OrinApplication.java # 应用主类
│   │   └── resources/
│   └── pom.xml
├── orin-frontend/          # 前端 Vue 3 + Vite 项目
│   ├── src/
│   │   ├── api/            # API 接口定义
│   │   ├── assets/         # 静态资源
│   │   ├── components/     # 公共组件
│   │   ├── composables/    # 组合式 API
│   │   ├── layout/         # 布局组件
│   │   ├── router/         # 路由配置
│   │   ├── stores/         # Pinia 状态管理
│   │   ├── utils/          # 工具函数
│   │   └── views/          # 页面组件
│   ├── public/             # 静态资源
│   ├── package.json
│   └── vite.config.js
├── docs/                   # 项目文档
│   ├── dev/                # 开发指南
│   ├── archive/            # 历史记录
│   └── 部署指南.md          # 详细部署文档
├── scripts/                # 辅助脚本
└── manage.sh               # 一键管理脚本
```

## 功能特性

### 核心功能
- **全链路监控**：实时追踪 CPU、内存利用率及令牌消耗，秒级洞察系统性能瓶颈
- **知识库自动同步**：深度集成 Dify 知识库，支持文档版本管理与云端动态资产更新
- **分布式生命周期**：从接入到注销，一站式控制不同环境下的智能体运行状态
- **交互日志审计**：全面保留会话流水与逻辑树，支持全站事件溯源与安全合规审查
- **模型安全管理**：密钥托管与多租户权限隔离，确保留言资产与模型接口访问安全
- **开放协同生态**：标准 Webhook 接口与 API 扩展，轻松对接现有 DevOps 运维体系

### 监控功能
- 实时性能监控
- 智能体状态跟踪
- 系统健康度评估
- 资源使用统计
- 活动日志展示

### 管理功能
- 智能体接入管理
- 知识库管理
- 模型配置
- 训练管理
- 权限控制

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.1
- **持久层**: Spring Data JPA
- **数据库**: MySQL / H2 (内存)
- **其他**: Maven, Hibernate

### 前端
- **框架**: Vue 3 + Vite
- **UI 库**: Element Plus
- **状态管理**: Pinia
- **图表库**: ECharts
- **其他**: Axios, js-cookie

## 快速开始

### 环境要求
- Java 17+
- Node.js 16+
- npm 或 yarn
- MySQL 8.0+
- Redis 6.0+ (可选，用于缓存和限流)

### 环境配置

> [!IMPORTANT]
> 首次运行前，必须配置环境变量。详细说明请参考 [环境配置指南](ENVIRONMENT_SETUP.md)

#### 1. 配置后端环境变量

```bash
cd orin-backend

# 复制环境变量示例文件
cp .env.example .env

# 编辑.env文件，填入实际配置
# 至少需要配置：DB_PASSWORD 和 JWT_SECRET
nano .env
```

**生成安全的JWT密钥**:
```bash
# 生成256位随机密钥
openssl rand -base64 64
```

#### 2. 创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE orindb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'orin_user'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON orindb.* TO 'orin_user'@'localhost';
FLUSH PRIVILEGES;
```

### 一键启动

项目根目录下提供了 `manage.sh` 脚本用于一键管理：

```bash
# 启动前后端
./manage.sh start

# 停止服务
./manage.sh stop

# 重启系统
./manage.sh restart

# 查看状态
./manage.sh status
```

### 手动启动

#### 后端启动

**开发环境**:
```bash
cd orin-backend
mvn spring-boot:run
```

**生产环境**:
```bash
cd orin-backend
mvn clean package -DskipTests

# 设置环境变量
export SPRING_PROFILES_ACTIVE=prod
export DB_PASSWORD=your_secure_password
export JWT_SECRET=your_jwt_secret

java -jar target/orin-backend-1.0.0-SNAPSHOT.jar
```

#### 前端启动

```bash
cd orin-frontend
npm install
npm run dev
```

访问 http://localhost:5173 查看前端界面。

## 部署说明

> [!CAUTION]
> 生产环境部署前，请务必阅读 [环境配置指南](ENVIRONMENT_SETUP.md) 和以下安全注意事项。

### 安全检查清单

部署到生产环境前，请确认：

- [ ] 已修改默认的JWT密钥（使用强随机密钥）
- [ ] 数据库密码足够强壮（至少16位）
- [ ] 已设置 `SPRING_PROFILES_ACTIVE=prod`
- [ ] JPA配置为 `validate` 模式（不会修改数据库结构）
- [ ] 日志级别设置为 `WARN` 或 `INFO`
- [ ] `.env` 文件未提交到版本控制
- [ ] 已配置防火墙规则
- [ ] 已启用HTTPS

### 前端生产环境部署

```bash
cd orin-frontend
npm run build
```

将生成的 `dist/` 目录内容部署到 Web 服务器（如 Nginx）

Nginx 示例配置：
```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080/api; # 转发到后端
        proxy_set_header Host $host;
    }
}
```

### 后端生产环境部署
使用 Maven 打包后直接运行：
```bash
mvn clean package -DskipTests
java -jar target/orin-backend-1.0.0-SNAPSHOT.jar
```

## API 接口

系统提供 RESTful API 接口，主要路径包括：
- `/api/agents` - 智能体管理
- `/api/knowledge` - 知识库管理
- `/api/models` - 模型管理
- `/api/monitor` - 监控接口
- `/api/runtime` - 运行时接口

## 文档资源

- **部署与运维**:
  - [部署指南](docs/部署指南.md)
  - [环境配置指南](ENVIRONMENT_SETUP.md)
  - [使用指南](docs/使用指南.md)

- **开发指南**:
  - [API文档](docs/API文档.md)
  - [DTO转换指南](docs/dev/DTO_MAPPING_GUIDE.md)
  - [异常处理指南](docs/dev/EXCEPTION_HANDLING_GUIDE.md)
  - [Flyway迁移指南](docs/dev/FLYWAY_MIGRATION_GUIDE.md)


## 开发规范

### 前端样式规范
- 使用 CSS 变量系统进行统一的颜色和尺寸管理
- 组件样式遵循 Element Plus 设计规范
- 深色/浅色模式兼容
- 响应式设计

### 后端代码规范
- Controller 层负责请求处理
- Service 层负责业务逻辑
- Repository 层负责数据访问
- 统一异常处理
- 日志记录

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 支持

如果您发现任何问题或有任何改进建议，请提交 Issue 或 Pull Request。
