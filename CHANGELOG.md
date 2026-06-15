<!-- markdownlint-disable MD024 MD041 -->
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

---

## [v0.2.0] - 2026-06-15

77 commits 自 v0.2.0-alpha.1。本段按主题分块而非逐 commit 罗列；详细 commit
列表 `git log v0.2.0-alpha.1..v0.2.0` 自取。

### Phase 1.5：角色化体验与权限闭环

**Added:**

- 角色矩阵：5 类角色（USER / OPERATOR / ADMIN / PLATFORM_ADMIN / SUPER_ADMIN）的默认入口、菜单可见性、JWT 边界与后端权限口径（[docs/角色矩阵.md](docs/角色矩阵.md)）
- 前端菜单与路由守卫同源使用角色常量，一级菜单、子菜单和直接 URL 访问按同一矩阵过滤
- 普通用户默认进入 `/portal`、运维进入智能体列表、管理员进入监控总览
- 用户 / 部门 / 角色管理接口移出匿名放行，补 JWT / 角色边界测试
- Playwright 角色导航 smoke：覆盖运维菜单过滤 + 普通用户管理台重定向
- API Key 自助治理：普通用户 / 运维管理自己的访问密钥，管理员保留全局治理能力
- 开发者工作台 + 管理员平台总览入口 + 管理员 / 开发者 / 用户手册（普通用户门户与资源级视图仍待续）

**Security:**

- **资源级 ACL 三刀收口**：
  - KB 资源级 ACL：`ownerUserId` + 行级隔离
  - Agent 资源级 ACL：写 + 读 + chat 三层
  - Workflow 资源级 ACL：5 写 + 4 读 + archive
  - 抽 `common.security.BaseOwnershipResolver` 统一 ACL 基类
- 敏感路径补 ACL 单元测试覆盖

### Phase 1.5 → Phase 2 过渡：ProviderAdapter 多模态扩展

**Added:**

- `ChatCompletionRequest.Message` 新增 `List<ContentPart> parts` 多模态字段（OpenAI 兼容：text / image_url + `image_url:{url,detail}`）
- `TranscriptionRequest` / `TranscriptionResponse` DTO（model + audioUrl + mimeType/language/providerParams）
- `ProviderAdapter.transcribe()` 通道与 `SiliconFlowTranscriptionAdapter` 实装（按 model 路由 whisper ↔ siliconflow-asr）
- `OcrService` 改用 `RouterService` + 多模态 parts 构造；删除三家云厂商 stub（aliyun / tencent / baidu）
- `AsrService` 改用 `RouterService.selectProviderByType("siliconflow-asr")` + `provider.transcribe()`

### Phase 2：质量与可观测

**Added:**

- **统一错误码大目标收尾**：165 处裸抛一次性改用 `BusinessException(错误码, msg)`，覆盖 system / skill / workflow / knowledge / gateway / multimodal / collaboration / agent / task / security 模块；`mvn compile` exit 0 + `mvn test` 636/636 全绿
- **错误码分类扩展**：3 个新分类（限流 10 / 任务 11 / 协作 12），共 16 个新码（`ORIN-XXXX` 前缀统一留待 Phase 3）
- **TraceID 跨 MQ 传播**（后端 + AI Engine 小刀 1）：RabbitMQ 发布时 `MessagePostProcessor` 注入 `traceparent` + `X-Trace-Id`；3 个 `@RabbitListener` 从 message property 抽取灌 MDC
- **AI Engine W3C traceparent inbound 解析**（小刀 2）：`app/core/w3c_trace.py` + `trace_context.py` + `logging_filter.py` + `trace_middleware.py` + mq_worker 抽 `message.headers`
- **Outbound httpx 注入**（小刀 3）：`app/core/trace_httpx.py` 工厂 + 6 个 engine 模块切到 `httpx_client`，9 个 call site 改完
- **AI Engine 结构化 JSON 日志**（小刀 4）：`JsonFormatter` + 字段对齐后端 logstash-encoder（`timestamp` / `level` / `logger` / `message` / `traceId` / `spanId` / `module` / `func` / `line` / `process` / `thread`）
- **OpenTelemetry SDK 接入**（小刀 5a/5b/5c）：
  - 5a：基础设施 + 幂等 init + `OTEL_SDK_DISABLED` 兜底 + conftest 默认 disable + 启停双钩子
  - 5b：contextvar ↔ OTel Context 双向桥 + propagator 切换 + 业务 span 影子化（executor.py 11 处调用方零改动）
  - 5c：docker-compose 加 jaeger (jaegertracing/all-in-one:1.62) + `scripts/trace-smoke.sh` 端到端验证 + 手册 §4.4 重写
