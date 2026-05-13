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

API Key 创建：管理台 → 系统管理 → API Key 管理。

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
| Trace 与监控 | `/api/traces/*` · `/api/v1/monitor/*` · `/api/v1/observability/*` · `/api/v1/alerts/*` |
| 用户权限 | `/api/v1/users/*` · `/api/v1/roles/*` · `/api/v1/departments/*` |
| 系统配置 | `/api/v1/system/*` · `/api/v1/settings/*` · `/api/v1/api-keys/*` · `/api/system/integrations/*` · `/api/system/mcp/*` · `/api/v1/notifications/*` · `/api/v1/statistics/*` · `/api/v1/help/*` |

## 4. 核心接口示例

### 4.1 登录

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# → { "token": "...", "refreshToken": "..." }
```

后续请求附加：`Authorization: Bearer <token>`

### 4.2 统一网关：聊天补全（OpenAI 兼容）

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-orin-xxx" \
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
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Authorization: Bearer sk-orin-xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "ORIN 智能体平台示例"
  }'
```

### 4.4 统一网关：模型列表

```bash
curl http://localhost:8080/v1/models \
  -H "Authorization: Bearer sk-orin-xxx"
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
```

### 4.9 Trace 与监控

```bash
curl http://localhost:8080/api/traces/{traceId} -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/api/traces/search?traceId=abc" -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/monitor/dashboard/summary -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/observability/langfuse/status -H "Authorization: Bearer $TOKEN"
```

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
