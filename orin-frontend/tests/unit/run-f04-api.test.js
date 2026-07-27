import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  getRunEvents,
  getRunAssignments,
  getRunLogs
} from '@/domains/run/api'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('F04 run api', () => {
  beforeEach(() => {
    request.get.mockReset()
    request.post.mockReset()
  })

  describe('getRunEvents', () => {
    it('calls GET /runs/{id}/events without afterSeq', () => {
      getRunEvents('run-1')
      expect(request.get).toHaveBeenCalledWith('/runs/run-1/events', { params: {} })
    })

    it('calls GET /runs/{id}/events with afterSeq', () => {
      getRunEvents('run-1', 5)
      expect(request.get).toHaveBeenCalledWith('/runs/run-1/events', {
        params: { afterSeq: 5 }
      })
    })
  })

  describe('getRunAssignments', () => {
    it('calls GET /runs/{id}/assignments', () => {
      getRunAssignments('run-1')
      expect(request.get).toHaveBeenCalledWith('/runs/run-1/assignments')
    })
  })

  describe('getRunLogs', () => {
    it('calls GET /runs/{id}/logs without afterSeq', () => {
      getRunLogs('run-1')
      expect(request.get).toHaveBeenCalledWith('/runs/run-1/logs', { params: {} })
    })

    it('calls GET /runs/{id}/logs with afterSeq', () => {
      getRunLogs('run-1', 3)
      expect(request.get).toHaveBeenCalledWith('/runs/run-1/logs', {
        params: { afterSeq: 3 }
      })
    })
  })

  describe('listRuns filter params', () => {
    it('passes filter params through', async () => {
      const { listRuns } = await import('@/domains/run/api')
      listRuns({ status: 'COMPLETED', agentId: 'ag-1', size: 20 })
      expect(request.get).toHaveBeenCalledWith('/runs', {
        params: { status: 'COMPLETED', agentId: 'ag-1', size: 20 }
      })
    })
  })
})
