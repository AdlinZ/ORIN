import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 一级菜单固定为 5 个：首页概览、智能体管理、知识与数据、运行监控、系统管理
 */
export const TOP_MENU_CONFIG = [
  {
    id: 'home',
    title: '首页概览',
    icon: 'DataLine',
    color: '#2563eb',
    path: ROUTES.HOME,
    requiresAdmin: false,
    children: [
      {
        title: '数据驾驶舱',
        path: ROUTES.HOME,
        icon: 'DataAnalysis',
        status: 'available',
      },
    ],
  },
  {
    id: 'agents',
    title: '智能体管理',
    icon: 'Robot',
    color: '#155eef',
    path: ROUTES.AGENTS.ROOT,
    requiresAdmin: false,
    children: [
      {
        title: '智能体台账',
        path: ROUTES.AGENTS.LIST,
        icon: 'Grid',
        children: [
          {
            title: '智能体列表',
            path: ROUTES.AGENTS.LIST,
            icon: 'List',
            status: 'available',
          },
          {
            title: '智能体接入',
            path: ROUTES.AGENTS.ONBOARD,
            icon: 'Plus',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '运行交互',
        path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
        icon: 'Monitor',
        children: [
          {
            title: '控制台',
            path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
            icon: 'Monitor',
            status: 'available',
          },
          {
            title: '知识对话',
            path: ROUTES.AGENTS.WORKSPACE,
            icon: 'ChatDotRound',
            status: 'available',
          },
          {
            title: '会话记录',
            path: ROUTES.AGENTS.CHAT_LOGS,
            icon: 'ChatDotRound',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '协同编排',
        path: ROUTES.AGENTS.WORKFLOWS,
        icon: 'Edit',
        children: [
          {
            title: '工作流编排',
            path: ROUTES.AGENTS.WORKFLOWS,
            icon: 'Edit',
            status: 'available',
          },
          {
            title: '协作任务',
            path: ROUTES.AGENTS.COLLABORATION,
            icon: 'Avatar',
            status: 'available',
          },
          {
            title: '协作看板',
            path: ROUTES.AGENTS.COLLABORATION_DASHBOARD,
            icon: 'DataAnalysis',
            status: 'beta',
          },
        ],
      },
      { divider: true },
      {
        title: '更多功能',
        path: ROUTES.AGENTS.SKILLS,
        icon: 'MagicStick',
        children: [
          {
            title: '能力扩展',
            path: ROUTES.AGENTS.SKILLS,
            icon: 'Star',
            status: 'available',
          },
          {
            title: '服务接入',
            path: ROUTES.AGENTS.MCP,
            icon: 'Connection',
            status: 'available',
          },
          {
            title: '工具配置',
            path: ROUTES.AGENTS.TOOLS,
            icon: 'Tool',
            status: 'available',
          },
          {
            title: '版本管理',
            path: ROUTES.AGENTS.VERSION_MANAGE,
            icon: 'Collection',
            status: 'available',
          },
          {
            title: '测试中心',
            path: ROUTES.AGENTS.TEST_DEBUG,
            icon: 'Bug',
            status: 'beta',
          },
        ],
      },
    ],
  },
  {
    id: 'knowledge',
    title: '知识与数据',
    icon: 'Reading',
    color: '#0f766e',
    path: ROUTES.KNOWLEDGE.ROOT,
    requiresAdmin: false,
    children: [
      {
        title: '知识库管理',
        path: ROUTES.KNOWLEDGE.LIST,
        icon: 'Collection',
        children: [
          {
            title: '知识库列表',
            path: ROUTES.KNOWLEDGE.LIST,
            icon: 'List',
            status: 'available',
          },
          {
            title: '素材管理',
            path: ROUTES.KNOWLEDGE.MEDIA,
            icon: 'Picture',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '知识图谱',
        path: ROUTES.KNOWLEDGE.GRAPH,
        icon: 'Connection',
        status: 'beta',
      },
      { divider: true },
      {
        title: '数据同步',
        path: ROUTES.KNOWLEDGE.SYNC,
        icon: 'Upload',
        children: [
          {
            title: '同步任务',
            path: ROUTES.KNOWLEDGE.SYNC,
            icon: 'Clock',
            status: 'beta',
          },
        ],
      },
      { divider: true },
      {
        title: '更多功能',
        path: ROUTES.KNOWLEDGE.EMBEDDING_LAB,
        icon: 'DataLine',
        children: [
          {
            title: '检索实验室',
            path: ROUTES.KNOWLEDGE.EMBEDDING_LAB,
            icon: 'Search',
            status: 'available',
          },
          {
            title: '多模态实验室',
            path: ROUTES.KNOWLEDGE.VLM_LAB,
            icon: 'View',
            status: 'available',
          },
        ],
      },
    ],
  },
  {
    id: 'monitor',
    title: '运行监控',
    icon: 'Monitor',
    color: '#f59e0b',
    path: ROUTES.MONITOR.ROOT,
    requiresAdmin: false,
    children: [
      {
        title: '监控总览',
        path: ROUTES.MONITOR.DASHBOARD,
        icon: 'DataLine',
        children: [
          {
            title: '监控大盘',
            path: ROUTES.MONITOR.DASHBOARD,
            icon: 'DataAnalysis',
            status: 'available',
          },
          {
            title: '服务器监控',
            path: ROUTES.MONITOR.SERVER,
            icon: 'Monitor',
            status: 'available',
          },
          {
            title: '任务队列',
            path: ROUTES.MONITOR.TASKS,
            icon: 'Tickets',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '性能分析',
        path: ROUTES.MONITOR.TOKENS,
        icon: 'TrendCharts',
        children: [
          {
            title: '令牌统计',
            path: ROUTES.MONITOR.TOKENS,
            icon: 'Coin',
            status: 'available',
          },
          {
            title: '成本分析',
            path: ROUTES.MONITOR.COSTS,
            icon: 'Money',
            status: 'available',
          },
          {
            title: '时延统计',
            path: ROUTES.MONITOR.LATENCY,
            icon: 'Timer',
            status: 'available',
          },
          {
            title: '错误统计',
            path: ROUTES.MONITOR.ERRORS,
            icon: 'Warning',
            status: 'available',
          },
          {
            title: '调用链路',
            path: ROUTES.MONITOR.TRACES,
            icon: 'Share',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '告警中心',
        path: ROUTES.MONITOR.ALERTS,
        icon: 'Bell',
        children: [
          {
            title: '告警管理',
            path: ROUTES.MONITOR.ALERTS,
            icon: 'Bell',
            status: 'available',
          },
          {
            title: '规则配置',
            path: ROUTES.MONITOR.ALERT_RULES,
            icon: 'Setting',
            status: 'beta',
          },
        ],
      },
      { divider: true },
      {
        title: '运维日志',
        path: ROUTES.MONITOR.LOGS,
        icon: 'Document',
        children: [
          {
            title: '日志归档',
            path: ROUTES.MONITOR.LOGS,
            icon: 'Document',
            status: 'available',
          },
          {
            title: '系统维护',
            path: ROUTES.MONITOR.MAINTENANCE,
            icon: 'Setting',
            status: 'available',
          },
        ],
      },
    ],
  },
  {
    id: 'system',
    title: '系统管理',
    icon: 'Setting',
    color: '#64748b',
    path: ROUTES.SYSTEM.ROOT,
    requiresAdmin: true,
    children: [
      {
        title: '组织权限',
        path: ROUTES.SYSTEM.USERS,
        icon: 'User',
        children: [
          {
            title: '用户管理',
            path: ROUTES.SYSTEM.USERS,
            icon: 'User',
            status: 'available',
          },
          {
            title: '部门管理',
            path: ROUTES.SYSTEM.DEPARTMENTS,
            icon: 'OfficeBuilding',
            status: 'available',
          },
          {
            title: '角色管理',
            path: ROUTES.SYSTEM.ROLES,
            icon: 'UserFilled',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '平台配置',
        path: ROUTES.SYSTEM.SETTINGS_BASE,
        icon: 'Tools',
        children: [
          {
            title: '基础设置',
            path: ROUTES.SYSTEM.SETTINGS_BASE,
            icon: 'Setting',
            status: 'available',
          },
          {
            title: '通知渠道',
            path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS,
            icon: 'Bell',
            status: 'available',
          },
          {
            title: '同步配置',
            path: ROUTES.SYSTEM.SETTINGS_SYNC,
            icon: 'Clock',
            status: 'beta',
          },
          {
            title: '灰度配置',
            path: ROUTES.SYSTEM.REVAMP_ROLLOUT,
            icon: 'Operation',
            status: 'beta',
          },
        ],
      },
      { divider: true },
      {
        title: '模型资源',
        path: ROUTES.SYSTEM.MODELS,
        icon: 'Cpu',
        children: [
          {
            title: '模型配置',
            path: ROUTES.SYSTEM.MODELS,
            icon: 'SetUp',
            status: 'available',
          },
          {
            title: '模型默认参数',
            path: ROUTES.SYSTEM.SETTINGS_MODEL_DEFAULTS,
            icon: 'Cpu',
            status: 'available',
          },
          {
            title: '定价配置',
            path: ROUTES.SYSTEM.PRICING,
            icon: 'PriceTag',
            status: 'available',
          },
          {
            title: '知识库配置',
            path: ROUTES.SYSTEM.SETTINGS_KNOWLEDGE,
            icon: 'Document',
            status: 'available',
          },
          {
            title: '文件管理',
            path: ROUTES.SYSTEM.FILES,
            icon: 'Folder',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '安全网关',
        path: ROUTES.SYSTEM.GATEWAY,
        icon: 'Lock',
        children: [
          {
            title: '统一网关',
            path: ROUTES.SYSTEM.GATEWAY,
            icon: 'Router',
            status: 'available',
          },
          {
            title: '接口文档中心',
            path: ROUTES.SYSTEM.UNIFIED_API_DOCS,
            icon: 'Document',
            status: 'available',
          },
          {
            title: '限流规则',
            path: ROUTES.SYSTEM.RATE_LIMIT,
            icon: 'Lightning',
            status: 'available',
          },
          {
            title: '分布式锁',
            path: ROUTES.SYSTEM.DISTRIBUTED_LOCK,
            icon: 'Lock',
            status: 'available',
          },
          {
            title: '审计日志',
            path: ROUTES.SYSTEM.AUDIT_LOGS,
            icon: 'Document',
            status: 'available',
          },
        ],
      },
      { divider: true },
      {
        title: '更多功能',
        path: ROUTES.SYSTEM.HELP_CENTER,
        icon: 'QuestionFilled',
        children: [
          {
            title: '帮助中心',
            path: ROUTES.SYSTEM.HELP_CENTER,
            icon: 'QuestionFilled',
            status: 'available',
          },
          {
            title: '统计分析',
            path: ROUTES.SYSTEM.STATISTICS,
            icon: 'DataAnalysis',
            status: 'available',
          },
          {
            title: '系统维护',
            path: ROUTES.SYSTEM.SYSTEM_MAINTENANCE,
            icon: 'Wrench',
            status: 'available',
          },
          {
            title: '外部集成',
            path: ROUTES.SYSTEM.SETTINGS_INTEGRATIONS,
            icon: 'Connection',
            status: 'beta',
          },
          {
            title: '服务配置',
            path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
            icon: 'Service',
            status: 'available',
          },
          {
            title: '访问密钥',
            path: ROUTES.SYSTEM.API_KEYS,
            icon: 'Key',
            status: 'available',
          },
        ],
      },
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
    if (currentPath.startsWith(menu.path)) {
      return menu.id
    }

    if (menu.children) {
      for (const child of menu.children) {
        if (child.divider) continue
        if (child.path && currentPath.startsWith(child.path)) {
          return menu.id
        }
        if (child.children) {
          for (const subChild of child.children) {
            if (subChild.divider) continue
            if (subChild.path && currentPath.startsWith(subChild.path)) {
              return menu.id
            }
          }
        }
      }
    }
  }
  return null
}
