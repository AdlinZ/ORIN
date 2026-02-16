<template>
  <div class="stats-widget">
    <div class="widget-header">
      <h3>系统统计</h3>
      <el-button text :icon="Refresh" @click="loadStats" :loading="loading" />
    </div>
    
    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.key">
        <div class="stat-icon" :style="{ background: stat.color + '15', color: stat.color }">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'" v-if="stat.trend !== undefined">
            <el-icon><component :is="stat.trend > 0 ? CaretTop : CaretBottom" /></el-icon>
            {{ Math.abs(stat.trend) }}%
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, Box, ChatDotRound, Coin, Timer, CaretTop, CaretBottom } from '@element-plus/icons-vue'

const props = defineProps({
  config: {
    type: Object,
    default: () => ({})
  }
})

const loading = ref(false)
const stats = ref([
  {
    key: 'apps',
    label: '活跃应用',
    value: '12',
    icon: Box,
    color: '#155eef',
    trend: 8.5
  },
  {
    key: 'calls',
    label: '今日调用',
    value: '2,847',
    icon: ChatDotRound,
    color: '#10b981',
    trend: 12.3
  },
  {
    key: 'tokens',
    label: 'Token 用量',
    value: '1.2M',
    icon: Coin,
    color: '#f59e0b',
    trend: -3.2
  },
  {
    key: 'latency',
    label: '平均延迟',
    value: '234ms',
    icon: Timer,
    color: '#8b5cf6',
    trend: -5.1
  }
])

const loadStats = async () => {
  loading.value = true
  try {
    // TODO: Call API
    // const data = await getDashboardStats()
    // stats.value = data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.stats-widget {
  padding: 24px;
  height: 100%;
}

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.widget-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--neutral-gray-50);
  border-radius: 12px;
  transition: all 0.3s;
}

.stat-card:hover {
  background: var(--neutral-gray-100);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 600;
  margin-top: 4px;
}

.stat-trend.up {
  color: #10b981;
}

.stat-trend.down {
  color: #ef4444;
}
</style>
