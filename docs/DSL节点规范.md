# ORIN 工作流 DSL 节点规范

> 定义每个节点的输入、输出和异常格式。用于工作流执行引擎的接口约定。

---

## 协议版本

当前版本：**1.0**

Java 后端在转发 DSL 到 Python 引擎前会自动设置 `dsl.version` 字段。Python 引擎执行前会验证版本号，不匹配时抛出错误。

### 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0 | 2026-03-28 | 初始版本 |

### 版本兼容性

- Java 后端版本常量：`CURRENT_DSL_VERSION = "1.0"`（位于 `WorkflowProxyController.java`）
- Python 引擎支持版本：`SUPPORTED_VERSIONS = ["1.0", "1.1"]`
- 引擎会拒绝不支持的版本，并返回明确的错误信息

---

## 通用说明

### 节点数据结构

```typescript
interface Node {
  id: string;           // 节点唯一标识
  type: string;         // 节点类型
  data: {              // 节点配置数据
    [key: string]: any;
  };
  position?: { x: number, y: number };
}

interface NodeExecutionOutput {
  outputs: { [key: string]: any };  // 输出数据字典
  selected_handle?: string;          // 用于条件分支的选择输出端口
}

interface WorkflowContext {
  inputs: { [key: string]: any };   // 工作流初始输入
  [nodeId: string]: any;            // 各节点输出，以节点ID为key
}
```

### 通用错误处理

- 所有节点的异常通过 `RuntimeError` 抛出
- 异常信息格式：`{NodeType} Error: {具体错误信息}`
- 工作流引擎会捕获异常并设置节点状态为 `FAILED`

---

## 节点类型详解

### 1. Start Node

**DSL 类型**: `start`

**说明**: 工作流入口节点，用于定义初始输入变量。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 无 | - | - | Start节点不需要配置 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| (继承context.inputs) | any | 将inputs中的所有变量透传到上下文 |

**异常**: 无

---

### 2. End Node

**DSL 类型**: `end`

**说明**: 工作流结束节点，用于收集最终输出。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| output_keys | string[] | 否 | 要收集的输出变量名列表 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| (empty) | object | 返回空对象，最终输出由引擎从上下文收集 |

**异常**: 无

---

### 3. LLM Node

**DSL 类型**: `llm`

**说明**: 大语言模型调用节点，使用OpenAI兼容API。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| prompt | string | 是 | 对话提示模板，支持 `{{variable}}` 变量替换 |
| model | string/dict | 否 | 模型名称或模型配置对象，默认 "gpt-3.5-turbo" |
| temperature | float | 否 | 采样温度，默认 0.7 |
| api_key | string | 否 | API密钥，不提供则使用环境变量 |
| base_url | string | 否 | API base URL |
| timeout | float | 否 | 超时时间(秒)，默认60 |
| system_prompt | string | 否 | 系统提示词 |
| prompt_template | array | 否 | Dify风格的提示模板数组 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| text | string | LLM生成的文本响应 |
| model | string | 实际使用的模型 |
| tokens_used | int | 消耗的token数量 |

**异常**:
- `ValueError`: 缺少API Key或Prompt配置
- `RuntimeError`: LLM Provider Error / Rate Limit Exceeded / LLM Connection Failed

---

### 4. Code Node

**DSL 类型**: `code`

**说明**: 执行Python代码片段。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | 是 | 要执行的Python代码 |
| timeout | float | 否 | 执行超时时间(秒)，默认30 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | any | 代码执行返回值 |
| logs | string[] | 代码中的print输出 |

**异常**:
- `RuntimeError`: 代码执行错误（包含行号和错误信息）

---

### 5. Variable Assigner Node

**DSL 类型**: `variable_assigner`

**说明**: 将值赋给指定变量。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| variable_name | string | 是 | 要赋值的变量名 |
| value | any | 是 | 要赋的值 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| variable_name | string | 赋值后的变量名（回传） |
| value | any | 赋的值 |

**异常**: 无

---

### 6. If-Else Node

**DSL 类型**: `if_else`

