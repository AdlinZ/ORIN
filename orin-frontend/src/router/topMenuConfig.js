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

function isAdminLike(userRoles = []) {
  return hasAnyRole(userRoles, ADMIN_MENU_ROLES)
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
  if (isAdminLike(userRoles)) {
    return ROUTES.SYSTEM.ADMIN_DASHBOARD
  }

  return ROUTES.PORTAL_API_KEYS
}

/**
 * 顶部导航菜单配置（Workspace vNext 四入口 + 更多工具）
 *
 * 主线：
 *   1. Agents   — 创建、草稿、冻结版本
 *   2. Runners  — 接入、状态、容量
 *   3. Runs     — 执行、日志、取消、重试
 *   4. Endpoints — 发布 API / MCP
 *
 * 旧模块（智能体管理 / 知识库 / 运行观测 / 系统设置）收进"更多工具"，
 * 不再占一级导航，但路由保持可访问。
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'agents',
    title: 'Agents',
    icon: 'Robot',
    color: '#155eef',
    path: ROUTES.WORKSPACE.AGENTS,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: 'Agent 列表', path: ROUTES.WORKSPACE.AGENTS, icon: 'List', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'runners',
    title: 'Runners',
    icon: 'Monitor',
    color: '#0f766e',
    path: ROUTES.WORKSPACE.RUNNERS,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: 'Runner 列表', path: ROUTES.WORKSPACE.RUNNERS, icon: 'Monitor', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'runs',
    title: 'Runs',
    icon: 'VideoPlay',
    color: '#f59e0b',
    path: ROUTES.WORKSPACE.RUNS,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: '执行记录', path: ROUTES.WORKSPACE.RUNS, icon: 'VideoPlay', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'endpoints',
    title: 'Endpoints',
    icon: 'Connection',
    color: '#8b5cf6',
    path: ROUTES.WORKSPACE.ENDPOINTS,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: 'API / MCP', path: ROUTES.WORKSPACE.ENDPOINTS, icon: 'Connection', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'legacy',
    title: '更多工具',
    icon: 'MoreFilled',
    color: '#64748b',
    path: '/dashboard',
    roles: BUILDER_MENU_ROLES,
    children: [
      { type: 'section', title: '智能体（旧版）' },
      { title: '智能体列表', path: ROUTES.AGENTS.LIST, icon: 'Grid', roles: BUILDER_MENU_ROLES },
      { title: '智能体工作台', path: ROUTES.AGENTS.WORKSPACE, icon: 'ChatDotRound', roles: BUILDER_MENU_ROLES },
      { title: '会话记录', path: ROUTES.AGENTS.CHAT_LOGS, icon: 'ChatDotRound', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '工作流' },
      { title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection', roles: BUILDER_MENU_ROLES },
      { title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '知识库' },
      { title: '知识中心', path: ROUTES.KNOWLEDGE.CENTER, icon: 'Reading', roles: BUILDER_MENU_ROLES },
      { title: '知识资产', path: ROUTES.KNOWLEDGE.ASSETS, icon: 'Collection', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '扩展' },
      { title: 'Skills', path: ROUTES.AGENTS.SKILLS, icon: 'Star', roles: BUILDER_MENU_ROLES },
      { title: 'MCP 服务', path: ROUTES.AGENTS.MCP, icon: 'Connection', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '模型' },
      { title: '模型管理', path: ROUTES.AGENTS.MODELS, icon: 'Cpu', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '运行观测' },
      { title: '运行总览', path: ROUTES.HOME, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '服务器监控', path: ROUTES.MONITOR.SERVER, icon: 'Monitor', roles: MONITOR_MENU_ROLES },
      { title: '调用链路', path: ROUTES.MONITOR.TRACES, icon: 'Share', roles: MONITOR_MENU_ROLES },
      { title: '告警与日志', path: ROUTES.MONITOR.ALERTS, icon: 'Bell', roles: MONITOR_MENU_ROLES },
      { type: 'section', title: '系统管理' },
      { title: '用户管理', path: ROUTES.SYSTEM.USERS, icon: 'User', roles: SYSTEM_MENU_ROLES },
      { title: '环境配置', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'Setting', roles: SYSTEM_MENU_ROLES },
      { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY, icon: 'Router', roles: SYSTEM_MENU_ROLES },
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
  })).filter((menu) => !menu.children || menu.children.length > 0)
}

/**
 * 根据当前路由判断激活的菜单
 * @param {string} currentPath - 当前路由路径
 * @returns {string|null} 激活的菜单 ID
 */
export function getActiveMenuId(currentPath) {
  if (!currentPath) return null

  let matchedByChild = null
  let longestChildPath = -1
  let matchedByMenu = null
  let longestMenuPath = -1

  for (const menu of TOP_MENU_CONFIG) {
    if (menu.path && currentPath.startsWith(menu.path) && menu.path.length > longestMenuPath) {
      matchedByMenu = menu.id
      longestMenuPath = menu.path.length
    }

    if (menu.children) {
      for (const child of menu.children) {
        if (child.path && currentPath.startsWith(child.path) && child.path.length > longestChildPath) {
          matchedByChild = menu.id
          longestChildPath = child.path.length
        }
      }
    }
  }

  return matchedByChild || matchedByMenu
}
