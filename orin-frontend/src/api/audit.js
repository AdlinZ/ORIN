import request from '@/utils/request';

// 获取审计日志列表
export const getAuditLogs = (params) => {
    return request.get('/audit-logs', { params });
};

// 按会话获取审计日志
export const getAuditLogsByConversation = (conversationId) => {
    return request.get(`/audit-logs/conversation/${conversationId}`);
};
