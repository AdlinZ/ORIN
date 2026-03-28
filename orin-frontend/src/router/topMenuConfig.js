import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 4个一级菜单，按照用户建议的结构组织
 *
 * 菜单结构：
 * 1. 智能体 - 智能体管理、工作流编排、多智能体协作、版本管理与测试
 * 2. 知识中心 - 知识库、知识图谱、同步管理
 * 3. 监控运维 - 监控总览、链路与分析、告警与事件、运维操作
 * 4. 系统管理 - 组织与权限、平台配置、模型与资源、安全与运维、支持与维护
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
                title: '智能体管理',
                path: ROUTES.AGENTS.LIST,
                icon: 'Grid',
                children: [
                    {
                        title: '智能体列表',
                        path: ROUTES.AGENTS.LIST,
                        icon: 'List'
                    },
                    {
                        title: '智能体接入',
                        path: ROUTES.AGENTS.ONBOARD,
                        icon: 'Plus'
                    }
                ]
            },
            { divider: true },
            {
                title: '运行与交互',
                path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
                icon: 'Monitor',
                children: [
                    {
                        title: '智能体控制台',
                        path: ROUTES.AGENTS.CONSOLE.replace('/:id', ''),
                        icon: 'Monitor'
                    },
                    {
                        title: '知识库对话',
                        path: ROUTES.AGENTS.WORKSPACE,
                        icon: 'ChatLineRound'
                    },
                    {
                        title: '会话记录',
                        path: ROUTES.AGENTS.CHAT_LOGS,
                        icon: 'ChatDotRound'
                    }
                ]
            },
            { divider: true },
            {
                title: '编排与协作',
                path: ROUTES.AGENTS.WORKFLOWS,
                icon: 'Edit',
                children: [
                    {
                        title: '工作流编排',
                        path: ROUTES.AGENTS.WORKFLOWS,
                        icon: 'Edit'
                    },
                    {
                        title: '协作任务列表',
                        path: ROUTES.AGENTS.COLLABORATION,
                        icon: 'Avatar'
                    },
                    {
                        title: '协作模式配置',
                        path: ROUTES.AGENTS.COLLABORATION_CONFIG,
                        icon: 'SetUp'
                    },
                    {
                        title: '协作仪表盘',
                        path: ROUTES.AGENTS.COLLABORATION_DASHBOARD,
                        icon: 'DataAnalysis'
                    }
                ]
            },
            { divider: true },
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
            {
                title: '版本与调试',
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
            // 知识库
            {
                title: '知识库',
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
                    },
                    {
                        title: '素材管理',
                        path: ROUTES.KNOWLEDGE.MEDIA,
                        icon: 'Picture'
                    }
                ]
            },
            { divider: true },
            {
                title: '知识图谱',
                path: ROUTES.KNOWLEDGE.GRAPH,
                icon: 'Connection',
                status: 'placeholder'
            },
            { divider: true },
            {
                title: '同步管理',
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
            // 监控总览
            {
                title: '监控总览',
                path: ROUTES.MONITOR.DASHBOARD,
                icon: 'DataLine',
                children: [
                    {
                        title: '监控大盘',
                        path: ROUTES.MONITOR.DASHBOARD,
                        icon: 'DataAnalysis'
                    },
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
            // 链路与分析
            {
                title: '链路与分析',
                path: ROUTES.MONITOR.TRACES,
                icon: 'TrendCharts',
                children: [
                    {
                        title: '调用链路',
                        path: ROUTES.MONITOR.TRACES,
                        icon: 'Share'
                    },
                    {
                        title: 'Token 统计',
                        path: ROUTES.MONITOR.TOKENS,
                        icon: 'Coin'
                    },
                    {
                        title: '成本分析',
                        path: ROUTES.MONITOR.COSTS,
                        icon: 'Money'
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
                    }
                ]
            },
            { divider: true },
            // 告警与事件
            {
                title: '告警与事件',
                path: ROUTES.MONITOR.ALERTS,
                icon: 'Bell',
                children: [
                    {
                        title: '告警管理',
                        path: ROUTES.MONITOR.ALERTS,
                        icon: 'Bell'
                    },
                    {
                        title: '告警规则配置',
                        path: ROUTES.MONITOR.ALERT_RULES,
                        icon: 'Setting'
                    }
                ]
            },
            { divider: true },
            // 运维操作
            {
                title: '运维操作',
                path: ROUTES.MONITOR.LOGS,
                icon: 'Tools',
                children: [
                    {
                        title: '日志归档',
                        path: ROUTES.MONITOR.LOGS,
                        icon: 'Document'
                    },
                    {
                        title: '系统维护',
                        path: ROUTES.MONITOR.MAINTENANCE,
                        icon: 'Setting'
                    }
                ]
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
            // 组织与权限
            {
                title: '组织与权限',
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
                    },
                    {
                        title: 'API Key',
                        path: ROUTES.SYSTEM.API_KEYS,
                        icon: 'Key'
                    }
                ]
            },
            { divider: true },
            // 平台配置
            {
                title: '平台配置',
                path: ROUTES.SYSTEM.SETTINGS_BASE,
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
                        title: '同步配置',
                        path: ROUTES.SYSTEM.SETTINGS_SYNC,
                        icon: 'Refresh'
                    },
                    {
                        title: '外部集成',
                        path: ROUTES.SYSTEM.SETTINGS_INTEGRATIONS,
                        icon: 'Connection'
                    },
                    {
                        title: 'MCP 服务',
                        path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
                        icon: 'Service'
                    }
                ]
            },
            { divider: true },
            // 模型与资源
            {
                title: '模型与资源',
                path: ROUTES.SYSTEM.MODELS,
                icon: 'Cpu',
                children: [
                    {
                        title: '模型默认参数',
                        path: ROUTES.SYSTEM.SETTINGS_MODEL_DEFAULTS,
                        icon: 'Cpu'
                    },
                    {
                        title: '模型配置',
                        path: ROUTES.SYSTEM.MODELS,
                        icon: 'SetUp'
                    },
                    {
                        title: '定价配置',
                        path: ROUTES.SYSTEM.PRICING,
                        icon: 'PriceTag'
                    },
                    {
                        title: '知识库配置',
                        path: ROUTES.SYSTEM.SETTINGS_KNOWLEDGE,
                        icon: 'Document'
                    },
                    {
                        title: '文件管理',
                        path: ROUTES.SYSTEM.FILES,
                        icon: 'Folder'
                    }
                ]
            },
            { divider: true },
            // 安全与运维
            {
                title: '安全与运维',
                path: ROUTES.SYSTEM.GATEWAY,
                icon: 'Shield',
                children: [
                    {
                        title: '统一网关',
                        path: ROUTES.SYSTEM.GATEWAY,
                        icon: 'Router'
                    },
                    {
                        title: '网关配置',
                        path: ROUTES.SYSTEM.SETTINGS_GATEWAY,
                        icon: 'Connection'
                    },
                    {
                        title: '限流规则',
                        path: ROUTES.SYSTEM.RATE_LIMIT,
                        icon: 'Lightning'
                    },
                    {
                        title: '分布式锁',
                        path: ROUTES.SYSTEM.DISTRIBUTED_LOCK,
                        icon: 'Lock'
                    },
                    {
                        title: '监控配置',
                        path: ROUTES.SYSTEM.SETTINGS_MONITOR,
                        icon: 'Monitor'
                    },
                    {
                        title: '审计日志',
                        path: ROUTES.SYSTEM.AUDIT_LOGS,
                        icon: 'Document'
                    }
                ]
            },
            { divider: true },
            // 支持与维护
            {
                title: '支持与维护',
                path: ROUTES.SYSTEM.HELP_CENTER,
                icon: 'QuestionFilled',
                children: [
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
