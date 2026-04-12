<template>
  <div class="api-doc-page" :class="{ 'guide-mode': activeDoc === 'guide' }">
    <aside class="left-panel">
      <div class="logo">ORIN</div>
      <el-input v-model="search" placeholder="搜索..." :prefix-icon="Search" clearable class="search" />

      <div class="doc-select">
        <el-dropdown class="doc-dropdown" trigger="click" @command="changeDocType">
          <div class="doc-dropdown-trigger">
            <span>{{ activeDocLabel }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="doc-dropdown-menu">
              <el-dropdown-item command="guide" :class="{ selected: activeDoc === 'guide' }">
                使用 ORIN
              </el-dropdown-item>
              <el-dropdown-item command="api" :class="{ selected: activeDoc === 'api' }">
                API 文档
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="tree">
        <template v-if="activeDoc === 'guide'">
          <div class="group-title">系统指南</div>
          
          <template v-for="(group, catName) in articlesGrouped" :key="catName">
            <div class="sub-title">{{ catName }}</div>
            <a 
              v-for="article in group" 
              :key="article.id"
              :href="`#guide-${article.id}`"
              class="endpoint" 
              :class="{ active: selectedArticle?.id === article.id }"
              @click.prevent="selectArticle(article)"
            >
              <span>{{ article.title }}</span>
            </a>
          </template>
        </template>

        <template v-else>
          <div class="group-title">Unified API</div>
          <div class="sub-title">基础信息</div>
          <a href="#api-index" class="endpoint">
            <span class="method get">GET</span>
            <span>API 入口</span>
          </a>
          <a href="#capabilities" class="endpoint">
            <span class="method get">GET</span>
            <span>能力清单</span>
          </a>
          <a href="#health" class="endpoint">
            <span class="method get">GET</span>
            <span>健康检查</span>
          </a>

          <div class="sub-title">模型与 Provider</div>
          <a href="#models" class="endpoint">
            <span class="method get">GET</span>
            <span>模型列表</span>
          </a>
          <a href="#providers" class="endpoint">
            <span class="method get">GET</span>
            <span>Provider 列表</span>
          </a>
          <a href="#routing-stats" class="endpoint">
            <span class="method get">GET</span>
            <span>路由统计</span>
          </a>

          <div class="sub-title">对话与嵌入</div>
          <a href="#chat-message" class="endpoint">
            <span class="method post">POST</span>
            <span>聊天完成</span>
          </a>
          <a href="#chat-stream" class="endpoint">
            <span class="method post">POST</span>
            <span>聊天完成（流式）</span>
          </a>
          <a href="#embeddings" class="endpoint">
            <span class="method post">POST</span>
            <span>文本嵌入</span>
          </a>

          <div class="sub-title">工作流</div>
          <a href="#workflow-execute" class="endpoint">
            <span class="method post">POST</span>
            <span>执行工作流</span>
          </a>
        </template>
      </div>
    </aside>

    <main class="center-panel">
      <template v-if="activeDoc === 'guide'">
        <!-- 动态文章详情 -->
        <div v-if="selectedArticle" class="article-content">
          <div class="head-tag">{{ selectedArticle.category }}</div>
          <h1 :id="`guide-${selectedArticle.id}`">{{ selectedArticle.title }}</h1>
          
          <div class="article-meta">
            <span class="meta-item">
              <el-icon><View /></el-icon>
              {{ selectedArticle.viewCount }} 次阅读
            </span>
            <span class="meta-item">更新时间：{{ formatDate(selectedArticle.updatedAt) }}</span>
          </div>

          <el-divider />

          <div class="markdown-body" v-html="articleContentHtml" />
        </div>
        
        <!-- 静态默认欢迎页 (未选中文章时展示) -->
        <div v-else>
          <div class="head-tag">入门</div>
        <h1 id="orin-intro">介绍</h1>
        <p class="lead">ORIN 是一个统一 AI 网关与智能体平台：你可以用一套 OpenAI 兼容接口，接入多个模型与 Provider，并配合工作流、监控和权限体系进行生产化落地。</p>
        <section class="section">
          <h2>你可以用 ORIN 做什么</h2>
          <div class="field">
            <div class="field-name">统一接入多模型</div>
            <p>通过 <code>/v1/chat/completions</code> 与 <code>/v1/embeddings</code> 统一调用不同模型，无需改业务代码。</p>
          </div>
          <div class="field">
            <div class="field-name">可观测与可治理</div>
            <p>结合健康检查、路由统计、审计和告警，快速定位问题并优化成本与时延。</p>
          </div>
          <div class="field">
            <div class="field-name">工作流与智能体协作</div>
            <p>将模型能力和业务流程组合成可复用的自动化链路。</p>
          </div>
        </section>

        <!-- 教程：30 分钟快速入门 -->
        <section id="orin-quickstart" class="section">
          <div class="head-tag">入门</div>
          <h1>30 分钟快速入门</h1>
          <p class="lead">建议按下面 4 步完成最小可用闭环。</p>
          <section class="section">
            <h2>步骤清单</h2>
            <div class="field">
              <div class="field-name">1. 获取 API Key</div>
              <p>在系统管理中创建 Key，调用时使用 <code>Authorization: Bearer {'{API_KEY}'}</code>。</p>
            </div>
            <div class="field">
              <div class="field-name">2. 查看可用模型</div>
              <p>通过 <code>GET /v1/models</code> 确认可用模型 ID。</p>
            </div>
            <div class="field">
              <div class="field-name">3. 发起首次对话请求</div>
              <p>调用 <code>POST /v1/chat/completions</code>，验证基础链路通畅。</p>
            </div>
            <div class="field">
              <div class="field-name">4. 检查健康与路由状态</div>
              <p>调用 <code>/v1/health</code> 与 <code>/v1/routing/stats</code> 观察服务状态和分发情况。</p>
            </div>
          </section>
        </section>

        <!-- 教程：核心概念 -->
        <section id="orin-core-concepts" class="section">
          <div class="head-tag">入门</div>
          <h1>核心概念</h1>
          <p class="lead">理解这些概念后，后续接入会更顺滑。</p>
          <section class="section">
            <h2>概念说明</h2>
            <div class="field">
              <div class="field-name">Provider</div>
              <p>底层模型服务提供方（如 OpenAI、SiliconFlow）。</p>
            </div>
            <div class="field">
              <div class="field-name">Model</div>
              <p>可被调用的模型标识（如 <code>gpt-4o</code>、<code>Qwen/Qwen2.5-7B-Instruct</code>）。</p>
            </div>
            <div class="field">
              <div class="field-name">Routing Strategy</div>
              <p>请求路由策略：成本优先、轮询、随机等。</p>
            </div>
            <div class="field">
              <div class="field-name">Trace ID</div>
              <p>链路追踪 ID，用于日志、监控与排障关联。</p>
            </div>
          </section>
        </section>

        <!-- 教程：第一个请求 -->
        <section id="orin-first-request" class="section">
          <div class="head-tag">入门</div>
          <h1>第一个请求</h1>
          <p class="lead">先用 cURL 跑通，再接入你的业务代码。</p>
          <section class="section">
            <h2>示例 cURL</h2>
            <pre class="code-block">curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Qwen/Qwen2.5-7B-Instruct",
    "messages": [{"role":"user","content":"你好，请介绍一下 ORIN。"}],
    "temperature": 0.7
  }'</pre>
          </section>
        </section>
        </div>
      </template>

      <template v-else>

      <!-- API 入口 -->
      <div class="head-tag">基础信息</div>
      <h1 id="api-index">API 入口</h1>
      <p class="lead">统一 API 根路径，返回入口信息和文档链接。</p>
      <div class="op-bar">
        <div class="left">
          <span class="method-chip">GET</span>
          <code>/v1</code>
        </div>
      </div>
      <section class="section">
        <h2>响应示例</h2>
        <pre class="code-block">{
  "name": "ORIN Unified API",
  "version": "v1",
  "description": "OpenAI兼容统一网关入口",
  "docs": {
    "swagger": "/swagger-ui/index.html",
    "openapi": "/v3/api-docs",
    "guide": "/v1/docs",
    "userDocInRepo": "docs/统一API用户文档.md"
  },
  "endpoints": {
    "health": "/v1/health",
    "models": "/v1/models",
    "chatCompletions": "/v1/chat/completions",
    "embeddings": "/v1/embeddings",
    "capabilities": "/v1/capabilities"
  }
}</pre>
      </section>

      <!-- 能力清单 -->
      <section id="capabilities" class="section">
        <div class="head-tag">基础信息</div>
        <h1>能力清单</h1>
        <p class="lead">返回当前统一网关暴露的所有能力分组和 Provider 统计。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip get">GET</span>
            <code>/v1/capabilities</code>
          </div>
        </div>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "gateway": "openai-compatible",
  "capabilities": {
    "chat": "/v1/chat/completions",
    "embeddings": "/v1/embeddings",
    "models": "/v1/models",
    "workflowExecution": "/v1/workflows/{workflowId}/execute"
  },
  "providerStatistics": {
    "total": 5,
    "healthy": 4
  }
}</pre>
        </section>
      </section>

      <!-- 健康检查 -->
      <section id="health" class="section">
        <div class="head-tag">基础信息</div>
        <h1>健康检查</h1>
        <p class="lead">检查所有已注册 Provider 的健康状态。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip get">GET</span>
            <code>/v1/health</code>
          </div>
        </div>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "status": "ok",
  "providers": {
    "openai": { "status": "healthy", "latencyMs": 120 },
    "anthropic": { "status": "healthy", "latencyMs": 85 }
  },
  "statistics": { "total": 5, "healthy": 4 }
}</pre>
        </section>
      </section>

      <!-- 模型列表 -->
      <section id="models" class="section">
        <div class="head-tag">模型与 Provider</div>
        <h1>模型列表</h1>
        <p class="lead">获取所有可用 Provider 支持的模型列表（OpenAI 兼容格式）。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip get">GET</span>
            <code>/v1/models</code>
          </div>
        </div>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "object": "list",
  "data": [
    {
      "id": "Qwen/Qwen2.5-7B-Instruct",
      "object": "model",
      "owned_by": "qwen",
      "provider": "siliconflow"
    },
    {
      "id": "gpt-4o",
      "object": "model",
      "owned_by": "openai",
      "provider": "openai"
    }
  ]
}</pre>
        </section>
      </section>

      <!-- Provider 列表 -->
      <section id="providers" class="section">
        <div class="head-tag">模型与 Provider</div>
        <h1>Provider 列表</h1>
        <p class="lead">获取所有已注册 Provider 的详细信息和统计。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip get">GET</span>
            <code>/v1/providers</code>
          </div>
        </div>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "providers": [
    {
      "id": "provider-1",
      "name": "SiliconFlow",
      "type": "openai-compatible",
      "status": "healthy",
      "models": ["Qwen/Qwen2.5-7B-Instruct", "deepseek-ai/DeepSeek-V3"]
    }
  ],
  "statistics": { "total": 5, "healthy": 4 }
}</pre>
        </section>
      </section>

      <!-- 路由统计 -->
      <section id="routing-stats" class="section">
        <div class="head-tag">模型与 Provider</div>
        <h1>路由统计</h1>
        <p class="lead">获取路由服务的详细统计信息，包括各模型的调用次数和平均延迟。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip get">GET</span>
            <code>/v1/routing/stats</code>
          </div>
        </div>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "totalRequests": 1523,
  "byModel": {
    "Qwen/Qwen2.5-7B-Instruct": { "requests": 800, "avgLatencyMs": 320 },
    "gpt-4o": { "requests": 723, "avgLatencyMs": 580 }
  },
  "byProvider": {
    "siliconflow": { "requests": 800, "healthy": true },
    "openai": { "requests": 723, "healthy": true }
  }
}</pre>
        </section>
      </section>

      <!-- 聊天完成 -->
      <section id="chat-message" class="section">
        <div class="head-tag">对话与嵌入</div>
        <h1>聊天完成</h1>
        <p class="lead">OpenAI 兼容的聊天完成接口，支持流式和非流式响应。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip post">POST</span>
            <code>/v1/chat/completions</code>
          </div>
          <div class="right-actions">
            <el-button type="primary" plain @click="copyCurl">复制 cURL</el-button>
          </div>
        </div>

        <section class="section">
          <h2>请求头</h2>
          <div class="field">
            <div class="field-name">Authorization <span class="type">string</span> <span class="req">必填</span></div>
            <p>API Key 认证。格式：<code>Authorization: Bearer {'{API_KEY}'}</code></p>
          </div>
          <div class="field">
            <div class="field-name">X-Provider-Id <span class="type">string</span></div>
            <p>指定使用某个 Provider（可选，默认按模型智能路由）。</p>
          </div>
          <div class="field">
            <div class="field-name">X-Routing-Strategy <span class="type">string</span></div>
            <p>路由策略：<code>LOWEST_COST</code>（默认）、<code>ROUND_ROBIN</code>、<code>RANDOM</code>。</p>
          </div>
          <div class="field">
            <div class="field-name">X-Trace-Id <span class="type">string</span></div>
            <p>自定义 Trace ID，用于链路追踪（可选）。</p>
          </div>
        </section>

        <section class="section">
          <h2>请求体 <span class="mime">application/json</span></h2>
          <div class="field">
            <div class="field-name">model <span class="type">string</span> <span class="req">必填</span></div>
            <p>模型名称，例如 <code>Qwen/Qwen2.5-7B-Instruct</code></p>
          </div>
          <div class="field">
            <div class="field-name">messages <span class="type">array</span> <span class="req">必填</span></div>
            <p>对话消息数组，格式同 OpenAI。</p>
          </div>
          <div class="field">
            <div class="field-name">stream <span class="type">boolean</span></div>
            <p>是否启用流式响应，默认 <code>false</code>。</p>
          </div>
          <div class="field">
            <div class="field-name">temperature <span class="type">number</span></div>
            <p>采样温度，默认 0.7。</p>
          </div>
          <div class="field">
            <div class="field-name">max_tokens <span class="type">integer</span></div>
            <p>最大生成 Token 数，默认 128。</p>
          </div>
        </section>

        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1712222222,
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "你好，我是 ORIN Unified API。" },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 128,
    "completion_tokens": 64,
    "total_tokens": 192
  }
}</pre>
        </section>
      </section>

      <!-- 聊天完成（流式） -->
      <section id="chat-stream" class="section">
        <div class="head-tag">对话与嵌入</div>
        <h1>聊天完成（流式）</h1>
        <p class="lead">SSE 流式聊天完成接口，逐块返回增量内容。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip post">POST</span>
            <code>/v1/chat/completions/stream</code>
          </div>
        </div>
        <section class="section">
          <h2>请求头</h2>
          <div class="field">
            <div class="field-name">Authorization <span class="type">string</span> <span class="req">必填</span></div>
            <p>API Key 认证。格式：<code>Authorization: Bearer {'{API_KEY}'}</code></p>
          </div>
        </section>
        <section class="section">
          <h2>请求体</h2>
          <p>同 <code>/v1/chat/completions</code>，需额外传递 <code>stream: true</code>。</p>
        </section>
        <section class="section">
          <h2>SSE 响应示例</h2>
          <pre class="code-block">event: message
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"你好"}}]}

