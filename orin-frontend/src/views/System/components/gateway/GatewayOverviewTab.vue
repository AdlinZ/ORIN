<template>
  <div class="gateway-overview-tab">
    <!-- Stats Cards -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ formatNumber(overview.totalRequests) }}</div>
          <div class="stat-label">总请求数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.qps }}</div>
          <div class="stat-label">QPS</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.avgLatencyMs }}ms</div>
          <div class="stat-label">平均延迟</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value" :class="{ 'text-success': overview.errorRate < 1, 'text-danger': overview.errorRate >= 1 }">
            {{ overview.errorRate }}%
          </div>
          <div class="stat-label">错误率</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Service Health -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>服务健康状态</span>
          <el-button type="primary" link size="small" @click="loadOverview">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="overview.serviceHealth || []" stripe>
        <el-table-column prop="serviceName" label="服务名称" />
        <el-table-column prop="instanceCount" label="实例数" width="100" />
        <el-table-column prop="healthyCount" label="健康实例" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-success': row.status === 'HEALTHY', 'text-warning': row.status === 'DEGRADED', 'text-danger': row.status === 'UNHEALTHY' }">
              {{ row.healthyCount }}/{{ row.instanceCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'HEALTHY' ? 'success' : row.status === 'DEGRADED' ? 'warning' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Quick Stats -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>网关统计</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="活跃路由">{{ overview.activeRoutes }}</el-descriptions-item>
            <el-descriptions-item label="活跃服务">{{ overview.activeServices }}</el-descriptions-item>
            <el-descriptions-item label="健康实例">{{ overview.healthyInstances }}</el-descriptions-item>
            <el-descriptions-item label="异常实例">{{ overview.unhealthyInstances }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>热门路由 TOP 5</span>
          </template>
          <el-table :data="overview.topRoutes || []" stripe size="small">
            <el-table-column prop="routeName" label="路由名称" />
            <el-table-column prop="requestCount" label="请求数" width="100" />
            <el-table-column prop="avgLatencyMs" label="平均延迟" width="100">
              <template #default="{ row }">{{ row.avgLatencyMs }}ms</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getGatewayOverview } from '@/api/gateway'

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
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num
}

const loadOverview = async () => {
  loading.value = true
  try {
    const res = await getGatewayOverview()
    Object.assign(overview, res)
  } catch (e) {
    ElMessage.error('加载网关概览失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.gateway-overview-tab {
  padding: 0;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
.text-warning { color: #e6a23c; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
