<template>
  <div class="page-container">
    <OrinPageShell
      title="智能体列表"
      description="统一管理已接入智能体，查看状态并进入控制台"
      icon="UserFilled"
      domain="智能体中枢"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push(ROUTES.AGENTS.ONBOARD)">
          接入新智能体
        </el-button>
        <el-button class="refresh-button" :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="search"
            placeholder="搜索智能体、模型、服务商"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选"
            style="width: 160px"
          >
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已停止" value="STOPPED" />
            <el-option label="高负载" value="HIGH_LOAD" />
            <el-option label="异常" value="ERROR" />
          </el-select>
          <el-select
            v-model="providerFilter"
            clearable
            placeholder="服务商筛选"
            style="width: 180px"
          >
            <el-option
              v-for="provider in providerOptions"
              :key="provider"
              :label="provider"
              :value="provider"
            />
          </el-select>
          <el-select
            v-model="typeFilter"
            clearable
            placeholder="类型筛选"
            style="width: 160px"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <section class="agent-overview-board">
      <el-card v-if="featuredAgent" shadow="never" class="agent-spotlight-card">
        <div class="spotlight-copy">
          <span class="spotlight-kicker">推荐工作入口</span>
          <h3>{{ featuredAgent.agentName }}</h3>
          <p>
            {{ featuredAgent.providerType }} · {{ featuredAgent.modelName }}
          </p>
          <div class="spotlight-tags">
            <el-tag size="small" effect="plain">
              {{ formatAgentType(featuredAgent.viewType) }}
            </el-tag>
            <el-tag :type="getStatusType(featuredAgent.status)" size="small" effect="light">
              {{ featuredAgent.status }}
            </el-tag>
          </div>
          <div class="spotlight-metrics">
            <div class="spotlight-metric">
              <span>智能体总数</span>
              <strong>{{ rows.length }}</strong>
            </div>
            <div class="spotlight-metric">
              <span>运行中</span>
              <strong>{{ runningCount }}</strong>
            </div>
            <div class="spotlight-metric">
              <span>最近访问</span>
              <strong>{{ recentAgents.length }}</strong>
            </div>
            <div class="spotlight-metric">
              <span>当前结果</span>
              <strong>{{ viewRows.length }}</strong>
            </div>
          </div>
        </div>
        <div class="spotlight-action">
          <div class="spotlight-icon" :style="{ background: getAgentAccent(featuredAgent).soft, color: getAgentAccent(featuredAgent).strong }">
            <el-icon><component :is="getAgentIcon(featuredAgent.viewType)" /></el-icon>
          </div>
          <el-button type="primary" @click="openConsole(featuredAgent)">
            进入控制台
          </el-button>
        </div>
      </el-card>

      <div class="agent-insight-grid">
        <el-card shadow="never" class="insight-card health-card">
          <template #header>
            <div class="side-card-header">
              <span>运行概览</span>
              <el-tag size="small" effect="plain">
                {{ healthPercent }}%
              </el-tag>
            </div>
          </template>
          <div class="health-ring">
            <el-progress
              type="dashboard"
              :percentage="healthPercent"
              :width="96"
              :stroke-width="10"
              color="var(--orin-primary, #0d9488)"
            />
            <p>运行中占比</p>
          </div>
          <div class="status-stack">
            <div
              v-for="item in statusBreakdown"
              :key="item.status"
              class="status-row"
            >
              <span class="status-dot" :class="`is-${item.intent}`" />
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="insight-card type-card">
          <template #header>
            <div class="side-card-header">
              <span>能力类型</span>
              <span>{{ typeBreakdown.length }} 类</span>
            </div>
          </template>
          <div class="type-list">
            <div
              v-for="item in typeBreakdown"
              :key="item.type"
              class="type-row"
            >
              <div class="type-name">
                <span class="type-icon" :style="{ background: item.accent.soft, color: item.accent.strong }">
                  <el-icon><component :is="getAgentIcon(item.type)" /></el-icon>
                </span>
                <span>{{ item.label }}</span>
              </div>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="recent-panel">
          <template #header>
            <div class="side-card-header">
              <span>最近控制台</span>
              <span>继续工作</span>
            </div>
          </template>
          <div v-if="recentAgents.length" class="recent-list">
            <button
              v-for="agent in recentAgents"
              :key="agent.id"
              type="button"
              class="recent-card"
              @click="openConsole(agent)"
            >
              <div class="recent-icon" :style="{ background: getAgentAccent(agent).soft, color: getAgentAccent(agent).strong }">
                <el-icon><component :is="getAgentIcon(agent.viewType)" /></el-icon>
              </div>
              <div class="recent-body">
                <div class="recent-title-row">
                  <span class="recent-title">{{ agent.agentName }}</span>
                  <el-tag size="small" effect="plain">
                    {{ formatAgentType(agent.viewType) }}
                  </el-tag>
                </div>
                <div class="recent-desc">
                  {{ agent.providerType }} · {{ agent.modelName }}
                </div>
                <div class="recent-meta">
                  <span>{{ agent.status }}</span>
                  <span>{{ formatRecentTime(agent.lastAccess) }}</span>
                </div>
              </div>
              <el-icon class="recent-arrow"><Right /></el-icon>
            </button>
          </div>
          <div v-else class="empty-recent">
            打开任意智能体控制台后，这里会保留快速入口。
          </div>
        </el-card>
      </div>
    </section>

    <OrinDataTable class="agent-table-panel">
      <template #header>
        <div class="table-heading">
          <div>
            <strong>智能体目录</strong>
            <span>按服务商、模型、状态与最近活跃情况管理企业 AI 服务</span>
          </div>
          <span class="table-count">{{ viewRows.length }} / {{ rows.length }}</span>
        </div>
      </template>
      <OrinAsyncState :status="state.status" empty-text="暂无智能体数据" @retry="loadData">
        <el-table
          class="agent-list-table"
          :data="viewRows"
          stripe
          border
          table-layout="auto"
          @row-click="goConsole"
        >
          <el-table-column prop="agentName" label="智能体名称" min-width="280" show-overflow-tooltip />
          <el-table-column prop="providerType" label="服务商" width="120" />
          <el-table-column prop="viewType" label="类型" width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ formatAgentType(row.viewType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="核心模型" min-width="260" show-overflow-tooltip />
          <el-table-column
            prop="status"
            label="状态 / 活跃"
            width="160"
            align="center"
          >
            <template #default="{ row }">
              <div class="status-cell">
                <el-tag :type="getStatusType(row.status)" effect="light">
                  {{ row.status }}
                </el-tag>
                <span>{{ formatTime(row.lastHeartbeat) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openConsole(row)">
                控制台
              </el-button>
              <el-button link type="danger" @click.stop="handleDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </OrinAsyncState>
    </OrinDataTable>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Refresh,
  Search,
  Right,
  ChatDotRound,
  Connection,
  Microphone,
  Headset,
  PictureFilled,
  VideoCamera
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ROUTES } from '@/router/routes'
import { getAgentList } from '@/api/monitor'
import { deleteAgent } from '@/api/agent'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toAgentListViewModel
} from '@/viewmodels'

const router = useRouter()
const state = reactive(createAsyncState())

const rows = ref([])
const search = ref('')
const statusFilter = ref('')
const providerFilter = ref('')
const typeFilter = ref('')
const recentAgents = ref([])

const RECENT_AGENTS_KEY = 'recent-agents'
const RECENT_AGENTS_META_KEY = 'recent-agents-meta'

const viewRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchQuery = !q ||
      row.agentName.toLowerCase().includes(q) ||
      row.modelName.toLowerCase().includes(q) ||
      row.providerType.toLowerCase().includes(q)
    const matchStatus = !statusFilter.value || row.status === statusFilter.value
    const matchProvider = !providerFilter.value || row.providerType === providerFilter.value
    const matchType = !typeFilter.value || row.viewType === typeFilter.value
    return matchQuery && matchStatus && matchProvider && matchType
  })
})

