<template>
  <div class="page-container execution-page">
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

    <section class="execution-metric-grid" aria-label="工作流执行概览">
      <article
        v-for="metric in executionMetrics"
        :key="metric.key"
        class="execution-metric-card"
        :class="`metric-tone-${metric.tone}`"
      >
        <span class="metric-icon" aria-hidden="true">
          <component :is="metric.icon" />
        </span>
        <div class="metric-copy">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.meta }}</small>
        </div>
      </article>
    </section>

    <section class="execution-workbench">
      <aside class="panel-card workflow-select-panel">
        <div class="workbench-card-header">
          <div>
            <strong>工作流</strong>
            <span>选择后查看执行流水</span>
          </div>
          <el-tag effect="plain" round>{{ filteredWorkflows.length }}</el-tag>
        </div>
        <OrinAsyncState
          :status="workflowState.status"
          empty-text="暂无可执行工作流，请先创建或导入流程"
          empty-action-label="回到编排页"
          @retry="loadWorkflows"
          @empty-action="goToWorkflowList"
        >
          <div class="workflow-list compact">
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
                <span>{{ workflow.source || 'ORIN' }}</span>
                <span>{{ formatShortTime(workflow.updatedAt) }}</span>
              </div>
            </button>
          </div>
        </OrinAsyncState>
      </aside>

      <section class="panel-card execution-main-panel">
        <div class="workbench-card-header main-header">
          <div>
            <strong>{{ selectedWorkflow?.workflowName || '选择一个工作流开始执行' }}</strong>
            <span>{{ selectedWorkflow?.description || '执行记录按最近时间排序，点击卡片查看输入、输出与 Trace。' }}</span>
          </div>
          <span v-if="selectedWorkflow" class="status-pill" :class="workflowStatusTone(selectedWorkflow.status)">
            {{ workflowStatusLabel(selectedWorkflow.status) }}
          </span>
        </div>

        <div v-if="selectedWorkflow" class="execution-run-strip">
          <div class="run-input-card">
            <div class="run-input-head">
              <strong>调试输入</strong>
              <span>JSON</span>
            </div>
            <el-input
              v-model="executionInput"
              type="textarea"
              :rows="4"
              resize="none"
              placeholder='例如：{"query":"总结最近一周告警","limit":10}'
            />
          </div>
          <div class="run-action-card">
            <div class="run-action-meta">
              <span>最后更新</span>
              <strong>{{ formatTime(selectedWorkflow.updatedAt) }}</strong>
            </div>
            <div class="runner-actions">
              <el-button :icon="Edit" @click="editWorkflow(selectedWorkflow)">
                去编排
              </el-button>
              <el-button type="primary" :icon="CaretRight" :loading="executing" @click="runSelectedWorkflow">
                立即执行
              </el-button>
            </div>
          </div>
        </div>

        <el-alert
          v-if="executionError"
          :title="executionError"
          type="error"
          :closable="false"
          show-icon
          class="execution-alert"
        />

        <div v-if="latestExecutionObject?.taskId || executionIdentifiers.length" class="latest-run-card">
          <div class="result-header">
            <strong>最新运行</strong>
            <span>{{ formatTime(latestExecutionAt) }}</span>
          </div>
          <div v-if="executionIdentifiers.length" class="execution-identifiers">
            <div
              v-for="item in executionIdentifiers"
              :key="item.label"
              class="execution-id-item"
            >
              <span>{{ item.label }}</span>
              <div class="execution-id-value">
                <code>{{ item.value }}</code>
                <el-button
                  v-if="item.isTrace"
                  size="small"
                  text
                  type="primary"
                  :icon="Search"
                  @click="openTrace(item.value)"
                >
                  查看链路
                </el-button>
              </div>
            </div>
          </div>
          <div v-if="latestExecutionObject?.taskId" class="task-actions">
            <el-button size="small" :icon="Search" :loading="taskStatusLoading" @click="queryLatestTask">
              查询任务状态
            </el-button>
            <el-button
              v-if="canReplayLatestTask"
              size="small"
              type="warning"
              :loading="taskStatusLoading"
              @click="replayLatestTask"
            >
              重试失败任务
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
        </div>

        <div class="execution-ledger-header">
          <div>
            <strong>执行流水</strong>
            <span>{{ selectedWorkflow ? selectedWorkflow.workflowName : '未选择工作流' }}</span>
          </div>
          <el-tag effect="plain" round>{{ instances.length }} 条</el-tag>
        </div>

        <OrinAsyncState
          :status="instanceState.status"
          :empty-text="selectedWorkflow ? '该工作流暂无实例记录' : '请选择工作流查看实例'"
          @retry="loadInstances"
        >
          <div class="execution-record-list">
            <div
              v-for="instance in instances"
              :key="instance.id"
              class="execution-record-card"
              :class="[instanceTone(instance.status), { active: selectedInstance?.id === instance.id }]"
              @click="selectInstance(instance)"
            >
              <div class="record-status-rail" aria-hidden="true" />
              <div class="record-main">
                <div class="record-title-row">
                  <code>{{ instance.id }}</code>
                  <span class="instance-pill" :class="instanceTone(instance.status)">
                    {{ instance.status }}
                  </span>
                </div>
                <p>{{ instancePreview(instance) }}</p>
                <div class="record-meta-row">
                  <span>开始 {{ formatTime(instance.createdAt) }}</span>
                  <span>结束 {{ formatTime(instance.finishedAt) }}</span>
                  <span>耗时 {{ formatDuration(instance) }}</span>
                </div>
              </div>
              <div class="record-actions">
                <span
                  class="record-action-link"
                  role="button"
                  tabindex="0"
                  @click.stop="selectInstance(instance)"
                  @keydown.enter.stop.prevent="selectInstance(instance)"
                  @keydown.space.stop.prevent="selectInstance(instance)"
                >
                  查看详情
                </span>
                <span
                  class="record-action-link"
                  role="button"
                  tabindex="0"
                  @click.stop="openTraceForInstance(instance)"
                  @keydown.enter.stop.prevent="openTraceForInstance(instance)"
                  @keydown.space.stop.prevent="openTraceForInstance(instance)"
                >
                  {{ traceLoadingId === instance.id ? '查询中' : 'Trace' }}
                </span>
              </div>
            </div>
          </div>
        </OrinAsyncState>
      </section>
    </section>

    <el-drawer
      v-model="detailDrawerVisible"
      title="实例详情"
      size="44%"
      append-to-body
    >
      <div v-if="selectedInstance" class="drawer-instance-meta">
        <span>实例 ID</span>
        <code>{{ selectedInstance.id }}</code>
        <span>状态</span>
        <el-tag size="small" :type="instanceTagType(selectedInstance.status)">
          {{ selectedInstance.status }}
        </el-tag>
        <template v-if="selectedTraceId">
          <span>Trace ID</span>
          <div class="drawer-trace-link">
            <code>{{ selectedTraceId }}</code>
            <el-button size="small" text type="primary" :icon="Search" @click="openTrace(selectedTraceId)">
              查看链路
            </el-button>
          </div>
        </template>
      </div>
      <div v-if="displayedInstanceDetail?.errorMessage" class="drawer-section">
        <el-alert type="error" :closable="false" show-icon :title="displayedInstanceDetail.errorMessage" />
      </div>
      <div v-if="displayedInstanceInput" class="drawer-section">
        <div class="result-header">
          <strong>实例输入</strong>
          <span>{{ formatTime(displayedInstanceDetail?.startedAt || selectedInstance?.createdAt) }}</span>
        </div>
        <pre class="result-pre">{{ displayedInstanceInput }}</pre>
      </div>
      <div v-if="nodeOutputCards.length" class="drawer-section">
        <div class="result-header">
          <strong>节点输出</strong>
          <span>{{ nodeOutputCards.length }} 个节点</span>
        </div>
        <div class="node-output-list">
          <div
            v-for="card in nodeOutputCards"
            :key="`drawer-${card.nodeId}`"
            class="node-output-card"
          >
            <div class="node-output-head">
              <div>
                <strong>{{ card.title }}</strong>
                <span>{{ card.type }}</span>
              </div>
              <el-tag size="small" type="success" effect="plain">已输出</el-tag>
            </div>
            <pre class="node-output-json">{{ card.preview }}</pre>
          </div>
        </div>
      </div>
      <div v-if="displayedInstanceOutput" class="drawer-section">
        <div class="result-header">
          <strong>实例输出</strong>
          <span>{{ displayedInstanceDetail?.status || 'UNKNOWN' }}</span>
        </div>
        <pre class="result-pre">{{ displayedInstanceOutput }}</pre>
      </div>
      <div v-if="displayedInstanceErrorStack" class="drawer-section">
        <div class="result-header">
          <strong>错误堆栈</strong>
          <span>Stacktrace</span>
        </div>
        <pre class="result-pre error-stack">{{ displayedInstanceErrorStack }}</pre>
      </div>
      <OrinEmptyState v-if="!displayedInstanceDetail" description="点击实例后查看输入、输出与错误信息" />
      <OrinEmptyState
        v-else-if="!displayedInstanceInput && !displayedInstanceOutput && !displayedInstanceDetail?.errorMessage"
        description="该实例暂无输入、输出或错误详情"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { CaretRight, Edit, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
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
import { getTraceByInstance } from '@/api/trace'

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
const latestInstanceDetail = ref(null)
const selectedInstance = ref(null)
const selectedInstanceDetail = ref(null)
const detailDrawerVisible = ref(false)
const traceLoadingId = ref('')
let executionPollTimer = null
let executionPollAttempts = 0

const filteredWorkflows = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return workflows.value
  return workflows.value.filter((workflow) => (
    workflow.workflowName.toLowerCase().includes(q) ||
    workflow.description.toLowerCase().includes(q)
  ))
})

