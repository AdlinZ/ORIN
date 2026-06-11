# AGENTS.md

> Coding agent 工作指引（Codex / Cursor / Claude Code 通用）。
> 启动任何任务前必读本文件。详细背景见 [docs/](./docs/)。

## 1. 你在做什么

你正在为 **ORIN**（智能体管理平台）做工程化迭代。项目当前处于"骨架完整、闭环未满"的阶段，目标是按 [docs/路线图.md](./docs/路线图.md) 推进到"优秀开源项目水平"。

**三层架构**：

- `orin-backend/`：Spring Boot 3.2 + MySQL + Redis · Java 主控层（业务实体唯一持久化方）
- `orin-frontend/`：Vue 3 + Vite + Element Plus · 管理台
- `orin-ai-engine/`：Python 3.11 + FastAPI + LangGraph · 工作流/协作执行层（不持久化业务数据）

### 1.1 当前推进口径（2026-06-11）

当前不要再优先扩新菜单或堆新模块。Phase 0 基线、MCP 主干、Workflow / Collaboration / API Key 的 API 级 smoke 已建立，后续开发按以下顺序收敛：

1. **Phase 1 收口**（当前 Sprint）：真实后端联调下的浏览器 E2E、协作人工干预验收、真实 Agent / MCP 子任务样本、OCR 空桩修复、外部 MCP 客户端展示资产。
2. **Phase 1.5 角色化体验**：资源级 ACL、API Key 端点限流、角色专属视图收敛。
3. **Phase 2 质量与可观测**：统一错误码、`traceparent` 跨 MQ 传播、JSON 结构化日志、OTel / Jaeger、覆盖率红线。
4. **Phase 3/4 安全运维与社区化**：备份恢复、默认凭据治理、v0.1.0 release、README 截图 / GIF。

**2026-06-11 安全修复已合并 main**：JWT roles 缺失时拒绝认证（不再 fallback 到 `ROLE_USER`）；ASR 三个云端空桩改为抛异常。详见 [TODO.md](./TODO.md) 审核修复记录。

短期最优先 PR：**真实后端协作 E2E + OCR 空桩修复 + Agent/MCP 子任务验收样本**。判断完成度仍以”前端入口 + Java service + Python AI Engine + smoke / E2E 验收”同时成立为准。

## 2. 启动前必读

按顺序：

1. [docs/架构设计.md](./docs/架构设计.md) — 模块边界、接口前缀、协作执行约束
2. [docs/开发规范.md](./docs/开发规范.md) — 编码规范、PR 必检、禁止事项
3. [docs/路线图.md](./docs/路线图.md) — 当前阶段与具体 todos
4. [docs/功能完成度.md](./docs/功能完成度.md) — 现有模块成熟度、勿当作完成的能力

接到具体任务时再读：

- 涉及部署 / 环境变量 → [docs/部署指南.md](./docs/部署指南.md)
- 涉及接口 / 联调 → [docs/API文档.md](./docs/API文档.md)
- 涉及前端入口 → [docs/使用指南.md](./docs/使用指南.md)、[orin-frontend/docs/](./orin-frontend/docs/)
- 涉及角色 / 权限 → [docs/角色矩阵.md](./docs/角色矩阵.md)
- 涉及 MCP 客户端展示 → [docs/mcp-client-setup.md](./docs/mcp-client-setup.md)、[docs/open-demo-checklist.md](./docs/open-demo-checklist.md)

## 3. 环境与启动

### 3.1 前置依赖

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| Python | 3.11–3.14 |
| MySQL | 8.0+ |
| Redis | 6.0+ |

### 3.2 启动三端

```bash
# 后端
cd orin-backend && mvn spring-boot:run        # :8080

# AI 引擎（首次需建 venv 并 pip install -e .）
cd orin-ai-engine && venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000

# 前端
cd orin-frontend && npm install && npm run dev   # :5173
```

或一键：`./manage.sh start`（macOS + 本地 MySQL 假设）

### 3.3 健康检查

```bash
curl http://localhost:8080/v1/health
curl http://localhost:8080/api/v1/health
curl http://localhost:8000/health
curl http://localhost:8000/v1/health
```

## 4. 测试与校验命令

任务完成后**必须运行**对应命令并确认通过：

```bash
# 后端
cd orin-backend && mvn test

# 前端
cd orin-frontend && npm run test && npm run build
# 涉及浏览器交互时额外运行：npm run test:e2e

# AI 引擎
cd orin-ai-engine && venv/bin/pytest

# 全链路烟测（启动三端后）
bash scripts/smoke-test.sh
```

