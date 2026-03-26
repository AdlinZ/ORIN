import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 4个一级菜单，按照用户建议的结构组织
 *
 * 菜单结构：
 * 1. 智能体 - 智能体管理、工作流编排、多智能体协作、版本管理与测试
 * 2. 知识中心 - 知识库管理、知识图谱、端侧同步
 * 3. 监控运维 - 监控大盘、链路追踪、成本分析、告警中心、运维工具
 * 4. 系统管理 - 用户权限、外部集成、网关、安全、配置、帮助与维护
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
            // 智能体管理
            {
                title: '智能体列表',
                path: ROUTES.AGENTS.LIST,
                icon: 'Grid'
            },
            {
                title: '智能体控制台',
                path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
                icon: 'Monitor'
            },
            { divider: true },
            // 工作流编排
            {
                title: '工作流编排',
                path: ROUTES.AGENTS.WORKFLOWS,
                icon: 'Edit'
            },
            { divider: true },
            // 多智能体协作
            {
                title: '多智能体协作',
                path: ROUTES.AGENTS.COLLABORATION,
                icon: 'Avatar',
                children: [
                    {
                        title: '协作任务列表',
                        path: ROUTES.AGENTS.COLLABORATION,
                        icon: 'List'
                    },
                    {
                        title: '协作模式配置',
                        path: ROUTES.AGENTS.COLLABORATION_CONFIG,
                        icon: 'SetUp'
                    },
                    {
                        title: '任务队列监控',
                        path: ROUTES.MONITOR.TASKS,
                        icon: 'Tickets'
                    }
                ]
            },
            { divider: true },
            // 能力扩展
            {
                title: '能力扩展',
                path: ROUTES.AGENTS.SKILLS,
                icon: 'MagicStick',
                children: [
                    {
                        title: '技能管理',
                        path: ROUTES.AGENTS.SKILLS,
                        icon: 'Star'
                    },
                    {
                        title: 'MCP 管理',
                        path: ROUTES.AGENTS.MCP,
                        icon: 'Connection'
                    },
                    {
                        title: 'Tools 注册',
                        path: ROUTES.AGENTS.TOOLS,
                        icon: 'Tool'
                    }
                ]
            },
            { divider: true },
            // 版本与测试
            {
                title: '版本与测试',
                path: ROUTES.AGENTS.VERSION_MANAGE,
                icon: 'Clock',
                children: [
                    {
                        title: '版本管理',
                        path: ROUTES.AGENTS.VERSION_MANAGE,
                        icon: 'Collection'
                    },
                    {
                        title: '测试与调试',
                        path: ROUTES.AGENTS.TEST_DEBUG,
                        icon: 'Bug'
                    }
                ]
            },
            { divider: true },
            // 会话记录
            {
                title: '会话记录',
                path: ROUTES.AGENTS.CHAT_LOGS,
                icon: 'ChatDotRound'
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
            // 知识库管理
            {
                title: '知识库管理',
                path: ROUTES.KNOWLEDGE.LIST,
                icon: 'Collection',
                children: [
                    {
                        title: '知识库列表',
                        path: ROUTES.KNOWLEDGE.LIST,
                        icon: 'List'
                    },
                    {
                        title: '文档管理',
                        path: ROUTES.KNOWLEDGE.LIST,
                        icon: 'Document'
                    }
                ]
            },
            {
                title: '素材库',
                path: ROUTES.KNOWLEDGE.MEDIA,
                icon: 'Picture'
            },
            { divider: true },
            // 知识图谱
            {
                title: '知识图谱',
                path: ROUTES.KNOWLEDGE.GRAPH,
                icon: 'Connection'
            },
            {
                title: '知识库评估',
                path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
                icon: 'DataAnalysis'
            },
            { divider: true },
            // 端侧同步
            {
                title: '端侧同步',
                path: ROUTES.KNOWLEDGE.SYNC,
                icon: 'Upload',
                children: [
                    {
                        title: '同步任务',
                        path: ROUTES.KNOWLEDGE.SYNC,
                        icon: 'Clock'
                    },
                    {
                        title: '设备列表',
                        path: ROUTES.KNOWLEDGE.SYNC,
                        icon: 'Monitor'
                    }
                ]
            },
            { divider: true },
            // 实验室
            {
                title: '实验室',
                path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
                icon: 'Cpu',
                children: [
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
                    }
                ]
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
            // 运行仪表盘
            {
                title: '运行仪表盘',
                path: ROUTES.MONITOR.DASHBOARD,
                icon: 'DataLine'
            },
            { divider: true },
            // 调用链路
            {
                title: '调用链路',
                path: ROUTES.MONITOR.TRACES,
                icon: 'Share'
            },
            { divider: true },
            // 成本统计
            {
                title: '成本统计',
                path: ROUTES.MONITOR.TOKENS,
                icon: 'Coin',
                children: [
                    {
                        title: 'Token 统计',
                        path: ROUTES.MONITOR.TOKENS,
                        icon: 'Coin'
                    },
                    {
                        title: '成本分析',
                        path: ROUTES.MONITOR.TOKENS,
                        icon: 'Money'
                    },
                    {
                        title: '时延统计',
                        path: ROUTES.MONITOR.LATENCY,
                        icon: 'Timer'
                    }
                ]
            },
            {
                title: '错误统计',
                path: ROUTES.MONITOR.ERRORS,
                icon: 'Warning'
            },
            { divider: true },
            // 告警中心
            {
                title: '告警中心',
                path: ROUTES.MONITOR.ALERTS,
                icon: 'Bell',
                children: [
                    {
                        title: '告警规则配置',
                        path: ROUTES.MONITOR.ALERT_RULES,
                        icon: 'Setting'
                    },
                    {
                        title: '通知渠道',
                        path: ROUTES.MONITOR.NOTIFICATIONS,
                        icon: 'Message'
                    }
                ]
            },
            { divider: true },
            // 系统监控
            {
                title: '系统监控',
                path: ROUTES.MONITOR.SERVER,
                icon: 'Cpu',
                children: [
                    {
                        title: '服务器监控',
                        path: ROUTES.MONITOR.SERVER,
                        icon: 'Monitor'
                    },
                    {
                        title: '任务队列',
                        path: ROUTES.MONITOR.TASKS,
                        icon: 'Tickets'
                    }
                ]
            },
            { divider: true },
            // 日志中心
            {
                title: '日志中心',
                path: ROUTES.MONITOR.LOGS,
                icon: 'Document',
                children: [
                    {
                        title: '日志查询',
                        path: ROUTES.MONITOR.LOGS,
                        icon: 'Search'
                    },
                    {
                        title: '审计日志',
                        path: ROUTES.SYSTEM.AUDIT_LOGS,
                        icon: 'List'
                    }
                ]
            },
            { divider: true },
            // 异常事件
            {
                title: '异常事件',
                path: ROUTES.MONITOR.ALERTS,
                icon: 'WarningFilled'
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
            // 集成管理
            {
                title: '集成管理',
                path: ROUTES.SYSTEM.EXTERNAL_FRAMEWORKS,
                icon: 'Connection',
                children: [
                    {
                        title: '外部框架集成',
                        path: ROUTES.SYSTEM.EXTERNAL_FRAMEWORKS,
                        icon: 'Grid'
                    },
                    {
                        title: 'MCP 服务管理',
                        path: ROUTES.SYSTEM.MCP_SERVICE,
                        icon: 'Service'
                    }
                ]
            },
            { divider: true },
            // API 网关
            {
                title: 'API 网关',
                path: ROUTES.SYSTEM.GATEWAY,
                icon: 'Router',
                children: [
                    {
                        title: '网关配置',
                        path: ROUTES.SYSTEM.GATEWAY,
                        icon: 'Connection'
                    },
                    {
                        title: '限流规则',
                        path: ROUTES.SYSTEM.RATE_LIMIT,
                        icon: 'Lightning'
                    }
                ]
            },
            { divider: true },
            // 用户权限
            {
                title: '用户权限',
                path: ROUTES.SYSTEM.USERS,
                icon: 'User',
                children: [
                    {
                        title: '用户管理',
                        path: ROUTES.SYSTEM.USERS,
                        icon: 'User'
                    },
                    {
                        title: '部门管理',
                        path: ROUTES.SYSTEM.DEPARTMENTS,
                        icon: 'OfficeBuilding'
                    },
                    {
                        title: '角色管理',
                        path: ROUTES.SYSTEM.ROLES,
                        icon: 'UserFilled'
                    }
                ]
            },
            { divider: true },
            // 系统设置
            {
                title: '系统设置',
                path: '/dashboard/system/settings',
                icon: 'Tools',
                children: [
                    {
                        title: '基础设置',
                        path: ROUTES.SYSTEM.SETTINGS_BASE,
                        icon: 'Setting'
                    },
                    {
                        title: '邮件服务',
                        path: ROUTES.SYSTEM.SETTINGS_MAIL,
                        icon: 'Message'
                    },
                    {
                        title: '通知渠道',
                        path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS,
                        icon: 'Bell'
                    },
                    {
                        title: '模型默认参数',
                        path: ROUTES.SYSTEM.SETTINGS_MODEL_DEFAULTS,
                        icon: 'Cpu'
                    },
                    {
                        title: '监控配置',
                        path: ROUTES.SYSTEM.SETTINGS_MONITOR,
                        icon: 'Monitor'
                    },
                    {
                        title: '知识库配置',
                        path: ROUTES.SYSTEM.SETTINGS_KNOWLEDGE,
                        icon: 'Document'
                    }
                ]
            },
            { divider: true },
            // 运维工具
            {
                title: '运维工具',
                path: ROUTES.SYSTEM.MESSAGES,
                icon: 'Tools',
                children: [
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
                    {
                        title: '分布式锁',
                        path: ROUTES.SYSTEM.DISTRIBUTED_LOCK,
                        icon: 'Lock'
                    },
                    { divider: true },
                    {
                        title: '帮助中心',
                        path: ROUTES.SYSTEM.HELP_CENTER,
                        icon: 'QuestionFilled'
                    },
                    {
                        title: '统计分析',
                        path: ROUTES.SYSTEM.STATISTICS,
                        icon: 'DataAnalysis'
                    },
                    {
                        title: '系统维护',
                        path: ROUTES.SYSTEM.SYSTEM_MAINTENANCE,
                        icon: 'Wrench'
                    }
                ]
            },
            { divider: true },
            // 审计日志
            {
                title: '审计日志',
                path: ROUTES.SYSTEM.AUDIT_LOGS,
                icon: 'Document'
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
        if (menu.children) {
            for (const child of menu.children) {
                if (child.divider) continue
                if (currentPath.startsWith(child.path)) {
                    return menu.id
                }
                // 检查是否匹配三级菜单路径
                if (child.children) {
                    for (const subChild of child.children) {
                        if (subChild.divider) continue
                        if (currentPath.startsWith(subChild.path)) {
                            return menu.id
                        }
                    }
                }
            }
        }
    }
    return null
}