const filteredWorkflowSelection = computed(() => {
  if (!filteredWorkflows.value.length) return null
  const stillVisible = filteredWorkflows.value.find((workflow) => workflow.id === selectedWorkflow.value?.id)
  return stillVisible || filteredWorkflows.value[0]
})

const workflowStats = computed(() => toWorkflowStatsViewModel(workflows.value))

const executionStats = computed(() => {
  const totalInstances = instances.value.length
  const successCount = instances.value.filter((item) => String(item.status || '').toUpperCase().includes('SUCCESS')).length
  const successRate = totalInstances ? Math.round((successCount / totalInstances) * 100) : 0
  return { totalInstances, successRate }
})

const executionMetrics = computed(() => [
  {
    key: 'workflows',
    label: '可执行工作流',
    value: workflowStats.value.total,
    meta: '从这里直接运行已接入的工作流',
    tone: 'teal',
    icon: CaretRight
  },
  {
    key: 'published',
    label: '已发布',
    value: workflowStats.value.published,
    meta: '可作为稳定入口运行',
    tone: 'emerald',
    icon: Edit
  },
  {
    key: 'instances',
    label: '最近实例',
    value: executionStats.value.totalInstances,
    meta: '当前选中工作流记录',
    tone: 'blue',
    icon: Refresh
  },
  {
    key: 'successRate',
    label: '最近成功率',
    value: `${executionStats.value.successRate}%`,
    meta: '基于当前工作流最近实例计算',
    tone: executionStats.value.successRate >= 80 ? 'emerald' : executionStats.value.successRate >= 50 ? 'amber' : 'rose',
    icon: Search
  }
])

