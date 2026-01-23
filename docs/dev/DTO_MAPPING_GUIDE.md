# DTO转换层使用指南

## 概述

ORIN项目使用MapStruct实现实体(Entity)与数据传输对象(DTO)之间的自动转换，提供清晰的API边界和数据安全性。

---

## 为什么需要DTO层？

### 问题

直接暴露Entity给客户端会导致：
- ❌ 数据库字段变更直接影响API契约
- ❌ 无法隐藏敏感字段（如API密钥、密码）
- ❌ 难以版本化API
- ❌ 无法进行字段级别的验证
- ❌ 循环引用问题（JPA关联）

### 解决方案

使用DTO层：
- ✅ API契约与数据库结构解耦
- ✅ 隐藏敏感信息
- ✅ 支持API版本化
- ✅ 字段级验证
- ✅ 避免循环引用

---

## 项目结构

```
src/main/java/com/adlin/orin/modules/
├── agent/
│   ├── dto/
│   │   ├── AgentResponse.java              # 响应DTO
│   │   ├── AgentCreateRequest.java         # 创建请求DTO
│   │   ├── AgentUpdateRequest.java         # 更新请求DTO
│   │   └── AgentListResponse.java          # 列表响应DTO
│   ├── mapper/
│   │   └── AgentMapper.java                # MapStruct Mapper
│   ├── entity/
│   │   ├── AgentMetadata.java              # 实体
│   │   └── AgentAccessProfile.java         # 实体
│   └── controller/
│       └── AgentController.java            # 使用DTO
└── knowledge/
    ├── dto/
    ├── mapper/
    └── ...
```

---

## DTO类型

### 1. Response DTO（响应）

用于返回数据给客户端。

**特点**:
- 隐藏敏感字段
- 可以组合多个Entity
- 使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 避免null字段

**示例**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {
    private String agentId;
    private String name;
    private String description;
    private String endpointDomain;  // 仅显示域名，不显示完整URL
    // 不包含 apiKey 等敏感字段
}
```

### 2. Create Request DTO（创建请求）

用于接收创建资源的请求。

**特点**:
- 包含验证注解
- 不包含ID等自动生成字段
- 使用 `@NotBlank`, `@Pattern` 等验证

**示例**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCreateRequest {
    @NotBlank(message = "Agent名称不能为空")
    private String name;
    
    @Pattern(regexp = "^https?://.*", message = "端点URL格式不正确")
    private String endpointUrl;
    
    @NotBlank(message = "API密钥不能为空")
    private String apiKey;
}
```

### 3. Update Request DTO（更新请求）

用于接收更新资源的请求。

**特点**:
- 所有字段可选（部分更新）
- 包含验证注解
- 不包含不可修改的字段

**示例**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUpdateRequest {
    private String name;  // 可选
    private String description;  // 可选
    // 不包含 providerType 等不可修改字段
}
```

### 4. List Response DTO（列表响应）

用于返回分页列表。

**特点**:
- 包含分页信息
- 使用简化的Summary对象

**示例**:
```java
@Data
@Builder
public class AgentListResponse {
    private List<AgentSummary> agents;
    private Long total;
    private Integer page;
    private Integer pageSize;
    
    @Data
    @Builder
    public static class AgentSummary {
        private String agentId;
        private String name;
        private String status;
    }
}
```

---

## MapStruct Mapper

### 基本用法

```java
@Mapper(componentModel = "spring")
public interface AgentMapper {
    
    // Entity -> Response DTO
    AgentResponse toResponse(AgentMetadata metadata);
    
    // Request DTO -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    AgentMetadata toEntity(AgentCreateRequest request);
    
    // 列表转换
    List<AgentResponse> toResponseList(List<AgentMetadata> entities);
}
```

### 高级特性

#### 1. 忽略null值更新

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void updateFromRequest(AgentUpdateRequest request, @MappingTarget AgentMetadata entity);
```

#### 2. 自定义字段映射

```java
@Mapping(target = "endpointDomain", source = "endpointUrl", qualifiedByName = "extractDomain")
AgentResponse toResponse(AgentMetadata metadata);

@Named("extractDomain")
default String extractDomain(String url) {
    try {
        return new URL(url).getHost();
    } catch (Exception e) {
        return null;
    }
}
```

#### 3. 组合多个Entity

```java
@Mapping(target = "connectionStatus", source = "profile.connectionStatus")
@Mapping(target = "health", source = "healthStatus")
AgentResponse toResponse(
    AgentMetadata metadata,
    @Context AgentAccessProfile profile,
    @Context AgentHealthStatus healthStatus
);
```

#### 4. 常量值

```java
@Mapping(target = "connectionStatus", constant = "ACTIVE")
AgentAccessProfile toAccessProfile(AgentCreateRequest request);
```

---

## Controller使用示例

### 使用DTO的Controller