不要为了让测试通过而注释 `@Test` 或加 `--skip-tests`。

## 5. 不可违反的硬约束

> 违反任何一条都会被驳回 PR。

### 5.1 架构层

- **业务持久化只能在 Java 后端**。AI 引擎不直连业务数据库；前端不直连 AI 引擎或外部 provider
- **协作执行链唯一**：编排层 `app.engine.collaboration_langgraph` → 分发层 `app.engine.mq_worker` → 执行内核 `app.engine.task_runtime.TaskRuntime`。**禁止**新增并行的执行内核或在 `mq_worker` 写独立执行逻辑
- **接口前缀不混用**：`/v1/*`（API Key · 对外协议入口，含 OpenAI 兼容网关与 MCP）、`/api/v1/*`（JWT · 业务）、`/api/workflows/*`（工作流管理）、`/api/traces/*`（Trace 查询）、`/api/system/*`（系统集成 / MCP 兼容入口）。新增模块前先核对能否复用现有前缀
- `/v1/mcp/**` 由 API Key 鉴权，与 `/api/v1/**` 的 JWT 业务接口隔离；不得混用鉴权通道
- **新能力优先在现有模块内闭环**，不另起平行实现

### 5.2 数据层

- 所有执行型请求必须携带 `traceId`，跨服务透传 HTTP header `traceparent`
- 任务对象统一状态枚举：`PENDING / RUNNING / COMPLETED / FAILED / CANCELLED`
- 协作包状态机：`PLANNING → DECOMPOSING → EXECUTING → CONSENSUS → COMPLETED / FAILED / FALLBACK`
- Docker quickstart 依赖 `docker/mysql/init/01-orin-schema.sql` 作为 `V1..V87` baseline schema snapshot；后端启动后由 Flyway 补跑 `V88..V90`。后续新增 schema 迁移从 `V91` 开始，禁止改写已发布迁移（尤其 `V5/V6/V8/V11/V87/V88/V89/V90`）。

### 5.3 安全层

- **绝对禁止**：提交 `.env` / API Key / 密码 / 私钥 / 任何凭据
- **绝对禁止**：日志打印密码、token、API Key、手机号、身份证等敏感字段
- 默认密码、示例密钥、`JWT_SECRET` 不得 hardcode 进代码，必须从环境变量读取
- 跨域 `CORS_ALLOWED_ORIGINS` 不得为 `*`
- 所有写操作 / 敏感读取要写入审计日志
- **JWT token 必须携带 `roles` claim**：`JwtAuthenticationFilter` 在 token 缺少 `roles` 时拒绝认证而非 fallback 到默认角色。`generateToken` 调用方必须传入角色列表；`refreshToken` 会自动保留原 claims，无需额外处理

### 5.4 流程层

- 不直接推 `main`，必须经 PR
- 不为绕过测试而 skip tests / 注释断言
- service 之间不调用对方私有方法，用接口或事件解耦

## 6. 编码规范要点

### 6.1 Java（后端）

- 包结构：`com.adlin.orin.modules.<模块名>.{controller,service,repository,entity,dto}`
- Controller 只做参数校验与组装，业务下沉到 service
- 异常统一用 `BusinessException(errorCode, message)`，全局 `@ControllerAdvice` 输出 `{ code, message, traceId }`
- 日志使用 SLF4J；同模块内 JPA 与 MyBatis 不混用

### 6.2 Python（AI 引擎）

- 类型注解必填，公共函数补 docstring
- 节点处理器继承 `BaseNodeHandler`，**禁止**在 handler 中持久化业务数据
- 长任务必须支持取消信号（`asyncio.CancelledError`）
- 依赖通过 `pyproject.toml` 管理（不维护 `requirements.txt`）

### 6.3 Vue 3（前端）

- 组合式 API（`<script setup>`），**禁用** Options API 写新代码
- 状态管理用 Pinia；组件间 props/emit 优先，跨页才用 store
- 路由常量集中在 `src/router/routes.js`，菜单暴露见 `src/router/topMenuConfig.js`
- 当前主入口以 `topMenuConfig.js` 的角色可见菜单为准：`/dashboard/applications/*`、`/dashboard/resources/*`、`/dashboard/runtime/*`、`/dashboard/control/*`。旧 `/dashboard/agents/*`、`/dashboard/system/*` 只按历史重定向处理。
- API 请求统一走 `src/api/*`，**禁止**组件直调 `axios`
- 表格优先用项目内 `ResizableTable` 包装组件，不裸用 `el-table`

## 7. 提交规范