const executionIdentifiers = computed(() => {
  const payload = latestExecutionObject.value || {}
  return [
    { label: 'taskId', value: payload.taskId },
    { label: 'workflowInstanceId', value: payload.workflowInstanceId || payload.instanceId },
    { label: 'traceId', value: resolveTraceId(payload), isTrace: true }
  ].filter((item) => item.value !== undefined && item.value !== null && String(item.value).length > 0)
})

const displayedInstanceDetail = computed(() => latestInstanceDetail.value || selectedInstanceDetail.value)

const selectedTraceId = computed(() => resolveTraceId(
  displayedInstanceDetail.value ||
  selectedInstance.value?.raw ||
  selectedInstance.value ||
  latestExecutionObject.value ||
  taskStatusResult.value
))

const displayedInstanceOutput = computed(() => {
  const output = displayedInstanceDetail.value?.outputData || taskStatusResult.value?.outputData
  return output ? JSON.stringify(output, null, 2) : ''
})

const displayedInstanceInput = computed(() => {
  const input = displayedInstanceDetail.value?.inputData || selectedInstance.value?.raw?.inputData
  return input ? JSON.stringify(input, null, 2) : ''
})

const displayedInstanceErrorStack = computed(() => displayedInstanceDetail.value?.errorStack || '')

const workflowGraphNodes = computed(() => {
  const definition = selectedWorkflow.value?.workflowDefinition || selectedWorkflow.value?.raw?.workflowDefinition
  const graph = definition?.workflow?.graph || definition?.graph || definition
  return Array.isArray(graph?.nodes) ? graph.nodes : []
})

const nodeOutputCards = computed(() => {
  const output = displayedInstanceDetail.value?.outputData || taskStatusResult.value?.outputData || {}
  const context = output.context || output

  return workflowGraphNodes.value
    .filter((node) => context?.[node.id] && !['start', 'end'].includes(String(node.type || '').toLowerCase()))
    .map((node) => {
      const nodeOutput = context[node.id]
      return {
        nodeId: node.id,
        title: node.data?.title || node.data?.label || node.title || node.id,
        type: normalizeWorkflowNodeTypeLabel(node.type),
        items: buildNodeOutputItems(nodeOutput),
        preview: safeStringify(nodeOutput)
      }
    })
    .filter((card) => card.items.length || card.preview)
})

const canReplayLatestTask = computed(() => {
  const status = String(taskStatusResult.value?.status || taskStatusResult.value?.data?.status || '').toUpperCase()
  return ['FAILED', 'DEAD'].includes(status)
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
    traceId: resolveTraceId(item),
    raw: item
  }))
}

const resolveTraceId = (payload) => {
  if (!payload || typeof payload !== 'object') return ''
  const data = payload.data && typeof payload.data === 'object' ? payload.data : {}
  return String(
    payload.traceId ||
    payload.trace_id ||
    payload.langfuseTraceId ||
    payload.langfuse_trace_id ||
    data.traceId ||
    data.trace_id ||
    data.langfuseTraceId ||
    data.langfuse_trace_id ||
    ''
  )
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
      selectedInstance.value = null
      selectedInstanceDetail.value = null
      markEmpty(instanceState)
    } else {
      markSuccess(instanceState)
      await selectInstance(instances.value[0], { silent: true })
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
  latestInstanceDetail.value = null
  selectedInstance.value = null
  selectedInstanceDetail.value = null
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

  clearExecutionPolling()
  executionError.value = ''
  latestExecution.value = ''
  latestExecutionObject.value = null
  latestExecutionAt.value = ''
  taskStatusResult.value = null
  latestInstanceDetail.value = null
  selectedInstance.value = null
  selectedInstanceDetail.value = null
  executing.value = true
  try {
    const response = await executeWorkflow(selectedWorkflow.value.id, parsedInput)
    const payload = response?.data ?? response
    latestExecutionObject.value = payload && typeof payload === 'object' ? payload : null
    latestExecution.value = JSON.stringify(payload, null, 2)
    latestExecutionAt.value = new Date().toISOString()
    taskStatusResult.value = null
    latestInstanceDetail.value = null
    const taskLabel = latestExecutionObject.value?.taskId ? `，任务 ${latestExecutionObject.value.taskId}` : ''
    const failedImmediately = latestExecutionObject.value?.status === 'FAILED'
    if (failedImmediately) {
      const failureMessage = latestExecutionObject.value?.errorMessage || latestExecutionObject.value?.message || '工作流未能入队'
      ElMessage.error(failureMessage)
    } else {
      ElMessage.success(`工作流已入队：${selectedWorkflow.value.workflowName}${taskLabel}`)
    }
    await loadInstances()
    startExecutionPolling(latestExecutionObject.value?.taskId, latestExecutionObject.value?.workflowInstanceId)
  } catch (error) {
    executionError.value = formatRequestError(error, '工作流执行失败')
  } finally {
    executing.value = false
  }
}

