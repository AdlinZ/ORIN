import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import WorkspaceLayout from '../layout/WorkspaceLayout.vue'
import AdminLayout from '../layout/AdminLayout.vue'
import Cookies from 'js-cookie'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { ROUTES, LEGACY_ROUTE_REDIRECTS } from './routes'
import { getSetupStatus } from '@/api/setup'
import {
    ADMIN_MENU_ROLES,
    WORKSPACE_MENU_ROLES,
    MONITOR_MENU_ROLES,
    ORGANIZATION_MENU_ROLES,
    USER_MENU_ROLES,
    canAccessAnyRole,
    getDashboardGuardRedirect,
    getDefaultHomeByRoles
} from './topMenuConfig'

const ADMIN_ROUTE_ROLES = [...ADMIN_MENU_ROLES]
const WORKSPACE_ROUTE_ROLES = [...WORKSPACE_MENU_ROLES]
const MONITOR_ROUTE_ROLES = [...MONITOR_MENU_ROLES]
const ORGANIZATION_ROUTE_ROLES = [...ORGANIZATION_MENU_ROLES]
const API_KEY_SELF_SERVICE_ROUTE_ROLES = [...USER_MENU_ROLES]
const SETUP_COMPLETED_SESSION_KEY = 'orin_setup_completed'
// 初始化状态在一次浏览器会话内不会因菜单跳转而改变。短缓存会让每隔几次
// 跳转重新等待 /setup/status，直接把网络延迟带到管理台导航上。
const SETUP_STATUS_CACHE_TTL_MS = 5 * 60 * 1000
const SETUP_STATUS_FAILURE_CACHE_TTL_MS = 30 * 1000

let setupStatusCache = null
let setupStatusCacheExpiresAt = 0
let setupStatusPromise = null

const hasCompletedSetupSession = () => {
    return typeof window !== 'undefined'
        && window.sessionStorage.getItem(SETUP_COMPLETED_SESSION_KEY) === 'true'
}

const getStoredToken = () => {
    return Cookies.get('orin_token')
        || window.localStorage.getItem('orin_token')
        || window.sessionStorage.getItem('orin_token')
        || ''
}

const getUnauthorizedFallback = (from, userRoles = []) => {
    const defaultHome = getDefaultHomeByRoles(userRoles)
    const publicFallbacks = new Set(['/', '/login', '/register', '/datawall', '/unified-docs'])

    if (!from?.path || publicFallbacks.has(from.path)) {
        return defaultHome
    }

    return from.fullPath || from.path
}

const getSetupStatusCached = async (force = false) => {
    const now = Date.now()
    if (!force && hasCompletedSetupSession()) {
        if (!setupStatusCache) {
            setupStatusCache = { completed: true, canInitialize: false }
            setupStatusCacheExpiresAt = now + SETUP_STATUS_CACHE_TTL_MS
        }
        return setupStatusCache
    }
    if (!force && setupStatusCache && now < setupStatusCacheExpiresAt) {
        return setupStatusCache
    }
    if (!setupStatusPromise) {
        setupStatusPromise = getSetupStatus()
            .then(status => {
                setupStatusCache = status
                setupStatusCacheExpiresAt = Date.now() + SETUP_STATUS_CACHE_TTL_MS
                if (status?.completed) {
                    window.sessionStorage.setItem(SETUP_COMPLETED_SESSION_KEY, 'true')
                }
                return status
            })
            .catch(() => {
                // 初始化服务短暂不可达时，正常的已登录页面不应在每次路由切换都
                // 被同一个 2 秒超时阻塞；仍保留短暂重试，避免掩盖首次初始化。
                setupStatusCache = { completed: false, canInitialize: false }
                setupStatusCacheExpiresAt = Date.now() + SETUP_STATUS_FAILURE_CACHE_TTL_MS
                return setupStatusCache
            })
            .finally(() => {
                setupStatusPromise = null
            })
    }
    return setupStatusPromise
}

