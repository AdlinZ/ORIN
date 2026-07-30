<!--
  Runs 执行记录列表页（Workspace vNext — F03 + F04）
  列表 + 筛选 + 创建对话框。点击行或「详情」按钮进入 RunDetailPage。
-->
<template>
  <div class="runs-page">
    <div class="page-header">
      <div>
        <h2>运行中心</h2>
        <p class="subtitle">发起一次真实运行，跟进进度、查看结果，并处理失败任务。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">开始运行</el-button>
    </div>

    <el-alert
      v-if="catalogLoaded && onlineRunners.length === 0"
      type="warning"
      :closable="false"
      show-icon
      class="blocking-alert"
      title="没有在线 Runner，新的运行不会开始"
    >
      <template #default>
        <div class="alert-action">
          <span>先接入执行节点，再开始运行。</span>
          <el-button size="small" @click="goEnrollRunner">接入 Runner</el-button>
        </div>
      </template>
    </el-alert>
    <el-alert
      v-else-if="catalogLoaded && frozenAgents.length === 0"
      type="info"
      :closable="false"
      show-icon
      class="blocking-alert"
      title="还没有可运行的 Agent"
    >
      <template #default>
        <div class="alert-action">
          <span>先完成 Agent 配置并冻结一个版本。</span>
          <el-button size="small" @click="router.push(ROUTES.WORKSPACE.AGENTS)">前往 Agent</el-button>
        </div>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="运行结果概览">
      <button type="button" :class="{ active: outcomeFilter === 'ACTIVE' }" @click="toggleOutcomeFilter('ACTIVE')">
        <span>进行中</span>
        <strong class="active-number">{{ runSummary.active }}</strong>
        <small>排队、分配或执行中</small>
      </button>
      <button type="button" :class="{ active: outcomeFilter === 'SUCCEEDED' }" @click="toggleOutcomeFilter('SUCCEEDED')">
        <span>已产出结果</span>
        <strong class="success-number">{{ runSummary.succeeded }}</strong>
        <small>可以查看和验证</small>
      </button>
      <button type="button" :class="{ active: outcomeFilter === 'NEEDS_ACTION' }" @click="toggleOutcomeFilter('NEEDS_ACTION')">
        <span>需要处理</span>
        <strong :class="{ 'danger-number': runSummary.needsAction > 0 }">{{ runSummary.needsAction }}</strong>
        <small>失败后可诊断或重试</small>
      </button>
    </section>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索任务、结果或 Agent"
        class="search-input"
      />
      <el-select v-model="outcomeFilter" size="small" class="outcome-filter">
        <el-option label="全部结果" value="ALL" />
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已产出结果" value="SUCCEEDED" />
        <el-option label="需要处理" value="NEEDS_ACTION" />
        <el-option label="已停止" value="STOPPED" />
      </el-select>
      <el-select v-model="filterAgent" placeholder="全部 Agent" clearable filterable size="small" class="agent-filter">
        <el-option v-for="agent in agentCatalog" :key="agent.agentId" :label="agent.name" :value="agent.agentId" />
      </el-select>
      <el-button size="small" :loading="loadState.status === 'loading'" @click="loadRuns">刷新</el-button>
    </div>

    <OrinAsyncState :status="loadState.status" :error="loadState.error" empty-text="暂无运行记录。准备好 Agent 和 Runner 后，开始第一次运行。" @retry="loadRuns">
      <OrinDataTable title="运行任务" :description="`${filteredRuns.length} / ${runs.length} 条记录`">
        <ResizableTable :data="filteredRuns" stripe border v-loading="loadState.status === 'loading'" @row-click="goDetail"
          :row-style="{ cursor: 'pointer' }">
          <el-table-column label="运行任务" min-width="300">
            <template #default="{ row }">
              <div class="run-identity">
                <strong>{{ truncate(row.input, 72) }}</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="进度与结果" min-width="250">
            <template #default="{ row }">
              <div class="run-outcome">
                <el-tag :type="runStatus(row.status).type" size="small">{{ runStatus(row.status).label }}</el-tag>
                <small>{{ outcomeDescription(row) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Agent" min-width="190">
            <template #default="{ row }">
              <div class="run-identity">
                <strong>{{ agentName(row.agentId) }}</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近变化" width="140" align="center">
            <template #default="{ row }">
              {{ formatTime(row.completedAt || row.startedAt || row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="210" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link :type="row.status === 'FAILED' ? 'danger' : 'primary'" size="small" @click.stop="goDetail(row)">
                {{ detailActionLabel(row) }}
              </el-button>
              <el-button v-if="isCancellable(row.status)" link type="danger" size="small"
                @click.stop="handleCancel(row)">取消</el-button>
              <el-button v-if="isRetryable(row)" link type="primary" size="small"
                @click.stop="handleRetry(row)">重新运行</el-button>
            </template>
          </el-table-column>
        </ResizableTable>
      </OrinDataTable>
    </OrinAsyncState>

    <el-dialog v-model="dialogVisible" title="开始运行" width="640px" @closed="resetForm">
      <p class="dialog-intro">选择一个可运行的 Agent，描述这次要完成的任务。</p>

      <section class="run-task-section">
        <div class="step-label"><span>1</span>选择 Agent</div>
        <el-select
          v-model="form.agentId"
          placeholder="选择可运行 Agent"
          filterable
          style="width: 100%"
          @change="onAgentChange"
        >
          <el-option
            v-for="agent in frozenAgents"
            :key="agent.agentId"
            :label="agent.name"
            :value="agent.agentId"
          />
        </el-select>
        <div v-if="selectedAgent" class="selection-summary">
          <div>
            <strong>{{ selectedAgent.name }}</strong>
            <span>{{ selectedAgent.description || '已冻结，可开始真实运行' }}</span>
          </div>
          <el-tag type="success" effect="plain">运行环境已就绪</el-tag>
        </div>
      </section>

      <section class="run-task-section">
        <div class="step-label"><span>2</span>描述任务</div>
        <el-input
          v-model="form.input"
          type="textarea"
          :rows="5"
          placeholder="例如：总结这段材料，并列出三个需要继续确认的问题"
        />
        <p class="field-hint">运行结果会原样关联这段输入，方便之后复查和比较。</p>
      </section>

      <el-collapse v-model="runSettingPanels" class="run-settings">
        <el-collapse-item name="settings">
          <template #title>
            <div class="settings-title">
              <strong>高级运行设置</strong>
              <span>系统已自动选择，按需调整</span>
            </div>
          </template>
          <el-form :model="form" label-position="top">
            <el-form-item label="运行版本（默认当前版本）" required>
              <el-select v-model="form.agentVersionId" placeholder="选择冻结版本" style="width: 100%">
                <el-option
                  v-for="version in agentVersions"
                  :key="getAgentVersionId(version)"
                  :label="versionOptionLabel(version)"
                  :value="getAgentVersionId(version)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Runner（默认在线节点）" required>
              <el-select v-model="form.runnerId" placeholder="选择在线 Runner" filterable style="width: 100%">
                <el-option
                  v-for="runner in onlineRunners"
                  :key="runner.id"
                  :label="runnerOptionLabel(runner)"
                  :value="runner.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!runReady" @click="doCreate">
          开始运行
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { createRun, listRuns, cancelRun, retryRun } from '@/domains/run/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'
import { listRunners } from '@/api/runner'
import { ROUTES } from '@/router/routes'
import {
  chooseDeliverableVersion,
  chooseRunRunner,
  compactId,
  formatWorkspaceTime,
  getAgentVersionId,
  getAgentVersionNumber,
  getRunOutcomeGroup,
  getRunStatusMeta,
  getTerminalReasonLabel,
  isRunCancellable,
  isRunRetryable,
} from '@/views/workspace/coreLoopPresentation'

const router = useRouter()
const route = useRoute()

// ---- state ----
const loadState = reactive({ status: 'loading', error: null })
const runs = ref([])
const dialogVisible = ref(false)
const creating = ref(false)
const runSettingPanels = ref([])

const keyword = ref('')
const outcomeFilter = ref('ALL')
const filterAgent = ref('')

const form = reactive({
  agentId: '',
  agentVersionId: '',
  runnerId: '',
  input: ''
})

const frozenAgents = ref([])
const agentCatalog = ref([])
const agentVersions = ref([])
const onlineRunners = ref([])
const runnerCatalog = ref([])
const catalogLoaded = ref(false)
const runStatus = getRunStatusMeta

// ---- auto-refresh ----
const autoRefreshTimer = ref(null)
const hasActiveRuns = computed(() =>
  runs.value.some(r => ['QUEUED', 'LEASED', 'RUNNING'].includes(r.status))
)
const selectedAgent = computed(() =>
  frozenAgents.value.find((agent) => agent.agentId === form.agentId) || null
)
const runReady = computed(() =>
  Boolean(form.agentId && form.agentVersionId && form.runnerId && form.input.trim())
)
const runSummary = computed(() => runs.value.reduce((summary, run) => {
  const group = getRunOutcomeGroup(run.status)
  if (group === 'ACTIVE') summary.active += 1
  if (group === 'SUCCEEDED') summary.succeeded += 1
  if (group === 'NEEDS_ACTION') summary.needsAction += 1
  return summary
}, { active: 0, succeeded: 0, needsAction: 0 }))
const filteredRuns = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return runs.value.filter((run) => {
    const matchesOutcome = outcomeFilter.value === 'ALL'
      || getRunOutcomeGroup(run.status) === outcomeFilter.value
    const matchesAgent = !filterAgent.value || run.agentId === filterAgent.value
    const matchesKeyword = !query || [
      run.id,
      run.input,
      run.output,
      run.errorMessage,
      agentName(run.agentId),
    ].some((value) => String(value || '').toLowerCase().includes(query))
    return matchesOutcome && matchesAgent && matchesKeyword
  })
})

// ---- data loading ----
async function loadRuns() {
  loadState.status = 'loading'
  try {
    const params = { size: 100 }
    const page = await listRuns(params)
    runs.value = page.content || []
    loadState.status = 'success'
  } catch (e) {
    loadState.status = 'error'
    loadState.error = e
  }
}

async function loadFormData() {
  try {
    const [agents, runnersList] = await Promise.all([
      listAgents(),
      listRunners()
    ])
    agentCatalog.value = agents || []
    frozenAgents.value = agentCatalog.value.filter(a => a.activeVersionStatus === 'FROZEN')
    const runnerData = runnersList.content || runnersList || []
    runnerCatalog.value = Array.isArray(runnerData) ? runnerData : []
    onlineRunners.value = runnerCatalog.value.filter(
      r => r.status === 'ONLINE'
    )
  } catch (_) { /* 页面保留已有运行记录 */ }
  finally {
    catalogLoaded.value = true
  }
}

async function onAgentChange(agentId, preferredVersionId = '') {
  form.agentVersionId = ''
  agentVersions.value = []
  if (!agentId) return
  try {
    const list = await getAgentVersions(agentId)
    agentVersions.value = (list || []).filter(v => v.status === 'FROZEN')
    const agent = frozenAgents.value.find((item) => item.agentId === agentId)
    form.agentVersionId = chooseDeliverableVersion(agent, agentVersions.value, preferredVersionId)
  } catch (_) { /* ignore */ }
}

// ---- actions ----
function goDetail(row) {
  router.push(`/workspace/runs/${row.id}`)
}

async function openCreateDialog() {
  if (!catalogLoaded.value) await loadFormData()
  if (onlineRunners.value.length === 0) {
    goEnrollRunner()
    return
  }
  if (frozenAgents.value.length === 0) {
    router.push(ROUTES.WORKSPACE.AGENTS)
    return
  }
  form.runnerId = chooseRunRunner(onlineRunners.value, form.runnerId)
  if (!form.agentId && frozenAgents.value.length === 1) {
    form.agentId = frozenAgents.value[0].agentId
    await onAgentChange(form.agentId)
  }
  dialogVisible.value = true
}

function goEnrollRunner() {
  const query = { returnTo: 'run' }
  const agentId = form.agentId || String(route.query.agentId || '')
  const versionId = form.agentVersionId || String(route.query.versionId || '')
  if (agentId) query.agentId = agentId
  if (versionId) query.versionId = versionId
  router.push({ path: ROUTES.WORKSPACE.RUNNERS, query })
}

function resetForm() {
  form.agentId = ''
  form.agentVersionId = ''
  form.runnerId = ''
  form.input = ''
  agentVersions.value = []
  runSettingPanels.value = []
}

async function doCreate() {
  if (!form.agentId || !form.input.trim()) {
    ElMessage.warning('请选择 Agent 并填写任务')
    return
  }
  if (!form.agentVersionId || !form.runnerId) {
    ElMessage.warning('运行环境尚未准备好，请检查高级运行设置')
    return
  }
  creating.value = true
  try {
    const created = await createRun({
      agentId: form.agentId,
      agentVersionId: form.agentVersionId,
      runnerId: form.runnerId,
      input: form.input
    })
    ElMessage.success('运行已开始')
    dialogVisible.value = false
    const createdId = created?.id || created?.runId || created?.run_id
    if (createdId) {
      router.push(`/workspace/runs/${createdId}`)
    } else {
      resetForm()
      loadRuns()
    }
  } catch (_) {
    ElMessage.error('运行启动失败')
  } finally {
    creating.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消这次运行？`, '确认取消', {
      type: 'warning',
      confirmButtonText: '取消运行',
      cancelButtonText: '返回',
    })
    await cancelRun(row.id)
    ElMessage.success('运行已取消')
    loadRuns()
  } catch (_) { /* cancelled */ }
}

async function handleRetry(row) {
  try {
    const newRun = await retryRun(row.id)
    ElMessage.success('已开始重新运行')
    if (newRun?.id) router.push(`/workspace/runs/${newRun.id}`)
    else loadRuns()
  } catch (_) { /* ignore */ }
}

// ---- auto-refresh ----
function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshTimer.value = setInterval(loadRuns, 5000)
}

function stopAutoRefresh() {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

watch(hasActiveRuns, (active) => {
  if (active) startAutoRefresh()
  else stopAutoRefresh()
})

// ---- helpers ----
const isCancellable = isRunCancellable
const isRetryable = isRunRetryable

function formatTime(ts) {
  return formatWorkspaceTime(ts)
}

function truncate(s, n) {
  if (!s) return '未填写运行输入'
  return s.length > n ? s.slice(0, n) + '…' : s
}

function agentName(agentId) {
  return agentCatalog.value.find((agent) => agent.agentId === agentId)?.name || compactId(agentId)
}

function versionOptionLabel(version) {
  const number = getAgentVersionNumber(version) || '?'
  const description = version.changeDescription || version.change_description || '冻结版本'
  return `v${number} · ${description}`
}

function runnerOptionLabel(runner) {
  return runner.hostname ? `${runner.name} · ${runner.hostname}` : runner.name
}

function outcomeDescription(row) {
  if (row.status === 'COMPLETED') {
    return row.output ? truncate(row.output, 46) : '运行完成，可查看结果'
  }
  if (row.status === 'FAILED') {
    return row.errorMessage
      ? truncate(row.errorMessage, 46)
      : getTerminalReasonLabel(row.terminalReason)
  }
  if (row.status === 'CANCELLED') return '本次运行已停止，可按需重新运行'
  if (row.status === 'RUNNING') return 'Agent 正在执行任务'
  if (row.status === 'LEASED') return '已分配执行节点，等待开始'
  if (row.status === 'QUEUED') return '正在等待可用执行节点'
  return '状态等待确认'
}

function detailActionLabel(row) {
  if (row.status === 'FAILED') return '处理失败'
  if (row.status === 'COMPLETED') return '查看结果'
  if (isCancellable(row.status)) return '查看进度'
  return '查看详情'
}

function toggleOutcomeFilter(nextFilter) {
  outcomeFilter.value = outcomeFilter.value === nextFilter ? 'ALL' : nextFilter
}

onMounted(async () => {
  const presetAgentId = String(route.query.agentId || '')
  const presetVersionId = String(route.query.versionId || '')
  if (presetAgentId) filterAgent.value = presetAgentId
  await Promise.all([loadRuns(), loadFormData()])
  if (route.query.create === '1') {
    await openCreateDialog()
    const selected = frozenAgents.value.find((agent) => agent.agentId === presetAgentId)
    if (selected) {
      form.agentId = selected.agentId
      await onAgentChange(selected.agentId, presetVersionId)
    }
  }
  if (hasActiveRuns.value) startAutoRefresh()
})

onUnmounted(stopAutoRefresh)
</script>

<style scoped lang="scss">
.runs-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  h2 { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
  .subtitle { margin: 0; color: #909399; font-size: 13px; }
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.summary-strip button {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border: 0;
  border-right: 1px solid var(--el-border-color-light);
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.summary-strip button:last-child {
  border-right: 0;
}

.summary-strip button:hover,
.summary-strip button.active {
  background: var(--el-fill-color-light);
}

.summary-strip button.active {
  box-shadow: inset 0 -2px 0 var(--el-color-primary);
}

.summary-strip span,
.summary-strip small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-strip strong {
  color: var(--el-text-color-primary);
  font-size: 24px;
  line-height: 1.1;
}

.summary-strip .active-number {
  color: var(--el-color-primary);
}

.summary-strip .success-number {
  color: var(--el-color-success);
}

.summary-strip .danger-number {
  color: var(--el-color-danger);
}

.search-input {
  width: min(360px, 100%);
}

.outcome-filter {
  width: 150px;
}

.agent-filter {
  width: 220px;
}

.blocking-alert {
  margin-bottom: 16px;
}

.alert-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.run-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.run-identity strong,
.run-identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-identity small {
  color: var(--el-text-color-secondary, #64748b);
  font-size: 12px;
}

.run-outcome {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.run-outcome small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-intro {
  margin: -6px 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.run-task-section {
  margin-bottom: 22px;
}

.step-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.step-label > span {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
}

.selection-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--el-color-success-light-7);
  border-radius: 8px;
  background: var(--el-color-success-light-9);
}

.selection-summary > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.selection-summary span {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-hint {
  margin: 7px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.run-settings {
  padding: 0 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.run-settings :deep(.el-collapse-item__header),
.run-settings :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.settings-title {
  display: flex;
  width: calc(100% - 22px);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.settings-title span {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: 1fr;
  }

  .summary-strip button {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .summary-strip button:last-child {
    border-bottom: 0;
  }

  .filter-bar {
    flex-wrap: wrap;
  }

  .search-input {
    width: 100%;
  }
}
</style>
