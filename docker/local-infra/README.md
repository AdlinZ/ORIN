# 本地基础设施 Compose

这个目录用于本地服务器部署 ORIN 的外部依赖。

## 默认启动

默认不带 profile 启动时，会启动：

- `Milvus` 单机栈
- `Prometheus`
- `Grafana`
- `node-exporter`
- `cadvisor`

启动方式：

```bash
cd docker/local-infra
cp .env.example .env
docker compose up -d
```

## 按需启用的 profile

### 队列

```bash
docker compose --profile queue up -d
```

会额外启动：

- `RabbitMQ`

如果要验证协作模块的 MQ 灰度链路，可以在仓库根目录执行：

```bash
./scripts/run-collab-mq-smoke.sh
```

这个脚本会：

- 启动 `RabbitMQ`
- 编译并运行协作模块相关后端测试
- 在临时 Python 3.11 容器里执行 `orin-ai-engine/tests/test_mq_worker.py`

### Langfuse

```bash
docker compose --profile langfuse up -d
```

会额外启动：

- `langfuse-web`
- `langfuse-worker`
- `langfuse-postgres`
- `langfuse-clickhouse`
- `langfuse-redis`
- `langfuse-s3`

### 图数据库

```bash
docker compose --profile graph up -d
```

会额外启动：

- `Neo4j`

### NVIDIA GPU 监控

```bash
docker compose --profile nvidia up -d
```

会额外启动：

- `nvidia-node-exporter`

适用前提：

- 本地服务器已安装 NVIDIA 驱动
- Docker 已具备 NVIDIA Container Toolkit
- Docker 支持 `--gpus all`

### 独立对象存储

```bash
docker compose --profile objectstore up -d
```

会额外启动：

- `MinIO`

## 建议

- 部署前先修改 `.env` 里的所有默认密码
- 本地服务器上的 `Redis`、`RabbitMQ`、`Milvus`、`Neo4j`、`MinIO` 不要直接暴露公网
- 如果云服务器要访问本地服务器，优先通过 VPN、Tailscale、ZeroTier 或专线
- 如果启用 `nvidia` profile，先在本地服务器执行 `nvidia-smi` 和 `docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi` 验证 GPU 运行时
