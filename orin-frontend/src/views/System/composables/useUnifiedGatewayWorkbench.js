import { computed, reactive, ref } from 'vue'
import {
  getAllPolicies,
  getUnifiedGatewayControlPlaneCoverage,
  getUnifiedGatewayOverview,
  getUnifiedGatewayWorkbench,
  getRoutes
} from '@/api/gateway'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess } from '@/viewmodels'

export function useUnifiedGatewayWorkbench() {
  const state = reactive(createAsyncState())
  const workbench = ref({
    overview: {},
    serviceHealth: [],
    routes: [],
    policyCounts: {},
    recentFailures: [],
    nextActions: [],
    controlPlaneCoverage: emptyCoverage()
  })

  const metrics = computed(() => {
    const overview = workbench.value.overview || {}
    return [
      { key: 'requests', label: '总请求数', value: formatNumber(overview.totalRequests), meta: '累计网关流量' },
      { key: 'qps', label: '当前 QPS', value: overview.qps ?? 0, meta: '最近 6 分钟估算' },
      { key: 'latency', label: '平均延迟', value: `${overview.avgLatencyMs ?? 0}ms`, meta: '近 1 小时成功请求' },
      {
        key: 'errorRate',
        label: '错误率',
        value: `${overview.errorRate ?? 0}%`,
        meta: '异常请求占比',
        intent: Number(overview.errorRate || 0) > 0 ? 'danger' : 'success'
      },
      {
        key: 'coverage',
        label: '单独配置覆盖',
        value: `${workbench.value.controlPlaneCoverage?.summary?.explicitPolicyCoverageRate ?? workbench.value.controlPlaneCoverage?.summary?.managedRate ?? 0}%`,
        meta: '后台入口增强配置覆盖度',
        intent: (workbench.value.controlPlaneCoverage?.summary?.attentionRequiredEndpoints ?? 0) > 0 ? 'warning' : 'success'
      }
    ]
  })

  const statusItems = computed(() => {
    const overview = workbench.value.overview || {}
    const failures = workbench.value.recentFailures || []
    const coverage = workbench.value.controlPlaneCoverage?.summary || {}
    return [
      {
        key: 'services',
        label: '服务健康',
        value: `${overview.healthyInstances ?? 0}/${(overview.healthyInstances ?? 0) + (overview.unhealthyInstances ?? 0)}`,
        meta: (overview.unhealthyInstances ?? 0) > 0 ? '存在异常实例，需要处理' : '实例健康状态正常',
        intent: (overview.unhealthyInstances ?? 0) > 0 ? 'danger' : 'success'
      },
      {
        key: 'routes',
        label: '活跃入口',
        value: overview.activeRoutes ?? 0,
        meta: '当前参与匹配的入口数量',
        intent: (overview.activeRoutes ?? 0) > 0 ? 'success' : 'warning'
      },
      {
        key: 'failures',
        label: '最近失败',
        value: failures.length,
        meta: failures.length ? '点击诊断记录定位入口' : '暂无失败诊断记录',
        intent: failures.length ? 'warning' : 'success'
      },
      {
        key: 'control-plane',
        label: '后台入口',
        value: `${coverage.baselineGovernedEndpoints ?? 0}/${coverage.totalEndpoints ?? 0}`,
        meta: (coverage.attentionRequiredEndpoints ?? 0) > 0 ? `${coverage.attentionRequiredEndpoints} 个入口需要处理` : '后台入口处于基础保护链路中',
        intent: (coverage.attentionRequiredEndpoints ?? 0) > 0 ? 'warning' : 'success'
      },
      {
        key: 'rescue',
        label: '救援通道',
        value: coverage.rescueReservedEndpoints ?? 0,
        meta: '登录、健康检查、统一网关修复入口',
        intent: (coverage.rescueReservedEndpoints ?? 0) > 0 ? 'success' : 'warning'
      }
    ]
  })

  const loadWorkbench = async () => {
    markLoading(state)
    try {
      const data = await getUnifiedGatewayWorkbench()
      workbench.value = data || workbench.value
      if (!workbench.value.routes?.length && !workbench.value.serviceHealth?.length) {
        markEmpty(state)
      } else {
        markSuccess(state)
      }
    } catch (error) {
      try {
        workbench.value = await loadWorkbenchFallback()
        markSuccess(state)
      } catch (fallbackError) {
        markError(state, fallbackError || error)
      }
    }
  }

  return {
    state,
    workbench,
    metrics,
    statusItems,
    loadWorkbench
  }
}

async function loadWorkbenchFallback() {
  const [overview, routes, policies, coverage] = await Promise.all([
    getUnifiedGatewayOverview(),
    getRoutes(),
    getAllPolicies(),
    getUnifiedGatewayControlPlaneCoverage()
  ])
  const routeRows = routes || []
  const failureRows = []
  return {
    overview: overview || {},
    serviceHealth: overview?.serviceHealth || [],
    routes: routeRows.map((route) => ({
      id: route.id,
      name: route.name,
      pathPattern: route.pathPattern,
      method: route.method,
      targetType: route.targetUrl ? 'DIRECT' : route.serviceId ? 'SERVICE' : 'LOCAL',
      target: route.targetUrl || route.serviceName || (route.serviceId ? `Service#${route.serviceId}` : 'ORIN 后台处理'),
      enabled: route.enabled,
      priority: route.priority,
      authRequired: route.authRequired,
      policyCount: [route.rateLimitPolicyId, route.circuitBreakerPolicyId, route.retryPolicyId, route.authRequired].filter(Boolean).length
    })),
    policyCounts: {
      rateLimit: policies?.rateLimitPolicies?.length || 0,
      circuitBreaker: policies?.circuitBreakerPolicies?.length || 0,
      retry: policies?.retryPolicies?.length || 0,
      acl: 0
    },
    recentFailures: failureRows,
    nextActions: buildFallbackActions(overview, routeRows, coverage),
    controlPlaneCoverage: coverage || emptyCoverage()
  }
}

function buildFallbackActions(overview, routes, coverage) {
  if ((coverage?.summary?.attentionRequiredEndpoints || 0) > 0) return ['处理需要关注的入口，补充认证、ACL 或限流策略']
  if (!routes?.length && !overview?.activeRoutes) return ['创建第一个开放入口，接入模型 API 或上游服务']
  if ((overview?.unhealthyInstances || 0) > 0) return ['检查异常实例并触发健康检查']
  return ['使用入口测试验证核心 API 路径']
}

function emptyCoverage() {
  return {
    summary: {
      totalEndpoints: 0,
      baselineGovernedEndpoints: 0,
      policyEnforcedEndpoints: 0,
      attentionRequiredEndpoints: 0,
      explicitPolicyCoverageRate: 0,
      managedEndpoints: 0,
      observedOnlyEndpoints: 0,
      rescueReservedEndpoints: 0,
      managedRate: 0
    },
    endpoints: []
  }
}

function formatNumber(value) {
  const num = Number(value || 0)
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return String(num)
}