**说明**: 条件分支节点，根据条件选择输出端口。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| condition | string | 是 | 条件表达式，如 `variable > 0` |
| condition_variable | string | 否 | 用于判断的变量路径 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | boolean | 条件判断结果 |
| selected_handle | string | "true" 或 "false" |

**异常**:
- `RuntimeError`: 条件表达式解析错误

---

### 7. Question Classifier Node

**DSL 类型**: `question_classifier`

**说明**: 使用LLM将问题分类到预定义类别。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query_variable | string | 是 | 要分类的查询变量路径，默认 "inputs.query" |
| classes | array | 是 | 类别列表，格式 `[{id, name}]` |
| model | string | 否 | 使用的模型，默认 "gpt-3.5-turbo" |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| class | string | 分类结果（类别ID） |
| selected_handle | string | 同class，用于路由 |

**异常**:
- `RuntimeError`: LLM调用错误或分类失败

---

### 8. Variable Aggregator Node

**DSL 类型**: `variable_aggregator`

**说明**: 从多个变量中取第一个非空值。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| variables | string[] | 是 | 要检查的变量路径列表 |
| output_variable | string | 否 | 输出变量名，默认 "aggregated_value" |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| aggregated_value | any | 第一个非空变量的值 |

**异常**: 无

---

### 9. Iteration Node

**DSL 类型**: `iteration`

**说明**: 对列表进行迭代处理。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| iterator_variable | string | 是 | 要迭代的列表变量路径 |
| max_concurrency | int | 否 | 最大并发数，默认1（串行） |
| item_variable_name | string | 否 | 迭代项在上下文中的变量名，默认 "iteration_item" |
| output_variable_name | string | 否 | 输出变量名，默认 "iteration_results" |
| iterate_method | string | 否 | 迭代方式，"llm" 或 "transform"，默认 "transform" |
| max_iterations | int | 否 | 最大迭代次数 |
| transform_type | string | 否 | 转换类型（transform模式）：pass_through/uppercase/lowercase/length/to_string |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| iteration_results | array | 迭代结果数组，每项包含 index, input, output, success |
| iteration_results_errors | array | 错误列表 |
| iteration_results_count | int | 成功数量 |
| iteration_results_error_count | int | 错误数量 |
| status | string | completed / completed_with_errors |
| selected_handle | string | completed / partial |

**异常**: 无（错误会记录在errors数组中）

---

### 10. Loop Node

**DSL 类型**: `loop`

**说明**: 循环执行节点，直到条件满足或达到最大次数。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| max_loops | int | 否 | 最大循环次数，默认10 |
| loop_condition | string | 否 | 循环退出条件表达式 |
| loop_variable | string | 否 | 循环计数器变量名 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| results | array | 每次循环的结果 |
| loop_count | int | 实际循环次数 |
| exit_reason | string | exit_condition / max_loops |

**异常**:
- `RuntimeError`: 循环条件解析错误

---

### 11. HTTP Request Node

**DSL 类型**: `http_request`

**说明**: 发送HTTP请求。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | string | 是 | 请求URL |
| method | string | 否 | HTTP方法，默认 "GET" |
| headers | object | 否 | 请求头 |
| params | object | 否 | URL参数 |
| body_type | string | 否 | body类型：none/json/formData/raw，默认 none |
| body_content | string | 否 | 请求体内容 |
| timeout | float | 否 | 超时时间(秒)，默认30 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| status_code | int | HTTP状态码 |
| body | string | 响应体原文 |
| json | object | 解析后的JSON（如果可解析） |
| headers | object | 响应头 |
| is_success | boolean | 是否成功（2xx状态码） |

**异常**:
- `RuntimeError`: 请求失败或超时

---

### 12. List Operator Node

**DSL 类型**: `list_operator`

**说明**: 对列表进行操作（限制/反转/排序）。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| list_variable | string | 是 | 列表变量路径 |
| operation | string | 否 | 操作类型：limit/reverse/sort，默认 limit |
| limit_count | int | 否 | 限制数量（limit模式），默认10 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | array | 操作后的列表 |
| count | int | 结果数量 |

**异常**:
- `RuntimeError`: 变量不是列表类型

