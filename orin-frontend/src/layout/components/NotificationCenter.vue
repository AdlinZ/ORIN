<template>
  <teleport to="body">
    <transition name="notification-panel">
      <div
        v-if="visible"
        class="notification-overlay"
        role="presentation"
        @click.self="handleClose"
      >
        <section
          class="notification-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="notification-center-title"
        >
          <header class="notification-panel-header">
            <h2 id="notification-center-title">
              通知中心
            </h2>
            <el-button
              text
              :icon="Close"
              class="notification-close"
              aria-label="关闭通知中心"
              @click="handleClose"
            />
          </header>

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
                    @click="markAsReadHandler(notification.id)"
                  >
                    <div class="notification-icon" :class="notification.type">
                      <el-icon>
                        <component :is="getNotificationIcon(notification.type)" />
                      </el-icon>
                    </div>
                    <div class="notification-content">
                      <div class="notification-title-row">
                        <div class="notification-title">
                          {{ notification.title }}
                        </div>
                        <div v-if="notification.isAggregated" class="notification-badges">
                          <span class="status-badge" :class="notification.statusClass">
                            {{ notification.statusLabel }}
                          </span>
                          <span v-if="notification.repeatCount > 1" class="repeat-badge">
                            x{{ notification.repeatCount }}
                          </span>
                        </div>
                      </div>
                      <div class="notification-message">
                        {{ notification.message }}
                      </div>
                      <div class="notification-meta">
                        <span>{{ formatTime(notification.time) }}</span>
                        <span v-if="notification.isAggregated && notification.fingerprint">
                          {{ notification.statusLabel }} · {{ notification.fingerprint }}
                        </span>
                      </div>
                    </div>
                    <div
                      v-if="!notification.read"
                      class="unread-dot"
                    />
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
                    @click="markAsReadHandler(notification.id)"
                  >
                    <div class="notification-icon" :class="notification.type">
                      <el-icon>
                        <component :is="getNotificationIcon(notification.type)" />
                      </el-icon>
                    </div>
                    <div class="notification-content">
                      <div class="notification-title-row">
                        <div class="notification-title">
                          {{ notification.title }}
                        </div>
                        <div v-if="notification.isAggregated" class="notification-badges">
                          <span class="status-badge" :class="notification.statusClass">
                            {{ notification.statusLabel }}
                          </span>
                          <span v-if="notification.repeatCount > 1" class="repeat-badge">
                            x{{ notification.repeatCount }}
                          </span>
                        </div>
                      </div>
                      <div class="notification-message">
                        {{ notification.message }}
                      </div>
                      <div class="notification-meta">
                        <span>{{ formatTime(notification.time) }}</span>
                        <span v-if="notification.isAggregated && notification.fingerprint">
                          {{ notification.statusLabel }} · {{ notification.fingerprint }}
                        </span>
                      </div>
                    </div>
                    <div class="unread-dot" />
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
              <el-button size="small" @click="markAllAsReadHandler">
                全部标记为已读
              </el-button>
              <el-button size="small" @click="clearAll">
                清空全部
              </el-button>
            </div>
          </div>
        </section>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  Warning, SuccessFilled, InfoFilled,
  Bell, ChatDotRound, Setting, Close
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  cleanupNotifications
} from '@/api/notification'
import dayjs from 'dayjs'

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
const unreadTotal = ref(0)
const loading = ref(false)
let pollTimer = null

// 轮询间隔（毫秒）
const POLL_INTERVAL = 30000
const POLL_INTERVAL_BACKGROUND = 60000

// 获取当前轮询间隔（根据页面可见性调整）
const getPollInterval = () => {
  if (document.hidden) {
    return POLL_INTERVAL_BACKGROUND
  }
  return POLL_INTERVAL
}

const fetchNotifications = async () => {
  // 防止重复请求
  if (loading.value) return

  loading.value = true
  try {
    const [res, unreadRes] = await Promise.all([
      getNotifications({ page: 0, size: 20 }),
      getUnreadCount()
    ])
    const data = res.data?.content || res.content || []
    unreadTotal.value = unreadRes.data?.count ?? unreadRes.count ?? unreadTotal.value

    notifications.value = data.map(normalizeNotification)

    emit('update:unreadCount', unreadCount.value)
  } catch (error) {
    console.error('Failed to fetch notifications:', error)
  } finally {
    loading.value = false
  }
}

const refreshUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadTotal.value = res.data?.count ?? res.count ?? 0
    emit('update:unreadCount', unreadTotal.value)
  } catch (error) {
    console.error('Failed to fetch unread notification count:', error)
  }
}

const normalizeNotification = (msg) => {
  const status = (msg.status || '').toUpperCase()
  const isResolved = status === 'RESOLVED'
  const isAggregated = Boolean(msg.dedupeKey || msg.fingerprint)
  const occurredAt = msg.lastOccurredAt || msg.createdAt
  return {
    id: msg.id,
    title: msg.title,
    message: msg.summary || msg.content,
    type: isResolved ? 'success' : (msg.type?.toLowerCase() || 'info'),
    time: parseSpringDate(occurredAt),
    read: msg.read || false,
    fingerprint: msg.fingerprint || msg.dedupeKey || '',
    isAggregated,
    repeatCount: Math.max(Number(msg.repeatCount || 1), 1),
    status,
    statusLabel: isResolved ? '已恢复' : '持续中',
    statusClass: isResolved ? 'resolved' : 'triggered'
  }
}

