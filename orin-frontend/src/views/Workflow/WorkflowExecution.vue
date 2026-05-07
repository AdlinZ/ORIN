<template>
  <div class="page-container">
    <OrinPageShell
      title="工作流执行"
      description="查看工作流运行状态、实例结果与执行历史"
      icon="VideoPlay"
      domain="工作流管理"
    >
      <template #actions>
        <el-button :icon="Edit" @click="goToWorkflowList">
          回到编排页
        </el-button>
        <el-button type="primary" :icon="Refresh" @click="reloadAll">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="search"
            placeholder="搜索工作流名称或描述"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <OrinMetricStrip :metrics="executionMetrics" class="execution-overview-grid" />

    <el-row :gutter="16">
      <el-col :xs="24" :xl="11">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <strong>工作流列表</strong>
              <span>{{ filteredWorkflows.length }} 个结果</span>
            </div>
          </template>
          <OrinAsyncState
            :status="workflowState.status"
            empty-text="暂无可执行工作流，请先创建或导入流程"
            empty-action-label="回到编排页"
            @retry="loadWorkflows"
            @empty-action="goToWorkflowList"
          >
            <div class="workflow-list">
              <button
                v-for="workflow in filteredWorkflows"
                :key="workflow.id"
                type="button"
                class="workflow-item"
                :class="{ active: selectedWorkflow?.id === workflow.id }"
                @click="selectWorkflow(workflow)"
              >
                <div class="workflow-item-head">
                  <span class="workflow-item-title">{{ workflow.workflowName }}</span>
                  <el-tag size="small" :type="workflowStatusTagType(workflow.status)" effect="plain">
                    {{ workflowStatusLabel(workflow.status) }}
                  </el-tag>
                </div>
                <div class="workflow-item-desc">
                  {{ workflow.description || '暂无描述' }}
                </div>
                <div class="workflow-item-meta">
                  <span>来源：{{ workflow.source }}</span>
                  <span>{{ formatTime(workflow.updatedAt) }}</span>
                </div>
              </button>
            </div>
          </OrinAsyncState>
        </el-card>
      </el-col>

      <el-col :xs="24" :xl="13">
        <el-card shadow="never" class="panel-card runner-panel">
          <template #header>
            <div class="panel-header">
              <strong>{{ selectedWorkflow?.workflowName || '选择一个工作流开始执行' }}</strong>
              <span v-if="selectedWorkflow">{{ workflowStatusLabel(selectedWorkflow.status) }}</span>
            </div>
          </template>

          <div v-if="selectedWorkflow" class="runner-content">
            <div class="runner-summary">
              <div class="summary-line">{{ selectedWorkflow.description || '暂无描述，可直接填写输入参数执行。' }}</div>
              <div class="summary-line muted">最后更新：{{ formatTime(selectedWorkflow.updatedAt) }}</div>
            </div>

            <el-form label-position="top">
              <el-form-item label="输入参数（JSON）">
                <el-input
                  v-model="executionInput"
                  type="textarea"
                  :rows="9"
                  placeholder='例如：{"query":"总结最近一周告警","limit":10}'
                />
              </el-form-item>
            </el-form>

            <div class="runner-actions">
              <el-button :icon="Edit" @click="editWorkflow(selectedWorkflow)">
                去编排
              </el-button>
              <el-button type="primary" :icon="CaretRight" :loading="executing" @click="runSelectedWorkflow">
                立即执行
              </el-button>
            </div>

            <el-alert
              v-if="executionError"
              :title="executionError"
              type="error"
              :closable="false"
              show-icon
              class="execution-alert"
            />

            <div v-if="latestExecution" class="result-section">
              <div class="result-header">
                <strong>最近执行结果</strong>
                <span>{{ formatTime(latestExecutionAt) }}</span>
              </div>
              <div v-if="executionIdentifiers.length" class="execution-identifiers">
                <div
                  v-for="item in executionIdentifiers"
                  :key="item.label"
                  class="execution-id-item"
                >
                  <span>{{ item.label }}</span>
                  <code>{{ item.value }}</code>
                </div>
              </div>
              <div v-if="latestExecutionObject?.taskId" class="task-actions">
                <el-button size="small" :icon="Search" :loading="taskStatusLoading" @click="queryLatestTask">
                  查询任务状态
                </el-button>
                <el-button
                  v-if="latestExecutionObject?.statusUrl"
                  size="small"
                  text
                  @click="openStatusUrl(latestExecutionObject.statusUrl)"
                >
                  打开状态接口
                </el-button>
              </div>
              <el-alert
                v-if="taskStatusResult"
                type="info"
                :closable="false"
                class="execution-alert"
              >
                <template #title>
                  任务状态：{{ taskStatusResult.status || taskStatusResult.data?.status || 'UNKNOWN' }}
                </template>
                <div v-if="taskStatusResult.workflowInstanceId || taskStatusResult.data?.workflowInstanceId">
                  实例ID：{{ taskStatusResult.workflowInstanceId || taskStatusResult.data?.workflowInstanceId }}
                </div>
                <div v-if="taskStatusResult.errorMessage || taskStatusResult.data?.errorMessage">
                  错误：{{ taskStatusResult.errorMessage || taskStatusResult.data?.errorMessage }}
                </div>
              </el-alert>
              <pre class="result-pre">{{ latestExecution }}</pre>
            </div>
          </div>

          <OrinEmptyState v-else description="从左侧选择一个工作流后即可执行" />
        </el-card>

        <OrinDataTable class="panel-card instances-panel">
          <template #header>
            <div class="panel-header">
              <strong>最近执行实例</strong>
              <span>{{ selectedWorkflow ? selectedWorkflow.workflowName : '未选择工作流' }}</span>
            </div>
          </template>
          <OrinAsyncState
            :status="instanceState.status"
            :empty-text="selectedWorkflow ? '该工作流暂无实例记录' : '请选择工作流查看实例'"
            @retry="loadInstances"
          >
            <el-table :data="instances" border stripe>
              <el-table-column prop="id" label="实例ID" min-width="180" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag size="small" :type="instanceTagType(row.status)">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="开始时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column prop="finishedAt" label="结束时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.finishedAt) }}
                </template>
              </el-table-column>
            </el-table>
          </OrinAsyncState>
        </OrinDataTable>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { CaretRight, Edit, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import { ROUTES } from '@/router/routes'
import request from '@/utils/request'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toWorkflowListViewModel,
  toWorkflowStatsViewModel,
  workflowStatusLabel,
  workflowStatusTagType
} from '@/viewmodels'
import { executeWorkflow, getWorkflowInstances, getWorkflows } from '@/api/workflow'

