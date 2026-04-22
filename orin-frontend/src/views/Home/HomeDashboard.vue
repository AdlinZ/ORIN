<template>
  <div class="dashboard-home command-center-root">
    <header class="page-header cc-header-glass">
      <!-- 1. 品牌区 -->
      <div class="header-brand">
        <h1 class="logo-text">ORIN<span class="logo-dot">.</span>ASIA</h1>
        <span class="header-divider"></span>
        <p class="header-subtitle">查看平台整体健康状态、调用趋势与关键异常</p>
      </div>

      <!-- 2. 状态区 -->
      <div class="header-status">
        <div class="status-core" :class="healthStatusClass">
          <span class="status-dot"></span>
          <span class="status-text">
            平台状态：{{ healthStatusText }} · 最近检测：{{ lastHealthCheck }} · 运行时长：{{ uptimeText }}
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
        <el-card class="panel-card premium-card panel-trend" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>近 7 天调用趋势</span>
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
        </el-card>

        <el-card class="panel-card premium-card panel-trend" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>平均时延趋势</span>
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
          <el-empty v-else :description="todayCostEmptyDescription" :image-size="72" />
        </el-card>
      </div>

      <!-- 右侧：节点健康 + 平台态势 -->
      <div class="middle-col-side">
        <el-card class="panel-card premium-card panel-main" shadow="never">
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
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
            <el-empty v-else description="当前节点暂无硬件监控数据" :image-size="56" />
          </div>
        </el-card>

      </div>
    </section>

    <!-- 底部三列 -->
    <section class="bottom-section">
      <!-- 成本与负载 -->
      <div class="bottom-col">
        <el-card class="panel-card premium-card panel-medium" shadow="never">
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
          <el-empty v-else :description="todayCostEmptyDescription" :image-size="72" />
        </el-card>

        <el-card class="panel-card premium-card" shadow="never">
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
        <el-card class="panel-card premium-card panel-medium" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>请求结构与负载占比</span>
              <span class="panel-sub">环形图</span>
            </div>
          </template>
          <div class="donut-grid">
            <div class="donut-block">
              <div class="donut-ring" :style="{ background: requestDonutGradient }">
                <div class="donut-inner">
                  <strong>{{ requestSuccessRate }}%</strong>
                  <span>成功率</span>
                </div>
              </div>
              <div class="donut-legend">
                <span><i class="dot success" />成功 {{ requestSummary.success }}</span>
                <span><i class="dot danger" />失败 {{ requestSummary.failure }}</span>
              </div>
            </div>
            <div class="donut-block">
              <div class="donut-ring" :style="{ background: resourceDonutGradient }">
                <div class="donut-inner">
                  <strong>{{ resourceUsageAvg.toFixed(1) }}%</strong>
                  <span>平均负载</span>
                </div>
              </div>
              <div class="donut-legend">
                <span><i class="dot cpu" />CPU {{ safeNumber(hardware.cpuUsage).toFixed(1) }}%</span>
                <span><i class="dot gpu" />GPU {{ safeNumber(hardware.gpuUsage).toFixed(1) }}%</span>
                <span><i class="dot mem" />内存 {{ safeNumber(hardware.memoryUsage).toFixed(1) }}%</span>
                <span><i class="dot disk" />磁盘 {{ safeNumber(hardware.diskUsage).toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="panel-card premium-card" shadow="never">
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
        </el-card>

        <el-card class="panel-card premium-card" shadow="never">
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
        <el-card class="panel-card premium-card panel-main" shadow="never">
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
        </el-card>

        <el-card class="panel-card premium-card" shadow="never">
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
        </el-card>

        <el-card class="panel-card premium-card" shadow="never">
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
          <el-empty v-else :description="UI_TEXT.common.noData" :image-size="72" />
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
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
} from '@/api/monitor'
import { UI_TEXT } from '@/constants/uiText'

const loading = ref(false)
const isRefreshing = ref(false)
const summaryData = ref({})
const uptimeMs = ref(0)
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
const hardwareSnapshotValid = ref(false)
const errorTypeTop = ref([])
const slowRequestTop = ref([])
const todayCostTrendRaw = ref([])
const queueStats = ref({ pending: 0, running: 0, failed: 0 })
const latencyTrendData = ref([])
const requestSummary = ref({ success: 0, failure: 0 })

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
  const normalizeStatus = (node) => String(node?.status || node?.state || '').toUpperCase()
  const online = serverNodes.value.filter((node) => {
    const status = normalizeStatus(node)
    if (!status) return true
    return status.includes('UP') || status.includes('ONLINE') || status.includes('HEALTHY')
  })
  const nonLocalOnline = online.find((node) => String(node?.id || '').toLowerCase() !== 'local')
  if (nonLocalOnline?.id) return String(nonLocalOnline.id)
  const nonLocalAny = serverNodes.value.find((node) => String(node?.id || '').toLowerCase() !== 'local')
  if (nonLocalAny?.id) return String(nonLocalAny.id)
  return null
})
const totalNodesCount = computed(() => (Array.isArray(serverNodes.value) ? serverNodes.value.length : 0))
const activeNodesCount = computed(() => {
  if (!Array.isArray(serverNodes.value) || !serverNodes.value.length) return 0
  return serverNodes.value.filter((node) => {
    const status = String(node?.status || node?.state || '').toUpperCase()
    return status.includes('UP') || status.includes('ONLINE') || status.includes('HEALTHY')
  }).length
})
const offlineNodesCount = computed(() => Math.max(totalNodesCount.value - activeNodesCount.value, 0))
const normalizeNodeStatus = (node) => {
  const raw = String(node?.status || node?.state || '').toUpperCase()
  if (raw.includes('UP') || raw.includes('ONLINE') || raw.includes('HEALTHY')) {
    return { statusClass: 'ok', statusText: '在线' }
  }
  if (raw.includes('WARN') || raw.includes('DEGRADED')) {
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
const requestSuccessRate = computed(() => {
  const total = safeNumber(requestSummary.value.success) + safeNumber(requestSummary.value.failure)
  if (!total) return 0
  return Math.round((safeNumber(requestSummary.value.success) / total) * 100)
})
const requestDonutGradient = computed(() => {
  const angle = (requestSuccessRate.value / 100) * 360
  return `conic-gradient(#10b981 0deg ${angle}deg, #ef4444 ${angle}deg 360deg)`
})
const resourceUsageAvg = computed(() => {
  const values = [
    safeNumber(hardware.value?.cpuUsage),
    safeNumber(hardware.value?.gpuUsage),
    safeNumber(hardware.value?.memoryUsage),
    safeNumber(hardware.value?.diskUsage),
  ]
  return values.reduce((sum, value) => sum + value, 0) / values.length
})
const resourceDonutGradient = computed(() => {
  const raw = [
    safeNumber(hardware.value?.cpuUsage),
    safeNumber(hardware.value?.gpuUsage),
    safeNumber(hardware.value?.memoryUsage),
    safeNumber(hardware.value?.diskUsage),
  ]
  const fallback = [25, 25, 25, 25]
  const values = raw.some((item) => item > 0) ? raw : fallback
  const total = Math.max(values.reduce((sum, value) => sum + value, 0), 1)
  const cpuAngle = (values[0] / total) * 360
  const gpuAngle = cpuAngle + (values[1] / total) * 360
  const memAngle = gpuAngle + (values[2] / total) * 360
  return `conic-gradient(#0ea5e9 0deg ${cpuAngle}deg, #8b5cf6 ${cpuAngle}deg ${gpuAngle}deg, #22c55e ${gpuAngle}deg ${memAngle}deg, #f59e0b ${memAngle}deg 360deg)`
})

const uptimeText = computed(() => {
  const totalSeconds = Math.floor(safeNumber(uptimeMs.value) / 1000)
  if (totalSeconds <= 0) return '暂无数据'
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  if (days > 0) return `${days}天 ${hours}小时 ${minutes}分钟`
  if (hours > 0) return `${hours}小时 ${minutes}分钟`
  return `${minutes}分钟`
})

const safeNumber = (value, fallback = 0) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
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
  if (numValue >= 85) return 'linear-gradient(90deg, var(--progress-critical-start), var(--progress-critical-end))'
  if (numValue >= 70) return 'linear-gradient(90deg, var(--progress-warning-start), var(--progress-warning-end))'
  return 'linear-gradient(90deg, var(--success-400), var(--success-500))'
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
    const latency = safeNumber(item.latencyMs, safeNumber(item.durationMs, safeNumber(item.responseMs)))
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
  let successCount = 0
  let failureCount = 0

  historyList.forEach((item) => {
    const isSuccess = item.success !== false
    if (!isSuccess) {
      failureCount += 1
      const code = item.errorCode || item.errorType || item.statusCode || 'UNKNOWN'
      errorMap.set(code, (errorMap.get(code) || 0) + 1)
    } else {
      successCount += 1
    }

    const latency = safeNumber(item.latencyMs, safeNumber(item.durationMs, safeNumber(item.responseMs)))
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

  requestSummary.value = { success: successCount, failure: failureCount }
}

const hydrateHardwareSnapshot = async (fallbackHardware) => {
  hardwareSnapshotValid.value = false
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
  snapshotTimeText.value = fallbackTime ? formatCheckTime(fallbackTime) : '--:--:--'
  hardwareSnapshotValid.value = hasFallbackMetrics && Boolean(fallbackTime)
}


const loadDashboardData = async () => {
  loading.value = true
  isRefreshing.value = true
  try {
    const [summaryRes, agentsRes, tokenHistoryRes, latencyRes, hardwareRes, healthRes, distributionRes, nodesRes] = await Promise.all([
      getGlobalSummary(),
      getAgentList(),
      getTokenHistory({ size: 200 }),
      getLatencyStats().catch(() => ({})),
      getServerHardware().catch(() => ({})),
      getSystemHealth().catch(() => ({})),
      getTokenDistribution().catch(() => ([])),
      getServerNodes().catch(() => ([])),
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

    const activeAgents = agents.filter((item) => item.enabled !== false && item.status !== 'OFFLINE').length
    const todayCalls = safeNumber(summary.daily_requests, historyList.filter((item) => dayjs(item.createdAt || item.timestamp).isSame(dayjs(), 'day')).length)
    const avgLatencyValue = safeNumber(summary.avgLatencyMs, safeNumber(summary.avg_latency_ms, safeNumber(latencyData.avgLatency, safeNumber(summary.avg_latency))))
    const alertCount = safeNumber(summary.alertCount, safeNumber(summary.highLoadAgents))

    summaryData.value = summary
    serverNodes.value = Array.isArray(nodesData) ? nodesData : []
    await hydrateHardwareSnapshot(hardware)
    uptimeMs.value = safeNumber(summary.system_uptime, safeNumber(summary.systemUptime))

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
    loading.value = false
    isRefreshing.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

const handleRefresh = () => {
  loadDashboardData()
}

onMounted(() => {
  loadDashboardData()
  window.addEventListener('page-refresh', handleRefresh)
})

onBeforeUnmount(() => {
  window.removeEventListener('page-refresh', handleRefresh)
})
</script>

<style scoped>
.command-center-root {
  --bg-page: #f4f7fb;
  --bg-card: #ffffff;
  --bg-card-soft: #f8fbfd;
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
  border-radius: 16px;
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-soft);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.05);
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.logo-text {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.5px;
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
  justify-content: flex-start;
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
  border-radius: 14px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--border-soft);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.1);
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

/* 底部三列 */
.bottom-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.bottom-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
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
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8) 0%, rgba(241, 245, 249, 0.5) 100%);
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
  filter: drop-shadow(0 3px 5px rgba(13, 148, 136, 0.2));
}

.trend-line.latency {
  stroke: var(--info-500);
  filter: drop-shadow(0 3px 5px rgba(37, 99, 235, 0.2));
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
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02);
}

.node-stat::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 3px;
}

