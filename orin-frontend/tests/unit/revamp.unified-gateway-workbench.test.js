import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import UnifiedGateway from '@/views/System/UnifiedGateway.vue'
import { useUnifiedGatewayRoutes } from '@/views/System/composables/useUnifiedGatewayRoutes'
import { useUnifiedGatewayPolicies } from '@/views/System/composables/useUnifiedGatewayPolicies'

const getMock = vi.fn()
const postMock = vi.fn()
const patchMock = vi.fn()
const putMock = vi.fn()
const errorMock = vi.fn()
const successMock = vi.fn()
const replaceMock = vi.fn(() => Promise.resolve())

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: replaceMock })
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: (...args) => getMock(...args),
    patch: (...args) => patchMock(...args),
    post: (...args) => postMock(...args),
    put: (...args) => putMock(...args),
    delete: vi.fn()
  }
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      error: (...args) => errorMock(...args),
      success: (...args) => successMock(...args),
      warning: vi.fn()
    }
  }
})

vi.mock('@/views/System/components/gateway/UnifiedGatewayRoutesTab.vue', () => ({ default: { template: '<div class="routes-tab">入口配置工具</div>' } }))
vi.mock('@/views/System/components/gateway/UnifiedGatewayServicesTab.vue', () => ({ default: { template: '<div class="services-tab">上游服务</div>' } }))
vi.mock('@/views/System/components/gateway/UnifiedGatewayAclTab.vue', () => ({ default: { template: '<div class="acl-tab">访问名单</div>' } }))
vi.mock('@/views/System/components/gateway/UnifiedGatewayPoliciesTab.vue', () => ({ default: { template: '<div class="policies-tab">入口策略</div>' } }))
vi.mock('@/views/System/components/gateway/UnifiedGatewayRateLimitTab.vue', () => ({ default: { template: '<div class="rate-limit-tab">平台底线</div>' } }))
vi.mock('@/views/System/ApiKeyManagement.vue', () => ({ default: { template: '<div class="api-key-management">API Key 管理</div>' } }))

