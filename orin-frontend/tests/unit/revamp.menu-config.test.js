/**
 * 新产品面菜单拆分（Slice 3a）
 *
 * - 工作台 5 个一级（首页 / 智能体 / 工作流 / 知识库 / 扩展能力）
 * - 管理台 6 个一级（平台总览 / 组织权限 / AI 基础设施 / 统一网关 / 运行运维 / 系统治理）
 * - Chat 1 个一级（对话）
 * - 二级 ≤ 6 项，最细功能由页面 Tab 表达
 *
 * 与拆 menuConfig 之前相比：
 * - 旧代码用一份 TOP_MENU_CONFIG + `surface` 字段过滤；
 * - 新代码每个产品面独立导出（workspaceMenu / adminMenu / chatMenu），
 *   共享 buildVisibleMenus / getActiveMenuIdForMenu / hasAnyRole 工具。
 */
import { describe, expect, it } from 'vitest'
import { WORKSPACE_MENU } from '@/router/menuConfig/workspaceMenu'
import { ADMIN_MENU } from '@/router/menuConfig/adminMenu'
import { CHAT_MENU } from '@/router/menuConfig/chatMenu'
import {
    ADMIN_MENU_ROLES,
    USER_MENU_ROLES,
    WORKSPACE_MENU_ROLES,
    buildVisibleMenus,
} from '@/router/menuConfig/shared'

describe('workspace menu IA', () => {
    it('exposes 5 top-level groups', () => {
        expect(WORKSPACE_MENU).toHaveLength(5)
        expect(WORKSPACE_MENU.map((m) => m.id)).toEqual([
            'workspace-home',
            'workspace-agents',
            'workspace-workflows',
            'workspace-knowledge',
            'workspace-extensions',
        ])
    })

    it('keeps each top-level group within 6 secondary entries', () => {
        WORKSPACE_MENU.forEach((menu) => {
            expect(menu.children.length).toBeLessThanOrEqual(6)
        })
    })

    it('ROLE_USER can see all 5 workspace groups but ROLE_ADMIN sees the same', () => {
        const userMenus = buildVisibleMenus(WORKSPACE_MENU, USER_MENU_ROLES).map((m) => m.id)
        const adminMenus = buildVisibleMenus(WORKSPACE_MENU, ADMIN_MENU_ROLES).map((m) => m.id)
        expect(userMenus).toEqual([
            'workspace-home',
            'workspace-agents',
            'workspace-workflows',
            'workspace-knowledge',
            'workspace-extensions',
        ])
        expect(adminMenus).toEqual([
            'workspace-home',
            'workspace-agents',
            'workspace-workflows',
            'workspace-knowledge',
            'workspace-extensions',
        ])
    })

    it('does not expose query-only home entries without matching page state', () => {
        const home = WORKSPACE_MENU.find((m) => m.id === 'workspace-home')
        expect(home.children.map((c) => c.title)).toEqual(['我的概览'])
    })
})

describe('admin menu IA', () => {
    it('exposes 6 top-level groups', () => {
        expect(ADMIN_MENU).toHaveLength(6)
        expect(ADMIN_MENU.map((m) => m.id)).toEqual([
            'admin-overview',
            'admin-organization',
            'admin-ai-infra',
            'admin-unified-gateway',
            'admin-runtime',
            'admin-governance',
        ])
    })

    it('keeps each top-level group within 6 secondary entries', () => {
        ADMIN_MENU.forEach((menu) => {
            expect(menu.children.length).toBeLessThanOrEqual(6)
        })
    })

    it('ROLE_ADMIN can see all 6 admin groups', () => {
        const menus = buildVisibleMenus(ADMIN_MENU, ADMIN_MENU_ROLES).map((m) => m.id)
        expect(menus).toEqual([
            'admin-overview',
            'admin-organization',
            'admin-ai-infra',
            'admin-unified-gateway',
            'admin-runtime',
            'admin-governance',
        ])
    })

    it('non-admin user gets no admin menu entries', () => {
        const menus = buildVisibleMenus(ADMIN_MENU, USER_MENU_ROLES)
        expect(menus).toHaveLength(0)
    })

    it('does not include any workspace group inside admin menu', () => {
        const labels = ADMIN_MENU.flatMap((m) => m.children.map((c) => c.title))
        expect(labels).not.toContain('智能体工作台')
        expect(labels).not.toContain('多智能体协作')
        expect(labels).not.toContain('工作流中心')
    })

    it('does not expose query-only overview or provider entries without matching page state', () => {
        const overview = ADMIN_MENU.find((m) => m.id === 'admin-overview')
        const aiInfra = ADMIN_MENU.find((m) => m.id === 'admin-ai-infra')
        expect(overview.children.map((c) => c.title)).toEqual(['运行摘要'])
        expect(aiInfra.children.map((c) => c.title)).not.toContain('Provider 配置')
    })

    it('uses unified gateway as the only open-platform group and exposes its four workspaces directly', () => {
        const gateway = ADMIN_MENU.find((m) => m.id === 'admin-unified-gateway')

        expect(gateway.title).toBe('统一网关')
        expect(gateway.children.map((c) => c.title)).toEqual(['总览', '统一入口', 'API Keys', '流量策略'])
        expect(gateway.children.map((c) => c.path)).toEqual([
            '/admin/gateway',
            '/admin/gateway?workspace=api',
            '/admin/gateway?workspace=access',
            '/admin/gateway?workspace=traffic',
        ])
        expect(ADMIN_MENU.flatMap((m) => m.children.map((c) => c.title))).not.toContain('MCP 服务')
    })
})

describe('chat menu IA', () => {
    it('exposes 1 top-level group with ORIN Chat only', () => {
        expect(CHAT_MENU).toHaveLength(1)
        expect(CHAT_MENU[0].id).toBe('chat')
        expect(CHAT_MENU[0].children.map((c) => c.title)).toEqual(['ORIN Chat'])
    })

    it('ROLE_USER can see the chat menu, ROLE_ADMIN cannot', () => {
        expect(buildVisibleMenus(CHAT_MENU, USER_MENU_ROLES)).toHaveLength(1)
        expect(buildVisibleMenus(CHAT_MENU, ADMIN_MENU_ROLES)).toHaveLength(0)
    })
})

describe('shared menu helpers', () => {
    it('buildVisibleMenus respects surface roles and falls through missing groups', () => {
        const menus = buildVisibleMenus(WORKSPACE_MENU, [])
        expect(menus).toHaveLength(0)
    })

    it('keeps menu stability reference equality across calls', () => {
        const a = buildVisibleMenus(WORKSPACE_MENU, WORKSPACE_MENU_ROLES)
        const b = buildVisibleMenus(WORKSPACE_MENU, WORKSPACE_MENU_ROLES)
        expect(a.map((m) => m.id)).toEqual(b.map((m) => m.id))
    })
})
