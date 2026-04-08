# ORIN 前端重构发布 Checkpoint

本文件是“发布执行入口”；详细操作细节统一收敛在 Runbook：

- 主手册：[ORIN-Revamp-Gray-Runbook.md](./ORIN-Revamp-Gray-Runbook.md)
- 文档索引：[README.md](./README.md)

## 1. 发版前 5 步（必须按顺序）
1. `npm run smoke:revamp -- --json-out ./artifacts/revamp-smoke-report.json --summary-out ./artifacts/revamp-smoke-summary.md`
2. `npm run test:revamp`
3. `npm run build`
4. `npm run acceptance:revamp -- --out ./artifacts/revamp-acceptance-checklist.md`
5. `npm run rollback:revamp:drill -- --out ./artifacts/revamp-rollback-drill.md`

## 2. 发布通过判定
- `smoke` / `test:revamp` / `build` 全通过。
- 四大域核心路径人工验收记录已补齐。
- 单模块 + 全量回退演练记录已补齐。

## 3. CI 产物核对
工作流：`.github/workflows/revamp-smoke.yml`

必须存在以下 artifact：
- `revamp-smoke-report.json`
- `revamp-smoke-summary.md`
- `revamp-acceptance-checklist.md`
- `revamp-rollback-drill.md`
