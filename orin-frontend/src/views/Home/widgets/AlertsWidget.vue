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
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const props = defineProps({
  config: {
    type: Object,
    default: () => ({ limit: 5 })
  }
})

const alerts = ref([])

import { getAlertHistory } from '@/api/alert'

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

const loadAlerts = async () => {
  try {
    const res = await getAlertHistory({ page: 0, size: props.config.limit || 10 })
    if (res && res.content) {
      alerts.value = res.content.map(item => ({
        id: item.id,
        title: item.alertMessage,
        time: formatTimeAgo(item.triggeredAt),
        severity: item.severity.toLowerCase()
      }))
    }
  } catch (error) {
    console.error('Failed to load alerts:', error)
  }
}

const formatTimeAgo = (dateStr) => {
  if (!dateStr) return ''
  // Handle Spring Boot LocalDateTime array format: [year, month, day, hour, minute, second, ns]
  if (Array.isArray(dateStr)) {
    if (dateStr.length >= 3) {
      const year = dateStr[0]
      const month = String(dateStr[1]).padStart(2, '0')
      const day = String(dateStr[2]).padStart(2, '0')
      const hour = dateStr.length >= 4 ? String(dateStr[3]).padStart(2, '0') : '00'
      const minute = dateStr.length >= 5 ? String(dateStr[4]).padStart(2, '0') : '00'
      const second = dateStr.length >= 6 ? String(dateStr[5]).padStart(2, '0') : '00'
      const isoString = `${year}-${month}-${day}T${hour}:${minute}:${second}`
      return dayjs(isoString).fromNow()
    }
  }
  return dayjs(dateStr).fromNow()
}

onMounted(() => {
  loadAlerts()
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
