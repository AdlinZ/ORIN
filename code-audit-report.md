# ORIN 项目代码审计报告

**审计日期**: 2026-02-27  
**审计范围**: Backend (Java) + AI Engine (Python) + Frontend (Vue)  
**代码统计**: Java 286文件 | Python 17文件 | Vue 90文件

---

## 🔴 严重问题 (Critical)

### 1. 代码执行安全漏洞 - Python AI Engine
**位置**: `orin-ai-engine/app/engine/handlers/code.py`

```python
# 危险代码
exec(code, {"__builtins__": __builtins__}, local_scope)
```

**问题**: 直接执行用户提供的任意 Python 代码，无沙箱隔离。攻击者可执行：
- `__import__('os').system('rm -rf /')`
- 读取敏感文件、反弹 shell、挖矿程序

**风险等级**: 🔴 CRITICAL

**修复建议**:
```python
# 方案1: 使用受限执行环境
import restrictedpython
from restrictedpython import compile_restricted, safe_globals

# 方案2: 使用 Docker 沙箱/隔离进程
# 方案3: 白名单限制允许的模块和函数
```

---

### 2. JWT 密钥硬编码风险
**位置**: `orin-backend/src/main/java/com/adlin/orin/security/JwtService.java`

```java
@Value("${jwt.secret:orin-secret-key-change-this-in-production-environment}")
private String secret;
```

**问题**: 
1. 默认值是硬编码的弱密钥
2. 注释说"must be at least 256-bits"，但默认值远不足
3. 生产环境如果忘记配置环境变量，会使用默认弱密钥

**风险等级**: 🔴 HIGH

**修复建议**:
```java
// 移除默认值，强制从环境变量读取
@Value("${JWT_SECRET}")
private String secret;

// 启动时校验密钥强度
@PostConstruct
public void validateSecret() {
    if (secret == null || secret.getBytes().length < 32) {
        throw new IllegalStateException("JWT_SECRET must be at least 256 bits");
    }
}
```

---

### 3. SQL 注入风险 - 原生查询
**位置**: 多个 Repository 文件

```java
// 在 KnowledgeDocumentRepository 等文件中
@Query(value = "SELECT * FROM knowledge_doc WHERE kb_id = ?1 AND ...", nativeQuery = true)
```

**问题**: 虽然当前使用参数化查询，但多处使用 `nativeQuery = true`，如果后续修改时拼接字符串，容易引入 SQL 注入。

**风险等级**: 🟡 MEDIUM

---

## 🟠 中等问题 (High/Medium)

### 4. CORS 配置过于宽松
**位置**: `orin-backend/src/main/java/com/adlin/orin/security/SecurityConfig.java`

```java
if ("*".equals(allowedOrigins)) {
    configuration.setAllowedOrigins(List.of("*"));
}
configuration.setAllowCredentials(false);  // 但所有控制器有 @CrossOrigin(origins = "*")
```

**问题**:
1. 默认允许所有来源 (`*`)
2. 各 Controller 上还有 `@CrossOrigin(origins = "*")`，可能覆盖 Security 配置
3. `AllowCredentials(false)` 与 `origins(*)` 在某些浏览器组合下可能被利用

**风险等级**: 🟠 HIGH

**修复建议**:
```java
// 移除所有 @CrossOrigin 注解
// 统一在 SecurityConfig 中配置
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // 明确指定允许的域名
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:5173",
        "https://orin.yourdomain.com"
    ));
    configuration.setAllowCredentials(true);
    // ...
}
```

---

### 5. 默认密码问题
**位置**: 
- `orin-backend/src/main/resources/db/migration/V3__Add_default_users.sql`
- `orin-backend/src/main/resources/db/migration/V4__Fix_passwords.sql`

```sql
-- V3 插入了明文密码
INSERT INTO sys_user (username, password, ...) VALUES ('admin', 'admin123', ...)

-- V4 尝试修复，但 hash 值格式有问题
UPDATE sys_user SET password = '$2a$10$8.7XNl.M5sC8H9l.5X/O.O.5X/O.O.5X/O.O.5X/O.O.5X/O.O.5X/O.' 
```

