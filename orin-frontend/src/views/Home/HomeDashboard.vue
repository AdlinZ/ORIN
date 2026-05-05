<template>
  <div class="dashboard-home command-center-root">
    <header class="page-header cc-header-glass">
      <!-- 1. 品牌区 -->
      <div class="header-brand">
        <h1 class="logo-text">ORIN 企业 AI 中枢</h1>
        <span class="header-divider"></span>
        <p class="header-subtitle">运营总览、服务健康、调用趋势与待处理异常</p>
      </div>

      <!-- 2. 状态区 -->
      <div class="header-status">
        <div class="status-core" :class="healthStatusClass">
          <span class="status-dot"></span>
          <span class="status-text">
            状态：{{ healthStatusText }} · 检测 {{ lastHealthCheck }} · 刷新 {{ refreshAgoText }}
          </span>
        </div>
      </div>

      <!-- 3. 操作区 -->
      <div class="header-actions">
        <el-button type="primary" :loading="isRefreshing" @click="loadDashboardData" round>
          {{ isRefreshing ? '刷新中...' : '刷新数据' }}
        </el-button>
      </div>
    </header>

    <!-- KPI 总览行 -->
    <section class="kpi-overview">
      <div class="kpi-grid kpi-grid-primary">
        <article class="kpi-card featured">
          <span class="kpi-label">活跃智能体数</span>
          <span class="kpi-value">{{ kpi.activeAgents }}</span>
          <span class="kpi-meta">{{ formatDelta(kpi.activeAgentsDelta, '较昨日') }}</span>
        </article>
        <article class="kpi-card featured">
          <span class="kpi-label">今日调用量</span>
          <span class="kpi-value">{{ kpi.todayCalls }}</span>
          <span class="kpi-meta">{{ formatDelta(kpi.callsDelta, '较昨日') }}</span>
        </article>
        <article class="kpi-card featured">
          <span class="kpi-label">平均响应时延</span>
          <span class="kpi-value">{{ kpi.avgLatency }}</span>
          <span class="kpi-meta">{{ kpi.latencyNote }}</span>
        </article>
        <article class="kpi-card featured" :class="{ 'kpi-danger': kpi.alertCount > 0 }">
          <span class="kpi-label">异常告警数</span>
          <span class="kpi-value">{{ kpi.alertCount }}</span>
          <span class="kpi-meta">{{ formatDelta(kpi.alertDelta, '较昨日') }}</span>
        </article>
      </div>
    </section>

    <!-- 中间两列：趋势图 + 节点活跃分析 -->
    <section class="middle-section">
      <!-- 左侧：趋势与成本 -->
      <div class="middle-col-main">
        <el-card class="panel-card panel-trend" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>调用趋势</span>
              <span class="panel-sub">单位：请求次数</span>
            </div>
          </template>
          <div v-if="trendData.length" class="trend-panel">
            <div class="line-chart-shell">
              <div class="line-y-axis">
                <span v-for="tick in trendGridTicks" :key="`trend-y-${tick.y}`">{{ tick.label }}</span>
              </div>
              <div class="line-main">
                <svg viewBox="0 0 700 220" preserveAspectRatio="none" class="trend-svg">
                  <line
                    v-for="tick in trendGridTicks"
                    :key="`trend-grid-${tick.y}`"
                    :x1="40" :x2="660"
                    :y1="tick.y" :y2="tick.y"
                    class="trend-grid-line"
                  />
                  <polygon class="trend-area" :points="trendAreaPoints" />
                  <polyline class="trend-line" :points="trendPoints" />
                  <circle
                    v-for="point in chartPoints"
                    :key="point.label"
                    :cx="point.x" :cy="point.y"
                    r="4" class="trend-dot"
                  />
                </svg>
                <div class="trend-axis">
                  <span v-for="item in trendData" :key="item.label">{{ item.label }}</span>
                </div>
              </div>
            </div>
          </div>
          <OrinEmptyState v-else description="暂无调用趋势数据" />
        </el-card>

        <el-card class="panel-card panel-trend" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>响应时延</span>
              <span class="panel-sub">单位：ms（近 7 天）</span>
            </div>
          </template>
          <div v-if="latencyTrendData.length" class="trend-panel">
            <div class="line-chart-shell">
              <div class="line-y-axis">
                <span v-for="tick in latencyGridTicks" :key="`latency-y-${tick.y}`">{{ tick.label }}</span>
              </div>
              <div class="line-main">
                <svg viewBox="0 0 700 220" preserveAspectRatio="none" class="trend-svg">
                  <line
                    v-for="tick in latencyGridTicks"
                    :key="`latency-grid-${tick.y}`"
                    :x1="40" :x2="660"
                    :y1="tick.y" :y2="tick.y"
                    class="trend-grid-line"
                  />
                  <polygon class="trend-area latency" :points="latencyAreaPoints" />
                  <polyline class="trend-line latency" :points="latencyTrendPoints" />
                  <circle
                    v-for="point in latencyChartPoints"
                    :key="point.label"
                    :cx="point.x" :cy="point.y"
                    r="4" class="trend-dot latency"
                  />
                </svg>
                <div class="trend-axis">
                  <span v-for="item in latencyTrendData" :key="item.label">{{ item.label }}</span>
                </div>
              </div>
            </div>
          </div>
          <OrinEmptyState v-else :description="todayCostEmptyDescription" />
        </el-card>
      </div>

      <!-- 右侧：节点健康 + 平台态势 -->
      <div class="middle-col-side">
        <el-card class="panel-card panel-main" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>节点健康概览</span>
              <span class="panel-sub">共 {{ totalNodesCount }} 个节点</span>
            </div>
          </template>
          <div class="node-summary">
            <div class="node-stat ok">
              <span class="node-stat-label">在线</span>
              <strong class="node-stat-value">{{ nodeStatusSummary.online }}</strong>
            </div>
            <div class="node-stat warn">
              <span class="node-stat-label">告警</span>
              <strong class="node-stat-value">{{ nodeStatusSummary.warning }}</strong>
            </div>
            <div class="node-stat danger">
              <span class="node-stat-label">离线</span>
              <strong class="node-stat-value">{{ nodeStatusSummary.offline }}</strong>
            </div>
          </div>
          <ul v-if="nodePreviewList.length" class="node-list">
            <li v-for="node in nodePreviewList" :key="node.id">
              <span class="node-id">{{ node.id }}</span>
              <span class="node-status" :class="node.statusClass">{{ node.statusText }}</span>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无节点状态数据" />
          <div class="node-load-block">
            <div class="node-load-head">
              <span>节点负载</span>
              <span class="panel-sub">节点：{{ snapshotNodeId }} · 采样：{{ snapshotTimeText }}</span>
            </div>
            <template v-if="hasHardwareMetrics">
              <div class="load-grid">
                <div class="load-tile">
                  <div class="tile-header">
                    <span>CPU</span>
                    <strong>{{ safeNumber(hardware.cpuUsage).toFixed(1) }}%</strong>
                  </div>
                  <div class="tile-rail">
                    <el-tooltip :content="`CPU: ${safeNumber(hardware.cpuUsage).toFixed(1)}%`" placement="top">
                      <div class="tile-fill cpu" :class="getTileThresholdClass(hardware.cpuUsage)"
                        :style="{ width: `${safeNumber(hardware.cpuUsage)}%`, background: getTileFillGradient(hardware.cpuUsage) }">
                      </div>
                    </el-tooltip>
                  </div>
                </div>
                <div class="load-tile">
                  <div class="tile-header">
                    <span>GPU</span>
                    <strong>{{ safeNumber(hardware.gpuUsage).toFixed(1) }}%</strong>
                  </div>
                  <div class="tile-rail">
                    <el-tooltip :content="`GPU: ${safeNumber(hardware.gpuUsage).toFixed(1)}%`" placement="top">
                      <div class="tile-fill gpu" :class="getTileThresholdClass(hardware.gpuUsage)"
                        :style="{ width: `${safeNumber(hardware.gpuUsage)}%`, background: getTileFillGradient(hardware.gpuUsage) }">
                      </div>
                    </el-tooltip>
                  </div>
                </div>
                <div class="load-tile">
                  <div class="tile-header">
                    <span>内存</span>
                    <strong>{{ safeNumber(hardware.memoryUsage).toFixed(1) }}%</strong>
                  </div>
                  <div class="tile-rail">
                    <el-tooltip :content="`内存: ${safeNumber(hardware.memoryUsage).toFixed(1)}%`" placement="top">
                      <div class="tile-fill memory" :class="getTileThresholdClass(hardware.memoryUsage)"
                        :style="{ width: `${safeNumber(hardware.memoryUsage)}%`, background: getTileFillGradient(hardware.memoryUsage) }">
                      </div>
                    </el-tooltip>
                  </div>
                </div>
                <div class="load-tile">
                  <div class="tile-header">
                    <span>磁盘</span>
                    <strong>{{ safeNumber(hardware.diskUsage).toFixed(1) }}%</strong>
                  </div>
                  <div class="tile-rail">
                    <el-tooltip :content="`磁盘: ${safeNumber(hardware.diskUsage).toFixed(1)}%`" placement="top">
                      <div class="tile-fill disk" :class="getTileThresholdClass(hardware.diskUsage)"
                        :style="{ width: `${safeNumber(hardware.diskUsage)}%`, background: getTileFillGradient(hardware.diskUsage) }">
                      </div>
                    </el-tooltip>
                  </div>
                </div>
              </div>
            </template>
            <OrinEmptyState v-else description="当前节点暂无硬件监控数据" :image-size="56" />
          </div>
        </el-card>

      </div>
    </section>

    <!-- 底部三列 -->
    <section class="bottom-section">
      <!-- 成本与负载 -->
      <div class="bottom-col">
        <el-card class="panel-card panel-medium" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>今日成本趋势</span>
              <span class="panel-sub">按小时（¥）</span>
            </div>
          </template>
          <div v-if="hasTodayCostBreakdownData" class="cost-trend">
            <div class="cost-bars">
              <div v-for="item in todayCostTrend" :key="item.hour" class="cost-bar-item">
                <div class="cost-bar-rail">
                  <div class="cost-bar-fill" :style="{ height: `${item.height}%` }" />
                </div>
                <span class="cost-hour">{{ item.hour }}</span>
              </div>
            </div>
            <div class="cost-total">今日累计成本：¥{{ todayCostTotal.toFixed(2) }}</div>
          </div>
          <OrinEmptyState v-else :description="todayCostEmptyDescription" />
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>资产与运行负载</span>
              <span class="panel-sub">沿用原首页核心卡片内容</span>
            </div>
          </template>
          <div class="asset-load-wrapper">
            <div class="asset-card primary">
              <div class="asset-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px;">
                  <ellipse cx="12" cy="5" rx="9" ry="3"></ellipse>
                  <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path>
                  <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path>
                </svg>
              </div>
              <div class="asset-info">
                <span class="asset-label">知识库数量</span>
                <strong class="asset-value">{{ summaryData.total_knowledge || 0 }}</strong>
              </div>
              <div class="asset-divider"></div>
              <div class="asset-info">
                <span class="asset-label">文档总数</span>
                <strong class="asset-value">{{ summaryData.total_documents || 0 }}</strong>
              </div>
            </div>
            <div class="asset-card secondary">
              <div class="asset-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px;">
                  <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
                  <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
                </svg>
              </div>
              <div class="asset-info">
                <span class="asset-label">今日消耗 Token</span>
                <strong class="asset-value">{{ formatK(summaryData.total_tokens || 0) }}</strong>
              </div>
              <div class="asset-divider"></div>
              <div class="asset-info">
                <span class="asset-label">估算成本</span>
                <strong class="asset-value">¥{{ safeNumber(summaryData.todayCost).toFixed(2) }}</strong>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 请求结构与资源分布 -->
      <div class="bottom-col">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>资源分布（按智能体）</span>
              <span class="panel-sub">{{ distribution.length }} 个活跃主体</span>
            </div>
          </template>
          <div v-if="topAgents.length" class="rank-list">
            <div v-for="(item, idx) in topAgents" :key="item.name" class="rank-item">
              <div class="rank-top">
                <span class="rank-index">{{ `0${idx + 1}` }}</span>
                <span class="rank-name">{{ item.name }}</span>
                <span class="rank-value">{{ formatK(item.value) }}</span>
              </div>
              <div class="rank-rail">
                <div class="rank-fill" :style="{ width: `${getRkWidth(item.value)}%` }" />
              </div>
            </div>
          </div>
          <OrinEmptyState v-else description="暂无智能体资源分布数据" />
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>队列与任务积压</span>
              <span class="panel-sub">实时任务状态</span>
            </div>
          </template>
          <div class="queue-grid">
            <div class="queue-item">
              <span>待处理</span>
              <strong>{{ queueStats.pending }}</strong>
            </div>
            <div class="queue-item">
              <span>处理中</span>
              <strong>{{ queueStats.running }}</strong>
            </div>
            <div class="queue-item">
              <span>失败</span>
              <strong>{{ queueStats.failed }}</strong>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 风险与审计 -->
      <div class="bottom-col">
        <el-card class="panel-card panel-main" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>实时审计日志</span>
              <span class="panel-sub">最近 6 条</span>
            </div>
          </template>
          <ul class="event-list" v-if="recentLogs.length">
            <li v-for="item in recentLogs" :key="`${item.time}-${item.text}`">
              <span class="event-time">{{ item.time }}</span>
              <span class="event-text">{{ item.text }}</span>
              <el-tag size="small" :type="item.status === 'healthy' ? 'success' : 'danger'">
                {{ item.status === 'healthy' ? '正常' : '异常' }}
              </el-tag>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无审计日志" />
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>慢请求 Top 5</span>
              <span class="panel-sub">按请求耗时排序</span>
            </div>
          </template>
          <ul v-if="slowRequestTop.length" class="mini-rank-list">
            <li v-for="(item, idx) in slowRequestTop" :key="`${item.name}-${idx}`">
              <span class="mini-rank-index">{{ idx + 1 }}</span>
              <span class="mini-rank-name">{{ item.name }}</span>
              <strong class="mini-rank-value">{{ item.value }}ms</strong>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无慢请求数据" />
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>错误类型 Top 5</span>
              <span class="panel-sub">按出现次数统计</span>
            </div>
          </template>
          <ul v-if="errorTypeTop.length" class="mini-rank-list">
            <li v-for="(item, idx) in errorTypeTop" :key="`${item.name}-${idx}`">
              <span class="mini-rank-index">{{ idx + 1 }}</span>
              <span class="mini-rank-name">{{ item.name }}</span>
              <strong class="mini-rank-value">{{ item.value }}</strong>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无错误类型数据" />
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>依赖与基础服务状态</span>
              <span class="panel-sub">
                外部正常 {{ externalDependencySummary.alive }}/{{ externalDependencySummary.total }} ·
                基础正常 {{ basicServiceSummary.alive }}/{{ basicServiceSummary.total }}
              </span>
            </div>
          </template>
          <div class="dependency-section-title">基础服务</div>
          <ul v-if="basicServices.length" class="dependency-list dependency-list--grid">
            <li v-for="item in basicServices" :key="item.key">
              <span class="dependency-name">{{ item.name }}</span>
              <el-tag size="small" :type="item.tagType">{{ item.text }}</el-tag>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无基础服务状态" :image-size="40" />

          <el-divider class="dependency-divider" />
          <div class="dependency-section-title">外部依赖</div>
          <ul v-if="externalDependencies.length" class="dependency-list dependency-list--grid">
            <li v-for="item in externalDependencies" :key="item.key">
              <span class="dependency-name">{{ item.name }}</span>
              <el-tag size="small" :type="item.tagType">{{ item.text }}</el-tag>
            </li>
          </ul>
          <OrinEmptyState v-else description="暂无外部依赖状态" :image-size="40" />
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import { getAgentList } from '@/api/agent'
import {
  getGlobalSummary,
  getTokenHistory,
  getLatencyStats,
  getTokenDistribution,
  getServerHardware,
  getServerHardwareTrend,
  getServerNodes,
  getSystemHealth,
  testPrometheusConnection,
  testMilvusConnection,
  getStorageHealthSnapshot,
  getSystemMaintenanceHealth,
} from '@/api/monitor'
import { getIntegrationStatus } from '@/api/integrations'
import { UI_TEXT } from '@/constants/uiText'

