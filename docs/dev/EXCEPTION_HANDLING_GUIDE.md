# 统一异常处理体系使用指南

## 概述

ORIN项目已实现统一的异常处理体系，提供清晰的错误分类、标准化的错误响应和详细的错误追踪。

---

## 异常层次结构

```
Exception
  └── RuntimeException
        └── BusinessException (业务异常基类)
              ├── ResourceNotFoundException (资源未找到)
              ├── ValidationException (验证异常)
              ├── AuthenticationException (认证异常)
              ├── AuthorizationException (授权异常)
              ├── WorkflowExecutionException (工作流异常)
              └── VectorizationException (向量化异常)
```

---

## 使用示例

### 1. 抛出资源未找到异常

```java
@Service
public class AgentManageService {
    
    public AgentMetadata getAgent(String agentId) {
        return agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));
    }
}
```

**响应示例**:
```json
{
  "code": "20001",
  "message": "Agent not found with id: abc123",
  "timestamp": "2026-01-22T15:30:00",
  "path": "/api/agents/abc123",
  "status": 404,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 2. 抛出验证异常

```java
@Service
public class KnowledgeManageService {
    
    public void createKnowledge(KnowledgeBase kb) {
        if (kb.getName() == null || kb.getName().isBlank()) {
            Map<String, String> fieldErrors = new HashMap<>();
            fieldErrors.put("name", "知识库名称不能为空");
            throw new ValidationException("知识库验证失败", fieldErrors);
        }
    }
}
```

**响应示例**:
```json
{
  "code": "90001",
  "message": "知识库验证失败",
  "timestamp": "2026-01-22T15:30:00",
  "path": "/api/knowledge",
  "status": 400,
  "metadata": {
    "name": "知识库名称不能为空"
  },
  "traceId": "550e8400-e29b-41d4-a716-446655440001"
}
```

---

### 3. 使用特定错误代码

```java
@Service
public class DifyIntegrationService {
    
    public void testConnection(String endpoint, String apiKey) {
        try {
            // 调用Dify API
        } catch (Exception e) {
            throw new BusinessException(
                ErrorCode.DIFY_API_ERROR,
                "无法连接到Dify服务: " + e.getMessage(),
                e
            );
        }
    }
}
```

**响应示例**:
```json
{
  "code": "80001",
  "message": "无法连接到Dify服务: Connection timeout",
  "detail": "Dify API调用失败",
  "timestamp": "2026-01-22T15:30:00",
  "path": "/api/agents/onboard",
  "status": 500,
  "traceId": "550e8400-e29b-41d4-a716-446655440002"
}
```

---

### 4. Controller层使用统一响应

```java
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    
    @GetMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentMetadata>> getAgent(@PathVariable String agentId) {
        AgentMetadata agent = agentService.getAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<AgentMetadata>> createAgent(
            @Valid @RequestBody AgentCreateRequest request) {
        AgentMetadata agent = agentService.createAgent(request);
        return ResponseEntity.ok(ApiResponse.success(agent, "智能体创建成功"));
    }
}
```

**成功响应示例**:
```json
{
  "code": "00000",
  "message": "智能体创建成功",
  "data": {
    "agentId": "abc123",
    "name": "My Agent",
    "status": "ACTIVE"
  }
}
```

---

## 错误代码分类

### 通用错误 (1xxxx)
- `10000` - 系统内部错误
- `10001` - 参数验证失败
- `10003` - 未授权访问
- `10004` - 禁止访问

### 资源错误 (2xxxx)
- `20001` - 资源未找到
- `20002` - 资源已存在
- `20003` - 资源冲突

### Agent错误 (3xxxx)
- `30001` - 智能体未找到
- `30003` - 智能体连接失败
- `30004` - 智能体接入失败

### Knowledge错误 (4xxxx)
- `40001` - 知识库未找到
- `40002` - 知识库同步失败
- `40005` - 向量化失败

### Model错误 (5xxxx)
- `50001` - 模型未找到
- `50002` - 模型配置无效
- `50003` - 模型API调用失败

### Workflow错误 (6xxxx)
- `60001` - 工作流未找到
- `60002` - 工作流执行失败

### 认证授权错误 (7xxxx)
- `70001` - 用户名或密码错误
- `70002` - 令牌已过期
- `70004` - 权限不足

### 外部服务错误 (8xxxx)
- `80001` - Dify API调用失败
- `80003` - Redis连接失败
- `80004` - 数据库操作失败

### 验证错误 (9xxxx)
- `90001` - 数据验证失败
- `90002` - 必填字段缺失
- `90003` - 格式不正确

---

## 最佳实践

### 1. 选择合适的异常类型

```java
// ✅ 好的做法
if (agent == null) {
    throw new ResourceNotFoundException("Agent", agentId);
}

