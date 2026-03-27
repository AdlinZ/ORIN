# ORIN - 智能体监控与管理系统

ORIN (Advanced Agent Management & Monitoring System) 是一个基于前后端分离架构的企业级 AI 智能体管理与监控平台。作为连接 AI 模型（如 Dify、SiliconFlow）与业务系统的桥梁，它实现了全链路监控、知识库自动化同步、多模型智能调度及可视化工作流编排。

## 当前实现说明

当前仓库的真实实现架构为：

- `orin-backend`: Spring Boot 主后端，负责智能体、知识库、工作流、监控、任务、权限、审计与统一 API 网关
- `orin-frontend`: Vue 3 管理端
- `orin-ai-engine`: Python FastAPI 执行引擎，负责部分工作流/DSL 执行

项目早期需求来源于基于 Yuxi-Know 的设计方案，但当前代码并不是“纯 Yuxi-Know/FastAPI 单体实现”。后续开发与改造请以仓库中的实际结构为准，详细基线见 [docs/阶段0_改造基线.md](docs/阶段0_改造基线.md)。

文档入口已整理，建议优先从 [docs/README.md](docs/README.md) 开始阅读，避免误用历史报告或阶段性文档。

## 项目结构

```
├── orin-backend/           # 后端 Spring Boot 项目
│   ├── src/main/
│   │   ├── java/com/adlin/orin/
│   │   │   ├── config/           # 配置类
│   │   │   ├── modules/          # 业务模块
│   │   │   │   ├── agent/        # 智能体管理模块
│   │   │   │   ├── knowledge/    # 知识库管理模块 (含 Milvus 向量引擎)
│   │   │   │   ├── workflow/     # 工作流编排模块 (可视化编辑器)
│   │   │   │   ├── skill/        # 智能体技能插件
│   │   │   │   ├── alert/        # 告警通知模块 (邮件、钉钉、企微)
│   │   │   │   ├── ragflow/      # RAGFlow 知识库集成模块
│   │   │   │   ├── model/        # 模型管理模块 (含 Kimi 集成)
│   │   │   │   ├── monitor/      # 监控模块
│   │   │   │   ├── multimodal/   # 多模态处理模块
│   │   │   │   ├── trace/        # 链路追踪模块
│   │   │   │   ├── apikey/       # 接口密钥管理
│   │   │   │   ├── audit/        # 审计日志模块
│   │   │   │   ├── runtime/      # 运行时管理模块
│   │   │   │   └── system/       # 系统管理模块
│   │   │   └── OrinApplication.java # 应用主类
│   │   └── resources/
│   │       ├── db/migration/     # Flyway 数据库迁移脚本
│   └── pom.xml
├── orin-ai-engine/           # Python AI 执行引擎 (FastAPI)
│   ├── app/
│   │   ├── engine/           # 核心引擎 (含各节点处理器)
│   │   ├── models/           # Pydantic 数据模型
│   │   └── api/              # API 路由
│   ├── tests/                # 测试用例
│   └── main.py               # 入口文件
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
│   │   └── views/          # 页面组件 (含可视化工作流编辑器)
│   ├── public/             # 静态资源
│   ├── package.json
│   └── vite.config.js
├── docs/                   # 项目文档
│   ├── dev/                # 开发指南
│   ├── README.md            # 文档索引与阅读顺序
│   ├── 使用指南.md          # 当前功能入口与联调方式
│   ├── 部署指南.md          # 开发/生产部署说明
│   ├── API文档.md           # 接口导航与 OpenAPI 入口
│   └── 系统功能实现评估报告.md # 当前能力边界评估
├── scripts/                # 辅助脚本
└── manage.sh               # 一键管理脚本
```

## 功能特性

### 核心功能状态

> [!NOTE]
> 以下功能状态标签：`已上线` - 功能完整实现 | `内测` - 核心功能完成但细节待完善 | `开发中` - 主要结构完成但不可用 | `占位` - 仅有入口无实际功能

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 全链路监控 | `已上线` | CPU/内存/令牌消耗追踪，Actuator + Prometheus 集成 |
| 知识库管理 | `已上线` | 上传、解析、检索与同步主链路已具备，扩展能力成熟度不一 |
| 智能体管理 | `已上线` | 接入控制、交互、统一网关与基础管理能力已具备 |
| 工作流编排 | `内测` | 可视化编辑器、DSL 执行，节点处理器部分实现 |
| 监控告警 | `内测` | 阈值监控、邮件/钉钉/企微通知，规则配置待完善 |
| 系统设置 | `内测` | 基础设置、邮件配置、模型默认参数 |
| 模型管理 | `开发中` | 多模型适配框架，OpenAI 兼容接口转发 |
| 审计日志 | `开发中` | 会话流水记录，全站事件溯源框架 |
| 知识图谱 | `开发中` | 已有页面与后端模型/仓储实现，仍需闭环验证 |
| 多模态处理 | `开发中` | OCR/ASR 已有实现，强依赖外部配置与降级策略 |
| 媒体中心 | `占位` | 暂无实际功能 |

