# Contributing to ORIN

感谢你愿意参与 ORIN。这个项目当前仍在工程化收敛阶段，贡献时请优先保证架构边界、测试和文档同步。

## 开始前

请先阅读：

- [AGENTS.md](./AGENTS.md)：工程约束与协作规则
- [docs/架构设计.md](./docs/架构设计.md)：三端边界与接口前缀
- [docs/开发规范.md](./docs/开发规范.md)：编码规范与 PR 必检项
- [docs/路线图.md](./docs/路线图.md)：当前阶段和优先级
- [docs/功能完成度.md](./docs/功能完成度.md)：模块成熟度与已知缺口

## 本地开发

ORIN 由三端组成：

- `orin-backend/`：Spring Boot 3.2 + MySQL + Redis
- `orin-frontend/`：Vue 3 + Vite + Element Plus
- `orin-ai-engine/`：Python 3.11 + FastAPI + LangGraph

本机进程模式：

```bash
# 后端
cd orin-backend && mvn spring-boot:run

# AI Engine
cd orin-ai-engine && venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000

# 前端
cd orin-frontend && npm install && npm run dev
```

Docker quickstart 当前依赖 `docker/mysql/init/01-orin-schema.sql` 作为 schema snapshot baseline。该快照覆盖到 `V83`，后端启动后由 Flyway 补跑 `V84..V87`；后续新增 schema 迁移从 `V88` 开始。不要改写已发布历史迁移来修 quickstart。

## 分支与提交

- 不直接推 `main`，所有变更走 PR。
- 分支名建议使用 `feat/...`、`fix/...`、`docs/...` 或 `chore/...`。
- Commit message 使用：

```text
<type>(<scope>): <subject>
```

常用 type：`feat`、`fix`、`refactor`、`docs`、`test`、`chore`、`perf`。

## 架构边界

- 业务持久化只能在 Java 后端；AI Engine 不直连业务数据库。
- 前端 API 请求统一走 `src/api/*`，不要在组件里直调 `axios`。
- 新接口必须归入已有前缀：`/v1/*`、`/api/v1/*` 或 `/api/workflows/*`。
- 协作执行链保持为 `collaboration_langgraph` -> `mq_worker` -> `TaskRuntime`。
- 不提交 `.env`、密钥、密码、私钥或任何凭据。

## 提交 PR 前

按改动范围运行对应命令：

```bash
# 后端
cd orin-backend && mvn test

# 前端
cd orin-frontend && npm run lint && npm run test && npm run build

# AI Engine
cd orin-ai-engine && python -m compileall app tests && venv/bin/pytest

# Schema snapshot baseline
bash scripts/check-schema-baseline.sh

# 三端启动后的最小烟测
bash scripts/smoke-test.sh
```

如果测试失败，请先判断是环境缺失、测试过时还是代码 bug，并在 PR 中说明。不要为了通过测试而跳过测试、注释断言或绕开失败。

## PR 要求

PR 描述应包含：

- 改动概述：为什么改
- 改动范围：backend / frontend / ai-engine / docs / ci
- 测试与校验：实际运行过的命令和结果
- 文档同步：涉及环境变量、接口、架构或运行方式时必须更新 docs

涉及数据库 schema 时，只能新增下一版本迁移文件。当前下一次真实 schema 变更从 `V88` 起。
