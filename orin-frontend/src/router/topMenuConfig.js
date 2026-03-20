import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 4个一级菜单，支持三级菜单
 *
 * 菜单结构：
 * 1. 智能体 - 智能体管理、会话管理、能力扩展、工作流编排
 * 2. 知识中心 - 知识库、素材库、实验室、知识图谱、端侧同步
 * 3. 监控运维 - 监控大盘、统计分析、调用链路、告警中心、运维工具
 * 4. 系统管理 - 用户权限、认证鉴权、消息中心、文件管理、系统设置
 */
export const TOP_MENU_CONFIG = [
    // ==================== 1. 智能体 ====================
    {
        id: 'agents',
        title: '智能体',
        icon: 'Robot',
        color: '#155eef',
        path: ROUTES.AGENTS.ROOT,
        requiresAdmin: false,
        children: [
            // 智能体管理（三级）
            {
                title: '智能体管理',
                path: ROUTES.AGENTS.LIST,
                icon: 'Grid'
            },
            {
                title: '智能体接入',
                path: ROUTES.AGENTS.ONBOARD,
                icon: 'Plus'
            },
            {
                title: '智能体控制台',
                path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
                icon: 'Monitor'
            },
            // 分隔
            { divider: true },
            // 会话管理
            {
                title: '会话记录',
                path: ROUTES.AGENTS.CHAT_LOGS,
                icon: 'ChatDotRound'
            },
            {
                title: '协作任务',
                path: ROUTES.AGENTS.COLLABORATION,
                icon: 'Avatar'
            },
            // 分隔
            { divider: true },
            // 能力扩展
            {
                title: '技能管理',
                path: ROUTES.AGENTS.SKILLS,
                icon: 'MagicStick'
            },
            {
                title: 'MCP 管理',
                path: ROUTES.AGENTS.MCP,
                icon: 'Connection'
            },
            {
                title: 'Tools 注册',
                icon: 'Tool'
            },
            // 分隔
            { divider: true },
            // 工作流
            {
                title: '工作流编排',
                path: ROUTES.AGENTS.WORKFLOWS,
                icon: 'Edit'
            }
        ]
    },

    // ==================== 2. 知识中心 ====================
    {
        id: 'knowledge',
        title: '知识中心',
        icon: 'Reading',
        color: '#8b5cf6',
        path: ROUTES.KNOWLEDGE.ROOT,
        requiresAdmin: false,
        children: [
            {
                title: '知识库管理',
                path: ROUTES.KNOWLEDGE.LIST,
                icon: 'Collection'
            },
            {
                title: '素材库',
                path: ROUTES.KNOWLEDGE.MEDIA,
                icon: 'Picture'
            },
            { divider: true },
            {
                title: '检索实验室',
                path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
                icon: 'Search'
            },
            {
                title: '嵌入实验室',
                path: ROUTES.KNOWLEDGE.EMBEDDING_LAB,
                icon: 'Aim'
            },
            {
                title: '视觉实验室',
                path: ROUTES.KNOWLEDGE.VLM_LAB,
                icon: 'View'
            },
            { divider: true },
            {
                title: '智力资产',
                path: ROUTES.KNOWLEDGE.INTELLIGENCE,
                icon: 'Brain'
            },
            {
                title: '端侧同步',
                path: ROUTES.KNOWLEDGE.SYNC,
                icon: 'Upload'
            }
        ]
    },

    // ==================== 3. 监控运维 ====================
    {
        id: 'monitor',
        title: '监控运维',
        icon: 'Monitor',
        color: '#f59e0b',
        path: ROUTES.MONITOR.ROOT,
        requiresAdmin: false,
        children: [
            {
                title: '监控大盘',
                path: ROUTES.MONITOR.DASHBOARD,
                icon: 'DataLine'
            },
            { divider: true },
            {
                title: 'Token 统计',
                path: ROUTES.MONITOR.TOKENS,
                icon: 'Coin'
            },
            {
                title: '时延统计',
                path: ROUTES.MONITOR.LATENCY,
                icon: 'Timer'
            },
            {
                title: '错误统计',
                path: ROUTES.MONITOR.ERRORS,
                icon: 'Warning'
            },
            { divider: true },
            {
                title: '调用链路',
                path: ROUTES.MONITOR.TRACES,
                icon: 'Share'
            },
            { divider: true },
            {
                title: '告警规则',
                path: ROUTES.MONITOR.ALERT_RULES,
                icon: 'Bell'
            },
            {
                title: '通知渠道',
                path: ROUTES.MONITOR.NOTIFICATIONS,
                icon: 'Message'
            },
            { divider: true },
            {
                title: '任务队列',
                path: ROUTES.MONITOR.TASKS,
                icon: 'Tickets'
            },
            {
                title: '服务器监控',
                path: ROUTES.MONITOR.SERVER,
                icon: 'Cpu'
            },
            {
                title: '日志归档',
                path: ROUTES.MONITOR.LOGS,
                icon: 'Document'
            }
        ]
    },

    // ==================== 4. 系统管理 ====================
    {
        id: 'system',
        title: '系统管理',
        icon: 'Setting',
        color: '#64748b',
        path: ROUTES.SYSTEM.ROOT,
        requiresAdmin: true,
        children: [
            // 用户权限
            {
                title: '用户管理',
                path: ROUTES.SYSTEM.USERS,
                icon: 'User'
            },
            {
                title: '部门权限',
                path: ROUTES.SYSTEM.DEPARTMENTS,
                icon: 'OfficeBuilding'
            },
            {
                title: '角色管理',
                path: ROUTES.SYSTEM.ROLES,
                icon: 'UserFilled'
            },
            { divider: true },
            // 认证鉴权
            {
                title: 'API Key',
                path: ROUTES.SYSTEM.API_KEYS,
                icon: 'Key'
            },
            {
                title: '限流配置',
                path: ROUTES.SYSTEM.RATE_LIMIT,
                icon: 'Lightning'
            },
            { divider: true },
            {
                title: '消息中心',
                path: ROUTES.SYSTEM.MESSAGES,
                icon: 'Message'
            },
            {
                title: '文件管理',
                path: ROUTES.SYSTEM.FILES,
                icon: 'Folder'
            },
            { divider: true },
            {
                title: '系统设置',
                path: ROUTES.SYSTEM.SETTINGS,
                icon: 'Tools'
            },
            {
                title: '审计日志',
                path: ROUTES.SYSTEM.AUDIT_LOGS,
                icon: 'Document'
            },
            {
                title: '模型配置',
                path: ROUTES.SYSTEM.MODELS,
                icon: 'Cpu'
            },
            {
                title: '定价配置',
                path: ROUTES.SYSTEM.PRICING,
                icon: 'PriceTag'
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
        // 检查是否匹配二级/三级菜单路径
        if (menu.children) {
            for (const child of menu.children) {
                if (child.divider) continue
                if (currentPath.startsWith(child.path)) {
                    return menu.id
                }
            }
        }
    }
    return null
}