const loading = ref(false)
const isRefreshing = ref(false)
const summaryData = ref({})
const lastRefreshAt = ref(0)
const nowTick = ref(Date.now())
const healthStatusText = ref('待检测')
const healthStatusClass = ref('status-warn')
const lastHealthCheck = ref('--:--:--')
const kpi = ref({
  activeAgents: 0,
  activeAgentsDelta: 0,
  todayCalls: 0,
  callsDelta: 0,
  avgLatency: '0ms',
  latencyValue: 0,
  latencyNote: '暂无波动数据',
  alertCount: 0,
  alertDelta: 0,
})

const trendData = ref([])
const distribution = ref([])
const recentLogs = ref([])
const hardware = ref({ cpuUsage: 0, gpuUsage: 0, memoryUsage: 0, diskUsage: 0 })
const serverNodes = ref([])
const snapshotNodeId = ref('未知节点')
const snapshotTimeText = ref('--:--:--')
const snapshotTimestampMs = ref(0)
const hardwareSnapshotValid = ref(false)
const errorTypeTop = ref([])
const slowRequestTop = ref([])
const todayCostTrendRaw = ref([])
const queueStats = ref({ pending: 0, running: 0, failed: 0 })
const latencyTrendData = ref([])
const externalDependencies = ref([])
const basicServices = ref([])
let dashboardLoadSeq = 0
const NODE_DATA_FRESH_WINDOW_MS = 3 * 60 * 1000

