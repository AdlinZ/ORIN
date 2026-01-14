import request from '../utils/request';

export function getModelList() {
    return request({
        url: '/models',
        method: 'get'
    });
}

export function saveModel(data) {
    return request({
        url: '/models',
        method: 'post',
        data
    });
}

export function deleteModel(id) {
    return request({
        url: `/models/${id}`,
        method: 'delete'
    });
}

export function toggleModelStatus(id) {
    return request({
        url: `/models/${id}/toggle`,
        method: 'patch'
    });
}