### 7.1 Commit Message

`<type>(<scope>): <subject>`

| type | 用途 |
|------|------|
| feat | 新功能 |
| fix | Bug 修复 |
| refactor | 重构（不改外部行为） |
| docs | 文档 |
| test | 测试 |
| chore | 构建、依赖、CI |
| perf | 性能优化 |

例：`feat(workflow): add DSL validator for loop nodes`

### 7.2 PR 描述模板

```markdown
## 改动概述
（1–3 句话说明 why）

## 改动范围
- backend: ...
- frontend: ...
- ai-engine: ...

## 测试与校验
- [ ] 单测已加 / 已通过
- [ ] 手工烟测已通过
- [ ] 涉及环境变量已同步 docs/部署指南.md

## 关联文档
（若涉及架构/接口变更，列出已同步的文档）
```

### 7.3 PR 必检项

- [ ] 协作执行是否仍经 `TaskRuntime`，未引入并行执行内核
- [ ] 新增接口归入对应前缀分组，未自创新前缀
- [ ] 涉及前端入口已更新 `routes.js` + `topMenuConfig.js`
- [ ] 涉及角色可见性或权限边界已同步 `docs/角色矩阵.md`
- [ ] 涉及环境变量已同步 [docs/部署指南.md](./docs/部署指南.md)
- [ ] 单测覆盖关键路径
- [ ] 不打印敏感字段、不提交 `.env`
- [ ] 新增 `generateToken` 调用已传入 `roles` claim（防止 JWT 认证被拒绝）
- [ ] 新增未实现方法抛 `UnsupportedOperationException` 而非返回错误字符串

## 8. 协作工作模式

### 8.0 默认执行策略：长时间自主运行

**默认目标是直接完成用户目标，而不是等待用户逐步指挥。** 除非触发 8.2.2 的必须询问条件，Agent 应持续执行“理解 → 定位 → 实现 → 测试 → 修复 → 复测 → 文档同步 → 结果汇报”的完整闭环。

- 用户给出目标后，默认已授权在当前仓库和任务范围内读取、修改、运行测试、启动本地服务及生成必要的临时文件
- 优先通过代码、测试、Git 历史、现有文档和运行结果自行消除不确定性；能调查得到的答案不得反问用户
- 对可逆、局部、符合现有架构的实现选择，直接采用最保守且与仓库既有模式一致的方案，并在最终报告中说明关键假设
- 长任务应自行拆分为内部步骤持续推进；不要因为耗时长、涉及多端、测试较多或上下文复杂而提前交还用户
- 运行中的命令、服务、测试和验收必须跟踪到明确结果；不得在必要进程仍未结束时宣告完成
- 发现与任务直接相关的缺陷时，可在不扩大产品范围、不违反硬约束的前提下连续做定向修复，直到目标通过或确认存在外部阻塞
- 用户未要求只分析、只规划或只评审时，默认应实际修改代码并完成验证，而不是只给建议
- 中间进展只需在阶段完成、出现风险或执行时间较长时简短汇报；不得用频繁确认打断自动执行

### 8.1 接到任务时

1. **理解任务**：先读路线图对应阶段的 todos，确认任务在哪个 phase
2. **定位代码**：用 grep / IDE 搜索找到现有实现（避免重复造轮子）
3. **形成验收口径**：从用户目标、现有测试和文档中推导完成条件；缺少细节时采用与现有行为兼容的最小合理假设
4. **小步迭代**：单 PR 原则上不超过 500 行改动；目标超过时自行按可独立验证的提交或后续 PR 边界拆分，但先完成当前可交付闭环
5. **持续验证**：每完成一个关键步骤就运行最相关的定向测试，最后再运行任务要求的完整校验
6. **同步文档**：架构 / 接口 / 环境变量变更必须同步文档

### 8.2 不确定与失败时

- **优先 grep**，不要凭印象写代码（路由、接口、组件命名极易过期）
- **先核对当前现状**，不要把旧文档说的"已完成"当真。判断依据是代码 + 测试 + 真实运行结果，不是注释或 README
- 涉及多端（前端 + Java + Python）的能力，**三端同时核对**才算闭环
- 先列出候选解释并用最小成本命令验证；不要把可通过实验解决的问题升级为用户决策
- 设计存在多个合理选项时，优先选择改动最小、兼容性最好、依赖最少、最符合现有模式的方案
- 测试与代码语义冲突时，先确认 truth source。确认测试过时则更新测试；确认代码有 bug 则修代码。禁止删除断言、跳过用例或掩盖真实行为
- 测试或验收失败后，允许在当前任务范围内进行多轮定向诊断和修复；每轮都必须重新运行同一失败命令或验收路径
- 修复不得借机扩展产品范围或做无关重构；若发现独立问题，记录到最终报告，不阻塞当前目标
- 依赖缺失时，先检查仓库声明、锁文件、构建脚本和文档。若属于项目已声明依赖，可按现有包管理方式安装或恢复；不得擅自引入新的生产依赖

