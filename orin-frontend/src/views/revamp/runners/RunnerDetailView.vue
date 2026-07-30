<template>
  <main class="runner-detail-page" v-loading="loading">
    <header class="page-header">
      <el-button link :icon="ArrowLeft" @click="router.push(ROUTES.WORKSPACE.RUNNERS)">
        返回执行节点
      </el-button>
      <template v-if="runner">
        <div class="runner-heading">
          <div>
            <h2>{{ runner.name }}</h2>
            <p>{{ runner.hostname || '主机信息尚未上报' }}</p>
          </div>
          <el-tag :type="statusMeta.type" size="large">{{ statusMeta.label }}</el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="canDrain" type="warning" plain @click="handleDrain">
            暂停接收新任务
          </el-button>
          <el-button v-if="canRestore" type="success" plain @click="handleRestore">
            恢复接单
          </el-button>
          <el-button
            v-if="runner.status !== 'REVOKED'"
            type="danger"
            plain
            class="revoke-button"
            @click="handleRevoke"
          >
            永久撤销接入
          </el-button>
        </div>
      </template>
    </header>

    <template v-if="runner">
      <section class="availability-card" :class="`is-${statusMeta.type}`">
        <div class="availability-copy">
          <span class="eyebrow">当前可用性</span>
          <h1>{{ availabilityTitle }}</h1>
          <p>{{ statusMeta.description }}</p>
        </div>
        <div class="connection-summary">
          <span>最近连接</span>
          <strong>{{ formatAge(runner.lastHeartbeatAgeSec) }}</strong>
          <small>{{ dependencyMeta.label }}</small>
        </div>
      </section>

      <el-alert
        v-if="runner.status === 'OFFLINE'"
        class="recovery-alert"
        type="warning"
        :closable="false"
        show-icon
        title="让原 Runner 进程重新连接即可恢复"
      >
        <template #default>
          <div class="recovery-content">
            <span>如果本地凭据仍在，Python 运行 <code>orin-runner resume</code>；Docker 使用原凭据卷重新启动容器。</span>
            <el-button size="small" type="primary" @click="diagnosticPanels = ['technical']">
              查看接入信息
            </el-button>
          </div>
        </template>
      </el-alert>

      <el-alert
        v-else-if="runner.status === 'REVOKED'"
        class="recovery-alert"
        type="info"
        :closable="false"
        show-icon
        title="这个接入已永久撤销"
        description="原凭据不能恢复。需要执行任务时，请返回列表并用新的节点名称重新接入。"
      />

      <section class="workload-strip" aria-label="Runner 工作负载">
        <div>
          <span>正在运行</span>
          <strong>{{ runner.activeRuns || 0 }}</strong>
          <small>个任务</small>
        </div>
        <div>
          <span>等待执行</span>
          <strong>{{ runner.queuedRuns || 0 }}</strong>
          <small>个任务</small>
        </div>
        <div>
          <span>并发上限</span>
          <strong>{{ runner.maxConcurrency || 1 }}</strong>
          <small>个任务</small>
        </div>
      </section>

      <section class="detail-grid">
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-heading">
              <strong>运行环境</strong>
              <el-tag :type="dependencyMeta.type" effect="plain" size="small">
                {{ dependencyMeta.label }}
              </el-tag>
            </div>
          </template>
          <dl class="info-list">
            <div>
              <dt>操作系统</dt>
              <dd>{{ environmentLabel }}</dd>
            </div>
            <div>
              <dt>Runner 版本</dt>
              <dd>{{ runner.version || '尚未上报' }}</dd>
            </div>
            <div>
              <dt>处理器</dt>
              <dd>{{ runner.cpuCores ? `${runner.cpuCores} 核` : '尚未上报' }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-heading">
              <strong>最近资源使用</strong>
              <small>{{ latestSnapshot ? formatTime(latestSnapshot.reportedAt) : '暂无上报' }}</small>
            </div>
          </template>
          <div v-if="latestSnapshot" class="resource-list">
            <div>
              <span>CPU</span>
              <strong>{{ percentValue(latestSnapshot.cpuUsage) }}</strong>
              <el-progress :percentage="safePercentage(latestSnapshot.cpuUsage)" :show-text="false" />
            </div>
            <div>
              <span>内存</span>
              <strong>{{ usageLabel(latestSnapshot.memoryUsed, latestSnapshot.memoryTotal) }}</strong>
              <el-progress
                :percentage="usagePercentage(latestSnapshot.memoryUsed, latestSnapshot.memoryTotal)"
                :show-text="false"
              />
            </div>
            <div>
              <span>磁盘</span>
              <strong>{{ usageLabel(latestSnapshot.diskUsed, latestSnapshot.diskTotal) }}</strong>
              <el-progress
                :percentage="usagePercentage(latestSnapshot.diskUsed, latestSnapshot.diskTotal)"
                :show-text="false"
              />
            </div>
          </div>
          <el-empty v-else description="连接后会开始上报资源使用" :image-size="56" />
        </el-card>
      </section>

      <el-collapse v-model="diagnosticPanels" class="diagnostics">
        <el-collapse-item name="technical">
          <template #title>
            <div class="diagnostic-title">
              <strong>诊断与接入信息</strong>
              <span>内部标识、凭据摘要和最近心跳</span>
            </div>
          </template>

          <el-descriptions :column="2" border size="small" class="technical-info">
            <el-descriptions-item label="Runner ID">{{ runner.id }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(runner.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="系统架构">{{ runner.arch || '—' }}</el-descriptions-item>
            <el-descriptions-item label="凭据状态">
              {{ credentialStateLabel }}
            </el-descriptions-item>
            <el-descriptions-item v-if="runner.credential" label="凭据摘要">
              {{ credentialSummary }}
            </el-descriptions-item>
            <el-descriptions-item v-if="runner.credential" label="Credential ID">
              {{ runner.credential.credentialId }}
            </el-descriptions-item>
          </el-descriptions>

          <OrinDataTable
            class="heartbeat-table"
            title="最近连接记录"
            description="仅用于判断节点连接和资源上报是否稳定"
          >
            <ResizableTable
              :data="runner.recentSnapshots || []"
              size="small"
              border
              empty-text="暂无连接记录"
            >
              <el-table-column label="上报时间" width="180">
                <template #default="{ row }">{{ formatTime(row.reportedAt) }}</template>
              </el-table-column>
              <el-table-column label="CPU" width="100" align="center">
                <template #default="{ row }">{{ percentValue(row.cpuUsage) }}</template>
              </el-table-column>
              <el-table-column label="内存使用" min-width="170">
                <template #default="{ row }">
                  {{ usageLabel(row.memoryUsed, row.memoryTotal) }}
                </template>
              </el-table-column>
              <el-table-column label="磁盘使用" min-width="170">
                <template #default="{ row }">
                  {{ usageLabel(row.diskUsed, row.diskTotal) }}
                </template>
              </el-table-column>
              <el-table-column label="运行环境" min-width="130">
                <template #default="{ row }">
                  <el-tag :type="getDependencyHealthMeta(row.dependencyHealth).type" size="small">
                    {{ getDependencyHealthMeta(row.dependencyHealth).label }}
                  </el-tag>
                </template>
              </el-table-column>
            </ResizableTable>
          </OrinDataTable>
        </el-collapse-item>
      </el-collapse>
    </template>

    <el-empty v-if="!runner && !loading" description="找不到这个执行节点" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getRunnerDetail, drainRunner, restoreRunner, revokeRunner } from '@/api/runner'
import { getRunnerStatusMeta } from '@/views/workspace/coreLoopPresentation'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const runner = ref(null)
const diagnosticPanels = ref([])

const latestSnapshot = computed(() => runner.value?.latestSnapshot || null)
const statusMeta = computed(() => getRunnerStatusMeta(runner.value?.status))
const canDrain = computed(() => ['ONLINE', 'DEGRADED'].includes(runner.value?.status))
const canRestore = computed(() => (
  runner.value?.status === 'DRAINING' || Boolean(runner.value?.drainRequested)
))
const availabilityTitle = computed(() => {
  const titles = {
    ONLINE: '这个节点可以接收新任务',
    DEGRADED: '这个节点需要检查后再持续运行',
    DRAINING: '这个节点已暂停接收新任务',
    OFFLINE: '这个节点当前无法执行任务',
    REVOKED: '这个节点已退出 ORIN',
    ENROLLING: '这个节点正在完成首次连接',
    NEW: '这个节点还没有建立连接',
  }
  return titles[runner.value?.status] || '正在确认节点可用性'
})
const environmentLabel = computed(() => {
  const parts = [runner.value?.os, runner.value?.arch].filter(Boolean)
  return parts.join(' · ') || '尚未上报'
})
const dependencyMeta = computed(() => getDependencyHealthMeta(runner.value?.lastDependencyHealth))
const credentialStateLabel = computed(() => {
  const state = runner.value?.credential?.status
  if (!runner.value?.credential) return '未提供'
  if (state === 'ACTIVE') return '有效'
  if (state === 'REVOKED') return '已撤销'
  return state || '状态未知'
})
const credentialSummary = computed(() => {
  const credential = runner.value?.credential
  if (!credential) return '—'
  return `${credential.keyPrefix || 'runner'}••••${credential.last4 || '—'}`
})

function getDependencyHealthMeta(status) {
  const states = {
    HEALTHY: { label: '运行环境正常', type: 'success' },
    DEGRADED: { label: '运行环境需检查', type: 'warning' },
    UNHEALTHY: { label: '运行环境异常', type: 'danger' },
    FAILED: { label: '运行环境异常', type: 'danger' },
  }
  return states[status] || { label: '尚未检查运行环境', type: 'info' }
}

function formatAge(sec) {
  if (sec == null) return '尚未连接'
  if (sec < 10) return '刚刚'
  if (sec < 60) return `${Math.floor(sec)} 秒前`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return `${Math.floor(sec / 86400)} 天前`
}

function formatBytes(bytes) {
  if (!Number.isFinite(Number(bytes))) return '—'
  const value = Number(bytes)
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let scaled = value / 1024
  let index = 0
  while (scaled >= 1024 && index < units.length - 1) {
    scaled /= 1024
    index += 1
  }
  return `${scaled.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

function formatTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN')
}

function safePercentage(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.min(100, Math.max(0, Math.round(number)))
}

function usagePercentage(used, total) {
  if (!Number(total) || !Number.isFinite(Number(used))) return 0
  return safePercentage((Number(used) / Number(total)) * 100)
}

function percentValue(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(1)}%` : '—'
}

function usageLabel(used, total) {
  return `${formatBytes(used)} / ${formatBytes(total)}`
}

async function fetchDetail() {
  const id = route.params.runnerId || route.params.serverId || route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getRunnerDetail(id)
    runner.value = res?.data ?? res
  } catch {
    ElMessage.error('执行节点详情加载失败')
  } finally {
    loading.value = false
  }
}

async function handleDrain() {
  try {
    await ElMessageBox.confirm(
      `暂停后，“${runner.value.name}”不会领取新任务；正在运行的任务会继续完成。`,
      '暂停接收新任务',
      {
        type: 'warning',
        confirmButtonText: '确认暂停',
        cancelButtonText: '取消',
      }
    )
    await drainRunner(runner.value.id)
    await fetchDetail()
    ElMessage.success('已暂停接收新任务')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('暂停失败')
  }
}

async function handleRestore() {
  try {
    const res = await restoreRunner(runner.value.id)
    const restored = res?.data ?? res
    ElMessage.success(
      restored?.status === 'OFFLINE'
        ? '已允许接单，等待 Runner 重新连接'
        : '已恢复接收新任务'
    )
    await fetchDetail()
  } catch {
    ElMessage.error('恢复失败')
  }
}

async function handleRevoke() {
  try {
    await ElMessageBox.confirm(
      `撤销后，“${runner.value.name}”的凭据将永久失效，且不能恢复。`,
      '永久撤销接入',
      {
        type: 'error',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
      }
    )
    await revokeRunner(runner.value.id)
    await fetchDetail()
    ElMessage.success('Runner 接入已撤销')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('撤销失败')
  }
}

onMounted(fetchDetail)
</script>

<style scoped>
.runner-detail-page {
  max-width: 1240px;
  padding: 24px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.runner-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.runner-heading h2 {
  margin: 0;
  font-size: 20px;
}

.runner-heading p {
  margin: 3px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.revoke-button {
  color: var(--el-color-danger) !important;
  border-color: var(--el-color-danger) !important;
  background: transparent !important;
}

.revoke-button:hover {
  color: #fff !important;
  background: var(--el-color-danger) !important;
}

.availability-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 24px 28px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color);
  border-left: 4px solid var(--el-color-info);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.availability-card.is-success { border-left-color: var(--el-color-success); }
.availability-card.is-warning { border-left-color: var(--el-color-warning); }
.availability-card.is-danger { border-left-color: var(--el-color-danger); }

.eyebrow {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
}

.availability-copy h1 {
  margin: 6px 0;
  font-size: 22px;
}

.availability-copy p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.connection-summary {
  display: flex;
  flex-direction: column;
  min-width: 170px;
  padding-left: 24px;
  border-left: 1px solid var(--el-border-color);
}

.connection-summary span,
.connection-summary small {
  color: var(--el-text-color-secondary);
}

.connection-summary strong {
  margin: 4px 0;
  font-size: 20px;
}

.recovery-alert {
  margin-bottom: 16px;
}

.recovery-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.recovery-content code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color);
}

