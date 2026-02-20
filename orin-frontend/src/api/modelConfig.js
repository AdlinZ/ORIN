import request from '@/utils/request'

export function getModelConfig() {
    return request({
        url: '/model-config',
        method: 'get'
    })
}

export function updateModelConfig(data) {
    return request({
        url: '/model-config',
        method: 'put',
        data
    })
}

export function testDifyConnection(endpoint, apiKey) {
    return request({
        url: '/model-config/test-dify-connection',
        method: 'post',
        params: { endpoint, apiKey }
    })
}

export function testSiliconFlowConnection(endpoint, apiKey, model) {
    return request({
        url: '/model-config/test-silicon-flow-connection',
        method: 'post',
        params: { endpoint, apiKey, model }
    })
}

export function testZhipuConnection(endpoint, apiKey, model) {
    return request({
        url: '/model-config/test-zhipu-connection',
        method: 'post',
        params: { endpoint, apiKey, model }
    })
}

export function testDeepSeekConnection(endpoint, apiKey, model) {
    return request({
        url: '/model-config/test-deepseek-connection',
        method: 'post',
        params: { endpoint, apiKey, model }
    })
}

export function testMinimaxConnection(endpoint, apiKey, model) {
    return request({
        url: '/model-config/test-minimax-connection',
        method: 'post',
        params: { endpoint, apiKey, model }
    })
}
