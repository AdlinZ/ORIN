<template>
  <div class="trace-viewer server-workspace">
    <section class="runtime-command-panel trace-command-panel">
      <div class="runtime-command-head">
        <div class="header-main">
          <div class="header-icon">
            <el-icon><Share /></el-icon>
          </div>
          <div>
            <h2 class="header-title">
              调用链路
            </h2>
            <div class="header-subtitle">
              查看执行链路、步骤耗时与运行指标
            </div>
          </div>
        </div>
      </div>
    </section>

    <el-card class="search-card" shadow="never">
      <div class="search-row">
        <el-input
          v-model="searchTraceId"
          clearable
          placeholder="输入 traceId 搜索调用链路"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" :loading="searching" @click="handleSearch">
          搜索
        </el-button>
        <el-button :icon="RefreshRight" :loading="recentLoading" @click="loadRecentTraces">
          刷新
        </el-button>
      </div>
    </el-card>

    <el-card v-if="!activeTraceId" class="table-card">
      <template #header>
        <div class="card-header">
          <span>最近调用链路</span>
        </div>
      </template>
      <el-table
        v-loading="recentLoading"
        border
        :data="recentTraces"
        empty-text="暂无调用链路数据，请输入 traceId 搜索"
        stripe
      >
        <el-table-column prop="traceId" label="Trace ID" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalSteps" label="步骤数" width="90" />
        <el-table-column prop="failedCount" label="失败数" width="90" />
        <el-table-column prop="totalDuration" label="总耗时 (ms)" width="130" />
        <el-table-column prop="firstStartedAt" label="开始时间" width="180" />
        <el-table-column prop="lastCompletedAt" label="最近更新时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openTrace(row.traceId)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <template v-else>
      <el-card class="trace-meta-card">
        <div class="trace-meta-row">
          <div>
            <div class="trace-meta-label">当前 Trace ID</div>
            <div class="trace-meta-value">{{ activeTraceId }}</div>
          </div>
          <div class="trace-meta-actions">
            <el-button @click="backToRecent">
              返回列表
            </el-button>
            <el-button
              v-if="traceLink.available && traceLink.link"
              type="primary"
              @click="openLangfuseLink"
            >
              Langfuse
            </el-button>
          </div>
        </div>
      </el-card>

      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-label">总步骤数</div>
              <div class="stat-value">{{ stats.totalSteps || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card success">
            <div class="stat-content">
              <div class="stat-label">成功</div>
              <div class="stat-value">{{ stats.successCount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card failed">
            <div class="stat-content">
              <div class="stat-label">失败</div>
              <div class="stat-value">{{ stats.failedCount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-label">总耗时 (ms)</div>
              <div class="stat-value">{{ stats.totalDuration || 0 }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="!detailLoading && traces.length === 0" description="未找到该 traceId 的调用链路" />

      <template v-else>
        <el-card class="chart-card">
          <h3>执行时序图</h3>
          <div ref="timelineChart" class="chart-container" />
        </el-card>

        <el-row :gutter="20" class="chart-row">
          <el-col :span="12">
            <el-card class="chart-card">
              <h3>CPU 使用率</h3>
              <div ref="cpuChart" class="chart-container-small" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="chart-card">
              <h3>内存使用</h3>
              <div ref="memoryChart" class="chart-container-small" />
            </el-card>
          </el-col>
        </el-row>

        <el-card class="table-card">
          <h3>步骤详情</h3>
          <el-table v-loading="detailLoading" border :data="traces" stripe>
            <el-table-column prop="stepName" label="步骤名称" min-width="150" />
            <el-table-column prop="skillName" label="技能名称" min-width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开始时间" width="180" />
            <el-table-column prop="durationMs" label="耗时 (ms)" width="120" />
            <el-table-column prop="cpuUsage" label="CPU %" width="100" />
            <el-table-column prop="memoryUsage" label="内存 (MB)" width="120">
              <template #default="{ row }">
                {{ formatMemory(row.memoryUsage) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="viewDetail(row)">
                  查看详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>
    </template>

    <el-dialog v-model="detailDialogVisible" title="步骤详情" width="800px">
      <div v-if="currentTrace">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="步骤名称">
            {{ currentTrace.stepName }}
          </el-descriptions-item>
          <el-descriptions-item label="技能名称">
            {{ currentTrace.skillName }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentTrace.status)">
              {{ currentTrace.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ currentTrace.durationMs }} ms
          </el-descriptions-item>
          <el-descriptions-item label="CPU 使用率">
            {{ currentTrace.cpuUsage }}%
          </el-descriptions-item>
          <el-descriptions-item label="内存使用">
            {{ formatMemory(currentTrace.memoryUsage) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h4>输入数据</h4>
          <pre class="json-viewer">{{ JSON.stringify(currentTrace.inputData, null, 2) }}</pre>
        </div>

        <div class="detail-section">
          <h4>输出数据</h4>
          <pre class="json-viewer">{{ JSON.stringify(currentTrace.outputData, null, 2) }}</pre>
        </div>

        <div v-if="currentTrace.errorMessage" class="detail-section error-section">
          <h4>错误信息</h4>
          <el-alert type="error" :closable="false">
            <div><strong>错误代码:</strong> {{ currentTrace.errorCode }}</div>
            <div><strong>错误消息:</strong> {{ currentTrace.errorMessage }}</div>
          </el-alert>
          <pre v-if="currentTrace.errorDetails" class="json-viewer">{{ JSON.stringify(currentTrace.errorDetails, null, 2) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRecentTraces, getTrace, getTraceLink, getTraceStats, searchTraces } from '@/api/trace'
import * as echarts from 'echarts'
import { RefreshRight, Search, Share } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeTraceId = ref('')
const searchTraceId = ref('')
const searching = ref(false)
const recentLoading = ref(false)
const detailLoading = ref(false)
const recentTraces = ref([])
const traces = ref([])
const stats = ref({})
const traceLink = ref({})
const detailDialogVisible = ref(false)
const currentTrace = ref(null)

const timelineChart = ref(null)
const cpuChart = ref(null)
const memoryChart = ref(null)

let timelineChartInstance = null
let cpuChartInstance = null
let memoryChartInstance = null

onBeforeUnmount(() => {
  disposeCharts()
})

const loadRecentTraces = async () => {
  recentLoading.value = true
  try {
    recentTraces.value = await getRecentTraces(20)
  } catch (error) {
    ElMessage.error('加载最近调用链路失败: ' + (error.message || error))
  } finally {
    recentLoading.value = false
  }
}

const handleSearch = async () => {
  const traceId = searchTraceId.value.trim()
  if (!traceId) {
    ElMessage.warning('请输入 traceId')
    return
  }

  searching.value = true
  try {
    const result = await searchTraces(traceId)
    if (result?.found === false) {
      resetDetail()
      ElMessage.warning('未找到该 traceId 的调用链路')
      return
    }
    await openTrace(traceId)
  } catch (error) {
    ElMessage.error('搜索调用链路失败: ' + (error.message || error))
  } finally {
    searching.value = false
  }
}

const openTrace = async (traceId) => {
  if (!traceId) return
  if (route.params.traceId !== traceId) {
    try {
      await router.push({ name: 'TraceDetail', params: { traceId } })
    } catch (error) {
      await loadTraceDetail(traceId)
    }
    return
  }
  await loadTraceDetail(traceId)
}

const backToRecent = async () => {
  try {
    await router.push({ name: 'RuntimeTraces' })
  } catch (error) {
    resetDetail()
    await loadRecentTraces()
  }
}

const loadTraceDetail = async (traceId) => {
  activeTraceId.value = traceId
  searchTraceId.value = traceId
  detailLoading.value = true
  try {
    const [traceRows, traceStats, link] = await Promise.all([
      getTrace(traceId),
      getTraceStats(traceId),
      getTraceLink(traceId).catch(() => ({}))
    ])
    traces.value = Array.isArray(traceRows) ? traceRows : []
    stats.value = traceStats || {}
    traceLink.value = link || {}
    await nextTick()
    initCharts()
  } catch (error) {
    traces.value = []
    stats.value = {}
    traceLink.value = {}
    disposeCharts()
    ElMessage.error('加载追踪数据失败: ' + (error.message || error))
  } finally {
    detailLoading.value = false
  }
}

const resetDetail = () => {
  activeTraceId.value = ''
  traces.value = []
  stats.value = {}
  traceLink.value = {}
  currentTrace.value = null
  disposeCharts()
}

const initCharts = () => {
  disposeCharts()
  if (traces.value.length === 0) return
  initTimelineChart()
  initCpuChart()
  initMemoryChart()
}

const disposeCharts = () => {
  timelineChartInstance?.dispose()
  cpuChartInstance?.dispose()
  memoryChartInstance?.dispose()
  timelineChartInstance = null
  cpuChartInstance = null
  memoryChartInstance = null
}

const initTimelineChart = () => {
  if (!timelineChart.value) return

  timelineChartInstance = echarts.init(timelineChart.value)

  const data = traces.value.map((trace, index) => {
    const startTime = new Date(trace.startedAt).getTime()
    const endTime = new Date(trace.completedAt || trace.startedAt).getTime()
    return {
      name: trace.stepName || trace.skillName || `Step ${index + 1}`,
      value: [index, startTime, endTime, trace.durationMs || 0],
      itemStyle: {
        color: trace.status === 'SUCCESS'
          ? '#00BFA5'
          : trace.status === 'FAILED'
            ? '#EF4444'
            : '#94A3B8'
      }
    }
  })

  timelineChartInstance.setOption({
    tooltip: {
      formatter: (params) => `${params.name}<br/>耗时: ${params.value[3]} ms`
    },
    grid: {
      left: '15%',
      right: '10%',
      top: '10%',
      bottom: '10%'
    },
    xAxis: {
      type: 'time',
      axisLabel: { formatter: '{HH}:{mm}:{ss}' }
    },
    yAxis: {
      type: 'category',
      data: traces.value.map((t, index) => t.stepName || t.skillName || `Step ${index + 1}`)
    },
    series: [{
      type: 'custom',
      renderItem: (params, api) => {
        const categoryIndex = api.value(0)
        const start = api.coord([api.value(1), categoryIndex])
        const end = api.coord([api.value(2), categoryIndex])
        const height = api.size([0, 1])[1] * 0.6

        return {
          type: 'rect',
          shape: {
            x: start[0],
            y: start[1] - height / 2,
            width: Math.max(end[0] - start[0], 2),
            height
          },
          style: api.style()
        }
      },
      encode: { x: [1, 2], y: 0 },
      data
    }]
  })
}

const initCpuChart = () => {
  if (!cpuChart.value) return

  cpuChartInstance = echarts.init(cpuChart.value)
  cpuChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: traces.value.map((t, index) => t.stepName || t.skillName || `Step ${index + 1}`)
    },
    yAxis: { type: 'value', name: 'CPU %' },
    series: [{
      data: traces.value.map(t => Number(t.cpuUsage || 0)),
      type: 'line',
      smooth: true,
      areaStyle: { color: 'var(--orin-primary)', opacity: 0.3 },
      itemStyle: { color: 'var(--orin-primary)' }
    }]
  })
}

const initMemoryChart = () => {
  if (!memoryChart.value) return

  memoryChartInstance = echarts.init(memoryChart.value)
  memoryChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const value = formatMemory(params[0].value)
        return `${params[0].name}<br/>内存: ${value}`
      }
    },
    xAxis: {
      type: 'category',
      data: traces.value.map((t, index) => t.stepName || t.skillName || `Step ${index + 1}`)
    },
    yAxis: {
      type: 'value',
      name: 'Memory (MB)',
      axisLabel: { formatter: value => (value / 1024 / 1024).toFixed(0) }
    },
    series: [{
      data: traces.value.map(t => Number(t.memoryUsage || 0)),
      type: 'bar',
      itemStyle: { color: '#14B8A6' }
    }]
  })
}

const openLangfuseLink = () => {
  if (traceLink.value?.link) {
    window.open(traceLink.value.link, '_blank', 'noopener,noreferrer')
  }
}

const viewDetail = (trace) => {
  currentTrace.value = trace
  detailDialogVisible.value = true
}

const formatMemory = (value) => {
  return value ? `${(Number(value) / 1024 / 1024).toFixed(2)} MB` : '-'
}

const getStatusTagType = (status) => {
  const types = {
    SUCCESS: 'success',
    FAILED: 'danger',
    RUNNING: 'primary',
    PENDING: 'info',
    SKIPPED: 'warning'
  }
  return types[status] || 'info'
}

watch(
  () => route.params.traceId,
  async (traceId) => {
    const nextTraceId = typeof traceId === 'string' ? traceId : ''
    if (nextTraceId) {
      await loadTraceDetail(nextTraceId)
    } else {
      resetDetail()
      await loadRecentTraces()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
@import '../Monitor/server-monitor-shared.css';

.trace-viewer {
  padding: 20px;
  min-height: 100%;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.56), transparent 260px);
}

.search-card,
.trace-meta-card,
.stats-row,
.chart-card,
.chart-row {
  margin-bottom: 16px;
}

.search-card,
.trace-meta-card,
.chart-card,
.table-card,
.stat-card {
  overflow: hidden;
  border-radius: var(--monitor-radius) !important;
  border: 1px solid var(--monitor-border, #e2e8f0) !important;
  background: var(--monitor-surface, #ffffff);
  box-shadow: var(--monitor-shadow-soft);
}

.search-card :deep(.el-card__body),
.trace-meta-card :deep(.el-card__body),
.chart-card :deep(.el-card__body),
.table-card :deep(.el-card__body),
.stat-card :deep(.el-card__body) {
  padding: 18px 20px;
}

.trace-command-panel .header-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 10px;
  border: 1px solid rgba(203, 213, 225, 0.78);
  background: rgba(255, 255, 255, 0.72);
  color: var(--monitor-accent);
  font-size: 18px;
}

.search-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.trace-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.trace-meta-label {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-bottom: 6px;
}

.trace-meta-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  word-break: break-all;
}

.trace-meta-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.stat-card {
  text-align: center;
}

.stat-card.success {
  border-left: 4px solid var(--success-color);
}

.stat-card.failed {
  border-left: 4px solid var(--error-color);
}

.stat-content {
  padding: 6px;
}

.stat-label {
  font-size: 14px;
  color: var(--neutral-gray-500);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 750;
  color: var(--neutral-gray-900);
}

.chart-card h3,
.table-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
}

.chart-container {
  width: 100%;
  height: 400px;
}

.chart-container-small {
  width: 100%;
  height: 300px;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h4 {
  margin: 0 0 10px 0;
  font-size: 16px;
  font-weight: 600;
}

.json-viewer {
  background: var(--neutral-gray-50);
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.error-section {
  border-left: 4px solid var(--error-500);
  padding-left: 15px;
}

@media (max-width: 768px) {
  .trace-viewer {
    padding: 12px;
  }

  .search-row,
  .trace-meta-row,
  .trace-meta-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