**问题**:
1. V3 先插入了明文密码
2. V4 的 BCrypt hash 格式不正确（含有 `.O.` 重复模式，可能是占位符）

**风险等级**: 🟠 HIGH

**修复建议**:
```sql
-- 生成正确的 BCrypt hash (使用 BCryptPasswordEncoder)
-- admin / ChangeMeNow123!
UPDATE sys_user SET password = '$2a$10$YourActualHashHere...' WHERE username = 'admin';
```

---

### 6. 文件上传安全检查不足
**位置**: 
- `orin-backend/src/main/java/com/adlin/orin/modules/multimodal/controller/MultimodalController.java`
- `orin-backend/src/main/java/com/adlin/orin/modules/knowledge/controller/KnowledgeManageController.java`

```java
// uploadDocument 方法没有对文件类型、大小、内容进行严格检查
public KnowledgeDocument uploadDocument(@PathVariable String kbId, 
    @RequestParam("file") MultipartFile file, ...)
```

**问题**:
1. 无文件类型白名单限制
2. 无文件大小限制（虽然有全局 10MB 配置，但业务层无二次校验）
3. 文件名未做安全处理（可能包含 `../` 路径遍历）
4. 无病毒/恶意文件扫描

**风险等级**: 🟠 MEDIUM

**修复建议**:
```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

public KnowledgeDocument uploadDocument(String kbId, MultipartFile file, String uploadedBy) {
    // 1. 验证文件大小
    if (file.getSize() > MAX_FILE_SIZE) {
        throw new IllegalArgumentException("File too large");
    }
    
    // 2. 验证文件类型（基于内容，不只是扩展名）
    String mimeType = file.getContentType();
    if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
        throw new IllegalArgumentException("File type not allowed");
    }
    
    // 3. 安全文件名
    String originalFilename = file.getOriginalFilename();
    String safeFilename = UUID.randomUUID().toString() + getExtension(originalFilename);
    
    // 4. 路径遍历防护
    Path targetPath = uploadDir.resolve(safeFilename).normalize();
    if (!targetPath.startsWith(uploadDir)) {
        throw new SecurityException("Path traversal detected");
    }
}
```

---

### 7. 缺少 Rate Limit 实现
**位置**: 全局

**问题**: 
- `application-dev.properties` 配置了 `rate.limit.requests=1000`，但实际无 Rate Limit 拦截器实现
- 登录接口无防暴力破解机制（虽然前端有滑动验证码，但后端未验证）

**风险等级**: 🟠 MEDIUM

**修复建议**:
```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String key = "rate_limit:" + getClientIP(request) + ":" + request.getRequestURI();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        if (count > RATE_LIMIT) {
            throw new RateLimitExceededException();
        }
        return true;
    }
}
```

---

## 🟡 低/信息级问题 (Low/Info)

### 8. 开发配置泄露敏感信息
**位置**: `orin-backend/src/main/resources/application-dev.properties`

```properties
spring.datasource.password=${DB_PASSWORD:password}
jwt.secret=${JWT_SECRET:dev-secret-key-only-for-development-do-not-use-in-production-must-be-at-least-256-bits}
```

**问题**: 虽然有环境变量覆盖，但默认值暴露了弱密码模式。

---

### 9. 日志敏感信息泄露
**位置**: 多处

```java
// LoginController.java
catch (Exception e) {
    log.error("Token validation failed: {}", e.getMessage());  // 可能包含敏感信息
}
```

**问题**: 错误日志可能包含：
- 数据库连接信息
- 用户密码
- JWT Token 内容

**修复建议**:
```java
// 生产环境关闭详细错误日志
// application-prod.properties
server.error.include-message=never
server.error.include-stacktrace=never
logging.level.com.adlin.orin=INFO
```

---

### 10. 前端 Token 存储
**位置**: `orin-frontend/src/views/Login.vue`

