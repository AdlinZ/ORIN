# ORIN Gateway MVP 开发方向

> 面向后续开发 agent 的任务说明。目标不是把 ORIN 全平台一次性对外，而是先把“统一网关 / API 中转站”做成可上线、可演示、可小规模使用的第一阶段产品。

## 1. 背景判断

ORIN 当前已经具备 Agent、Workflow、Collaboration、MCP、知识库、系统治理等多条能力线，但完整平台直接对外上线的风险较高：

- 多智能体协作、复杂 Workflow、知识库和 MCP 依赖环境较多，公网演示难以保证稳定。
- 公开注册、多租户、在线充值、用户自带 provider key 等能力会显著增加安全与运营复杂度。
- 当前最容易形成真实闭环、也最容易对外解释的能力，是统一模型网关。

因此第一阶段建议收敛为：

> **ORIN Gateway：OpenAI 兼容的 AI API 中转与治理网关。**

英文口径：

> **ORIN Gateway: an OpenAI-compatible AI API relay with multi-provider routing, API key governance, quota control, audit logs, and cost visibility.**

产品策略：

```text
Gateway first, Agent platform later.
```

## 2. 第一阶段产品边界

### 2.1 要做

- OpenAI 兼容入口：
  - `GET /v1/models`
  - `POST /v1/chat/completions`
- 管理员配置上游 provider：
  - provider key
  - base URL
  - model id
  - model alias
  - enabled 状态
- API Key 生命周期：
  - 创建
  - 禁用 / 启用
  - 轮换
  - 删除
  - 最近调用历史
  - 调用摘要
- 调用治理：
  - API Key 鉴权
  - key 级限流
  - 日 / 月 token 配额
  - 统一错误码
  - 请求超时
  - 请求体大小限制
- 调用观测：
  - traceId / traceparent
  - 请求数
  - 成功 / 失败
  - 平均耗时
  - prompt tokens
  - completion tokens
  - total tokens
  - provider / model 分布
  - 成本估算
- 管理台收敛入口：
  - 网关总览
  - API Keys
  - 模型路由
  - 调用日志
  - 成本统计
- Smoke 验收：
  - 创建临时 API Key
  - 调 `/v1/models`
  - 调 `/v1/chat/completions`
  - 验证审计 / usage / trace 记录
  - 禁用 key 后再次调用返回 401

### 2.2 暂时不要做

- 公开注册
- 在线充值 / 账单支付
- 多租户套餐
- 用户自助接入任意 provider key
- Agent 市场
- Workflow 对外售卖
- MCP 任意命令执行对外开放
- 复杂路由策略，例如动态竞价、自动 fallback、多模型 ensemble
- 生产环境保存完整 prompt / response 明文

这些能力可以进入后续阶段，但不要阻塞 Gateway MVP。

## 3. 建议架构

第一阶段 Gateway 主要由 Java 后端承担，AI Engine 暂时不参与普通 `/v1/chat/completions` 中转链路。AI Engine 继续保留给 Workflow / Collaboration / MCP 执行。

```text
Client
  |
  | OpenAI-compatible request
  v
/v1/chat/completions
  |
  | API Key auth / rate limit / quota / traceId
  v
Model Router
  |
  | model alias -> provider model
  v
Provider Adapter
  |
  | DeepSeek / OpenAI / SiliconFlow / Kimi / Zhipu
  v
Audit + Usage + Cost
```

### 3.1 接口前缀

必须遵守现有接口前缀约束：

| 前缀 | 用途 | 鉴权 |
|------|------|------|
| `/v1/*` | 对外 OpenAI 兼容入口 | API Key |
| `/api/v1/*` | 内部业务接口 | JWT |
| `/api/system/*` | 系统集成 / MCP 管理 | JWT |

不要新建 `/gateway/*`、`/openai/*`、`/proxy/*` 等并行前缀。

### 3.2 推荐调用链

```text
/v1/chat/completions
  -> API Key filter
  -> quota / rate limit
  -> model alias resolver
  -> provider credential resolver
  -> provider adapter
  -> usage parser
  -> audit / usage / cost persistence
  -> OpenAI-compatible response
```

## 4. 数据与审计要求

每次 Gateway 调用至少记录：

```text
request_id
trace_id
api_key_id
user_id
provider
model_alias
provider_model
status
http_status
latency_ms
prompt_tokens
completion_tokens
total_tokens
estimated_cost
error_code
created_at
```

默认不要保存完整 prompt / response 明文。若已有字段会保存请求或响应内容，必须：

- 默认脱敏
- 不保存 Authorization / API Key / provider token
- 不保存密码、手机号、身份证等敏感字段
- 文档写清楚保存策略

## 5. 安全硬约束

- API Key 只能在创建或轮换时返回一次明文。
- API Key 入库必须哈希或按现有安全策略加密，不允许明文可逆展示。
- provider key 不能出现在日志、前端响应、审计明细中。
- `CORS_ALLOWED_ORIGINS` 生产环境不能为 `*`。
- `JWT_SECRET`、默认管理员密码、数据库密码、provider key 必须来自环境变量。
- `/v1/*` 使用 API Key 鉴权，不能混用 JWT。
- `/api/v1/*` 管理接口使用 JWT，不能用 `CLIENT_ACCESS` key 放行。
- demo 用户不能创建 / 轮换 / 删除真实 provider credential。
- 请求体必须有大小限制和超时限制。
- 上游错误要统一包装，不能把 provider 原始敏感响应直接透出。

## 6. 开发阶段建议

### Gateway-0：代码盘点与最小链路

目标：确认现有 `/v1/*` 已经能跑通，并列出缺口。

任务：

