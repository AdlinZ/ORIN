---
slug: 003-endpoint-response-contract
title: ADR-003 · Endpoint 同步 / 流式 / 异步响应契约
status: Accepted
date: 2026-07-25
deciders: Adlin
supersedes: []
related:
  - docs/features/F05-发布API与MCP.md
  - docs/adr/001-runner-dispatch-and-lease.md
  - docs/adr/002-agent-version-immutability-and-secret-reference.md
  - TODO.md (F05)
---

> 状态：Accepted — 定义 F05 发布 API 的外部调用契约；实现从第一刀 REST 同步闭环开始，流式与异步在后续迭代补齐。

## 1. 背景（Context）

F05 将已冻结的 `AgentVersion` 发布为外部可调用 Endpoint。外部调用者（REST client、MCP client）需要稳定的请求/响应契约，包括：

- **同步**：client 发请求，等 Runner 执行完，拿结果
- **异步**：client 发请求，立即返回 `statusUrl`，后续轮询
- **流式**：client 发请求，通过 SSE 逐步接收输出

当前 `McpJsonRpcService.tools/call` 走 `ExternalMcpAgentExecutionService → CollaborationExecutor`，**完全绕过 Run / Runner / traceId 体系**，不满足 F05 验收条件。

ADR-001 已在 Runner dispatch 层定稿了 lease / renew / result / events 的机器通道契约，但未定义外部 client 面对 Endpoint 时的 HTTP 契约。本 ADR 补齐这一层。

## 2. 决策（Decision）

### 2.1 REST Endpoint URL

```
POST /v1/endpoints/{endpointId}/run
```

- 路径注册在 `/v1/**` 下，由 `ApiKeyAuthInterceptor` 统一鉴权
- `{endpointId}` 是 `agent_endpoints.id`（如 `ep_a1b2c3d4e5f6`）

### 2.2 鉴权

外部 client 使用 API Key 鉴权，两种方式均可：

```
Authorization: Bearer sk-orin-xxxxxxxx
X-API-Key: sk-orin-xxxxxxxx
```

API Key 必须属于该 Endpoint 的 `config.allowedApiKeyIds` 列表；否则返回 `403 ENDPOINT_ACCESS_DENIED`。

### 2.3 请求体

```json
{
  "input": "string (required) — 用户输入 / prompt",
  "stream": "boolean (optional, default false) — 是否 SSE 流式返回",
  "timeoutMs": "number (optional, default 60000) — 同步等待超时"
}
```

### 2.4 同步响应（`stream: false`，默认）

**成功 (200)**：
```json
{
  "runId": "run_xxx",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "output": "Agent 执行结果文本",
  "events": [
    {"seq": 1, "level": "INFO", "message": "Agent started", "timestamp": 1720000000000}
  ]
}
```

**超时 (200, status != COMPLETED)**：
```json
{
  "runId": "run_xxx",
  "traceId": "...",
  "status": "RUNNING",
  "output": null,
  "statusUrl": "/api/v1/runs/run_xxx"
}
```

Client 应轮询 `GET /v1/endpoints/{endpointId}/runs/{runId}` 获取最终结果。

### 2.5 异步响应（`stream: false` + `timeoutMs: 0`）

**202 Accepted**：
```json
{
  "runId": "run_xxx",
  "traceId": "...",
  "status": "QUEUED",
  "statusUrl": "/api/v1/runs/run_xxx"
}
```

### 2.6 流式响应（`stream: true`）

`Content-Type: text/event-stream`，SSE 事件：

```
event: run_created
data: {"runId":"run_xxx","traceId":"..."}

event: log
data: {"seq":1,"level":"INFO","message":"Agent started","timestamp":1720000000000}

event: output
data: {"text":"Hello"}

event: done
data: {"status":"COMPLETED","runId":"run_xxx","traceId":"..."}
```

**流式实现不在第一刀范围内**，第一刀只实现同步 (200) 和异步 (202)。

### 2.7 查询 Run 状态

```
GET /v1/endpoints/{endpointId}/runs/{runId}
```

与 `GET /api/v1/runs/{runId}` 同结构，但走 API Key 鉴权（非 JWT），且仅允许查询该 endpoint 下的 run。

### 2.8 MCP 工具映射

每个 published Endpoint 自动成为一个 MCP tool：

- **工具名**：`endpoint.<endpointId>`
- **tools/list**：返回 user 的已发布 endpoints（仅限该 API Key 可访问的）
- **tools/call**：与 `POST /v1/endpoints/{endpointId}/run` 走**同一个执行路径**（`EndpointExecutionService.execute()`）

