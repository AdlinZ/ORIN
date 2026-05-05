import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AuditCenterV2 from '@/views/revamp/system/AuditCenterV2.vue'

const getMock = vi.fn()
const postMock = vi.fn()
const putMock = vi.fn()
const deleteMock = vi.fn()
const successMock = vi.fn()
const warningMock = vi.fn()
const errorMock = vi.fn()
const confirmMock = vi.fn()

vi.mock('@/utils/request', () => ({
  default: {
    get: (...args) => getMock(...args),
    post: (...args) => postMock(...args),
    put: (...args) => putMock(...args),
    delete: (...args) => deleteMock(...args)
  }
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: (...args) => successMock(...args),
      warning: (...args) => warningMock(...args),
      error: (...args) => errorMock(...args)
    },
    ElMessageBox: { confirm: (...args) => confirmMock(...args) }
  }
})

const createWrapper = () => mount(AuditCenterV2, {
  global: {
    stubs: {
      OrinPageShell: { template: '<div><slot /><slot name="actions" /></div>' },
      OrinAsyncState: { template: '<div><slot /></div>' },
      OrinAuditTable: {
        props: ['rows'],
        template: '<div class="audit-rows">{{ rows.length }}</div>'
      },
      'el-tabs': { template: '<div><slot /></div>' },
      'el-tab-pane': { template: '<div><slot /></div>' },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-card': { template: '<div><slot name="header" /><slot /></div>' },
      'el-form': { template: '<form><slot /></form>' },
      'el-form-item': { template: '<div><slot /></div>' },
      'el-switch': { template: '<input type="checkbox" />' },
      'el-select': { template: '<select><slot /></select>' },
      'el-option': { template: '<option><slot /></option>' },
      'el-input-number': { template: '<input type="number" />' },
      'el-table': { template: '<div><slot /></div>' },
      'el-table-column': { template: '<div><slot :row="{ name: \'root\', level: \'INFO\', newLevel: \'INFO\' }" /></div>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' }
    }
  }
})

describe('AuditCenterV2', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
    deleteMock.mockReset()
    successMock.mockReset()
    warningMock.mockReset()
    errorMock.mockReset()
    confirmMock.mockReset()

    getMock.mockImplementation((url) => {
      if (url === '/audit/logs') {
        return Promise.resolve([
          { createdAt: '2026-04-09T00:00:00Z', userName: 'admin', success: true }
        ])
      }
      if (url === '/system/log-config/stats') {
        return Promise.resolve({ totalCount: 12, estimatedSizeMb: 3 })
      }
      if (url === '/system/log-config') {
        return Promise.resolve([
          { configKey: 'log.audit.enabled', configValue: 'true' },
          { configKey: 'log.level', configValue: 'ALL' },
          { configKey: 'log.retention.days', configValue: '30' }
        ])
      }
      if (url === '/system/log-config/loggers') {
        return Promise.resolve({ root: 'INFO' })
      }
      return Promise.resolve({})
    })
    putMock.mockResolvedValue({})
    postMock.mockResolvedValue({})
    deleteMock.mockResolvedValue({})
    confirmMock.mockResolvedValue('confirm')
  })

  it('loads audit rows on mounted', async () => {
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()
    expect(getMock).toHaveBeenCalledWith('/audit/logs', { params: { page: 0, size: 20 } })
    expect(wrapper.find('.audit-rows').text()).toBe('1')
  })

  it('saves audit config with three put requests', async () => {
    const wrapper = createWrapper()
    await Promise.resolve()
    const saveBtn = wrapper.findAll('button').find((btn) => btn.text().includes('保存配置'))
    expect(saveBtn).toBeTruthy()
    await saveBtn.trigger('click')

    expect(putMock).toHaveBeenCalled()
    expect(putMock).toHaveBeenCalledWith('/system/log-config/log.audit.enabled', { value: 'true' })
    expect(putMock).toHaveBeenCalledWith('/system/log-config/log.level', { value: 'ALL' })
    expect(putMock).toHaveBeenCalledWith('/system/log-config/log.retention.days', { value: '30' })
    expect(successMock).toHaveBeenCalledWith('审计配置已保存')
  })

  it('submits cleanup task and refreshes stats/logs', async () => {
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    const cleanupBtn = wrapper.findAll('button').find((btn) => btn.text().includes('清理历史日志'))
    expect(cleanupBtn).toBeTruthy()
    await cleanupBtn.trigger('click')
    await Promise.resolve()

    expect(postMock).toHaveBeenCalledWith('/system/log-config/cleanup', null, { params: { days: 30 } })
    expect(successMock).toHaveBeenCalledWith('日志清理任务已提交')
    expect(getMock).toHaveBeenCalledWith('/system/log-config/stats')
    expect(getMock).toHaveBeenCalledWith('/audit/logs', { params: { page: 0, size: 20 } })
  })

  it('does not reset all loggers when confirmation is canceled', async () => {
    confirmMock.mockRejectedValue('cancel')
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    const resetAllBtn = wrapper.findAll('button').find((btn) => btn.text().includes('全部重置'))
    expect(resetAllBtn).toBeTruthy()
    await resetAllBtn.trigger('click')
    await Promise.resolve()

    expect(confirmMock).toHaveBeenCalled()
    expect(postMock).not.toHaveBeenCalledWith('/system/log-config/loggers/reset-all')
    expect(errorMock).not.toHaveBeenCalledWith('重置失败')
  })

  it('resets all loggers after confirmation', async () => {
    confirmMock.mockResolvedValue('confirm')
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    const resetAllBtn = wrapper.findAll('button').find((btn) => btn.text().includes('全部重置'))
    expect(resetAllBtn).toBeTruthy()
    await resetAllBtn.trigger('click')
    await Promise.resolve()

    expect(confirmMock).toHaveBeenCalled()
    expect(postMock).toHaveBeenCalledWith('/system/log-config/loggers/reset-all')
    expect(successMock).toHaveBeenCalledWith('全部 Logger 已重置')
    expect(getMock).toHaveBeenCalledWith('/system/log-config/loggers')
  })

  it('resets single logger and refreshes logger list', async () => {
    const wrapper = createWrapper()
    await Promise.resolve()
    await Promise.resolve()

    const resetBtn = wrapper.findAll('button').find((btn) => btn.text().trim() === '重置')
    expect(resetBtn).toBeTruthy()
    await resetBtn.trigger('click')
    await Promise.resolve()

    expect(deleteMock).toHaveBeenCalledWith('/system/log-config/loggers/root')
    expect(successMock).toHaveBeenCalledWith('Logger root 已重置')
    expect(getMock).toHaveBeenCalledWith('/system/log-config/loggers')
  })
})
