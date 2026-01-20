import request from '@/utils/request'

export function onboardDeepSeekAgent(data) {
    return request({
        url: '/agents/onboard-deepseek',
        method: 'post',
        params: {
            endpointUrl: data.endpointUrl,
            apiKey: data.apiKey,
            model: data.model,
            name: data.agentName,
            temperature: data.temperature
        }
    })
}
