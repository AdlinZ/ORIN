import request from '@/utils/request'

// 暂停协作任务
export function pauseCollaboration(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/pause`,
    method: 'post'
  })
}

// 恢复协作任务
export function resumeCollaboration(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/resume`,
    method: 'post'
  })
}

// 取消协作任务
export function cancelCollaboration(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/cancel`,
    method: 'post'
  })
}

// 重跑子任务
export function retrySubtask(packageId, subTaskId) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/retry`,
    method: 'post'
  })
}

// 跳过子任务
export function skipSubtask(packageId, subTaskId) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/skip`,
    method: 'post'
  })
}

// 回滚到检查点
export function rollbackToCheckpoint(packageId, checkpointId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints/${checkpointId}/rollback`,
    method: 'post'
  })
}

// 获取检查点列表
export function getCheckpoints(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints`,
    method: 'get'
  })
}

// 人工接管子任务
export function manuallyHandleSubtask(packageId, subTaskId, handlerInput) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/manual`,
    method: 'post',
    data: { handlerInput }
  })
}

// 获取运行时状态
export function getRuntimeStatus(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/runtime`,
    method: 'get'
  })
}

// 获取 Agent 指标
export function getAgentMetrics(agentId) {
  return request({
    url: `/collaboration/metrics/agent/${agentId}`,
    method: 'get'
  })
}

// 获取协作降级建议
export function getDegradationSuggestion(agentId) {
  return request({
    url: `/collaboration/metrics/suggestion`,
    method: 'get',
    params: { agentId }
  })
}