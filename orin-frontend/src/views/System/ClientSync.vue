<template>
  <div class="sync-container page-container fade-in" :class="{ embedded }">
    <section class="sync-console">
      <header class="sync-hero">
        <div v-if="!embedded" class="sync-hero-row">
          <div class="sync-hero-main">
            <div class="sync-icon">
              <el-icon><Refresh /></el-icon>
            </div>
            <div class="sync-title-block">
              <h1>数据同步</h1>
              <p>管理知识库端侧同步、Webhook 回调与 Dify 上游同步。</p>
            </div>
          </div>

          <div class="sync-hero-actions">
            <el-button
              :icon="Refresh"
              :loading="statusLoading"
              :disabled="syncPolling"
              @click="loadStatus"
            >
              刷新状态
            </el-button>
          </div>
        </div>

        <div class="sync-summary" data-testid="sync-workspaces">
          <button
            v-for="tab in tabs"
            :key="tab.name"
            type="button"
            :class="['sync-summary-card', { active: activeTab === tab.name }]"
            @click="switchTab(tab.name)"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            <span>
              <strong>{{ tab.label }}</strong>
              <small>{{ tabSummaryMap[tab.name] }}</small>
            </span>
          </button>
        </div>
      </header>

      <section class="sync-content-panel">
        <!-- 状态栏 -->
        <div v-if="activeTab !== 'dify'" class="status-bar">
          <div class="status-item">
            <span class="status-label">最新检查点</span>
            <el-tag :type="checkpointData?.checkpoint ? 'success' : 'info'" size="small">
              {{ checkpointData?.checkpoint ? formatDateTime(checkpointData.checkpoint) : '暂无' }}
            </el-tag>
          </div>
          <div class="status-item">
            <span class="status-label">待同步变更</span>
            <el-tag :type="pendingCount > 0 ? 'warning' : 'success'" size="small">
              {{ pendingCount }}
            </el-tag>
          </div>
        </div>

        <el-alert
          v-if="syncPolling && activeTab !== 'dify'"
          class="sync-polling-alert"
          type="info"
          :closable="false"
          show-icon
          title="同步进行中，正在自动刷新状态..."
        />

        <!-- 变更记录 -->
        <div v-show="activeTab === 'changes'" class="tab-content">
          <div class="content-toolbar">
            <div class="toolbar-left">
              <el-button
                size="small"
                :icon="Refresh"
                :loading="changesLoading"
                @click="loadChanges"
              >
                刷新
              </el-button>
            </div>
            <div class="toolbar-right">
              <el-button
                type="primary"
                size="small"
                :loading="syncing"
                :disabled="syncPolling || !agentList.length"
                @click="handleFullSync"
              >
                全量同步
              </el-button>
              <el-button
                type="success"
                size="small"
                :loading="syncing"
                :disabled="syncPolling || !agentList.length"
                @click="handleIncrementalSync"
              >
                增量同步
              </el-button>
            </div>
          </div>

          <el-table v-loading="changesLoading" :data="changes" stripe>
            <template #empty>
              <el-empty description="暂无变更记录" :image-size="80" />
            </template>
            <el-table-column
              prop="agentId"
              label="Agent"
              width="170"
              show-overflow-tooltip
            />
            <el-table-column
              prop="documentId"
              label="文档ID"
              width="200"
              show-overflow-tooltip
            />
            <el-table-column
              prop="knowledgeBaseId"
              label="知识库ID"
              width="150"
              show-overflow-tooltip
            />
            <el-table-column prop="changeType" label="变更类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getChangeTypeTag(row.changeType)" size="small">
                  {{ row.changeType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column
              prop="contentHash"
              label="Hash"
              width="150"
              show-overflow-tooltip
            />
            <el-table-column prop="changedAt" label="变更时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.changedAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="synced" label="已同步" width="80">
              <template #default="{ row }">
                <el-tag :type="row.synced ? 'success' : 'warning'" size="small">
                  {{ row.synced ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="changesPage"
              v-model:page-size="changesSize"
              :total="changesTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadChanges"
              @current-change="loadChanges"
            />
          </div>
        </div>

        <!-- Webhook -->
        <div v-show="activeTab === 'webhooks'" class="tab-content">
          <div class="content-toolbar">
            <div class="toolbar-left" />
            <div class="toolbar-right">
              <el-button type="primary" size="small" @click="openWebhookDialog">
                添加 Webhook
              </el-button>
            </div>
          </div>

          <el-table v-loading="webhooksLoading" :data="webhooks">
            <template #empty>
              <el-empty description="暂无 Webhook 配置" :image-size="80" />
            </template>
            <el-table-column
              prop="agentId"
              label="Agent"
              width="170"
              show-overflow-tooltip
            />
            <el-table-column prop="webhookUrl" label="URL" show-overflow-tooltip />
            <el-table-column prop="eventTypes" label="事件类型" width="220">
              <template #default="{ row }">
                <el-tag
                  v-for="event in (row.eventTypes || '').split(',').filter(Boolean)"
                  :key="event"
                  size="small"
                  class="event-tag"
                >
                  {{ event }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.disabled ? 'danger' : row.enabled ? 'success' : 'info'" size="small">
                  {{ row.disabled ? '已失效' : row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="failureCount" label="失败次数" width="90">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.failureCount > 0 }">{{ row.failureCount || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastFailureTime" label="最后失败" width="160">
              <template #default="{ row }">
                {{ row.lastFailureTime ? formatDateTime(row.lastFailureTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button
                  v-if="row.disabled"
                  type="primary"
                  size="small"
                  text
                  @click="handleReenableWebhook(row.id)"
                >
                  重新启用
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  text
                  @click="handleDeleteWebhook(row.id)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Dify 同步 -->
        <div v-show="activeTab === 'dify'" class="tab-content">
          <div class="dify-layout">
            <!-- 左：配置 -->
            <div class="dify-config-card">
              <div class="section-title">
                Dify 连接配置
              </div>
              <el-form :model="difyConfig" label-width="90px" size="default">
                <el-form-item label="API 地址">
                  <el-input v-model="difyConfig.apiUrl" placeholder="http://localhost:3000/v1" />
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input
                    v-model="difyConfig.apiKey"
                    type="password"
                    show-password
                    placeholder="dataset-*** 或 app-***"
                  />
                </el-form-item>
                <el-form-item label="Key 类型">
                  <el-tag v-if="difyKeyType === 'dataset'" type="success">
                    Dataset API Key
                  </el-tag>
                  <el-tag v-else-if="difyKeyType === 'app'" type="warning">
                    App API Key
                  </el-tag>
                  <el-tag v-else type="info">
                    未识别
                  </el-tag>
                </el-form-item>
                <el-form-item label="启用">
                  <el-switch v-model="difyConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="difySaving" @click="handleSaveDifyConfig">
                    保存
                  </el-button>
                  <el-button :loading="difyTesting" @click="handleTestDifyConnection">
                    测试连接
                  </el-button>
                </el-form-item>
              </el-form>
              <el-alert
                v-if="difyKeyType === 'dataset'"
                title="当前是 Dataset API Key：支持知识库同步，不支持应用/工作流同步与平台概览。"
                type="info"
                :closable="false"
                show-icon
              />
            </div>

            <!-- 右：同步动作 + 概览 -->
            <div class="dify-action-card">
              <div class="section-title">
                同步动作
              </div>
              <div class="dify-actions">
                <el-button
                  type="primary"
                  :loading="difySyncing"
                  style="width: 100%"
                  @click="handleDifyFullSync"
                >
                  {{ difyKeyType === 'dataset' ? '仅同步知识库（Dataset API）' : '完整同步（知识库 + 工作流 + 应用）' }}
                </el-button>
                <el-button
                  type="default"
                  :loading="difyWorkflowSyncing"
                  :disabled="difyKeyType === 'dataset'"
                  style="width: 100%"
                  @click="handleDifyWorkflowSync"
                >
                  仅同步工作流
                </el-button>
              </div>

              <el-alert
                v-if="difySyncResult"
                :title="difySyncResult.success ? '同步成功' : `同步失败: ${difySyncResult.message || '未知错误'}`"
                :type="difySyncResult.success ? 'success' : 'error'"
                show-icon
                closable
                class="sync-result"
                @close="difySyncResult = null"
              />

              <template v-if="difyOverview && difyKeyType !== 'dataset'">
                <div class="section-title" style="margin-top: 24px">
                  同步概览
                </div>
                <div class="overview-stats">
                  <div class="overview-stat">
                    <span class="overview-num">{{ difyOverview.appsCount || 0 }}</span>
                    <span class="overview-label">应用</span>
                  </div>
                  <div class="overview-stat">
                    <span class="overview-num">{{ difyOverview.workflowsCount || 0 }}</span>
                    <span class="overview-label">工作流</span>
                  </div>
                  <div class="overview-stat">
                    <span class="overview-num">{{ difyOverview.datasetsCount || 0 }}</span>
                    <span class="overview-label">知识库</span>
                  </div>
                  <div class="overview-stat">
                    <span class="overview-num">{{ difyOverview.apiKeysCount || 0 }}</span>
                    <span class="overview-label">API Keys</span>
                  </div>
                </div>
              </template>
              <div v-else class="overview-empty">
                <el-empty :description="difyKeyType === 'dataset' ? 'Dataset Key 不支持平台概览' : '完整同步后可查看概览'" :image-size="60" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Webhook 对话框 -->
      <el-dialog v-model="showWebhookDialog" title="添加 Webhook" width="480px">
        <el-form :model="webhookForm" label-position="top">
          <el-form-item label="关联 Agent">
            <el-select
              v-model="webhookForm.agentId"
              placeholder="请选择 Agent"
              filterable
              style="width: 100%"
              :loading="agentsLoading"
            >
              <el-option
                v-for="a in agentList"
                :key="getAgentIdentifier(a)"
                :label="a.name || getAgentIdentifier(a)"
                :value="getAgentIdentifier(a)"
              >
                <span>{{ a.name || getAgentIdentifier(a) }}</span>
                <span class="agent-option-id">{{ getAgentIdentifier(a) }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="Webhook URL">
            <el-input v-model="webhookForm.webhookUrl" placeholder="https://example.com/webhook" />
          </el-form-item>
          <el-form-item label="密钥（可选）">
            <el-input v-model="webhookForm.webhookSecret" type="password" placeholder="用于签名验证" />
          </el-form-item>
          <el-form-item label="事件类型">
            <el-checkbox-group v-model="webhookForm.eventTypes">
              <el-checkbox label="document_added">
                文档新增
              </el-checkbox>
              <el-checkbox label="document_updated">
                文档更新
              </el-checkbox>
              <el-checkbox label="document_deleted">
                文档删除
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showWebhookDialog = false">
            取消
          </el-button>
          <el-button type="primary" @click="handleSaveWebhook">
            保存
          </el-button>
        </template>
      </el-dialog>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, List, Promotion, Link } from '@element-plus/icons-vue'
import {
  getClientChanges,
  getClientCheckpoint,
  getPendingChangeCount,
  getClientWebhooks,
  saveClientWebhook,
  deleteClientWebhook,
  reenableClientWebhook,
  triggerFullSync,
  triggerIncrementalSync,
  getDifySyncOverview,
  fullSyncDifyAll,
  fullSyncDifyKnowledgeOnly,
  syncDifyWorkflows
} from '@/api/knowledge'
import {
  getDifyConfig as getSystemDifyConfig,
  saveDifyConfig as saveSystemDifyConfig,
  testDifyConnection as testSystemDifyConnection
} from '@/api/integrations'
import { getAgentList } from '@/api/agent'

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const router = useRouter()

const POLL_INTERVAL_MS = 2000
const POLL_MAX_ROUNDS = 8

const tabs = [
  { name: 'changes', label: '变更记录', icon: List },
  { name: 'webhooks', label: 'Webhook', icon: Link },
  { name: 'dify', label: 'Dify 同步', icon: Promotion }
]

const activeTab = ref('changes')
const agentId = ref('')
const agentList = ref([])
const agentsLoading = ref(false)

const checkpointData = ref(null)
const pendingCount = ref(0)
const statusLoading = ref(false)

const changes = ref([])
const changesLoading = ref(false)
const changesPage = ref(1)
const changesSize = ref(10)
const changesTotal = ref(0)

const syncing = ref(false)
const syncPolling = ref(false)
let pollingTimer = null
let pollingRounds = 0
let statusLoadRequestId = 0
let changesLoadRequestId = 0

const webhooks = ref([])
const webhooksLoading = ref(false)
const showWebhookDialog = ref(false)
const webhookForm = ref({ agentId: '', webhookUrl: '', webhookSecret: '', eventTypes: [] })

const difyConfig = reactive({ apiUrl: '', apiKey: '', enabled: false })
const difySaving = ref(false)
const difyTesting = ref(false)
const difyOverview = ref(null)
const difyOverviewLoading = ref(false)
const difySyncing = ref(false)
const difyWorkflowSyncing = ref(false)
const difySyncResult = ref(null)
const normalizedDifyApiKey = computed(() => String(difyConfig.apiKey || '').trim())
const difyKeyType = computed(() => {
  if (normalizedDifyApiKey.value.startsWith('dataset-')) return 'dataset'
  if (normalizedDifyApiKey.value.startsWith('app-')) return 'app'
  return 'unknown'
})

const tabSummaryMap = computed(() => ({
  changes: `${pendingCount.value} 待同步 · ${syncPolling.value ? '同步中' : '空闲'}`,
  webhooks: `${webhooks.value.length} 个回调 · ${agentList.value.length || 0} 个 Agent`,
  dify: difyKeyType.value === 'dataset'
    ? 'Dataset Key · 知识库同步'
    : difyKeyType.value === 'app'
      ? 'App Key · 完整同步'
      : '配置连接与上游同步'
}))

const unwrapResponse = (res) => (res && typeof res === 'object' && 'data' in res ? res.data : res)
const getErrorMessage = (error) => {
  if (!error) return '未知错误'
  if (typeof error === 'string') return error
  return error?.response?.data?.message || error?.message || '未知错误'
}
const getAgentIdentifier = (agent) => agent?.agentId || agent?.id || ''
const getCurrentAgents = () => {
  if (!agentList.value.length) return []
  if (!agentId.value) return agentList.value
  return agentList.value.filter((item) => getAgentIdentifier(item) === agentId.value)
}

const stopSyncPolling = () => {
  if (pollingTimer) {
    clearTimeout(pollingTimer)
    pollingTimer = null
  }
  pollingRounds = 0
  syncPolling.value = false
}

const pollSyncState = async () => {
  if (!agentList.value.length) {
    stopSyncPolling()
    return
  }
  try {
    await Promise.all([
      loadStatus(),
      activeTab.value === 'changes' ? loadChanges() : Promise.resolve()
    ])
    pollingRounds += 1
    if (pendingCount.value === 0 || pollingRounds >= POLL_MAX_ROUNDS) {
      stopSyncPolling()
      return
    }
  } catch (e) {
    console.error('轮询同步状态失败:', e)
  }
  pollingTimer = setTimeout(pollSyncState, POLL_INTERVAL_MS)
}

const startSyncPolling = () => {
  stopSyncPolling()
  syncPolling.value = true
  pollingRounds = 0
  pollSyncState()
}

const updateQuery = (patch = {}) => {
  const nextQuery = { ...route.query, ...patch }
  Object.keys(nextQuery).forEach((k) => {
    if (nextQuery[k] === '' || nextQuery[k] == null) delete nextQuery[k]
  })
  router.replace({ query: nextQuery }).catch(() => {})
}

const loadAgentList = async () => {
  agentsLoading.value = true
  try {
    const res = await getAgentList()
    const raw = unwrapResponse(res)
    const list = Array.isArray(raw) ? raw : []
    agentList.value = list
    const routeAgentId = typeof route.query.agentId === 'string' ? route.query.agentId : ''
    const currentId = agentId.value || routeAgentId
    const matched = list.find((item) => getAgentIdentifier(item) === currentId)
    agentId.value = matched ? getAgentIdentifier(matched) : ''
    updateQuery({ agentId: agentId.value })
    await loadStatus()
    await loadTabData(activeTab.value)
  } catch (e) {
    console.error('加载 Agent 列表失败:', e)
  } finally {
    agentsLoading.value = false
  }
}

const switchTab = (name) => {
  activeTab.value = name
  updateQuery({ tab: name })
  loadTabData(name)
}

const loadTabData = async (tab) => {
  if (tab === 'dify') {
    await Promise.all([loadDifyConfig(), loadDifyOverview()])
    return
  }
  if (tab === 'changes') await loadChanges()
  else if (tab === 'webhooks') await loadWebhooks()
}

const loadStatus = async () => {
  const requestId = ++statusLoadRequestId
  statusLoading.value = true
  try {
    const targets = getCurrentAgents()
    if (!targets.length) {
      checkpointData.value = null
      pendingCount.value = 0
      return
    }
    const results = await Promise.all(targets.map(async (agent) => {
      const id = getAgentIdentifier(agent)
      const [cpRes, countRes] = await Promise.all([getClientCheckpoint(id), getPendingChangeCount(id)])
      return {
        agentId: id,
        checkpoint: unwrapResponse(cpRes)?.checkpoint || null,
        pending: Number(unwrapResponse(countRes)?.pendingCount || 0)
      }
    }))
    if (requestId !== statusLoadRequestId) return
    pendingCount.value = results.reduce((sum, item) => sum + item.pending, 0)
    const latestCheckpoint = results
      .map((item) => item.checkpoint)
      .filter(Boolean)
      .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0] || null
    checkpointData.value = latestCheckpoint ? { checkpoint: latestCheckpoint } : null
  } catch (e) {
    console.error('加载状态失败:', e)
  } finally {
    if (requestId === statusLoadRequestId) statusLoading.value = false
  }
}

const loadChanges = async () => {
  const requestId = ++changesLoadRequestId
  changesLoading.value = true
  try {
    const targets = getCurrentAgents()
    if (!targets.length) {
      changes.value = []
      changesTotal.value = 0
      return
    }
    if (agentId.value) {
      const res = await getClientChanges(agentId.value, { page: changesPage.value - 1, size: changesSize.value })
      if (requestId !== changesLoadRequestId) return
      const data = unwrapResponse(res) || {}
      changes.value = (data.content || []).map((item) => ({ ...item, agentId: agentId.value }))
      changesTotal.value = data.totalElements || data.total || 0
      return
    }
    const allRows = []
    await Promise.all(targets.map(async (agent) => {
      const id = getAgentIdentifier(agent)
      const res = await getClientChanges(id, { page: 0, size: 200 })
      const data = unwrapResponse(res) || {}
      const rows = Array.isArray(data.content) ? data.content : []
      allRows.push(...rows.map((row) => ({ ...row, agentId: id })))
    }))
    if (requestId !== changesLoadRequestId) return
    allRows.sort((a, b) => new Date(b.changedAt || 0).getTime() - new Date(a.changedAt || 0).getTime())
    changesTotal.value = allRows.length
    const start = (changesPage.value - 1) * changesSize.value
    const end = start + changesSize.value
    changes.value = allRows.slice(start, end)
  } catch (e) {
    ElMessage.error('加载变更记录失败: ' + getErrorMessage(e))
  } finally {
    if (requestId === changesLoadRequestId) changesLoading.value = false
  }
}

const handleFullSync = async () => {
  const targets = getCurrentAgents()
  if (!targets.length) {
    ElMessage.warning('暂无可同步的 Agent')
    return
  }
  syncing.value = true
  try {
    const results = await Promise.all(targets.map(async (agent) => {
      const id = getAgentIdentifier(agent)
      const res = await triggerFullSync(id)
      return unwrapResponse(res) || {}
    }))
    const hasFailure = results.some((item) => !item.success)
    const totalExported = results.reduce((sum, item) => sum + Number(item.exportedCount || 0), 0)
    if (!hasFailure) {
      ElMessage.success(`全量同步已触发，导出文档 ${totalExported} 条`)
      startSyncPolling()
    } else {
      ElMessage.error('部分 Agent 全量同步失败，请查看后端日志')
    }
  } catch (e) {
    ElMessage.error('全量同步失败: ' + getErrorMessage(e))
  } finally {
    syncing.value = false
  }
}

const handleIncrementalSync = async () => {
  const targets = getCurrentAgents()
  if (!targets.length) {
    ElMessage.warning('暂无可同步的 Agent')
    return
  }
  syncing.value = true
  try {
    const results = await Promise.all(targets.map(async (agent) => {
      const id = getAgentIdentifier(agent)
      const res = await triggerIncrementalSync(id)
      return unwrapResponse(res) || {}
    }))
    const hasFailure = results.some((item) => !item.success)
    const totalPending = results.reduce((sum, item) => sum + Number(item.pendingCount || 0), 0)
    if (!hasFailure) {
      ElMessage.success(`增量同步已触发，待同步变更 ${totalPending} 条`)
      startSyncPolling()
    } else {
      ElMessage.error('部分 Agent 增量同步失败，请查看后端日志')
    }
  } catch (e) {
    ElMessage.error('增量同步失败: ' + getErrorMessage(e))
  } finally {
    syncing.value = false
  }
}

const loadWebhooks = async () => {
  webhooksLoading.value = true
  try {
    const targets = getCurrentAgents()
    if (!targets.length) {
      webhooks.value = []
      return
    }
    const result = await Promise.all(targets.map(async (agent) => {
      const id = getAgentIdentifier(agent)
      const res = await getClientWebhooks(id)
      const rows = unwrapResponse(res) || []
      return rows.map((item) => ({ ...item, agentId: item.agentId || id }))
    }))
    webhooks.value = result.flat()
  } catch (e) {
    ElMessage.error('加载 Webhooks 失败: ' + getErrorMessage(e))
  } finally {
    webhooksLoading.value = false
  }
}

const handleSaveWebhook = async () => {
  const selectedAgentId = webhookForm.value.agentId
    || agentId.value
    || (agentList.value.length === 1 ? getAgentIdentifier(agentList.value[0]) : '')
  if (!selectedAgentId) {
    ElMessage.warning('请选择要绑定的 Agent')
    return
  }
  if (!webhookForm.value.webhookUrl) {
    ElMessage.warning('请输入 Webhook URL')
    return
  }
  try {
    await saveClientWebhook(selectedAgentId, {
      ...webhookForm.value,
      eventTypes: webhookForm.value.eventTypes.join(','),
      enabled: true
    })
    ElMessage.success('Webhook 保存成功')
    showWebhookDialog.value = false
    webhookForm.value = { agentId: '', webhookUrl: '', webhookSecret: '', eventTypes: [] }
    loadWebhooks()
  } catch (e) {
    ElMessage.error('保存失败: ' + getErrorMessage(e))
  }
}

const openWebhookDialog = () => {
  showWebhookDialog.value = true
  if (!webhookForm.value.agentId && agentList.value.length === 1) {
    webhookForm.value.agentId = getAgentIdentifier(agentList.value[0])
  }
}

const handleDeleteWebhook = async (webhookId) => {
  try {
    await ElMessageBox.confirm('确定要删除此 Webhook 吗?', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteClientWebhook(webhookId)
    ElMessage.success('删除成功')
    loadWebhooks()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + getErrorMessage(e))
  }
}

const handleReenableWebhook = async (webhookId) => {
  try {
    await reenableClientWebhook(webhookId)
    ElMessage.success('Webhook 已重新启用')
    loadWebhooks()
  } catch (e) {
    ElMessage.error('启用失败: ' + getErrorMessage(e))
  }
}

const loadDifyConfig = async () => {
  try {
    const res = await getSystemDifyConfig()
    const config = unwrapResponse(res) || {}
    difyConfig.apiUrl = config.apiUrl || ''
    difyConfig.apiKey = config.apiKey || ''
    difyConfig.enabled = !!config.enabled
  } catch (e) {
    console.error('加载 Dify 配置失败:', e)
  }
}

const handleSaveDifyConfig = async () => {
  const apiUrl = String(difyConfig.apiUrl || '').trim()
  const apiKey = String(difyConfig.apiKey || '').trim()
  if (!apiUrl || !apiKey) {
    ElMessage.warning('请先填写 Dify API 地址和 API Key')
    return
  }
  difySaving.value = true
  try {
    await saveSystemDifyConfig({ apiUrl, apiKey, enabled: difyConfig.enabled })
    difyConfig.apiUrl = apiUrl
    difyConfig.apiKey = apiKey
    ElMessage.success('Dify 配置已保存')
  } catch (e) {
    ElMessage.error('保存 Dify 配置失败: ' + getErrorMessage(e))
  } finally {
    difySaving.value = false
  }
}

const handleTestDifyConnection = async () => {
  difyTesting.value = true
  try {
    const res = await testSystemDifyConnection()
    const data = unwrapResponse(res) || {}
    data.success ? ElMessage.success(data.message || '连接成功') : ElMessage.error(data.message || '连接失败')
  } catch (e) {
    ElMessage.error('测试连接失败: ' + getErrorMessage(e))
  } finally {
    difyTesting.value = false
  }
}

const handleDifyFullSync = async () => {
  difySyncing.value = true
  difySyncResult.value = null
  try {
    const syncApi = difyKeyType.value === 'dataset' ? fullSyncDifyKnowledgeOnly : fullSyncDifyAll
    const res = await syncApi()
    const data = unwrapResponse(res) || {}
    difySyncResult.value = data
    if (data.success) {
      if (difyKeyType.value === 'dataset') {
        ElMessage.success('Dify 知识库同步已完成')
      } else {
        ElMessage.success('Dify 完整同步已完成')
        await loadDifyOverview()
      }
    } else {
      ElMessage.error(data.message || (difyKeyType.value === 'dataset' ? 'Dify 知识库同步失败' : 'Dify 完整同步失败'))
    }
  } catch (e) {
    difySyncResult.value = { success: false, message: getErrorMessage(e) }
    ElMessage.error((difyKeyType.value === 'dataset' ? 'Dify 知识库同步失败: ' : 'Dify 完整同步失败: ') + getErrorMessage(e))
  } finally {
    difySyncing.value = false
  }
}

const handleDifyWorkflowSync = async () => {
  if (difyKeyType.value === 'dataset') {
    ElMessage.warning('Dataset API Key 不支持工作流同步')
    return
  }
  difyWorkflowSyncing.value = true
  difySyncResult.value = null
  try {
    const res = await syncDifyWorkflows()
    const data = unwrapResponse(res) || {}
    difySyncResult.value = data
    if (data.success) {
      ElMessage.success('工作流同步已完成')
      await loadDifyOverview()
    } else {
      ElMessage.error(data.message || '工作流同步失败')
    }
  } catch (e) {
    difySyncResult.value = { success: false, message: getErrorMessage(e) }
    ElMessage.error('工作流同步失败: ' + getErrorMessage(e))
  } finally {
    difyWorkflowSyncing.value = false
  }
}

const loadDifyOverview = async () => {
  if (difyKeyType.value === 'dataset') {
    difyOverview.value = null
    return
  }
  difyOverviewLoading.value = true
  try {
    const res = await getDifySyncOverview()
    difyOverview.value = unwrapResponse(res)
  } catch (e) {
    console.error('加载 Dify 概览失败:', e)
  } finally {
    difyOverviewLoading.value = false
  }
}

const getChangeTypeTag = (type) => ({ ADDED: 'success', UPDATED: 'warning', DELETED: 'danger' }[type] || 'info')

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  const tab = route.query.tab
  if (typeof tab === 'string' && tabs.some((t) => t.name === tab)) activeTab.value = tab
  loadAgentList()
  if (activeTab.value === 'dify') {
    loadDifyConfig()
    loadDifyOverview()
  }
})

onUnmounted(() => {
  stopSyncPolling()
})
</script>

<style scoped>
.sync-container {
  color: #243244;
}

.sync-container.embedded {
  min-height: auto;
}

.sync-console {
  overflow: visible;
  border: 1px solid var(--orin-border, #e2e8f0);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
  box-shadow: 0 14px 36px -34px rgba(15, 23, 42, 0.5);
}

.sync-hero {
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--orin-border, #e2e8f0);
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.82), rgba(255, 255, 255, 0.96) 48%),
    var(--neutral-white, #ffffff);
}

.sync-hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.sync-hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.sync-icon {
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

.sync-title-block {
  min-width: 0;
}

.sync-title-block h1 {
  margin: 0;
  color: #0f172a;
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: 0;
}

.sync-title-block p {
  margin: 7px 0 0;
  max-width: 760px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.sync-hero-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.sync-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.sync-summary-card {
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

.sync-summary-card:hover,
.sync-summary-card.active {
  border-color: rgba(15, 118, 110, 0.22);
  background: #ffffff;
  box-shadow: 0 8px 18px -16px rgba(15, 23, 42, 0.45);
}

.sync-summary-card .el-icon {
  margin-top: 2px;
  color: var(--orin-primary, #0d9488);
}

.sync-summary-card span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.sync-summary-card strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.sync-summary-card small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-content-panel {
  padding: 14px;
  background: transparent;
  overflow: visible;
}

.agent-option-id {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-left: 8px;
}

/* 引导空态 */
.empty-guide {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.empty-guide-icon {
  font-size: 64px;
  color: var(--el-color-info-light-5, #c0c4cc);
}

/* 状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 16px;
  background: #ffffff;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sync-polling-alert {
  margin-bottom: 12px;
}

/* 内容区 */
.tab-content {
  animation: fadeIn 0.2s ease;
}

.content-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.event-tag {
  margin-right: 4px;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: 500;
}

/* Dify 布局 */
.dify-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}

.dify-config-card,
.dify-action-card {
  background: var(--el-fill-color-lighter, #fafafa);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.dify-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.sync-result {
  margin-top: 12px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.overview-stat {
  text-align: center;
  padding: 16px 8px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

.overview-num {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.overview-label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.overview-empty {
  padding: 20px 0;
}

@media (max-width: 900px) {
  .sync-hero-row {
    flex-direction: column;
  }

  .sync-hero-actions {
    width: 100%;
  }

  .sync-summary {
    grid-template-columns: 1fr;
  }

  .dify-layout {
    grid-template-columns: 1fr;
  }
}
</style>
