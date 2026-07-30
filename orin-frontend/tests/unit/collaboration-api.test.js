import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  checkPackageCompleted,
  completePackage,
  createCollabSession,
  createCollaborationPackage,
  decomposePackage,
  deleteCheckpoint,
  executeSubtask,
  failPackage,
  filterPackages,
  getAllPackages,
  getBlackboard,
  getCheckpoint,
  getCollabSessionMetrics,
  getCollabSessionState,
  getCollaborationStats,
  getEventHistory,
  getExecutableSubtasks,
  getMyPackages,
  getPackage,
  getSubtasks,
  listCheckpoints,
  listCollabSessionMessages,
  listCollabSessions,
  manualCompletePackage,
  manualCompleteSubtask,
  openCollabSessionStream,
  pauseCollabTurn,
  retrySubtask,
  rollbackToCheckpoint,
  resumeCollabTurn,
  saveCheckpoint,
  sendCollabSessionMessage,
  skipSubtask,
  switchCollabSessionPolicy,
  triggerFallback,
  updateSubtaskStatus
} from '@/api/collaboration'
import {
  cancelCollaboration,
  pauseCollaboration,
  resumeCollaboration
} from '@/api/collaborationRuntime'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

describe('collaboration intervention api', () => {
  beforeEach(() => {
    request.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uses package intervention endpoints under the /api/v1 baseURL', () => {
    pauseCollaboration('pkg-1')
    resumeCollaboration('pkg-1')
    cancelCollaboration('pkg-1')
    manualCompletePackage('pkg-1', 'final result')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/collaboration/packages/pkg-1/pause',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/collaboration/packages/pkg-1/resume',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/collaboration/packages/pkg-1/cancel',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      url: '/collaboration/packages/pkg-1/manual-complete',
      method: 'post',
      data: { result: 'final result' }
    })
  })

  it('uses subtask intervention endpoints under the package resource', () => {
    retrySubtask('pkg-1', 'sub-1')
    skipSubtask('pkg-1', 'sub-1')
    manualCompleteSubtask('pkg-1', 'sub-1', 'operator result')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/collaboration/packages/pkg-1/subtasks/sub-1/retry',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/collaboration/packages/pkg-1/subtasks/sub-1/skip',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/collaboration/packages/pkg-1/subtasks/sub-1/manual-complete',
      method: 'post',
      data: { result: 'operator result' }
    })
  })

  it('keeps package lifecycle, blackboard and checkpoint requests on their resource paths', () => {
    const payload = { title: 'release validation' }
    const status = { status: 'RUNNING' }
    const checkpoint = { answer: 42 }

    createCollaborationPackage(payload)
    decomposePackage('pkg-1', ['runner'])
    getExecutableSubtasks('pkg-1')
    updateSubtaskStatus('pkg-1', 'sub-1', status)
    checkPackageCompleted('pkg-1')
    triggerFallback('pkg-1', 'timeout')
    completePackage('pkg-1', 'done')
    failPackage('pkg-1', 'failed')
    getPackage('pkg-1')
    getAllPackages()
    getMyPackages()
    getCollaborationStats()
    getSubtasks('pkg-1')
    getEventHistory('pkg-1')
    executeSubtask('pkg-1', 'sub-1')
    getBlackboard('pkg-1')
    saveCheckpoint('pkg-1', 'cp-1', checkpoint)
    getCheckpoint('pkg-1', 'cp-1')
    listCheckpoints('pkg-1')
    rollbackToCheckpoint('pkg-1', 'cp-1')
    deleteCheckpoint('pkg-1', 'cp-1')
    filterPackages({ status: 'EXECUTING' })

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/collaboration/packages', method: 'post', data: payload },
      { url: '/collaboration/packages/pkg-1/decompose', method: 'post', data: { capabilities: ['runner'] } },
      { url: '/collaboration/packages/pkg-1/executable', method: 'get' },
      { url: '/collaboration/packages/pkg-1/subtasks/sub-1/status', method: 'put', data: status },
      { url: '/collaboration/packages/pkg-1/completed', method: 'get' },
      { url: '/collaboration/packages/pkg-1/fallback', method: 'post', data: { reason: 'timeout' } },
      { url: '/collaboration/packages/pkg-1/complete', method: 'post', data: { result: 'done' } },
      { url: '/collaboration/packages/pkg-1/fail', method: 'post', data: { errorMessage: 'failed' } },
      { url: '/collaboration/packages/pkg-1', method: 'get' },
      { url: '/collaboration/packages', method: 'get' },
      { url: '/collaboration/packages/user', method: 'get' },
      { url: '/collaboration/stats', method: 'get' },
      { url: '/collaboration/packages/pkg-1/subtasks', method: 'get' },
      { url: '/collaboration/events/pkg-1', method: 'get' },
      { url: '/collaboration/packages/pkg-1/subtasks/sub-1/execute', method: 'post' },
      { url: '/collaboration/packages/pkg-1/blackboard', method: 'get' },
      { url: '/collaboration/packages/pkg-1/checkpoints', method: 'post', data: { checkpointId: 'cp-1', data: checkpoint } },
      { url: '/collaboration/packages/pkg-1/checkpoints/cp-1', method: 'get' },
      { url: '/collaboration/packages/pkg-1/checkpoints', method: 'get' },
      { url: '/collaboration/packages/pkg-1/checkpoints/cp-1/rollback', method: 'post' },
      { url: '/collaboration/packages/pkg-1/checkpoints/cp-1', method: 'delete' },
      { url: '/collaboration/packages/filter', method: 'get', params: { status: 'EXECUTING' } }
    ])
  })

  it('keeps session requests scoped to the session and turn identifiers', () => {
    createCollabSession({ topic: 'release' })
    listCollabSessions()
    sendCollabSessionMessage('session-1', { content: 'hello' })
    getCollabSessionState('session-1', 'turn-1')
    pauseCollabTurn('session-1', 'turn-1')
    resumeCollabTurn('session-1', 'turn-1')
    switchCollabSessionPolicy('session-1', 'single-agent')
    listCollabSessionMessages('session-1', { cursor: 'next' })
    getCollabSessionMetrics(48)

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/collaboration/sessions', method: 'post', data: { topic: 'release' } },
      { url: '/collaboration/sessions', method: 'get' },
      { url: '/collaboration/sessions/session-1/messages', method: 'post', data: { content: 'hello' } },
      { url: '/collaboration/sessions/session-1/state', method: 'get', params: { turnId: 'turn-1' } },
      { url: '/collaboration/sessions/session-1/turns/turn-1/pause', method: 'post' },
      { url: '/collaboration/sessions/session-1/turns/turn-1/resume', method: 'post' },
      { url: '/collaboration/sessions/session-1/policy', method: 'post', data: { mainAgentPolicy: 'single-agent' } },
      { url: '/collaboration/sessions/session-1/messages', method: 'get', params: { cursor: 'next' } },
      { url: '/collaboration/sessions/metrics', method: 'get', params: { hours: 48 } }
    ])
  })

  it('parses structured and plain-text session stream events without leaking the bearer token into the URL', async () => {
    const chunks = [
      new TextEncoder().encode('event: progress\ndata: {"step":1}\n\n'),
      new TextEncoder().encode('data: plain result\n\n')
    ]
    const reader = {
      read: vi.fn()
        .mockResolvedValueOnce({ value: chunks[0], done: false })
        .mockResolvedValueOnce({ value: chunks[1], done: false })
        .mockResolvedValueOnce({ done: true })
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      body: { getReader: () => reader }
    })
    vi.stubGlobal('fetch', fetchMock)
    const events = []

    await openCollabSessionStream('session-1', 'turn-1', 'secret-token', (...event) => events.push(event))

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/collaboration/sessions/session-1/stream?turnId=turn-1',
      expect.objectContaining({ headers: { Authorization: 'Bearer secret-token', Accept: 'text/event-stream' } })
    )
    expect(events).toEqual([
      ['progress', { step: 1 }],
      ['message', { content: 'plain result' }]
    ])
  })

  it('rejects an unavailable session stream with the server status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503, body: null }))

    await expect(openCollabSessionStream('session-1', 'turn-1', '', vi.fn()))
      .rejects.toThrow('SSE stream open failed: 503')
  })
})
