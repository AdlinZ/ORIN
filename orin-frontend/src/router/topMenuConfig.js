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
 * 顶部导航菜单配置
 * 一级菜单固定为四个产品域，二级菜单按 section 收敛入口，避免把历史模块平铺到顶栏。
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'control',
    title: '平台控制',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.SYSTEM.ROOT,
    roles: SYSTEM_MENU_ROLES,
    children: [
      { type: 'section', title: '组织权限' },
      { title: '用户管理', path: ROUTES.SYSTEM.USERS, icon: 'User', roles: ORGANIZATION_MENU_ROLES },
      { title: '部门管理', path: ROUTES.SYSTEM.DEPARTMENTS, icon: 'OfficeBuilding', roles: ORGANIZATION_MENU_ROLES },
      { type: 'section', title: '平台配置' },
      { title: '平台总览', path: ROUTES.SYSTEM.ADMIN_DASHBOARD, icon: 'DataBoard', roles: SYSTEM_MENU_ROLES },
      { title: '模型管理', path: ROUTES.SYSTEM.MODELS, icon: 'SetUp', roles: SYSTEM_MENU_ROLES },
      { title: '定价配置', path: ROUTES.SYSTEM.PRICING, icon: 'PriceTag', roles: SYSTEM_MENU_ROLES },
      { title: '环境配置', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'Setting', roles: SYSTEM_MENU_ROLES },
      { title: '通知设置', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS, icon: 'Bell', roles: SYSTEM_MENU_ROLES },
      { title: '数据资产', path: ROUTES.SYSTEM.DATA_ASSETS, icon: 'Folder', roles: SYSTEM_MENU_ROLES },
      { type: 'section', title: '开放与网关' },
      { title: 'MCP 服务', path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE, icon: 'Connection', roles: SYSTEM_MENU_ROLES },
      { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY, icon: 'Router', roles: SYSTEM_MENU_ROLES },
    ],
  },
  {
    id: 'runtime',
    title: '运行观测',
    icon: 'Monitor',
    color: '#475569',
    path: ROUTES.MONITOR.ROOT,
    roles: MONITOR_MENU_ROLES,
    children: [
      { title: '运行总览', path: ROUTES.HOME, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '服务器监控', path: ROUTES.MONITOR.SERVER, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '任务队列', path: ROUTES.MONITOR.TASKS, icon: 'Tickets', roles: MONITOR_MENU_ROLES },
      { title: '调用链路', path: ROUTES.MONITOR.TRACES, icon: 'Share', roles: MONITOR_MENU_ROLES },
      { title: '用量统计', path: ROUTES.MONITOR.TOKENS, icon: 'Coin', roles: MONITOR_MENU_ROLES },
      { title: '性能分析', path: ROUTES.MONITOR.LATENCY, icon: 'Timer', roles: MONITOR_MENU_ROLES },
      { title: '告警与日志', path: ROUTES.MONITOR.ALERTS, icon: 'Bell', roles: MONITOR_MENU_ROLES },
    ],
  },
  {
    id: 'applications',
    title: '应用构建',
    icon: 'Robot',
    color: '#0f766e',
    path: ROUTES.AGENTS.ROOT,
    roles: BUILDER_MENU_ROLES,
    children: [
      { type: 'section', title: '智能体' },
      { title: '工作台', path: ROUTES.AGENTS.WORKSPACE, icon: 'ChatDotRound', roles: BUILDER_MENU_ROLES },
      { title: '智能体列表', path: ROUTES.AGENTS.LIST, icon: 'List', roles: BUILDER_MENU_ROLES },
      { title: '会话记录', path: ROUTES.AGENTS.CHAT_LOGS, icon: 'ChatDotRound', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '工作流与协作' },
      { title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection', roles: BUILDER_MENU_ROLES },
      { title: '可视化编排', path: ROUTES.AGENTS.WORKFLOW_VISUAL, icon: 'Edit', roles: BUILDER_MENU_ROLES },
      { title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay', roles: BUILDER_MENU_ROLES },
      { title: '多智能体协同', path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS, icon: 'Connection', roles: BUILDER_MENU_ROLES },
      { type: 'section', title: '扩展' },
      { title: 'Skills', path: ROUTES.AGENTS.SKILLS, icon: 'Star', roles: BUILDER_MENU_ROLES },
      { title: 'MCP 服务', path: ROUTES.AGENTS.MCP, icon: 'Connection', roles: BUILDER_MENU_ROLES },
      { title: '模型工具', path: ROUTES.AGENTS.MODEL_TOOLS, icon: 'Setting', roles: BUILDER_MENU_ROLES },
    ],
  },
  {
    id: 'resources',
    title: '资源知识',
    icon: 'Reading',
    color: '#334155',
    path: ROUTES.KNOWLEDGE.CENTER,
    roles: BUILDER_MENU_ROLES,
    children: [
      { title: '知识中心', path: ROUTES.KNOWLEDGE.CENTER, icon: 'Reading', roles: BUILDER_MENU_ROLES },
      { title: '知识资产', path: ROUTES.KNOWLEDGE.ASSETS, icon: 'Collection', roles: BUILDER_MENU_ROLES },
      { title: '检索实验', path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB, icon: 'Search', roles: BUILDER_MENU_ROLES },
    ],
  },
]

/**
 * 获取可见的菜单项（根据权限过滤）
 * @param {string[]} userRoles - 当前用户角色列表
 * @returns {Array} 过滤后的菜单配置
 */
export function getVisibleMenus(userRoles = []) {
  const adminLike = isAdminLike(userRoles)

  return TOP_MENU_CONFIG.filter((menu) => {
    switch (menu.id) {
      case 'runtime':
        return adminLike
      default:
        return hasAnyRole(userRoles, menu.roles)
    }
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