.workload-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.workload-strip > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 16px 20px;
}

.workload-strip > div + div {
  border-left: 1px solid var(--el-border-color);
}

.workload-strip span,
.workload-strip small {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.workload-strip strong {
  margin-left: auto;
  font-size: 22px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.detail-card {
  min-height: 260px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-heading small {
  color: var(--el-text-color-secondary);
}

.info-list {
  margin: 0;
}

.info-list > div {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 13px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.info-list dt {
  color: var(--el-text-color-secondary);
}

.info-list dd {
  margin: 0;
  font-weight: 600;
}

.resource-list {
  display: grid;
  gap: 22px;
}

.resource-list > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px 16px;
}

.resource-list span {
  color: var(--el-text-color-secondary);
}

.resource-list .el-progress {
  grid-column: 1 / 3;
}

.diagnostics {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.diagnostics :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 62px;
  padding: 0 18px;
  border-radius: 8px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.diagnostics :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  background: var(--el-bg-color);
}

.diagnostics :deep(.el-collapse-item__content) {
  padding: 0 18px 18px;
}

.diagnostic-title {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.diagnostic-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.technical-info {
  margin-bottom: 16px;
}

.technical-info :deep(.el-descriptions__body),
.technical-info :deep(.el-descriptions__table),
.technical-info :deep(.el-descriptions__content.el-descriptions__cell.is-bordered-content) {
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
}

.technical-info :deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

@media (max-width: 820px) {
  .runner-detail-page { padding: 16px; }
  .page-header { align-items: flex-start; flex-wrap: wrap; }
  .header-actions { width: 100%; margin-left: 0; }
  .availability-card { align-items: flex-start; flex-direction: column; }
  .connection-summary {
    width: 100%;
    padding: 16px 0 0;
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
  .workload-strip,
  .detail-grid { grid-template-columns: 1fr; }
  .workload-strip > div + div {
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
  .recovery-content { align-items: flex-start; flex-direction: column; }
}
</style>
