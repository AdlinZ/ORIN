---
name: Bug report
about: Report a reproducible problem in ORIN
title: "fix: "
labels: bug
assignees: ""
---

## 问题描述

请简要说明发生了什么，以及你期望发生什么。

## 影响范围

- [ ] backend
- [ ] frontend
- [ ] ai-engine
- [ ] docs / ci / scripts
- [ ] 不确定

## 复现步骤

1.
2.
3.

## 实际结果

请粘贴错误信息、接口响应或截图。不要粘贴 `.env`、API Key、密码、token 或其他凭据。

## 期望结果

说明正确行为。

## 环境信息

- OS:
- JDK:
- Node.js:
- Python:
- MySQL:
- Redis:
- 启动方式：本机进程 / Docker quickstart / 其他

## 已尝试的检查

- [ ] `bash scripts/check-schema-baseline.sh`
- [ ] `bash scripts/smoke-test.sh`
- [ ] 后端健康检查 `/v1/health`、`/api/v1/health`
- [ ] AI Engine 健康检查 `:8000/health`、`:8000/v1/health`
