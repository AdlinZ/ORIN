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

API Key 创建：管理员在管理台 `/dashboard/control/gateway` 的访问凭据区域创建；开发者 / API 调用方在 `/platform` 自助创建。平台访问密钥统一为 `CLIENT_ACCESS` 类型、`sk-orin-*` 前缀；`PROVIDER_CREDENTIAL` 与 `MCP_ENV` 仅用于上游凭据或 MCP env，不可作为 `/v1/*` 调用密钥。

对外产品化域名建议使用 `api.<your-domain>/chat/completions`，由网关 / Nginx 转发到后端实际协议入口 `/v1/chat/completions`。代码层继续保持 `/v1/*` 作为 API Key 对外协议前缀，避免新增并行接口前缀。

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
# → { "token": "...", "user": { "userId": 1, "username": "admin", ... }, "roles": ["ROLE_ADMIN"] }
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

### 4.4.1 网关端点 Public Demo 开放范围

| Endpoint | Public Demo | 说明 |
|----------|------------|------|
| `/v1/chat/completions` | ✅ 开放 | auth + rate limit + audit + quota |
| `/v1/models` | ✅ 开放 | API Key 鉴权后的可用模型列表 |
| `/v1/embeddings` | ❌ 默认关闭 | 需 `orin.gateway.endpoints.embeddings-enabled=true` |
| `/v1/health` | ✅ 公开 | 健康检查 |
| `/v1/capabilities` | ✅ 公开 | 能力清单 |

### 4.4.2 OpenAI SDK 集成

ORIN 统一网关兼容 OpenAI SDK，只需替换 `base_url` 和 `api_key`。

**Python:**
```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-orin-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    base_url="https://your-orin-instance/v1",
)

completion = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Hello, ORIN!"}],
)
print(completion.choices[0].message.content)
```

**Node.js:**
```javascript
import OpenAI from "openai";

const client = new OpenAI({
  apiKey: "sk-orin-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  baseURL: "https://your-orin-instance/v1",
});

const completion = await client.chat.completions.create({
  model: "gpt-4",
  messages: [{ role: "user", content: "Hello, ORIN!" }],
});
console.log(completion.choices[0].message.content);
```

> **注意:** `base_url` / `baseURL` 必须以 `/v1` 结尾，与 OpenAI 官方 API 路径约定一致。

### 4.4.3 Public Demo 错误语义

ORIN Gateway 统一错误响应格式：

```json
{
  "code": "AUTH_API_KEY_INVALID",
  "message": "Invalid API key",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| HTTP Status | 语义码 | 含义 | 处理建议 |
|-------------|--------|------|----------|
| `200` | — | 成功 | 正常消费响应 |
| `401` | `AUTH_API_KEY_INVALID` | 缺少或无效 API Key；或 Key 已被禁用 | 检查 `Authorization: Bearer sk-orin-xxx` 头是否正确；确认 Key 未被禁用或删除 |
| `413` | `payload_too_large` | 请求体超过 1 MiB 上限 | 减小请求体（`messages` 总长度） |
| `429` | —（HTTP 标准） | 命中限流（超出 `rateLimitPerMinute` 或 `rateLimitPerDay`）；或超出 `monthlyTokenQuota` | 降低请求频率；联系管理员提升配额 |
| `501` | `not_implemented` | `/v1/embeddings` 端点已关闭 | Embeddings 默认关闭，如需使用联系管理员开启 |
| `503` | `service_unavailable` | 当前无可用 provider（provider 凭据未配置或全部不可达） | 稍后重试；联系管理员确认 provider 配置 |
| `500` | `internal_error` | 网关内部错误（provider 异常、超时等），响应不包含 provider 原始错误 | 记录 `traceId`，联系管理员排查 |

### 4.4.4 如何理解 traceId

每次 `/v1/chat/completions` 和 `/v1/embeddings` 请求都会在响应头 `X-Trace-Id` 中返回一个唯一标识。

**traceId 的用途：**

- **排查错误**：遇到 `500` 或 `503` 时，记录 `X-Trace-Id` 响应头中的值，管理员可通过 `GET /api/v1/traces/{traceId}/summary`（JWT 鉴权）查看该次调用的完整链路
- **审计追溯**：`audit_logs` 表中每条记录都有 `traceId` 字段，可关联到具体请求
- **自定义 traceId**：客户端可在请求头 `X-Trace-Id` 中传入自己的 trace ID，Gateway 会透传并使用（便于对接客户端已有的追踪系统）。若不传，Gateway 自动生成 UUID

**OpenAI SDK 示例（获取 traceId）：**

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-orin-xxx",
    base_url="https://your-orin-instance/v1",
)

# OpenAI SDK 不直接暴露响应头。如需获取 X-Trace-Id，
# 可改用 httpx 或 requests 直接调用，或通过 SDK 的 response 对象获取
response = client.chat.completions.with_raw_response.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Hello"}],
)
trace_id = response.headers.get("X-Trace-Id")
print(f"Trace ID: {trace_id}")
completion = response.parse()
print(completion.choices[0].message.content)
```