// ==================== 路由配置 ====================
const routes = [
    // 欢迎页
    {
        path: '/',
        name: 'Welcome',
        component: () => import('@/views/Home.vue'),
        meta: { title: '欢迎使用 ORIN' }
    },

    // 登录页
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login.vue'),
        meta: { title: '用户登录' }
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/Register.vue'),
        meta: { title: '注册账号' }
    },
    {
        path: '/setup',
        name: 'SetupWizard',
        component: () => import('@/views/SetupWizard.vue'),
        meta: { title: '首次初始化' }
    },
    {
        path: '/chat',
        name: 'ChatPortal',
        component: () => import('@/views/UserPortal.vue'),
        // Chat 是公开产品入口；登录后由页面按当前身份加载个人会话和可用服务。
        meta: { title: 'ORIN Chat' }
    },
    {
        path: '/chat/profile',
        name: 'ChatProfile',
        component: () => import('@/views/UserProfile.vue'),
        meta: { title: '个人中心', roles: USER_MENU_ROLES }
    },
    {
        path: '/platform',
        name: 'DeveloperPlatform',
        component: () => import('@/views/System/ApiKeyManagement.vue'),
        props: { selfService: true },
        meta: { title: '开发者平台', roles: API_KEY_SELF_SERVICE_ROUTE_ROLES }
    },
    {
        path: '/platform/api-keys',
        name: 'PlatformApiKeys',
        component: () => import('@/views/System/ApiKeyManagement.vue'),
        props: { selfService: true },
        meta: { title: '开发者平台', roles: API_KEY_SELF_SERVICE_ROUTE_ROLES }
    },
    {
        path: '/platform/docs',
        name: 'PlatformApiDocs',
        component: () => import('@/views/System/UnifiedApiDocs.vue'),
        meta: { title: 'API 文档', roles: API_KEY_SELF_SERVICE_ROUTE_ROLES }
    },
    {
        path: '/portal',
        redirect: ROUTES.CHAT
    },
    {
        path: '/portal/api-keys',
        redirect: ROUTES.PLATFORM_API_KEYS
    },

    // 数据大屏
    {
        path: '/datawall',
        name: 'DataWall',
        component: () => import('@/views/DataWall.vue'),
        meta: { title: '数据大屏' }
    },
    // 独立统一 API 文档页（不在 dashboard 内）
    {
        path: '/unified-docs',
        name: 'ApiDocsPortal',
        component: () => import('@/views/System/UnifiedApiDocs.vue'),
        meta: { title: '统一 API 文档' }
    },

    // Phase 7: 历史 /dashboard/{applications,control,runtime,resources} 顶层重定向到 canonical 新路径。
    // 子路径仍由 LEGACY_ROUTE_REDIRECTS 自动生成；mount 块保留作为向后兼容兜底（命中不到子路径时渲染）。
    {
        path: '/dashboard/applications',
        redirect: ROUTES.WORKSPACE_ROOT
    },
    {
        path: '/dashboard/control',
        redirect: ROUTES.ADMIN_ROOT
    },
    {
        path: '/dashboard/runtime',
        redirect: `${ROUTES.ADMIN_ROOT}/runtime`
    },
    {
        path: '/dashboard/resources',
        redirect: `${ROUTES.WORKSPACE_ROOT}/knowledge`
    },

    // 主应用布局
    {
        path: '/dashboard',
        component: MainLayout,
        redirect: () => {
            const userStore = useUserStore()
            userStore.restoreFromCookies()
            return getDefaultHomeByRoles(userStore.roles || [])
        },
        children: [
            // ==================== 个人中心 ====================
            {
                path: 'profile',
                name: 'Profile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心', icon: 'User' }
            },
            {
                path: 'mcp',
                redirect: ROUTES.MCP.SERVERS,
                meta: { title: 'MCP', category: 'applications', hidden: true },
                children: [
                    {
                        path: 'servers',
                        redirect: ROUTES.MCP.SERVERS,
                        meta: { title: 'MCP 服务', icon: 'Connection', hidden: true }
                    }
                ]
            },

            // ==================== 智能体管理模块（Phase 7b: mount 迁到 top-level /workspace） ====================
            {
                path: 'applications',
                redirect: ROUTES.WORKSPACE_ROOT
            },

            // ==================== 运行监控模块（Phase 7b: mount 迁到 top-level /admin/runtime） ====================
            {
                path: 'runtime',
                redirect: `${ROUTES.ADMIN_ROOT}/runtime`
            },

            // ==================== 知识库管理模块（Phase 7b: mount 迁到 top-level /workspace/knowledge） ====================
            {
                path: 'resources',
                redirect: `${ROUTES.WORKSPACE_ROOT}/knowledge`
            },

            // Phase 7b: control mount 移到 top-level（见下方同名 top-level 路由）
            {
                path: 'control',
                redirect: ROUTES.ADMIN_ROOT
            },

            // 404 页面
            {
                path: ':pathMatch(.*)*',
                name: 'NotFound',
                component: () => import('@/views/Error/NotFound.vue'),
                meta: { title: '页面不存在' }
            }
        ]
    },

    // 404 页面 - 全局捕获（必须放在最后）
    {
        path: '/:pathMatch(.*)*',
        name: 'GlobalNotFound',
        component: () => import('@/views/Error/NotFound.vue'),
        meta: { title: '页面不存在' }
    },

    // ==================== Phase 7b: top-level mounts（新 canonical 路径） ====================
    // 这些路由块从 /dashboard.children 迁出，挂到 /workspace 与 /admin 等新路径下。
    // 子路径的旧形态由 LEGACY_ROUTE_REDIRECTS 兜底 redirect 到 canonical。
    {
        path: `${ROUTES.WORKSPACE_ROOT}/knowledge`,
        component: WorkspaceLayout,
        meta: { title: '资源知识', category: 'resources', surface: 'workspace', roles: WORKSPACE_ROUTE_ROLES },
        children: [
            {
                path: '',
                redirect: '/workspace/knowledge/center'
            },
            {
                path: 'center',
                name: 'ResourcesKnowledgeCenter',
                component: () => import('@/views/Knowledge/EmbeddingLab.vue'),
                meta: { title: '知识检索', icon: 'Reading' }
            },
            {
                path: 'assets',
                name: 'ResourcesKnowledgeAssets',
                component: () => import('@/views/Knowledge/KnowledgeAssets.vue'),
                meta: { title: '知识资产', icon: 'Collection' }
            },
            {
                path: 'knowledge',
                name: 'ResourcesKnowledge',
                redirect: '/workspace/knowledge/assets',
                meta: { title: '知识库（旧）', hidden: true }
            },
            {
                path: 'knowledge/create',
                name: 'KnowledgeCreate',
                component: () => import('@/views/Knowledge/KBCreate.vue'),
                meta: { title: '创建知识库', hidden: true }
            },
            {
                path: 'knowledge/detail/:id',
                name: 'KnowledgeDetail',
                component: () => import('@/views/Knowledge/KBDetail.vue'),
                meta: { title: '知识库详情', hidden: true }
            },
            {
                path: 'knowledge/:kbId/document/:docId',
                name: 'DocumentDetail',
                component: () => import('@/views/Knowledge/DocumentDetail.vue'),
                meta: { title: '文档详情', hidden: true }
            },
            {
                path: 'retrieval',
                name: 'ResourcesRetrieval',
                component: () => import('@/views/Knowledge/EmbeddingLab.vue'),
                meta: { title: '知识检索', icon: 'Search' }
            },
            {
                path: 'embedding-lab',
                redirect: '/workspace/knowledge/retrieval'
            },
            {
                path: 'rag-lab',
                redirect: '/workspace/knowledge/retrieval'
            },
            {
                path: 'retrieval-test',
                name: 'ResourcesRetrievalTest',
                component: () => import('@/views/Knowledge/RetrievalTestPage.vue'),
                meta: { title: '检索测试', icon: 'Aim' }
            },
            {
                path: 'architecture',
                name: 'ResourcesArchitecture',
                component: () => import('@/views/Knowledge/AssetSchema.vue'),
                meta: { title: '资产架构', icon: 'Grid' }
            },
            {
                path: 'graph',
                name: 'ResourcesGraph',
                redirect: '/workspace/knowledge/assets',
                meta: { title: '知识图谱（旧）', hidden: true }
            },
            {
                path: 'graph/:id',
                name: 'ResourcesGraphDetail',
                component: () => import('@/views/Knowledge/KnowledgeGraphDetail.vue'),
                meta: { title: '图谱详情', hidden: true }
            },
            {
                path: 'sync',
                redirect: '/admin/data-assets?assetTab=sync&tab=changes'
            },
            {
                path: 'intelligence',
                redirect: 'architecture'
            }
        ]
    },
    {
        path: ROUTES.WORKSPACE_ROOT,
        component: WorkspaceLayout,
        meta: { title: '应用构建', category: 'applications', surface: 'workspace', roles: WORKSPACE_ROUTE_ROLES },
        children: [
            {
                path: '',
                redirect: ROUTES.WORKSPACE
            },
            {
                path: 'profile',
                name: 'WorkspaceProfile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心', hidden: true, roles: WORKSPACE_ROUTE_ROLES }
            },
            {
                path: 'agents',
                name: 'ApplicationAgents',
                component: () => import('@/views/revamp/agents/AgentListV2.vue'),
                meta: { title: '智能体列表', icon: 'Grid' }
            },
            {
                path: 'developer',
                name: 'ApplicationDeveloper',
                component: () => import('@/views/revamp/agents/DeveloperDashboard.vue'),
                meta: { title: '工作台首页', icon: 'Monitor', roles: WORKSPACE_ROUTE_ROLES }
            },
            {
                path: 'agents/console',
                name: 'AgentConsoleEntry',
                redirect: ROUTES.AGENTS.LIST,
                meta: { title: '应用控制台', icon: 'Monitor' }
            },
            {
                path: 'agents/console/:id',
                name: 'AgentConsole',
                component: () => import('@/views/Agent/AgentConsole.vue'),
                meta: { title: '应用控制台', hidden: true }
            },
            {
                path: 'agents/onboard',
                name: 'AgentOnboard',
                component: () => import('@/views/AgentOnboarding.vue'),
                meta: { title: '智能体接入', hidden: true }
            },
            {
                path: 'conversations',
                name: 'ApplicationConversations',
                component: () => import('@/views/Agent/ChatLogs.vue'),
                meta: { title: '会话记录', icon: 'ChatDotRound' }
            },
            {
                path: 'workspace',
                name: 'ApplicationWorkspace',
                component: () => import('@/views/Agent/AgentWorkspace.vue'),
                meta: { title: '智能体工作台', icon: 'Monitor' }
            },
            {
                path: 'workflows/execution',
                name: 'ApplicationWorkflowExecution',
                component: () => import('@/views/Workflow/WorkflowExecution.vue'),
                meta: { title: '工作流执行', icon: 'VideoPlay' }
            },
            {
                path: 'workflows-v2',
                redirect: ROUTES.AGENTS.WORKFLOWS,
                meta: { hidden: true }
            },
            {
                path: 'workflows-v2/canvas',
                redirect: ROUTES.AGENTS.WORKFLOW_VISUAL,
                meta: { hidden: true }
            },
            {
                path: 'workflows-v2/runs',
                redirect: ROUTES.AGENTS.WORKFLOW_EXECUTION,
                meta: { hidden: true }
            },
            {
                path: 'workflows-v2/:id',
                redirect: ROUTES.AGENTS.WORKFLOWS,
                meta: { hidden: true }
            },
            {
                path: 'collaboration/dashboard',
                component: () => import('@/views/revamp/collaboration/CollaborationDashboardV2.vue'),
                meta: { title: '协作任务包看板', icon: 'DataAnalysis' }
            },
            {
                path: 'collaboration/workflows',
                name: 'MultiAgentCollaborationWorkflows',
                component: () => import('@/views/Playground/PlaygroundWorkflows.vue'),
                meta: { title: '多智能体协同', icon: 'Connection' }
            },
            {
                path: 'skills',
                name: 'ApplicationSkills',
                redirect: ROUTES.AGENTS.SKILLS,
                meta: { title: 'Skills', icon: 'MagicStick', hidden: true }
            },
            {
                path: 'mcp',
                name: 'ApplicationMcp',
                redirect: ROUTES.MCP.SERVERS,
                meta: { title: 'MCP 管理', icon: 'Connection', hidden: true }
            },
            {
                path: 'extensions',
                name: 'ApplicationExtensions',
                component: () => import('@/views/Agent/AgentExtensions.vue'),
                meta: { title: '智能体扩展', icon: 'MagicStick' }
            },
            {
                path: 'playground',
                name: 'AgentPlayground',
                component: () => import('@/views/Playground/PlaygroundContainer.vue'),
                meta: { title: '多智能体控制台', icon: 'Play' }
            },
            {
                path: 'playground/overview',
                name: 'PlaygroundOverview',
                component: () => import('@/views/Playground/PlaygroundOverview.vue'),
                meta: { title: '多智能体总览', icon: 'Histogram' }
            },
            {
                path: 'playground/workflows',
                name: 'LegacyPlaygroundWorkflows',
                redirect: (to) => ({
                    path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
                    query: to.query
                }),
                meta: { title: '多智能体协同', icon: 'Connection', hidden: true }
            },
            {
                path: 'playground/run',
                name: 'PlaygroundRun',
                redirect: '/workspace/workspace',
                meta: { title: '协作对话', icon: 'VideoPlay' }
            },
            {
                path: 'workflows',
                name: 'ApplicationWorkflows',
                component: () => import('@/views/Workflow/WorkflowList.vue'),
                meta: { title: '工作流中心', icon: 'Connection' }
            },
            {
                path: 'workflows/:id',
                name: 'WorkflowEditCompat',
                redirect: (to) => `/workspace/workflows/visual/${to.params.id}`,
                meta: { hidden: true }
            },
            {
                path: 'workflows/create',
                name: 'WorkflowCreate',
                component: () => import('@/views/Workflow/WorkflowEditor.vue'),
                meta: { title: '创建工作流', hidden: true }
            },
            {
                path: 'workflows/edit/:id',
                name: 'WorkflowEdit',
                component: () => import('@/views/Workflow/WorkflowEditor.vue'),
                meta: { title: '编辑工作流', hidden: true }
            },
            {
                path: 'workflows/visual',
                name: 'VisualWorkflowCreate',
                component: () => import('@/views/Workflow/VisualWorkflowEditor.vue'),
                meta: { title: '可视化工作流编辑器', hidden: true }
            },
            {
                path: 'workflows/visual/:id',
                name: 'VisualWorkflowEdit',
                component: () => import('@/views/Workflow/VisualWorkflowEditor.vue'),
                meta: { title: '编辑可视化工作流', hidden: true }
            }
        ]
    },
    {
        path: `${ROUTES.ADMIN_ROOT}/runtime`,
        component: AdminLayout,
        meta: { title: '运行监控', category: 'runtime', roles: MONITOR_ROUTE_ROLES },
        children: [
            // 监控总览
            {
                path: 'overview',
                name: 'HomeDashboard',
                component: () => import('@/views/Home/HomeDashboard.vue'),
                meta: { title: '监控总览', icon: 'DataAnalysis' }
            },
            {
                path: 'home',
                redirect: '/admin/runtime/overview'
            },
            {
                path: 'metrics',
                name: 'RuntimeMetrics',
                component: () => import('@/views/Monitor/TokenStats.vue'),
                meta: { title: '用量统计', icon: 'TrendCharts' }
            },
            {
                path: 'costs',
                redirect: '/admin/pricing'
            },
            {
                path: 'latency',
                name: 'RuntimeLatency',
                component: () => import('@/views/Monitor/LatencyStats.vue'),
                meta: { title: '性能分析', icon: 'Timer' }
            },
            {
                path: 'errors',
                name: 'RuntimeErrors',
                component: () => import('@/views/Monitor/ErrorStats.vue'),
                meta: { title: '错误统计', icon: 'Warning' }
            },
            {
                path: 'traces',
                name: 'RuntimeTraces',
                component: () => import('@/views/Trace/TraceViewer.vue'),
                meta: { title: '调用链路', icon: 'Share' }
            },
            {
                path: 'traces/:traceId',
                name: 'TraceDetail',
                component: () => import('@/views/Trace/TraceViewer.vue'),
                meta: { title: '链路详情', hidden: true }
            },
            {
                path: 'dataflow/:traceId',
                name: 'DataFlow',
                component: () => import('@/views/Monitor/DataFlow.vue'),
                meta: { title: '数据流追踪', hidden: true }
            },
            {
                path: 'alerts',
                name: 'RuntimeAlerts',
                component: () => import('@/views/Monitor/AlertsLogsCenter.vue'),
                meta: { title: '告警与日志', icon: 'Bell', roles: ADMIN_ROUTE_ROLES }
            },
            {
                path: 'alerts/rules/create',
                name: 'RuntimeAlertRuleCreate',
                component: () => import('@/views/Monitor/AlertRuleBuilder.vue'),
                meta: { title: '创建告警规则', hidden: true, roles: ADMIN_ROUTE_ROLES }
            },
            {
                path: 'alerts/rules/:id/edit',
                name: 'RuntimeAlertRuleEdit',
                component: () => import('@/views/Monitor/AlertRuleBuilder.vue'),
                meta: { title: '编辑告警规则', hidden: true, roles: ADMIN_ROUTE_ROLES }
            },
            {
                path: 'audit-logs',
                name: 'RuntimeAuditLogs',
                component: () => import('@/views/revamp/system/AuditCenterV2.vue'),
                meta: { title: '审计日志', icon: 'List', roles: ADMIN_ROUTE_ROLES }
            },
            {
                path: 'server/:serverId',
                name: 'RuntimeServerNode',
                component: () => import('@/views/Monitor/ServerNodeDetail.vue'),
                meta: { title: '节点监控详情', hidden: true }
            },
            {
                path: 'server',
                name: 'RuntimeServer',
                component: () => import('@/views/Monitor/ServerMonitor.vue'),
                meta: { title: '服务器监控', icon: 'Monitor' }
            },
            {
                path: 'tasks',
                name: 'RuntimeTasks',
                component: () => import('@/views/Monitor/TaskQueue.vue'),
                meta: { title: '任务队列', icon: 'Tickets' }
            },
            {
                path: 'rate-limit',
                name: 'RuntimeRateLimit',
                component: () => import('@/views/Monitor/RateLimit.vue'),
                meta: { title: '限流配置', icon: 'Lightning', roles: ADMIN_ROUTE_ROLES }
            },
            {
                path: 'logs',
                name: 'RuntimeLogs',
                component: () => import('@/views/Monitor/LogArchive.vue'),
                meta: { title: '日志归档', icon: 'Document' }
            },
            {
                path: 'maintenance',
                name: 'RuntimeMaintenance',
                component: () => import('@/views/System/SystemMaintenance.vue'),
                meta: { title: '系统维护', icon: 'Tools' }
            }
        ]
    },
    {
        path: ROUTES.ADMIN_ROOT,
        component: AdminLayout,
        meta: { title: '平台控制', category: 'control', surface: 'admin', requiresAdmin: true, roles: ADMIN_ROUTE_ROLES },
        children: [
        {
            path: '',
            redirect: ROUTES.ADMIN
        },
        {
            path: 'profile',
            name: 'AdminProfile',
            component: () => import('@/views/Profile.vue'),
            meta: { title: '个人中心', hidden: true, roles: ADMIN_ROUTE_ROLES }
        },
            // 平台总览
        {
            path: 'admin-overview',
            name: 'ControlAdminDashboard',
            component: () => import('@/views/revamp/system/AdminDashboard.vue'),
            meta: { title: '平台总览', icon: 'DataBoard', roles: ADMIN_ROUTE_ROLES }
        },
        // 用户权限
        {
            path: 'users',
            name: 'ControlUsers',
            component: () => import('@/views/System/UserManagement.vue'),
            meta: { title: '用户管理', icon: 'User', roles: ORGANIZATION_ROUTE_ROLES }
        },
        {
            path: 'departments',
            name: 'ControlDepartments',
            component: () => import('@/views/System/DepartmentManagement.vue'),
            meta: { title: '部门管理', icon: 'OfficeBuilding', roles: ORGANIZATION_ROUTE_ROLES }
        },
        {
            path: 'roles',
            name: 'ControlRoles',
            component: () => import('@/views/System/RoleManagement.vue'),
            meta: { title: '角色管理', icon: 'UserFilled', roles: ORGANIZATION_ROUTE_ROLES }
        },
        // AI 基础设施是平台级资源，必须挂在 AdminLayout 下，不能以工作台子路由别名承载。
        {
            path: 'models',
            name: 'ControlModels',
            component: () => import('@/views/ModelConfig/ModelList.vue'),
            meta: { title: '模型管理', icon: 'Cpu', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'models/add',
            name: 'ControlModelAdd',
            component: () => import('@/views/ModelConfig/AddModel.vue'),
            meta: { title: '添加模型', hidden: true, roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'models/edit/:id',
            name: 'ControlModelEdit',
            component: () => import('@/views/ModelConfig/AddModel.vue'),
            meta: { title: '编辑模型', hidden: true, roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'audit-logs',
            name: 'ControlAuditLogs',
            component: () => import('@/views/revamp/system/AuditCenterV2.vue'),
            meta: { title: '审计日志', icon: 'List', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'api-keys',
            name: 'ApiKeyManagement',
            redirect: (to) => ({
                path: '/admin/gateway',
                query: { ...to.query, workspace: to.query.workspace || 'access' }
            }),
            meta: { title: 'API 密钥', icon: 'Key', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'file-management',
            redirect: (to) => ({
                path: '/admin/data-assets',
                query: { ...to.query, assetTab: 'files' }
            })
        },
        {
            path: 'data-assets',
            name: 'ControlDataAssets',
            component: () => import('@/views/System/DataAssets.vue'),
            meta: { title: '数据资产', icon: 'Folder', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'system-env',
            name: 'ControlSystemEnv',
            component: () => import('@/views/System/MonitorSettings.vue'),
            meta: { title: '环境配置', icon: 'Tools', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'gateway',
            name: 'ControlUnifiedGateway',
            alias: 'unified-gateway',
            component: () => import('@/views/System/UnifiedGateway.vue'),
            meta: { title: '统一网关', icon: 'Connection', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'unified-api-docs',
            name: 'ControlUnifiedApiDocs',
            component: () => import('@/views/System/UnifiedApiDocs.vue'),
            meta: { title: '统一 API 文档', icon: 'Document', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'mcp-service',
            name: 'ControlMcpService',
            component: () => import('@/views/System/McpService.vue'),
            meta: { title: 'MCP 服务', icon: 'Service', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'pricing',
            name: 'ControlPricing',
            component: () => import('@/views/System/PricingConfig.vue'),
            meta: { title: '定价配置', icon: 'PriceTag', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'statistics',
            name: 'ControlStatistics',
            component: () => import('@/views/System/Statistics.vue'),
            meta: { title: '统计分析', icon: 'DataAnalysis', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'notification-channels',
            name: 'NotificationChannels',
            component: () => import('@/views/Mail/MailSetup.vue'),
            meta: { title: '通知设置', icon: 'Message', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'sync',
            redirect: (to) => ({
                path: '/admin/data-assets',
                query: { ...to.query, assetTab: 'sync', tab: to.query.tab || 'changes' }
            })
        },
        {
            path: 'client-sync',
            redirect: (to) => ({
                path: '/admin/data-assets',
                query: { ...to.query, assetTab: 'sync', tab: to.query.tab || 'changes' }
            })
        },
        {
            path: 'mail',
            redirect: '/admin/notification-channels?tab=overview',
            meta: { title: '邮件中心', icon: 'Message' }
        },
        {
            path: 'mail/setup',
            redirect: '/admin/notification-channels?tab=service',
            meta: { title: '配置与联通', icon: 'Setting', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'mail/compose',
            redirect: '/admin/notification-channels?tab=compose',
            meta: { title: '发送与模板', icon: 'EditPen', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'mail/tracking',
            redirect: '/admin/notification-channels?tab=tracking',
            meta: { title: '追踪与回执', icon: 'List', roles: ADMIN_ROUTE_ROLES }
        },
        {
            path: 'mail-center',
            redirect: '/admin/notification-channels?tab=overview',
            meta: { title: '邮件中心', icon: 'Message', roles: ADMIN_ROUTE_ROLES }
        }
    ]
},
]

// ==================== 添加旧路由重定向 ====================
// 自动为所有旧路由添加重定向规则
const normalizeRoutePath = (path = '') => {
    if (!path) return '/'
    const normalized = path.startsWith('/') ? path : `/${path}`
    return normalized.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
}

const joinRoutePath = (basePath = '', childPath = '') => {
    if (!childPath) return normalizeRoutePath(basePath)
    if (childPath.startsWith('/')) return normalizeRoutePath(childPath)
    return normalizeRoutePath(`${basePath}/${childPath}`)
}

const hasRoutePath = (routeList, targetPath, parentPath = '') => {
    const normalizedTargetPath = normalizeRoutePath(targetPath)

    return routeList.some(route => {
        const currentPath = joinRoutePath(parentPath, route.path || '')

        if (currentPath === normalizedTargetPath) {
            return true
        }

        if (route.children?.length) {
            return hasRoutePath(route.children, normalizedTargetPath, currentPath)
        }

        return false
    })
}

Object.entries(LEGACY_ROUTE_REDIRECTS).forEach(([oldPath, newPath]) => {
    const dashboardRoute = routes.find(r => r.path === '/dashboard')
    if (dashboardRoute && dashboardRoute.children) {
        const normalizedOldPath = normalizeRoutePath(oldPath)
        const normalizedNewPath = normalizeRoutePath(newPath)

        // 跳过已经是正式路由的路径，以及重定向到自身的无效兼容项。
        if (
            normalizedOldPath === normalizedNewPath ||
            hasRoutePath(routes, normalizedOldPath)
        ) {
            return
        }

        // 移除 /dashboard 前缀
        const relativePath = oldPath.replace('/dashboard/', '')
        dashboardRoute.children.push({
            path: relativePath,
            redirect: newPath  // 使用完整路径，不要移除前缀
        })
    }
})

// ==================== 创建路由实例 ====================
const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

// ==================== 路由守卫 ====================
router.beforeEach(async (to, from, next) => {
    // 设置页面标题
    if (to.meta.title) {
        document.title = `${to.meta.title} - ORIN`
    }

    const setupStatus = await getSetupStatusCached(to.path === ROUTES.SETUP)
    if (setupStatus && !setupStatus.completed && setupStatus.canInitialize && to.path !== ROUTES.SETUP) {
        return next(ROUTES.SETUP)
    }
    if (to.path === ROUTES.SETUP && setupStatus?.completed) {
        const token = getStoredToken()
        if (token) {
            const userStore = useUserStore()
            userStore.restoreFromCookies()
            return next(getDefaultHomeByRoles(userStore.roles || []))
        }
        return next(ROUTES.LOGIN)
    }

    // 检查是否需要登录
    const token = getStoredToken()

    // 公开页面列表
    const publicPages = ['/', '/login', '/register', '/setup', '/chat', '/datawall', '/unified-docs']
    const authRequired = !publicPages.includes(to.path)

    if (authRequired && !token) {
        ElMessage.warning('请先登录')
        return next('/login')
    }

    const userStore = useUserStore()

    if (token && (!userStore.roles || userStore.roles.length === 0)) {
        userStore.restoreFromCookies()
    }

    if (token && to.path === ROUTES.REGISTER) {
        return next(getDefaultHomeByRoles(userStore.roles || []))
    }

    // 统一使用角色默认首页，避免所有角色都落到同一入口。
    // 只处理 /dashboard 根路径，保留用户显式访问运行总览等具体页面的能力。
    if (to.path === '/dashboard') {
        const defaultHome = getDefaultHomeByRoles(userStore.roles || [])
        if (to.path !== defaultHome) {
            return next(defaultHome)
        }
    }

    // 统一拦截 /dashboard/*：非管理员直接重定向到 ROLE_USER 入口。
    // 补足子页面没声明 meta.roles 的边界。
    const dashboardRedirect = getDashboardGuardRedirect(userStore.roles || [], to.path)
    if (dashboardRedirect) {
        ElMessage.error('您没有权限访问此页面')
        return next(dashboardRedirect)
    }

    // 检查权限。父级模块和子页面都可以声明 roles，直接输入 URL 时同样生效。
    const roleRequirements = to.matched
        .map(record => record.meta?.roles)
        .filter(roles => Array.isArray(roles) && roles.length > 0)

    if (roleRequirements.length > 0) {
        const userRoles = userStore.roles || []
        const hasRequiredRoles = roleRequirements.every(roles => canAccessAnyRole(userRoles, roles))

        if (!hasRequiredRoles) {
            ElMessage.error('您没有权限访问此页面')
            return next(getUnauthorizedFallback(from, userRoles))
        }
    }

    next()
})

export default router
