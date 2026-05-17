# ORIN

[![CI](https://github.com/AdlinZ/ORIN/actions/workflows/ci.yml/badge.svg)](https://github.com/AdlinZ/ORIN/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-artifacts%20available-blue)](./docs/功能完成度.md#4-测试覆盖率基线)
[![Docker](https://img.shields.io/badge/docker-quickstart%20pending-yellow)](./docker-compose.yml)
[![License](https://img.shields.io/badge/license-MIT-green)](./README.md#license)

> 智能体管理平台 · 本科毕业设计项目
>
> 三层架构：`Spring Boot 后端 + Vue 3 前端 + Python AI Engine`

## 30 秒启动

```bash
git clone https://github.com/AdlinZ/ORIN.git
cd ORIN
cp .env.example .env
docker compose up
```

Docker quickstart 依赖 `docker/mysql/init/01-orin-schema.sql` 作为 schema snapshot baseline 初始化 MySQL，后端启动后再由 Flyway 补跑快照之后的迁移；当前不支持从空库纯重放 `V1..latest`。可用 `bash scripts/check-schema-baseline.sh` 检查快照覆盖版本与当前最高迁移；无 Docker runtime 时可先跑 `bash scripts/check-docker-quickstart.sh` 做静态预检，但它不等于真实容器 smoke。

启动后访问：

- 前端：<http://localhost:5173>
- 后端：<http://localhost:8080>（Swagger：`/swagger-ui/index.html`）
- AI 引擎：<http://localhost:8000>

## 项目简介

ORIN 是一个面向企业的智能体（Agent）一体化管理平台，提供智能体接入、知识库治理、工作流编排、多智能体协作、统一观测等能力。系统在 OpenAI 兼容网关之上，沉淀业务级管控能力（鉴权、审计、限流、配额），并通过独立的 Python 执行引擎承载工作流与协作的实际执行。

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Spring Boot 3.2 · MySQL 8 · Redis · MyBatis/JPA |
| 前端 | Vue 3 · Vite · Element Plus · Pinia |
| AI 引擎 | Python 3.11 · FastAPI · LangGraph |
| 中间件 | Milvus（向量库）· RabbitMQ（可选） |

## 目录结构

```
ORIN/
├── orin-backend/      Spring Boot 主后端，业务管控与统一网关
├── orin-frontend/     Vue 3 管理台
├── orin-ai-engine/    Python FastAPI 执行引擎
├── docker/            本地基础设施 compose
├── scripts/           运维与 smoke 脚本
├── docs/              项目文档
└── manage.sh          本地开发一键启停脚本
```

## 快速开始

```bash
# 前置：JDK 17+ · Node 18+ · Python 3.11+ · MySQL 8 · Redis
# 本机开发一键启动（macOS / 本地 MySQL 环境）
./manage.sh start

# 或分别启动，详见 docs/部署指南.md
```

## 文档

| 文档 | 用途 |
|------|------|
| [docs/架构设计.md](./docs/架构设计.md) | 系统架构、模块边界、协作执行约束 |
| [docs/部署指南.md](./docs/部署指南.md) | 环境变量、本地/生产部署、检查清单 |
| [docs/使用指南.md](./docs/使用指南.md) | 前端导航与功能入口 |
| [docs/API文档.md](./docs/API文档.md) | 接口分组、统一网关示例 |
| [docs/功能完成度.md](./docs/功能完成度.md) | 各模块成熟度与可验收能力 |
| [docs/开发规范.md](./docs/开发规范.md) | 协作约束、提交规范、联调要点 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 贡献流程、分支与 PR 规范 |
| [SUPPORT.md](./SUPPORT.md) | 获取帮助、提 issue 前的信息清单 |
| [SECURITY.md](./SECURITY.md) | 漏洞披露流程与依赖维护口径 |
| [TODO.md](./TODO.md) | 待办与进度 |

## 当前状态

骨架完整，主链路（智能体对话、知识检索、工作流编排）可演示；多智能体协作与多模态等高级能力仍在收敛。CI 已启用非 Docker 必过 checks，并上传三端 coverage artifacts / Step Summary；当前不设置覆盖率红线。本机进程模式优先通过 health checks 与 `bash scripts/smoke-test.sh` 验证；Docker quickstart 仍待可用 Docker runtime 下真实 smoke 验证。详见 [docs/功能完成度.md](./docs/功能完成度.md)。

## License

MIT
