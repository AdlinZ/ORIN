import request from '@/utils/request';

// ==================== 会话管理 ====================

export const getConversationList = (params) => {
    return request.get('/conversation-logs/grouped', { params });
};

export const getConversationHistory = (conversationId) => {
    return request.get(`/conversation-logs/${conversationId}/history`);
};

export const deleteConversation = (conversationId) => {
    return request.delete(`/conversation-logs/${conversationId}`);
};

export const clearConversationHistory = (agentId) => {
    return request.delete('/conversation-logs', { params: { agentId } });
};
