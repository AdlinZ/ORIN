import request from '@/utils/request'

/**
 * 系统配置接口
 */

// 获取系统基础配置
export function getSystemConfig() {
    return request({
        url: '/system/config',
        method: 'get'
    })
}

// 更新系统基础配置
export function updateSystemConfig(data) {
    return request({
        url: '/system/config',
        method: 'put',
        data
    })
}

// 获取供应商列表（按显示顺序）
export function getProviderList() {
    return request({
        url: '/system/providers',
        method: 'get'
    })
}

// 获取所有供应商（包括禁用的）
export function getAllProviders() {
    return request({
        url: '/system/providers/all',
        method: 'get'
    })
}

// 获取供应商名称映射
export function getProviderNameMap() {
    return request({
        url: '/system/providers/map',
        method: 'get'
    })
}

// 更新供应商配置
export function updateProvider(providerKey, data) {
    return request({
        url: `/system/providers/${providerKey}`,
        method: 'put',
        data
    })
}

// 批量更新显示顺序
export function updateDisplayOrders(orders) {
    return request({
        url: '/system/providers/display-order',
        method: 'put',
        data: orders
    })
}

// 启用/禁用供应商
export function setProviderEnabled(providerKey, enabled) {
    return request({
        url: `/system/providers/${providerKey}/enabled`,
        method: 'put',
        params: { enabled }
    })
}