- 搜索 `/v1/chat/completions`、`/v1/models` 当前实现。
- 梳理 API Key filter、限流、审计、usage 统计代码。
- 梳理 provider adapter 和 model config 数据结构。
- 产出缺口清单，不急着改 UI。
- 补最小 API smoke：
  - 临时 key 创建
  - `/v1/models`
  - `/v1/chat/completions`
  - usage / audit 校验
  - 禁用 key 后 401

完成标准：

- 一个无需前端的 `scripts/gateway-smoke.sh` 能验证 Gateway 基线（已实现，详见 `scripts/gateway-smoke.sh`，覆盖：临时 key 创建 → `/v1/models` → `/v1/chat/completions` → usage / trace / audit 记录 → 禁用后 401 → 清理）。
- 若有改动只覆盖 Gateway 的核心 5 项链路而未触 `/v1/mcp` 时，跑 `gateway-smoke.sh`；否则继续跑 `business-smoke.sh`。

### Gateway-1：可上线安全基线

目标：能把 Gateway 放公网小范围使用。

任务：

- 生产环境默认凭据检查。
- CORS 固定域名。
- API Key quota / rate limit 强校验。
- provider key 脱敏审计。
- 请求体大小限制。
- 上游超时和错误码统一。
- 不保存完整 prompt / response，或默认关闭。
- 增加生产部署检查文档。

完成标准：

- 没有默认弱密码。
- 没有 `CORS *`。
- gitleaks / CodeQL / CI 通过。
- `gateway-smoke` 通过。

### Gateway-2：管理台产品化

目标：管理端像一个 API 中转站，而不是完整 Agent 平台的杂糅页面。

推荐入口：

```text
统一网关
├── 总览
├── API Keys
├── 模型路由
├── 调用日志
└── 成本统计
```

任务：

- 收敛 `/dashboard/control/gateway` 页面信息架构。
- 明确 `CLIENT_ACCESS` 与 provider credential 的区别。
- 增加模型 alias 映射展示。
- 增加 key 级调用历史。
- 增加成本估算图表。
- 增加 OpenAI SDK 接入示例复制。

完成标准：

- 用户能从页面理解：
  - 我有哪些 key
  - key 调了多少次
  - 哪个模型花了多少钱
  - 怎么用 curl / OpenAI SDK 接入

### Gateway-3：小规模公测

目标：可以给少量朋友或面试官发 key。

建议限制：

- 不开放注册。
- 管理员手工发 key。
- 每个 key 每天 100 次请求。
- 每个 key 每月 100k token。
- 只开放低成本模型。
- 流式输出先限制并发。
- demo 环境禁止高风险管理操作。

完成标准：

- 有公网域名和 HTTPS。
- README 有 Gateway 接入示例。
- 有 `v0.1.0` release note。
- 可以用 OpenAI SDK 调通：

```js
import OpenAI from "openai";

const client = new OpenAI({
  apiKey: "sk-orin-xxx",
  baseURL: "https://api.example.com/v1"
});

const completion = await client.chat.completions.create({
  model: "orin/deepseek-chat",
  messages: [{ role: "user", content: "hello" }]
});
```

## 7. 验收命令建议

开发 agent 完成 Gateway 相关任务后，至少运行：

```bash
# backend
cd orin-backend && mvn test

# frontend if gateway UI changed
cd orin-frontend && npm run test && npm run build

# full API smoke after services are up
bash scripts/business-smoke.sh
```

如果新增独立 Gateway smoke：

```bash
# Gateway MVP 专用 smoke：/v1/* + API Key 全生命周期 + usage/trace/audit
bash scripts/gateway-smoke.sh
```

环境变量（可选）：

- `ORIN_GATEWAY_SMOKE_REQUIRE_LIVE`：`auto`（默认）/ `0` / `1`，见脚本注释
- `ORIN_GATEWAY_SMOKE_MODEL`：留空走 `/v1/models` 自发现，否则用固定 model id
- `ORIN_GATEWAY_SMOKE_TIMEOUT_SECONDS`：默认 30

不要用 `--skip-tests`，不要注释断言。

## 8. 对外叙事建议

README 或官网可以这样写：

> ORIN 当前以统一模型网关作为第一阶段公开能力，提供 OpenAI 兼容 API 中转、多 provider 路由、API Key 生命周期、限流配额、调用审计和成本统计；Agent / Workflow / MCP 作为平台扩展能力继续演进。

不要写：

- “企业级 Agent 平台已完整上线”
- “支持任意 MCP 安全执行”
- “生产级多租户 SaaS”
- “完整计费系统”

当前阶段的关键词：

- OpenAI-compatible
- API Gateway
- Multi-provider routing
- API key governance
- Quota and rate limiting
- Audit and cost visibility
- Gateway first, Agent platform later

## 9. 下一位开发 agent 的第一步

请先完成以下只读盘点，再决定修改范围：

```bash
rg "/v1/chat|chat/completions|/v1/models|CLIENT_ACCESS|api-keys|Gateway|provider" orin-backend/src/main/java
rg "gateway|apiKey|models|chat/completions" orin-frontend/src
sed -n '1,220p' docs/API文档.md
sed -n '1,220p' docs/功能完成度.md
```

然后输出：

1. 当前 `/v1/chat/completions` 是否真实转发 provider。
2. 当前 `/v1/models` 是否返回可用模型 alias。
3. API Key 禁用、轮换、限流、配额是否在 `/v1/*` 强制执行。
4. 调用日志是否记录 usage、latency、error code。
5. 哪些能力已有 smoke，哪些缺独立 Gateway smoke。

在此基础上做最小 PR，优先补闭环和验收，不要先扩菜单。
