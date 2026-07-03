<template>
  <div class="api-doc-page">
    <aside class="docs-nav">
      <div class="brand-block">
        <div class="brand-mark">ORIN</div>
        <div>
          <strong>统一文档中心</strong>
          <span>OpenAI 兼容网关 / MCP / 工作流</span>
        </div>
      </div>

      <div class="nav-search">
        <el-input
          v-model="search"
          :prefix-icon="Search"
          placeholder="搜索端点、场景、错误码"
          clearable
        />
      </div>

      <div class="nav-group">
        <button
          v-for="item in navSections"
          :key="item.id"
          type="button"
          :class="['nav-item', { active: activeSection === item.id }]"
          @click="scrollToSection(item.id)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>

      <div class="nav-divider" />

      <div class="nav-title">常用端点</div>
      <button
        v-for="endpoint in filteredEndpoints"
        :key="endpoint.path"
        type="button"
        :class="['endpoint-link', { active: selectedEndpoint === endpoint.path }]"
        @click="selectEndpoint(endpoint)"
      >
        <span class="method-pill" :class="endpoint.method.toLowerCase()">{{ endpoint.method }}</span>
        <span>{{ endpoint.title }}</span>
      </button>
    </aside>

    <main class="docs-main">
      <section id="overview" class="hero-section">
        <div class="hero-copy">
          <div class="eyebrow">API Key 入口</div>
          <h1>把 ORIN 当作一个可治理的 OpenAI 兼容网关来接入</h1>
          <p>
            这里不做大段静态说明，按实际接入顺序组织：拿 Key、确认模型、发请求、看 Trace、
            排查错误。管理台 JWT 接口和对外 API Key 接口分开展示。
          </p>
        </div>
        <div class="hero-actions">
          <el-button :icon="Key" type="primary" @click="openControl('/dashboard/control/gateway?workspace=access')">
            API Key 管理
          </el-button>
          <el-button :icon="Connection" @click="openControl('/dashboard/control/gateway')">
            网关控制台
          </el-button>
          <el-button :icon="Document" @click="openControl('/swagger-ui/index.html')">
            Swagger
          </el-button>
        </div>
      </section>

      <section class="status-grid" aria-label="接入摘要">
        <div v-for="item in gatewayFacts" :key="item.label" class="fact-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </section>

      <section id="quickstart" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">Quickstart</div>
            <h2>四步跑通第一条请求</h2>
          </div>
          <el-button :icon="CopyDocument" @click="copyText(snippets.curl)">
            复制 cURL
          </el-button>
        </div>

        <div class="quickstart-layout">
          <div class="steps-list">
            <div v-for="step in quickstartSteps" :key="step.title" class="step-row">
              <div class="step-index">{{ step.index }}</div>
              <div>
                <strong>{{ step.title }}</strong>
                <p>{{ step.description }}</p>
              </div>
            </div>
          </div>

          <div class="config-panel">
            <div class="config-row">
              <label>Base URL</label>
              <div class="inline-control">
                <el-input v-model="baseUrl" spellcheck="false" />
                <el-button :icon="CopyDocument" @click="copyText(baseUrl)">复制</el-button>
              </div>
            </div>
            <div class="config-row">
              <label>CLIENT_ACCESS Key</label>
              <el-input
                v-model="apiKey"
                type="password"
                show-password
                placeholder="sk-orin-..."
                autocomplete="off"
              />
            </div>
            <div class="config-row compact">
              <label>模型 ID</label>
              <el-input v-model="modelId" spellcheck="false" />
            </div>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="本页不会保存 API Key；刷新后会清空。生产客户端请使用后端环境变量或密钥管理服务。"
            />
          </div>
        </div>
      </section>

      <section id="recipes" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">Recipes</div>
            <h2>可直接套用的调用片段</h2>
          </div>
          <div class="snippet-tabs">
            <button
              v-for="tab in snippetTabs"
              :key="tab.key"
              type="button"
              :class="['snippet-tab', { active: activeSnippet === tab.key }]"
              @click="activeSnippet = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <div class="code-panel">
          <div class="code-toolbar">
            <span>{{ activeSnippetLabel }}</span>
            <el-button size="small" text :icon="CopyDocument" @click="copyText(activeSnippetCode)">
              复制
            </el-button>
          </div>
          <pre><code>{{ activeSnippetCode }}</code></pre>
        </div>
      </section>

      <section id="endpoints" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">Reference</div>
            <h2>端点目录</h2>
          </div>
          <el-tag effect="plain">/v1 为 API Key 对外入口</el-tag>
        </div>

        <div class="endpoint-table">
          <button
            v-for="endpoint in filteredEndpoints"
            :key="endpoint.path"
            type="button"
            :class="['endpoint-row', { selected: selectedEndpoint === endpoint.path }]"
            @click="selectEndpoint(endpoint)"
          >
            <span class="method-pill" :class="endpoint.method.toLowerCase()">{{ endpoint.method }}</span>
            <code>{{ endpoint.path }}</code>
            <span class="endpoint-title">{{ endpoint.title }}</span>
            <el-tag size="small" :type="endpoint.auth ? 'warning' : 'success'" effect="plain">
              {{ endpoint.auth ? 'API Key' : 'Public' }}
            </el-tag>
            <span class="endpoint-note">{{ endpoint.note }}</span>
          </button>
        </div>
      </section>

      <section id="mcp" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">MCP</div>
            <h2>把 ORIN Agent / Workflow 暴露给外部客户端</h2>
          </div>
          <el-button :icon="Connection" @click="selectEndpoint(mcpEndpoint)">
            测试 initialize
          </el-button>
        </div>

        <div class="mcp-grid">
          <div class="plain-panel">
            <h3>鉴权边界</h3>
            <p><code>/v1/mcp</code> 只接受 <code>CLIENT_ACCESS</code> 类型 API Key，不复用 JWT。</p>
            <p>可暴露对象来自当前 Key 所属用户的 <code>mcpExposed=true</code> Agent / Workflow。</p>
          </div>
          <div class="plain-panel">
            <h3>客户端配置</h3>
            <pre><code>{{ snippets.mcpConfig }}</code></pre>
          </div>
        </div>
      </section>

      <section id="troubleshooting" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">Troubleshooting</div>
            <h2>常见错误先看这里</h2>
          </div>
        </div>

        <div class="trouble-grid">
          <div v-for="item in troubleshooting" :key="item.code" class="trouble-card">
            <strong>{{ item.code }}</strong>
            <p>{{ item.reason }}</p>
            <small>{{ item.action }}</small>
          </div>
        </div>
      </section>

      <section id="playground" class="content-section">
        <div class="section-heading">
          <div>
            <div class="eyebrow">Gateway Playground</div>
            <h2>内置路由测试台</h2>
          </div>
          <el-button v-if="!playgroundLoaded" :icon="VideoPlay" @click="playgroundLoaded = true">
            加载测试台
          </el-button>
        </div>
        <UnifiedGatewayPlaygroundTab v-if="playgroundLoaded" />
        <div v-else class="playground-placeholder">
          <strong>测试台会读取网关控制台数据，需要已登录管理台。</strong>
          <p>公开文档、端点目录和右侧在线请求不依赖 JWT；需要做 ACL / 路由策略联调时再手动加载测试台。</p>
        </div>
      </section>
    </main>

    <aside class="try-panel">
      <div class="try-card">
        <div class="try-head">
          <div>
            <span>在线请求</span>
            <strong>{{ selectedEndpointMeta.method }} {{ selectedEndpointMeta.path }}</strong>
          </div>
          <el-tag size="small" :type="selectedEndpointMeta.auth ? 'warning' : 'success'">
            {{ selectedEndpointMeta.auth ? 'API Key' : 'Public' }}
          </el-tag>
        </div>

        <div class="try-field">
          <label>Endpoint</label>
          <el-select v-model="selectedEndpoint" @change="handleEndpointChange">
            <el-option
              v-for="endpoint in endpoints"
              :key="endpoint.path"
              :label="`${endpoint.method} ${endpoint.path}`"
              :value="endpoint.path"
            />
          </el-select>
        </div>

        <div v-if="selectedEndpointMeta.auth" class="try-field">
          <label>Authorization</label>
          <el-input
            v-model="apiKey"
            type="password"
            show-password
            placeholder="Bearer sk-orin-..."
            autocomplete="off"
          />
        </div>

        <div v-if="!isGetRequest" class="try-field">
          <label>Request Body</label>
          <el-input
            v-model="requestBodyText"
            type="textarea"
            :autosize="{ minRows: 9, maxRows: 16 }"
            spellcheck="false"
          />
        </div>

        <div class="try-actions">
          <el-button type="primary" :loading="sending" :icon="VideoPlay" @click="runTryRequest">
            发送
          </el-button>
          <el-button :icon="Refresh" @click="resetRequestBody">重置</el-button>
          <el-button :icon="CopyDocument" @click="copyText(currentCurl)">复制 cURL</el-button>
        </div>
      </div>

      <div class="try-card result-card">
        <div class="try-head compact-head">
          <div>
            <span>响应</span>
            <strong>{{ resultTitle }}</strong>
          </div>
          <span v-if="latencyMs !== null" class="latency">{{ latencyMs }} ms</span>
        </div>
        <pre class="result-body"><code>{{ resultText }}</code></pre>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Connection,
  CopyDocument,
  Document,
  Guide,
  Key,
  QuestionFilled,
  Refresh,
  Search,
  Tickets,
  VideoPlay
} from '@element-plus/icons-vue'
import UnifiedGatewayPlaygroundTab from './components/gateway/UnifiedGatewayPlaygroundTab.vue'
import { invokeUnifiedApiEndpoint } from '@/api/apiEndpoint'

