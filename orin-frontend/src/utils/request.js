import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import Cookies from 'js-cookie';

// Create Axios Instance
const service = axios.create({
    baseURL: '/api/v1', // Global Base URL
    timeout: 60000 // Global Default Timeout (60s)
});

// 重试配置
const MAX_RETRIES = 2; // 最大重试次数
const RETRY_DELAY = 1000; // 重试延迟（毫秒）

// Token 刷新状态
let isRefreshing = false;
let refreshSubscribers = [];

// 延迟函数
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// 订阅 Token 刷新完成
function subscribeTokenRefresh(callback) {
    refreshSubscribers.push(callback);
}

// 通知所有订阅者 Token 已刷新
function onTokenRefreshed(newToken) {
    refreshSubscribers.forEach(callback => callback(newToken));
    refreshSubscribers = [];
}

// 通知订阅者刷新失败
function onTokenRefreshFailed() {
    refreshSubscribers.forEach(callback => callback(null));
    refreshSubscribers = [];
}

// 刷新 Token
async function refreshToken() {
    const userStore = useUserStore();
    const currentToken = userStore.token;

    if (!currentToken) {
        throw new Error('No token available');
    }

    // 直接调用刷新 API
    const response = await axios.post('/api/v1/auth/refresh', null, {
        headers: {
            'Authorization': `Bearer ${currentToken}`
        }
    });

    const newToken = response.data.token;
    if (newToken) {
        // 更新 Cookie 和 Store
        Cookies.set('orin_token', newToken, { expires: 7 });
        userStore.token = newToken;
        return newToken;
    }

    throw new Error('Refresh token failed');
}

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
        return response.data;
    },
    async (error) => {
        console.error('Request Error:', error);
        const config = error.config;
        if (error.response && error.response.status === 401) {
            console.warn(`401 Unauthorized for URL: ${config.url}`);
        }

        // 防止刷新请求本身进入无限循环
        if (config.url === '/auth/refresh') {
            return Promise.reject(error);
        }

        // 处理 401 错误 - 尝试刷新 Token
        if (error.response && error.response.status === 401 && !config._retry) {
            config._retry = true;

            if (!isRefreshing) {
                isRefreshing = true;

                try {
                    const newToken = await refreshToken();
                    isRefreshing = false;
                    onTokenRefreshed(newToken);

                    // 使用新 Token 重试原请求
                    config.headers['Authorization'] = `Bearer ${newToken}`;
                    return service(config);

                } catch (refreshError) {
                    isRefreshing = false;
                    onTokenRefreshFailed();

                    // 刷新失败，需要重新登录
                    const userStore = useUserStore();
                    userStore.logout();

                    ElMessage.error('登录已过期，请重新登录');

                    setTimeout(() => {
                        if (window.location.pathname !== '/login') {
                            window.location.href = '/login';
                        }
                    }, 1500);

                    return Promise.reject(refreshError);
                }
            } else {
                // 正在刷新中，等待刷新完成
                return new Promise((resolve, reject) => {
                    subscribeTokenRefresh((newToken) => {
                        if (newToken) {
                            config.headers['Authorization'] = `Bearer ${newToken}`;
                            resolve(service(config));
                        } else {
                            reject(error);
                        }
                    });
                });
            }
        }

        // 判断是否应该重试（非401错误）
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

        if (error.response) {
            const status = error.response.status;
            switch (status) {
                case 400:
                    message = '请求参数错误';
                    break;
                case 403:
                    message = '权限不足，拒绝访问';
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

        return Promise.reject(error);
    }
);

export default service;

