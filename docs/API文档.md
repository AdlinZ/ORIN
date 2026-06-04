# ORIN API 文档

本文档提供接口分组导航与统一网关使用示例。详细字段以 Swagger 与控制器代码为准，避免文档与代码漂移。

## 1. 入口与文档资源

| 资源 | 地址 |
|------|------|
| 后端基址 | `http://localhost:8080` |
| 统一网关入口 | `GET /v1` |
| 网关文档导航 | `GET /v1/docs` |
| 网关能力清单 | `GET /v1/capabilities` |
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| 健康检查 | `/v1/health` · `/api/v1/health` |
| AI 引擎健康 | `:8000/health` · `:8000/v1/health` |

## 2. 鉴权方式

| 接口前缀 | 鉴权 | 说明 |
|----------|------|------|
| `/v1/*` | API Key | OpenAI 兼容网关，header `Authorization: Bearer sk-orin-xxx` 或 `X-API-Key: sk-orin-xxx` |
| `/api/v1/*` | JWT | 内部业务接口，需先调用 `/api/v1/auth/login` 获取 token |
| `/v1/health` · `/api/v1/health` | 无 | 健康检查公开 |

API Key 创建：管理员在管理台 `/dashboard/control/gateway` 的访问凭据区域创建；普通用户 / 运维在 `/portal/api-keys` 自助创建。平台访问密钥统一为 `CLIENT_ACCESS` 类型、`sk-orin-*` 前缀；`PROVIDER_CREDENTIAL` 与 `MCP_ENV` 仅用于上游凭据或 MCP env，不可作为 `/v1/*` 调用密钥。

API Key 生命周期接口：

- `GET /api/v1/api-keys`：查询平台访问密钥，响应只返回前缀、状态、配额、过期时间、最后使用时间等摘要。
- `POST /api/v1/api-keys`：创建平台访问密钥，明文 `secretKey` 只在创建响应中返回一次。
- `PATCH /api/v1/api-keys/{keyId}/disable` / `enable`：禁用或启用密钥，禁用后 `/v1/mcp` 与其他 `/v1/*` 入口必须返回 `401`。
- `POST /api/v1/api-keys/{keyId}/rotate`：轮换密钥，旧密钥立即失效，新明文只返回一次。
- `POST /api/v1/api-keys/{keyId}/secret`：管理员受控回显明文，必须提交当前密码和 `confirmReveal=REVEAL_API_KEY`；成功/失败均写脱敏审计。
- `GET /api/v1/api-keys/{keyId}/usage?limit=20`：返回该 key 的 30 天调用摘要与最近调用历史，只包含状态、计数、路径、traceId、耗时、错误摘要等脱敏字段。
- `DELETE /api/v1/api-keys/{keyId}`：删除密钥。

权限语义：

- `ROLE_ADMIN`、`ROLE_SUPER_ADMIN`、`ROLE_PLATFORM_ADMIN` 具备全局 API Key 治理能力，可管理全部 `CLIENT_ACCESS` Key，并可管理供应商凭据和 MCP env 密钥。
- `ROLE_OPERATOR`、`ROLE_USER` 只能管理自己拥有的 `CLIENT_ACCESS` Key。所有权来自 JWT 当前用户；`X-User-Id` 与请求体 `targetUserId` 不会覆盖自助用户归属。
- 自助用户访问非本人 Key 时统一返回 `404`；访问明文回显、配额重置、供应商凭据或 MCP env 密钥接口返回 `403`。
- 自助用户创建 / 轮换后只会获得一次明文 `secretKey`，不能再次 reveal 旧密钥。

创建、禁用、启用、轮换、删除、配额重置、明文回显、调用历史读取均写审计日志；审计详情只记录 `keyId / userId / action / success` 等摘要，不记录 API Key 原文、JWT、provider token、完整请求体或完整响应体。

错误码（统一网关）：

- `401` 缺少或无效 API Key（语义码 `AUTH_API_KEY_INVALID`）
- `429` 命中限流
- `503` 当前无可用 Provider
- `500` 网关内部错误

响应头 `X-Trace-Id` 用于排查，建议保留。

## 3. 接口分组

按控制器实际前缀保守归类：