const stubs = {
  OrinPageShell: { template: '<div><slot name="actions" /><slot name="filters" /><slot /></div>' },
  OrinMetricStrip: { props: ['metrics'], template: '<div class="metrics">{{ metrics.length }}</div>' },
  OrinStatusSummary: { props: ['items'], template: '<div class="status">{{ items.length }}</div>' },
  OrinDataTable: { template: '<div><slot name="header" /><slot /></div>' },
  OrinAsyncState: { template: '<div><slot /></div>' },
  OrinDetailPanel: { template: '<section><slot /></section>' },
  OrinEmptyState: { template: '<div />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-space': { template: '<span><slot /></span>' },
  'el-switch': { template: '<input type="checkbox" />' },
  'el-drawer': { template: '<aside><slot /></aside>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-alert': { template: '<div><slot name="title" /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' }
}

describe('gateway workbench revamp', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    patchMock.mockReset()
    putMock.mockReset()
    errorMock.mockReset()
    successMock.mockReset()
    replaceMock.mockClear()
    getMock.mockImplementation((url) => {
      if (url === '/system/gateway/workbench') {
        return Promise.resolve({
          overview: { totalRequests: 1, qps: 0.1, avgLatencyMs: 20, errorRate: 0, activeRoutes: 1, healthyInstances: 1, unhealthyInstances: 0, topRoutes: [] },
          serviceHealth: [],
          routes: [],
          policyCounts: {},
          recentFailures: [],
          nextActions: ['处理需要关注的入口，补充认证、ACL 或限流策略'],
          controlPlaneCoverage: {
            summary: {
              totalEndpoints: 3,
              baselineGovernedEndpoints: 1,
              policyEnforcedEndpoints: 1,
              attentionRequiredEndpoints: 1,
              rescueReservedEndpoints: 1,
              explicitPolicyCoverageRate: 33.33
            },
            endpoints: [
              { pathPattern: '/api/v1/pricing', methods: ['GET'], status: 'ATTENTION_REQUIRED', reason: '缺少认证或单独配置' },
              { pathPattern: '/api/v1/system/gateway/routes', methods: ['GET'], status: 'RESCUE_RESERVED', reason: '救援入口保留最低可用直连能力' },
              { pathPattern: '/api/v1/agents/**', methods: ['ALL'], status: 'POLICY_ENFORCED', routeId: 1, routeName: 'agents-local', reason: '已绑定显式入口策略' }
            ]
          }
        })
      }
      if (url === '/system/gateway/routes') return Promise.resolve([])
      if (url === '/system/gateway/services') return Promise.resolve([])
      if (url === '/system/gateway/policies') return Promise.resolve({ rateLimitPolicies: [], circuitBreakerPolicies: [], retryPolicies: [] })
      if (url === '/system/gateway/control-plane/coverage') {
        return Promise.resolve({
          summary: { totalEndpoints: 1, baselineGovernedEndpoints: 0, policyEnforcedEndpoints: 0, attentionRequiredEndpoints: 1, rescueReservedEndpoints: 0, explicitPolicyCoverageRate: 0 },
          endpoints: [{ pathPattern: '/api/v1/pricing', methods: ['GET'], status: 'ATTENTION_REQUIRED', reason: '缺少认证或单独配置' }]
        })
      }
      return Promise.resolve({})
    })
    postMock.mockResolvedValue({})
  })

  it('renders runtime metrics first and exposes the gateway workspaces', async () => {
    const wrapper = mount(UnifiedGateway, { global: { stubs } })
    await Promise.resolve()
    await nextTick()

    const text = wrapper.text()
    expect(text).toContain('运行总览')
    expect(text).toContain('当前 QPS')
    expect(text).toContain('平均延迟')
    expect(text).toContain('错误率')
    expect(text).toContain('运行结论')

    const tabLabels = wrapper.findAll('.workspace-tab strong').map((label) => label.text())
    expect(tabLabels).toEqual(['运行态', '统一入口', '访问控制', '流量策略'])
    expect(tabLabels).not.toContain('上游服务')
    expect(tabLabels).not.toContain('密钥中心')
    expect(tabLabels).not.toContain('安全与流量')
  })

  it('switches from overview to the unified entry workspace', async () => {
    const wrapper = mount(UnifiedGateway, { global: { stubs } })
    await Promise.resolve()
    await nextTick()

    const routesButton = wrapper.findAll('.workspace-tab').find((button) => button.text().includes('统一入口'))
    await routesButton.trigger('click')
    await Promise.resolve()
    await nextTick()

    expect(getMock).toHaveBeenCalledWith('/system/gateway/routes')
    expect(wrapper.find('.routes-tab').exists()).toBe(true)
    expect(wrapper.find('.services-tab').exists()).toBe(true)
    expect(wrapper.text()).toContain('后台入口配置')
    expect(wrapper.text()).toContain('上游服务')
    expect(wrapper.text()).toContain('配置统一入口')
    expect(wrapper.text()).toContain('需要处理')
  })

  it('switches to the access control workspace', async () => {
    const wrapper = mount(UnifiedGateway, { global: { stubs } })
    await Promise.resolve()
    await nextTick()

    const accessButton = wrapper.findAll('.workspace-tab').find((button) => button.text().includes('访问控制'))
    await accessButton.trigger('click')
    await nextTick()

    expect(accessButton.classes()).toContain('active')
    expect(wrapper.find('.api-key-management').exists()).toBe(true)
    expect(wrapper.find('.acl-tab').exists()).toBe(true)
    expect(wrapper.text()).toContain('API Key 管理')
    expect(wrapper.text()).toContain('访问名单')
  })

  it('switches to the traffic policy workspace', async () => {
    const wrapper = mount(UnifiedGateway, { global: { stubs } })
    await Promise.resolve()
    await nextTick()

    const trafficButton = wrapper.findAll('.workspace-tab').find((button) => button.text().includes('流量策略'))
    await trafficButton.trigger('click')
    await Promise.resolve()
    await nextTick()

    expect(trafficButton.classes()).toContain('active')
    expect(getMock).toHaveBeenCalledWith('/system/gateway/policies')
    expect(wrapper.find('.policies-tab').exists()).toBe(true)
    expect(wrapper.find('.rate-limit-tab').exists()).toBe(true)
    expect(wrapper.text()).toContain('入口策略')
    expect(wrapper.text()).toContain('平台底线')
  })

  it('adds an explicit policy route from an attention-required control-plane endpoint', async () => {
    const routes = useUnifiedGatewayRoutes()
    await routes.loadRoutes()

    await routes.createLocalControlPlaneRoute({
      pathPattern: '/api/v1/pricing',
      methods: ['GET']
    })

    expect(postMock).toHaveBeenCalledWith('/system/gateway/routes', expect.objectContaining({
      name: '后台入口 GET /api/v1/pricing',
      pathPattern: '/api/v1/pricing',
      method: 'GET',
      targetUrl: null,
      serviceId: null,
      authRequired: true
    }))
    expect(successMock).toHaveBeenCalled()
  })

  it('does not render deprecated governance wording', async () => {
    const wrapper = mount(UnifiedGateway, { global: { stubs } })
    await Promise.resolve()
    await nextTick()

    expect(wrapper.text()).not.toContain('仅观测')
    expect(wrapper.text()).not.toContain('裸露')
    expect(wrapper.text()).not.toContain('纳管')
    expect(wrapper.text()).not.toContain('为什么要用')
    expect(wrapper.text()).not.toContain('路由编排')
    expect(wrapper.text()).not.toContain('治理策略')
    expect(wrapper.text()).not.toContain('显式策略')
    expect(wrapper.text()).not.toContain('基础治理')
  })

  it('rolls back route switch state when update fails', async () => {
    const routes = useUnifiedGatewayRoutes()
    const row = { id: 1, enabled: false }
    patchMock.mockRejectedValue(new Error('denied'))

    await routes.toggleRoute(row)

    expect(row.enabled).toBe(true)
    expect(errorMock).toHaveBeenCalled()
  })

  it('rolls back policy switch state when update fails', async () => {
    const gatewayPolicies = useUnifiedGatewayPolicies()
    const row = { id: 1, enabled: false }
    putMock.mockRejectedValue(new Error('denied'))

    await gatewayPolicies.togglePolicy('rate-limit', row)

    expect(row.enabled).toBe(true)
    expect(errorMock).toHaveBeenCalled()
  })
})