```javascript
const token = res.token || (res.data && res.data.token);
userStore.login(token, user, roles || ['ROLE_USER']);
localStorage.setItem('orin_user', JSON.stringify(user));
```

**问题**:
1. Token 存储在 localStorage，易受 XSS 攻击窃取
2. 用户敏感信息也存储在 localStorage

**建议**: 
- 使用 HttpOnly Cookie（更安全的做法，需要后端配合）
- 或者至少使用 sessionStorage 减少 XSS 窗口期

---

### 11. 实体类同时用 Lombok 和手写 Getter/Setter
**位置**: `orin-backend/src/main/java/com/adlin/orin/modules/system/entity/SysUser.java`

```java
@Data  // Lombok 生成 getter/setter
@Entity
public class SysUser {
    // ... 字段
    
    public Long getUserId() { ... }  // 手写的 getter
    public void setUserId(Long userId) { ... }  // 手写的 setter
}
```

**问题**: 代码冗余，`@Data` 和手写方法重复。

**修复**: 删除手写方法，或移除 `@Data` 只用手写方法。

---

### 12. Python 代码缺少类型检查
**位置**: `orin-ai-engine/app/engine/executor.py`

```python
def _build_adjacency_list(self, dsl: WorkflowDSL) -> Dict[str, List[str]]:
```

**问题**: 虽然有类型注解，但缺少运行时类型检查。`dsl` 可能为 `None` 或错误类型。

---

### 13. 注释代码未清理
**位置**: `SiliconFlowProxyController.java`

```java
// We hardcode API Key for now or get it from a default agent/config?
// The frontend call to /files does NOT include agentId, so we don't know which...
// 大量实现注释保留在代码中
```

**问题**: 开发注释未清理，包含设计思路和历史决策，可能影响代码可读性。

---

## 📊 问题汇总

| 等级 | 数量 | 问题类型 |
|------|------|----------|
| 🔴 Critical | 2 | 代码执行漏洞、JWT 硬编码 |
| 🟠 High | 3 | CORS、默认密码、文件上传 |
| 🟡 Medium | 2 | SQL 注入风险、Rate Limit |
| 🟢 Low | 6 | 日志泄露、Token 存储、代码风格 |

---

## 🛠️ 修复优先级

### 立即修复（本周）
1. **Python 代码执行沙箱化** - 这是远程代码执行漏洞
2. **JWT 密钥强制环境变量读取** - 移除默认值
3. **修复默认密码** - 使用正确的 BCrypt hash

### 短期修复（2周内）
4. 文件上传安全检查
5. CORS 配置收紧
6. Rate Limit 实现

### 中期优化（1个月内）
7. 前端 Token 存储安全
8. 日志脱敏
9. 代码风格统一

---

## ✅ 优点

1. **架构清晰** - 模块化设计，职责分离明确
2. **配置分离** - dev/prod 环境配置分离良好
3. **安全实践** - 使用 BCrypt、JJWT 等标准库
4. **审计日志** - 登录等关键操作有审计记录
5. **API 文档** - 集成 Swagger/OpenAPI
6. **数据库迁移** - 使用 Flyway 管理 schema 变更

---

## 📁 关键文件清单

```
orin-backend/
├── src/main/java/com/adlin/orin/
│   ├── security/
│   │   ├── JwtService.java           # JWT 硬编码问题
│   │   └── SecurityConfig.java       # CORS 配置
│   ├── modules/
│   │   ├── system/controller/LoginController.java
│   │   ├── multimodal/controller/MultimodalController.java  # 文件上传
│   │   └── knowledge/controller/KnowledgeManageController.java
│   └── config/JacksonConfig.java     # JSON 解析限制
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V3__Add_default_users.sql  # 明文密码
│   │   └── V4__Fix_passwords.sql      # 错误 hash
│   ├── application-dev.properties
│   └── application-prod.properties

orin-ai-engine/
└── app/engine/handlers/code.py       # 代码执行漏洞

orin-frontend/
└── src/views/Login.vue               # Token 存储
```

---

*报告生成时间: 2026-02-27 14:45*
