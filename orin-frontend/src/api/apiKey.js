import request from '@/utils/request'

/**
 * API密钥管理接口
 */

// 获取所有API密钥
export function getAllApiKeys() {
    return request({
        url: '/api-keys',
        method: 'get'
    })
}

// 创建API密钥
export function createApiKey(data) {
    return request({
        url: '/api-keys',
        method: 'post',
        data
    })
}

// 启用API密钥
export function enableApiKey(id) {
    return request({
        url: `/api-keys/${id}/enable`,
        method: 'patch'
    })
}

// 禁用API密钥
export function disableApiKey(id) {
    return request({
        url: `/api-keys/${id}/disable`,
        method: 'patch'
    })
}

// 删除API密钥
export function deleteApiKey(keyId) {
    return request({
        url: `/api-keys/${keyId}`,
        method: 'delete'
    })
}

// 重置月度配额
export function resetQuota(keyId) {
    return request({
        url: `/api/v1/api-keys/${keyId}/reset-quota`,
        method: 'post'
    })
}