const queryLatestTask = async () => {
  const taskId = latestExecutionObject.value?.taskId
  if (!taskId) return null
  taskStatusLoading.value = true
  try {
    const response = await request.get(`/api/v1/workflow-tasks/${taskId}`, { baseURL: '' })
    taskStatusResult.value = response?.data ?? response
    const instanceId = taskStatusResult.value?.workflowInstanceId || latestExecutionObject.value?.workflowInstanceId
    if (instanceId) {
      await loadInstanceDetail(instanceId)
    }
    return taskStatusResult.value
  } catch (error) {
    ElMessage.error(formatRequestError(error, '任务状态查询失败'))
    return null
  } finally {
    taskStatusLoading.value = false
  }
}

const loadInstanceDetail = async (instanceId) => {
  try {
    const response = await request.get(`/api/workflows/instances/${instanceId}`, { baseURL: '' })
    latestInstanceDetail.value = response?.data ?? response
  } catch (error) {
    console.warn('Failed to load workflow instance detail:', error)
  }
}

const selectInstance = async (row, options = {}) => {
  if (!row?.id) return
  selectedInstance.value = row
  if (!options.silent) {
    latestInstanceDetail.value = null
    detailDrawerVisible.value = true
  }
  try {
    const response = await request.get(`/api/workflows/instances/${row.id}`, { baseURL: '' })
    selectedInstanceDetail.value = response?.data ?? response
  } catch (error) {
    selectedInstanceDetail.value = null
    if (!options.silent) {
      ElMessage.error(formatRequestError(error, '实例详情查询失败'))
    }
  }
}

const isTerminalTaskStatus = (status) => {
  const normalized = String(status || '').toUpperCase()
  return ['COMPLETED', 'FAILED', 'DEAD', 'CANCELLED'].includes(normalized)
}

const clearExecutionPolling = () => {
  if (executionPollTimer) {
    window.clearInterval(executionPollTimer)
    executionPollTimer = null
  }
}

const startExecutionPolling = (taskId, instanceId) => {
  clearExecutionPolling()
  if (!taskId) return
  executionPollAttempts = 0
  if (instanceId) {
    loadInstanceDetail(instanceId)
  }
  executionPollTimer = window.setInterval(async () => {
    executionPollAttempts += 1
    const result = await queryLatestTask()
    const status = result?.status || result?.data?.status
    if (isTerminalTaskStatus(status) || executionPollAttempts >= 90) {
      clearExecutionPolling()
      await loadInstances()
    }
  }, 2000)
}

const replayLatestTask = async () => {
  const taskId = latestExecutionObject.value?.taskId
  if (!taskId) return
  taskStatusLoading.value = true
  try {
    const response = await request.post(`/api/v1/workflow-tasks/${taskId}/replay`, null, { baseURL: '' })
    const payload = response?.data ?? response
    latestExecutionObject.value = {
      ...latestExecutionObject.value,
      taskId: payload.newTaskId || latestExecutionObject.value.taskId
    }
    latestExecution.value = JSON.stringify(payload, null, 2)
    taskStatusResult.value = payload
    ElMessage.success('失败任务已重新入队')
    startExecutionPolling(latestExecutionObject.value.taskId, latestExecutionObject.value.workflowInstanceId)
  } catch (error) {
    ElMessage.error(formatRequestError(error, '任务重试失败'))
  } finally {
    taskStatusLoading.value = false
  }
}

const openStatusUrl = (url) => {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

const extractTraceIdFromResponse = (payload) => {
  const directTraceId = resolveTraceId(payload)
  if (directTraceId) return directTraceId

  const rows = Array.isArray(payload?.data)
    ? payload.data
    : Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.records)
        ? payload.records
        : []

  const firstWithTrace = rows.find((item) => resolveTraceId(item))
  return firstWithTrace ? resolveTraceId(firstWithTrace) : ''
}

const openTrace = (traceId) => {
  const normalizedTraceId = String(traceId || '').trim()
  if (!normalizedTraceId) {
    ElMessage.warning('该实例暂无 traceId')
    return
  }
  router.push(ROUTES.MONITOR.TRACE_DETAIL.replace(':traceId', normalizedTraceId))
}