const router = useRouter()
const route = useRoute()

const workflowState = reactive(createAsyncState())
const instanceState = reactive(createAsyncState({ status: 'empty' }))

const search = ref('')
const workflows = ref([])
const selectedWorkflow = ref(null)
const instances = ref([])
const executionInput = ref('{\n  "query": ""\n}')
const executing = ref(false)
const latestExecution = ref('')
const latestExecutionObject = ref(null)
const latestExecutionAt = ref('')
const executionError = ref('')
const taskStatusLoading = ref(false)
const taskStatusResult = ref(null)

const filteredWorkflows = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return workflows.value
  return workflows.value.filter((workflow) => (
    workflow.workflowName.toLowerCase().includes(q) ||
    workflow.description.toLowerCase().includes(q)
  ))
})

const workflowStats = computed(() => toWorkflowStatsViewModel(workflows.value))

const executionStats = computed(() => {
  const totalInstances = instances.value.length
  const successCount = instances.value.filter((item) => String(item.status || '').toUpperCase().includes('SUCCESS')).length
  const successRate = totalInstances ? Math.round((successCount / totalInstances) * 100) : 0
  return { totalInstances, successRate }
})

const executionMetrics = computed(() => [
  { label: '可执行工作流', value: workflowStats.value.total, meta: '从这里直接运行已接入的工作流' },
  { label: '已发布', value: workflowStats.value.published, meta: '可作为稳定入口运行' },
  { label: '最近实例', value: executionStats.value.totalInstances, meta: '当前选中工作流记录' },
  { label: '最近成功率', value: `${executionStats.value.successRate}%`, meta: '基于当前工作流最近实例计算' }
])

