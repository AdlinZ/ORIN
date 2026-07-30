<template>
  <main class="workflow-catalog-page">
    <header class="page-header">
      <div>
        <h2>工作流</h2>
        <p>先确认工作流可以交付，再进入运行验证；导入、导出和归档放在高级管理中。</p>
      </div>
      <el-button type="primary" :icon="Plus" :loading="creating" @click="createWorkflow">
        新建工作流
      </el-button>
    </header>

    <el-alert
      v-if="!loading && summary.blocked > 0"
      class="delivery-alert"
      type="error"
      :closable="false"
      show-icon
      :title="`${summary.blocked} 个工作流当前不能交付`"
    >
      <template #default>
        <span>即使历史状态显示为已发布，只要当前编排仍有问题，就必须先修复。</span>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="工作流交付状态概览">
      <div>
        <span>可运行</span>
        <strong class="success-number">{{ summary.ready }}</strong>
        <small>已发布且校验通过</small>
      </div>
      <div>
        <span>待发布</span>
        <strong class="warning-number">{{ summary.readyToPublish }}</strong>
        <small>编排有效，尚未发布</small>
      </div>
      <div>
        <span>需处理</span>
        <strong :class="{ 'danger-number': summary.blocked > 0 }">
          {{ summary.blocked + summary.archived }}
        </strong>
        <small>{{ summary.blocked }} 个需修复，{{ summary.archived }} 个归档</small>
      </div>
    </section>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索工作流名称或说明"
        class="search-input"
      />
      <el-select v-model="deliveryFilter" class="filter-control">
        <el-option label="全部交付状态" value="ALL" />
        <el-option label="可运行" value="READY" />
        <el-option label="待发布" value="READY_TO_PUBLISH" />
        <el-option label="需修复" value="BLOCKED" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button class="secondary-button" :icon="Refresh" :loading="loading" @click="loadWorkflows">
        刷新
      </el-button>
    </div>

    <OrinAsyncState
      :status="loadState.status"
      :error-text="loadErrorText"
      empty-text="还没有工作流。新建一个最小工作流并完成第一次运行验证。"
      empty-action-label="新建工作流"
      @retry="loadWorkflows"
      @empty-action="createWorkflow"
    >
      <OrinDataTable
        title="可交付工作流"
        :description="`${filteredRows.length} / ${rows.length} 个工作流`"
      >
        <ResizableTable
          :data="filteredRows"
          border
          stripe
          :row-style="{ cursor: 'pointer' }"
          empty-text="没有符合当前筛选的工作流"
          @row-click="editWorkflow"
        >
          <el-table-column label="工作流" min-width="330">
            <template #default="{ row }">
              <div class="workflow-identity">
                <strong>{{ row.workflowName }}</strong>
                <small>{{ row.description || '暂未填写用途说明' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="交付状态" min-width="230">
            <template #default="{ row }">
              <div class="delivery-cell">
                <el-tag :type="deliveryMeta(row).tag" size="small">
                  {{ deliveryMeta(row).label }}
                </el-tag>
                <small>{{ deliveryMeta(row).description }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近更新" width="170">
            <template #default="{ row }">
              <span class="updated-at">{{ formatTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="245" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.deliveryState === 'BLOCKED'">
                <el-button link type="danger" size="small" @click.stop="editWorkflow(row)">
                  修复编排
                </el-button>
              </template>
              <template v-else-if="row.deliveryState === 'READY_TO_PUBLISH'">
                <el-button
                  link
                  type="success"
                  size="small"
                  :loading="publishingId === row.id"
                  @click.stop="publishAndVerify(row)"
                >
                  发布并验证
                </el-button>
              </template>
              <template v-else-if="row.deliveryState === 'READY'">
                <el-button link type="success" size="small" @click.stop="runWorkflow(row)">
                  运行验证
                </el-button>
              </template>
              <el-button link type="primary" size="small" @click.stop="editWorkflow(row)">
                {{ row.deliveryState === 'ARCHIVED' ? '查看编排' : '编辑' }}
              </el-button>
            </template>
          </el-table-column>
        </ResizableTable>

        <div class="advanced-entry">
          <span>Dify 导入导出、发布校验详情、归档和删除保留在高级管理页。</span>
          <el-button link type="primary" @click="openAdvanced">进入高级管理</el-button>
        </div>
      </OrinDataTable>
    </OrinAsyncState>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  createWorkflow as createWorkflowApi,
  getWorkflows,
  publishWorkflow,
} from '@/api/workflow'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import {
  toWorkflowListViewModel,
  workflowDeliveryState,
} from '@/viewmodels'
import {
  createDefaultWorkflowDsl,
  normalizeWorkflowDsl,
  validateWorkflowDsl,
} from '@/views/Workflow/workflowDsl'

const router = useRouter()
const rows = ref([])
const keyword = ref('')
const deliveryFilter = ref('ALL')
const loading = ref(false)
const creating = ref(false)
const publishingId = ref(null)
const loadState = reactive({ status: 'loading', error: null })

const loadErrorText = computed(() => (
  loadState.error?.response?.data?.message
  || loadState.error?.message
  || '工作流列表加载失败，请稍后重试'
))

function toCatalogRow(workflow) {
  const definition = normalizeWorkflowDsl(workflow.workflowDefinition || {})
  const issues = validateWorkflowDsl(definition)
  return {
    ...workflow,
    workflowDefinition: definition,
    issueCount: issues.length,
    issues,
    deliveryState: workflowDeliveryState(workflow, issues.length),
  }
}

const summary = computed(() => rows.value.reduce((result, row) => {
  if (row.deliveryState === 'READY') result.ready += 1
  if (row.deliveryState === 'READY_TO_PUBLISH') result.readyToPublish += 1
  if (row.deliveryState === 'BLOCKED') result.blocked += 1
  if (row.deliveryState === 'ARCHIVED') result.archived += 1
  return result
}, { ready: 0, readyToPublish: 0, blocked: 0, archived: 0 }))

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesQuery = !query || [row.workflowName, row.description]
      .some((value) => String(value || '').toLowerCase().includes(query))
    const matchesDelivery = deliveryFilter.value === 'ALL'
      || row.deliveryState === deliveryFilter.value
    return matchesQuery && matchesDelivery
  })
})

