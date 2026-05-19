import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CollaborationDashboardV2 from '@/views/revamp/collaboration/CollaborationDashboardV2.vue'

const getCollaborationStatsMock = vi.fn()
const getAllPackagesMock = vi.fn()
const getEventHistoryMock = vi.fn()
const getSubtasksMock = vi.fn()
const createCollaborationPackageMock = vi.fn()
const manualCompletePackageMock = vi.fn()
const manualCompleteSubtaskMock = vi.fn()
const retrySubtaskMock = vi.fn()
const skipSubtaskMock = vi.fn()
const getRuntimeStatusMock = vi.fn()
const getDiagnosticsMock = vi.fn()
const pauseCollaborationMock = vi.fn()
const resumeCollaborationMock = vi.fn()
const cancelCollaborationMock = vi.fn()
const warningMock = vi.fn()
const successMock = vi.fn()
const confirmMock = vi.fn()
const promptMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}))

vi.mock('@/api/collaboration', () => ({
  getCollaborationStats: (...args) => getCollaborationStatsMock(...args),
  getAllPackages: (...args) => getAllPackagesMock(...args),
  getEventHistory: (...args) => getEventHistoryMock(...args),
  getSubtasks: (...args) => getSubtasksMock(...args),
  manualCompletePackage: (...args) => manualCompletePackageMock(...args),
  manualCompleteSubtask: (...args) => manualCompleteSubtaskMock(...args),
  retrySubtask: (...args) => retrySubtaskMock(...args),
  skipSubtask: (...args) => skipSubtaskMock(...args),
  createCollaborationPackage: (...args) => createCollaborationPackageMock(...args)
}))

vi.mock('@/api/collaborationRuntime', () => ({
  getRuntimeStatus: (...args) => getRuntimeStatusMock(...args),
  getDiagnostics: (...args) => getDiagnosticsMock(...args),
  pauseCollaboration: (...args) => pauseCollaborationMock(...args),
  resumeCollaboration: (...args) => resumeCollaborationMock(...args),
  cancelCollaboration: (...args) => cancelCollaborationMock(...args)
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      warning: (...args) => warningMock(...args),
      success: (...args) => successMock(...args)
    },
    ElMessageBox: {
      confirm: (...args) => confirmMock(...args),
      prompt: (...args) => promptMock(...args)
    }
  }
})

const createWrapper = () => mount(CollaborationDashboardV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /><slot name="filters" /></div>' },
      OrinFilterBar: { template: '<div><slot /></div>' },
      OrinAsyncState: { template: '<div><slot /></div>' },
      OrinTaskTimeline: { template: '<div class="timeline-stub" />' },
      StatCard: { template: '<div class="stat-card" />' },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div><slot name="header" /><slot /></div>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-input': { template: '<input />' },
      'el-select': { template: '<select><slot /></select>' },
      'el-option': { template: '<option><slot /></option>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
      'el-drawer': { template: '<div><slot /></div>' },
      'el-descriptions': { template: '<div><slot /></div>' },
      'el-descriptions-item': { template: '<div><slot /></div>' },
      'el-form': { template: '<form><slot /></form>' },
      'el-form-item': { template: '<div><slot /></div>' },
      'el-table': {
        props: ['data'],
        template: '<div class="packages-table">{{ data.length }}<slot /></div>'
      },
      'el-table-column': {
        template: '<div><slot :row="{ packageId: \'pkg-1\', status: \'EXECUTING\', createdAt: null, priority: \'NORMAL\' }" /></div>'
      }
    }
  }
})