```java
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {
    
    private final AgentManageService agentService;
    private final AgentMapper agentMapper;
    
    /**
     * 创建Agent
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> createAgent(
            @Valid @RequestBody AgentCreateRequest request) {
        
        // 1. DTO -> Entity
        AgentMetadata metadata = agentMapper.toEntity(request);
        
        // 2. 业务逻辑
        AgentMetadata created = agentService.createAgent(metadata);
        
        // 3. Entity -> DTO
        AgentResponse response = agentMapper.toResponse(created);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 获取Agent详情
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgent(
            @PathVariable String agentId) {
        
        AgentMetadata metadata = agentService.getAgent(agentId);
        AgentResponse response = agentMapper.toResponse(metadata);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 更新Agent
     */
    @PutMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentResponse>> updateAgent(
            @PathVariable String agentId,
            @Valid @RequestBody AgentUpdateRequest request) {
        
        AgentMetadata metadata = agentService.getAgent(agentId);
        agentMapper.updateFromRequest(request, metadata);
        
        AgentMetadata updated = agentService.updateAgent(metadata);
        AgentResponse response = agentMapper.toResponse(updated);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 获取Agent列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AgentListResponse>> listAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<AgentMetadata> agents = agentService.listAgents(page, size);
        List<AgentListResponse.AgentSummary> summaries = 
            agentMapper.toSummaryList(agents);
        
        AgentListResponse response = AgentListResponse.builder()
            .agents(summaries)
            .total((long) agents.size())
            .page(page)
            .pageSize(size)
            .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

## 验证注解

### 常用验证注解

```java
@NotNull        // 不能为null
@NotBlank       // 不能为null、空字符串或仅包含空格
@NotEmpty       // 不能为null或空（集合、数组、字符串）
@Size(min=, max=)  // 长度限制
@Min(value)     // 最小值
@Max(value)     // 最大值
@Email          // 邮箱格式
@Pattern(regexp=)  // 正则表达式
@Past           // 过去的日期
@Future         // 未来的日期
```

### 自定义验证消息

```java
@NotBlank(message = "Agent名称不能为空")
private String name;

@Pattern(regexp = "^https?://.*", message = "端点URL必须以http://或https://开头")
private String endpointUrl;
```

### 嵌套对象验证

```java
@Data
public class AgentCreateRequest {
    @NotBlank
    private String name;
    
    @Valid  // 启用嵌套验证
    private ConfigDto config;
}

@Data
public class ConfigDto {
    @NotBlank
    private String key;
    
    @Min(1)
    private Integer value;
}
```

---

## 最佳实践

### 1. 命名规范

- Response DTO: `{Entity}Response`
- Create Request: `{Entity}CreateRequest`
- Update Request: `{Entity}UpdateRequest`
- List Response: `{Entity}ListResponse`
- Mapper: `{Entity}Mapper`

### 2. 敏感信息处理

```java
// ❌ 错误：直接返回敏感信息
@Data
public class AgentResponse {
    private String apiKey;  // 暴露API密钥
    private String endpointUrl;  // 暴露完整URL
}

// ✅ 正确：隐藏或脱敏
@Data
public class AgentResponse {
    private String endpointDomain;  // 仅显示域名
    // 不包含apiKey字段
}
```

### 3. 部分更新

```java
// 使用IGNORE策略，仅更新非null字段
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void updateFromRequest(UpdateRequest request, @MappingTarget Entity entity);
```

### 4. 避免循环引用

```java
// ❌ 错误：直接返回Entity（可能有JPA关联）
@OneToMany
private List<RelatedEntity> related;  // 可能导致循环引用

// ✅ 正确：DTO中使用ID列表
private List<String> relatedIds;
```

### 5. 分层清晰

```
Controller (DTO) 
    ↓
Service (Entity)
    ↓
Repository (Entity)
```

Controller层只处理DTO，Service层只处理Entity。

---

## 常见问题

### Q1: 何时使用DTO？

**所有对外API都应该使用DTO**，包括：
- REST API
- GraphQL API
- WebSocket消息

内部服务调用可以直接使用Entity。

### Q2: DTO和Entity的区别？

| 特性 | Entity | DTO |
|------|--------|-----|
| 用途 | 数据库映射 | API数据传输 |
| 注解 | JPA注解 | 验证注解 |
| 字段 | 所有数据库字段 | 仅API需要的字段 |
| 关联 | JPA关联 | 简单类型或ID |
| 可变性 | 可变 | 通常不可变 |

### Q3: MapStruct vs 手动转换？

**MapStruct优势**:
- 编译时生成，性能好
- 类型安全
- 减少样板代码
- 易于维护

**手动转换适用于**:
- 复杂的业务逻辑
- 需要额外查询的字段

### Q4: 如何处理分页？

```java
@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
}

// 使用
PageResponse<AgentResponse> response = PageResponse.<AgentResponse>builder()
    .content(agentMapper.toResponseList(agents))
    .total(total)
    .page(page)
    .pageSize(size)
    .totalPages((int) Math.ceil((double) total / size))
    .build();
```

---

## 迁移指南

### 从Entity直接返回迁移到DTO

**步骤1**: 创建DTO类
```java
// 创建 AgentResponse.java
```

**步骤2**: 创建Mapper
```java
// 创建 AgentMapper.java
```

**步骤3**: 更新Controller
```java
// 旧代码
return ResponseEntity.ok(agentMetadata);

// 新代码
AgentResponse response = agentMapper.toResponse(agentMetadata);
return ResponseEntity.ok(ApiResponse.success(response));
```

**步骤4**: 更新测试
```java
// 更新API测试，验证DTO字段
```

---

## 相关资源

- [MapStruct官方文档](https://mapstruct.org/)
- [Bean Validation规范](https://beanvalidation.org/)
- [Spring Validation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#validation)

---

## 总结

DTO转换层为ORIN项目提供了：
- ✅ 清晰的API边界
- ✅ 数据安全性
- ✅ API版本化能力
- ✅ 更好的可维护性

遵循本指南的规范，可以构建健壮、安全的API接口。
