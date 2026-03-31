import request from '@/utils/request'

// ==================== Overview ====================
export const getGatewayOverview = () => {
    return request.get('/system/gateway/overview')
}

export const getGatewayTrends = (hours = 1) => {
    return request.get('/system/gateway/overview/trends', { params: { hours } })
}

// ==================== Routes ====================
export const getRoutes = () => {
    return request.get('/system/gateway/routes')
}

export const getRoute = (id) => {
    return request.get(`/system/gateway/routes/${id}`)
}

export const createRoute = (data) => {
    return request.post('/system/gateway/routes', data)
}

export const updateRoute = (id, data) => {
    return request.put(`/system/gateway/routes/${id}`, data)
}

export const patchRoute = (id, data) => {
    return request.patch(`/system/gateway/routes/${id}`, data)
}

export const deleteRoute = (id) => {
    return request.delete(`/system/gateway/routes/${id}`)
}

export const testRoute = (data) => {
    return request.post('/system/gateway/routes/test', data)
}

// ==================== Services ====================
export const getServices = () => {
    return request.get('/system/gateway/services')
}

export const getService = (id) => {
    return request.get(`/system/gateway/services/${id}`)
}

export const createService = (data) => {
    return request.post('/system/gateway/services', data)
}

export const updateService = (id, data) => {
    return request.put(`/system/gateway/services/${id}`, data)
}

export const deleteService = (id) => {
    return request.delete(`/system/gateway/services/${id}`)
}

// ==================== Service Instances ====================
export const getServiceInstances = (serviceId) => {
    return request.get(`/system/gateway/services/${serviceId}/instances`)
}

export const createServiceInstance = (serviceId, data) => {
    return request.post(`/system/gateway/services/${serviceId}/instances`, data)
}

export const updateServiceInstance = (serviceId, instanceId, data) => {
    return request.put(`/system/gateway/services/${serviceId}/instances/${instanceId}`, data)
}

export const deleteServiceInstance = (serviceId, instanceId) => {
    return request.delete(`/system/gateway/services/${serviceId}/instances/${instanceId}`)
}

export const triggerHealthCheck = (serviceId, instanceId) => {
    return request.post(`/system/gateway/services/${serviceId}/instances/${instanceId}/health-check`)
}

// ==================== ACL Rules ====================
export const getAclRules = () => {
    return request.get('/system/gateway/acl')
}

export const getAclRule = (id) => {
    return request.get(`/system/gateway/acl/${id}`)
}

export const createAclRule = (data) => {
    return request.post('/system/gateway/acl', data)
}

export const updateAclRule = (id, data) => {
    return request.put(`/system/gateway/acl/${id}`, data)
}

export const deleteAclRule = (id) => {
    return request.delete(`/system/gateway/acl/${id}`)
}

export const testAclRule = (data) => {
    return request.post('/system/gateway/acl/test', data)
}

// ==================== Policies ====================
export const getAllPolicies = () => {
    return request.get('/system/gateway/policies')
}

export const getRateLimitPolicies = () => {
    return request.get('/system/gateway/policies/rate-limit')
}

export const createRateLimitPolicy = (data) => {
    return request.post('/system/gateway/policies/rate-limit', data)
}

export const updateRateLimitPolicy = (id, data) => {
    return request.put(`/system/gateway/policies/rate-limit/${id}`, data)
}

export const deleteRateLimitPolicy = (id) => {
    return request.delete(`/system/gateway/policies/rate-limit/${id}`)
}

export const getCircuitBreakerPolicies = () => {
    return request.get('/system/gateway/policies/circuit-breaker')
}

export const createCircuitBreakerPolicy = (data) => {
    return request.post('/system/gateway/policies/circuit-breaker', data)
}

export const updateCircuitBreakerPolicy = (id, data) => {
    return request.put(`/system/gateway/policies/circuit-breaker/${id}`, data)
}

export const deleteCircuitBreakerPolicy = (id) => {
    return request.delete(`/system/gateway/policies/circuit-breaker/${id}`)
}

export const getRetryPolicies = () => {
    return request.get('/system/gateway/policies/retry')
}

export const createRetryPolicy = (data) => {
    return request.post('/system/gateway/policies/retry', data)
}

export const updateRetryPolicy = (id, data) => {
    return request.put(`/system/gateway/policies/retry/${id}`, data)
}

export const deleteRetryPolicy = (id) => {
    return request.delete(`/system/gateway/policies/retry/${id}`)
}

// ==================== Audit Logs ====================
export const getGatewayAuditLogs = (params) => {
    return request.get('/system/gateway/audit-logs', { params })
}
