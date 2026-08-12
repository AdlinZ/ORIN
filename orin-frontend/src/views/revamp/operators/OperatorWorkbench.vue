<template>
  <div class="page-container operator-workbench">
    <OrinPageShell
      title="运维工作台"
      description="AI 应用管理员日常入口：我的资产、绑定状态、调试会话、API Key 用量与待处理文档"
      icon="DataAnalysis"
      domain="智能体中枢"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push(ROUTES.AGENTS.ONBOARD)">
          接入智能体
        </el-button>
        <el-button :icon="Collection" @click="$router.push(ROUTES.KNOWLEDGE.CREATE)">
          新建知识库
        </el-button>
        <el-button class="refresh-button" :icon="Refresh" :loading="loading" @click="loadAll">
          刷新
        </el-button>
      </template>
    </OrinPageShell>

    <p class="scope-note">
      非管理员默认只看到自己拥有的智能体与知识库；管理员可见平台全部资产。绑定与调试摘要来自真实会话与工具绑定接口。
    </p>

    <section class="kpi-grid">
      <article class="kpi-card">
        <span class="kpi-label">智能体</span>
        <strong class="kpi-value">{{ agents.length }}</strong>
        <span class="kpi-meta">已发布 {{ publishedAgentCount }}</span>
      </article>
      <article class="kpi-card">
        <span class="kpi-label">知识库</span>
        <strong class="kpi-value">{{ knowledgeBases.length }}</strong>
        <span class="kpi-meta">启用 {{ enabledKbCount }}</span>
      </article>
      <article class="kpi-card" :class="{ danger: pendingDocuments.length > 0 }">
        <span class="kpi-label">待处理文档</span>
        <strong class="kpi-value">{{ pendingDocuments.length }}</strong>
        <span class="kpi-meta">向量化 PENDING / FAILED</span>
      </article>
      <article class="kpi-card">
        <span class="kpi-label">最近 Trace</span>
        <strong class="kpi-value">{{ recentTraces.length }}</strong>
        <span class="kpi-meta">近次调用摘要</span>
      </article>
    </section>

    <section class="quick-link-row">
      <el-button
        v-for="link in quickLinks"
        :key="link.path"
        plain
        @click="$router.push(link.path)"
      >
        {{ link.title }}
      </el-button>
      <el-button plain @click="$router.push(ROUTES.AGENTS.WORKSPACE)">调试工作台</el-button>
      <el-button plain @click="$router.push(ROUTES.KNOWLEDGE.RETRIEVAL_LAB)">知识检索</el-button>
      <el-button plain @click="$router.push(ROUTES.PORTAL_API_KEYS)">我的 API Key</el-button>
    </section>

    <div class="panel-grid">
      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>智能体</span>
            <el-button link type="primary" @click="$router.push(ROUTES.AGENTS.LIST)">全部</el-button>
          </div>
        </template>
        <OrinAsyncState :status="agentState.status" empty-text="暂无智能体" @retry="loadAgents">
          <ul class="asset-list">
            <li v-for="agent in agents.slice(0, 8)" :key="agent.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ agent.name }}</strong>
                <span>{{ agent.providerType || '-' }} · {{ agent.modelName || agent.model || '-' }}</span>
              </div>
              <div class="asset-tags">
                <el-tag size="small" :type="agent.mcpExposed ? 'success' : 'info'" effect="plain">
                  {{ agent.mcpExposed ? '已发布' : '未发布' }}
                </el-tag>
                <el-tag size="small" :type="agent.boundKbCount > 0 ? 'success' : 'warning'" effect="plain">
                  绑定 KB {{ agent.boundKbCount ?? 0 }}
                </el-tag>
                <el-button link type="primary" @click="openAgentWorkspace(agent)">调试</el-button>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>知识库</span>
            <el-button link type="primary" @click="$router.push(ROUTES.KNOWLEDGE.ASSETS)">全部</el-button>
          </div>
        </template>
        <OrinAsyncState :status="kbState.status" empty-text="暂无知识库" @retry="loadKnowledge">
          <ul class="asset-list">
            <li v-for="kb in knowledgeBases.slice(0, 8)" :key="kb.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ kb.name }}</strong>
                <span>{{ kb.type || '-' }} · {{ kbStatusText(kb.status) }}</span>
              </div>
              <div class="asset-tags">
                <el-tag size="small" :type="kb.status === 'ENABLED' ? 'success' : 'info'" effect="plain">
                  {{ kbStatusText(kb.status) }}
                </el-tag>
                <el-tag v-if="kb.ownerUserId" size="small" effect="plain">
                  归属 {{ kb.ownerUserId }}
                </el-tag>
                <el-button link type="primary" @click="openKbDetail(kb)">详情</el-button>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>待处理文档</span>
            <el-button link type="primary" @click="$router.push(ROUTES.KNOWLEDGE.ASSETS)">去处理</el-button>
          </div>
        </template>
        <OrinAsyncState :status="pendingState.status" empty-text="没有待处理文档" @retry="loadPending">
          <ul class="asset-list">
            <li v-for="doc in pendingDocuments.slice(0, 8)" :key="doc.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ doc.fileName || doc.id }}</strong>
                <span>向量 {{ doc.vectorStatus || '-' }} · 解析 {{ doc.parseStatus || '-' }}</span>
              </div>
              <div class="asset-tags">
                <el-tag size="small" type="warning" effect="plain">{{ doc.vectorStatus }}</el-tag>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>最近调试</span>
            <span class="panel-sub">会话更新时间</span>
          </div>
        </template>
        <OrinAsyncState :status="debugState.status" empty-text="暂无调试会话" @retry="loadRecentDebug">
          <ul class="asset-list">
            <li v-for="session in recentSessions.slice(0, 8)" :key="session.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ session.title || '未命名会话' }}</strong>
                <span>{{ session.agentName || session.agentId }} · {{ formatTime(session.updatedAt || session.createdAt) }}</span>
              </div>
              <div class="asset-tags">
                <el-button link type="primary" @click="openSession(session)">继续</el-button>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>API Key 用量</span>
            <el-button link type="primary" @click="$router.push(ROUTES.PORTAL_API_KEYS)">管理</el-button>
          </div>
        </template>
        <OrinAsyncState :status="apiKeyState.status" empty-text="暂无 API Key" @retry="loadApiKeyUsage">
          <ul class="asset-list">
            <li v-for="item in apiKeyUsage.slice(0, 5)" :key="item.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ item.name }}</strong>
                <span>
                  近窗调用 {{ item.totalCalls ?? 0 }} · 失败率 {{ formatPercent(item.failureRate) }}
                  · 最近 {{ formatTime(item.lastUsedAt) }}
                </span>
              </div>
              <div class="asset-tags">
                <el-tag size="small" :type="item.enabled === false ? 'info' : 'success'" effect="plain">
                  {{ item.enabled === false ? '已禁用' : '启用' }}
                </el-tag>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>

      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span>最近 Trace</span>
            <span class="panel-sub">查看详情或复制 ID</span>
          </div>
        </template>
        <OrinAsyncState :status="traceState.status" empty-text="暂无 Trace" @retry="loadTraces">
          <ul class="asset-list">
            <li v-for="trace in recentTraces.slice(0, 8)" :key="trace.traceId || trace.id" class="asset-row">
              <div class="asset-main">
                <strong>{{ trace.traceId || trace.id }}</strong>
                <span>{{ formatTime(trace.createdAt || trace.startTime) }}</span>
              </div>
              <div class="asset-tags">
                <el-button
                  v-if="trace.traceId || trace.id"
                  link
                  type="primary"
                  @click="openTrace(trace)"
                >
                  查看
                </el-button>
                <el-button
                  v-if="trace.traceId || trace.id"
                  link
                  @click="copyTraceId(trace)"
                >
                  复制 ID
                </el-button>
              </div>
            </li>
          </ul>
        </OrinAsyncState>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Collection, Plus, Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ROUTES } from '@/router/routes'
