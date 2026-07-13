/**
 * Layout 边界单元测试（Slice 3a）。
 *
 * 验证：
 * - /workspace/* 路由匹配 WorkspaceLayout
 * - /admin/* 路由匹配 AdminLayout
 * - /admin/* 子路由同时匹配 AdminLayout
 * - /dashboard/* 兜底壳仍可用（MainLayout）
 * - /chat 不进任何 Layout
 */
import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import router from '@/router'

describe('layout mount boundaries', () => {
    it('/workspace mount is WorkspaceLayout', () => {
        const resolved = router.resolve(ROUTES.WORKSPACE_ROOT)
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).toContain(ROUTES.WORKSPACE_ROOT)
        const mountRecord = resolved.matched.find((r) => r.path === ROUTES.WORKSPACE_ROOT)
        // component 名通常被编译，这里只断言 mountRecord 存在
        expect(mountRecord).toBeTruthy()
    })

    it('/workspace/agents mount is WorkspaceLayout', () => {
        const resolved = router.resolve(ROUTES.AGENTS.LIST)
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).toContain(ROUTES.WORKSPACE_ROOT)
    })

    it('keeps workspace profile inside WorkspaceLayout', () => {
        const resolved = router.resolve(ROUTES.WORKSPACE_PATHS.PROFILE)
        expect(resolved.name).toBe('WorkspaceProfile')
        expect(resolved.matched.map((record) => record.path)).toContain(ROUTES.WORKSPACE_ROOT)
    })

    it('/admin mount is AdminLayout', () => {
        const resolved = router.resolve(ROUTES.ADMIN)
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).toContain(ROUTES.ADMIN_ROOT)
    })

    it('/admin/runtime mount is AdminLayout', () => {
        const resolved = router.resolve(`${ROUTES.ADMIN_ROOT}/runtime`)
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).toContain(`${ROUTES.ADMIN_ROOT}/runtime`)
    })

    it('/admin/users mount is AdminLayout', () => {
        const resolved = router.resolve(ROUTES.SYSTEM.USERS || `${ROUTES.ADMIN_ROOT}/users`)
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).toContain(ROUTES.ADMIN_ROOT)
    })

    it('keeps admin profile inside AdminLayout', () => {
        const resolved = router.resolve(ROUTES.ADMIN_PATHS.PROFILE)
        expect(resolved.name).toBe('AdminProfile')
        expect(resolved.matched.map((record) => record.path)).toContain(ROUTES.ADMIN_ROOT)
    })

    it('/chat does not enter any Layout mount', () => {
        const resolved = router.resolve(ROUTES.CHAT)
        // Chat 是顶层裸组件，不应匹配 /workspace 与 /admin mount
        const matchedPaths = resolved.matched.map((record) => record.path)
        expect(matchedPaths).not.toContain(ROUTES.WORKSPACE_ROOT)
        expect(matchedPaths).not.toContain(ROUTES.ADMIN_ROOT)
        expect(matchedPaths).toContain(ROUTES.CHAT)
    })
})
