import request from '@/utils/request'

export function getNotifications(params) {
  return request({
    url: '/api/v1/notifications',
    method: 'get',
    params
  })
}

export function getUnreadCount() {
  return request({
    url: '/api/v1/notifications/unread-count',
    method: 'get'
  })
}

export function getNotificationStats() {
  return request({
    url: '/api/v1/notifications/stats',
    method: 'get'
  })
}

export function getNotificationById(id) {
  return request({
    url: `/api/v1/notifications/${id}`,
    method: 'get'
  })
}

export function sendNotification(data) {
  return request({
    url: '/api/v1/notifications',
    method: 'post',
    data
  })
}

export function sendSystemNotification(data) {
  return request({
    url: '/api/v1/notifications/system',
    method: 'post',
    data
  })
}

export function markAsRead(id) {
  return request({
    url: `/api/v1/notifications/${id}/read`,
    method: 'post'
  })
}

export function markAllAsRead() {
  return request({
    url: '/api/v1/notifications/read-all',
    method: 'post'
  })
}

export function cleanupNotifications() {
  return request({
    url: '/api/v1/notifications/cleanup',
    method: 'delete'
  })
}