const CHART_CONFIG = {
  left: 40,
  right: 660,
  top: 24,
  bottom: 176,
}

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

const buildChartScale = (series, { preferZeroBaseline = false } = {}) => {
  const values = series.map((item) => safeNumber(item.value)).filter((value) => Number.isFinite(value))
  if (!values.length) {
    return { min: 0, max: 1, range: 1, rawMin: 0, rawMax: 0 }
  }

  const rawMin = Math.min(...values)
  const rawMax = Math.max(...values)

  if (rawMax === rawMin) {
    if (rawMax === 0) {
      return { min: 0, max: 1, range: 1, rawMin, rawMax }
    }
    const padding = Math.max(Math.abs(rawMax) * 0.2, 1)
    const min = preferZeroBaseline && rawMin > 0 ? 0 : rawMin - padding
    const max = rawMax + padding
    return { min, max, range: max - min, rawMin, rawMax }
  }

  const rawRange = rawMax - rawMin
  let min = rawMin - rawRange * 0.12
  let max = rawMax + rawRange * 0.18

  if (preferZeroBaseline && rawMin >= 0 && rawMin / rawMax < 0.35) {
    min = 0
  }

  if (rawMin >= 0) {
    min = Math.max(0, min)
  }

  return { min, max, range: max - min, rawMin, rawMax }
}

const formatTickValue = (value, range, suffix = '') => {
  const absRange = Math.abs(range)
  const digits = absRange < 1 ? 2 : absRange < 10 ? 1 : 0
  const formatted = Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: digits,
  })
  return `${formatted}${suffix}`
}

const buildLinePoints = (series, scale) => {
  if (!series.length) return []
  const valueRange = Math.max(scale.max - scale.min, 1e-6)
  const step = series.length > 1 ? (CHART_CONFIG.right - CHART_CONFIG.left) / (series.length - 1) : CHART_CONFIG.right - CHART_CONFIG.left
  return series.map((item, index) => {
    const normalized = (safeNumber(item.value) - scale.min) / valueRange
    const ratio = clamp(normalized, 0, 1)
    return {
      label: item.label,
      x: CHART_CONFIG.left + index * step,
      y: CHART_CONFIG.bottom - ratio * (CHART_CONFIG.bottom - CHART_CONFIG.top),
    }
  })
}

const trendScale = computed(() => buildChartScale(trendData.value))
const latencyScale = computed(() => buildChartScale(latencyTrendData.value))

const chartPoints = computed(() => buildLinePoints(trendData.value, trendScale.value))
const latencyChartPoints = computed(() => buildLinePoints(latencyTrendData.value, latencyScale.value))

const trendPoints = computed(() => chartPoints.value.map((point) => `${point.x},${point.y}`).join(' '))
const latencyTrendPoints = computed(() => latencyChartPoints.value.map((point) => `${point.x},${point.y}`).join(' '))

const buildAreaPoints = (points) => {
  if (!points.length) return ''
  const firstX = points[0].x
  const lastX = points[points.length - 1].x
  return `${firstX},${CHART_CONFIG.bottom} ${points.map((point) => `${point.x},${point.y}`).join(' ')} ${lastX},${CHART_CONFIG.bottom}`
}

const trendAreaPoints = computed(() => buildAreaPoints(chartPoints.value))
const latencyAreaPoints = computed(() => buildAreaPoints(latencyChartPoints.value))

const buildGridTicks = (domain, suffix = '') => {
  const scales = [1, 0.75, 0.5, 0.25, 0]
  return scales.map((ratio) => ({
    y: CHART_CONFIG.top + (1 - ratio) * (CHART_CONFIG.bottom - CHART_CONFIG.top),
    label: formatTickValue(
      domain.min + (domain.max - domain.min) * ratio,
      domain.range,
      suffix,
    ),
  }))
}
const trendGridTicks = computed(() => buildGridTicks(trendScale.value))
const latencyGridTicks = computed(() => buildGridTicks(latencyScale.value, 'ms'))
const topAgents = computed(() => distribution.value.slice(0, 3))
const currentHardwareNodeId = computed(() => {
  const directId = hardware.value?.serverId || hardware.value?.nodeId || hardware.value?.id
  if (directId) return String(directId)
  const localNode = serverNodes.value.find((node) => String(node?.id || '').toLowerCase() === 'local')
  return localNode?.id || 'local'
})
const preferredBusinessNodeId = computed(() => {
  if (!Array.isArray(serverNodes.value) || !serverNodes.value.length) return null
  const online = serverNodes.value.filter((node) => normalizeNodeStatus(node).statusClass === 'ok')
  const nonLocalOnline = online.find((node) => String(node?.id || '').toLowerCase() !== 'local')
  if (nonLocalOnline?.id) return String(nonLocalOnline.id)
  const nonLocalAny = serverNodes.value.find((node) => String(node?.id || '').toLowerCase() !== 'local')
  if (nonLocalAny?.id) return String(nonLocalAny.id)
  return null
})
const totalNodesCount = computed(() => (Array.isArray(serverNodes.value) ? serverNodes.value.length : 0))
const activeNodesCount = computed(() => {
  if (!Array.isArray(serverNodes.value) || !serverNodes.value.length) return 0
  return serverNodes.value.filter((node) => normalizeNodeStatus(node).statusClass === 'ok').length
})
const offlineNodesCount = computed(() => Math.max(totalNodesCount.value - activeNodesCount.value, 0))
const normalizeEpochToMs = (value) => {
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) return 0
  if (n > 1e17) return Math.floor(n / 1e6) // ns
  if (n > 1e14) return Math.floor(n / 1e3) // us
  if (n > 1e11) return Math.floor(n) // ms
  if (n > 1e9) return Math.floor(n * 1000) // s
  return 0
}
const parseTimestampMs = (value) => {
  if (value === undefined || value === null || value === '') return 0
  if (Array.isArray(value) && value.length >= 6) {
    const [year, month, day, hour, minute, second, nanos] = value
    const ms = nanos ? Math.floor(Number(nanos) / 1e6) : 0
    const date = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second), ms)
    const ts = date.getTime()
    return Number.isFinite(ts) ? ts : 0
  }
  if (typeof value === 'number') return normalizeEpochToMs(value)
  const raw = String(value).trim()
  if (!raw) return 0
  if (/^\d+$/.test(raw)) return normalizeEpochToMs(Number(raw))
  const ts = dayjs(raw).valueOf()
  return Number.isFinite(ts) ? ts : 0
}
const hasNodeRuntimeData = (node) => {
  if (!node || typeof node !== 'object') return false
  if (node.hasData === true) return true
  if (node.recordedAt || node.timestamp || node.lastSeenAt || node.lastReportAt) return true
  if (
    hasHardwareMetrics.value &&
    snapshotNodeId.value &&
    String(node.id || '').trim() &&
    String(node.id) === String(snapshotNodeId.value)
  ) {
    return true
  }
  return false
}
const hasRecentNodeRuntimeData = (node) => {
  if (!node || typeof node !== 'object') return false
  const now = Date.now()
  const candidates = [node.recordedAt, node.timestamp, node.lastSeenAt, node.lastReportAt]
  const ts = candidates.map((item) => parseTimestampMs(item)).find((item) => item > 0) || 0
  if (ts > 0 && now - ts <= NODE_DATA_FRESH_WINDOW_MS) return true
  if (
    hasHardwareMetrics.value &&
    snapshotTimestampMs.value > 0 &&
    now - snapshotTimestampMs.value <= NODE_DATA_FRESH_WINDOW_MS &&
    snapshotNodeId.value &&
    String(node.id || '').trim() &&
    String(node.id) === String(snapshotNodeId.value)
  ) {
    return true
  }
  return false
}

