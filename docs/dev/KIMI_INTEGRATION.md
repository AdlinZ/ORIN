# Kimi 大模型集成模块

## 概述

Kimi 是 ORIN 系统的大模型集成模块，提供对 Kimi (月之暗面) API 的统一接入，支持聊天对话、视觉理解等功能。

## 架构设计

```
orin-backend/
└── src/main/java/com/adlin/orin/
    └── modules/
        ├── model/
        │   └── service/
        │       ├── KimiIntegrationService.java           # Kimi 服务接口
        │       └── impl/KimiIntegrationServiceImpl.java  # 服务实现
        ├── agent/
        │   └── service/
        │       └── KimiAgentManageService.java           # Kimi 智能体管理
        └── multimodal/
            └── service/
                └── VisualAnalysisService.java            # 视觉理解服务
```

## 配置说明

### application.properties

```properties
# Kimi API 配置
kimi.default.endpoint=https://api.moonshot.cn/v1
kimi.default.model=moonshot-v1-8k-chat
```

## API 接口

### Kimi 服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/models/kimi/test-connection` | 测试 Kimi 连接 |
| POST | `/api/models/kimi/chat` | 发送聊天消息 |
| GET | `/api/models/kimi/models` | 获取可用模型列表 |

### 视觉理解

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/multimodal/analyze` | 分析图片内容 |

## 功能特性

### 1. 聊天对话

- 支持单轮和多轮对话
- 可配置模型参数 (temperature, top_p, max_tokens)
- 支持流式响应

### 2. 模型管理

- 支持多种 Kimi 模型:
  - `moonshot-v1-8k-chat` (8K 上下文)
  - `moonshot-v1-32k-chat` (32K 上下文)
  - `moonshot-v1-128k-chat` (128K 上下文)
- 模型列表动态获取

### 3. 视觉理解

- 支持图片输入分析
- 图像描述和问答
- 图表理解

## 使用示例

### 测试连接

```bash
curl -X POST http://localhost:8080/api/models/kimi/test-connection \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.moonshot.cn/v1",
    "apiKey": "your-kimi-api-key"
  }'
```

### 发送聊天消息

```bash
curl -X POST http://localhost:8080/api/models/kimi/chat \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.moonshot.cn/v1",
    "apiKey": "your-kimi-api-key",
    "model": "moonshot-v1-8k-chat",
    "message": "Hello, Kimi!"
  }'
```

### 带完整参数的聊天

```bash
curl -X POST http://localhost:8080/api/models/kimi/chat \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "https://api.moonshot.cn/v1",
    "apiKey": "your-kimi-api-key",
    "model": "moonshot-v1-8k-chat",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "What is AI?"}
    ],
    "temperature": 0.7,
    "topP": 0.9,
    "maxTokens": 1000
  }'
```

### 视觉理解分析

```bash
curl -X POST http://localhost:8080/api/multimodal/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "imageUrl": "https://example.com/image.jpg",
    "prompt": "Describe this image"
  }'
```

## 模型参数说明

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| model | string | 模型名称 | moonshot-v1-8k-chat |
| messages | array | 消息列表 | - |
| temperature | float | 采样温度 (0-2) | 0.7 |
| top_p | float | Top P 采样 (0-1) | 1.0 |
| max_tokens | int | 最大生成 token 数 | 1024 |

## 与其他模型对比

| 特性 | Kimi | OpenAI | SiliconFlow |
|------|------|--------|-------------|
| 上下文长度 | 最长 128K | 128K | 视模型而定 |
| 中文能力 | 优秀 | 优秀 | 一般 |
| 视觉支持 | 支持 | 支持 | 视模型而定 |
| API 风格 | OpenAI 兼容 | OpenAI 兼容 | OpenAI 兼容 |

## 注意事项

1. 需要月之暗面平台 API Key
2. 不同模型有不同的速率限制
3. 长上下文模型费用较高
4. 图片需要可公开访问的 URL