---

### 13. Tool Node

**DSL 类型**: `tool`

**说明**: 调用外部工具（占位符）。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tool_name | string | 是 | 工具名称 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| status | string | 工具执行状态 |

**异常**: 无

---

### 14. Answer Node

**DSL 类型**: `answer`

**说明**: 返回最终答案给用户。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| response_variable | string | 否 | 回答内容变量路径 |
| text | string | 否 | 直接指定回答文本 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| text | string | 最终回答文本 |

**异常**: 无

---

### 15. Template Transform Node

**DSL 类型**: `template_transform`

**说明**: 使用Jinja2模板转换变量。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| template | string | 是 | Jinja2模板字符串 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | string | 模板渲染结果 |

**异常**:
- `RuntimeError`: 模板解析错误

---

### 16. Parameter Extractor Node

**DSL 类型**: `parameter_extractor`

**说明**: 使用LLM从文本中提取结构化参数。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| input_variable | string | 否 | 输入文本变量路径，默认 "inputs.query" |
| parameters | array | 是 | 参数定义 `[{name, type, description, required}]` |
| model | string | 否 | 使用的模型，默认 "gpt-3.5-turbo" |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| (由parameters定义) | any | 每个定义的参数及其提取值 |

**异常**:
- `RuntimeError`: LLM调用错误或JSON解析错误

---

### 17. Knowledge Retrieval Node

**DSL 类型**: `knowledge_retrieval`

**说明**: 从知识库检索相关文档。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query_variable | string | 否 | 查询文本变量路径，默认 "inputs.query" |
| knowledge_id | string | 是 | 知识库ID |
| top_k | int | 否 | 返回结果数量，默认5 |
| alpha | float | 否 | 混合搜索权重(0-1)，默认0.7 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | array | 检索结果 `[{content, score, doc_id}]` |
| text | string | 所有结果内容拼接的文本 |
| count | int | 结果数量 |
| query | string | 原始查询 |
| knowledge_id | string | 知识库ID |

**异常**:
- `RuntimeError`: 检索请求失败或超时

---

### 18. Document Extractor Node

**DSL 类型**: `document_extractor`

**说明**: 从文档/文件变量中提取内容。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file_variable | string | 否 | 文件变量路径，默认 "inputs.files" |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| text | string | 提取的文本内容 |
| count | int | 文件数量 |

**异常**: 无

---

### 19. Planner Node (协作)

**DSL 类型**: `planner`

**说明**: 规划子任务。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| task | string | 是 | 任务描述 |
| planning_method | string | 否 | 规划方法 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| subtasks | array | 生成的子任务列表 |
| plan_id | string | 计划ID |

**异常**:
- `RuntimeError`: 规划失败

---

### 20. Delegate Node (协作)

**DSL 类型**: `delegate`

**说明**: 委托任务给其他Agent或工作流。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agent_id | string | 否 | 目标Agent ID |
| workflow_id | string | 否 | 目标工作流 ID |
| task | string | 是 | 任务描述 |
| context | object | 否 | 传递的上下文 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| result | any | 执行结果 |
| status | string | success/failed |

**异常**:
- `RuntimeError`: 委托执行失败

---

### 21. Parallel Fork Node (协作)

**DSL 类型**: `parallel_fork`

**说明**: 并行执行多个分支。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| branches | array | 是 | 并行分支配置 |
| max_concurrency | int | 否 | 最大并发数 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| results | object | 各分支结果，以分支ID为key |
| status | string | completed/partial/failed |

**异常**: 无

---

### 22. Consensus Node (协作)

**DSL 类型**: `consensus`

**说明**: 达成共识（从多个结果中取共识）。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| source_variable | string | 是 | 源结果变量路径 |
| strategy | string | 否 | 共识策略：first/last/llm，默认 first |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| consensus | any | 共识结果 |
| agreed | boolean | 是否达成共识 |

**异常**:
- `RuntimeError`: 共识策略执行失败

---

### 23. Critic Node (协作)

**DSL 类型**: `critic`

