/**
 * ORIN 系统路由常量
 * 集中管理所有路由路径，避免硬编码
 */

// ==================== 主要路由 ====================
export const ROUTES = {
    // 首页
    HOME: '/dashboard/home',

    // 应用模块
    APPLICATIONS: {
        ROOT: '/dashboard/applications',
        AGENTS: '/dashboard/applications/agents',
        CONVERSATIONS: '/dashboard/applications/conversations',
        MODELS: '/dashboard/applications/models',
        SKILLS: '/dashboard/applications/skills',
        WORKFLOWS: '/dashboard/applications/workflows',
        WORKFLOW_CREATE: '/dashboard/applications/workflows/create',
        WORKFLOW_EDIT: '/dashboard/applications/workflows/edit',
        WORKFLOW_VISUAL: '/dashboard/applications/workflows/visual',
        AGENT_CONSOLE: '/dashboard/applications/agents/console',
        AGENT_ONBOARD: '/dashboard/applications/agents/onboard',
    },

    // 运行模块
    RUNTIME: {
        ROOT: '/dashboard/runtime',
        OVERVIEW: '/dashboard/runtime/overview',
        METRICS: '/dashboard/runtime/metrics',
        TRACES: '/dashboard/runtime/traces',
        ALERTS: '/dashboard/runtime/alerts',
    },

    // 资源模块
    RESOURCES: {
        ROOT: '/dashboard/resources',
        KNOWLEDGE: '/dashboard/resources/knowledge',
        KNOWLEDGE_CREATE: '/dashboard/resources/knowledge/create',
        MEDIA: '/dashboard/resources/media',
        EMBEDDING_LAB: '/dashboard/resources/embedding-lab',
        RAG_LAB: '/dashboard/resources/rag-lab',
        VLM_LAB: '/dashboard/resources/vlm-lab',
        ARCHITECTURE: '/dashboard/resources/architecture',
    },

    // 控制模块
    CONTROL: {
        ROOT: '/dashboard/control',
        USERS: '/dashboard/control/users',
        LOG_CONFIG: '/dashboard/control/log-config',
        AUDIT_LOGS: '/dashboard/control/audit-logs',
        API_MANAGEMENT: '/dashboard/control/api-management',
        PRICING: '/dashboard/control/pricing',
        MONITOR_CONFIG: '/dashboard/control/monitor-config',
    },

    // 其他
    PROFILE: '/dashboard/profile',
    LOGIN: '/login',
}

// ==================== 旧路由重定向映射表 ====================
export const LEGACY_ROUTE_REDIRECTS = {
    // 应用模块
    '/dashboard/agent/list': ROUTES.APPLICATIONS.AGENTS,
    '/dashboard/agent/chat-history': ROUTES.APPLICATIONS.CONVERSATIONS,
    '/dashboard/agent/conversation-logs': ROUTES.APPLICATIONS.CONVERSATIONS,
    '/dashboard/agent/model-list': ROUTES.APPLICATIONS.MODELS,
    '/dashboard/agent/model-config': ROUTES.APPLICATIONS.MODELS,
    '/dashboard/skill/management': ROUTES.APPLICATIONS.SKILLS,
    '/dashboard/workflow/list': ROUTES.APPLICATIONS.WORKFLOWS,
    '/dashboard/workflow/management': ROUTES.APPLICATIONS.WORKFLOWS,
    '/dashboard/workflow/visual': ROUTES.APPLICATIONS.WORKFLOW_VISUAL,
    '/dashboard/workflow/visual/:id': ROUTES.APPLICATIONS.WORKFLOW_VISUAL,

    // 运行模块
    '/dashboard/monitor': ROUTES.RUNTIME.OVERVIEW,
    '/dashboard/stats/tokens': ROUTES.RUNTIME.METRICS,
    '/dashboard/system/alerts': ROUTES.RUNTIME.ALERTS,

    // 资源模块
    '/dashboard/knowledge/list': ROUTES.RESOURCES.KNOWLEDGE,
    '/dashboard/knowledge/media': ROUTES.RESOURCES.MEDIA,
    '/dashboard/knowledge/embedding-lab': ROUTES.RESOURCES.EMBEDDING_LAB,
    '/dashboard/knowledge/lab': ROUTES.RESOURCES.RAG_LAB,
    '/dashboard/knowledge/vlm-playground': ROUTES.RESOURCES.VLM_LAB,
    '/dashboard/knowledge/architecture': ROUTES.RESOURCES.ARCHITECTURE,
    '/dashboard/knowledge/intelligence': ROUTES.RESOURCES.ARCHITECTURE,

    // 控制模块
    '/dashboard/system/log-config': ROUTES.CONTROL.LOG_CONFIG,
    '/dashboard/system/audit-logs': ROUTES.CONTROL.AUDIT_LOGS,
    '/dashboard/system/api-management': ROUTES.CONTROL.API_MANAGEMENT,
    '/dashboard/system/api-keys': ROUTES.CONTROL.API_MANAGEMENT,
    '/dashboard/system/pricing': ROUTES.CONTROL.PRICING,
    '/dashboard/system/monitor-config': ROUTES.CONTROL.MONITOR_CONFIG,
}

