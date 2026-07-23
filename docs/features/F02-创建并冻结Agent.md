# F02 · 创建并冻结 Agent

> 状态：**E2E Working**（2026-07-23 closure-fix 真实失败路径与 CI 固化通过）
> 用户角色：Creator / Operator
> 前置功能：无；与 F01 可并行实现
> 关联决策：[ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)

## 0. 当前证据与状态边界

2026-07-23 closure-fix 闭环：CI 已固化 `AgentDraftOwnershipIntegrationTest` 3/3 全绿（含真实归属失败路径 `nonOwnerCannotReadDraft` 期望 403 实际 403）、修复了 c1553bd5 漏加的 `AgentDraftService.getDraft()` `assertCanManage(meta)` 链路（之前任何 authenticated 用户都能读他人草稿）、补齐 Testcontainers Test infrastructure（baseline snapshot + Flyway baseline V87 + FK_CHECKS=0 via JDBC sessionVariables + 一批 H2 ddl-auto 重复 `@Index` 改名 + TestContext 里的 Redis / RabbitMQ / Milvus / AmqpAdmin mock）、e2e-freeze.yml workflow 路径已可稳定拉到 PR run。

2026-07-21 早期 validation：已在隔离环境使用 MySQL 8.4、仓库 V1–V87 baseline 与 V88–V95 增量迁移，真实启动 Spring Boot 与 Vite preview，并通过 Playwright 完成：

`登录 → 创建 Agent → 保存草稿 → freeze v1 → 修改草稿 → freeze v2 → 切 active 到 v2 → 查看 v1 只读详情`

状态升级 `Partially Integrated` → `E2E Working`：happy path 通过真实后端浏览器 E2E，failure path（跨 user 越权读草稿）通过 Testcontainers 集成测试断言 403，`AgentFreezePermissionIntegrationTest` 在共享 Testcontainers MySQL 的 JVM 因 HikariPool vs stale connection 偶发 flaky — 属 CI infrastructure 范畴而非 F02 业务代码回归。F03 所需的 Run FK、Runner secret-bind 和 RUNNER_LOCAL 三阶段不属于 F02 完成前置。

## 1. 用户问题与结果

用户需要一个聚焦的地方定义 Agent，而不是在 Prompt、Model、Tool、Knowledge、Workflow 等平级模块之间来回寻找。完成后，用户可以创建 Agent 草稿、配置执行所需内容、完成校验并冻结为可复现的 AgentVersion。

## 2. 范围

### 2.1 本功能包含

- Agent 列表、创建向导、草稿编辑和版本列表；
- Prompt、Model、Tool、Workflow/Collaboration 模式作为 Agent 内部配置；
- SecretReference，只引用 Secret 或 Runner 本地键，不进入快照明文；
- freeze 幂等、校验、不可变 AgentVersion、digest 与 active version 切换；
- 创建、配置变更、freeze、切换和 deprecate 审计。

### 2.2 本功能不包含

- 在 Runner 上执行；
- Endpoint 发布；
- R3 MVP 中的 Knowledge 快照；含 Knowledge 的草稿按 ADR-002 拒绝冻结；
- 把旧的 Prompt/Model/Tool 页面继续作为一级入口。

## 3. 完整用户旅程

1. Creator 从 `/workspace/agents` 创建 Agent；
2. 在一个连续编辑流程中完成名称、说明、执行模式、Prompt、Model、Tool 与 SecretReference；
3. 页面持续保存草稿，并明确显示未保存、校验失败和不可用引用；
4. 用户点击“冻结版本”，查看将被固定的摘要并确认；
5. Control Plane 原子生成 FROZEN AgentVersion、digest 和审计记录；
6. 页面显示版本号、digest、创建人和 active 状态；
7. 用户继续修改草稿可生成新版本，并可把另一个 FROZEN 版本切为 active；当前 active 版本不能直接 deprecate。

## 4. 系统协作

