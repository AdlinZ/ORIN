/**
 * ORIN 系统路由常量
 * 集中管理所有路由路径，避免硬编码
 *
 * 菜单结构（按产品面拆分）：
 * 1. ORIN 工作台（/workspace）
 *    - 首页：我的概览、最近使用、执行动态
 *    - 智能体：智能体列表、智能体工作台、会话记录
 *    - 工作流：工作流中心、可视化编排、执行记录、多智能体协作
 *    - 知识库：知识中心、知识资产、检索实验
 *    - 扩展能力：Skills、MCP 服务、模型工具
 * 2. ORIN 管理台（/admin）
 *    - 平台总览：运行摘要、资源统计、异常概览
 *    - 组织权限：用户管理、部门管理、角色管理
 *    - AI 基础设施：模型管理、Provider 配置、定价配置、环境配置
 *    - 开放平台：API Key、统一网关、MCP 服务
 *    - 运行运维：服务器监控、任务队列、调用链路、用量统计、性能分析、告警与日志
 *    - 系统治理：通知设置、数据资产、审计日志、系统维护
 * 3. ORIN Chat（/chat）
 *
 * 工作台使用 WorkspaceLayout，管理台使用 AdminLayout，
 * 不再共用 MainLayout。MainLayout 保留为历史 /dashboard/* 兜底壳。
 */

const agentRoutes = {
    ROOT: '/workspace',
    LIST: '/workspace/agents',
    ONBOARD: '/workspace/agents/onboard',
    CONSOLE: '/workspace/agents/console/:id',
    CHAT_LOGS: '/workspace/conversations',
    WORKSPACE: '/workspace/workspace',
    DEVELOPER: '/workspace/developer',
    SKILLS: '/workspace/extensions?tab=skills',
    MCP: '/workspace/extensions?tab=mcp',
    MODEL_TOOLS: '/workspace/extensions?tab=bindings',
    EXTENSIONS: '/workspace/extensions',
    COLLABORATION_WORKFLOWS: '/workspace/collaboration/workflows',
    WORKFLOWS: '/workspace/workflows',
    WORKFLOW_EXECUTION: '/workspace/workflows/execution',
    WORKFLOW_CREATE: '/workspace/workflows/create',
    WORKFLOW_EDIT: '/workspace/workflows/edit/:id',
    WORKFLOW_VISUAL: '/workspace/workflows/visual',
    WORKFLOW_VISUAL_EDIT: '/workspace/workflows/visual/:id',
    MODELS: '/workspace/models',
    MODEL_ADD: '/workspace/models/add',
    MODEL_EDIT: '/workspace/models/edit/:id',
    COLLABORATION_DASHBOARD: '/workspace/collaboration/dashboard',
    COLLABORATION: '/workspace/collaboration',
    PLAYGROUND: '/workspace/playground',
    PLAYGROUND_OVERVIEW: '/workspace/playground/overview',
    PLAYGROUND_RUN: '/workspace/playground/run',
    VERSION_MANAGE: '/workspace/workflows/execution',
    TEST_DEBUG: '/workspace/workflows/execution',
}

const knowledgeRoutes = {
    ROOT: '/workspace/knowledge',
    CENTER: '/workspace/knowledge/center',
    ASSETS: '/workspace/knowledge/assets',
    LIST: '/workspace/knowledge/assets',
    CREATE: '/workspace/knowledge/knowledge/create',
    DETAIL: '/workspace/knowledge/knowledge/detail/:id',
    DOCUMENT_DETAIL: '/workspace/knowledge/knowledge/:kbId/document/:docId',
    RETRIEVAL_LAB: '/workspace/knowledge/retrieval',
    EMBEDDING_LAB: '/workspace/knowledge/retrieval',
    RETRIEVAL_TEST: '/workspace/knowledge/retrieval-test',
    INTELLIGENCE: '/workspace/knowledge/architecture',
    GRAPH: '/workspace/knowledge/assets',
    GRAPH_DETAIL: '/workspace/knowledge/graph/:id',
    SYNC: '/workspace/knowledge/sync',
    ARCHITECTURE: '/workspace/knowledge/architecture',
}

