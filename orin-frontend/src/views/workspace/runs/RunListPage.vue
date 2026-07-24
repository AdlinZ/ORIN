<!--
  Runs 执行记录页（Workspace vNext — F03）
  功能：创建 Run（选 Agent + Runner）、列表、取消、重试
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

    <!-- Run 列表 -->
    <OrinAsyncState :status="state.status" empty-text="暂无 Run。点击「创建 Run」开始。" @retry="loadRuns">
      <OrinDataTable>
        <el-table :data="runs" stripe border v-loading="state.status === 'loading'">
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
          <el-table-column label="操作" width="180" align="center">
            <template #default="{ row }">
              <el-button v-if="isCancellable(row.status)" link type="danger" size="small"
                @click="handleCancel(row)">取消</el-button>
              <el-button v-if="isRetryable(row)" link type="primary" size="small"
                @click="handleRetry(row)">重试</el-button>
              <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="Run 详情" size="480px">
      <template v-if="selectedRun">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Run ID">{{ selectedRun.id }}</el-descriptions-item>
          <el-descriptions-item label="Agent">{{ selectedRun.agentId }}</el-descriptions-item>
          <el-descriptions-item label="Version">{{ selectedRun.agentVersionId }}</el-descriptions-item>
          <el-descriptions-item label="Runner">{{ selectedRun.runnerId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(selectedRun.status)" size="small">{{ selectedRun.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(selectedRun.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(selectedRun.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(selectedRun.completedAt) }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedRun.retryCount }}/{{ selectedRun.maxRetries }}</el-descriptions-item>
          <el-descriptions-item label="输入">
            <pre class="mono-text">{{ selectedRun.input || '—' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="输出">
            <pre class="mono-text">{{ selectedRun.output || '—' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedRun.errorMessage" label="错误">
            <pre class="mono-text error-text">{{ selectedRun.errorMessage }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <!-- F04 操作按钮 -->
        <div v-if="isCancellable(selectedRun.status) || isRetryable(selectedRun)" style="margin-top: 16px; display: flex; gap: 8px">
          <el-button v-if="isCancellable(selectedRun.status)" type="danger" size="small"
            @click="handleCancel(selectedRun)">取消 Run</el-button>
          <el-button v-if="isRetryable(selectedRun)" type="primary" size="small"
            @click="handleRetry(selectedRun)">重试 Run</el-button>
        </div>

        <!-- F04 日志查看器 -->
        <div style="margin-top: 20px">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
            <h4 style="margin: 0">执行日志</h4>
            <el-button size="small" :loading="logLoading" @click="loadLogs">刷新</el-button>
          </div>
          <div class="log-viewer" ref="logViewerRef">
            <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
            <div v-for="l in logs" :key="l.sequence" :class="['log-line', `log-${l.level?.toLowerCase() || 'info'}`]">
              <span class="log-seq">{{ l.sequence }}</span>
              <span class="log-time">{{ formatTime(l.createdAt) }}</span>
              <span class="log-level">{{ l.level }}</span>
              <span class="log-msg">{{ l.message }}</span>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { createRun, listRuns, cancelRun, retryRun, getRunLogs } from '@/domains/run/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'
import { listRunners } from '@/api/runner'

// ---- state ----
const state = reactive({ status: 'loading', error: null })
const runs = ref([])
const dialogVisible = ref(false)
const creating = ref(false)
const detailVisible = ref(false)
const selectedRun = ref(null)

const form = reactive({
  agentId: '',
  agentVersionId: '',
  runnerId: '',
  input: ''
})

const frozenAgents = ref([])
const agentVersions = ref([])
const onlineRunners = ref([])

// ---- F04 auto-refresh ----
const autoRefreshTimer = ref(null)
const hasActiveRuns = computed(() =>
  runs.value.some(r => ['QUEUED', 'LEASED', 'RUNNING'].includes(r.status))
)

// ---- F04 log viewer ----
const logs = ref([])
const logSeq = ref(-1)
const logLoading = ref(false)

// ---- data loading ----
async function loadRuns() {
  state.status = 'loading'
  try {
    const page = await listRuns({ size: 100 })
    runs.value = page.content || []
    state.status = 'success'
  } catch (e) {
    state.status = 'error'
    state.error = e
    ElMessage.error('加载 Run 列表失败')
  }
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
      r => r.status === 'ONLINE' || r.status === 'DEGRADED'
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
  } catch (e) {
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
    await retryRun(row.id)
    ElMessage.success('已创建重试 Run')
    loadRuns()
  } catch (_) { /* ignore */ }
}

async function showDetail(row) {
  selectedRun.value = row
  detailVisible.value = true
  logs.value = []
  logSeq.value = -1
  await loadLogs()
}

async function loadLogs() {
  if (!selectedRun.value) return
  logLoading.value = true
  try {
    const newLogs = await getRunLogs(selectedRun.value.id, logSeq.value)
    if (newLogs && newLogs.length) {
      logs.value = [...logs.value, ...newLogs]
      logSeq.value = newLogs[newLogs.length - 1].sequence
    }
  } catch (_) { /* ignore */ }
  finally { logLoading.value = false }
}

function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshTimer.value = setInterval(async () => {
    try {
      const page = await listRuns({ size: 100 })
      runs.value = page.content || []
    } catch (_) { /* silent */ }
  }, 5000)
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
  return map[s] || 'info'
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

.input-preview {
  color: #606266;
  font-size: 13px;
}

.label-sm {
  font-size: 12px;
  color: #909399;
}

.mono-text {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
}

.error-text {
  color: #f56c6c;
}

.log-viewer {
  background: #1e1e2e;
  border-radius: 6px;
  padding: 12px;
  max-height: 320px;
  overflow-y: auto;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.log-empty {
  color: #6c7086;
  text-align: center;
  padding: 20px;
}

.log-line {
  display: flex;
  gap: 8px;
  color: #cdd6f4;
}

.log-info { color: #cdd6f4; }
.log-warn { color: #f9e2af; }
.log-error { color: #f38ba8; }
.log-debug { color: #6c7086; }

.log-seq {
  color: #585b70;
  min-width: 32px;
  text-align: right;
}

.log-time {
  color: #585b70;
  min-width: 100px;
}

.log-level {
  font-weight: 600;
  min-width: 44px;
}

.log-msg {
  word-break: break-all;
}
</style>
