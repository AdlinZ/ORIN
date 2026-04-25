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

// Pricing Config API — re-exported from dedicated module for backward compatibility
export { getPricingConfig, getPricingByProvider, savePricingConfig, deletePricingConfig } from '@/api/pricing';

export const getServerHardware = () => {
    return request.get('/monitor/server-hardware');
};

// Server Hardware History APIs
export const getServerNodes = () => {
    return request.get('/monitor/server-hardware/nodes');
};

export const getServerHardwareHistory = (params = {}) => {
    return request.get('/monitor/server-hardware/history', { params });
};

const PERIOD_TO_MS = {
    '5m': 5 * 60 * 1000,
    '1h': 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    '7d': 7 * 24 * 60 * 60 * 1000
};

const normalizeTrendPoint = (item = {}) => ({
    timestamp: item.timestamp,
    cpuUsage: item.cpuUsage || 0,
    memoryUsage: item.memoryUsage || 0,
    diskUsage: item.diskUsage || 0,
    gpuUsage: item.gpuUsage || 0,
    gpuMemoryUsage: item.gpuMemoryUsage || 0
});

const fallbackServerHardwareTrendByHistory = async (period, serverId) => {
    const now = Date.now();
    const range = PERIOD_TO_MS[period] || PERIOD_TO_MS['1h'];
    const params = {
        startTime: now - range,
        endTime: now,
        page: 0,
        size: 500
    };
    if (serverId) params.serverId = serverId;

    const history = await request.get('/monitor/server-hardware/history', {
        params,
        noRetry: true,
        silentError: true
    });

    const points = Array.isArray(history?.content) ? history.content : [];
    return points
        .slice()
        .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0))
        .map(normalizeTrendPoint);
};

export const getServerHardwareTrend = async (period = '1h', serverId = null) => {
    const params = { period };
    if (serverId) params.serverId = serverId;
    try {
        const trend = await request.get('/monitor/server-hardware/trend', {
            params,
            noRetry: true,
            silentError: true
        });
        return (Array.isArray(trend) ? trend : []).map(normalizeTrendPoint);
    } catch (error) {
        const status = error?.response?.status;
        if (status === 501 || status === 404) {
            console.warn(`[monitor] trend endpoint unavailable (status ${status}), fallback to history endpoint`);
            return fallbackServerHardwareTrendByHistory(period, serverId);
        }
        throw error;
    }
};

export const getServerHardwareStats = () => {
    return request.get('/monitor/server-hardware/stats');
};

export const collectServerHardware = () => {
    return request.post('/monitor/server-hardware/collect');
};

// Server Info (Node Configuration) APIs
export const getServerInfoList = () => {
    return request.get('/monitor/server-info/list');
};

export const getServerInfo = (serverId) => {
    return request.get('/monitor/server-info', {
        params: { serverId }
    });
};

export const createServerInfo = (data) => {
    return request.post('/monitor/server-info', data);
};

export const updateServerInfo = (data) => {
    return request.put('/monitor/server-info', data);
};

export const deleteServerInfo = (serverId) => {
    return request.delete('/monitor/server-info', {
        params: { serverId }
    });
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

export const getSystemProperties = (config = {}) => {
    return request.get('/monitor/system/properties', config);
};

export const updateSystemProperties = (properties) => {
    return request.post('/monitor/system/properties', properties);
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

export const testMilvusConnection = (host = 'localhost', port = 19530, token = '', config = {}) => {
    return request.get('/monitor/milvus/test', {
        params: { host, port, token },
        ...config
    });
};

export const getStorageHealthSnapshot = (config = {}) => {
    return request.get('/storage/health', config);
};

export const getSystemMaintenanceHealth = (config = {}) => {
    return request.get('/system/maintenance/health', config);
};

export const getPrometheusServerStatus = (serverId = null, config = {}) => {
    const params = {};
    if (serverId) params.serverId = serverId;
    return request.get('/monitor/prometheus/server-status', { params, ...config });
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

// ==================== Rate Limit ====================

export const getRateLimitConfigCached = (config = {}) => {
    return request.get('/admin/rate-limit/config/cached', config);
};
