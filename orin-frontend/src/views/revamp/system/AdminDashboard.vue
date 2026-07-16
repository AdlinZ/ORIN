<template>
  <div class="overview-page">
    <section class="overview-toolbar" :class="{ 'is-offline': !summary.isOnline }">
      <div class="toolbar-title">
        <div class="toolbar-kicker">
          <span class="status-dot" :class="summary.isOnline ? 'is-success' : 'is-error'" />
          <span>管理台 / 平台总览</span>
        </div>
        <h1>平台总览</h1>
        <p>基础设施、开放网关与运行态概览</p>
      </div>
      <div class="toolbar-actions">
        <span class="toolbar-status">{{ summary.isOnline ? '运行正常' : '需要关注' }}</span>
        <div class="toolbar-clock">
          <span>{{ clock.time }}</span>
          <small>{{ clock.date }}</small>
        </div>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </section>

    <section class="kpi-strip" aria-label="平台关键指标">
      <article
        v-for="metric in kpiMetrics"
        :key="metric.label"
        class="kpi-card"
        :class="`is-${metric.variant}`"
      >
        <span class="kpi-label">{{ metric.label }}</span>
        <strong class="kpi-value"><AnimatedNumber :value="metric.value" /></strong>
        <span class="kpi-caption">{{ metric.caption }}</span>
      </article>
    </section>

    <OrinAsyncState :status="summaryState.status" error-text="平台总览加载失败" @retry="loadAll">
      <div class="overview-wall">
      <div class="overview-grid">
        <!-- 左列: 平台资源 + 任务运行态 + 最近异常请求 -->
        <div class="grid-col grid-col-main">
          <!-- 平台资源 Bento -->
          <section class="panel resource-panel">
            <header class="panel-header">
              <span class="panel-title">平台资源</span>
              <span class="panel-sub">{{ resourceSummary }}</span>
            </header>
            <div class="bento-grid resource-grid">
              <article
                v-for="item in resourceTiles"
                :key="item.key"
                class="resource-tile"
                :style="{ '--tile-accent': item.color }"
              >
                <div class="resource-icon">
                  <el-icon :size="20">
                    <component :is="item.icon" />
                  </el-icon>
                </div>
                <div class="resource-body">
                  <span class="resource-label">{{ item.label }}</span>
                  <strong class="resource-value">
                    <AnimatedNumber :value="item.value" />
                  </strong>
                </div>
              </article>
            </div>
          </section>

          <!-- 任务运行态分布 -->
          <section class="panel chart-panel task-panel">
            <header class="panel-header">
              <span class="panel-title">任务运行态分布</span>
              <span class="panel-sub">共 {{ summary.metrics.totalTasks }} 个任务</span>
            </header>
            <div class="chart-body task-chart-body">
              <div class="task-pie">
                <PieChart
                  v-if="summary.metrics.totalTasks > 0"
                  :data="taskPieData"
                  height="124px"
                  :show-legend="false"
                />
                <OrinEmptyState
                  v-else
                  description="暂无任务数据"
                  :image-size="56"
                />
              </div>
              <ul class="task-legend">
                <li
                  v-for="item in taskLegend"
                  :key="item.status"
                  :class="['task-legend-row', { 'is-empty': item.count === 0 }]"
                >
                  <span class="legend-swatch" :style="{ background: item.color }" />
                  <span class="legend-label">{{ item.label }}</span>
                  <strong class="legend-value">{{ item.count }}</strong>
                </li>
              </ul>
            </div>
          </section>

          <!-- 最近异常请求 -->
          <section class="panel table-panel">
            <header class="panel-header">
              <span class="panel-title">最近异常请求</span>
              <span class="panel-sub">审计日志 · 最近 5 条</span>
            </header>
            <OrinEmptyState
              v-if="!summary.topAlertEvents?.length"
              description="暂无异常记录"
              :image-size="80"
            />
            <OrinDataTable v-else compact>
              <el-table :data="summary.topAlertEvents" stripe size="small">
                <el-table-column prop="method" label="方法" width="80" />
                <el-table-column prop="endpoint" label="接口" min-width="200" show-overflow-tooltip />
                <el-table-column prop="statusCode" label="状态" width="90">
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
                    {{ formatTime(row.createdAt) }}
                  </template>
                </el-table-column>
              </el-table>
            </OrinDataTable>
          </section>
        </div>

        <!-- 右列: 系统健康 + 用户 & API Key + 智能体类型 + 最近活动 -->
        <div class="grid-col grid-col-side">
          <!-- 系统健康 -->
          <section class="panel health-panel">
            <header class="panel-header">
              <span class="panel-title">系统健康</span>
            </header>
            <div class="health-rows">
              <article class="health-row">
                <span class="status-dot" :class="healthBackendClass" />
                <div class="health-meta">
                  <span class="health-label">Backend</span>
                  <span class="health-desc">{{ healthBackendText }}</span>
                </div>
              </article>
              <article class="health-row">
                <span class="status-dot" :class="healthAiEngineClass" />
                <div class="health-meta">
                  <span class="health-label">AI Engine</span>
                  <span class="health-desc">{{ healthAiEngineText }}</span>
                </div>
              </article>
            </div>
          </section>

          <!-- 用户 & API Key (admin only) -->
          <section v-if="hasAdminStats" class="panel admin-panel">
            <header class="panel-header">
              <span class="panel-title">用户 & API Key</span>
            </header>
            <div class="admin-grid">
              <article class="admin-tile">
                <span class="admin-label">平台用户</span>
                <strong class="admin-value">
                  <AnimatedNumber :value="summary.adminStats.totalUsers" />
                </strong>
              </article>
              <article class="admin-tile">
                <span class="admin-label">API Key</span>
                <strong class="admin-value">
                  <AnimatedNumber :value="summary.adminStats.totalApiKeys" />
                </strong>
              </article>
              <article
                class="admin-tile"
                :class="{ 'is-danger': summary.adminStats.activeAlerts > 0 }"
              >
                <span class="admin-label">触发中告警</span>
                <strong class="admin-value">
                  <AnimatedNumber :value="summary.adminStats.activeAlerts" />
                </strong>
              </article>
              <article class="admin-tile">
                <span class="admin-label">已解决告警</span>
                <strong class="admin-value">
                  <AnimatedNumber :value="summary.adminStats.resolvedAlerts" />
                </strong>
              </article>
            </div>
          </section>

          <!-- 智能体类型分布 -->
          <section class="panel chart-panel agent-panel">
            <header class="panel-header">
              <span class="panel-title">智能体类型分布</span>
              <span class="panel-sub">共 {{ summary.agentTypeTotal }} 个智能体</span>
            </header>
            <div class="chart-body">
              <PieChart
                v-if="summary.agentTypes.length > 0"
                :data="agentTypePieData"
                height="132px"
              />
              <OrinEmptyState
                v-else
                description="暂无智能体数据"
                :image-size="56"
              />
            </div>
          </section>

        </div>
      </div>

      <!-- 最近活动：横向摘要避免右栏过长 -->
      <section class="panel activity-panel">
        <header class="panel-header">
          <span class="panel-title">最近活动</span>
          <span class="panel-sub">审计日志 · 最近 {{ summary.recentActivity.length }} 条</span>
        </header>
        <OrinEmptyState
          v-if="!summary.recentActivity?.length"
          description="暂无活动记录"
          :image-size="56"
        />
        <ul v-else class="activity-list">
          <li
            v-for="item in summary.recentActivity.slice(0, 6)"
            :key="item.id"
            class="activity-row"
          >
            <span class="activity-dot" :class="item.success ? 'is-success' : 'is-error'" />
            <div class="activity-body">
              <span class="activity-endpoint">{{ item.endpoint || '-' }}</span>
              <span class="activity-meta">
                <span>{{ item.method || '-' }}</span>
                <span v-if="item.statusCode">· {{ item.statusCode }}</span>
                <span>· {{ formatTime(item.createdAt) }}</span>
              </span>
            </div>
          </li>
        </ul>
      </section>

      <!-- 趋势 -->
      <section class="panel trend-panel">
        <header class="panel-header">
          <span class="panel-title">请求与 Token 趋势</span>
          <span class="panel-sub">{{ trendRangeText }}</span>
        </header>
        <div class="trend-body">
          <OrinEmptyState
            v-if="!hasTrendData"
            description="暂无趋势数据"
            :image-size="80"
          />
          <div v-else class="trend-charts">
            <div class="trend-chart-cell">
              <span class="trend-chart-label">请求量</span>
              <LineChart
                :data="requestTrendSeries"
                color="var(--orin-primary)"
                :show-data-zoom="true"
                height="170px"
              />
            </div>
            <div class="trend-chart-cell">
              <span class="trend-chart-label">Token 消耗</span>
              <LineChart
                :data="tokenTrendSeries"
                color="var(--accent-500, #8b5cf6)"
                :show-data-zoom="true"
                height="170px"
              />
            </div>
          </div>
        </div>
      </section>

      <!-- 快速入口 -->
      <section v-if="summary.quickLinks?.length" class="panel quick-panel">
        <header class="panel-header">
          <span class="panel-title">快速入口</span>
        </header>
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
      </section>
      </div>
    </OrinAsyncState>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatLineRound,
  Connection,
  DataAnalysis,
  Document,
  Histogram,
  MagicStick,
  SetUp,
  Share,
  Tickets,
  User,
  Warning
} from '@element-plus/icons-vue'
import { Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

import AnimatedNumber from '@/components/AnimatedNumber.vue'
import LineChart from '@/components/LineChart.vue'
import PieChart from '@/components/PieChart.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'

import { getDashboardSummary } from '@/api/dashboard'
import { toDashboardSummaryViewModel } from '@/viewmodels'

const router = useRouter()
const summaryState = reactive({ status: 'loading' })
const summary = ref({})

// 实时时钟
const clock = reactive({ time: '--:--:--', date: '----年--月--日' })
let clockTimer = null

const startClock = () => {
  const update = () => {
    const now = dayjs()
    clock.time = now.format('HH:mm:ss')
    clock.date = now.format('YYYY年MM月DD日 dddd')
  }
  update()
  clockTimer = setInterval(update, 1000)
}

const stopClock = () => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
}

