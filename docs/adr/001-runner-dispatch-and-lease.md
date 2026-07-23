---
slug: 001-runner-dispatch-and-lease
title: ADR-001 · Runner dispatch、lease 与现有 RabbitMQ/mq_worker 的关系
status: Accepted
date: 2026-07-19
deciders: ORIN vNext 架构组
supersedes: []
amended-by:
  - 002-agent-version-immutability-and-secret-reference.md
related:
  - docs/Runner架构设计.md
  - docs/架构设计.md
  - docs/API文档.md
  - docs/开发规范.md
  - TODO.md (P0 · ADR-001)
---

> 状态：**Accepted**（2026-07-19 文档评审通过）。本文档自此起作为 Runner 分发与 lease 的实现约束；R2 实现 PR 引用 `Refs ADR-001` 即可。
>
> **修订备注**：[ADR-002 §D-2.7](./002-agent-version-immutability-and-secret-reference.md#d-27-secret-bind-协议-amend-adr-001-d-12) 在 `§D-1.2` 五端点之外新增第六端点 `/api/system/runners/{runnerId}/runs/{runId}/secret-bind`。本文件 `§D-1.2` 端点表已同步为六端点；`../API文档.md §2.1` / `../架构设计.md §3.2` / `../Runner架构设计.md §2` 同步项见 [ADR-002 §7 amend 同步项](./002-agent-version-immutability-and-secret-reference.md)。

## 1. 背景（Context）

### 1.1 当前已有的运行时

当前 `orin-ai-engine` 存在一条基于 RabbitMQ 的协作子任务分发链：

- `app.engine.mq_worker.CollabMQWorker`：消费 `collaboration-task-queue`（持久化队列，TTL=5min，DLX=`collaboration-task-dlx`），幂等使用 Redis SETNX `collab:idemp:{packageId}:{subTaskId}:{attempt}`，结果写回 `collaboration-reply-exchange`；
- `app.engine.task_runtime.TaskRuntime`：唯一执行内核，三种执行模式 `AGENT / WORKFLOW / MCP`，其中 `WORKFLOW` 进一步回呼 Java 后端 `/api/workflows/{id}/execute`；
- `app.engine.collaboration_langgraph`：协作编排层，最终把子任务以 MQ 消息方式发到上述队列。

### 1.2 vNext 要做的事

ORIN vNext 引入“远程 Runner”作为 Agent Run 的执行载体，要求：

- Run 必须在用户自有服务器上的 Runner 中执行；
- 同一 Run 只能由一台 Runner 持有一次 lease；
- lease 在取消、超时、撤销时必须能在控制面被立即作废。

### 1.3 候选分发通道与其限制

我们为 Run 的分发通道评估了四类候选。**事实前提**：AMQP 客户端、Runner 出站 WebSocket、Control Plane 入站 WebSocket 三类都有各自的拓扑形态；其中 AMQP 和“Runner 出站 WebSocket”确实可以由 Runner 主动出站发起，不要求 Runner 开放入站端口。所以“是否需要入站”不是否决它们的根本理由。我们否决它们的依据是部署、生命周期、二套分发语义与 Run 维度的概念错位：

| 候选 | 拓扑/机制概览 | 否决理由 |
| --- | --- | --- |
| **RabbitMQ AMQP** | Runner 作为 AMQP 客户端长连 broker；broker 持有消息、Runner 通过 routing key / per-runner queue 拉取。 | ① **额外 broker**：每台 Runner 都要解析 broker 地址 + 集群中长驻 broker；当前 vNext 部署形态只需要 Control Plane + Runner，不引入 broker；② **凭据生命周期复杂**：每台 Runner 都要在 broker 上维护独立账号与队列生命周期（创建/轮换/撤销），这与“Runner Credential 是独立可撤销可轮换类型”重叠，但分两个体系维护（broker + Control Plane），任一处不一致都会失同步；③ **第二套分发语义**：MQ 自带调度（队列、routing、ack、重试），与本 ADR §D-1 规定的 lease 语义会在“过期回收/撤销作废/重复 ack 抑制”上发生行为分歧，长期并存会让任务分发存在两套不可调和的真相。 |
| **WebSocket from Control Plane（Control Plane 入站）** | Control Plane 主动向 Runner 建立 WebSocket；双向推送。 | 需要 Control Plane 入站到 Runner，在家庭网络 / NAT / 企业防火墙后不可靠，且需要在网关层开一条与现有 `/api/system/**` 完全不同的连接管理栈。 |
| **WebSocket from Runner（Runner 主动出站）** | Runner 主动连 Control Plane 的 WebSocket 端点；Control Plane 通过同一 socket 推送 lease/cancel/drain。 | 与 HTTPS Long-Poll + 独立 POST 相比，WebSocket 需要在 Control Plane 引入单独的双向连接栈（会话管理、心跳协议、断线重连策略），而本 ADR §D-1.2 拆出的 5 端点模型在普通 HTTP/1.1 上已经能覆盖所有语义；引入 WebSocket 只会增加部署复杂度而没有新增能力。 |
| **HTTPS Long-Poll + 独立 POST from Runner** | Runner 主动出站；lease 通过 `/lease/claim` 长轮询，cancel/drain/revoke 通过 `/lease/{leaseId}/renew` 响应或显式端点；状态/结果通过独立 POST。 | **采用**。零入站、与现有 Nginx/网关栈兼容、不引入额外基础设施。本 ADR §D-1.2 的 5 端点即此模型的 MVP 形态。 |

### 1.4 结论性边界

因此 Run 分发通道与现有协作 MQ **不可共用**：

- 协作子任务继续走 `mq_worker`（**协作包内部子任务扇出**，不需要机器身份）；
- Run 投递走 **Runner → Control Plane 主动出站 HTTPS，响应携带 lease**（跨机器、需机器身份、需要 lease）；
- 二者都必须经过同一个 `TaskRuntime`。

## 2. 决策（Decision）

> 下面每条决策编号（`D-1.x`）实现时必须可被独立验收。

### D-1.1 Lease 的唯一发放方是 Control Plane

- Java 后端（`com.adlin.orin.modules.runner`，目标模块名以骨架为准）是 Run lease 的**唯一**发放方和维护者；
- `mq_worker` 不得承担“按 Runner 路由 Run”的职责；
- 取消、重试、续租、超时回收全部在 Control Plane 完成。

### D-1.2 Runner 端点拆分（MVP · 已含 ADR-002 amend）

Runner 与 Control Plane 的交互拆分为**六个**独立端点（**含 ADR-002 第六端点 `/secret-bind`**），**不要试图用一条长轮询连接承担所有控制**。MVP 接口清单（路径以 [docs/API文档.md](../API文档.md) 为准）：

| 端点 | 方法 | 用途 | 关键约束 |
| --- | --- | --- | --- |
| `/api/system/runners/{runnerId}/lease/claim` | **POST**，长轮询 | 拉取一个新 lease | 连接保持到有 lease 可发/超时；返回 204 时立即关闭让 Runner 退避；**仅在 `ONLINE` 状态被允许**（`DEGRADED` 默认不发 lease，详见 [Runner架构设计 §4](../Runner架构设计.md)） |
| `/api/system/runners/{runnerId}/lease/{leaseId}/renew` | POST | 续租；返回 JSON 控制指令 | 响应体 JSON `{“action”: “no_op” \| “cancel” \| “drain”, “reason”: “...” \| null, “lease_expires_at”: “...”, “trace_id”: “...”}`；`reason` 仅 `cancel` 时必填（`USER_CANCELLED` / `SECRET_REVOKED` / `LEASE_EXPIRED` / `ASSIGNMENT_TERMINATED`）；`lease_expires_at` 每次返回；Runner 必须解析并在本地生效；每 `lease_renew_interval` 调用一次 |
| `/api/system/runners/{runnerId}/heartbeat` | POST | 上报心跳与资源快照 | 独立通道，不与 lease claim 复用；超时按 [Runner架构设计 §4](../Runner架构设计.md) 退化状态 |
| `/api/system/runners/{runnerId}/runs/{runId}/events` | POST | **批量**回传中间态与日志 | 请求体为批量数组；幂等键 `run:idemp:{runId}:{leaseId}:{runAttempt}:{eventSeq}`（批量内每个 event 各自独立） |
| `/api/system/runners/{runnerId}/runs/{runId}/result` | POST | 提交最终结果 | 幂等键 `run:idemp:{runId}:{leaseId}:{runAttempt}:{-1}`；DB UNIQUE 约束是最终保证（见 D-1.4） |
| `/api/system/runners/{runnerId}/runs/{runId}/secret-bind` | POST | 返回 Run 在该 lease 下允许的 materialized secrets | 请求体 `{ “assignment_id”: “...” }`；Control Plane 从 `assignment_id` 派生 run_id / lease_id / runner_id 并校验 URL 路径 `{runId}` 一致；Runner 不允许上传 `secret_refs`；详见 [ADR-002 §D-2.7](../adr/002-agent-version-immutability-and-secret-reference.md) |

**禁止**：

- 在 `lease/claim` 长轮询连接上关闭后再读响应——长轮询连接只能用来”拿一次 lease”；
- 用 heartbeat 隐式续租——续租必须显式调用 `/lease/{leaseId}/renew`；
- 在 `/result` 成功后由 Runner 调用任何 `/lease/release` 端点——assignment 由 Control Plane 在接受 result 后原子结束；MVP **不**新增 release 端点；
- `DEGRADED` Runner 主动发起 `lease/claim`——DEGRADED 不领单，需要修复后切回 ONLINE；
- 引入 `/lease/{leaseId}/release` 作为通用端点；
- 在 lease claim / renew 响应里”顺便”附 secret——仅 `secret-bind` 端点下发；
- `/secret-inject` 端点**不**存在——materialized secrets 必须在 `secret-bind` 同一响应内返回；
- 让 Runner 上传 `secret_refs`——这是越权读取风险。

### D-1.3 TaskRuntime 保持唯一执行内核；协作 MQ 不变

- `app.engine.task_runtime.TaskRuntime` 仍是唯一任务计算类，**禁止**在 Runner 控制循环里复制 Agent / Workflow / Collaboration 执行逻辑；
- 协作子任务继续走 `collaboration_langgraph → mq_worker → TaskRuntime`；
- Runner 控制循环**不**消费 `collaboration-task-queue`，也不向 RabbitMQ publish 任何 Run 级别的消息。

### D-1.4 幂等、重试与最终事实

#### D-1.4.1 幂等键

| 上下文 | 命名空间 | TTL |
| --- | --- | --- |
| 协作子任务（不改动） | `collab:idemp:{packageId}:{subTaskId}:{subtaskAttempt}` | 10 分钟 |
| Run 事件与结果 | `run:idemp:{runId}:{leaseId}:{runAttempt}:{eventSeq}` | = `lease_expires_at` + 30s |

`eventSeq` 是 Runner 自增序号；最终结果用 `-1`。

**响应策略**：

- 同一幂等键 + **相同 payload 摘要**（按 `run_id` + body canonical hash）→ 直接接受，按正常 200 返回；不计新 attempt；
- 同一幂等键 + **不同 payload 摘要** → 409 `RESULT_CONFLICT`，**不**更新写状态，原状态保留；
- 不存在的幂等键 → 正常写入。

#### D-1.4.2 最终事实来源

- **数据库唯一约束 / 状态机 CAS 是最终幂等保证**：
  - 表 `run_event(run_id, lease_id, run_attempt, event_seq)` 上 `UNIQUE(run_id, lease_id, run_attempt, event_seq)`；
  - 表 `run_result` 上 `UNIQUE(run_id, lease_id, run_attempt)`；
  - 表 `run_assignment` 状态机：`ASSIGNED → ACKED → COMPLETED | FAILED | CANCELLED | EXPIRED`，任何终态不可被覆盖（[开发规范 §1.4](../开发规范.md)）；
  - `terminal_reason`（独立字段，不进入状态枚举本身）：
    - 用户取消：`CANCELLED / USER_CANCELLED`
    - 网络失联 / 心跳超时 / lease 过期：`EXPIRED / NETWORK_LOST`
    - Revoke 后重分配：`EXPIRED / CREDENTIAL_REVOKED`
    - Revoke 后失败：`FAILED / RUNNER_REVOKED`
  - 状态变更写审计时同时写入 `terminal_reason`，禁止把 `EXPIRED(CANCELLED)` 这类复合值直接作为状态名使用。
- **Redis 仅作短期加速层**：用于“短时间内的重复 ack 抑制”与“续租/心跳高频请求的快速拒绝”。Redis 中的值不可与 DB 冲突，冲突时以 DB 为准。

#### D-1.4.3 重试与重新分配模型

“重新分配”是一个被滥用的词。本节把“用户主动行为”“Runner 失联”“凭证撤销”三类触发拆开，各自独立：

| 触发原因 | 控制面行为 | 是否产生新 assignment | 数据库痕迹（`status` / `terminal_reason`） |
| --- | --- | --- | --- |
| **Runner 心跳超时 / lease 过期 / Runner 进程失联 / 网络中断** | 按 R2 实施时配置的重试策略自动重新分配 | 是 | 旧 `run_assignment.status=EXPIRED`，`terminal_reason=NETWORK_LOST`；新增 `run_assignment`（新 `lease_id`），`run.run_attempt += 1`；旧 attempt 的失败事实（`error_code` / `failed_at` / `runner_id` / `trace_id`）保留 |
| **Runner 重复 ack / 网络抖动手动重提结果** | 命中 `UNIQUE` 直接接受 | 否 | 命中 `UNIQUE` 不产生新 attempt；写新 audit 表示“重复 ack” |
| **用户手动 Cancel**（Workspace 触发） | Run 终态 `CANCELLED` | **否，禁止重新分配** | `run_assignment.status=CANCELLED`，`terminal_reason=USER_CANCELLED`，`run.status=CANCELLED`；**不**创建新 attempt |
| **用户触发 Runner Drain** | Runner 进入 `DRAINING`，停止领取新 Run；当前 Run **自然完成**或被 Cancel | **否**，直到当前 Run 结束 | 仅更新 Runner 状态；当前 Run 仍归当前 lease 完成 |
| **Runner Credential 撤销** | Control Plane 立即把 Runner 置为 `REVOKED`；当前 active assignment 按策略处理 | 由策略决定（见下） | 见“Revoke 策略”子节 |
| **用户手动 Retry**（Workspace 触发） | 创建**新 Run**，并挂上 `retry_of_run_id` | 是（新 Run） | 新 `run.id`，新 `run_id`，`run.retry_of_run_id` 指向原 Run；原 Run 终态不被覆盖 |

**Revoke 策略**（默认 `FAIL`，可由 `run.retry_on_revoke` 配置项覆盖为 `REASSIGN`）：

- `FAIL`：当前 active assignment 终态 `status=FAILED`，`terminal_reason=RUNNER_REVOKED`；Run 终态 `FAILED`，**不**进入重新分配；
- `REASSIGN`：与“Runner 失联”相同的重新分配流程——`run_attempt += 1`，新增 assignment，原 `run_assignment.status=EXPIRED`，`terminal_reason=CREDENTIAL_REVOKED`。该配置项须在 Run 入参快照中显式记录，避免 Revoke 后行为不可追溯。

**禁止**：

- “自动重新分配”与“用户重试”共用同一条记录；
- 把 Cancel、Drain 误归为“重新分配”原因；
- 新 attempt 覆盖任何旧 attempt 的失败事实字段；
- Revoke 之后再让 Runner 用失效凭据“上报 REVOKED”（见 D-1.6）。

### D-1.5 W3C trace context 传播

- `traceId` 在 **Run 创建时**生成；如果调用方（如 Endpoint 调用 / 用户手动 Retry）在 `traceparent` 中已经携带了 traceId，则 Run 直接继承该 traceId；如果没有则由 Control Plane 生成；
- lease 发放时 Control Plane 携带的 `traceparent` 是“Run 维度的子 span”，traceId 必须等于 `run.trace_id`；
- 跨 Runner、跨 Control Plane 的所有跳都必须传播 W3C `traceparent`，但**不要求每次跳都原样回传同一个 traceparent 字节**——W3C 规范允许每跳创建新的 span id 而保留 traceId；
- Runner 必须把入口的 `traceparent` 翻译到内部追踪上下文，并把出口的 `traceparent` 重新序列化（含本次跳的新 span id）发送；
- 控制面日志、审计和最终 Result 中的 `traceId` 必须与 `run.trace_id` 一致（用 traceId 字符串比较，不用 traceparent 字符串比较）；
- `traceparent` 处理协议（Runner 强制）：
  - `traceparent` 缺失或非法，但 `run.trace_id` 合法 → Control Plane / Runner 基于 `run.trace_id` 创建新 span id，组成新的 `traceparent`，traceId 等于 `run.trace_id`；
  - `traceparent` 中解析出的 traceId 与 `run.trace_id` 不一致 → 拒绝执行该 Run / lease 并返回协议错误（例如 `409 TRACE_MISMATCH` 或 lease 时的 `LEASE_INVALID`）；
  - `run.trace_id` 缺失 → 拒绝发放 lease（`LEASE_INVALID trace_id_missing`）；
  - **禁止** Runner 自生成另一个 traceId；任何兜底生成的新 `traceparent` 不得引入新的 traceId，只允许换 spanId。

### D-1.6 取消 / Drain / 撤销

> 本节把“取消 / Drain / 撤销”三类用户或管理员行为与 §D-1.4.3 的重新分配模型对齐：取消和 Drain **不**进入重新分配；撤销按 `FAIL` / `REASSIGN` 策略分支（详见 §D-1.4.3）。

- **用户取消 Run**：控制面把对应 `run_assignment.status` 标记为 `CANCELLED`，`terminal_reason=USER_CANCELLED`，Run 进入终态 `CANCELLED`；Runner 通过下一次 `/lease/renew` 收到的 `cancel` 控制帧，或下一次 `/events` 或 `/result` 返回的 `LEASE_EXPIRED`（HTTP 410）获知。**不再做重新分配**。
- **用户触发 Runner Drain**：控制面把 Runner 状态置为 `DRAINING`；下一次 `/lease/claim` 返回 204，Runner 不再拉新 Run；当前 Run **自然完成**或由用户取消；Runner 在当前 lease 结束后才允许进入 `OFFLINE`。
- **管理员撤销 Runner Credential**：控制面**自己**持久化 Runner=`REVOKED`（无需 Runner 上报）；Runner **下一次任意端点请求**收到 `403 RUNNER_REVOKED`，Runner **立即**取消本地任务、停止后续请求并退出进程；Runner **不再**用失效凭据"释放 lease / 上报 REVOKED"，那两类消息无法跨过 403 鉴权。
- **禁止**：把 cancel/drain/revoke 设计为与 lease claim 同一长轮询连接上的隐藏消息；这些必须经显式端点或下一次响应；
- **禁止**："凭据失效" 笼统用 403——必须区分鉴权失败与身份失效（见 §D-1.7）。

### D-1.7 错误响应契约

`{code, message, traceId}` 与 `BusinessException` 响应一致；常用错误码：

| 错误码 | HTTP | Runner 应做 |
| --- | --- | --- |
| `RUNNER_CREDENTIAL_INVALID` | 401 | 凭据缺失 / 格式错误 / 未知；Runner 立即停止后续请求并退出进程（不可重试） |
| `RUNNER_REVOKED` | 403 | 凭据有效但 Runner 身份已被撤销；Runner 立即取消本地任务、停止后续请求并退出进程（见 D-1.6） |
| `RUNNER_OFFLINE` | 409 | 进入降级，不发新 lease 拉取 |
| `LEASE_EXPIRED` / `LEASE_NOT_FOUND` | 410 | 丢弃该 lease 上的中间状态 |
| `RESULT_CONFLICT` | 409 | 同一幂等键但 payload 不一致；Runner 应停止当前批次的写入并查找本地日志排查根因 |
| `INTERNAL_ERROR` | 5xx | 保留本地状态按指数退避重试 |

> 不再使用 `RESULT_DUPLICATE`；同幂等键同 payload 由 §D-1.4.1 直接接受为 200。
> 仅当 Control Plane 已确认 Runner 身份（即已校验过凭据签名）后才返回 `RUNNER_REVOKED`；身份未确认前一律返回 `RUNNER_CREDENTIAL_INVALID`。

## 3. 后果（Consequences）

### 3.1 正面

- 通道与凭据类型清晰：5 个端点 + 2 种状态语义（Run/Runner），未来扩展不需要重构通信层；
- 协作 MQ 不被破坏：现状沉淀的幂等与 trace 经验继续生效；
- TaskRuntime 不被复制：执行内核唯一；
- 双重 idempotent story：DB 唯一约束保真相，Redis 加速幂等响应；
- 取消/撤销语义收敛：所有状态变迁在 Control Plane 一处完成。

### 3.2 负面与代价

- 端点数量增加：5 个端点要求清晰的 Owner 划分（Runner SDK vs Control Plane），文档/前端/监控都要 1:1 跟进；
- Redis 仅做加速层需要明示：实现者容易把 Redis 当真相来源，必须用代码注释 + 测试守护；
- 心跳与 lease 续租在实现层必须独立，避免运行中耦合（更明确，但更多接口要测）；
- W3C traceparent 进出翻译要求 ai-engine 内部有 trace contextvar 工具支持，落地时需要确认现有 `app/core/trace_context.py` 兼容 W3C spec 的 span id 生成。

## 4. 备选方案（Alternatives considered）

| 方案 | 主要拒绝理由 |
| --- | --- |
| 复用 RabbitMQ 作为 Run 投递通道 | 1.3 节列出的 4 条理由 |
| WebSocket 由 Control Plane 主动推 | 需要 Control Plane 入站；引入与 `mq_worker` 不同协议的连接管理栈 |
| 单个长轮询连接承担 cancel/drain/revoke | 同一连接只能表达一次响应，无法在 lease 返回后再发送控制指令（参见 §1.3 单连接模型限制） |
| gRPC Bidirectional Stream | 新增 gRPC 依赖与 proto 工具链；当前 MVP 不需要双向语义；如未来确认需要，写新 ADR supersede 本条 |
| Kubernetes 自定义 Operator | vNext 产品定位是“无需 Kubernetes”（[产品定位 §2](../产品定位.md)），明确不做 |

## 5. 待办与开放问题（Open questions）

> 下面每条都必须真正落到 TODO 与 PR，没有 TODO 跟踪前不得视为已闭环。

1. **lease claim 端点的传输层选型**：Servlet async / WebFlux / Netty，写到 [架构设计 §3.1](../架构设计.md)。决定前不得开始 D-1.2 接口的实现。
2. **续租间隔配置**：默认与 D-1.2 表一致，但要求实现可配置并写入 [部署指南](../部署指南.md)。
3. **`/events` 升级为流式的时机**：本 ADR 决定 MVP 为批量 POST；任何“升级为 chunked/SSE”必须先写新 ADR supersede 本节。
4. **Runner Credential 类型化字段**：是 opaque token 还是 JWT；同样延后到 [开发规范 §3](../开发规范.md) 与 R2 实现 ADR 一并落定。
5. **Run 重试 → Endpoint 影响的可见性**：用户手动 Retry 时是否同时影响已绑定的 Endpoint？这与 ADR-003（Endpoint 响应契约）正交，本 ADR 不决策。
6. **`run.retry_on_revoke` 字段在 Run 入参快照中的位置与版本化**：与 ADR-002 协调。
7. **失败事实保留字段落表与 JSON 契约**：`error_code` / `failed_at` / `runner_id` / `trace_id` 在不同 attempt 之间如何统一序列化，待 R2 实现 PR 中给出。

> 下列事项已在本 ADR 中定稿并关闭，不再列为开放问题：
>
> - `/lease/{leaseId}/renew` 响应控制帧的协议形态 → §D-1.2 决定为 JSON `{"action": "no_op" | "cancel" | "drain", "trace_id": "..."}`。
> - `/events` 是否流式 → §D-1.2 决定为批量 POST。
> - `/result` 之后是否主动 `release` → §D-1.2 决定 MVP 不提供 release 端点，由 Control Plane 在 result 接受后原子结束 assignment。

## 6. 与其他 ADR / TODO 的关系

- **ADR-002（AgentVersion 不可变与 SecretReference）**：复用本 ADR §D-1.4 的“最终事实来源在 DB”原则，规定 Run 入参快照从 `agent_version` 表读取而不是从 MQ payload 合并。
- **ADR-003（Endpoint 同步 / 流式 / 异步响应）**：复用本 ADR §D-1.6 的“取消与超时必须经显式端点”，规定 Endpoint 在异步路径下的取消如何转译为 lease 状态。
- **TODO P0「当前基线缺陷」**：与本 ADR 并行推进；本 ADR 不修复旧 bug，只是约束未来行为。

## 7. 验收条件（Acceptance criteria）

状态约定：

- **评审期间**：ADR frontmatter `status: Proposed`，ADR 索引表与 TODO 中该项保持 `[ ]`。
- **评审通过**：同步将 frontmatter、ADR 索引表、TODO 切换到 `Accepted` / `[x]`；以下全部勾选完成后才允许切换。

本节只列**文档层**与**同步性**的硬验收项（任一未勾选前不切换 `Accepted`）；R2 实现层回归放在 PR review 中核对，不在本 ADR 内累计。

文档层验收项：

- [x] [Runner架构设计.md §2](../Runner架构设计.md) 拓扑图移除 `mq_worker` 组件，改为"Runner 控制循环 → TaskRuntime"；
- [x] [架构设计.md §3.2](../架构设计.md) “目标”段与本 ADR §D-1.2 接口列表一一对应；
- [x] [开发规范.md §1.4](../开发规范.md) 增补"Run 幂等键 / RunAttempt / 双重事实源 / W3C 每跳新 span / `run_assignment` 状态机 + `terminal_reason`"条款；
- [x] [API文档.md](../API文档.md) §2.1 列出 Runner 机器通道五端点清单，与本 ADR §D-1.2 完全一致；五端点错误响应细则与本 ADR §D-1.7 一致；
- [x] 本 ADR §5 七条开放问题已拆分进 TODO 子条目并开始跟踪。

R2 实现层回归（**不**列为 `Accepted` 前置，由 R2 PR 自检）：

- R2 PR description 第一行写 `Refs ADR-001`；评审引用 ADR 文档便于回链；不再在本 ADR 文件内记录实现进度，避免与 ADR README “ADR 只描述决策及其背景；实现进度由代码、测试和 PR 体现”规则冲突。
