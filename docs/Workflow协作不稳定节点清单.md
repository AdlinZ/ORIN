# Workflow / Collaboration 不稳定节点与限制清单 (E3.4)

> 本文档记录 Workflow 和 Collaboration 模块中当前不支持或不稳定的节点、功能和边界限制。
> 基于代码审查，标注每个问题的现状和风险等级。

---

## 一、Workflow 节点状态

### 完全支持的节点（可用）

| 节点类型 | DSL type | Handler | 状态 | 说明 |
|----------|----------|---------|------|------|
| Start | `start` | `StartNodeHandler` | ✅ 稳定 | 透传输入，无实际逻辑 |
| End | `end` | `EndNodeHandler` | ✅ 稳定 | 收集最终输出 |
| LLM | `llm` | `LLMNodeHandler` | ✅ 稳定 | 调用 DeepSeek 服务 |
| Agent | `agent` | `AgentNodeHandler` | ✅ 稳定 | 调用 AgentExecutor |
| Skill | `skill` | `SkillNodeHandler` | ✅ 稳定 | 调用 SkillService |
| If-Else | `if-else` | `IfElseNodeHandler` | ✅ 稳定 | 条件分支路由 |
| Knowledge Retrieval | `knowledge-retrieval` | `KnowledgeNodeHandler` | ✅ 稳定 | 混合检索 |

### 模拟实现的节点（不完整）

| 节点类型 | Handler | 现状 | 风险 |
|----------|---------|------|------|
| **Code** | `CodeNodeHandler` | 仅返回模拟结果，不执行代码 | ⚠️ 高 - 功能未实现 |
| **Variable Assigner** | `VariableAssignerNodeHandler` | 仅更新 context 值，不处理表达式 | ⚠️ 中 - 变量解析不完整 |

### 未实现的节点（不可用）

| 节点类型 | DSL type | 状态 | 说明 |
|----------|----------|------|------|
| HTTP Request | `http_request` | ❌ 未实现 | 无对应 Handler |
| Iteration | `iteration` | ❌ 未实现 | 无对应 Handler |
| Loop | `loop` | ❌ 未实现 | 无对应 Handler |
| Question Classifier | `question_classifier` | ❌ 未实现 | 无对应 Handler |
| Template Transform | `template_transform` | ❌ 未实现 | 无对应 Handler |
| Parameter Extractor | `parameter_extractor` | ❌ 未实现 | 无对应 Handler |
| Document Extractor | `document_extractor` | ❌ 未实现 | 无对应 Handler |
| Tool | `tool` | ❌ 未实现 | 无对应 Handler |
| Answer | `answer` | ❌ 未实现 | 无对应 Handler |
| List Operator | `list_operator` | ❌ 未实现 | 无对应 Handler |
| Retry Policy | `retry_policy` | ❌ 未实现 | 无对应 Handler |

### 协作相关节点（未在 Workflow 引擎中实现）

| 节点类型 | DSL type | 状态 | 说明 |
|----------|----------|------|------|
| Planner | `planner` | ❌ 未实现 | Collaboration 层有逻辑，但未接入 Workflow |
| Delegate | `delegate` | ❌ 未实现 | Collaboration 层有逻辑，但未接入 Workflow |
| Parallel Fork | `parallel_fork` | ❌ 未实现 | Collaboration 有并行执行，但未作为节点暴露 |
| Consensus | `consensus` | ❌ 未实现 | Collaboration 有共识逻辑，但未作为节点暴露 |
| Critic | `critic` | ❌ 未实现 | Collaboration 有评审逻辑，但未作为节点暴露 |
| Memory Read | `memory_read` | ❌ 未实现 | Collaboration 有记忆服务，但未作为节点暴露 |
| Memory Write | `memory_write` | ❌ 未实现 | Collaboration 有记忆服务，但未作为节点暴露 |
| Event Emit | `event_emit` | ❌ 未实现 | Collaboration 有事件总线，但未作为节点暴露 |
| Event Listen | `event_listen` | ❌ 未实现 | Collaboration 有事件总线，但未作为节点暴露 |

---

## 二、Workflow 引擎限制

### 变量解析不完整

**问题**：`GraphExecutor` 中 `context` 作为全局状态共享，但节点间的变量引用（如 `{{nodeId.output}}`）未在 `GraphExecutor` 层实现解析。

**现状**：
- `LLMNodeHandler` 直接从 `context` 取 `prompt` 字段
- `KnowledgeNodeHandler` 直接从 `context` 取 `query`、`dataset_ids` 字段
- 节点输出通过 `context.put(nodeId, result.getOutputs())` 写入，但依赖节点通过 `context.get(nodeId)` 获取

**风险**：⚠️ 中 - 复杂变量表达式（如 `{{knowledge_node.output}}`）可能无法正确解析

### If-Else Handler 条件评估简陋

**代码**：`IfElseNodeHandler.java:42-44`
```java
String condition = (String) nodeData.get("condition"); // e.g. "true"
conditionMet = "true".equalsIgnoreCase(condition);
```

