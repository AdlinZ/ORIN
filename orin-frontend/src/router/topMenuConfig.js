import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 固定的4个一级菜单，每个菜单包含多个二级菜单项
 */
export const TOP_MENU_CONFIG = [
    {
        id: 'applications',
        title: '应用',
        icon: 'Box',
        color: '#155eef',
        path: ROUTES.APPLICATIONS.ROOT,
        requiresAdmin: false,
        children: [
            {
                title: '应用列表',
                path: ROUTES.APPLICATIONS.AGENTS,
                icon: 'List'
            },
            {
                title: '会话记录',
                path: ROUTES.APPLICATIONS.CONVERSATIONS,
                icon: 'ChatDotRound'
            },
            {
                title: '模型管理',
                path: ROUTES.APPLICATIONS.MODELS,
                icon: 'Cpu'
            },
            {
                title: '技能绑定',
                path: ROUTES.APPLICATIONS.SKILLS,
                icon: 'MagicStick'
            },
            {
                title: '流程编排',
                path: ROUTES.APPLICATIONS.WORKFLOWS,
                icon: 'Connection'
            }
        ]
    },
    {
        id: 'runtime',
        title: '运行',
        icon: 'Monitor',
        color: '#10b981',
        path: ROUTES.RUNTIME.ROOT,
        requiresAdmin: false,
        children: [
            {
                title: '运行概览',
                path: ROUTES.RUNTIME.OVERVIEW,
                icon: 'DataLine'
            },
            {
                title: '实时指标',
                path: ROUTES.RUNTIME.METRICS,
                icon: 'TrendCharts'
            },
            {
                title: '调用链路',
                path: ROUTES.RUNTIME.TRACES,
                icon: 'Share'
            },
            {
                title: '异常告警',
                path: ROUTES.RUNTIME.ALERTS,
                icon: 'Warning'
            }
        ]
    },
    {
        id: 'resources',
        title: '资源',
        icon: 'Collection',
        color: '#8b5cf6',
        path: ROUTES.RESOURCES.ROOT,
        requiresAdmin: false,
        children: [
            {
                title: '知识库',
                path: ROUTES.RESOURCES.KNOWLEDGE,
                icon: 'Document'
            },
            {
                title: '素材库',
                path: ROUTES.RESOURCES.MEDIA,
                icon: 'Picture'
            },
            {
                title: 'RAG 实验室',
                path: ROUTES.RESOURCES.EMBEDDING_LAB,
                icon: 'Aim'
            },
            {
                title: '视觉实验室',
                path: ROUTES.RESOURCES.VLM_LAB,
                icon: 'View'
            },
            {
                title: '资产架构',
                path: ROUTES.RESOURCES.ARCHITECTURE,
                icon: 'Grid'
            }
        ]
    },
    {
        id: 'control',
        title: '控制',
        icon: 'Setting',
        color: '#64748b',
        path: ROUTES.CONTROL.ROOT,
        requiresAdmin: true,
        children: [
            {
                title: '用户权限',
                path: ROUTES.CONTROL.USERS,
                icon: 'User'
            },
            {
                title: '日志配置',
                path: ROUTES.CONTROL.LOG_CONFIG,
                icon: 'Document'
            },
            {
                title: '审计日志',
                path: ROUTES.CONTROL.AUDIT_LOGS,
                icon: 'Notebook'
            },
            {
                title: 'API 管理',
                path: ROUTES.CONTROL.API_MANAGEMENT,
                icon: 'Link'
            },
            {
                title: '定价策略',
                path: ROUTES.CONTROL.PRICING,
                icon: 'Coin'
            },
            {
                title: '监控设置',
                path: ROUTES.CONTROL.MONITOR_CONFIG,
                icon: 'Monitor'
            }
        ]
    }
]

/**
 * 获取可见的菜单项（根据权限过滤）
 * @param {boolean} isAdmin - 是否为管理员
 * @returns {Array} 过滤后的菜单配置
 */
export function getVisibleMenus(isAdmin = false) {
    return TOP_MENU_CONFIG.filter(menu => {
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
        // 检查是否匹配一级菜单路径
        if (currentPath.startsWith(menu.path)) {
            return menu.id
        }
        // 检查是否匹配二级菜单路径
        if (menu.children.some(child => currentPath.startsWith(child.path))) {
            return menu.id
        }
    }
    return null
}
