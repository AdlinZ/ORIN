# ORIN - 企业级 AI 智能体管理与监控平台

> 智能体管理、知识库向量检索、工作流编排、协作任务调度一体化平台

---

## 核心能力

### 🤖 智能体管理
- 多模型适配 (OpenAI / SiliconFlow / Dify)
- 智能体创建、配置、监控
- API Key 管理与限流

### 📚 知识库
- 文档上传、解析、分块、向量化
- 向量检索 (Milvus)
- 知识图谱构建与查询
- OCR/ASR 多模态支持

### 🔄 工作流
- 可视化工作流编排
- 串行/并行协作执行
- 执行状态追踪

### 📊 监控与运维
- 全链路追踪 (Trace ID)
- 资源监控 (CPU/内存/令牌)
- 告警通知 (邮件/钉钉/企微)
- 系统维护 (备份/升级/日志)

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MySQL + Redis |
| 前端 | Vue 3 + Vite + Element Plus |
| AI 引擎 | Python FastAPI |
| 向量库 | Milvus |
| 消息队列 | RabbitMQ |

---

## 快速开始

### 前置要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- (可选) Milvus 2.3+

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/AdlinZ/ORIN.git
cd ORIN

# 2. 启动后端
cd orin-backend
mvn spring-boot:run

# 3. 启动前端
cd orin-frontend
npm install
npm run dev
```

### 部署

详见 [部署前检查清单](./docs/I1.2-部署前检查清单.md)

---

## 模块能力矩阵

| 模块 | 能力 | 状态 |
|------|------|------|
| 智能体 | 创建/编辑/聊天/监控 | ✅ |
| 知识库 | 上传/解析/检索/图谱 | ✅ |
| 工作流 | 编排/执行/追踪 | ✅ |
| 协作 | 串行/并行/结果汇总 | ✅ |
| 监控 | 指标/告警/日志 | ✅ |
| 系统 | 维护/备份/配置 | ✅ |

---

## 文档

- [部署前检查清单](./docs/I1.2-部署前检查清单.md)
- [验收状态文档](./docs/验收状态文档.md)
- [任务分段书](./docs/任务分段书.md)

---

## License

MIT