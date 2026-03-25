/**
 * ORIN 系统路由常量
 * 集中管理所有路由路径，避免硬编码
 *
 * 菜单结构：
 * 1. 智能体 - 智能体管理、协作任务、版本管理、工作流编排
 * 2. 知识中心 - 知识库、素材库、实验室、端侧同步
 * 3. 监控运维 - 监控大盘、统计分析、调用链路、告警中心、运维工具
 * 4. 系统管理 - 用户权限、认证鉴权、外部框架集成、帮助中心、系统维护
 */

// ==================== 主要路由 ====================
export const ROUTES = {
    // 首页
    HOME: '/dashboard/home',

    // ==================== 智能体模块 ====================
    AGENTS: {
        ROOT: '/dashboard/agents',
        // 智能体管理（二级）
        LIST: '/dashboard/agents/list',
        ONBOARD: '/dashboard/agents/onboard',
        CONSOLE: '/dashboard/agents/console/:id',
        // 会话管理（二级）
        CHAT_LOGS: '/dashboard/agents/chat-logs',
        // 协作任务（二级）
        COLLABORATION: '/dashboard/agents/collaboration',
        COLLABORATION_TASKS: '/dashboard/agents/collaboration/tasks',
        COLLABORATION_CONFIG: '/dashboard/agents/collaboration/config',
        // 版本管理与测试（二级）
        VERSION_MANAGE: '/dashboard/agents/version',
        TEST_DEBUG: '/dashboard/agents/test',
        // 能力扩展（二级）
        SKILLS: '/dashboard/agents/skills',
        MCP: '/dashboard/agents/mcp',
        TOOLS: '/dashboard/agents/tools',
        EXTERNAL_FRAMEWORKS: '/dashboard/agents/external-frameworks',
        // 工作流编排（二级）
        WORKFLOWS: '/dashboard/agents/workflows',
    },

    // ==================== 知识中心模块 ====================
    KNOWLEDGE: {
        ROOT: '/dashboard/knowledge',
        // 知识库管理（二级）
        LIST: '/dashboard/knowledge/list',
        CREATE: '/dashboard/knowledge/create',
        DETAIL: '/dashboard/knowledge/detail/:id',
        // 素材库（二级）
        MEDIA: '/dashboard/knowledge/media',
        // 实验室（二级）
        RETRIEVAL_LAB: '/dashboard/knowledge/retrieval-lab',
        EMBEDDING_LAB: '/dashboard/knowledge/embedding-lab',
        VLM_LAB: '/dashboard/knowledge/vlm-lab',
        // 智力资产
        INTELLIGENCE: '/dashboard/knowledge/intelligence',
        // 知识图谱（LightRAG + Neo4j 可视化）
        GRAPH: '/dashboard/knowledge/graph',
        // 端侧同步（二级）
        SYNC: '/dashboard/knowledge/sync',
    },

    // ==================== 监控运维模块 ====================
    MONITOR: {
        ROOT: '/dashboard/monitor',
        // 监控大盘（二级）
        DASHBOARD: '/dashboard/monitor/dashboard',
        // 统计分析（二级）
        TOKENS: '/dashboard/monitor/tokens',
        LATENCY: '/dashboard/monitor/latency',
        ERRORS: '/dashboard/monitor/errors',
        // 调用链路（二级）
        TRACES: '/dashboard/monitor/traces',
        DATAFLOW: '/dashboard/monitor/dataflow',
        // 告警中心（二级）
        ALERTS: '/dashboard/monitor/alerts',
        ALERT_RULES: '/dashboard/monitor/alert-rules',
        NOTIFICATIONS: '/dashboard/monitor/notifications',
        // 运维工具（二级）
        TASKS: '/dashboard/monitor/tasks',
        SERVER: '/dashboard/monitor/server',
        LOGS: '/dashboard/monitor/logs',
        VERSION_UPGRADE: '/dashboard/monitor/version-upgrade',
    },

    // ==================== 系统管理模块 ====================
    SYSTEM: {
        ROOT: '/dashboard/system',
        // 用户权限（二级）
        USERS: '/dashboard/system/users',
        DEPARTMENTS: '/dashboard/system/departments',
        ROLES: '/dashboard/system/roles',
        // 认证鉴权（二级）
        API_KEYS: '/dashboard/system/api-keys',
        RATE_LIMIT: '/dashboard/system/rate-limit',
        // 消息中心（二级）
        MESSAGES: '/dashboard/system/messages',
        // 文件管理（二级）
        FILES: '/dashboard/system/files',
        // 系统设置（二级）
        SETTINGS: '/dashboard/system/settings',
        AUDIT_LOGS: '/dashboard/system/audit-logs',
        // 配置中心（二级）
        MODELS: '/dashboard/system/models',
        PRICING: '/dashboard/system/pricing',
        // 系统环境配置
        MONITOR_SETTINGS: '/dashboard/system/monitor-settings',
        // 网关与分布式
        GATEWAY: '/dashboard/system/gateway',
        DISTRIBUTED_LOCK: '/dashboard/system/distributed-lock',
        // 外部框架集成（二级）
        EXTERNAL_FRAMEWORKS: '/dashboard/system/external-frameworks',
        DIFY: '/dashboard/system/external-frameworks/dify',
        RAGFLOW: '/dashboard/system/external-frameworks/ragflow',
        AUTOGEN: '/dashboard/system/external-frameworks/autogen',
        CREWAI: '/dashboard/system/external-frameworks/crewai',
        // MCP服务管理（二级）
        MCP_SERVICE: '/dashboard/system/mcp-service',
        // 帮助中心（二级）
        HELP_CENTER: '/dashboard/system/help-center',
        // 系统维护（二级）
        SYSTEM_MAINTENANCE: '/dashboard/system/maintenance',
    },

    // 个人中心
    PROFILE: '/dashboard/profile',
    LOGIN: '/login',
}

