import { describe, expect, it } from 'vitest'
import {
  AsyncStatus,
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markPartial,
  markRetrying,
  markSuccess
} from '@/viewmodels/asyncState'

describe('revamp async state', () => {
  it('creates idle state by default', () => {
    const state = createAsyncState()
    expect(state.status).toBe(AsyncStatus.IDLE)
    expect(state.error).toBeNull()
  })

  it('transitions through loading -> success', () => {
    const state = createAsyncState()
    markLoading(state)
    expect(state.status).toBe(AsyncStatus.LOADING)
    markSuccess(state)
    expect(state.status).toBe(AsyncStatus.SUCCESS)
    expect(typeof state.updatedAt).toBe('number')
  })

  it('supports empty / error / retrying / partial', () => {
    const state = createAsyncState()
    markEmpty(state)
    expect(state.status).toBe(AsyncStatus.EMPTY)
    const error = new Error('boom')
    markError(state, error)
    expect(state.status).toBe(AsyncStatus.ERROR)
    expect(state.error).toBe(error)
    markRetrying(state)
    expect(state.status).toBe(AsyncStatus.RETRYING)
    markPartial(state)
    expect(state.status).toBe(AsyncStatus.PARTIAL)
  })
})
