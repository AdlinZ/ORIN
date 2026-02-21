<template>
  <div class="trace-viewer">
    <el-card class="header-card">
      <h2>调用链路追踪</h2>
    </el-card>

    <!-- 追踪统计 -->
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

    <!-- 时序图 -->
    <el-card class="chart-card">
      <h3>执行时序图</h3>
      <div ref="timelineChart" class="chart-container"></div>
    </el-card>

    <!-- 性能指标图 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card class="chart-card">
          <h3>CPU 使用率</h3>
          <div ref="cpuChart" class="chart-container-small"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <h3>内存使用</h3>
          <div ref="memoryChart" class="chart-container-small"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 追踪详情表格 -->
    <el-card class="table-card">
      <h3>步骤详情</h3>
      <el-table border :data="traces" stripe>
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
            {{ row.memoryUsage ? (row.memoryUsage / 1024 / 1024).toFixed(2) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="步骤详情" width="800px">
      <div v-if="currentTrace">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="步骤名称">{{ currentTrace.stepName }}</el-descriptions-item>
          <el-descriptions-item label="技能名称">{{ currentTrace.skillName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentTrace.status)">
              {{ currentTrace.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ currentTrace.durationMs }} ms</el-descriptions-item>
          <el-descriptions-item label="CPU 使用率">{{ currentTrace.cpuUsage }}%</el-descriptions-item>
          <el-descriptions-item label="内存使用">
            {{ currentTrace.memoryUsage ? (currentTrace.memoryUsage / 1024 / 1024).toFixed(2) + ' MB' : '-' }}
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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import * as echarts from 'echarts'

const route = useRoute()
const traces = ref([])
const stats = ref({})
const detailDialogVisible = ref(false)
const currentTrace = ref(null)

const timelineChart = ref(null)
const cpuChart = ref(null)
const memoryChart = ref(null)

let timelineChartInstance = null
let cpuChartInstance = null
let memoryChartInstance = null

onMounted(async () => {
  const traceId = route.params.traceId
  if (traceId) {
    await loadTraces(traceId)
    await loadStats(traceId)
    await nextTick()
    initCharts()
  }
})

const loadTraces = async (traceId) => {
  try {
    const response = await axios.get(`/api/traces/${traceId}`)
    traces.value = response.data
  } catch (error) {
    ElMessage.error('加载追踪数据失败: ' + error.message)
  }
}

const loadStats = async (traceId) => {
  try {
    const response = await axios.get(`/api/traces/${traceId}/stats`)
    stats.value = response.data
  } catch (error) {
    ElMessage.error('加载统计数据失败: ' + error.message)
  }
}

const initCharts = () => {
  initTimelineChart()
  initCpuChart()
  initMemoryChart()
}

const initTimelineChart = () => {
  if (!timelineChart.value) return

  timelineChartInstance = echarts.init(timelineChart.value)

  const data = traces.value.map((trace, index) => ({
    name: trace.stepName,
    value: [
      index,
      new Date(trace.startedAt).getTime(),
      new Date(trace.completedAt || trace.startedAt).getTime(),
      trace.durationMs
    ],
    itemStyle: {
      color: trace.status === 'SUCCESS' ? '#00BFA5' : 
             trace.status === 'FAILED' ? '#EF4444' : '#94A3B8'
    }
  }))

  const option = {
    tooltip: {
      formatter: (params) => {
        return `${params.name}<br/>耗时: ${params.value[3]} ms`
      }
    },
    grid: {
      left: '15%',
      right: '10%',
      top: '10%',
      bottom: '10%'
    },
    xAxis: {
      type: 'time',
      axisLabel: {
        formatter: '{HH}:{mm}:{ss}'
      }
    },
    yAxis: {
      type: 'category',
      data: traces.value.map(t => t.stepName)
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
            width: end[0] - start[0],
            height: height
          },
          style: api.style()
        }
      },
      encode: {
        x: [1, 2],
        y: 0
      },
      data: data
    }]
  }

  timelineChartInstance.setOption(option)
}

const initCpuChart = () => {
  if (!cpuChart.value) return

  cpuChartInstance = echarts.init(cpuChart.value)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: traces.value.map(t => t.stepName)
    },
    yAxis: {
      type: 'value',
      name: 'CPU %'
    },
    series: [{
      data: traces.value.map(t => t.cpuUsage || 0),
      type: 'line',
      smooth: true,
      areaStyle: {
        color: 'var(--orin-primary)',
        opacity: 0.3
      },
      itemStyle: { color: 'var(--orin-primary)' }
    }]
  }

  cpuChartInstance.setOption(option)
}

const initMemoryChart = () => {
  if (!memoryChart.value) return

  memoryChartInstance = echarts.init(memoryChart.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const value = (params[0].value / 1024 / 1024).toFixed(2)
        return `${params[0].name}<br/>内存: ${value} MB`
      }
    },
    xAxis: {
      type: 'category',
      data: traces.value.map(t => t.stepName)
    },
    yAxis: {
      type: 'value',
      name: 'Memory (MB)',
      axisLabel: {
        formatter: (value) => (value / 1024 / 1024).toFixed(0)
      }
    },
    series: [{
      data: traces.value.map(t => t.memoryUsage || 0),
      type: 'bar',
      itemStyle: {
        color: '#14B8A6'
      }
    }]
  }

  memoryChartInstance.setOption(option)
}

const viewDetail = (trace) => {
  currentTrace.value = trace
  detailDialogVisible.value = true
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
</script>

<style scoped>
.trace-viewer {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header-card h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.stats-row {
  margin-bottom: 20px;
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
  padding: 10px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-card h3 {
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

.chart-row {
  margin-bottom: 20px;
}

.table-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
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
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.error-section {
  border-left: 4px solid #F56C6C;
  padding-left: 15px;
}
</style>
