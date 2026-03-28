# ORIN Workflow 典型样例

> 本文档记录 3 个典型 Workflow 的固定输入输出样例，用于验收和 smoke 测试。
> 样例基于真实代码实现，节点类型与 `GraphExecutor` 和各 `NodeHandler` 对应。

---

## 样例 1：简单 LLM 聊天流程

**用途**：验证 LLM 节点基本调用能力

### 节点拓扑

```
Start --> LLM --> End
```

### Graph DSL

```json
{
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "data": {}
    },
    {
      "id": "llm_node",
      "type": "llm",
      "data": {
        "model": "deepseek-chat",
        "prompt": "请回复: {{query}}"
      }
    },
    {
      "id": "end",
      "type": "end",
      "data": {}
    }
  ],
  "edges": [
    {"source": "start", "target": "llm_node"},
    {"source": "llm_node", "target": "end"}
  ]
}
```

### 输入 (initialContext)

```json
{
  "query": "你好",
  "model": "deepseek-chat"
}
```

### 各节点输出

| 节点 | 输出 |
|------|------|
| start | `{}` (透传输入) |
| llm_node | `{"text": "你好！很高兴见到你。", "output": "你好！很高兴见到你。"}` |
| end | 继承全局 context |

### 最终结果

```json
{
  "success": true,
  "context": {
    "query": "你好",
    "model": "deepseek-chat",
    "llm_node": {
      "text": "你好！很高兴见到你。",
      "output": "你好！很高兴见到你。"
    }
  }
}
```

### 验收检查点

- [ ] Start 节点正常透传输入到 context
- [ ] LLM 节点正确调用 `DeepSeekIntegrationService`
- [ ] LLM 节点输出包含 `text` 和 `output` 字段
- [ ] End 节点正确收集最终输出
- [ ] 执行成功 `success: true`

---

## 样例 2：知识检索 + LLM 问答

**用途**：验证知识库检索节点和串行节点协作

### 节点拓扑

```
Start --> Knowledge Retrieval --> LLM --> End
```

### Graph DSL

```json
{
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "data": {}
    },
    {
      "id": "knowledge_node",
      "type": "knowledge-retrieval",
      "data": {
        "query_variable": "query",
        "knowledge_id": "{{kb_id}}",
        "top_k": 4,
        "alpha": 0.7
      }
    },
    {
      "id": "llm_node",
      "type": "llm",
      "data": {
        "model": "deepseek-chat",
        "prompt": "基于以下上下文回答问题。\n\n上下文：{{knowledge_node.output}}\n\n问题：{{query}}"
      }
    },
    {
      "id": "end",
      "type": "end",
      "data": {}
    }
  ],
  "edges": [
    {"source": "start", "target": "knowledge_node"},
    {"source": "knowledge_node", "target": "llm_node"},
    {"source": "llm_node", "target": "end"}
  ]
}
```

### 输入 (initialContext)

```json
{
  "query": "ORIN系统支持哪些功能？",
  "kb_id": "kb_001",
  "dataset_ids": ["kb_001"]
}
```

### 各节点输出

| 节点 | 输出 |
|------|------|
| start | `{}` (透传输入) |
| knowledge_node | `{"result": [{"content": "...", "score": 0.95, "metadata": {...}}], "output": "ORIN是一个AI智能体平台...\n\n支持知识库管理...\n", "count": 2}` |
| llm_node | `{"text": "基于检索结果，ORIN系统支持：1. 知识库管理 2. 智能体编排 3. 工作流自动化...", "output": "..."}` |
| end | 继承全局 context |

### 最终结果

```json
{
  "success": true,
  "context": {
    "query": "ORIN系统支持哪些功能？",
    "kb_id": "kb_001",
    "dataset_ids": ["kb_001"],
    "knowledge_node": {
      "result": [...],
      "output": "ORIN是一个AI智能体平台...\n\n支持知识库管理...\n",
      "count": 2
    },
    "llm_node": {
      "text": "基于检索结果，ORIN系统支持：...",
      "output": "..."
    }
  }
}
```

### 验收检查点

- [ ] Knowledge Retrieval 节点正确调用 `RetrievalService.hybridSearch()`
- [ ] Knowledge Retrieval 节点输出包含 `result`、`output`、`count` 字段
- [ ] LLM 节点能正确引用前置节点的输出 `{{knowledge_node.output}}`
- [ ] 变量在 context 中正确传递
- [ ] 串行执行顺序正确（先知识检索，再 LLM 生成）

---

## 样例 3：If-Else 分支 + Agent/Skill 调用

