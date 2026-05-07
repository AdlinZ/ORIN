/**
 * ORIN 系统路由常量
 * 集中管理所有路由路径，避免硬编码
 *
 * 菜单结构：
 * 1. 智能体管理 - 智能体列表、智能体工作台、扩展管理
 * 2. 工作流管理 - 工作流设计、执行记录、多智能体编排
 * 3. 知识库管理 - 知识库、知识资产、同步管理
 * 4. 运行监控 - 监控总览、链路与分析、告警与事件、运维操作
 * 5. 系统设置 - 组织权限、平台设置、模型管理、统一网关、支持维护
 */

const agentRoutes = {
    ROOT: '/dashboard/applications',
    LIST: '/dashboard/applications/agents',
    ONBOARD: '/dashboard/applications/agents/onboard',
    CONSOLE: '/dashboard/applications/agents/console/:id',
    CHAT_LOGS: '/dashboard/applications/conversations',
    WORKSPACE: '/dashboard/applications/workspace',
    SKILLS: '/dashboard/applications/skills',
    MCP: '/dashboard/applications/mcp',
    EXTENSIONS: '/dashboard/applications/extensions',
    WORKFLOWS: '/dashboard/applications/workflows',
    WORKFLOW_EXECUTION: '/dashboard/applications/workflows/execution',
    WORKFLOW_CREATE: '/dashboard/applications/workflows/create',
    WORKFLOW_EDIT: '/dashboard/applications/workflows/edit/:id',
    WORKFLOW_VISUAL: '/dashboard/applications/workflows/visual',
    WORKFLOW_VISUAL_EDIT: '/dashboard/applications/workflows/visual/:id',
    MODELS: '/dashboard/applications/models',
    MODEL_ADD: '/dashboard/applications/models/add',
    MODEL_EDIT: '/dashboard/applications/models/edit/:id',
    COLLABORATION_DASHBOARD: '/dashboard/applications/collaboration/dashboard',
    COLLABORATION: '/dashboard/applications/collaboration',
    PLAYGROUND: '/dashboard/applications/playground',
    PLAYGROUND_OVERVIEW: '/dashboard/applications/playground/overview',
    PLAYGROUND_WORKFLOWS: '/dashboard/applications/playground/workflows',
    PLAYGROUND_RUN: '/dashboard/applications/playground/run',
    VERSION_MANAGE: '/dashboard/applications/workflows/execution',
    TEST_DEBUG: '/dashboard/applications/workflows/execution',
}

const knowledgeRoutes = {
    ROOT: '/dashboard/resources',
    CENTER: '/dashboard/resources/center',
    ASSETS: '/dashboard/resources/assets',
    LIST: '/dashboard/resources/assets',
    CREATE: '/dashboard/resources/knowledge/create',
    DETAIL: '/dashboard/resources/knowledge/detail/:id',
    DOCUMENT_DETAIL: '/dashboard/resources/knowledge/:kbId/document/:docId',
    RETRIEVAL_LAB: '/dashboard/resources/retrieval',
    EMBEDDING_LAB: '/dashboard/resources/retrieval',
    RETRIEVAL_TEST: '/dashboard/resources/retrieval-test',
    INTELLIGENCE: '/dashboard/resources/architecture',
    GRAPH: '/dashboard/resources/assets',
    GRAPH_DETAIL: '/dashboard/resources/graph/:id',
    SYNC: '/dashboard/resources/sync',
    ARCHITECTURE: '/dashboard/resources/architecture',
}