const defaultBaseUrl = (() => {
  if (typeof window === 'undefined') return 'http://localhost:8080'
  const { protocol, hostname, port, origin } = window.location
  if (port === '5173') return `${protocol}//${hostname}:8080`
  return origin
})()

const baseUrl = ref(defaultBaseUrl)
const apiKey = ref('')
const modelId = ref('Qwen/Qwen2.5-7B-Instruct')
const search = ref('')
const activeSection = ref('overview')
const activeSnippet = ref('curl')
const selectedEndpoint = ref('/v1/chat/completions')
const requestBodyText = ref('')
const sending = ref(false)
const playgroundLoaded = ref(false)
const responseStatus = ref(null)
const responseText = ref('')
const errorStatus = ref(null)
const errorText = ref('')
const latencyMs = ref(null)

const navSections = [
  { id: 'overview', label: '接入总览', icon: Guide },
  { id: 'quickstart', label: '快速接入', icon: Key },
  { id: 'recipes', label: '代码片段', icon: Document },
  { id: 'endpoints', label: '端点目录', icon: Tickets },
  { id: 'mcp', label: 'MCP 客户端', icon: Connection },
  { id: 'troubleshooting', label: '排障', icon: QuestionFilled },
  { id: 'playground', label: '路由测试台', icon: VideoPlay }
]

