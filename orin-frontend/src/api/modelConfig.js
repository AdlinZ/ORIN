import request from '@/utils/request';

export const getModelConfig = () => {
    return request.get('/model-config');
};

export const updateModelConfig = (data) => {
    return request.put('/model-config', data);
};

export const testDifyConnection = (endpoint, apiKey) => {
    return request.post('/model-config/test-dify-connection', null, {
        params: { endpoint, apiKey }
    });
};

export const testSiliconFlowConnection = (endpoint, apiKey, model) => {
    return request.post('/model-config/test-silicon-flow-connection', null, {
        params: { endpoint, apiKey, model }
    });
};