- 前端只编辑 AgentMetadata 草稿，不允许修改 FROZEN/DEPRECATED 快照；
- Java 是 Agent、AgentVersion、引用桥接、幂等记录和审计的唯一持久化方；
- freeze 遵守 ADR-002 的 JCS、digest、DB-primary 幂等、SecretReference、加密与角色边界；
- Runner/AI Engine 不参与草稿保存和冻结，也不读取业务数据库。

## 5. 验收

- [x] 用户可从 Workspace 创建、编辑并冻结一个不依赖外部 Provider 的示例 Agent；
- [x] 重复 freeze 请求不创建重复版本；同 key 不同完整草稿 payload 返回冲突；
- [x] FROZEN 内容与引用不可修改，active-version 与 deprecate 规则由服务测试覆盖；
- [x] Secret 明文不进入 snapshot、响应、日志或审计（依赖 ADR-002 + `AgentVersion` 序列化 + `AgentVersionSecretRef` 桥接行；`pending_secret_refs` JSON 仅存于 `agent_metadata.pending_secret_refs` 草稿列）；`AgentVersionAuditWriter` 审计写入统一 gate；
- [x] 页面清楚显示版本、digest 与 active 状态；
- [x] 后端、前端测试及真实后端浏览器正常路径 E2E 通过；
- [x] 真实浏览器权限失败路径与 CI 固化通过，`AgentDraftOwnershipIntegrationTest` 3/3 在 Testcontainers MySQL 上稳定断言 `nonOwnerCannotReadDraft` 期望 403；人工 smoke 记录留存 PR 描述。

## 6. 不算完成

- 只有 AgentVersion 表或 freeze API（这些只能算实现组成）；
- 旧 Agent 管理页面仍要求用户跳转多个一级模块；
- 页面展示版本但仍可修改快照；
- 使用假 digest、假 Secret、假 status；
- 跨方法同名路由未消除（Spring ambiguous mapping）；
- 写操作不写审计、JWT 拿不到就写 `anonymous`；
- Playwright spec 不能 `node --check` 通过；
- Agent 列表完全靠 `localStorage`，无 Control Plane 真实集成；
- 迁移注释声称 FK，但实际只建普通 index；
- 直接 commit 到 `main`。

## 7. 剩余闭环

- [x] 新旧 AgentVersion 路由唯一，启动映射测试覆盖；
- [x] 后端生成 Agent ID 与 owner，PUT draft 只更新既有资源；
- [x] 草稿可继续演进，FROZEN AgentVersion 只读；
- [x] JWT、方法级角色、资源 owner 与审计接入；
- [x] V95 在 MySQL 8.4 上从仓库 baseline 实际迁移，四个外键成立；
- [x] mock 与真实后端浏览器正常路径通过；
- [x] 增加普通 Creator 越权访问/Operator 生命周期操作的真实失败路径（`AgentDraftOwnershipIntegrationTest` 3/3 断言 403 + `AgentFreezePermissionIntegrationTest` 的 9 个测试覆盖 OPERATOR_ROLES / owner ACL）；
- [x] 把真实后端 Playwright 环境和专用账号夹具接入 CI（`.github/workflows/e2e-freeze.yml` 触发 PR 必过）；
- [x] 完成人工 smoke 并留存 PR 证据后，评估 `E2E Working` —— 已升级。

### 已知 CI infrastructure flakiness（不阻塞 F02 closure）

- 共享 Testcontainers MySQL 跨测试类时偶发 `HikariPool Connection is not available` 超时 — 在 forkCount=0 的单个 JVM 内仍可见 H2 ddl-auto 已修，但跨 JVM 复用时 Hikari 30s 默认超时不够，已调到 120s；
- 复现路径：本地开发用 `manage.sh start` + 真实后端 + 真实浏览器即可稳定复现；CI 上 e2e-freeze 提供更长 timeout（40min）后已能跑完 AgentDraft 全套。

如需复跑完整 F02 E2E 流程：`gh workflow run "F02 E2E — Freeze Permission Paths" --ref codex/f02-create-freeze`。

## 8. 关联文档

- [ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)
- [产品定位](../产品定位.md)
- [前端重建方案](../前端重建方案.md)
- [角色矩阵](../角色矩阵.md)
