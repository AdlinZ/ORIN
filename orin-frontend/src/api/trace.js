import request from '@/utils/request';

export const getTrace = (traceId) => {
    return request.get(`/traces/${traceId}`);
};

export const getTraceByInstance = (instanceId) => {
    return request.get(`/traces/instance/${instanceId}`);
};

export const getTraceStats = (traceId) => {
    return request.get(`/traces/${traceId}/stats`);
};

export const searchTraces = (traceId, params = {}) => {
    return request.get('/traces/search', {
        params: { traceId, ...params }
    });
};

export const getRecentTraces = (size = 20) => {
    return request.get('/traces/recent', {
        params: { size }
    });
};

export const getTraceLink = (traceId) => {
    return request.get(`/traces/${traceId}/link`);
};

export const getDataFlow = (traceId) => {
    return request.get(`/dataflow/${traceId}`);
};
