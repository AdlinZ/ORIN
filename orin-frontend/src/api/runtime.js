import request from '@/utils/request';

export const controlAgent = (agentId, action) => {
    return request.post(`/runtime/agents/${agentId}/control`, { action });
};

export const getAgentLogs = (agentId, config = {}) => {
    return request.get(`/runtime/agents/${agentId}/logs`, config);
};
