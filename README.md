# ORIN

> 当前定位：`Spring Boot + Vue 3 + Python AI Engine` 的智能体管理平台骨架，已具备部分主链路能力，但仍在做闭环收敛和平台化补完。

---

## 当前口径

不要把旧文档或旧页面中的“已完成”“已验收”“100%”直接当成现状。当前更准确的描述是：

- 平台骨架完整
- 智能体、知识库、工作流、协作、观测等模块都有入口
- 但不少链路仍处于“部分完成、需逐项核实、需补真实闭环”的状态

统一基线请先看：

- [docs/ORIN统一开发总计划.md](./docs/ORIN统一开发总计划.md)
- [docs/阶段0_改造基线.md](./docs/阶段0_改造基线.md)
- [docs/系统功能实现评估报告.md](./docs/系统功能实现评估报告.md)
- [docs/ORIN开发Agent执行说明.md](./docs/ORIN开发Agent执行说明.md)

额外提醒：

- 前端存在不少兼容路由、隐藏路由、同页复用路由和占位入口
- 后端存在多组接口前缀并存，不应假设所有系统能力都统一挂在一个 `/api/v1/system/*` 下
- 涉及工作流、协作、知识链路时，必须同时核对前端、Java 后端、Python AI 引擎三层

## 当前模块方向

### 🤖 智能体管理
- 智能体接入、配置、聊天、部分 provider 路由
- 统一 API 网关与平台管理接口并存
- 生命周期治理、版本管理、批处理能力仍需按代码逐项核实

### 📚 知识库
- 文档上传、解析、分块、向量化、检索具备基础入口
- 图谱页面已挂载，但菜单配置仍将其视为占位入口
- 多模态、同步治理仍需以实际链路为准
- 不应默认“页面存在 = 能力已交付”

### 🔄 工作流
- 可视化工作流编排
- Java 管理层 + Python 执行层双链路
- 复杂节点、协作节点、真实执行语义仍在补完

### 📊 监控与运维
- Trace、审计、Prometheus、Langfuse 等能力并存
- 当前目标是统一观测口径，而不是宣称已完成统一观测平台

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

# 2. 建议先看 docs/使用指南.md 和 docs/部署指南.md
# 3. 优先使用手动分开启动，manage.sh 更偏本机开发脚本
```

补充说明：

- `manage.sh` 默认假设本机有可用的 MySQL、且 AI 引擎使用 `orin-ai-engine/venv`
- 如果你的环境不是这套假设，优先按 [docs/部署指南.md](./docs/部署指南.md) 里的手动方式启动

### 部署

详见：

- [docs/部署指南.md](./docs/部署指南.md)
- [docs/I1.2-部署前检查清单.md](./docs/I1.2-部署前检查清单.md)

---

## 文档

- [docs/README.md](./docs/README.md)
- [docs/ORIN统一开发总计划.md](./docs/ORIN统一开发总计划.md)
- [docs/ORIN开发Agent执行说明.md](./docs/ORIN开发Agent执行说明.md)
- [docs/系统功能实现评估报告.md](./docs/系统功能实现评估报告.md)
- [docs/使用指南.md](./docs/使用指南.md)
- [docs/API文档.md](./docs/API文档.md)

---

## License

MIT
