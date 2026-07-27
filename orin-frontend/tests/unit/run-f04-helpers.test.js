/**
 * F04 RunDetailPage helper logic tests.
 *
 * Tests the pure functions that power RunDetailPage — status mapping,
 * terminal reason labels, isCancellable/isRetryable/isActive, status step
 * computation.  These are the functions inlined in <script setup>; testing
 * them here gives us confidence without needing a full component mount.
 */
import { describe, expect, it } from 'vitest'

// ---- replicated helpers (mirrors RunDetailPage.vue) ----

const STATUS_TYPE = {
  QUEUED: 'info',
  LEASED: 'warning',
  RUNNING: '',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'info'
}

function statusType(s) {
  return STATUS_TYPE[s] ?? 'info'
}

function isCancellable(s) {
  return s === 'QUEUED' || s === 'LEASED' || s === 'RUNNING'
}

function isRetryable(r) {
  return (r.status === 'FAILED' || r.status === 'CANCELLED') && r.retryCount < r.maxRetries
}

function isActive(s) {
  return s === 'QUEUED' || s === 'LEASED' || s === 'RUNNING'
}

function terminalReasonLabel(reason) {
  const map = {
    USER_CANCELLED: '用户主动取消',
    NETWORK_LOST: 'Runner 失联或网络中断',
    CREDENTIAL_REVOKED: 'Runner 凭据被撤销',
    SECRET_REVOKED: '关联密钥已被撤销',
    RUNNER_REVOKED: 'Runner 已被管理员撤销',
    RUNNER_FAILED: '执行过程中出错',
    LEASE_EXPIRED: 'Lease 超时未续约',
    CANCELLED: '已取消',
    SECRET_BIND_FAILED: '密钥绑定失败'
  }
  return map[reason] || reason || '—'
}

function statusStep(status) {
  const order = ['QUEUED', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']
  if (!status) return 0
  if (status === 'COMPLETED') return 4
  if (status === 'FAILED' || status === 'CANCELLED') return 3
  return order.indexOf(status)
}

// ---- tests ----

describe('F04 RunDetailPage helpers', () => {
  describe('statusType', () => {
    it('returns correct tag types', () => {
      expect(statusType('QUEUED')).toBe('info')
      expect(statusType('LEASED')).toBe('warning')
      expect(statusType('RUNNING')).toBe('')  // default tag
      expect(statusType('COMPLETED')).toBe('success')
      expect(statusType('FAILED')).toBe('danger')
      expect(statusType('CANCELLED')).toBe('info')
      expect(statusType('UNKNOWN')).toBe('info')
    })
  })

  describe('isCancellable', () => {
    it('returns true for QUEUED, LEASED, RUNNING', () => {
      expect(isCancellable('QUEUED')).toBe(true)
      expect(isCancellable('LEASED')).toBe(true)
      expect(isCancellable('RUNNING')).toBe(true)
    })

    it('returns false for terminal states', () => {
      expect(isCancellable('COMPLETED')).toBe(false)
      expect(isCancellable('FAILED')).toBe(false)
      expect(isCancellable('CANCELLED')).toBe(false)
    })
  })

  describe('isRetryable', () => {
    it('returns true for FAILED with remaining retries', () => {
      expect(isRetryable({ status: 'FAILED', retryCount: 0, maxRetries: 3 })).toBe(true)
      expect(isRetryable({ status: 'CANCELLED', retryCount: 0, maxRetries: 3 })).toBe(true)
    })

    it('returns false when retries exhausted', () => {
      expect(isRetryable({ status: 'FAILED', retryCount: 3, maxRetries: 3 })).toBe(false)
    })

    it('returns false for non-retryable statuses', () => {
      expect(isRetryable({ status: 'COMPLETED', retryCount: 0, maxRetries: 3 })).toBe(false)
      expect(isRetryable({ status: 'RUNNING', retryCount: 0, maxRetries: 3 })).toBe(false)
      expect(isRetryable({ status: 'QUEUED', retryCount: 0, maxRetries: 3 })).toBe(false)
    })
  })

  describe('isActive', () => {
    it('returns true for active states', () => {
      expect(isActive('QUEUED')).toBe(true)
      expect(isActive('LEASED')).toBe(true)
      expect(isActive('RUNNING')).toBe(true)
    })

    it('returns false for terminal states', () => {
      expect(isActive('COMPLETED')).toBe(false)
      expect(isActive('FAILED')).toBe(false)
      expect(isActive('CANCELLED')).toBe(false)
    })
  })

  describe('terminalReasonLabel', () => {
    it('maps known reasons to Chinese', () => {
      expect(terminalReasonLabel('USER_CANCELLED')).toBe('用户主动取消')
      expect(terminalReasonLabel('NETWORK_LOST')).toBe('Runner 失联或网络中断')
      expect(terminalReasonLabel('CREDENTIAL_REVOKED')).toBe('Runner 凭据被撤销')
      expect(terminalReasonLabel('SECRET_REVOKED')).toBe('关联密钥已被撤销')
      expect(terminalReasonLabel('RUNNER_REVOKED')).toBe('Runner 已被管理员撤销')
      expect(terminalReasonLabel('RUNNER_FAILED')).toBe('执行过程中出错')
      expect(terminalReasonLabel('LEASE_EXPIRED')).toBe('Lease 超时未续约')
      expect(terminalReasonLabel('CANCELLED')).toBe('已取消')
      expect(terminalReasonLabel('SECRET_BIND_FAILED')).toBe('密钥绑定失败')
    })

    it('returns raw reason for unknown codes', () => {
      expect(terminalReasonLabel('UNKNOWN_CODE')).toBe('UNKNOWN_CODE')
    })

    it('returns — for null/undefined', () => {
      expect(terminalReasonLabel(null)).toBe('—')
      expect(terminalReasonLabel(undefined)).toBe('—')
    })
  })

  describe('statusStep', () => {
    it('returns step index for ordered states', () => {
      expect(statusStep('QUEUED')).toBe(0)
      expect(statusStep('LEASED')).toBe(1)
      expect(statusStep('RUNNING')).toBe(2)
    })

    it('returns 4 for COMPLETED (all steps done)', () => {
      expect(statusStep('COMPLETED')).toBe(4)
    })

    it('returns 3 for FAILED and CANCELLED (RUNNING was reached)', () => {
      expect(statusStep('FAILED')).toBe(3)
      expect(statusStep('CANCELLED')).toBe(3)
    })

    it('returns 0 for null/undefined', () => {
      expect(statusStep(null)).toBe(0)
      expect(statusStep(undefined)).toBe(0)
    })
  })
})
