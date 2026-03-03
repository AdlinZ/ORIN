# RAGFlow 知识库集成模块

## 概述

RAGFlow 是 ORIN 系统的知识库集成模块，提供对 RAGFlow 服务的统一接入，支持知识库的创建、文档管理、向量检索等功能。

## 架构设计

```
orin-backend/
└── src/main/java/com/adlin/orin/
    ├── config/
    │   └── RAGFlowConfig.java         # RAGFlow RestTemplate 配置
    └── modules/knowledge/
        ├── service/
        │   ├── RAGFlowIntegrationService.java       # 服务接口
        │   └── impl/RAGFlowIntegrationServiceImpl.java # 服务实现
        └── controller/
            └── RAGFlowController.java               # REST API 接口
```

## 配置说明

### application.properties

```properties
# RAGFlow API 配置
ragflow.api.endpoint=https://api.ragflow.io/v1
ragflow.api.timeout=60000
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/ragflow/test-connection` | 测试 RAGFlow 连接 |
| POST | `/api/knowledge/ragflow/kb/create` | 创建知识库 |
| GET | `/api/knowledge/ragflow/kb/list` | 获取知识库列表 |
| GET | `/api/knowledge/ragflow/kb/{kbId}` | 获取知识库详情 |
| DELETE | `/api/knowledge/ragflow/kb/{kbId}` | 删除知识库 |
| POST | `/api/knowledge/ragflow/kb/{kbId}/document/upload` | 上传文档 |
| GET | `/api/knowledge/ragflow/kb/{kbId}/documents` | 获取文档列表 |
| DELETE | `/api/knowledge/ragflow/document/{docId}` | 删除文档 |
| POST | `/api/knowledge/ragflow/kb/{kbId}/retrieval` | 检索测试 |

## 功能特性

### 1. 知识库管理

- 创建/删除 RAGFlow 知识库
- 获取知识库列表和详情
- 同步知识库配置

### 2. 文档管理

- 支持 PDF、TXT、MD、DOCX 等格式
- 文档上传和删除
- 文档向量化状态跟踪

### 3. 检索测试

- 支持自定义查询语句
- 可配置返回结果数量 (topK)
- 返回相关性评分和摘要

## 使用示例

### 测试连接

```bash
curl -X POST http://localhost:8080/api/knowledge/ragflow/test-connection \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.ragflow.io/v1",
    "apiKey": "your-ragflow-api-key"
  }'
```

### 创建知识库

```bash
curl -X POST http://localhost:8080/api/knowledge/ragflow/kb/create \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.ragflow.io/v1",
    "apiKey": "your-ragflow-api-key",
    "name": "My Knowledge Base",
    "description": "Knowledge base for ORIN system"
  }'
```

### 上传文档

```bash
curl -X POST http://localhost:8080/api/knowledge/ragflow/kb/{kbId}/document/upload \
  -F "file=@document.pdf" \
  -F "endpointUrl=https://api.ragflow.io/v1" \
  -F "apiKey=your-ragflow-api-key"
```

### 检索测试

```bash
curl -X POST http://localhost:8080/api/knowledge/ragflow/kb/{kbId}/retrieval \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.ragflow.io/v1",
    "apiKey": "your-ragflow-api-key",
    "query": "What is ORIN?",
    "topK": 5
  }'
```

## 与 Dify 知识库对比

| 特性 | Dify | RAGFlow |
|------|------|---------|
| 部署方式 | SaaS / 自托管 | 自托管 |
| 向量引擎 | 内置 | 灵活配置 |
| 文档处理 | 基础分块 | 深度理解 |
| API 风格 | OpenAI 兼容 | OpenAI 兼容 |

## 注意事项

1. RAGFlow 需要自托管部署
2. 确保 API Key 具有相应权限
3. 文档上传后需要等待向量化完成
4. 检索结果受分块策略影响
