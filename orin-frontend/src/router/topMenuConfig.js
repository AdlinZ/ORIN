import { ROUTES } from './routes'

export const ADMIN_MENU_ROLES = ['ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN']
export const OPERATOR_MENU_ROLES = ['ROLE_OPERATOR']
export const USER_MENU_ROLES = ['ROLE_USER', 'USER']
export const PLATFORM_ADMIN_MENU_ROLES = ['ROLE_PLATFORM_ADMIN']
export const SUPER_ADMIN_MENU_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ADMIN']
export const DASHBOARD_OPERATOR_ROLES = [...ADMIN_MENU_ROLES, ...OPERATOR_MENU_ROLES]
export const MONITOR_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const ORGANIZATION_MENU_ROLES = [...SUPER_ADMIN_MENU_ROLES]
export const SYSTEM_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const OPERATOR_MONITOR_MENU_ENABLED = false
export const PLATFORM_ADMIN_ORGANIZATION_MENU_ENABLED = false

function hasAnyRole(userRoles = [], targetRoles = []) {
  if (!targetRoles || targetRoles.length === 0) return true
  return targetRoles.some((role) => userRoles.includes(role))
}

function isAdminLike(userRoles = []) {
  return hasAnyRole(userRoles, ADMIN_MENU_ROLES)
}

function isSuperAdminLike(userRoles = []) {
  return hasAnyRole(userRoles, SUPER_ADMIN_MENU_ROLES)
}

function isPlatformAdmin(userRoles = []) {
  return hasAnyRole(userRoles, PLATFORM_ADMIN_MENU_ROLES)
}

function isOperatorLike(userRoles = []) {
  return hasAnyRole(userRoles, OPERATOR_MENU_ROLES)
}

export function canAccessAnyRole(userRoles = [], targetRoles = []) {
  return hasAnyRole(userRoles, targetRoles)
}

export function getDefaultHomeByRoles(userRoles = []) {
  if (isAdminLike(userRoles)) {
    return ROUTES.HOME
  }

  if (isOperatorLike(userRoles)) {
    return ROUTES.AGENTS.LIST
  }

  return ROUTES.PORTAL
}

/**
 * 顶部导航菜单配置（真正二级结构）
 * 一级菜单：顶栏 tab
 * 二级菜单：下拉直出页面链接，无中间分组层
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'agents',
    title: '智能体管理',
    icon: 'Robot',
    color: '#0f766e',
    path: ROUTES.AGENTS.ROOT,
    roles: DASHBOARD_OPERATOR_ROLES,
    children: [
      { title: '智能体工作台', path: ROUTES.AGENTS.WORKSPACE, icon: 'ChatDotRound', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '智能体列表', path: ROUTES.AGENTS.LIST, icon: 'List', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '会话记录', path: ROUTES.AGENTS.CHAT_LOGS, icon: 'ChatDotRound', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '多智能体协同', path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS, icon: 'Connection', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '扩展管理', path: ROUTES.AGENTS.EXTENSIONS, icon: 'Star', roles: DASHBOARD_OPERATOR_ROLES },
    ],
  },
  {
    id: 'workflows',
    title: '工作流管理',
    icon: 'Edit',
    color: '#334155',
    path: ROUTES.AGENTS.WORKFLOWS,
    roles: DASHBOARD_OPERATOR_ROLES,
    children: [
      { title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '可视化编排', path: ROUTES.AGENTS.WORKFLOW_VISUAL, icon: 'Edit', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay', roles: DASHBOARD_OPERATOR_ROLES },
    ],
  },
  {
    id: 'knowledge',
    title: '知识库管理',
    icon: 'Reading',
    color: '#0f766e',
    path: ROUTES.KNOWLEDGE.CENTER,
    roles: DASHBOARD_OPERATOR_ROLES,
    children: [
      { title: '知识检索', path: ROUTES.KNOWLEDGE.CENTER, icon: 'Reading', roles: DASHBOARD_OPERATOR_ROLES },
      { title: '知识资产', path: ROUTES.KNOWLEDGE.ASSETS, icon: 'Collection', roles: DASHBOARD_OPERATOR_ROLES },
    ],
  },
  {
    id: 'monitor',
    title: '运行监控',
    icon: 'Monitor',
    color: '#475569',
    path: ROUTES.MONITOR.ROOT,
    roles: MONITOR_MENU_ROLES,
    children: [
      { title: '监控总览', path: ROUTES.HOME, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '服务器监控', path: ROUTES.MONITOR.SERVER, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '调用链路', path: ROUTES.MONITOR.TRACES, icon: 'Share', roles: MONITOR_MENU_ROLES },
      { title: '用量统计', path: ROUTES.MONITOR.TOKENS, icon: 'Coin', roles: MONITOR_MENU_ROLES },
      { title: '性能分析', path: ROUTES.MONITOR.LATENCY, icon: 'Timer', roles: MONITOR_MENU_ROLES },
      { title: '告警与日志', path: ROUTES.MONITOR.ALERTS, icon: 'Bell', roles: MONITOR_MENU_ROLES },
    ],
  },
  {
    id: 'organization',
    title: '组织权限',
    icon: 'OfficeBuilding',
    color: '#334155',
    path: ROUTES.SYSTEM.ROOT,
    roles: ORGANIZATION_MENU_ROLES,
    children: [
      { title: '用户管理', path: ROUTES.SYSTEM.USERS, icon: 'User', roles: ORGANIZATION_MENU_ROLES },
      { title: '部门管理', path: ROUTES.SYSTEM.DEPARTMENTS, icon: 'OfficeBuilding', roles: ORGANIZATION_MENU_ROLES },
      { title: '角色管理', path: ROUTES.SYSTEM.ROLES, icon: 'UserFilled', roles: ORGANIZATION_MENU_ROLES },
    ],
  },
  {
    id: 'system',
    title: '系统设置',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.SYSTEM.ROOT,
    roles: SYSTEM_MENU_ROLES,
    children: [
      { title: '模型管理', path: ROUTES.SYSTEM.MODELS, icon: 'SetUp', roles: SYSTEM_MENU_ROLES },
      { title: '定价配置', path: ROUTES.SYSTEM.PRICING, icon: 'PriceTag', roles: SYSTEM_MENU_ROLES },
      { title: '环境配置', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'Setting', roles: SYSTEM_MENU_ROLES },
      { title: '通知设置', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS, icon: 'Bell', roles: SYSTEM_MENU_ROLES },
      { title: '数据资产', path: ROUTES.SYSTEM.DATA_ASSETS, icon: 'Folder', roles: SYSTEM_MENU_ROLES },
      { title: 'MCP 服务', path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE, icon: 'Connection', roles: SYSTEM_MENU_ROLES },
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
  const superAdminLike = isSuperAdminLike(userRoles)
  const platformAdmin = isPlatformAdmin(userRoles)
  const operatorLike = isOperatorLike(userRoles)

  return TOP_MENU_CONFIG.filter((menu) => {
    switch (menu.id) {
      case 'monitor':
        return superAdminLike || platformAdmin || (operatorLike && OPERATOR_MONITOR_MENU_ENABLED)
      case 'organization':
        return superAdminLike || (platformAdmin && PLATFORM_ADMIN_ORGANIZATION_MENU_ENABLED)
      default:
        return hasAnyRole(userRoles, menu.roles)
    }
  }).map((menu) => ({
    ...menu,
    children: (menu.children || []).filter((child) => hasAnyRole(userRoles, child.roles || menu.roles)),
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