const endpoints = [
  {
    title: 'API 入口',
    method: 'GET',
    path: '/v1',
    auth: false,
    note: '返回文档链接和核心端点',
    body: null
  },
  {
    title: '健康检查',
    method: 'GET',
    path: '/v1/health',
    auth: false,
    note: 'Provider 健康快照',
    body: null
  },
  {
    title: '能力清单',
    method: 'GET',
    path: '/v1/capabilities',
    auth: false,
    note: '当前网关暴露能力',
    body: null
  },
  {
    title: 'Provider 列表',
    method: 'GET',
    path: '/v1/providers',
    auth: false,
    note: '已注册 Provider 与统计',
    body: null
  },
  {
    title: '路由统计',
    method: 'GET',
    path: '/v1/routing/stats',
    auth: false,
    note: '路由命中和延迟统计',
    body: null
  },
  {
    title: '模型列表',
    method: 'GET',
    path: '/v1/models',
    auth: true,
    note: 'OpenAI 兼容模型列表',
    body: null
  },
  {
    title: '聊天完成',
    method: 'POST',
    path: '/v1/chat/completions',
    auth: true,
    note: 'OpenAI 兼容非流式/stream=true',
    body: () => ({
      model: modelId.value,
      messages: [{ role: 'user', content: '你好，请用一句话介绍 ORIN。' }],
      temperature: 0.7,
      max_tokens: 128
    })
  },
  {
    title: '流式聊天',
    method: 'POST',
    path: '/v1/chat/completions/stream',
    auth: true,
    note: 'SSE 流式响应',
    body: () => ({
      model: modelId.value,
      messages: [{ role: 'user', content: '请流式输出 3 个接入建议。' }],
      stream: true,
      max_tokens: 256
    })
  },
  {
    title: '文本嵌入',
    method: 'POST',
    path: '/v1/embeddings',
    auth: true,
    note: '默认需显式启用 embeddings 端点',
    body: () => ({
      model: 'text-embedding-3-small',
      input: 'ORIN 是智能体管理平台'
    })
  },
  {
    title: '执行 Workflow',
    method: 'POST',
    path: '/v1/workflows/{workflowId}/execute',
    auth: true,
    note: '将 {workflowId} 替换为已发布工作流 ID',
    body: () => ({
      input: {
        topic: 'hello-orin'
      }
    })
  },
  {
    title: 'MCP initialize',
    method: 'POST',
    path: '/v1/mcp',
    auth: true,
    note: 'Streamable HTTP JSON-RPC',
    body: () => ({
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: {
        protocolVersion: '2024-11-05',
        capabilities: {},
        clientInfo: { name: 'orin-docs', version: '1.0.0' }
      }
    })
  }
]