.node-stat.ok::before { background: linear-gradient(90deg, #22c55e, #86efac); }
.node-stat.warn::before { background: linear-gradient(90deg, #f59e0b, #fcd34d); }
.node-stat.danger::before { background: linear-gradient(90deg, #ef4444, #fca5a5); }

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
  background: linear-gradient(135deg, rgba(255,255,255,0.9) 0%, rgba(248, 250, 252, 0.6) 100%);
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

.tile-fill.cpu  { background: linear-gradient(90deg, var(--info-400), var(--info-500)); }
.tile-fill.gpu  { background: linear-gradient(90deg, var(--accent-400), var(--accent-500)); }
.tile-fill.memory { background: linear-gradient(90deg, var(--success-400), var(--success-500)); }
.tile-fill.disk  { background: linear-gradient(90deg, var(--warning-400), var(--warning-500)); }
.tile-fill.warning  { background: linear-gradient(90deg, var(--progress-warning-start), var(--progress-warning-end)) !important; }
.tile-fill.critical { background: linear-gradient(90deg, var(--progress-critical-start), var(--progress-critical-end)) !important; }

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
  background: linear-gradient(180deg, var(--info-400) 0%, var(--primary-500) 100%);
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
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  gap: 16px;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative;
  overflow: hidden;
}

.asset-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  opacity: 0.1;
  pointer-events: none;
}

.asset-card.primary {
  background: linear-gradient(135deg, #ffffff 0%, #f1f5f9 100%);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}
.asset-card.primary::before { background: radial-gradient(circle at top right, #0ea5e9, transparent 60%); }
.asset-card.primary .asset-icon { background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%); color: #0284c7; }

.asset-card.secondary {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}
.asset-card.secondary::before { background: radial-gradient(circle at top right, #10b981, transparent 60%); }
.asset-card.secondary .asset-icon { background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%); color: #059669; }

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
  margin: 0 8px;
}

/* Donut */
.donut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.donut-block {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.75);
  padding: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.donut-ring {
  width: 110px;
  height: 110px;
  border-radius: 999px;
  display: grid;
  place-items: center;
}

.donut-inner {
  width: 72px;
  height: 72px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.donut-inner strong { color: var(--text-strong); font-size: 16px; }
.donut-inner span { color: var(--text-subtle); font-size: 11px; }

.donut-legend {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.donut-legend span {
  color: var(--text-main);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.donut-legend .dot {
  width: 8px; height: 8px;
  border-radius: 999px;
  display: inline-block;
}

.donut-legend .dot.success { background: #10b981; }
.donut-legend .dot.danger  { background: #ef4444; }
.donut-legend .dot.cpu     { background: #0ea5e9; }
.donut-legend .dot.gpu     { background: #8b5cf6; }
.donut-legend .dot.mem     { background: #22c55e; }
.donut-legend .dot.disk    { background: #f59e0b; }

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
  background: linear-gradient(90deg, var(--success-500) 0%, var(--info-500) 100%);
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
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid transparent;
  min-width: 0;
  width: 100%;
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
  display: block;
  flex: 1;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

@media (max-width: 1320px) {
  .status-core {
    padding: 8px 12px;
  }

  .status-text {
    white-space: normal;
    word-break: break-word;
    overflow-wrap: anywhere;
    line-height: 1.35;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

/* Responsive */
@media (max-width: 1200px) {
  .kpi-grid-primary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1560px) {
  .bottom-section {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .asset-card {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 10px;
  }

  .asset-divider {
    display: none;
  }

  .asset-info {
    flex: 1 1 calc(50% - 6px);
  }
}

@media (max-width: 1240px) {
  .middle-section {
    grid-template-columns: 1fr;
  }

  .bottom-section {
    grid-template-columns: 1fr;
  }

  .donut-grid {
    grid-template-columns: 1fr;
  }

  .queue-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .event-list li {
    grid-template-columns: 84px 1fr auto;
  }
}

@media (max-width: 900px) {
  .dashboard-home {
    padding: 12px;
  }

  .page-header {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .header-actions {
    justify-self: start;
  }

  .kpi-grid-primary,
  .bottom-section {
    grid-template-columns: 1fr;
  }

  .panel-main :deep(.el-card__body),
  .panel-trend :deep(.el-card__body) {
    min-height: 0;
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
html.dark .donut-block,
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
</style>
