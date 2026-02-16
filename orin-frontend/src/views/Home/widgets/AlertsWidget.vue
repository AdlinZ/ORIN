<template>
  <div class="alerts-widget">
    <div class="widget-header">
      <h3>异常告警</h3>
      <el-badge :value="alerts.length" :max="99" class="badge">
        <el-button text :icon="Bell" />
      </el-badge>
    </div>
    
    <div class="alerts-list" v-if="alerts.length > 0">
      <div class="alert-item" v-for="alert in displayAlerts" :key="alert.id" :class="`severity-${alert.severity}`">
        <div class="alert-icon">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <div class="alert-content">
          <div class="alert-title">{{ alert.title }}</div>
          <div class="alert-time">{{ alert.time }}</div>
        </div>
        <el-tag :type="getSeverityType(alert.severity)" size="small">
          {{ alert.severity }}
        </el-tag>
      </div>
    </div>
    
    <el-empty v-else description="暂无告警" :image-size="100" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Bell, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  config: {
    type: Object,
    default: () => ({ limit: 5 })
  }
})

const alerts = ref([
  {
    id: 1,
    title: 'Agent-GPT-4 响应超时',
    time: '2分钟前',
    severity: 'error'
  },
  {
    id: 2,
    title: 'Token 使用量接近配额',
    time: '15分钟前',
    severity: 'warning'
  },
  {
    id: 3,
    title: '知识库同步延迟',
    time: '1小时前',
    severity: 'info'
  }
])

const displayAlerts = computed(() => {
  return alerts.value.slice(0, props.config.limit || 5)
})

const getSeverityType = (severity) => {
  const map = {
    error: 'danger',
    warning: 'warning',
    info: 'info'
  }
  return map[severity] || 'info'
}

onMounted(() => {
  // TODO: Load alerts from API
})
</script>

<style scoped>
.alerts-widget {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
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

.alerts-list {
  flex: 1;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.alert-item:hover {
  background: var(--neutral-gray-50);
}

.alert-item.severity-error {
  border-left-color: #ef4444;
}

.alert-item.severity-warning {
  border-left-color: #f59e0b;
}

.alert-item.severity-info {
  border-left-color: #3b82f6;
}

.alert-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.severity-error .alert-icon {
  background: #fee2e2;
  color: #ef4444;
}

.severity-warning .alert-icon {
  background: #fef3c7;
  color: #f59e0b;
}

.severity-info .alert-icon {
  background: #dbeafe;
  color: #3b82f6;
}

.alert-content {
  flex: 1;
}

.alert-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--neutral-gray-900);
  margin-bottom: 2px;
}

.alert-time {
  font-size: 12px;
  color: var(--neutral-gray-500);
}
</style>
