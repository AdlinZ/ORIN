<template>
  <div class="page-container">
    <OrinPageShell
      title="多智能体协作"
      description="任务包、子任务与事件流的统一协作控制台"
      icon="DataAnalysis"
      domain="工作流管理"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          创建任务包
        </el-button>
        <el-button :icon="Refresh" @click="loadAll">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选"
            style="width: 180px"
          >
            <el-option
              v-for="status in statuses"
              :key="status"
              :value="status"
              :label="status"
            />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <OrinMetricStrip :metrics="collaborationMetrics" class="stats-row" />

    <el-card shadow="never" class="chat-entry-card">
      <template #header>
        <div class="side-header">
          协作对话入口
        </div>
      </template>
      <el-input
        v-model="chatIntent"
        type="textarea"
        :rows="3"
        placeholder="输入你的协作意图，跳转到智能体工作台继续对话"
      />
      <div class="chat-entry-actions">
        <el-button type="primary" @click="goToWorkspace">
          去工作台对话
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <OrinDataTable>
          <template #header>
            <div class="table-header">
              <strong>协作任务包</strong>
              <span>{{ filteredPackages.length }} 个结果</span>
            </div>
          </template>
          <OrinAsyncState :status="packagesState.status" empty-text="暂无协作任务包" @retry="loadPackages">
            <el-table :data="filteredPackages" border stripe>
              <el-table-column
                prop="packageId"
                label="任务包ID"
                min-width="180"
                show-overflow-tooltip
              />
              <el-table-column
                prop="intent"
                label="任务意图"
                min-width="260"
                show-overflow-tooltip
              />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTag(row.status)" size="small">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="priority" label="优先级" width="110" />
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="showTimeline(row)">
                    事件流
                  </el-button>
                  <el-button link type="info" @click="openDetail(row)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </OrinAsyncState>
        </OrinDataTable>
      </el-col>

      <el-col :xs="24" :lg="9">
        <OrinDetailPanel title="事件时间线" eyebrow="任务包详情">
          <OrinAsyncState :status="timelineState.status" empty-text="请选择任务包查看事件">
            <OrinTaskTimeline :items="timeline" />
          </OrinAsyncState>
        </OrinDetailPanel>
      </el-col>
    </el-row>

    <el-dialog v-model="showCreate" title="创建协作任务包" width="620px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="任务意图">
          <el-input
            v-model="createForm.intent"
            type="textarea"
            :rows="3"
            placeholder="描述你的目标任务"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="LOW" value="LOW" />
            <el-option label="NORMAL" value="NORMAL" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="URGENT" value="URGENT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">
          取消
        </el-button>
        <el-button type="primary" :loading="creating" @click="createPackage">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="协作任务包详情" size="720px">
      <div v-if="activePackage" class="detail-drawer">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务包ID">
            {{ activePackage.packageId }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(activePackage.status)" size="small">
              {{ activePackage.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            {{ activePackage.priority }}
          </el-descriptions-item>
          <el-descriptions-item label="协作模式">
            {{ activePackage.collaborationMode }}
          </el-descriptions-item>
          <el-descriptions-item label="任务意图" :span="2">
            {{ activePackage.intent }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section-header">
          <strong>子任务</strong>
          <el-button link type="primary" :icon="Refresh" @click="loadSubtasksForActive">
            刷新
          </el-button>
        </div>

        <OrinAsyncState :status="subtasksState.status" empty-text="暂无子任务">
          <el-table :data="subtasks" border stripe>
            <el-table-column prop="subTaskId" label="子任务ID" width="120" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="expectedRole" label="角色" width="110" />
            <el-table-column prop="status" label="状态" width="130">
              <template #default="{ row }">
                <el-tag :type="subtaskStatusTag(row.status)" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试" width="70" align="center" />
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canSkip(row)"
                  link
                  type="warning"
                  :disabled="Boolean(operatingSubtaskId)"
                  @click="handleSkipSubtask(row)"
                >
                  跳过
                </el-button>
                <el-button
                  v-if="canManualComplete(row)"
                  link
                  type="success"
                  :disabled="Boolean(operatingSubtaskId)"
                  @click="handleManualCompleteSubtask(row)"
                >
                  手动完成
                </el-button>
                <el-button
                  v-if="canRetry(row)"
                  link
                  type="primary"
                  :disabled="Boolean(operatingSubtaskId)"
                  @click="handleRetrySubtask(row)"
                >
                  重试
                </el-button>
                <span v-if="!hasSubtaskAction(row)" class="muted-action">-</span>
              </template>
            </el-table-column>
          </el-table>
        </OrinAsyncState>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinDetailPanel from '@/components/orin/OrinDetailPanel.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinTaskTimeline from '@/components/orin/OrinTaskTimeline.vue'
import { ROUTES } from '@/router/routes'
import {
  createCollaborationPackage,
  getAllPackages,
  getCollaborationStats,
  getEventHistory,
  getSubtasks,
  manualCompleteSubtask,
  retrySubtask,
  skipSubtask
} from '@/api/collaboration'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toCollaborationPackagesViewModel,
  toCollaborationStatsViewModel,
  toCollaborationSubtasksViewModel,
  toTimelineViewModel
} from '@/viewmodels'

const statuses = ['PLANNING', 'DECOMPOSING', 'EXECUTING', 'CONSENSUS', 'COMPLETED', 'FAILED', 'FALLBACK']
const router = useRouter()

const packagesState = reactive(createAsyncState())
const timelineState = reactive(createAsyncState({ status: 'empty' }))
const subtasksState = reactive(createAsyncState({ status: 'empty' }))
const stats = reactive(toCollaborationStatsViewModel({}))

const statusFilter = ref('')
const packages = ref([])
const timeline = ref([])
const subtasks = ref([])
const activePackage = ref(null)
const chatIntent = ref('')
const detailVisible = ref(false)
const operatingSubtaskId = ref('')

const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({
  intent: '',
  priority: 'NORMAL'
})

const filteredPackages = computed(() => {
  if (!statusFilter.value) return packages.value
  return packages.value.filter((item) => item.status === statusFilter.value)
})

const collaborationMetrics = computed(() => [
  { label: '任务总数', value: stats.total, meta: '当前纳管任务包' },
  { label: '执行中', value: stats.running, meta: '需要持续跟进' },
  { label: '已完成', value: stats.completed, meta: '稳定交付结果' },
  { label: '成功率', value: `${stats.successRate}%`, meta: '最近协作任务口径' }
])

const loadStats = async () => {
  try {
    const response = await getCollaborationStats()
    Object.assign(stats, toCollaborationStatsViewModel(response))
  } catch (error) {
    ElMessage.warning('协作统计加载失败')
  }
}

const loadPackages = async () => {
  markLoading(packagesState)
  try {
    const response = await getAllPackages()
    packages.value = toCollaborationPackagesViewModel(response)
    if (packages.value.length === 0) {
      markEmpty(packagesState)
    } else {
      markSuccess(packagesState)
    }
  } catch (error) {
    markError(packagesState, error)
  }
}

const loadAll = async () => {
  await Promise.all([loadStats(), loadPackages()])
}

const showTimeline = async (row) => {
  if (!row?.packageId) return
  activePackage.value = row
  markLoading(timelineState)
  try {
    const response = await getEventHistory(row.packageId)
    timeline.value = toTimelineViewModel(response)
    if (timeline.value.length === 0) {
      markEmpty(timelineState)
    } else {
      markSuccess(timelineState)
    }
  } catch (error) {
    markError(timelineState, error)
  }
}

const loadSubtasks = async (row) => {
  if (!row?.packageId) return
  markLoading(subtasksState)
  try {
    const response = await getSubtasks(row.packageId)
    subtasks.value = toCollaborationSubtasksViewModel(response)
    if (subtasks.value.length === 0) {
      markEmpty(subtasksState)
    } else {
      markSuccess(subtasksState)
    }
  } catch (error) {
    markError(subtasksState, error)
  }
}

const loadSubtasksForActive = async () => {
  await loadSubtasks(activePackage.value)
}

const openDetail = async (row) => {
  activePackage.value = row
  detailVisible.value = true
  await Promise.all([loadSubtasks(row), showTimeline(row)])
}

const refreshActiveDetails = async () => {
  const packageId = activePackage.value?.packageId
  await loadAll()
  if (!packageId) return
  const nextPackage = packages.value.find((item) => item.packageId === packageId) || activePackage.value
  activePackage.value = nextPackage
  await Promise.all([loadSubtasks(nextPackage), showTimeline(nextPackage)])
}

const canRetry = (row) => row.status === 'FAILED'
const canSkip = (row) => ['PENDING', 'FAILED'].includes(row.status)
const canManualComplete = (row) => ['PENDING', 'RUNNING', 'FAILED', 'AWAITING_HUMAN_INPUT', 'MANUAL_HANDLING'].includes(row.status)
const hasSubtaskAction = (row) => canRetry(row) || canSkip(row) || canManualComplete(row)

const handleSkipSubtask = async (row) => {
  await ElMessageBox.confirm('确认跳过该子任务并继续调度后续任务？', '跳过子任务', { type: 'warning' })
  operatingSubtaskId.value = row.subTaskId
  try {
    await skipSubtask(activePackage.value.packageId, row.subTaskId)
    ElMessage.success('已跳过子任务')
    await refreshActiveDetails()
  } finally {
    operatingSubtaskId.value = ''
  }
}

const handleManualCompleteSubtask = async (row) => {
  await ElMessageBox.confirm('确认将该子任务标记为手动完成？本阶段会提交空产出。', '手动完成子任务', { type: 'warning' })
  operatingSubtaskId.value = row.subTaskId
  try {
    await manualCompleteSubtask(activePackage.value.packageId, row.subTaskId, '')
    ElMessage.success('已手动完成子任务')
    await refreshActiveDetails()
  } finally {
    operatingSubtaskId.value = ''
  }
}

const handleRetrySubtask = async (row) => {
  await ElMessageBox.confirm('确认使用原始输入重试该子任务？', '重试子任务', { type: 'warning' })
  operatingSubtaskId.value = row.subTaskId
  try {
    await retrySubtask(activePackage.value.packageId, row.subTaskId)
    ElMessage.success('已提交重试')
    await refreshActiveDetails()
  } finally {
    operatingSubtaskId.value = ''
  }
}

const createPackage = async () => {
  if (!createForm.intent.trim()) {
    ElMessage.warning('请先填写任务意图')
    return
  }
  creating.value = true
  try {
    await createCollaborationPackage({
      intent: createForm.intent,
      priority: createForm.priority
    })
    ElMessage.success('任务包创建成功')
    showCreate.value = false
    createForm.intent = ''
    await loadAll()
  } finally {
    creating.value = false
  }
}

const goToWorkspace = () => {
  const prompt = chatIntent.value.trim()
  router.push({
    path: ROUTES.AGENTS.WORKSPACE,
    query: prompt ? { prompt } : {}
  })
}

const formatTime = (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'

const statusTag = (status) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED':
    case 'FALLBACK': return 'danger'
    case 'EXECUTING':
    case 'CONSENSUS': return 'warning'
    default: return 'info'
  }
}

const subtaskStatusTag = (status) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'SKIPPED': return 'info'
    case 'RUNNING':
    case 'AWAITING_HUMAN_INPUT':
    case 'MANUAL_HANDLING': return 'warning'
    default: return 'info'
  }
}

onMounted(loadAll)
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}

.chat-entry-card {
  margin-bottom: 16px;
}

.chat-entry-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.side-header {
  font-weight: 600;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-header span {
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.muted-action {
  color: var(--text-tertiary);
}

@media (max-width: 992px) {
  .side-header {
    margin-top: 8px;
  }
}
</style>
