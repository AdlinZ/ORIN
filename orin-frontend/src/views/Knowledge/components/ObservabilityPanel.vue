<template>
  <div class="observability-panel">
    <!-- Stats Cards -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon pending">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.pending || 0 }}</div>
          <div class="stat-label">排队中</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon running">
          <el-icon><Loading /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.running || 0 }}</div>
          <div class="stat-label">处理中</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon success">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.success || 0 }}</div>
          <div class="stat-label">成功</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon failed">
          <el-icon><CircleClose /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.failed || 0 }}</div>
          <div class="stat-label">失败</div>
        </div>
      </div>
    </div>

    <!-- Queue Length Chart -->
    <div class="chart-section">
      <h4>队列长度趋势</h4>
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <!-- Task List -->
    <div class="task-section">
      <div class="section-header">
        <h4>任务日志</h4>
        <el-button-group size="small">
          <el-button
            :type="filterStatus === 'all' ? 'primary' : ''"
            @click="filterStatus = 'all'"
          >
            全部
          </el-button>
          <el-button
            :type="filterStatus === 'FAILED' ? 'danger' : ''"
            @click="filterStatus = 'FAILED'"
          >
            失败
          </el-button>
          <el-button @click="loadTasks">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-button-group>
      </div>

      <el-table
        :data="filteredTasks"
        stripe
        style="width: 100%"
        :loading="loading"
      >
        <el-table-column prop="id" label="任务ID" width="120" show-overflow-tooltip />
        <el-table-column prop="taskType" label="任务类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ getTaskTypeText(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="60" align="center" />
        <el-table-column prop="executionTimeMs" label="耗时" width="80">
          <template #default="{ row }">
            {{ formatDuration(row.executionTimeMs) }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              type="primary"
              text
              size="small"
              @click="retryTask(row.id)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="totalTasks"
        layout="prev, pager, next"
        style="margin-top: 16px; justify-content: center"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Clock,
  Loading,
  CircleCheck,
  CircleClose,
  Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getKnowledgeTasks, getQueueStats, retryKnowledgeTask } from '@/api/knowledge'

const props = defineProps({
  kbId: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const tasks = ref([])
const stats = ref({})
const filterStatus = ref('all')
const currentPage = ref(1)
const pageSize = ref(20)
const totalTasks = ref(0)
const chartRef = ref(null)
const chartHistory = ref([])

let chart = null
let statsTimer = null

// Computed
const filteredTasks = computed(() => {
  if (filterStatus.value === 'all') return tasks.value
  return tasks.value.filter(t => t.status === filterStatus.value)
})

// Load tasks
const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeTasks({
      page: currentPage.value - 1,
      size: pageSize.value,
      kbId: props.kbId
    })
    tasks.value = res.data?.content || res.data || []
    totalTasks.value = res.data?.totalElements || tasks.value.length
  } catch (err) {
    console.error('Failed to load tasks:', err)
  } finally {
    loading.value = false
  }
}

// Load queue stats
const loadStats = async () => {
  try {
    const res = await getQueueStats()
    const newStats = res.data || res || {}

    // Update chart history
    const now = new Date().toLocaleTimeString()
    chartHistory.value.push({
      time: now,
      pending: newStats.pending || 0,
      running: newStats.running || 0,
      failed: newStats.failed || 0
    })

    // Keep only last 20 data points
    if (chartHistory.value.length > 20) {
      chartHistory.value.shift()
    }

    stats.value = newStats
    updateChart()
  } catch (err) {
    console.error('Failed to load stats:', err)
  }
}

// Retry task
const retryTask = async (taskId) => {
  try {
    await retryKnowledgeTask(taskId)
    ElMessage.success('已提交重试')
    loadTasks()
    loadStats()
  } catch (err) {
    ElMessage.error('重试失败')
  }
}

// Chart
const updateChart = () => {
  if (!chart || chartHistory.value.length === 0) return

  const times = chartHistory.value.map(h => h.time)
  const pendingData = chartHistory.value.map(h => h.pending)
  const runningData = chartHistory.value.map(h => h.running)
  const failedData = chartHistory.value.map(h => h.failed)

  chart.setOption({
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: [
      {
        name: '排队中',
        type: 'line',
        data: pendingData,
        smooth: true,
        itemStyle: { color: '#E6A23C' }
      },
      {
        name: '处理中',
        type: 'line',
        data: runningData,
        smooth: true,
        itemStyle: { color: '#409EFF' }
      },
      {
        name: '失败',
        type: 'line',
        data: failedData,
        smooth: true,
        itemStyle: { color: '#F56C6C' }
      }
    ]
  })
}

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['排队中', '处理中', '失败'],
      bottom: 0
    },
    xAxis: {
      type: 'category',
      data: [],
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: []
  })
}

// Helpers
const getTaskTypeText = (type) => {
  const map = {
    'PARSING': '解析',
    'CHUNKING': '分块',
    'VECTORIZING': '向量化',
    'GRAPH_EXTRACT': '图谱抽取'
  }
  return map[type] || type
}

const getStatusText = (status) => {
  const map = {
    'PENDING': '等待',
    'RUNNING': '运行中',
    'SUCCESS': '成功',
    'FAILED': '失败',
    'RETRYING': '重试中'
  }
  return map[status] || status
}

const getStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'RUNNING': 'primary',
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'RETRYING': 'warning'
  }
  return map[status] || 'info'
}

const formatDuration = (ms) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

// Lifecycle
onMounted(() => {
  initChart()
  loadTasks()
  loadStats()

  // Poll for stats every 5 seconds
  statsTimer = setInterval(loadStats, 5000)
})

onUnmounted(() => {
  if (statsTimer) {
    clearInterval(statsTimer)
  }
  chart?.dispose()
})
</script>

<style scoped>
.observability-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 16px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.stat-icon {
  font-size: 32px;
  padding: 12px;
  border-radius: 8px;
}

.stat-icon.pending {
  color: #E6A23C;
  background: #fdf6ec;
}

.stat-icon.running {
  color: #409EFF;
  background: #ecf5ff;
}

.stat-icon.success {
  color: #67C23A;
  background: #f0f9eb;
}

.stat-icon.failed {
  color: #F56C6C;
  background: #fef0f0;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.chart-section {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.chart-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.chart-container {
  height: 200px;
}

.task-section {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
</style>
