<!--
  F04 Run 详情页 — 状态时间线 + 事件流 + 实时日志 + Trace 跳转
  路由: /workspace/runs/:runId
-->
<template>
  <div class="run-detail-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <el-button :icon="ArrowLeft" link @click="$router.push('/workspace/runs')">返回列表</el-button>
        <h2>Run {{ runId }}</h2>
      </div>
      <div class="header-actions" v-if="run">
        <el-button v-if="isCancellable(run.status)" type="danger" @click="handleCancel">取消 Run</el-button>
        <el-button v-if="isRetryable(run)" type="primary" @click="handleRetry">重试 Run</el-button>
      </div>
    </div>

    <OrinAsyncState :status="state.status" :error="state.error" @retry="loadRun">
      <template v-if="run">
        <!-- 状态流转 -->
        <el-card class="status-card" shadow="never">
          <template #header><strong>状态流转</strong></template>
          <el-steps :active="statusStep" finish-status="success" align-center>
            <el-step title="QUEUED" description="已排队" />
            <el-step title="LEASED" description="已分配" />
            <el-step title="RUNNING" description="执行中" />
            <el-step :title="run.status" :description="statusDescription"
              :status="terminalStepStatus" />
          </el-steps>
          <div v-if="run.terminalReason" class="terminal-reason">
            <el-alert :title="terminalReasonTitle" :type="terminalReasonType" :closable="false" show-icon />
          </div>
        </el-card>

        <!-- 基本信息 -->
        <el-card class="info-card" shadow="never">
          <template #header><strong>基本信息</strong></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="Run ID">
              <span class="mono">{{ run.id }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(run.status)" size="small">{{ run.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Agent">
              <span class="mono">{{ run.agentId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="Version">
              <span class="mono">{{ run.agentVersionId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="Runner">
              <span class="mono">{{ run.runnerId || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="Trace ID">
              <router-link v-if="run.traceId"
                :to="`/dashboard/runtime/traces/${run.traceId}`"
                class="mono trace-link">
                {{ run.traceId }}
              </router-link>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="Attempt">{{ run.runAttempt ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="重试">{{ run.retryCount }}/{{ run.maxRetries }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(run.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(run.startedAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ formatTime(run.completedAt) }}</el-descriptions-item>
            <el-descriptions-item label="创建者">{{ run.createdBy || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="run.input" class="io-section">
            <h4>输入</h4>
            <pre class="mono io-block">{{ run.input }}</pre>
          </div>
          <div v-if="run.output" class="io-section">
            <h4>输出</h4>
            <pre class="mono io-block">{{ run.output }}</pre>
          </div>
          <div v-if="run.errorMessage" class="io-section">
            <h4>错误</h4>
            <pre class="mono io-block error-text">{{ run.errorMessage }}</pre>
          </div>
          <div v-if="run.retryOfRunId" class="io-section">
            <h4>原始 Run</h4>
            <router-link :to="`/workspace/runs/${run.retryOfRunId}`" class="mono trace-link">
              {{ run.retryOfRunId }}
            </router-link>
          </div>
        </el-card>

        <!-- 分配历史 -->
        <el-card v-if="assignments.length" class="timeline-card" shadow="never">
          <template #header><strong>分配历史</strong>（{{ assignments.length }} 次）</template>
          <el-timeline>
            <el-timeline-item
              v-for="a in assignments" :key="a.id"
              :timestamp="formatTime(a.createdAt)"
              :type="assignmentColor(a.status)"
              placement="top">
              <div>
                <el-tag size="small" :type="assignmentColor(a.status)">{{ a.status }}</el-tag>
                <span style="margin-left: 8px">Runner: <code>{{ a.runnerId }}</code></span>
                <span style="margin-left: 8px">Attempt #{{ a.runAttempt }}</span>
                <div v-if="a.terminalReason" style="margin-top: 4px; color: #909399; font-size: 12px">
                  终态原因: {{ terminalReasonLabel(a.terminalReason) }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 事件时间线 + 日志（双栏） -->
        <div class="dual-panel">
          <!-- 事件时间线 -->
          <el-card class="timeline-card" shadow="never">
            <template #header>
              <div class="card-header-row">
                <strong>事件时间线</strong>
                <el-button size="small" :loading="eventLoading" @click="refreshEvents">刷新</el-button>
              </div>
            </template>
            <div class="timeline-scroll" v-if="events.length">
              <el-timeline>
                <el-timeline-item
                  v-for="e in events" :key="e.id"
                  :timestamp="formatTime(e.timestamp)"
                  :type="eventColor(e.level)"
                  placement="top">
                  <el-tag size="small" :type="eventTagType(e.level)">{{ e.level }}</el-tag>
                  <span style="margin-left: 6px">{{ e.message }}</span>
                  <span class="event-meta">#{{ e.eventSeq }} · attempt {{ e.runAttempt }}</span>
                </el-timeline-item>
              </el-timeline>
            </div>
            <div v-else class="empty-hint">暂无事件</div>
          </el-card>

          <!-- 日志查看器 -->
          <el-card class="timeline-card" shadow="never">
            <template #header>
              <div class="card-header-row">
                <strong>实时日志</strong>
                <el-button size="small" :loading="logLoading" @click="loadLogs">刷新</el-button>
              </div>
            </template>
            <div class="log-viewer" ref="logViewerRef">
              <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
              <div v-for="l in logs" :key="l.sequence"
                :class="['log-line', `log-${(l.level || 'info').toLowerCase()}`]">
                <span class="log-seq">{{ l.sequence }}</span>
                <span class="log-time">{{ formatTime(l.createdAt) }}</span>
                <span class="log-level">{{ l.level || 'INFO' }}</span>
                <span class="log-msg">{{ l.message }}</span>
              </div>
            </div>
          </el-card>
        </div>
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
    if (isActive(run.value.status)) {
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
    await ElMessageBox.confirm(`确定取消 Run ${runId.value}？`, '确认取消', { type: 'warning' })
    await cancelRun(runId.value)
    ElMessage.success('Run 已取消')
    loadRun()
    loadAssignments()
  } catch (_) { /* cancelled */ }
}

async function handleRetry() {
  try {
    const newRun = await retryRun(runId.value)
    ElMessage.success(`已创建重试 Run: ${newRun.id}`)
    router.push(`/workspace/runs/${newRun.id}`)
  } catch (_) { /* ignore */ }
}

// ---- computed ----
const statusStep = computed(() => {
  const order = ['QUEUED', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']
  const s = run.value?.status
  if (!s) return 0
  if (s === 'COMPLETED') return 4 // show all 4 steps as done
  if (s === 'FAILED' || s === 'CANCELLED') return 3 // show RUNNING as reached
  return order.indexOf(s)
})

const terminalStepStatus = computed(() => {
  const s = run.value?.status
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED') return 'error'
  if (s === 'CANCELLED') return 'error'
  return 'process'
})

const statusDescription = computed(() => {
  const s = run.value?.status
  if (s === 'COMPLETED') return '执行成功'
  if (s === 'FAILED') return '执行失败'
  if (s === 'CANCELLED') return '已取消'
  return ''
})

const terminalReasonTitle = computed(() => {
  if (!run.value?.terminalReason) return ''
  const label = terminalReasonLabel(run.value.terminalReason)
  return `终态原因: ${label}`
})

const terminalReasonType = computed(() => {
  const r = run.value?.terminalReason
  if (!r) return 'info'
  if (r === 'USER_CANCELLED') return 'warning'
  if (r === 'NETWORK_LOST' || r === 'CREDENTIAL_REVOKED' || r === 'RUNNER_REVOKED' || r === 'SECRET_REVOKED') return 'error'
  return 'error'
})

// ---- helpers ----
function statusType(s) {
  const map = { QUEUED: 'info', LEASED: 'warning', RUNNING: '', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }
  return map[s] ?? 'info'
}

function isCancellable(s) {
  return s === 'QUEUED' || s === 'LEASED' || s === 'RUNNING'
}

function isRetryable(r) {
  return (r.status === 'FAILED' || r.status === 'CANCELLED') && r.retryCount < r.maxRetries
}

function isActive(s) {
  return s === 'QUEUED' || s === 'LEASED' || s === 'RUNNING'
}

function formatTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN')
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

function terminalReasonLabel(reason) {
  const map = {
    USER_CANCELLED: '用户主动取消',
    NETWORK_LOST: 'Runner 失联或网络中断',
    CREDENTIAL_REVOKED: 'Runner 凭据被撤销',
    SECRET_REVOKED: '关联密钥已被撤销',
    RUNNER_REVOKED: 'Runner 已被管理员撤销',
    RUNNER_FAILED: '执行过程中出错',
    LEASE_EXPIRED: 'Lease 超时未续约',
    CANCELLED: '已取消',
    SECRET_BIND_FAILED: '密钥绑定失败'
  }
  return map[reason] || reason || '—'
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
  if (s && !isActive(s)) stopPolling()
})
</script>

<style scoped lang="scss">
.run-detail-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  h2 { margin: 4px 0 0; font-size: 20px; font-weight: 600; word-break: break-all; }
  .header-actions { display: flex; gap: 8px; flex-shrink: 0; }
}

.status-card, .info-card, .timeline-card {
  margin-bottom: 16px;
}

.terminal-reason {
  margin-top: 16px;
}

.dual-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
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

.io-section {
  margin-top: 16px;
  h4 { margin: 0 0 4px; font-size: 13px; color: #909399; }
}

.io-block {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 160px;
  overflow-y: auto;
}

.mono {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}

.error-text {
  color: #f56c6c;
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

@media (max-width: 768px) {
  .dual-panel {
    grid-template-columns: 1fr;
  }
}
</style>
