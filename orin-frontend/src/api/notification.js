import request from '@/utils/request'

// 获取告警通知配置
export function getNotificationConfig() {
  return request({
    url: '/alerts/notification-config',
    method: 'get'
  })
}

// 保存告警通知配置
export function saveNotificationConfig(data) {
  return request({
    url: '/alerts/notification-config',
    method: 'post',
    data
  })
}

// 测试通知渠道
export function testNotification(channel) {
  return request({
    url: '/alerts/notification-config/test',
    method: 'post',
    data: { channel }
  })
}

export function getNotifications(params) {
  return request({
    url: '/notifications',
    method: 'get',
    params
  })
}

export function getUnreadCount() {
  return request({
    url: '/notifications/unread-count',
    method: 'get'
  })
}

export function getNotificationStats() {
  return request({
    url: '/notifications/stats',
    method: 'get'
  })
}

export function getNotificationById(id) {
  return request({
    url: `/notifications/${id}`,
    method: 'get'
  })
}

export function sendNotification(data) {
  return request({
    url: '/notifications',
    method: 'post',
    data
  })
}

export function sendSystemNotification(data) {
  return request({
    url: '/notifications/system',
    method: 'post',
    data
  })
}

export function markAsRead(id) {
  return request({
    url: `/notifications/${id}/read`,
    method: 'post'
  })
}

export function markAllAsRead() {
  return request({
    url: '/notifications/read-all',
    method: 'post'
  })
}

export function cleanupNotifications() {
  return request({
    url: '/notifications/cleanup',
    method: 'delete'
  })
}
