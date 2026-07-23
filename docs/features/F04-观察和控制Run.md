# F04 · 观察和控制 Run

> 状态：Not Started
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

- [ ] 一个真实跨机器 Run 的状态、日志、Trace 和结果在同一页面可见；
- [ ] traceId 跨 Control Plane、Runner、TaskRuntime 一致；
- [ ] Cancel 可达 Runner，且不会被错误地重新分配；
- [ ] Retry 创建新 Run 并保留原失败事实；
- [ ] 敏感值不出现在日志、Trace、结果或审计；
- [ ] 权限、失败状态、真实后端 E2E 和手工 smoke 通过。

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