**用途**：验证条件分支和分支选择能力

### 节点拓扑

```
         --> [if] Agent --> End
Start -->|
         --> [else] Skill --> End
```

### Graph DSL

```json
{
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "data": {}
    },
    {
      "id": "decision",
      "type": "if-else",
      "data": {
        "condition": "{{condition_result}}"
      }
    },
    {
      "id": "agent_node",
      "type": "agent",
      "data": {
        "agentId": 1
      }
    },
    {
      "id": "skill_node",
      "type": "skill",
      "data": {
        "skillId": 1
      }
    },
    {
      "id": "end",
      "type": "end",
      "data": {}
    }
  ],
  "edges": [
    {"source": "start", "target": "decision"},
    {"source": "decision", "target": "agent_node", "sourceHandle": "if"},
    {"source": "decision", "target": "skill_node", "sourceHandle": "else"},
    {"source": "agent_node", "target": "end"},
    {"source": "skill_node", "target": "end"}
  ]
}
```

### 输入 (initialContext) - True 分支

```json
{
  "query": "帮我查询今天的天气",
  "condition_result": true,
  "agentId": 1
}
```

### True 分支执行路径

| 步骤 | 节点 | selected_handle | 说明 |
|------|------|-----------------|------|
| 1 | decision | `if` | condition_result=true，走 if 分支 |
| 2 | agent_node | - | agent_node 执行 |
| 3 | skill_node | - | **跳过** (未选中分支) |
| 4 | end | - | 收集 agent_node 输出 |

### 各节点输出 (True 分支)

| 节点 | 输出 |
|------|------|
| start | `{}` |
| decision | `{"result": true}`, selected_handle = `"if"` |
| agent_node | `{"response": "今天北京天气晴朗，气温15-22度。"}` |
| skill_node | 无输出 (SKIPPED) |
| end | 继承全局 context |

### 最终结果 (True 分支)

```json
{
  "success": true,
  "context": {
    "query": "帮我查询今天的天气",
    "condition_result": true,
    "agentId": 1,
    "decision": {"result": true},
    "agent_node": {"response": "今天北京天气晴朗，气温15-22度。"}
  }
}
```

### 输入 (initialContext) - False 分支

```json
{
  "query": "帮我执行某个技能",
  "condition_result": false,
  "skillId": 1
}
```

### 各节点输出 (False 分支)

| 节点 | 输出 |
|------|------|
| start | `{}` |
| decision | `{"result": false}`, selected_handle = `"else"` |
| agent_node | 无输出 (SKIPPED) |
| skill_node | `{"result": "技能执行完成"}` |
| end | 继承全局 context |

### 验收检查点

- [ ] IfElse 节点正确评估 `condition_result`
- [ ] IfElse 节点正确设置 `selected_handle`（`if` 或 `else`）
- [ ] 选中分支的节点正常执行
- [ ] 未选中分支的节点状态为 `SKIPPED`，不写入 context
- [ ] End 节点正确收集最终输出
- [ ] Both 分支路径都能成功完成

---

## 附录：节点类型映射

| DSL type | Handler Bean | 实现类 |
|----------|--------------|--------|
| `start` | `startNodeHandler` | `StartNodeHandler` |
| `end` | `endNodeHandler` | `EndNodeHandler` |
| `llm` | `llmNodeHandler` | `LLMNodeHandler` |
| `agent` | `agentNodeHandler` | `AgentNodeHandler` |
| `skill` | `skillNodeHandler` | `SkillNodeHandler` |
| `if-else` | `ifElseNodeHandler` | `IfElseNodeHandler` |
| `knowledge-retrieval` | `knowledgeNodeHandler` | `KnowledgeNodeHandler` |
| `code` | `codeNodeHandler` | `CodeNodeHandler` |
| `variable-assigner` | `variableAssignerNodeHandler` | `VariableAssignerNodeHandler` |

---

## 附录：运行方式

### 后端单元测试

```bash
cd orin-backend
# 运行 GraphExecutor 相关测试
./mvnw test -Dtest=GraphExecutorTest

# 运行 WorkflowService 相关测试
./mvnw test -Dtest=WorkflowServiceTest
```

### 手动触发 Workflow

通过 `WorkflowController` 或 `WorkflowProxyController` 提交 DSL 和初始输入：

```
POST /api/workflow/execute
Content-Type: application/json

{
  "graphDefinition": { ... },
  "initialContext": { ... }
}
```

---

## 更新记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-28 | 1.0 | 初始版本，3个典型样例 |