| 模块 | 主要前缀 |
|------|----------|
| 认证 | `/api/v1/auth/*` |
| 智能体 | `/api/v1/agents/*` · `/api/v1/agents/chat/*` · `/api/v1/admin/agents/*` |
| 统一网关 | `/v1/*` |
| 知识库 | `/api/v1/knowledge/*` · `/api/v1/knowledge/sync/*` |
| 工作流 | `/api/workflows/*` · `/api/v1/workflow/*` |
| 协作 | `/api/v1/collaboration/*` |
| Trace 与监控 | `/api/traces/*` · `/api/v1/monitor/*` · `/api/v1/dashboard/*` · `/api/v1/observability/*` · `/api/v1/alerts/*` |
| 用户权限 | `/api/v1/users/*` · `/api/v1/roles/*` · `/api/v1/departments/*` |
| 系统配置 | `/api/v1/system/*` · `/api/v1/settings/*` · `/api/v1/api-keys/*` · `/api/system/integrations/*` · `/api/system/mcp/*` · `/api/v1/notifications/*` · `/api/v1/statistics/*` · `/api/v1/help/*` |

## 4. 核心接口示例

### 4.1 登录

```bash
ORIN_ADMIN_PASSWORD=<由本地配置或初始化向导创建的管理员密码>
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"${ORIN_ADMIN_PASSWORD}\"}"
# → { "token": "...", "refreshToken": "..." }
```

后续请求附加：`Authorization: Bearer <token>`

### 4.2 统一网关：聊天补全（OpenAI 兼容）

```bash
ORIN_API_KEY=<CLIENT_ACCESS_KEY>
curl -X POST http://localhost:8080/v1/chat/completions \
  --header "$(printf 'Authorization: Bearer %s' "$ORIN_API_KEY")" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Qwen/Qwen2.5-7B-Instruct",
    "messages": [
      {"role":"user","content":"你好，请简要介绍 ORIN。"}
    ],
    "temperature": 0.7,
    "max_tokens": 256
  }'
```

### 4.3 统一网关：文本向量

```bash
ORIN_API_KEY=<CLIENT_ACCESS_KEY>
curl -X POST http://localhost:8080/v1/embeddings \
  --header "$(printf 'Authorization: Bearer %s' "$ORIN_API_KEY")" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "ORIN 智能体平台示例"
  }'
```

### 4.4 统一网关：模型列表

```bash
ORIN_API_KEY=<CLIENT_ACCESS_KEY>
curl http://localhost:8080/v1/models \
  --header "$(printf 'Authorization: Bearer %s' "$ORIN_API_KEY")"
```

### 4.5 智能体管理

```bash
# 列表
curl http://localhost:8080/api/v1/agents -H "Authorization: Bearer $TOKEN"

# 接入
curl -X POST http://localhost:8080/api/v1/agents/onboard \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "name": "demo", "type": "OPENAI_COMPAT", "endpoint": "..." }'

# 内部对话（区别于网关）
curl -X POST http://localhost:8080/api/v1/agents/{agentId}/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "message": "..." }'
```

### 4.6 知识库

```bash
# 列表
curl http://localhost:8080/api/v1/knowledge/list -H "Authorization: Bearer $TOKEN"

# 上传文档
curl -X POST http://localhost:8080/api/v1/knowledge/{kbId}/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@doc.pdf"

# 检索
curl -X POST http://localhost:8080/api/v1/knowledge/retrieve \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "kbId": 1, "query": "...", "topK": 5 }'

# 图谱可视化数据（limit 默认 500，最大 2000）
curl "http://localhost:8080/api/v1/knowledge/graphs/{graphId}/visualization?limit=500" \
  -H "Authorization: Bearer $TOKEN"

# 端侧同步增量
curl http://localhost:8080/api/v1/knowledge/sync/client/{agentId}/changes \
  -H "Authorization: Bearer $TOKEN"
```

### 4.7 工作流

```bash
# 管理
curl http://localhost:8080/api/workflows -H "Authorization: Bearer $TOKEN"

# 触发执行
curl -X POST http://localhost:8080/api/v1/workflow/run \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "workflowId": 1,
    "inputs": { "query": "test", "iterations": 3 }
  }'
```

Workflow task 运行态接口保持在 `/api/v1/workflow-tasks/**`，兼容旧 `/v1/tasks/**` 对外查询入口。wire status 固定为：

