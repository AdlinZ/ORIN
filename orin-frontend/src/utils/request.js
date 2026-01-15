import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

// Create Axios Instance
const service = axios.create({
    baseURL: '/api/v1', // Global Base URL
    timeout: 10000 // Request Timeout
});

// 重试配置
const MAX_RETRIES = 2; // 最大重试次数
const RETRY_DELAY = 1000; // 重试延迟（毫秒）

// 延迟函数
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

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

        // 初始化重试计数
        if (!config.retryCount) {
            config.retryCount = 0;
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
    async (error) => {
        console.error('Request Error:', error);
        const config = error.config;

        // 判断是否应该重试
        const shouldRetry = (
            error.message.includes('Network Error') ||
            error.message.includes('timeout') ||
            error.code === 'ECONNABORTED' ||
            (error.response && error.response.status >= 500)
        ) && config.retryCount < MAX_RETRIES;

        if (shouldRetry) {
            config.retryCount += 1;
            console.log(`重试请求 (${config.retryCount}/${MAX_RETRIES}): ${config.url}`);

            // 等待后重试
            await delay(RETRY_DELAY * config.retryCount);

            // 重新发起请求
            return service(config);
        }

        // 如果重试失败或不需要重试，显示错误消息
        let message = '请求失败，请稍后重试';
        let shouldLogout = false;

        if (error.response) {
            const status = error.response.status;
            switch (status) {
                case 400:
                    message = '请求参数错误';
                    break;
                case 401:
                    message = '登录已过期，请重新登录';
                    shouldLogout = true;
                    break;
                case 403:
                    // 403 可能是 Token 过期或权限不足
                    // 检查错误信息中是否包含 Token 相关关键词
                    const errorMsg = error.response.data?.message || '';
                    if (errorMsg.toLowerCase().includes('token') ||
                        errorMsg.toLowerCase().includes('expired') ||
                        errorMsg.toLowerCase().includes('invalid')) {
                        message = '登录已过期，请重新登录';
                        shouldLogout = true;
                    } else {
                        message = '权限不足，拒绝访问';
                    }
                    break;
                case 404:
                    message = '请求资源不存在';
                    break;
                case 500:
                    message = '服务器内部错误';
                    break;
                default:
                    message = `请求失败 (${status})`;
            }
        } else if (error.message.includes('timeout')) {
            message = '请求超时，请检查网络连接';
        } else if (error.message.includes('Network Error')) {
            message = '网络连接失败，请检查网络后重试';
        }

        // 只在最后一次重试失败后才显示错误消息
        if (config.retryCount >= MAX_RETRIES || !shouldRetry) {
            ElMessage.error(message);
        }

        // 如果需要登出，清除 Token 并跳转到登录页
        if (shouldLogout) {
            const userStore = useUserStore();
            userStore.logout();

            // 延迟跳转，让用户看到错误提示
            setTimeout(() => {
                // 使用 window.location 确保完全刷新
                if (window.location.pathname !== '/login') {
                    window.location.href = '/login';
                }
            }, 1500);
        }

        return Promise.reject(error);
    }
);

export default service;