const executionIdentifiers = computed(() => {
  const payload = latestExecutionObject.value || {}
  return [
    { label: 'taskId', value: payload.taskId },
    { label: 'workflowInstanceId', value: payload.workflowInstanceId || payload.instanceId },
    { label: 'traceId', value: payload.traceId }
  ].filter((item) => item.value !== undefined && item.value !== null && String(item.value).length > 0)
})

const normalizeInstances = (payload) => {
  const list = Array.isArray(payload?.data?.records)
    ? payload.data.records
    : Array.isArray(payload?.data)
      ? payload.data
      : Array.isArray(payload?.records)
        ? payload.records
        : Array.isArray(payload)
          ? payload
          : []

  return list.map((item, index) => ({
    id: item.id || item.instanceId || item.runId || `instance-${index}`,
    status: item.status || item.state || 'UNKNOWN',
    createdAt: item.createdAt || item.startTime || item.startedAt || null,
    finishedAt: item.finishedAt || item.completedAt || item.endTime || null,
    raw: item
  }))
}

const loadWorkflows = async () => {
  markLoading(workflowState)
  try {
    const response = await getWorkflows()
    workflows.value = toWorkflowListViewModel(response?.data || response)
    if (!workflows.value.length) {
      markEmpty(workflowState)
      selectedWorkflow.value = null
      return
    }

    markSuccess(workflowState)

    const queryWorkflowId = String(route.query.workflowId || '')
    if (queryWorkflowId) {
      const matched = workflows.value.find((item) => String(item.id) === queryWorkflowId)
      if (matched) {
        selectedWorkflow.value = matched
        return
      }
    }

    if (!selectedWorkflow.value || !workflows.value.some((item) => item.id === selectedWorkflow.value.id)) {
      selectedWorkflow.value = workflows.value[0]
    }
  } catch (error) {
    markError(workflowState, error)
  }
}

const loadInstances = async () => {
  if (!selectedWorkflow.value?.id) {
    instances.value = []
    markEmpty(instanceState)
    return
  }

  markLoading(instanceState)
  try {
    const response = await getWorkflowInstances(selectedWorkflow.value.id)
    instances.value = normalizeInstances(response)
    if (!instances.value.length) {
      markEmpty(instanceState)
    } else {
      markSuccess(instanceState)
    }
  } catch (error) {
    markError(instanceState, error)
  }
}

const reloadAll = async () => {
  await loadWorkflows()
  await loadInstances()
}

const selectWorkflow = async (workflow) => {
  selectedWorkflow.value = workflow
  latestExecution.value = ''
  latestExecutionObject.value = null
  latestExecutionAt.value = ''
  executionError.value = ''
  taskStatusResult.value = null
  await loadInstances()
}

const runSelectedWorkflow = async () => {
  if (!selectedWorkflow.value?.id) return

  let parsedInput = {}
  const rawInput = executionInput.value.trim()
  if (rawInput) {
    try {
      parsedInput = JSON.parse(rawInput)
    } catch {
      executionError.value = '输入参数不是合法 JSON，请修正后再执行'
      return
    }
  }

  executionError.value = ''
  executing.value = true
  try {
    const response = await executeWorkflow(selectedWorkflow.value.id, parsedInput)
    const payload = response?.data ?? response
    latestExecutionObject.value = payload && typeof payload === 'object' ? payload : null
    latestExecution.value = JSON.stringify(payload, null, 2)
    latestExecutionAt.value = new Date().toISOString()
    taskStatusResult.value = null
    const taskLabel = latestExecutionObject.value?.taskId ? `，任务 ${latestExecutionObject.value.taskId}` : ''
    ElMessage.success(`工作流已入队：${selectedWorkflow.value.workflowName}${taskLabel}`)
    await loadInstances()
  } catch (error) {
    executionError.value = error?.response?.data?.message || error?.message || '工作流执行失败'
  } finally {
    executing.value = false
  }
}

const queryLatestTask = async () => {
  const taskId = latestExecutionObject.value?.taskId
  if (!taskId) return
  taskStatusLoading.value = true
  try {
    const response = await request.get(`/v1/tasks/${taskId}`, { baseURL: '' })
    taskStatusResult.value = response?.data ?? response
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '任务状态查询失败')
  } finally {
    taskStatusLoading.value = false
  }
}