const mcpEndpoint = endpoints.find((item) => item.path === '/v1/mcp')

const gatewayFacts = [
  { label: '对外根路径', value: '/v1', note: 'OpenAI 兼容入口' },
  { label: '认证方式', value: 'CLIENT_ACCESS', note: 'Bearer sk-orin-*' },
  { label: '管理接口', value: '/api/v1', note: 'JWT 业务接口，不与 /v1 混用' },
  { label: '追踪字段', value: 'X-Trace-Id', note: '请求可自带，响应会回传' }
]

const quickstartSteps = [
  {
    index: '1',
    title: '创建 CLIENT_ACCESS Key',
    description: '在网关控制台创建平台访问密钥；密钥只显示一次，不要写进前端代码。'
  },
  {
    index: '2',
    title: '确认可用模型',
    description: '调用 GET /v1/models，或者在模型管理里检查 Provider 健康状态。'
  },
  {
    index: '3',
    title: '发起 /v1/chat/completions',
    description: '接口格式兼容 OpenAI SDK，可通过 Base URL 切换到 ORIN。'
  },
  {
    index: '4',
    title: '用 Trace ID 排障',
    description: '失败时保留响应头或响应体里的 Trace ID，到运行观测里查询关联记录。'
  }
]

const snippetTabs = [
  { key: 'curl', label: 'cURL' },
  { key: 'node', label: 'Node SDK' },
  { key: 'python', label: 'Python SDK' },
  { key: 'mcpConfig', label: 'MCP' }
]

const troubleshooting = [
  {
    code: 'AUTH_API_KEY_INVALID / 401',
    reason: '缺少 API Key、Key 已禁用、类型不是 CLIENT_ACCESS，或 Header 未按 Bearer 格式传递。',
    action: '到 API Key 管理页重新创建或启用 Key；确认客户端使用 Authorization: Bearer sk-orin-*。'
  },
  {
    code: 'service_unavailable / 503',
    reason: '没有可用 Provider，或所选模型没有健康的路由目标。',
    action: '检查模型管理、Provider Key、健康检查和 /v1/routing/stats。'
  },
  {
    code: 'not_implemented / 501',
    reason: '当前环境未启用某个可选端点，例如 embeddings 默认关闭。',
    action: '按部署文档开启对应配置，再重启后端。'
  },
  {
    code: 'payload_too_large / 413',
    reason: '请求体超过网关限制。',
    action: '压缩消息上下文、减少批量输入，或拆分为多次请求。'
  },
  {
    code: 'MCP tools 为空',
    reason: '当前 API Key 所属用户没有暴露 mcpExposed=true 的 Agent / Workflow。',
    action: '在 Agent 或 Workflow 配置中开启 MCP 暴露，再重新 tools/list。'
  },
  {
    code: 'Trace 查不到',
    reason: '请求没有进入后端、未带 API Key、或只命中了公开健康接口。',
    action: '优先用 /v1/chat/completions 或 /v1/mcp tools/call 产生业务审计记录。'
  }
]

