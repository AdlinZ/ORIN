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

export const getDataFlow = (traceId) => {
    return request.get(`/dataflow/${traceId}`);
};