const normalizeNodeStatus = (node) => {
  const raw = String(node?.status || node?.state || '').toUpperCase()
  if (node?.online === true || raw.includes('UP') || raw.includes('ONLINE') || raw.includes('HEALTHY')) {
    return { statusClass: 'ok', statusText: '在线' }
  }
  if (hasRecentNodeRuntimeData(node)) {
    return { statusClass: 'ok', statusText: '在线' }
  }
  if (node?.online === false && hasNodeRuntimeData(node)) {
    return { statusClass: 'warn', statusText: '告警' }
  }
  if (hasNodeRuntimeData(node) || raw.includes('WARN') || raw.includes('DEGRADED')) {
    return { statusClass: 'warn', statusText: '告警' }
  }
  return { statusClass: 'danger', statusText: '离线' }
}
const nodeStatusSummary = computed(() => {
  const summary = { online: 0, warning: 0, offline: 0 }
  const list = Array.isArray(serverNodes.value) ? serverNodes.value : []
  list.forEach((node) => {
    const status = normalizeNodeStatus(node)
    if (status.statusClass === 'ok') summary.online += 1
    else if (status.statusClass === 'danger') summary.offline += 1
    else summary.warning += 1
  })
  return summary
})
const nodePreviewList = computed(() => {
  if (!Array.isArray(serverNodes.value)) return []
  return serverNodes.value.slice(0, 6).map((node, idx) => {
    const status = normalizeNodeStatus(node)
    return {
      id: String(node?.id || node?.name || `node-${idx + 1}`),
      ...status,
    }
  })
})
const hasHardwareMetrics = computed(() => {
  if (!hardwareSnapshotValid.value) return false
  const source = hardware.value || {}
  const hasValue = ['cpuUsage', 'gpuUsage', 'memoryUsage', 'diskUsage'].some((key) => {
    return safeNumber(source[key]) > 0
  })
  return hasValue && Boolean(snapshotNodeId.value) && snapshotTimeText.value !== '--:--:--'
})
const todayCostTrend = computed(() => {
  if (!Array.isArray(todayCostTrendRaw.value) || !todayCostTrendRaw.value.length) return []
  const max = Math.max(...todayCostTrendRaw.value.map((item) => safeNumber(item.value)), 1)
  return todayCostTrendRaw.value.map((item) => ({
    hour: item.hour,
    value: safeNumber(item.value),
    height: safeNumber(item.value) > 0 ? Math.max((safeNumber(item.value) / max) * 100, 8) : 0,
  }))
})
const todayCostTotal = computed(() => todayCostTrend.value.reduce((sum, item) => sum + safeNumber(item.value), 0))
const hasTodayCostBreakdownData = computed(() => todayCostTrend.value.some((item) => safeNumber(item.value) > 0))
const todayCostEmptyDescription = computed(() => {
  const totalFromSummary = safeNumber(summaryData.value?.todayCost, safeNumber(summaryData.value?.today_cost))
  if (totalFromSummary > 0) {
    return `今日累计成本 ¥${totalFromSummary.toFixed(2)}，暂无小时级明细`
  }
  return '今日暂无成本上报数据'
})
const externalDependencySummary = computed(() => {
  const summary = { alive: 0, down: 0, disabled: 0, total: 0 }
  const list = Array.isArray(externalDependencies.value) ? externalDependencies.value : []
  summary.total = list.length
  list.forEach((item) => {
    if (item.state === 'alive') summary.alive += 1
    else if (item.state === 'down') summary.down += 1
    else summary.disabled += 1
  })
  return summary
})

const normalizeDependencyState = (item) => {
  const enabled = item?.enabled === true
  const connected = item?.connected === true
  const status = String(item?.status || '').toUpperCase()
  if (!enabled || status.includes('DISABLED')) {
    return { state: 'disabled', text: '未启用', tagType: 'info' }
  }
  if (connected || status.includes('CONNECTED') || status.includes('UP') || status.includes('OK')) {
    return { state: 'alive', text: '正常', tagType: 'success' }
  }
  return { state: 'down', text: '异常', tagType: 'danger' }
}

const buildExternalDependencies = (integrationStatus) => {
  const payload = integrationStatus && typeof integrationStatus === 'object' ? integrationStatus : {}
  externalDependencies.value = Object.entries(payload).map(([key, value]) => {
    const state = normalizeDependencyState(value)
    return {
      key,
      name: key.toUpperCase(),
      ...state,
    }
  })
}

const basicServiceSummary = computed(() => {
  const summary = { alive: 0, down: 0, unknown: 0, total: 0 }
  const list = Array.isArray(basicServices.value) ? basicServices.value : []
  summary.total = list.length
  list.forEach((item) => {
    if (item.state === 'alive') summary.alive += 1
    else if (item.state === 'down') summary.down += 1
    else summary.unknown += 1
  })
  return summary
})

const normalizeBasicServiceState = (status) => {
  const raw = String(status || '').toUpperCase()
  if (raw.includes('UP') || raw.includes('ONLINE') || raw.includes('HEALTHY') || raw.includes('CONNECTED') || raw.includes('OK')) {
    return { state: 'alive', text: '正常', tagType: 'success' }
  }
  if (raw.includes('DOWN') || raw.includes('OFFLINE') || raw.includes('ERROR') || raw.includes('FAIL')) {
    return { state: 'down', text: '异常', tagType: 'danger' }
  }
  return { state: 'unknown', text: '未知', tagType: 'info' }
}

const readServiceProbeStatus = (service) => {
  if (!service || typeof service !== 'object') return ''
  const errorText = String(service.error || '').toUpperCase()
  const message = String(service.message || '').toUpperCase()
  if (errorText.includes('DISABLED') || message.includes('DISABLED')) return 'DISABLED'
  if (service.online === true) return 'UP'
  if (service.online === false) return 'DOWN'
  if (service.success === true) return 'UP'
  if (service.success === false) return 'DOWN'
  if (service.connected === true) return 'UP'
  if (service.connected === false) return 'DOWN'
  if (message.includes('SUCCESS')) return 'UP'
  if (message.includes('FAIL') || message.includes('ERROR') || message.includes('TIMEOUT')) return 'DOWN'
  return String(service.status || service.state || '').toUpperCase()
}

