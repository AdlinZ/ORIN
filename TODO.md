# ORIN 项目待办事项 (TODO)

## 🚀 即刻任务
- [x] **开启 Mock 模式**：已将 `MockVectorStoreProvider` 设为首选实现，目前无需安装 Milvus 即可流畅运行知识库的增删改查。
- [x] **修复知识库删除**：已解决因 Milvus 不在线导致的删除挂起和 500 错误。
- [x] **修复计数显示**：知识库列表现在实时计算文档数量。

## 🛠️ 环境配置 (重要)
- [ ] **配置 Docker 版 Milvus**：
    - [ ] 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)。
    - [ ] 在项目根目录创建 `docker-compose.yml` (或使用官方 Standalone 模板)。
    - [ ] 运行 `docker-compose up -d` 启动向量数据库。
    - [ ] 验证端口 `19530` 是否连通。

## 💻 功能增强
- [ ] **新增：向量数据库配置页面**：
    - [ ] 在前端"系统设置"中增加"向量检索配置"子项。
    - [ ] 支持在线修改 Milvus 地址 (`host`)、端口 (`port`) 和 Token。
    - [ ] 增加"联通性检查"按钮，点击后后端尝试 ping Milvus 并返回状态。
- [ ] **切换真实/Mock 模式**：
    - [ ] 优化后端配置，支持从 `application.properties` 或数据库动态切换向量引擎，无需修改代码。

## 🧪 长期优化
- [ ] **知识库分片策略**：完善 UNSTRUCTURED 类型的 PDF/Word 自动分段逻辑。
- [ ] **多模态支持**：对接 VLM 模型实现图片知识库的检索。

## 🤖 RAGFlow 知识库集成
- [x] **RAGFlow 集成**：已实现 `RAGFlowConfig` 配置管理、`RAGFlowIntegrationService` 服务接口，支持知识库同步、文档上传、向量化检索。
- [x] **多知识库适配**：统一 Dify 和 RAGFlow 的知识库接口，支持无缝切换。
- [x] **文档管理增强**：支持 RAGFlow 文档上传、分块策略配置、检索测试。

## 🧠 Kimi 大模型集成
- [x] **Kimi API 集成**：已实现 `KimiIntegrationService` 服务、`KimiAgentManageService` 智能体管理，支持 Kimi API 调用。
- [x] **多模型统一管理**：在模型配置中支持 Kimi (moonshot) 提供商，支持 OpenAI 兼容接口。
- [x] **视觉理解能力**：通过 `VisualAnalysisService` 支持图片理解和分析。

## 🤖 极致轻量化 Agent 集成 (ZeroClaw)
- [ ] **轻量化助手接入**：调研利用 **ZeroClaw** (Rust 版本) 替代/增强目前的系统助手，以实现 <5MB 的极低资源运行。
- [ ] **智能监控分析**：在监控指标异常时，利用 ZeroClaw 进行自主根因诊断，并生成 24h 趋势分析报告。
- [ ] **主动维护 (Self-healing)**：利用 ZeroClaw 的自主循环（Autonomous Loop）特性，实现自动化的故障排除（如：自动清理爆满的日志、重启异常卡死的 Node 进程）。
- [x] **全渠道告警通知**：集成 ZeroClaw 的 Telegram/Discord 消息网关，实现离线状态下的"人话"版运维指令交互。

## 🔧 代码审核与增强 (2026-03-08)
- [x] **前端Loading状态管理**：新增全局Loading状态管理 (stores/loading.js)，axios拦截器自动管理
- [x] **统一动画样式**：新增 assets/styles/animations.css
- [x] **敏感数据脱敏**：新增 SensitiveDataMasker.java 工具类
- [x] **健康状态概览API**：新增 AgentHealthOverview DTO 及对应接口

---
*上次更新时间: 2026-03-08*
