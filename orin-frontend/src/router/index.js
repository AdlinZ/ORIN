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
                    }
                ]
            },
            {
                path: 'knowledge',
                meta: { title: '知识库管理' },
                children: [
                    {
                        path: 'list',
                        name: 'KnowledgeList',
                        component: () => import('../views/Knowledge/KnowledgeList.vue'),
                        meta: { title: '知识库列表' }
                    }
                ]
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
                path: 'monitor/tokens',
                name: 'TokenStats',
                component: () => import('../views/Monitor/TokenStats.vue'),
                meta: { title: 'Token 统计分析', icon: 'Cpu' }
            },
            {
                path: 'monitor/latency',
                name: 'LatencyStats',
                component: () => import('../views/Monitor/LatencyStats.vue'),
                meta: { title: '延迟分析', icon: 'Connection' }
            },
            {
                path: 'model',
                meta: { title: '模型管理' },
                children: [
                    {
                        path: 'config',
                        name: 'ModelConfig',
                        component: () => import('../views/ModelConfig/ModelSystemConfig.vue'),
                        meta: { title: '系统配置' }
                    },
                    {
                        path: 'list',
                        name: 'ModelList',
                        component: () => import('../views/ModelConfig/ModelList.vue'),
                        meta: { title: '模型列表' }
                    }
                ]
            },
            {
                path: 'training',
                meta: { title: '模型训练' },
                children: [
                    {
                        path: 'files',
                        name: 'TrainingFiles',
                        component: () => import('../views/Training/FileList.vue'),
                        meta: { title: '训练文件管理' }
                    },
                    {
                        path: 'train',
                        name: 'ModelTrain',
                        component: () => import('../views/Training/ModelTrain.vue'),
                        meta: { title: '训练模型' }
                    },
                    {
                        path: 'checkpoints',
                        name: 'Checkpoints',
                        component: () => import('../views/Training/Checkpoints.vue'),
                        meta: { title: '检查点' }
                    }
                ]
            }
        ]
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
