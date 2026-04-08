import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SystemGatewayV2 from '@/views/revamp/system/SystemGatewayV2.vue'

const pushMock = vi.fn()
const getAuditLogsMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock
  })
}))

vi.mock('@/api/audit', () => ({
  getAuditLogs: (...args) => getAuditLogsMock(...args)
}))

vi.mock('element-plus/theme-chalk/base.css', () => ({}), { virtual: true })

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { warning: vi.fn() }
  }
})

vi.mock('@/components/orin/OrinPageShell.vue', () => ({
  default: { template: '<div><slot /><slot name="actions" /></div>' }
}))

vi.mock('@/components/orin/OrinMaturityBadge.vue', () => ({
  default: { template: '<span />' }
}))

vi.mock('@/components/orin/OrinAuditTable.vue', () => ({
  default: {
    props: ['rows'],
    template: '<div class="audit-count">{{ rows.length }}</div>'
  }
}))

const createWrapper = () => mount(SystemGatewayV2, {
  global: {
    stubs: {
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div :class="$attrs.class" @click="$emit(\'click\')"><slot name="header" /><slot /></div>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-link': { template: '<a><slot /></a>' }
    }
  }
})

describe('SystemGatewayV2', () => {
  beforeEach(() => {
    pushMock.mockReset()
    getAuditLogsMock.mockReset()
  })

  const flush = async () => {
    await Promise.resolve()
    await Promise.resolve()
  }

  it('navigates to target route when clicking a gateway card', async () => {
    getAuditLogsMock.mockResolvedValue([])
    const wrapper = createWrapper()
    await flush()
    const cards = wrapper.findAll('.gateway-card')
    expect(cards.length).toBeGreaterThan(0)
    await cards[0].trigger('click')
    expect(pushMock).toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/dashboard/control/gateway')
  })

  it('falls back to built-in audit rows when backend returns empty', async () => {
    getAuditLogsMock.mockResolvedValue([])
    const wrapper = createWrapper()
    await flush()
    expect(wrapper.find('.audit-count').text()).toBe('3')
  })

  it('dispatches refresh event and reloads audit logs', async () => {
    getAuditLogsMock.mockResolvedValue([])
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')
    const wrapper = createWrapper()
    await flush()

    const refreshBtn = wrapper.findAll('button').find((btn) => btn.text().includes('刷新状态'))
    expect(refreshBtn).toBeTruthy()
    await refreshBtn.trigger('click')
    await flush()

    expect(dispatchSpy).toHaveBeenCalled()
    expect(getAuditLogsMock.mock.calls.length).toBeGreaterThanOrEqual(2)
    dispatchSpy.mockRestore()
  })
})
