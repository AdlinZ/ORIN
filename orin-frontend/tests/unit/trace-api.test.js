import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  getRecentTraces,
  getTrace,
  getTraceByInstance,
  getTraceLink,
  getTraceStats,
  searchTraces
} from '@/api/trace'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

describe('trace api', () => {
  beforeEach(() => {
    request.get.mockReset()
  })

  it('uses /traces paths so global /api/v1 baseURL is preserved', () => {
    getTrace('trace-1')
    getTraceStats('trace-1')
    getTraceByInstance(8)
    searchTraces('trace-1')
    getRecentTraces(20)
    getTraceLink('trace-1')

    expect(request.get).toHaveBeenNthCalledWith(1, '/traces/trace-1')
    expect(request.get).toHaveBeenNthCalledWith(2, '/traces/trace-1/stats')
    expect(request.get).toHaveBeenNthCalledWith(3, '/traces/instance/8')
    expect(request.get).toHaveBeenNthCalledWith(4, '/traces/search', {
      params: { traceId: 'trace-1' }
    })
    expect(request.get).toHaveBeenNthCalledWith(5, '/traces/recent', {
      params: { size: 20 }
    })
    expect(request.get).toHaveBeenNthCalledWith(6, '/traces/trace-1/link')
  })
})
