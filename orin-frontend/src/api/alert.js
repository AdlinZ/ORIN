import request from '@/utils/request';

// ==================== 告警规则 ====================

export const getAlertRules = (params = {}) => {
    return request.get('/alerts/rules', { params });
};

export const createAlertRule = (data) => {
    return request.post('/alerts/rules', data);
};

export const updateAlertRule = (id, data) => {
    return request.put(`/alerts/rules/${id}`, data);
};

export const deleteAlertRule = (id) => {
    return request.delete(`/alerts/rules/${id}`);
};

export const testAlertRule = (id) => {
    return request.post(`/alerts/rules/${id}/test`);
};

// ==================== 告警历史 ====================

export const getAlertHistory = (params = {}) => {
    return request.get('/alerts/history', { params });
};

export const getAgentAlertHistory = (agentId, params = {}) => {
    return request.get(`/alerts/history/agent/${agentId}`, { params });
};

export const resolveAlert = (id) => {
    return request.post(`/alerts/history/${id}/resolve`);
};

// ==================== 告警统计 ====================

export const getAlertStats = () => {
    return request.get('/alerts/stats');
};

// ==================== 手动触发告警 ====================

export const triggerAlert = (data) => {
    return request.post('/alerts/trigger', data);
};