import { getAgentList } from '@/api/agent'
import { getKnowledgeList, getPendingDocuments } from '@/api/knowledge'
import { getRecentTraces } from '@/api/trace'
import { getDashboardSummary } from '@/api/dashboard'
import { getAgentToolBinding, listChatSessions } from '@/api/agent-chat'
import { getAllApiKeys, getApiKeyUsage } from '@/api/apiKey'
import { toDashboardSummaryViewModel } from '@/viewmodels/adapters/dashboard'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess
} from '@/viewmodels'

const router = useRouter()
const loading = ref(false)
const agents = ref([])
const knowledgeBases = ref([])
const pendingDocuments = ref([])
const recentTraces = ref([])
const recentSessions = ref([])
const apiKeyUsage = ref([])
const quickLinks = ref([])

const agentState = reactive(createAsyncState())
const kbState = reactive(createAsyncState())
const pendingState = reactive(createAsyncState())
const traceState = reactive(createAsyncState())
const debugState = reactive(createAsyncState())
const apiKeyState = reactive(createAsyncState())

const publishedAgentCount = computed(() => agents.value.filter((item) => item.mcpExposed).length)
const enabledKbCount = computed(() => knowledgeBases.value.filter((item) => item.status === 'ENABLED').length)

const kbStatusText = (status) => {
  if (status === 'ENABLED') return '启用'
  if (status === 'DISABLED') return '停用'
  return status || '未知'
}

