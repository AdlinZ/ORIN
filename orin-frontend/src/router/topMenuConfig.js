import { ROUTES } from './routes'

export const ADMIN_MENU_ROLES = ['ROLE_ADMIN', 'ADMIN']
export const USER_MENU_ROLES = ['ROLE_USER', 'USER']
export const DASHBOARD_ADMIN_ROLES = [...ADMIN_MENU_ROLES]
export const WORKSPACE_MENU_ROLES = [...ADMIN_MENU_ROLES, ...USER_MENU_ROLES]
export const MONITOR_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const ORGANIZATION_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const SYSTEM_MENU_ROLES = [...ADMIN_MENU_ROLES]
export const BUILDER_MENU_ROLES = [...ADMIN_MENU_ROLES]

export const PRODUCT_SURFACES = Object.freeze({
  CHAT: 'chat',
  WORKSPACE: 'workspace',
  ADMIN: 'admin',
})

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
    path: ROUTES.WORKSPACE,
    icon: 'Grid',
    roles: WORKSPACE_MENU_ROLES,
  },
  [PRODUCT_SURFACES.ADMIN]: {
    id: PRODUCT_SURFACES.ADMIN,
    title: 'ORIN 管理台',
    shortTitle: '管理台',
    path: ROUTES.ADMIN,
    icon: 'Setting',
    roles: ADMIN_MENU_ROLES,
  },
})

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
    // 管理员默认进入管理台：/admin/admin-overview（canonical，旧路径由 LEGACY_ROUTE_REDIRECTS 自动重定向）
    return ROUTES.ADMIN
  }

  return ROUTES.CHAT
}

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
 * 顶部导航菜单配置
 * 一级菜单固定为四个产品域，二级菜单按 section 收敛入口，避免把历史模块平铺到顶栏。
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'control',
    title: '平台控制',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.ADMIN_PATHS.ROOT,
    surface: PRODUCT_SURFACES.ADMIN,
    roles: SYSTEM_MENU_ROLES,
    children: [
      { type: 'section', title: '组织权限' },
      { title: '用户管理', path: ROUTES.ADMIN_PATHS.USERS, icon: 'User', roles: ORGANIZATION_MENU_ROLES },
      { title: '部门管理', path: ROUTES.ADMIN_PATHS.DEPARTMENTS, icon: 'OfficeBuilding', roles: ORGANIZATION_MENU_ROLES },
      { type: 'section', title: '平台配置' },
      { title: '平台总览', path: ROUTES.ADMIN_PATHS.HOME, icon: 'DataBoard', roles: SYSTEM_MENU_ROLES },
      { title: '模型管理', path: ROUTES.ADMIN_PATHS.MODELS, icon: 'SetUp', roles: SYSTEM_MENU_ROLES },
      { title: '定价配置', path: ROUTES.ADMIN_PATHS.PRICING, icon: 'PriceTag', roles: SYSTEM_MENU_ROLES },
      { title: '环境配置', path: ROUTES.ADMIN_PATHS.ENVIRONMENT, icon: 'Setting', roles: SYSTEM_MENU_ROLES },
      { title: '通知设置', path: ROUTES.ADMIN_PATHS.NOTIFICATIONS, icon: 'Bell', roles: SYSTEM_MENU_ROLES },
      { title: '数据资产', path: ROUTES.ADMIN_PATHS.DATA_ASSETS, icon: 'Folder', roles: SYSTEM_MENU_ROLES },
      { type: 'section', title: '开放与网关' },
      { title: 'MCP 服务', path: ROUTES.ADMIN_PATHS.MCP, icon: 'Connection', roles: SYSTEM_MENU_ROLES },
      { title: '统一网关', path: ROUTES.ADMIN_PATHS.GATEWAY, icon: 'Router', roles: SYSTEM_MENU_ROLES },
    ],
  },
  {
    id: 'runtime',
    title: '运行观测',
    icon: 'Monitor',
    color: '#475569',
    path: ROUTES.ADMIN_PATHS.RUNTIME,
    surface: PRODUCT_SURFACES.ADMIN,
    roles: MONITOR_MENU_ROLES,
    children: [
      { title: '运行总览', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/overview`, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '服务器监控', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/server`, icon: 'DataAnalysis', roles: MONITOR_MENU_ROLES },
      { title: '任务队列', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/tasks`, icon: 'Tickets', roles: MONITOR_MENU_ROLES },
      { title: '调用链路', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/traces`, icon: 'Share', roles: MONITOR_MENU_ROLES },
      { title: '用量统计', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/metrics`, icon: 'Coin', roles: MONITOR_MENU_ROLES },
      { title: '性能分析', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/latency`, icon: 'Timer', roles: MONITOR_MENU_ROLES },
      { title: '告警与日志', path: `${ROUTES.ADMIN_PATHS.RUNTIME}/alerts`, icon: 'Bell', roles: MONITOR_MENU_ROLES },
    ],
  },
  {
    id: 'applications',
    title: '应用构建',
    icon: 'Robot',
    color: '#0f766e',
    path: ROUTES.WORKSPACE_PATHS.ROOT,
    surface: PRODUCT_SURFACES.WORKSPACE,
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { type: 'section', title: '智能体' },
      { title: '工作台', path: ROUTES.WORKSPACE_PATHS.AGENT_WORKSPACE, icon: 'ChatDotRound', roles: WORKSPACE_MENU_ROLES },
      { title: '智能体列表', path: ROUTES.WORKSPACE_PATHS.AGENTS, icon: 'List', roles: WORKSPACE_MENU_ROLES },
      { title: '会话记录', path: ROUTES.WORKSPACE_PATHS.CONVERSATIONS, icon: 'ChatDotRound', roles: WORKSPACE_MENU_ROLES },
      { type: 'section', title: '工作流与协作' },
      { title: '工作流中心', path: ROUTES.WORKSPACE_PATHS.WORKFLOWS, icon: 'Connection', roles: WORKSPACE_MENU_ROLES },
      { title: '可视化编排', path: ROUTES.WORKSPACE_PATHS.WORKFLOW_VISUAL, icon: 'Edit', roles: WORKSPACE_MENU_ROLES },
      { title: '执行记录', path: ROUTES.WORKSPACE_PATHS.WORKFLOW_EXECUTION, icon: 'VideoPlay', roles: WORKSPACE_MENU_ROLES },
      { title: '多智能体协同', path: ROUTES.WORKSPACE_PATHS.COLLABORATION, icon: 'Connection', roles: WORKSPACE_MENU_ROLES },
      { type: 'section', title: '扩展' },
      { title: 'Skills', path: `${ROUTES.WORKSPACE_PATHS.EXTENSIONS}?tab=skills`, icon: 'Star', roles: WORKSPACE_MENU_ROLES },
      { title: 'MCP 服务', path: `${ROUTES.WORKSPACE_PATHS.EXTENSIONS}?tab=mcp`, icon: 'Connection', roles: WORKSPACE_MENU_ROLES },
      { title: '模型工具', path: `${ROUTES.WORKSPACE_PATHS.EXTENSIONS}?tab=bindings`, icon: 'Setting', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'resources',
    title: '资源知识',
    icon: 'Reading',
    color: '#334155',
    path: ROUTES.WORKSPACE_PATHS.KNOWLEDGE_CENTER,
    surface: PRODUCT_SURFACES.WORKSPACE,
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: '知识中心', path: ROUTES.WORKSPACE_PATHS.KNOWLEDGE_CENTER, icon: 'Reading', roles: WORKSPACE_MENU_ROLES },
      { title: '知识资产', path: ROUTES.WORKSPACE_PATHS.KNOWLEDGE_ASSETS, icon: 'Collection', roles: WORKSPACE_MENU_ROLES },
      { title: '检索实验', path: ROUTES.WORKSPACE_PATHS.KNOWLEDGE_RETRIEVAL, icon: 'Search', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'chat',
    title: '对话',
    icon: 'ChatRound',
    color: '#0f766e',
    path: ROUTES.CHAT,
    surface: PRODUCT_SURFACES.CHAT,
    roles: USER_MENU_ROLES,
    children: [
      { title: 'ORIN Chat', path: ROUTES.CHAT, icon: 'ChatRound', roles: USER_MENU_ROLES }
    ],
  },
]

/**
 * 获取可见的菜单项（根据权限过滤）
 * @param {string[]} userRoles - 当前用户角色列表
 * @returns {Array} 过滤后的菜单配置
 */
export function getVisibleMenus(userRoles = [], surface = null) {
  const adminLike = isAdminLike(userRoles)
  const selectedSurface = surface || (adminLike ? PRODUCT_SURFACES.ADMIN : PRODUCT_SURFACES.CHAT)

  return TOP_MENU_CONFIG.filter((menu) => {
    if (menu.surface !== selectedSurface) return false
    switch (menu.id) {
      case 'runtime':
        return adminLike
      case 'chat':
        return !adminLike && hasAnyRole(userRoles, menu.roles)
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

  const legacyDomain = [
    ['/dashboard/resources', 'resources'],
    ['/dashboard/applications', 'applications'],
    ['/dashboard/runtime', 'runtime'],
    ['/dashboard/control', 'control'],
  ].find(([prefix]) => currentPath.startsWith(prefix))
  if (legacyDomain) return legacyDomain[1]

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
