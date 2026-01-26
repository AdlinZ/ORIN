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

export function createWorkflow(data) {
    return request({
        baseURL: '',
        url: '/api/workflows',
        method: 'post',
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
