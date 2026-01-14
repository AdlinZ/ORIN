import request from '@/utils/request';

export const getGlobalSummary = () => {
    return request.get('/monitor/dashboard/summary');
};

export const getAgentList = () => {
    // Current backend path
    return request.get('/monitor/agents/list');
};

export const getAgentMetrics = (agentId, start, end) => {
    return request.get(`/monitor/agents/${agentId}/metrics`, {
        params: { start, end }
    });
};

export const triggerMockData = () => {
    return request.post('/monitor/mock/trigger');
};

export const testDifyConnection = (endpointUrl, apiKey) => {
    return request.post('/monitor/dify/test-connection', null, {
        params: { endpointUrl, apiKey }
    });
};

export const getDifyApps = (endpointUrl, apiKey) => {
    return request.get('/monitor/dify/apps', {
        params: { endpointUrl, apiKey }
    });
};
