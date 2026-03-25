import request from '@/utils/request';

// 获取 MCP 服务列表
export const getMcpServices = (params) => {
    return request.get('/system/mcp/services', { params });
};

// 获取单个 MCP 服务
export const getMcpService = (id) => {
    return request.get(`/system/mcp/services/${id}`);
};

// 创建 MCP 服务
export const createMcpService = (data) => {
    return request.post('/system/mcp/services', data);
};

// 更新 MCP 服务
export const updateMcpService = (id, data) => {
    return request.put(`/system/mcp/services/${id}`, data);
};

// 删除 MCP 服务
export const deleteMcpService = (id) => {
    return request.delete(`/system/mcp/services/${id}`);
};

// 测试 MCP 服务连接
export const testMcpConnection = (id) => {
    return request.post(`/system/mcp/services/${id}/test`);
};

// 获取 MCP 工具列表
export const getMcpTools = () => {
    return request.get('/system/mcp/tools');
};