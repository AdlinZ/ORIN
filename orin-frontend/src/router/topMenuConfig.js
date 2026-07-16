/**
 * ORIN 顶层菜单兼容层。
 *
 * 旧的 TOP_MENU_CONFIG 把 chat / workspace / admin 三产品面放在同一份配置里，
 * 仅靠 surface 字段过滤。本文件改造为「按产品面独立配置 + 顶层菜单聚合」：
 *
 * - ADMIN_MENU / WORKSPACE_MENU / CHAT_MENU 各自在 router/menuConfig/ 下声明。
 * - 顶层 TOP_MENU_CONFIG = 三者聚合，仍然带 surface 字段以兼容旧测试 / 调用方。
 * - getVisibleMenus(roles, surface) / getActiveMenuId / getDashboardGuardRedirect
 *   行为完全保持，新组件可选用 menuConfig 直接消费专属菜单。
 *
 * 历史 /dashboard/* 兼容路径识别仍由 getProductSurfaceByPath 提供，避免老链接白屏。
 */
import { ROUTES } from './routes'
import { ADMIN_MENU } from './menuConfig/adminMenu'
import { WORKSPACE_MENU } from './menuConfig/workspaceMenu'
import { CHAT_MENU } from './menuConfig/chatMenu'
import {
    ADMIN_MENU_ROLES,
    WORKSPACE_MENU_ROLES,
    USER_MENU_ROLES,
    buildVisibleMenus,
    getActiveMenuIdForMenu,
    hasAnyRole,
    isAdminLike,
} from './menuConfig/shared'

export {
    ADMIN_MENU_ROLES,
    USER_MENU_ROLES,
    WORKSPACE_MENU_ROLES,
}

export const MONITOR_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const ORGANIZATION_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const SYSTEM_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const BUILDER_MENU_ROLES = [...ADMIN_MENU_ROLES]

export const PRODUCT_SURFACES = Object.freeze({
    CHAT: 'chat',
    WORKSPACE: 'workspace',
    ADMIN: 'admin',
})

/**
 * 历史 PRODUCT_SURFACE_CONFIG：保留字段以避免破坏现有调用方
 * （Login.vue / Home.vue / Register.vue 等）。
 * 注意：升级后不再用于运行时切换产品面，真正的产品面切换由 Layout 决定。
 */
export const PRODUCT_SURFACE_CONFIG = Object.freeze({
    [PRODUCT_SURFACES.CHAT]: {
        id: PRODUCT_SURFACES.CHAT,
        title: 'ORIN Chat',
        shortTitle: '对话',
        path: ROUTES.CHAT,
        icon: 'ChatRound',
        roles: USER_MENU_ROLES,
    },
    [PRODUCT_SURFACES.WORKSPACE]: {
        id: PRODUCT_SURFACES.WORKSPACE,
        title: 'ORIN 工作台',
        shortTitle: '工作台',
        path: ROUTES.WORKSPACE_ROOT,
        icon: 'Grid',
        roles: WORKSPACE_MENU_ROLES,
    },
    [PRODUCT_SURFACES.ADMIN]: {
        id: PRODUCT_SURFACES.ADMIN,
        title: 'ORIN 管理台',
        shortTitle: '管理台',
        path: ROUTES.ADMIN_ROOT,
        icon: 'Setting',
        roles: ADMIN_MENU_ROLES,
    },
})

/**
 * 顶层聚合菜单（admin 控制台 / 运行观测 / 应用构建 / 资源知识 / 对话）
 * 用于兼容旧测试与未拆分前的组件，保留 legacy id（control / runtime / applications / resources / chat）
 * 以便 revamp.top-menu-config.test.js 仍可断言。
 */