describe('CollaborationDashboardV2', () => {
  beforeEach(() => {
    getCollaborationStatsMock.mockReset()
    getAllPackagesMock.mockReset()
    getEventHistoryMock.mockReset()
    getSubtasksMock.mockReset()
    createCollaborationPackageMock.mockReset()
    manualCompletePackageMock.mockReset()
    manualCompleteSubtaskMock.mockReset()
    retrySubtaskMock.mockReset()
    skipSubtaskMock.mockReset()
    getRuntimeStatusMock.mockReset()
    getDiagnosticsMock.mockReset()
    pauseCollaborationMock.mockReset()
    resumeCollaborationMock.mockReset()
    cancelCollaborationMock.mockReset()
    warningMock.mockReset()
    successMock.mockReset()
    confirmMock.mockReset()
    promptMock.mockReset()
    confirmMock.mockResolvedValue()
    promptMock.mockResolvedValue({ value: 'done manually' })
    getSubtasksMock.mockResolvedValue([])
    getRuntimeStatusMock.mockResolvedValue({})
    getDiagnosticsMock.mockResolvedValue({})
  })

  it('loads stats and packages on mounted', async () => {
    getCollaborationStatsMock.mockResolvedValue({ totalTasks: 3, completedTasks: 2, executingTasks: 1 })
    getAllPackagesMock.mockResolvedValue([
      { packageId: 'pkg-1', intent: 'intent A', status: 'EXECUTING' },
      { packageId: 'pkg-2', intent: 'intent B', status: 'COMPLETED' }
    ])
    getEventHistoryMock.mockResolvedValue([])

    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    expect(getCollaborationStatsMock).toHaveBeenCalledTimes(1)
    expect(getAllPackagesMock).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.packages-table').text()).toContain('2')
  })

  it('shows validation warning when creating package without intent', async () => {
    getCollaborationStatsMock.mockResolvedValue({})
    getAllPackagesMock.mockResolvedValue([])
    getEventHistoryMock.mockResolvedValue([])

    const wrapper = createWrapper()
    await Promise.resolve()

    const createBtn = wrapper.findAll('button').find((btn) => btn.text().trim() === '创建')
    expect(createBtn).toBeTruthy()
    await createBtn.trigger('click')
    expect(warningMock).toHaveBeenCalledWith('请先填写任务意图')
    expect(createCollaborationPackageMock).not.toHaveBeenCalled()
  })

  it('loads event history from timeline action', async () => {
    getCollaborationStatsMock.mockResolvedValue({})
    getAllPackagesMock.mockResolvedValue([{ packageId: 'pkg-1', intent: 'intent A', status: 'EXECUTING' }])
    getEventHistoryMock.mockResolvedValue([{ eventType: 'DISPATCHED', timestamp: '2026-04-09T01:00:00Z' }])

    const wrapper = createWrapper()
    await Promise.resolve()
    const timelineBtn = wrapper.findAll('button').find((btn) => btn.text().includes('事件流'))
    expect(timelineBtn).toBeTruthy()
    await timelineBtn.trigger('click')
    expect(getEventHistoryMock).toHaveBeenCalledWith('pkg-1')
  })

  it('pauses an executing package from the detail drawer and refreshes runtime state', async () => {
    getCollaborationStatsMock.mockResolvedValue({})
    getAllPackagesMock.mockResolvedValue([{ packageId: 'pkg-1', intent: 'intent A', status: 'EXECUTING' }])
    getEventHistoryMock.mockResolvedValue([])
    pauseCollaborationMock.mockResolvedValue({})

    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    const detailBtn = wrapper.findAll('button').find((btn) => btn.text().includes('详情'))
    expect(detailBtn).toBeTruthy()
    await detailBtn.trigger('click')
    await Promise.resolve()

    const pauseBtn = wrapper.findAll('button').find((btn) => btn.text().trim() === '暂停')
    expect(pauseBtn).toBeTruthy()
    await pauseBtn.trigger('click')
    await Promise.resolve()

    expect(confirmMock).toHaveBeenCalledWith('确认暂停该协作任务包？', '暂停协作包', { type: 'warning' })
    expect(pauseCollaborationMock).toHaveBeenCalledWith('pkg-1')
    expect(successMock).toHaveBeenCalledWith('协作包已暂停')
  })
})
