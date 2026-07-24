# F05 · 发布 API 与 MCP

> 状态：Not Started
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

- [ ] Workspace 可发布一个真实 AgentVersion，而不是生成占位配置；
- [ ] curl 和至少一个 MCP 客户端调用成功；
- [ ] 每次外部调用创建 Run、traceId 和审计；
- [ ] API Key 明文只返回一次，禁用/轮换立即生效；
- [ ] Runner 离线、限流、认证失败与内部错误契约稳定；
- [ ] Endpoint 页面、Agent Page、真实 E2E 和外部 smoke 通过。

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

## 7. 实现状态（2026-07-24）

**当前状态：Backend Only**

- 已有：`agent_endpoints` 表（V98）、Endpoint CRUD
- 已有：`EndpointListPage.vue`（前端占位页）
- 缺失：REST 执行 / MCP `tools/list` `tools/call`、API Key 权限、速率限制、真实外部调用
- 升级到 E2E Working 需要：F03 Runner 闭环 → 端点绑定 AgentVersion + RunnerPool → 外部 HTTP/MCP 调用产生 Run