const selectedEndpointMeta = computed(() => (
  endpoints.find((item) => item.path === selectedEndpoint.value) || endpoints[0]
))

const isGetRequest = computed(() => selectedEndpointMeta.value.method === 'GET')

const filteredEndpoints = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return endpoints
  return endpoints.filter((item) => (
    item.title.toLowerCase().includes(keyword)
    || item.path.toLowerCase().includes(keyword)
    || item.note.toLowerCase().includes(keyword)
    || item.method.toLowerCase().includes(keyword)
  ))
})

const snippets = computed(() => {
  const key = apiKey.value.trim() || '<ORIN_CLIENT_ACCESS_KEY>'
  const model = modelId.value.trim() || '<MODEL_ID>'
  const normalizedBase = baseUrl.value.replace(/\/$/, '')
  return {
    curl: `ORIN_API_KEY="${key}"
curl -X POST ${normalizedBase}/v1/chat/completions \\
  -H "Authorization: Bearer $ORIN_API_KEY" \\
  -H "Content-Type: application/json" \\
  -H "X-Trace-Id: demo-$(date +%s)" \\
  -d '{
    "model": "${model}",
    "messages": [
      { "role": "user", "content": "你好，请用一句话介绍 ORIN。" }
    ],
    "temperature": 0.7,
    "max_tokens": 128
  }'`,
    node: `import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.ORIN_API_KEY,
  baseURL: "${normalizedBase}/v1"
});

const completion = await client.chat.completions.create({
  model: "${model}",
  messages: [{ role: "user", content: "你好，请介绍 ORIN。" }]
});

process.stdout.write(completion.choices[0].message.content + "\\n");`,
    python: `from openai import OpenAI
import os

client = OpenAI(
    api_key=os.environ["ORIN_API_KEY"],
    base_url="${normalizedBase}/v1",
)

completion = client.chat.completions.create(
    model="${model}",
    messages=[{"role": "user", "content": "你好，请介绍 ORIN。"}],
)

print(completion.choices[0].message.content)`,
    mcpConfig: `{
  "mcpServers": {
    "orin": {
      "url": "${normalizedBase}/v1/mcp",
      "headers": {
        "Authorization": "Bearer ${key}"
      }
    }
  }
}`
  }
})

const activeSnippetCode = computed(() => snippets.value[activeSnippet.value])
const activeSnippetLabel = computed(() => (
  snippetTabs.find((item) => item.key === activeSnippet.value)?.label || 'Code'
))

const currentCurl = computed(() => {
  const endpoint = selectedEndpointMeta.value
  const normalizedBase = baseUrl.value.replace(/\/$/, '')
  const headers = [
    endpoint.auth ? `  -H "Authorization: Bearer ${apiKey.value.trim() || '<ORIN_CLIENT_ACCESS_KEY>'}"` : null,
    !isGetRequest.value ? '  -H "Content-Type: application/json"' : null
  ].filter(Boolean)

  const body = !isGetRequest.value
    ? ` \\\n  -d '${requestBodyText.value.replace(/\n/g, '').replace(/\s{2,}/g, ' ')}'`
    : ''

  return `curl -X ${endpoint.method} ${normalizedBase}${endpoint.path} \\\n${headers.join(' \\\n')}${body}`
})

const resultTitle = computed(() => {
  if (responseStatus.value) return `HTTP ${responseStatus.value}`
  if (errorStatus.value) return `HTTP ${errorStatus.value}`
  return '尚未发送'
})

const resultText = computed(() => {
  if (responseText.value) return responseText.value
  if (errorText.value) return errorText.value
  return JSON.stringify({
    hint: '选择端点后点击发送。公开端点可直接测试；受保护端点需要 CLIENT_ACCESS API Key。'
  }, null, 2)
})

const refreshRequestBody = () => {
  const body = selectedEndpointMeta.value.body
  requestBodyText.value = body ? JSON.stringify(body(), null, 2) : ''
}

