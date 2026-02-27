import request from '@/utils/request';

// ZeroClaw 配置管理
export const getZeroClawConfigs = (config = {}) => {
    return request.get('/zeroclaw/configs', config);
};

export const createZeroClawConfig = (data, config = {}) => {
    return request.post('/zeroclaw/configs', data, config);
};

export const updateZeroClawConfig = (id, data, config = {}) => {
    return request.put(`/zeroclaw/configs/${id}`, data, config);
};

export const deleteZeroClawConfig = (id, config = {}) => {
    return request.delete(`/zeroclaw/configs/${id}`, config);
};

export const testZeroClawConnection = (data, config = {}) => {
    return request.post('/zeroclaw/configs/test-connection', data, config);
};

// ZeroClaw 状态监控
export const getZeroClawStatus = (config = {}) => {
    return request.get('/zeroclaw/status', config);
};

// ZeroClaw 智能分析
export const performZeroClawAnalysis = (data, config = {}) => {
    return request.post('/zeroclaw/analyze', data, config);
};

export const getZeroClawReports = (params = {}, config = {}) => {
    return request.get('/zeroclaw/reports', {
        params,
        ...config
    });
};

export const getZeroClawReportsByAgent = (agentId, config = {}) => {
    return request.get(`/zeroclaw/reports/agent/${agentId}`, config);
};

export const generateZeroClawDailyReport = (config = {}) => {
    return request.post('/zeroclaw/reports/daily', null, config);
};

// ZeroClaw 主动维护
export const executeZeroClawSelfHealing = (data, config = {}) => {
    return request.post('/zeroclaw/self-healing', data, config);
};

export const getZeroClawSelfHealingLogs = (params = {}, config = {}) => {
    return request.get('/zeroclaw/self-healing/logs', {
        params,
        ...config
    });
};
