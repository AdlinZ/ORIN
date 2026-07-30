import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ProductOverviewPage from '@/views/workspace/overview/ProductOverviewPage.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  listAgents: vi.fn(),
  listRunners: vi.fn(),
  listRuns: vi.fn(),
  listEndpoints: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/domains/agent/api', () => ({
  listAgents: (...args) => mocks.listAgents(...args),
}))

vi.mock('@/api/runner', () => ({
  listRunners: (...args) => mocks.listRunners(...args),
}))

vi.mock('@/domains/run/api', () => ({
  listRuns: (...args) => mocks.listRuns(...args),
}))

vi.mock('@/domains/endpoint/api', () => ({
  listEndpoints: (...args) => mocks.listEndpoints(...args),
}))

const createWrapper = () => mount(ProductOverviewPage, {
  global: {
    stubs: {
      'el-alert': {
        props: ['title'],
        template: '<div class="alert-stub">{{ title }}<slot /></div>',
      },
      'el-button': {
        template: '<button @click="$emit(\'click\')"><slot /></button>',
      },
      'el-card': {
        template: '<section><slot name="header" /><slot /></section>',
      },
      'el-tag': {
        template: '<span><slot /></span>',
      },
      'el-icon': {
        template: '<i><slot /></i>',
      },
    },
  },
})

describe('ProductOverviewPage', () => {
  beforeEach(() => {
    mocks.push.mockReset()
    mocks.listAgents.mockReset()
    mocks.listRunners.mockReset()
    mocks.listRuns.mockReset()
    mocks.listEndpoints.mockReset()
  })

  it('makes a missing online Runner a visible product blocker', async () => {
    mocks.listAgents.mockResolvedValue([
      { agentId: 'ag-1', activeVersionStatus: 'FROZEN' },
    ])
    mocks.listRunners.mockResolvedValue({
      content: [{ id: 'runner-1', status: 'OFFLINE' }],
    })
    mocks.listRuns.mockResolvedValue({ content: [] })
    mocks.listEndpoints.mockResolvedValue({ content: [] })

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('当前阻塞')
    expect(wrapper.text()).toContain('接入在线 Runner')
    expect(wrapper.text()).toContain('新 Run 和外部服务调用都无法真正完成')
    expect(wrapper.get('[aria-label="首次交付向导"]').text()).toContain('跟着 5 步发布第一个 Agent')
    expect(wrapper.get('[aria-label="首次交付向导"]').text()).toContain('2 / 5')
    expect(wrapper.get('[aria-label="交付就绪证据"]').text()).toContain('当前步骤')
    expect(wrapper.find('.summary-grid').exists()).toBe(false)
  })

  it('shows the closed-loop state and recent Run after real delivery exists', async () => {
    mocks.listAgents.mockResolvedValue([
      { agentId: 'ag-1', activeVersionStatus: 'FROZEN' },
    ])
    mocks.listRunners.mockResolvedValue({
      content: [{ id: 'runner-1', status: 'ONLINE' }],
    })
    mocks.listRuns.mockResolvedValue({
      content: [{
        id: 'run-1',
        agentId: 'ag-1',
        status: 'COMPLETED',
        input: 'acceptance run',
        output: 'accepted result',
        createdAt: 1785228532489,
      }],
    })
    mocks.listEndpoints.mockResolvedValue({
      content: [{ id: 'ep-1', name: 'acceptance service', status: 'ACTIVE', endpointType: 'REST_API' }],
    })

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('核心闭环可用')
    expect(wrapper.text()).toContain('acceptance run')
    expect(wrapper.text()).toContain('accepted result')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('acceptance service')
    expect(wrapper.text()).toContain('可调用')
    expect(wrapper.get('[aria-label="首次交付向导"]').text()).toContain('首次交付已完成')
    expect(wrapper.get('[aria-label="首次交付向导"]').text()).toContain('5 / 5')
  })

  it('lets a newcomer open each delivery step without exposing internal IDs', async () => {
    mocks.listAgents.mockResolvedValue([])
    mocks.listRunners.mockResolvedValue({ content: [] })
    mocks.listRuns.mockResolvedValue({ content: [] })
    mocks.listEndpoints.mockResolvedValue({ content: [] })

    const wrapper = createWrapper()
    await flushPromises()

    const guide = wrapper.get('[aria-label="交付就绪证据"]')
    expect(guide.text()).toContain('创建 Agent')
    expect(guide.text()).toContain('固定可运行版本')
    expect(guide.text()).toContain('接入执行节点')
    expect(guide.text()).toContain('验证一次输出')
    expect(guide.text()).toContain('发布给用户')

    await guide.findAll('button')[2].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/workspace/runners',
      query: { returnTo: 'run' },
    })
  })

  it('treats the latest failed Run as the next action even when older delivery exists', async () => {
    mocks.listAgents.mockResolvedValue([
      { agentId: 'ag-1', activeVersionStatus: 'FROZEN' },
    ])
    mocks.listRunners.mockResolvedValue({
      content: [{ id: 'runner-1', status: 'ONLINE' }],
    })
    mocks.listRuns.mockResolvedValue({
      content: [
        { id: 'run-failed', agentId: 'ag-1', status: 'FAILED', input: 'latest run', errorMessage: 'provider unavailable', createdAt: 200 },
        { id: 'run-completed', agentId: 'ag-1', status: 'COMPLETED', input: 'older run', createdAt: 100 },
      ],
    })
    mocks.listEndpoints.mockResolvedValue({
      content: [{ id: 'ep-1', name: 'service', status: 'ACTIVE', endpointType: 'REST_API' }],
    })

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('处理失败 Run')
    expect(wrapper.text()).toContain('provider unavailable')
    expect(wrapper.text()).toContain('最近一次执行')
  })
})
