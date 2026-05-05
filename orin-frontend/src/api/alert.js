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

// ==================== 告警通知配置 ====================

export const testNotificationChannel = (channel) => {
    return request.post('/alerts/notification-config/test', { channel });
};

export const getNotificationConfig = () => {
    return request.get('/alerts/notification-config');
};

export const saveNotificationConfig = (data) => {
    return request.post('/alerts/notification-config', data);
};

export const getNotificationStatus = () => {
    return request.get('/alerts/notification-config/status');
};

export const testNotification = (channel) => {
    return request.post('/alerts/notification-config/test', { channel });
};

// ==================== 通知中心 ====================

// 标记单条通知为已读
export const markNotificationAsRead = (id) => {
    return request.post(`/alerts/history/${id}/resolve`);
};

// 标记全部通知为已读
export const markAllNotificationsAsRead = () => {
    return request.post('/alerts/history/resolve-all');
};

// 清空所有通知
export const clearAllNotifications = () => {
    return request.delete('/alerts/history/clear-all');
};

// 获取未读通知数量
export const getUnreadNotificationCount = () => {
    return request.get('/alerts/history/unread-count');
};
