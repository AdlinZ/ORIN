# F05 · 发布 API 与 MCP

> 状态：E2E Working
> 用户角色：Creator / Operator / Endpoint Consumer
> 前置功能：F03；生产验收依赖 F04

## 1. 用户问题与结果

用户已经验证 AgentVersion，但仍缺少稳定的外部使用方式。完成后，用户可以从 Workspace 把固定 AgentVersion 发布为 Endpoint，通过 API Key 管理访问，并使用 curl 和至少一个 MCP 客户端真实调用；每次调用都会产生可观察 Run。

## 2. 范围

- Endpoint 创建、版本/RunnerPool 绑定、启停和健康状态；
- REST 执行与 MCP `tools/list`、`tools/call`；
- 适用时复用 OpenAI-compatible 入口；
- API Key、权限、限流、配额、调用审计；
- 同步、流式、异步响应契约，由 ADR-003 定稿；
- Agent Page 的最小发布体验；
- Runner 不可用时的稳定外部错误。

不包含 API 市场、计费平台、复杂流量治理或通用 API Gateway 产品面。

## 3. 完整用户旅程

1. 用户从已成功运行的 FROZEN AgentVersion 点击“发布”；
2. 选择 RunnerPool、协议、访问策略并创建 Endpoint；
3. 页面展示真实 URL、MCP 配置和一次性 API Key；
4. 用户复制 curl 调用成功，ORIN 创建 Run 并返回结果/流；
5. 用户把配置加入至少一个 MCP 客户端，完成 tools/list 与 tools/call；
6. Workspace 中可查看 Endpoint 健康、调用历史和关联 Runs；
7. Runner 不可用、Key 无效、限流和内部失败返回稳定且不泄漏实现细节的错误。

## 4. 验收

- [x] Workspace 可发布一个真实 AgentVersion，而不是生成占位配置；
- [x] curl 和至少一个 MCP 客户端调用成功；
- [x] 每次外部调用创建 Run、traceId 和审计；
- [x] API Key 明文只返回一次，禁用/轮换立即生效；
- [x] Runner 离线、限流、认证失败与内部错误契约稳定；
- [x] Endpoint 页面、Agent Page、真实 E2E 和外部 smoke 通过。

## 5. 不算完成

- 只有 Endpoint 表或发布按钮；
- 只返回静态示例或绕过统一 Run；
- 只验证 Swagger，没有 curl/MCP 客户端；
- 外部请求不产生 Run、traceId 或审计；
- API Key、Secret 或内部 Runner 信息被泄漏。

## 6. 关联文档

- [API 文档](../API文档.md)
- [MCP Client Setup](../mcp-client-setup.md)
- [Open Demo Checklist](../open-demo-checklist.md)
- [角色矩阵](../角色矩阵.md)

## 7. 实现状态（2026-07-27）

**当前状态：E2E Working**

- `agent_endpoints` 表（V98）+ Endpoint CRUD
- ADR-003（Endpoint 响应契约）定稿
- `EndpointExecutionService` — REST / MCP 共用执行逻辑
- `POST /v1/endpoints/{endpointId}/run` — 外部 REST 执行（API Key 鉴权）
- `GET /v1/endpoints/{endpointId}/runs/{runId}` — Run 状态查询（API Key 鉴权，endpointId 校验归属）
- `McpJsonRpcService` — MCP `tools/list` + `tools/call` 复用同一执行路径
- Endpoint ↔ API Key 访问控制（`assignApiKey` / `revokeApiKey`）
- Endpoint 资源级 ACL（按 createdBy 隔离 + admin/operator 豁免）
- publish 时自动创建 API Key 并返回明文
- Run 绑定 endpointId（V100 migration）
- `statusUrl` 指向 `/v1/endpoints/{epId}/runs/{runId}`（API Key 可访问）
- `EndpointExecutionE2ETest` — 10 个集成测试
- `McpStreamableHttpTest` — MCP endpoint 模型测试，包含未获授权 API Key 不得从 `tools/list` 发现 Endpoint 的访问边界
- Workspace 发布后一次性展示 API Key，并只展示本次选择协议对应的 REST curl 或 MCP 配置；关闭窗口即从页面状态清除明文
- 发布中心按“可调用 / 已下线”表达交付状态，历史服务可重新打开协议对应的调用说明；统一网关与密钥治理保留为高级下钻，不占用主菜单
- `scripts/f05-external-acceptance.sh` — Docker Runner 实际验收：临时 Agent/Endpoint/Key/Runner → REST 完成 Run 与状态查询 → 无效 Key 401 → MCP `initialize` / `tools/list` / `tools/call` → 下线 503；临时 Runner 凭据与 API Key 在退出时清理
- 设置 `ORIN_F05_REQUIRE_STDIO_BRIDGE=1` 与 `ORIN_MCP_BRIDGE_PYTHON` 后，
  同一脚本还会经随仓库发布的 stdio MCP Bridge 完成 `initialize`、`tools/list`
  和 `tools/call`，用于候选版外部客户端回归。
- `tests/e2e/real-backend/endpoint-f05-real.spec.js` 于 2026-07-27 通过：真实 Workspace 从冻结 AgentVersion 选择版本、发布 Endpoint、展示一次性 API Key 与所选 REST 协议的调用方式，并在关闭交接窗口后确认端点列表更新。
- `scripts/f05-external-acceptance.sh` 于 2026-07-27 在真实 Control Plane + Docker Runner 下通过：REST Run 完成与状态查询、无效 Key `401`、MCP `initialize / tools/list / tools/call`、stdio Bridge 客户端以及下线 Endpoint `503`。
- 正式 Release 仍需一个 MCP 桌面客户端（Codex、Claude Desktop、Cursor 或 Windsurf）的人工展示证据；流式 SSE、RunnerPool 选择不在当前 MVP 范围。
