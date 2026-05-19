# ORIN 详细改造待办清单

本文档用于跟踪 ORIN 后续改造工作，架构基线见 [docs/架构设计.md](docs/架构设计.md)。

## 使用说明

- `[x]` = 已合并 main 并烟测通过
- `[~]` = 部分完成，代码或页面已落地但端到端闭环/验收不足
- `[ ]` = 未开始或未完成
- `[!]` = 已废弃，不再按该项推进
- 优先级标记
  - `P0` 核心闭环，优先完成
  - `P1` 重要增强，核心闭环后完成
  - `P2` 体验和治理优化

---

## 阶段 0：基线收敛

### 文档与架构基线

- [x] `P0` 新增阶段 0 基线文档，明确当前真实架构、改造范围和实施约束
- [x] `P0` 更新 README，明确当前实现以 `Spring Boot + Vue 3 + Python AI Engine` 为准
- [x] `P1` 更新 `docs/API文档.md`，补齐当前真实接口分组与路径口径
- [x] `P1` 为”原始设计方案 vs 当前实现”整理一页简版对照说明，供产品/开发统一理解

### 链路基线

- [x] `P0` 统一三条主链路命名与边界
  - Agent 调用链
  - Workflow 执行链
  - Collaboration 协作链
- [x] `P0` 明确每条链路的入口接口、执行服务、审计点、追踪点和失败处理点
- [x] `P0` 建立核心链路验收矩阵
  - 已在 `docs/功能完成度.md` 固定 Agent 对话、知识库、Workflow、Collaboration、MCP 五条核心链路的前端入口、后端入口、AI Engine 参与点、必需依赖、当前风险和下一步补测入口
  - 后续模块评分、smoke、targeted test 和真实链路验收以该矩阵为入口逐步更新

---

## 阶段 1：多智能体协作闭环

### 审查结论（2026-03-26）

- 当前完成度判断：约 `75%`
- 已完成的重点
  - 协作任务包、子任务、事件日志三类核心数据模型已落地
  - 协作包状态机已落地
  - 子任务状态机与合法流转校验已落地
  - 前端任务拓扑与事件时间线已切到真实接口
  - 创建任务包后前端会自动触发任务分解
  - CollaborationExecutor 已集成真实 Agent 调用
  - 添加了运行时执行接口（启动、重试）
  - 添加了人工干预接口（跳过、手动完成）
  - 协作包完成后统一审计记录
- 仍未完成的重点
  - 前端协作页人工干预操作按钮与详情闭环已补基础入口，仍需端到端业务验收
  - 协作 Workflow 子任务已支持显式 `workflowId` 分解、MQ `WORKFLOW` 策略映射，并复用 AI Engine `TaskRuntime.execute_workflow_task`
  - 协作页筛选已接真实接口，但人工操作闭环仍缺失
  - Workflow 子任务运行态 smoke 已提供 `ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK=1` 可选强验收，运行环境需具备 RabbitMQ、AI Engine MQ worker、后端协作结果监听与 `ORIN_BACKEND_AUTHORIZATION`

### 1.1 后端协作任务执行

- [x] `P0` 梳理协作相关实体和职责
  - `CollaborationTask`
  - `CollaborationPackageEntity`
  - `CollabSubtaskEntity`
  - `CollabEventLogEntity`
- [x] `P0` 明确”简单协作任务”和”协作任务包”两套模型是否长期共存
- [x] `P0` 为 `CollaborationService` 定义真实执行流程，替换当前仅推进索引的占位逻辑
- [x] `P0` 将子任务执行映射到真实执行器
  - 调用智能体
  - 调用工作流
  - 人工任务占位
- [x] `P0` 为协作包增加执行状态机
  - `PLANNING`
  - `DECOMPOSING`
  - `EXECUTING`
  - `CONSENSUS`
  - `COMPLETED`
  - `FAILED`
  - `FALLBACK`
- [x] `P0` 为子任务增加状态机与合法流转校验
  - `PENDING`
  - `RUNNING`
  - `COMPLETED`
  - `FAILED`
  - `SKIPPED`
  - `CANCELLED`

