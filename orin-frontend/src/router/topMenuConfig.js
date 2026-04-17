import { ROUTES } from './routes'

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
      { title: '智能体列表', path: ROUTES.AGENTS.LIST, icon: 'List', status: 'available' },
      { title: '知识对话', path: ROUTES.AGENTS.WORKSPACE, icon: 'ChatDotRound', status: 'available' },
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
    ],
  },
  {
    id: 'knowledge',
    title: '知识库',
    icon: 'Reading',
    color: '#0f766e',
    path: ROUTES.KNOWLEDGE.ROOT,
    requiresAdmin: false,
    children: [
      { title: '知识库列表', path: ROUTES.KNOWLEDGE.LIST, icon: 'List', status: 'available' },
      { title: '素材管理', path: ROUTES.KNOWLEDGE.MEDIA, icon: 'Picture', status: 'available' },
      { title: '知识图谱', path: ROUTES.KNOWLEDGE.GRAPH, icon: 'Connection', status: 'beta' },
      { title: '同步管理', path: ROUTES.KNOWLEDGE.SYNC, icon: 'Clock', status: 'beta' },
      { title: '检索实验室', path: ROUTES.KNOWLEDGE.EMBEDDING_LAB, icon: 'Search', status: 'available' },
      { title: '多模态实验室', path: ROUTES.KNOWLEDGE.VLM_LAB, icon: 'View', status: 'available' },
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
      { title: '首页', path: ROUTES.HOME, icon: 'HomeFilled', status: 'available' },
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
      { title: '访问密钥', path: ROUTES.SYSTEM.API_KEYS, icon: 'Key', status: 'available' },
      { title: '模型配置', path: ROUTES.SYSTEM.MODELS, icon: 'SetUp', status: 'available' },
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
      { title: '基础设置', path: ROUTES.SYSTEM.SETTINGS_BASE, icon: 'Setting', status: 'available' },
      { title: '通知渠道', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS, icon: 'Bell', status: 'available' },
      { title: '文件管理', path: ROUTES.SYSTEM.FILES, icon: 'Folder', status: 'available' },
      { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY, icon: 'Router', status: 'available' },
      { title: '分布式锁', path: ROUTES.SYSTEM.DISTRIBUTED_LOCK, icon: 'Lock', status: 'available' },
      { title: '审计日志', path: ROUTES.SYSTEM.AUDIT_LOGS, icon: 'Document', status: 'available' },
    ],
  },
]

/**
 * 获取可见的菜单项（根据权限过滤）
 * @param {boolean} isAdmin - 是否为管理员
 * @returns {Array} 过滤后的菜单配置
 */
export function getVisibleMenus(isAdmin = false) {
  return TOP_MENU_CONFIG.filter((menu) => {
    if (menu.requiresAdmin) {
      return isAdmin
    }
    return true
  })
}

/**
 * 根据当前路由判断激活的菜单
 * @param {string} currentPath - 当前路由路径
 * @returns {string|null} 激活的菜单 ID
 */
export function getActiveMenuId(currentPath) {
  for (const menu of TOP_MENU_CONFIG) {
    if (currentPath.startsWith(menu.path)) return menu.id
    if (menu.children) {
      for (const child of menu.children) {
        if (child.path && currentPath.startsWith(child.path)) return menu.id
      }
    }
  }
  return null
}
