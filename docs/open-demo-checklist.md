# ORIN Open Demo 验收清单

本文用于区分三类 MCP / Open Demo 验收口径：脚本 smoke、Codex 客户端验收、外部客户端展示。未按对应层级实际跑通时，不得在 README、路线图或发布说明中标记为已完成。

## 当前验收记录

- 2026-05-21：`bash scripts/open-demo-acceptance.sh` 通过，Workflow `tools/call` 返回 trace metadata；未设置 `ORIN_OPEN_DEMO_AGENT_ID`，Agent provider-backed 场景跳过。
- 2026-05-21：Codex CLI 子会话通过临时 `orin` MCP 配置调用 `workflow.42` 成功，返回 `taskId=task-083f5fa3-e7c7-4926-a5d3-10cd8a094cdc`、`workflowInstanceId=61`、`traceId=3f19a4d6-3ad7-4c67-9170-16fcc53b5381`、`statusUrl=/api/v1/workflow-tasks/task-083f5fa3-e7c7-4926-a5d3-10cd8a094cdc`、`status=QUEUED`；临时 API Key、Workflow 与 Codex MCP 配置已清理。
- 2026-05-21：Codex CLI 子会话通过临时 `orin-agent` Streamable HTTP MCP 配置调用真实 provider-backed Ollama Agent 成功；临时 Agent 使用本机 `llama3.1:8b`，`tools/list` 暴露 `agent.*`，Agent `tools/call` 返回 `ORIN_CODEX_AGENT_OK` 且包含 `Trace ID` 与 `Package ID`；临时 API Key、Agent 与 Codex MCP 配置已清理。
- 待补：Claude Desktop / Cursor / Windsurf 外部客户端展示截图或 GIF。

## 1. API Smoke

目标：验证 ORIN `/v1/mcp` 的协议入口、鉴权、`tools/list`、Workflow `tools/call` 与可选 Agent `tools/call` 可通过脚本稳定复验。

前置条件：

- 后端、数据库、Redis 已启动。
- 准备一个 `CLIENT_ACCESS / sk-orin-*` API Key。
- Key owner 拥有至少一个 `mcpExposed=true` 的 Workflow；如需 Agent 强验收，还需拥有可用 provider/model 的 `mcpExposed=true` Agent。
- 外部 provider-backed Agent 只在显式设置 Agent ID 时调用。

命令：

```bash
ORIN_API_KEY=<CLIENT_ACCESS_KEY> bash scripts/mcp-open-demo-smoke.sh
ORIN_MCP_CALL_TOOLS=1 ORIN_API_KEY=<CLIENT_ACCESS_KEY> bash scripts/mcp-open-demo-smoke.sh
ORIN_OPEN_DEMO_AGENT_ID=<agent-id> bash scripts/open-demo-acceptance.sh
```

期望结果：

- `initialize` 成功。
- `tools/list` 能看到当前 key owner 可访问的 `agent.*` 或 `workflow.*`。
- Workflow `tools/call` 返回 `taskId` / `workflowInstanceId` / `traceId` / `statusUrl`。
- 设置 `ORIN_OPEN_DEMO_AGENT_ID` 时，Agent `tools/call` 返回非空文本，并包含 `Trace ID` 与 `Package ID`。
- 脚本输出不打印 API Key、provider key 或完整敏感上下文。

完成口径：

- 只有脚本退出码为 0，才可标记该层已通过。
- 未配置真实 provider/model 时，不得把 Agent provider-backed 场景标记为完成。
- Agent tool 未出现在 `tools/list` 时，应先确认 Agent 已 `mcpExposed=true`，且 owner 与当前 `CLIENT_ACCESS` key 一致。

## 2. Codex Client Acceptance

目标：以 Codex 作为开发者真实 MCP 客户端，验证 ORIN MCP 不只通过脚本，也能在真实工具客户端中完成发现与调用。

前置条件：

- 已按 [docs/mcp-client-setup.md](./mcp-client-setup.md) 配置 Codex MCP 客户端。
- 推荐使用本地 stdio bridge；若 Codex 当前版本支持 Streamable HTTP MCP，也可直接配置 `http://localhost:8080/v1/mcp`。
- `ORIN_API_KEY` 必须是 `CLIENT_ACCESS / sk-orin-*`，且 key owner 拥有目标 Agent / Workflow。
- Agent provider-backed 验收需要本地已配置可用 provider/model/key。

验收步骤：

1. 在 Codex 中加载 ORIN MCP 配置。
2. 确认 tools 列表出现 `agent.*` 或 `workflow.*`。
3. 调用一个 Workflow tool，确认返回 `taskId`、`workflowInstanceId`、`traceId`、`statusUrl`。
4. 调用一个真实 provider-backed Agent tool，确认响应文本非空，并能看到 `Trace ID` 与 `Package ID`。
5. 回到 ORIN 后端或前端 trace / collaboration 页面，按返回 metadata 查到对应记录。

完成口径：

- 记录验收日期、Codex 版本或构建信息、调用的 Agent / Workflow ID、是否使用 stdio bridge 或 Streamable HTTP。
- 验收记录中不得写入 API Key、JWT、provider key 或 secret 明文。
- 只配置成功但未执行 `tools/list` 和 `tools/call`，不得标记为完成。

## 3. External Client Showcase

目标：为开源展示验证 Claude Desktop、Cursor、Windsurf 等外部客户端兼容性。该层是展示资产，不阻塞 Phase 1 工程闭环。

客户端范围：

- Claude Desktop：优先使用 `orin-mcp-bridge` stdio bridge。
- Cursor：使用其 MCP 配置入口接入同一 bridge 或兼容 HTTP endpoint。
- Windsurf：使用其 MCP 配置入口接入同一 bridge 或兼容 HTTP endpoint。

验收内容：

- 客户端能列出 ORIN 暴露的 `agent.*` / `workflow.*`。
- 至少完成一次 Workflow `tools/call`。
- 至少完成一次真实 provider-backed Agent `tools/call`。
- 展示截图或 GIF 中不得出现 API Key、provider key、JWT、用户隐私数据。

完成口径：

- 未在真实客户端里跑通时，只能标记为“待展示”。
- Claude Desktop / Cursor / Windsurf 任一客户端失败，不影响 Codex client acceptance 的完成状态，但应在展示清单中记录失败环境与错误信息。
- 对外 README、文章或视频只引用真实跑通过的客户端，不用脚本 smoke 代替人工客户端展示。