**说明**: 审查/评审结果。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| target_variable | string | 是 | 要审查的目标变量路径 |
| criteria | array | 否 | 审查标准 |
| model | string | 否 | 使用的模型 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| passed | boolean | 是否通过审查 |
| feedback | string | 审查反馈 |
| suggestions | array | 改进建议 |

**异常**:
- `RuntimeError`: 审查失败

---

### 24. Memory Read Node (协作)

**DSL 类型**: `memory_read`

**说明**: 从共享存储读取记忆。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| memory_key | string | 是 | 记忆键 |
| memory_type | string | 否 | 记忆类型 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| memory | any | 读取的记忆内容 |

**异常**:
- `RuntimeError`: 读取失败

---

### 25. Memory Write Node (协作)

**DSL 类型**: `memory_write`

**说明**: 写入共享存储记忆。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| memory_key | string | 是 | 记忆键 |
| memory_value | any | 是 | 要写入的值 |
| memory_type | string | 否 | 记忆类型 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否成功 |

**异常**:
- `RuntimeError`: 写入失败

---

### 26. Event Emit Node (协作)

**DSL 类型**: `event_emit`

**说明**: 发送事件。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| event_name | string | 是 | 事件名称 |
| event_data | any | 否 | 事件数据 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|------|
| event_id | string | 事件ID |
| emitted | boolean | 是否发送成功 |

**异常**: 无

---

### 27. Event Listen Node (协作)

**DSL 类型**: `event_listen`

**说明**: 监听事件。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| event_name | string | 是 | 要监听的事件名称 |
| timeout | float | 否 | 超时时间(秒) |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|------|
| event_data | any | 收到的事件数据 |
| received | boolean | 是否收到事件 |

**异常**:
- `RuntimeError`: 监听失败

---

### 28. Retry Policy Node (协作)

**DSL 类型**: `retry_policy`

**说明**: 配置重试策略。

**输入 (node.data)**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| max_retries | int | 否 | 最大重试次数，默认3 |
| retry_interval | float | 否 | 重试间隔(秒)，默认1 |
| backoff_multiplier | float | 否 | 退避倍数，默认2 |

**输出 (outputs)**:
| 字段 | 类型 | 说明 |
|------|------|------|------|
| policy_id | string | 策略ID |
| configured | boolean | 是否配置成功 |

**异常**: 无

---

## 附录 A：统一协作数据结构 (C1.1)

> 定义多智能体协作的核心数据结构，用于统一任务管理、上下文传递、共享内存、重试和回滚机制。

### A.1 枚举定义

#### TaskStatus - 任务状态
| 值 | 说明 |
|---|---|
| `pending` | 等待执行 |
| `running` | 执行中 |
| `success` | 执行成功 |
| `failed` | 执行失败 |
| `retrying` | 重试中 |
| `rolled_back` | 已回滚 |

#### RetryStrategy - 重试策略
| 值 | 说明 |
|---|---|
| `fixed` | 固定延迟 |
| `linear` | 线性递增 |
| `exponential` | 指数退避 |
| `exponential_capped` | 指数退避（带上限） |

#### TaskType - 任务类型
| 值 | 说明 |
|---|---|
| `planning` | 任务分解规划 |
| `execution` | 单智能体执行 |
| `parallel` | 并行执行 |
| `critique` | 审查评审 |
| `consensus` | 多智能体共识 |
| `rollback` | 回滚任务 |

---

### A.2 Task - 任务定义

顶层协作工作单元。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | string | 是 | 唯一任务标识 |
| type | TaskType | 是 | 任务类型分类 |
| description | string | 是 | 人类可读的任务描述 |
| parent_id | string | 否 | 父任务ID（层级任务） |
| timeout | float | 否 | 超时时间（秒），默认300 |
| max_retries | int | 否 | 最大重试次数，默认3 |
| retry_strategy | RetryStrategy | 否 | 重试策略，默认exponential_capped |
| retry_delay | float | 否 | 初始重试延迟（秒），默认1.0 |
| retry_multiplier | float | 否 | 退避倍数，默认2.0 |
| depends_on | string[] | 否 | 依赖的任务ID列表 |
| required_roles | string[] | 否 | 需要的智能体角色列表 |
| input_schema | object | 否 | 期望的输入结构 |
| output_schema | object | 否 | 期望的输出结构 |
| priority | int | 否 | 优先级，数值越高越优先 |
| metadata | object | 否 | 附加元数据 |

