# ORIN 统一 API 用户文档

本文档面向调用方，提供统一入口、认证方式和最小可用示例。

## 1. 统一入口

- 统一入口索引：`GET /v1`
- 文档导航：`GET /v1/docs`
- 能力清单：`GET /v1/capabilities`
- OpenAI 兼容主入口：`/v1/*`

本地默认地址：`http://localhost:8080`

## 2. 认证

统一网关使用 API Key（推荐）：

- `Authorization: Bearer sk-orin-xxxx`
- 或 `X-API-Key: sk-orin-xxxx`

创建 API Key：管理端 `系统管理 -> API Key 管理`。

## 3. 快速开始

### 3.1 查看入口

```bash
curl http://localhost:8080/v1
```

### 3.2 健康检查

```bash
curl http://localhost:8080/v1/health
```

### 3.3 获取模型列表

```bash
curl -H "Authorization: Bearer <YOUR_API_KEY>" \
  http://localhost:8080/v1/models
```

### 3.4 聊天补全（OpenAI 兼容）

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Qwen/Qwen2.5-7B-Instruct",
    "messages": [
      {"role":"user","content":"你好，请简要介绍 ORIN 网关。"}
    ],
    "temperature": 0.7,
    "max_tokens": 128
  }'
```

### 3.5 文本向量

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "统一 API 文档示例"
  }'
```

## 4. 常见错误码

- `401`：缺少 API Key 或 API Key 无效
- `429`：命中限流
- `503`：当前无可用 Provider
- `500`：网关内部错误

建议保留响应头 `X-Trace-Id` 便于排查。

## 5. 调试与参考

- Swagger UI：`/swagger-ui/index.html`
- OpenAPI：`/v3/api-docs`
- 详细接口说明：`docs/API文档.md`
- 网关接入速查：`docs/统一网关对外接入快速指南.md`
