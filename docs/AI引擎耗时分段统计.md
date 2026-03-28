# AI 引擎执行链耗时分段统计

> G1.3: 为 AI 引擎执行链补基础耗时分段统计

---

## 当前已有的统计

AI 引擎已在以下位置记录耗时：

### 协作执行器 (collaboration_executor.py)

```python
# 任务级别
start_time = time.time()
duration = time.time() - start_time

# 返回结构
{
    "start_time": 1774662000.0,
    "duration": 1.234,
    "subtasks": [...]
}
```

### 工作流执行器 (executor.py)

```python
# 节点级别
start_time=time.time(),
end_time=time.time(),
duration=0.0

# NodeTrace 包含
- start_time: 开始时间戳
- end_time: 结束时间戳
- duration: 执行耗时(秒)
```

### 迭代/循环节点 (logic.py)

```python
max_duration = node_data.get("max_duration", 300)  # 默认 5 分钟
if time.time() - start_time > max_duration:
    errors.append({"error": "max_duration exceeded"})
```

---

## 分段统计结构

### 执行链分段

```
[调度] → [LLM 调用] → [Tool 执行] → [结果处理] → [输出]
  <50ms   <3s          <2s         <500ms       <100ms
```

### 统计指标

| 阶段 | 字段 | 类型 | 说明 |
|------|------|------|------|
| 调度 | `schedule_latency` | Float | 任务入队到开始执行 |
| LLM | `llm_latency` | Float | 模型调用耗时 |
| Tool | `tool_latency` | Float | 工具执行耗时 |
| 总耗时 | `total_duration` | Float | 整体执行时间 |
| Token | `input_tokens` / `output_tokens` | Int | Token 消耗 |

---

## 扩展建议

在 `executor.py` 中增加分段统计：

```python
# 执行前记录
stage_start = time.time()

# LLM 调用阶段
llm_start = time.time()
response = await llm_handler.execute(node)
llm_latency = time.time() - llm_start

# Tool 执行阶段
tool_start = time.time()
result = await tool_handler.execute(node)
tool_latency = time.time() - tool_start

# 汇总
trace = {
    "schedule_latency": schedule_latency,
    "llm_latency": llm_latency,
    "tool_latency": tool_latency,
    "total_duration": total_duration
}
```

---

*最后更新: 2026-03-28*