event: message
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"，"}}]}

event: done
data: [DONE]</pre>
        </section>
      </section>

      <!-- 文本嵌入 -->
      <section id="embeddings" class="section">
        <div class="head-tag">对话与嵌入</div>
        <h1>文本嵌入</h1>
        <p class="lead">OpenAI 兼容的文本嵌入接口，将文本转换为向量表示。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip post">POST</span>
            <code>/v1/embeddings</code>
          </div>
        </div>
        <section class="section">
          <h2>请求头</h2>
          <div class="field">
            <div class="field-name">Authorization <span class="type">string</span> <span class="req">必填</span></div>
            <p>API Key 认证。格式：<code>Authorization: Bearer {'{API_KEY}'}</code></p>
          </div>
        </section>
        <section class="section">
          <h2>请求体 <span class="mime">application/json</span></h2>
          <div class="field">
            <div class="field-name">model <span class="type">string</span> <span class="req">必填</span></div>
            <p>嵌入模型名称，例如 <code>text-embedding-3-small</code></p>
          </div>
          <div class="field">
            <div class="field-name">input <span class="type">string | array</span> <span class="req">必填</span></div>
            <p>待嵌入的文本，可以是单条字符串或字符串数组（最大 2048 条）。</p>
          </div>
        </section>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.123, -0.456, 0.789, ...],
      "index": 0
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}</pre>
        </section>
      </section>

      <!-- 执行工作流 -->
      <section id="workflow-execute" class="section">
        <div class="head-tag">工作流</div>
        <h1>执行工作流</h1>
        <p class="lead">触发指定工作流的异步执行，支持优先级控制。</p>
        <div class="op-bar">
          <div class="left">
            <span class="method-chip post">POST</span>
            <code>/v1/workflows/{workflowId}/execute</code>
          </div>
        </div>
        <section class="section">
          <h2>路径参数</h2>
          <div class="field">
            <div class="field-name">workflowId <span class="type">string</span> <span class="req">必填</span></div>
            <p>工作流 ID（Long 类型）。</p>
          </div>
        </section>
        <section class="section">
          <h2>查询参数</h2>
          <div class="field">
            <div class="field-name">priority <span class="type">string</span></div>
            <p>任务优先级：<code>LOW</code>、<code>NORMAL</code>（默认）、<code>HIGH</code>、<code>HIGHEST</code>。</p>
          </div>
        </section>
        <section class="section">
          <h2>请求体 <span class="mime">application/json</span></h2>
          <div class="field">
            <div class="field-name">input <span class="type">object</span> <span class="req">必填</span></div>
            <p>工作流输入参数，键值对形式。</p>
          </div>
          <div class="field">
            <div class="field-name">Authorization <span class="type">string</span> <span class="req">必填</span></div>
            <p>API Key 认证。格式：<code>Authorization: Bearer {'{API_KEY}'}</code></p>
          </div>
        </section>
        <section class="section">
          <h2>响应示例</h2>
          <pre class="code-block">{
  "taskId": "task-12345",
  "workflowId": 1,
  "status": "PENDING",
  "message": "Workflow execution queued"
}</pre>
        </section>
      </section>

      <!-- 在线调试 -->
      <section id="try-run" class="section">
        <h2>在线调试</h2>
        <p class="lead">在当前页面直接发起请求，返回结果展示在右侧面板。</p>
        <div class="playground">
          <div class="playground-label">Endpoint</div>
          <el-select v-model="selectedEndpoint" placeholder="选择接口" style="width: 100%; margin-bottom: 12px">
            <el-option label="POST /v1/chat/completions" value="/v1/chat/completions" />
            <el-option label="POST /v1/embeddings" value="/v1/embeddings" />
            <el-option label="GET /v1/models" value="/v1/models" />
            <el-option label="GET /v1/health" value="/v1/health" />
            <el-option label="GET /v1/providers" value="/v1/providers" />
            <el-option label="GET /v1/capabilities" value="/v1/capabilities" />
            <el-option label="GET /v1/routing/stats" value="/v1/routing/stats" />
            <el-option label="GET /v1" value="/v1" />
          </el-select>
          <div class="playground-label">API Key</div>
          <el-input
            v-model="apiKey"
            type="password"
            show-password
            placeholder="sk-orin-..."
            autocomplete="off"
          />
          <div class="playground-label">Request Body / Params (JSON)</div>
          <el-input
            v-model="requestBodyText"
            type="textarea"
            :autosize="{ minRows: 8, maxRows: 16 }"
            spellcheck="false"
          />
          <div class="playground-actions">
            <el-button :loading="sending" type="primary" @click="runTryRequest">发送请求</el-button>
            <el-button @click="resetRequestBody">重置</el-button>
          </div>
        </div>
      </section>
      </template>
    </main>

    <aside v-if="activeDoc === 'api'" class="right-panel">
      <div class="card">
        <div class="card-title">Error {{ errorStatusText }}</div>
        <pre class="code">{{ errorText || defaultErrorExample }}</pre>
      </div>

      <div class="card">
        <div class="card-title">
          {{ successTitle }}
          <span v-if="latencyMs !== null" class="latency">{{ latencyMs }} ms</span>
        </div>
        <pre class="code">{{ responseText || responseExample }}</pre>
      </div>
    </aside>
  </div>