// KPI 数字
const kpiMetrics = computed(() => [
  {
    label: '进行中任务',
    value: summary.value.metrics?.openTasks ?? 0,
    variant: 'primary',
    caption: '当前执行队列'
  },
  {
    label: '失败任务',
    value: summary.value.metrics?.failedTasks ?? 0,
    variant: (summary.value.metrics?.failedTasks ?? 0) > 0 ? 'danger' : 'default',
    caption: '需要人工处理'
  },
  {
    label: '活动告警',
    value: summary.value.adminStats?.activeAlerts ?? 0,
    variant: (summary.value.adminStats?.activeAlerts ?? 0) > 0 ? 'warning' : 'default',
    caption: '平台风险信号'
  },
  {
    label: 'Trace',
    value: summary.value.metrics?.traces ?? 0,
    variant: 'default',
    caption: '可追踪调用'
  }
])

// 平台资源 bento tiles
const resourceTiles = computed(() => {
  const m = summary.value.metrics || {}
  return [
    { key: 'agents', label: '智能体', value: m.agents ?? 0, color: 'var(--metric-cpu-color, #3b82f6)', icon: MagicStick },
    { key: 'workflows', label: '工作流', value: m.workflows ?? 0, color: 'var(--metric-gpu-color, #8b5cf6)', icon: Share },
    { key: 'knowledge', label: '知识库', value: m.knowledgeBases ?? 0, color: 'var(--metric-memory-color, #10b981)', icon: Document },
    { key: 'collab', label: '协作包', value: m.collaborationPackages ?? 0, color: 'var(--metric-disk-color, #f59e0b)', icon: Connection },
    { key: 'traces', label: 'Trace 记录', value: m.traces ?? 0, color: 'var(--orin-primary, #0d9488)', icon: DataAnalysis }
  ]
})

