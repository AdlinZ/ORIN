import { ROUTES } from './routes'

export const ADMIN_MENU_ROLES = ['ROLE_ADMIN', 'ADMIN']
export const USER_MENU_ROLES = ['ROLE_USER', 'USER']
export const DASHBOARD_ADMIN_ROLES = [...ADMIN_MENU_ROLES]
export const MONITOR_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const ORGANIZATION_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const SYSTEM_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const BUILDER_MENU_ROLES = [...ADMIN_MENU_ROLES]

function hasAnyRole(userRoles = [], targetRoles = []) {
  if (!targetRoles || targetRoles.length === 0) return true
  return targetRoles.some((role) => userRoles.includes(role))
}

function isMenuSection(item = {}) {
  return item.type === 'section' || item.divider
}

function filterMenuChildren(children = [], userRoles = [], fallbackRoles = []) {
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

export function canAccessAnyRole(userRoles = [], targetRoles = []) {
  return hasAnyRole(userRoles, targetRoles)
}

export function getDefaultHomeByRoles(userRoles = []) {
  if (hasAnyRole(userRoles, USER_MENU_ROLES) && !hasAnyRole(userRoles, ADMIN_MENU_ROLES)) {
    return ROUTES.PORTAL_API_KEYS
  }

  // 管理员默认进入产品概览，由概览给出当前阻塞项和下一步动作。
  return ROUTES.HOME
}

/**
 * 产品主导航（概览 → Agent → 运行 → 发布 → 资源 → 系统设置）
 *
 * 菜单只暴露完成核心闭环所需的入口。历史页面与兼容路由继续保留，
 * 但不再通过主导航主动暴露。
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'overview',
    title: '概览',
    icon: 'HomeFilled',
    color: '#334155',
    path: ROUTES.WORKSPACE.OVERVIEW,
    roles: BUILDER_MENU_ROLES,
    direct: true,
  },
  {
    id: 'agents',
    title: 'Agent',
    icon: 'Robot',
    color: '#155eef',
    path: ROUTES.WORKSPACE.AGENTS,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: 'Agent 列表', path: ROUTES.WORKSPACE.AGENTS, icon: 'Robot', roles: BUILDER_MENU_ROLES },
      { title: '工作流', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'runs',
    title: '运行',
    icon: 'VideoPlay',
    color: '#0f766e',
    path: ROUTES.WORKSPACE.RUNS,
    roles: BUILDER_MENU_ROLES,
    direct: true,
  },
  {
    id: 'publish',
    title: '发布',
    icon: 'Connection',
    color: '#8b5cf6',
    path: ROUTES.WORKSPACE.ENDPOINTS,
    roles: BUILDER_MENU_ROLES,
    direct: true,
  },
  {
    id: 'resources',
    title: '资源',
    icon: 'Collection',
    color: '#d97706',
    path: ROUTES.WORKSPACE.RUNNERS,
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: 'Runner', path: ROUTES.WORKSPACE.RUNNERS, icon: 'Monitor', roles: BUILDER_MENU_ROLES },
      { title: '模型', path: ROUTES.AGENTS.MODELS, icon: 'Cpu', roles: BUILDER_MENU_ROLES },
      { title: '知识库', path: ROUTES.KNOWLEDGE.CENTER, icon: 'Reading', roles: BUILDER_MENU_ROLES },
      { title: 'MCP 工具', path: ROUTES.AGENTS.MCP, icon: 'SetUp', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'settings',
    title: '系统设置',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.SYSTEM.SETTINGS_BASE,
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '系统概览', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'DataBoard', roles: SYSTEM_MENU_ROLES },
      { title: '用户管理', path: ROUTES.SYSTEM.USERS, icon: 'User', roles: SYSTEM_MENU_ROLES },
      { title: '审计记录', path: ROUTES.SYSTEM.AUDIT_LOGS, icon: 'Document', roles: SYSTEM_MENU_ROLES },
    ],
  },
]

/**
 * 获取可见的菜单项（根据权限过滤）
 * @param {string[]} userRoles - 当前用户角色列表
 * @returns {Array} 过滤后的菜单配置
 */
export function getVisibleMenus(userRoles = []) {
  return TOP_MENU_CONFIG.filter((menu) => {
    return hasAnyRole(userRoles, menu.roles)
  }).map((menu) => ({
    ...menu,
    children: filterMenuChildren(menu.children || [], userRoles, menu.roles),
  })).filter((menu) => menu.direct || !menu.children || menu.children.length > 0)
}

/**
 * 根据当前路由判断激活的菜单
 * @param {string} currentPath - 当前路由路径
 * @returns {string|null} 激活的菜单 ID
 */
export function getActiveMenuId(currentPath) {
  if (!currentPath) return null

  const comparablePath = currentPath.split(/[?#]/, 1)[0]
  // Trace 是单次运行的技术下钻，不再占用主菜单入口，但仍归属“运行”域。
  if (comparablePath.startsWith(ROUTES.MONITOR.TRACES)) return 'runs'
  // 网关控制台是发布域的高级管理页，不再占用主菜单入口。
  if (comparablePath.startsWith(ROUTES.SYSTEM.GATEWAY.split(/[?#]/, 1)[0])) return 'publish'
  // 环境参数和审计策略是系统设置的高级下钻，不再占用主菜单入口。
  if (comparablePath.startsWith(ROUTES.SYSTEM.SETTINGS_MONITOR)) return 'settings'
  if (comparablePath.startsWith(ROUTES.SYSTEM.AUDIT_SETTINGS)) return 'settings'
  let matchedByChild = null
  let longestChildPath = -1
  let matchedByMenu = null
  let longestMenuPath = -1

  for (const menu of TOP_MENU_CONFIG) {
    const menuPath = menu.path?.split(/[?#]/, 1)[0]
    if (menuPath && comparablePath.startsWith(menuPath) && menuPath.length > longestMenuPath) {
      matchedByMenu = menu.id
      longestMenuPath = menuPath.length
    }

    if (menu.children) {
      for (const child of menu.children) {
        const childPath = child.path?.split(/[?#]/, 1)[0]
        if (childPath && comparablePath.startsWith(childPath) && childPath.length > longestChildPath) {
          matchedByChild = menu.id
          longestChildPath = childPath.length
        }
      }
    }
  }

  return matchedByChild || matchedByMenu
}
