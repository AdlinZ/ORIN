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
    - [ ] 在前端“系统设置”中增加“向量检索配置”子项。
    - [ ] 支持在线修改 Milvus 地址 (`host`)、端口 (`port`) 和 Token。
    - [ ] 增加“联通性检查”按钮，点击后后端尝试 ping Milvus 并返回状态。
- [ ] **切换真实/Mock 模式**：
    - [ ] 优化后端配置，支持从 `application.properties` 或数据库动态切换向量引擎，无需修改代码。

## 🧪 长期优化
- [ ] **知识库分片策略**：完善 UNSTRUCTURED 类型的 PDF/Word 自动分段逻辑。
- [ ] **多模态支持**：对接 VLM 模型实现图片知识库的检索。

---
*上次更新时间: 2026-02-03*
