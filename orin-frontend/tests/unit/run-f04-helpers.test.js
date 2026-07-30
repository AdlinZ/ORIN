/**
 * F04 RunDetailPage helper logic tests.
 *
 * Tests the pure functions that power RunDetailPage — status mapping,
 * terminal reason labels, isCancellable/isRetryable/isActive, status step
 * computation.  These are the functions inlined in <script setup>; testing
 * them here gives us confidence without needing a full component mount.
 */
import { describe, expect, it } from 'vitest'
import {
  getRunStatusMeta,
  getRunStatusStep,
  getTerminalReasonLabel,
  isRunActive,
  isRunCancellable,
  isRunRetryable,
} from '@/views/workspace/coreLoopPresentation'

// ---- tests ----

describe('F04 RunDetailPage helpers', () => {
  describe('statusType', () => {
    it('returns correct tag types', () => {
      expect(getRunStatusMeta('QUEUED').type).toBe('warning')
      expect(getRunStatusMeta('LEASED').type).toBe('warning')
      expect(getRunStatusMeta('RUNNING').type).toBe('primary')
      expect(getRunStatusMeta('COMPLETED').type).toBe('success')
      expect(getRunStatusMeta('FAILED').type).toBe('danger')
      expect(getRunStatusMeta('CANCELLED').type).toBe('info')
      expect(getRunStatusMeta('UNKNOWN').type).toBe('info')
    })
  })

  describe('isCancellable', () => {
    it('returns true for QUEUED, LEASED, RUNNING', () => {
      expect(isRunCancellable('QUEUED')).toBe(true)
      expect(isRunCancellable('LEASED')).toBe(true)
      expect(isRunCancellable('RUNNING')).toBe(true)
    })

    it('returns false for terminal states', () => {
      expect(isRunCancellable('COMPLETED')).toBe(false)
      expect(isRunCancellable('FAILED')).toBe(false)
      expect(isRunCancellable('CANCELLED')).toBe(false)
    })
  })

  describe('isRetryable', () => {
    it('returns true for FAILED with remaining retries', () => {
      expect(isRunRetryable({ status: 'FAILED', retryCount: 0, maxRetries: 3 })).toBe(true)
      expect(isRunRetryable({ status: 'CANCELLED', retryCount: 0, maxRetries: 3 })).toBe(true)
    })

    it('returns false when retries exhausted', () => {
      expect(isRunRetryable({ status: 'FAILED', retryCount: 3, maxRetries: 3 })).toBe(false)
    })

    it('returns false for non-retryable statuses', () => {
      expect(isRunRetryable({ status: 'COMPLETED', retryCount: 0, maxRetries: 3 })).toBe(false)
      expect(isRunRetryable({ status: 'RUNNING', retryCount: 0, maxRetries: 3 })).toBe(false)
      expect(isRunRetryable({ status: 'QUEUED', retryCount: 0, maxRetries: 3 })).toBe(false)
    })
  })

  describe('isActive', () => {
    it('returns true for active states', () => {
      expect(isRunActive('QUEUED')).toBe(true)
      expect(isRunActive('LEASED')).toBe(true)
      expect(isRunActive('RUNNING')).toBe(true)
    })

    it('returns false for terminal states', () => {
      expect(isRunActive('COMPLETED')).toBe(false)
      expect(isRunActive('FAILED')).toBe(false)
      expect(isRunActive('CANCELLED')).toBe(false)
    })
  })

  describe('terminalReasonLabel', () => {
    it('maps known reasons to Chinese', () => {
      expect(getTerminalReasonLabel('USER_CANCELLED')).toBe('用户主动取消')
      expect(getTerminalReasonLabel('NETWORK_LOST')).toBe('Runner 失联或网络中断')
      expect(getTerminalReasonLabel('CREDENTIAL_REVOKED')).toBe('Runner 凭据被撤销')
      expect(getTerminalReasonLabel('SECRET_REVOKED')).toBe('关联密钥已被撤销')
      expect(getTerminalReasonLabel('RUNNER_REVOKED')).toBe('Runner 已被管理员撤销')
      expect(getTerminalReasonLabel('RUNNER_FAILED')).toBe('执行过程中出错')
      expect(getTerminalReasonLabel('LEASE_EXPIRED')).toBe('Lease 超时未续约')
      expect(getTerminalReasonLabel('CANCELLED')).toBe('已取消')
      expect(getTerminalReasonLabel('SECRET_BIND_FAILED')).toBe('密钥绑定失败')
    })

    it('returns raw reason for unknown codes', () => {
      expect(getTerminalReasonLabel('UNKNOWN_CODE')).toBe('UNKNOWN_CODE')
    })

    it('returns — for null/undefined', () => {
      expect(getTerminalReasonLabel(null)).toBe('—')
      expect(getTerminalReasonLabel(undefined)).toBe('—')
    })
  })

  describe('statusStep', () => {
    it('returns step index for ordered states', () => {
      expect(getRunStatusStep('QUEUED')).toBe(0)
      expect(getRunStatusStep('LEASED')).toBe(1)
      expect(getRunStatusStep('RUNNING')).toBe(2)
    })

    it('returns 4 for COMPLETED (all steps done)', () => {
      expect(getRunStatusStep('COMPLETED')).toBe(4)
    })

    it('returns 3 for FAILED and CANCELLED (RUNNING was reached)', () => {
      expect(getRunStatusStep('FAILED')).toBe(3)
      expect(getRunStatusStep('CANCELLED')).toBe(3)
    })

    it('returns 0 for null/undefined', () => {
      expect(getRunStatusStep(null)).toBe(0)
      expect(getRunStatusStep(undefined)).toBe(0)
    })
  })
})