// ==================== 侧边栏菜单配置 ====================
export const SIDEBAR_MENU_CONFIG = [
    {
        id: 'applications',
        title: '应用',
        icon: 'Box',
        color: '#155eef',
        path: ROUTES.APPLICATIONS.ROOT,
        children: [
            {
                title: '应用列表',
                path: ROUTES.APPLICATIONS.AGENTS,
                icon: 'Grid',
            },
            {
                title: '会话记录',
                path: ROUTES.APPLICATIONS.CONVERSATIONS,
                icon: 'ChatDotRound',
            },
            {
                title: '模型管理',
                path: ROUTES.APPLICATIONS.MODELS,
                icon: 'Cpu',
            },
            {
                title: '技能绑定',
                path: ROUTES.APPLICATIONS.SKILLS,
                icon: 'MagicStick',
            },
            {
                title: '流程编排',
                path: ROUTES.APPLICATIONS.WORKFLOWS,
                icon: 'Connection',
            },
        ],
    },
    {
        id: 'runtime',
        title: '运行',
        icon: 'Monitor',
        color: '#10b981',
        path: ROUTES.RUNTIME.ROOT,
        children: [
            {
                title: '运行概览',
                path: ROUTES.RUNTIME.OVERVIEW,
                icon: 'DataAnalysis',
            },
            {
                title: '实时指标',
                path: ROUTES.RUNTIME.METRICS,
                icon: 'TrendCharts',
            },
            {
                title: '调用链路',
                path: ROUTES.RUNTIME.TRACES,
                icon: 'Share',
            },
            {
                title: '异常告警',
                path: ROUTES.RUNTIME.ALERTS,
                icon: 'Bell',
            },
        ],
    },
    {
        id: 'resources',
        title: '资源',
        icon: 'Collection',
        color: '#8b5cf6',
        path: ROUTES.RESOURCES.ROOT,
        children: [
            {
                title: '知识库',
                path: ROUTES.RESOURCES.KNOWLEDGE,
                icon: 'Reading',
            },
            {
                title: '素材库',
                path: ROUTES.RESOURCES.MEDIA,
                icon: 'Picture',
            },
            {
                title: '向量实验室',
                path: ROUTES.RESOURCES.EMBEDDING_LAB,
                icon: 'Aim',
            },
            {
                title: '检索实验室',
                path: ROUTES.RESOURCES.RAG_LAB,
                icon: 'Search',
            },
            {
                title: '视觉实验室',
                path: ROUTES.RESOURCES.VLM_LAB,
                icon: 'View',
            },
            {
                title: '资产架构',
                path: ROUTES.RESOURCES.ARCHITECTURE,
                icon: 'Grid',
            },
        ],
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
                icon: 'User',
            },
            {
                title: '日志配置',
                path: ROUTES.CONTROL.LOG_CONFIG,
                icon: 'Document',
            },
            {
                title: '审计日志',
                path: ROUTES.CONTROL.AUDIT_LOGS,
                icon: 'List',
            },
            {
                title: 'API 管理',
                path: ROUTES.CONTROL.API_MANAGEMENT,
                icon: 'Link',
            },
            {
                title: '定价策略',
                path: ROUTES.CONTROL.PRICING,
                icon: 'Coin',
            },
            {
                title: '监控设置',
                path: ROUTES.CONTROL.MONITOR_CONFIG,
                icon: 'Tools',
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
        if (path.startsWith(menu.path)) {
            breadcrumbs.push({ title: menu.title, path: menu.path })

            // 查找二级菜单
            const child = menu.children?.find(c => path.startsWith(c.path))
            if (child) {
                breadcrumbs.push({ title: child.title, path: child.path })
            }
            break
        }
    }

    return breadcrumbs
}
