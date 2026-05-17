# Support

ORIN is a personal open source project in active engineering stabilization. Support is best-effort and should stay focused on reproducible issues, project setup, and contribution workflow.

## Before Asking

Please check:

- [README.md](./README.md) for project overview and quickstart
- [CONTRIBUTING.md](./CONTRIBUTING.md) for local setup, branch rules, and PR expectations
- [docs/部署指南.md](./docs/部署指南.md) for environment and deployment notes
- [docs/API文档.md](./docs/API文档.md) for API prefix and integration details
- [docs/功能完成度.md](./docs/功能完成度.md) for current module maturity
- [docs/路线图.md](./docs/路线图.md) for planned work

## Where to Ask

- Reproducible bug: open a GitHub issue using the Bug report template.
- Scoped enhancement: open a GitHub issue using the Feature request template.
- Implementation proposal: open a draft PR and describe the design trade-offs.
- Security-sensitive issue: do not open a public issue with exploit details, secrets, tokens, private logs, or user data.

## What to Include

For runtime problems, include:

- OS, JDK, Node.js, Python, MySQL, and Redis versions
- Startup mode: local processes, Docker quickstart, or another setup
- Commands you ran
- The failing health check or test command
- Redacted logs or responses

Useful checks:

```bash
bash scripts/check-schema-baseline.sh
bash scripts/smoke-test.sh
curl -fsS http://localhost:8080/v1/health
curl -fsS http://localhost:8080/api/v1/health
curl -fsS http://localhost:8000/health
curl -fsS http://localhost:8000/v1/health
```

## Support Boundaries

The project does not provide guaranteed SLA, production operations support, or private debugging of unredacted credentials. External model providers, Dify/RAGFlow, Milvus, RabbitMQ, Langfuse, Neo4j, MinIO, and other optional services may require their own support channels.
