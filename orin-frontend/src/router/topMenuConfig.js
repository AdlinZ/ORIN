import { ROUTES } from './routes'

/**
 * 顶部导航菜单配置
 * 4个一级菜单，按照用户建议的结构组织
 *
 * 菜单结构：
 * 1. 智能体中枢 - 智能体管理、工作流编排、多智能体协作、版本管理与测试
 * 2. 知识与工作流 - 知识库、知识图谱、同步管理
 * 3. 监控与运维 - 监控总览、链路与分析、告警与事件、运维操作
 * 4. 系统与网关 - 组织与权限、平台配置、模型与资源、安全与运维、支持与维护
 */
export const TOP_MENU_CONFIG = [
    // ==================== 1. 智能体中枢 ====================
    {
        id: 'agents',
        title: '智能体中枢',
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
                        icon: 'List',
                        status: 'available'
                    },
                    {
                        title: '智能体接入',
                        path: ROUTES.AGENTS.ONBOARD,
                        icon: 'Plus',
                        status: 'available'
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
                        icon: 'Monitor',
                        status: 'available'
                    },
                    {
                        title: '知识库对话',
                        path: ROUTES.AGENTS.WORKSPACE,
                        icon: 'ChatLineRound',
                        status: 'available'
                    },
                    {
                        title: '会话记录',
                        path: ROUTES.AGENTS.CHAT_LOGS,
                        icon: 'ChatDotRound',
                        status: 'available'
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
                        icon: 'Edit',
                        status: 'available'
                    },
                    {
                        title: '协作任务列表',
                        path: ROUTES.AGENTS.COLLABORATION,
                        icon: 'Avatar',
                        status: 'available'
                    },
                    {
                        title: '协作模式配置',
                        path: ROUTES.AGENTS.COLLABORATION_CONFIG,
                        icon: 'SetUp',
                        status: 'beta'
                    },
                    {
                        title: '协作仪表盘',
                        path: ROUTES.AGENTS.COLLABORATION_DASHBOARD,
                        icon: 'DataAnalysis',
                        status: 'beta'
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
                        icon: 'Star',
                        status: 'available'
                    },
                    {
                        title: 'MCP 管理',
                        path: ROUTES.AGENTS.MCP,
                        icon: 'Connection',
                        status: 'available'
                    },
                    {
                        title: 'Tools 注册',
                        path: ROUTES.AGENTS.TOOLS,
                        icon: 'Tool',
                        status: 'available'
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
                        icon: 'Collection',
                        status: 'available'
                    },
                    {
                        title: '测试与调试',
                        path: ROUTES.AGENTS.TEST_DEBUG,
                        icon: 'Bug',
                        status: 'beta'
                    }
                ]
            }
        ]
    },

    // ==================== 2. 知识与工作流 ====================
    {
        id: 'knowledge',
        title: '知识与工作流',
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
                        icon: 'List',
                        status: 'available'
                    },
                    {
                        title: '文档管理',
                        path: ROUTES.KNOWLEDGE.LIST,
                        icon: 'Document',
                        status: 'available'
                    },
                    {
                        title: '素材管理',
                        path: ROUTES.KNOWLEDGE.MEDIA,
                        icon: 'Picture',
                        status: 'available'
                    }
                ]
            },
            { divider: true },
            {
                title: '知识图谱',
                path: ROUTES.KNOWLEDGE.GRAPH,
                icon: 'Connection',
                status: 'beta'
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
                        icon: 'Clock',
                        status: 'beta'
                    },
                    {
                        title: '设备列表',
                        path: ROUTES.KNOWLEDGE.SYNC,
                        icon: 'Monitor',
                        status: 'planned'
                    }
                ]
            }
        ]
    },

    // ==================== 3. 监控与运维 ====================
    {
        id: 'monitor',
        title: '监控与运维',
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
                        icon: 'DataAnalysis',
                        status: 'available'
                    },
                    {
                        title: '服务器监控',
                        path: ROUTES.MONITOR.SERVER,
                        icon: 'Monitor',
                        status: 'available'
                    },
                    {
                        title: '任务队列',
                        path: ROUTES.MONITOR.TASKS,
                        icon: 'Tickets',
                        status: 'available'
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
                        icon: 'Share',
                        status: 'available'
                    },
                    {
                        title: 'Token 统计',
                        path: ROUTES.MONITOR.TOKENS,
                        icon: 'Coin',
                        status: 'available'
                    },
                    {
                        title: '成本分析',
                        path: ROUTES.MONITOR.COSTS,
                        icon: 'Money',
                        status: 'available'
                    },
                    {
                        title: '时延统计',
                        path: ROUTES.MONITOR.LATENCY,
                        icon: 'Timer',
                        status: 'available'
                    },
                    {
                        title: '错误统计',
                        path: ROUTES.MONITOR.ERRORS,
                        icon: 'Warning',
                        status: 'available'
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
                        icon: 'Bell',
                        status: 'available'
                    },
                    {
                        title: '告警规则配置',
                        path: ROUTES.MONITOR.ALERT_RULES,
                        icon: 'Setting',
                        status: 'beta'
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
                        icon: 'Document',
                        status: 'available'
                    },
                    {
                        title: '系统维护',
                        path: ROUTES.MONITOR.MAINTENANCE,
                        icon: 'Setting',
                        status: 'available'
                    }
                ]
            }
        ]
    },

    // ==================== 4. 系统与网关 ====================
    {
        id: 'system',
        title: '系统与网关',
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
                        icon: 'User',
                        status: 'available'
                    },
                    {
                        title: '部门管理',
                        path: ROUTES.SYSTEM.DEPARTMENTS,
                        icon: 'OfficeBuilding',
                        status: 'available'
                    },
                    {
                        title: '角色管理',
                        path: ROUTES.SYSTEM.ROLES,
                        icon: 'UserFilled',
                        status: 'available'
                    },
                    {
                        title: 'API Key',
                        path: ROUTES.SYSTEM.API_KEYS,
                        icon: 'Key',
                        status: 'available'
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
                        icon: 'Setting',
                        status: 'available'
                    },
                    {
                        title: '重构灰度控制台',
                        path: ROUTES.SYSTEM.REVAMP_ROLLOUT,
                        icon: 'Operation',
                        status: 'beta'
                    },
                    {
                        title: '通知中心',
                        path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS,
                        icon: 'Bell',
                        status: 'available'
                    },
                    {
                        title: '同步配置',
                        path: ROUTES.SYSTEM.SETTINGS_SYNC,
                        icon: 'Refresh',
                        status: 'beta'
                    },
                    {
                        title: '外部集成',
                        path: ROUTES.SYSTEM.SETTINGS_INTEGRATIONS,
                        icon: 'Connection',
                        status: 'beta'
                    },
                    {
                        title: 'MCP 服务',
                        path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
                        icon: 'Service',
                        status: 'available'
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
                        icon: 'Cpu',
                        status: 'available'
                    },
                    {
                        title: '模型配置',
                        path: ROUTES.SYSTEM.MODELS,
                        icon: 'SetUp',
                        status: 'available'
                    },
                    {
                        title: '定价配置',
                        path: ROUTES.SYSTEM.PRICING,
                        icon: 'PriceTag',
                        status: 'available'
                    },
                    {
                        title: '知识库配置',
                        path: ROUTES.SYSTEM.SETTINGS_KNOWLEDGE,
                        icon: 'Document',
                        status: 'available'
                    },
                    {
                        title: '文件管理',
                        path: ROUTES.SYSTEM.FILES,
                        icon: 'Folder',
                        status: 'available'
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
                        icon: 'Router',
                        status: 'available'
                    },
                    {
                        title: '统一 API 文档',
                        path: ROUTES.SYSTEM.UNIFIED_API_DOCS,
                        icon: 'Document',
                        status: 'available'
                    },
                    {
                        title: '限流规则',
                        path: ROUTES.SYSTEM.RATE_LIMIT,
                        icon: 'Lightning',
                        status: 'available'
                    },
                    {
                        title: '分布式锁',
                        path: ROUTES.SYSTEM.DISTRIBUTED_LOCK,
                        icon: 'Lock',
                        status: 'available'
                    },
                    {
                        title: '监控配置',
                        path: ROUTES.SYSTEM.SETTINGS_MONITOR,
                        icon: 'Monitor',
                        status: 'available'
                    },
                    {
                        title: '审计日志',
                        path: ROUTES.SYSTEM.AUDIT_LOGS,
                        icon: 'Document',
                        status: 'available'
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
                        icon: 'QuestionFilled',
                        status: 'available'
                    },
                    {
                        title: '统计分析',
                        path: ROUTES.SYSTEM.STATISTICS,
                        icon: 'DataAnalysis',
                        status: 'available'
                    },
                    {
                        title: '系统维护',
                        path: ROUTES.SYSTEM.SYSTEM_MAINTENANCE,
                        icon: 'Wrench',
                        status: 'available'
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