**问题**：仅支持 `"true"`/`"false"` 字符串判断，不支持复杂表达式

**风险**：⚠️ 高 - 实际生产环境中的条件表达式无法使用

### 并行执行实际为串行模拟

**代码**：`GraphExecutor.java:36`
```java
private final ExecutorService executor = Executors.newCachedThreadPool();
```

**问题**：虽然使用了 `CompletableFuture` 和线程池，但节点间的依赖等待机制（`allOf` + `join`）导致实际执行是串行的

**风险**：⚠️ 中 - 并行节点需要所有前置节点完成才能执行下一个

### 执行超时固定为 300 秒

**代码**：`GraphExecutor.java:116`
```java
allFutures.get(300, TimeUnit.SECONDS);
```

**问题**：超时时间硬编码，无法配置

**风险**：⚠️ 低 - 长任务可能意外超时

---

## 三、Collaboration 限制

### Workflow 执行未集成

**代码**：`CollaborationExecutor.java:196-221`
```java
// executeWithWorkflow - TODO: 集成工作流执行服务
private CompletableFuture<String> executeWithWorkflow(...) {
    future.complete("Workflow completed");  // 直接返回模拟结果
}
```

**问题**：协作中的子任务类型为 `WORKFLOW` 时，返回模拟结果而非真实执行

**风险**：⚠️ 高 - 无法在协作中使用工作流子任务

### 人工任务未实现

**代码**：`CollaborationExecutor.java:226-235`
```java
// executeHumanTask - 人工任务占位
private CompletableFuture<String> executeHumanTask(...) {
    // 返回一个等待人工完成的 future - 永远不完成
    return future;
}
```

**问题**：人工任务类型直接返回未完成的 `CompletableFuture`，无法触发人工介入

**风险**：⚠️ 高 - 人工审批/干预场景无法使用

### 子任务分配策略简单

**代码**：`CollaborationExecutor.java:81-83`
```java
// 选择第一个可用的 Agent（实际场景中应根据 capability 匹配）
AgentMetadata selectedAgent = agents.get(0);
```

**问题**：总是选择第一个 Agent，不支持根据角色/capability 匹配

**风险**：⚠️ 中 - 多角色协作时无法正确分配

### Agent 响应为空时的处理

**代码**：`CollaborationExecutor.java:107-113`
```java
if (response.isPresent()) {
    ...
} else {
    String errorResult = "Agent execution returned empty response";
    log.warn(errorResult);
    future.complete(errorResult);  // 返回错误字符串而非抛出异常
}
```

**问题**：Agent 空响应被当作成功结果返回，而非失败

**风险**：⚠️ 中 - 上游无法区分"空结果成功"和"执行失败"

### 依赖检查不处理循环依赖

**代码**：`CollaborationOrchestrator.java:248-254`
```java
return dependsOn.stream().allMatch(depId ->
    subtasks.stream()
        .filter(s -> s.getSubTaskId().equals(depId))
        .anyMatch(s -> "COMPLETED".equals(s.getStatus()))
);
```

**问题**：只检查前置任务是否完成，不检测循环依赖

**风险**：⚠️ 低 - 配置错误时可能死锁

---

## 四、已知不稳定功能汇总

| 功能 | 模块 | 风险等级 | 说明 |
|------|------|---------|------|
| Code 节点 | Workflow | 🔴 高 | 代码执行未实现 |
| If-Else 条件表达式 | Workflow | 🔴 高 | 仅支持 true/false |
| Workflow 子任务 | Collaboration | 🔴 高 | 未集成，直接返回模拟 |
| 人工任务 | Collaboration | 🔴 高 | 占位符，无法完成 |
| 变量解析 | Workflow | 🟡 中 | 复杂表达式不支持 |
| Agent 选择策略 | Collaboration | 🟡 中 | 总是选第一个 |
| 空响应处理 | Collaboration | 🟡 中 | 空结果当作成功 |
| 并行执行 | Workflow | 🟡 中 | 实际为串行模拟 |
| 超时配置 | Workflow | 🟢 低 | 硬编码 300s |
| 循环依赖检测 | Collaboration | 🟢 低 | 未实现 |

---

## 五、修复优先级建议

### P0（影响核心流程，修复后方可交付）

1. **If-Else 条件表达式** - 升级为支持 SpEL 或简单表达式求值
2. **Workflow 子任务集成** - 实现 `executeWithWorkflow()` 调用真实工作流服务
3. **人工任务完成机制** - 实现人工任务回调或轮询机制

### P1（功能不完整但有 workaround）

4. **Code 节点** - 要么实现沙箱执行，要么从 DSL 规范中移除
5. **Agent 选择策略** - 根据 expectedRole 匹配 agent capability
6. **空响应处理** - 区分"成功但无结果"和"执行失败"

### P2（体验优化）

7. **并行执行真并行** - 优化依赖检查逻辑，支持真正并行
8. **超时可配置** - 从 Strategy 配置读取
9. **循环依赖检测** - 增加前置检测

---

## 更新记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-28 | 1.0 | 初始版本，记录不支持和不稳定的节点清单 |
