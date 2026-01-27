import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import Cookies from 'js-cookie'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '欢迎使用 ORIN' }
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login.vue'),
        meta: { title: '用户登录' }
    },
    {
        path: '/dashboard',
        component: MainLayout,
        redirect: '/dashboard/monitor',
        children: [
            {
                path: 'monitor',
                name: 'Monitor',
                component: () => import('@/views/MonitorDashboard.vue'),
                meta: { title: '监控大屏', icon: 'Monitor' }
            },
            {
                path: 'profile',
                name: 'Profile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心', icon: 'User' }
            },
            {
                path: 'agent',
                meta: { title: '智能体管理' },
                children: [
                    {
                        path: 'list',
                        name: 'AgentList',
                        component: () => import('../views/Agent/AgentList.vue'),
                        meta: { title: '智能体列表' }
                    },
                    {
                        path: 'onboard',
                        name: 'AgentOnboard',
                        component: () => import('../views/AgentOnboarding.vue'),
                        meta: { title: '接入新 Agent' }
                    },
                    {
                        path: 'logs',
                        name: 'AgentLogs',
                        component: () => import('../views/Agent/ChatLogs.vue'),
                        meta: { title: '会话记录' }
                    },
                    {
                        path: 'model-list',
                        name: 'ModelList',
                        component: () => import('../views/ModelConfig/ModelList.vue'),
                        meta: { title: '模型列表' }
                    },
                    {
                        path: 'model-config',
                        name: 'ModelConfig',
                        component: () => import('../views/ModelConfig/ModelSystemConfig.vue'),
                        meta: { title: '模型系统项' }
                    }
                ]
            },
            {
                path: 'knowledge',
                meta: { title: '知识管理' },
                redirect: '/dashboard/knowledge/unstructured',
                children: [
                    {
                        path: 'unstructured',
                        name: 'KnowledgeUnstructured',
                        component: () => import('../views/Knowledge/UnifiedKnowledgeView.vue'),
                        meta: { title: '非结构化知识', type: 'DOCUMENT' }
                    },
                    {
                        path: 'structured',
                        name: 'KnowledgeStructured',
                        component: () => import('../views/Knowledge/UnifiedKnowledgeView.vue'),
                        meta: { title: '结构化知识', type: 'STRUCTURED' }
                    },
                    {
                        path: 'procedural',
                        name: 'KnowledgeProcedural',
                        component: () => import('../views/Knowledge/UnifiedKnowledgeView.vue'),
                        meta: { title: '程序化知识', type: 'API' }
                    },
                    {
                        path: 'meta',
                        name: 'KnowledgeMeta',
                        component: () => import('../views/Knowledge/UnifiedKnowledgeView.vue'),
                        meta: { title: '元知识与记忆', type: 'META' }
                    }
                ]
            },
            {
                path: 'workflow',
                meta: { title: '工作流管理' },
                redirect: '/dashboard/workflow/list',
                children: [
                    {
                        path: 'list',
                        name: 'WorkflowList',
                        component: () => import('../views/Workflow/WorkflowList.vue'),
                        meta: { title: '工作流列表' }
                    },
                    {
                        path: 'management',
                        name: 'WorkflowManagement',
                        component: () => import('../views/Workflow/WorkflowManagement.vue'),
                        meta: { title: '工作流编排' }
                    },
                    {
                        path: 'create',
                        name: 'WorkflowCreate',
                        component: () => import('../views/Workflow/WorkflowEditor.vue'),
                        meta: { title: '创建工作流' }
                    },
                    {
                        path: 'edit/:id',
                        name: 'WorkflowEdit',
                        component: () => import('../views/Workflow/WorkflowEditor.vue'),
                        meta: { title: '编辑工作流' }
                    },
                    {
                        path: 'visual',
                        name: 'VisualWorkflowCreate',
                        component: () => import('../views/Workflow/VisualWorkflowEditor.vue'),
                        meta: { title: '可视化工作流编辑器' }
                    },
                    {
                        path: 'visual/:id',
                        name: 'VisualWorkflowEdit',
                        component: () => import('../views/Workflow/VisualWorkflowEditor.vue'),
                        meta: { title: '编辑可视化工作流' }
                    }
                ]
            },
            {
                path: 'skill',
                meta: { title: '技能管理' },
                redirect: '/dashboard/skill/management',
                children: [
                    {
                        path: 'management',
                        name: 'SkillManagement',
                        component: () => import('../views/Skill/SkillManagement.vue'),
                        meta: { title: '技能管理' }
                    }
                ]
            },
            {
                path: 'trace/:traceId',
                name: 'TraceViewer',
                component: () => import('../views/Trace/TraceViewer.vue'),
                meta: { title: '调用链路追踪' }
            },
            {
                path: 'system',
                meta: { title: '系统设置' },
                children: [
                    {
                        path: 'log-config',
                        name: 'LogConfig',
                        component: () => import('../views/System/LogConfig.vue'),
                        meta: { title: '日志配置' }
                    },
                    {
                        path: 'audit-logs',
                        name: 'AuditLogs',
                        component: () => import('../views/System/AuditLogs.vue'),
                        meta: { title: '审计日志', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'alerts',
                        name: 'AlertManagement',
                        component: () => import('../views/System/AlertManagement.vue'),
                        meta: { title: '告警管理', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'api-management',
                        name: 'ApiManagement',
                        component: () => import('../views/System/ApiManagement.vue'),
                        meta: { title: 'API端点管理', roles: ['ROLE_ADMIN'] }
                    },
                    {
                        path: 'api-keys',
                        name: 'ApiKeyManagement',
                        component: () => import('../views/System/ApiKeyManagement.vue'),
                        meta: { title: 'API密钥管理', roles: ['ROLE_ADMIN'] }
                    }
                ]
            },
            {
                path: 'monitor/dataflow/:traceId',
                name: 'DataFlow',
                component: () => import('../views/Monitor/DataFlow.vue'),
                meta: { title: '请求链路追踪' }
            },
            {
                path: 'stats/tokens',
                name: 'TokenStats',
                component: () => import('../views/Monitor/TokenStats.vue'),
                meta: { title: 'Token 统计分析', icon: 'Cpu' }
            },
            {
                path: 'monitor/latency',
                name: 'LatencyStats',
                component: () => import('../views/Monitor/LatencyStats.vue'),
                meta: { title: '延迟分析', icon: 'Connection' }
            }
        ]
    },
    // Workflow Chat App (Standalone)
    {
        path: '/chat/:id',
        name: 'WorkflowChat',
        component: () => import('@/views/Chat/WorkflowChat.vue'),
        meta: { title: 'Chat' }
    },
    // 404
    {
        path: '/:pathMatch(.*)*',
        redirect: '/dashboard/monitor'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// Navigation Guard
router.beforeEach((to, from, next) => {
    const token = Cookies.get('orin_token');
    const userStore = useUserStore();

    // Update Document Title
    if (to.meta.title) {
        document.title = `${to.meta.title} - ORIN Platform`;
    }

    // Check authentication
    if (!token && to.path.startsWith('/dashboard')) {
        // No token, trying to access protected dashboard -> redirect to login
        next('/login');
        return;
    }

    if (token && to.path === '/login') {
        // Have token, trying to access login -> redirect to monitor
        next('/dashboard/monitor');
        return;
    }

    // Check role-based permissions
    if (to.meta.roles && to.meta.roles.length > 0) {
        const requiredRoles = to.meta.roles;
        const hasPermission = userStore.hasAnyRole(requiredRoles);

        if (!hasPermission) {
            ElMessage.error('您没有权限访问此页面');
            // Redirect to monitor dashboard
            next('/dashboard/monitor');
            return;
        }
    }

    next();
})

export default router