const openTraceForInstance = async (row) => {
  const traceId = row?.traceId || resolveTraceId(row?.raw)
  if (traceId) {
    openTrace(traceId)
    return
  }

  if (!row?.id) return
  traceLoadingId.value = row.id
  try {
    const response = await getTraceByInstance(row.id)
    const linkedTraceId = extractTraceIdFromResponse(response)
    if (!linkedTraceId) {
      ElMessage.warning('该实例暂未关联调用链路')
      return
    }
    openTrace(linkedTraceId)
  } catch (error) {
    ElMessage.error(formatRequestError(error, '调用链路查询失败'))
  } finally {
    traceLoadingId.value = ''
  }
}

const hasVisualWorkflowGraph = (workflow) => {
  const definition = workflow?.workflowDefinition || workflow?.raw?.workflowDefinition
  const graph = definition?.workflow?.graph || definition?.graph || definition
  return Array.isArray(graph?.nodes)
}

const editWorkflow = (workflow) => {
  if (!workflow?.id) return
  const routePattern = hasVisualWorkflowGraph(workflow)
    ? ROUTES.AGENTS.WORKFLOW_VISUAL_EDIT
    : ROUTES.AGENTS.WORKFLOW_EDIT
  router.push(routePattern.replace(':id', workflow.id))
}

const goToWorkflowList = () => {
  router.push(ROUTES.AGENTS.WORKFLOWS)
}

const formatTime = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-')

const formatShortTime = (value) => (value ? dayjs(value).format('MM/DD HH:mm') : '-')

const formatDuration = (instance) => {
  const start = instance?.createdAt ? dayjs(instance.createdAt) : null
  const end = instance?.finishedAt ? dayjs(instance.finishedAt) : null
  if (!start || !end || !start.isValid() || !end.isValid()) return '-'

  const milliseconds = end.diff(start)
  if (!Number.isFinite(milliseconds) || milliseconds < 0) return '-'
  if (milliseconds < 1000) return `${milliseconds}ms`
  if (milliseconds < 60000) return `${(milliseconds / 1000).toFixed(1)}s`
  return `${Math.round(milliseconds / 60000)}m`
}

const formatRequestError = (error, fallback) => {
  const status = error?.response?.status
  const serverMessage = error?.response?.data?.message || error?.data?.message
  if (serverMessage) return serverMessage

  const statusMessages = {
    400: '请求参数错误',
    401: '登录已过期，请重新登录',
    403: '权限不足，拒绝访问',
    404: '请求资源不存在',
    500: '服务器内部错误'
  }
  if (statusMessages[status]) return statusMessages[status]

  const message = error?.message || ''
  if (message.includes('Network Error')) return '网络连接失败，请检查网络后重试'
  if (message.includes('timeout')) return '请求超时，请检查网络连接'
  return message || fallback
}

const formatScore = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(3) : '-'
}

const truncateText = (value, maxLength) => {
  const text = Array.from(String(value || ''), (char) => {
    const code = char.charCodeAt(0)
    return code < 32 && ![9, 10, 13].includes(code) ? ' ' : char
  }).join('').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

const normalizeWorkflowNodeTypeLabel = (type) => {
  const labels = {
    knowledge_retrieval: '知识检索',
    'knowledge-retrieval': '知识检索',
    variable_assigner: '变量赋值',
    if_else: '条件分支',
    llm: 'LLM',
    code: '代码',
    agent: '智能体',
    loop: '循环',
    skill: '技能'
  }
  return labels[type] || type || '节点'
}

const buildNodeOutputItems = (nodeOutput) => {
  const list = Array.isArray(nodeOutput?.result)
    ? nodeOutput.result
    : Array.isArray(nodeOutput?.results)
      ? nodeOutput.results
      : []

  if (!list.length) {
    const text = nodeOutput?.output || nodeOutput?.text || nodeOutput?.result
    return typeof text === 'string'
      ? [{ label: '输出', content: truncateText(text, 520), score: '', meta: '' }]
      : []
  }

  return list.slice(0, 3).map((item, index) => {
    const content = typeof item === 'string'
      ? item
      : item.content || item.text || item.output || safeStringify(item)
    return {
      label: `Top ${index + 1}`,
      content: truncateText(content, 520),
      score: item?.score !== undefined ? formatScore(item.score) : '',
      meta: buildNodeOutputMeta(item?.metadata)
    }
  })
}

const buildNodeOutputMeta = (metadata) => {
  if (!metadata || typeof metadata !== 'object') return ''
  return [
    metadata.title || metadata.doc_id || metadata.chunk_id,
    metadata.source
  ].filter(Boolean).join(' · ')
}

const safeStringify = (value) => {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value || '')
  }
}

const readableRecordText = (value) => {
  if (value === undefined || value === null || value === '') return ''
  if (typeof value === 'string') return value
  if (value.answer) return value.answer
  if (value.text) return value.text
  if (value.output) return value.output
  if (value.result && typeof value.result === 'string') return value.result
  if (value.outputs) return readableRecordText(value.outputs)
  if (value.query) return `输入：${value.query}`
  return safeStringify(value)
}