`QUEUED / RUNNING / RETRYING / COMPLETED / FAILED / DEAD / CANCELLED`

语义约束：

- `CANCELLED` 是终态。
- 仅 `QUEUED` 可取消：`POST /api/v1/workflow-tasks/{taskId}/cancel`。
- 仅 `FAILED / DEAD` 可重放：`POST /api/v1/workflow-tasks/{taskId}/replay`，重放会创建新的 `QUEUED` 任务，原任务保持原终态。
- 任务详情：`GET /api/v1/workflow-tasks/{taskId}`。
- 工作流任务历史：`GET /api/v1/workflow-tasks/workflow/{workflowId}`。
- 任务中心与 Workflow 执行页会在 `FAILED / DEAD` 重放前展示失败原因、死信原因、重试次数和 traceId；重放成功后返回并展示 `originalTaskId / newTaskId`。
- Workflow 创建请求可在顶层 `retryPolicy.maxRetries` 覆盖该 Workflow task 最大重试次数；设为 `0` 表示失败后直接进入 `FAILED` 终态，不进入 `RETRYING / DEAD`。

### 4.8 协作

```bash
# 创建任务包
curl -X POST http://localhost:8080/api/v1/collaboration/packages \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "Demo",
    "collaborationMode": "SEQUENTIAL"
  }'

# 查询状态
curl http://localhost:8080/api/v1/collaboration/packages/{id}/status \
  -H "Authorization: Bearer $TOKEN"

# 事件流
curl http://localhost:8080/api/v1/collaboration/events/{packageId} \
  -H "Authorization: Bearer $TOKEN"

# 运行时 / 诊断 / 人工干预
curl http://localhost:8080/api/v1/collaboration/packages/{packageId}/runtime \
  -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/collaboration/packages/{packageId}/diagnostics \
  -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/collaboration/packages/{packageId}/pause \
  -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/collaboration/packages/{packageId}/resume \
  -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/collaboration/packages/{packageId}/cancel \
  -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/collaboration/packages/{packageId}/manual-complete \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"result":"manual result"}'
```

### 4.9 Trace 与监控

```bash
curl http://localhost:8080/api/traces/{traceId} -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/traces/{traceId}/summary -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/api/traces/search?traceId=abc" -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/monitor/dashboard/summary -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/dashboard/summary -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/observability/langfuse/status -H "Authorization: Bearer $TOKEN"
```

`GET /api/v1/traces/{traceId}/summary` 返回脱敏聚合摘要：workflow instance、workflow tasks、collaboration packages、audit logs、trace steps 和 Langfuse link 状态。响应只包含 ID、状态、时间、耗时、错误摘要、计数和跳转所需字段；不得返回 `inputData`、`outputData`、`requestParams`、`responseContent`、token、API Key 或 provider 凭据。

`GET /api/v1/dashboard/summary` 返回前端统一改造 1.0 的角色化首页聚合摘要：`roles / defaultHome / systemHealth / metrics / recentActivity / quickLinks`。该接口只读、JWT 鉴权，由 Java 后端聚合 AI Engine 健康状态，前端不得直连 AI Engine。`recentActivity` 只返回审计摘要字段，不返回完整请求体、响应体、token、API Key 或 provider 凭据。

## 5. 联调技巧

```bash
# 查接口是否真的存在
rg "@RequestMapping|@GetMapping|@PostMapping" \
   orin-backend/src/main/java/com/adlin/orin

# 查鉴权放行规则
rg "swagger-ui|v3/api-docs|requestMatchers" \
   orin-backend/src/main/java/com/adlin/orin/security
```

注意：

- 同一智能体能力可能分散在 **管理接口** + **聊天接口** + **OpenAI 网关** 三处入口，第三方接入优先用网关
- `/api/workflows/*` 偏管理与查询，`/api/v1/workflow/run` 偏代理执行
- 知识库同步类接口（Notion / Web / RAGFlow / Dify）成熟度不一致，使用前先确认对应 service / adapter 是否已实装

## 6. 维护原则

- 本文档只维护"入口级别"信息，避免再出现手写接口清单与控制器脱节
- 新增/删除模块前缀时，同步更新本文与 [架构设计.md](./架构设计.md)
- 字段细节查 Swagger，不再在此重复