// ==================== 旧路由重定向映射表 ====================
export const LEGACY_ROUTE_REDIRECTS = {
    // 智能体模块（旧路径）
    '/dashboard/agent/list': ROUTES.AGENTS.LIST,
    '/dashboard/agent/chat-history': ROUTES.AGENTS.CHAT_LOGS,
    '/dashboard/agent/conversation-logs': ROUTES.AGENTS.CHAT_LOGS,
    '/dashboard/agent/onboard': ROUTES.AGENTS.ONBOARD,
    '/dashboard/agent/console': ROUTES.AGENTS.CONSOLE,
    '/dashboard/applications/agents': ROUTES.AGENTS.LIST,
    '/dashboard/applications/conversations': ROUTES.AGENTS.CHAT_LOGS,
    '/dashboard/applications/collaboration': ROUTES.AGENTS.COLLABORATION,

    // 技能模块（旧路径）
    '/dashboard/skill/management': ROUTES.AGENTS.SKILLS,
    '/dashboard/applications/skills': ROUTES.AGENTS.SKILLS,

    // 工作流模块（旧路径）
    '/dashboard/workflow/list': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/workflow/management': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/workflow/visual': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/workflow/visual/:id': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/applications/workflows': ROUTES.AGENTS.WORKFLOWS,

    // 知识中心模块（旧路径）
    '/dashboard/knowledge/list': ROUTES.KNOWLEDGE.LIST,
    '/dashboard/knowledge/media': ROUTES.KNOWLEDGE.MEDIA,
    '/dashboard/knowledge/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/knowledge/lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/knowledge/vlm-playground': ROUTES.KNOWLEDGE.VLM_LAB,
    '/dashboard/knowledge/intelligence': ROUTES.KNOWLEDGE.INTELLIGENCE,
    '/dashboard/resources/knowledge': ROUTES.KNOWLEDGE.LIST,
    '/dashboard/resources/media': ROUTES.KNOWLEDGE.MEDIA,
    '/dashboard/resources/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/resources/rag-lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/resources/vlm-lab': ROUTES.KNOWLEDGE.VLM_LAB,
    '/dashboard/resources/architecture': ROUTES.KNOWLEDGE.LIST,

    // 监控运维模块（旧路径）
    '/dashboard/monitor': ROUTES.MONITOR.DASHBOARD,
    '/dashboard/runtime/overview': ROUTES.MONITOR.DASHBOARD,
    '/dashboard/runtime/metrics': ROUTES.MONITOR.TOKENS,
    '/dashboard/stats/tokens': ROUTES.MONITOR.TOKENS,
    '/dashboard/system/alerts': ROUTES.MONITOR.ALERTS,

    // 系统管理模块（旧路径）
    '/dashboard/system/log-config': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/system/audit-logs': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/system/api-keys': ROUTES.SYSTEM.API_KEYS,
    '/dashboard/system/monitor-config': ROUTES.SYSTEM.SETTINGS,
    '/dashboard/control/users': ROUTES.SYSTEM.USERS,
    '/dashboard/control/audit-logs': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/control/file-management': ROUTES.SYSTEM.FILES,
    '/dashboard/control/system-env': ROUTES.SYSTEM.MONITOR_SETTINGS,
    '/dashboard/control/knowledge-config': ROUTES.SYSTEM.SETTINGS,
    '/dashboard/control/notification-channels': ROUTES.MONITOR.NOTIFICATIONS,
    '/dashboard/control/client-sync': ROUTES.KNOWLEDGE.SYNC,
    '/dashboard/control/rate-limit': ROUTES.SYSTEM.RATE_LIMIT,
    '/dashboard/control/mail': ROUTES.SYSTEM.MESSAGES,

    // 监控运维模块（旧路径补充）
    '/dashboard/runtime/traces': ROUTES.MONITOR.TRACES,
    '/dashboard/runtime/server': ROUTES.MONITOR.SERVER,
    '/dashboard/runtime/tasks': ROUTES.MONITOR.TASKS,
    '/dashboard/monitor/tokens': ROUTES.MONITOR.TOKENS,
    '/dashboard/monitor/latency': ROUTES.MONITOR.LATENCY,
    '/dashboard/monitor/traces': ROUTES.MONITOR.TRACES,
    '/dashboard/monitor/alerts': ROUTES.MONITOR.ALERTS,
    '/dashboard/monitor/tasks': ROUTES.MONITOR.TASKS,
    '/dashboard/monitor/server': ROUTES.MONITOR.SERVER,
    '/dashboard/monitor/dataflow': ROUTES.MONITOR.DATAFLOW,

    // 模型相关（旧路径）
    '/dashboard/agent/model-list': ROUTES.SYSTEM.MODELS,
    '/dashboard/agent/model-config': ROUTES.SYSTEM.MODELS,
    '/dashboard/applications/models': ROUTES.SYSTEM.MODELS,
}