const openStatusUrl = (url) => {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

const editWorkflow = (workflow) => {
  if (!workflow?.id) return
  router.push(ROUTES.AGENTS.WORKFLOW_EDIT.replace(':id', workflow.id))
}

const goToWorkflowList = () => {
  router.push(ROUTES.AGENTS.WORKFLOWS)
}

const formatTime = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-')

const instanceTagType = (status) => {
  const normalized = String(status || '').toUpperCase()
  if (normalized.includes('SUCCESS') || normalized.includes('COMPLETED')) return 'success'
  if (normalized.includes('RUN') || normalized.includes('PENDING')) return 'warning'
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) return 'danger'
  return 'info'
}

watch(() => route.query.workflowId, async (workflowId) => {
  if (!workflowId || !workflows.value.length) return
  const matched = workflows.value.find((item) => String(item.id) === String(workflowId))
  if (matched) {
    await selectWorkflow(matched)
  }
})

onMounted(async () => {
  await loadWorkflows()
  if (selectedWorkflow.value?.id) {
    await loadInstances()
  }
})
</script>

<style scoped>
.execution-overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.overview-card {
  border-radius: 18px;
}

.overview-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview-card-accent {
  background: var(--orin-surface, #ffffff);
  border-color: var(--orin-border-strong, #d8e0e8);
}

.overview-card-accent .overview-label,
.overview-card-accent .overview-value,
.overview-card-accent .overview-meta {
  color: inherit;
}

.overview-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.overview-value {
  font-size: 34px;
  line-height: 1;
  color: #0f172a;
}

.overview-meta {
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.panel-card {
  border-radius: 20px;
}

.panel-card + .panel-card {
  margin-top: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workflow-list {
  display: grid;
  gap: 12px;
}

.workflow-item {
  width: 100%;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.workflow-item:hover,
.workflow-item.active {
  transform: translateY(-1px);
  border-color: rgba(124, 58, 237, 0.28);
  box-shadow: 0 14px 24px rgba(15, 23, 42, 0.08);
}

.workflow-item-head,
.workflow-item-meta,
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workflow-item-title {
  font-weight: 600;
  color: #0f172a;
}

.workflow-item-desc,
.workflow-item-meta,
.summary-line {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

.summary-line.muted {
  color: #94a3b8;
}

.runner-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.runner-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.execution-alert {
  margin-top: 4px;
}

.result-section {
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  padding-top: 16px;
}

.execution-identifiers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.execution-id-item {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.8);
}

.execution-id-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #64748b;
}

.execution-id-item code {
  display: block;
  overflow: hidden;
  font-size: 12px;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.result-pre {
  margin: 12px 0 0;
  padding: 16px;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}

@media (max-width: 1200px) {
  .execution-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .execution-overview-grid {
    grid-template-columns: 1fr;
  }

  .workflow-item-head,
  .workflow-item-meta,
  .panel-header,
  .runner-actions,
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .runner-actions {
    width: 100%;
  }

  .execution-identifiers {
    grid-template-columns: 1fr;
  }
}

html.dark .overview-label {
  color: #94a3b8;
}

html.dark .overview-value,
html.dark .workflow-item-title,
html.dark .panel-header strong {
  color: #e2e8f0;
}

html.dark .overview-meta,
html.dark .workflow-item-desc,
html.dark .workflow-item-meta,
html.dark .summary-line {
  color: #94a3b8;
}

html.dark .workflow-item {
  background: rgba(15, 23, 42, 0.86);
  border-color: rgba(71, 85, 105, 0.52);
}

html.dark .execution-id-item {
  background: rgba(15, 23, 42, 0.62);
  border-color: rgba(71, 85, 105, 0.52);
}

html.dark .execution-id-item code {
  color: #e2e8f0;
}

html.dark .workflow-item:hover,
html.dark .workflow-item.active {
  border-color: rgba(45, 212, 191, 0.38);
  box-shadow: 0 12px 24px rgba(2, 6, 23, 0.45);
}

html.dark .result-section {
  border-top-color: rgba(71, 85, 105, 0.55);
}
</style>
