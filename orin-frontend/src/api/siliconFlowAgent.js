import request from '@/utils/request';

export const onboardSiliconFlowAgent = (endpoint, apiKey, model, name) => {
    return request.post('/agents/onboard-silicon-flow', null, {
        params: { endpointUrl: endpoint, apiKey: apiKey, model: model, name: name }
    });
};

export const sendSiliconFlowMessage = (endpoint, apiKey, model, message) => {
    return request.post('/silicon-flow-agent/send-message', null, {
        params: { endpoint, apiKey, model, message }
    });
};

export const sendSiliconFlowMessageWithParams = (
    endpoint,
    apiKey,
    model,
    message,
    temperature = 0.7,
    topP = 0.7,
    maxTokens = 500
) => {
    return request.post('/silicon-flow-agent/send-message-with-params', null, {
        params: {
            endpoint,
            apiKey,
            model,
            message,
            temperature,
            topP,
            maxTokens
        }
    });
};