const formatTime = (value) => {
  if (!value) return '-'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('MM-DD HH:mm') : String(value)
}

const formatPercent = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `${Math.round(num * 100)}%`
}

const normalizeList = (payload) => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  if (Array.isArray(payload?.content)) return payload.content
  return []
}

const loadAgents = async () => {
  markLoading(agentState)
  try {
    const rows = normalizeList(await getAgentList())
    const mapped = rows.map((item) => ({
      id: item.id || item.agentId,
      name: item.name || item.agentName || '未命名智能体',
      providerType: item.providerType || item.provider,
      modelName: item.modelName || item.model,
      mcpExposed: Boolean(item.mcpExposed),
      ownerUserId: item.ownerUserId ?? null,
      boundKbCount: 0
    }))
    const withBindings = await Promise.all(mapped.slice(0, 12).map(async (agent) => {
      if (!agent.id) return agent
      try {
        const binding = await getAgentToolBinding(agent.id)
        const kbIds = Array.isArray(binding?.kbIds) ? binding.kbIds : []
        return { ...agent, boundKbCount: kbIds.length }
      } catch {
        return agent
      }
    }))
    const bindingById = new Map(withBindings.map((item) => [item.id, item.boundKbCount]))
    agents.value = mapped.map((agent) => ({
      ...agent,
      boundKbCount: bindingById.get(agent.id) ?? 0
    }))
    if (!agents.value.length) markEmpty(agentState)
    else markSuccess(agentState)
  } catch (error) {
    markError(agentState, error)
  }
}

const loadKnowledge = async () => {
  markLoading(kbState)
  try {
    knowledgeBases.value = normalizeList(await getKnowledgeList()).map((item) => ({
      id: item.id,
      name: item.name || '未命名知识库',
      type: item.type,
      status: item.status || 'ENABLED',
      ownerUserId: item.ownerUserId ?? null
    }))
    if (!knowledgeBases.value.length) markEmpty(kbState)
    else markSuccess(kbState)
  } catch (error) {
    markError(kbState, error)
  }
}

const loadPending = async () => {
  markLoading(pendingState)
  try {
    pendingDocuments.value = normalizeList(await getPendingDocuments())
    if (!pendingDocuments.value.length) markEmpty(pendingState)
    else markSuccess(pendingState)
  } catch (error) {
    markError(pendingState, error)
  }
}

const loadTraces = async () => {
  markLoading(traceState)
  try {
    recentTraces.value = normalizeList(await getRecentTraces(8))
    if (!recentTraces.value.length) markEmpty(traceState)
    else markSuccess(traceState)
  } catch (error) {
    markError(traceState, error)
  }
}