// 页面可见性变化处理
const handleVisibilityChange = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
  // 根据可见性调整轮询间隔
  pollTimer = setInterval(fetchNotifications, getPollInterval())
}

const parseSpringDate = (dateStr) => {
  if (!dateStr) return new Date()
  if (Array.isArray(dateStr)) {
    if (dateStr.length >= 3) {
      const year = dateStr[0]
      const month = String(dateStr[1]).padStart(2, '0')
      const day = String(dateStr[2]).padStart(2, '0')
      const hour = dateStr.length >= 4 ? String(dateStr[3]).padStart(2, '0') : '00'
      const minute = dateStr.length >= 5 ? String(dateStr[4]).padStart(2, '0') : '00'
      const second = dateStr.length >= 6 ? String(dateStr[5]).padStart(2, '0') : '00'
      const isoString = `${year}-${month}-${day}T${hour}:${minute}:${second}`
      return dayjs(isoString).toDate()
    }
  }
  return dayjs(dateStr).toDate()
}

onMounted(() => {
  fetchNotifications()
  // 使用可见性感知的轮询策略
  pollTimer = setInterval(fetchNotifications, getPollInterval())
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

const filteredNotifications = computed(() => {
  return notifications.value
})

const unreadNotifications = computed(() => {
  return notifications.value.filter(n => !n.read)
})

const unreadCount = computed(() => {
  return unreadTotal.value
})

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

const markAsReadHandler = async (id) => {
  const notification = notifications.value.find(n => n.id === id)
  if (notification && !notification.read) {
    try {
      // 调用服务端接口标记为已读
      await markAsRead(id)
      notification.read = true
      await refreshUnreadCount()
    } catch (error) {
      console.error('标记已读失败:', error)
      ElMessage.error('标记已读失败，请重试')
    }
  }
}

const markAllAsReadHandler = async () => {
  try {
    // 调用服务端接口标记全部为已读
    await markAllAsRead()
    notifications.value.forEach(n => n.read = true)
    unreadTotal.value = 0
    emit('update:unreadCount', 0)
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    console.error('标记全部已读失败:', error)
    ElMessage.error('操作失败，请重试')
  }
}

const clearAll = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有通知吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 调用服务端接口清空
    await cleanupNotifications()
    notifications.value = []
    unreadTotal.value = 0
    emit('update:unreadCount', 0)
    ElMessage.success('已清空所有通知')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('清空通知失败:', error)
      ElMessage.error('操作失败，请重试')
    }
  }
}

const handleClose = (done) => {
  visible.value = false
  if (typeof done === 'function') {
    done()
  }
}
</script>

<style scoped>
.notification-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  box-sizing: border-box;
  background: rgba(15, 23, 42, 0.18);
  direction: ltr;
}

.notification-panel {
  position: absolute;
  top: 12px;
  right: 12px;
  bottom: 12px;
  width: min(360px, calc(100vw - 24px));
  max-width: calc(100vw - 24px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
  box-shadow: 0 24px 64px -32px rgba(15, 23, 42, 0.55);
  overflow: hidden;
}

.notification-panel-header {
  flex: 0 0 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.notification-panel-header h2 {
  min-width: 0;
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
}

.notification-close {
  flex: 0 0 auto;
}

.notification-panel-enter-active,
.notification-panel-leave-active {
  transition: opacity 0.18s ease;
}

.notification-panel-enter-active .notification-panel,
.notification-panel-leave-active .notification-panel {
  transition: transform 0.18s ease;
}

.notification-panel-enter-from,
.notification-panel-leave-to {
  opacity: 0;
}

.notification-panel-enter-from .notification-panel,
.notification-panel-leave-to .notification-panel {
  transform: translateX(18px);
}

.notification-center {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notification-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.notification-tabs :deep(.el-tabs__content) {
  height: calc(100% - 48px);
}

.notification-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.notification-list {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.notification-item {
  display: flex;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 14px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
  box-sizing: border-box;
}

.notification-item:hover {
  background: var(--el-fill-color-lighter);
}

.notification-item.unread {
  background: var(--el-color-primary-light-9);
}

.notification-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
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

.notification-title-row {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 4px;
}

.notification-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.notification-badges {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-badge,
.repeat-badge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.status-badge.triggered {
  color: var(--el-color-error);
  background: var(--el-color-error-light-9);
}

.status-badge.resolved {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.repeat-badge {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.notification-message {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  overflow-wrap: anywhere;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notification-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  flex-shrink: 0;
  align-self: center;
  margin-left: auto;
}

.notification-actions {
  flex: 0 0 auto;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 8px;
  justify-content: flex-start;
  flex-wrap: wrap;
  background: var(--el-bg-color);
}

.notification-actions :deep(.el-button) {
  min-width: 0;
  margin-left: 0;
  white-space: nowrap;
}

@media (max-width: 520px) {
  .notification-panel {
    top: 10px;
    right: 10px;
    bottom: 10px;
    width: min(360px, calc(100vw - 20px));
    max-width: calc(100vw - 20px);
  }

  .notification-tabs :deep(.el-tabs__header) {
    padding: 0 12px;
    margin-bottom: 0;
  }

  .notification-item {
    gap: 10px;
    padding: 14px 10px;
  }

  .notification-icon {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }

  .notification-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    padding: 12px;
  }

  .notification-actions :deep(.el-button) {
    width: 100%;
    padding-inline: 8px;
  }
}

@media (max-width: 360px) {
  .notification-actions {
    grid-template-columns: 1fr;
  }
}
</style>
