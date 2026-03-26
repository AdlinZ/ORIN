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

    // 主应用布局
    {
        path: '/dashboard',
        component: MainLayout,
        redirect: ROUTES.HOME,
        children: [
            // ==================== 首页 ====================
            {
                path: 'home',
                name: 'HomeDashboard',
                component: () => import('@/views/Home/HomeDashboard.vue'),
                meta: { title: '首页', icon: 'HomeFilled' }
            },

            // ==================== 个人中心 ====================
            {
                path: 'profile',
                name: 'Profile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心', icon: 'User' }
            },

            // ==================== 智能体模块 ====================
            {
                path: 'agents',
                meta: { title: '智能体', category: 'agents' },
                redirect: ROUTES.AGENTS.LIST,
                children: [
                    // 智能体管理（三级菜单父级）
                    {
                        path: 'manage',
                        name: 'AgentManage',
                        redirect: ROUTES.AGENTS.LIST,
                        meta: { title: '智能体管理', hidden: true }
                    },
                    // 会话管理（三级菜单父级）
                    {
                        path: 'session',
                        name: 'SessionManage',
                        redirect: ROUTES.AGENTS.CHAT_LOGS,
                        meta: { title: '会话管理', hidden: true }
                    },
                    // 能力扩展（三级菜单父级）
                    {
                        path: 'capability',
                        name: 'CapabilityManage',
                        redirect: ROUTES.AGENTS.SKILLS,
                        meta: { title: '能力扩展', hidden: true }
                    },
                    // 智能体列表
                    {
                        path: 'list',
                        name: 'AgentList',
                        component: () => import('@/views/Agent/AgentList.vue'),
                        meta: { title: '智能体列表', icon: 'Grid' }
                    },
                    // 智能体接入
                    {
                        path: 'onboard',
                        name: 'AgentOnboard',
                        component: () => import('@/views/AgentOnboarding.vue'),
                        meta: { title: '智能体接入', icon: 'Plus' }
                    },
                    // 智能体控制台
                    {
                        path: 'console/:id',
                        name: 'AgentConsole',
                        component: () => import('@/views/Agent/AgentConsole.vue'),
                        meta: { title: '智能体控制台', hidden: true }
                    },
                    // 智能体控制台入口（选择代理）
                    {
                        path: 'console',
                        name: 'AgentConsoleEntry',
                        component: () => import('@/views/Agent/AgentConsoleEntry.vue'),
                        meta: { title: '应用控制台', hidden: true }
                    },
                    // 会话记录
                    {
                        path: 'chat-logs',
                        name: 'ChatLogs',
                        component: () => import('@/views/Agent/ChatLogs.vue'),
                        meta: { title: '会话记录', icon: 'ChatDotRound' }
                    },
                    // 协作任务
                    {
                        path: 'collaboration',
                        name: 'Collaboration',
                        component: () => import('@/views/Agent/Collaboration.vue'),
                        meta: { title: '协作任务', icon: 'Avatar' }
                    },
                    // 协作仪表盘
                    {
                        path: 'collaboration/dashboard',
                        name: 'CollaborationDashboard',
                        component: () => import('@/views/Agent/CollaborationDashboard.vue'),
                        meta: { title: '协作仪表盘', icon: 'DataAnalysis' }
                    },
                    // 协作任务列表
                    {
                        path: 'collaboration/tasks',
                        name: 'CollaborationTasks',
                        component: () => import('@/views/Agent/Collaboration.vue'),
                        meta: { title: '协作任务列表', icon: 'List' }
                    },
                    // 协作模式配置
                    {
                        path: 'collaboration/config',
                        name: 'CollaborationConfig',
                        component: () => import('@/views/Agent/Collaboration.vue'),
                        meta: { title: '协作模式配置', icon: 'SetUp' }
                    },
                    // 版本管理
                    {
                        path: 'version',
                        name: 'VersionManage',
                        component: () => import('@/views/Agent/AgentList.vue'),
                        meta: { title: '版本管理', icon: 'Clock' }
                    },
                    // 测试与调试
                    {
                        path: 'test',
                        name: 'TestDebug',
                        component: () => import('@/views/Agent/AgentList.vue'),
                        meta: { title: '测试与调试', icon: 'Bug' }
                    },
                    // 技能管理
                    {
                        path: 'skills',
                        name: 'SkillList',
                        component: () => import('@/views/Skill/SkillManagement.vue'),
                        meta: { title: '技能管理', icon: 'MagicStick' }
                    },
                    // MCP 管理
                    {
                        path: 'mcp',
                        name: 'McpList',
                        component: () => import('@/views/Skill/SkillManagement.vue'),
                        meta: { title: 'MCP 管理', icon: 'Connection' }
                    },
                    // Tools 注册
                    {
                        path: 'tools',
                        name: 'ToolList',
                        component: () => import('@/views/Skill/SkillManagement.vue'),
                        meta: { title: 'Tools 注册', icon: 'Tool' }
                    },
                    // 工作流编排
                    {
                        path: 'workflows',
                        name: 'WorkflowList',
                        component: () => import('@/views/Workflow/WorkflowList.vue'),
                        meta: { title: '工作流编排', icon: 'Edit' }
                    },
                    {
                        path: 'workflows/visual/:id?',
                        name: 'VisualWorkflow',
                        component: () => import('@/views/Workflow/VisualWorkflowEditor.vue'),
                        meta: { title: '可视化编排', hidden: true }
                    },
                ]
            },

            // ==================== 知识中心模块 ====================
            {
                path: 'knowledge',
                meta: { title: '知识中心', category: 'knowledge' },
                redirect: ROUTES.KNOWLEDGE.LIST,
                children: [
                    // 知识库管理
                    {
                        path: 'list',
                        name: 'KnowledgeList',
                        component: () => import('@/views/Knowledge/KBList.vue'),
                        meta: { title: '知识库管理', icon: 'Collection' }
                    },
                    {
                        path: 'create',
                        name: 'KnowledgeCreate',
                        component: () => import('@/views/Knowledge/KBCreate.vue'),
                        meta: { title: '创建知识库', hidden: true }
                    },
                    {
                        path: 'detail/:id',
                        name: 'KnowledgeDetail',
                        component: () => import('@/views/Knowledge/KBDetail.vue'),
                        meta: { title: '知识库详情', hidden: true }
                    },
                    // 素材库
                    {
                        path: 'media',
                        name: 'KnowledgeMedia',
                        component: () => import('@/views/Knowledge/MediaHub.vue'),
                        meta: { title: '素材库', icon: 'Picture' }
                    },
                    // 检索实验室
                    {
                        path: 'retrieval-lab',
                        name: 'RetrievalLab',
                        component: () => import('@/views/Knowledge/RetrievalLab.vue'),
                        meta: { title: '检索实验室', icon: 'Search' }
                    },
                    // 嵌入实验室
                    {
                        path: 'embedding-lab',
                        name: 'EmbeddingLab',
                        component: () => import('@/views/Knowledge/EmbeddingLab.vue'),
                        meta: { title: '嵌入实验室', icon: 'Aim' }
                    },
                    // 视觉实验室
                    {
                        path: 'vlm-lab',
                        name: 'VlmLab',
                        component: () => import('@/views/Knowledge/VlmPlayground.vue'),
                        meta: { title: '视觉实验室', icon: 'View' }
                    },
                    // 智力资产
                    {
                        path: 'intelligence',
                        name: 'Intelligence',
                        component: () => import('@/views/Knowledge/IntelligenceCenter.vue'),
                        meta: { title: '智力资产', icon: 'Brain' }
                    },
                    // 端侧同步
                    {
                        path: 'sync',
                        name: 'KnowledgeSync',
                        component: () => import('@/views/System/ClientSync.vue'),
                        meta: { title: '端侧同步', icon: 'Upload' }
                    },
                    // 知识图谱（暂时重定向到知识库）
                    {
                        path: 'graph',
                        redirect: ROUTES.KNOWLEDGE.LIST
                    },
                ]
            },

            // ==================== 监控运维模块 ====================
            {
                path: 'monitor',
                meta: { title: '监控运维', category: 'monitor' },
                redirect: ROUTES.MONITOR.DASHBOARD,
                children: [
                    // 监控大盘
                    {
                        path: 'dashboard',
                        name: 'MonitorDashboard',
                        component: () => import('@/views/MonitorDashboard.vue'),
                        meta: { title: '监控大盘', icon: 'DataAnalysis' }
                    },
                    // 统计分析（三级菜单父级）
                    {
                        path: 'stats',
                        name: 'MonitorStats',
                        redirect: ROUTES.MONITOR.TOKENS,
                        meta: { title: '统计分析', hidden: true }
                    },
                    // Token 统计
                    {
                        path: 'tokens',
                        name: 'TokenStats',
                        component: () => import('@/views/Monitor/TokenStats.vue'),
                        meta: { title: 'Token 统计', icon: 'Coin' }
                    },
                    // 时延统计
                    {
                        path: 'latency',
                        name: 'LatencyStats',
                        component: () => import('@/views/Monitor/LatencyStats.vue'),
                        meta: { title: '时延统计', icon: 'Timer' }
                    },
                    // 错误统计
                    {
                        path: 'errors',
                        name: 'ErrorStats',
                        component: () => import('@/views/Monitor/TokenStats.vue'),
                        meta: { title: '错误统计', icon: 'Warning' }
                    },
                    // 调用链路
                    {
                        path: 'traces',
                        name: 'Traces',
                        component: () => import('@/views/Trace/TraceViewer.vue'),
                        meta: { title: '调用链路', icon: 'Share' }
                    },
                    // 数据流
                    {
                        path: 'dataflow/:traceId?',
                        name: 'DataFlow',
                        component: () => import('@/views/Monitor/DataFlow.vue'),
                        meta: { title: '数据流', icon: 'Switch' }
                    },
                    // 告警中心（三级菜单父级）
                    {
                        path: 'alerts-center',
                        name: 'AlertsCenter',
                        redirect: ROUTES.MONITOR.ALERTS,
                        meta: { title: '告警中心', hidden: true }
                    },
                    // 告警管理
                    {
                        path: 'alerts',
                        name: 'Alerts',
                        component: () => import('@/views/System/AlertManagement.vue'),
                        meta: { title: '告警管理', icon: 'Bell' }
                    },
                    // 告警规则
                    {
                        path: 'alert-rules',
                        name: 'AlertRules',
                        component: () => import('@/views/System/AlertManagement.vue'),
                        meta: { title: '告警规则', icon: 'Bell' }
                    },
                    // 通知渠道
                    {
                        path: 'notifications',
                        name: 'NotificationChannels',
                        component: () => import('@/views/System/NotificationChannels.vue'),
                        meta: { title: '通知渠道', icon: 'Message' }
                    },
                    // 运维工具（三级菜单父级）
                    {
                        path: 'ops',
                        name: 'MonitorOps',
                        redirect: ROUTES.MONITOR.TASKS,
                        meta: { title: '运维工具', hidden: true }
                    },
                    // 任务队列
                    {
                        path: 'tasks',
                        name: 'TaskQueue',
                        component: () => import('@/views/Monitor/TaskQueue.vue'),
                        meta: { title: '任务队列', icon: 'Tickets' }
                    },
                    // 服务器监控
                    {
                        path: 'server',
                        name: 'ServerMonitor',
                        component: () => import('@/views/Monitor/ServerMonitor.vue'),
                        meta: { title: '服务器监控', icon: 'Cpu' }
                    },
                    // 日志归档
                    {
                        path: 'logs',
                        name: 'LogArchive',
                        component: () => import('@/views/System/AuditLogs.vue'),
                        meta: { title: '日志归档', icon: 'Document' }
                    },
                ]
            },

            // ==================== 系统管理模块 ====================
            {
                path: 'system',
                meta: { title: '系统管理', category: 'system', requiresAdmin: true },
                redirect: ROUTES.SYSTEM.USERS,
                children: [
                    // 用户权限（三级菜单父级）
                    {
                        path: 'auth',
                        name: 'SystemAuth',
                        redirect: ROUTES.SYSTEM.USERS,
                        meta: { title: '用户权限', hidden: true }
                    },
                    // 用户管理
                    {
                        path: 'users',
                        name: 'UserManagement',
                        component: () => import('@/views/System/UserManagement.vue'),
                        meta: { title: '用户管理', icon: 'User', roles: ['ROLE_ADMIN'] }
                    },
                    // 部门权限
                    {
                        path: 'departments',
                        name: 'DepartmentManagement',
                        component: () => import('@/views/System/DepartmentManagement.vue'),
                        meta: { title: '部门权限', icon: 'OfficeBuilding', roles: ['ROLE_ADMIN'] }
                    },
                    // 角色管理
                    {
                        path: 'roles',
                        name: 'RoleManagement',
                        component: () => import('@/views/System/RoleManagement.vue'),
                        meta: { title: '角色管理', icon: 'UserFilled', roles: ['ROLE_ADMIN'] }
                    },
                    // 认证鉴权（三级菜单父级）
                    {
                        path: 'security',
                        name: 'SystemSecurity',
                        redirect: ROUTES.SYSTEM.API_KEYS,
                        meta: { title: '认证鉴权', hidden: true }
                    },
                    // API Key
                    {
                        path: 'api-keys',
                        name: 'ApiKeyManagement',
                        component: () => import('@/views/System/ApiKeyManagement.vue'),
                        meta: { title: 'API Key', icon: 'Key', roles: ['ROLE_ADMIN'] }
                    },
                    // 限流配置
                    {
                        path: 'rate-limit',
                        name: 'RateLimitConfig',
                        component: () => import('@/views/System/RateLimitConfig.vue'),
                        meta: { title: '限流配置', icon: 'Lightning', roles: ['ROLE_ADMIN'] }
                    },
                    // 消息中心（邮件服务）
                    {
                        path: 'messages',
                        name: 'MessageCenter',
                        redirect: ROUTES.SYSTEM.SETTINGS_MAIL,
                        meta: { title: '消息中心', icon: 'Message', roles: ['ROLE_ADMIN'], hidden: true }
                    },
                    // 文件管理
                    {
                        path: 'files',
                        name: 'FileManagement',
                        component: () => import('@/views/System/FileManagement.vue'),
                        meta: { title: '文件管理', icon: 'Folder', roles: ['ROLE_ADMIN'] }
                    },
                    // 系统设置（三级菜单父级）
                    {
                        path: 'system-settings',
                        name: 'SystemSettingsParent',
                        redirect: ROUTES.SYSTEM.SETTINGS_BASE,
                        meta: { title: '系统设置', hidden: true }
                    },
                    // 系统设置统一壳层
                    {
                        path: 'settings',
                        name: 'SystemSettingsLayout',
                        component: () => import('@/views/SystemSettings/SystemSettingsLayout.vue'),
                        redirect: ROUTES.SYSTEM.SETTINGS_BASE,
                        meta: { title: '系统设置', hidden: true },
                        children: [
                            // 基础设置
                            {
                                path: 'base',
                                name: 'BaseSettings',
                                component: () => import('@/views/SystemSettings/BaseSettings.vue'),
                                meta: { title: '基础设置', roles: ['ROLE_ADMIN'] }
                            },
                            // 邮件服务
                            {
                                path: 'mail',
                                name: 'MailSettings',
                                component: () => import('@/views/SystemSettings/MailSettings.vue'),
                                meta: { title: '邮件服务', roles: ['ROLE_ADMIN'] }
                            },
                            // 通知渠道
                            {
                                path: 'notifications',
                                name: 'NotificationSettings',
                                component: () => import('@/views/SystemSettings/NotificationSettings.vue'),
                                meta: { title: '通知渠道', roles: ['ROLE_ADMIN'] }
                            },
                            // 模型默认参数
                            {
                                path: 'model-defaults',
                                name: 'ModelDefaultSettings',
                                component: () => import('@/views/SystemSettings/ModelDefaultSettings.vue'),
                                meta: { title: '模型默认参数', roles: ['ROLE_ADMIN'] }
                            },
                            // 监控配置 - 复用现有页面
                            {
                                path: 'monitor',
                                name: 'MonitorSettings',
                                component: () => import('@/views/System/MonitorSettings.vue'),
                                meta: { title: '监控配置', roles: ['ROLE_ADMIN'] }
                            },
                            // 知识库配置 - 复用现有页面
                            {
                                path: 'knowledge',
                                name: 'KnowledgeSettings',
                                component: () => import('@/views/System/KnowledgeConfig.vue'),
                                meta: { title: '知识库配置', roles: ['ROLE_ADMIN'] }
                            },
                            // 网关配置 - 复用现有页面
                            {
                                path: 'gateway',
                                name: 'GatewaySettings',
                                component: () => import('@/views/System/ApiGateway.vue'),
                                meta: { title: '网关配置', roles: ['ROLE_ADMIN'] }
                            },
                            // 同步配置 - 复用现有页面
                            {
                                path: 'sync',
                                name: 'SyncSettings',
                                component: () => import('@/views/System/ClientSync.vue'),
                                meta: { title: '同步配置', roles: ['ROLE_ADMIN'] }
                            }
                        ]
                    },
                    // 审计日志
                    {
                        path: 'audit-logs',
                        name: 'AuditLogs',
                        component: () => import('@/views/System/AuditLogs.vue'),
                        meta: { title: '审计日志', icon: 'Document', roles: ['ROLE_ADMIN'] }
                    },
                    // 模型配置
                    {
                        path: 'models',
                        name: 'ModelConfig',
                        component: () => import('@/views/ModelConfig/ModelList.vue'),
                        meta: { title: '模型配置', icon: 'Cpu', roles: ['ROLE_ADMIN'] }
                    },
                    // 定价配置
                    {
                        path: 'pricing',
                        name: 'PricingConfig',
                        component: () => import('@/views/System/PricingConfig.vue'),
                        meta: { title: '定价配置', icon: 'PriceTag', roles: ['ROLE_ADMIN'] }
                    },
                    // 系统环境配置
                    {
                        path: 'monitor-settings',
                        name: 'LegacyMonitorSettings',
                        component: () => import('@/views/System/MonitorSettings.vue'),
                        meta: { title: '系统环境配置', icon: 'Setting', roles: ['ROLE_ADMIN'] }
                    },
                    // 统一网关
                    {
                        path: 'gateway',
                        name: 'ApiGateway',
                        component: () => import('@/views/System/ApiGateway.vue'),
                        meta: { title: '统一网关', icon: 'Router', roles: ['ROLE_ADMIN'] }
                    },
                    // 分布式锁
                    {
                        path: 'distributed-lock',
                        name: 'DistributedLock',
                        component: () => import('@/views/System/DistributedLock.vue'),
                        meta: { title: '分布式锁', icon: 'Lock', roles: ['ROLE_ADMIN'] }
                    },
                    // 外部框架集成汇总
                    {
                        path: 'external-frameworks',
                        name: 'ExternalFrameworksList',
                        component: () => import('@/views/System/ExternalFrameworks.vue'),
                        meta: { title: '外部框架集成', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    // Dify 集成
                    {
                        path: 'external-frameworks/dify',
                        name: 'DifyIntegration',
                        component: () => import('@/views/System/ExternalFrameworks.vue'),
                        meta: { title: 'Dify 集成', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    // RAGFlow 集成
                    {
                        path: 'external-frameworks/ragflow',
                        name: 'RagflowIntegration',
                        component: () => import('@/views/System/ExternalFrameworks.vue'),
                        meta: { title: 'RAGFlow 集成', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    // AutoGen 集成
                    {
                        path: 'external-frameworks/autogen',
                        name: 'AutogenIntegration',
                        component: () => import('@/views/System/ExternalFrameworks.vue'),
                        meta: { title: 'AutoGen 集成', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    // CrewAI 集成
                    {
                        path: 'external-frameworks/crewai',
                        name: 'CrewaiIntegration',
                        component: () => import('@/views/System/ExternalFrameworks.vue'),
                        meta: { title: 'CrewAI 集成', icon: 'Connection', roles: ['ROLE_ADMIN'] }
                    },
                    // MCP 服务管理
                    {
                        path: 'mcp-service',
                        name: 'McpService',
                        component: () => import('@/views/System/McpService.vue'),
                        meta: { title: 'MCP 服务管理', icon: 'Service', roles: ['ROLE_ADMIN'] }
                    },
                    // 帮助中心
                    {
                        path: 'help-center',
                        name: 'HelpCenter',
                        component: () => import('@/views/System/HelpCenter.vue'),
                        meta: { title: '帮助中心', icon: 'QuestionFilled', roles: ['ROLE_ADMIN'] }
                    },
                    // 统计分析
                    {
                        path: 'statistics',
                        name: 'Statistics',
                        component: () => import('@/views/System/Statistics.vue'),
                        meta: { title: '统计分析', icon: 'DataAnalysis', roles: ['ROLE_ADMIN'] }
                    },
                    // 系统维护
                    {
                        path: 'maintenance',
                        name: 'SystemMaintenance',
                        component: () => import('@/views/System/SystemMaintenance.vue'),
                        meta: { title: '系统维护', icon: 'Tools', roles: ['ROLE_ADMIN'] }
                    },
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
Object.entries(LEGACY_ROUTE_REDIRECTS).forEach(([oldPath, newPath]) => {
    const dashboardRoute = routes.find(r => r.path === '/dashboard')
    if (dashboardRoute && dashboardRoute.children) {
        // 移除 /dashboard 前缀
        const relativePath = oldPath.replace('/dashboard/', '')
        dashboardRoute.children.push({
            path: relativePath,
            redirect: newPath
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
    const publicPages = ['/', '/login', '/datawall']
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

    // 检查 requiresAdmin
    if (to.meta.requiresAdmin) {
        const userStore = useUserStore()
        if (!userStore.isAdmin) {
            ElMessage.error('您没有权限访问此页面')
            return next(from.path || ROUTES.HOME)
        }
    }

    next()
})

export default router
