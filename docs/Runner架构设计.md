# ORIN Runner 架构设计

> 状态：vNext 目标架构。F01 的 Runner 注册、Credential、远程心跳、资源监控和 Drain/Revoke 已部分实现；F03 的远程 Run 分发尚未实现。
> 当前 `orin-ai-engine` 仍同时承载固定部署执行服务，并新增轻量 Runner entry point；本文件定义后续向完整 Runner Runtime 演进的边界。

## 1. 目标

Runner 是安装在用户服务器上的轻量执行进程。它负责接收 ORIN 分配的 Agent Run、调用现有执行内核、回传状态与日志，并上报与 Agent 运行相关的服务器资源。

Runner 不持久化 ORIN 业务数据，不承担服务器创建和通用运维。

## 2. 目标拓扑

```text
MCP / API / Agent Page
          │
          ▼
ORIN Backend / Control Plane
  ├─ Agent & AgentVersion
  ├─ Runner & RunnerPool
  ├─ Run lease / state / audit
  └─ Endpoint / auth / routing
          │ HTTPS（Runner 主动出站）
          ▼
ORIN Runner
  ├─ enrollment & credential
  ├─ heartbeat & capacity
  ├─ run lease client（lease claim / renew）
  ├─ events / result 客户端（独立幂等 POST）
  ├─ secret-bind 客户端（第六端点，请求体仅 `{assignment_id}`）
  └─ TaskRuntime（唯一执行内核）
```

> 详情见 [ADR-001 §D-1.2](./adr/001-runner-dispatch-and-lease.md) 与 [ADR-002 §D-2.7](./adr/002-agent-version-immutability-and-secret-reference.md)。Runner **不消费** RabbitMQ，也不向 RabbitMQ publish Run 级消息。`mq_worker` 仍然是协作子任务（包内）的分发通道，与 Runner 互不重叠。

可选的 RabbitMQ、Redis、Prometheus、Grafana 是基础设施或增强组件，不是用户理解 Runner 的前置概念。

## 3. 组件职责

### 3.1 Control Plane

- 创建一次性 Enrollment Token；
- 接收 Runner 注册并签发 Runner Credential；
- 保存 Runner 身份、标签、能力、容量和最新心跳；
- 创建 Run，选择或确认 Runner Pool；
- 通过 lease 保证同一 Run 不被重复执行；
- 持久化 Run 状态、日志索引、Trace、Endpoint 和审计；
- 在 Runner 离线或 lease 过期时停止分配新任务。

业务数据仍只能由 Java 后端持久化。

### 3.2 Runner

- 使用一次性 Enrollment Token 完成首次注册；
- 使用 Runner Credential 发起后续机器请求；
- 周期上报心跳和资源快照；
- 主动获取被分配的 Run；
- 校验 AgentVersion、运行能力和本地 SecretReference；
- 将任务交给现有 `TaskRuntime`；
- 流式回传状态、日志、Trace 与结果；
- 响应取消信号并在退出前释放或结束 lease；
- 仅在 Control Plane 的 `secret-bind` 协议中按 lease 接收 `materialized_secrets`；该对象只允许保存在**进程内存**的 `runtime.secret_resolver` 中，禁止序列化进 lease payload 之外的请求、禁止写磁盘、禁止跨 lease 复用；详情见 [ADR-002 §D-2.7](./adr/002-agent-version-immutability-and-secret-reference.md)。

### 3.3 TaskRuntime

`app.engine.task_runtime.TaskRuntime` 继续作为唯一任务计算内核。Runner 只增加控制循环、机器认证、租约和遥测，不复制 Agent、Workflow 或 Collaboration 的执行语义。

现有协作链继续遵守：

```text
collaboration_langgraph
→ mq_worker
→ TaskRuntime
```

Runner 落地前必须通过 ADR 明确队列任务与 HTTPS lease 的迁移方式；同一种任务不得长期保留两套并行分发语义。

## 4. Runner 生命周期

```text
NEW
→ ENROLLING
→ ONLINE
→ DEGRADED
→ DRAINING
→ OFFLINE
→ REVOKED
```