</template>

<script setup>
import axios from 'axios'
import request from '@/utils/request'
import { computed, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Search, View } from '@element-plus/icons-vue'
import { marked } from 'marked'
import {
  getHelpArticles,
  getHelpArticle,
  getHelpCategories,
  searchHelpArticles
} from '@/api/help'
import dayjs from 'dayjs'

const search = ref('')
const sending = ref(false)
const apiKey = ref('')
const latencyMs = ref(null)
const responseStatus = ref(null)
const errorStatus = ref(null)
const responseText = ref('')
const errorText = ref('')
const selectedEndpoint = ref('/v1/chat/completions')
const activeDoc = ref('api')
const endpointMethodMap = {
  '/v1/chat/completions': 'POST',
  '/v1/embeddings': 'POST',
  '/v1/models': 'GET',
  '/v1/health': 'GET',
  '/v1/providers': 'GET',
  '/v1/capabilities': 'GET',
  '/v1/routing/stats': 'GET',
  '/v1': 'GET'
}

const defaultBody = {
  model: 'Qwen/Qwen2.5-7B-Instruct',
  messages: [{ role: 'user', content: '你好，请简单介绍一下你自己。' }],
  temperature: 0.7,
  max_tokens: 128
}
const requestBodyText = ref(JSON.stringify(defaultBody, null, 2))