### 4.4.5 curl 快速接入

**获取模型列表：**

```bash
ORIN_API_KEY="sk-orin-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
ORIN_BASE="https://your-orin-instance"

curl -sS "${ORIN_BASE}/v1/models" \
  -H "Authorization: Bearer ${ORIN_API_KEY}" | jq .
```

**发送聊天请求：**

```bash
curl -sS "${ORIN_BASE}/v1/chat/completions" \
  -H "Authorization: Bearer ${ORIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: my-trace-$(date +%s)" \
  -d '{
    "model": "deepseek-ai/DeepSeek-V3",
    "messages": [{"role": "user", "content": "Hello, ORIN!"}],
    "max_tokens": 256,
    "temperature": 0.7
  }' | jq .
```

**查看响应头获取 traceId：**

```bash
curl -i -sS "${ORIN_BASE}/v1/chat/completions" \
  -H "Authorization: Bearer ${ORIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-ai/DeepSeek-V3",
    "messages": [{"role": "user", "content": "Hi"}],
    "max_tokens": 16
  }' 2>&1 | grep -i 'x-trace-id'
```

**验证 embeddings 已关闭（Public Demo 预期 501）：**

```bash
curl -i -sS "${ORIN_BASE}/v1/embeddings" \
  -H "Authorization: Bearer ${ORIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"model": "text-embedding-3-small", "input": "test"}'
# 期望：HTTP/1.1 501 Not Implemented
# {"code": "not_implemented", "message": "Embeddings endpoint is disabled...", ...}
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

#### 4.5.1 智能体对话会话（ROLE_USER /chat 入口）

`/api/v1/agents/chat/sessions/**` 是 `/chat` 页面使用的会话 / 消息 / 知识库附加接口，前端走 `src/api/agent-chat.js`，全部归入既有 `/api/v1/agents/chat/` 前缀。

```bash
# 列出会话（可按 agentId 过滤）
curl 'http://localhost:8080/api/v1/agents/chat/sessions?agentId=<id>' \
  -H "Authorization: Bearer $TOKEN"

# 创建会话
curl -X POST http://localhost:8080/api/v1/agents/chat/sessions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"agentId":"<id>","title":"<可选标题>"}'

# 获取会话元信息（不包含 messages）
curl http://localhost:8080/api/v1/agents/chat/sessions/<sessionId> \
  -H "Authorization: Bearer $TOKEN"

# 获取会话消息（支持分页）
curl 'http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/messages?limit=50&before=<索引>' \
  -H "Authorization: Bearer $TOKEN"

# 发送消息（blocking）
curl -X POST http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/messages \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"<用户消息>","kbIds":["<可选>"]}'

# 发送消息（SSE 流式）
curl -N -X POST http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/messages/stream \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"<用户消息>","kbIds":["<可选>"]}'

# 知识库附加 / 解绑 / 列出
curl -X POST http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/attach-kb \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"kbId":"<id>"}'
curl -X POST http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/detach-kb \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"kbId":"<id>"}'
curl http://localhost:8080/api/v1/agents/chat/sessions/<sessionId>/kbs \
  -H "Authorization: Bearer $TOKEN"

# 删除会话
curl -X DELETE http://localhost:8080/api/v1/agents/chat/sessions/<sessionId> \
  -H "Authorization: Bearer $TOKEN"
```

错误响应统一带 `traceId`（与 `4.4.4` 同源），前端 `request.js → buildErrorMessage` 会把它附在 message 末尾，ROLE_USER 的 `/chat` 对话区会展示该 traceId 便于排错。

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