### 已上线功能详情

- **全链路监控**：实时追踪 CPU、内存利用率及令牌消耗，集成 Micrometer 链路追踪与 Trace ID 溯源，秒级洞察系统性能瓶颈
- **知识库管理**：支持文档上传、解析、分块、向量检索及多源同步入口
- **智能体生命周期**：从接入到注销，一站式控制不同环境下的智能体运行状态
- **交互日志审计**：保留会话流水与事件溯源入口，具体覆盖深度需结合模块确认

### 开发中功能详情

- **可视化工作流**（`内测`）：支持编排 AI 工作流，提供节点处理器：
    - **逻辑控制**：代码执行 (Python)、条件分支、变量聚合
    - **数据处理**：模板转换 (Jinja2)、参数提取 (JSON)、知识检索
    - **工具交互**：HTTP 请求 (HTTpx)、列表操作、直接回复
    - **预览与调试**：工作流实时预览，执行轨迹可视化

- **智能告警中心**（`内测`）：支持多指标阈值监控，自动触发邮件、钉钉、企业微信异步通知

- **多模型平台适配**（`开发中`）：统一适配 Dify、OpenAI、SiliconFlow (硅基流动)、Kimi (月之暗面) 等主流模型提供商

- **接口密钥管理**（`开发中`）：密钥托管、多租户权限隔离及 API Key 配额控制

## 技术栈

### 后端
- **主控后端**: Spring Boot 3.2.1
- **持久层**: Spring Data JPA + Flyway (数据库迁移)
- **数据库**: MySQL 8.0, Milvus (向量引擎), Redis (缓存/限流)
- **其他**: Maven, Hibernate, MapStruct, Micrometer Tracing (Zipkin 可选)
- **通知**: JavaMailSender, DingTalk/WeChat Webhook

### AI 引擎 (Python)
- **角色**: 独立工作流/DSL 执行引擎
- **核心**: Python 3.10+, FastAPI
- **执行器**: 异步图执行架构 (Asyncio Graph Executor)
- **模板**: Jinja2
- **通信**: HTTpx
- **智能**: OpenAI SDK (多模型兼容)

### 前端
- **框架**: Vue 3 + Vite
- **UI 库**: Element Plus
- **状态管理**: Pinia
- **图表库**: ECharts
- **编辑器**: LogicFlow / Vue-Flow (工作流可视化)
- **其他**: Axios, js-cookie

## 快速开始

### 环境要求
- Java 17+
- Node.js 16+
- Python 3.10+
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

#### 3. AI 引擎启动 (Python)

AI 引擎使用 [Poetry](https://python-poetry.org/) 管理依赖。

```bash
cd orin-ai-engine

# 如果是首次安装（需要安装 Poetry）
# pip install poetry
# poetry install

# 使用预配置的 venv（推荐）
./venv/bin/python -m poetry install

# 启动服务
./venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 一键启动 (本地管理)

项目根目录下提供了 `manage.sh` 脚本用于本地开发时的管理：

```bash
# 启动前后端
./manage.sh start

# 停止服务
./manage.sh stop

# 查看状态
./manage.sh status
```

### 一键部署 (Ubuntu 生产环境)

如果您是在一台**空白的 Ubuntu 服务器**上进行部署，可以使用 `deploy_ubuntu.sh` 脚本。它会自动安装 JDK、Node.js、MySQL、Redis、Docker、Nginx 等所有依赖并配置 Systemd 服务。

```bash
# 赋予执行权限
chmod +x deploy_ubuntu.sh

# 以 root 权限运行
sudo ./deploy_ubuntu.sh
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

系统当前同时提供业务接口和 OpenAI 兼容网关，常见入口包括：

- `/api/v1/agents/*` - 智能体管理与交互
- `/api/v1/knowledge/*` - 知识库与同步
- `/api/workflows/*`、`/api/v1/workflow/*` - 工作流管理与执行
- `/api/traces/*` - Trace 查询
- `/api/v1/system/*` - 系统配置与运维
- `/v1/*` - OpenAI 兼容网关

## 文档资源

- **部署与运维**:
  - [文档索引](docs/README.md)
  - [部署指南](docs/部署指南.md)
  - [环境配置指南](ENVIRONMENT_SETUP.md)
  - [使用指南](docs/使用指南.md)

- **开发指南**:
  - [API文档](docs/API文档.md)
  - [DTO转换指南](docs/dev/DTO_MAPPING_GUIDE.md)
  - [异常处理指南](docs/dev/EXCEPTION_HANDLING_GUIDE.md)
  - [Flyway迁移指南](docs/dev/FLYWAY_MIGRATION_GUIDE.md)
  - [RAGFlow集成指南](docs/dev/RAGFLOW_INTEGRATION.md)
  - [Kimi集成指南](docs/dev/KIMI_INTEGRATION.md)


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