const providerOptions = computed(() => {
  return [...new Set(rows.value.map((row) => row.providerType).filter(Boolean))]
})

const typeOptions = computed(() => {
  return [...new Set(rows.value.map((row) => row.viewType).filter(Boolean))]
    .map((value) => ({ value, label: formatAgentType(value) }))
})

const runningCount = computed(() => rows.value.filter((row) => row.status === 'RUNNING').length)
const featuredAgent = computed(() => recentAgents.value[0] || rows.value.find((row) => row.status === 'RUNNING') || rows.value[0] || null)
const healthPercent = computed(() => {
  if (!rows.value.length) return 0
  return Math.round((runningCount.value / rows.value.length) * 100)
})

const statusBreakdown = computed(() => {
  const labels = {
    RUNNING: '运行中',
    STOPPED: '已停止',
    HIGH_LOAD: '高负载',
    ERROR: '异常'
  }
  const intents = {
    RUNNING: 'success',
    STOPPED: 'info',
    HIGH_LOAD: 'warning',
    ERROR: 'danger'
  }
  const tally = rows.value.reduce((acc, row) => {
    const status = row.status || 'UNKNOWN'
    acc[status] = (acc[status] || 0) + 1
    return acc
  }, {})

  return Object.entries(tally)
    .map(([status, count]) => ({
      status,
      count,
      label: labels[status] || status,
      intent: intents[status] || 'info'
    }))
    .sort((a, b) => b.count - a.count)
})

