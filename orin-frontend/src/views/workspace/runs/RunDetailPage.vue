<!--
  F04 Run 详情页 — 状态时间线 + 事件流 + 实时日志 + Trace 跳转
  路由: /workspace/runs/:runId
-->
<template>
  <div class="run-detail-page">
    <div class="page-header">
      <div>
        <el-button :icon="ArrowLeft" link @click="$router.push('/workspace/runs')">返回列表</el-button>
        <h2>运行结果</h2>
      </div>
      <div class="header-actions" v-if="run">
        <el-button v-if="isRunCancellable(run.status)" type="danger" @click="handleCancel">取消运行</el-button>
        <el-button v-if="isRunRetryable(run)" type="primary" @click="handleRetry">重新运行</el-button>
      </div>
    </div>

    <OrinAsyncState :status="state.status" :error="state.error" @retry="loadRun">
      <template v-if="run">
        <el-card class="result-card" shadow="never">
          <template #header>
            <div class="result-header">
              <div>
                <strong>{{ runStatus.label }}</strong>
                <span>{{ resultSummary }}</span>
              </div>
              <el-tag :type="runStatus.type">{{ runStatus.label }}</el-tag>
            </div>
          </template>
          <div class="io-grid">
            <section class="io-panel">
              <h3>输入</h3>
              <pre class="io-block">{{ run.input || '—' }}</pre>
            </section>
            <section class="io-panel output-panel">
              <h3>{{ run.errorMessage ? '失败原因' : '输出' }}</h3>
              <pre :class="['io-block', { 'error-text': run.errorMessage }]">{{ run.errorMessage || run.output || outputPlaceholder }}</pre>
            </section>
          </div>
          <el-alert
            v-if="run.terminalReason"
            class="terminal-reason"
            :title="terminalReasonTitle"
            :type="terminalReasonType"
            :closable="false"
            show-icon
          />
        </el-card>

        <el-collapse v-model="diagnosticPanels" class="diagnostics">
          <el-collapse-item name="process">
            <template #title>
              <div class="collapse-title">
                <strong>执行过程</strong>
                <span>{{ events.length }} 个事件 · {{ logs.length }} 条日志</span>
              </div>
            </template>
            <el-steps :active="statusStep" finish-status="success" align-center class="status-steps">
              <el-step title="已排队" />
              <el-step title="已分配" />
              <el-step title="执行中" />
              <el-step :title="runStatus.label" :status="terminalStepStatus" />
            </el-steps>

            <div v-if="assignments.length" class="assignment-section">
              <h3>调度记录</h3>
              <el-timeline>
                <el-timeline-item
                  v-for="a in assignments" :key="a.id"
                  :timestamp="formatTime(a.createdAt)"
                  :type="assignmentColor(a.status)"
                  placement="top"
                >
                  <el-tag size="small" :type="assignmentColor(a.status)">{{ a.status }}</el-tag>
                  <span class="assignment-meta">Runner {{ compactId(a.runnerId) }} · 第 {{ a.runAttempt }} 次</span>
                  <div v-if="a.terminalReason" class="event-meta">
                    {{ getTerminalReasonLabel(a.terminalReason) }}
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>

            <div class="dual-panel">
              <section class="diagnostic-panel">
                <div class="card-header-row">
                  <h3>事件</h3>
                  <el-button size="small" :loading="eventLoading" @click="refreshEvents">刷新</el-button>
                </div>
                <div v-if="events.length" class="timeline-scroll">
                  <el-timeline>
                    <el-timeline-item
                      v-for="e in events" :key="e.id"
                      :timestamp="formatTime(e.timestamp)"
                      :type="eventColor(e.level)"
                      placement="top"
                    >
                      <el-tag size="small" :type="eventTagType(e.level)">{{ e.level }}</el-tag>
                      <span class="event-message">{{ e.message }}</span>
                      <span class="event-meta">#{{ e.eventSeq }} · 第 {{ e.runAttempt }} 次</span>
                    </el-timeline-item>
                  </el-timeline>
                </div>
                <div v-else class="empty-hint">暂无事件</div>
              </section>

              <section class="diagnostic-panel">
                <div class="card-header-row">
                  <h3>日志</h3>
                  <el-button size="small" :loading="logLoading" @click="loadLogs">刷新</el-button>
                </div>
                <div class="log-viewer">
                  <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
                  <div v-for="l in logs" :key="l.sequence"
                    :class="['log-line', `log-${(l.level || 'info').toLowerCase()}`]">
                    <span class="log-seq">{{ l.sequence }}</span>
                    <span class="log-level">{{ l.level || 'INFO' }}</span>
                    <span class="log-msg">{{ l.message }}</span>
                  </div>
                </div>
              </section>
            </div>
          </el-collapse-item>

          <el-collapse-item name="technical">
            <template #title>
              <div class="collapse-title">
                <strong>技术信息</strong>
                <span>标识、Trace、重试和时间</span>
              </div>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="Run ID"><span class="mono">{{ run.id }}</span></el-descriptions-item>
              <el-descriptions-item label="Agent"><span class="mono">{{ run.agentId }}</span></el-descriptions-item>
              <el-descriptions-item label="版本"><span class="mono">{{ run.agentVersionId }}</span></el-descriptions-item>
              <el-descriptions-item label="Runner"><span class="mono">{{ run.runnerId || '—' }}</span></el-descriptions-item>
              <el-descriptions-item label="Trace">
                <router-link v-if="run.traceId" :to="`/dashboard/runtime/traces/${run.traceId}`" class="mono trace-link">
                  {{ run.traceId }}
                </router-link>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="执行次数">{{ run.runAttempt ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="重试">{{ run.retryCount }}/{{ run.maxRetries }}</el-descriptions-item>
              <el-descriptions-item label="创建者">{{ run.createdBy || '—' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(run.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(run.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ formatTime(run.completedAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="run.retryOfRunId" label="原始 Run">
                <router-link :to="`/workspace/runs/${run.retryOfRunId}`" class="mono trace-link">
                  {{ run.retryOfRunId }}
                </router-link>
              </el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>
        </el-collapse>
      </template>
    </OrinAsyncState>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import { getRun, cancelRun, retryRun, getRunLogs, getRunEvents, getRunAssignments } from '@/domains/run/api'
import {
  compactId,
  formatWorkspaceTime,
  getRunStatusMeta,
  getRunStatusStep,
  getTerminalReasonLabel,
  isRunActive,
  isRunCancellable,
  isRunRetryable,
} from '@/views/workspace/coreLoopPresentation'

const route = useRoute()
const router = useRouter()
const runId = computed(() => route.params.runId)

// ---- state ----
const state = reactive({ status: 'loading', error: null })
const run = ref(null)
const events = ref([])
const logs = ref([])
const assignments = ref([])
const eventSeq = ref(-1)
const logSeq = ref(-1)
const eventLoading = ref(false)
const logLoading = ref(false)
const diagnosticPanels = ref([])

// ---- polling ----
const statusTimer = ref(null)
const eventTimer = ref(null)
const logTimer = ref(null)

// ---- data loading ----
async function loadRun() {
  try {
    run.value = await getRun(runId.value)
    state.status = 'success'
    // start/stop polling based on terminal status
    if (isRunActive(run.value.status)) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (e) {
    state.status = 'error'
    state.error = e
  }
}

async function refreshEvents() {
  eventLoading.value = true
  try {
    const newEvents = await getRunEvents(runId.value, eventSeq.value)
    if (newEvents && newEvents.length) {
      events.value = [...events.value, ...newEvents]
      eventSeq.value = newEvents[newEvents.length - 1].eventSeq
    }
  } catch (_) { /* silent */ }
  finally { eventLoading.value = false }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const newLogs = await getRunLogs(runId.value, logSeq.value)
    if (newLogs && newLogs.length) {
      logs.value = [...logs.value, ...newLogs]
      logSeq.value = newLogs[newLogs.length - 1].sequence
    }
  } catch (_) { /* silent */ }
  finally { logLoading.value = false }
}

async function loadAssignments() {
  try {
    assignments.value = await getRunAssignments(runId.value) || []
  } catch (_) { /* silent */ }
}

// ---- polling ----
function startPolling() {
  stopPolling()
  // status poll every 3s
  statusTimer.value = setInterval(loadRun, 3000)
  // event poll every 2s
  eventTimer.value = setInterval(refreshEvents, 2000)
  // log poll every 2s
  logTimer.value = setInterval(loadLogs, 2000)
}

function stopPolling() {
  if (statusTimer.value) { clearInterval(statusTimer.value); statusTimer.value = null }
  if (eventTimer.value) { clearInterval(eventTimer.value); eventTimer.value = null }
  if (logTimer.value) { clearInterval(logTimer.value); logTimer.value = null }
}

// ---- actions ----
async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定取消这次运行？', '确认取消', {
      type: 'warning',
      confirmButtonText: '取消运行',
      cancelButtonText: '返回',
    })
    await cancelRun(runId.value)
    ElMessage.success('Run 已取消')
    loadRun()
    loadAssignments()
  } catch (_) { /* cancelled */ }
}

async function handleRetry() {
  try {
    const newRun = await retryRun(runId.value)
    ElMessage.success('已开始重新运行')
    router.push(`/workspace/runs/${newRun.id}`)
  } catch (_) { /* ignore */ }
}

// ---- computed ----
const statusStep = computed(() => getRunStatusStep(run.value?.status))
const runStatus = computed(() => getRunStatusMeta(run.value?.status))
const outputPlaceholder = computed(() => isRunActive(run.value?.status) ? '等待运行结果…' : '无输出')
const resultSummary = computed(() => {
  if (run.value?.status === 'COMPLETED') return `完成于 ${formatTime(run.value.completedAt)}`
  if (run.value?.status === 'FAILED') return '运行未完成，可展开执行过程定位问题'
  if (run.value?.status === 'CANCELLED') return '本次运行已终止'
  return '运行结束后，结果会显示在这里'
})

const terminalStepStatus = computed(() => {
  const s = run.value?.status
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED') return 'error'
  if (s === 'CANCELLED') return 'error'
  return 'process'
})

const terminalReasonTitle = computed(() => {
  if (!run.value?.terminalReason) return ''
  return `结束原因：${getTerminalReasonLabel(run.value.terminalReason)}`
})

const terminalReasonType = computed(() => {
  const r = run.value?.terminalReason
  if (!r) return 'info'
  if (r === 'USER_CANCELLED') return 'warning'
  if (r === 'NETWORK_LOST' || r === 'CREDENTIAL_REVOKED' || r === 'RUNNER_REVOKED' || r === 'SECRET_REVOKED') return 'error'
  return 'error'
})

function formatTime(ts) {
  return formatWorkspaceTime(ts)
}

function eventColor(level) {
  const map = { INFO: 'primary', WARN: 'warning', ERROR: 'danger', DEBUG: 'info' }
  return map[level] || 'primary'
}

function eventTagType(level) {
  const map = { INFO: '', WARN: 'warning', ERROR: 'danger', DEBUG: 'info' }
  return map[level] || ''
}

function assignmentColor(status) {
  const map = { ASSIGNED: 'warning', ACKED: '', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info', EXPIRED: 'danger' }
  return map[status] ?? 'info'
}

// ---- lifecycle ----
onMounted(async () => {
  await loadRun()
  await refreshEvents()
  await loadLogs()
  await loadAssignments()
})

onBeforeUnmount(() => stopPolling())

// stop polling when run reaches terminal state
watch(() => run.value?.status, (s) => {
  if (s && !isRunActive(s)) stopPolling()
})
</script>

<style scoped lang="scss">
.run-detail-page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  h2 { margin: 4px 0 2px; font-size: 22px; font-weight: 600; }
  .header-actions { display: flex; gap: 8px; flex-shrink: 0; }
}

.result-card {
  margin-bottom: 20px;
  border-color: var(--el-border-color-light);
}

.result-header,
.collapse-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.result-header > div,
.collapse-title {
  min-width: 0;
}

.result-header > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.result-header span,
.collapse-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.io-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.io-panel {
  min-width: 0;
}

.io-panel h3,
.diagnostic-panel h3,
.assignment-section h3 {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.output-panel .io-block {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.terminal-reason {
  margin-top: 16px;
}

.diagnostics {
  padding: 0 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}

.diagnostics :deep(.el-collapse-item__header) {
  height: 54px;
  font-size: 14px;
}

.diagnostics :deep(.el-collapse-item:last-child .el-collapse-item__header),
.diagnostics :deep(.el-collapse-item:last-child .el-collapse-item__wrap) {
  border-bottom: 0;
}

.collapse-title {
  width: calc(100% - 24px);
}

.status-steps {
  margin: 8px 0 28px;
}

.assignment-section {
  margin-bottom: 20px;
}

.assignment-meta,
.event-message {
  margin-left: 8px;
}

.dual-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.diagnostic-panel {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.timeline-scroll {
  max-height: 400px;
  overflow-y: auto;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.io-block {
  min-height: 120px;
  max-height: 320px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-family: inherit;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.mono {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}

.error-text {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9) !important;
  border-color: var(--el-color-danger-light-7) !important;
}

.trace-link {
  color: #409eff;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

.event-meta {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.empty-hint {
  color: #909399;
  text-align: center;
  padding: 24px;
  font-size: 13px;
}

.log-viewer {
  background: #1e1e2e;
  border-radius: 6px;
  padding: 12px;
  max-height: 400px;
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

.log-level {
  font-weight: 600;
  min-width: 44px;
}

.log-msg {
  word-break: break-all;
}

@media (max-width: 768px) {
  .io-grid,
  .dual-panel {
    grid-template-columns: 1fr;
  }

  .run-detail-page {
    padding: 16px;
  }

  .result-header,
  .collapse-title {
    align-items: flex-start;
  }
}
</style>
