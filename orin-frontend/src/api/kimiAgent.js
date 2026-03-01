import request from '@/utils/request'

export function onboardKimiAgent(endpoint, apiKey, model, name) {
    return request.post('/agents/onboard-kimi', null, {
        params: {
            endpointUrl: endpoint,
            apiKey: apiKey,
            model: model,
            name: name
        }
    })
}
