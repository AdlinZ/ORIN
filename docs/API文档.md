# ORIN API 文档

本文档提供 ORIN 系统的完整 API 接口说明，基于当前真实实现。

## 基础信息

- **Base URL**: `http://localhost:8080`
- **认证方式**: JWT Bearer Token
- **Content-Type**: `application/json`

---

## 接口分组

| 模块 | 路径前缀 | 说明 |
|------|---------|------|
| 智能体 | `/api/v1/agents/*` | Agent 调用链入口 |
| 工作流 | `/api/workflows/*`, `/api/v1/workflow/*` | Workflow 执行链入口 |
| 协作 | `/api/v1/collaboration/*` | Collaboration 协作链入口 |
| 追踪 | `/api/traces/*` | 调用链路查询 |
| 可观测性 | `/api/v1/observability/*` | Langfuse 配置 |

---

## 1. Agent 调用链

入口: `/api/v1/agents/*`

### 1.1 与智能体对话

```http
POST /api/v1/agents/{agentId}/chat
Content-Type: application/json
```

**请求体**:
```json
{
  "message": "用户消息",
  "conversation_id": "可选会话ID",
  "enable_thinking": false,
  "thinking_budget": 1024
}
```

### 1.2 获取智能体列表

```http
GET /api/v1/agents
```

### 1.3 智能体版本管理

```http
GET /api/v1/agents/{agentId}/versions
POST /api/v1/agents/{agentId}/versions
POST /api/v1/agents/{agentId}/versions/{versionId}/rollback
```

---

## 2. Workflow 执行链

### 2.1 工作流管理

```http
POST /api/workflows  // 创建工作流
GET /api/workflows   // 获取工作流列表
GET /api/workflows/{id}  // 获取详情
```

### 2.2 工作流执行

```http
POST /api/v1/workflow/run
POST /api/workflows/{id}/execute
```

---

## 3. Collaboration 协作链

### 3.1 协作包管理

```http
POST /api/v1/collaboration/packages  // 创建任务包
GET /api/v1/collaboration/packages  // 获取列表
GET /api/v1/collaboration/packages/{packageId}  // 详情
```

### 3.2 协作编排

```http
POST /api/v1/collaboration/orchestrate  // 触发分解
POST /api/v1/collaboration/packages/{packageId}/start  // 启动执行
POST /api/v1/collaboration/packages/{packageId}/retry  // 重试
POST /api/v1/collaboration/packages/{packageId}/skip-subtask/{subtaskId}  // 跳过子任务
```

### 3.3 协作事件

```http
GET /api/v1/collaboration/events/{packageId}  // 获取事件时间线
```

---

## 4. 追踪链路

### 4.1 按 traceId 查询

```http
GET /api/traces/{traceId}
GET /api/traces/search?traceId=xxx
```

### 4.2 Langfuse 深链

```http
GET /api/traces/{traceId}/link
```

---

## 5. 可观测性配置

### 5.1 Langfuse 状态

```http
GET /api/v1/observability/langfuse/status
GET /api/v1/observability/config
```

---

## 6. 任务队列

### 6.1 任务管理

```http
GET /api/v1/tasks  // 任务列表
GET /api/v1/tasks/{taskId}  // 任务详情
POST /api/v1/tasks/{taskId}/retry  // 重试
POST /api/v1/tasks/{taskId}/cancel  // 取消
```

### 6.2 死信队列

```http
GET /api/v1/tasks/dead  // 死信任务
POST /api/v1/tasks/{taskId}/recover  // 恢复
```

---

## 7. 知识库同步

### 7.1 端侧同步

```http
GET /api/v1/knowledge/sync/changes?agentId=xxx&page=0&size=10
POST /api/v1/knowledge/sync/confirm  // 确认同步
GET /api/v1/knowledge/sync/export?agentId=xxx  // 导出
```

---

## 错误码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

---

## 认证示例

```bash
TOKEN="your-jwt-token"

# 智能体对话
curl -X POST http://localhost:8080/api/v1/agents/agent-001/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'

# 查询追踪
curl http://localhost:8080/api/traces/search?traceId=abc123 \
  -H "Authorization: Bearer $TOKEN"
```

---

**文档版本**: v1.1
**更新日期**: 2026-03-26