const monitorRoutes = {
    ROOT: '/admin/runtime',
    HOME: '/admin/runtime/overview',
    DASHBOARD: '/admin/runtime/server',
    TOKENS: '/admin/runtime/metrics',
    LATENCY: '/admin/runtime/latency',
    ERRORS: '/admin/runtime/errors',
    TRACES: '/admin/runtime/traces',
    TRACE_DETAIL: '/admin/runtime/traces/:traceId',
    DATAFLOW: '/admin/runtime/dataflow/:traceId',
    ALERTS: '/admin/runtime/alerts',
    AUDIT_LOGS: '/admin/runtime/audit-logs',
    ALERT_RULES: '/admin/runtime/alerts',
    ALERT_RULE_CREATE: '/admin/runtime/alerts/rules/create',
    ALERT_RULE_EDIT: '/admin/runtime/alerts/rules/:id/edit',
    NOTIFICATIONS: '/admin/runtime/alerts',
    TASKS: '/admin/runtime/tasks',
    SERVER: '/admin/runtime/server',
    SERVER_NODE: '/admin/runtime/server/:serverId',
    LOGS: '/admin/runtime/logs',
    MAINTENANCE: '/admin/runtime/maintenance',
    VERSION_UPGRADE: '/admin/runtime/version-upgrade',
    RATE_LIMIT: '/admin/runtime/rate-limit',
}

const systemRoutes = {
    ROOT: '/admin',
    ADMIN_DASHBOARD: '/admin/admin-overview',
    USERS: '/admin/users',
    DEPARTMENTS: '/admin/departments',
    ROLES: '/admin/roles',
    API_KEYS: '/admin/gateway?workspace=access',
    RATE_LIMIT: '/admin/rate-limit',
    MESSAGES: '/admin/notification-channels?tab=overview',
    DATA_ASSETS: '/admin/data-assets',
    FILES: '/admin/data-assets?assetTab=files',
    SETTINGS: '/admin/system-env',
    SETTINGS_BASE: '/admin/system-env',
    SETTINGS_MAIL: '/admin/notification-channels?tab=service',
    SETTINGS_NOTIFICATIONS: '/admin/notification-channels',
    SETTINGS_MODEL_DEFAULTS: '/admin/system-env',
    SETTINGS_MONITOR: '/admin/system-env',
    SETTINGS_GATEWAY: '/admin/gateway',
    UNIFIED_GATEWAY: '/admin/unified-gateway',
    SETTINGS_SYNC: '/admin/data-assets?assetTab=sync&tab=changes',
    SYNC: '/admin/data-assets?assetTab=sync&tab=changes',
    SETTINGS_MCP_SERVICE: '/admin/mcp-service',
    AUDIT_LOGS: '/admin/audit-logs',
    MODELS: '/workspace/models',
    PRICING: '/admin/pricing',
    MONITOR_SETTINGS: '/admin/system-env',
    GATEWAY: '/admin/gateway',
    UNIFIED_API_DOCS: '/admin/unified-api-docs',
    MCP_SERVICE: '/admin/mcp-service',
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
    MAIL: '/admin/notification-channels?tab=overview',
    MAIL_SETUP: '/admin/notification-channels?tab=service',
    MAIL_COMPOSE: '/admin/notification-channels?tab=compose',
    MAIL_TRACKING: '/admin/notification-channels?tab=tracking',
    MAIL_CENTER: '/admin/notification-channels?tab=overview',
    PROFILE: '/dashboard/profile',
}

const mcpRoutes = {
    ROOT: '/dashboard/mcp',
    SERVERS: agentRoutes.MCP,
}

const workspacePaths = {
    ROOT: '/workspace',
    HOME: '/workspace/developer',
    PROFILE: '/workspace/profile',
    AGENT_WORKSPACE: '/workspace/workspace',
    AGENTS: '/workspace/agents',
    CONVERSATIONS: '/workspace/conversations',
    WORKFLOWS: '/workspace/workflows',
    WORKFLOW_VISUAL: '/workspace/workflows/visual',
    WORKFLOW_EXECUTION: '/workspace/workflows/execution',
    COLLABORATION: '/workspace/collaboration/workflows',
    EXTENSIONS: '/workspace/extensions',
    KNOWLEDGE_CENTER: '/workspace/knowledge/center',
    KNOWLEDGE_ASSETS: '/workspace/knowledge/assets',
    KNOWLEDGE_RETRIEVAL: '/workspace/knowledge/retrieval',
}

const adminPaths = {
    ROOT: '/admin',
    HOME: '/admin/admin-overview',
    PROFILE: '/admin/profile',
    USERS: '/admin/users',
    DEPARTMENTS: '/admin/departments',
    MODELS: '/admin/models',
    PRICING: '/admin/pricing',
    ENVIRONMENT: '/admin/system-env',
    NOTIFICATIONS: '/admin/notification-channels',
    DATA_ASSETS: '/admin/data-assets',
    MCP: '/admin/mcp-service',
    GATEWAY: '/admin/gateway',
    RUNTIME: '/admin/runtime',
}

