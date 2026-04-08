import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RevampRolloutCenterV2 from '@/views/revamp/system/RevampRolloutCenterV2.vue'

const pushMock = vi.fn()
const resolveMock = vi.fn((path) => ({
  matched: typeof path === 'string' && path.startsWith('/dashboard') ? [{}] : []
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
    resolve: resolveMock
  })
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn()
    }
  }
})

vi.mock('@/components/orin/OrinPageShell.vue', () => ({
  default: { template: '<div><slot /><slot name="actions" /></div>' }
}))

vi.mock('@/components/orin/OrinMaturityBadge.vue', () => ({
  default: { template: '<span />' }
}))

const storage = {}
const localStorageMock = {
  getItem: (key) => (Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : null),
  setItem: (key, value) => {
    storage[key] = String(value)
  },
  removeItem: (key) => {
    delete storage[key]
  },
  clear: () => {
    Object.keys(storage).forEach((key) => delete storage[key])
  }
}

const createWrapper = () => mount(RevampRolloutCenterV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /></div>' },
      OrinMaturityBadge: { template: '<span />' },
      'el-space': { template: '<div><slot /></div>' },
      'el-card': { template: '<div><slot /><slot name="header" /></div>' },
      'el-timeline': { template: '<div><slot /></div>' },
      'el-timeline-item': { template: '<div><slot /></div>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-table': true,
      'el-table-column': true,
      'el-switch': { template: '<input type="checkbox" />' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' }
    }
  }
})

describe('RevampRolloutCenterV2', () => {
  let createElementSpy
  let createObjectURLSpy
  let revokeObjectURLSpy
  const rawCreateElement = document.createElement.bind(document)

  beforeEach(() => {
    Object.defineProperty(window, 'localStorage', {
      value: localStorageMock,
      configurable: true
    })
    localStorageMock.clear()
    pushMock.mockReset()
    resolveMock.mockClear()

    createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test')
    revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tagName) => {
      if (tagName === 'a') {
        return {
          click: vi.fn(),
          set href(value) { this._href = value },
          get href() { return this._href },
          set download(value) { this._download = value },
          get download() { return this._download }
        }
      }
      return rawCreateElement(tagName)
    })
  })

  afterEach(() => {
    createElementSpy.mockRestore()
    createObjectURLSpy.mockRestore()
    revokeObjectURLSpy.mockRestore()
  })

  it('enables all revamp flags when clicking 全部启用', async () => {
    const wrapper = createWrapper()
    const allEnableBtn = wrapper.findAll('button').find((btn) => btn.text().includes('全部启用'))
    expect(allEnableBtn).toBeTruthy()
    await allEnableBtn.trigger('click')

    expect(window.localStorage.getItem('orin_ff_revampAgentsHub')).toBe('true')
    expect(window.localStorage.getItem('orin_ff_revampSystemGateway')).toBe('true')
  })

  it('disables all revamp flags when clicking 全部关闭', async () => {
    window.localStorage.setItem('orin_ff_revampAgentsHub', 'true')
    window.localStorage.setItem('orin_ff_revampSystemGateway', 'true')

    const wrapper = createWrapper()
    const allDisableBtn = wrapper.findAll('button').find((btn) => btn.text().includes('全部关闭'))
    expect(allDisableBtn).toBeTruthy()
    await allDisableBtn.trigger('click')

    expect(window.localStorage.getItem('orin_ff_revampAgentsHub')).toBe('false')
    expect(window.localStorage.getItem('orin_ff_revampSystemGateway')).toBe('false')
  })

  it('resets overrides when clicking 重置默认', async () => {
    window.localStorage.setItem('orin_ff_revampAgentsHub', 'false')

    const wrapper = createWrapper()
    const resetBtn = wrapper.findAll('button').find((btn) => btn.text().includes('重置默认'))
    expect(resetBtn).toBeTruthy()
    await resetBtn.trigger('click')

    expect(window.localStorage.getItem('orin_ff_revampAgentsHub')).toBe(null)
  })

  it('runs smoke check and shows last run time', async () => {
    const wrapper = createWrapper()
    const smokeBtn = wrapper.findAll('button').find((btn) => btn.text().includes('一键冒烟检查'))
    expect(smokeBtn).toBeTruthy()
    await smokeBtn.trigger('click')

    expect(resolveMock).toHaveBeenCalled()
    expect(wrapper.text()).toContain('最近检查：')
    expect(window.localStorage.getItem('orin_revamp_smoke_history')).toBeTruthy()
  })

  it('exports smoke report json', async () => {
    const wrapper = createWrapper()
    const exportBtn = wrapper.findAll('button').find((btn) => btn.text().includes('导出 JSON'))
    expect(exportBtn).toBeTruthy()
    await exportBtn.trigger('click')

    expect(createObjectURLSpy).toHaveBeenCalled()
    expect(revokeObjectURLSpy).toHaveBeenCalled()
  })
})
