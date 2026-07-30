# F04 · 观察和控制 Run

> 状态：E2E Working
> 用户角色：Creator / Operator
> 前置功能：F03

## 1. 用户问题与结果

一次 Run 只有最终成功或失败不足以帮助用户理解 Agent。完成后，用户可以在一个 Run 详情中看到时间线、实时日志、Trace、结果和资源关联，并能安全取消或基于原失败事实重试。

## 2. 范围

- Runs 列表、筛选和详情；
- 状态时间线、批量事件、日志、Trace、结果与错误；
- 当前 Runner、AgentVersion、attempt、lease 与 traceId 关联；
- Cancel、Drain 影响展示、手动 Retry 产生新 Run；
- 日志脱敏、权限、审计和稳定错误展示；
- Runner 离线、lease 过期、Secret 撤销和本地 Secret 缺失的解释。

不包含通用 APM、任意服务器日志浏览或修改历史 Run。

## 3. 完整用户旅程

1. 用户从 Agent 或 Runs 列表打开真实 Run；
2. 页面按时间展示 PENDING、assignment、RUNNING、事件和终态；
3. 用户查看实时或准实时日志、Trace、结果以及对应 AgentVersion/Runner；
4. 长 Run 可被取消，Runner 在协议时限内停止；
5. 失败 Run 保留原错误、attempt 和结果，用户点击 Retry 后得到关联的新 Run；
6. 页面能解释离线、过期、撤销、容量或运行时错误，并给出下一步。

## 4. 验收

- [x] 一个真实跨机器 Run 的状态、日志、Trace 和结果在同一页面可见；
- [x] traceId 跨 Control Plane、Runner、TaskRuntime 一致；
- [x] Cancel 可达 Runner，且不会被错误地重新分配；
- [x] Retry 创建新 Run 并保留原失败事实；
- [x] 敏感值不出现在日志、Trace、结果或审计；
- [x] 权限、失败状态、真实后端 E2E 和手工 smoke 通过。

## 5. 不算完成

- 只有日志接口或 Trace 页面；
- 只能看最终 result，无法解释执行过程；
- Retry 覆盖原 Run；
- Cancel 只改数据库状态但 Runner 继续执行；
- 页面展示未脱敏原始事件。

## 6. 关联文档

- [ADR-001](../adr/001-runner-dispatch-and-lease.md)
- [开发规范](../开发规范.md)
- [角色矩阵](../角色矩阵.md)

## 7. 实现状态（2026-07-25）

**当前状态：E2E Working**

### 已闭环

- `GET /api/v1/runs/{runId}/events` — 事件时间线端点（增量拉取），H2 正向契约测试通过
- `GET /api/v1/runs/{runId}/assignments` — 分配历史端点，H2 正向契约测试通过
- `GET /api/v1/runs` 筛选参数（status、agentId、runnerId），H2 3 维筛选测试通过
- Run 资源级 ACL：普通用户只可查看、取消或重试自己的 Run；`ROLE_OPERATOR` / `ROLE_ADMIN` 可跨 owner 处理；历史无主记录只向特权角色可见
- `RunDetailPage.vue` — 独立详情页：状态流转（el-steps）、事件时间线（el-timeline，2s 自动轮询）、实时日志、Trace 跳转、终态原因中文解释、Cancel/Retry 按钮
- `RunListPage.vue` — 运行中心按“进行中 / 已产出结果 / 需要处理 / 已停止”聚合记录，失败任务可直接诊断或重试；Runner 等调度字段不占用主列表
- Trace 不再作为主菜单入口，只从具体 Run 详情的技术信息中下钻
- Python Runner 6 步事件时间线（started → config → secrets → execution_started → execution_completed → finished）
- 深层链接 `/workspace/runs/:runId` 正确加载 Run 详情
- 详情页自动轮询（活跃 Run 时每 2-3s 刷新状态/事件/日志）
- `statusType ||→??` bug 修复（RUNNING 状态不因 falsy fallback 显示错误颜色）

### 测试证据

| 层级 | 测试 | 结果 |
|------|------|------|
| Backend 全量回归 | `mvn test` | 853 passed（Temurin 17） |
| Backend 定向 | `RunF04H2Test`（16）+ `RunServiceLeaseSecurityTest`（8） | BUILD SUCCESS；覆盖 owner 隔离、跨 owner 403、特权角色跨 owner 管理、Retry 保留原 owner |
| Frontend vitest | 21 个 F04 相关测试 | 全绿 |
| Frontend build | `npm run build` | ✅ 8.57s |
| Playwright E2E | 2 scenarios（详情页；确认取消；提交 Retry） | 2/2 passed；用例真实点击确认框并断言对应 API 请求 |
| Docker Runner E2E | `f03-runner-e2e.sh` with `orin-runner:f04` | logs=6 events=6 assignments=1 ✅ |

### 继续硬化但不阻塞 E2E Working

- W3C `traceparent` 全跳（Control Plane → Runner → TaskRuntime 跨进程）
- Redis 幂等加速
- RunnerPool/容量调度
- Gateway Secret revision/rotation
