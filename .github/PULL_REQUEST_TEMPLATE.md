## 改动概述

说明这次 PR 为什么需要做，以及用户可见行为是否变化。

## 改动范围

- backend:
- frontend:
- ai-engine:
- docs / ci / scripts:

## 测试与校验

- [ ] 后端：`cd orin-backend && mvn test`
- [ ] 前端：`cd orin-frontend && npm run lint && npm run test && npm run build`
- [ ] AI Engine：`cd orin-ai-engine && python -m compileall app tests && venv/bin/pytest`
- [ ] Schema baseline：`bash scripts/check-schema-baseline.sh`
- [ ] 三端 smoke：`bash scripts/smoke-test.sh`
- [ ] 不适用，原因：

## 架构与安全检查

- [ ] 协作执行仍经 `TaskRuntime`，未引入并行执行内核
- [ ] 新增接口归入现有前缀，未自创新前缀
- [ ] 涉及前端入口时已更新 `routes.js` 与 `topMenuConfig.js`
- [ ] 涉及环境变量时已同步 `docs/部署指南.md`
- [ ] 不打印敏感字段，不提交 `.env`、密钥、密码或 token
- [ ] 涉及 schema 时只新增迁移文件，未改写已发布迁移

## 关联文档 / Issue

-
