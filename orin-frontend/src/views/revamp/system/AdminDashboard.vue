<template>
  <div class="page-container">
    <OrinPageShell
      title="平台总览"
      description="平台运行状态、用户与资源统计、异常告警与高频操作"
      icon="DataBoard"
      domain="系统设置"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="loadAll">
          刷新
        </el-button>
      </template>
    </OrinPageShell>

    <OrinMetricStrip :metrics="adminMetrics" class="stats-row" />

    <el-row :gutter="16" class="admin-grid">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>平台资源</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="智能体总数">
              {{ summary.metrics?.agents ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="工作流总数">
              {{ summary.metrics?.workflows ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="知识库总数">
              {{ summary.metrics?.knowledgeBases ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="协作任务包">
              {{ summary.metrics?.collaborationPackages ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="Trace 记录">
              {{ summary.metrics?.traces ?? 0 }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>任务状态</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="进行中任务">
              {{ summary.metrics?.openTasks ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="失败任务">
              <span :class="{ 'text-danger': (summary.metrics?.failedTasks ?? 0) > 0 }">
                {{ summary.metrics?.failedTasks ?? 0 }}
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>告警概览</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="触发中告警">
              <el-tag v-if="(summary.adminStats?.activeAlerts ?? 0) > 0" type="danger" size="small">
                {{ summary.adminStats?.activeAlerts }}
              </el-tag>
              <span v-else class="text-muted">0</span>
            </el-descriptions-item>
            <el-descriptions-item label="已解决告警">
              {{ summary.adminStats?.resolvedAlerts ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="平台用户">
              {{ summary.adminStats?.totalUsers ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="API Key 总数">
              {{ summary.adminStats?.totalApiKeys ?? 0 }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>最近异常请求</span>
              <span class="panel-sub">审计日志 · 最近 5 条</span>
            </div>
          </template>
          <OrinAsyncState :status="recentState.status" empty-text="暂无异常记录">
            <OrinDataTable compact>
              <el-table :data="summary.topAlertEvents" stripe size="small">
                <el-table-column prop="method" label="方法" width="70" />
                <el-table-column prop="endpoint" label="接口" min-width="180" show-overflow-tooltip />
                <el-table-column prop="statusCode" label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.statusCode >= 500 ? 'danger' : row.statusCode >= 400 ? 'warning' : 'info'"
                    >
                      {{ row.statusCode || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="时间" width="160">
                  <template #default="{ row }">
                    {{ row.createdAt ? formatTime(row.createdAt) : '-' }}
                  </template>
                </el-table-column>
              </el-table>
            </OrinDataTable>
          </OrinAsyncState>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>快速入口</span>
            </div>
          </template>
          <div class="quick-links">
            <el-button
              v-for="link in summary.quickLinks"
              :key="link.path"
              type="primary"
              plain
              @click="router.push(link.path)"
            >
              {{ link.title }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { getDashboardSummary } from '@/api/dashboard'
import { toDashboardSummaryViewModel } from '@/viewmodels'
import dayjs from 'dayjs'

const router = useRouter()
const summaryState = reactive({ status: 'loading' })
const summary = ref({})

const recentState = computed(() => {
  const events = summary.value.topAlertEvents
  return {
    status: Array.isArray(events) && events.length > 0 ? 'success' : 'empty',
    error: null
  }
})

const adminMetrics = computed(() => [
  {
    label: '平台用户',
    value: summary.value.adminStats?.totalUsers ?? 0,
    meta: '系统账户'
  },
  {
    label: 'API Key',
    value: summary.value.adminStats?.totalApiKeys ?? 0,
    meta: '访问密钥'
  },
  {
    label: '触发中告警',
    value: summary.value.adminStats?.activeAlerts ?? 0,
    meta: '需关注',
    intent: (summary.value.adminStats?.activeAlerts ?? 0) > 0 ? 'danger' : undefined
  },
  {
    label: '进行中任务',
    value: summary.value.metrics?.openTasks ?? 0,
    meta: '当前队列'
  }
])

const loadSummary = async () => {
  summaryState.status = 'loading'
  try {
    const raw = await getDashboardSummary()
    summary.value = toDashboardSummaryViewModel(raw)
    summaryState.status = 'success'
  } catch (e) {
    summaryState.status = 'error'
    ElMessage.error('平台总览加载失败')
  }
}

const loadAll = () => {
  loadSummary()
}

const formatTime = (value) => {
  if (!value) return '-'
  const t = typeof value === 'string' ? value : String(value)
  const d = dayjs(t)
  return d.isValid() ? d.format('MM-DD HH:mm') : '-'
}

onMounted(() => {
  loadSummary()
})
</script>

<style scoped>
.page-container {
  padding: 16px;
}

.stats-row {
  margin-bottom: 16px;
}

.admin-grid {
  margin-bottom: 16px;
}

.panel-card {
  height: 100%;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-sub {
  font-size: 12px;
  color: #64748b;
  font-weight: normal;
}

.text-danger {
  color: #ef4444;
}

.text-muted {
  color: #64748b;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
