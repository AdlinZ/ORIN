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

            // ==================== 应用模块 ====================
            {
                path: 'applications',
                meta: { title: '应用', category: 'applications' },
                children: [
                    // 应用列表（智能体）
                    {
                        path: 'agents',
                        name: 'ApplicationAgents',
                        component: () => import('@/views/Agent/AgentList.vue'),
                        meta: { title: '应用列表', icon: 'Grid' }
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

                    // 模型管理
                    {
                        path: 'models',
                        name: 'ApplicationModels',
                        component: () => import('@/views/ModelConfig/ModelList.vue'),
                        meta: { title: '模型管理', icon: 'Cpu' }
                    },
                    {
                        path: 'models/config',
                        name: 'ModelConfig',
                        component: () => import('@/views/ModelConfig/ModelSystemConfig.vue'),
                        meta: { title: '模型配置', hidden: true }
                    },

                    // 技能绑定
                    {
                        path: 'skills',
                        name: 'ApplicationSkills',
                        component: () => import('@/views/Skill/SkillManagement.vue'),
                        meta: { title: '技能绑定', icon: 'MagicStick' }
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
                    // 运行概览
                    {
                        path: 'overview',
                        name: 'RuntimeOverview',
                        component: () => import('@/views/MonitorDashboard.vue'),
                        meta: { title: '运行概览', icon: 'DataAnalysis' }
                    },

                    // 实时指标
                    {
                        path: 'metrics',
                        name: 'RuntimeMetrics',
                        component: () => import('@/views/Monitor/TokenStats.vue'),
                        meta: { title: '实时指标', icon: 'TrendCharts' }
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

                    // 异常告警
                    {
                        path: 'alerts',
                        name: 'RuntimeAlerts',
                        component: () => import('@/views/System/AlertManagement.vue'),
                        meta: { title: '异常告警', icon: 'Bell', roles: ['ROLE_ADMIN'] }
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
                        component: () => import('@/views/Knowledge/KBList.vue'),
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

                    // 资产架构
                    {
                        path: 'architecture',
                        name: 'ResourcesArchitecture',
                        component: () => import('@/views/Knowledge/AssetSchema.vue'),
                        meta: { title: '资产架构', icon: 'Grid' }
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

                    // 日志配置
                    {
                        path: 'log-config',
                        name: 'ControlLogConfig',
                        component: () => import('@/views/System/LogConfig.vue'),
                        meta: { title: '日志配置', icon: 'Document', roles: ['ROLE_ADMIN'] }
                    },

                    // 审计日志
                    {
                        path: 'audit-logs',
                        name: 'ControlAuditLogs',
                        component: () => import('@/views/System/AuditLogs.vue'),
                        meta: { title: '审计日志', icon: 'List', roles: ['ROLE_ADMIN'] }
                    },

                    // API 管理
                    {
                        path: 'api-management',
                        name: 'ControlApiManagement',
                        component: () => import('@/views/System/ApiManagement.vue'),
                        meta: { title: 'API 管理', icon: 'Link', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'api-keys',
                        name: 'ApiKeyManagement',
                        component: () => import('@/views/System/ApiKeyManagement.vue'),
                        meta: { title: 'API 密钥管理', icon: 'Key', roles: ['ROLE_ADMIN'] }
                    },

                    // 定价策略
                    {
                        path: 'pricing',
                        name: 'ControlPricing',
                        component: () => import('@/views/System/PricingConfig.vue'),
                        meta: { title: '定价策略', icon: 'Coin', roles: ['ROLE_ADMIN'] }
                    },

                    // 监控设置
                    {
                        path: 'monitor-config',
                        name: 'ControlMonitorConfig',
                        component: () => import('@/views/System/MonitorSettings.vue'),
                        meta: { title: '监控设置', icon: 'Tools', roles: ['ROLE_ADMIN'] }
                    }
                ]
            }
        ]
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

    next()
})

export default router