const resourceSummary = computed(() => {
  const m = summary.value.metrics || {}
  const total = (m.agents ?? 0) + (m.workflows ?? 0) + (m.knowledgeBases ?? 0)
    + (m.collaborationPackages ?? 0) + (m.traces ?? 0)
  return `${total} 项资源`
})

// 任务运行态
const TASK_COLOR_BY_STATUS = {
  QUEUED: 'var(--chart-color-3, #f59e0b)',
  RUNNING: 'var(--chart-color-1, #0d9488)',
  RETRYING: 'var(--chart-color-5, #eab308)',
  COMPLETED: 'var(--chart-color-4, #10b981)',
  FAILED: 'var(--chart-color-6, #ef4444)',
  DEAD: 'var(--chart-color-6, #ef4444)',
  CANCELLED: 'var(--chart-color-2, #94a3b8)'
}

const TASK_LABEL_ZH = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  RETRYING: '重试中',
  COMPLETED: '已完成',
  FAILED: '失败',
  DEAD: '死信',
  CANCELLED: '已取消'
}

const taskLegend = computed(() => {
  const items = summary.value.metrics?.taskStatuses || []
  return items.map((item) => ({
    status: item.status,
    label: TASK_LABEL_ZH[item.status] || item.label,
    count: item.count,
    color: TASK_COLOR_BY_STATUS[item.status] || 'var(--chart-color-2, #94a3b8)'
  }))
})

