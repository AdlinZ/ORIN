export const AsyncStatus = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  EMPTY: 'empty',
  SUCCESS: 'success',
  ERROR: 'error',
  RETRYING: 'retrying',
  PARTIAL: 'partial'
})

export function createAsyncState(extra = {}) {
  return {
    status: AsyncStatus.IDLE,
    error: null,
    updatedAt: null,
    ...extra
  }
}

export function markLoading(state) {
  state.status = AsyncStatus.LOADING
  state.error = null
}

export function markRetrying(state) {
  state.status = AsyncStatus.RETRYING
}

export function markSuccess(state) {
  state.status = AsyncStatus.SUCCESS
  state.error = null
  state.updatedAt = Date.now()
}

export function markEmpty(state) {
  state.status = AsyncStatus.EMPTY
  state.error = null
  state.updatedAt = Date.now()
}

export function markError(state, error) {
  state.status = AsyncStatus.ERROR
  state.error = error || null
  state.updatedAt = Date.now()
}

export function markPartial(state, error = null) {
  state.status = AsyncStatus.PARTIAL
  state.error = error
  state.updatedAt = Date.now()
}
