import { ROUTES } from './routes'

export const ADMIN_MENU_ROLES = ['ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN']
export const OPERATOR_MENU_ROLES = ['ROLE_OPERATOR']
export const USER_MENU_ROLES = ['ROLE_USER', 'USER']
export const PLATFORM_ADMIN_MENU_ROLES = ['ROLE_PLATFORM_ADMIN']
export const SUPER_ADMIN_MENU_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ADMIN']
export const OPERATOR_MONITOR_MENU_ENABLED = false
export const PLATFORM_ADMIN_ORGANIZATION_MENU_ENABLED = false

function hasAnyRole(userRoles = [], targetRoles = []) {
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

export function getDefaultHomeByRoles(userRoles = []) {
  if (isAdminLike(userRoles)) {
    return ROUTES.HOME
  }

  if (isOperatorLike(userRoles)) {
    return ROUTES.AGENTS.LIST
  }

  return ROUTES.AGENTS.WORKSPACE
}

/**
 * 顶部导航菜单配置（真正二级结构）
 * 一级菜单：顶栏 tab
 * 二级菜单：下拉直出页面链接，无中间分组层
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'agents',
    title: '智能体',
    icon: 'Robot',
    color: '#155eef',
    path: ROUTES.AGENTS.ROOT,
    requiresAdmin: false,
    children: [
      { title: '智能体对话', path: ROUTES.AGENTS.WORKSPACE, icon: 'ChatDotRound', status: 'available' },
      { title: '智能体列表', path: ROUTES.AGENTS.LIST, icon: 'List', status: 'available' },
      { title: '会话记录', path: ROUTES.AGENTS.CHAT_LOGS, icon: 'ChatDotRound', status: 'available' },
      { title: '智能体扩展', path: ROUTES.AGENTS.EXTENSIONS, icon: 'Star', status: 'available' },
    ],
  },
  {
    id: 'workflows',
    title: '工作流',
    icon: 'Edit',
    color: '#7c3aed',
    path: ROUTES.AGENTS.WORKFLOWS,
    requiresAdmin: false,
    children: [
      { title: '工作流编排', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Edit', status: 'available' },
      { title: '工作流执行', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay', status: 'available' },
      { title: '多智能体编排', path: ROUTES.AGENTS.PLAYGROUND_WORKFLOWS, icon: 'Connection', status: 'available' },
      { title: '多智能体运行', path: ROUTES.AGENTS.PLAYGROUND_RUN, icon: 'VideoPlay', status: 'available' },
    ],
  },
  {
    id: 'knowledge',
    title: '知识库',
    icon: 'Reading',
    color: '#0f766e',
    path: ROUTES.KNOWLEDGE.CENTER,
    requiresAdmin: false,
    children: [
      { title: '知识中心', path: ROUTES.KNOWLEDGE.CENTER, icon: 'Reading', status: 'available' },
      { title: '知识资产', path: ROUTES.KNOWLEDGE.ASSETS, icon: 'Collection', status: 'available' },
    ],
  },
  {
    id: 'monitor',
    title: '监控',
    icon: 'Monitor',
    color: '#f59e0b',
    path: ROUTES.MONITOR.ROOT,
    requiresAdmin: false,
    children: [
      { title: '监控总览', path: ROUTES.HOME, icon: 'DataAnalysis', status: 'available' },
      { title: '服务器监控', path: ROUTES.MONITOR.SERVER, icon: 'DataAnalysis', status: 'available' },
      { title: '调用链路', path: ROUTES.MONITOR.TRACES, icon: 'Share', status: 'available' },
      { title: '成本统计', path: ROUTES.MONITOR.TOKENS, icon: 'Coin', status: 'available' },
      { title: '性能分析', path: ROUTES.MONITOR.LATENCY, icon: 'Timer', status: 'available' },
      { title: '告警与日志', path: ROUTES.MONITOR.ALERTS, icon: 'Bell', status: 'available' },
    ],
  },
  {
    id: 'organization',
    title: '组织',
    icon: 'OfficeBuilding',
    color: '#0891b2',
    path: ROUTES.SYSTEM.ROOT,
    requiresAdmin: true,
    children: [
      { title: '用户管理', path: ROUTES.SYSTEM.USERS, icon: 'User', status: 'available' },
      { title: '部门管理', path: ROUTES.SYSTEM.DEPARTMENTS, icon: 'OfficeBuilding', status: 'available' },
      { title: '角色管理', path: ROUTES.SYSTEM.ROLES, icon: 'UserFilled', status: 'available' },
      { title: '定价配置', path: ROUTES.SYSTEM.PRICING, icon: 'PriceTag', status: 'available' },
    ],
  },
  {
    id: 'system',
    title: '系统设置',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.SYSTEM.ROOT,
    requiresAdmin: true,
    children: [
      { title: '模型配置', path: ROUTES.SYSTEM.MODELS, icon: 'SetUp', status: 'available' },
      { title: '系统环境', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'Setting', status: 'available' },
      { title: '通知渠道', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS, icon: 'Bell', status: 'available' },
      { title: '文件管理', path: ROUTES.SYSTEM.FILES, icon: 'Folder', status: 'available' },
      { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY, icon: 'Router', status: 'available' },
      { title: '数据同步', path: ROUTES.SYSTEM.SYNC, icon: 'Refresh', status: 'beta' },
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
  const superAdminLike = isSuperAdminLike(userRoles)
  const platformAdmin = isPlatformAdmin(userRoles)
  const operatorLike = isOperatorLike(userRoles)

  return TOP_MENU_CONFIG.filter((menu) => {
    switch (menu.id) {
      case 'monitor':
        return superAdminLike || platformAdmin || (operatorLike && OPERATOR_MONITOR_MENU_ENABLED)
      case 'organization':
        return superAdminLike || (platformAdmin && PLATFORM_ADMIN_ORGANIZATION_MENU_ENABLED)
      case 'system':
        return adminLike
      default:
        return true
    }
  })
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
