# 🔐 "拒绝访问" 问题解决方案

## 🎯 问题现象

访问日志配置中心时出现以下错误：
```
❌ 拒绝访问
❌ 加载 Logger 列表失败
❌ 拒绝访问
❌ 加载系统配置失败
```

## 🔍 问题原因

通过后端日志分析发现：
```
2026-01-15T14:04:33.868+08:00  WARN 91645 --- [orin-backend] [nio-8080-exec-5] 
c.a.o.security.JwtAuthenticationFilter   : JWT Token is expired
```

**根本原因**：**JWT 登录令牌已过期** ⏰

## 📋 技术细节

### JWT 配置
```properties
# application.properties
jwt.expiration=86400000  # 24小时 = 86400000 毫秒
```

### 安全配置
```java
// SecurityConfig.java
.requestMatchers("/api/v1/**").authenticated()  // 需要认证
```

所有 `/api/v1/**` 端点（包括日志配置 API）都需要有效的 JWT Token。

## ✅ 解决方案

### 方案 1：重新登录（立即生效）

1. **刷新页面**或点击**退出登录**
2. 使用用户名和密码**重新登录**
3. 系统会颁发新的 JWT Token（有效期 24 小时）
4. 再次访问日志配置中心

### 方案 2：延长 Token 有效期（可选）

如果你希望登录状态保持更长时间，可以修改配置：

**文件**：`orin-backend/src/main/resources/application.properties`

```properties
# 修改前（24小时）
jwt.expiration=86400000

# 修改为 7 天
jwt.expiration=604800000

# 或修改为 30 天
jwt.expiration=2592000000
```

**修改后需要重启后端服务**：
```bash
./manage.sh restart
```

⚠️ **注意**：延长 Token 有效期会降低安全性，建议：
- **开发环境**：7-30 天
- **生产环境**：保持 24 小时或更短

### 方案 3：自动刷新 Token（高级）

可以在前端实现 Token 自动刷新机制：
1. 检测 Token 即将过期（如剩余 5 分钟）
2. 自动调用刷新接口获取新 Token
3. 用户无感知地保持登录状态

（需要后端实现刷新 Token 接口）

## 🔍 如何检查 Token 状态

### 方法 1：浏览器开发者工具
1. 按 `F12` 打开开发者工具
2. 进入 **Application** 或 **存储** 标签
3. 查看 **Local Storage** 或 **Session Storage**
4. 找到 `token` 或 `jwt` 字段
5. 复制 Token 到 [jwt.io](https://jwt.io) 解析
6. 查看 `exp` 字段（过期时间戳）

### 方法 2：查看后端日志
```bash
tail -f orin-backend/backend.log | grep -i "expired\|token"
```

如果看到 `JWT Token is expired`，说明 Token 已过期。

## 🛡️ 安全最佳实践

### Token 有效期建议
| 环境 | 建议有效期 | 毫秒值 |
|------|-----------|--------|
| 开发环境 | 7 天 | 604800000 |
| 测试环境 | 24 小时 | 86400000 |
| 生产环境 | 2-8 小时 | 7200000-28800000 |
| 高安全环境 | 15-30 分钟 | 900000-1800000 |

### 其他安全措施
1. **启用 Token 刷新机制**
2. **记录用户活动**，长时间不活动自动登出
3. **IP 绑定**，Token 只能在同一 IP 使用
4. **设备绑定**，限制同时登录设备数
5. **敏感操作二次验证**

## 📝 快速操作指南

### 立即解决问题
```
1. 访问 http://localhost:5173
2. 点击右上角用户头像
3. 选择"退出登录"
4. 重新登录
5. 访问"系统管理" → "日志配置中心"
6. ✅ 问题解决！
```

### 延长登录时间（可选）
```bash
# 1. 编辑配置文件
vim orin-backend/src/main/resources/application.properties

# 2. 修改这一行
jwt.expiration=604800000  # 改为 7 天

# 3. 重启服务
./manage.sh restart

# 4. 重新登录
# 现在登录状态可以保持 7 天
```

## 🎯 总结

### 问题
- ❌ JWT Token 过期（24小时有效期）
- ❌ 所有需要认证的 API 都返回 403

### 解决
- ✅ 重新登录获取新 Token
- ✅ 或延长 Token 有效期
- ✅ 或实现自动刷新机制

### 预防
- 🔔 前端添加 Token 过期提示
- 🔔 实现自动刷新机制
- 🔔 记录用户活动时间

---

**问题解决时间**: 2026-01-15 14:04  
**根本原因**: JWT Token 过期  
**解决方法**: 重新登录