const defaultErrorExample = `{
  "code": "AUTH_API_KEY_INVALID",
  "message": "Missing API key"
}`

const responseExample = `{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1712222222,
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "你好，我是 ORIN Unified API。" },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 128,
    "completion_tokens": 64,
    "total_tokens": 192
  }
}`

const successTitle = computed(() => responseStatus.value ? String(responseStatus.value) : '200')
const errorStatusText = computed(() => errorStatus.value ? String(errorStatus.value) : '')
const activeDocLabel = computed(() => activeDoc.value === 'guide' ? '使用 ORIN' : 'API 文档')

// Help Center / Guide Logic
const categories = ref([])
const articlesGrouped = ref({})
const isSearching = ref(false)
const selectedArticle = ref(null)
const articleContentHtml = ref('')

onMounted(async () => {
  await fetchCategoriesAndArticles()
  
  // Handle auto open from hash
  const hash = window.location.hash
  if (hash && hash.startsWith('#guide-')) {
    const id = hash.replace('#guide-', '')
    activeDoc.value = 'guide'
    await selectArticle({ id })
  }
})

const fetchCategoriesAndArticles = async () => {
  try {
    const catsRes = await getHelpCategories()
    categories.value = catsRes.data || catsRes || []
    
    // Fetch all articles to build the tree
    const articlesRes = await getHelpArticles({ page: 0, size: 1000 })
    const allArticles = (articlesRes.data?.content || articlesRes.data) || []
    
    const grouped = {}
    categories.value.forEach(c => grouped[c] = [])
    
    allArticles.forEach(item => {
      const cat = item.category || '未分类'
      if (!grouped[cat]) grouped[cat] = []
      grouped[cat].push(item)
    })
    
    const finalGrouped = {}
    Object.keys(grouped).forEach(k => {
      if (grouped[k] && grouped[k].length > 0) {
        finalGrouped[k] = grouped[k]
      }
    })
    
    articlesGrouped.value = finalGrouped
  } catch (e) {
    console.error('获取帮助文章失败:', e)
  }
}

