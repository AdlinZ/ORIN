# Changelog

All notable changes to ORIN will be documented in this file.

This project follows the spirit of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses Semantic Versioning during the `v0.x` phase.

## [v0.1.0] - 2026-04-15

### Added

- **三层架构基线**：Spring Boot 后端 + Vue 3 前端 + Python AI Engine (FastAPI + LangGraph)
- **智能体接入**：支持多 Provider (OpenAI / SiliconFlow / Dify / Ollama)，Agent 增删改查、对话接口、MCP 暴露开关
- **知识库治理**：Milvus 向量库集成、RAGFlow 同步、文档上传/解析/向量化/检索、端侧变更查询与 Webhook
- **工作流编排**：Workflow DSL 可视化编辑器、循环/条件/并行节点、`/api/v1/workflow/run` 执行接口、LangGraph 执行引擎
- **多智能体协作**：Collaboration 包/子任务状态机、人工干预接口（skip/retry/manual-complete）、FALLBACK 重派机制
- **统一网关**：API Key 生命周期管理（创建/禁用/轮换）、JWT 鉴权、限流、配额、审计日志
- **可观测性**：W3C traceparent 全链路传递、JSON 结构化日志（LogstashEncoder + MDC）、Langfuse LLM trace、Prometheus 指标
- **MCP-Native**：Streamable HTTP MCP 协议、`/v1/mcp` 端点、tools/call 端到端验收、Codex/Claude Desktop 客户端接入文档
- **Docker Quickstart**：`docker compose up` 拉起全部基础设施、schema snapshot 初始化、business-smoke 验收脚本
- **CI 基线**：GitHub Actions (schema baseline / backend / frontend / AI Engine)、CodeQL / gitleaks 扫描、Docker smoke

### Security

- API Key 访问密钥按 `CLIENT_ACCESS / sk-orin-*` 口径统一管理
- JWT 鉴权 + 角色权限过滤（USER / OPERATOR / ADMIN / PLATFORM_ADMIN / SUPER_ADMIN）
- CORS 白名单、敏感字段脱敏审计、gitleaks 扫描基线

### Documentation

- [架构设计.md](docs/架构设计.md)、[部署指南.md](docs/部署指南.md)、[使用指南.md](docs/使用指南.md)
- [API文档.md](docs/API文档.md)、[开发规范.md](docs/开发规范.md)、[角色矩阵.md](docs/角色矩阵.md)
- [open-demo-checklist.md](docs/open-demo-checklist.md)、[mcp-client-setup.md](docs/mcp-client-setup.md)

### Known Limitations

- Langfuse / Prometheus / local Trace 三套并存，前端监控页统一入口待收敛
- AutoGen / CrewAI 预留位未实现，不应视为已完成能力
- 外部客户端展示资产（截图/GIF）待录制

---

## [v0.2.0-alpha.1] - 2026-05-14

### Added

- Closed the collaboration human-intervention loop with skip, manual-complete, and retry actions.
- Added package-level FALLBACK max-attempt protection for collaboration execution.
- Added workflow loop node support in the backend DSL normalizer.
- Added a `limit` query parameter to the knowledge graph `/visualization` API.
- Added real knowledge graph visualization in the frontend graph detail page.
- Added two-layer graph search in the frontend: local canvas highlight plus Enter-triggered full-graph API search.

### Fixed

- Fixed collaboration `skip` and `manual-complete` so downstream subtasks are scheduled instead of leaving the package stalled.
- Fixed workflow subtask execution from AI Engine to backend by reusing contextual `_authorization` with `ORIN_BACKEND_AUTHORIZATION` fallback.
- Fixed the backend collaboration result listener so it consumes the result queue and advances subtask state.
- Fixed `branch_result` payload loss caused by listener/status double writes.
- Fixed the `future.complete` race where async completion could overwrite full result payloads with simplified payloads.
- Fixed workflow `loop` while/until execution so body outputs are written back before the next condition evaluation.
- Fixed `parallel_fork` error handling so branch exceptions are collected instead of being swallowed as plain strings.
- Fixed knowledge graph build stats so `entity_count` and `relation_count` are not overwritten by stale JPA entity instances.
- Fixed knowledge graph entity search and detail fallback paths to preserve `graphId` isolation across graphs.

### Changed

- Unified the if-else workflow node contract around the AI Engine `conditions` semantics and removed the old Java placeholder fallback.
- Changed knowledge graph MySQL visualization fallback node ids from synthetic `node_0` values to real `GraphEntity.id` values.
- Documented the local knowledge graph smoke checklist, including valid knowledge-base payload shape and document parse/build readiness checks.

### Removed

- Removed the unused legacy `GraphVisualization.vue` component.

### Deferred

- Deferred workflow `switch` node support until a real multi-branch routing use case appears.
- Deferred workflow collaboration-subprocess nodes until a real workflow-to-collaboration orchestration use case appears.
- Deferred unified error handling from Phase 1 to Phase 2 cross-cutting governance, alongside logging, Trace, and error tracking.

### Tech Debt

- `GraphExtractionService` should move from direct `SiliconFlowEmbeddingAdapter.chat()` calls to the unified LLM gateway.
- Knowledge graph relation extraction output rate needs evaluation against relation-heavy documents.