### 1.2 协作事件与共享上下文

- [x] `P0` 补齐事件总线事件定义与触发点
  - 包创建
  - 包分解
  - 子任务开始
  - 子任务完成
  - 子任务失败
  - 回退触发
  - 包完成
- [x] `P1` 为黑板内存增加统一结构
  - 当前意图
  - 上下文摘要
  - 中间结果
  - 最终结论
- [~] `P1` 增加协作检查点保存与恢复能力
  - 当前已具备 service 层保存、读取、列出、回滚能力
  - 补齐控制器接口和前端闭环
- [x] `P1` 增加人工干预接口
  - 强制跳过子任务
  - 手动写入子任务结果
  - 重试失败子任务
  - 手动完成协作包

### 1.3 协作前端页面

- [x] `P0` 统一协作页路由和 API 口径，避免旧页面与新页面重复
- [x] `P0` 将 `CollaborationDashboard.vue` 中的模拟数据改为真实接口返回
- [x] `P0` 协作页展示真实拓扑
  - 子任务依赖关系
  - 当前执行节点
  - 各节点状态
- [x] `P1` 协作页展示真实事件时间线
- [~] `P1` 协作页支持人工干预操作
- [x] `P1` 协作页支持按用户、状态、优先级筛选

### 1.4 阶段验收

- [~] `P0` 创建协作包后可以自动分解为真实子任务
- [x] `P0` 协作子任务可以实际调用智能体或工作流
- [~] `P0` 协作事件能完整记录并回显到前端
- [~] `P0` 子任务失败后可重试或触发回退
- [~] `P0` 协作包完成后有明确最终结果和审计记录

---

## 阶段 2：任务与队列体系收敛

### 2.1 模型收敛

- [x] `P0` 梳理并统一 `TaskQueue` 与 `TaskEntity` 的职责边界
- [x] `P0` 明确以下对象的层级关系
  - 业务任务
  - 工作流任务
  - 协作任务
  - 重试任务
- [x] `P0` 统一任务状态字段与语义
  - `PENDING`
  - `RUNNING`
  - `RETRYING`
  - `COMPLETED`
  - `FAILED`
  - `DEAD`
  - `CANCELLED`

### 2.2 RabbitMQ 与重试

- [x] `P0` 保留并完善现有指数退避重试逻辑
- [x] `P0` 明确延迟重试、最大重试次数、死信入队条件
- [x] `P1` 为不同任务类型支持不同重试策略
- [x] `P1` 为死信任务增加人工恢复能力

### 2.3 任务监控接口

- [x] `P0` 增加任务队列监控 API
  - 队列长度
  - 运行中任务
  - 失败任务
  - 死信任务
  - 按优先级统计
- [x] `P1` 增加任务详情页所需字段
  - 触发来源
  - 关联工作流
  - 关联协作包
  - 最后错误
  - 下次重试时间

### 2.4 前端任务页

- [x] `P0` 将任务页改成真实队列视图，不只展示普通表格列表
- [x] `P1` 增加失败任务重试、取消、恢复操作
- [x] `P1` 增加任务统计卡片和优先级分布图

### 2.5 阶段验收

- [x] `P0` 高优先级任务优先执行
- [x] `P0` 任务失败后可自动进入重试链路
- [x] `P0` 达到最大重试次数后进入死信队列
- [x] `P0` 前端可查看完整任务生命周期

---

## 阶段 3：监控主链路统一

### 3.1 Trace 与审计统一

- [x] `P0` 统一全系统 `traceId` 传递机制
- [x] `P0` 从 API 网关到后端服务到 Python AI Engine 全链路透传 `traceId`
- [x] `P0` 统一审计日志字段
  - 用户
  - 请求入口
  - provider
  - workflowId / instanceId
  - collaboration packageId
  - 成功失败
  - duration
  - token

### 3.2 Langfuse 接入

