# F02 · 创建并冻结 Agent

> 状态：**Not Started**（2026-07-21 closure-fix 进行中，最小切片落地后回退）
> 用户角色：Creator / Operator
> 前置功能：无；与 F01 可并行实现
> 关联决策：[ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)

## 0. 关于"Partially Integrated"回退的说明

7月初尝试以最小纵向切片推进 F02 已经创建了一组后端/前端文件，但用户评审发现以下阻塞：

1. `AgentManageController` 与新建的 `AgentFreezeController` 在相同 `/api/v1/agents/{agentId}/versions/**` 路径上产生 Spring ambiguous mapping，**实际应用启动会失败**；
2. "新建 Agent"路径上没有真正创建 Agent 的接口——`PUT /agents/{id}/draft` 走的是 `findById(...).orElseThrow(AGENT_NOT_FOUND)`，前端随机生成的 agentId 必返回 404；
3. 首次 freeze 后草稿被锁死（`requireEditable` 在 `activeVersionId != null` 时直接拒绝），违反 ADR-002 中"AgentMetadata 是唯一可变草稿"的原则，F02 旅程 "修改草稿 → freeze v2" 走不下去；
4. 冻结/切换/废弃均落在 `permitAll()` 鉴权范围，`SecurityConfig.java:71` 上没限制；写操作未带角色与 JWT 校验；`currentActor()` 在 anonymous 上下文会写成 `anonymous`；
5. `tests/e2e/agent-f02.spec.js` 文件存在 JS 模板字符串引号不匹配，**`node --check` 失败 → spec 从未执行**；
6. Agent 列表来自浏览器 `localStorage`，不是 Control Plane 真实列表，跨设备/清缓存即失；
7. V95 迁移注释声称 `active_version_id` 是 FK，但实际只 `INDEX`，没有 `FOREIGN KEY` 约束；
8. 直接 commit 到 `main`，违反项目"不直接推 main，必须 PR"硬约束。

F02 commit 已 reset 软撤回，工作树保留所有改动；状态保持 `Not Started`，等 closure-fix 真正跑通"创建 → 保存 → freeze v1 → 改草稿 → freeze v2 → 切 active"再升级。

完整 closure-fix 计划见 `~/.claude/plans/golden-cooking-bee.md` 末尾的 §Closure fix 待办清单。

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

（恢复原始 Not Started 状态，本节待 closure-fix 实际跑通后由用户验收证据填充）

- [ ] 用户可从 Workspace 创建、编辑并冻结一个不依赖外部 Provider 的示例 Agent；
- [ ] 重复 freeze 请求不创建重复版本；同 key 不同 payload 返回冲突；
- [ ] FROZEN 内容与引用不可修改，active-version 与 deprecate 规则生效；
- [ ] Secret 明文不进入 snapshot、响应、日志或审计；
- [ ] 页面清楚显示校验错误、版本、digest 与 active 状态；
- [ ] 后端、前端测试及真实后端浏览器 E2E 通过。

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

## 7. Closure-fix 必做（先于"Partially Integrated"）

详细计划见 `~/.claude/plans/golden-cooking-bee.md`；以下条目按用户评审要求：

1. **合并新旧 `agents/{id}/versions/**` 路由**：把 `AgentManageController` 上的 `GET/POST versions`、`POST rollback`、`GET compare` 全部迁到 `AgentFreezeController`（已 ADR-002 化），按 Spring 路径优先级保留"新建 Agent / 列表"两类不冲突端点；`AgentManageController` 仅保留 onboarding / chat 等非 version 端点。
2. **新增"真正创建 Agent"端点**：`POST /api/v1/agents` 或 `POST /api/v1/agents/{id}/draft`（upsert 改成"不存在则创建，存在则更新"）。前端不再用随机 agentId。
3. **保持草稿可变，仅冻结快照**：`AgentDraftService.requireEditable` 删除；`AgentManageServiceImpl.updateAgent` 不再因 `activeVersionId` 拒绝；仅冻结后 secretRef 不可修改走 FROZEN 校验。
4. **恢复 JWT + 角色 + 审计**：
   - 在 `SecurityConfig` 把 `/api/v1/agents/**` 从 `permitAll()` 收紧为 `.authenticated()`，并按端点放宽 `POST /draft`、`POST /versions`、`PUT /active-version`、`POST /deprecate` 走 `hasAnyRole("ADMIN","OPERATOR")`；
   - `currentActor()` 在 JWT 上下文缺失直接抛 401 而不是 fallback `anonymous`；
   - `AgentVersionAuditWriter` 增补 `AGENT_DRAFT_UPDATED` / `AGENT_DRAFT_SECRET_REF_CHANGED` 写审计；
   - `RunnerCredentialAuthFilter` / `EnrollmentTokenAuthFilter` 旁路依旧保留（F01 已要求）。
5. **修复 Playwright 语法错误 + 真实跑通**：spec 中 `document.cookie = ...` 引号配对补全；mock 路径与真实后端路径分别去掉 `test.skip` 兜底；CI 接入 MySQL testcontainer。
6. **真实 UI + 后端跑通完整链路**：创建 → 保存草稿 → freeze v1 → 修改草稿 → freeze v2 → 切 active 到 v2。这是 F02 在 Partially Integrated 之前的最低门槛。
7. **处理 V94/V95 提交顺序和 FK**：
   - 确认 `V94__Runner_Infrastructure.sql` 已经或将被 commit（不是 dirty working tree 孤悬）；
   - `V95` 中 `agent_metadata.active_version_id` 改为正式 `FOREIGN KEY (active_version_id) REFERENCES agent_versions(id) ON DELETE NO ACTION`，与 ADR-002 §D-2.1 一致。
8. **入口唯一**：F02 status 在 `docs/features/README.md` `功能主线` 表与 `docs/功能完成度.md` 同步，全部回到 `Not Started`。

## 8. 关联文档

- [ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)
- [产品定位](../产品定位.md)
- [前端重建方案](../前端重建方案.md)
- [角色矩阵](../角色矩阵.md)