function buildLegacyTopMenuConfig() {
    // admin 历史一级分组：以旧 ID（control / runtime）暴露
    const adminControl = {
        id: 'control',
        title: '平台控制',
        icon: 'Setting',
        color: '#64748b',
        path: ROUTES.ADMIN_PATHS.ROOT,
        surface: PRODUCT_SURFACES.ADMIN,
        roles: [...ADMIN_MENU_ROLES],
        children: flattenAdminSections(['admin-organization', 'admin-ai-infra', 'admin-unified-gateway', 'admin-governance']),
    }

    const adminRuntime = {
        id: 'runtime',
        title: '运行观测',
        icon: 'Monitor',
        color: '#475569',
        path: ROUTES.ADMIN_PATHS.RUNTIME,
        surface: PRODUCT_SURFACES.ADMIN,
        roles: [...ADMIN_MENU_ROLES],
        children: ADMIN_MENU.find((m) => m.id === 'admin-runtime')?.children || [],
    }

    const workspaceApps = {
        id: 'applications',
        title: '应用构建',
        icon: 'Robot',
        color: '#0f766e',
        path: ROUTES.WORKSPACE_PATHS.ROOT,
        surface: PRODUCT_SURFACES.WORKSPACE,
        roles: [...WORKSPACE_MENU_ROLES],
        children: flattenWorkspaceSections(['workspace-home', 'workspace-agents', 'workspace-workflows', 'workspace-extensions']),
    }

    const workspaceResources = {
        id: 'resources',
        title: '资源知识',
        icon: 'Reading',
        color: '#334155',
        path: ROUTES.WORKSPACE_PATHS.KNOWLEDGE_CENTER,
        surface: PRODUCT_SURFACES.WORKSPACE,
        roles: [...WORKSPACE_MENU_ROLES],
        children: WORKSPACE_MENU.find((m) => m.id === 'workspace-knowledge')?.children || [],
    }

    const chatTop = CHAT_MENU[0]

    return [
        adminControl,
        adminRuntime,
        workspaceApps,
        workspaceResources,
        // admin 历史菜单中 chat 不应出现；为了兼容，只在非 admin 角色下暴露
        chatTop,
    ]
}

function flattenWorkspaceSections(ids) {
    const sections = []
    for (const id of ids) {
        const menu = WORKSPACE_MENU.find((m) => m.id === id)
        if (menu?.children) sections.push(...menu.children)
    }
    // 去重（按 path）
    const seen = new Set()
    return sections.filter((child) => {
        if (!child?.path) return false
        if (seen.has(child.path)) return false
        seen.add(child.path)
        return true
    })
}

function flattenAdminSections(ids) {
    const sections = []
    for (const id of ids) {
        const menu = ADMIN_MENU.find((m) => m.id === id)
        if (menu?.children) sections.push(...menu.children)
    }
    const seen = new Set()
    return sections.filter((child) => {
        if (!child?.path) return false
        if (seen.has(child.path)) return false
        seen.add(child.path)
        return true
    })
}

export const TOP_MENU_CONFIG = buildLegacyTopMenuConfig()

/**
 * 获取可见的菜单项（根据权限过滤）。
 *
 * 与拆分前的语义保持一致：未传 surface 时，按角色给默认 surface（admin → admin，非 admin → chat）。
 * 同时保留对 'runtime' 这类历史 id 的过滤规则（仅 admin 可见）。
 */
export function getVisibleMenus(userRoles = [], surface = null) {
    const adminLike = isAdminLike(userRoles)
    const selectedSurface = surface || (adminLike ? PRODUCT_SURFACES.ADMIN : PRODUCT_SURFACES.CHAT)

    return TOP_MENU_CONFIG
        .filter((menu) => menu.surface === selectedSurface)
        .filter((menu) => hasAnyRole(userRoles, menu.roles))
        .map((menu) => ({
            ...menu,
            children: filterMenuChildrenCompat(menu.children || [], userRoles, menu.roles),
        }))
        .filter((menu) => menu.id === 'chat'
            ? !adminLike && hasAnyRole(userRoles, menu.roles)
            : true)
        .filter((menu) => !menu.children || menu.children.length > 0)
}

/**
 * 兼容旧实现的 section 过滤逻辑：
 * - section 仅在有可见子项时保留
 * - 子项按 roles 过滤
 */
