import request from '../utils/request';

/**
 * 用户登录
 */
export function login(data) {
    return request({
        url: '/auth/login',
        method: 'post',
        data
    });
}

/**
 * 用户自助注册
 */
export function register(data) {
    return request({
        url: '/auth/register',
        method: 'post',
        data
    });
}

/**
 * 注册能力状态
 */
export function getRegistrationStatus() {
    return request({
        url: '/auth/registration-status',
        method: 'get'
    });
}

/**
 * 刷新Token
 * 使用当前有效的Token获取新Token
 */
export function refreshToken() {
    return request({
        url: '/auth/refresh',
        method: 'post'
    });
}

/**
 * 验证Token
 * 检查当前Token是否有效
 */
export function validateToken() {
    return request({
        url: '/auth/validate',
        method: 'get'
    });
}

// ==================== 用户验证 ====================

export const sendVerificationCode = (email, type = 'bind') => {
    return request.post('/auth/send-code', { email, type });
};

export const verifyCode = (email, code) => {
    return request.post('/auth/verify-code', { email, code });
};

export const bindEmail = (userId, email, code) => {
    return request.post('/auth/bind-email', { userId, email, code });
};
