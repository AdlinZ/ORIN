<!--
  Runs 执行记录列表页（Workspace vNext — F03 + F04）
  列表 + 筛选 + 创建对话框。点击行或「详情」按钮进入 RunDetailPage。
-->
<template>
  <div class="runs-page">
    <div class="page-header">
      <div>
        <h2>Runs</h2>
        <p class="subtitle">选择已冻结 Agent → 选择可用 Runner → 创建 Run → 执行</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">创建 Run</el-button>
    </div>

    <el-alert type="success" :closable="false" style="margin-bottom: 16px">
      Run 将由所选 ONLINE Runner 主动领取，并通过唯一 TaskRuntime 执行；点击详情可查看事件时间线、实时日志与 Trace。
    </el-alert>

    <!-- F04 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 140px"
        @change="loadRuns">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input v-model="filterAgent" placeholder="Agent ID（精确）" clearable size="small" style="width: 200px"
        @keyup.enter="loadRuns" @clear="loadRuns">
        <template #append><el-button :icon="Search" @click="loadRuns" /></template>
      </el-input>
      <el-button size="small" @click="clearFilters">清除筛选</el-button>
    </div>

    <!-- Run 列表 -->
    <OrinAsyncState :status="loadState.status" :error="loadState.error" empty-text="暂无 Run。点击「创建 Run」开始。" @retry="loadRuns">
      <OrinDataTable>
        <el-table :data="runs" stripe border v-loading="loadState.status === 'loading'" @row-click="goDetail"
          row-style="cursor: pointer">
          <el-table-column prop="id" label="Run ID" width="200" show-overflow-tooltip />
          <el-table-column prop="agentId" label="Agent" width="180" show-overflow-tooltip />
          <el-table-column prop="agentVersionId" label="Version" width="200" show-overflow-tooltip />
          <el-table-column prop="runnerId" label="Runner" width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="输入" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="input-preview">{{ truncate(row.input, 60) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="goDetail(row)">详情</el-button>
              <el-button v-if="isCancellable(row.status)" link type="danger" size="small"
                @click.stop="handleCancel(row)">取消</el-button>
              <el-button v-if="isRetryable(row)" link type="primary" size="small"
                @click.stop="handleRetry(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </OrinDataTable>
    </OrinAsyncState>

    <!-- 创建 Run 对话框 -->
    <el-dialog v-model="dialogVisible" title="创建 Run" width="560px" @closed="resetForm">
      <el-form :model="form" label-position="top">
        <el-form-item label="Agent（已冻结）" required>
          <el-select v-model="form.agentId" placeholder="选择 Agent" filterable
            @change="onAgentChange" style="width: 100%">
            <el-option v-for="a in frozenAgents" :key="a.agentId"
              :label="`${a.name} (v${a.activeVersionNumber})`" :value="a.agentId" />
          </el-select>
          <div v-if="agentVersions.length" style="margin-top: 8px">
            <span class="label-sm">选择版本：</span>
            <el-select v-model="form.agentVersionId" placeholder="选择版本" size="small" style="width: 100%">
              <el-option v-for="v in agentVersions" :key="v.id"
                :label="`v${v.versionNumber} — ${v.changeDescription || '冻结版本'}`" :value="v.id" />
            </el-select>
          </div>
        </el-form-item>

        <el-form-item label="Runner" required>
          <el-select v-model="form.runnerId" placeholder="选择可用 Runner" filterable style="width: 100%">
            <el-option v-for="r in onlineRunners" :key="r.id"
              :label="`${r.name} (${r.status} · ${r.hostname || '-'})`" :value="r.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="输入 (Prompt)">
          <el-input v-model="form.input" type="textarea" :rows="3"
            placeholder="给 Agent 的输入…" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { createRun, listRuns, cancelRun, retryRun } from '@/domains/run/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'
import { listRunners } from '@/api/runner'

const router = useRouter()

// ---- state ----
const loadState = reactive({ status: 'loading', error: null })
const runs = ref([])
const dialogVisible = ref(false)
const creating = ref(false)

const filterStatus = ref('')
const filterAgent = ref('')
const statusOptions = ['QUEUED', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']

const form = reactive({
  agentId: '',
  agentVersionId: '',
  runnerId: '',
  input: ''
})

const frozenAgents = ref([])
const agentVersions = ref([])
const onlineRunners = ref([])

// ---- auto-refresh ----
const autoRefreshTimer = ref(null)
const hasActiveRuns = computed(() =>
  runs.value.some(r => ['QUEUED', 'LEASED', 'RUNNING'].includes(r.status))
)

// ---- data loading ----
async function loadRuns() {
  loadState.status = 'loading'
  try {
    const params = { size: 100 }
    if (filterStatus.value) params.status = filterStatus.value
    if (filterAgent.value) params.agentId = filterAgent.value
    const page = await listRuns(params)
    runs.value = page.content || []
    loadState.status = 'success'
  } catch (e) {
    loadState.status = 'error'
    loadState.error = e
  }
}

function clearFilters() {
  filterStatus.value = ''
  filterAgent.value = ''
  loadRuns()
}

async function loadFormData() {
  try {
    const [agents, runnersList] = await Promise.all([
      listAgents(),
      listRunners()
    ])
    frozenAgents.value = (agents || []).filter(a => a.activeVersionStatus === 'FROZEN')
    const runnerData = runnersList.content || runnersList || []
    onlineRunners.value = (Array.isArray(runnerData) ? runnerData : []).filter(
      r => r.status === 'ONLINE'
    )
  } catch (_) { /* dialog will show empty options */ }
}

async function onAgentChange(agentId) {
  form.agentVersionId = ''
  agentVersions.value = []
  if (!agentId) return
  try {
    const list = await getAgentVersions(agentId)
    agentVersions.value = (list || []).filter(v => v.status === 'FROZEN')
    if (agentVersions.value.length === 1) {
      form.agentVersionId = agentVersions.value[0].id
    }
  } catch (_) { /* ignore */ }
}

// ---- actions ----
function goDetail(row) {
  router.push(`/workspace/runs/${row.id}`)
}

function openCreateDialog() {
  loadFormData()
  dialogVisible.value = true
}

function resetForm() {
  form.agentId = ''
  form.agentVersionId = ''
  form.runnerId = ''
  form.input = ''
  agentVersions.value = []
}

async function doCreate() {
  if (!form.agentId || !form.agentVersionId || !form.runnerId) {
    ElMessage.warning('请选择 Agent、版本和 Runner')
    return
  }
  creating.value = true
  try {
    await createRun({
      agentId: form.agentId,
      agentVersionId: form.agentVersionId,
      runnerId: form.runnerId,
      input: form.input
    })
    ElMessage.success('Run 已创建')
    dialogVisible.value = false
    resetForm()
    loadRuns()
  } catch (_) {
    ElMessage.error('创建 Run 失败')
  } finally {
    creating.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消 Run ${row.id}？`, '确认取消', { type: 'warning' })
    await cancelRun(row.id)
    ElMessage.success('Run 已取消')
    loadRuns()
  } catch (_) { /* cancelled */ }
}

async function handleRetry(row) {
  try {
    const newRun = await retryRun(row.id)
    ElMessage.success(`已创建重试 Run: ${newRun.id}`)
    loadRuns()
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
function statusType(s) {
  const map = { QUEUED: 'info', LEASED: 'warning', RUNNING: '', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }
  return map[s] ?? 'info'
}

function isCancellable(s) {
  return s === 'QUEUED' || s === 'LEASED' || s === 'RUNNING'
}

function isRetryable(row) {
  return (row.status === 'FAILED' || row.status === 'CANCELLED') && row.retryCount < row.maxRetries
}

function formatTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN')
}

function truncate(s, n) {
  if (!s) return '—'
  return s.length > n ? s.slice(0, n) + '…' : s
}

onMounted(async () => {
  await loadRuns()
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

.input-preview {
  color: #606266;
  font-size: 13px;
}

.label-sm {
  font-size: 12px;
  color: #909399;
}
</style>