- [x] `P0` 为智能体调用接入 Langfuse trace / generation / event
- [x] `P0` 为工作流节点执行接入 Langfuse span
- [x] `P0` 为协作子任务执行接入 Langfuse event 或 span
- [x] `P1` 增加 Langfuse 开关与配置校验
- [x] `P1` 将 Langfuse 错误降级为非阻断型，不影响主流程

### 3.3 Prometheus 与本地 Trace

- [x] `P0` 保留现有 Prometheus 系统监控接口
- [x] `P0` 明确 Prometheus 负责系统层，Langfuse 负责 LLM 链路层，本地 Trace 负责业务归档层
- [x] `P1` 在监控页增加三类监控数据的统一入口

### 3.4 前端监控页

- [x] `P0` 调整监控页信息架构
  - 系统总览
  - 调用链路
  - Token / 成本
  - 延迟
  - 错误分布
- [~] `P1` 增加 Langfuse 深链按钮或嵌入视图入口
- [x] `P1` 增加按 `traceId` 搜索与跳转

### 3.5 阶段验收

- [x] `P0` 一次智能体请求能在系统中完整查到 `traceId`
- [x] `P0` 能查看该请求的审计记录、耗时、Token、错误信息
- [x] `P0` Langfuse 中能看到对应链路数据

---

## 阶段 4：知识库与端侧同步治理

### 4.1 文档状态机

- [x] `P0` 梳理文档生命周期字段
  - 上传中
  - 已上传
  - 解析中
  - 解析失败
  - 待向量化
  - 向量化中
  - 向量化失败
  - 已完成
- [x] `P0` 统一后端对文档状态的更新入口，避免分散写入
- [x] `P1` 为前端显示补齐状态标签、失败原因、最后处理时间

### 4.2 端侧同步接口治理

- [x] `P0` 明确变更查询接口语义
  - 新增
  - 更新
  - 删除
  - 同步确认
- [x] `P0` 规范 checkpoint 的生成和推进逻辑
- [x] `P0` 规范全量导出分页策略与字段结构
- [x] `P0` 文档下载接口补权限校验和审计
- [x] `P1` Webhook 增加失败重试和失效标记

### 4.3 同步前端与管理

- [x] `P1` 客户端同步页面展示
  - 最近同步时间
  - 待同步变更数
  - Webhook 状态
  - 最近失败记录
- [x] `P1` 支持手动触发全量同步和增量同步

### 4.4 阶段验收

- [x] `P0` 首次全量同步可成功导出并下载文档
- [x] `P0` 增量同步可正确处理新增、更新、删除
- [x] `P0` Webhook 可以触发增量同步

---

## 阶段 5：外部框架集成收口

### 5.1 接入模型统一

- [x] `P0` 将 Dify、RAGFlow、MCP、外部框架相关页面按统一能力模型收口
  - 连接测试
  - 资源发现
  - 同步导入
  - 执行调用
- [x] `P1` 清理”只有菜单或展示页、没有真实后端”的伪完成模块

### 5.2 Dify / RAGFlow

- [x] `P0` 校验 Dify 工作流导入导出和知识同步链路
- [x] `P0` 校验 RAGFlow 同步、上传、检索链路
- [x] `P1` 统一配置管理与错误提示

### 5.3 AutoGen / CrewAI / 预留位