const buildBasicServices = ({ health, prometheus, milvus, storage, maintenance }) => {
  const list = []
  const push = (key, name, status) => {
    if (!key) return
    const normalized = normalizeBasicServiceState(status)
    list.push({ key, name, ...normalized })
  }

  const dbStatus = health?.database?.status || health?.components?.db?.status || maintenance?.services?.database
  const redisStatus = health?.components?.redis?.status || maintenance?.services?.redis
  const milvusProbeStatus = readServiceProbeStatus(milvus)
  const prometheusProbeStatus = readServiceProbeStatus(prometheus)
  const inferredPrometheusStatus = hasHardwareMetrics.value ? 'UP' : ''
  const milvusStatus = health?.components?.milvus?.status || maintenance?.services?.milvus || milvusProbeStatus
  const prometheusStatus =
    health?.components?.prometheus?.status ||
    maintenance?.services?.prometheus ||
    prometheusProbeStatus ||
    inferredPrometheusStatus
  push('database', '数据库', dbStatus)
  push('redis', 'Redis', redisStatus)
  push('prometheus', 'Prometheus', prometheusStatus)
  push('milvus', 'Milvus', milvusStatus)

  const primary = storage?.primary || {}
  const secondary = storage?.secondary || {}
  if (primary.backend) {
    const primaryStatus = primary.up === true ? 'UP' : primary.up === false ? 'DOWN' : ''
    push(`storage-${primary.backend}-primary`, `${String(primary.backend).toUpperCase()}(主存储)`, primaryStatus)
  }
  if (secondary.backend) {
    const secondaryStatus = secondary.up === true ? 'UP' : secondary.up === false ? 'DOWN' : ''
    push(`storage-${secondary.backend}-secondary`, `${String(secondary.backend).toUpperCase()}(次存储)`, secondaryStatus)
  }

  const uniqueByKey = new Map()
  list.forEach((item) => {
    if (!uniqueByKey.has(item.key)) uniqueByKey.set(item.key, item)
  })
  basicServices.value = Array.from(uniqueByKey.values())
}

const formatDurationLabel = (valueMs, zeroText = '0秒') => {
  const totalSeconds = Math.floor(Math.max(safeNumber(valueMs), 0) / 1000)
  if (totalSeconds <= 0) return zeroText
  if (totalSeconds < 60) return `${totalSeconds}秒`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes < 60) return `${minutes}分${seconds}秒`
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `${hours}小时${remainMinutes}分`
}

const refreshAgoText = computed(() => {
  if (!lastRefreshAt.value) return '尚未刷新'
  const elapsedMs = Math.max(nowTick.value - lastRefreshAt.value, 0)
  return `${formatDurationLabel(elapsedMs, '刚刚')}前`
})

const safeNumber = (value, fallback = 0) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

const readLatencyValue = (item = {}) => {
  return safeNumber(
    item.latencyMs,
    safeNumber(
      item.durationMs,
      safeNumber(
        item.responseMs,
        safeNumber(item.responseTime)
      )
    )
  )
}

const readLatencyStatValue = (summary = {}, latencyData = {}) => {
  return safeNumber(
    summary.avgLatencyMs,
    safeNumber(
      summary.avg_latency_ms,
      safeNumber(
        latencyData.avgLatency,
        safeNumber(
          latencyData.avg,
          safeNumber(String(summary.avg_latency || '').replace(/[^\d.-]/g, ''))
        )
      )
    )
  )
}

const readCostValue = (item) => {
  return safeNumber(
    item?.cost,
    safeNumber(
      item?.estimatedCost,
      safeNumber(
        item?.estimated_cost,
        safeNumber(
          item?.externalPrice,
          safeNumber(item?.external_price, safeNumber(item?.totalCost, safeNumber(item?.total_cost)))
        )
      )
    )
  )
}

const formatCheckTime = (value) => {
  if (!value) return dayjs().format('HH:mm:ss')
  const parsed = dayjs(value)
  if (parsed.isValid()) return parsed.format('HH:mm:ss')
  return dayjs().format('HH:mm:ss')
}

const unwrapResponse = (res, fallback = null) => {
  if (res?.data !== undefined) return res.data
  if (res !== undefined && res !== null) return res
  return fallback
}

const formatDelta = (delta, prefix) => {
  const value = safeNumber(delta)
  const sign = value >= 0 ? '+' : '-'
  return `${prefix} ${sign}${Math.abs(value).toFixed(0)}%`
}

const formatK = (v) => {
  const value = safeNumber(v)
  if (!value) return '0'
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)
}

const getRkWidth = (v) => {
  if (!distribution.value.length) return 0
  const max = Math.max(...distribution.value.map((item) => safeNumber(item.value)), 1)
  return (safeNumber(v) / max) * 100
}

const getTileThresholdClass = (value) => {
  const numValue = safeNumber(value)
  if (numValue >= 85) return 'critical'
  if (numValue >= 70) return 'warning'
  return 'normal'
}

const getTileFillGradient = (value) => {
  const numValue = safeNumber(value)
  if (numValue >= 85) return 'var(--progress-critical-end)'
  if (numValue >= 70) return 'var(--progress-warning-end)'
  return 'var(--success-500)'
}

const buildTrendData = (historyList) => {
  const dayKeys = Array.from({ length: 7 }, (_, idx) => dayjs().subtract(6 - idx, 'day').format('YYYY-MM-DD'))
  const dayMap = dayKeys.reduce((acc, key) => {
    acc[key] = 0
    return acc
  }, {})

  historyList.forEach((item) => {
    const time = item.createdAt || item.timestamp || item.time
    if (!time) return
    const dayKey = dayjs(time).format('YYYY-MM-DD')
    if (!(dayKey in dayMap)) return
    dayMap[dayKey] += safeNumber(item.totalRequests, safeNumber(item.totalTokens, 1))
  })

  trendData.value = dayKeys.map((dayKey) => ({
    label: dayjs(dayKey).format('MM-DD'),
    value: dayMap[dayKey],
  }))
}

const buildLatencyTrendData = (historyList) => {
  const dayKeys = Array.from({ length: 7 }, (_, idx) => dayjs().subtract(6 - idx, 'day').format('YYYY-MM-DD'))
  const latencyMap = dayKeys.reduce((acc, key) => {
    acc[key] = { total: 0, count: 0 }
    return acc
  }, {})

  historyList.forEach((item) => {
    const time = item.createdAt || item.timestamp || item.time
    if (!time) return
    const dayKey = dayjs(time).format('YYYY-MM-DD')
    if (!(dayKey in latencyMap)) return
    const latency = readLatencyValue(item)
    if (latency <= 0) return
    latencyMap[dayKey].total += latency
    latencyMap[dayKey].count += 1
  })

  latencyTrendData.value = dayKeys.map((dayKey) => ({
    label: dayjs(dayKey).format('MM-DD'),
    value: latencyMap[dayKey].count ? latencyMap[dayKey].total / latencyMap[dayKey].count : 0,
  }))
}