function filterMenuChildrenCompat(children = [], userRoles = [], fallbackRoles = []) {
    const isMenuSection = (item = {}) => item.type === 'section' || item.divider

    const visibleChildren = children.filter((child) => (
        isMenuSection(child) || hasAnyRole(userRoles, child.roles || fallbackRoles)
    ))

    return visibleChildren.filter((child, index) => {
        if (!isMenuSection(child)) return true

        const nextSectionIndex = visibleChildren
            .slice(index + 1)
            .findIndex((nextChild) => isMenuSection(nextChild))
        const sectionChildren = nextSectionIndex >= 0
            ? visibleChildren.slice(index + 1, index + 1 + nextSectionIndex)
            : visibleChildren.slice(index + 1)

        return sectionChildren.some((nextChild) => !isMenuSection(nextChild))
    })
}

export { isAdminLike, hasAnyRole as canAccessAnyRole }

/**
 * 根据当前路由返回激活的菜单 id（兼容旧实现）。
 */
export function getActiveMenuId(currentPath) {
    if (!currentPath) return null

    const legacyDomain = [
        ['/dashboard/resources', 'resources'],
        ['/dashboard/applications', 'applications'],
        ['/dashboard/runtime', 'runtime'],
        ['/dashboard/control', 'control'],
    ].find(([prefix]) => currentPath.startsWith(prefix))
    if (legacyDomain) return legacyDomain[1]

    return getActiveMenuIdForMenu(TOP_MENU_CONFIG, currentPath)
}

/**
 * ROLE_ADMIN 默认进入管理台，ROLE_USER 默认进入 /chat。
 */
export function getDefaultHomeByRoles(userRoles = []) {
    if (isAdminLike(userRoles)) {
        return ROUTES.ADMIN
    }
    return ROUTES.CHAT
}

/**
 * 识别当前路径属于哪个产品面（兼容旧调用）。
 * 仍然识别 /dashboard/control、/dashboard/runtime、/dashboard/applications、/dashboard/resources
 * 是为了旧重定向 URL 经过中间态时，UI 渲染能落到对应 Layout。
 */
export function getProductSurfaceByPath(path = '') {
    if (path === ROUTES.CHAT || path.startsWith(`${ROUTES.CHAT}/`)) {
        return PRODUCT_SURFACES.CHAT
    }
    if (
        path === ROUTES.ADMIN_ROOT
        || path.startsWith(`${ROUTES.ADMIN_ROOT}/`)
        || path.startsWith('/dashboard/control')
        || path.startsWith('/dashboard/runtime')
    ) {
        return PRODUCT_SURFACES.ADMIN
    }
    if (
        path === ROUTES.WORKSPACE_ROOT
        || path.startsWith(`${ROUTES.WORKSPACE_ROOT}/`)
        || path.startsWith('/dashboard/applications')
        || path.startsWith('/dashboard/resources')
        || path.startsWith(ROUTES.PLATFORM)
    ) {
        return PRODUCT_SURFACES.WORKSPACE
    }
    return null
}

export function getAvailableProductSurfaces(userRoles = []) {
    return Object.values(PRODUCT_SURFACE_CONFIG)
        .filter((surface) => hasAnyRole(userRoles, surface.roles))
}

/**
 * ROLE_USER 可以进入工作台资源域，但不能进入管理台治理域。
 * 历史的 /dashboard/profile 对普通用户重定向到独立的 Chat 个人中心。
 *
 * @param {string[]} userRoles  当前用户角色列表
 * @param {string}   targetPath 目标路径
 * @returns {string|null} 重定向目标路径，未命中返回 null
 */
export function getDashboardGuardRedirect(userRoles = [], targetPath = '') {
    if (!targetPath) return null
    if (!targetPath.startsWith('/dashboard')) return null
    if (targetPath === '/dashboard/profile') {
        return isAdminLike(userRoles) ? null : ROUTES.CHAT_PROFILE
    }
    if (isAdminLike(userRoles)) return null
    if (
        hasAnyRole(userRoles, WORKSPACE_MENU_ROLES)
        && (
            targetPath.startsWith('/dashboard/applications')
            || targetPath.startsWith('/dashboard/resources')
        )
    ) {
        return null
    }
    return ROUTES.CHAT
}

/**
 * Convenience re-exports.
 * 新组件建议直接从 router/menuConfig/{workspaceMenu,adminMenu,chatMenu,shared} 消费。
 */
export { buildVisibleMenus, getActiveMenuIdForMenu }
