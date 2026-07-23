---
slug: 002-agent-version-immutability-and-secret-reference
title: ADR-002 · AgentVersion 不可变规则与 SecretReference
status: Accepted
date: 2026-07-19
deciders: ORIN vNext 架构组
supersedes: []
amends:
  - 001-runner-dispatch-and-lease.md#D-1.2
amended-by: []
revision: v4.1
revision-note: |
  v4.1 基于 2026-07-19 第四轮评审反馈（3 实质阻塞 + 7 处遗留文本 + RFC 8785 库选型修正）小修收口：
  §D-2.8.2 lease_secret_binding 移除冗余 run_id / lease_id / runner_id 列，仅保留
  assignment_id 作为唯一事实键；§D-2.8.3 secret-bind 事务改用 run_assignment 单行
  FOR UPDATE 校验（status IN ('ASSIGNED','ACKED') AND lease_expires_at > NOW()），
  不再走 INSERT IGNORE、不再携带 request_digest、不再开独立 lease 表；同一事务
  INSERT 全部 CONTROL_PLANE refs，避免部分成功；
  §D-2.8.5 renew 响应统一为 {action, reason, lease_expires_at, trace_id}，
  lease_expires_at 每次返回以避免 Runner 停在旧时间点；
  §D-2.1.1 删除 accept_current_deprecation 分支，PUT /active-version 只切指针，
  旧版本 deprecate 单独走 POST /deprecate；
  §D-2.6.2 删除 freeze 阶段 Runner capability 检查（与 §D-2.6.1 矛盾）；
  §D-2.8.8 FAILED(SECRET_REVOKED) 拆为 LEASE_EXPIRED / ASSIGNMENT_TERMINATED /
  SECRET_REVOKED / RUNNER_LOCAL_SECRET_MISSING 四类，分别映射 Runner 是否能写
  /events + /result；
  §5 开放问题去除 grace window 命名、Knowledge MVP 必拒冻结；
  Python `canonicaljson` 移除推荐——按 RFC 8785 Appendix H 它属于"其他方案"，
  应自实现或用 Appendix G 参考实现 + test vectors 字节级验证。
  v1/v2/v3 直接作废，不保留兼容。
related:
  - ../Runner架构设计.md
  - ../架构设计.md
  - ../开发规范.md
  - ../API文档.md
  - ../部署指南.md
  - ../角色矩阵.md
  - ./001-runner-dispatch-and-lease.md
  - ../../TODO.md (P0 · ADR-002)
---

> 状态：**Accepted · v4.1**（2026-07-19 文档评审通过 → 原子提交）。本 ADR 仅作产品与架构决策；**不**创建迁移、不修改业务代码。R3 实施 PR description 首行写 `Refs ADR-002`。

## 1. 背景（Context）

### 1.1 当前代码现状

| 对象 | 现状 | 缺口 |
| --- | --- | --- |
| `com.adlin.orin.modules.agent.entity.AgentMetadata` | 可变草稿表（`name` / `description` / `mode` / `modelName` / `providerType` / `temperature` / `topP` / `maxTokens` / `systemPrompt` / `parameters` / `toolCallingOverride` / `mcpExposed`） | 没有"当前发布版本"指针；缺对 Tool / Knowledge / MCP / Secret 的引用表达 |
| `com.adlin.orin.modules.agent.entity.AgentVersion` | 已有 `config_snapshot` JSON、`version_number`、`is_active`、`change_description`、`created_by`、唯一约束 `uk_agent_version(agent_id, version_number)` | `deleteVersion` 允许删非活跃行；`rollbackToVersion` 通过 JPA setter 原地改 `is_active` 与 `AgentMetadata`；无 `content_digest`；无 `status` 字段；snapshot 仅序列化 AgentMetadata 的扁平字段，**不**包含工具/知识/MCP/Secret 引用 |
| `com.adlin.orin.modules.agent.entity.AgentAccessProfile` | per-agentId 明文/弱加密列：`apiKey`、`datasetApiKey` | 与 `gateway_secrets`（V72）双账本并存；Secret 跟 agentId 走，不跟 version 走 |
| `gateway_secrets`（V72） | 统一密钥中心已有：`secret_id`、`secret_type`、`encrypted_secret`、`key_hash`、`key_prefix`、`last4`、`base_url`（独立列）、`rotation_at`、`expires_at`、`user_id`、`created_by/updated_by`、`index(secret_type,provider,status)` | 缺 `secret_revision` / `secret_grace_until` 字段；Agent 调用 provider 的密钥尚未统一接入；缺 SecretReference 适配；缺 lease_secret_binding 表 |
| `com.adlin.orin.security.EncryptionUtil` | `Cipher.getInstance("AES")` 默认等价 `AES/ECB/PKCS5Padding`；key 缺失时返回明文 | ECB 模式不安全；缺 IV；缺 AAD/MAC，非 AE；缺 per-secret nonce；被多个模块共用，迁移范围必须跨模块 |
| `app.engine.task_runtime.TaskRuntime.execute_agent_task` | 按 `preferred_agent_id` 调用 `/api/v1/agents/{id}/chat` 或 `ephemeral:*` 内联 system_prompt | Run **不**绑 AgentVersion；可重放性不可证 |
| `com.adlin.orin.modules.knowledge.entity` (`KnowledgeBase` / `KnowledgeDocument` / `KnowledgeSkill` / `KnowledgeGraph`) | 知识侧已有独立实体 | 没有不可变的 `knowledge_snapshot`；当前没有引用形态 |
| `com.adlin.orin.modules.model.entity.ModelConfig` / `ModelMetadata` | 模型配置独立 | 没有不可变的 `model_version`；无法做版本化引用 |

### 1.2 仓库文档已表达的约束

- `../产品定位.md` §4 / §5 — Agent / AgentVersion 是核心对象；AgentVersion 是"不可变的 Prompt、模型、工具、知识与执行配置快照"；**不再**有独立 Prompt / Model / Knowledge / Tool 产品面。
- `../架构设计.md` §5 数据所有权 — Agent / AgentVersion 由 Java Control Plane 唯一定义；Runner 本地缓存必须可丢弃。
- `../角色矩阵.md` §3.2 — Administrator 管理 Provider / Secret；Creator / Operator 仅"使用已授权项、使用引用，不见明文"。
- `../部署指南.md` §6 — `ENCRYPTION_KEY` 必须设置，否则 API Key 可能以弱加密存储（**当前确实弱**）。

### 1.3 接受的明确验收口径

需求方提的硬验收四条件："Run 必须可复现 / Secret 不能进入不可变快照明文 / Secret 轮换不能引发新 AgentVersion / SecretReference 绑定关系变化可审计"——当前架构全部不满足，且 v1 在草稿真相源、digest 唯一约束、secret-bind 越权、轮换与幂等的内在冲突上引入额外不确定性。v2 全部重写。

## 2. 决策（Decision）

下面以 `D-2.x` 编号；每条都对应 v1 的某项反馈或新增口径。

### D-2.1 唯一草稿真相源

- `AgentMetadata` = 唯一可变草稿，保留现有扁平字段；新增 `active_version_id` FK → `agent_versions.id`（可空：尚未冻结过任何版本时）。
- `AgentVersion` 创建时即 **FROZEN**；**不存在 DRAFT**。
- `AgentVersion.lifecycle ∈ {FROZEN, DEPRECATED}`。
- `AgentVersion.is_active` 删除（active 状态由 `AgentMetadata.active_version_id` 持有）。
- **回滚 = 直接更新 `AgentMetadata.active_version_id`**，不创建内容相同的副本。
- 端点：
  - `GET /api/v1/agents/{agentId}` 返回 `active_version_id`；
  - `PUT /api/v1/agents/{agentId}/active-version` body `{ "version_id": "..." }` 切换；
  - FROZEN 版本本身 **不可** 通过 `PATCH /api/v1/agents/{agentId}/versions/{versionId}` 改字段（见 D-2.2）。

#### D-2.1.1 `PUT /active-version` 校验（v4.1 收紧）

`PUT /active-version` body `{ "version_id": "..." }` 必须满足**所有**约束，否则抛对应 ErrorCode：

| 条件 | 失败响应 |
| --- | --- |
| `version_id` 属于该 `agent_id` | `404 AGENT_VERSION_NOT_FOUND` |
| `version.status = FROZEN` | `200` + 切换成功 |
| `version.status = DEPRECATED`（目标已退役） | `409 RUN_VERSION_RETIRED`（v3 误写为 404；DEPRECATED 资源**存在但已退役**，应当是 conflict） |
| 新 `active_version_id` == 目标 `version_id`（幂等） | `200` + 不写 audit |

