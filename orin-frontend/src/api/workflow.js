import request from '@/utils/request'

export function getWorkflows() {
    return request({
        baseURL: '',
        url: '/api/workflows',
        method: 'get'
    })
}

export function getWorkflow(id) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}`,
        method: 'get'
    })
}

export function getWorkflowInstance(instanceId) {
    return request({
        baseURL: '',
        url: `/api/workflows/instances/${instanceId}`,
        method: 'get'
    })
}

export function createWorkflow(data) {
    return request({
        baseURL: '',
        url: '/api/workflows',
        method: 'post',
        data
    })
}

export function updateWorkflow(id, data) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}`,
        method: 'put',
        data
    })
}

export function executeWorkflow(id, inputs) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}/execute`,
        method: 'post',
        data: inputs
    })
}

export function runWorkflowPreview(payload) {
    return request({
        baseURL: '',
        url: '/api/v1/workflow/run',
        method: 'post',
        data: payload
    })
}

export function getWorkflowInstances(id) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}/instances`,
        method: 'get'
    })
}

export function importWorkflow(formData) {
    return request({
        baseURL: '',
        url: '/api/workflows/import/dify',
        method: 'post',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}

export function deleteWorkflow(id) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}`,
        method: 'delete'
    })
}

export function getWorkflowAccess(id) {
    return request({
        baseURL: '',
        url: `/api/workflows/${id}/access`,
        method: 'get'
    })
}

export function generateAIWorkflow(prompt) {
    return request({
        baseURL: '',
        url: '/api/workflows/generate',
        method: 'post',
        data: { prompt }
    })
}

