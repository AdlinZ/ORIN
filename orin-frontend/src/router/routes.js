/**
 * ORIN 系统路由常量
 * 集中管理所有路由路径，避免硬编码
 *
 * 菜单结构：
 * 1. 智能体 - 智能体管理、协作任务、版本管理、工作流编排
 * 2. 知识中心 - 知识库、知识图谱、同步管理
 * 3. 监控运维 - 监控总览、链路与分析、告警与事件、运维操作
 * 4. 系统管理 - 组织与权限、平台配置、模型与资源、安全与运维、支持与维护
 */

const agentRoutes = {
    ROOT: '/dashboard/applications',
    LIST: '/dashboard/applications/agents',
    ONBOARD: '/dashboard/applications/agents/onboard',
    CONSOLE: '/dashboard/applications/agents/console/:id',
    CHAT_LOGS: '/dashboard/applications/conversations',
    WORKSPACE: '/dashboard/applications/workspace',
    COLLABORATION_DASHBOARD: '/dashboard/applications/collaboration/dashboard',
    COLLABORATION: '/dashboard/applications/collaboration',
    COLLABORATION_TASKS: '/dashboard/applications/collaboration/tasks',
    COLLABORATION_CONFIG: '/dashboard/applications/collaboration/config',
    VERSION_MANAGE: '/dashboard/applications/version',
    TEST_DEBUG: '/dashboard/applications/test',
    SKILLS: '/dashboard/applications/skills',
    MCP: '/dashboard/control/mcp-service',
    TOOLS: '/dashboard/control/mcp-service',
    EXTERNAL_FRAMEWORKS: '/dashboard/control/external-frameworks',
    WORKFLOWS: '/dashboard/applications/workflows',
    WORKFLOW_CREATE: '/dashboard/applications/workflows/create',
    WORKFLOW_EDIT: '/dashboard/applications/workflows/edit/:id',
    WORKFLOW_VISUAL: '/dashboard/applications/workflows/visual',
    WORKFLOW_VISUAL_EDIT: '/dashboard/applications/workflows/visual/:id',
    MODELS: '/dashboard/applications/models',
    MODEL_CONFIG: '/dashboard/applications/models/config',
    MODEL_ADD: '/dashboard/applications/models/add',
    MODEL_EDIT: '/dashboard/applications/models/edit/:id',
}

const knowledgeRoutes = {
    ROOT: '/dashboard/resources',
    LIST: '/dashboard/resources/knowledge',
    CREATE: '/dashboard/resources/knowledge/create',
    DETAIL: '/dashboard/resources/knowledge/detail/:id',
    DOCUMENT_DETAIL: '/dashboard/resources/knowledge/:kbId/document/:docId',
    MEDIA: '/dashboard/resources/media',
    RETRIEVAL_LAB: '/dashboard/resources/embedding-lab',
    EMBEDDING_LAB: '/dashboard/resources/embedding-lab',
    VLM_LAB: '/dashboard/resources/vlm-lab',
    RETRIEVAL_TEST: '/dashboard/resources/retrieval-test',
    INTELLIGENCE: '/dashboard/resources/architecture',
    GRAPH: '/dashboard/resources/graph',
    GRAPH_DETAIL: '/dashboard/resources/graph/:id',
    SYNC: '/dashboard/resources/sync',
    ARCHITECTURE: '/dashboard/resources/architecture',
}

const monitorRoutes = {
    ROOT: '/dashboard/runtime',
    DASHBOARD: '/dashboard/runtime/overview',
    TOKENS: '/dashboard/runtime/metrics',
    COSTS: '/dashboard/runtime/costs',
    LATENCY: '/dashboard/runtime/latency',
    ERRORS: '/dashboard/runtime/errors',
    TRACES: '/dashboard/runtime/traces',
    TRACE_DETAIL: '/dashboard/runtime/traces/:traceId',
    DATAFLOW: '/dashboard/runtime/dataflow/:traceId',
    ALERTS: '/dashboard/runtime/alerts',
    ALERT_RULES: '/dashboard/runtime/alert-rules',
    NOTIFICATIONS: '/dashboard/runtime/alerts',
    TASKS: '/dashboard/runtime/tasks',
    SERVER: '/dashboard/runtime/server',
    LOGS: '/dashboard/runtime/logs',
    MAINTENANCE: '/dashboard/runtime/maintenance',
    VERSION_UPGRADE: '/dashboard/runtime/version-upgrade',
    RATE_LIMIT: '/dashboard/runtime/rate-limit',
}