const instancePreview = (instance) => {
  const raw = instance?.raw || {}
  const error = raw.errorMessage || raw.error || raw.errorStack
  if (error) return truncateText(error, 120)

  const output = raw.outputData || raw.outputs || raw.result || raw.output
  const outputText = readableRecordText(output)
  if (outputText) return truncateText(outputText, 120)

  const input = raw.inputData || raw.inputs || raw.input
  const inputText = readableRecordText(input)
  if (inputText) return truncateText(inputText, 120)

  return '点击查看输入、输出与节点详情'
}

const instanceTagType = (status) => {
  const normalized = String(status || '').toUpperCase()
  if (normalized.includes('SUCCESS') || normalized.includes('COMPLETED')) return 'success'
  if (normalized.includes('RUN') || normalized.includes('PENDING')) return 'warning'
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) return 'danger'
  return 'info'
}

const workflowStatusTone = (status) => {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'ACTIVE') return 'tone-emerald'
  if (normalized === 'DRAFT') return 'tone-blue'
  if (normalized === 'ARCHIVED') return 'tone-amber'
  return 'tone-neutral'
}

const instanceTone = (status) => {
  const normalized = String(status || '').toUpperCase()
  if (normalized.includes('SUCCESS') || normalized.includes('COMPLETED')) return 'tone-emerald'
  if (normalized.includes('RUN') || normalized.includes('PENDING')) return 'tone-blue'
  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('DEAD')) return 'tone-rose'
  if (normalized.includes('CANCEL')) return 'tone-amber'
  return 'tone-neutral'
}

watch(() => route.query.workflowId, async (workflowId) => {
  if (!workflowId || !workflows.value.length) return
  const matched = workflows.value.find((item) => String(item.id) === String(workflowId))
  if (matched) {
    await selectWorkflow(matched)
  }
})

watch(filteredWorkflowSelection, async (nextWorkflow) => {
  if (!nextWorkflow || nextWorkflow.id === selectedWorkflow.value?.id) return
  await selectWorkflow(nextWorkflow)
})

onMounted(async () => {
  await loadWorkflows()
  if (selectedWorkflow.value?.id) {
    await loadInstances()
  }
})

onUnmounted(() => {
  clearExecutionPolling()
})
</script>

<style scoped>
.execution-page {
  background:
    radial-gradient(circle at 92% 0%, rgba(14, 165, 233, 0.1), transparent 28%),
    radial-gradient(circle at 0% 4%, rgba(20, 184, 166, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.62), rgba(255, 255, 255, 0) 280px);
}

.execution-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.execution-metric-card {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 14px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.76)),
    linear-gradient(135deg, var(--tone-panel), rgba(255, 255, 255, 0));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.execution-metric-card::after {
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  content: "";
  background: linear-gradient(90deg, var(--tone-color), transparent);
}

.metric-icon,
.runner-symbol {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--tone-border);
  color: var(--tone-strong);
  background: var(--tone-soft);
}

.metric-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.runner-symbol {
  width: 56px;
  height: 56px;
  border-radius: 14px;
}

.metric-copy {
  min-width: 0;
}

.metric-copy span,
.metric-copy small {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.metric-copy span {
  font-weight: 700;
}

.metric-copy strong {
  display: block;
  margin: 5px 0 4px;
  color: #0f172a;
  font-size: 26px;
  line-height: 1;
}

.metric-tone-teal,
.tone-teal {
  --tone-color: #0d9488;
  --tone-strong: #0f766e;
  --tone-soft: rgba(20, 184, 166, 0.13);
  --tone-panel: rgba(240, 253, 250, 0.86);
  --tone-border: rgba(13, 148, 136, 0.2);
}

.metric-tone-emerald,
.tone-emerald {
  --tone-color: #059669;
  --tone-strong: #047857;
  --tone-soft: rgba(16, 185, 129, 0.13);
  --tone-panel: rgba(236, 253, 245, 0.88);
  --tone-border: rgba(5, 150, 105, 0.22);
}

.metric-tone-blue,
.tone-blue {
  --tone-color: #2563eb;
  --tone-strong: #1d4ed8;
  --tone-soft: rgba(59, 130, 246, 0.13);
  --tone-panel: rgba(239, 246, 255, 0.9);
  --tone-border: rgba(37, 99, 235, 0.2);
}

.metric-tone-amber,
.tone-amber {
  --tone-color: #d97706;
  --tone-strong: #b45309;
  --tone-soft: rgba(245, 158, 11, 0.15);
  --tone-panel: rgba(255, 251, 235, 0.92);
  --tone-border: rgba(217, 119, 6, 0.24);
}

.metric-tone-rose,
.tone-rose {
  --tone-color: #e11d48;
  --tone-strong: #be123c;
  --tone-soft: rgba(244, 63, 94, 0.12);
  --tone-panel: rgba(255, 241, 242, 0.9);
  --tone-border: rgba(225, 29, 72, 0.2);
}

.tone-neutral {
  --tone-color: #64748b;
  --tone-strong: #475569;
  --tone-soft: rgba(100, 116, 139, 0.1);
  --tone-panel: rgba(248, 250, 252, 0.88);
  --tone-border: rgba(100, 116, 139, 0.18);
}

.panel-card {
  border-color: rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.execution-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.workflow-select-panel,
.execution-main-panel {
  overflow: hidden;
  padding: 0;
  background: rgba(255, 255, 255, 0.96);
}

.workbench-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.82);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.82));
}