**只切指针**：`PUT /active-version` **不**自动 deprecate 旧版本——Operator 必须先 `PUT /active-version` 切到另一 FROZEN 版本，再单独 `POST /api/v1/agents/{agentId}/versions/{versionId}/deprecate` 把旧 version 标 DEPRECATED。**禁止**端点内合并操作；**禁止**请求体 `accept_current_deprecation` 等参数；**禁止**覆盖 `deprecation_reason`。

#### D-2.1.2 Run 引用 DEPRECATED 版本的统一响应（v4 修订）

新 Run 在创建或调度时若命中 `DEPRECATED` version：

- 业务端不允许新建引用 → 抛 `409 RUN_VERSION_RETIRED`（不是 404——资源存在但已退役）；
- Audit 写 `RUN_REJECTED_RETIRED_VERSION`。

这一行覆盖 v3 早期"404 RUN_VERSION_RETIRED"的措辞错误。

### D-2.2 不可变性边界

永久不可变的字段：

| 字段类别 | 字段 | 说明 |
| --- | --- | --- |
| 快照内容 | `config_snapshot`、`snapshot_schema_version` | 序列化后不可改 |
| 摘要 | `content_digest` | 摘要值不可改 |
| 引用桥接 | `agent_version_secret_refs` / `agent_version_tool_refs` / `agent_version_knowledge_refs` / `model_ref` 各行 | 冻结后禁止 INSERT / UPDATE / DELETE |

受控可改字段（由 Operator 角色执行，写审计）：

| 字段 | 说明 |
| --- | --- |
| `status` | 仅允许 `FROZEN → DEPRECATED` 一次转换 |
| `deprecated_at` / `deprecated_by` / `deprecation_reason` | 与 DEPRECATED 同时写入 |

应用层校验：服务层抛 `BusinessException(ErrorCode.AGENT_VERSION_FROZEN, "...")`；R3 实施 PR 可加 DB trigger。

`DELETE` 永远禁止：服务层抛 `BusinessException(ErrorCode.AGENT_VERSION_DELETE_FORBIDDEN, "...")`，把"删除"并入 DEPRECATED。

### D-2.3 content_digest 与幂等冻结

`content_digest` envelope 与 canonical 序列化规则见 §D-2.4。digest 入库字段 `content_digest`（64-hex）；DB 索引 `idx_agent_version_content_digest`，**不**设 `UNIQUE`——rollback 复用同内容与回滚契约冲突。

`snapshot_schema_version` 字段独立写出（V1 / V2 ...），与 digest 一起被 Runner 校对；**`snapshotSchemaVersion` 自己也进入 digest envelope**（见 §D-2.4）。

#### D-2.3.1 Freeze 同事务（v4 新增）

`POST /api/v1/agents/{agentId}/versions` 的 freeze 流程必须在一个 DB 事务内完成以下 1–4，任一失败整事务回滚，**不**写半截版本：

```text
BEGIN;
  -- 1. 读 AgentMetadata 草稿
  SELECT * FROM agent_metadata WHERE agent_id = :id FOR UPDATE;

  -- 2. 校验 freeze 请求的 secret_refs / tool_refs / knowledge_refs / model_ref
  --    含 SNAPSHOT_SCHEMA_INCOMPATIBLE 等冻结时校验（§D-2.4.1）

  -- 3. 计算 content_digest（canonicalize 见 §D-2.4.2）；失败 → 整事务回滚，
  --    抛 SNAPSHOT_CANONICALIZE_FAILED（**禁止**降级为非幂等路径）
  --    客户端必须重新提交合法输入

  -- 4. INSERT INTO agent_versions
  --    INSERT INTO agent_version_freeze_idempotency
  --    COMMIT
END;
```

- **UNIQUE 冲突处理**：INSERT INTO `agent_version_freeze_idempotency` 若 `(agent_id, idempotency_key_hash)` 已存在：
  - 事务回滚本次 INSERT；
  - 在**新事务**内读出已存在的 `agent_version_id` 与 `request_digest`；
  - 与本次 `request_digest` 比较：相同 → 返回历史 `agent_version_id`；不同 → 抛 `IDEMPOTENCY_KEY_CONFLICT`；
- 整个事务任一步抛错（`SNAPSHOT_CANONICALIZE_FAILED`、`SNAPSHOT_SCHEMA_INCOMPATIBLE`、`AGENT_VERSION_FROZEN`、`IDEMPOTENCY_KEY_CONFLICT` 等）→ 客户端重新发起；
- canonicalize 失败**不**允许"非幂等降级"——直接拒绝请求。

#### D-2.3.2 幂等记录（DB primary + Redis acceleration）

```text
agent_version_freeze_idempotency (
  agent_id              VARCHAR(50) NOT NULL,
  idempotency_key_hash  CHAR(64) NOT NULL,
  request_digest        CHAR(64) NOT NULL,
  agent_version_id      VARCHAR(50) NOT NULL,
  created_at            DATETIME NOT NULL,
  expires_at            DATETIME NOT NULL,
  PRIMARY KEY (agent_id, idempotency_key_hash),
  INDEX idx_idemp_expires (expires_at)
)
```

- **行为**：
  - 同一 `(agent_id, idempotency_key_hash)` + 相同 `request_digest` → 返回历史 `agent_version_id`；
  - 同一 `(agent_id, idempotency_key_hash)` + **不同** `request_digest` → 抛 `BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, ...)`，**不**创建新版本；
  - 首次请求走完整 freeze 路径（§D-2.3.1）；
  - `request_digest` 缺失 → 整 freeze 拒绝（`SNAPSHOT_CANONICALIZE_FAILED` 或 `SNAPSHOT_SCHEMA_INCOMPATIBLE`），**不**降级为非幂等——非幂等路径会绕过 §D-2.4 digest 校验，破坏 Run 可复现承诺；
  - `expires_at` 由后台 job 清理过期行；MVP 默认 24h。

- **Redis 仅做加速**：写入 DB 后同时把 `(agent_id, idempotency_key_hash) → agent_version_id` 缓存到 Redis 键 `idemp:{agent_id}:{key_hash}`（不存 client 原始 key，避免明文泄漏），TTL 24h。Redis 重启 / 淘汰 / 写 DB 后写 Redis 前崩溃都不影响正确性——DB 命中再回填 Redis。

- **Run 创建校验**：Run 创建时持久化 `run.agent_version_digest` + `run.snapshot_schema_version`；Runner 上报 digest 不一致返回 `LEASE_INVALID digest_mismatch`（与 ADR-001 §D-1.5 错误风格一致）。

- **digest 重算接口**：`GET /api/v1/agents/{agentId}/versions/{versionId}/digest`（GET only，不修改任何字段；供审计与验证）。

### D-2.4 快照内容：内联 vs 引用 + canonical JSON

#### D-2.4.1 引用形态分类

不可变快照**强制**要求被引用对象是真正的不可变实体；MVP 阶段对 Knowledge 采用"拒绝冻结"策略，对其他执行配置采用"内联优先、引用兜底"。

| 字段类别 | 存储位置 | 要求 |
| --- | --- | --- |
| 标量配置 | `config_snapshot` | `name / description / systemPrompt / temperature / topP / maxTokens / mode / toolCallingOverride / mcpExposed` 完整内联；与 `model_ref.base_url` 一起作为非敏感执行配置 |
| Model 引用 | `AgentVersion.model_ref` | **必须**指向不可变的 `model_version_id + model_version_digest`；MVP 不实现 → 整段内联 model 配置 + 不可变 base_url |
| Tool 引用 | `agent_version_tool_refs[]` | **必须**指向不可变的 `tool_version_id + tool_version_digest`；MVP 不实现 → 整段内联（按 §D-2.4.2 "内联工具稳定键" 排序） |
| **Knowledge 引用** | `agent_version_knowledge_refs[]` | **MVP 必拒冻结**：R3 不实现 `KnowledgeSnapshot`，因此 freeze validator 调用 `KnowledgeSnapshotService.requireImmutable(knowledge_snapshot_id)`，未实现则抛 `BusinessException(ErrorCode.SNAPSHOT_SCHEMA_INCOMPATIBLE, ...)`；含 Knowledge 的草稿**必须**等到 R4 `knowledge_snapshot` 落地后再开放 |
| Workflow / Collaboration DSL | `AgentVersion.workflow_ref` | **必须**指向不可变的 `workflow_version_id + workflow_version_digest`；MVP 不实现 → 整段内联 DSL AST 与 prompt 模板 |
| Provider Secret | `agent_version_secret_refs[]` | 永远只引用 `gateway_secrets.secret_id`，**不**内联明文（见 D-2.6）；base_url 等非敏感关联进 `model_ref.base_url` |