const buildTopLists = (historyList) => {
  const errorMap = new Map()
  const slowList = []
  const hourCostMap = new Map()
  const todayKey = dayjs().format('YYYY-MM-DD')

  historyList.forEach((item) => {
    const isSuccess = item.success !== false
    if (!isSuccess) {
      const code = item.errorCode || item.errorType || item.statusCode || 'UNKNOWN'
      errorMap.set(code, (errorMap.get(code) || 0) + 1)
    }

    const latency = readLatencyValue(item)
    if (latency > 0) {
      slowList.push({
        name: item.endpoint?.split('/').pop() || item.agentName || item.providerId || '请求',
        value: Math.round(latency),
      })
    }

    const ts = item.createdAt || item.timestamp || item.time
    if (ts && dayjs(ts).format('YYYY-MM-DD') === todayKey) {
      const hour = dayjs(ts).format('HH:00')
      const cost = readCostValue(item)
      hourCostMap.set(hour, (hourCostMap.get(hour) || 0) + cost)
    }
  })

  errorTypeTop.value = Array.from(errorMap.entries())
    .map(([name, value]) => ({ name: String(name), value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 5)

  slowRequestTop.value = slowList.sort((a, b) => b.value - a.value).slice(0, 5)

  todayCostTrendRaw.value = Array.from({ length: 24 }, (_, idx) => {
    const hour = `${String(idx).padStart(2, '0')}:00`
    return { hour, value: safeNumber(hourCostMap.get(hour), 0) }
  })

}

const hydrateHardwareSnapshot = async (fallbackHardware) => {
  hardwareSnapshotValid.value = false
  snapshotTimestampMs.value = 0
  const candidateNodeId = preferredBusinessNodeId.value
  if (candidateNodeId) {
    try {
      const trend = await getServerHardwareTrend('1h', candidateNodeId)
      const latest = Array.isArray(trend) && trend.length ? trend[trend.length - 1] : null
      if (latest) {
        hardware.value = {
          cpuUsage: safeNumber(latest.cpuUsage),
          gpuUsage: safeNumber(latest.gpuUsage),
          memoryUsage: safeNumber(latest.memoryUsage),
          diskUsage: safeNumber(latest.diskUsage),
          serverId: candidateNodeId,
        }
        snapshotNodeId.value = candidateNodeId
        snapshotTimestampMs.value = parseTimestampMs(latest.timestamp)
        snapshotTimeText.value = formatCheckTime(latest.timestamp)
        hardwareSnapshotValid.value = true
        return
      }
    } catch (error) {
      // Graceful fallback to local snapshot API
    }
  }

  const fallbackNodeId = String(
    fallbackHardware?.serverId ||
    fallbackHardware?.nodeId ||
    fallbackHardware?.id ||
    preferredBusinessNodeId.value ||
    currentHardwareNodeId.value ||
    '未知节点'
  )
  const fallbackTime = fallbackHardware?.timestamp || fallbackHardware?.checkedAt || fallbackHardware?.updateTime
  const hasFallbackMetrics = ['cpuUsage', 'gpuUsage', 'memoryUsage', 'diskUsage'].some((key) => {
    const value = fallbackHardware?.[key]
    return value !== undefined && value !== null && Number.isFinite(Number(value))
  })

  hardware.value = {
    cpuUsage: safeNumber(fallbackHardware?.cpuUsage),
    gpuUsage: safeNumber(fallbackHardware?.gpuUsage),
    memoryUsage: safeNumber(fallbackHardware?.memoryUsage),
    diskUsage: safeNumber(fallbackHardware?.diskUsage),
    serverId: fallbackNodeId,
  }
  snapshotNodeId.value = fallbackNodeId
  snapshotTimestampMs.value = parseTimestampMs(fallbackTime)
  snapshotTimeText.value = fallbackTime ? formatCheckTime(fallbackTime) : '--:--:--'
  hardwareSnapshotValid.value = hasFallbackMetrics && Boolean(fallbackTime)
}


const loadDashboardData = async () => {
  const loadSeq = ++dashboardLoadSeq
  loading.value = true
  isRefreshing.value = true
  try {
    const [summaryRes, agentsRes, tokenHistoryRes, latencyRes, hardwareRes, healthRes, distributionRes, nodesRes, integrationStatusRes] = await Promise.all([
      getGlobalSummary(),
      getAgentList(),
      getTokenHistory({ size: 200 }),
      getLatencyStats().catch(() => ({})),
      getServerHardware().catch(() => ({})),
      getSystemHealth().catch(() => ({})),
      getTokenDistribution().catch(() => ([])),
      getServerNodes().catch(() => ([])),
      getIntegrationStatus().catch(() => ({})),
    ])

    const summary = unwrapResponse(summaryRes, {}) || {}
    const agents = unwrapResponse(agentsRes, []) || []
    const tokenHistoryPayload = unwrapResponse(tokenHistoryRes, {}) || {}
    const historyList = Array.isArray(tokenHistoryPayload)
      ? tokenHistoryPayload
      : Array.isArray(tokenHistoryPayload.content)
        ? tokenHistoryPayload.content
        : []

    const latencyData = unwrapResponse(latencyRes, {}) || {}
    const hardware = unwrapResponse(hardwareRes, {}) || {}
    const health = unwrapResponse(healthRes, {}) || {}
    const distributionData = unwrapResponse(distributionRes, []) || []
    const nodesData = unwrapResponse(nodesRes, []) || []
    const integrationStatus = unwrapResponse(integrationStatusRes, {}) || {}

    const activeAgents = agents.filter((item) => item.enabled !== false && item.status !== 'OFFLINE').length
    const todayCalls = safeNumber(summary.daily_requests, historyList.filter((item) => dayjs(item.createdAt || item.timestamp).isSame(dayjs(), 'day')).length)
    const avgLatencyValue = readLatencyStatValue(summary, latencyData)
    const alertCount = safeNumber(summary.alertCount, safeNumber(summary.highLoadAgents))

    summaryData.value = summary
    serverNodes.value = Array.isArray(nodesData) ? nodesData : []
    await hydrateHardwareSnapshot(hardware)

    const healthRaw = String(health?.status || health?.code || '').toUpperCase()
    if (healthRaw.includes('UP') || healthRaw.includes('OK') || healthRaw.includes('SUCCESS')) {
      healthStatusText.value = '运行正常'
      healthStatusClass.value = 'status-ok'
    } else if (healthRaw) {
      healthStatusText.value = '存在异常'
      healthStatusClass.value = 'status-danger'
    } else {
      healthStatusText.value = '待检测'
      healthStatusClass.value = 'status-warn'
    }
    lastHealthCheck.value = formatCheckTime(
      health?.checkedAt || health?.timestamp || summary?.lastHealthCheckAt || summary?.updateTime
    )
    kpi.value = {
      activeAgents,
      activeAgentsDelta: safeNumber(summary.onlineAgentsTrend, safeNumber(summary.online_agents_trend)),
      todayCalls,
      callsDelta: safeNumber(summary.dailyRequestsTrend, safeNumber(summary.daily_requests_trend)),
      avgLatency: `${avgLatencyValue.toFixed(0)}ms`,
      latencyValue: avgLatencyValue,
      latencyNote: avgLatencyValue <= 800 ? '响应稳定，建议保持当前配置' : '时延偏高，建议优化慢链路',
      alertCount,
      alertDelta: safeNumber(summary.alertTrend, safeNumber(summary.alert_count_trend)),
    }

    buildTrendData(historyList)
    buildLatencyTrendData(historyList)
    buildTopLists(historyList)
    buildExternalDependencies(integrationStatus)
    buildBasicServices({
      health,
      prometheus: {},
      milvus: {},
      storage: {},
      maintenance: {},
    })

    // Prometheus/Milvus/存储巡检改为后台更新，不阻塞主刷新
    void Promise.allSettled([
      testPrometheusConnection(),
      testMilvusConnection(),
      getStorageHealthSnapshot(),
      getSystemMaintenanceHealth(),
    ]).then((results) => {
      if (loadSeq !== dashboardLoadSeq) return
      const [prometheusRes, milvusRes, storageHealthRes, maintenanceHealthRes] = results.map((item) => {
        if (item.status === 'fulfilled') return item.value
        return {}
      })
      const prometheusHealth = unwrapResponse(prometheusRes, {}) || {}
      const milvusHealth = unwrapResponse(milvusRes, {}) || {}
      const storageHealth = unwrapResponse(storageHealthRes, {}) || {}
      const maintenanceHealth = unwrapResponse(maintenanceHealthRes, {}) || {}
      buildBasicServices({
        health,
        prometheus: prometheusHealth,
        milvus: milvusHealth,
        storage: storageHealth,
        maintenance: maintenanceHealth,
      })
    })

    distribution.value = Array.isArray(distributionData)
      ? distributionData
          .map((item) => ({ name: item.name || item.agentName || '未知主体', value: safeNumber(item.value) }))
          .sort((a, b) => b.value - a.value)
      : []
    recentLogs.value = historyList.slice(0, 6).map((item) => ({
      time: dayjs(item.createdAt || item.timestamp || Date.now()).format('MM-DD HH:mm'),
      text: `${item.agentName || item.providerId || '系统'} | ${item.endpoint?.split('/').pop() || '处理任务'}`,
      status: item.success === false ? 'critical' : 'healthy',
    }))
    if (!recentLogs.value.length) {
      recentLogs.value = [
        { time: '- -', text: health?.status ? `平台状态：${health.status}` : UI_TEXT.common.noData, status: 'healthy' },
      ]
    }

    queueStats.value = {
      pending: safeNumber(summary.pendingTasks, safeNumber(summary.queuePending)),
      running: safeNumber(summary.runningTasks, safeNumber(summary.queueRunning)),
      failed: safeNumber(summary.failedTasks, safeNumber(summary.queueFailed)),
    }
  } catch (error) {
    ElMessage.error('加载首页数据失败，请稍后重试')
    lastHealthCheck.value = dayjs().format('HH:mm:ss')
  } finally {
    lastRefreshAt.value = Date.now()
    loading.value = false
    isRefreshing.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

const handleRefresh = () => {
  loadDashboardData()
}

let refreshClockTimer = null

onMounted(() => {
  lastRefreshAt.value = Date.now()
  refreshClockTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  loadDashboardData()
  window.addEventListener('page-refresh', handleRefresh)
})

onBeforeUnmount(() => {
  if (refreshClockTimer) {
    clearInterval(refreshClockTimer)
    refreshClockTimer = null
  }
  window.removeEventListener('page-refresh', handleRefresh)
})
</script>

<style scoped>
.command-center-root {
  --bg-page: #ffffff;
  --bg-card: #ffffff;
  --bg-card-soft: #ffffff;
  --border-soft: rgba(148, 163, 184, 0.22);
  --text-strong: #0f172a;
  --text-main: #1e293b;
  --text-subtle: #64748b;
  --primary-400: #22b3a5;
  --primary-500: #0d9488;
  --primary-600: #0f766e;
  --info-400: #60a5fa;
  --info-500: #2563eb;
  --accent-400: #a78bfa;
  --accent-500: #7c3aed;
  --success-400: #4ade80;
  --success-500: #10b981;
  --warning-400: #fbbf24;
  --warning-500: #f59e0b;
  --danger-400: #f87171;
  --danger-500: #ef4444;
  --progress-warning-start: #f59e0b;
  --progress-warning-end: #f97316;
  --progress-critical-start: #f43f5e;
  --progress-critical-end: #ef4444;
  --status-badge-success-fg: #047857;
  --status-badge-success-bg: rgba(16, 185, 129, 0.12);
  --status-badge-success-border: rgba(16, 185, 129, 0.36);
  --status-badge-warning-fg: #b45309;
  --status-badge-warning-bg: rgba(245, 158, 11, 0.12);
  --status-badge-warning-border: rgba(245, 158, 11, 0.36);
  --status-badge-danger-fg: #b91c1c;
  --status-badge-danger-bg: rgba(239, 68, 68, 0.12);
  --status-badge-danger-border: rgba(239, 68, 68, 0.36);
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  min-height: calc(100vh - 76px);
  background: var(--bg-page);
}

.cc-header-glass {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(460px, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-soft);
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.04);
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.logo-text {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0;
  color: var(--text-strong);
}

.logo-dot {
  color: var(--primary-500);
}

.header-divider {
  width: 1px;
  height: 24px;
  background: rgba(148, 163, 184, 0.3);
}

.header-subtitle {
  margin: 0;
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 500;
}

.header-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
  justify-self: end;
}

/* KPI 总览 */
.kpi-overview {
  display: flex;
  flex-direction: column;
}

.kpi-grid {
  display: grid;
  gap: 12px;
}

.kpi-grid-primary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.kpi-card {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--border-soft);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kpi-card:hover {
  transform: none;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
}

.kpi-card.kpi-danger {
  border-color: rgba(239, 68, 68, 0.3);
}

.kpi-label {
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.kpi-value {
  color: var(--text-strong);
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
}

.kpi-value-sm {
  font-size: 20px;
  line-height: 1.25;
}

.kpi-meta {
  color: var(--primary-600);
  font-size: 12px;
}

/* 中间两列 */
.middle-section {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 1fr);
  gap: 12px;
  align-items: stretch;
}

.middle-col-main,
.middle-col-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.middle-col-side {
  height: 100%;
}

.middle-col-side > .panel-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.middle-col-side > .panel-card :deep(.el-card__body) {
  flex: 1;
}

/* 底部三列 — 使用多列布局，让卡片按高度自动打包填充，避免出现空缺 */
.bottom-section {
  column-count: 3;
  column-gap: 12px;
}

.bottom-col {
  display: contents;
}

.bottom-section :deep(.panel-card.el-card) {
  display: block;
  width: 100%;
  margin: 0 0 12px;
  break-inside: avoid;
  page-break-inside: avoid;
  -webkit-column-break-inside: avoid;
}

/* Panel cards */
:deep(.panel-card.el-card) {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--border-soft);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

:deep(.panel-card .el-card__header) {
  border-bottom: 1px solid var(--border-soft);
  padding: 12px 16px;
}

:deep(.panel-card .el-card__body) {
  padding: 12px 16px;
}

.panel-main :deep(.el-card__body) {
  min-height: 320px;
}

.panel-trend :deep(.el-card__body) {
  min-height: 250px;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-main);
}

.panel-sub {
  font-size: 12px;
  color: var(--text-subtle);
}

/* Trend charts */
.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
}

.line-chart-shell {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 8px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.line-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-subtle);
  text-align: right;
  padding-top: 2px;
  padding-bottom: 22px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.line-main {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 6px;
}

.trend-svg {
  width: 100%;
  height: 100%;
  min-height: 140px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 12px;
  box-shadow: inset 0 2px 8px rgba(15, 23, 42, 0.02);
}

.trend-grid-line {
  stroke: rgba(148, 163, 184, 0.18);
  stroke-width: 1;
  stroke-dasharray: 6 4;
}

.trend-area {
  fill: color-mix(in srgb, var(--primary-500) 16%, transparent);
}

.trend-area.latency {
  fill: color-mix(in srgb, var(--info-500) 14%, transparent);
}

.trend-line {
  fill: none;
  stroke: var(--primary-500);
  stroke-width: 2.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: none;
}

.trend-line.latency {
  stroke: var(--info-500);
  filter: none;
}

.trend-dot {
  fill: var(--primary-500);
  stroke: #ffffff;
  stroke-width: 1.25;
}

.trend-dot.latency {
  fill: var(--info-500);
  stroke: #ffffff;
  stroke-width: 1.5;
}

.trend-axis {
  display: flex;
  justify-content: space-between;
  padding-left: calc(40 / 700 * 100%);
  padding-right: calc(40 / 700 * 100%);
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  margin-top: 0;
}

/* Node health */
.node-summary {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.node-stat {
  flex: 1;
  min-width: 0;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #ffffff;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02);
}

.node-stat::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 3px;
}

.node-stat.ok::before { background: #22c55e; }
.node-stat.warn::before { background: #f59e0b; }
.node-stat.danger::before { background: #ef4444; }

.node-stat-label {
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 500;
}

.node-stat-value {
  color: var(--text-strong);
  font-size: 24px;
  font-weight: 800;
  line-height: 1.1;
}

.node-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.6);
  transition: all 0.2s ease;
}

.node-list li:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
  transform: translateY(-1px);
}

.node-id {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 500;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.node-status {
  padding: 4px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.node-status.ok {
  color: var(--status-badge-success-fg);
  background: var(--status-badge-success-bg);
  border: 1px solid var(--status-badge-success-border);
}

.node-status.warn {
  color: var(--status-badge-warning-fg);
  background: var(--status-badge-warning-bg);
  border: 1px solid var(--status-badge-warning-border);
}

.node-status.danger {
  color: var(--status-badge-danger-fg);
  background: var(--status-badge-danger-bg);
  border: 1px solid var(--status-badge-danger-border);
}

.node-load-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed rgba(148, 163, 184, 0.3);
}

.node-load-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--text-main);
  font-size: 13px;
  font-weight: 700;
}

