import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import KnowledgeListV2 from '@/views/revamp/knowledge/KnowledgeListV2.vue'
import { ROUTES } from '@/router/routes'

const pushMock = vi.fn()
const getKnowledgeListMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock
  })
}))

vi.mock('@/api/knowledge', () => ({
  getKnowledgeList: (...args) => getKnowledgeListMock(...args),
  deleteKnowledge: vi.fn()
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn() },
    ElMessageBox: { confirm: vi.fn() }
  }
})

const createWrapper = () => mount(KnowledgeListV2, {
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
        template: '<div class="knowledge-table" @click="$emit(\'row-click\', data[0])">{{ data.length }}<slot /></div>'
      },
      'el-table-column': {
        template: '<div><slot :row="{ id: \'kb-mock\', type: \'UNSTRUCTURED\', name: \'Mock KB\', stats: { documentCount: 1 }, description: \'desc\', updatedAt: null }" /></div>'
      }
    }
  }
})

describe('KnowledgeListV2', () => {
  beforeEach(() => {
    pushMock.mockReset()
    getKnowledgeListMock.mockReset()
  })

  it('loads knowledge list on mounted', async () => {
    getKnowledgeListMock.mockResolvedValue([
      { id: 'kb-1', name: 'Manual', type: 'UNSTRUCTURED', description: 'manual docs' },
      { id: 'kb-2', name: 'Graph', type: 'STRUCTURED', description: 'entity graph' }
    ])
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()
    expect(getKnowledgeListMock).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.knowledge-table').text()).toContain('2')
  })

  it('navigates to create and detail pages', async () => {
    getKnowledgeListMock.mockResolvedValue([
      { id: 'kb-1', name: 'Manual', type: 'UNSTRUCTURED', description: 'manual docs' }
    ])
    const wrapper = createWrapper()
    await Promise.resolve()

    const createBtn = wrapper.findAll('button').find((btn) => btn.text().includes('创建知识库'))
    expect(createBtn).toBeTruthy()
    await createBtn.trigger('click')
    expect(pushMock).toHaveBeenCalledWith(ROUTES.KNOWLEDGE.CREATE)

    await wrapper.find('.knowledge-table').trigger('click')
    expect(pushMock).toHaveBeenCalledWith(ROUTES.KNOWLEDGE.DETAIL.replace(':id', 'kb-1'))
  })
})