- **覆盖率红线 + CI gate**（小刀 6）：
  - `coverage-baseline.json`：3 端门槛（ai-engine 55% / backend 17% / frontend 60%）
  - `scripts/coverage-gate.py`：3 路径实测（pass / fail / missing-report）
  - `.github/workflows/ci.yml` 3 端 `Coverage gate` 步骤

**Fixed:**

- **真 bug 修复（trace 链路）**：
  - Python `callHandlers` 在 propagate 链上**只**调 `handler.filter`，**不**调 `logger.filter` —— 之前 main.py 把 `TraceContextFilter` 挂到 root logger 是无效的（子 logger 冒泡时根本不走），已迁到 handler 上
  - OTel 1.42.1 `tracer.start_span` 在 NonRecordingSpan parent 下**复用** parent span_id（OTel 把 NonRecordingSpan 当 transit 转发）—— 出站跳必须手工构造 `SpanContext(span_id=generate_span_id())` + `NonRecordingSpan`
  - `_attach_otel` 嵌套顺序：先 detach 旧 token 再 attach 新 token（否则 OTel contextvars 栈错乱，新 span 立即失效）
  - OTel 进程级 TracerProvider 不可 reset（1.42.x 第二次 set 破坏 `_TRACER_PROVIDER` 全局状态）—— 测试用 lifecycle + pytest 字母序隔离
- `tracing.py` 业务 span 影子化：`finish_span` 用 `spans.pop()` 会破坏 `get_trace_summary()`（mirror 必须 append-only）—— 改为 OTel 栈独立 LIFO pop + mirror 仅 set end_time

**Changed:**

- **移除 `X-Trace-Id` legacy header**（小刀 3）：W3C `traceparent` 主路径取代
- **删除 3 家云厂商 OCR / ASR stub**（OcrService / AsrService 迁移到 ProviderAdapter 时清理）
- **TraceContextFilter 移至 handler 挂载点**（小刀 4 顺手）：与 Python `logging` callHandlers 语义对齐

**Documentation:**

- [docs/手册-开发者.md](docs/手册-开发者.md) §4.4 Trace 调试：拆 4.4.1 Jaeger UI / 4.4.2 环境变量 / 4.4.3 端到端 smoke / 4.4.4 日志与 header / 4.4.5 排查清单
- [docs/路线图.md](docs/路线图.md) §0 / §0.1 同步覆盖率门槛与 Phase 2 完工状态
- TODO.md 同步 8 刀 commit hash 与"Known Limitations"刷新

### CI / 工程

- `.github/workflows/ci.yml` 加 3 端 `Coverage gate` 步骤（行 53 / 103 / 156）
- `scripts/coverage-gate.py` 纯 std-lib 实现，不引第三方依赖
- `scripts/trace-smoke.sh` 端到端 Jaeger 验证（不替用户起服务）

### Known Limitations（仍未做）

- **ORIN-XXXX 错误码前缀统一**：当前错误码是数字 ID，未加 `ORIN-` 前缀 —— 涉及 40+ 错误码全局重命名，留待 v0.3.0
- **Python 端 `task_runtime.py` 结构化错误码**：后端 Java 端已统一，AI Engine Python 仍 raise Python exception
- **Langfuse / Prometheus / Jaeger 三方监控 UI 统一入口**：等结构化日志落地后整合（已完成 50%）
- **外部客户端展示资产（截图/GIF/视频）**：待录制
- **统一监控大盘**（Prometheus / Grafana）：独立小刀，路线图 §4.5
- **Sentry 错误追踪**：路线图 §4.4

### 升级注意事项

- **外部用户**：CHANGELOG 中"Changed"段的 `X-Trace-Id` 移除 + W3C `traceparent` 主路径；接入端（Codex / Claude Desktop / 自家调用方）需确保 `traceparent` 头透传
- **自部署用户**：升级后首次启动会看到 `[otel] tracing initialized: service=orin-ai-engine env=dev exporter=ConsoleSpanExporter(stdout) sdk=1.42.1` —— 这是 Phase 2 小刀 5a 新增的 init 日志，证明 OTel SDK 装好
- **CI 维护者**：main branch protection 加 `coverage-gate` check 3 个（backend / frontend / ai-engine），门槛调高需 PR 改 `coverage-baseline.json` + 路线图同步

---