refreshRequestBody()

const scrollToSection = (id) => {
  activeSection.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const selectEndpoint = (endpoint) => {
  selectedEndpoint.value = endpoint.path
  activeSection.value = 'endpoints'
  refreshRequestBody()
}

const handleEndpointChange = () => {
  refreshRequestBody()
}

const resetRequestBody = () => {
  refreshRequestBody()
  responseStatus.value = null
  responseText.value = ''
  errorStatus.value = null
  errorText.value = ''
  latencyMs.value = null
}

const copyText = async (value) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const openControl = (path) => {
  window.open(path, '_blank', 'noopener,noreferrer')
}

const runTryRequest = async () => {
  const endpoint = selectedEndpointMeta.value
  if (endpoint.auth && !apiKey.value.trim()) {
    ElMessage.warning('请先输入 CLIENT_ACCESS API Key')
    return
  }

  if (endpoint.path.includes('{workflowId}')) {
    ElMessage.warning('请先把端点里的 {workflowId} 替换为真实工作流 ID 后再用 cURL 调用')
    return
  }

  let payload = null
  if (!isGetRequest.value) {
    try {
      payload = JSON.parse(requestBodyText.value)
    } catch {
      ElMessage.error('Request Body 不是合法 JSON')
      return
    }
  }

  sending.value = true
  responseStatus.value = null
  responseText.value = ''
  errorStatus.value = null
  errorText.value = ''
  latencyMs.value = null

  const startedAt = performance.now()
  try {
    const headers = {}
    if (!isGetRequest.value) headers['Content-Type'] = 'application/json'
    if (endpoint.auth) headers.Authorization = `Bearer ${apiKey.value.trim()}`

    const res = await invokeUnifiedApiEndpoint({
      method: endpoint.method.toLowerCase(),
      url: endpoint.path,
      data: payload,
      headers,
      timeout: 120000
    })
    responseStatus.value = res.status || 200
    responseText.value = JSON.stringify(res.data, null, 2)
    ElMessage.success('请求完成')
  } catch (err) {
    const status = err?.response?.status || 'ERROR'
    errorStatus.value = status
    errorText.value = JSON.stringify(err?.response?.data || { message: err?.message || 'Request failed' }, null, 2)
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
  grid-template-columns: 292px minmax(0, 1fr) 420px;
  background: #f6f7f9;
  color: #111827;
}

.docs-nav,
.try-panel {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
  background: #ffffff;
}

.docs-nav {
  border-right: 1px solid #e5e7eb;
  padding: 18px 14px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #111827;
  color: #ffffff;
  font-weight: 800;
}

.brand-block strong,
.brand-block span {
  display: block;
}

.brand-block span {
  margin-top: 3px;
  color: #6b7280;
  font-size: 12px;
}

.nav-search {
  margin-bottom: 14px;
}

.nav-group {
  display: grid;
  gap: 4px;
}

.nav-item,
.endpoint-link {
  width: 100%;
  border: 0;
  background: transparent;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 8px;
  text-align: left;
  cursor: pointer;
}

.nav-item:hover,
.nav-item.active,
.endpoint-link:hover,
.endpoint-link.active {
  background: #eef2ff;
  color: #1f2937;
}

.nav-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 16px 0;
}

.nav-title {
  margin: 0 0 8px 4px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
}

.endpoint-link {
  font-size: 13px;
}

.method-pill {
  flex: 0 0 auto;
  min-width: 46px;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 800;
  text-align: center;
}

.method-pill.get {
  background: #dcfce7;
  color: #166534;
}

.method-pill.post {
  background: #dbeafe;
  color: #1d4ed8;
}

.docs-main {
  padding: 28px;
}

.hero-section,
.content-section,
.fact-card,
.try-card,
.plain-panel,
.trouble-card {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  border-radius: 8px;
}

.hero-section {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
}

.hero-copy {
  max-width: 820px;
}

.eyebrow {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  margin-top: 8px;
  max-width: 780px;
  font-size: 40px;
  line-height: 1.12;
  font-weight: 800;
}

h2 {
  margin-top: 6px;
  font-size: 24px;
  line-height: 1.2;
}

h3 {
  margin-bottom: 8px;
  font-size: 16px;
}

.hero-copy p {
  margin-top: 12px;
  color: #4b5563;
  line-height: 1.7;
}

.hero-actions {
  flex: 0 0 178px;
  display: grid;
  gap: 8px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.fact-card {
  padding: 16px;
}

.fact-card span,
.fact-card small {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.fact-card strong {
  display: block;
  margin: 8px 0 4px;
  font-size: 20px;
}

.content-section {
  scroll-margin-top: 20px;
  margin-top: 16px;
  padding: 22px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.quickstart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 16px;
}

.steps-list {
  display: grid;
  gap: 10px;
}

.step-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.step-index {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 800;
}

.step-row p,
.plain-panel p,
.trouble-card p,
.trouble-card small {
  margin-top: 6px;
  color: #4b5563;
  line-height: 1.6;
}

.config-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
}

.config-row label,
.try-field label {
  display: block;
  margin-bottom: 6px;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
}

.inline-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.snippet-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.snippet-tab {
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #374151;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.snippet-tab.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 700;
}

.code-panel,
.plain-panel pre {
  overflow: hidden;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
}

.code-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

pre {
  margin: 0;
  padding: 14px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre;
}

code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.endpoint-table {
  display: grid;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.endpoint-row {
  display: grid;
  grid-template-columns: 62px minmax(190px, 1.1fr) minmax(120px, 0.8fr) 84px minmax(160px, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 52px;
  padding: 10px 12px;
  border: 0;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.endpoint-row:last-child {
  border-bottom: 0;
}

.endpoint-row:hover,
.endpoint-row.selected {
  background: #f8fafc;
}

.endpoint-title {
  font-weight: 700;
}

.endpoint-note {
  color: #6b7280;
  font-size: 13px;
}

.mcp-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 12px;
}

.plain-panel {
  padding: 16px;
}

.plain-panel code:not(pre code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: #f3f4f6;
}

.plain-panel pre {
  margin-top: 10px;
}

.trouble-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trouble-card {
  padding: 14px;
}

.trouble-card strong {
  color: #991b1b;
}

.playground-placeholder {
  padding: 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.playground-placeholder p {
  margin-top: 6px;
  color: #4b5563;
  line-height: 1.6;
}

.try-panel {
  border-left: 1px solid #e5e7eb;
  padding: 16px;
}

.try-card {
  padding: 14px;
  margin-bottom: 12px;
}

.try-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.try-head span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
}

.try-head strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}

.compact-head {
  align-items: center;
  margin-bottom: 8px;
}

.try-field {
  margin-bottom: 12px;
}

.try-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.result-card {
  padding-bottom: 0;
  overflow: hidden;
}

.latency {
  color: #4b5563;
  font-size: 12px;
  font-weight: 700;
}

.result-body {
  min-height: 260px;
  max-height: 480px;
  margin: 0 -14px;
  border-top: 1px solid #e5e7eb;
  background: #111827;
  color: #e5e7eb;
}

.result-body code {
  display: block;
  background: transparent;
  color: inherit;
}

@media (max-width: 1380px) {
  .api-doc-page {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .try-panel {
    position: static;
    grid-column: 2;
    height: auto;
    border-left: 0;
    border-top: 1px solid #e5e7eb;
  }
}

@media (max-width: 960px) {
  .api-doc-page {
    display: block;
  }

  .docs-nav,
  .try-panel {
    position: static;
    height: auto;
  }

  .docs-main {
    padding: 14px;
  }

  .hero-section,
  .section-heading,
  .quickstart-layout,
  .mcp-grid {
    display: block;
  }

  .hero-actions,
  .config-panel,
  .mcp-grid {
    margin-top: 14px;
  }

  h1 {
    font-size: 30px;
  }

  .status-grid,
  .trouble-grid {
    grid-template-columns: 1fr;
  }

  .endpoint-row {
    grid-template-columns: 62px minmax(0, 1fr);
  }

  .endpoint-row .endpoint-title,
  .endpoint-row .endpoint-note {
    grid-column: 2;
  }
}
</style>