const typeBreakdown = computed(() => {
  const tally = rows.value.reduce((acc, row) => {
    const type = row.viewType || 'UNKNOWN'
    acc[type] = (acc[type] || 0) + 1
    return acc
  }, {})

  return Object.entries(tally)
    .map(([type, count]) => ({
      type,
      count,
      label: formatAgentType(type),
      accent: getAgentAccent({ viewType: type })
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6)
})

const loadData = async () => {
  markLoading(state)
  try {
    const response = await getAgentList()
    rows.value = toAgentListViewModel(response)
    loadRecentAgents()
    if (rows.value.length === 0) {
      markEmpty(state)
    } else {
      markSuccess(state)
    }
  } catch (error) {
    markError(state, error)
  }
}

const openConsole = (row) => {
  const id = row?.id || row?.raw?.id
  if (!id) return
  const recentIds = JSON.parse(localStorage.getItem(RECENT_AGENTS_KEY) || '[]')
  const recentMeta = JSON.parse(localStorage.getItem(RECENT_AGENTS_META_KEY) || '{}')
  const now = Date.now()
  const nextRecentIds = [id, ...recentIds.filter((item) => item !== id)].slice(0, 10)

  recentMeta[id] = now

  localStorage.setItem(RECENT_AGENTS_KEY, JSON.stringify(nextRecentIds))
  localStorage.setItem(RECENT_AGENTS_META_KEY, JSON.stringify(recentMeta))
  loadRecentAgents()
  router.push(ROUTES.AGENTS.CONSOLE.replace(':id', id))
}

const goConsole = (row) => openConsole(row)

const handleDelete = async (row) => {
  if (!row?.id) {
    ElMessage.warning('该记录缺少 ID，无法删除')
    return
  }
  await ElMessageBox.confirm(`确认删除智能体「${row.agentName}」吗？`, '删除确认', { type: 'warning' })
  await deleteAgent(row.id)
  ElMessage.success('删除成功')
  loadData()
}

const getStatusType = (status) => {
  switch (status) {
    case 'RUNNING': return 'success'
    case 'HIGH_LOAD': return 'warning'
    case 'ERROR': return 'danger'
    case 'STOPPED': return 'info'
    default: return ''
  }
}

const formatTime = (val) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-')

const formatRecentTime = (timestamp) => {
  if (!timestamp) return '近期未打开'
  const date = new Date(timestamp)
  const now = Date.now()
  const diff = now - date.getTime()

  if (diff < 60_000) return '刚刚访问'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}

const formatAgentType = (type) => {
  const typeMap = {
    CHAT: '对话',
    WORKFLOW: '工作流',
    TEXT_TO_IMAGE: '文生图',
    IMAGE_TO_IMAGE: '图生图',
    TEXT_TO_SPEECH: '语音合成',
    SPEECH_TO_TEXT: '转写文字',
    TEXT_TO_VIDEO: '视频生成',
    TTI: '文生图',
    TTS: '语音合成',
    STT: '转写文字',
    TTV: '视频生成'
  }
  return typeMap[String(type || '').toUpperCase()] || (type || '未知类型')
}

const getAgentIcon = (type) => {
  const normalized = String(type || '').toUpperCase()
  if (normalized === 'WORKFLOW') return Connection
  if (normalized === 'TEXT_TO_IMAGE' || normalized === 'IMAGE_TO_IMAGE' || normalized === 'TTI') return PictureFilled
  if (normalized === 'TEXT_TO_SPEECH' || normalized === 'TTS') return Headset
  if (normalized === 'SPEECH_TO_TEXT' || normalized === 'STT') return Microphone
  if (normalized === 'TEXT_TO_VIDEO' || normalized === 'TTV') return VideoCamera
  return ChatDotRound
}

const getAgentAccent = (agent) => {
  const normalized = String(agent?.viewType || '').toUpperCase()
  if (normalized === 'WORKFLOW') return { soft: 'rgba(14, 165, 233, 0.14)', strong: '#0369a1' }
  if (normalized === 'TEXT_TO_IMAGE' || normalized === 'IMAGE_TO_IMAGE' || normalized === 'TTI') {
    return { soft: 'rgba(249, 115, 22, 0.14)', strong: '#c2410c' }
  }
  if (normalized === 'TEXT_TO_SPEECH' || normalized === 'TTS') {
    return { soft: 'rgba(217, 70, 239, 0.14)', strong: '#a21caf' }
  }
  if (normalized === 'SPEECH_TO_TEXT' || normalized === 'STT') {
    return { soft: 'rgba(34, 197, 94, 0.14)', strong: '#15803d' }
  }
  if (normalized === 'TEXT_TO_VIDEO' || normalized === 'TTV') {
    return { soft: 'rgba(239, 68, 68, 0.14)', strong: '#b91c1c' }
  }
  return { soft: 'rgba(13, 148, 136, 0.14)', strong: 'var(--orin-primary, #0d9488)' }
}

const loadRecentAgents = () => {
  const recentIds = JSON.parse(localStorage.getItem(RECENT_AGENTS_KEY) || '[]')
  const recentMeta = JSON.parse(localStorage.getItem(RECENT_AGENTS_META_KEY) || '{}')

  recentAgents.value = recentIds
    .map((id) => {
      const agent = rows.value.find((item) => item.id === id)
      if (!agent) return null
      return {
        ...agent,
        lastAccess: recentMeta[id] || null
      }
    })
    .filter(Boolean)
    .slice(0, 4)
}

onMounted(loadData)
</script>

<style scoped>
.agent-overview-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.agent-insight-grid {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(300px, 1fr) minmax(360px, 1.35fr);
  gap: 16px;
  align-items: stretch;
}

.agent-spotlight-card,
.insight-card,
.recent-panel {
  border-radius: 8px;
  border-color: var(--orin-border-strong, #d8e0e8);
}

.agent-spotlight-card {
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.92), rgba(255, 255, 255, 0.96) 56%),
    radial-gradient(circle at 92% 12%, rgba(13, 148, 136, 0.16), transparent 30%);
}

.agent-spotlight-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 22px;
}