.load-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  align-items: stretch;
  gap: 10px;
}

.load-tile {
  box-sizing: border-box;
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 10px;
  background: #ffffff;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
  min-height: 84px;
  transition: all 0.2s ease;
}

.load-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
}

.tile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tile-header span {
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 600;
}

.tile-header strong {
  color: var(--text-strong);
  font-size: 16px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.tile-rail {
  width: 100%;
  height: 4px;
  background: #e2e8f0;
  border-radius: 9999px;
  overflow: hidden;
}

.tile-fill {
  height: 100%;
  border-radius: 9999px;
  transition: width 0.3s ease, background 0.3s ease;
  cursor: pointer;
}

.tile-fill.cpu  { background: var(--info-500); }
.tile-fill.gpu  { background: var(--accent-500); }
.tile-fill.memory { background: var(--success-500); }
.tile-fill.disk  { background: var(--warning-500); }
.tile-fill.warning  { background: var(--progress-warning-end) !important; }
.tile-fill.critical { background: var(--progress-critical-end) !important; }

/* Cost trend */
.cost-trend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cost-bars {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 6px;
}

.cost-bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.cost-bar-rail {
  width: 100%;
  height: 70px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(248, 250, 252, 0.7);
  display: flex;
  align-items: end;
  justify-content: center;
  padding: 4px;
}

.cost-bar-fill {
  width: 100%;
  border-radius: 5px;
  background: var(--primary-500);
}

.cost-hour {
  color: var(--text-subtle);
  font-size: 11px;
}

.cost-total {
  color: var(--text-main);
  font-size: 12px;
}

/* Asset cards */
.asset-load-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.asset-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 1px minmax(0, 1fr);
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  gap: 14px;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative;
  overflow: hidden;
}

