import { describe, expect, it } from 'vitest'
import { buildErrorMessage } from '@/utils/request'

describe('request error message', () => {
  it('keeps backend message and appends traceId for troubleshooting', () => {
    const message = buildErrorMessage({
      response: {
        status: 500,
        data: {
          message: '工作流执行失败',
          traceId: 'trace-open-demo-1'
        }
      }
    })

    expect(message).toBe('工作流执行失败（Trace ID: trace-open-demo-1）')
  })

  it('falls back to status message and still preserves traceId', () => {
    const message = buildErrorMessage({
      response: {
        status: 403,
        data: {
          traceId: 'trace-forbidden-1'
        }
      }
    })

    expect(message).toBe('权限不足，拒绝访问（Trace ID: trace-forbidden-1）')
  })

  it('does not duplicate traceId when backend message already includes it', () => {
    const message = buildErrorMessage({
      response: {
        status: 400,
        data: {
          message: '参数错误，请提供 Trace ID: trace-present',
          traceId: 'trace-present'
        }
      }
    })

    expect(message).toBe('参数错误，请提供 Trace ID: trace-present')
  })
})
