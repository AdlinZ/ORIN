import request from '@/utils/request'

export function getWorkflows() {
    return request({
        url: '/api/workflows',
        method: 'get'
    })
}

export function getWorkflow(id) {
    return request({
        url: `/api/workflows/${id}`,
        method: 'get'
    })
}

export function createWorkflow(data) {
    return request({
        url: '/api/workflows',
        method: 'post',
        data
    })
}

export function executeWorkflow(id, inputs) {
    return request({
        url: `/api/workflows/${id}/execute`,
        method: 'post',
        data: inputs
    })
}

export function getWorkflowInstances(id) {
    return request({
        url: `/api/workflows/${id}/instances`,
        method: 'get'
    })
}
