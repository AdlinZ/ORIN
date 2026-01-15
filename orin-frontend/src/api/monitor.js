import request from '@/utils/request';

export const getGlobalSummary = (config = {}) => {
    return request.get('/monitor/dashboard/summary', config);
};

export const getAgentList = (config = {}) => {
    // Current backend path
    return request.get('/monitor/agents/list', config);
};

export const getAgentMetrics = (agentId, start, end, config = {}) => {
    return request.get(`/monitor/agents/${agentId}/metrics`, {
        params: { start, end },
        ...config
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

// Token 统计相关 API
export const getTokenStats = (config = {}) => {
    return request.get('/monitor/tokens/stats', config);
};

export const getTokenHistory = (params = {}, config = {}) => {
    return request.get('/monitor/tokens/history', {
        params,
        ...config
    });
};

export const getTokenTrend = (period = 'daily', config = {}) => {
    return request.get('/monitor/tokens/trend', {
        params: { period },
        ...config
    });
};

// Latency 统计相关 API
export const getLatencyStats = (config = {}) => {
    return request.get('/monitor/latency/stats', config);
};

export const getLatencyHistory = (params = {}, config = {}) => {
    return request.get('/monitor/latency/history', {
        params,
        ...config
    });
};

export const getLatencyTrend = (period = 'daily', config = {}) => {
    return request.get('/monitor/latency/trend', {
        params: { period },
        ...config
    });
};
