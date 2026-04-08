# ORIN API 调用示例

## 智能体管理

### 批量导出智能体
```bash
# 导出所有智能体
curl -X POST "http://localhost:8080/api/v1/agents/batch/export" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -o agents_export.json

# 导出指定智能体
curl -X POST "http://localhost:8080/api/v1/agents/batch/export" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["agent-id-1", "agent-id-2"]' \
  -o agents_export.json
```

### 批量导入智能体
```bash
curl -X POST "http://localhost:8080/api/v1/agents/batch/import" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@agents_export.json"
```

### 刷新所有智能体元数据
```bash
curl -X POST "http://localhost:8080/api/v1/agents/refresh" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 查询异步任务状态
```bash
curl -X GET "http://localhost:8080/api/v1/agents/jobs/job-xxx" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 知识库任务

### 查询任务列表
```bash
curl -X GET "http://localhost:8080/api/v1/knowledge/tasks?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 查询失败任务
```bash
curl -X GET "http://localhost:8080/api/v1/knowledge/tasks/failed" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 重试任务
```bash
curl -X POST "http://localhost:8080/api/v1/knowledge/tasks/task-id/retry" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 工作流

### 执行工作流（带iteration/loop）
```bash
curl -X POST "http://localhost:8080/api/v1/workflow/run" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "workflowId": 1,
    "inputs": {
      "query": "test",
      "iterations": 3
    }
  }'
```

## 协作任务

### 创建协作包
```bash
curl -X POST "http://localhost:8080/api/v1/collaboration/packages" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Collaboration",
    "description": "Test",
    "collaborationMode": "SEQUENTIAL"
  }'
```

### 查询任务状态
```bash
curl -X GET "http://localhost:8080/api/v1/collaboration/packages/package-id/status" \
  -H "Authorization: Bearer YOUR_TOKEN"
```