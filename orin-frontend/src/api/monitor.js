import request from '@/utils/request';

export const getGlobalSummary = (config = {}) => {
    return request.get('/monitor/dashboard/summary', config);
};

export const getAgentList = (config = {}) => {
    // Current backend path
    return request.get('/monitor/agents/list', config);
};

export const getAgentMetrics = (agentId, start, end, config = {}) => {
    return request.get(`/monitor/agents/${agentId}/metrics`, {
        params: { start, end },
        ...config
    });
};

export const triggerMockData = () => {
    return request.post('/monitor/mock/trigger');
};

export const testDifyConnection = (endpointUrl, apiKey) => {
    return request.post('/monitor/dify/test-connection', null, {
        params: { endpointUrl, apiKey }
    });
};

export const getDifyApps = (endpointUrl, apiKey) => {
    return request.get('/monitor/dify/apps', {
        params: { endpointUrl, apiKey }
    });
};

// Token 统计相关 API
export const getTokenStats = (config = {}) => {
    return request.get('/monitor/tokens/stats', config);
};

export const getTokenHistory = (params = {}, config = {}) => {
    return request.get('/monitor/tokens/history', {
        params,
        ...config
    });
};

export const getTokenDistribution = (params = {}, config = {}) => {
    return request.get('/monitor/tokens/distribution', {
        params,
        ...config
    });
};

export const getCostDistribution = (params = {}, config = {}) => {
    return request.get('/monitor/costs/distribution', {
        params,
        ...config
    });
};

export const getTokenTrend = (period = 'daily', config = {}) => {
    return request.get('/monitor/tokens/trend', {
        params: { period },
        ...config
    });
};

// Latency 统计相关 API
export const getLatencyStats = () => {
    return request.get('/monitor/latency/stats');
};

export const getLatencyHistory = (params = {}, config = {}) => {
    return request.get('/monitor/latency/history', {
        params,
        ...config
    });
};

export const getLatencyTrend = (period = 'daily', config = {}) => {
    return request.get('/monitor/latency/trend', {
        params: { period },
        ...config
    });
};

// Pricing Config API
export const getPricingConfig = () => {
    return request.get('/pricing/config');
};

export const savePricingConfig = (data) => {
    return request.post('/pricing/config', data);
};

export const deletePricingConfig = (id) => {
    return request.delete(`/pricing/config/${id}`);
};

export const getServerHardware = () => {
    return request.get('/monitor/server-hardware');
};

// Server Hardware History APIs
export const getServerHardwareHistory = (params = {}) => {
    return request.get('/monitor/server-hardware/history', { params });
};

export const getServerHardwareTrend = (period = '1h') => {
    return request.get('/monitor/server-hardware/trend', { params: { period } });
};

export const getServerHardwareStats = () => {
    return request.get('/monitor/server-hardware/stats');
};

export const collectServerHardware = () => {
    return request.post('/monitor/server-hardware/collect');
};

// Token Stats Dashboard APIs
export const getTokenByDayOfWeek = (config = {}) => {
    return request.get('/monitor/tokens/by-day-of-week', config);
};

export const getTokenByHour = (config = {}) => {
    return request.get('/monitor/tokens/by-hour', config);
};

export const getTokenByType = (config = {}) => {
    return request.get('/monitor/tokens/by-type', config);
};

export const getSessions = (limit = 20, config = {}) => {
    return request.get('/monitor/sessions', {
        params: { limit },
        ...config
    });
};

// Daily token trend
export const getDailyTokenTrend = (period = 'daily', config = {}) => {
    return request.get('/monitor/tokens/trend', {
        params: { period },
        ...config
    });
};

// 健康状态概览 API
export const getAgentHealthOverview = (config = {}) => {
    return request.get('/monitor/agents/health-overview', config);
};

// ==================== 数据流追踪 ====================

export const getDataFlow = (traceId) => {
    return request.get(`/dataflow/${traceId}`);
};

export const getTraceInstance = (instanceId) => {
    return request.get(`/traces/instance/${instanceId}`);
};

// ==================== 健康检查 ====================

export const getHealthStatus = () => {
    return request.get('/health');
};

// ==================== 系统健康检查 ====================

export const getSystemHealth = () => {
    return request.get('/health');
};

export const getSchedulerStatus = () => {
    return request.get('/scheduler/status');
};

// ==================== Local Server Info ====================

export const getLocalServerInfo = (config = {}) => {
    return request.get('/monitor/local-server-info', config);
};

export const getPrometheusConfig = (config = {}) => {
    return request.get('/monitor/prometheus/config', config);
};

export const updatePrometheusConfig = (data) => {
    return request.post('/monitor/prometheus/config', data);
};

export const testPrometheusConnection = (config = {}) => {
    return request.get('/monitor/prometheus/test', config);
};

export const getPrometheusServerStatus = (config = {}) => {
    return request.get('/monitor/prometheus/server-status', config);
};

// ==================== 链路追踪查询 ====================

export const getTraceById = (traceId, config = {}) => {
    return request.get(`/monitor/traces/${traceId}`, config);
};

export const getCallSuccessRate = (params = {}, config = {}) => {
    return request.get('/monitor/stats/success-rate', {
        params,
        ...config
    });
};

export const getErrorDistribution = (params = {}, config = {}) => {
    return request.get('/monitor/stats/error-distribution', {
        params,
        ...config
    });
};

// ==================== 限流配置 ====================

export const getRateLimitConfig = (config = {}) => {
    return request.get('/admin/rate-limit/config', config);
};

export const updateRateLimitConfig = (data, config = {}) => {
    return request.put('/admin/rate-limit/config', data, config);
};

// ==================== Langfuse 可观测性 ====================

export const getLangfuseStatus = (config = {}) => {
    return request.get('/observability/langfuse/status', config);
};
