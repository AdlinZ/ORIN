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

    <section class="agent-overview-grid">
      <el-card shadow="never" class="overview-card overview-card-accent">
        <span class="overview-label">智能体总数</span>
        <strong class="overview-value">{{ rows.length }}</strong>
        <span class="overview-meta">覆盖对话、工作流和多模态执行</span>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span class="overview-label">运行中</span>
        <strong class="overview-value">{{ runningCount }}</strong>
        <span class="overview-meta">当前可直接进入控制台的智能体</span>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span class="overview-label">最近访问</span>
        <strong class="overview-value">{{ recentAgents.length }}</strong>
        <span class="overview-meta">从下方卡片可继续最近的工作现场</span>
      </el-card>
    </section>

    <el-card v-if="recentAgents.length" shadow="never" class="recent-panel">
      <template #header>
        <div class="recent-panel-header">
          <div>
            <div class="recent-kicker">继续工作</div>
            <strong>最近访问的控制台</strong>
          </div>
          <span class="recent-note">保留最近打开过的智能体入口</span>
        </div>
      </template>
      <div class="recent-grid">
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
              <span>状态：{{ agent.status }}</span>
              <span>{{ formatRecentTime(agent.lastAccess) }}</span>
            </div>
          </div>
          <el-icon class="recent-arrow"><Right /></el-icon>
        </button>
      </div>
    </el-card>

    <el-card shadow="never">
      <OrinAsyncState :status="state.status" empty-text="暂无智能体数据" @retry="loadData">
        <el-table
          class="agent-list-table"
          :data="viewRows"
          stripe
          border
          table-layout="auto"
          @row-click="goConsole"
        >
          <el-table-column prop="agentName" label="智能体名称" min-width="240" show-overflow-tooltip />
          <el-table-column prop="providerType" label="服务商" width="130" />
          <el-table-column prop="viewType" label="类型" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ formatAgentType(row.viewType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="核心模型" min-width="220" show-overflow-tooltip />
          <el-table-column
            prop="status"
            label="状态"
            width="120"
            align="center"
          >
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" effect="light">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastHeartbeat" label="最后活跃" width="168" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatTime(row.lastHeartbeat) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right" align="center">
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
    </el-card>
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
  return { soft: 'rgba(20, 184, 166, 0.14)', strong: '#0f766e' }
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
.agent-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.overview-card {
  border-radius: 18px;
}

.overview-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px;
}

.overview-card-accent {
  background: linear-gradient(140deg, #0f766e, #155e75) !important;
  border-color: #0f766e !important;
  box-shadow: 0 12px 28px rgba(15, 118, 110, 0.18);
}

.overview-card-accent .overview-label,
.overview-card-accent .overview-value,
.overview-card-accent .overview-meta {
  color: #f8fafc;
}

.overview-label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #475569;
}

.overview-value {
  font-size: 36px;
  line-height: 1;
  color: #0f172a;
}

.overview-meta {
  font-size: 14px;
  line-height: 1.5;
  color: #334155;
}

.recent-panel {
  margin-bottom: 16px;
  border-radius: 22px;
}

.recent-panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.recent-kicker {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}

.recent-note {
  font-size: 13px;
  color: #64748b;
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.recent-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), #f8fafc);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.recent-card:hover {
  transform: translateY(-2px);
  border-color: rgba(13, 148, 136, 0.3);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.08);
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
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 128px;
}

.recent-title-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.recent-title {
  min-width: 0;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed rgba(148, 163, 184, 0.32);
}

.recent-arrow {
  color: #94a3b8;
  flex: 0 0 auto;
  margin-top: 4px;
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

.page-container :deep(.header-description) {
  margin-top: 6px;
}

.page-container :deep(.header-filters) {
  margin-top: 14px;
  padding-top: 14px;
}

@media (max-width: 960px) {
  .agent-overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .recent-panel-header,
  .recent-meta {
    flex-direction: column;
    align-items: flex-start;
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
