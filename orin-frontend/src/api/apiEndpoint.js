import request from '@/utils/request'

/**
 * API端点管理接口
 */

// 获取所有API端点
export function getAllEndpoints() {
    return request({
        url: '/api-endpoints',
        method: 'get'
    })
}

// 获取API端点详情
export function getEndpointById(id) {
    return request({
        url: `/api-endpoints/${id}`,
        method: 'get'
    })
}

// 创建API端点
export function createEndpoint(data) {
    return request({
        url: '/api-endpoints',
        method: 'post',
        data
    })
}

// 更新API端点
export function updateEndpoint(id, data) {
    return request({
        url: `/api-endpoints/${id}`,
        method: 'put',
        data
    })
}

// 删除API端点
export function deleteEndpoint(id) {
    return request({
        url: `/api-endpoints/${id}`,
        method: 'delete'
    })
}

// 启用/禁用API端点
export function toggleEndpoint(id, enabled) {
    return request({
        url: `/api-endpoints/${id}/toggle`,
        method: 'patch',
        params: { enabled }
    })
}

// 获取API统计信息
export function getEndpointStats() {
    return request({
        url: '/api-endpoints/stats',
        method: 'get'
    })
}

// 初始化默认API端点
export function initializeDefaultEndpoints() {
    return request({
        url: '/api-endpoints/initialize',
        method: 'post'
    })
}
