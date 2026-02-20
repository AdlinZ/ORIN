<template>
  <el-drawer
    v-model="visible"
    title="通知中心"
    direction="rtl"
    size="400px"
    :before-close="handleClose"
  >
    <div class="notification-center">
      <!-- 通知标签页 -->
      <el-tabs v-model="activeTab" class="notification-tabs">
        <el-tab-pane label="全部" name="all">
          <div class="notification-list">
            <div 
              v-for="notification in filteredNotifications" 
              :key="notification.id"
              class="notification-item"
              :class="{ unread: !notification.read }"
              @click="markAsRead(notification.id)"
            >
              <div class="notification-icon" :class="notification.type">
                <el-icon>
                  <component :is="getNotificationIcon(notification.type)" />
                </el-icon>
              </div>
              <div class="notification-content">
                <div class="notification-title">{{ notification.title }}</div>
                <div class="notification-message">{{ notification.message }}</div>
                <div class="notification-time">{{ formatTime(notification.time) }}</div>
              </div>
              <div 
                v-if="!notification.read" 
                class="unread-dot"
              ></div>
            </div>
            
            <el-empty 
              v-if="filteredNotifications.length === 0" 
              description="暂无通知"
              :image-size="100"
            />
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="未读" name="unread">
          <div class="notification-list">
            <div 
              v-for="notification in unreadNotifications" 
              :key="notification.id"
              class="notification-item unread"
              @click="markAsRead(notification.id)"
            >
              <div class="notification-icon" :class="notification.type">
                <el-icon>
                  <component :is="getNotificationIcon(notification.type)" />
                </el-icon>
              </div>
              <div class="notification-content">
                <div class="notification-title">{{ notification.title }}</div>
                <div class="notification-message">{{ notification.message }}</div>
                <div class="notification-time">{{ formatTime(notification.time) }}</div>
              </div>
              <div class="unread-dot"></div>
            </div>
            
            <el-empty 
              v-if="unreadNotifications.length === 0" 
              description="暂无未读通知"
              :image-size="100"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <!-- 操作按钮 -->
      <div class="notification-actions">
        <el-button size="small" @click="markAllAsRead">全部标记为已读</el-button>
        <el-button size="small" @click="clearAll">清空全部</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { 
  Warning, SuccessFilled, InfoFilled,
  Bell, ChatDotRound, Setting
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'update:unreadCount'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('all')

// 初始通知数据 (已移除 Mock 数据)
const notifications = ref([])

onMounted(async () => {
  // TODO: 从后端加载真实的告警/通知数据 (AlertHistory)
  // fetchNotifications()
})

const filteredNotifications = computed(() => {
  return notifications.value
})

const unreadNotifications = computed(() => {
  return notifications.value.filter(n => !n.read)
})

const unreadCount = computed(() => {
  return unreadNotifications.value.length
})

// 监听未读数量变化
emit('update:unreadCount', unreadCount.value)

const getNotificationIcon = (type) => {
  const iconMap = {
    error: Warning,
    warning: Warning,
    success: SuccessFilled,
    info: InfoFilled,
    system: Bell,
    message: ChatDotRound,
    setting: Setting
  }
  return iconMap[type] || InfoFilled
}

const formatTime = (time) => {
  const now = new Date()
  const diff = now - time
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`
  
  return time.toLocaleDateString('zh-CN')
}

const markAsRead = (id) => {
  const notification = notifications.value.find(n => n.id === id)
  if (notification && !notification.read) {
    notification.read = true
    emit('update:unreadCount', unreadCount.value)
  }
}

const markAllAsRead = () => {
  notifications.value.forEach(n => n.read = true)
  emit('update:unreadCount', 0)
  ElMessage.success('已全部标记为已读')
}

const clearAll = () => {
  notifications.value = []
  emit('update:unreadCount', 0)
  ElMessage.success('已清空所有通知')
}

const handleClose = (done) => {
  done()
}
</script>

<style scoped>
.notification-center {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.notification-tabs {
  flex: 1;
  overflow: hidden;
}

.notification-list {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.notification-item:hover {
  background: var(--el-fill-color-lighter);
}

.notification-item.unread {
  background: var(--el-color-primary-light-9);
}

.notification-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 20px;
}

.notification-icon.error {
  background: var(--el-color-error-light-9);
  color: var(--el-color-error);
}

.notification-icon.warning {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.notification-icon.success {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.notification-icon.info {
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.notification-message {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notification-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  flex-shrink: 0;
  align-self: center;
}

.notification-actions {
  padding: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 8px;
  justify-content: center;
}
</style>
