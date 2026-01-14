import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

// Create Axios Instance
const service = axios.create({
    baseURL: '/api/v1', // Global Base URL
    timeout: 10000 // Request Timeout
});

// Request Interceptor
service.interceptors.request.use(
    (config) => {
        // In real app, we get token from Store/Cookie
        const userStore = useUserStore();
        const token = userStore.token;
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token;
        }

        // Also inject User-Id if available (for legacy support)
        const userId = userStore.userId;
        if (userId) {
            config.headers['X-User-Id'] = userId;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response Interceptor
service.interceptors.response.use(
    (response) => {
        // If backend returns custom code, handle it here
        // For now, we assume 200 is success
        return response;
    },
    (error) => {
        console.error('Request Error:', error);
        let message = '请求失败，请稍后重试';

        if (error.response) {
            const status = error.response.status;
            switch (status) {
                case 400: message = '请求参数错误'; break;
                case 401: message = '未授权，请重新登录'; break;
                case 403: message = '拒绝访问'; break;
                case 404: message = '请求资源不存在'; break;
                case 500: message = '服务器内部错误'; break;
                default: message = `请求失败 (${status})`;
            }
        } else if (error.message.includes('timeout')) {
            message = '请求超时，请检查网络';
        } else if (error.message.includes('Network Error')) {
            message = '网络连接失败';
        }

        ElMessage.error(message);
        return Promise.reject(error);
    }
);

export default service;
