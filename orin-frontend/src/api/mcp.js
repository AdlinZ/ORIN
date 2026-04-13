import request from '@/utils/request';

// 获取 MCP 服务列表
export const getMcpServices = (params) => {
    return request.get('/api/system/mcp/services', { baseURL: '', params });
};

// 获取单个 MCP 服务
export const getMcpService = (id) => {
    return request.get(`/api/system/mcp/services/${id}`, { baseURL: '' });
};

// 创建 MCP 服务
export const createMcpService = (data) => {
    return request.post('/api/system/mcp/services', data, { baseURL: '' });
};

// 更新 MCP 服务
export const updateMcpService = (id, data) => {
    return request.put(`/api/system/mcp/services/${id}`, data, { baseURL: '' });
};

// 删除 MCP 服务
export const deleteMcpService = (id) => {
    return request.delete(`/api/system/mcp/services/${id}`, { baseURL: '' });
};

// 测试 MCP 服务连接
export const testMcpConnection = (id) => {
    return request.post(`/api/system/mcp/services/${id}/test`, null, { baseURL: '' });
};

// 获取 MCP 工具列表
export const getMcpTools = () => {
    return request.get('/api/system/mcp/tools', { baseURL: '' });
};

// 从市场安装工具
export const installMcpTool = (toolKey) => {
    return request.post(`/api/system/mcp/tools/${toolKey}/install`, null, { baseURL: '' });
};

// 启用/禁用服务
export const setMcpServiceEnabled = (id, enabled) => {
    return request.put(`/api/system/mcp/services/${id}/enabled`, { enabled }, { baseURL: '' });
};
