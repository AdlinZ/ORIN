<template>
  <div class="statistics-container">
    <OrinPageShell
      domain="系统控制"
      title="统计分析"
      description="查看系统使用数据、Token消耗和任务执行统计"
      icon="DataAnalysis"
    >
      <template #actions>
        <el-button type="primary" @click="handleExport">
          <el-icon><Download /></el-icon>
          导出报表
        </el-button>
      </template>
    </OrinPageShell>

    <section class="statistics-workbar">
      <el-form :inline="true">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="handleDateChange"
          />
        </el-form-item>
      </el-form>
    </section>

    <!-- 概览卡片 -->
    <el-row :gutter="20" class="overview-cards">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: var(--orin-primary)">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">
                活跃用户
              </div>
              <div class="stat-value">
                {{ overview.totalActiveUsers || 0 }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: var(--success-500)">
              <el-icon><ChatLineRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">
                API 调用
              </div>
              <div class="stat-value">
                {{ overview.totalApiCalls || 0 }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: var(--warning-500)">
              <el-icon><Coin /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">
                Token 消耗
              </div>
              <div class="stat-value">
                {{ formatNumber(overview.totalTokens) }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: var(--error-500)">
              <el-icon><Grid /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">
                任务执行
              </div>
              <div class="stat-value">
                {{ overview.totalTasks || 0 }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图表 -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <section class="analysis-panel">
          <div class="panel-heading">
            <div>
              <h2>每日 API 调用趋势</h2>
              <p>按所选日期范围观察调用规模变化。</p>
            </div>
          </div>
          <div v-loading="loading">
            <div ref="apiChartRef" style="height: 300px" />
          </div>
        </section>
      </el-col>
      <el-col :xs="24" :lg="12">
        <section class="analysis-panel">
          <div class="panel-heading">
            <div>
              <h2>Token 消耗趋势</h2>
              <p>查看工作负载和模型调用带来的消耗变化。</p>
            </div>
          </div>
          <div v-loading="loading">
            <div ref="tokenChartRef" style="height: 300px" />
          </div>
        </section>
      </el-col>
    </el-row>

    <!-- 详细统计表格 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="12">
        <OrinDataTable compact class="statistics-table">
          <template #header>
            <div class="table-heading">
              <div>
                <strong>任务状态分布</strong>
                <span>按状态汇总所选时间范围内的任务执行。</span>
              </div>
            </div>
          </template>
          <OrinAsyncState
            :status="loading ? 'loading' : taskStats.length > 0 ? 'success' : 'empty'"
            empty-text="暂无任务状态统计"
            @retry="loadData"
          >
            <el-table :data="taskStats">
              <el-table-column prop="status" label="状态">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="count" label="数量" />
              <el-table-column label="占比">
                <template #default="{ row }">
                  {{ calculatePercentage(row.count, totalTaskCount) }}%
                </template>
              </el-table-column>
            </el-table>
          </OrinAsyncState>
        </OrinDataTable>
      </el-col>
      <el-col :xs="24" :lg="12">
        <OrinDataTable compact class="statistics-table">
          <template #header>
            <div class="table-heading">
              <div>
                <strong>Top 10 智能体调用</strong>
                <span>识别所选时间范围内使用频率最高的智能体。</span>
              </div>
            </div>
          </template>
          <OrinAsyncState
            :status="loading ? 'loading' : agentStats.length > 0 ? 'success' : 'empty'"
            empty-text="暂无智能体调用统计"
            @retry="loadData"
          >
            <el-table :data="agentStats">
              <el-table-column prop="agent" label="智能体" />
              <el-table-column prop="count" label="调用次数" />
            </el-table>
          </OrinAsyncState>
        </OrinDataTable>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, ChatLineRound, Coin, Grid, Download } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import {
  getStatisticsOverview,
  getStatisticsUsers,
  getStatisticsAgents,
  getStatisticsTokens,
  getStatisticsTasks,
  exportStatistics
} from '@/api/statistics'
import dayjs from 'dayjs'

const loading = ref(false)
const dateRange = ref([])
const overview = ref({})
const userStats = ref({})
const agentStats = ref([])
const tokenStats = ref({})
const taskStats = ref([])

const apiChartRef = ref(null)
const tokenChartRef = ref(null)
let apiChart = null
let tokenChart = null

const totalTaskCount = computed(() => {
  return taskStats.value.reduce((sum, item) => sum + item.count, 0)
})

onMounted(() => {
  // 默认查询近7天
  const end = dayjs()
  const start = end.subtract(7, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]

  loadData()
})

const loadData = async () => {
  if (dateRange.value.length !== 2) return

  loading.value = true
  const [startDate, endDate] = dateRange.value

  try {
    const [overviewRes, userRes, agentRes, tokenRes, taskRes] = await Promise.all([
      getStatisticsOverview(),
      getStatisticsUsers({ startDate, endDate }),
      getStatisticsAgents({ startDate, endDate }),
      getStatisticsTokens({ startDate, endDate }),
      getStatisticsTasks({ startDate, endDate })
    ])

    overview.value = overviewRes.data || overviewRes
    userStats.value = userRes.data || userRes
    agentStats.value = (agentRes.data || agentRes).topAgents || []
    tokenStats.value = tokenRes.data || tokenRes
    taskStats.value = (taskRes.data || taskRes).byStatus || []

    // 计算总计
    overview.value.totalActiveUsers = userStats.value.totalActiveUsers || 0
    overview.value.totalApiCalls = userStats.value.totalApiCalls || 0
    overview.value.totalTokens = tokenStats.value.totalTokens?.total || 0
    overview.value.totalTasks = taskStats.value.reduce((sum, item) => sum + item.count, 0)

    setTimeout(() => {
      renderCharts()
    }, 100)
  } catch (e) {
    console.error('加载统计数据失败:', e)
    ElMessage.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

const handleDateChange = () => {
  loadData()
}

const handleExport = async () => {
  if (dateRange.value.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }

  try {
    const [startDate, endDate] = dateRange.value
    const res = await exportStatistics({ type: 'daily', startDate, endDate })

    // 下载文件
    const blob = new Blob([res], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `statistics_${dayjs().format('YYYYMMDD')}.csv`
    link.click()
    window.URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (e) {
    console.error('导出失败:', e)
    ElMessage.error('导出失败')
  }
}

const renderCharts = () => {
  // API 调用趋势图
  if (apiChartRef.value) {
    if (!apiChart) {
      apiChart = echarts.init(apiChartRef.value)
    }
    const dailyCalls = (userStats.value.dailyActiveUsers || []).map(item => ({
      date: item.date,
      value: item.count
    }))
    apiChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: dailyCalls.map(d => d.date) },
      yAxis: { type: 'value' },
      series: [{
        data: dailyCalls.map(d => d.value),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 }
      }]
    })
  }

  // Token 消耗趋势图
  if (tokenChartRef.value) {
    if (!tokenChart) {
      tokenChart = echarts.init(tokenChartRef.value)
    }
    const dailyTokens = (tokenStats.value.dailyTokens || []).map(item => ({
      date: item.date,
      value: item.total
    }))
    tokenChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: dailyTokens.map(d => d.date) },
      yAxis: { type: 'value' },
      series: [{
        data: dailyTokens.map(d => d.value),
        type: 'bar',
        smooth: true,
        areaStyle: { opacity: 0.3 }
      }]
    })
  }
}

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const getStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'warning'
  }
  return map[status] || 'info'
}