### 8.2.1 可自主处理的冲突

以下情况默认继续执行，无需等待用户确认：

- 实现细节未指定，但可从现有模式、接口契约或测试推导
- 需要在任务相关文件中补充单测、类型、错误处理或文档
- 定向测试失败且根因位于本次任务范围内
- 本地生成物、缓存或端口占用影响验证；仅可自主重启本轮由 Agent 启动，或已确认属于当前仓库且可安全重启的开发进程，无法确认进程归属时按 8.2.2 询问用户
- 已声明依赖未安装、锁文件要求的工具缺失，且安装不会修改生产依赖契约
- 同一目标有多个可逆实现，且其中一个明显具有更小改动和更好兼容性

处理时应保留硬约束，记录关键判断，并持续推进到可验证结果。

### 8.2.2 必须询问或停止的边界

只有出现以下情况，且无法通过仓库内信息或安全的可逆操作解决时，才询问用户：

- 用户要求与本文件的架构、安全、迁移或禁止事项发生直接冲突
- 需要真实凭据、账号授权、验证码、付费资源或用户独有的外部信息
- 需要执行不可逆或高破坏性操作，例如删除生产数据、重写已发布迁移、强推、覆盖用户未提交改动
- 两种方案会造成明显不同的产品行为、公共 API 或数据模型，且仓库内没有 truth source 可判定
- 关键外部环境不可用，例如 Docker daemon、数据库或第三方服务缺失，并且无法用项目已有测试替代完成必要验收
- 预计会超出用户明确给出的范围、成本、时间或变更上限

询问前必须先完成所有不依赖该决定的工作，并提供：阻塞事实、已完成内容、至少两个方案、trade-off 和推荐方案。不得仅因“拿不准”“可能耗时”或“测试再次失败”而停止。

### 8.2.3 长时间任务恢复

- 为长任务维护简短的内部检查清单，完成一项即更新状态
- 命令超时或会话中断时，先检查现有进程、日志、工作树和测试产物，再从最近完成点继续；不要从头重做
- 可并行的只读调查和独立测试应并行执行；有依赖关系的修改与验证按顺序执行
- 连续失败时应收窄变量、使用更小测试定位根因，并保留失败证据；确认是外部阻塞后再按 8.2.2 处理
- 最终汇报必须区分“已完成并验证”“已完成但受环境限制未验证”“未完成且被阻塞”

### 8.3 完成判定

任务"完成"的口径不是"代码写完编译通过"，而是：

- [ ] 单元测试覆盖关键路径
- [ ] 本地手工跑通一次完整流程
- [ ] 涉及的文档已同步
- [ ] PR 描述清晰、CI 通过、无敏感信息
- [ ] 路线图对应 todo 可标记 `[x]`

## 9. 速查表

| 想知道 | 看哪里 |
|--------|--------|
| 项目做什么 | [README.md](./README.md) |
| 当前阶段重点 | [docs/路线图.md](./docs/路线图.md) |
| 模块到底完成多少 | [docs/功能完成度.md](./docs/功能完成度.md) |
| 怎么部署 / 环境变量 | [docs/部署指南.md](./docs/部署指南.md) |
| 接口长啥样 | [docs/API文档.md](./docs/API文档.md)、Swagger `:8080/swagger-ui/index.html` |
| 前端导航 | [docs/使用指南.md](./docs/使用指南.md) |
| 架构原则 | [docs/架构设计.md](./docs/架构设计.md) |
| 编码规范 | [docs/开发规范.md](./docs/开发规范.md) |
| 前端专项 | [orin-frontend/docs/](./orin-frontend/docs/) |
| 待办 | [TODO.md](./TODO.md) |

## 10. 严禁事项速记

- ❌ 提交密钥 / `.env` / 凭据
- ❌ 跳过测试或注释断言
- ❌ 在 AI 引擎写业务库
- ❌ 新增第二套协作执行内核
- ❌ 自创接口前缀
- ❌ 直接 push main
- ❌ 把"页面存在"当作"能力闭环"
- ❌ 凭旧文档行动而不核对当前代码
- ❌ 在生产配置里使用默认密码 / 通配 CORS
