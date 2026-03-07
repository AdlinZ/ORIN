<template>
  <div class="health-overview">
    <div class="overview-header">
      <h3>智能体健康状态</h3>
      <el-button :icon="Refresh" size="small" @click="fetchHealthOverview" :loading="loading">刷新</el-button>
    </div>
    
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-item">
          <div class="stat-value">{{ overview.totalAgents || 0 }}</div>
          <div class="stat-label">总数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item healthy">
          <div class="stat-value">{{ overview.healthyAgents || 0 }}</div>
          <div class="stat-label">健康</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item unhealthy">
          <div class="stat-value">{{ overview.unhealthyAgents || 0 }}</div>
          <div class="stat-label">异常</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item offline">
          <div class="stat-value">{{ overview.offlineAgents || 0 }}</div>
          <div class="stat-label">离线</div>
        </div>
      </el-col>
    </el-row>
    
    <div class="health-rate">
      <el-progress 
        :percentage="overview.healthRate || 0" 
        :color="healthRateColor"
        :stroke-width="10"
        :show-text="true"
      />
    </div>
    
    <div class="agents-by-status" v-if="overview.agentsByStatus">
      <div 
        v-for="(agents, status) in overview.agentsByStatus" 
        :key="status" 
        class="status-group"
      >
        <div class="status-header">
          <el-tag :type="getStatusType(status)" size="small">{{ status }}</el-tag>
          <span class="count">{{ agents.length }}</span>
        </div>
        <div class="agent-list">
          <div 
            v-for="agent in agents.slice(0, 5)" 
            :key="agent.agentId" 
            class="agent-item"
          >
            <span class="agent-name">{{ agent.agentName }}</span>
            <span class="agent-metrics" v-if="agent.cpuUsage">
              CPU: {{ agent.cpuUsage }}% | MEM: {{ agent.memoryUsage }}%
            </span>
          </div>
          <div v-if="agents.length > 5" class="more">
            +{{ agents.length - 5 }} 更多
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAgentHealthOverview } from '@/api/monitor'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const overview = ref({})

const fetchHealthOverview = async () => {
  loading.value = true
  try {
    const res = await getAgentHealthOverview()
    overview.value = res || {}
  } catch (error) {
    ElMessage.error('获取健康概览失败')
  } finally {
    loading.value = false
  }
}

const healthRateColor = computed(() => {
  const rate = overview.value.healthRate || 0
  if (rate >= 80) return '#67C23A'
  if (rate >= 60) return '#E6A23C'
  return '#F56C6C'
})

const getStatusType = (status) => {
  const typeMap = {
    'RUNNING': 'success',
    'HIGH_LOAD': 'warning',
    'STOPPED': 'info',
    'ERROR': 'danger'
  }
  return typeMap[status] || 'info'
}

onMounted(() => {
  fetchHealthOverview()
})
</script>

<style scoped>
.health-overview {
  padding: 16px;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.overview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-item {
  text-align: center;
  padding: 12px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.stat-item.healthy {
  background: rgba(103, 194, 58, 0.1);
}

.stat-item.unhealthy {
  background: rgba(230, 162, 60, 0.1);
}

.stat-item.offline {
  background: rgba(144, 147, 153, 0.1);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.stat-item.healthy .stat-value {
  color: #67C23A;
}

.stat-item.unhealthy .stat-value {
  color: #E6A23C;
}

.stat-item.offline .stat-value {
  color: #909399;
}

.stat-label {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
}

.health-rate {
  margin-bottom: 16px;
}

.agents-by-status {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-group {
  border: 1px solid var(--neutral-gray-200);
  border-radius: 8px;
  padding: 12px;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.count {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-item {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding: 4px 8px;
  background: var(--neutral-gray-50);
  border-radius: 4px;
}

.agent-name {
  font-weight: 500;
}

.agent-metrics {
  color: var(--neutral-gray-500);
}

.more {
  font-size: 12px;
  color: var(--neutral-gray-400);
  text-align: center;
  padding: 4px;
}
</style>
