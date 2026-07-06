import { describe, expect, it, vi } from 'vitest'
import { formatChatError } from '@/utils/formatChatError'

describe('formatChatError', () => {
  it('returns the FALLBACK string when given an empty input', () => {
    expect(formatChatError(null)).toContain('请求失败')
    expect(formatChatError(undefined)).toContain('请求失败')
    expect(formatChatError('')).toContain('请求失败')
  })

  it('renders a friendly Chinese message with traceId for an axios 500', () => {
    const err = {
      response: {
        status: 500,
        data: { code: 'BUSINESS_500', message: '服务暂时不可用', traceId: 'trace-xyz-001' }
      }
    }
    const out = formatChatError(err)
    expect(out).toContain('trace-xyz-001')
    expect(out).toContain('Trace ID')
  })

  it('falls back to the status-based message when the backend gives no message', () => {
    const err = { response: { status: 403, data: { traceId: 'trace-403' } } }
    const out = formatChatError(err)
    expect(out).toContain('权限')
    expect(out).toContain('trace-403')
  })

  it('uses the SSE error payload when no axios response is present', () => {
    const err = {
      message: 'SSE 通道返回错误',
      response: { data: { traceId: 'sse-trace-1' } }
    }
    const out = formatChatError(err)
    expect(out).toContain('SSE 通道返回错误')
    expect(out).toContain('sse-trace-1')
  })

  it('does not duplicate the traceId when it is already in the message', () => {
    const err = {
      response: {
        status: 500,
        data: { message: '失败 (Trace ID: abc-123)', traceId: 'abc-123' }
      }
    }
    const out = formatChatError(err)
    const matches = out.match(/Trace ID:\s*abc-123/g) || []
    expect(matches.length).toBe(1)
  })

  it('handles a plain Error instance with message and a top-level traceId', () => {
    const err = new Error('无法连接 AI Engine')
    err.traceId = 'plain-trace-1'
    const out = formatChatError(err)
    expect(out).toContain('无法连接 AI Engine')
    expect(out).toContain('plain-trace-1')
  })

  it('falls back to a generic message when no message and no traceId are present', () => {
    const out = formatChatError(new Error(''))
    expect(typeof out).toBe('string')
    expect(out.length).toBeGreaterThan(0)
  })

  it('passes through a raw string input', () => {
    expect(formatChatError('自定义错误：模型不可用')).toBe('自定义错误：模型不可用')
  })
})