const monitorRoutes = {
    ROOT: '/dashboard/runtime',
    HOME: '/dashboard/runtime/overview',
    DASHBOARD: '/dashboard/runtime/server',
    TOKENS: '/dashboard/runtime/metrics',
    LATENCY: '/dashboard/runtime/latency',
    ERRORS: '/dashboard/runtime/errors',
    TRACES: '/dashboard/runtime/traces',
    TRACE_DETAIL: '/dashboard/runtime/traces/:traceId',
    DATAFLOW: '/dashboard/runtime/dataflow/:traceId',
    ALERTS: '/dashboard/runtime/alerts',
    AUDIT_LOGS: '/dashboard/runtime/audit-logs',
    ALERT_RULES: '/dashboard/runtime/alerts',
    ALERT_RULE_CREATE: '/dashboard/runtime/alerts/rules/create',
    ALERT_RULE_EDIT: '/dashboard/runtime/alerts/rules/:id/edit',
    NOTIFICATIONS: '/dashboard/runtime/alerts',
    TASKS: '/dashboard/runtime/tasks',
    SERVER: '/dashboard/runtime/server',
    SERVER_NODE: '/dashboard/runtime/server/:serverId',
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
    API_KEYS: '/dashboard/control/gateway?workspace=access',
    RATE_LIMIT: '/dashboard/control/rate-limit',
    MESSAGES: '/dashboard/control/notification-channels?tab=overview',
    DATA_ASSETS: '/dashboard/control/data-assets',
    FILES: '/dashboard/control/data-assets?assetTab=files',
    SETTINGS: '/dashboard/control/system-env',
    SETTINGS_BASE: '/dashboard/control/system-env',
    SETTINGS_MAIL: '/dashboard/control/notification-channels?tab=service',
    SETTINGS_NOTIFICATIONS: '/dashboard/control/notification-channels',
    SETTINGS_MODEL_DEFAULTS: '/dashboard/control/system-env',
    SETTINGS_MONITOR: '/dashboard/control/system-env',
    SETTINGS_GATEWAY: '/dashboard/control/gateway',
    UNIFIED_GATEWAY: '/dashboard/control/unified-gateway',
    SETTINGS_SYNC: '/dashboard/control/data-assets?assetTab=sync&tab=changes',
    SYNC: '/dashboard/control/data-assets?assetTab=sync&tab=changes',
    SETTINGS_MCP_SERVICE: '/dashboard/control/mcp-service',
    AUDIT_LOGS: '/dashboard/control/audit-logs',
    MODELS: '/dashboard/applications/models',
    PRICING: '/dashboard/control/pricing',
    MONITOR_SETTINGS: '/dashboard/control/system-env',
    GATEWAY: '/dashboard/control/gateway',
    UNIFIED_API_DOCS: '/dashboard/control/unified-api-docs',
    MCP_SERVICE: '/dashboard/control/mcp-service',
    HELP_CENTER: '/unified-docs',
}

const controlRoutes = {
    ROOT: systemRoutes.ROOT,
    USERS: systemRoutes.USERS,
    API_KEYS: systemRoutes.API_KEYS,
    FILE_MANAGEMENT: systemRoutes.FILES,
    DATA_ASSETS: systemRoutes.DATA_ASSETS,
    SYSTEM_ENV: systemRoutes.SETTINGS_MONITOR,
    NOTIFICATION_CHANNELS: systemRoutes.SETTINGS_NOTIFICATIONS,
    CLIENT_SYNC: systemRoutes.SETTINGS_SYNC,
    MAIL: '/dashboard/control/notification-channels?tab=overview',
    MAIL_SETUP: '/dashboard/control/notification-channels?tab=service',
    MAIL_COMPOSE: '/dashboard/control/notification-channels?tab=compose',
    MAIL_TRACKING: '/dashboard/control/notification-channels?tab=tracking',
    MAIL_CENTER: '/dashboard/control/notification-channels?tab=overview',
    PROFILE: '/dashboard/profile',
}

