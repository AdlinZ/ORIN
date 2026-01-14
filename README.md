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
- MySQL (可选)

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
```bash
cd orin-backend
mvn spring-boot:run
```
或打包后运行：
```bash
mvn clean package -DskipTests
java -jar target/orin-backend-0.0.1-SNAPSHOT.jar
```

#### 前端启动
```bash
cd orin-frontend
npm install
npm run dev
```

## 部署说明

### 前端生产环境部署
```bash
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
java -jar target/orin-backend-0.0.1-SNAPSHOT.jar
```

## API 接口

系统提供 RESTful API 接口，主要路径包括：
- `/api/agents` - 智能体管理
- `/api/knowledge` - 知识库管理
- `/api/models` - 模型管理
- `/api/monitor` - 监控接口
- `/api/runtime` - 运行时接口

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
