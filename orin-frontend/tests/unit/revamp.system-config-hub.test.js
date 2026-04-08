import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SystemConfigHubV2 from '@/views/revamp/system/SystemConfigHubV2.vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock
  })
}))

vi.mock('element-plus/theme-chalk/base.css', () => ({}), { virtual: true })

vi.mock('@/components/orin/OrinPageShell.vue', () => ({
  default: { template: '<div><slot /><slot name="actions" /></div>' }
}))

vi.mock('@/components/orin/OrinMaturityBadge.vue', () => ({
  default: { template: '<span />' }
}))

const createWrapper = () => mount(SystemConfigHubV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /></div>' },
      OrinMaturityBadge: { template: '<span />' },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div class="entry-card" @click="$emit(\'click\')"><slot /></div>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-link': { template: '<a><slot /></a>' }
    }
  }
})

describe('SystemConfigHubV2', () => {
  beforeEach(() => {
    pushMock.mockReset()
  })

  it('navigates when clicking an entry card', async () => {
    const wrapper = createWrapper()
    const cards = wrapper.findAll('.entry-card')
    expect(cards.length).toBeGreaterThan(0)
    await cards[0].trigger('click')
    expect(pushMock).toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/dashboard/runtime/server')
  })

  it('dispatches refresh event on refresh action', async () => {
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')
    const wrapper = createWrapper()
    const refreshBtn = wrapper.findAll('button').find((btn) => btn.text().includes('刷新'))
    expect(refreshBtn).toBeTruthy()
    await refreshBtn.trigger('click')
    expect(dispatchSpy).toHaveBeenCalled()
    dispatchSpy.mockRestore()
  })
})