const taskPieData = computed(() => {
  return taskLegend.value
    .filter((item) => item.count > 0)
    .map((item) => ({ name: item.label, value: item.count, itemStyle: { color: item.color } }))
})

// 智能体类型
const AGENT_TYPE_COLOR_KEYS = ['--chart-color-1', '--chart-color-2', '--chart-color-3', '--chart-color-4', '--chart-color-5', '--chart-color-6']

const agentTypePieData = computed(() => {
  const types = summary.value.agentTypes || []
  return types.map((item, index) => ({
    name: item.label,
    value: item.count,
    itemStyle: { color: `var(${AGENT_TYPE_COLOR_KEYS[index % AGENT_TYPE_COLOR_KEYS.length]}, #0d9488)` }
  }))
})

// 系统健康
const healthBackendClass = computed(() => {
  const status = String(summary.value.systemHealth?.backend?.status || '').toUpperCase()
  if (status === 'UP' || status === 'OK') return 'is-success'
  if (status === 'DOWN') return 'is-error'
  return 'is-warning'
})
const healthBackendText = computed(() => {
  const status = String(summary.value.systemHealth?.backend?.status || 'UNKNOWN').toUpperCase()
  return `Backend · ${status}`
})
const healthAiEngineClass = computed(() => {
  const reachable = summary.value.systemHealth?.aiEngine?.reachable === true
  return reachable ? 'is-success' : 'is-error'
})
const healthAiEngineText = computed(() => {
  const engine = summary.value.systemHealth?.aiEngine || {}
  const reachable = engine.reachable === true
  return reachable
    ? `${engine.service || 'orin-ai-engine'} · 可达`
    : `${engine.service || 'orin-ai-engine'} · 不可达`
})

const hasAdminStats = computed(() => {
  const stats = summary.value.adminStats
  return stats && (
    Number(stats.totalUsers) > 0
    || Number(stats.totalApiKeys) > 0
    || Number(stats.activeAlerts) > 0
    || Number(stats.resolvedAlerts) > 0
  )
})

// 趋势
const TREND_COLOR_REQUEST = 'var(--orin-primary, #0d9488)'
const TREND_COLOR_TOKEN = 'var(--accent-500, #8b5cf6)'

const toTimestampSeries = (points) => {
  if (!Array.isArray(points)) return []
  return points.map((point) => ({
    timestamp: dayjs(point.date).valueOf(),
    value: Number(point.value) || 0
  }))
}

const requestTrendSeries = computed(() => toTimestampSeries(summary.value.trends?.requestCount || []))
const tokenTrendSeries = computed(() => toTimestampSeries(summary.value.trends?.tokenUsage || []))

const hasTrendData = computed(() => requestTrendSeries.value.length > 0 || tokenTrendSeries.value.length > 0)

const trendRangeText = computed(() => {
  const range = summary.value.trends?.range || {}
  if (range.start && range.end) return `${range.start} ~ ${range.end}`
  return '近 14 天'
})

// 工具
const formatTime = (value) => {
  if (!value) return '-'
  const d = dayjs(typeof value === 'string' ? value : String(value))
  return d.isValid() ? d.format('MM-DD HH:mm') : '-'
}

