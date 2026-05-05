<template>
  <div class="gateway-overview-tab">
    <!-- Stat Cards -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <div class="stat-card" :style="{ '--accent': card.color }">
          <div class="stat-card-accent" />
          <div class="stat-card-body">
            <div class="stat-icon-wrap" :style="{ background: card.bg }">
              <el-icon :style="{ color: card.color }"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value" :style="card.valueStyle">{{ card.format(overview[card.key]) }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Service Health -->
    <div class="section-card" style="margin-top: 20px;">
      <div class="section-header">
        <div class="section-title">
          <el-icon><SetUp /></el-icon>
          服务健康状态
        </div>
        <el-button type="primary" link size="small" :loading="loading" @click="loadOverview">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <el-table v-loading="loading" :data="overview.serviceHealth || []" stripe>
        <el-table-column prop="serviceName" label="服务名称" />
        <el-table-column prop="instanceCount" label="实例数" width="90" align="center" />
        <el-table-column label="健康实例" width="100" align="center">
          <template #default="{ row }">
            <span :class="healthClass(row.status)">{{ row.healthyCount }}/{{ row.instanceCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <el-icon class="empty-icon"><SetUp /></el-icon>
            <p class="empty-text">暂无服务，请先在「服务管理」中添加上游服务</p>
          </div>
        </template>
      </el-table>
    </div>

    <!-- Bottom row -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="12">
        <div class="section-card">
          <div class="section-header">
            <div class="section-title"><el-icon><DataAnalysis /></el-icon> 网关统计</div>
          </div>
          <div class="stats-grid">
            <div class="stats-grid-item">
              <div class="stats-grid-value">{{ overview.activeRoutes ?? 0 }}</div>
              <div class="stats-grid-label">活跃路由</div>
            </div>
            <div class="stats-grid-item">
              <div class="stats-grid-value">{{ overview.activeServices ?? 0 }}</div>
              <div class="stats-grid-label">活跃服务</div>
            </div>
            <div class="stats-grid-item">
              <div class="stats-grid-value success">{{ overview.healthyInstances ?? 0 }}</div>
              <div class="stats-grid-label">健康实例</div>
            </div>
            <div class="stats-grid-item">
              <div class="stats-grid-value" :class="overview.unhealthyInstances > 0 ? 'danger' : ''">
                {{ overview.unhealthyInstances ?? 0 }}
              </div>
              <div class="stats-grid-label">异常实例</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="section-card">
          <div class="section-header">
            <div class="section-title"><el-icon><TrendCharts /></el-icon> 热门路由 TOP 5</div>
          </div>
          <el-table :data="overview.topRoutes || []" stripe size="small">
            <el-table-column prop="routeName" label="路由名称" />
            <el-table-column prop="requestCount" label="请求数" width="90" align="right" />
            <el-table-column label="平均延迟" width="100" align="right">
              <template #default="{ row }">{{ row.avgLatencyMs }}ms</template>
            </el-table-column>
          </el-table>
          <div v-if="!overview.topRoutes?.length" class="empty-state small">
            <p class="empty-text">暂无请求数据</p>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, TrendCharts, Timer, Warning, SetUp, Refresh, DataAnalysis } from '@element-plus/icons-vue'
import { getUnifiedGatewayOverview } from '@/api/gateway'

const loading = ref(false)
const overview = reactive({
  totalRequests: 0,
  qps: 0,
  avgLatencyMs: 0,
  errorRate: 0,
  activeRoutes: 0,
  activeServices: 0,
  healthyInstances: 0,
  unhealthyInstances: 0,
  serviceHealth: [],
  topRoutes: []
})

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return String(num)
}

const statCards = computed(() => [
  {
    key: 'totalRequests',
    label: '总请求数',
    icon: DataLine,
    color: '#2563eb',
    bg: '#eff6ff',
    format: formatNumber,
  },
  {
    key: 'qps',
    label: '当前 QPS',
    icon: TrendCharts,
    color: '#059669',
    bg: '#ecfdf5',
    format: (v) => (v ?? 0).toString(),
  },
  {
    key: 'avgLatencyMs',
    label: '平均延迟',
    icon: Timer,
    color: '#d97706',
    bg: '#fffbeb',
    format: (v) => `${v ?? 0}ms`,
  },
  {
    key: 'errorRate',
    label: '错误率',
    icon: Warning,
    color: overview.errorRate >= 1 ? '#dc2626' : '#059669',
    bg: overview.errorRate >= 1 ? '#fef2f2' : '#ecfdf5',
    format: (v) => `${v ?? 0}%`,
    valueStyle: { color: overview.errorRate >= 1 ? '#dc2626' : '#059669' },
  },
])

const statusTagType = (s) => s === 'HEALTHY' ? 'success' : s === 'DEGRADED' ? 'warning' : 'danger'
const statusLabel = (s) => ({ HEALTHY: '健康', DEGRADED: '降级', UNHEALTHY: '异常' }[s] || s)
const healthClass = (s) => s === 'HEALTHY' ? 'text-success' : s === 'DEGRADED' ? 'text-warning' : 'text-danger'

const loadOverview = async () => {
  loading.value = true
  try {
    const res = await getUnifiedGatewayOverview()
    Object.assign(overview, res)
  } catch {
    ElMessage.error('加载网关概览失败')
  } finally {
    loading.value = false
  }
}

loadOverview()
</script>

<style scoped>
.gateway-overview-tab {
  padding: 0;
}

/* Stat Cards */
.stat-card {
  position: relative;
  background: #fff;
  border: 1px solid var(--neutral-gray-100, #f0f0f0);
  border-radius: 10px;
  overflow: hidden;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}

.stat-card-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--accent);
  border-radius: 10px 10px 0 0;
}

.stat-card-body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-wrap .el-icon {
  font-size: 20px;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--neutral-gray-500, #6b7280);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--neutral-gray-800, #1f2937);
  line-height: 1.1;
}

/* Section Cards */
.section-card {
  background: #fff;
  border: 1px solid var(--neutral-gray-100, #f0f0f0);
  border-radius: 10px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--neutral-gray-100, #f0f0f0);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-700, #374151);
}

.section-title .el-icon {
  font-size: 15px;
  color: var(--el-color-primary, #2563eb);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: var(--neutral-gray-100, #f0f0f0);
}

.stats-grid-item {
  background: #fff;
  padding: 20px 16px;
  text-align: center;
}

.stats-grid-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--neutral-gray-800, #1f2937);
  line-height: 1;
  margin-bottom: 6px;
}

.stats-grid-value.success { color: #059669; }
.stats-grid-value.danger  { color: #dc2626; }

.stats-grid-label {
  font-size: 12px;
  color: var(--neutral-gray-500, #6b7280);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  gap: 8px;
}

.empty-state.small {
  padding: 16px;
}

.empty-icon {
  font-size: 36px;
  color: var(--neutral-gray-300, #d1d5db);
}

.empty-text {
  font-size: 13px;
  color: var(--neutral-gray-400, #9ca3af);
  margin: 0;
  text-align: center;
}

.text-success { color: #059669; }
.text-warning  { color: #d97706; }
.text-danger   { color: #dc2626; }
</style>
