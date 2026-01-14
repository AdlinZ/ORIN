# 智能体健康监控系统

## 概述

实现了一个自动化的智能体健康监控系统，定期检查所有已接入智能体的运行状态。

## 核心功能

### 1. 定时健康检查
- **执行频率**：每5分钟自动执行一次
- **检查范围**：所有已接入的智能体（Dify 和 SiliconFlow）
- **自动化**：无需手动干预，系统启动后自动运行

### 2. 状态判定逻辑

#### 健康检查方式
- **SiliconFlow 智能体**：调用 `testConnection` 方法测试 API 连接
- **Dify 智能体**：调用 `testConnection` 方法验证端点可用性

#### 状态分类
- **RUNNING（运行中）**：连接测试成功，健康评分 100
- **ERROR（异常）**：连接测试失败，健康评分 0
- **UNKNOWN（未知）**：未知的 Provider 类型，健康评分 50

### 3. 自动更新机制
每次健康检查后，系统会自动更新：
- **status**：当前运行状态
- **healthScore**：健康评分（0-100）
- **lastHeartbeat**：最后检查时间戳

## 技术实现

### 核心类
**文件位置**：`com.adlin.orin.modules.monitor.task.AgentHealthCheckTask`

### 关键方法
1. `checkAgentHealth()` - 定时任务入口，每5分钟执行
2. `checkSingleAgent()` - 检查单个智能体健康状况
3. `checkSiliconFlowAgent()` - SiliconFlow 专用健康检查
4. `checkDifyAgent()` - Dify 专用健康检查
5. `updateAgentStatus()` - 更新智能体状态到数据库

### 依赖服务
- `AgentHealthStatusRepository` - 健康状态数据访问
- `AgentAccessProfileRepository` - 访问凭证数据访问
- `DifyIntegrationService` - Dify API 集成
- `SiliconFlowIntegrationService` - SiliconFlow API 集成

## 使用说明

### 查看健康状态
在智能体管理页面的"接入状态"列可以看到实时状态：
- 🟢 **RUNNING** - 智能体运行正常
- 🔴 **ERROR** - 智能体连接失败
- ⚪ **UNKNOWN** - 状态未知

### 状态更新时间
- 初次接入：立即设置为 RUNNING
- 定时检查：每5分钟自动更新一次
- 手动触发：重启系统后立即执行一次检查

## 日志监控

### 查看健康检查日志
```bash
tail -f /Users/adlin/Desktop/JAVA/orin-backend/backend.log | grep "health check"
```

### 日志级别
- **INFO**：健康检查开始/完成
- **DEBUG**：单个智能体检查详情
- **WARN**：检查失败或配置缺失
- **ERROR**：检查过程中的异常

## 配置调整

### 修改检查频率
编辑 `AgentHealthCheckTask.java` 中的 `@Scheduled` 注解：
```java
@Scheduled(fixedRate = 300000) // 当前：5分钟（300000ms）
```

可选配置：
- 1分钟：`60000`
- 3分钟：`180000`
- 10分钟：`600000`

### 禁用健康检查
如需临时禁用，可以在 `OrinApplication.java` 中移除 `@EnableScheduling` 注解。

## 性能影响

- **网络开销**：每个智能体每5分钟一次 API 测试请求
- **数据库操作**：每次检查更新一次健康状态记录
- **CPU 使用**：极低，仅在定时任务执行时短暂占用

## 未来优化方向

1. **智能频率调整**：根据智能体稳定性动态调整检查频率
2. **告警机制**：状态异常时发送通知
3. **历史记录**：保存健康检查历史，用于趋势分析
4. **批量优化**：并发检查多个智能体，提升效率