// 数据加载
const loadSummary = async () => {
  summaryState.status = 'loading'
  try {
    const raw = await getDashboardSummary()
    summary.value = toDashboardSummaryViewModel(raw)
    summaryState.status = 'success'
  } catch (e) {
    summaryState.status = 'error'
    console.error('Failed to load dashboard summary', e)
  }
}

const loadAll = () => loadSummary()

onMounted(() => {
  loadSummary()
  startClock()
})

onBeforeUnmount(() => {
  stopClock()
})
</script>

<style scoped>
.overview-page {
  padding: var(--orin-page-gap, 18px);
  display: flex;
  flex-direction: column;
  gap: var(--orin-block-gap, 14px);
}

/* 紧凑页面工具栏 */
.overview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 58px;
  padding: 2px 0 10px;
  border-bottom: 1px solid var(--orin-border-strong, #e2e8f0);
}

.toolbar-title {
  flex: 1;
  min-width: 0;
}

.toolbar-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary, #64748b);
  font-size: 10px;
  font-weight: 600;
}

.toolbar-title h1 {
  display: block;
  margin: 3px 0 0;
  color: var(--text-primary, #1e293b);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.35;
}

.toolbar-title p {
  display: none;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-status {
  padding: 4px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--success-color, #10b981) 10%, transparent);
  color: var(--success-color, #10b981);
  font-size: 12px;
  font-weight: 650;
}

.overview-toolbar.is-offline .toolbar-status { color: var(--error-color, #ef4444); }

.toolbar-clock {
  text-align: right;
  font-variant-numeric: tabular-nums;
  padding-right: 8px;
  border-right: 1px solid var(--orin-border-strong, #e2e8f0);
}

.toolbar-clock span {
  display: block;
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.toolbar-clock small {
  display: none;
  margin-top: 1px;
  color: var(--text-secondary, #64748b);
  font-size: 11px;
}

/* KPI strip */
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.kpi-card {
  min-height: 82px;
  padding: 11px 12px;
  border: 1px solid var(--orin-border-strong, #e2e8f0);
  border-radius: 8px;
  background: var(--orin-surface, #fff);
  box-shadow: none;
}

.kpi-card::before {
  display: none;
}

.kpi-card.is-warning { border-color: rgba(245, 158, 11, 0.45); }
.kpi-card.is-danger { border-color: rgba(239, 68, 68, 0.38); }

.kpi-label,
.kpi-caption {
  display: block;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  font-weight: 650;
}

.kpi-value {
  display: block;
  margin: 5px 0 2px;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.kpi-caption { font-size: 10px; font-weight: 500; }

html.dark .kpi-value {
  color: var(--text-primary-dark, #f1f5f9);
}

html.dark .kpi-card {
  background: rgba(15, 28, 28, 0.92);
  border-color: rgba(148, 163, 184, 0.18);
}

/* 运营总览：主区聚焦趋势与异常，右栏承载健康和治理摘要。 */
.overview-wall {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 320px);
  align-items: start;
  gap: 10px;
}

.grid-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.agent-panel .chart-body {
  min-height: 132px;
  justify-content: center;
  align-items: center;
}

.agent-panel :deep(.orin-empty-state) {
  width: 100%;
  min-height: 132px;
  justify-content: center;
}

@media (max-width: 1180px) {
  .overview-grid {
    grid-template-columns: minmax(0, 1fr) minmax(260px, 300px);
  }

  .kpi-strip {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .resource-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .overview-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding-bottom: 12px;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .kpi-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-wall {
    display: flex;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

}

.panel {
  background: var(--orin-surface, #ffffff);
  border: 1px solid var(--orin-border-strong, #e2e8f0);
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-width: 0;
}

html.dark .panel {
  background: var(--neutral-gray-50, #0f1c1c);
  border-color: rgba(148, 163, 184, 0.18);
}

.panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  letter-spacing: 0.02em;
}

html.dark .panel-title {
  color: var(--text-primary-dark, #f1f5f9);
}

.panel-sub {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

/* 平台资源 bento */
.bento-grid {
  display: grid;
  gap: var(--orin-grid-gap, 14px);
}

.resource-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.resource-tile {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 68px;
  padding: 10px 11px;
  border: 1px solid var(--orin-border-strong, #e2e8f0);
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, #ffffff);
  --tile-accent: var(--orin-primary, #0d9488);
}

html.dark .resource-tile {
  background: rgba(15, 23, 42, 0.4);
  border-color: rgba(148, 163, 184, 0.18);
}

.resource-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--tile-accent) 14%, transparent);
  color: var(--tile-accent);
  flex-shrink: 0;
}

.resource-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.resource-label {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  font-weight: 600;
}

.resource-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  margin-top: 2px;
}

html.dark .resource-value {
  color: var(--text-primary-dark, #f1f5f9);
}

/* 图表区 */
.chart-body {
  display: flex;
  align-items: stretch;
  gap: 12px;
  min-height: 132px;
}

.task-chart-body {
  align-items: center;
}

.task-pie {
  flex: 0 0 180px;
  min-width: 180px;
}

.task-panel .task-chart-body {
  align-items: center;
  min-height: 124px;
}

.task-panel .task-pie {
  flex: 0 0 124px;
  align-self: center;
  min-width: 124px;
  width: 124px;
}

.task-panel .task-legend {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px 10px;
}

.task-legend {
  flex: 1;
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px 12px;
  align-self: stretch;
}

.task-legend-row {
  display: grid;
  grid-template-columns: 12px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 6px;
  font-size: 12px;
}

.task-legend-row.is-empty {
  opacity: 0.45;
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.legend-label {
  color: var(--text-primary, #1e293b);
  font-weight: 600;
}

html.dark .legend-label {
  color: var(--text-primary-dark, #f1f5f9);
}

.legend-value {
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary, #64748b);
  font-weight: 700;
}

@media (max-width: 980px) {
  .resource-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .task-panel .task-chart-body {
    align-items: stretch;
    flex-direction: column;
  }

  .task-panel .task-pie {
    align-self: center;
  }

  .task-panel .task-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 系统健康 */
.health-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.health-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-base, 8px);
  border: 1px solid var(--orin-border-strong, #e2e8f0);
}

html.dark .health-row {
  border-color: rgba(148, 163, 184, 0.18);
}

.health-meta {
  display: flex;
  flex-direction: column;
}

.health-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}

html.dark .health-label {
  color: var(--text-primary-dark, #f1f5f9);
}

.health-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

/* Admin tile grid */
.admin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 10px;
}

.admin-tile {
  padding: 12px;
  border-radius: var(--radius-base, 8px);
  border: 1px solid var(--orin-border-strong, #e2e8f0);
  background: var(--orin-surface, #ffffff);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

html.dark .admin-tile {
  background: rgba(15, 23, 42, 0.4);
  border-color: rgba(148, 163, 184, 0.18);
}

.admin-tile.is-danger {
  border-color: rgba(239, 68, 68, 0.45);
  background: rgba(239, 68, 68, 0.06);
}

.admin-label {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  font-weight: 600;
}

.admin-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  font-variant-numeric: tabular-nums;
}

html.dark .admin-value {
  color: var(--text-primary-dark, #f1f5f9);
}

.admin-tile.is-danger .admin-value {
  color: var(--error-color, #ef4444);
}

/* 活动列表 */
.activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}

.activity-row {
  display: grid;
  grid-template-columns: 12px 1fr;
  gap: 10px;
  align-items: flex-start;
  padding: 7px 0;
  min-width: 0;
}

.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
}

.activity-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.activity-endpoint {
  font-size: 13px;
  color: var(--text-primary, #1e293b);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

html.dark .activity-endpoint {
  color: var(--text-primary-dark, #f1f5f9);
}

.activity-meta {
  font-size: 11px;
  color: var(--text-secondary, #64748b);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .activity-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .activity-list {
    grid-template-columns: 1fr;
  }
}

/* 趋势图 */
.trend-body {
  min-height: 180px;
}

.trend-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .trend-charts {
    grid-template-columns: 1fr;
  }
}

.trend-chart-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.trend-chart-label {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  font-weight: 600;
}

/* 快速入口 */
.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* Status dot 复用全局 */
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  background: var(--neutral-gray-300, #cbd5e1);
}

.status-dot.is-success {
  background: var(--success-color, #10b981);
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.18);
}

.status-dot.is-error {
  background: var(--error-color, #ef4444);
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.18);
}

.status-dot.is-warning {
  background: var(--warning-color, #f59e0b);
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.18);
}
</style>