function deliveryMeta(row) {
  const definitions = {
    READY: {
      label: '可运行',
      tag: 'success',
      description: '已发布且编排校验通过',
    },
    READY_TO_PUBLISH: {
      label: '待发布',
      tag: 'warning',
      description: '编排有效，可发布后验证',
    },
    BLOCKED: {
      label: '需修复',
      tag: 'danger',
      description: `${row.issueCount} 项编排问题`,
    },
    ARCHIVED: {
      label: '已归档',
      tag: 'info',
      description: '不再进入新的运行',
    },
  }
  return definitions[row.deliveryState]
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

async function loadWorkflows() {
  loading.value = true
  loadState.status = 'loading'
  loadState.error = null
  try {
    rows.value = toWorkflowListViewModel(await getWorkflows()).map(toCatalogRow)
    loadState.status = rows.value.length > 0 ? 'success' : 'empty'
  } catch (error) {
    loadState.status = 'error'
    loadState.error = error
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

async function createWorkflow() {
  creating.value = true
  try {
    const created = await createWorkflowApi({
      workflowName: '未命名工作流',
      description: '',
      workflowType: 'DAG',
      workflowDefinition: createDefaultWorkflowDsl(),
    })
    const workflowId = created?.id || created?.workflowId
    ElMessage.success('已创建工作流草稿')
    if (workflowId) editWorkflow({ id: workflowId })
    else await loadWorkflows()
  } catch (error) {
    ElMessage.error(error?.message || '工作流创建失败')
  } finally {
    creating.value = false
  }
}

async function publishAndVerify(row) {
  publishingId.value = row.id
  try {
    await publishWorkflow(row.id)
    ElMessage.success('工作流已发布，继续进行运行验证')
    runWorkflow(row)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '工作流发布失败')
  } finally {
    publishingId.value = null
  }
}

function editWorkflow(row) {
  if (!row?.id) return
  router.push(ROUTES.AGENTS.WORKFLOW_VISUAL_EDIT.replace(':id', row.id))
}

function runWorkflow(row) {
  router.push({
    path: ROUTES.AGENTS.WORKFLOW_EXECUTION,
    query: { workflowId: row.id },
  })
}

function openAdvanced() {
  router.push(ROUTES.AGENTS.WORKFLOWS_ADVANCED)
}

onMounted(loadWorkflows)
</script>

<style scoped>
.workflow-catalog-page {
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
  padding: 28px 32px 40px;
}

.page-header,
.filter-bar,
.advanced-entry {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
}

.page-header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.filter-bar {
  gap: 10px;
}

.delivery-alert {
  margin-bottom: 16px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.summary-strip > div {
  padding: 16px 18px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.summary-strip > div:last-child {
  border-right: 0;
}

.summary-strip span,
.summary-strip small {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-strip strong {
  display: inline-block;
  margin: 7px 0 5px;
  color: var(--el-text-color-primary);
  font-size: 24px;
}

.summary-strip .success-number {
  color: var(--el-color-success);
}

.summary-strip .warning-number {
  color: var(--el-color-warning);
}

.summary-strip .danger-number {
  color: var(--el-color-danger);
}

.filter-bar {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.search-input {
  width: min(380px, 100%);
}

.filter-control {
  width: 180px;
}

.workflow-identity,
.delivery-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.delivery-cell {
  align-items: flex-start;
}

.workflow-identity strong,
.workflow-identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-identity strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.workflow-identity small,
.delivery-cell small,
.updated-at,
.advanced-entry {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.advanced-entry {
  justify-content: flex-end;
  gap: 6px;
  padding-top: 14px;
}

.secondary-button {
  border-color: var(--el-border-color);
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

@media (max-width: 900px) {
  .workflow-catalog-page {
    padding: 20px 16px 32px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-strip > div:nth-child(2) {
    border-right: 0;
  }

  .summary-strip > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
