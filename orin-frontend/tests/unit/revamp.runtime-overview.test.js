import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RuntimeOverviewV2 from '@/views/revamp/monitor/RuntimeOverviewV2.vue'

const getGlobalSummaryMock = vi.fn()
const getCallSuccessRateMock = vi.fn()
const getLangfuseStatusMock = vi.fn()
const warningMock = vi.fn()

vi.mock('@/api/monitor', () => ({
  getGlobalSummary: (...args) => getGlobalSummaryMock(...args),
  getCallSuccessRate: (...args) => getCallSuccessRateMock(...args),
  getLangfuseStatus: (...args) => getLangfuseStatusMock(...args)
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      warning: (...args) => warningMock(...args)
    }
  }
})

const createWrapper = () => mount(RuntimeOverviewV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /></div>' },
      StatCard: {
        props: ['label', 'value'],
        template: '<div class="stat-card">{{ label }}:{{ value }}</div>'
      },
      OrinAsyncState: {
        props: ['status'],
        template: '<div class="async-state">{{ status }}<slot /></div>'
      },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div><slot name="header" /><slot /></div>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-link': { template: '<a><slot /></a>' }
    }
  }
})

const flush = async () => {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

describe('RuntimeOverviewV2', () => {
  beforeEach(() => {
    getGlobalSummaryMock.mockReset()
    getCallSuccessRateMock.mockReset()
    getLangfuseStatusMock.mockReset()
    warningMock.mockReset()
  })

  it('marks success when all monitor APIs succeed', async () => {
    getGlobalSummaryMock.mockResolvedValue({ onlineAgents: 8, requestCount: 1000 })
    getCallSuccessRateMock.mockResolvedValue({ totalCalls: 100, successCalls: 95, successRate: 95 })
    getLangfuseStatusMock.mockResolvedValue({ enabled: true, configured: true })

    const wrapper = createWrapper()
    await flush()

    expect(getGlobalSummaryMock).toHaveBeenCalledTimes(1)
    expect(getCallSuccessRateMock).toHaveBeenCalledTimes(1)
    expect(getLangfuseStatusMock).toHaveBeenCalledTimes(1)
    const statusText = wrapper.findAll('.async-state')[0].text()
    expect(statusText).toContain('success')
    expect(warningMock).not.toHaveBeenCalled()
  })

  it('marks partial and warns when part of monitor APIs fail', async () => {
    getGlobalSummaryMock.mockResolvedValue({ onlineAgents: 5, requestCount: 500 })
    getCallSuccessRateMock.mockRejectedValue(new Error('rate fail'))
    getLangfuseStatusMock.mockResolvedValue({ enabled: false, configured: false })

    const wrapper = createWrapper()
    await flush()

    expect(getGlobalSummaryMock).toHaveBeenCalledTimes(1)
    expect(getCallSuccessRateMock).toHaveBeenCalledTimes(1)
    expect(getLangfuseStatusMock).toHaveBeenCalledTimes(1)
    const statusText = wrapper.findAll('.async-state')[0].text()
    expect(statusText).toContain('partial')
    expect(warningMock).toHaveBeenCalledWith('部分监控数据加载失败，已展示可用数据')
  })
})
