# ORIN API Gateway MVP Checker

这是一个独立的最小可用测试产品，用聊天方式验证 ORIN 后端是否正在提供 API 服务，以及统一网关聊天链路是否正常。

特点：

- 左侧是 API 配置区：后端地址、API Key、模型和超时。
- 右侧是聊天测试区：直接发送消息调用 `/v1/chat/completions`。
- 提供“测试连接”按钮，快速检查 `/v1/health` 和 `/v1/providers`。
- 提供用户友好的静态 Web 前端，不需要接入 ORIN 前端工程。
- 只依赖 Python 3 标准库，不依赖 ORIN 后端、前端或第三方包。
- 默认连接 `http://127.0.0.1:8080`。

## 前端产品运行

推荐使用内置 Python Web 服务启动。它会托管前端并代理检查请求到 ORIN 后端，因此不受 ORIN 后端 CORS 白名单和本地端口占用影响：

```bash
cd mvp/api-gateway-checker
python3 serve_gateway_checker.py
```

然后打开：

```text
http://127.0.0.1:5174
```

页面支持：

- 填写后端地址和 API Key。
- 填写模型名和超时时间。
- 一键测试网关连接。
- 直接通过聊天验证统一 API 是否能返回模型响应。

如果你只想使用纯静态托管，也可以运行：

```bash
python3 -m http.server 5173
```

静态托管模式下，浏览器会直接请求 ORIN 后端；这要求后端 CORS 允许当前前端地址。

## 快速运行

命令行检查器仍可直接运行：

```bash
python3 mvp/api-gateway-checker/check_orin_gateway.py
```

指定后端地址：

```bash
python3 mvp/api-gateway-checker/check_orin_gateway.py \
  --base-url http://127.0.0.1:8080
```

带 API Key 做认证链路检查：

```bash
ORIN_API_KEY=sk-orin-xxxx \
python3 mvp/api-gateway-checker/check_orin_gateway.py
```

检查管理端统一网关路由解析：

```bash
ORIN_API_KEY=sk-orin-xxxx \
python3 mvp/api-gateway-checker/check_orin_gateway.py \
  --gateway-route-test
```

检查完整聊天补全链路：

```bash
ORIN_API_KEY=sk-orin-xxxx \
python3 mvp/api-gateway-checker/check_orin_gateway.py \
  --run-chat \
  --chat-model Qwen/Qwen2.5-7B-Instruct
```

输出 JSON：

```bash
python3 mvp/api-gateway-checker/check_orin_gateway.py --json
```

## 命令行检查项

- `GET /v1`：统一 API 入口和核心端点声明。
- `GET /v1/docs`：统一 API 文档导航。
- `GET /v1/health`：统一网关健康状态和 Provider 统计。
- `GET /api/v1/health`：兼容健康检查端点。
- `GET /v1/capabilities`：OpenAI 兼容能力清单。
- `GET /v1/providers`：Provider 注册表可读性。
- `GET /v1/models` 无 API Key：确认受保护端点拒绝匿名调用。
- `GET /v1/models` 带 API Key：确认 OpenAI 兼容模型列表可访问。
- `POST /api/v1/system/gateway/routes/test`：可选，确认管理端路由解析是否能匹配探测路径。
- `POST /v1/chat/completions`：可选，确认推理链路能走到 Provider；如果返回 `503`，表示网关可达但当前无健康 Provider。

## 退出码

- `0`：没有失败项。警告和跳过项不会导致失败。
- `1`：存在失败项，例如后端不可达、公开网关入口异常、认证失败或聊天接口返回非预期错误。