.asset-card::before {
  display: none;
}

.asset-card.primary {
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}
.asset-card.primary .asset-icon { background: #e0f2fe; color: #0284c7; }

.asset-card.secondary {
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}
.asset-card.secondary .asset-icon { background: #dcfce7; color: #059669; }

.asset-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.06);
}

.asset-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.asset-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.asset-label {
  font-size: 13px;
  color: var(--text-subtle);
  margin-bottom: 4px;
  font-weight: 500;
}

.asset-value {
  font-size: 20px;
  color: var(--text-strong);
  font-weight: 800;
  line-height: 1.2;
}

.asset-divider {
  width: 1px;
  height: 32px;
  background: rgba(148, 163, 184, 0.2);
  justify-self: center;
}

/* Rank list */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 10px;
  padding: 8px;
  background: rgba(248, 250, 252, 0.75);
}

.rank-top {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.rank-index { font-size: 12px; font-weight: 800; color: var(--primary-500); }
.rank-name  { color: var(--text-main); font-size: 13px; }
.rank-value { color: var(--text-strong); font-weight: 700; font-size: 12px; }

.rank-rail {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: #e2e8f0;
}

.rank-fill {
  height: 8px;
  border-radius: 999px;
  background: var(--success-500);
}

/* Queue */
.queue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.queue-item {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.75);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.queue-item span  { color: var(--text-subtle); font-size: 12px; }
.queue-item strong { color: var(--text-strong); font-size: 20px; }


.dependency-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dependency-list--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}

.dependency-list li {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 8px;
  padding: 5px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(248, 250, 252, 0.75);
}

.dependency-name {
  color: var(--text-main);
  font-size: 12px;
  font-weight: 600;
}

.dependency-section-title {
  color: var(--text-main);
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
}

.dependency-divider {
  margin: 8px 0;
}

/* Event list */
.event-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-list li {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  padding: 8px;
  display: grid;
  grid-template-columns: 96px 1fr auto;
  align-items: center;
  gap: 8px;
  background: rgba(248, 250, 252, 0.7);
}

.event-time  { color: var(--text-subtle); font-size: 12px; }
.event-text  { color: var(--text-main); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Mini rank */
.mini-rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini-rank-list li {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 26px 1fr auto;
  align-items: center;
  gap: 8px;
  background: rgba(248, 250, 252, 0.75);
}

.mini-rank-index { color: var(--primary-500); font-size: 12px; font-weight: 700; }
.mini-rank-name  { color: var(--text-main); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mini-rank-value { color: var(--text-strong); font-size: 13px; }

/* Status badges */
.status-core {
  display: inline-flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 16px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid transparent;
  min-width: 0;
  width: auto;
  max-width: 100%;
}

.status-core.status-ok {
  color: var(--status-badge-success-fg);
  background: var(--status-badge-success-bg);
  border-color: rgba(16, 185, 129, 0.35);
}

.status-core.status-warn {
  color: var(--status-badge-warning-fg);
  background: var(--status-badge-warning-bg);
  border-color: rgba(245, 158, 11, 0.35);
}

.status-core.status-danger {
  color: var(--status-badge-danger-fg);
  background: var(--status-badge-danger-bg);
  border-color: rgba(239, 68, 68, 0.35);
}

.status-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 8px currentColor;
}

.status-text {
  display: -webkit-box;
  flex: 1 1 auto;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.35;
  min-width: 0;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (max-width: 1280px) {
  .status-core {
    padding: 8px 12px;
  }
}

@media (max-width: 1024px) {
  .status-core {
    width: 100%;
  }
}

/* Responsive — clean ladder: 1600 / 1280 / 1024 / 768 */
@media (max-width: 1600px) {
  .bottom-section {
    column-count: 2;
  }
  .cost-bars {
    grid-template-columns: repeat(12, minmax(0, 1fr));
    gap: 4px;
  }
}

@media (max-width: 1280px) {
  .kpi-grid-primary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .middle-section {
    grid-template-columns: 1fr;
  }
  .cost-bars {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
  .panel-main :deep(.el-card__body) {
    min-height: 260px;
  }
  .panel-trend :deep(.el-card__body) {
    min-height: 220px;
  }
}

@media (max-width: 1024px) {
  .cc-header-glass {
    grid-template-columns: minmax(200px, auto) minmax(0, 1fr);
  }
  .header-status {
    grid-column: 1 / -1;
  }
  .header-actions {
    justify-self: end;
  }
  .kpi-grid-primary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .bottom-section {
    column-count: 1;
  }
  .queue-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .event-list li {
    grid-template-columns: 84px 1fr auto;
  }
}

@media (max-width: 768px) {
  .dashboard-home {
    padding: 12px;
    gap: 12px;
  }
  .cc-header-glass {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 14px 16px;
  }
  .header-actions {
    justify-self: start;
  }
  .kpi-grid-primary {
    grid-template-columns: 1fr;
  }
  .kpi-value {
    font-size: 26px;
  }
  .asset-card {
    grid-template-columns: 44px minmax(0, 1fr);
    row-gap: 10px;
  }
  .asset-card .asset-info:nth-of-type(2) {
    grid-column: 1 / -1;
    padding-top: 8px;
    border-top: 1px dashed rgba(148, 163, 184, 0.3);
  }
  .asset-divider {
    display: none;
  }
  .load-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .cost-bars {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
  .queue-grid {
    grid-template-columns: 1fr;
  }
  .event-list li {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }
  .panel-main :deep(.el-card__body),
  .panel-trend :deep(.el-card__body) {
    min-height: 0;
  }
  .node-summary {
    flex-wrap: wrap;
  }
  .node-summary .node-stat {
    flex: 1 1 calc(33.33% - 8px);
  }
}

/* Dark mode */
html.dark .command-center-root {
  background: #0f172a;
}

html.dark .cc-header-glass {
  background: rgba(15, 23, 42, 0.85);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

html.dark .logo-text,
html.dark .panel-header,
html.dark .kpi-value,
html.dark .asset-value,
html.dark .node-load-head {
  color: #f1f5f9;
}

html.dark .kpi-card,
html.dark :deep(.panel-card.el-card) {
  background: rgba(30, 41, 59, 0.82);
  border-color: rgba(71, 85, 105, 0.52);
}

html.dark .kpi-card:hover {
  background: rgba(51, 65, 85, 0.88);
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.3);
}

html.dark :deep(.panel-card .el-card__header) {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

html.dark .load-tile,
html.dark .queue-item,
html.dark .mini-rank-list li,
html.dark .cost-bar-rail,
html.dark .rank-item,
html.dark .event-list li,
html.dark .node-list li,
html.dark .node-stat,
html.dark .asset-card.primary,
html.dark .asset-card.secondary {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(71, 85, 105, 0.5);
}

html.dark .trend-svg {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(71, 85, 105, 0.5);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

html.dark .trend-grid-line {
  stroke: rgba(148, 163, 184, 0.2);
}

html.dark .trend-dot,
html.dark .trend-dot.latency {
  stroke: #0f172a;
}

html.dark .tile-rail {
  background: rgba(15, 23, 42, 0.78);
}

html.dark .cost-bar-rail {
  background: rgba(15, 23, 42, 0.78);
}
</style>