const loadRecentDebug = async () => {
  markLoading(debugState)
  try {
    const candidates = agents.value.slice(0, 5)
    if (!candidates.length) {
      recentSessions.value = []
      markEmpty(debugState)
      return
    }
    const batches = await Promise.all(candidates.map(async (agent) => {
      try {
        const sessions = normalizeList(await listChatSessions({ agentId: agent.id }))
        return sessions.slice(0, 3).map((session) => ({
          id: session.id || session.sessionId,
          agentId: agent.id,
          agentName: agent.name,
          title: session.title || '未命名会话',
          createdAt: session.createdAt,
          updatedAt: session.updatedAt || session.createdAt
        }))
      } catch {
        return []
      }
    }))
    recentSessions.value = batches.flat()
      .sort((a, b) => dayjs(b.updatedAt || 0).valueOf() - dayjs(a.updatedAt || 0).valueOf())
      .slice(0, 8)
    if (!recentSessions.value.length) markEmpty(debugState)
    else markSuccess(debugState)
  } catch (error) {
    markError(debugState, error)
  }
}

const loadApiKeyUsage = async () => {
  markLoading(apiKeyState)
  try {
    const keys = normalizeList(await getAllApiKeys()).slice(0, 5)
    const rows = await Promise.all(keys.map(async (key) => {
      const id = key.id || key.keyId
      let usage = {}
      try {
        usage = await getApiKeyUsage(id, { limit: 5 }) || {}
      } catch {
        usage = {}
      }
      return {
        id,
        name: key.name || id,
        enabled: key.enabled !== false && key.status !== 'DISABLED',
        totalCalls: usage.totalCalls ?? usage.total_calls ?? 0,
        failureRate: usage.failureRate ?? usage.failure_rate ?? null,
        lastUsedAt: usage.lastUsedAt || usage.last_used_at || key.lastUsedAt || null
      }
    }))
    apiKeyUsage.value = rows
    if (!apiKeyUsage.value.length) markEmpty(apiKeyState)
    else markSuccess(apiKeyState)
  } catch (error) {
    markError(apiKeyState, error)
  }
}

const loadSummary = async () => {
  try {
    const summary = toDashboardSummaryViewModel(await getDashboardSummary({ silentError: true }))
    quickLinks.value = summary.quickLinks || []
  } catch {
    quickLinks.value = []
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadAgents(), loadKnowledge(), loadPending(), loadTraces(), loadSummary(), loadApiKeyUsage()])
    await loadRecentDebug()
  } finally {
    loading.value = false
  }
}

const openAgentWorkspace = (agent) => {
  if (!agent?.id) return
  router.push(ROUTES.AGENTS.CONSOLE.replace(':id', agent.id))
}

const openSession = (session) => {
  if (!session?.agentId) return
  router.push({
    path: ROUTES.AGENTS.CONSOLE.replace(':id', session.agentId),
    query: session.id ? { sessionId: session.id } : undefined
  })
}

const openKbDetail = (kb) => {
  if (!kb?.id) return
  router.push(ROUTES.KNOWLEDGE.DETAIL.replace(':id', kb.id))
}

const openTrace = (trace) => {
  const traceId = trace?.traceId || trace?.id
  if (!traceId) return
  router.push(ROUTES.MONITOR.TRACE_DETAIL.replace(':traceId', encodeURIComponent(String(traceId))))
}

const copyTraceId = async (trace) => {
  const traceId = trace?.traceId || trace?.id
  if (!traceId) return
  try {
    await navigator.clipboard.writeText(String(traceId))
    ElMessage.success(`已复制 Trace ID：${traceId}`)
  } catch {
    ElMessage.info(`Trace ID：${traceId}`)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.operator-workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scope-note {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kpi-card.danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.kpi-label {
  color: #64748b;
  font-size: 13px;
}

.kpi-value {
  font-size: 28px;
  line-height: 1.1;
  color: #0f172a;
}

.kpi-meta {
  color: #94a3b8;
  font-size: 12px;
}

.quick-link-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.panel-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.panel-sub {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 400;
}

.asset-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.asset-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.asset-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.asset-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.asset-main strong {
  color: #0f172a;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-main span {
  color: #94a3b8;
  font-size: 12px;
}

.asset-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

@media (max-width: 1100px) {
  .kpi-grid,
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
