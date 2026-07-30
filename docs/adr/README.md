# ORIN 架构决策记录（ADR）

> 本目录存放 ORIN vNext 阶段的架构决策记录（Architecture Decision Record）。
> 每条 ADR 描述一次重要的技术或产品决策、备选方案与代价，让后续的开发者在改动相关领域前可以一眼看清“当时为什么这样定”。

## 维护规则

1. **决策必须可执行**：写“已经定了什么”，而不是“计划考虑什么”；尚未被采纳的提案放在状态 `Proposed`；被否决的提案把状态标 `Rejected` 并保留备选方案分析。
2. **编号不重用**：一条 ADR 一个编号；撤销一条 ADR 写一条新的 ADR 说明“被 ADR-xxx 取代”，不删除原文件。
3. **状态机**：`Proposed` → `Accepted` → `Superseded by ADR-xxx`，或 `Rejected`。状态写在 frontmatter 的 `status:` 字段。
4. **Frontmatter 是单一真相**：标题、状态、日期、提议人、关联文档**只**写在文件顶部的 YAML frontmatter，不在正文 H1/H2 重复；正文从“状态说明”或“背景”开始。
5. **语言**：与仓库一致——本文档以中文为主；术语和命令以英文/代码为准。
6. **同步要求**：当 ADR 改变架构、接口或产品边界时，必须同步更新：
   - `docs/架构设计.md`
   - `docs/Runner架构设计.md`（如涉及 Runner）
   - `docs/API文档.md`（如涉及接口）
   - `docs/开发规范.md`（如涉及硬约束）
   - 对应 TODO 条目（如涉及路线图）
7. **TODO 跟踪前置**：ADR “开放问题”中的每一项必须显式落到 TODO 与 PR；只写进 ADR 而未落到 TODO 不视为已闭环。状态保持 `Proposed` 时不得推进依赖实现。
8. **可回看**：每条 ADR 必须包含“日期 + 提议人/团队 + 关联 PR/TODO”。
9. **不得伪造后续**：ADR 只描述决策及其背景；实现进度由代码、测试和 PR 体现，不在 ADR 内重复。

## 文件命名

```text
NNN-short-slug.md
```

- `NNN`：三位数编号，从 `001` 起递增，**不重用**；
- `short-slug`：小写、中划线分隔的英文短语，与 `title:` 主题一致；
- 文件名不带 `adr-` 前缀，避免与目录名重复；
- 唯一链接约定：仓库内引用使用相对路径 `docs/adr/NNN-slug.md`，**禁止**使用诸如 `docs/../TODO.md` 的越级相对路径。

## Frontmatter 模板

每条 ADR 必须在文件顶部拥有 YAML frontmatter：

```yaml
---
slug: NNN-short-slug
title: ADR-NNN · 标题
status: Proposed | Accepted | Superseded | Rejected
date: YYYY-MM-DD
deciders: 人/团队
supersedes: []
related:
  - docs/相关文档.md
  - TODO.md (分组)
---
```

正文最小化结构：

```markdown
> 状态：与 frontmatter `status` 一致并解释当前阶段。

## 1. 背景（Context）

## 2. 决策（Decision）

## 3. 后果（Consequences）

## 4. 备选方案（Alternatives considered）

## 5. 待办与开放问题（Open questions）

## 6. 与其他 ADR / TODO 的关系（可选）

## 7. 验收条件（Acceptance criteria）
```

## 当前 ADR 列表

| 编号 | 标题 | 状态 | 日期 |
| --- | --- | --- | --- |
| [ADR-001](./001-runner-dispatch-and-lease.md) | Runner dispatch、lease 与现有 RabbitMQ/mq_worker 的关系 | Accepted | 2026-07-19 |
| [ADR-002](./002-agent-version-immutability-and-secret-reference.md) | AgentVersion 不可变规则与 SecretReference | Accepted | 2026-07-19 |
| [ADR-003](./003-endpoint-response-contract.md) | Endpoint 同步 / 流式 / 异步响应契约 | Accepted | 2026-07-25 |

## 队列中的 ADR

下列 ADR 在 [TODO.md](../../TODO.md) P0 中挂名；ADR-001 / ADR-002 / ADR-003 已 Accepted。
