import { describe, expect, it } from 'vitest'
import taskQueueSource from '@/views/Monitor/TaskQueue.vue?raw'

describe('TaskQueue workbench surface', () => {
  it('uses one task table surface instead of nesting table cards', () => {
    expect(taskQueueSource).toContain('<OrinPageShell')
    expect(taskQueueSource).toContain('class="task-toolbar"')
    expect(taskQueueSource).toContain('<OrinDataTable compact>')
    expect(taskQueueSource).not.toContain('task-list-card')
    expect(taskQueueSource).not.toContain('priority-card')
    expect(taskQueueSource).not.toContain('<el-card')
  })

  it('keeps status selection, pagination, replay and cancellation in the canonical surface', () => {
    expect(taskQueueSource).toContain('v-model="activeTab"')
    expect(taskQueueSource).toContain('page: currentPage.value - 1')
    expect(taskQueueSource).toContain('@size-change="handleSizeChange"')
    expect(taskQueueSource).toContain('@current-change="handlePageChange"')
    expect(taskQueueSource).toContain('@click="handleReplay(row)"')
    expect(taskQueueSource).toContain('@click="handleCancel(row)"')
  })

  it('keeps task detail out of the primary table and adapts the dialog to narrow screens', () => {
    expect(taskQueueSource).toContain('title="任务详情"')
    expect(taskQueueSource).toContain('width="min(720px, calc(100vw - 32px))"')
    expect(taskQueueSource).toContain('formatJson(currentTask.inputData)')
  })
})