.workbench-card-header strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.4;
}

.workbench-card-header span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.workflow-select-panel .workflow-list {
  max-height: calc(100vh - 318px);
  padding: 14px;
  overflow: auto;
}

.workflow-list.compact {
  gap: 10px;
}

.execution-main-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.main-header {
  align-items: center;
}

.execution-run-strip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.82);
  background:
    radial-gradient(circle at 0% 0%, rgba(20, 184, 166, 0.08), transparent 30%),
    rgba(248, 250, 252, 0.72);
}

.run-input-card,
.run-action-card,
.latest-run-card {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
}

.run-input-card {
  min-width: 0;
  padding: 12px;
}

.run-input-head,
.run-action-meta,
.execution-ledger-header,
.record-title-row,
.record-meta-row,
.record-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.run-input-head {
  margin-bottom: 8px;
}

.run-input-head strong {
  color: #0f172a;
  font-size: 13px;
}

.run-input-head span,
.run-action-meta span,
.execution-ledger-header span,
.record-meta-row {
  color: #64748b;
  font-size: 12px;
}

.run-action-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px;
}

.run-action-card .runner-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  width: 100%;
}

.run-action-card :deep(.el-button) {
  width: 100%;
  min-width: 0;
  margin-left: 0 !important;
  justify-content: center;
}

.run-action-card :deep(.el-button + .el-button) {
  margin-left: 0 !important;
}

.run-action-meta {
  align-items: flex-start;
  flex-direction: column;
  gap: 4px;
}

.run-action-meta strong {
  color: #0f172a;
  font-size: 13px;
}

.latest-run-card {
  margin: 16px 16px 0;
  padding: 14px;
}

.execution-main-panel .execution-alert {
  margin: 12px 16px 0;
}

.execution-ledger-header {
  padding: 14px 16px;
  border-top: 1px solid rgba(226, 232, 240, 0.72);
  border-bottom: 1px solid rgba(226, 232, 240, 0.72);
  background: rgba(255, 255, 255, 0.72);
}

.execution-ledger-header strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.execution-record-list {
  display: grid;
  gap: 10px;
  padding: 14px 16px 18px;
}

.execution-record-card {
  display: grid;
  grid-template-columns: 4px minmax(0, 1fr) 136px;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.execution-record-card:hover,
.execution-record-card.active {
  border-color: var(--tone-border);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.74)),
    var(--tone-panel);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.07);
}

.record-status-rail {
  width: 4px;
  border-radius: 999px;
  background: var(--tone-color);
}

.record-main {
  min-width: 0;
}

