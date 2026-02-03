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

export const chatAgent = (agentId, message, fileId, overrideSystemPrompt, conversationId, enableThinking, thinkingBudget) => {
    const data = { message };
    if (fileId) {
        data.file_id = fileId;
    }
    if (overrideSystemPrompt) {
        data.system_prompt = overrideSystemPrompt;
    }
    if (conversationId) {
        data.conversation_id = conversationId;
    }
    if (enableThinking !== undefined) {
        data.enable_thinking = enableThinking;
    }
    if (thinkingBudget !== undefined) {
        data.thinking_budget = thinkingBudget;
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

export const uploadMultimodalFile = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post('/multimodal/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
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


export const getJobStatus = (agentId, jobId) => {
    return request.get(`/agents/${agentId}/jobs/${jobId}`);
};

export const getGroupedConversationLogs = (page = 0, size = 15) => {
    return request.get('/conversation-logs/grouped', { params: { page, size } });
};

export const getConversationHistory = (conversationId) => {
    return request.get(`/conversation-logs/${conversationId}/history`);
};