// ❌ 不好的做法
if (agent == null) {
    throw new RuntimeException("Agent not found");
}
```

### 2. 提供有用的错误信息

```java
// ✅ 好的做法
throw new ValidationException(
    "知识库配置无效",
    Map.of("endpoint", "端点URL格式不正确")
);

// ❌ 不好的做法
throw new ValidationException("Invalid config");
```

### 3. 使用特定的错误代码

```java
// ✅ 好的做法
throw new BusinessException(
    ErrorCode.AGENT_CONNECTION_FAILED,
    "无法连接到智能体: " + endpoint
);

// ❌ 不好的做法
throw new BusinessException(
    ErrorCode.SYSTEM_ERROR,
    "Connection failed"
);
```

### 4. 记录详细的日志

```java
// ✅ 好的做法
try {
    // 业务逻辑
} catch (Exception e) {
    log.error("Failed to process agent onboarding: agentId={}, endpoint={}",
        agentId, endpoint, e);
    throw new BusinessException(
        ErrorCode.AGENT_ONBOARD_FAILED,
        "智能体接入失败",
        e
    );
}
```

---

## 开发环境 vs 生产环境

### 开发环境 (dev)
- 返回详细的错误信息（`detail` 字段）
- 包含堆栈跟踪
- 显示所有元数据

### 生产环境 (prod)
- 隐藏敏感的错误详情
- 不返回堆栈跟踪
- 仅显示用户友好的错误消息

---

## 错误追踪

每个错误响应都包含 `traceId`，可用于：
1. 关联日志查询
2. 问题追踪
3. 用户支持

**日志查询示例**:
```bash
grep "550e8400-e29b-41d4-a716-446655440000" backend.log
```

---

## 迁移指南

### 从旧代码迁移

**旧代码**:
```java
if (agent == null) {
    throw new RuntimeException("Agent not found");
}
```

**新代码**:
```java
if (agent == null) {
    throw new ResourceNotFoundException("Agent", agentId);
}
```

### 批量替换建议

1. **查找所有 `throw new RuntimeException`**
2. **根据业务场景选择合适的异常类型**
3. **使用特定的错误代码**
4. **添加详细的错误信息**

---

## 常见问题

### Q1: 何时使用 BusinessException vs 特定异常？

- 使用特定异常（如 `ResourceNotFoundException`）当错误类型明确时
- 使用 `BusinessException` 当需要自定义错误代码时

### Q2: 如何添加新的错误代码？

在 `ErrorCode` 枚举中添加：
```java
NEW_ERROR_CODE("12345", "错误描述")
```

### Q3: 如何处理第三方库的异常？

```java
try {
    // 调用第三方库
} catch (ThirdPartyException e) {
    throw new BusinessException(
        ErrorCode.EXTERNAL_SERVICE_ERROR,
        "第三方服务调用失败: " + e.getMessage(),
        e
    );
}
```

---

## 相关文档

- [P1实施计划](P1_IMPLEMENTATION_PLAN.md)
- [API文档](docs/API文档.md)
- [错误代码完整列表](ErrorCode.java)
