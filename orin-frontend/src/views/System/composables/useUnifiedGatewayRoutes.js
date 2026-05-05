import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAllPolicies,
  getUnifiedGatewayControlPlaneCoverage,
  getRouteEffectiveConfig,
  getRoutes,
  getServices,
  createRoute,
  patchRoute,
  testRoute
} from '@/api/gateway'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess } from '@/viewmodels'

export function useUnifiedGatewayRoutes() {
  const state = reactive(createAsyncState())
  const routes = ref([])
  const services = ref([])
  const policies = ref({ rateLimitPolicies: [], circuitBreakerPolicies: [], retryPolicies: [] })
  const controlPlaneCoverage = ref({ summary: {}, endpoints: [] })
  const effectiveConfig = ref(null)
  const detailState = reactive(createAsyncState())
  const testResult = ref(null)

  const loadRoutes = async () => {
    markLoading(state)
    try {
      const [routeRows, serviceRows, policyRows, coverageRows] = await Promise.all([
        getRoutes(),
        getServices({ includeInstances: true }),
        getAllPolicies(),
        getUnifiedGatewayControlPlaneCoverage()
      ])
      routes.value = routeRows || []
      services.value = serviceRows || []
      policies.value = policyRows || policies.value
      controlPlaneCoverage.value = coverageRows || controlPlaneCoverage.value
      routes.value.length ? markSuccess(state) : markEmpty(state)
    } catch (error) {
      markError(state, error)
    }
  }

  const loadEffectiveConfig = async (routeId) => {
    markLoading(detailState)
    try {
      effectiveConfig.value = await getRouteEffectiveConfig(routeId)
      markSuccess(detailState)
    } catch (error) {
      effectiveConfig.value = null
      markError(detailState, error)
    }
  }

  const toggleRoute = async (row) => {
    const next = row.enabled
    try {
      await patchRoute(row.id, { enabled: next })
      ElMessage.success(next ? '入口已启用' : '入口已禁用')
    } catch (error) {
      row.enabled = !next
      ElMessage.error(error?.message || '入口状态更新失败')
    }
  }

  const runRouteTest = async (form) => {
    testResult.value = null
    try {
      testResult.value = await testRoute(form)
      return testResult.value
    } catch (error) {
      ElMessage.error(error?.message || '入口测试失败')
      throw error
    }
  }

  const createLocalControlPlaneRoute = async (endpoint) => {
    const method = endpoint.methods?.length === 1 ? endpoint.methods[0] : 'ALL'
    const request = {
      name: `后台入口 ${method} ${endpoint.pathPattern}`.slice(0, 100),
      pathPattern: endpoint.pathPattern,
      method,
      targetUrl: null,
      serviceId: null,
      authRequired: true,
      enabled: true,
      priority: 100,
      description: '统一网关后台入口单独配置'
    }
    try {
      await createRoute(request)
      ElMessage.success('已添加后台入口单独配置')
      await loadRoutes()
    } catch (error) {
      ElMessage.error(error?.message || '后台入口配置创建失败')
      throw error
    }
  }

  return {
    state,
    routes,
    services,
    policies,
    controlPlaneCoverage,
    effectiveConfig,
    detailState,
    testResult,
    loadRoutes,
    loadEffectiveConfig,
    toggleRoute,
    runRouteTest,
    createLocalControlPlaneRoute
  }
}