**示例**:
```json
{
  "id": "task_001",
  "type": "execution",
  "description": "Analyze user query and generate response",
  "timeout": 60.0,
  "max_retries": 3,
  "retry_strategy": "exponential_capped",
  "depends_on": [],
  "required_roles": ["analyst"]
}
```

---

### A.3 Subtask - 子任务定义

分配给特定智能体的工作单元。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | string | 是 | 唯一子任务标识 |
| task_id | string | 是 | 父任务ID |
| sequence | int | 是 | 执行顺序号 |
| description | string | 是 | 子任务描述 |
| assigned_role | string | 是 | 分配的角色 |
| agent_id | string | 否 | 预分配的智能体ID |
| input_data | object | 否 | 子任务输入数据 |
| prompt_template | string | 否 | 可选的提示词模板 |
| checkpoint_id | string | 否 | 失败时恢复的检查点ID |
| result | SubtaskResult | 否 | 执行后填充的结果 |
| status | TaskStatus | 否 | 状态，默认pending |
| started_at | float | 否 | 开始时间戳 |
| completed_at | float | 否 | 完成时间戳 |

---

### A.4 SubtaskResult - 子任务结果

子任务执行的标准化结果。

| 字段 | 类型 | 说明 |
|---|---|---|
| subtask_id | string | 子任务标识 |
| status | TaskStatus | 执行状态 |
| output | any | 子任务输出 |
| error | string | 失败时的错误信息 |
| execution_time | float | 执行耗时（秒） |
| retries_used | int | 使用的重试次数 |
| trace_id | string | 关联的追踪ID |
| checkpoint_snapshot | object | 状态快照（用于回滚） |

---

### A.5 SharedMemory - 共享内存

跨智能体共享状态的结构化接口。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| memory_id | string | 是 | 唯一内存标识 |
| memory_type | string | 是 | 类型：short_term/long_term/working |
| key | string | 是 | 内存键 |
| value | any | 是 | 内存值 |
| content_schema | object | 否 | 期望的内容结构 |
| owner_task_id | string | 否 | 拥有此内存的任务ID |
| readable_by | string[] | 否 | 可读取的角色/智能体列表 |
| writable_by | string[] | 否 | 可写入的角色/智能体列表 |
| ttl | float | 否 | 生存时间（秒） |
| created_at | float | 否 | 创建时间戳 |
| updated_at | float | 否 | 更新时间戳 |
| expires_at | float | 否 | 过期时间戳 |
| version | int | 否 | 版本号（乐观锁） |

---

### A.6 CollaborationResult - 协作结果

协作操作结果的统一包装器。

| 字段 | 类型 | 说明 |
|---|---|---|
| collaboration_id | string | 协作会话唯一ID |
| task_id | string | 关联的任务ID |
| status | TaskStatus | 执行状态 |
| success | boolean | status == SUCCESS 的快捷方式 |
| primary_result | any | 主结果输出 |
| subtask_results | SubtaskResult[] | 所有子任务结果列表 |
| total_subtasks | int | 子任务总数 |
| successful_subtasks | int | 成功的子任务数 |
| failed_subtasks | int | 失败的子任务数 |
| started_at | float | 开始时间戳 |
| completed_at | float | 完成时间戳 |
| total_duration | float | 总执行时间（秒） |
| errors | string[] | 遇到的所有错误列表 |
| fatal_error | string | 导致完全失败的错误 |
| memory_writes | SharedMemory[] | 内存修改记录 |
| rolled_back | boolean | 是否已回滚 |
| rollback_reason | string | 回滚原因 |

---

### A.7 RetryPolicy - 重试策略

