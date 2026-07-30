<template>
  <main class="runner-list-page">
    <header class="page-header">
      <div>
        <h2>执行节点</h2>
        <p class="subtitle">Runner 负责真正执行 Agent 任务；至少需要一个可运行节点。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showEnrollDialog = true">
        接入 Runner
      </el-button>
    </header>

    <el-alert
      v-if="!loading && !readiness.ready"
      class="runner-blocker"
      type="warning"
      :closable="false"
      show-icon
      title="当前没有可运行的 Runner"
    >
      <template #default>
        <div class="blocker-content">
          <span>
            {{ returningToRun
              ? '完成接入后会带你回到运行创建页。'
              : '新的运行会停留在排队状态，先完成一个执行节点的接入。' }}
          </span>
          <el-button size="small" type="primary" @click="showEnrollDialog = true">
            现在接入
          </el-button>
        </div>
      </template>
    </el-alert>

    <section class="readiness-strip" aria-label="Runner 可用性概览">
      <div>
        <span>可运行</span>
        <strong class="ready-count">{{ readiness.online }}</strong>
        <small>可以接收新任务</small>
      </div>
      <div>
        <span>需要处理</span>
        <strong>{{ readiness.needsAttention }}</strong>
        <small>离线或运行环境异常</small>
      </div>
      <div>
        <span>暂停接单</span>
        <strong>{{ readiness.paused }}</strong>
        <small>等待恢复后再接任务</small>
      </div>
    </section>

    <OrinDataTable
      title="已接入节点"
      :description="total > 0 ? `共 ${total} 个，按最近连接排序` : '完成接入后会显示在这里'"
    >
      <ResizableTable
        :data="runners"
        v-loading="loading"
        stripe
        border
        :row-style="{ cursor: 'pointer' }"
        empty-text="还没有执行节点"
        @row-click="goDetail"
      >
        <el-table-column label="执行节点" min-width="220">
          <template #default="{ row }">
            <div class="runner-identity">
              <strong>{{ row.name }}</strong>
              <small>{{ row.hostname || '等待上报主机信息' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可用状态" min-width="225">
          <template #default="{ row }">
            <div class="runner-status">
              <el-tag :type="runnerStatus(row).type" size="small">
                {{ runnerStatus(row).label }}
              </el-tag>
              <small>{{ runnerStatus(row).description }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="任务负载" width="145" align="center">
          <template #default="{ row }">
            <div class="load-cell">
              <strong>{{ row.activeRuns || 0 }} / {{ row.maxConcurrency || '—' }}</strong>
              <small>{{ row.queuedRuns ? `${row.queuedRuns} 个等待中` : '当前运行 / 并发上限' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近连接" width="145" align="center">
          <template #default="{ row }">
            <span :class="heartbeatClass(row)">
              {{ formatAge(row.lastHeartbeatAgeSec) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="下一步" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="goDetail(row)">
              {{ row.status === 'OFFLINE' ? '查看恢复方式' : '查看' }}
            </el-button>
            <el-button
              v-if="row.status === 'ONLINE' || row.status === 'DEGRADED'"
              link
              type="warning"
              size="small"
              @click.stop="handleDrain(row)"
            >
              暂停接新任务
            </el-button>
            <el-button
              v-if="row.status === 'DRAINING' || row.drainRequested"
              link
              type="success"
              size="small"
              @click.stop="handleRestore(row)"
            >
              恢复接单
            </el-button>
          </template>
        </el-table-column>
      </ResizableTable>

      <template v-if="total > size" #footer>
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchList"
        />
      </template>
    </OrinDataTable>

    <EnrollmentWizard
      v-model:visible="showEnrollDialog"
      :returning-to-run="returningToRun"
      @enrolled="fetchList"
      @ready="handleRunnerReady"
    />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listRunners, drainRunner, restoreRunner } from '@/api/runner'
import { ROUTES } from '@/router/routes'
import {
  getRunnerReadiness,
  getRunnerStatusMeta,
} from '@/views/workspace/coreLoopPresentation'
import EnrollmentWizard from './EnrollmentWizard.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const runners = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const showEnrollDialog = ref(false)

const readiness = computed(() => getRunnerReadiness(runners.value))
const returningToRun = computed(() => route.query.returnTo === 'run')
const runnerStatus = (row) => getRunnerStatusMeta(row.status)

function heartbeatClass(row) {
  if (row.status === 'OFFLINE') return 'text-danger'
  if (row.lastHeartbeatAgeSec == null) return 'text-muted'
  if (row.lastHeartbeatAgeSec > 30) return 'text-warning'
  return 'text-success'
}

function formatAge(sec) {
  if (sec == null) return '尚未连接'
  if (sec < 10) return '刚刚'
  if (sec < 60) return `${Math.floor(sec)} 秒前`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return `${Math.floor(sec / 86400)} 天前`
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listRunners({ page: page.value - 1, size: size.value })
    const data = res?.data ?? res
    runners.value = Array.isArray(data?.content) ? data.content : (Array.isArray(data) ? data : [])
    total.value = data?.totalElements ?? runners.value.length
  } catch {
    ElMessage.error('执行节点列表加载失败')
  } finally {
    loading.value = false
  }
}

function goDetail(row) {
  router.push(ROUTES.WORKSPACE.RUNNER_DETAIL.replace(':runnerId', row.id))
}

async function handleDrain(row) {
  try {
    await ElMessageBox.confirm(
      `暂停后，“${row.name}”不会领取新任务；正在运行的任务会继续完成。`,
      '暂停接收新任务',
      {
        type: 'warning',
        confirmButtonText: '确认暂停',
        cancelButtonText: '取消',
      }
    )
    await drainRunner(row.id)
    await fetchList()
    ElMessage.success('已暂停接收新任务')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('暂停失败')
  }
}

async function handleRestore(row) {
  try {
    const res = await restoreRunner(row.id)
    const restored = res?.data ?? res
    ElMessage.success(
      restored?.status === 'OFFLINE'
        ? '已允许接单，等待 Runner 重新连接'
        : '已恢复接收新任务'
    )
    await fetchList()
  } catch {
    ElMessage.error('恢复失败')
  }
}

function handleRunnerReady() {
  const query = { create: '1' }
  if (route.query.agentId) query.agentId = String(route.query.agentId)
  if (route.query.versionId) query.versionId = String(route.query.versionId)
  router.push({ path: ROUTES.WORKSPACE.RUNS, query })
}

onMounted(fetchList)
</script>

<style scoped>
.runner-list-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.runner-blocker {
  margin-bottom: 16px;
}

.blocker-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.readiness-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 16px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.readiness-strip > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 16px;
  padding: 16px 20px;
}

.readiness-strip > div + div {
  border-left: 1px solid var(--orin-border-strong, #d8e0e8);
}

.readiness-strip span,
.readiness-strip small {
  color: var(--el-text-color-secondary);
}

.readiness-strip strong {
  grid-row: 1 / 3;
  grid-column: 2;
  align-self: center;
  font-size: 24px;
}

.readiness-strip .ready-count {
  color: var(--el-color-success);
}

.runner-identity,
.runner-status,
.load-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.runner-identity small,
.runner-status small,
.load-cell small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.runner-status {
  align-items: flex-start;
}

.load-cell {
  align-items: center;
}

.text-success { color: var(--el-color-success); }
.text-warning { color: var(--el-color-warning); }
.text-danger { color: var(--el-color-danger); }
.text-muted { color: var(--el-text-color-placeholder); }

@media (max-width: 800px) {
  .runner-list-page { padding: 16px; }
  .readiness-strip { grid-template-columns: 1fr; }
  .readiness-strip > div + div {
    border-left: 0;
    border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  }
  .blocker-content { align-items: flex-start; flex-direction: column; }
}
</style>
