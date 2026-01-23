# 缓存策略文档

## 概述

ORIN项目使用Redis作为缓存后端，通过Spring Cache抽象层实现透明缓存。这旨在减少数据库负载，提高常用查询的响应速度。

---

## 配置

### 1. 依赖

- `spring-boot-starter-data-redis`: Redis连接
- `spring-boot-starter-cache`: Spring Cache抽象

### 2. 缓存配置类

`com.adlin.orin.config.CacheConfig` 定义了缓存策略：

- **序列化**: Key使用String序列化，Value使用JSON序列化 (GenericJackson2JsonRedisSerializer)
- **Null值处理**: 禁止缓存Null值

### 3. 过期时间 (TTL)

| 缓存名称 | TTL (时间) | 用途 |
|---------|-----------|------|
| (默认) | 60分钟 | 通用缓存 |
| `agents` | 30分钟 | 单个Agent的元数据 |
| `agent_list` | 5分钟 | Agent列表（高频变动需较短TTL） |
| `knowledge_bases` | 2小时 | 知识库信息 |

---

## 缓存键 (Cache Keys)

### 命名规范

- **Cache Name**: 复数名词，如 `agents`, `users`
- **Key**: 资源的唯一标识符，通常是ID

### 示例

- `agents::abc-123` -> Agent ID为 `abc-123` 的元数据
- `agent_list::SimpleKey []` -> Agent列表

---

## 注解使用指南

使用Spring Cache注解管理缓存生命周期。

### 1. @Cacheable (读缓存)

用于查询方法。如果缓存存在，直接返回；否则执行方法并缓存结果。

```java
@Cacheable(value = "agents", key = "#agentId")
public AgentMetadata getAgentMetadata(String agentId) {
    return repository.findById(agentId).orElseThrow();
}
```

### 2. @CacheEvict (清除缓存)

用于更新或删除方法。确保缓存与数据库保持一致。

**单个清除**:
```java
@CacheEvict(value = "agents", key = "#agentId")
public void updateAgent(String agentId, AgentUpdateRequest request) { ... }
```

**批量清除** (例如清除列表缓存):
```java
@CacheEvict(value = "agent_list", allEntries = true)
public void onboardAgent(...) { ... }
```

### 3. @Caching (组合操作)

同时清除多个缓存（例如更新Agent时，清除详情缓存和列表缓存）。

```java
@Caching(evict = {
    @CacheEvict(value = "agents", key = "#agentId"),
    @CacheEvict(value = "agent_list", allEntries = true)
})
public void updateAgent(...) { ... }
```

---

## 监控

可以通过Spring Boot Actuator监控缓存统计信息（如果启用了统计）。

API: `/actuator/metrics/cache.gets`, `/actuator/metrics/cache.puts`, `/actuator/metrics/cache.evictions`

---

## 最佳实践

1.  **只缓存读多写少的数据**: 如配置信息、元数据。
2.  **谨慎缓存列表**: 列表缓存容易失效且命中率低，仅在列表查询非常频繁且变动不频繁时使用。设置较短的TTL。
3.  **处理事务**: 缓存操作通常在事务提交后执行（Spring默认行为是在方法返回后，如果在事务内可能存在不一致，但在本项目场景下可接受）。
4.  **序列化**: 确保缓存对象实现了 `Serializable` 接口（虽然JSON序列化不强制，但是个好习惯），并且有无参构造函数。

---

## 常见问题

### Q: 更新数据库后缓存没更新？
A: 检查是否忘记添加 `@CacheEvict`，或者 Key 生成策略不一致。

### Q: 类型转换错误 (ClassCastException)？
A: Redis中存储的JSON可能反序列化为错误的类。通常发生在代码重构后。解决方案是清除Redis缓存或更改 `serialVersionUID`。

### Q: 缓存击穿/穿透防护？
A: 目前使用基本的TTL。对于高并发场景，需考虑使用互斥锁或布隆过滤器（暂未实现，视为P2/P3需求）。
