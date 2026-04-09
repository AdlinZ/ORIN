<template>
  <div class="collab-page">
    <PageHeader
      title="多智能体协作会话"
      description="对话输入即触发 Orchestrator -> Research -> Draft 并行 -> Merge -> Critique -> Final"
      icon="Connection"
    >
      <template #actions>
        <el-button @click="goOpsPage">
          运维任务包页
        </el-button>
        <el-button type="primary" :icon="Plus" @click="createSessionWithDefaults">
          新建会话
        </el-button>
      </template>
    </PageHeader>

    <div class="metrics-header">
      <div class="metrics-title">协作运行状态</div>
      <el-tag :type="overallTagType(metrics.overallLevel)">{{ metrics.overallLevel || 'GREEN' }}</el-tag>
    </div>
    <el-alert
      v-for="(alert, idx) in metrics.alerts"
      :key="`alert-${idx}`"
      class="metrics-alert"
      type="warning"
      :closable="false"
      :title="alert"
      show-icon
    />

    <el-row :gutter="12" class="metrics-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card" :class="metricClass('successRate')"><div class="metric-title">成功率</div><div class="metric-value">{{ formatPercent(metrics.successRate) }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card" :class="metricClass('p95LatencyMs')"><div class="metric-title">P95(ms)</div><div class="metric-value">{{ Math.round(metrics.p95LatencyMs || 0) }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card" :class="metricClass('dlqBacklog')"><div class="metric-title">DLQ积压</div><div class="metric-value">{{ metrics.dlqBacklog || 0 }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card"><div class="metric-title">竞标触发率</div><div class="metric-value">{{ formatPercent(metrics.biddingTriggerRate) }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card" :class="metricClass('biddingPostSuccessRate')"><div class="metric-title">竞标后成功率</div><div class="metric-value">{{ formatPercent(metrics.biddingPostSuccessRate) }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card" :class="metricClass('avgCritiqueRounds')"><div class="metric-title">平均Critique轮次</div><div class="metric-value">{{ (metrics.avgCritiqueRounds || 0).toFixed(2) }}</div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="main-layout">
      <el-col :xs="24" :sm="24" :md="7" :lg="6">
        <el-card shadow="never" class="session-panel">
          <template #header>
            <div class="panel-title">会话列表</div>
          </template>
          <el-scrollbar max-height="620px">
            <div
              v-for="item in sessions"
              :key="item.sessionId"
              class="session-item"
              :class="{ active: currentSession?.sessionId === item.sessionId }"
              @click="selectSession(item)"
            >
              <div class="session-item-title">{{ item.title || '协作会话' }}</div>
              <div class="session-item-meta">
                <el-tag size="small" type="info">{{ item.mainAgentPolicy }}</el-tag>
                <span>{{ formatDateTime(item.updatedAt) }}</span>
              </div>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="17" :lg="18">
        <el-card shadow="never" class="chat-panel">
          <template #header>
            <div class="chat-header">
              <div>
                <div class="chat-title">{{ currentSession?.title || '未选择会话' }}</div>
                <div class="chat-subtitle" v-if="currentSession">
                  默认策略: {{ currentSession.mainAgentPolicy }} | critique 阈值: {{ currentSession.qualityThreshold }}
                </div>
              </div>
              <div class="chat-status" v-if="turnState.latestTurnId">
                <el-tag :type="turnState.latestTurnStatus === 'COMPLETED' ? 'success' : (turnState.latestTurnStatus === 'FAILED' ? 'danger' : 'warning')">
                  {{ turnState.latestTurnStatus || 'RUNNING' }}
                </el-tag>
                <el-tag v-if="selectionMode" size="small" :type="selectionMode === 'bid' ? 'warning' : 'info'">
                  主 Agent: {{ selectionMode }}
                </el-tag>
                <span v-if="selectedAgentId" class="selected-agent">{{ selectedAgentId }}</span>
              </div>
            </div>
            <div v-if="turnState.latestTurnId" class="runtime-actions">
              <el-button size="small" :disabled="!runtimePanel.uiActions.canPause" @click="pauseTurn">暂停</el-button>
              <el-button size="small" :disabled="!runtimePanel.uiActions.canResume" @click="resumeTurn">恢复</el-button>
              <el-button
                size="small"
                :disabled="!runtimePanel.uiActions.canSwitchPolicy"
                @click="switchPolicy(selectionMode === 'bid' ? 'STATIC_THEN_BID' : 'BID_FIRST')"
              >
                切主策略
              </el-button>
            </div>
          </template>

          <div class="runtime-panel" v-if="turnState.latestTurnId">
            <div class="runtime-section">
              <div class="runtime-title">阶段进度时间线</div>
              <div class="timeline-list">
                <div v-for="(item, idx) in runtimePanel.timeline.slice(-8)" :key="`timeline-${idx}`" class="timeline-item">
                  <div class="timeline-stage">{{ item.stage }}</div>
                  <div class="timeline-content">{{ item.content }}</div>
                </div>
              </div>
            </div>
            <div class="runtime-section">
              <div class="runtime-title">分支状态</div>
              <div class="branch-grid">
                <div v-for="branch in runtimePanel.branches" :key="branch.branchId" class="branch-card">
                  <div class="branch-header">
                    <span>{{ branch.branchId }} / {{ branch.role }}</span>
                    <el-tag size="small" :type="branch.status === 'COMPLETED' ? 'success' : (branch.status === 'FAILED' ? 'danger' : 'warning')">
                      {{ branch.status }}
                    </el-tag>
                  </div>
                  <div class="branch-meta">Score: {{ branch.score || 0 }}</div>
                  <div class="branch-meta" v-if="branch.degradeReason">降级原因: {{ branch.degradeReason }}</div>
                  <div class="branch-summary">{{ branch.summary || '-' }}</div>
                  <div class="branch-actions">
                    <el-button
                      size="small"
                      :disabled="!runtimePanel.uiActions.canRetryBranch"
                      @click="retryBranch(branch.branchId)"
                    >
                      重试分支
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
            <div class="runtime-section">
              <div class="runtime-title">仲裁决策</div>
              <div class="arbiter-block" v-if="runtimePanel.arbiter.winnerBranchId">
                胜出分支: {{ runtimePanel.arbiter.winnerBranchId }} | 分数: {{ runtimePanel.arbiter.winnerScore }}
              </div>
              <div class="arbiter-block" v-else>暂无仲裁决策</div>
            </div>
            <div class="runtime-section">
              <div class="runtime-title">关键证据</div>
              <div class="evidence-list">
                <el-tag
                  v-for="(ref, idx) in runtimePanel.evidenceRefs.slice(0, 8)"
                  :key="`evidence-${idx}`"
                  size="small"
                  type="info"
                >
                  {{ ref.branchId }} - {{ ref.ref }}
                </el-tag>
              </div>
            </div>
            <div class="runtime-section">
              <div class="runtime-title">建议动作</div>
              <div class="hint-list">
                <div v-for="(hint, idx) in runtimePanel.operatorHints" :key="`hint-${idx}`">{{ hint }}</div>
              </div>
            </div>
          </div>

          <el-scrollbar ref="chatScrollRef" height="500px" class="message-scroll">
            <div class="message-list">
              <div v-for="msg in messages" :key="msg.id || msg.localId" class="message-item" :class="msg.role">
                <div class="message-role">{{ getRoleLabel(msg.role, msg.stage) }}</div>
                <div class="message-content">{{ msg.content }}</div>
                <div class="message-meta">{{ formatDateTime(msg.createdAt) }}</div>
              </div>
            </div>
          </el-scrollbar>

          <div class="composer">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="4"
              placeholder="输入你的问题，系统会以协作回合实时返回 research / draft / critique / final 阶段结果"
              @keydown.ctrl.enter="sendMessage"
            />
            <div class="composer-actions">
              <el-button @click="refreshState" :loading="stateLoading">刷新状态</el-button>
              <el-button type="primary" :loading="sending || streaming" @click="sendMessage">发送 (Ctrl+Enter)</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { ROUTES } from '@/router/routes'
import { useUserStore } from '@/stores/user'
import {
  createCollabSession,
  getCollabSessionMetrics,
  getCollabSessionState,
  listCollabSessionMessages,
  listCollabSessions,
  openCollabSessionStream,
  pauseCollabTurn,
  resumeCollabTurn,
  sendCollabSessionMessage,
  switchCollabSessionPolicy
} from '@/api/collaboration'
import { retrySubtask } from '@/api/collaborationRuntime'

const router = useRouter()
const userStore = useUserStore()

const sessions = ref([])
const currentSession = ref(null)
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const streaming = ref(false)
const stateLoading = ref(false)
const activeTurnId = ref('')
const chatScrollRef = ref(null)

const turnState = reactive({
  latestTurnId: '',
  latestTurnStatus: '',
  packageId: '',
  selection: {}
})
const metrics = reactive({
  successRate: 0,
  p95LatencyMs: 0,
  dlqBacklog: 0,
  biddingTriggerRate: 0,
  biddingPostSuccessRate: 0,
  avgCritiqueRounds: 0,
  overallLevel: 'GREEN',
  metricLevels: {},
  alerts: []
})

const selectedAgentId = ref('')
const selectionMode = ref('')
const eventDedup = new Set()

const runtimePanel = reactive({
  timeline: [],
  branches: [],
  arbiter: {},
  evidenceRefs: [],
  uiActions: {
    canPause: false,
    canResume: false,
    canRetryBranch: false,
    canSwitchPolicy: false
  },
  operatorHints: []
})

const defaultSessionConfig = {
  mainAgentPolicy: 'STATIC_THEN_BID',
  qualityThreshold: 0.82,
  maxCritiqueRounds: 3,
  draftParallelism: 4,
  title: '对话协作会话'
}

const createSessionWithDefaults = async () => {
  try {
    const created = await createCollabSession(defaultSessionConfig)
    await loadSessions()
    const picked = sessions.value.find(s => s.sessionId === created.sessionId) || created
    await selectSession(picked)
    ElMessage.success('会话已创建')
  } catch (e) {
    ElMessage.error('创建会话失败')
  }
}

const loadSessions = async () => {
  const data = await listCollabSessions()
  sessions.value = Array.isArray(data) ? data : []
  if (!currentSession.value && sessions.value.length > 0) {
    await selectSession(sessions.value[0])
  }
}

const loadMetrics = async () => {
  try {
    const data = await getCollabSessionMetrics(24)
    metrics.successRate = data.successRate || 0
    metrics.p95LatencyMs = data.p95LatencyMs || 0
    metrics.dlqBacklog = data.dlqBacklog || 0
    metrics.biddingTriggerRate = data.biddingTriggerRate || 0
    metrics.biddingPostSuccessRate = data.biddingPostSuccessRate || 0
    metrics.avgCritiqueRounds = data.avgCritiqueRounds || 0
    metrics.overallLevel = data.overallLevel || 'GREEN'
    metrics.metricLevels = data.metricLevels || {}
    metrics.alerts = data.alerts || []
  } catch (e) {
    // keep UI usable if metrics endpoint is temporarily unavailable
  }
}

const selectSession = async (session) => {
  currentSession.value = session
  await loadMessages(session.sessionId)
  await refreshState()
}

const loadMessages = async (sessionId, turnId = '') => {
  const data = await listCollabSessionMessages(sessionId, { turnId, page: 0, size: 200 })
  messages.value = Array.isArray(data) ? data : []
  await scrollToBottom()
}

const refreshState = async () => {
  if (!currentSession.value) return
  stateLoading.value = true
  try {
    const state = await getCollabSessionState(currentSession.value.sessionId, activeTurnId.value || undefined)
    turnState.latestTurnId = state.latestTurnId || ''
    turnState.latestTurnStatus = state.latestTurnStatus || ''
    turnState.packageId = state.packageId || ''
    turnState.selection = state.selection || {}
    selectedAgentId.value = state.selection?.selectedAgentId || ''
    selectionMode.value = state.selection?.mode || ''
    runtimePanel.timeline = Array.isArray(state.timeline) ? state.timeline : []
    runtimePanel.branches = Array.isArray(state.branches) ? state.branches : []
    runtimePanel.arbiter = state.arbiter || {}
    runtimePanel.evidenceRefs = Array.isArray(state.evidenceRefs) ? state.evidenceRefs : []
    runtimePanel.uiActions = state.uiActions || runtimePanel.uiActions
    runtimePanel.operatorHints = Array.isArray(state.operatorHints) ? state.operatorHints : []
  } finally {
    stateLoading.value = false
  }
}

const sendMessage = async () => {
  if (!currentSession.value) {
    await createSessionWithDefaults()
  }
  const content = inputText.value.trim()
  if (!content || sending.value || streaming.value) {
    return
  }

  sending.value = true
  try {
    const localId = `local-${Date.now()}`
    messages.value.push({
      localId,
      role: 'user',
      stage: 'USER_INPUT',
      content,
      createdAt: new Date().toISOString()
    })
    await scrollToBottom()

    const result = await sendCollabSessionMessage(currentSession.value.sessionId, {
      content,
      category: 'GENERATION',
      priority: 'NORMAL',
      complexity: 'MEDIUM',
      collaborationMode: 'PARALLEL',
      executionProfile: 'PROD_CONTROLLED',
      workloadType: 'RND_COMPLEX',
      failurePolicy: 'AUTO_DEGRADE_CONTINUE'
    })

    inputText.value = ''
    activeTurnId.value = result.turnId
    await refreshState()
    await streamTurn(currentSession.value.sessionId, result.turnId)
    await loadMessages(currentSession.value.sessionId, result.turnId)
    await refreshState()
    await loadSessions()
    await loadMetrics()
  } catch (e) {
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

const streamTurn = async (sessionId, turnId) => {
  streaming.value = true
  eventDedup.clear()
  try {
    await openCollabSessionStream(sessionId, turnId, userStore.token, (eventName, event) => {
      const dedupKey = `${eventName}-${event.stage || ''}-${event.timestamp || ''}-${event.content || ''}`
      if (eventDedup.has(dedupKey)) {
        return
      }
      eventDedup.add(dedupKey)

      messages.value.push({
        localId: `evt-${event.timestamp || Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        role: event.stage === 'FINAL_ANSWER' ? 'assistant' : 'system',
        stage: event.stage,
        content: event.content,
        createdAt: new Date(event.timestamp || Date.now()).toISOString()
      })

      if (event.stage === 'BIDDING_TRIGGERED' && event.data?.selection) {
        selectedAgentId.value = event.data.selection.selectedAgentId || selectedAgentId.value
        selectionMode.value = event.data.selection.mode || 'bid'
      }
      if (event.stage === 'FINAL_ANSWER' || event.stage === 'TURN_FAILED') {
        streaming.value = false
      }
      refreshState()
      scrollToBottom()
    })
  } catch (e) {
    ElMessage.warning('流式连接中断，已切换到状态轮询')
  } finally {
    streaming.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (!chatScrollRef.value) return
  const wrap = chatScrollRef.value.wrapRef
  if (wrap) {
    wrap.scrollTop = wrap.scrollHeight
  }
}

const getRoleLabel = (role, stage) => {
  if (role === 'user') return '你'
  if (role === 'assistant') return 'Final'
  return stage || 'system'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const formatPercent = (value) => `${((value || 0) * 100).toFixed(1)}%`
const metricClass = (key) => {
  const level = metrics.metricLevels?.[key] || 'GREEN'
  if (level === 'RED') return 'metric-danger'
  if (level === 'YELLOW') return 'metric-warn'
  return 'metric-ok'
}
const overallTagType = (level) => {
  if (level === 'RED') return 'danger'
  if (level === 'YELLOW') return 'warning'
  return 'success'
}

const goOpsPage = () => {
  router.push(ROUTES.AGENTS.COLLABORATION_DASHBOARD)
}

const pauseTurn = async () => {
  if (!currentSession.value || !turnState.latestTurnId) return
  await pauseCollabTurn(currentSession.value.sessionId, turnState.latestTurnId)
  ElMessage.success('已暂停回合')
  await refreshState()
}

const resumeTurn = async () => {
  if (!currentSession.value || !turnState.latestTurnId) return
  await resumeCollabTurn(currentSession.value.sessionId, turnState.latestTurnId)
  ElMessage.success('已恢复回合')
  await refreshState()
}

const retryBranch = async (branchId) => {
  if (!turnState.packageId || !branchId) return
  await retrySubtask(turnState.packageId, branchId)
  ElMessage.success(`已发起分支重试: ${branchId}`)
  await refreshState()
}

const switchPolicy = async (policy) => {
  if (!currentSession.value || !policy) return
  await switchCollabSessionPolicy(currentSession.value.sessionId, policy)
  ElMessage.success(`已切换策略: ${policy}`)
  await loadSessions()
  await refreshState()
}

onMounted(async () => {
  await loadMetrics()
  await loadSessions()
  if (!sessions.value.length) {
    await createSessionWithDefaults()
  }
})
</script>

<style scoped>
.collab-page {
  padding: 20px;
}

.metrics-row {
  margin-top: 10px;
}

.metrics-header {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metrics-title {
  font-size: 14px;
  font-weight: 600;
}

.metrics-alert {
  margin-top: 8px;
}

.metric-card {
  border-radius: 8px;
}

.metric-card.metric-ok {
  border: 1px solid #e1f3d8;
}

.metric-card.metric-warn {
  border: 1px solid #f8e3b3;
}

.metric-card.metric-danger {
  border: 1px solid #fbc4c4;
}

.metric-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
}

.main-layout {
  margin-top: 12px;
}

.session-panel,
.chat-panel {
  border-radius: 10px;
}

.panel-title {
  font-weight: 600;
}

.session-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
  cursor: pointer;
}

.session-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.session-item-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.session-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.chat-title {
  font-weight: 700;
}

.chat-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chat-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.runtime-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.selected-agent {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.runtime-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
  background: #fafcff;
}

.runtime-section + .runtime-section {
  margin-top: 10px;
}

.runtime-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.timeline-item {
  border-left: 3px solid #d9ecff;
  padding-left: 8px;
}

.timeline-stage {
  font-size: 12px;
  font-weight: 600;
}

.timeline-content {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.branch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}

.branch-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 8px;
  background: #fff;
}

.branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.branch-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.branch-summary {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.branch-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.arbiter-block {
  font-size: 12px;
}

.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hint-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-scroll {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-item {
  padding: 10px;
  border-radius: 8px;
  background: #f6f8fb;
}

.message-item.user {
  background: #ecf5ff;
}

.message-item.assistant {
  background: #f0f9eb;
}

.message-role {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.6;
}

.message-meta {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.composer {
  margin-top: 12px;
}

.composer-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
