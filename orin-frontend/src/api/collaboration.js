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

// 获取协作统计
export function getCollaborationStats() {
  return request({
    url: '/collaboration/stats',
    method: 'get'
  })
}

// 获取任务包的子任务列表
export function getSubtasks(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks`,
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

// 启动子任务执行
export function executeSubtask(packageId, subTaskId) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/execute`,
    method: 'post'
  })
}

// 重试子任务
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

// 手动完成子任务
export function manualCompleteSubtask(packageId, subTaskId, result) {
  return request({
    url: `/collaboration/packages/${packageId}/subtasks/${subTaskId}/manual-complete`,
    method: 'post',
    data: { result }
  })
}

// 手动完成协作包
export function manualCompletePackage(packageId, result) {
  return request({
    url: `/collaboration/packages/${packageId}/manual-complete`,
    method: 'post',
    data: { result }
  })
}

// 获取黑板结构
export function getBlackboard(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/blackboard`,
    method: 'get'
  })
}

// 保存检查点
export function saveCheckpoint(packageId, checkpointId, data) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints`,
    method: 'post',
    data: { checkpointId, data }
  })
}

// 获取检查点
export function getCheckpoint(packageId, checkpointId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints/${checkpointId}`,
    method: 'get'
  })
}

// 列出所有检查点
export function listCheckpoints(packageId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints`,
    method: 'get'
  })
}

// 回滚到检查点
export function rollbackToCheckpoint(packageId, checkpointId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints/${checkpointId}/rollback`,
    method: 'post'
  })
}

// 删除检查点
export function deleteCheckpoint(packageId, checkpointId) {
  return request({
    url: `/collaboration/packages/${packageId}/checkpoints/${checkpointId}`,
    method: 'delete'
  })
}

// 筛选任务包
export function filterPackages(params) {
  return request({
    url: '/collaboration/packages/filter',
    method: 'get',
    params
  })
}

// ===== 会话优先协作 API =====

export function createCollabSession(data) {
  return request({
    url: '/collaboration/sessions',
    method: 'post',
    data
  })
}

export function listCollabSessions() {
  return request({
    url: '/collaboration/sessions',
    method: 'get'
  })
}

export function sendCollabSessionMessage(sessionId, data) {
  return request({
    url: `/collaboration/sessions/${sessionId}/messages`,
    method: 'post',
    data
  })
}

export function getCollabSessionState(sessionId, turnId) {
  return request({
    url: `/collaboration/sessions/${sessionId}/state`,
    method: 'get',
    params: { turnId }
  })
}

export function pauseCollabTurn(sessionId, turnId) {
  return request({
    url: `/collaboration/sessions/${sessionId}/turns/${turnId}/pause`,
    method: 'post'
  })
}

export function resumeCollabTurn(sessionId, turnId) {
  return request({
    url: `/collaboration/sessions/${sessionId}/turns/${turnId}/resume`,
    method: 'post'
  })
}

export function switchCollabSessionPolicy(sessionId, mainAgentPolicy) {
  return request({
    url: `/collaboration/sessions/${sessionId}/policy`,
    method: 'post',
    data: { mainAgentPolicy }
  })
}

export function listCollabSessionMessages(sessionId, params = {}) {
  return request({
    url: `/collaboration/sessions/${sessionId}/messages`,
    method: 'get',
    params
  })
}

export function getCollabSessionMetrics(hours = 24) {
  return request({
    url: '/collaboration/sessions/metrics',
    method: 'get',
    params: { hours }
  })
}

export async function openCollabSessionStream(sessionId, turnId, token, onEvent) {
  const response = await fetch(`/api/v1/collaboration/sessions/${sessionId}/stream?turnId=${encodeURIComponent(turnId)}`, {
    method: 'GET',
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      Accept: 'text/event-stream'
    }
  })

  if (!response.ok || !response.body) {
    throw new Error(`SSE stream open failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const flushEvent = () => {
    if (!dataLines.length) {
      return
    }
    const raw = dataLines.join('\n')
    try {
      const parsed = JSON.parse(raw)
      onEvent(eventName, parsed)
    } catch (e) {
      onEvent(eventName, { content: raw })
    }
    eventName = 'message'
    dataLines = []
  }

  let streamDone = false
  while (!streamDone) {
    const { value, done } = await reader.read()
    streamDone = done
    if (done) {
      flushEvent()
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const rawLine of lines) {
      const line = rawLine.replace(/\r$/, '')
      if (line === '') {
        flushEvent()
        continue
      }
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
        continue
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
  }
}
