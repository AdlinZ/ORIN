import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TraceViewer from '@/views/Trace/TraceViewer.vue'

const mocks = vi.hoisted(() => ({
  route: { params: {} },
  routerPush: vi.fn(() => Promise.resolve()),
  getRecentTraces: vi.fn(),
  getTrace: vi.fn(),
  getTraceStats: vi.fn(),
  getTraceLink: vi.fn(),
  searchTraces: vi.fn(),
  chartSetOption: vi.fn(),
  chartDispose: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush
  })
}))

vi.mock('@/api/trace', () => ({
  getRecentTraces: mocks.getRecentTraces,
  getTrace: mocks.getTrace,
  getTraceStats: mocks.getTraceStats,
  getTraceLink: mocks.getTraceLink,
  searchTraces: mocks.searchTraces
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: mocks.chartSetOption,
    dispose: mocks.chartDispose
  }))
}))

const mountTraceViewer = () => mount(TraceViewer, {
  global: {
    directives: {
      loading: {}
    },
    stubs: {
      PageHeader: true,
      ElAlert: { template: '<div><slot /></div>' },
      ElButton: { template: '<button @click="$emit(`click`)"><slot /></button>' },
      ElCard: { template: '<section><slot name="header" /><slot /></section>' },
      ElCol: { template: '<div><slot /></div>' },
      ElDescriptions: { template: '<div><slot /></div>' },
      ElDescriptionsItem: { template: '<div><slot /></div>' },
      ElDialog: { template: '<div><slot /></div>' },
      ElEmpty: true,
      ElInput: true,
      ElRow: { template: '<div><slot /></div>' },
      ElTable: true,
      ElTableColumn: true,
      ElTag: { template: '<span><slot /></span>' }
    }
  }
})

describe('TraceViewer', () => {
  beforeEach(() => {
    mocks.route.params = {}
    mocks.routerPush.mockClear()
    mocks.getRecentTraces.mockReset()
    mocks.getTrace.mockReset()
    mocks.getTraceStats.mockReset()
    mocks.getTraceLink.mockReset()
    mocks.searchTraces.mockReset()
    mocks.chartSetOption.mockClear()
    mocks.chartDispose.mockClear()
    mocks.getRecentTraces.mockResolvedValue([])
    mocks.getTrace.mockResolvedValue([])
    mocks.getTraceStats.mockResolvedValue({})
    mocks.getTraceLink.mockResolvedValue({})
  })

  it('loads recent traces when route has no traceId', async () => {
    mountTraceViewer()
    await flushPromises()

    expect(mocks.getRecentTraces).toHaveBeenCalledWith(20)
    expect(mocks.getTrace).not.toHaveBeenCalled()
  })

  it('loads trace detail when route contains traceId', async () => {
    mocks.route.params = { traceId: 'trace-1' }
    mocks.getTrace.mockResolvedValue([
      {
        traceId: 'trace-1',
        stepName: 'Start',
        status: 'SUCCESS',
        startedAt: '2026-05-03T12:00:00',
        completedAt: '2026-05-03T12:00:01',
        durationMs: 1000
      }
    ])
    mocks.getTraceStats.mockResolvedValue({ totalSteps: 1, successCount: 1 })
    mocks.getTraceLink.mockResolvedValue({ available: true, link: 'https://langfuse.local/trace-1' })

    mountTraceViewer()
    await flushPromises()

    expect(mocks.getTrace).toHaveBeenCalledWith('trace-1')
    expect(mocks.getTraceStats).toHaveBeenCalledWith('trace-1')
    expect(mocks.getTraceLink).toHaveBeenCalledWith('trace-1')
    expect(mocks.getRecentTraces).not.toHaveBeenCalled()
  })
})