// Searching logic using watch
let searchTimeout = null
watch(search, (newVal) => {
  if (activeDoc.value !== 'guide') return
  
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(async () => {
    if (!newVal.trim()) {
      isSearching.value = false
      await fetchCategoriesAndArticles()
      return
    }
    isSearching.value = true
    try {
      const res = await searchHelpArticles(newVal, 0, 50)
      const data = res.data || res
      const results = data.content || data || []
      articlesGrouped.value = {
        '搜索结果': results
      }
    } catch(e) {
      console.error(e)
    }
  }, 300)
})

const selectArticle = async (article) => {
  try {
    const res = await getHelpArticle(article.id)
    selectedArticle.value = res.data || res
    
    let md = selectedArticle.value.content || ''
    try {
      articleContentHtml.value = marked(md)
    } catch(e) {
      articleContentHtml.value = md
    }
    
    window.location.hash = `#guide-${article.id}`
  } catch(e) {
    ElMessage.error('获取文章内容失败')
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  if (Array.isArray(date)) {
    return dayjs(new Date(date[0], date[1] - 1, date[2], date[3] || 0, date[4] || 0)).format('YYYY-MM-DD HH:mm')
  }
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const changeDocType = (docType) => {
  activeDoc.value = docType
  if (docType === 'guide') {
    if (selectedArticle.value) {
      window.location.hash = `#guide-${selectedArticle.value.id}`
    } else {
      window.location.hash = '#orin-intro'
    }
    return
  }
  window.location.hash = '#api-index'
}

const copyCurl = async () => {
  const method = endpointMethodMap[selectedEndpoint.value] || 'POST'
  const curl = `curl -X ${method} http://localhost:5173${selectedEndpoint.value} \\
  -H "Authorization: Bearer ${apiKey.value || '<YOUR_API_KEY>'}" \\
  -H "Content-Type: application/json" \\
  -d '${requestBodyText.value.replace(/\n/g, '')}'`
  try {
    await navigator.clipboard.writeText(curl)
    ElMessage.success('已复制 cURL')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const resetRequestBody = () => {
  requestBodyText.value = JSON.stringify(defaultBody, null, 2)
}

const runTryRequest = async () => {
  if (!apiKey.value.trim()) {
    ElMessage.warning('请先输入 API Key')
    return
  }

  const isGet = endpointMethodMap[selectedEndpoint.value] === 'GET'
  let payload = null

  if (!isGet) {
    try {
      payload = JSON.parse(requestBodyText.value)
    } catch {
      ElMessage.error('请求体不是合法 JSON')
      return
    }
  }

  sending.value = true
  responseText.value = ''
  errorText.value = ''
  responseStatus.value = null
  errorStatus.value = null
  latencyMs.value = null

  const startedAt = performance.now()
  try {
    const config = {
      headers: {
        Authorization: `Bearer ${apiKey.value.trim()}`,
        'Content-Type': 'application/json'
      },
      timeout: 120000
    }
    const res = isGet
      ? await request.get(selectedEndpoint.value, config)
      : await request.post(selectedEndpoint.value, payload, config)
    responseStatus.value = res.status
    responseText.value = JSON.stringify(res.data, null, 2)
    ElMessage.success('请求成功')
  } catch (err) {
    const status = err?.response?.status || 'ERROR'
    errorStatus.value = status
    const errorData = err?.response?.data || { message: err?.message || 'Request failed' }
    errorText.value = JSON.stringify(errorData, null, 2)
    ElMessage.error(`请求失败: ${status}`)
  } finally {
    latencyMs.value = Math.round(performance.now() - startedAt)
    sending.value = false
  }
}
</script>

<style scoped>
.api-doc-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 480px;
  background: #f8fafc;
}
.api-doc-page.guide-mode {
  grid-template-columns: 320px minmax(0, 1fr);
}
.left-panel {
  background: #fff;
  border-right: 1px solid #e5e7eb;
  padding: 18px 16px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
}
.logo {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 14px;
}
.search {
  margin-bottom: 14px;
}
.doc-select {
  margin-bottom: 18px;
}
.doc-dropdown {
  width: 100%;
}
.doc-dropdown-trigger {
  width: 100%;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #eff6ff;
  color: #0f172a;
  font-weight: 700;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  cursor: pointer;
  box-sizing: border-box;
}
:deep(.doc-dropdown-menu .el-dropdown-menu__item.selected) {
  color: #1d4ed8;
  font-weight: 700;
}
.group-title {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 8px;
}
.sub-title {
  color: #64748b;
  font-size: 14px;
  margin: 14px 0 6px;
}
.endpoint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1e293b;
  text-decoration: none;
  padding: 8px;
  border-radius: 8px;
}
.endpoint:hover, .endpoint.active {
  background: #eff6ff;
}
.method {
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  padding: 2px 8px;
}
.method.post {
  background: #dbeafe;
  color: #1d4ed8;
}
.method.get {
  background: #dcfce7;
  color: #15803d;
}
.center-panel {
  padding: 38px 52px 60px;
}
.head-tag {
  color: #2563eb;
  font-weight: 600;
}
h1 {
  margin: 10px 0 8px;
  font-size: 52px;
  line-height: 1.05;
  letter-spacing: -0.02em;
}
.lead {
  color: #475569;
  margin-bottom: 18px;
}
.op-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  padding: 10px 12px;
}
.op-bar .left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.right-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.method-chip {
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 700;
  font-size: 12px;
  border-radius: 999px;
  padding: 4px 10px;
}
.section {
  margin-top: 34px;
}
.playground {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 12px;
  padding: 14px;
}
.playground-label {
  font-size: 13px;
  color: #475569;
  margin: 12px 0 8px;
  font-weight: 600;
}
.playground-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
h2 {
  font-size: 40px;
  margin: 0 0 12px;
}
.mime {
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}
.field {
  border-top: 1px solid #e5e7eb;
  padding: 14px 0;
}
.field-name {
  font-size: 22px;
  font-weight: 700;
}
.type {
  margin-left: 8px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}
.req {
  margin-left: 8px;
  background: #fee2e2;
  color: #b91c1c;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}
.right-panel {
  padding: 28px 18px;
  border-left: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
}
.card {
  background: #fff;
  border: 1px solid #dbe1ea;
  border-radius: 16px;
  margin-bottom: 14px;
  overflow: hidden;
}
.card-title {
  background: #f1f5f9;
  color: #2563eb;
  font-weight: 700;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.latency {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}
.code {
  margin: 0;
  padding: 12px;
  background: #fff;
  max-height: 340px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
}
.code-block {
  margin: 0;
  padding: 14px 16px;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre;
}
@media (max-width: 1280px) {
  .api-doc-page {
    grid-template-columns: 280px minmax(0, 1fr);
  }
  .right-panel {
    display: none;
  }
}

@media (max-width: 900px) {
  .api-doc-page {
    grid-template-columns: 1fr;
  }
  .left-panel {
    position: static;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }
  .center-panel {
    padding: 22px 14px 40px;
  }
  h1 {
    font-size: 38px;
  }
  h2 {
    font-size: 30px;
  }
}

/* Markdown Styles for Guide Mode */
.article-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  color: #64748b;
  font-size: 13px;
  margin-bottom: 24px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.markdown-body {
  line-height: 1.8;
  color: #334155;
  font-size: 15px;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 24px;
  margin-bottom: 12px;
  color: #0f172a;
  font-weight: 700;
}
.markdown-body :deep(p) {
  margin-bottom: 12px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}
.markdown-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #be123c;
}
.markdown-body :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 14px;
  border-radius: 8px;
  overflow-x: auto;
  margin-bottom: 16px;
}
.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
</style>
