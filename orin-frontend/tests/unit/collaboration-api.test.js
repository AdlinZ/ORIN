import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  manualCompletePackage,
  manualCompleteSubtask,
  retrySubtask,
  skipSubtask
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
})