不在 MCP 层暴露 raw Agent——只有通过端点发布的 AgentVersion 才能被外部 MCP 客户端调用。

### 2.9 错误契约

| HTTP 状态码 | ErrorCode | 含义 |
|---|---|---|
| 401 | `AUTH_API_KEY_INVALID` | API Key 缺失或无效 |
| 403 | `ENDPOINT_ACCESS_DENIED` | API Key 无权访问此 Endpoint |
| 404 | `ENDPOINT_NOT_FOUND` | Endpoint 不存在或已下线 |
| 429 | `RATE_LIMIT_EXCEEDED` | 超出限流配额 |
| 503 | `RUNNER_UNAVAILABLE` | 无可用 Runner |
| 503 | `ENDPOINT_INACTIVE` | Endpoint 状态非 ACTIVE |
| 500 | `EXECUTION_FAILED` | Runner 执行失败 |

所有错误响应格式：
```json
{
  "code": "ERROR_CODE",
  "message": "人类可读描述",
  "traceId": "uuid"
}
```

**禁止泄漏**：`configSnapshot`、`encryptedSecret`、Runner 内部地址、`leaseToken`。

### 2.10 审计

每次 Endpoint 调用产生：
1. **Run 记录**（`runs` 表）— `created_by = "api-key:<keyId>"`, `trace_id`
2. **审计日志**（`audit_logs` 表）— 记录 `ENDPOINT_EXECUTE`，包含 `endpointId`、`apiKeyId`、`runId`、`traceId`

## 3. 后果（Consequences）

### 正面
- 外部 client 拿到稳定的 REST 契约，`runId` + `traceId` 可追踪
- MCP 和 REST 共享同一执行路径，不产生两套行为
- 错误契约统一，client 可预期处理

### 负面
- 同步等待需要 Control Plane 轮询 Run 状态直到 Runner 完成——如果 Runner 慢，HTTP 连接会长时间保持。MVP 用 `timeoutMs` 兜底。
- 流式 SSE 需要 Run events 实时推送，依赖 F04 事件体系成熟后才好做

### 与现有代码的关系
- `ExternalMcpAgentExecutionService` 不再被 MCP `tools/call` 使用；保留代码但不调用
- `McpJsonRpcService.tools/call` 改为调用 `EndpointExecutionService`（复用 REST 路径）
- `McpJsonRpcService.tools/list` 改为列出 user 的 published endpoints

## 4. 备选方案（Alternatives considered）

### A. MCP 直接走 CollaborationExecutor（当前实现）
- 优点：已有代码，不需要 Runner
- 缺点：不产生 Run、无 traceId、绕过 F03/F04 体系，验收不通过
- **否决**

### B. 每 endpoint 单独启动 MCP Server 进程
- 优点：完全隔离
- 缺点：运维复杂度爆炸，MVP 不需要
- **推迟到 R3**

### C. 用 OpenAI-compatible `/v1/chat/completions` 格式
- 优点：生态兼容
- 缺点：Agent 语义（Run、traceId、events）与 Chat Completions 模型不匹配
- **推迟**：可后续加一个 thin adapter 映射到同一执行路径

## 5. 待办与开放问题（Open questions）

- [ ] 流式 SSE 实现（依赖 F04 Run events 实时推送成熟）
- [ ] OpenAI-compatible adapter
- [ ] RunnerPool 选择策略（当前默认 auto-select first ONLINE runner）
- [ ] Endpoint 级别的 WebSocket 实时推送

## 6. 与其他 ADR / TODO 的关系

- ADR-001：本 ADR 的 REST 执行路径内部调用 `RunService.createRun()` → `leaseRun()`，完全复用 ADR-001 的 Runner dispatch 契约
- ADR-002：Endpoint 只能绑定 FROZEN AgentVersion，不可变
- TODO F05：本 ADR 是第一刀实现的基础

## 7. 验收条件（Acceptance criteria）

- [x] REST 同步端点 `POST /v1/endpoints/{epId}/run` 可被 curl + API Key 调用
- [x] 每次调用创建 Run，返回 `runId` + `traceId`
- [x] 审计日志记录 `ENDPOINT_EXECUTE`
- [x] API Key 无权访问时返回 403
- [x] Endpoint 下线后返回 503
- [x] Runner 不可用时返回 503 + `RUNNER_UNAVAILABLE`
- [x] E2E 测试覆盖完整闭环
- [ ] MCP `tools/list` + `tools/call` 走同一执行路径（第二刀，REST 闭环后）
- [ ] 流式 SSE（后续迭代）
