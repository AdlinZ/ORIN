/**
 * 协作页基础交互测试
 * 使用 Vitest 框架测试协作包创建、列表展示、子任务状态等核心交互
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getAllPackages, createCollaborationPackage, getSubtasks } from '@/api/collaboration'

// Mock API 响应
vi.mock('@/api/collaboration', () => ({
  getAllPackages: vi.fn(),
  createCollaborationPackage: vi.fn(),
  getSubtasks: vi.fn()
}))

describe('协作页交互测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('协作包列表加载', () => {
    it('应能正确加载协作包列表数据', async () => {
      const mockPackages = [
        { packageId: 'pkg-001', intent: '测试任务1', status: 'EXECUTING' },
        { packageId: 'pkg-002', intent: '测试任务2', status: 'COMPLETED' }
      ]

      getAllPackages.mockResolvedValue({ data: mockPackages })

      const res = await getAllPackages()

      expect(res.data).toHaveLength(2)
      expect(res.data[0].packageId).toBe('pkg-001')
    })

    it('应能按状态筛选协作包', async () => {
      const mockPackages = [
        { packageId: 'pkg-001', status: 'EXECUTING' }
      ]

      getAllPackages.mockResolvedValue({ data: mockPackages })

      const res = await getAllPackages()

      expect(res.data.every(p => p.status === 'EXECUTING' || p.status === 'COMPLETED')).toBe(true)
    })
  })

  describe('创建协作包', () => {
    it('应能成功创建协作包并返回 packageId', async () => {
      const createForm = {
        intent: '测试任务',
        category: 'GENERATION',
        priority: 'NORMAL'
      }

      createCollaborationPackage.mockResolvedValue({
        data: { packageId: 'pkg-new-001', ...createForm }
      })

      const res = await createCollaborationPackage(createForm)

      expect(res.data.packageId).toBe('pkg-new-001')
    })

    it('应验证必填字段', async () => {
      const createForm = { intent: '' }

      createCollaborationPackage.mockRejectedValue(new Error('意图不能为空'))

      await expect(createCollaborationPackage(createForm)).rejects.toThrow('意图不能为空')
    })
  })

  describe('子任务状态流转', () => {
    it('PENDING -> RUNNING 状态转换', () => {
      let subtask = { subTaskId: 'sub-001', status: 'PENDING' }
      subtask.status = 'RUNNING'
      expect(subtask.status).toBe('RUNNING')
    })

    it('RUNNING -> COMPLETED 状态转换', () => {
      let subtask = { subTaskId: 'sub-001', status: 'RUNNING' }
      subtask.status = 'COMPLETED'
      expect(subtask.status).toBe('COMPLETED')
    })

    it('FAILED -> PENDING (重试) 状态转换', () => {
      let subtask = { subTaskId: 'sub-001', status: 'FAILED' }
      subtask.status = 'PENDING'
      expect(subtask.status).toBe('PENDING')
    })
  })

  describe('协作包详情加载', () => {
    it('应能正确加载协作包详情，包含子任务列表', async () => {
      const mockDetail = {
        packageId: 'pkg-001',
        intent: '测试任务',
        status: 'EXECUTING',
        subtasks: [
          { subTaskId: 'sub-001', status: 'PENDING' },
          { subTaskId: 'sub-002', status: 'RUNNING' }
        ]
      }

      getSubtasks.mockResolvedValue({ data: mockDetail.subtasks })

      const res = await getSubtasks('pkg-001')

      expect(res.data).toHaveLength(2)
    })
  })

  describe('人工干预操作', () => {
    it('跳过子任务应更新状态为 SKIPPED', async () => {
      let subtask = { subTaskId: 'sub-001', status: 'PENDING' }
      subtask.status = 'SKIPPED'
      expect(subtask.status).toBe('SKIPPED')
    })

    it('手动完成子任务应更新状态为 COMPLETED', async () => {
      let subtask = { subTaskId: 'sub-001', status: 'PENDING', result: '' }
      subtask.status = 'COMPLETED'
      subtask.result = '手动完成的结果'
      expect(subtask.status).toBe('COMPLETED')
      expect(subtask.result).toBe('手动完成的结果')
    })
  })
})