export const ROUTES = {
    PORTAL: '/portal',
    HOME: '/dashboard/runtime/overview',
    AGENTS: agentRoutes,
    KNOWLEDGE: knowledgeRoutes,
    RESOURCES: {
        ROOT: knowledgeRoutes.ROOT,
        CENTER: knowledgeRoutes.CENTER,
        ASSETS: knowledgeRoutes.ASSETS,
        KNOWLEDGE: knowledgeRoutes.LIST,
        EMBEDDING_LAB: knowledgeRoutes.EMBEDDING_LAB,
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
const LEGACY_ROUTE_REDIRECTS_RAW = {
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
    '/dashboard/applications/collaboration/tasks': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/collaboration/config': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/collaboration/dashboard': ROUTES.AGENTS.COLLABORATION_DASHBOARD,
    '/dashboard/applications/version': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/test': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/tools': ROUTES.AGENTS.MCP,
    '/dashboard/control/revamp-rollout': ROUTES.SYSTEM.ROOT,
    '/dashboard/applications/models': ROUTES.AGENTS.MODELS,
    '/dashboard/applications/models/config': ROUTES.SYSTEM.SETTINGS_MONITOR,
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
    '/dashboard/knowledge/center': ROUTES.KNOWLEDGE.CENTER,
    '/dashboard/knowledge/list': ROUTES.KNOWLEDGE.ASSETS,
    '/dashboard/knowledge/create': ROUTES.KNOWLEDGE.CREATE,
    '/dashboard/knowledge/detail/:id': ROUTES.KNOWLEDGE.DETAIL,
    '/dashboard/knowledge/:kbId/document/:docId': ROUTES.KNOWLEDGE.DOCUMENT_DETAIL,
    '/dashboard/knowledge/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/knowledge/graph': ROUTES.KNOWLEDGE.ASSETS,
    '/dashboard/knowledge/graph/:id': ROUTES.KNOWLEDGE.GRAPH_DETAIL,
    '/dashboard/knowledge/sync': ROUTES.SYSTEM.SYNC,
    '/dashboard/knowledge/lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/knowledge/intelligence': ROUTES.KNOWLEDGE.INTELLIGENCE,
    '/dashboard/resources/knowledge': ROUTES.KNOWLEDGE.ASSETS,
    '/dashboard/resources/knowledge/create': ROUTES.KNOWLEDGE.CREATE,
    '/dashboard/resources/knowledge/detail/:id': ROUTES.KNOWLEDGE.DETAIL,
    '/dashboard/resources/embedding-lab': ROUTES.KNOWLEDGE.EMBEDDING_LAB,
    '/dashboard/resources/graph': ROUTES.KNOWLEDGE.ASSETS,
    '/dashboard/resources/graph/:id': ROUTES.KNOWLEDGE.GRAPH_DETAIL,
    '/dashboard/resources/sync': ROUTES.SYSTEM.SYNC,
    '/dashboard/resources/rag-lab': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/resources/architecture': ROUTES.KNOWLEDGE.ARCHITECTURE,

    // 监控运维模块（旧路径）
    '/dashboard/monitor': ROUTES.MONITOR.DASHBOARD,
    '/dashboard/runtime/overview': ROUTES.MONITOR.SERVER,
    '/dashboard/runtime/metrics': ROUTES.MONITOR.TOKENS,
    '/dashboard/runtime/rate-limit': ROUTES.MONITOR.RATE_LIMIT,
    '/dashboard/stats/tokens': ROUTES.MONITOR.TOKENS,
    '/dashboard/stats/costs': ROUTES.SYSTEM.PRICING,
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
    '/dashboard/control/knowledge-config': ROUTES.SYSTEM.SETTINGS_MONITOR,
    '/dashboard/control/notification-channels': ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS,
    '/dashboard/control/client-sync': ROUTES.SYSTEM.SETTINGS_SYNC,
    '/dashboard/control/rate-limit': ROUTES.SYSTEM.GATEWAY,
    '/dashboard/control/mail': ROUTES.SYSTEM.MESSAGES,
    '/dashboard/control/departments': ROUTES.SYSTEM.DEPARTMENTS,
    '/dashboard/control/roles': ROUTES.SYSTEM.ROLES,
    '/dashboard/control/gateway': ROUTES.SYSTEM.GATEWAY,
    '/dashboard/control/unified-gateway': ROUTES.SYSTEM.GATEWAY,
    '/dashboard/control/external-frameworks': ROUTES.SYSTEM.SETTINGS_BASE,
    '/dashboard/control/mcp-service': ROUTES.SYSTEM.MCP_SERVICE,
    '/dashboard/control/help-center': ROUTES.SYSTEM.HELP_CENTER,
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
    '/dashboard/monitor/costs': ROUTES.SYSTEM.PRICING,
    '/dashboard/monitor/latency': ROUTES.MONITOR.LATENCY,
    '/dashboard/monitor/traces': ROUTES.MONITOR.TRACES,
    '/dashboard/monitor/alerts': ROUTES.MONITOR.ALERTS,
    '/dashboard/monitor/alert-rules': ROUTES.MONITOR.ALERTS,
    '/dashboard/runtime/alert-rules': ROUTES.MONITOR.ALERTS,
    '/dashboard/monitor/tasks': ROUTES.MONITOR.TASKS,
    '/dashboard/monitor/server': ROUTES.MONITOR.SERVER,
    '/dashboard/monitor/dataflow': ROUTES.MONITOR.DATAFLOW,

    // 模型相关（旧路径）
    '/dashboard/agent/model-list': ROUTES.SYSTEM.MODELS,
    '/dashboard/agent/model-config': ROUTES.SYSTEM.MODELS,

    // 缺少 dashboard 前缀的历史地址
    '/system/api-keys': ROUTES.SYSTEM.API_KEYS,
    '/workflow': ROUTES.AGENTS.WORKFLOWS,

    // 首页重定向
    '/dashboard/home': ROUTES.HOME,
    '/dashboard/runtime/home': ROUTES.HOME,
}

function buildLegacyRedirects(rawMap) {
    const deduped = new Map()
    for (const [from, to] of Object.entries(rawMap)) {
        if (!from || !to || from === to) {
            continue
        }
        if (!deduped.has(from)) {
            deduped.set(from, to)
        }
    }
    return Object.fromEntries(deduped)
}

// 清理重定向噪音：过滤掉自重定向与空映射，统一输出稳定映射表
export const LEGACY_ROUTE_REDIRECTS = buildLegacyRedirects(LEGACY_ROUTE_REDIRECTS_RAW)

// ==================== 侧边栏菜单配置 ====================
// 支持二级和三级菜单
export const SIDEBAR_MENU_CONFIG = [
    // ==================== 1. 智能体管理 ====================
    {
        id: 'agents',
        title: '智能体管理',
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
                ]
            },
            // 扩展管理（三级）
            {
                id: 'capability',
                title: '扩展管理',
                icon: 'MagicStick',
                path: '/dashboard/agents/capability',
                children: [
                    { title: '技能管理', path: ROUTES.AGENTS.SKILLS },
                    { title: 'MCP 服务', path: ROUTES.AGENTS.MCP },
                ]
            },
            // 工作流设计
            {
                id: 'workflow',
                title: '工作流设计',
                icon: 'Connection',
                path: ROUTES.AGENTS.WORKFLOWS,
            },
            {
                id: 'workflow-execution',
                title: '工作流执行',
                icon: 'VideoPlay',
                path: ROUTES.AGENTS.WORKFLOW_EXECUTION,
            },
            // Agent Playground
            {
                id: 'playground-workflows',
                title: '多智能体编排',
                icon: 'Connection',
                path: ROUTES.AGENTS.PLAYGROUND_WORKFLOWS,
            },
        ],
    },

    // ==================== 2. 知识库管理 ====================
    {
        id: 'knowledge',
        title: '知识库管理',
        icon: 'Reading',
        color: '#8b5cf6',
        path: ROUTES.KNOWLEDGE.ROOT,
        redirect: ROUTES.KNOWLEDGE.CENTER,
        children: [
            {
                id: 'knowledge-center',
                title: '知识检索',
                icon: 'Reading',
                path: ROUTES.KNOWLEDGE.CENTER,
            },
            {
                id: 'knowledge-assets',
                title: '知识资产',
                icon: 'Collection',
                path: ROUTES.KNOWLEDGE.ASSETS,
            },
        ],
    },

    // ==================== 3. 运行监控 ====================
    {
        id: 'monitor',
        title: '运行监控',
        icon: 'Monitor',
        color: '#f59e0b',
        path: ROUTES.MONITOR.ROOT,
        redirect: ROUTES.HOME,
        children: [
            {
                id: 'overview',
                title: '监控总览',
                icon: 'DataAnalysis',
                path: ROUTES.HOME,
                children: [
                    { title: '监控总览', path: ROUTES.HOME },
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
                    { title: '时延统计', path: ROUTES.MONITOR.LATENCY },
                    { title: '错误统计', path: ROUTES.MONITOR.ERRORS },
                ]
            },
            {
                id: 'incidents',
                title: '告警与事件',
                icon: 'Bell',
                path: ROUTES.MONITOR.ALERTS,
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

    // ==================== 4. 系统设置 ====================
    {
        id: 'system',
        title: '系统设置',
        icon: 'Setting',
        color: '#64748b',
        path: ROUTES.SYSTEM.ROOT,
        redirect: ROUTES.SYSTEM.USERS,
        requiresAdmin: true,
        children: [
            // 组织权限（三级）
            {
                id: 'organization',
                title: '组织权限',
                icon: 'User',
                path: '/dashboard/system/organization',
                children: [
                    { title: '用户管理', path: ROUTES.SYSTEM.USERS },
                    { title: '部门管理', path: ROUTES.SYSTEM.DEPARTMENTS },
                    { title: '角色管理', path: ROUTES.SYSTEM.ROLES },
                ]
            },
            // 平台设置（三级）
            {
                id: 'platform',
                title: '平台设置',
                icon: 'Tools',
                path: '/dashboard/system/platform',
                children: [
                    { title: '环境配置', path: ROUTES.SYSTEM.SETTINGS_BASE },
                    { title: '通知设置', path: ROUTES.SYSTEM.SETTINGS_NOTIFICATIONS },
                    { title: '数据资产', path: ROUTES.SYSTEM.DATA_ASSETS },
                    { title: 'MCP 服务', path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE },
                ]
            },
            // 模型与文件（三级）
            {
                id: 'resources',
                title: '模型与文件',
                icon: 'Cpu',
                path: '/dashboard/system/resources',
                children: [
                    { title: '模型管理', path: ROUTES.SYSTEM.MODELS },
                    { title: '定价配置', path: ROUTES.SYSTEM.PRICING },
                ]
            },
            // 网关与审计（三级）
            {
                id: 'security-ops',
                title: '网关与审计',
                icon: 'Shield',
                path: '/dashboard/system/security-ops',
                children: [
                    { title: '统一网关', path: ROUTES.SYSTEM.GATEWAY },
                    { title: '监控配置', path: ROUTES.SYSTEM.SETTINGS_MONITOR },
                    { title: '审计日志', path: ROUTES.SYSTEM.AUDIT_LOGS },
                ]
            },
            // 支持维护（三级）
            {
                id: 'support',
                title: '支持维护',
                icon: 'QuestionFilled',
                path: '/dashboard/system/support',
                children: [
                    { title: '帮助中心', path: ROUTES.SYSTEM.HELP_CENTER },
                    { title: '统计分析', path: ROUTES.SYSTEM.STATISTICS },
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
