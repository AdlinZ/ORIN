# ZeroClaw 轻量化 Agent 集成模块

## 概述

ZeroClaw 是 ORIN 系统的轻量化 Agent 集成模块，支持以下核心功能：

1. **轻量化助手接入** - 通过 REST API 与外部轻量化 Agent 服务通信
2. **智能监控分析** - 异常自动诊断、趋势分析、根因定位
3. **主动维护 (Self-healing)** - 自动化故障排除、日志清理、缓存管理

## 架构设计

```
orin-backend/
└── src/main/java/com/adlin/orin/modules/zeroclaw/
    ├── client/          # HTTP 客户端
    │   └── ZeroClawClient.java
    ├── controller/      # REST API 接口
    │   └── ZeroClawController.java
    ├── dto/             # 数据传输对象
    │   ├── ZeroClawAnalysisRequest.java
    │   ├── ZeroClawConnectionRequest.java
    │   └── ZeroClawSelfHealingRequest.java
    ├── entity/          # 实体类
    │   ├── ZeroClawConfig.java
    │   ├── ZeroClawAnalysisReport.java
    │   └── ZeroClawSelfHealingLog.java
    ├── repository/      # 数据访问层
    │   ├── ZeroClawConfigRepository.java
    │   ├── ZeroClawAnalysisReportRepository.java
    │   └── ZeroClawSelfHealingLogRepository.java
    ├── service/         # 业务逻辑层
    │   ├── ZeroClawService.java
    │   └── ZeroClawServiceImpl.java
    └── task/            # 定时任务
        └── ZeroClawScheduledTask.java
```

## 数据库表结构

- **zeroclaw_configs** - ZeroClaw 服务配置
- **zeroclaw_analysis_reports** - 智能分析报告
- **zeroclaw_self_healing_logs** - 主动维护操作记录

## API 接口

### 配置管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/zeroclaw/configs` | 获取所有配置 |
| POST | `/api/zeroclaw/configs` | 创建配置 |
| PUT | `/api/zeroclaw/configs/{id}` | 更新配置 |
| DELETE | `/api/zeroclaw/configs/{id}` | 删除配置 |
| POST | `/api/zeroclaw/configs/test-connection` | 测试连接 |

### 状态监控

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/zeroclaw/status` | 获取 ZeroClaw 连接状态 |

### 智能分析

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/zeroclaw/analyze` | 执行智能分析 |
| GET | `/api/zeroclaw/reports` | 获取分析报告列表 |
| GET | `/api/zeroclaw/reports/agent/{agentId}` | 获取指定 Agent 的分析报告 |
| POST | `/api/zeroclaw/reports/daily` | 手动生成 24h 趋势报告 |

### 主动维护

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/zeroclaw/self-healing` | 执行主动维护操作 |
| GET | `/api/zeroclaw/self-healing/logs` | 获取维护操作记录 |

## 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| generateDailyTrendReport | 每天凌晨 2:00 | 生成 24h 趋势分析报告 |
| checkAnomaliesAndDiagnose | 每 5 分钟 | 检查异常 Agent 并触发诊断 |
| performSelfHealingCheck | 每 10 分钟 | 执行主动维护检查 |

## 配置示例

```json
{
  "configName": "ZeroClaw Main",
  "endpointUrl": "http://localhost:8081",
  "accessToken": "your-token-here",
  "enabled": true,
  "enableAnalysis": true,
  "enableSelfHealing": true,
  "heartbeatInterval": 60
}
```

## 分析请求示例

```json
{
  "analysisType": "ANOMALY_DIAGNOSIS",
  "agentId": "agent-123",
  "startTime": 1700000000000,
  "endTime": 1700086400000,
  "context": "High latency detected"
}
```

## 主动维护请求示例

```json
{
  "actionType": "CLEAR_LOGS",
  "targetResource": "orin-system-logs",
  "reason": "Log directory exceeding 1GB",
  "forceExecute": false
}
```

## 支持的维护操作类型

- **CLEAR_LOGS** - 清理日志文件
- **RESTART_PROCESS** - 重启进程
- **CLEANUP_CACHE** - 清理缓存
- **SCALE_RESOURCE** - 扩缩容资源

## 与 ZeroClaw 服务端的接口约定

ZeroClaw 服务端需要实现以下接口：

### Health Check
```
GET /health
Response: 200 OK
```

### Analysis
```
POST /api/analyze
Request: { "analysisType": "...", "data": {...} }
Response: { "title": "...", "summary": "...", "rootCause": "...", "recommendations": "...", "severity": "..." }
```

### Self-Healing
```
POST /api/self-healing
Request: { "action": "...", "params": {...} }
Response: { "success": true/false, "error": "..." }
```

### Status
```
GET /status
Response: { "version": "...", "uptime": "...", "activeTasks": ... }
```

## 后续扩展

- [ ] Telegram/Discord 消息网关集成
- [ ] WebSocket 实时通知
- [ ] 更多维护操作类型
- [ ] 分析结果可视化图表