- [x] `P1` 明确哪些模块是预留位
- [x` `P1` 对预留位页面添加”未启用/待实现”状态，避免误导

### 5.4 阶段验收

- [x] `P0` 外部集成页面与后端能力一一对应
- [x] `P0` 已支持的集成可真实测试和调用
- [x] `P1` 未支持的集成不会伪装成可用状态

---

## 阶段 6：辅助能力补齐

### 6.1 消息中心

- [x] `P1` 将系统通知、任务异常、告警事件统一到消息中心
- [x] `P1` 支持已读未读、分类筛选、批量标记
- [x] `P2` 支持按用户与系统广播消息区分展示

### 6.2 帮助中心

- [x] `P1` 将帮助中心从静态页面改为读取后端帮助文章接口
- [x] `P1` 支持分类、搜索、启停、阅读计数
- [x] `P2` 增加与当前页面相关的帮助入口

### 6.3 统计分析

- [x] `P1` 统一统计分析数据来源
- [x] `P1` 增加日报、周报、月报导出
- [x] `P2` 增加趋势分析与资源预测能力

### 6.4 系统设置与治理

- [x] `P1` 将模型、通知、监控、同步、网关配置收口到统一系统设置入口
- [x] `P2` 增加配置项生效提示、校验和审计

---

## 技术债与清理项

### 文档与命名清理

- [x] `P1` 清理遗留文档中与当前实现不符的描述
- [x] `P1` 统一前后端模块命名，减少”monitor/runtime/trace/stats”混用
- [x] `P2` 清理废弃或误导性的旧页面入口

### 代码结构清理

- [x] `P1` 清理协作模块中的重复实现与旧接口
- [x] `P1` 清理任务模块中重复的状态定义
- [x] `P2` 将公共状态枚举、事件名、错误码收口到统一位置

### 测试补齐

- [~] `P0` 建立非 Docker CI 基线
  `.github/workflows/ci.yml` 已覆盖 schema baseline、后端 `mvn test`、前端 `npm run lint && npm run test:coverage && npm run build`、AI Engine `compileall && pytest --cov`，main branch protection 已要求这四个 checks 通过。CI 已上传 coverage artifacts 并写入 GitHub Step Summary；Docker compose smoke、Python black/isort、覆盖率 PR 评论仍后续补齐。
- [~] `P0` 收敛本机最小运行闭环
  本机进程模式以 backend、frontend、AI Engine、Redis、MySQL 或等价可达数据库连接为必需基线；`scripts/smoke-test.sh` 已作为最小闭环入口，Docker quickstart 已在干净 volume 下通过真实 runtime smoke 验证。
- [~] `P0` 建立 Docker quickstart 静态预检
  `scripts/check-docker-quickstart.sh` 已覆盖 root compose 服务名、MySQL init 挂载、schema snapshot 边界与关键服务连线；真实 Docker runtime 另已通过 `scripts/docker-smoke.sh` 与 HTTP smoke。
- [x] `P0` 固化 Docker runtime smoke
  `scripts/docker-smoke.sh` 已覆盖 clean volume build/up、六容器 health、后端/AI/前端 HTTP endpoint、Flyway V88/V89/V90、默认 admin 密码/角色、MCP route 与业务 smoke 校验；`.github/workflows/docker-smoke.yml` 已支持手动触发，后续稳定后可考虑纳入必过 checks。
- [x] `P0` 固化 `/v1/mcp` 企业级协议边界测试
  `McpStreamableHttpTest` 已覆盖 CLIENT_ACCESS API Key 鉴权、JWT 隔离、Origin 白名单、`notifications/initialized` 202、JSON-RPC batch 拒绝、owner / `mcpExposed` 隔离、Agent trace/package 返回、Workflow 提交返回与无敏感参数审计。
- [x] `P0` 建立核心业务闭环 smoke baseline
  `scripts/business-smoke.sh` 已覆盖登录、Agent 列表、Workflow 最小 DSL 创建/发布/提交/查询、Workflow trace summary 聚合断言、已完成任务取消/重放保护、失败 Workflow replay、Collaboration 创建/分解/查询、Collaboration trace summary 聚合断言；Agent chat 需显式设置 `ORIN_BUSINESS_SMOKE_AGENT_ID`，设置后会强校验响应不是错误负载且 trace summary 中存在审计或 trace 记录；`ORIN_BUSINESS_SMOKE_WORKFLOW_SUBTASK=1` 会额外验证显式 Workflow 协作子任务经 AI Engine `TaskRuntime` 入队并能在同一 trace summary 中聚合到 workflow task；RabbitMQ 可达时要求 Workflow task 到 `COMPLETED`。
- [x] `P1` Phase 1B：默认运行态闭环与任务队列稳定化
  Root Docker quickstart 已纳入 RabbitMQ，后端 workflow consumer 默认启用；V89 修复 Docker snapshot 路径默认 admin 明文密码/缺角色问题；`docker-smoke.sh` 严格调用 `business-smoke.sh` 验证 Workflow 完整消费。
- [x] `P1` Phase 1C：补齐任务状态一致性、失败恢复与前端调用历史
  Workflow task wire status 固定为 `QUEUED/RUNNING/RETRYING/COMPLETED/FAILED/DEAD/CANCELLED`；取消仅允许 `QUEUED` 并写入 `CANCELLED` 终态，重放仅允许 `FAILED/DEAD` 并创建新 `QUEUED` 任务；前端任务中心与 Workflow 执行页补调用历史、状态徽标、失败重放/排队取消入口。
- [~] `P1` Phase 1D：失败恢复入口与 Trace 聚合
  新增 `GET /api/v1/traces/{traceId}/summary` 脱敏聚合 workflow instance、workflow tasks、collaboration packages、audit logs、trace steps 与 Langfuse link 状态；TraceViewer 可跳转任务/Workflow/协作包；任务中心和 Workflow 执行页补恢复确认、新任务高亮；Workflow 创建请求支持 `retryPolicy.maxRetries` 任务级重试覆盖；协作 Dashboard 补 runtime/diagnostics/events/subtasks 与包级、子任务级人工干预入口；`business-smoke.sh` 已覆盖失败 Workflow replay，并对 Workflow / Collaboration / 可选 Agent Chat 增加 trace summary 端到端断言。
- [~] `P1` Phase 1E：API Key 生命周期与权限治理基线
  平台访问密钥固定 `CLIENT_ACCESS / sk-orin-*` 口径；API Key 创建、禁用、启用、删除、配额重置、轮换写入脱敏审计；前端 API Key 管理页支持状态展示、轮换确认、一次性密钥展示与 MCP 配置复制；`business-smoke.sh` 覆盖临时 key 创建、`/v1/mcp` 成功、禁用后 401 与清理，并已纳入 Docker runtime smoke 验证。后续补角色自助权限、长期配额趋势与限流命中明细。
- [~] `P1` Phase 1F：API Key 调用历史与敏感回显收敛
  新增 `GET /api/v1/api-keys/{keyId}/usage`，基于现有网关审计和业务审计聚合 30 天调用数、成功/失败、失败率、Token、平均耗时与最近调用历史，响应不返回 `requestParams/responseContent`；前端 API Key 管理页新增“历史”弹窗；管理员明文回显新增显式二次确认 `confirmReveal=REVEAL_API_KEY`。
- [~] `P1` 固定 schema snapshot baseline，确保 Docker quickstart 走快照初始化 + 快照之后的 Flyway 迁移
  短期正式口径：`docker/mysql/init/01-orin-schema.sql` 作为 `V1..V87` baseline schema snapshot；后端启动后补跑 `V88` 及之后迁移，当前最高迁移为 `V90`，后续新增 schema 迁移从 `V91` 开始。禁止直接改写已发布迁移，尤其是 `V5/V6/V8/V11/V87/V88/V89/V90`。长期如确实需要空库纯 Flyway 重放，再单独做历史迁移重整。
- [~] `P1` 重构 WorkflowProxyControllerTest，去除对 Milvus/RabbitMQ/Neo4j 等外部依赖
  当前已通过 `@Tag("integration")` 隔离，不再阻塞 CI；后续仍应改为纯单元测试，减少对本机外部服务的依赖
- [x] `P1` 修复 WorkflowServiceTest workflowDslNormalizer 依赖注入缺失
- [~] `P0` 为协作链补充后端单元测试和集成测试
- [x] `P0` 为任务重试/死信逻辑补测试
- [x] `P0` 为同步接口补测试
- [~] `P1` 为前端协作页、任务页、Trace 聚合视图补更完整交互测试
  已补充协作包暂停交互、协作人工干预 API、任务恢复/取消、Trace 聚合加载和前端错误 traceId 提示测试；真实浏览器 E2E 仍待补。

### 开源演示版安全与 MCP 基线

- [x] `P1` README 增加 MCP-Native、CodeQL、gitleaks 状态入口
- [x] `P1` 增加 CodeQL 与 gitleaks GitHub Actions 基线
- [x] `P1` 增加 `scripts/mcp-open-demo-smoke.sh`，覆盖 `/v1/mcp initialize`、`tools/list` 与可选 Agent / Workflow `tools/call`
- [x] `P1` 强化 Claude Desktop / Streamable HTTP MCP 客户端接入文档与排障清单

---

## 里程碑建议

### 里程碑 M1

- [~] 完成阶段 1 与阶段 2
- [~] 协作与任务链路形成真实闭环

### 里程碑 M2

- [x] 完成阶段 3
- [~] 统一监控主链路上线

### 里程碑 M3

- [x] 完成阶段 4 与阶段 5
- [x] 知识同步与外部集成能力收口

### 里程碑 M4

- [x] 完成阶段 6 与技术债清理
- [x] 形成稳定可维护版本

---

## 审查备注（2026-03-26）

- 本次状态按当前代码实装重新校准，不再仅以历史勾选为准
- 协作模块当前结论：后端执行、事件、检查点、人工干预接口已具备基础实现，前端详情已补 runtime、diagnostics、events、subtasks 与人工干预按钮；协作执行链端到端验收仍需继续补
- 任务模块当前结论：队列、重试、死信主链路已落地，任务状态语义已收敛，前端统计、调用历史、失败重放和排队取消入口已对齐
- 监控模块当前结论：Trace、Audit、Langfuse、DataFlow 多套能力并存，前端统一入口、traceId 搜索、脱敏聚合摘要和关联对象跳转已闭环
- 同步模块当前结论：变更查询、检查点、Webhook 治理已具备基础闭环，手动全量/增量同步能力已完成
- 外部集成当前结论：Dify、RAGFlow 为已支持能力；AutoGen、CrewAI 仍为预留位，不应视为已完成
- 测试当前结论：已补仓储/状态类后端测试，协作编排集成测试与前端协作页、任务页交互测试待补充
- 开源演示版增量：Workflow DSL 发布校验已覆盖环、孤岛、不可达节点、无终点路径与非法边引用；MCP 演示脚本与 CodeQL/gitleaks 基线已补；前端错误提示会保留后端 traceId。

---

---

## 本次更新（2026-03-26）

状态更新项目：

- [x] 协作页人工干预功能（包级 pause/resume/cancel/manual-complete；子任务 retry/skip/manual-complete）
- [x] 协作子任务真实调用智能体 / Workflow 执行路径
- [x] 任务状态字段与语义统一
- [x] 任务详情接口字段补齐（触发来源、错误信息、重试时间等）
- [x] 任务统计卡片和优先级分布图
- [x] 前端可查看完整任务生命周期（任务详情对话框）
- [x] 监控页三类监控数据统一入口（本地Trace、Prometheus、Langfuse）
- [x] traceId 搜索与跳转功能
- [~] Langfuse 深链按钮和 Dashboard 入口
- [x] 手动触发全量同步和增量同步功能
- [x] 协作链后端单元测试
- [x] 外部集成页面与后端能力一一对应
- [~] 前端协作页和任务页交互测试（Vitest 单元测试）
- [x] 开源演示版基线（MCP-Native README 入口、Claude Desktop 文档、MCP smoke 脚本、CodeQL/gitleaks workflow、DSL 非法图校验）

---

## 已完成历史事项

- [x] 集成 Milvus 向量引擎，支持知识库的增删改查及向量检索
- [x] 修复知识库删除因 Milvus 不在线导致的挂起和 500 错误
- [x] 修复知识库列表文档计数显示
- [x] 配置 Docker 版 Milvus 并完成联通
- [x] 实现 RAGFlow 知识库集成与多知识库适配
- [x] 实现 Kimi API 集成与多模型统一管理
- [x] 支持视觉理解能力
- [x] 完成阶段 0 文档基线收敛

---

*最后更新: 2026-03-26*
