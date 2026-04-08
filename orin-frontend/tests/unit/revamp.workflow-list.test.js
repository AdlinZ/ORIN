import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkflowListV2 from '@/views/revamp/workflow/WorkflowListV2.vue'
import { ROUTES } from '@/router/routes'

const pushMock = vi.fn()
const getWorkflowsMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock
  })
}))

vi.mock('@/api/workflow', () => ({
  getWorkflows: (...args) => getWorkflowsMock(...args),
  exportWorkflow: vi.fn(),
  deleteWorkflow: vi.fn()
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn() }
  }
})

const createWrapper = () => mount(WorkflowListV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /><slot name="filters" /></div>' },
      OrinFilterBar: { template: '<div><slot /></div>' },
      OrinAsyncState: { template: '<div><slot /></div>' },
      StatCard: { template: '<div class="stat-card" />' },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div><slot /></div>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-input': { template: '<input />' },
      'el-select': { template: '<select><slot /></select>' },
      'el-option': { template: '<option><slot /></option>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-table': {
        props: ['data'],
        template: '<div class="workflow-table">{{ data.length }}<slot /></div>'
      },
      'el-table-column': {
        template: '<div><slot :row="{ id: \'wf-mock\', status: \'DRAFT\', workflowName: \'Mock\', description: \'Desc\', updatedAt: null }" /></div>'
      }
    }
  }
})

describe('WorkflowListV2', () => {
  beforeEach(() => {
    pushMock.mockReset()
    getWorkflowsMock.mockReset()
  })

  it('loads workflows on mounted', async () => {
    getWorkflowsMock.mockResolvedValue([
      { id: 'wf-1', name: 'Flow 1', status: 'PUBLISHED' },
      { id: 'wf-2', name: 'Flow 2', status: 'DRAFT' }
    ])
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()
    expect(getWorkflowsMock).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.workflow-table').text()).toContain('2')
  })

  it('navigates to create and visual editor entry', async () => {
    getWorkflowsMock.mockResolvedValue([])
    const wrapper = createWrapper()
    await Promise.resolve()
    const createBtn = wrapper.findAll('button').find((btn) => btn.text().includes('新建工作流'))
    const visualBtn = wrapper.findAll('button').find((btn) => btn.text().includes('可视化编辑器'))
    expect(createBtn).toBeTruthy()
    expect(visualBtn).toBeTruthy()
    await createBtn.trigger('click')
    await visualBtn.trigger('click')
    expect(pushMock).toHaveBeenCalledWith(ROUTES.AGENTS.WORKFLOW_CREATE)
    expect(pushMock).toHaveBeenCalledWith(ROUTES.AGENTS.WORKFLOW_VISUAL)
  })
})
