<template>
  <div class="page-container">
    <OrinPageShell
      title="工作流执行"
      description="查看工作流运行状态、实例结果与执行历史"
      icon="VideoPlay"
      domain="工作流运行"
      maturity="available"
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

    <section class="execution-overview-grid">
      <el-card shadow="never" class="overview-card overview-card-accent">
        <span class="overview-label">可执行工作流</span>
        <strong class="overview-value">{{ workflowStats.total }}</strong>
        <span class="overview-meta">从这里直接运行已接入的工作流</span>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span class="overview-label">已发布</span>
        <strong class="overview-value">{{ workflowStats.published }}</strong>
        <span class="overview-meta">可作为稳定入口运行的工作流</span>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span class="overview-label">最近实例</span>
        <strong class="overview-value">{{ executionStats.totalInstances }}</strong>
        <span class="overview-meta">当前选中工作流的最近执行记录</span>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span class="overview-label">最近成功率</span>
        <strong class="overview-value">{{ executionStats.successRate }}%</strong>
        <span class="overview-meta">基于当前工作流最近实例计算</span>
      </el-card>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="11">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <strong>工作流列表</strong>
              <span>{{ filteredWorkflows.length }} 个结果</span>
            </div>
          </template>
          <OrinAsyncState :status="workflowState.status" empty-text="暂无工作流数据" @retry="loadWorkflows">
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
                  <el-tag size="small" :type="workflow.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">
                    {{ workflow.status === 'PUBLISHED' ? '已发布' : '草稿' }}
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
              <span v-if="selectedWorkflow">{{ selectedWorkflow.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
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
              <pre class="result-pre">{{ latestExecution }}</pre>
            </div>
          </div>

          <el-empty v-else description="从左侧选择一个工作流后即可执行" :image-size="88" />
        </el-card>

        <el-card shadow="never" class="panel-card instances-panel">
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
        </el-card>
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
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import { ROUTES } from '@/router/routes'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess, toWorkflowListViewModel, toWorkflowStatsViewModel } from '@/viewmodels'
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
const latestExecutionAt = ref('')
const executionError = ref('')

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
  latestExecutionAt.value = ''
  executionError.value = ''
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
    latestExecution.value = JSON.stringify(response?.data ?? response, null, 2)
    latestExecutionAt.value = new Date().toISOString()
    ElMessage.success(`工作流已执行：${selectedWorkflow.value.workflowName}`)
    await loadInstances()
  } catch (error) {
    executionError.value = error?.response?.data?.message || error?.message || '工作流执行失败'
  } finally {
    executing.value = false
  }
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
  background: linear-gradient(135deg, #7c3aed, #5b21b6);
  border-color: transparent;
}

.overview-card-accent .overview-label,
.overview-card-accent .overview-value,
.overview-card-accent .overview-meta {
  color: #f8fafc;
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
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), #f8fafc);
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
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.92), rgba(15, 23, 42, 0.78));
  border-color: rgba(71, 85, 105, 0.52);
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