const systemRoutes = {
    ROOT: '/dashboard/control',
    USERS: '/dashboard/control/users',
    DEPARTMENTS: '/dashboard/control/departments',
    ROLES: '/dashboard/control/roles',
    API_KEYS: '/dashboard/control/api-keys',
    RATE_LIMIT: '/dashboard/control/rate-limit',
    MESSAGES: '/dashboard/control/mail/setup',
    FILES: '/dashboard/control/file-management',
    SETTINGS: '/dashboard/control/system-env',
    SETTINGS_BASE: '/dashboard/control/system-env',
    SETTINGS_MAIL: '/dashboard/control/mail/setup',
    SETTINGS_NOTIFICATIONS: '/dashboard/control/notification-channels',
    SETTINGS_MODEL_DEFAULTS: '/dashboard/applications/models/config',
    SETTINGS_MONITOR: '/dashboard/control/system-env',
    SETTINGS_KNOWLEDGE: '/dashboard/control/knowledge-config',
    SETTINGS_GATEWAY: '/dashboard/control/gateway',
    SETTINGS_SYNC: '/dashboard/control/client-sync',
    SETTINGS_INTEGRATIONS: '/dashboard/control/external-frameworks',
    SETTINGS_MCP_SERVICE: '/dashboard/control/mcp-service',
    AUDIT_LOGS: '/dashboard/control/audit-logs',
    MODELS: '/dashboard/applications/models',
    PRICING: '/dashboard/control/pricing',
    MONITOR_SETTINGS: '/dashboard/control/system-env',
    GATEWAY: '/dashboard/control/gateway',
    DISTRIBUTED_LOCK: '/dashboard/control/distributed-lock',
    EXTERNAL_FRAMEWORKS: '/dashboard/control/external-frameworks',
    DIFY: '/dashboard/control/external-frameworks?tab=dify',
    RAGFLOW: '/dashboard/control/external-frameworks?tab=ragflow',
    AUTOGEN: '/dashboard/control/external-frameworks?tab=autogen',
    CREWAI: '/dashboard/control/external-frameworks?tab=crewai',
    MCP_SERVICE: '/dashboard/control/mcp-service',
    HELP_CENTER: '/dashboard/control/help-center',
    STATISTICS: '/dashboard/control/statistics',
    SYSTEM_MAINTENANCE: '/dashboard/control/maintenance',
}

const controlRoutes = {
    ROOT: systemRoutes.ROOT,
    USERS: systemRoutes.USERS,
    API_KEYS: systemRoutes.API_KEYS,
    FILE_MANAGEMENT: systemRoutes.FILES,
    SYSTEM_ENV: systemRoutes.SETTINGS_MONITOR,
    KNOWLEDGE_CONFIG: systemRoutes.SETTINGS_KNOWLEDGE,
    NOTIFICATION_CHANNELS: systemRoutes.SETTINGS_NOTIFICATIONS,
    CLIENT_SYNC: systemRoutes.SETTINGS_SYNC,
    MAIL: '/dashboard/control/mail',
    MAIL_SETUP: '/dashboard/control/mail/setup',
    MAIL_COMPOSE: '/dashboard/control/mail/compose',
    MAIL_TRACKING: '/dashboard/control/mail/tracking',
    MAIL_CENTER: '/dashboard/control/mail-center',
    PROFILE: '/dashboard/profile',
}

