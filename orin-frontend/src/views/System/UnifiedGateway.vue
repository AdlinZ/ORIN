<template>
  <div class="unified-gateway-workbench page-container fade-in">
    <section class="gateway-console">
      <header class="gateway-hero">
        <div class="gateway-hero-row">
          <div class="gateway-hero-main">
            <div class="gateway-icon">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="gateway-title-block">
              <h1>AI API 中转站</h1>
              <p>OpenAI 兼容 API 中转、多模型路由、API Key 治理、限流配额、调用审计 — 统一模型网关控制台</p>
            </div>
          </div>

          <div class="gateway-hero-actions">
            <el-button
              :icon="Refresh"
              :loading="consoleLoading"
              @click="refreshCurrentWorkspace"
            >
              刷新
            </el-button>
            <el-button type="primary" :icon="Connection" @click="openRouteTest">
              入口测试
            </el-button>
          </div>
        </div>

        <div class="gateway-baseurl-bar">
          <div class="baseurl-main">
            <span class="baseurl-label">Base URL</span>
            <code class="baseurl-value">{{ baseUrl }}/v1</code>
            <el-button size="small" text :icon="CopyDocument" @click="copyText(baseUrl + '/v1')">
              复制
            </el-button>
          </div>
          <div class="endpoint-chips">
            <span
              v-for="ep in endpointChips"
              :key="ep.path"
              class="endpoint-chip"
              :class="ep.status"
            >
              <span class="chip-method">{{ ep.method }}</span>
              <span class="chip-path">{{ ep.path }}</span>
              <el-tag size="small" :type="ep.status === 'open' ? 'success' : 'info'">
                {{ ep.label }}
              </el-tag>
            </span>
          </div>
        </div>

        <div class="gateway-summary" data-testid="gateway-workspaces">
          <button
            v-for="item in workspaces"
            :key="item.key"
            type="button"
            :class="['gateway-summary-card', 'workspace-tab', { active: activeWorkspace === item.key }]"
            @click="setActiveWorkspace(item.key)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>
              <strong>{{ item.label }}</strong>
              <small>{{ workspaceSummaryMap[item.key] }}</small>
            </span>
          </button>
        </div>
      </header>

      <section class="gateway-content-panel">
        <section v-show="activeWorkspace === 'overview'" class="workspace-panel">
          <OrinMetricStrip :metrics="gatewayOverviewMetrics" class="block-gap" />

          <el-row :gutter="16" class="block-gap">
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" class="panel-card">
                <template #header>
                  <div class="panel-header">
                    <span>快速接入</span>
                    <span class="panel-header-meta">curl / OpenAI SDK</span>
                  </div>
                </template>
                <div class="quickstart-tabs">
                  <button
                    v-for="tab in quickstartTabs"
                    :key="tab.key"
                    type="button"
                    :class="['qs-tab-btn', { active: activeQuickstartTab === tab.key }]"
                    @click="activeQuickstartTab = tab.key"
                  >
                    {{ tab.label }}
                  </button>
                </div>
                <div class="code-block-wrapper">
                  <pre class="code-block"><code>{{ quickstartCodeSnippets[activeQuickstartTab] }}</code></pre>
                  <el-button
                    size="small"
                    text
                    :icon="CopyDocument"
                    class="copy-code-btn"
                    @click="copyText(quickstartCodeSnippets[activeQuickstartTab])"
                  >
                    复制
                  </el-button>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" class="panel-card">
                <template #header>
                  <div class="panel-header">
                    <span>模型与端点状态</span>
                  </div>
                </template>
                <div class="endpoint-status-list">
                  <div
                    v-for="ep in endpointStatusList"
                    :key="ep.path"
                    class="endpoint-status-item"
                  >
                    <div class="ep-info">
                      <span class="ep-method-tag" :class="ep.methodClass">{{ ep.method }}</span>
                      <code class="ep-path">{{ ep.path }}</code>
                    </div>
                    <el-tag size="small" :type="ep.statusType">{{ ep.statusLabel }}</el-tag>
                  </div>
                </div>
                <el-divider />
                <div class="model-list-section">
                  <div class="section-subhead">
                    <span>可用模型</span>
                    <span v-if="modelsState.status === 'success'" class="model-count">{{ models.length }} 个</span>
                  </div>
                  <OrinAsyncState
                    :status="modelsState.status"
                    empty-text="暂无已配置模型，请在模型管理中配置 provider 和模型映射"
                    @retry="loadModels"
                  >
                    <div class="model-tags">
                      <el-tag
                        v-for="m in models"
                        :key="m.id || m.name"
                        size="small"
                        effect="plain"
                        class="model-tag"
                      >
                        {{ m.name || m.model || m.id }}
                      </el-tag>
                    </div>
                  </OrinAsyncState>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <OrinDataTable class="block-gap">
            <template #header>
              <div class="table-head">
                <span>最近调用</span>
                <span>Gateway 审计日志</span>
              </div>
            </template>
            <OrinAsyncState
              :status="recentCallsState.status"
              empty-text="暂无 Gateway 调用记录，启动客户端调用后将在审计日志中展示"
              @retry="loadRecentCalls"
            >
              <el-table :data="recentCalls" stripe size="small">
                <el-table-column label="Trace ID" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="trace-id-cell">
                      <span class="trace-id-text">{{ row.traceId }}</span>
                      <el-button
                        size="small"
                        text
                        :icon="CopyDocument"
                        @click="copyText(row.traceId)"
                      />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="modelAlias"
                  label="模型别名"
                  min-width="130"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="providerModel"
                  label="Provider 模型"
                  min-width="130"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="endpoint"
                  label="端点"
                  min-width="150"
                  show-overflow-tooltip
                />
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.success ? 'success' : 'danger'">
                      {{ row.success ? '成功' : '失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="errorCode" label="错误码" width="100">
                  <template #default="{ row }">
                    {{ row.errorCode || '--' }}
                  </template>
                </el-table-column>
                <el-table-column label="延迟" width="90" align="right">
                  <template #default="{ row }">
                    {{ row.responseTime != null ? row.responseTime + 'ms' : '--' }}
                  </template>
                </el-table-column>
                <el-table-column prop="totalTokens" label="Tokens" width="80" align="right">
                  <template #default="{ row }">
                    {{ row.totalTokens != null ? row.totalTokens : '--' }}
                  </template>
                </el-table-column>
                <el-table-column label="时间" width="170">
                  <template #default="{ row }">
                    {{ formatTime(row.createdAt) }}
                  </template>
                </el-table-column>
              </el-table>
            </OrinAsyncState>
          </OrinDataTable>
        </section>

        <section v-if="activeWorkspace === 'api'" class="workspace-panel">
          <OrinDataTable>
            <template #header>
              <div class="table-head">
                <span>统一入口</span>
                <div class="head-actions">
                  <el-button size="small" :icon="Refresh" @click="loadRoutes">
                    刷新
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :icon="Connection"
                    @click="openRouteTest"
                  >
                    测试入口
                  </el-button>
                </div>
              </div>
            </template>
            <OrinAsyncState
              :status="routesState.status"
              empty-text="暂无入口配置，请添加模型 API、服务代理或后台入口策略"
              @retry="loadRoutes"
            >
              <el-table :data="routes" stripe>
                <el-table-column prop="name" label="入口名称" min-width="140" />
                <el-table-column label="路径/方法" min-width="220" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">
                      {{ row.method || 'ALL' }}
                    </el-tag>
                    <span class="path-text">{{ row.pathPattern }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="入口类型" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="entryType(row).tag">
                      {{ entryType(row).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="目标" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="target-text">{{ routeTarget(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="策略" width="150">
                  <template #default="{ row }">
                    <el-space wrap>
                      <el-tag v-if="row.authRequired" size="small" effect="plain">
                        认证
                      </el-tag>
                      <el-tag
                        v-if="row.rateLimitPolicyId"
                        size="small"
                        type="warning"
                        effect="plain"
                      >
                        限流
                      </el-tag>
                      <el-tag
                        v-if="row.circuitBreakerPolicyId"
                        size="small"
                        type="danger"
                        effect="plain"
                      >
                        熔断
                      </el-tag>
                      <el-tag
                        v-if="row.retryPolicyId"
                        size="small"
                        type="success"
                        effect="plain"
                      >
                        重试
                      </el-tag>
                    </el-space>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="92">
                  <template #default="{ row }">
                    <el-switch v-model="row.enabled" @change="toggleRoute(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="诊断" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" @click="openRouteDetail(row)">
                      生效链路
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </OrinAsyncState>
          </OrinDataTable>

          <OrinDataTable class="block-gap">
            <template #header>
              <div class="table-head">
                <span>后台入口配置</span>
                <span>{{ attentionControlPlaneEndpoints.length }} 个入口需要处理</span>
              </div>
            </template>
            <el-table :data="attentionControlPlaneEndpoints" stripe>
              <el-table-column
                prop="pathPattern"
                label="入口路径"
                min-width="240"
                show-overflow-tooltip
              />
              <el-table-column label="方法" width="120">
                <template #default="{ row }">
                  {{ formatMethods(row.methods) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="reason"
                label="需要处理"
                min-width="220"
                show-overflow-tooltip
              />
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" @click="createLocalControlPlaneRoute(row)">
                    添加单独配置
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <OrinEmptyState description="当前没有需要处理的后台入口" />
              </template>
            </el-table>
          </OrinDataTable>

          <div class="api-entry-grid block-gap">
            <UnifiedGatewayRoutesTab mode="actions" />
            <UnifiedGatewayServicesTab />
          </div>
        </section>

        <section v-if="activeWorkspace === 'access'" class="workspace-panel access-workspace">
          <ApiKeyManagement embedded />
          <div class="access-list-section block-gap">
            <div class="workspace-section-head">
              <span class="command-eyebrow">访问名单</span>
              <h3>ACL 与 API Key 要求</h3>
              <p>按 IP、路径和凭据要求控制哪些调用方可以进入统一网关。</p>
            </div>
            <UnifiedGatewayAclTab />
          </div>
        </section>

        <section v-if="activeWorkspace === 'traffic'" class="workspace-panel traffic-workspace">
          <div class="workspace-section-head">
            <span class="command-eyebrow">入口策略</span>
            <h3>限流、熔断与重试</h3>
            <p>维护可复用的入口级策略，并在统一入口中绑定到具体路径。</p>
          </div>
          <UnifiedGatewayPoliciesTab />

          <div class="workspace-section-head block-gap">
            <span class="command-eyebrow">平台底线</span>
            <h3>系统级默认限流</h3>
            <p>配置统一网关的全局保护阈值，作为入口策略之外的默认防线。</p>
          </div>
          <UnifiedGatewayRateLimitTab />
        </section>
      </section>
    </section>

    <el-drawer v-model="routeDrawerVisible" title="入口生效链路" size="520px">
      <OrinAsyncState :status="detailState.status" empty-text="请选择一个入口查看生效配置" @retry="reloadSelectedRoute">
        <template v-if="effectiveConfig">
          <OrinDetailPanel :title="effectiveConfig.route?.name" :eyebrow="effectiveConfig.targetType">
            <div class="route-summary">
              <span>{{ effectiveConfig.route?.method || 'ALL' }}</span>
              <strong>{{ effectiveConfig.route?.pathPattern }}</strong>
            </div>
            <el-alert
              v-for="warning in effectiveConfig.warnings || []"
              :key="warning"
              type="warning"
              :closable="false"
              class="warning-line"
              :title="warning"
            />
          </OrinDetailPanel>

          <ol class="chain-list">
            <li v-for="step in effectiveConfig.chain || []" :key="step.key">
              <span class="chain-label">{{ step.label }}</span>
              <el-tag size="small" effect="plain">
                {{ step.status }}
              </el-tag>
              <p>{{ step.detail }}</p>
            </li>
          </ol>

          <OrinDetailPanel title="目标实例" class="block-gap">
            <OrinDataTable compact>
              <el-table :data="effectiveConfig.allInstances || []" size="small">
                <el-table-column prop="host" label="主机" />
                <el-table-column prop="port" label="端口" width="80" />
                <el-table-column prop="status" label="状态" width="90" />
                <template #empty>
                  <OrinEmptyState description="此入口由后台处理或直连目标承接，没有服务实例" />
                </template>
              </el-table>
            </OrinDataTable>
          </OrinDetailPanel>
        </template>
      </OrinAsyncState>
    </el-drawer>

    <el-dialog v-model="testDialogVisible" title="入口诊断测试器" width="560px">
      <el-form :model="testForm" label-width="90px">
        <el-form-item label="请求路径">
          <el-input v-model="testForm.path" placeholder="/api/v1/example" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="testForm.method" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="PATCH" value="PATCH" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="testResult"
        :type="testResult.success ? 'success' : 'warning'"
        :closable="false"
        class="block-gap"
      >
        <template #title>
          <span v-if="testResult.success">
            匹配 {{ testResult.matchedRoute }}，目标 {{ testResult.targetUrl || testResult.targetService || 'ORIN 后台处理' }}
          </span>
          <span v-else>未匹配任何入口</span>
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="testDialogVisible = false">
          关闭
        </el-button>
        <el-button type="primary" :loading="testing" @click="runTest">
          测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Connection,
  CopyDocument,
  Key,
  Operation,
  Refresh,
  Share,
  TrendCharts
} from '@element-plus/icons-vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinStatusSummary from '@/components/orin/OrinStatusSummary.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDetailPanel from '@/components/orin/OrinDetailPanel.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import UnifiedGatewayRoutesTab from './components/gateway/UnifiedGatewayRoutesTab.vue'
import UnifiedGatewayServicesTab from './components/gateway/UnifiedGatewayServicesTab.vue'
import UnifiedGatewayAclTab from './components/gateway/UnifiedGatewayAclTab.vue'
import UnifiedGatewayPoliciesTab from './components/gateway/UnifiedGatewayPoliciesTab.vue'
import UnifiedGatewayRateLimitTab from './components/gateway/UnifiedGatewayRateLimitTab.vue'
import ApiKeyManagement from './ApiKeyManagement.vue'
import { useUnifiedGatewayWorkbench } from './composables/useUnifiedGatewayWorkbench'
import { useUnifiedGatewayRoutes } from './composables/useUnifiedGatewayRoutes'
import { useUnifiedGatewayPolicies } from './composables/useUnifiedGatewayPolicies'
import { getAllApiKeys } from '@/api/apiKey'
import { getGatewayAuditLogs } from '@/api/audit'
import { getModelList } from '@/api/model'
import { getAllProviders } from '@/api/system'
import { createAsyncState, markLoading, markSuccess, markEmpty, markError } from '@/viewmodels'
import dayjs from 'dayjs'

const workspaces = [
  { key: 'overview', label: '总览', icon: TrendCharts },
  { key: 'api', label: '统一入口', icon: Share },
  { key: 'access', label: 'API Keys', icon: Key },
  { key: 'traffic', label: '流量策略', icon: Operation }
]

const route = useRoute()
const router = useRouter()
const workspaceKeys = new Set(workspaces.map((item) => item.key))
const normalizeWorkspace = (workspace) => {
  const key = String(workspace || '')
  return workspaceKeys.has(key) ? key : 'overview'
}

const activeWorkspace = ref(normalizeWorkspace(route.query.workspace))
const routeDrawerVisible = ref(false)
const selectedRouteId = ref(null)
const testDialogVisible = ref(false)
const testing = ref(false)
const testForm = reactive({ path: '', method: 'GET' })

const setActiveWorkspace = (workspace) => {
  const nextWorkspace = normalizeWorkspace(workspace)
  activeWorkspace.value = nextWorkspace

  const nextQuery = { ...route.query }
  if (nextWorkspace === 'overview') {
    delete nextQuery.workspace
  } else {
    nextQuery.workspace = nextWorkspace
  }

  router.replace({ query: nextQuery }).catch(() => {})
}

const consoleLoading = ref(false)

const baseUrl = computed(() => window.location.origin)

const endpointChips = [
  { method: 'POST', path: '/v1/chat/completions', status: 'open', label: '开放' },
  { method: 'GET', path: '/v1/models', status: 'open', label: '开放' },
  { method: 'POST', path: '/v1/embeddings', status: 'closed', label: '默认关闭' }
]

const gatewayOverviewMetrics = computed(() => {
  const keyCount = apiKeys.value.length
  const enabledCount = apiKeys.value.filter((k) => {
    const s = (k.status || '').toUpperCase()
    return s === 'ACTIVE' || s === 'ENABLED'
  }).length
  const usedTokens = apiKeys.value.reduce((sum, k) => sum + (Number(k.usedTokens) || 0), 0)
  const failedCount = overviewStats.value.failedCalls
  const avgLatency = overviewStats.value.avgLatency

  return [
    { key: 'keyCount', label: 'API Key 总数', value: keyCount, meta: `${enabledCount} 个已启用` },
    { key: 'enabledKeys', label: '已启用 Key', value: enabledCount, meta: keyCount > 0 ? `共 ${keyCount} 个` : '暂无 Key' },
    { key: 'recentCalls', label: '近期调用', value: formatNumber(overviewStats.value.totalCalls), meta: '审计日志记录' },
    { key: 'tokenUsage', label: 'Token 用量', value: formatNumber(usedTokens), meta: '所有 Key 累计' },
    { key: 'failedCalls', label: '失败次数', value: failedCount, meta: '审计日志记录', intent: failedCount > 0 ? 'danger' : 'success' },
    { key: 'avgLatency', label: '平均延迟', value: avgLatency != null ? `${avgLatency}ms` : '--', meta: '审计日志记录' }
  ]
})

const apiKeys = ref([])
const overviewStats = ref({ totalCalls: 0, failedCalls: 0, avgLatency: null })

const modelsState = reactive(createAsyncState())
const models = ref([])

const recentCallsState = reactive(createAsyncState())
const recentCalls = ref([])

const activeQuickstartTab = ref('curl')
const quickstartTabs = [
  { key: 'curl', label: 'curl' },
  { key: 'nodejs', label: 'Node.js SDK' },
  { key: 'python', label: 'Python SDK' }
]

const quickstartCodeSnippets = computed(() => {
  const origin = baseUrl.value
  return {
    curl: `curl -sS "${origin}/v1/chat/completions" \\\\
  -H "Authorization: Bearer sk-orin-..." \\\\
  -H "Content-Type: application/json" \\\\
  -d '{
    "model": "deepseek-ai/DeepSeek-V3",
    "messages": [{"role": "user", "content": "Hello, ORIN!"}],
    "max_tokens": 256
  }'`,
    nodejs: `import OpenAI from "openai";

const client = new OpenAI({
  apiKey: "sk-orin-...",
  baseURL: "${origin}/v1",
});

const completion = await client.chat.completions.create({
  model: "deepseek-ai/DeepSeek-V3",
  messages: [{ role: "user", content: "Hello, ORIN!" }],
});
// → completion.choices[0].message.content`,
    python: `from openai import OpenAI

client = OpenAI(
    api_key="sk-orin-...",
    base_url="${origin}/v1",
)

completion = client.chat.completions.create(
    model="deepseek-ai/DeepSeek-V3",
    messages=[{"role": "user", "content": "Hello, ORIN!"}],
)
print(completion.choices[0].message.content)`
  }
})

const endpointStatusList = [
  {
    path: '/v1/chat/completions',
    method: 'POST',
    methodClass: 'method-post',
    statusLabel: '已开放',
    statusType: 'success'
  },
  {
    path: '/v1/models',
    method: 'GET',
    methodClass: 'method-get',
    statusLabel: '已开放',
    statusType: 'success'
  },
  {
    path: '/v1/embeddings',
    method: 'POST',
    methodClass: 'method-post',
    statusLabel: '默认关闭',
    statusType: 'info'
  }
]

function copyText(text) {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function formatTime(val) {
  if (!val) return '--'
  return dayjs(val).format('YYYY-MM-DD HH:mm:ss')
}

async function loadModels() {
  markLoading(modelsState)
  try {
    const data = await getModelList()
    if (Array.isArray(data)) {
      models.value = data
      markSuccess(modelsState)
    } else if (data && Array.isArray(data.content)) {
      models.value = data.content
      markSuccess(modelsState)
    } else if (data && Array.isArray(data.data)) {
      models.value = data.data
      markSuccess(modelsState)
    } else {
      models.value = []
      markEmpty(modelsState)
    }
  } catch {
    models.value = []
    markError(modelsState)
  }
}

async function loadRecentCalls() {
  markLoading(recentCallsState)
  try {
    const data = await getGatewayAuditLogs({ type: 'BUSINESS', size: 20 })
    const content = data?.content || data?.data || data
    if (Array.isArray(content)) {
      recentCalls.value = content
      content.length > 0 ? markSuccess(recentCallsState) : markEmpty(recentCallsState)
    } else {
      recentCalls.value = []
      markEmpty(recentCallsState)
    }
  } catch {
    recentCalls.value = []
    markError(recentCallsState)
  }
}

async function loadOverviewStats() {
  try {
    const data = await getGatewayAuditLogs({ type: 'BUSINESS', size: 1000 })
    const content = data?.content || data?.data || data
    if (Array.isArray(content) && content.length > 0) {
      const total = content.length
      const failed = content.filter((r) => !r.success).length
      const latencies = content.map((r) => r.responseTime).filter((v) => v != null)
      const avgLat = latencies.length > 0 ? Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length) : null
      overviewStats.value = { totalCalls: total, failedCalls: failed, avgLatency: avgLat }
    }
  } catch {
    // Silently fail — overview stats are best-effort
  }
}

async function loadApiKeys() {
  try {
    const data = await getAllApiKeys()
    if (Array.isArray(data)) {
      apiKeys.value = data
    } else if (data && Array.isArray(data.content)) {
      apiKeys.value = data.content
    } else if (data && Array.isArray(data.data)) {
      apiKeys.value = data.data
    }
  } catch {
    apiKeys.value = []
  }
}

async function loadGatewayConsole() {
  consoleLoading.value = true
  await Promise.all([
    loadApiKeys(),
    loadOverviewStats(),
    loadModels(),
    loadRecentCalls()
  ])
  consoleLoading.value = false
}

const {
  state: workbenchState,
  workbench,
  metrics,
  statusItems,
  loadWorkbench
} = useUnifiedGatewayWorkbench()

const {
  state: routesState,
  routes,
  controlPlaneCoverage,
  effectiveConfig,
  detailState,
  testResult,
  loadRoutes,
  loadEffectiveConfig,
  toggleRoute,
  runRouteTest,
  createLocalControlPlaneRoute
} = useUnifiedGatewayRoutes()

const { loadPolicies } = useUnifiedGatewayPolicies()

const controlPlaneEndpointPreview = computed(() => {
  const endpoints = workbench.value.controlPlaneCoverage?.endpoints || []
  return [
    ...endpoints.filter((item) => item.status === 'ATTENTION_REQUIRED')
  ].slice(0, 8)
})

const attentionControlPlaneEndpoints = computed(() =>
  (controlPlaneCoverage.value.endpoints || []).filter((item) => item.status === 'ATTENTION_REQUIRED')
)

const workbenchAttentionEndpoints = computed(() =>
  (workbench.value.controlPlaneCoverage?.endpoints || []).filter((item) => item.status === 'ATTENTION_REQUIRED')
)

const coverageSummary = computed(() => workbench.value.controlPlaneCoverage?.summary || {})

const attentionEndpointCount = computed(() => coverageSummary.value.attentionRequiredEndpoints || 0)

const workspaceSummaryMap = computed(() => {
  const keyCount = apiKeys.value.length
  const enabledCount = apiKeys.value.filter((k) => {
    const s = (k.status || '').toUpperCase()
    return s === 'ACTIVE' || s === 'ENABLED'
  }).length
  return {
    overview: `${keyCount} 个 Key · ${formatNumber(overviewStats.value.totalCalls)} 次调用`,
    api: '路由、服务与入口管理',
    access: `API Key 生命周期 · ${enabledCount} 启用`,
    traffic: '限流、熔断、重试与默认防线'
  }
})

const heroMetrics = computed(() => {
  const overview = workbench.value.overview || {}
  const healthy = overview.healthyInstances ?? 0
  const unhealthy = overview.unhealthyInstances ?? 0
  const totalInstances = healthy + unhealthy
  return [
    { key: 'requests', label: '总请求数', value: formatNumber(overview.totalRequests), meta: '累计网关流量' },
    { key: 'qps', label: '当前 QPS', value: overview.qps ?? 0, meta: '最近 6 分钟估算' },
    { key: 'latency', label: '平均延迟', value: `${overview.avgLatencyMs ?? 0}ms`, meta: '近 1 小时成功请求' },
    {
      key: 'errorRate',
      label: '错误率',
      value: `${overview.errorRate ?? 0}%`,
      meta: '异常请求占比',
      intent: Number(overview.errorRate || 0) > 0 ? 'danger' : 'success'
    },
    {
      key: 'activeRoutes',
      label: '活跃入口',
      value: overview.activeRoutes ?? 0,
      meta: '当前参与匹配的入口数量',
      intent: (overview.activeRoutes ?? 0) > 0 ? 'success' : 'warning'
    },
    {
      key: 'serviceHealth',
      label: '服务健康',
      value: `${healthy}/${totalInstances}`,
      meta: unhealthy > 0 ? `${unhealthy} 个异常实例` : '实例健康状态正常',
      intent: unhealthy > 0 ? 'danger' : 'success'
    }
  ]
})

const operationsSummary = computed(() => {
  const attention = attentionEndpointCount.value
  const failures = workbench.value.recentFailures?.length || 0
  const unhealthy = workbench.value.overview?.unhealthyInstances || 0
  const totalEvents = attention + failures + unhealthy
  if (totalEvents > 0) {
    return {
      title: `${totalEvents} 个入口问题需要处理`,
      description: '核心运行指标仍放在首位；这里只收敛会影响调用成功率、访问安全或上游健康的事项。',
      badge: '待处理项',
      summary: buildFocusSummary(attention, failures, unhealthy),
      intent: 'warning'
    }
  }
  return {
    title: '入口运行正常',
    description: '模型 API、服务代理和后台控制面没有失败诊断。下一步通常是测试核心入口或补充单独配置。',
    badge: '当前结论',
    summary: '没有需要立即处理的入口异常',
    intent: 'success'
  }
})

const entryLanes = computed(() => [
  {
    key: 'open-api',
    label: '开放能力面 /v1',
    value: `${workbench.value.overview?.activeRoutes || 0} 个入口`,
    meta: '模型 API 与 OpenAI 兼容入口。',
    intent: 'success'
  },
  {
    key: 'control-plane',
    label: '后台控制面 /api/v1',
    value: `${coverageSummary.value.baselineGovernedEndpoints || 0}/${coverageSummary.value.totalEndpoints || 0}`,
    meta: attentionEndpointCount.value > 0
      ? `${attentionEndpointCount.value} 个入口需要处理。`
      : '后台入口默认经过基础保护链路。',
    intent: attentionEndpointCount.value > 0 ? 'warning' : 'success'
  },
  {
    key: 'rescue',
    label: '救援入口',
    value: coverageSummary.value.rescueReservedEndpoints || 0,
    meta: '登录、健康检查、统一网关修复入口保留直连，配置错误时仍能救回系统。',
    intent: 'neutral'
  }
])

const primaryActions = computed(() => {
  if (attentionEndpointCount.value > 0) {
    return [
      { key: 'policy', label: '添加单独配置', type: 'primary', handler: handlePrimaryEntryAction },
      { key: 'test', label: '测试入口', type: 'default', handler: openRouteTest }
    ]
  }
  if ((workbench.value.recentFailures?.length || 0) > 0) {
    return [
      { key: 'test', label: '测试入口', type: 'primary', handler: openRouteTest },
      { key: 'api', label: '查看统一入口', type: 'default', handler: () => setActiveWorkspace('api') }
    ]
  }
  return [
    { key: 'test', label: '测试入口', type: 'primary', handler: openRouteTest },
    { key: 'traffic', label: '查看流量策略', type: 'default', handler: () => setActiveWorkspace('traffic') }
  ]
})

const quickActions = computed(() => [
  {
    key: 'test',
    label: '测试入口',
    description: '输入路径和方法，确认匹配、策略和目标。',
    handler: openRouteTest
  },
  {
    key: 'open-api',
    label: '配置统一入口',
    description: '维护 /v1、后台控制面或服务代理入口。',
    handler: () => setActiveWorkspace('api')
  },
  {
    key: 'proxy',
    label: '配置上游服务',
    description: '把上游服务、实例和健康检查接入统一入口。',
    handler: () => setActiveWorkspace('api')
  },
  {
    key: 'traffic',
    label: '流量策略',
    description: '维护限流、熔断、重试和平台底线。',
    handler: () => setActiveWorkspace('traffic')
  }
])

const secondaryRuntimeMetrics = computed(() =>
  metrics.value.filter((metric) => metric.key === 'coverage')
)

watch(
  () => route.query.workspace,
  (workspace) => {
    activeWorkspace.value = normalizeWorkspace(workspace)
  }
)

watch(activeWorkspace, (workspace) => {
  if (workspace === 'overview') loadGatewayConsole()
  if (workspace === 'api') loadRoutes()
  if (workspace === 'traffic') loadPolicies()
})

onMounted(() => {
  loadGatewayConsole()
  if (activeWorkspace.value === 'api') loadRoutes()
  if (activeWorkspace.value === 'traffic') loadPolicies()
})

const refreshCurrentWorkspace = () => {
  if (activeWorkspace.value === 'overview') loadGatewayConsole()
  if (activeWorkspace.value === 'api') loadRoutes()
  if (activeWorkspace.value === 'traffic') loadPolicies()
}

const openRouteDetail = async (row) => {
  selectedRouteId.value = row.id
  routeDrawerVisible.value = true
  await loadEffectiveConfig(row.id)
}

const reloadSelectedRoute = () => {
  if (selectedRouteId.value) loadEffectiveConfig(selectedRouteId.value)
}

function openRouteTest() {
  testDialogVisible.value = true
}

function buildFocusSummary(attention, failures, unhealthy) {
  const parts = []
  if (failures > 0) parts.push(`${failures} 条失败`)
  if (attention > 0) parts.push(`${attention} 个策略问题`)
  if (unhealthy > 0) parts.push(`${unhealthy} 个上游异常`)
  return parts.join('，')
}

function formatNumber(value) {
  const num = Number(value || 0)
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return String(num)
}

async function handlePrimaryEntryAction() {
  const firstAttention = workbenchAttentionEndpoints.value[0]
  if (firstAttention) {
    await createLocalControlPlaneRoute(firstAttention)
    await loadWorkbench()
    setActiveWorkspace('api')
    return
  }
  if (workbench.value.recentFailures?.length) {
    setActiveWorkspace('api')
    return
  }
  openRouteTest()
}

const runTest = async () => {
  if (!testForm.path) {
    ElMessage.warning('请输入请求路径')
    return
  }
  testing.value = true
  try {
    await runRouteTest(testForm)
  } finally {
    testing.value = false
  }
}

const entryType = (row) => {
  if (row.pathPattern?.startsWith('/v1')) return { label: '开放 API', tag: 'success' }
  if (row.targetUrl) return { label: '直连上游', tag: 'success' }
  if (row.serviceId) return { label: '服务代理', tag: 'primary' }
  return { label: '后台控制面', tag: 'info' }
}

const routeTarget = (row) => {
  if (row.targetUrl) return row.targetUrl
  if (row.serviceName) return row.serviceName
  if (row.serviceId) return `Service#${row.serviceId}`
  return 'ORIN 后台处理'
}

const formatMethods = (methods = []) => {
  if (!methods.length) return 'ALL'
  return methods.join(', ')
}

const coverageStatusLabel = (status) => {
  if (status === 'POLICY_ENFORCED') return '单独配置'
  if (status === 'BASELINE_GOVERNED') return '基础保护'
  if (status === 'RESCUE_RESERVED') return '救援保留'
  return '需要处理'
}

const coverageStatusType = (status) => {
  if (status === 'POLICY_ENFORCED' || status === 'BASELINE_GOVERNED') return 'success'
  if (status === 'RESCUE_RESERVED') return 'info'
  return 'warning'
}
</script>

<style scoped>
.unified-gateway-workbench {
  color: #243244;
}

.gateway-console {
  overflow: visible;
  border: 1px solid var(--orin-border, #e2e8f0);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
  box-shadow: 0 14px 36px -34px rgba(15, 23, 42, 0.5);
}

.gateway-hero {
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--orin-border, #e2e8f0);
  background: var(--neutral-white, #ffffff);
}

.gateway-hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.gateway-hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.gateway-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(240, 253, 250, 0.78);
  color: var(--orin-primary, #0d9488);
  font-size: 18px;
}

.gateway-title-block {
  min-width: 0;
}

.gateway-title-block h1 {
  margin: 0;
  color: #0f172a;
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: 0;
}

.gateway-title-block p {
  margin: 7px 0 0;
  max-width: 780px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.gateway-hero-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.gateway-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.gateway-summary-card {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.gateway-summary-card:hover,
.gateway-summary-card.active {
  border-color: rgba(15, 118, 110, 0.22);
  background: #ffffff;
  box-shadow: 0 8px 18px -16px rgba(15, 23, 42, 0.45);
}

.gateway-summary-card .el-icon {
  margin-top: 2px;
  color: var(--orin-primary, #0d9488);
}

.gateway-summary-card span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.gateway-summary-card strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.gateway-summary-card small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gateway-content-panel {
  padding: 14px;
  background: transparent;
  overflow: visible;
}

.workspace-panel {
  margin-top: 0;
}

.command-eyebrow {
  display: block;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.runtime-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 0.8fr);
  gap: 16px;
  align-items: stretch;
}

.runtime-hero-main,
.operations-card {
  min-width: 0;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.runtime-hero-main {
  padding: 16px;
}

.runtime-hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.runtime-hero-head h2,
.operations-card h3 {
  margin: 6px 0 0;
  color: #172033;
  letter-spacing: 0;
}

.runtime-hero-head h2 {
  font-size: 20px;
  line-height: 1.25;
}

.hero-updated {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.hero-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr));
  gap: 10px;
}

.hero-metric-card {
  min-width: 0;
  min-height: 112px;
  padding: 14px;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.hero-metric-card span,
.hero-metric-card small,
.operation-facts span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.hero-metric-card span,
.operation-facts span {
  font-weight: 800;
}

.hero-metric-card strong {
  display: block;
  margin: 10px 0 8px;
  color: #172033;
  font-size: 28px;
  line-height: 1;
}

.hero-metric-card.intent-success strong {
  color: #047857;
}

.hero-metric-card.intent-warning strong {
  color: #b45309;
}

.hero-metric-card.intent-danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.hero-metric-card.intent-danger strong {
  color: #dc2626;
}

.operations-card {
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.operations-card h3 {
  font-size: 17px;
  line-height: 1.35;
}

.operations-card p {
  margin: 0;
  color: #53657d;
  font-size: 13px;
  line-height: 1.65;
}

.operation-facts {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #f8fafc;
}

.operation-facts strong {
  color: #172033;
  font-size: 15px;
  line-height: 1.35;
}

.operations-card.intent-warning .operation-facts {
  border-color: #f1b84b;
  background: #fffaf0;
}

.operations-card.intent-warning .operation-facts strong {
  color: #b45309;
}

.operations-card.intent-success .operation-facts {
  border-color: #b6ead6;
  background: #f0fdf7;
}

.operations-card.intent-success .operation-facts strong {
  color: #047857;
}

.command-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.overview-secondary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.entry-map {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.entry-map.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.entry-lane {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
}

.lane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.lane-head span {
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.lane-head strong {
  color: #172033;
  font-size: 18px;
  line-height: 1;
  text-align: right;
}

.entry-lane p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.lane-warning {
  border-color: #f8c26a;
  background: #fffaf0;
}

.lane-warning .lane-head strong {
  color: #d97706;
}

.lane-success .lane-head strong {
  color: #059669;
}

.block-gap {
  margin-top: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.75fr) minmax(420px, 1.25fr);
  gap: 16px;
}

.table-head,
.head-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-head {
  width: 100%;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.table-head > span:last-child {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.quick-action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 16px 16px;
}

.quick-action-grid button {
  display: grid;
  gap: 6px;
  min-height: 76px;
  padding: 12px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.quick-action-grid button:hover {
  border-color: #0d9488;
  background: #f0fdfa;
}

.quick-action-grid span {
  color: #172033;
  font-size: 13px;
  font-weight: 800;
}

.quick-action-grid small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.runtime-section {
  padding: 16px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.api-entry-grid {
  display: grid;
  gap: 16px;
}

.access-workspace,
.traffic-workspace {
  display: grid;
  gap: 16px;
}

.access-list-section {
  min-width: 0;
}

.workspace-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 16px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #fbfdff;
}

.workspace-section-head h3 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 18px;
  line-height: 1.3;
}

.workspace-section-head p {
  max-width: 560px;
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  text-align: right;
}

.access-workspace :deep(.gateway-acl-tab),
.traffic-workspace :deep(.gateway-policies-tab),
.traffic-workspace :deep(.gateway-rate-limit-tab) {
  min-width: 0;
}

.security-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.security-sidebar,
.security-main {
  min-width: 0;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.security-sidebar {
  position: sticky;
  top: 16px;
  overflow: hidden;
}

.security-sidebar-head {
  padding: 16px;
  border-bottom: 1px solid #e5edf5;
}

.security-sidebar-head h2,
.security-main-head h3 {
  margin: 6px 0 0;
  color: #172033;
  letter-spacing: 0;
}

.security-sidebar-head h2 {
  font-size: 18px;
  line-height: 1.3;
}

.security-sidebar-head p,
.security-main-head p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.security-stat-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid #e5edf5;
}

.security-stat-list article {
  min-width: 0;
  padding: 12px 10px;
  border-right: 1px solid #e5edf5;
  background: #fbfdff;
}

.security-stat-list article:last-child {
  border-right: 0;
}

.security-stat-list span,
.security-stat-list small {
  display: block;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.security-stat-list span {
  font-weight: 800;
}

.security-stat-list strong {
  display: block;
  margin: 8px 0 5px;
  color: #172033;
  font-size: 20px;
  line-height: 1;
}

.security-nav {
  display: grid;
  gap: 6px;
  padding: 10px;
}

.security-nav button {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.security-nav button:hover {
  background: #f8fafc;
}

.security-nav button.active {
  border-color: #0d9488;
  background: #ecfdf9;
}

.security-nav span {
  color: #172033;
  font-size: 13px;
  font-weight: 800;
}

.security-nav small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.security-main {
  padding: 0;
  overflow: hidden;
}

.security-main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 16px;
  border-bottom: 1px solid #e5edf5;
  background: #fbfdff;
}

.security-main-head h3 {
  font-size: 18px;
  line-height: 1.3;
}

.security-main-head p {
  max-width: 520px;
  text-align: right;
}

.security-main :deep(.gateway-policies-tab),
.security-main :deep(.gateway-acl-tab),
.security-main :deep(.gateway-rate-limit-tab) {
  padding: 16px;
}

.security-main :deep(.section-card),
.security-main :deep(.el-card) {
  border-radius: 8px;
  border-color: #d8e0e8;
  box-shadow: none;
}

.security-main :deep(.policy-nav) {
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title span {
  color: #172033;
  font-size: 15px;
  font-weight: 800;
}

.section-title small {
  color: #64748b;
  font-size: 12px;
}

.path-text,
.target-text {
  margin-left: 8px;
  color: #334155;
  font-size: 13px;
}

.route-summary {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
}

.route-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.route-summary strong {
  color: #1e293b;
  font-size: 15px;
}

.warning-line {
  margin: 0 16px 12px;
}

.chain-list {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.chain-list li {
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.chain-label {
  margin-right: 8px;
  color: #1e293b;
  font-weight: 700;
}

.chain-list p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .gateway-hero-row {
    flex-direction: column;
  }

  .gateway-hero-actions {
    justify-content: flex-start;
  }

  .gateway-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .runtime-hero,
  .overview-secondary-grid,
  .overview-grid,
  .security-workspace {
    grid-template-columns: 1fr;
  }

  .security-sidebar {
    position: static;
  }

  .hero-metric-grid,
  .entry-map.compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-action-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .gateway-hero {
    padding: 14px 14px 16px;
  }

  .gateway-summary {
    grid-template-columns: 1fr;
  }

  .gateway-content-panel {
    padding: 10px;
  }

  .runtime-hero-head,
  .table-head,
  .head-actions,
  .workspace-section-head,
  .security-main-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-metric-grid,
  .entry-map.compact,
  .security-stat-list {
    grid-template-columns: 1fr;
  }

  .security-stat-list article {
    border-right: 0;
    border-bottom: 1px solid #e5edf5;
  }

  .security-stat-list article:last-child {
    border-bottom: 0;
  }

  .security-main-head p {
    max-width: none;
    text-align: left;
  }

  .workspace-section-head p {
    max-width: none;
    text-align: left;
  }
}

html.dark .gateway-console {
  background: var(--neutral-gray-900, #0f172a);
  box-shadow: none;
}

html.dark .gateway-hero {
  background: var(--neutral-gray-900, #0f172a);
}

html.dark .gateway-title-block h1,
html.dark .gateway-summary-card strong {
  color: #f8fafc;
}

html.dark .gateway-title-block p,
html.dark .gateway-summary-card small {
  color: #94a3b8;
}

/* ---- Gateway Console: Base URL & Endpoint bar ---- */
.gateway-baseurl-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 10px 14px;
  border: 1px solid rgba(15, 118, 110, 0.14);
  border-radius: 8px;
  background: rgba(240, 253, 250, 0.32);
}

html.dark .gateway-baseurl-bar {
  border-color: rgba(45, 212, 191, 0.16);
  background: rgba(15, 118, 110, 0.08);
}

.baseurl-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.baseurl-label {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

html.dark .baseurl-label {
  color: #5eead4;
}

.baseurl-value {
  padding: 3px 10px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 4px;
  background: #ffffff;
  color: #0f172a;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .baseurl-value {
  border-color: rgba(45, 212, 191, 0.22);
  background: #0f172a;
  color: #f8fafc;
}

.endpoint-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.endpoint-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #d8e0e8;
  border-radius: 6px;
  background: #ffffff;
  font-size: 12px;
  white-space: nowrap;
}

html.dark .endpoint-chip {
  border-color: #334155;
  background: #1e293b;
}

.endpoint-chip.open {
  border-color: #b6ead6;
  background: #f0fdf7;
}

html.dark .endpoint-chip.open {
  border-color: #065f46;
  background: #022c22;
}

.endpoint-chip.closed {
  border-color: #e2e8f0;
  background: #f8fafc;
}

html.dark .endpoint-chip.closed {
  border-color: #334155;
  background: #1e293b;
}

.chip-method {
  color: #0f766e;
  font-weight: 800;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
}

.chip-path {
  color: #334155;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
}

html.dark .chip-method {
  color: #5eead4;
}

html.dark .chip-path {
  color: #cbd5e1;
}

/* ---- Panel card ---- */
.panel-card {
  border: 1px solid #d8e0e8 !important;
  border-radius: 8px;
  box-shadow: none !important;
}

html.dark .panel-card {
  border-color: #334155 !important;
  background: #0f172a;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #172033;
  font-size: 15px;
  font-weight: 800;
}

html.dark .panel-header {
  color: #f8fafc;
}

.panel-header-meta {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

html.dark .panel-header-meta {
  color: #94a3b8;
}

/* ---- Quickstart tabs ---- */
.quickstart-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
}

html.dark .quickstart-tabs {
  border-color: #334155;
  background: #1e293b;
}

.qs-tab-btn {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s ease;
}

.qs-tab-btn:hover {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.06);
}

.qs-tab-btn.active {
  border-color: #0d9488;
  background: #ffffff;
  color: #0f172a;
}

html.dark .qs-tab-btn.active {
  border-color: #5eead4;
  background: #0f172a;
  color: #f8fafc;
}

/* ---- Code block ---- */
.code-block-wrapper {
  position: relative;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  overflow: hidden;
}

html.dark .code-block-wrapper {
  border-color: #334155;
}

.code-block {
  margin: 0;
  padding: 14px;
  background: #f8fafc;
  color: #334155;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-x: auto;
  max-height: 280px;
}

html.dark .code-block {
  background: #020617;
  color: #cbd5e1;
}

.copy-code-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  background: #ffffff;
  border-radius: 4px;
}

html.dark .copy-code-btn {
  background: #0f172a;
}

/* ---- Endpoint status list ---- */
.endpoint-status-list {
  display: grid;
  gap: 8px;
}

.endpoint-status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fbfdff;
}

html.dark .endpoint-status-item {
  border-color: #1e293b;
  background: #0f172a;
}

.ep-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ep-method-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 800;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  color: #ffffff;
}

.ep-method-tag.method-post {
  background: #0d9488;
}

.ep-method-tag.method-get {
  background: #2563eb;
}

.ep-path {
  color: #334155;
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .ep-path {
  color: #cbd5e1;
}

/* ---- Model list ---- */
.model-list-section {
  min-height: 48px;
}

.section-subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #172033;
  font-size: 13px;
  font-weight: 800;
}

html.dark .section-subhead {
  color: #f8fafc;
}

.model-count {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

html.dark .model-count {
  color: #94a3b8;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-tag {
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  font-size: 11px;
}

/* ---- Trace ID cell ---- */
.trace-id-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.trace-id-text {
  font-family: 'Fira Code', Monaco, Consolas, monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f766e;
}

html.dark .trace-id-text {
  color: #5eead4;
}
</style>