**禁止**：

- 把可变业务实体（未冻结的 `KnowledgeDocument` / `ModelConfig` 行 / `ToolConfiguration` 等）作为引用对象；
- 在 R3 MVP 阶段对 Knowledge 整段内联知识文档 / chunk / 索引内容——`KnowledgeSnapshot` 不实现就不应允许冻结该类 AgentVersion；
- 在 R3 MVP 阶段把 Workflow DSL 与 Knowledge 混在 "整段内联允许" 的同一段描述里——本节明确两者差异化处理。

#### D-2.4.2 Canonical JSON（修正 v3 NFC 错误）

两侧（Java + Python）**采用 RFC 8785 (JSON Canonicalization Scheme)**：

- Java：`com.adlin.orin.common.snapshot.JcsCanonicalizer`；
- Python：`orin_ai_engine.app.snapshot.jcs_canonicalize`；
- **关键规则**（按 [RFC 8785 §3.1](https://www.rfc-editor.org/rfc/rfc8785#section-3.1)）：
  - JSON 输出编码 UTF-8，**不**做 Unicode normalization（如 NFC/NFKC）——RFC 8785 明文规定保留原始 Unicode 字符串字节；
  - number 严格 IEEE-754 词法表示（禁止 `NaN` / `Infinity` 出现于合法 JSON）；
  - `null` 显式存在；
  - object key 唯一；
  - object key 按 UTF-16 code unit 升序；
  - array 顺序由调用方决定，JCS **不**改变 array 顺序；
- 库选型建议（**仅作参考**，R3 PR 不应被绑定）：
  - Java：`com.fasterxml.jackson.databind` + 自实现 RFC 8785，或 `com.github.erdtbaustelletiel:jcs`；
  - Python：**不要**预选 `canonicaljson`——按 [RFC 8785 Appendix H](https://www.rfc-editor.org/rfc/rfc8785#appendix-H)，`canonicaljson` 是"其他 Canonical JSON 方案"（非 RFC 8785 兼容实现）；参考实现见 [Appendix G](https://www.rfc-editor.org/rfc/rfc8785#appendix-G) 或自行实现 + 用 RFC 8785 test vectors 字节级验证；
- R3 PR 必须固定两侧字节级一致；用 RFC 8785 提供的 test vectors 验证；不得用 `canonicaljson` 作为 RFC 8785 实现，否则会产生跨语言 digest 不一致。

**ORIN schema 预处理规则**（在调用 JCS 之前由 ORIN 处理；**不**依赖 JCS 内部排序）：

- **引用数组排序**（digest 与数组重排无关）：
  - `tools[]` 按 `toolVersionId`（不可变）升序；
  - **内联 tools**（无 `toolVersionId`）按 **`name`（字符串稳定键）**升序——`name` 由 R3 schema 强制唯一且不可变；
  - `knowledge[]` 按 `knowledgeSnapshotId` 升序；
  - `workflow` 按 `workflowVersionId` 升序；
  - `secretRefs[]` 按 `alias` 升序；
- ORIN schema 文档（R3 PR 提交时附）规定 `name` 命名规则（不可为空、长度上限、字符集、唯一性约束）；
- "排序键缺失时"（如内联 tool 缺 `name`）→ freeze 拒绝 `SNAPSHOT_SCHEMA_INCOMPATIBLE`，不允许隐式 fallback。

#### D-2.4.3 digest envelope（固定形状）

`content_digest = SHA-256(JCS(canonical_digest_envelope))`；**`snapshotSchemaVersion` 自己必须在 envelope 内**：

```json
{
  "snapshotSchemaVersion": 1,
  "config": {},
  "model": {},
  "tools": [],
  "knowledge": [],
  "workflow": {},
  "secretRefs": []
}
```

- `config`：标量配置 + 非敏感关联（base_url 等）的内联对象；
- `model`：model_ref 不可变引用对象（含 `model_version_id` + `model_version_digest`），**或** MVP 内联对象；
- `tools`：冻结前按 `tool_version_id`（引用形态）或 `name`（内联形态）升序排序的数组；
- `knowledge`：MVP 必空——含 Knowledge 的草稿在 R3 必拒冻结（§D-2.4.1）；未来 `knowledge_snapshot_id` 升序排序；
- `workflow`：workflow_ref 不可变引用对象，或 MVP 内联 DSL 对象；
- `secretRefs`：冻结前按 `alias` 升序排序的数组（参见 D-2.6）。

**禁止**在 envelope 内出现明文 Secret / Token / Password / Private Key / Connection String。

冻结前的 reference arrays（`tools / knowledge / secretRefs`）**必须**按稳定键（实现各自字段的"id"）升序排序；内联 tools 按 `name` 升序，**不**依赖 JCS 内部排序——确保 digest 与重排顺序无关。

### D-2.5 Run 引用规则

- `run` 表新增：
  - `agent_version_id`（不可空，FK → `agent_versions.id`）；
  - `agent_version_digest`（不可空，64 字符串）；
  - `snapshot_schema_version`（不可空）。
- Run 创建校验：`agent_version.status = 'FROZEN'`；**DEPRECATED 不再被新建 Run 引用**——DEPRECATED 上的请求返回 `409 RUN_VERSION_RETIRED`（资源存在但已退役，不是 404）；
- 既有 Run 不受影响：lease 未过期时继续在该 lease 上执行；lease 过期需重新分配时按 ADR-001 §D-1.4.3 自动重新分配规则；
- 用户手动 Retry = 新 `run_id`（ADR-001 §D-1.4.3）；不复用旧 AgentVersion 状态机；
- 过渡期 `agent_id` 调用方自动绑定 `AgentMetadata.active_version_id`，响应中暴露 `version_id` 便于审计。

### D-2.6 SecretReference（5 字段，2 source，三阶段校验）

```json
{
  "alias": "openai.primary",
  "source": "CONTROL_PLANE",
  "secretId": "gsec_provider_900000123",
  "required": true,
  "injectAs": "OPENAI_API_KEY"
}
```

- `alias`：AgentVersion 内稳定句柄，**唯一**（同一版本不可重复）；
- `source ∈ {CONTROL_PLANE, RUNNER_LOCAL}`：
  - `CONTROL_PLANE`：必填 `secretId`（指向 `gateway_secrets.secret_id`）；pre-freeze 校验 `status=ACTIVE` 与未过期；
  - `RUNNER_LOCAL`：必填 `localKey`（Runner 本地 env / 文件名）；**控制面永远不获明文**——DTO 上拆为 `secretId` / `localKey` 二选一字段；
- `required`：true 时控制面会按下方三阶段校验，缺则拒绝；false 缺失走降级（Run 可启动但 secret 不可用）；
- `injectAs`：在 Runner `runtime.secret_resolver` 中映射的最终 key 名（建议 `<PROVIDER>_<ASPECT>` 大写）；详见 §D-2.7 响应形态。

`scope` **不属于** SecretReference；它属于 `gateway_secrets` 的 `user_id` 与 RBAC，决定"谁能引用这个 secret"。AgentVersion 不自声明 scope。

#### D-2.6.1 RUNNER_LOCAL 三阶段校验（不允许在 freeze 阶段读取 Runner 状态）

| 阶段 | 校验内容 | 失败处理 |
| --- | --- | --- |
| **Freeze** | 仅校验 `localKey/injectAs` 结构合法（命名规则、长度、字符集）；不读取任何 Runner capabilities | 通过冻结，落入 secret_refs |
| **Run 创建 / 调度** | 匹配 Runner / RunnerPool 时，只选择声明具备对应 `localKey` capability 的 Runner；Control Plane 保存的 capability 只是 `localKey` 字符串列表，**不**包含值 | 没有匹配 Runner：保持 `PENDING`（默认）；显式策略可让 Operator 触发"重新选择另一台具备能力的 Runner"，但**禁止** Control Plane 自动把 Run 切到另一个 AgentVersion（`run.agent_version_id` 必须固定） |
| **lease / secret-bind** | Runner 在 `/secret-bind` 响应消费前，把 `runtime.secret_resolver` 的 `localKey` → 值在本机解析；若本机无该 env / 文件 | 拒绝消费 → 通过 `/events` 上报 `local_secret_unresolved` 事件，再由 `/result` 提交 `assignment.status = FAILED`、`terminal_reason = RUNNER_LOCAL_SECRET_MISSING`；Run 终态 `FAILED`；**不**自动改写 Run 历史；调度侧是否把 assignment 重新分配给另一台具备能力的 Runner 必须在 Operator 配置里显式允许（R3 PR 默认 `false`，即 Run 直接 FAILED） |

**禁止**：

- 在 `Run 创建 / 调度` 阶段自动把 Run 切到 "全 CONTROL_PLANE 的 AgentVersion"——这违反 `run.agent_version_id` 不可变 + AgentVersion 必须显式冻结的语义（§D-2.1 / §D-2.5）；
- 通过 `/renew` 响应携带 RUNNER_LOCAL_SECRET_MISSING——`/renew` 契约只表达 cancel / drain / no_op，不表达 Run-level 失败事实；
- 让 Run 的失败与 lease 续租失败耦合——lease renewal 与 Run assignment 失败是两个独立状态机（见 ADR-001 §D-1.4）。

**Control Plane 不得**：

- 在 Runner 注册或心跳响应里强制收集本地 Secret 明文 / 值哈希 / 环境变量快照；
- 持有 `localKey` 与值的对照表；
- 允许 Operator / Administrator 通过 Admin 端点 push、list、search 任何本地 Secret 值。

只能持久化 `localKey` 与 `injectAs` 字符串、`required` 标志、声明的 capability 集合。Runner 解析失败由 Runner 自己承担，Control Plane 不"代填"。

#### D-2.6.2 已知 invalid 组合

- `required=true` 且 `source=CONTROL_PLANE` 且 `secretId` 不存在 → 拒绝冻结；
- `injectAs` 已占用且语义冲突（同一版本内） → 拒绝冻结；
- 字符集 / 长度 / 命名规则不合法（`localKey`、`injectAs`、alias） → 拒绝冻结。

**freeze 阶段不读 Runner capabilities**：capability 匹配只在 Run 调度阶段执行（§D-2.6.1）。任何"freeze 时已能确定没有任何注册 Runner 声明该 `localKey`"类校验都**禁止**出现——freeze 阶段永远不应感知具体 Runner。

### D-2.7 secret-bind 协议（amend ADR-001 §D-1.2：第六个机器端点）

本 ADR **amend** `001-runner-dispatch-and-lease.md#D-1.2`：在 ADR-001 §D-1.2 五端点之外，机器通道新增第六端点：

| 端点 | 方法 | 用途 | 关键约束 |
| --- | --- | --- | --- |
| `/api/system/runners/{runnerId}/runs/{runId}/secret-bind` | POST | 返回 Run 在该 lease 下允许的 materialized secrets | Runner 只提交 `runId + leaseId`；Control Plane 从 Run → AgentVersion 读取允许的 SecretReference；校验 lease 归属 |

amend 同步要求（本 ADR `Accepted` 时**必须同时**完成）：

- 在 `001-runner-dispatch-and-lease.md` frontmatter 增 `amended-by: [ADR-002]`；
- 在 `001-runner-dispatch-and-lease.md` §D-1.2 表内同步为六端点（增 secret-bind 一行，下文"禁止"段同步新增不再有 `/secret-inject`）；
- 在 `../API文档.md §2.1` 把六端点表格同步落地；
- `../Runner架构设计.md §2` 拓扑段同步增 secret-bind 一行；
- `docs/adr/README.md` 索引 ADR-001 行 status 字段保持 `Accepted`，ADR-002 行 §7 验收条件保留以上同步项。

**禁止**：

- 让 Runner 上传 `secret_refs` ——这正是 v1 / v2 早期版本的越权读取风险来源；
- 引入 `/secret-inject` 端点作为独立路径——materialized secrets **必须**在 `/secret-bind` 同一响应内返回；
- 在 lease claim / renew 响应里"顺便"附 secret——仅有 `secret-bind` 端点下发。

请求体：

```json
{ "assignment_id": "..." }
```

- 请求体**只**含 `assignment_id`；
- Runner 身份由 `Authorization: Bearer <Runner Credential>` 头部携带；
- URL 路径 `{runnerId}` 与 `{runId}` 由 Control Plane 从 `assignment_id` 派生并校验：URL `{runId}` 必须等于 `run_assignment.run_id` 派生值；URL `{runnerId}` 必须等于 `run_assignment.runner_id`，**不**允许跨 Runner 调用 `/secret-bind`。

响应体（**修正**：每个 SecretReference 仅有一个 `injectAs`，materialized response 按 injectAs 直接给值；非敏感项如 `base_url` 应已落在 `model_ref.base_url` 中由 snapshot 携带，不再次下发）：

```json
{
  "lease_id": "...",
  "run_id": "...",
  "materialized_secrets": {
    "OPENAI_API_KEY": "sk-..."
  },
  "secret_revision_bindings": {
    "OPENAI_API_KEY": "gsec_provider_900000123@rev:2026-07-19T10:00:00Z"
  },
  "expires_at_epoch_ms": 1730000000000
}
```

> `materialized_secrets` 的 key 直接是 `injectAs`，便于 Runner 端直接 `resolver.set(injectAs, value)`；不再经过 `alias → secretObj` 二次映射。映射漏写风险留在 Control Plane 的 §D-2.6 secret_refs 校对即可。

Runner 把 `materialized_secrets` + `secret_revision_bindings` **仅**保存到进程内 `runtime.secret_resolver` 的 in-memory map；lease 结束 / 失败 / 清 map 时立刻清空；禁止写盘 / 跨 lease 复用 / 上送非 secret-bind 端点。`expires_at_epoch_ms` 之后即使 Runner 仍在执行也不得使用任何过期 secret——按 §D-2.8 终态执行。

### D-2.8 Secret 轮换 + revision 绑定（Run 可复现的真实边界）

#### D-2.8.1 gateway_secret_revisions 表

旧密文不能只在 `gateway_secrets.encrypted_secret` 原地覆盖；必须分到独立的版本表：

```text
gateway_secret_revisions (
  secret_id            VARCHAR(100) NOT NULL,    -- 引用 gateway_secrets.secret_id
  revision             VARCHAR(64) NOT NULL,      -- 单调递增字符串 / 时间戳 ID
  encrypted_envelope   VARCHAR(2048) NOT NULL,   -- v2 envelope；envelope 是密文唯一真相（含 algorithm/keyId/nonce/ciphertext+tag）
  created_at           DATETIME NOT NULL,
  retired_at           DATETIME NULL,             -- NULL = 当前 active
  PRIMARY KEY (secret_id, revision),
  INDEX idx_revision_retired (retired_at),
  FOREIGN KEY (secret_id) REFERENCES gateway_secrets(secret_id)
)
```

`gateway_secrets` 主表不再直接持有 `encrypted_secret`，改为 `active_revision` pointer + 元数据（`secret_type`、`status`、`user_id`、`key_prefix`、`last4`、`base_url` 等）；避免两个真相源。

AAD / algorithm / keyId / nonce **不**单独落列——全部从 `encrypted_envelope` 解出或从不可变字段派生。

#### D-2.8.2 lease_secret_binding 表（含状态机）

`assignment_id` 是唯一"事实键"——`lease_id` / `run_id` / `runner_id` 由 `run_assignment` 派生，**不**在 `lease_secret_binding` 重复落列，避免四列互相对不齐：

```text
lease_secret_binding (
  assignment_id        VARCHAR(50) NOT NULL,     -- 唯一事实键；派生 lease_id / run_id / runner_id
  inject_as            VARCHAR(255) NOT NULL,    -- 即 §D-2.6 的 injectAs
  secret_id            VARCHAR(100) NOT NULL,
  revision             VARCHAR(64) NOT NULL,
  status               VARCHAR(20) NOT NULL,     -- ACTIVE | INVALIDATED | RELEASED
  bound_at             DATETIME NOT NULL,
  invalidated_at       DATETIME NULL,
  invalidation_reason  VARCHAR(64) NULL,         -- REVOKED | LEASE_EXPIRED | ASSIGNMENT_TERMINATED
  PRIMARY KEY (assignment_id, inject_as),
  INDEX idx_lsb_assignment_status (assignment_id, status),
  INDEX idx_lsb_secret (secret_id, revision),
  FOREIGN KEY (assignment_id) REFERENCES run_assignment(id),
  FOREIGN KEY (secret_id, revision) REFERENCES gateway_secret_revisions(secret_id, revision)
)
```

- `status = ACTIVE`：binding 有效，Runner 持有的 `materialized_secrets` 仍可用；
- `status = INVALIDATED`：被 Revocation 显式作废；后续 `renew` 必须返回 `action=cancel reason=SECRET_REVOKED`；
- `status = RELEASED`：assignment 终态触发后台清理由 ACTIVE → RELEASED（仅状态变化，密文已被 `gateway_secret_revisions.retired_at` 单独控制）。

#### D-2.8.3 `/secret-bind` 事务（v4.1 收紧）

**`assignment_id` 是唯一入口**；`lease_id` / `run_id` / `runner_id` 由 `run_assignment` 行派生，**不**作为请求参数传 control plane（请求体仅 `{ assignment_id }`）。RUNNER 必须通过既有 lease claim 拿到 assignment_id，再以 `assignment_id` 调用 `/secret-bind`。

```text
BEGIN;
  -- 1. 单行锁 run_assignment：assignment 状态 + lease 归属 + lease 未过期，
  --    全部用一次 FOR UPDATE 校验，不另开 lease 表
  SELECT *
    FROM run_assignment
   WHERE id = :assignment_id
     AND runner_id = :runner_id              -- lease 归属 Runner
     AND status IN ('ASSIGNED', 'ACKED')      -- ADR-001 §D-1.4.2 五态赋值机
     AND lease_expires_at > NOW()
     FOR UPDATE;
  -- 命中 0 行 → ROLLBACK，返回 410 LEASE_EXPIRED 或 404 ASSIGNMENT_NOT_FOUND

  -- 2. 批量读 active_revision（一次 SQL 取所有 secretId）
  SELECT secret_id, active_revision
    FROM gateway_secrets
   WHERE secret_id IN (:secret_ids)
     AND status = 'ACTIVE'
     FOR UPDATE;

  -- 3. 在同事务内 INSERT 该 AgentVersion 的全部 CONTROL_PLANE refs
  --    （普通 INSERT，捕获 duplicate key；不走 INSERT IGNORE）；
  --    捕获到 DuplicateKeyException → 读取已存在 binding（status = ACTIVE），
  --    不重新选择 version；不替换原 binding 的 revision
  FOR each (secret_id, inject_as, revision) IN step2 results:
    INSERT INTO lease_secret_binding
      (assignment_id, inject_as, secret_id, revision, status, bound_at)
    VALUES
      (:assignment_id, :inject_as, :secret_id, :revision, 'ACTIVE', NOW())
    -- On unique violation: select existing row and keep its revision; do not re-bind

  -- 4. RUNNER_LOCAL refs 不在事务里处理：Runner 在本机解析
  --    （§D-2.6.1 第 3 阶段），control plane 不持有 binding

  -- 5. COMMIT
END;
```

- **完整性检查**：step 2 SELECT `WHERE IN (:secret_ids)` 必须返回**所有** required CONTROL_PLANE refs 的 `(secret_id, active_revision)` 行；任一 Secret 缺失、`status ≠ 'ACTIVE'` 或 `active_revision` 为 NULL，整事务 ROLLBACK，返回 `404 SECRET_REFERENCE_NOT_FOUND`（提示哪个 `inject_as` 找不到对应 secret_id）；
- **必须**在同一事务内完成 1–4；任一失败 → ROLLBACK，**不**半成品 binding；
- `request_digest` **不**在本事务——它是 freeze 阶段的幂等记录（§D-2.3），与 secret-bind 无关；
- step 3 不用 `INSERT IGNORE`；用普通 INSERT + DuplicateKey 捕获 → 读出已存在 binding（status = ACTIVE），**不**重新选择最新版；
- step 2 在 `WHERE IN` 的同时**保证**所有 CONTROL_PLANE refs 同事务可见——避免部分成功；
- 整事务 COMMIT 后再向 Runner 返回 `materialized_secrets` 响应——失败 → 4xx，**不**返回明文。

#### D-2.8.4 四种操作语义

| 操作 | 触发者 | 对 lease_secret_binding 的影响 | 对 gateway_secret_revisions 的影响 | 对 `gateway_secrets.active_revision` 的影响 |
| --- | --- | --- | --- | --- |
| **Rotation** | Administrator | active lease 的 binding 不变，仍用旧 revision；新 assignment 的 lease 取最新 revision | 插入新 revision 行；旧 revision `retired_at=NOW()` | `active_revision` 更新为新 revision |
| **New lease / New assignment** | scheduler | 首次 `/secret-bind` 走 §D-2.8.3 事务取 latest active revision | 无写入 | 不变 |
| **Revocation** | Administrator | **所有**对该 `secret_id` 的 `ACTIVE` binding → `status = INVALIDATED`、`invalidated_at = NOW()`、`invalidation_reason = 'REVOKED'`；下一次 `/renew` 在响应中返回 `action=cancel, reason=SECRET_REVOKED` | 所有 revision 行 `retired_at=NOW()` | `status='REVOKED'` |
| **Cleanup** | 后台 job | `assignment` 终态 + binding `status='ACTIVE'` 不存在（已 RELEASED / INVALIDATED）→ 把遗留 binding 物理删除 | 物理 DELETE 已 `retired_at` 的 revision 行（默认 retention = 24h） | 不变 |

#### D-2.8.5 `/renew` 响应统一形状（v4.1 收紧）

长 Run 完成 `secret-bind` 后只周期调用 `/renew`；renew 必须在响应中携带**最新 `lease_expires_at`**，否则 Runner 停在首次 secret-bind 返回的旧时间点提前断开 lease。

`/renew` 响应统一为：

```json
{
  "action": "no_op",
  "reason": null,
  "lease_expires_at": "2026-07-19T12:34:56Z",
  "trace_id": "..."
}
```

- `action ∈ {no_op, cancel, drain}`；
- `reason` 仅在 `cancel` 时必填（`SECRET_REVOKED` / `LEASE_EXPIRED` / `ASSIGNMENT_TERMINATED`）；`no_op` / `drain` 时 `reason = null`；
- `lease_expires_at` **每次**响应都返回（no_op 时也返回），Runner 据此刷新本地 lease 到期计时；
- `cancel + reason=USER_CANCELLED` → Runner 停止本地任务；**控制面已有 CANCELLED 事实**，**不要求** Runner 再次提交 result；
- `cancel + reason=SECRET_REVOKED` → lease 尚有效时允许 Runner 走 `/events` + `/result` 提交 `FAILED(SECRET_REVOKED)`；
- `cancel + reason=LEASE_EXPIRED` → Runner 立刻停用所有 materialized secrets；控制面**不再接受**任何写入；
- `cancel + reason=ASSIGNMENT_TERMINATED` → 视 Runner 本地执行结果决定终态（`COMPLETED` / `FAILED` / `CANCELLED`），但**仅在 Runner 收到此响应前尚未收到 lease-expired 信号时**可提交；响应到达后再提交控制面返回 410；
- lease 已过期或 assignment 已达终态（`COMPLETED` / `FAILED` / `CANCELLED` / `EXPIRED`）→ `/renew` 直接返回 **`410 LEASE_EXPIRED`** 或 **`409 ASSIGNMENT_TERMINATED`**，**不**返回 `cancel` action，**不**允许再次提交 result；
- `/renew/{leaseId}` 一次只控制当前 lease；**删除**"一个响应取消多个 lease / 响应 JSON 数组由 R3 PR 决定"——同 Runner 上其他 lease 在它们各自的 renew 调用里独立处理；
- ADR-002 `Accepted` 切换时同步在 ADR-001 §D-1.2 renew 响应 JSON 字段表里加 `reason` 与 `lease_expires_at` 字段；`expires_at_epoch_ms` 不再在 secret-bind 响应中作为 grace 概念出现。

#### D-2.8.6 24h 仅控制 revision 物理删除，与授权无关

- **不再使用 grace 概念**作为凭据硬期限；v3 §D-2.8.4 提到 "grace 失效" 一类短语已被本文删除；
- Secret 使用授权 = "assignment.status ∈ {ASSIGNED, ACKED} ∧ lease_expires_at > NOW() ∧ binding.status = ACTIVE ∧ secret.status = ACTIVE"；
- 24h 仅是 `gateway_secret_revisions` 物理删除 retention：assignment 终态后 + binding 已转 RELEASED + `retired_at + retention < NOW` 才物理 DELETE；
- Long Run 可超过 24h；只要 assignment 与 binding 都 ACTIVE，对应 revision 保留可解密；
- Revocation 是**唯一**立刻终止授权的操作，与 retention 无关。

#### D-2.8.7 assignment 终态触发 binding 清理

ADR-001 §D-1.2 已**禁止** `release` 端点，因此 binding 清理不是"显式 release"，而是由后台监听 `run_assignment.status` 终态变更触发（事件或定时扫表，R3 PR 决定形态）。终态包含：

- `COMPLETED` / `FAILED` / `CANCELLED` —— 见 ADR-001 §D-1.4.2；
- `EXPIRED` —— lease 过期回收，binding 自动失效。

assignment 终态 → 后台扫描：所有 `lease_secret_binding` 中 `assignment_id = :id AND status = ACTIVE` → `status = RELEASED`、`invalidated_at = NOW()`、`invalidation_reason = 'ASSIGNMENT_TERMINATED'`。

#### D-2.8.8 Run 可复现边界（重写）

- **非敏感执行配置**（`config_snapshot` / `content_digest` / 引用桥接 / `snapshot_schema_version`） = 永久可复现；
- **历史凭据** = 仅在 `(assignment.status ∈ {ASSIGNED, ACKED} ∧ lease_expires_at > NOW() ∧ binding.status = ACTIVE ∧ secret.status = ACTIVE)` 四元组都满足时可解密；任一不满足即解密失败，Runner 必须立刻走 `/events` + `/result` 提交对应 `terminal_reason` 的失败，**不**自动改写 Run 历史；
- 失败原因必须区分：

  | 不满足条件 | `terminal_reason` | Runner 能否再写 `/events` / `/result` |
  | --- | --- | --- |
  | `lease_expires_at ≤ NOW()` | `LEASE_EXPIRED` | 不能；写请求返回 410 |
  | `assignment.status ∈ {COMPLETED, FAILED, CANCELLED, EXPIRED}` | `ASSIGNMENT_TERMINATED` | 不能；写请求返回 410 |
  | `binding.status = INVALIDATED` | `SECRET_REVOKED` | 能；正常写 `/result` `FAILED(SECRET_REVOKED)` |
  | `binding.status = RELEASED` | `LEASE_EXPIRED`（assignment 已终态） | 不能 |
  | `secret.status = REVOKED` | `SECRET_REVOKED` | 能 |
  | `RUNNER_LOCAL` 本机 env 缺失 | `RUNNER_LOCAL_SECRET_MISSING` | 能（通过 §D-2.6.1 第 3 阶段 `/events` + `/result`） |

  Runner 必须根据失败原因选择正确路径——`LEASE_EXPIRED` / `ASSIGNMENT_TERMINATED` 之后无法再投递任何事件或结果（控制面已停止接受该 lease 的写入）；其他失败仍可走 `/events` + `/result` 提交终态；
- RunDetail 页面只展示 "本 Run 引用 secret_id X revision Y"；revision 何时被 retention 物理删除与 Run 可复现无关。

### D-2.9 审计与可追溯

落现有 `audit_log`（V93）；不新增表。

| 事件 | 字段 |
| --- | --- |
| `AGENT_VERSION_FROZEN` | `agent_version_id, content_digest, snapshot_schema_version, actor, secret_refs[*].(alias, source, ref_id, injectAs)` |
| `AGENT_VERSION_DEPRECATED` | `agent_version_id, actor, reason, deprecated_at` |
| `AGENT_DRAFT_SECRET_REF_CHANGED` | `agent_id, alias, before.(source,ref_id), after.(source,ref_id), actor` |
| `SECRET_REVISION_CREATED` | `secret_id, revision_id, actor`（不记明文） |
| `SECRET_REVISION_BOUND` | `lease_id, run_id, alias, secret_id, revision_id, bound_at` |
| `LEASE_SECRET_BOUND` | 同上 + `runner_id` |
| `SECRET_REVISION_RETIRED` | `secret_id, revision_id, retired_at`（Revocation 或 retention 到期物理 DELETE 前置事件） |
| `SECRET_PLAINTEXT_REVEAL` | `secret_id, actor, confirm_reveal` |

### D-2.10 日志与回显脱敏（修正 v1 "REDACTED + 首尾 4 位" 自相矛盾）

全部替换为 `***REDACTED***`，**不**保留首尾四位字符——`last4` 只来自 `gateway_secrets.last4` 摘要字段，不从 value 切片。

三道防线并行：

1. **结构化 denylist**：JSON Schema / Protobuf 注解对敏感字段（`api_key` / `bearer_token` / `password` / `private_key` / `connection_string`）打 tag；写入日志 / Trace / 审计 metadata 前由 `runtime.secret_resolver.redact(value)` 整体替换；
2. **lease materialized value exact-match**：当前 lease 解析到的 secret value 作精确字符串匹配替换；in-memory map 的 value 不进任何日志 / Trace；
3. **拒绝任意 Secret 对象**：日志 SDK 默认不接受 `Secret` / `ProviderCredential` 类型实例；序列化白名单字段；**禁止**关键字黑名单（关键字无法防止拼写变体泄漏）。

reveal API 与二次确认沿用 v1 §D-2.10。

### D-2.11 加密硬化（基于仓库现状，v3 收紧）

#### D-2.11.1 算法与 envelope（envelope 作为唯一真相源）

- **算法**：`AES/GCM/NoPadding`；
- **nonce**：每条密文随机 12 字节（GCM 推荐长度），**不**写死 16 字节；
- **JCE 输出顺序**：`ciphertext ‖ tag`（即 Java `Cipher.doFinal` 输出流默认的拼接序）；
- **envelope 字符串化**：`v2:<keyId>:base64url(nonce):base64url(ciphertext_tag_concat)`；
  - `keyId` 在 envelope 头部出现一次；
  - `nonce`、`ciphertext`、`tag` 通过 envelope 解析得到；
- **gateway_secrets / gateway_secret_revisions 列**（envelope 是密文唯一真相）：
  - **不**单独保留 `encryption_nonce` 列；
  - **不**单独保留 `encryption_aad` 列；
  - **不**单独保留 `encryption_algorithm` 列（`AES_GCM_256` 是 R3 唯一支持算法；后续算法演进由 envelope 版本号承担）；
  - 保留 `encryption_key_id`（冗余缓存，envelope 头部也有，不作为真相）；
  - 元数据列（`secret_type`、`status`、`key_prefix`、`last4`、`base_url`、`user_id`、`active_revision`）**不**含密文相关列；
- **AAD 编码规则（v4 明确）**：

  ```text
  AAD_BYTES = UTF-8("ORIN_AAD_v1" || 0x1F || secret_id || 0x1F || secret_type || 0x1F || "AES_GCM_256")
  ```

  - 以 UTF-8 字节编码；
  - `0x1F` (Unit Separator) 作字段分隔符，避免字段值含分隔符造成歧义；
  - `"ORIN_AAD_v1"` 作版本前缀，未来 AES-GCM 算法演进或 AAD 字段增改时升 `v1` → `v2`；
  - `secret_type` 是 `gateway_secrets.secret_type`（`PROVIDER_CREDENTIAL` / `CLIENT_ACCESS` / `MCP_ENV` 等）；
  - **禁止**用可变 alias 作为 AAD 组成部分；
  - AAD 从不可变字段（`secret_id` + `secret_type` + 版本前缀）派生，不再落列。

- 读取时按 envelope `keyId` 选 active key；envelope 自身提供 nonce / ciphertext / tag；AAD 按本节规则重新派生。

#### D-2.11.2 ENCRYPTION_KEY 定义

- `ENCRYPTION_KEY` **必须**是 Base64 编码串，解码后**恰好**得到 32 字节（256-bit AES key）；
- **不**接受任意 UTF-8 "≥ 32 字节" 字符串（v2 描述已被本节修正）；
- 推荐生成：`openssl rand -base64 32`；
- **缺失即启动失败**：应用启动期 `EncryptionUtil.initialize()` 直接 `IllegalStateException`，删除 v1 的"key 未配置返回明文"分支；
- 当下仓库的 `EncryptionUtil` 仍以"缺失 → 返回明文"实现——本节为 R3 行为目标，**未实施前不冒充现状**（见 `../部署指南.md §6` 同步标注）。

#### D-2.11.3 EncryptionUtil 调用方（仓库实测）

R3 PR 必须先 `grep -r "EncryptionUtil\b" orin-backend/src/main/java` 列出全部调用方再统一替换为 `JceGcmEncryptionService`。已实测清单（v2 推测的 `ProviderCredential/AuditLog` 等不准）：

- `com.adlin.orin.gateway.service.GatewaySecretService`
- `com.adlin.orin.gateway.service.impl.ExternalIntegrationServiceImpl`
- `com.adlin.orin.modules.setup.service.SetupInitializeService`
- `com.adlin.orin.modules.setup.service.SetupStatusService`

R3 PR 在 PR description 列出 grep 输出与对应替换动作。**禁止**只升 `gateway_secrets` 路径而让其他调用方继续使用 ECB。

#### D-2.11.4 ErrorCode 与 HTTP status 映射（移到 R3 PR review）

- 仓库现状：[`com.adlin.orin.common.exception.ErrorCode`](../../orin-backend/src/main/java/com/adlin/orin/common/exception/ErrorCode.java) 只含 `code` + `message`，**没有**内含 HTTP status 字段；
- HTTP status 映射由 `GlobalExceptionHandler`（或同等位置）独立完成——本 ADR 不假设它在 ErrorCode 内部；
- **R3 PR 应同时**：
  - 在 `ErrorCode` 增列 `AGENT_VERSION_FROZEN` / `AGENT_VERSION_DELETE_FORBIDDEN` / `AGENT_VERSION_NOT_FOUND` / `RUN_VERSION_RETIRED` / `RUN_REJECTED_RETIRED_VERSION` / `LEASE_INVALID_SECRET_EXPIRED` / `LEASE_INVALID_SECRET_NOT_BOUND` / `LEASE_INVALID_DIGEST_MISMATCH` / `LEASE_INVALID_SECRET_REVOKED` / `IDEMPOTENCY_KEY_CONFLICT` / `SNAPSHOT_SCHEMA_INCOMPATIBLE` / `SNAPSHOT_CANONICALIZE_FAILED` / `SECRET_REFERENCE_NOT_FOUND` / `SECRET_REVISION_RETIRED` / `RUNNER_LOCAL_SECRET_MISSING` 等枚举（仅 `code` + `message`，http status 仍由 GlobalExceptionHandler 映射），或扩字段 `httpStatus`；
  - 同步 `GlobalExceptionHandler` 中的 → status 映射；
  - 单元测试覆盖每枚举值的映射；
- **不**允许把语义塞进 `OPERATION_FAILED.message` 字符串；
- ErrorCode 注册是 **R3 层 PR review 验收项**，**不**作为 `ADR-002 Accepted` 的文档验收前置。

### D-2.12 角色与权限边界

- **Creator**：写自己的 Agent 草稿（`/api/v1/agents/{agentId}/draft/secret-refs` 增删改）、触发 freeze；读自己 `active_version_id` 的 digest；**不可**写已 FROZEN 版本的 secret_refs（FROZEN 之后只允许 GET `secret_refs` 列表与 digest）；
- **Operator**：对 FROZEN 版本做 DEPRECATED；指定 Run 用哪个版本；**不可**写草稿 / 不可读 Secret 明文；
- **Administrator**：管理 `gateway_secrets`、轮换、撤销、reveal（带二次确认）；读所有 audit；
- **Endpoint Consumer**：调用 `/v1/agents/{key}/runs`，不接触 `secret_id` / `revision`；只看 Run 状态与结果；
- **Runner**：仅在 `/secret-bind` 拿本 Run 允许的 materialized secrets + revision_bindings；持久化阶段全量 redact。

## 3. 后果（Consequences）

### 3.1 正面

- 草稿唯一真相源：`AgentMetadata.active_version_id` 单一指针；rollback 不复制；
- digest 与 rollback 不再冲突：不强制 UNIQUE，`Idempotency-Key` 保证幂等；
- lifecycle 字段受控可改：FROZEN → DEPRECATED 与"不可变快照"语义自洽；
- snapshot 内联非敏感执行配置：Run 可复现承诺不再依赖"被外部篡改的业务实体"；
- secret-bind 防越权：Runner 不上传 secret_refs，Control Plane 是唯一允许读取；
- revision 绑定：明确"Run 可复现"仅对非敏感配置；凭据使用授权 = `(assignment.status ∈ {ASSIGNED, ACKED} ∧ lease_expires_at > NOW() ∧ binding.status = ACTIVE ∧ secret.status = ACTIVE)`；Long Run 可超 24h；24h 仅是 revision 物理 DELETE retention 默认值，**不**作凭据硬期限；
- 加密硬化：AES-GCM/12-byte nonce/AAD = secret_id+type/key 缺失启动失败/versioned envelope；跨模块迁移杜绝遗留 ECB；
- 与 ADR-001 协同：第六端点 `/secret-bind` 作为 ADR-002 amend 引入 ADR-001 §D-1.2，Run→AgentVersion FK + revision_bindings + secret-bind 三件套一起走线；
- 一级产品面守住：仍只 Agent / AgentVersion / Runner / RunnerPool / Run / Endpoint 六个核心对象；SecretReference 是 AgentVersion 的引用形态，不是新一级对象。

### 3.2 负面与代价

- `agent_versions` 与 `agent_metadata` schema 改动：`agent_versions.is_active` 删除、`status` 两态；`agent_metadata.active_version_id` FK；新增桥接表 `agent_version_secret_refs` / `agent_version_tool_refs` / `agent_version_knowledge_refs`；新增 `lease_secret_binding`；迁移需在 R3 PR 给出 boot-back 计划；
- `secret-bind` 与 lease claim / renew / cancel 之间的时序图要在 R3 PR 中表达清楚；
- Knowledge MVP **必拒冻结**：含 Knowledge 的草稿在 R3 必拒，**不**整段内联知识文档 / chunk / 索引；R4 引入 `knowledge_snapshot` 后再开放知识引用；
- EncryptionUtil 跨模块迁移——R3 PR 必须列出全部受影响文件，不允许局部升级；
- `idemp:{agent_id}:{key_hash}` Redis 键需要双层 TTL（24h retention + 主动清理）；
- 旧 `AgentAccessProfile` 表写路径拒绝、读路径返回兼容——R3 PR 提供完整迁移计划；
- `SecretReference.secretId` / `localKey` 在 v2 文本以 `secretId` 占位统一，DTO 字段拆分在 R3 PR 同步——可能造成 v2 文档与 PR 描述轻微不一致，PR description 必须显式声明。

## 4. 备选方案（Alternatives considered）

| 方案 | 主要拒绝理由 |
| --- | --- |
| AgentVersion 上保留 DRAFT 状态 | 双草稿真相源；rollback 须复制同内容创建新版本，与本 ADR §D-2.1 单草稿原则冲突 |
| digest 强制 `UNIQUE(agent_id, content_digest)` | 阻止 rollback 复用同内容；与 §D-2.1 单草稿 rollback 路径冲突 |
| FROZEN 行任何字段都不许改 | lifecycle 迁移需要受控"少数字段可改"，与 FROZEN → DEPRECATED 冲突 |
| 全部引用 model/tool/knowledge snapshot id | 当前 R3 之前无可用不可变 snapshot 实现，影响 Run 可复现承诺 |
| 全部内联到 config_snapshot | 牺牲可读性 / 复用性；MVP 暂时混用（引用优先、内联兜底） |
| 让 Runner 上传 secret_refs | 越权读取风险 |
| `/secret-inject` + `/secret-bind` 双端点 | 增加攻击面与代码路径；统一在 `/secret-bind` 返回 |
| 不引入 AES-GCM / 不修 ECB | 与现有安全护栏冲突 |
| 用 alias 作为 AAD | alias 可变；违反 AEAD 基本要求 |
| Key 缺失回退明文 | 启动应失败而不是悄悄回退 |
| 用"保留前 4 后 4 字符"做脱敏兜底 | 与"全量 REDACTED"语义冲突；前缀末位信息应来自 `last4` / `key_prefix` 摘要列 |
| 关键字黑名单脱敏 | 拼写变体易漏；改为结构化 tag + 精确值替换 |

## 5. 待办与开放问题（Open questions）

> 每条都需在 TODO 与 R3 PR 中独立落地。任一未跟踪前不切 `Accepted`。

1. R3 PR 创建迁移前 `ls V*.sql | sort -V | tail -1` 复核最新版本号，**不**提前锁 V94；同时增 `idx_agent_version_status`、`idx_agent_version_content_digest`、`idx_lease_secret_binding_assignment_status`、`idx_revision_retired`、`idx_idemp_expires`。
2. `AgentMetadata.active_version_id` FK → `agent_versions.id`；首次冻结前为空值的兼容性由 R3 PR 决定。
3. `agent_version_secret_refs`、`agent_version_tool_refs`、`agent_version_knowledge_refs` 列结构 + `UNIQUE(agent_version_id, alias)` 草案（具体列名与索引 R3 PR 给出）。
4. **Knowledge 必拒冻结**（R3 不实现 `KnowledgeSnapshot`，含 Knowledge 草稿 `SNAPSHOT_SCHEMA_INCOMPATIBLE`）；R4 引入 `knowledge_snapshot` 后再开放；`knowledge_snapshot_id` 与 `knowledge_snapshot_digest` 由 R4 阶段 ADR 给出。
5. `gateway_secret_revisions.retention_period` 默认值（24h）由 R3 PR 配置项化；与"凭据硬期限"无关，仅控制物理 DELETE 时机。
6. `AgentAccessProfile` 弃用表迁移、影子表、保留期。
7. 前端 SecretReference 编辑体验（仅在 draft 路径下生效）：wireframe by R3 PR 同步。

## 6. 与其他 ADR / TODO / 文档的关系

- **ADR-001 (Accepted)**：
  - 本 ADR §D-2.7 引入 `/secret-bind` 第六端点 = **amend** ADR-001 §D-1.2；
  - 其他 lease / 状态机 / 幂等语义不变；
  - `lease_secret_binding` 与 ADR-001 §D-1.4.2 互不冲突。
- **ADR-003 (Pending)**：Endpoint 同步 / 流式 / 异步响应
  - 复用 §D-2.8 的 revision_bindings 在 Endpoint 三态响应下的重用；
  - async 路径下 secret-bind 与"先绑再异步触发"联动；
  - Endpoint 弃用某 AgentVersion 时的灰度替换与 ADR-002 §D-2.2 DEPRECATED 状态正交。
- **TODO P0**：本 ADR §5 七条子项已拆分；与 ADR-001 §5 开放问题不重复。

## 7. 验收条件（Acceptance criteria）

状态约定：

- **评审期间**：frontmatter `status: Proposed`，ADR 索引表 Proposed，TODO 中该项 `[ ]`；
- **评审通过**：同步把 ADR-002 frontmatter、ADR 索引表、TODO 切到 `Accepted` / `[x]`；并要求下列文档层验收全部勾选、**且 ADR-001 同步项（见下）全部勾选**才能切换。

文档层验收项（任一未勾前不切 `Accepted`）：

- [ ] [../架构设计.md §3](../架构设计.md) 增加 "Draft 仅在 AgentMetadata、AgentVersion lifecycle 仅 FROZEN/DEPRECATED + active_version_id 单一指针、DEPRECATED 不再被新 Run 引用" 段；
- [ ] [../架构设计.md §5 数据所有权](../架构设计.md) 补 "SecretReference 5 字段、scope 不在 AgentVersion 自声明、明文不下发 lease payload / 不进 snapshot、Knowledge/Workflow 强制不可变引用"；
- [ ] [../Runner架构设计.md §3.2](../Runner架构设计.md) 增加 "Runner 仅在 `/secret-bind` 协议中拿 materialized secrets + revision_bindings（**不允许上传 secret_refs**），进程内存持有、禁止持久化"；
- [ ] [../开发规范.md §1.4](../开发规范.md) 增加 "Run → AgentVersion 不可空 FK + digest + snapshot_schema_version 三件套校验、digest 不 UNIQUE、Idempotency-Key 24h、Run 仅引用 FROZEN"；§3 增加 "AES-GCM 替代 ECB、12-byte nonce、AAD = secret_id+type、key 缺失启动失败、versioned envelope、跨模块迁移范围"；**§1.4 移除 v1 残留的 `DRAFT` 字样**（与 v3 两态 lifecycle 冲突）；
- [ ] [../API文档.md §2.1](../API文档.md) 把机器通道端点同步为 **六端点**（包含 `secret-bind`），移除 `/secret-inject`；§3.1 `Agent 草稿 Secret Reference` 行（`/draft/secret-refs`） + `AgentVersion` 行扩 `PUT /active-version` / `POST /deprecate` / `GET /digest`；reveal 标注二次确认；
- [ ] [../部署指南.md](../部署指南.md) §6 升级 `ENCRYPTION_KEY` 为 "Base64 解码后恰好 32 字节、生成 `openssl rand -base64 32`、缺失即启动失败"；`ENCRYPTION_KEY_ID` 与 `ENCRYPTION_KEYS_ACTIVE` 列入生产必检；**标注行为为 R3 目标，禁止把"启动失败"冒充现状**（当前代码仍回退明文）；
- [ ] [../角色矩阵.md §3.2](../角色矩阵.md) 同步 Operator 不可见 Secret 明文 / Administrator reveal 二次确认 / Creator 仅在 draft 路径写 secret-refs / FROZEN 后 secret-refs 只 GET / RUNNER_LOCAL 三阶段（freeze/schedule/secret-bind）；
- [ ] 本 ADR §5 七条开放问题已拆分进 TODO 子条目并开始跟踪。

amend 同步项（**ADR-002 Accepted 切换前必勾**）：

- [ ] [001-runner-dispatch-and-lease.md](./001-runner-dispatch-and-lease.md) frontmatter 增 `amended-by: [ADR-002]`；
- [ ] [001-runner-dispatch-and-lease.md §D-1.2](./001-runner-dispatch-and-lease.md) 端点表同步为 **六端点**（新增 `secret-bind` 行；"禁止"段增加不允许 `/secret-inject` 的说明）；
- [ ] [../架构设计.md §3.2 Runner 拓扑段](../架构设计.md) 同步增 secret-bind 行；
- [ ] `docs/adr/README.md` 索引 ADR-001 行保留 `Accepted`，ADR-002 行切换 `Accepted`；
- [ ] TODO.md 中 ADR-002 行切换 `[x]`，ADR-001 行 §D-1.2 同步项留作新挂条目。

R3 实现层回归（**不**列为 `Accepted` 前置；R3 PR description 首行写 `Refs ADR-002`，由 PR review 核对）：

- 创建迁移前先 `ls V*.sql` 复核最新编号并写到 PR description；
- Flyway 迁移覆盖 §D-2.1 active_version_id / §D-2.2 不可变触发器 / §D-2.3 `agent_version_freeze_idempotency` 表 / §D-2.4 reference 数组排序与 RFC 8785 JCS / §D-2.6 桥接表 + DTO `secretId/localKey` 二选一 / §D-2.8 `gateway_secret_revisions` + `lease_secret_binding` 表 / §D-2.11 envelope 列结构 + 跨模块 ECB 替换（含 `grep -r "EncryptionUtil\b"` 输出）；
- `ErrorCode` 11 项枚举注册 + `GlobalExceptionHandler` 映射 + 单元测试（**R3 代码层验收**）；
- 对 `config_snapshot` / `lease payload` / Run `events` 与 `result` 增补脱敏与 digest / snapshot_schema_version 校验的自动化用例；
- 跨机器真实后端 E2E（SecretReference freeze → publish Endpoint → curl 成功 + digest / schema_version 比对 + secret rotation 触发 `SECRET_REVISION_BOUND` 审计 + Revocation 触发 binding 失效 + 24h retention cleanup 后旧 revision 物理删除）；
- 用例覆盖 "旧 `AgentAccessProfile` 写路径拒绝 / 读路径返回兼容" 行为；
- 用例覆盖 `Idempotency-Key` 命中与 `IDEMPOTENCY_KEY_CONFLICT`（同 key 不同 request_digest）；
- 用例覆盖 `RUNNER_LOCAL` 三阶段——freeze 通过但 Run 调度因 Runner 缺 capability 而拒绝 assignment；
- 用例覆盖 `gateway_secret_revisions` Rotation / New-lease / Revocation / Cleanup 四类操作。