// ==================== 侧边栏菜单配置 ====================
// 支持二级和三级菜单
export const SIDEBAR_MENU_CONFIG = [
    // ==================== 1. 智能体 ====================
    {
        id: 'agents',
        title: '智能体',
        icon: 'Robot',
        color: '#155eef',
        path: ROUTES.AGENTS.ROOT,
        redirect: ROUTES.AGENTS.LIST,
        children: [
            // 智能体管理（三级）
            {
                id: 'agent-manage',
                title: '智能体管理',
                icon: 'Grid',
                path: '/dashboard/agents/manage',
                children: [
                    { title: '智能体列表', path: ROUTES.AGENTS.LIST },
                    { title: '智能体接入', path: ROUTES.AGENTS.ONBOARD },
                    { title: '智能体控制台', path: ROUTES.AGENTS.CONSOLE },
                ]
            },
            // 会话管理（三级）
            {
                id: 'session-manage',
                title: '会话管理',
                icon: 'ChatDotRound',
                path: '/dashboard/agents/session',
                children: [
                    { title: '会话记录', path: ROUTES.AGENTS.CHAT_LOGS },
                    { title: '协作任务', path: ROUTES.AGENTS.COLLABORATION },
                ]
            },
            // 能力扩展（三级）
            {
                id: 'capability',
                title: '能力扩展',
                icon: 'MagicStick',
                path: '/dashboard/agents/capability',
                children: [
                    { title: '技能管理', path: ROUTES.AGENTS.SKILLS },
                    { title: 'MCP 管理', path: ROUTES.AGENTS.MCP },
                    { title: 'Tools 注册', path: ROUTES.AGENTS.TOOLS },
                    { title: '外部框架', path: ROUTES.AGENTS.EXTERNAL_FRAMEWORKS },
                ]
            },
            // 工作流编排
            {
                id: 'workflow',
                title: '工作流编排',
                icon: 'Connection',
                path: ROUTES.AGENTS.WORKFLOWS,
            },
        ],
    },

    // ==================== 2. 知识中心 ====================
    {
        id: 'knowledge',
        title: '知识中心',
        icon: 'Reading',
        color: '#8b5cf6',
        path: ROUTES.KNOWLEDGE.ROOT,
        redirect: ROUTES.KNOWLEDGE.LIST,
        children: [
            {
                id: 'kb-manage',
                title: '知识库管理',
                icon: 'Collection',
                path: ROUTES.KNOWLEDGE.LIST,
            },
            {
                id: 'media',
                title: '素材库',
                icon: 'Picture',
                path: ROUTES.KNOWLEDGE.MEDIA,
            },
            // 实验室（三级）
            {
                id: 'labs',
                title: '实验室',
                icon: 'Cpu',
                redirect: ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
                children: [
                    { title: '检索实验室', path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB },
                    { title: '嵌入实验室', path: ROUTES.KNOWLEDGE.EMBEDDING_LAB },
                    { title: '视觉实验室', path: ROUTES.KNOWLEDGE.VLM_LAB },
                ]
            },
            {
                id: 'intelligence',
                title: '智力资产',
                icon: 'Brain',
                path: ROUTES.KNOWLEDGE.INTELLIGENCE,
            },
            {
                id: 'sync',
                title: '端侧同步',
                icon: 'Upload',
                path: ROUTES.KNOWLEDGE.SYNC,
            },
        ],
    },

    // ==================== 3. 监控运维 ====================
    {
        id: 'monitor',
        title: '监控运维',
        icon: 'Monitor',
        color: '#f59e0b',
        path: ROUTES.MONITOR.ROOT,
        redirect: ROUTES.MONITOR.DASHBOARD,
        children: [
            {
                id: 'dashboard',
                title: '监控大盘',
                icon: 'DataAnalysis',
                path: ROUTES.MONITOR.DASHBOARD,
            },
            // 统计分析（三级）
            {
                id: 'stats',
                title: '统计分析',
                icon: 'TrendCharts',
                path: '/dashboard/monitor/stats',
                children: [
                    { title: 'Token 统计', path: ROUTES.MONITOR.TOKENS },
                    { title: '时延统计', path: ROUTES.MONITOR.LATENCY },
                    { title: '错误统计', path: ROUTES.MONITOR.ERRORS },
                ]
            },
            {
                id: 'traces',
                title: '调用链路',
                icon: 'Share',
                path: ROUTES.MONITOR.TRACES,
            },
            // 告警中心（三级）
            {
                id: 'alerts',
                title: '告警中心',
                icon: 'Bell',
                path: '/dashboard/monitor/alerts',
                children: [
                    { title: '告警管理', path: ROUTES.MONITOR.ALERTS },
                    { title: '告警规则', path: ROUTES.MONITOR.ALERT_RULES },
                    { title: '通知渠道', path: ROUTES.MONITOR.NOTIFICATIONS },
                ]
            },
            // 运维工具（三级）
            {
                id: 'ops',
                title: '运维工具',
                icon: 'Tools',
                path: '/dashboard/monitor/ops',
                children: [
                    { title: '任务队列', path: ROUTES.MONITOR.TASKS },
                    { title: '服务器监控', path: ROUTES.MONITOR.SERVER },
                    { title: '日志归档', path: ROUTES.MONITOR.LOGS },
                    { title: '版本升级', path: ROUTES.MONITOR.VERSION_UPGRADE },
                ]
            },
        ],
    },

    // ==================== 4. 系统管理 ====================
    {
        id: 'system',
        title: '系统管理',
        icon: 'Setting',
        color: '#64748b',
        path: ROUTES.SYSTEM.ROOT,
        redirect: ROUTES.SYSTEM.USERS,
        requiresAdmin: true,
        children: [
            // 用户权限（三级）
            {
                id: 'auth',
                title: '用户权限',
                icon: 'User',
                path: '/dashboard/system/auth',
                children: [
                    { title: '用户管理', path: ROUTES.SYSTEM.USERS },
                    { title: '部门权限', path: ROUTES.SYSTEM.DEPARTMENTS },
                    { title: '角色管理', path: ROUTES.SYSTEM.ROLES },
                ]
            },
            // 认证鉴权（三级）
            {
                id: 'security',
                title: '认证鉴权',
                icon: 'Key',
                path: '/dashboard/system/security',
                children: [
                    { title: 'API Key', path: ROUTES.SYSTEM.API_KEYS },
                    { title: '限流配置', path: ROUTES.SYSTEM.RATE_LIMIT },
                ]
            },
            {
                id: 'messages',
                title: '消息中心',
                icon: 'Message',
                path: ROUTES.SYSTEM.MESSAGES,
            },
            {
                id: 'files',
                title: '文件管理',
                icon: 'Folder',
                path: ROUTES.SYSTEM.FILES,
            },
            // 系统设置（三级）
            {
                id: 'settings',
                title: '系统设置',
                icon: 'Tools',
                path: '/dashboard/system/settings',
                children: [
                    { title: '系统配置', path: ROUTES.SYSTEM.SETTINGS },
                    { title: '审计日志', path: ROUTES.SYSTEM.AUDIT_LOGS },
                    { title: '模型配置', path: ROUTES.SYSTEM.MODELS },
                    { title: '定价配置', path: ROUTES.SYSTEM.PRICING },
                ]
            },
            // 网关与分布式（三级）
            {
                id: 'gateway',
                title: '网关与分布式',
                icon: 'Router',
                path: '/dashboard/system/gateway',
                children: [
                    { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY },
                    { title: '分布式锁', path: ROUTES.SYSTEM.DISTRIBUTED_LOCK },
                ]
            },
        ],
    },
]

