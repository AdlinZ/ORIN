# ORIN 前端全量重构灰度 Runbook（Element Plus 方案）

文档入口：
- 文档索引：`./README.md`
- 发布入口：`./ORIN-Revamp-Release-Checkpoint.md`

## 1. 目标
- 在不改后端接口路径的前提下，按模块灰度启用 V2 页面。
- 支持随时按 flag 回退到旧页面。
- 提供统一的测试与验收步骤，减少“页面可见但链路未闭环”的误判。

## 2. 关键命令
- 运行重构测试集：`npm run test:revamp`
- 生产构建验证：`npm run build`
- 生成 flag 快照脚本（输出浏览器可执行片段）：`npm run flags:revamp:snapshot`
- 执行灰度静态探活（flag/路由/V2 映射）：`npm run smoke:revamp`
- 生成验收清单：`npm run acceptance:revamp -- --out ./artifacts/revamp-acceptance-checklist.md`
- 生成回退演练清单：`npm run rollback:revamp:drill -- --out ./artifacts/revamp-rollback-drill.md`

## 3. Feature Flag 清单
- `revampAgentsHub`
- `revampKnowledgeHub`
- `revampWorkflowHub`
- `revampCollaboration`
- `revampRuntimeOverview`
- `revampSystemGateway`
- `revampAuditCenter`
- `revampSystemConfigHub`
- `showMaturityBadge`

LocalStorage key 规则：`orin_ff_<flagName>`

示例：
- `orin_ff_revampAgentsHub=true`
- `orin_ff_revampAgentsHub=false`

## 4. 当前环境 flag 状态导出
1. 在项目根执行：`npm run flags:revamp:snapshot`
2. 复制输出的 JS 片段。
3. 在浏览器控制台执行该片段，得到当前开关状态。

### 4.1 可视化灰度控制台（推荐）
- 管理员入口：`/dashboard/control/revamp-rollout`
- 功能：查看全量模块成熟度、阶段、当前开关；支持“全部启用/全部关闭/重置默认”。
- 冒烟增强：支持“一键冒烟检查”、失败项一键跳转、失败原因分组统计、结果 JSON 导出、最近检查历史（本地）。
- 使用建议：每次操作后执行 `npm run smoke:revamp`，并做对应模块人工冒烟。

### 4.2 CI 产物
- Workflow：`.github/workflows/revamp-smoke.yml`
- 输出 artifact：
  - `revamp-smoke-report.json`
  - `revamp-smoke-summary.md`
  - `revamp-acceptance-checklist.md`
  - `revamp-rollback-drill.md`

## 5. 灰度启用顺序（建议）
1. `revampAgentsHub`
2. `revampKnowledgeHub` + `revampWorkflowHub`
3. `revampRuntimeOverview`
4. `revampCollaboration`
5. `revampSystemGateway` + `revampSystemConfigHub`
6. `revampAuditCenter`

每一步都执行一次：
- `npm run smoke:revamp`
- `npm run test:revamp`
- 目标路径人工冒烟（创建/查询/执行/追踪/回退）

## 6. 回退策略
- 回退单模块：将对应 `orin_ff_*` 设为 `false`，刷新页面。
- 紧急全量回退：将所有 `revamp*` 开关设为 `false`。
- UI 信息保留：`showMaturityBadge` 可独立控制，不影响业务流程。

## 7. 发布前 Checklist
- `npm run test:revamp` 全通过。
- `npm run smoke:revamp` 通过。
- `npm run build` 通过。
- 四大域核心路径均有至少 1 条人工验收记录：
  - 智能体中枢
  - 知识与工作流
  - 监控与运维
  - 系统与网关
- 回退演练完成（至少 1 次单模块回退、1 次全量回退）。
- 线上问题定位信息可用（审计、监控、协作状态可查）。