export const ROUTES = {
    HOME: '/dashboard/home',
    AGENTS: agentRoutes,
    KNOWLEDGE: knowledgeRoutes,
    RESOURCES: {
        ROOT: knowledgeRoutes.ROOT,
        KNOWLEDGE: knowledgeRoutes.LIST,
        MEDIA: knowledgeRoutes.MEDIA,
        EMBEDDING_LAB: knowledgeRoutes.EMBEDDING_LAB,
        VLM_LAB: knowledgeRoutes.VLM_LAB,
        GRAPH: knowledgeRoutes.GRAPH,
        ARCHITECTURE: knowledgeRoutes.ARCHITECTURE,
    },
    MONITOR: monitorRoutes,
    SYSTEM: systemRoutes,
    CONTROL: controlRoutes,
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
    '/dashboard/agents/list': ROUTES.AGENTS.LIST,
    '/dashboard/agents/onboard': ROUTES.AGENTS.ONBOARD,
    '/dashboard/agents/console/:id': ROUTES.AGENTS.CONSOLE,
    '/dashboard/agents/chat-logs': ROUTES.AGENTS.CHAT_LOGS,
    '/dashboard/agents/workflows': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/agents/workflows/visual': ROUTES.AGENTS.WORKFLOW_VISUAL,
    '/dashboard/agents/workflows/visual/:id': ROUTES.AGENTS.WORKFLOW_VISUAL_EDIT,
    '/dashboard/applications/agents': ROUTES.AGENTS.LIST,
    '/dashboard/applications/conversations': ROUTES.AGENTS.CHAT_LOGS,
    '/dashboard/applications/collaboration': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/models': ROUTES.AGENTS.MODELS,
    '/dashboard/applications/models/config': ROUTES.AGENTS.MODEL_CONFIG,
    '/dashboard/applications/models/add': ROUTES.AGENTS.MODEL_ADD,
    '/dashboard/applications/models/edit/:id': ROUTES.AGENTS.MODEL_EDIT,

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
    '/dashboard/knowledge/create': ROUTES.KNOWLEDGE.CREATE,
    '/dashboard/knowledge/detail/:id': ROUTES.KNOWLEDGE.DETAIL,
    '/dashboard/knowledge/:kbId/document/:docId': ROUTES.KNOWLEDGE.DOCUMENT_DETAIL,
    '/dashboard/knowledge/media': ROUTES.KNOWLEDGE.MEDIA,
    '/dashboard/knowledge/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/knowledge/graph': ROUTES.KNOWLEDGE.GRAPH,
    '/dashboard/knowledge/graph/:id': ROUTES.KNOWLEDGE.GRAPH_DETAIL,
    '/dashboard/knowledge/sync': ROUTES.KNOWLEDGE.SYNC,
    '/dashboard/knowledge/lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/knowledge/vlm-playground': ROUTES.KNOWLEDGE.VLM_LAB,
    '/dashboard/knowledge/intelligence': ROUTES.KNOWLEDGE.INTELLIGENCE,
    '/dashboard/resources/knowledge': ROUTES.KNOWLEDGE.LIST,
    '/dashboard/resources/knowledge/create': ROUTES.KNOWLEDGE.CREATE,
    '/dashboard/resources/knowledge/detail/:id': ROUTES.KNOWLEDGE.DETAIL,
    '/dashboard/resources/media': ROUTES.KNOWLEDGE.MEDIA,
    '/dashboard/resources/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/resources/graph': ROUTES.KNOWLEDGE.GRAPH,
    '/dashboard/resources/graph/:id': ROUTES.KNOWLEDGE.GRAPH_DETAIL,
    '/dashboard/resources/sync': ROUTES.KNOWLEDGE.SYNC,
    '/dashboard/resources/rag-lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/resources/vlm-lab': ROUTES.KNOWLEDGE.VLM_LAB,
    '/dashboard/resources/architecture': ROUTES.KNOWLEDGE.ARCHITECTURE,

    // 监控运维模块（旧路径）
    '/dashboard/monitor': ROUTES.MONITOR.DASHBOARD,
    '/dashboard/runtime/overview': ROUTES.MONITOR.DASHBOARD,
    '/dashboard/runtime/metrics': ROUTES.MONITOR.TOKENS,
    '/dashboard/runtime/rate-limit': ROUTES.MONITOR.RATE_LIMIT,
    '/dashboard/stats/tokens': ROUTES.MONITOR.TOKENS,
    '/dashboard/stats/costs': ROUTES.MONITOR.COSTS,
    '/dashboard/system/alerts': ROUTES.MONITOR.ALERTS,
    '/trace/:traceId': ROUTES.MONITOR.TRACE_DETAIL,

    // 系统管理模块（旧路径）
    '/dashboard/system/log-config': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/system/audit-logs': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/system/api-keys': ROUTES.SYSTEM.API_KEYS,
    '/dashboard/system/settings': ROUTES.SYSTEM.SETTINGS_BASE,
    '/dashboard/system/monitor-config': ROUTES.SYSTEM.SETTINGS_MONITOR,
    '/dashboard/system/monitor-settings': ROUTES.SYSTEM.SETTINGS_MONITOR,
    '/dashboard/control/users': ROUTES.SYSTEM.USERS,
    '/dashboard/control/audit-logs': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/control/file-management': ROUTES.SYSTEM.FILES,
    '/dashboard/control/system-env': ROUTES.SYSTEM.SETTINGS_MONITOR,
    '/dashboard/control/knowledge-config': ROUTES.SYSTEM.SETTINGS_KNOWLEDGE,
    '/dashboard/control/notification-channels': ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS,
    '/dashboard/control/client-sync': ROUTES.SYSTEM.SETTINGS_SYNC,
    '/dashboard/control/rate-limit': ROUTES.SYSTEM.RATE_LIMIT,
    '/dashboard/control/mail': ROUTES.SYSTEM.MESSAGES,
    '/dashboard/control/departments': ROUTES.SYSTEM.DEPARTMENTS,
    '/dashboard/control/roles': ROUTES.SYSTEM.ROLES,
    '/dashboard/control/gateway': ROUTES.SYSTEM.GATEWAY,
    '/dashboard/control/distributed-lock': ROUTES.SYSTEM.DISTRIBUTED_LOCK,
    '/dashboard/control/external-frameworks': ROUTES.SYSTEM.EXTERNAL_FRAMEWORKS,
    '/dashboard/control/mcp-service': ROUTES.SYSTEM.MCP_SERVICE,
    '/dashboard/control/help-center': ROUTES.SYSTEM.HELP_CENTER,
    '/dashboard/control/statistics': ROUTES.SYSTEM.STATISTICS,
    '/dashboard/control/maintenance': ROUTES.SYSTEM.SYSTEM_MAINTENANCE,
    '/dashboard/control/pricing': ROUTES.SYSTEM.PRICING,
    '/dashboard/control/mail/setup': ROUTES.CONTROL.MAIL_SETUP,
    '/dashboard/control/mail/compose': ROUTES.CONTROL.MAIL_COMPOSE,
    '/dashboard/control/mail/tracking': ROUTES.CONTROL.MAIL_TRACKING,
    '/dashboard/control/mail-center': ROUTES.CONTROL.MAIL_CENTER,

    // 监控运维模块（旧路径补充）
    '/dashboard/runtime/traces': ROUTES.MONITOR.TRACES,
    '/dashboard/runtime/server': ROUTES.MONITOR.SERVER,
    '/dashboard/runtime/tasks': ROUTES.MONITOR.TASKS,
    '/dashboard/monitor/tokens': ROUTES.MONITOR.TOKENS,
    '/dashboard/monitor/costs': ROUTES.MONITOR.COSTS,
    '/dashboard/monitor/latency': ROUTES.MONITOR.LATENCY,
    '/dashboard/monitor/traces': ROUTES.MONITOR.TRACES,
    '/dashboard/monitor/alerts': ROUTES.MONITOR.ALERTS,
    '/dashboard/monitor/alert-rules': ROUTES.MONITOR.ALERT_RULES,
    '/dashboard/monitor/tasks': ROUTES.MONITOR.TASKS,
    '/dashboard/monitor/server': ROUTES.MONITOR.SERVER,
    '/dashboard/monitor/dataflow': ROUTES.MONITOR.DATAFLOW,

    // 模型相关（旧路径）
    '/dashboard/agent/model-list': ROUTES.SYSTEM.MODELS,
    '/dashboard/agent/model-config': ROUTES.SYSTEM.MODELS,
    '/dashboard/applications/models': ROUTES.SYSTEM.MODELS,

    // 缺少 dashboard 前缀的历史地址
    '/system/api-keys': ROUTES.SYSTEM.API_KEYS,
    '/workflow': ROUTES.AGENTS.WORKFLOWS,
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
                    { title: '智能体工作台', path: ROUTES.AGENTS.WORKSPACE },
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
                title: '知识库',
                icon: 'Collection',
                path: ROUTES.KNOWLEDGE.LIST,
                children: [
                    { title: '知识库列表', path: ROUTES.KNOWLEDGE.LIST },
                    { title: '素材管理', path: ROUTES.KNOWLEDGE.MEDIA },
                    { title: '检索测试', path: ROUTES.KNOWLEDGE.RETRIEVAL_TEST },
                ]
            },
            {
                id: 'graph',
                title: '知识图谱',
                icon: 'Connection',
                path: ROUTES.KNOWLEDGE.GRAPH,
            },
            {
                id: 'sync',
                title: '同步管理',
                icon: 'Upload',
                path: ROUTES.KNOWLEDGE.SYNC,
                children: [
                    { title: '同步任务', path: ROUTES.KNOWLEDGE.SYNC },
                    { title: '设备管理', path: ROUTES.KNOWLEDGE.SYNC },
                ]
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
                id: 'overview',
                title: '监控总览',
                icon: 'DataAnalysis',
                path: ROUTES.MONITOR.DASHBOARD,
                children: [
                    { title: '监控大盘', path: ROUTES.MONITOR.DASHBOARD },
                    { title: '服务器监控', path: ROUTES.MONITOR.SERVER },
                    { title: '任务队列', path: ROUTES.MONITOR.TASKS },
                ]
            },
            {
                id: 'analysis',
                title: '链路与分析',
                icon: 'TrendCharts',
                path: '/dashboard/monitor/analysis',
                children: [
                    { title: '调用链路', path: ROUTES.MONITOR.TRACES },
                    { title: 'Token 统计', path: ROUTES.MONITOR.TOKENS },
                    { title: '成本分析', path: ROUTES.MONITOR.COSTS },
                    { title: '时延统计', path: ROUTES.MONITOR.LATENCY },
                    { title: '错误统计', path: ROUTES.MONITOR.ERRORS },
                ]
            },
            {
                id: 'incidents',
                title: '告警与事件',
                icon: 'Bell',
                path: '/dashboard/monitor/incidents',
                children: [
                    { title: '告警管理', path: ROUTES.MONITOR.ALERTS },
                    { title: '告警规则', path: ROUTES.MONITOR.ALERT_RULES },
                ]
            },
            {
                id: 'ops',
                title: '运维操作',
                icon: 'Tools',
                path: '/dashboard/monitor/ops',
                children: [
                    { title: '日志归档', path: ROUTES.MONITOR.LOGS },
                    { title: '系统维护', path: ROUTES.MONITOR.MAINTENANCE },
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
            // 组织与权限（三级）
            {
                id: 'organization',
                title: '组织与权限',
                icon: 'User',
                path: '/dashboard/system/organization',
                children: [
                    { title: '用户管理', path: ROUTES.SYSTEM.USERS },
                    { title: '部门管理', path: ROUTES.SYSTEM.DEPARTMENTS },
                    { title: '角色管理', path: ROUTES.SYSTEM.ROLES },
                    { title: 'API Key', path: ROUTES.SYSTEM.API_KEYS },
                ]
            },
            // 平台配置（三级）
            {
                id: 'platform',
                title: '平台配置',
                icon: 'Tools',
                path: '/dashboard/system/platform',
                children: [
                    { title: '基础设置', path: ROUTES.SYSTEM.SETTINGS_BASE },
                    { title: '通知中心', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS },
                    { title: '同步配置', path: ROUTES.SYSTEM.SETTINGS_SYNC },
                    { title: '外部集成', path: ROUTES.SYSTEM.SETTINGS_INTEGRATIONS },
                    { title: 'MCP 服务', path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE },
                ]
            },
            // 模型与资源（三级）
            {
                id: 'resources',
                title: '模型与资源',
                icon: 'Cpu',
                path: '/dashboard/system/resources',
                children: [
                    { title: '模型默认参数', path: ROUTES.SYSTEM.SETTINGS_MODEL_DEFAULTS },
                    { title: '模型配置', path: ROUTES.SYSTEM.MODELS },
                    { title: '定价配置', path: ROUTES.SYSTEM.PRICING },
                    { title: '知识库配置', path: ROUTES.SYSTEM.SETTINGS_KNOWLEDGE },
                    { title: '文件管理', path: ROUTES.SYSTEM.FILES },
                ]
            },
            // 安全与运维（三级）
            {
                id: 'security-ops',
                title: '安全与运维',
                icon: 'Shield',
                path: '/dashboard/system/security-ops',
                children: [
                    { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY },
                    { title: '限流规则', path: ROUTES.SYSTEM.RATE_LIMIT },
                    { title: '分布式锁', path: ROUTES.SYSTEM.DISTRIBUTED_LOCK },
                    { title: '监控配置', path: ROUTES.SYSTEM.SETTINGS_MONITOR },
                    { title: '审计日志', path: ROUTES.SYSTEM.AUDIT_LOGS },
                ]
            },
            // 支持与维护（三级）
            {
                id: 'support',
                title: '支持与维护',
                icon: 'QuestionFilled',
                path: '/dashboard/system/support',
                children: [
                    { title: '帮助中心', path: ROUTES.SYSTEM.HELP_CENTER },
                    { title: '统计分析', path: ROUTES.SYSTEM.STATISTICS },
                    { title: '系统维护', path: ROUTES.SYSTEM.SYSTEM_MAINTENANCE },
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
