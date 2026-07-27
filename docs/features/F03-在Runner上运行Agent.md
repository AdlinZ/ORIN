# F03 · 在 Runner 上运行 Agent

> 状态：E2E Working
> 用户角色：Creator / Operator
> 前置功能：F01、F02
> 关联决策：[ADR-001](../adr/001-runner-dispatch-and-lease.md)、[ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)

## 1. 用户问题与结果

用户已经接入服务器并创建 AgentVersion，但还不能确认 Agent 是否真的在指定机器上执行。完成后，用户可以从 Agent 或 Runs 页面选择 Runner/RunnerPool，创建 Run，并由目标 Runner 通过唯一 `TaskRuntime` 执行固定版本并返回最终结果。

## 2. 范围

### 2.1 本功能包含

- 从 AgentVersion 发起测试 Run；
- 手动选择 Runner/RunnerPool 与容量校验；
- Run、run_assignment、lease claim/renew、secret-bind、events/result；
- AgentVersion digest/schema 校验与 SecretReference materialization；
- 超时、失联、重复提交、Runner Revoke 和本地 Secret 缺失；
- 一个无外部 Provider 的确定性示例 Agent 和跨机器验收。

### 2.2 本功能不包含

- Kubernetes 式调度、自动扩缩和抢占；
- 通用工作负载或第二套执行内核；
- 完整日志/Trace 浏览体验，归 F04；
- 对外发布，归 F05。

## 3. 完整用户旅程

1. 用户在某个 FROZEN AgentVersion 上点击“运行”；
2. 选择 ONLINE Runner 或 RunnerPool，并看到容量或能力不匹配原因；
3. 页面创建 Run 并展示 PENDING；
4. Runner 主动 claim lease，校验版本与 SecretReference 后交给 `TaskRuntime`；
5. 页面显示 RUNNING 和实际 runner/version/traceId；
6. 执行完成后展示 COMPLETED 与真实结果；失败时展示稳定错误与可追踪事实；
7. 网络重试不会造成重复执行或覆盖原结果。

## 4. 系统协作

- Java Control Plane 是 Run、assignment、lease、事件索引、结果和审计的事实源；
- Runner 只通过 `/api/system/runners/**` 主动出站，不访问业务数据库；
- `TaskRuntime` 是唯一执行内核，现有协作 MQ 只负责协作包内部子任务；
- AgentVersion、digest、Secret revision 和 traceId 在整个 Run 中固定并可审计。

## 5. 验收

- [x] 在隔离的 Colima VM / Docker 容器中，真实 Runner 成功执行确定性 AgentVersion；
- [x] 页面能确认实际 Runner、版本、traceId、状态、事件和结果；
- [x] 非 ONLINE Runner 不接新 Run，DRAINING/REVOKED 仍沿用 F01 状态机；
- [x] 数据库行锁、事件唯一键和结果 payload hash 保证重试不重复完成；网络中断和 lease 过期会停止本地执行；
- [x] Secret materialization 仅进入 Runner 进程内存；持久化 binding 只保存引用，日志只记录数量；
- [x] 后端、Runner、前端测试、真实 Docker Runner E2E 和浏览器 smoke 通过。

## 6. 不算完成

- 只有 Run 表、lease Controller 或 Runner 轮询循环；
- Run 实际仍在固定 AI Engine 机器执行；
- UI 使用模拟状态或结果；
- 没有跨机器证据、幂等验证或失败路径。

## 7. 关联文档

- [ADR-001](../adr/001-runner-dispatch-and-lease.md)
- [ADR-002](../adr/002-agent-version-immutability-and-secret-reference.md)
- [Runner 架构设计](../Runner架构设计.md)
- [API 文档](../API文档.md)

## 8. 实现状态（2026-07-24）

**当前状态：E2E Working**

- 已闭环：`run_assignment` 是 lease/attempt/终态原因的事实表；Runner 机器通道提供 claim、renew、secret-bind、events、result；`runs` 保留面向产品查询的投影字段。
- 已闭环：Python Runner 从独立 Docker 容器主动出站，经 claim → secret-bind → `TaskRuntime` → events/result 完成真实确定性 AgentVersion，并在 lease 终结时取消本地执行。
- 已闭环：`/workspace/runs` 可选择真实 FROZEN AgentVersion 与 ONLINE Runner；详情可核对 Runner、版本、traceId、attempt、事件和最终输出。
- 自动化证据：`scripts/f03-runner-e2e.sh` 创建临时 Agent、冻结版本、接入隔离 Runner、创建 Run，并断言 `COMPLETED`、确定性输出和至少两条持久化事件。
- 测试证据：backend 836、AI Engine 216、frontend 129 全部通过，前端生产构建通过。
- 继续硬化但不阻塞 E2E Working：claim 真正 long-poll、全跳 W3C `traceparent`、Redis 幂等加速、RunnerPool/容量调度，以及 Gateway Secret revision/rotation。
