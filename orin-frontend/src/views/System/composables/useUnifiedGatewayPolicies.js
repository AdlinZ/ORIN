import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAllPolicies,
  updateCircuitBreakerPolicy,
  updateRateLimitPolicy,
  updateRetryPolicy
} from '@/api/gateway'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess } from '@/viewmodels'

export function useUnifiedGatewayPolicies() {
  const state = reactive(createAsyncState())
  const policies = ref({ rateLimitPolicies: [], circuitBreakerPolicies: [], retryPolicies: [] })

  const loadPolicies = async () => {
    markLoading(state)
    try {
      policies.value = await getAllPolicies()
      const total = (policies.value.rateLimitPolicies?.length || 0)
        + (policies.value.circuitBreakerPolicies?.length || 0)
        + (policies.value.retryPolicies?.length || 0)
      total ? markSuccess(state) : markEmpty(state)
    } catch (error) {
      markError(state, error)
    }
  }

  const togglePolicy = async (type, row) => {
    const next = row.enabled
    try {
      if (type === 'rate-limit') await updateRateLimitPolicy(row.id, { enabled: next })
      if (type === 'circuit-breaker') await updateCircuitBreakerPolicy(row.id, { enabled: next })
      if (type === 'retry') await updateRetryPolicy(row.id, { enabled: next })
      ElMessage.success(next ? '策略已启用' : '策略已禁用')
    } catch (error) {
      row.enabled = !next
      ElMessage.error(error?.message || '策略状态更新失败')
    }
  }

  return {
    state,
    policies,
    loadPolicies,
    togglePolicy
  }
}