协作任务的重试配置。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| policy_id | string | 是 | 唯一策略标识 |
| max_retries | int | 否 | 最大重试次数，默认3 |
| strategy | RetryStrategy | 否 | 重试策略，默认exponential_capped |
| initial_delay | float | 否 | 初始延迟（秒），默认1.0 |
| max_delay | float | 否 | 最大延迟上限，默认60.0 |
| multiplier | float | 否 | 退避倍数，默认2.0 |
| enable_jitter | boolean | 否 | 是否启用抖动（避免雷鸣般群发），默认true |
| jitter_factor | float | 否 | 抖动比例，默认0.1 |
| retryable_errors | string[] | 否 | 会触发重试的错误列表 |
| non_retryable_errors | string[] | 否 | 跳过重试的错误列表 |
| shared_budget | boolean | 否 | 重试次数是否跨分支共享，默认false |
| budget_key | string | 否 | 共享预算跟踪的键 |

**延迟计算方法**:
- `fixed`: delay = initial_delay
- `linear`: delay = initial_delay * (attempt + 1)
- `exponential`: delay = initial_delay * (multiplier ^ attempt)
- `exponential_capped`: delay = min(initial_delay * (multiplier ^ attempt), max_delay)

---

### A.8 RollbackAction - 回滚操作

协作回滚机制的操作定义。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| action_id | string | 是 | 唯一操作标识 |
| task_id | string | 是 | 所属任务ID |
| action_type | string | 是 | 类型：restore_checkpoint/compensate/notify |
| target_memory_key | string | 否 | 要恢复的内存键 |
| target_checkpoint_id | string | 否 | 要恢复的检查点ID |
| target_agent_id | string | 否 | 要通知的智能体ID |
| compensation_data | object | 否 | 补偿操作的数据 |
| executed | boolean | 否 | 是否已执行，默认false |
| executed_at | float | 否 | 执行时间戳 |
| result | any | 否 | 执行结果 |

---

### A.9 Checkpoint - 检查点

用于保存和恢复协作状态的快照。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| checkpoint_id | string | 是 | 唯一检查点标识 |
| task_id | string | 是 | 关联的任务ID |
| subtask_id | string | 否 | 关联的子任务ID |
| context_snapshot | object | 否 | 完整上下文状态快照 |
| memory_snapshot | SharedMemory[] | 否 | 内存状态快照 |
| output_snapshot | object | 否 | 检查点时的节点输出 |
| created_at | float | 是 | 创建时间戳 |
| description | string | 否 | 人类可读的描述 |
| tags | string[] | 否 | 标签列表 |

---

### A.10 CollaborationContext - 协作上下文

贯穿整个协作工作流执行的统一上下文。

| 字段 | 类型 | 说明 |
|---|---|---|
| collaboration_id | string | 协作会话唯一ID |
| trace_id | string | 分布式追踪ID |
| root_task_id | string | 嵌套协作的根任务ID |
| current_task | Task | 当前执行的任务 |
| task_queue | Task[] | 等待执行的任务队列 |
| subtasks | object | 所有子任务，键为子任务ID |
| shared_memory | object | 共享内存，键为内存键 |
| checkpoints | object | 检查点，键为检查点ID |
| rollback_stack | RollbackAction[] | 回滚操作栈 |
| retry_budgets | object | 重试预算，键为任务ID |
| results | object | 结果聚合，键为协作ID |
| config | object | 配置参数 |
| started_at | float | 开始时间戳 |
| last_updated_at | float | 最后更新时间戳 |

---

## 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-03-28 | 初始版本，定义所有节点类型 |
| 1.1 | 2026-03-28 | 附录A：增加统一协作数据结构定义（C1.1） |

---

## 注意事项

1. **变量路径格式**: 使用点号分隔，如 `inputs.query`、`node_1.output`
2. **模板变量格式**: 使用 `{{variable}}` 或 `{variable}`，如 `{{inputs.query}}`
3. **selected_handle**: 用于If-Else、QuestionClassifier等条件节点，决定后续路由
4. **错误处理**: 节点内部捕获的异常应重新抛出RuntimeError，并包含有意义的错误信息
5. **协作数据结构**: 协作相关结构定义在 `app/models/collaboration.py`，主工作流模型在 `app/models/workflow.py`
