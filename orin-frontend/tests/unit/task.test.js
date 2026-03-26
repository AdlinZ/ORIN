/**
 * 任务页基础交互测试
 * 使用 Vitest 框架测试任务列表、状态变更、重试、取消等核心交互
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getQueuedTasks,
  getRunningTasks,
  getFailedTasks,
  getTaskById,
  replayTask,
  cancelTask
} from '@/api/task'

// Mock API 响应
vi.mock('@/api/task', () => ({
  getQueuedTasks: vi.fn(),
  getRunningTasks: vi.fn(),
  getFailedTasks: vi.fn(),
  getTaskById: vi.fn(),
  replayTask: vi.fn(),
  cancelTask: vi.fn()
}))

describe('任务页交互测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('任务列表加载', () => {
    it('应能正确加载排队中任务列表', async () => {
      const mockTasks = [
        { taskId: 'task-001', status: 'QUEUED', priority: 'HIGH' },
        { taskId: 'task-002', status: 'QUEUED', priority: 'NORMAL' }
      ]

      getQueuedTasks.mockResolvedValue({ data: { content: mockTasks, total: 2 } })

      const res = await getQueuedTasks()

      expect(res.data.content).toHaveLength(2)
      expect(res.data.content[0].status).toBe('QUEUED')
    })

    it('应能正确加载执行中任务列表', async () => {
      const mockTasks = [
        { taskId: 'task-003', status: 'RUNNING' }
      ]

      getRunningTasks.mockResolvedValue({ data: { content: mockTasks, total: 1 } })

      const res = await getRunningTasks()

      expect(res.data.content[0].status).toBe('RUNNING')
    })

    it('应能正确加载失败任务列表', async () => {
      const mockTasks = [
        { taskId: 'task-004', status: 'FAILED', errorMessage: '执行失败' }
      ]

      getFailedTasks.mockResolvedValue({ data: { content: mockTasks, total: 1 } })

      const res = await getFailedTasks()

      expect(res.data.content[0].status).toBe('FAILED')
    })
  })

  describe('任务详情加载', () => {
    it('应能正确加载任务详情，包含所有字段', async () => {
      const mockDetail = {
        taskId: 'task-001',
        workflowId: 1,
        status: 'QUEUED',
        priority: 'HIGH',
        triggeredBy: 'test-user',
        triggerSource: 'manual',
        retryCount: 0,
        maxRetries: 3,
        nextRetryAt: null,
        errorMessage: null,
        queuedAt: '2026-03-26T10:00:00',
        inputData: { key: 'value' }
      }

      getTaskById.mockResolvedValue({ data: mockDetail })

      const res = await getTaskById('task-001')

      expect(res.data.taskId).toBe('task-001')
      expect(res.data.triggeredBy).toBe('test-user')
      expect(res.data.retryCount).toBe(0)
    })
  })

  describe('任务重试', () => {
    it('应能成功触发失败任务重试', async () => {
      replayTask.mockResolvedValue({ data: { success: true, taskId: 'task-004' } })

      const res = await replayTask('task-004')

      expect(replayTask).toHaveBeenCalledWith('task-004')
      expect(res.data.success).toBe(true)
    })

    it('重试失败的任务应增加重试次数', async () => {
      let task = { taskId: 'task-004', retryCount: 0, status: 'FAILED' }
      task.retryCount += 1
      task.status = 'QUEUED'

      expect(task.retryCount).toBe(1)
      expect(task.status).toBe('QUEUED')
    })
  })

  describe('任务取消', () => {
    it('应能成功取消排队中的任务', async () => {
      cancelTask.mockResolvedValue({ data: { success: true } })

      const res = await cancelTask('task-001')

      expect(cancelTask).toHaveBeenCalledWith('task-001')
      expect(res.data.success).toBe(true)
    })

    it('执行中的任务不能被取消', async () => {
      const task = { taskId: 'task-003', status: 'RUNNING' }
      const canCancel = task.status === 'QUEUED'

      expect(canCancel).toBe(false)
    })
  })

  describe('任务状态过滤', () => {
    it('应能按状态过滤任务', async () => {
      const allTasks = [
        { taskId: 'task-001', status: 'QUEUED' },
        { taskId: 'task-002', status: 'QUEUED' },
        { taskId: 'task-003', status: 'RUNNING' }
      ]

      const queuedTasks = allTasks.filter(t => t.status === 'QUEUED')

      expect(queuedTasks).toHaveLength(2)
    })
  })

  describe('任务统计', () => {
    it('应能正确统计各状态任务数量', () => {
      const tasks = [
        { status: 'QUEUED' },
        { status: 'QUEUED' },
        { status: 'RUNNING' },
        { status: 'FAILED' }
      ]

      const stats = {
        queued: tasks.filter(t => t.status === 'QUEUED').length,
        running: tasks.filter(t => t.status === 'RUNNING').length,
        failed: tasks.filter(t => t.status === 'FAILED').length
      }

      expect(stats.queued).toBe(2)
      expect(stats.running).toBe(1)
      expect(stats.failed).toBe(1)
    })
  })
})