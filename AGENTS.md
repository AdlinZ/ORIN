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

### 8.1 接到任务时

1. **理解任务**：先读路线图对应阶段的 todos，确认任务在哪个 phase
2. **定位代码**：用 grep / IDE 搜索找到现有实现（避免重复造轮子）
3. **小步迭代**：单 PR 不超过 500 行改动，超过就拆
4. **同步文档**：架构 / 接口 / 环境变量变更必须同步文档

### 8.2 不确定时

- **优先 grep**，不要凭印象写代码（路由、接口、组件命名极易过期）
- **先核对当前现状**，不要把旧文档说的"已完成"当真。判断依据是代码 + 测试 + 真实运行结果，不是注释或 README
- 涉及多端（前端 + Java + Python）的能力，**三端同时核对**才算闭环
- 拿不准的设计选择**先用 PR 描述提问**，不要默默走偏

### 8.2.1 冲突即停（硬规则）

**当审批指令与新发现的约束冲突时，必须立即停下来反馈，不可自行权衡。**

冲突场景包括但不限于：

- 镜像 / 包体积超过目标 → 不可自行切换基础镜像或移除依赖
- 测试 / 验收失败 → 允许在当前任务范围内做一次定向修复；禁止修改测试代码绕过真实问题
- 历史迁移在新环境跑不通 → 不可改写历史迁移文件
- 依赖缺失 → 不可自行补依赖，需先确认是预期还是 bug
- 关键验证（Docker daemon / DB / 凭据）不可用 → 第一时间停下询问，不可先做完后报告

**正确处理流程**：

1. 发现冲突 → 立即停止动手
2. 整理至少两个可选方案（含 trade-off）
3. 给出自己的推荐 + 理由
4. 等待用户决策
5. 仅按用户选定的方案执行

**禁止做法**：

- "我看到问题就顺手解决了"
- "为了完成任务自己拍板"
- "等做完后在 PR 描述里报告"

**测试与实际代码语义冲突时**：先确认 truth source（看真实代码与产品行为）。若确认测试断言过时，可作为本轮唯一一次定向修复更新测试；若是代码 bug，可作为本轮唯一一次定向修复更新代码。禁止为了通过测试而删除断言、跳过用例或掩盖真实行为。

**测试 / 验收失败的处理（一次修复制）**：

1. 跑测试或验收发现 fail → 先判断根因，并说明将做一次定向修复。
2. 只允许修复与当前任务直接相关的问题；不得扩大范围、不得顺手重构、不得绕过测试。
3. 完成第一次修复后必须重新运行同一条失败命令或同一验收路径。
4. 如果第一次修复后仍失败 → 立即停止继续修复，报告失败现象、已尝试的修复、至少两个可选方案（含 trade-off）和推荐方案，等待用户决策。
5. 如果失败明显来自环境缺失、凭据缺失、Docker/DB 不可用等不可由代码修复的问题 → 不消耗一次修复机会，直接报告阻塞与可选方案。

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