export const ROUTES = {
    SETUP: '/setup',
    CHAT: '/chat',
    CHAT_PROFILE: '/chat/profile',
    WORKSPACE_ROOT: '/workspace',
    WORKSPACE: '/workspace/developer',
    WORKSPACE_PATHS: workspacePaths,
    ADMIN_ROOT: '/admin',
    ADMIN: '/admin/admin-overview',
    ADMIN_PATHS: adminPaths,
    PLATFORM: '/platform',
    PLATFORM_API_KEYS: '/platform/api-keys',
    PLATFORM_DOCS: '/platform/docs',
    PORTAL: '/chat',
    PORTAL_API_KEYS: '/platform/api-keys',
    HOME: '/admin/runtime/overview',
    MCP: mcpRoutes,
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
    REGISTER: '/register',
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
    '/dashboard/applications/workspace': ROUTES.AGENTS.WORKSPACE,
    '/dashboard/applications/developer': ROUTES.AGENTS.DEVELOPER,
    '/dashboard/applications/extensions': ROUTES.AGENTS.EXTENSIONS,
    '/dashboard/applications/playground': ROUTES.AGENTS.PLAYGROUND,
    '/dashboard/applications/playground/overview': ROUTES.AGENTS.PLAYGROUND_OVERVIEW,
    '/dashboard/applications/playground/run': ROUTES.AGENTS.PLAYGROUND_RUN,
    '/dashboard/applications/collaboration': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/collaboration/tasks': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/collaboration/config': ROUTES.AGENTS.COLLABORATION,
    '/dashboard/applications/collaboration/dashboard': ROUTES.AGENTS.COLLABORATION_DASHBOARD,
    '/dashboard/applications/collaboration/workflows': ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
    '/dashboard/applications/playground/workflows': ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
    '/dashboard/applications/version': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/test': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/tools': ROUTES.MCP.SERVERS,
    '/dashboard/applications/mcp': ROUTES.MCP.SERVERS,
    '/dashboard/mcp': ROUTES.MCP.SERVERS,
    '/dashboard/mcp/servers': ROUTES.MCP.SERVERS,
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
    '/dashboard/applications/workflows/visual': ROUTES.AGENTS.WORKFLOW_VISUAL,
    '/dashboard/applications/workflows/visual/:id': ROUTES.AGENTS.WORKFLOW_VISUAL_EDIT,
    '/dashboard/applications/workflows/execution': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/workflows-v2': ROUTES.AGENTS.WORKFLOWS,
    '/dashboard/applications/workflows-v2/canvas': ROUTES.AGENTS.WORKFLOW_VISUAL,
    '/dashboard/applications/workflows-v2/runs': ROUTES.AGENTS.WORKFLOW_EXECUTION,
    '/dashboard/applications/workflows-v2/:id': ROUTES.AGENTS.WORKFLOWS,

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
    '/dashboard/resources/center': ROUTES.KNOWLEDGE.CENTER,
    '/dashboard/resources/assets': ROUTES.KNOWLEDGE.ASSETS,
    '/dashboard/resources/retrieval': ROUTES.KNOWLEDGE.RETRIEVAL_LAB,
    '/dashboard/resources/retrieval-test': ROUTES.KNOWLEDGE.RETRIEVAL_TEST,
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
    '/dashboard/runtime/overview': ROUTES.MONITOR.HOME,
    '/dashboard/runtime/metrics': ROUTES.MONITOR.TOKENS,
    '/dashboard/runtime/latency': ROUTES.MONITOR.LATENCY,
    '/dashboard/runtime/errors': ROUTES.MONITOR.ERRORS,
    '/dashboard/runtime/dataflow/:traceId': ROUTES.MONITOR.DATAFLOW,
    '/dashboard/runtime/server/:serverId': ROUTES.MONITOR.SERVER_NODE,
    '/dashboard/runtime/alerts': ROUTES.MONITOR.ALERTS,
    '/dashboard/runtime/logs': ROUTES.MONITOR.LOGS,
    '/dashboard/runtime/maintenance': ROUTES.MONITOR.MAINTENANCE,
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
    '/dashboard/control/admin-overview': ROUTES.SYSTEM.ADMIN_DASHBOARD,
    '/dashboard/control/api-keys': ROUTES.SYSTEM.API_KEYS,
    '/dashboard/control/audit-logs': ROUTES.SYSTEM.AUDIT_LOGS,
    '/dashboard/control/file-management': ROUTES.SYSTEM.FILES,
    '/dashboard/control/data-assets': ROUTES.SYSTEM.DATA_ASSETS,
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
    '/dashboard/control/statistics': '/admin/statistics',
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
                    { title: '多智能体协同', path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS },
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
                    { title: '扩展管理', path: ROUTES.AGENTS.EXTENSIONS },
                ]
            },
            // 工作流管理
            {
                id: 'workflow',
                title: '工作流中心',
                icon: 'Connection',
                path: ROUTES.AGENTS.WORKFLOWS,
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