// ==================== 面包屑生成辅助函数 ====================
/**
 * 根据路由路径生成面包屑
 * @param {string} path - 当前路由路径
 * @returns {Array} 面包屑数组
 */
export function generateBreadcrumbs(path) {
    const breadcrumbs = [{ title: '首页', path: ROUTES.HOME }]

    // 查找匹配的菜单项
    for (const menu of SIDEBAR_MENU_CONFIG) {
        // 检查是否是该一级菜单的子路径
        const isInMenu = path.startsWith(menu.path.replace('/dashboard/', '/dashboard/')) ||
            menu.children?.some(child => {
                // 二级菜单（直接子节点）
                if (child.path && path.startsWith(child.path)) return true
                // 三级菜单
                if (child.children) {
                    return child.children.some(grandChild => path.startsWith(grandChild.path))
                }
                return false
            })

        if (isInMenu) {
            breadcrumbs.push({ title: menu.title, path: menu.path })

            // 查找二级菜单
            const child = menu.children?.find(c => {
                if (c.path && path.startsWith(c.path)) return true
                if (c.children) {
                    return c.children.some(grandChild => path.startsWith(grandChild.path))
                }
                return false
            })

            if (child) {
                breadcrumbs.push({ title: child.title, path: child.path || menu.path })

                // 查找三级菜单
                if (child.children) {
                    const grandChild = child.children.find(gc => path.startsWith(gc.path))
                    if (grandChild) {
                        breadcrumbs.push({ title: grandChild.title, path: grandChild.path })
                    }
                }
            }
            break
        }
    }

    return breadcrumbs
}
