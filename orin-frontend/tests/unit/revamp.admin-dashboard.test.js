import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AdminDashboard from '@/views/revamp/system/AdminDashboard.vue'
import { ROUTES } from '@/router/routes'

const pushMock = vi.fn()
const getDashboardSummaryMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock })
}))

vi.mock('@/api/dashboard', () => ({
  getDashboardSummary: (...args) => getDashboardSummaryMock(...args)
}))

const summary = {
  systemHealth: {
    backend: { status: 'UP' },
    aiEngine: { status: 'UP', reachable: true }
  },
  metrics: { failedTasks: 3 },
  adminStats: {
    totalUsers: 11,
    totalApiKeys: 3,
    activeAlerts: 2,
    resolvedAlerts: 8
  },
  topAlertEvents: [
    {
      method: 'POST',
      endpoint: '/api/v1/example',
      statusCode: 500,
      createdAt: '2026-07-28T10:00:00Z'
    }
  ]
}

const createWrapper = () => mount(AdminDashboard, {
  global: {
    stubs: {
      OrinPageShell: {
        props: ['title', 'description'],
        template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>'
      },
      OrinAsyncState: { template: '<div><slot /></div>' },
      OrinDataTable: { template: '<div><slot /></div>' },
      'el-button': {
        template: '<button @click="$emit(\'click\')"><slot /></button>'
      },
      'el-tag': { template: '<span><slot /></span>' },
      'el-table': {
        props: ['data'],
        template: '<div class="table">{{ data?.length || 0 }}</div>'
      },
      'el-table-column': { template: '<div />' }
    }
  }
})

describe('AdminDashboard', () => {
  beforeEach(() => {
    pushMock.mockReset()
    getDashboardSummaryMock.mockReset()
    getDashboardSummaryMock.mockResolvedValue(summary)
  })

  it('turns system settings into an action-oriented governance entry', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('系统设置')
    expect(wrapper.text()).toContain('2 条运行告警需要关注')
    expect(wrapper.text()).toContain('用户与访问')
    expect(wrapper.text()).toContain('审计记录')
    expect(wrapper.text()).toContain('高级运维')
    expect(wrapper.text()).not.toContain('智能体总数')
    expect(wrapper.text()).not.toContain('工作流总数')
  })

  it('keeps risky configuration behind explicit advanced actions', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '环境参数').trigger('click')
    expect(pushMock).toHaveBeenCalledWith(ROUTES.SYSTEM.SETTINGS_MONITOR)

    await wrapper.findAll('button').find((button) => button.text() === '审计策略').trigger('click')
    expect(pushMock).toHaveBeenCalledWith(ROUTES.SYSTEM.AUDIT_SETTINGS)
  })
})