const calculatePercentage = (count, total) => {
  if (!total) return 0
  return ((count / total) * 100).toFixed(1)
}
</script>

<style scoped>
.statistics-container {
  padding: 0;
}

.statistics-workbar {
  display: flex;
  align-items: center;
  min-height: 58px;
  padding: 10px 16px;
  margin-bottom: 20px;
  border: 1px solid var(--orin-border, var(--el-border-color-lighter));
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, var(--el-bg-color));
}

.statistics-workbar :deep(.el-form-item) {
  margin-bottom: 0;
}

.overview-cards {
  margin-bottom: 20px;
}

.stat-card {
  padding: 18px;
  border: 1px solid var(--orin-border, var(--el-border-color-lighter));
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, var(--el-bg-color));
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s;
}

.stat-card:hover {
  border-color: var(--orin-primary, var(--el-color-primary));
  transform: translateY(-4px);
}

.analysis-panel {
  min-height: 100%;
  padding: 20px;
  border: 1px solid var(--orin-border, var(--el-border-color-lighter));
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, var(--el-bg-color));
}

.panel-heading {
  margin-bottom: 16px;
}

.panel-heading h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.panel-heading p,
.table-heading span {
  display: block;
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.table-heading strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: var(--neutral-gray-500);
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--neutral-gray-800);
}
</style>
