# ORIN API 文档

这份文档不再尝试手写“完整接口清单”，而是提供当前仓库中最稳定的接口分组和查阅方式。它不是“功能已全部对接”的承诺，只说明当前代码中能看到哪些控制器入口。具体参数、请求体和响应结构，请以 OpenAPI 和控制器代码为准。

## 先看哪里

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 后端控制器根目录：`orin-backend/src/main/java/com/adlin/orin/modules`

## 基础约定

- Base URL：`http://localhost:8080`
- 认证方式：JWT Bearer Token
- 内容类型：大多数接口使用 `application/json`
- 文件上传：通常使用 `multipart/form-data`

## 当前主要接口分组

下面按控制器实际前缀做保守归类：

| 模块 | 主要路径前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/v1/auth/*` | 登录、刷新、校验 |
| 智能体 | `/api/v1/agents/*`, `/api/v1/agents/chat/*`, `/api/v1/admin/agents/*` | 智能体接入、管理、会话与少量管理端接口 |
| API 网关 | `/v1/*` | OpenAI 兼容网关 |
| 知识库 | `/api/v1/knowledge/*`, `/api/v1/knowledge/sync/*` | 知识库、文档、检索、同步 |
| 工作流 | `/api/workflows/*`, `/api/v1/workflow/*` | 工作流管理与代理执行 |
| 协作 | `/api/v1/collaboration/*` | 协作任务、包、事件、检查点、回退 |
| Trace 与观测 | `/api/traces/*`, `/api/v1/monitor/*`, `/api/v1/monitor/dataflow/*`, `/api/v1/observability/*`, `/api/v1/alerts/*` | Trace、监控、观测、告警 |
| 用户权限 | `/api/v1/users/*`, `/api/v1/roles/*`, `/api/v1/departments/*` | 用户、角色、部门 |
| 系统与配置 | `/api/v1/system/*`, `/api/v1/settings/*`, `/api/v1/api-keys/*`, `/api/system/integrations/*`, `/api/system/mcp/*`, `/api/v1/notifications/*`, `/api/v1/statistics/*`, `/api/v1/help/*` | 系统配置、集成、通知、帮助、统计 |

## 核心接口导航

### 1. 认证

常见入口：

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/validate`

### 2. 智能体

常见入口：

- `GET /api/v1/agents`
- `POST /api/v1/agents/onboard`
- `POST /api/v1/agents/{agentId}/chat`

说明：

- 部分历史文档里提到的某些批量能力、版本能力、导入导出能力，需要先确认当前控制器和服务是否已闭环，不要只依据旧文档判断。
- 当前同一个“智能体能力”可能分散在管理接口、聊天接口和 OpenAI 兼容网关三处，不要默认只有一条入口。

### 3. OpenAI 兼容网关

常见入口：

- `POST /v1/chat/completions`
- `POST /v1/embeddings`

说明：

- 这部分由网关控制器统一适配不同 provider。
- 第三方接入优先看这里，而不是直接调用内部业务接口。

### 4. 知识库

常见入口：

- `GET /api/v1/knowledge/list`
- `POST /api/v1/knowledge`
- `POST /api/v1/knowledge/{kbId}/documents/upload`
- `POST /api/v1/knowledge/retrieve`
- `POST /api/v1/knowledge/sync/dify/{agentId}`
- `GET /api/v1/knowledge/sync/client/{agentId}/changes`

说明：

- 知识库模块接口较多，包含 Notion、Web、RAGFlow、数据库同步等扩展入口。
- 如果要确认某类同步能力是否真可用，建议同时检查对应 service/adapter。

### 5. 工作流

常见入口：

- `GET /api/workflows`
- `POST /api/workflows`
- `POST /api/workflows/{id}/execute`
- `POST /api/v1/workflow/run`

说明：

- `/api/workflows/*` 偏管理和实例查询
- `/api/v1/workflow/run` 偏代理执行入口

### 6. 协作

常见入口：

- `POST /api/v1/collaboration/packages`
- `GET /api/v1/collaboration/packages`
- `POST /api/v1/collaboration/tasks`
- `GET /api/v1/collaboration/events/{packageId}`

说明：

- 该模块有控制器和数据结构，但具体执行成熟度需要结合 Python AI 引擎一起判断。

### 7. Trace、监控与观测

常见入口：

- `GET /api/traces/{traceId}`
- `GET /api/traces/search`
- `GET /api/traces/{traceId}/link`
- `GET /api/v1/monitor/dashboard/summary`
- `GET /api/v1/monitor/tokens/stats`
- `GET /api/v1/observability/langfuse/status`
- `GET /api/v1/alerts/history`

### 8. 系统与配置

常见入口：

- `GET /api/v1/system/log-config`
- `GET /api/v1/system/log-config/loggers`
- `PUT /api/v1/system/log-config/loggers/{loggerName}`
- `GET /api/v1/system/config`
- `PUT /api/v1/system/config`
- `GET /api/v1/settings/defaults`
- `GET /api/v1/api-keys`
- `GET /api/system/integrations/status`
- `GET /api/system/mcp/services`

## 联调建议

### 查接口是否真的存在

```bash
rg -n "@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping" \
  orin-backend/src/main/java/com/adlin/orin
```

### 查某个接口是不是旧文档遗留或文档写偏了

```bash
rg -n "/api/v1/workflow/run|/api/v1/agents|/api/v1/knowledge|/api/v1/settings|/api/system/integrations" \
  orin-backend/src/main/java/com/adlin/orin
```

### 查鉴权放行规则

```bash
rg -n "swagger-ui|v3/api-docs|requestMatchers" \
  orin-backend/src/main/java/com/adlin/orin/security
```

## 示例

```bash
TOKEN="your-jwt-token"

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

```bash
curl http://localhost:8080/api/traces/search?traceId=abc123 \
  -H "Authorization: Bearer $TOKEN"
```

## 维护说明

- 本文档只维护“入口级别”信息，避免再出现手写接口清单和真实控制器脱节的问题。
- 如果某个页面声称依赖某组接口，联调时请先确认控制器前缀、service 实现和前端调用是否真的对齐。
- 如果新增或删除模块级前缀，应同步更新这里和 [docs/README.md](/Users/adlin/Documents/Code/ORIN/docs/README.md)。

**更新日期**：2026-03-28
