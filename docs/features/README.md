# ORIN 产品功能索引

本目录是 ORIN vNext 的**产品交付真相源**。功能不是某张表、某个接口或某个页面，而是用户能够从界面开始并获得可验证结果的一段完整旅程。

架构设计、ADR、API 文档和开发规范继续定义实现约束；它们本身不证明产品功能已经完成。

## 功能主线

| ID | 功能 | 用户结果 | 当前状态 |
| --- | --- | --- | --- |
| F01 | [接入并监控服务器](./F01-接入并监控服务器.md) | 用户把自己的服务器接入 ORIN，并看到真实在线状态和资源 | Partially Integrated |
| F02 | [创建并冻结 Agent](./F02-创建并冻结Agent.md) | 用户完成 Agent 草稿配置并得到不可变 AgentVersion | Not Started |
| F03 | [在 Runner 上运行 Agent](./F03-在Runner上运行Agent.md) | 用户选择 Runner/RunnerPool，真实执行一个固定 AgentVersion | Not Started |
| F04 | [观察和控制 Run](./F04-观察和控制Run.md) | 用户查看状态、日志、Trace、结果，并能取消或重试 | Not Started |
| F05 | [发布 API 与 MCP](./F05-发布API与MCP.md) | 用户把已验证 AgentVersion 发布并从外部真实调用 | Not Started |

功能按 F01 → F05 形成主路径。允许并行准备后续功能的技术基础，但不得在前置用户旅程尚未成立时把后续功能标记为完成。

## 状态定义

| 状态 | 判定 |
| --- | --- |
| `Not Started` | 用户旅程尚不可执行；即使已有旧代码或设计，也仍算未开始 |
| `Backend Only` | 只有数据模型、服务或接口；用户无法从产品界面完成旅程 |
| `Partially Integrated` | 多端已经连接，但关键正常路径或失败路径仍需人工绕过 |
| `E2E Working` | 用户可从真实界面完成旅程，真实后端 E2E 与手工 smoke 通过 |
| `Production Ready` | 在 E2E Working 基础上完成权限、安全、审计、升级与运维验收 |

只有 `E2E Working` 或 `Production Ready` 才能在路线图和 TODO 中把顶层功能勾为完成。

## 功能文档规则

每份功能文档必须包含：

1. 用户问题与结果；
2. 范围与明确不做；
3. 正常用户旅程；
4. 页面、Control Plane、Runner/Runtime 的协作；
5. 失败、安全、权限和审计路径；
6. 自动化与手工验收；
7. 不算完成的情况；
8. 关联 ADR、API、部署和角色文档。

实现可以拆成多个可审查 PR，但拆分项只是“实现组成”，不能独立把功能状态切为完成。最后一个集成 PR 必须提供用户旅程证据，并同步本索引、对应功能文档、[路线图](../路线图.md)、[功能完成度](../功能完成度.md)与 [TODO](../../TODO.md)。

## 模板

新增主功能前使用 [功能规格模板](./TEMPLATE.md)。新功能必须直接帮助用户创建、运行、监控或发布 Agent；否则应归入现有功能内部、Admin、兼容层或暂缓范围。
