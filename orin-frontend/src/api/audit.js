import request from '@/utils/request';

// 获取审计日志列表
export const getAuditLogs = (params) => {
    return request.get('/audit-logs', { params });
};

// 按会话获取审计日志
export const getAuditLogsByConversation = (conversationId) => {
    return request.get(`/audit-logs/conversation/${conversationId}`);
};

// 获取网关审计日志
export const getGatewayAuditLogs = (params) => {
    return request.get('/audit/logs', { params });
};

// 获取日志配置
export const getLogConfig = () => {
    return request.get('/system/log-config');
};

// 更新单项日志配置
export const updateLogConfig = (configKey, value) => {
    return request.put(`/system/log-config/${configKey}`, { value });
};

// 获取日志统计
export const getLogConfigStats = () => {
    return request.get('/system/log-config/stats');
};

// 清理日志
export const cleanupLogConfig = (days) => {
    return request.post('/system/log-config/cleanup', null, { params: { days } });
};

// 获取 logger 运行级别
export const getLoggerLevels = () => {
    return request.get('/system/log-config/loggers');
};

// 更新 logger 运行级别
export const updateLoggerLevel = (loggerName, level) => {
    return request.put(`/system/log-config/loggers/${loggerName}`, { level });
};

// 重置单个 logger
export const resetLoggerLevel = (loggerName) => {
    return request.delete(`/system/log-config/loggers/${loggerName}`);
};

// 重置所有 logger
export const resetAllLoggerLevels = () => {
    return request.post('/system/log-config/loggers/reset-all');
};
