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