| 状态 | 含义 | 是否接收新 Run |
|------|------|----------------|
| NEW | 控制面已创建接入指令，Runner 尚未注册 | 否 |
| ENROLLING | 正在交换身份与能力 | 否 |
| ONLINE | 心跳正常且容量可用 | 是 |
| DEGRADED | 心跳存在，但资源、版本或依赖异常 | 默认否 |
| DRAINING | 维护模式，只等待当前 Run 结束 | 否 |
| OFFLINE | 超过离线阈值未收到心跳 | 否 |
| REVOKED | 凭据被管理员撤销 | 否 |

建议默认值（实现时配置化）：

- 心跳周期：10 秒；
- DEGRADED：连续 3 次心跳异常；
- OFFLINE：60 秒无心跳；
- Run lease：30 秒，可续租；
- 日志分片：按大小和时间双阈值上传。

## 5. Run 生命周期

对外业务状态继续使用：

```text
PENDING → RUNNING → COMPLETED
                  ↘ FAILED
                  ↘ CANCELLED
```

调度与传输细节通过独立字段表达，不扩充公共业务状态：

- `assignmentStatus`：UNASSIGNED / LEASED / ACKNOWLEDGED / EXPIRED；
- `runnerId`、`runnerPoolId`；
- `leaseId`、`leaseExpiresAt`；
- `startedAt`、`finishedAt`；
- `traceId`、`errorCode`。

重试创建新的 Run 或明确的 attempt 记录，不覆盖原失败事实。

## 6. Runner 上报字段

MVP 只保存最新快照：

- runnerId、name、version、hostname、os、arch；
- labels、capabilities；
- cpu logical cores、cpu usage；
- memory total/used；
- disk total/used；
- GPU model/count、VRAM total/used（可选）；
- maxConcurrency、activeRuns、queuedRuns；
- dependency health；
- lastHeartbeatAt。

控制面不默认采集任意进程、文件列表、网络流量或用户数据。

## 7. API 分组（计划）

以下是目标契约，不代表当前 Swagger 已存在：

| 用途 | 计划路径 | 鉴权 |
|------|----------|------|
| Runner 管理 | `/api/v1/runners/**` | JWT + 资源权限 |
| Runner Pool 管理 | `/api/v1/runner-pools/**` | JWT + 资源权限 |
| Run 管理 | `/api/v1/runs/**` | JWT + 资源权限 |
| Enrollment Token | `/api/v1/runner-enrollment-tokens/**` | JWT + 管理权限 |
| Runner 机器通道 | `/api/system/runners/**` | Runner Credential |
| Agent 对外执行 | `/v1/agents/{agentKey}/runs` | API Key |
| MCP 发布 | `/v1/mcp` | API Key |

不新增根级接口前缀。

## 8. 安全边界

- Enrollment Token 一次性、短时有效，使用后立即作废；
- Runner Credential 可单独撤销、轮换，不复用用户 JWT 或 API Key；
- 机器通道必须使用 TLS；
- Runner 默认主动出站，不要求开放 SSH、Docker Socket 或通用远程 Shell；
- Secret 优先使用 Runner 本地环境或 SecretReference，控制面不在心跳和日志中下发明文；
- Run 输入、输出和日志遵守现有敏感字段脱敏与审计规则；
- Runner 不连接 MySQL、业务 Redis 或其他业务数据库；
- Runner 上报指标遵循最小采集原则。

## 9. MVP 明确不做

- 自动创建服务器；
- 自动扩缩 Runner 数量；
- 通用镜像构建平台；
- Pod/容器级调度；
- 服务发现、负载均衡、网络策略、存储编排；
- 任意远程 Shell；
- 完整时序指标平台；
- 运行中的进程跨服务器迁移。

## 10. 验收场景

1. 一条命令完成 Runner 接入；
2. Runner 10 秒内出现在 Workspace；
3. CPU、内存、磁盘、容量与标签可见；
4. 用户可手动选择 Runner 执行 AgentVersion；
5. 状态、日志、Trace 和结果可回传；
6. 关闭 Runner 后 60 秒内显示 OFFLINE；
7. OFFLINE / DRAINING / REVOKED Runner 不再获得新 Run；
8. 重复 pull、网络重试和 lease 过期不会重复提交结果；
9. Runner 不访问业务数据库；
10. 外部 Endpoint 调用能追踪到固定 AgentVersion、Run 和 Runner。