.record-title-row code {
  min-width: 0;
  max-width: 180px;
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-main p {
  margin: 8px 0;
  max-width: 720px;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
}

.record-meta-row {
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 6px 14px;
}

.record-actions {
  flex: 0 0 auto;
  flex-direction: column;
  align-self: center;
  justify-content: flex-end;
  gap: 8px;
}

.record-action-link {
  display: inline-flex !important;
  width: 100% !important;
  min-height: 24px !important;
  align-items: center !important;
  justify-content: flex-end !important;
  padding: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  color: #0f766e !important;
  background: transparent !important;
  box-shadow: none !important;
  font-size: 12px !important;
  font-weight: 700 !important;
  line-height: 1.4 !important;
  text-align: right !important;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.18s ease;
}

.record-action-link:hover {
  color: #0d9488 !important;
  background: transparent !important;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.execution-insights {
  display: grid;
  grid-template-columns: minmax(460px, 1.3fr) minmax(320px, 0.7fr);
  gap: 16px;
  margin-bottom: 16px;
}

.execution-insight-card {
  border: 1px solid var(--tone-border, rgba(148, 163, 184, 0.22));
  border-radius: 8px;
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.82)),
    linear-gradient(135deg, var(--tone-panel, rgba(248, 250, 252, 0.9)), rgba(255, 255, 255, 0));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.launch-card {
  --tone-color: #0d9488;
  --tone-strong: #0f766e;
  --tone-soft: rgba(20, 184, 166, 0.13);
  --tone-panel: rgba(240, 253, 250, 0.86);
  --tone-border: rgba(13, 148, 136, 0.2);
  display: grid;
  gap: 14px;
}

.selected-runner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.selected-runner-copy {
  min-width: 0;
  flex: 1;
}

.selected-runner-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.selected-runner h2 {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.selected-runner p {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.selected-runner-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.selected-runner-meta span,
.status-pill,
.instance-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--tone-border);
  border-radius: 999px;
  color: var(--tone-strong);
  background: var(--tone-soft);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.selected-runner-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.recent-instance-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.recent-instance-item {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  border-radius: 6px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.78);
  color: #0f172a;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.recent-instance-item.active {
  border-color: var(--tone-border);
  background: var(--tone-panel);
  box-shadow: inset 3px 0 0 var(--tone-color);
}

.recent-instance-item .instance-id {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  padding: 13px 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.workflow-item:hover,
.workflow-item.active {
  transform: none;
  border-color: rgba(13, 148, 136, 0.28);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.72)),
    rgba(240, 253, 250, 0.9);
  box-shadow: inset 3px 0 0 #0d9488;
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
  min-width: 0;
  overflow: hidden;
  font-weight: 600;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-item-desc,
.workflow-item-meta,
.summary-line {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

.workflow-item-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-line.muted {
  color: #94a3b8;
}

.runner-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.execution-console :deep(.el-card__body) {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.82));
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

.execution-id-value,
.drawer-trace-link {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.execution-id-value code,
.drawer-trace-link code {
  flex: 1;
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

.instances-panel :deep(.el-table__cell) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.instances-panel :deep(.el-table__cell.el-table-fixed-column--right) {
  background: #fff !important;
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.05);
}

.instances-panel :deep(.el-table__fixed-right .cell) {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.error-stack {
  max-height: 180px;
  margin: 8px 0 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.instance-output {
  margin-top: 12px;
}

.node-output {
  margin-top: 12px;
}

.node-output-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.node-output-card {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.86);
}

.node-output-head,
.node-output-item-head,
.node-output-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.node-output-head > div {
  display: grid;
  gap: 2px;
}

.node-output-head span,
.node-output-item-head span,
.node-output-meta {
  color: #64748b;
  font-size: 12px;
}

.node-output-items {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.node-output-item {
  padding-top: 8px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
}

.node-output-item p {
  margin: 8px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.node-output-meta {
  margin-top: 8px;
  overflow-wrap: anywhere;
}

.node-output-json {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow: auto;
}

.drawer-instance-meta {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px 12px;
  align-items: center;
  margin-bottom: 18px;
  color: #64748b;
}

.drawer-instance-meta code {
  min-width: 0;
  overflow: hidden;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-trace-link {
  overflow: hidden;
}

.drawer-section {
  margin-top: 18px;
}

@media (max-width: 1200px) {
  .execution-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .execution-workbench {
    grid-template-columns: 1fr;
  }

  .workflow-select-panel .workflow-list {
    max-height: none;
  }

  .execution-insights {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .execution-overview-grid {
    grid-template-columns: 1fr;
  }

  .workflow-item-head,
  .workflow-item-meta,
  .panel-header,
  .selected-runner,
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

  .execution-run-strip {
    grid-template-columns: 1fr;
  }

  .execution-record-card {
    grid-template-columns: 4px minmax(0, 1fr);
  }

  .record-actions {
    grid-column: 2;
    flex-direction: row;
    justify-content: flex-start;
  }

  .record-action-link {
    width: auto !important;
    justify-content: flex-start !important;
    text-align: left;
  }
}

html.dark .overview-label {
  color: #94a3b8;
}

html.dark .overview-value,
html.dark .workflow-item-title,
html.dark .panel-header strong,
html.dark .workbench-card-header strong,
html.dark .execution-ledger-header strong,
html.dark .run-input-head strong,
html.dark .run-action-meta strong,
html.dark .record-title-row code {
  color: #e2e8f0;
}

html.dark .overview-meta,
html.dark .workflow-item-desc,
html.dark .workflow-item-meta,
html.dark .summary-line,
html.dark .workbench-card-header span,
html.dark .execution-ledger-header span,
html.dark .record-meta-row,
html.dark .record-main p {
  color: #94a3b8;
}

html.dark .workflow-select-panel,
html.dark .execution-main-panel {
  border-color: rgba(71, 85, 105, 0.52);
  background: rgba(15, 23, 42, 0.86);
}

html.dark .workbench-card-header,
html.dark .execution-run-strip,
html.dark .execution-ledger-header {
  border-color: rgba(71, 85, 105, 0.52);
  background: rgba(15, 23, 42, 0.62);
}

html.dark .run-input-card,
html.dark .run-action-card,
html.dark .latest-run-card,
html.dark .execution-record-card {
  border-color: rgba(71, 85, 105, 0.52);
  background: rgba(15, 23, 42, 0.72);
}

html.dark .execution-record-card:hover,
html.dark .execution-record-card.active {
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.9), rgba(15, 23, 42, 0.72)),
    var(--tone-panel);
}

html.dark .record-action-link {
  color: #5eead4 !important;
  background: transparent !important;
}

html.dark .record-action-link:hover {
  color: #99f6e4 !important;
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

html.dark .node-output-card {
  background: rgba(15, 23, 42, 0.62);
  border-color: rgba(71, 85, 105, 0.52);
}

html.dark .node-output-item {
  border-top-color: rgba(71, 85, 105, 0.55);
}

html.dark .node-output-item p,
html.dark .node-output-json {
  color: #cbd5e1;
}

html.dark .node-output-json {
  background: rgba(2, 6, 23, 0.42);
}

html.dark .execution-insight-card,
html.dark .recent-instance-item {
  background: rgba(15, 23, 42, 0.82);
  border-color: rgba(71, 85, 105, 0.52);
}

html.dark .recent-instance-item.active {
  background: rgba(20, 184, 166, 0.16);
}

html.dark .selected-runner h2,
html.dark .drawer-instance-meta code {
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
