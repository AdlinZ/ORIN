# ORIN API 文档

本文档提供 ORIN 系统的完整 API 接口说明。

## 基础信息

- **Base URL**: `http://localhost:8080/api/v1`
- **认证方式**: JWT Bearer Token
- **Content-Type**: `application/json`

---

## 1. 智能体管理 API

### 1.1 接入智能体

```http
POST /agents/onboard
```

**请求体**:
```json
{
  "endpointUrl": "http://dify-server/v1",
  "apiKey": "app-xxx",
  "datasetApiKey": "dataset-xxx"
}
```

### 1.2 获取智能体列表

```http
GET /agents
```

### 1.3 智能体版本管理

#### 创建版本
```http
POST /agents/{agentId}/versions
```

**请求体**:
```json
{
  "description": "优化模型参数",
  "createdBy": "admin"
}
```

#### 获取版本列表
```http
GET /agents/{agentId}/versions
```

#### 回滚版本
```http
POST /agents/{agentId}/versions/{versionId}/rollback
```

#### 版本对比
```http
GET /agents/{agentId}/versions/compare?version1=1&version2=2
```

---

### 1.4 批量操作

#### 批量导出
```http
POST /agents/batch/export
```
**请求体** (可选，指定ID导出，空则导出所有):
```json
["agent1", "agent2"]
```

#### 批量导入
```http
POST /agents/batch/import
Content-Type: multipart/form-data
```
**参数**:
- `file`: JSON 配置文件

---

## 2. 知识库管理 API

### 2.1 文档上传

```http
POST /knowledge/{kbId}/documents/upload
Content-Type: multipart/form-data
```

**参数**:
- `file`: 文件（PDF, TXT, MD, DOCX）
- `uploadedBy`: 上传者

### 2.2 获取文档列表

```http
GET /knowledge/{kbId}/documents
```

### 2.3 触发向量化

```http
POST /knowledge/documents/{docId}/vectorize
```

### 2.4 获取知识库统计

```http
GET /knowledge/{kbId}/stats
```

**响应**:
```json
{
  "documentCount": 10,
  "totalCharCount": 50000
}
```

---

## 3. 多模态文件 API

### 3.1 上传文件

```http
POST /multimodal/upload
Content-Type: multipart/form-data
```

**参数**:
- `file`: 文件（图片/音频/视频/文档）
- `uploadedBy`: 上传者

### 3.2 获取文件列表

```http
GET /multimodal/files
```

### 3.3 按类型获取文件

```http
GET /multimodal/files/type/{fileType}
```

**fileType**: IMAGE, AUDIO, VIDEO, DOCUMENT

### 3.4 下载文件

```http
GET /multimodal/files/{fileId}/download
```

### 3.5 获取缩略图

```http
GET /multimodal/files/{fileId}/thumbnail
```

---

## 4. 日志管理 API

### 4.1 获取所有 Logger

```http
GET /system/log-config/loggers
```

### 4.2 设置日志级别

```http
PUT /system/log-config/loggers/{loggerName}
```

**请求体**:
```json
{
  "level": "DEBUG"
}
```

**level**: TRACE, DEBUG, INFO, WARN, ERROR, OFF, NULL

### 4.3 批量设置日志级别

```http
POST /system/log-config/loggers/batch
```

**请求体**:
```json
{
  "com.adlin.orin": "DEBUG",
  "org.springframework": "WARN"
}
```

### 4.4 重置所有 Logger

```http
POST /system/log-config/loggers/reset-all
```

---

## 5. 告警管理 API

### 5.1 创建告警规则

```http
POST /alerts/rules
```

**请求体**:
```json
{
  "ruleName": "CPU 使用率过高",
  "ruleType": "PERFORMANCE",
  "conditionExpr": "cpu_usage > 80",
  "thresholdValue": 80.0,
  "severity": "WARNING",
  "enabled": true,
  "notificationChannels": "EMAIL,DINGTALK",
  "recipientList": "admin@example.com",
  "cooldownMinutes": 5
}
```

### 5.2 获取告警规则

```http
GET /alerts/rules
```

### 5.3 测试告警通知

```http
POST /alerts/rules/{id}/test
```

### 5.4 获取告警历史

```http
GET /alerts/history?page=0&size=20
```

### 5.5 解决告警

```http
POST /alerts/history/{id}/resolve
```

### 5.6 获取告警统计

```http
GET /alerts/stats
```

**响应**:
```json
{
  "totalRules": 5,
  "enabledRules": 4,
  "activeAlerts": 2,
  "totalAlerts": 100
}
```

---

## 6. 监控 API

### 6.1 获取监控看板

```http
GET /monitor/dashboard/summary
```

### 6.2 获取智能体状态

```http
GET /monitor/agents/{agentId}/status
```

### 6.3 获取请求链路

```http
GET /monitor/dataflow/{traceId}
```

---

## 错误码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

---

## 认证示例

```bash
# 获取 Token（需要先实现登录接口）
TOKEN="your-jwt-token"

# 使用 Token 调用 API
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/agents
```

---

**文档版本**: v1.0  
**更新日期**: 2026-01-13
