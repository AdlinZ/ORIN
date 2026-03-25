import request from '@/utils/request'

// 创建协作任务包
export function createCollaborationPackage(data) {
  return request({
    url: '/collaboration/packages',
    method: 'post',
    data
  })
}

// 分解任务包
export function decomposePackage(packageId, capabilities) {
  return request({
    url: `/collaboration/packages/${packageId}/decompose`,
    method: 'post',
    data: { capabilities }
  })
}

// 获取可执行的子任务
export function getExecutableSubtasks(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/executable`,
    method: 'get'
  })
}

// 更新子任务状态
export function updateSubtaskStatus(packageId, subTaskId, data) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/status`,
    method: 'put',
    data
  })
}

// 检查任务是否完成
export function checkPackageCompleted(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/completed`,
    method: 'get'
  })
}

// 触发回退
export function triggerFallback(packageId, reason) {
  return request({
    url: `/collaboration/packages/${packageId}/fallback`,
    method: 'post',
    data: { reason }
  })
}

// 完成协作任务
export function completePackage(packageId, result) {
  return request({
    url: `/collaboration/packages/${packageId}/complete`,
    method: 'post',
    data: { result }
  })
}

// 标记任务失败
export function failPackage(packageId, errorMessage) {
  return request({
    url: `/collaboration/packages/${packageId}/fail`,
    method: 'post',
    data: { errorMessage }
  })
}

// 获取任务包详情
export function getPackage(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}`,
    method: 'get'
  })
}

// 获取所有任务包
export function getAllPackages() {
  return request({
    url: '/collaboration/packages',
    method: 'get'
  })
}

// 获取用户任务包
export function getMyPackages() {
  return request({
    url: '/collaboration/packages/user',
    method: 'get'
  })
}

// 获取协作事件历史
export function getEventHistory(packageId) {
  return request({
    url: `/collaboration/events/${packageId}`,
    method: 'get'
  })
}

// 获取协作统计
export function getCollaborationStats() {
  return request({
    url: '/collaboration/stats',
    method: 'get'
  })
}