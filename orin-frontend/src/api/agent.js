import request from '@/utils/request';

export const onboardAgent = (data) => {
    return request.post('/agents/onboard', data);
};

export const getAgentList = () => {
    return request.get('/agents');
};

export const getAgentLogs = (agentId) => {
    return request.get(`/runtime/agents/${agentId}/logs`);
};

export const controlAgent = (agentId, action) => {
    return request.post(`/runtime/agents/${agentId}/control`, { action });
};

export const updateAgent = (agentId, data) => {
    return request.put(`/agents/${agentId}`, data);
};

export const chatAgent = (agentId, message, fileId) => {
    const data = { message };
    if (fileId) {
        data.file_id = fileId;
    }
    // Extended timeout for thinking models (e.g., DeepSeek-R1)
    return request.post(`/agents/${agentId}/chat`, data, {
        timeout: 120000 // 120 seconds for thinking models
    });
};

export const uploadFile = (file, agentId) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('purpose', 'batch');
    if (agentId) {
        formData.append('agentId', agentId);
    }
    return request.post('/files', formData);
};

export const getAgentAccessProfile = (agentId) => {
    return request.get(`/agents/${agentId}/access-profile`);
};

export const getAgentMetadata = (agentId) => {
    return request.get(`/agents/${agentId}/metadata`);
};

export const deleteAgent = (agentId) => {
    return request.delete(`/agents/${agentId}`);
};