.spotlight-copy {
  min-width: 0;
}

.spotlight-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--orin-primary, #0d9488);
}

.spotlight-copy h3 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.25;
}

.spotlight-copy p {
  margin: 8px 0 12px;
  color: #475569;
}

.spotlight-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.spotlight-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(90px, 1fr));
  gap: 10px;
  max-width: 680px;
  margin-top: 16px;
}

.spotlight-metric {
  padding: 10px 12px;
  border: 1px solid rgba(13, 148, 136, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.68);
}

.spotlight-metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.spotlight-metric strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1;
}

.spotlight-action {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 0 0 auto;
}

.spotlight-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  font-size: 22px;
}

.side-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 700;
  color: #0f172a;
}

.side-card-header span:last-child {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.health-ring {
  display: grid;
  place-items: center;
  padding: 0 0 10px;
}

.health-ring p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.status-stack,
.type-list,
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.status-row,
.type-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.72);
  color: #475569;
  font-size: 13px;
}

.status-row strong,
.type-row strong {
  margin-left: auto;
  color: #0f172a;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #94a3b8;
}

.status-dot.is-success {
  background: var(--success-color, #10b981);
}

.status-dot.is-warning {
  background: var(--warning-color, #f59e0b);
}

.status-dot.is-danger {
  background: var(--error-color, #ef4444);
}

.type-name {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.type-name span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  flex: 0 0 auto;
}

.recent-card {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) 18px;
  align-items: center;
  column-gap: 12px;
  width: 100%;
  min-height: 92px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.recent-panel .recent-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.recent-card:hover {
  transform: none;
  border-color: rgba(13, 148, 136, 0.32);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.recent-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  flex: 0 0 auto;
  font-size: 18px;
}

.recent-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.recent-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  margin-bottom: 8px;
}

.recent-title {
  min-width: 0;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.35;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.recent-title-row .el-tag {
  flex: 0 0 auto;
}

.recent-desc,
.recent-meta {
  font-size: 13px;
  color: #64748b;
}

.recent-desc {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.recent-meta span:first-child {
  color: var(--success-color, #10b981);
  font-weight: 700;
}

.recent-meta span:last-child {
  color: #64748b;
}

.recent-arrow {
  color: #94a3b8;
  justify-self: end;
}

.empty-recent {
  padding: 18px 14px;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  border-radius: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  background: rgba(248, 250, 252, 0.7);
}

.status-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  line-height: 1.2;
}

.status-cell > span {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.refresh-button {
  color: #64748b;
  border-color: rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.9);
}

.refresh-button:hover {
  color: #334155;
  border-color: rgba(148, 163, 184, 0.55);
  background: #fff;
}

html.dark .recent-card {
  border-color: rgba(71, 85, 105, 0.55);
  background: rgba(15, 23, 42, 0.94);
  box-shadow: 0 10px 24px rgba(2, 8, 23, 0.38);
}

html.dark .recent-card:hover {
  border-color: rgba(45, 212, 191, 0.45);
  box-shadow: 0 16px 32px rgba(2, 8, 23, 0.5);
}

html.dark .recent-title {
  color: #e2e8f0;
}

html.dark .agent-spotlight-card {
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.88) 58%),
    radial-gradient(circle at 92% 12%, rgba(45, 212, 191, 0.18), transparent 30%);
}

html.dark .spotlight-copy h3,
html.dark .spotlight-metric strong,
html.dark .side-card-header,
html.dark .status-row strong,
html.dark .type-row strong {
  color: #e2e8f0;
}

html.dark .spotlight-copy p,
html.dark .spotlight-metric span,
html.dark .health-ring p,
html.dark .side-card-header span:last-child,
html.dark .status-row,
html.dark .type-row,
html.dark .empty-recent,
html.dark .status-cell > span {
  color: #94a3b8;
}

html.dark .status-row,
html.dark .type-row,
html.dark .empty-recent,
html.dark .spotlight-metric {
  border-color: rgba(71, 85, 105, 0.55);
  background: rgba(15, 23, 42, 0.7);
}

html.dark .recent-desc,
html.dark .recent-meta {
  color: #94a3b8;
}

html.dark .recent-arrow {
  color: #64748b;
}

html.dark .refresh-button {
  color: #94a3b8;
  border-color: rgba(71, 85, 105, 0.6);
  background: rgba(15, 23, 42, 0.88);
}

html.dark .refresh-button:hover {
  color: #cbd5e1;
  border-color: rgba(148, 163, 184, 0.7);
  background: rgba(15, 23, 42, 0.96);
}

.page-container :deep(.page-header-wrapper) {
  margin-bottom: 16px;
}

.page-container :deep(.page-header-container) {
  padding: 18px 24px;
}

.page-container :deep(.header-main) {
  gap: 16px;
}

.page-container :deep(.header-icon) {
  width: 36px;
  height: 36px;
  font-size: 18px;
}

.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.table-heading strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.table-heading span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.table-heading .table-count {
  margin-top: 0;
  color: var(--orin-primary, #0d9488);
  font-weight: 700;
  white-space: nowrap;
}

html.dark .table-heading strong {
  color: #e2e8f0;
}

.page-container :deep(.header-description) {
  margin-top: 6px;
}

.page-container :deep(.header-filters) {
  margin-top: 14px;
  padding-top: 14px;
}

@media (max-width: 1280px) {
  .agent-insight-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recent-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .agent-insight-grid,
  .recent-panel .recent-list,
  .spotlight-metrics {
    grid-template-columns: 1fr;
  }

  .agent-spotlight-card :deep(.el-card__body),
  .spotlight-action,
  .recent-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .spotlight-action {
    width: 100%;
  }

  .recent-card {
    align-items: flex-start;
  }
}

.agent-list-table :deep(.el-table__cell .cell) {
  word-break: keep-all;
}

.agent-list-table :deep(.el-table__fixed-right td.el-table__cell),
.agent-list-table :deep(.el-table__fixed-right th.el-table__cell),
.agent-list-table :deep(.el-table__fixed-right-patch) {
  background: #fff;
}

.agent-list-table :deep(.el-table__fixed-right .el-table__row--striped td.el-table__cell) {
  background: #fafafa;
}

.agent-list-table :deep(.el-table__fixed-right tbody tr:hover > td.el-table__cell) {
  background: var(--neutral-gray-50);
}

.agent-list-table :deep(.el-table__fixed-right) {
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.06);
}

html.dark .agent-list-table :deep(.el-table__fixed-right td.el-table__cell),
html.dark .agent-list-table :deep(.el-table__fixed-right th.el-table__cell),
html.dark .agent-list-table :deep(.el-table__fixed-right-patch) {
  background: rgba(15, 23, 42, 0.9);
}

html.dark .agent-list-table :deep(.el-table__fixed-right .el-table__row--striped td.el-table__cell) {
  background: rgba(30, 41, 59, 0.84);
}

html.dark .agent-list-table :deep(.el-table__fixed-right tbody tr:hover > td.el-table__cell) {
  background: rgba(30, 41, 59, 0.94);
}
</style>
