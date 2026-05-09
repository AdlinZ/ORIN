<template>
  <div class="workflow-page">
    <OrinPageShell
      title="工作流中心"
      description="以 ORIN Workflow DSL v1 维护工作流，Dify 仅作为导入导出兼容格式"
      icon="Connection"
      domain="工作流管理"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="loadWorkflows">刷新</el-button>
        <el-button :icon="Upload" @click="importDialogVisible = true">导入 Dify DSL</el-button>
        <el-button type="primary" :icon="Plus" @click="createWorkflow">新建工作流</el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="searchQuery"
            :prefix-icon="Search"
            clearable
            placeholder="搜索名称、描述、来源"
            class="search-input"
          />
          <el-select v-model="statusFilter" clearable placeholder="状态" class="filter-select">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
          <el-select v-model="compatibilityFilter" clearable placeholder="发布校验" class="filter-select">
            <el-option label="完全兼容" value="FULL" />
            <el-option label="部分兼容" value="PARTIAL" />
            <el-option label="需修复" value="BLOCKED" />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <section class="workflow-metric-grid" aria-label="工作流概览">
      <article
        v-for="metric in workflowMetrics"
        :key="metric.key"
        class="workflow-metric-card"
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

    <section class="workflow-overview-board">
      <article class="workflow-spotlight-card" :class="featuredWorkflow ? statusTone(featuredWorkflow.status) : 'tone-neutral'">
        <div class="spotlight-eyebrow">当前重点</div>
        <template v-if="featuredWorkflow">
          <div class="spotlight-title-row">
            <div class="spotlight-title-main">
              <span class="spotlight-symbol">
                <el-icon><Connection /></el-icon>
              </span>
              <h2>{{ featuredWorkflow.workflowName }}</h2>
            </div>
            <span class="status-pill" :class="statusTone(featuredWorkflow.status)">
              {{ statusLabel(featuredWorkflow.status) }}
            </span>
          </div>
          <p>{{ featuredWorkflow.description || '暂无描述' }}</p>
          <div class="spotlight-meta">
            <span :class="sourceTone(featuredWorkflow)">{{ workflowSource(featuredWorkflow) }}</span>
            <span>{{ featuredWorkflow.nodeCount }} 个节点</span>
            <span>{{ formatTime(featuredWorkflow.updatedAt) }}</span>
          </div>
          <div class="spotlight-actions">
            <el-button type="primary" @click="editWorkflow(featuredWorkflow)">继续编排</el-button>
            <el-button @click="goExecution(featuredWorkflow)">查看执行</el-button>
          </div>
        </template>
        <OrinEmptyState
          v-else
          description="暂无工作流，先创建一个 ORIN DSL 草稿"
          action-label="新建工作流"
          @action="createWorkflow"
        />
      </article>

      <div class="workflow-insight-grid">
        <article class="workflow-insight-card insight-publish">
          <div class="insight-card-head">
            <strong>发布健康度</strong>
            <span>{{ publishablePercent }}%</span>
          </div>
          <el-progress
            :percentage="publishablePercent"
            :stroke-width="10"
            :color="publishablePercent >= 80 ? '#10b981' : publishablePercent >= 50 ? '#f59e0b' : '#ef4444'"
          />
          <div class="compatibility-stack">
            <div
              v-for="item in compatibilitySummary"
              :key="item.label"
              class="compatibility-row"
            >
              <span class="row-bar" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workflow-insight-card insight-source">
          <div class="insight-card-head">
            <strong>来源分布</strong>
            <span>{{ sourceBreakdown.length }} 类</span>
          </div>
          <div class="breakdown-list">
            <div v-for="item in sourceBreakdown" :key="item.label" class="breakdown-item">
              <span class="row-bar" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workflow-insight-card insight-node">
          <div class="insight-card-head">
            <strong>节点类型</strong>
            <span>{{ nodeTypeBreakdown.length }} 类</span>
          </div>
          <div class="breakdown-list">
            <div v-for="item in nodeTypeBreakdown" :key="item.label" class="breakdown-item">
              <span class="row-bar" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workflow-insight-card insight-recent">
          <div class="insight-card-head">
            <strong>最近更新</strong>
            <span>{{ recentWorkflows.length }} 条</span>
          </div>
          <div class="recent-list">
            <button
              v-for="workflow in recentWorkflows"
              :key="workflow.id"
              type="button"
              class="recent-item"
              @click="editWorkflow(workflow)"
            >
              <span>
                <i :class="statusTone(workflow.status)" />
                {{ workflow.workflowName }}
              </span>
              <small>{{ formatTime(workflow.updatedAt) }}</small>
            </button>
          </div>
        </article>
      </div>
    </section>

    <OrinDataTable class="workflow-table">
      <template #header>
        <div class="table-header">
          <strong>工作流列表</strong>
          <span>{{ filteredWorkflows.length }} / {{ workflows.length }}</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filteredWorkflows"
        border
        table-layout="auto"
        :row-class-name="rowClassName"
      >
        <el-table-column label="工作流" min-width="320">
          <template #default="{ row }">
            <div class="workflow-name">
              <span class="workflow-row-symbol" :class="sourceTone(row)">
                <el-icon><Connection /></el-icon>
              </span>
              <div>
                <button type="button" class="name-button" @click="editWorkflow(row)">
                  {{ row.workflowName }}
                </button>
                <p :title="row.description || '暂无描述'">{{ row.description || '暂无描述' }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="status-pill" :class="statusTone(row.status)">
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="92">
          <template #default="{ row }">
            <span class="source-pill" :class="sourceTone(row)">
              {{ workflowSource(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="发布校验" width="150">
          <template #default="{ row }">
            <div class="compatibility-cell" :title="publishIssueTitle(row)">
              <span class="compatibility-pill" :class="compatibilityTone(row)">
                {{ compatibilityDisplayLabel(row) }}
              </span>
              <span v-if="publishIssueCount(row)">
                {{ publishIssueCount(row) }} 项
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="节点" width="74" align="center">
          <template #default="{ row }">{{ row.nodeCount }}</template>
        </el-table-column>
        <el-table-column label="最后更新" width="136">
          <template #default="{ row }">
            <span class="time-cell" :title="formatTime(row.updatedAt)">
              {{ formatShortTime(row.updatedAt) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="152" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <button type="button" class="action-link primary" @click="editWorkflow(row)">
                编排
              </button>
              <button type="button" class="action-link" @click="goExecution(row)">
                执行
              </button>
              <el-dropdown trigger="click" @command="(command) => handleWorkflowCommand(command, row)">
                <button type="button" class="action-link">
                  更多
                  <el-icon class="more-icon"><ArrowDown /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="publish" :disabled="!canPublish(row)">发布</el-dropdown-item>
                    <el-dropdown-item command="export">导出 Dify</el-dropdown-item>
                    <el-dropdown-item command="archive" :disabled="row.status === 'ARCHIVED'">归档</el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="danger-menu-item">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <OrinEmptyState
            description="暂无工作流"
            action-label="新建工作流"
            @action="createWorkflow"
          />
        </template>
      </el-table>
    </OrinDataTable>

    <el-dialog v-model="importDialogVisible" title="导入 Dify DSL" width="520px">
      <el-form label-position="top">
        <el-form-item label="工作流名称" required>
          <el-input v-model="importForm.name" placeholder="请输入工作流名称" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="importForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="YAML/DSL 文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept=".yml,.yaml,.json"
            :on-change="onFileChange"
            :on-remove="() => { importFile = null }"
          >
            <el-icon><UploadFilled /></el-icon>
            <div>拖拽文件到此处，或点击选择</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入并转换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Connection, Plus, Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import { archiveWorkflow, createWorkflow as createWorkflowApi, deleteWorkflow, exportWorkflow, getWorkflows, importWorkflow, publishWorkflow } from '@/api/workflow'
import { NODE_TYPE_LABELS, compatibilityLabel, createDefaultWorkflowDsl, normalizeWorkflowDsl, validateWorkflowDsl } from './workflowDsl'

const router = useRouter()
const loading = ref(false)
const importing = ref(false)
const workflows = ref([])
const searchQuery = ref('')
const statusFilter = ref('')
const compatibilityFilter = ref('')
const importDialogVisible = ref(false)
const importFile = ref(null)
const importForm = reactive({ name: '', description: '' })

const normalizedWorkflows = computed(() => workflows.value.map(workflow => {
  const definition = normalizeWorkflowDsl(workflow.workflowDefinition || {})
  return {
    ...workflow,
    workflowDefinition: definition,
    compatibilityReport: definition.metadata.compatibility,
    nodeCount: definition.graph.nodes.length
  }
}))

const filteredWorkflows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return normalizedWorkflows.value.filter(workflow => {
    const text = `${workflow.workflowName || ''} ${workflow.description || ''} ${workflowSource(workflow)}`.toLowerCase()
    const matchesKeyword = !keyword || text.includes(keyword)
    const matchesStatus = !statusFilter.value || workflow.status === statusFilter.value
    const matchesCompatibility = !compatibilityFilter.value || workflowCompatibilityLevel(workflow) === compatibilityFilter.value
    return matchesKeyword && matchesStatus && matchesCompatibility
  })
})

const workflowMetrics = computed(() => {
  const items = normalizedWorkflows.value
  return [
    {
      key: 'total',
      label: '工作流总数',
      value: items.length,
      meta: `${filteredWorkflows.value.length} 个当前结果`,
      tone: 'teal',
      icon: Connection
    },
    {
      key: 'active',
      label: '已发布',
      value: items.filter(item => item.status === 'ACTIVE').length,
      meta: `${publishablePercent.value}% 可发布健康度`,
      tone: 'emerald',
      icon: Connection
    },
    {
      key: 'dify',
      label: 'Dify 导入',
      value: items.filter(item => workflowSource(item) === 'DIFY').length,
      meta: '兼容导入导出',
      tone: 'blue',
      icon: Upload
    },
    {
      key: 'blocked',
      label: '不可发布',
      value: items.filter(item => !canPublish(item)).length,
      meta: '需处理 DSL 校验问题',
      tone: 'rose',
      icon: Connection
    }
  ]
})

const featuredWorkflow = computed(() => {
  const items = [...normalizedWorkflows.value]
  return items
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
    .find(item => item.status === 'ACTIVE') || items[0] || null
})

const recentWorkflows = computed(() => [...normalizedWorkflows.value]
  .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
  .slice(0, 4))

const compatibilitySummary = computed(() => {
  const items = normalizedWorkflows.value
  return [
    { label: '完全兼容', value: items.filter(item => workflowCompatibilityLevel(item) === 'FULL').length },
    { label: '部分兼容', value: items.filter(item => workflowCompatibilityLevel(item) === 'PARTIAL').length },
    { label: '需修复', value: items.filter(item => workflowCompatibilityLevel(item) === 'BLOCKED').length }
  ]
})

const publishablePercent = computed(() => {
  const items = normalizedWorkflows.value
  if (!items.length) return 0
  return Math.round((items.filter(item => canPublish(item)).length / items.length) * 100)
})

const sourceBreakdown = computed(() => buildBreakdown(normalizedWorkflows.value.map(item => workflowSource(item))))

const nodeTypeBreakdown = computed(() => {
  const types = normalizedWorkflows.value.flatMap(item => item.workflowDefinition.graph.nodes.map(node => NODE_TYPE_LABELS[node.type] || node.type || '未知节点'))
  return buildBreakdown(types).slice(0, 5)
})

function buildBreakdown(values) {
  const counts = values.reduce((acc, value) => {
    const key = value || '未知'
    acc[key] = (acc[key] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts)
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value)
}

async function loadWorkflows() {
  loading.value = true
  try {
    const response = await getWorkflows()
    workflows.value = normalizeWorkflowRows(response)
  } catch (error) {
    ElMessage.error(error.message || '加载工作流失败')
  } finally {
    loading.value = false
  }
}

function normalizeWorkflowRows(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response?.content)) return response.content
  return []
}

async function createWorkflow() {
  const response = await createWorkflowApi({
    workflowName: '未命名工作流',
    description: 'ORIN Workflow DSL v1',
    workflowType: 'DAG',
    workflowDefinition: createDefaultWorkflowDsl()
  })
  ElMessage.success('已创建工作流草稿')
  const workflowId = response?.id || response?.data?.id
  if (workflowId) {
    router.push(`/dashboard/applications/workflows/visual/${workflowId}`)
  } else {
    loadWorkflows()
  }
}

function editWorkflow(row) {
  router.push(`/dashboard/applications/workflows/visual/${row.id}`)
}

function goExecution(row) {
  router.push({ path: '/dashboard/applications/workflows/execution', query: { workflowId: row.id } })
}

async function publish(row) {
  try {
    await publishWorkflow(row.id)
    ElMessage.success('发布成功')
    loadWorkflows()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '发布失败')
  }
}

async function archive(row) {
  await archiveWorkflow(row.id)
  ElMessage.success('已归档')
  loadWorkflows()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除「${row.workflowName}」？该操作不可恢复。`, '删除工作流', { type: 'warning' })
  await deleteWorkflow(row.id)
  ElMessage.success('已删除')
  loadWorkflows()
}

async function exportDify(row) {
  const response = await exportWorkflow(row.id)
  const blob = new Blob([response.data], { type: 'application/x-yaml' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${row.workflowName || 'workflow'}-dify.yaml`
  link.click()
  URL.revokeObjectURL(link.href)
}

function handleWorkflowCommand(command, row) {
  if (command === 'publish') {
    publish(row)
  } else if (command === 'export') {
    exportDify(row)
  } else if (command === 'archive') {
    archive(row)
  } else if (command === 'delete') {
    remove(row)
  }
}

function onFileChange(file) {
  importFile.value = file.raw
}

async function submitImport() {
  if (!importForm.name.trim() || !importFile.value) {
    ElMessage.warning('请填写名称并选择文件')
    return
  }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('name', importForm.name.trim())
    formData.append('description', importForm.description || '')
    formData.append('file', importFile.value)
    const response = await importWorkflow(formData)
    ElMessage.success('Dify DSL 已转换为 ORIN DSL')
    importDialogVisible.value = false
    importForm.name = ''
    importForm.description = ''
    importFile.value = null
    const workflowId = response?.id || response?.data?.id
    if (workflowId) {
      router.push(`/dashboard/applications/workflows/visual/${workflowId}`)
    } else {
      loadWorkflows()
    }
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function workflowSource(row) {
  return row.workflowDefinition?.metadata?.source || 'ORIN'
}

function canPublish(row) {
  return publishIssueCount(row) === 0
}

function statusLabel(status) {
  return ({ DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已归档' })[status] || status || '未知'
}

function statusTone(status) {
  return ({ DRAFT: 'tone-blue', ACTIVE: 'tone-emerald', ARCHIVED: 'tone-amber' })[status] || 'tone-neutral'
}

function sourceTone(row) {
  const source = workflowSource(row).toUpperCase()
  if (source === 'DIFY') return 'tone-violet'
  if (source === 'ORIN') return 'tone-teal'
  return 'tone-blue'
}

function publishIssueCount(row) {
  return validateWorkflowDsl(row.workflowDefinition).length
}

function publishIssueTitle(row) {
  const issues = validateWorkflowDsl(row.workflowDefinition)
  return issues.length ? issues.join('\n') : '可发布'
}

function workflowCompatibilityLevel(row) {
  if (publishIssueCount(row) > 0) return 'BLOCKED'
  return row.compatibilityReport?.level || 'FULL'
}

function compatibilityDisplayLabel(row) {
  if (publishIssueCount(row) > 0) return '需修复'
  return compatibilityLabel(row.compatibilityReport)
}

function compatibilityTone(row) {
  const level = workflowCompatibilityLevel(row)
  if (level === 'BLOCKED') return 'tone-rose'
  if (level === 'PARTIAL') return 'tone-amber'
  return 'tone-emerald'
}

function rowClassName({ row }) {
  return `workflow-row ${statusTone(row.status)}`
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function formatShortTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

onMounted(loadWorkflows)
</script>

<style scoped>
.workflow-page {
  padding: 24px;
  background:
    radial-gradient(circle at 92% 0%, rgba(14, 165, 233, 0.1), transparent 28%),
    radial-gradient(circle at 0% 4%, rgba(20, 184, 166, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.62), rgba(255, 255, 255, 0) 280px);
}

.search-input {
  width: 320px;
}

.filter-select {
  width: 160px;
}

.workflow-table {
  margin-top: 16px;
}

.workflow-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.workflow-metric-card {
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

.workflow-metric-card::after {
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  content: "";
  background: linear-gradient(90deg, var(--tone-color), transparent);
}

.metric-icon {
  width: 40px;
  height: 40px;
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--tone-border);
  border-radius: 10px;
  color: var(--tone-strong);
  background: var(--tone-soft);
}

.metric-icon :deep(svg) {
  width: 18px;
  height: 18px;
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

.tone-violet {
  --tone-color: #7c3aed;
  --tone-strong: #6d28d9;
  --tone-soft: rgba(124, 58, 237, 0.12);
  --tone-panel: rgba(245, 243, 255, 0.9);
  --tone-border: rgba(124, 58, 237, 0.2);
}

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

.workflow-overview-board {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(520px, 1.6fr);
  gap: 16px;
  margin-top: 16px;
}

.workflow-spotlight-card,
.workflow-insight-card {
  border: 1px solid var(--tone-border, rgba(148, 163, 184, 0.22));
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.82)),
    linear-gradient(135deg, var(--tone-panel, rgba(248, 250, 252, 0.9)), rgba(255, 255, 255, 0));
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
}

.workflow-spotlight-card {
  position: relative;
  min-height: 260px;
  padding: 20px;
  overflow: hidden;
}

.workflow-spotlight-card::before {
  position: absolute;
  inset: 0 0 auto 0;
  height: 4px;
  content: "";
  background: linear-gradient(90deg, var(--tone-color), transparent);
}

.spotlight-eyebrow {
  position: relative;
  margin-bottom: 12px;
  color: var(--tone-strong);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.spotlight-title-row,
.spotlight-meta,
.spotlight-actions,
.insight-card-head,
.compatibility-row,
.breakdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.spotlight-title-row,
.insight-card-head,
.compatibility-row,
.breakdown-item {
  justify-content: space-between;
}

.spotlight-title-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.spotlight-symbol,
.workflow-row-symbol {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--tone-border);
  color: var(--tone-strong);
  background: var(--tone-soft);
}

.spotlight-symbol {
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.workflow-row-symbol {
  width: 34px;
  height: 34px;
  border-radius: 9px;
}

.spotlight-title-row h2 {
  margin: 0;
  color: #101828;
  font-size: 22px;
  line-height: 1.25;
}

.workflow-spotlight-card p {
  min-height: 48px;
  margin: 12px 0 0;
  color: #475467;
  line-height: 1.7;
}

.spotlight-meta {
  flex-wrap: wrap;
  margin-top: 18px;
  color: #667085;
  font-size: 13px;
}

.spotlight-meta span,
.source-pill,
.status-pill,
.compatibility-pill {
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

.spotlight-actions {
  margin-top: 22px;
}

.workflow-insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.workflow-insight-card {
  --tone-color: #0d9488;
  --tone-strong: #0f766e;
  --tone-soft: rgba(20, 184, 166, 0.12);
  --tone-panel: rgba(240, 253, 250, 0.82);
  --tone-border: rgba(13, 148, 136, 0.18);
  min-height: 148px;
  padding: 16px;
}

.insight-source {
  --tone-color: #2563eb;
  --tone-strong: #1d4ed8;
  --tone-soft: rgba(59, 130, 246, 0.12);
  --tone-panel: rgba(239, 246, 255, 0.86);
  --tone-border: rgba(37, 99, 235, 0.18);
}

.insight-node {
  --tone-color: #7c3aed;
  --tone-strong: #6d28d9;
  --tone-soft: rgba(124, 58, 237, 0.12);
  --tone-panel: rgba(245, 243, 255, 0.86);
  --tone-border: rgba(124, 58, 237, 0.18);
}

.insight-recent {
  --tone-color: #d97706;
  --tone-strong: #b45309;
  --tone-soft: rgba(245, 158, 11, 0.12);
  --tone-panel: rgba(255, 251, 235, 0.9);
  --tone-border: rgba(217, 119, 6, 0.18);
}

.insight-card-head {
  margin-bottom: 14px;
}

.insight-card-head strong {
  color: #101828;
}

.insight-card-head span {
  color: var(--tone-strong);
  font-weight: 700;
}

.compatibility-stack,
.breakdown-list,
.recent-list {
  display: grid;
  gap: 10px;
}

.compatibility-stack {
  margin-top: 14px;
}

.compatibility-row,
.breakdown-item {
  position: relative;
  color: #667085;
  font-size: 13px;
}

.compatibility-row span:nth-child(2),
.breakdown-item span:nth-child(2) {
  flex: 1;
  min-width: 0;
}

.compatibility-row {
  min-height: 28px;
}

.row-bar {
  width: 7px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--tone-color);
}

.compatibility-row strong,
.breakdown-item strong {
  color: #101828;
}

.recent-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  border-radius: 6px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.74);
  color: #101828;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, box-shadow 0.18s ease;
}

.recent-item:hover {
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.recent-item > span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.recent-item i {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--tone-color);
}

.recent-item small {
  color: #667085;
  white-space: nowrap;
}

.table-header,
.workflow-name,
.compatibility-cell {
  display: flex;
  gap: 10px;
}

.table-header {
  align-items: center;
  justify-content: space-between;
}

.workflow-name {
  min-width: 0;
  align-items: center;
}

.workflow-name > div {
  min-width: 0;
}

.workflow-name p {
  margin: 4px 0 0;
  max-width: 100%;
  overflow: hidden;
  color: #667085;
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name-button {
  max-width: 100%;
  display: block;
  overflow: hidden;
  border: 0;
  padding: 0;
  color: #0f172a;
  background: transparent;
  font-weight: 600;
  line-height: 1.35;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.name-button:hover {
  color: var(--orin-primary, #0d9488);
}

.compatibility-cell span {
  color: #667085;
  font-size: 12px;
}

.compatibility-cell {
  align-items: center;
  min-width: 0;
}

.compatibility-cell > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compatibility-cell .compatibility-pill {
  flex: 0 0 auto;
  color: var(--tone-strong);
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  white-space: nowrap;
}

.action-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 0;
  padding: 0;
  color: #475569;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
}

.action-link:hover {
  color: var(--orin-primary, #0d9488);
}

.action-link.primary {
  color: var(--orin-primary, #0d9488);
}

.more-icon {
  margin-left: 2px;
  font-size: 12px;
}

.time-cell {
  color: #475569;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

:deep(.danger-menu-item) {
  color: #dc2626;
}

.workflow-page :deep(.workflow-table .el-table__cell) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.workflow-page :deep(.workflow-table .el-table__cell.el-table-fixed-column--right) {
  background: #fff !important;
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.05);
}

.workflow-page :deep(.workflow-table .el-table__row.tone-emerald .el-table__cell.el-table-fixed-column--right) {
  background: #f7fffc !important;
}

.workflow-page :deep(.workflow-table .el-table__row.tone-blue .el-table__cell.el-table-fixed-column--right) {
  background: #f8fbff !important;
}

.workflow-page :deep(.workflow-table .el-table__row.tone-amber .el-table__cell.el-table-fixed-column--right) {
  background: #fffdf7 !important;
}

.workflow-page :deep(.workflow-table .el-table__row.tone-rose .el-table__cell.el-table-fixed-column--right) {
  background: #fff8fa !important;
}

.workflow-page :deep(.workflow-row.tone-emerald td.el-table__cell) {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.035), transparent 46%) !important;
}

.workflow-page :deep(.workflow-row.tone-blue td.el-table__cell) {
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.035), transparent 46%) !important;
}

.workflow-page :deep(.workflow-row.tone-amber td.el-table__cell) {
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.035), transparent 46%) !important;
}

.workflow-page :deep(.workflow-row.tone-rose td.el-table__cell) {
  background: linear-gradient(90deg, rgba(244, 63, 94, 0.035), transparent 46%) !important;
}

.workflow-page :deep(.el-table__row:hover td.el-table__cell) {
  background: rgba(13, 148, 136, 0.05) !important;
}

@media (max-width: 1180px) {
  .workflow-overview-board {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .workflow-page {
    padding: 16px;
  }

  .workflow-insight-grid {
    grid-template-columns: 1fr;
  }

  .spotlight-title-row,
  .spotlight-actions,
  .recent-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
