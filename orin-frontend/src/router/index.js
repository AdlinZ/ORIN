import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import Cookies from 'js-cookie'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { ROUTES, LEGACY_ROUTE_REDIRECTS } from './routes'

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

    // 主应用布局
    {
        path: '/dashboard',
        component: MainLayout,
        redirect: ROUTES.HOME,
        children: [
            // ==================== 个人中心 ====================
            {
                path: 'profile',
                name: 'Profile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心', icon: 'User' }
            },

            // ==================== 应用模块 ====================
            {
                path: 'applications',
                meta: { title: '应用', category: 'applications' },
                children: [
                    // 应用列表（智能体）
                    {
                        path: 'agents',
                        name: 'ApplicationAgents',
                        component: () => import('@/views/revamp/agents/AgentListV2.vue'),
                        meta: { title: '应用列表', icon: 'Grid' }
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
                        meta: { title: '接入新应用', hidden: true }
                    },

                    // 会话记录
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
                        path: 'collaboration',
                        redirect: ROUTES.AGENTS.WORKFLOW_EXECUTION
                    },
                    {
                        path: 'collaboration/dashboard',
                        redirect: ROUTES.AGENTS.WORKFLOW_EXECUTION
                    },

                    // 模型管理
                    {
                        path: 'models',
                        name: 'ApplicationModels',
                        component: () => import('@/views/ModelConfig/ModelList.vue'),
                        meta: { title: '模型管理', icon: 'Cpu' }
                    },
                    {
                        path: 'models/add',
                        name: 'ModelAdd',
                        component: () => import('@/views/ModelConfig/AddModel.vue'),
                        meta: { title: '添加模型', hidden: true }
                    },
                    {
                        path: 'models/edit/:id',
                        name: 'ModelEdit',
                        component: () => import('@/views/ModelConfig/AddModel.vue'),
                        meta: { title: '编辑模型', hidden: true }
                    },

                    // 技能绑定
                    {
                        path: 'skills',
                        name: 'ApplicationSkills',
                        component: () => import('@/views/Skill/SkillManagement.vue'),
                        meta: { title: '技能绑定', icon: 'MagicStick' }
                    },
                    {
                        path: 'mcp',
                        name: 'ApplicationMcp',
                        component: () => import('@/views/System/McpService.vue'),
                        meta: { title: 'MCP 管理', icon: 'Connection' }
                    },
                    {
                        path: 'extensions',
                        name: 'ApplicationExtensions',
                        component: () => import('@/views/Agent/AgentExtensions.vue'),
                        meta: { title: '智能体扩展', icon: 'MagicStick' }
                    },

                    // 流程编排
                    {
                        path: 'workflows',
                        name: 'ApplicationWorkflows',
                        component: () => import('@/views/Workflow/WorkflowList.vue'),
                        meta: { title: '流程编排', icon: 'Connection' }
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

            // ==================== 运行模块 ====================
            {
                path: 'runtime',
                meta: { title: '运行', category: 'runtime' },
                children: [
                    // 首页
                    {
                        path: 'home',
                        name: 'HomeDashboard',
                        component: () => import('@/views/Home/HomeDashboard.vue'),
                        meta: { title: '首页', icon: 'HomeFilled' }
                    },

                    // 实时指标
                    {
                        path: 'metrics',
                        name: 'RuntimeMetrics',
                        component: () => import('@/views/Monitor/TokenStats.vue'),
                        meta: { title: '实时指标', icon: 'TrendCharts' }
                    },
                    {
                        path: 'costs',
                        redirect: '/dashboard/control/pricing'
                    },
                    {
                        path: 'latency',
                        name: 'RuntimeLatency',
                        component: () => import('@/views/Monitor/LatencyStats.vue'),
                        meta: { title: '时延统计', icon: 'Timer' }
                    },
                    {
                        path: 'errors',
                        name: 'RuntimeErrors',
                        component: () => import('@/views/Monitor/ErrorStats.vue'),
                        meta: { title: '错误统计', icon: 'Warning' }
                    },

                    // 调用链路
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

                    // 异常告警（含告警规则，内部以 tab 切换）
                    {
                        path: 'alerts',
                        name: 'RuntimeAlerts',
                        component: () => import('@/views/System/AlertManagement.vue'),
                        meta: { title: '异常告警', icon: 'Bell', roles: ['ROLE_ADMIN'] }
                    },

                    // 服务器监控
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

                    // 任务队列
                    {
                        path: 'tasks',
                        name: 'RuntimeTasks',
                        component: () => import('@/views/Monitor/TaskQueue.vue'),
                        meta: { title: '任务队列', icon: 'Tickets' }
                    },

                    // 限流配置
                    {
                        path: 'rate-limit',
                        name: 'RuntimeRateLimit',
                        component: () => import('@/views/Monitor/RateLimit.vue'),
                        meta: { title: '限流配置', icon: 'Lightning', roles: ['ROLE_ADMIN'] }
                    },

                    // 日志归档
                    {
                        path: 'logs',
                        name: 'RuntimeLogs',
                        component: () => import('@/views/Monitor/LogArchive.vue'),
                        meta: { title: '日志归档', icon: 'Document' }
                    },

                    // 系统维护
                    {
                        path: 'maintenance',
                        name: 'RuntimeMaintenance',
                        component: () => import('@/views/System/SystemMaintenance.vue'),
                        meta: { title: '系统维护', icon: 'Tools' }
                    }
                ]
            },

            // ==================== 资源模块 ====================
            {
                path: 'resources',
                meta: { title: '资源', category: 'resources' },
                children: [
                    // 知识库
                    {
                        path: 'knowledge',
                        name: 'ResourcesKnowledge',
                        component: () => import('@/views/revamp/knowledge/KnowledgeListV2.vue'),
                        meta: { title: '知识库', icon: 'Reading' }
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

                    // 素材库
                    {
                        path: 'media',
                        name: 'ResourcesMedia',
                        component: () => import('@/views/Knowledge/MediaHub.vue'),
                        meta: { title: '素材库', icon: 'Picture' }
                    },

                    // RAG 实验室（合并了原「向量实验室」+「检索实验室」）
                    {
                        path: 'embedding-lab',
                        name: 'ResourcesEmbeddingLab',
                        component: () => import('@/views/Knowledge/EmbeddingLab.vue'),
                        meta: { title: 'RAG 实验室', icon: 'Aim' }
                    },

                    // 检索实验室 → 重定向到 RAG 实验室（已合并）
                    {
                        path: 'rag-lab',
                        redirect: '/dashboard/resources/embedding-lab'
                    },

                    // 视觉实验室
                    {
                        path: 'vlm-lab',
                        name: 'ResourcesVlmLab',
                        component: () => import('@/views/Knowledge/VlmPlayground.vue'),
                        meta: { title: '视觉实验室', icon: 'View' }
                    },

                    // 检索测试
                    {
                        path: 'retrieval-test',
                        name: 'ResourcesRetrievalTest',
                        component: () => import('@/views/Knowledge/RetrievalTestPage.vue'),
                        meta: { title: '检索测试', icon: 'Aim' }
                    },

                    // 资产架构
                    {
                        path: 'architecture',
                        name: 'ResourcesArchitecture',
                        component: () => import('@/views/Knowledge/AssetSchema.vue'),
                        meta: { title: '资产架构', icon: 'Grid' }
                    },
                    {
                        path: 'graph',
                        name: 'ResourcesGraph',
                        component: () => import('@/views/Knowledge/KnowledgeGraphList.vue'),
                        meta: { title: '知识图谱', icon: 'Connection' }
                    },
                    {
                        path: 'graph/:id',
                        name: 'ResourcesGraphDetail',
                        component: () => import('@/views/Knowledge/KnowledgeGraphDetail.vue'),
                        meta: { title: '图谱详情', hidden: true }
                    },
                    {
                        path: 'sync',
                        name: 'ResourcesSync',
                        component: () => import('@/views/Knowledge/UnifiedKnowledgeView.vue'),
                        meta: { title: '同步管理', icon: 'Upload' }
                    },

                    // 智力资产中心（重定向到资产架构）
                    {
                        path: 'intelligence',
                        redirect: 'architecture'
                    }
                ]
            },

            // ==================== 控制模块 ====================
            {
                path: 'control',
                meta: { title: '控制', category: 'control', requiresAdmin: true },
                children: [
                    // 用户权限
                    {
                        path: 'users',
                        name: 'ControlUsers',
                        component: () => import('@/views/System/UserManagement.vue'),
                        meta: { title: '用户权限', icon: 'User', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'departments',
                        name: 'ControlDepartments',
                        component: () => import('@/views/System/DepartmentManagement.vue'),
                        meta: { title: '部门管理', icon: 'OfficeBuilding', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'roles',
                        name: 'ControlRoles',
                        component: () => import('@/views/System/RoleManagement.vue'),
                        meta: { title: '角色管理', icon: 'UserFilled', roles: ['ROLE_ADMIN'] }
                    },



                    // 审计日志
                    {
                        path: 'audit-logs',
                        name: 'ControlAuditLogs',
                        component: () => import('@/views/revamp/system/AuditCenterV2.vue'),
                        meta: { title: '审计日志', icon: 'List', roles: ['ROLE_ADMIN'] }
                    },

                    // API 密钥管理
                    {
                        path: 'api-keys',
                        name: 'ApiKeyManagement',
                        component: () => import('@/views/System/ApiKeyManagement.vue'),
                        meta: { title: 'API 密钥管理', icon: 'Key', roles: ['ROLE_ADMIN'] }
                    },

                    // 文件管理
                    {
                        path: 'file-management',
                        name: 'FileManagement',
                        component: () => import('@/views/System/FileManagement.vue'),
                        meta: { title: '文件管理', icon: 'Folder', roles: ['ROLE_ADMIN'] }
                    },

                    // 系统环境配置
                    {
                        path: 'system-env',
                        name: 'ControlSystemEnv',
                        component: () => import('@/views/System/MonitorSettings.vue'),
                        meta: { title: '系统环境配置', icon: 'Tools', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'gateway',
                        name: 'ControlGateway',
                        component: () => import('@/views/System/ApiGateway.vue'),
                        meta: { title: '统一网关', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'unified-api-docs',
                        name: 'ControlUnifiedApiDocs',
                        component: () => import('@/views/System/UnifiedApiDocs.vue'),
                        meta: { title: '统一 API 文档', icon: 'Document', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'distributed-lock',
                        name: 'ControlDistributedLock',
                        component: () => import('@/views/System/DistributedLock.vue'),
                        meta: { title: '分布式锁', icon: 'Lock', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'mcp-service',
                        name: 'ControlMcpService',
                        component: () => import('@/views/System/McpService.vue'),
                        meta: { title: 'MCP 服务', icon: 'Service', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'pricing',
                        name: 'ControlPricing',
                        component: () => import('@/views/System/PricingConfig.vue'),
                        meta: { title: '定价配置', icon: 'PriceTag', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'statistics',
                        name: 'ControlStatistics',
                        component: () => import('@/views/System/Statistics.vue'),
                        meta: { title: '统计分析', icon: 'DataAnalysis', roles: ['ROLE_ADMIN'] }
                    },

                    // 通知中心（统一入口）
                    {
                        path: 'notification-channels',
                        name: 'NotificationChannels',
                        component: () => import('@/views/Mail/MailSetup.vue'),
                        meta: { title: '通知中心', icon: 'Bell', roles: ['ROLE_ADMIN'] }
                    },

                    // 端侧知识库同步
                    {
                        path: 'client-sync',
                        name: 'ClientSync',
                        component: () => import('@/views/System/ClientSync.vue'),
                        meta: { title: '端侧同步', icon: 'Upload', roles: ['ROLE_ADMIN'] }
                    },

                    // 邮件中心（新版 - 任务导向）
                    {
                        path: 'mail',
                        redirect: '/dashboard/control/mail/setup',
                        meta: { title: '邮件中心', icon: 'Message' }
                    },
                    {
                        path: 'mail/setup',
                        name: 'MailSetup',
                        component: () => import('@/views/Mail/MailSetup.vue'),
                        meta: { title: '配置与联通', icon: 'Setting', roles: ['ROLE_ADMIN'] },
                        alias: 'control/mail/setup'
                    },
                    {
                        path: 'mail/compose',
                        name: 'MailCompose',
                        component: () => import('@/views/Mail/MailCompose.vue'),
                        meta: { title: '发送与模板', icon: 'EditPen', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'mail/tracking',
                        name: 'MailTracking',
                        component: () => import('@/views/Mail/MailTracking.vue'),
                        meta: { title: '追踪与回执', icon: 'List', roles: ['ROLE_ADMIN'] }
                    },

                    // 邮件中心（旧版，保留兼容）
                    {
                        path: 'mail-center',
                        name: 'MailCenter',
                        component: () => import('@/views/System/MailCenter.vue'),
                        meta: { title: '邮件中心', icon: 'Message', roles: ['ROLE_ADMIN'] }
                    }
                ]
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
    }
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
router.beforeEach((to, from, next) => {
    // 设置页面标题
    if (to.meta.title) {
        document.title = `${to.meta.title} - ORIN`
    }

    // 检查是否需要登录
    const token = Cookies.get('orin_token')

    // 公开页面列表
    const publicPages = ['/', '/login', '/datawall', '/unified-docs']
    const authRequired = !publicPages.includes(to.path)

    if (authRequired && !token) {
        ElMessage.warning('请先登录')
        return next('/login')
    }

    // 检查权限
    if (to.meta.roles) {
        const userStore = useUserStore()
        const hasRole = to.meta.roles.some(role => userStore.roles?.includes(role))

        if (!hasRole) {
            ElMessage.error('您没有权限访问此页面')
            return next(from.path || ROUTES.HOME)
        }
    }

    next()
})

export default router
