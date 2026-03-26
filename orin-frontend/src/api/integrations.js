import request from '@/utils/request';

// ========== Dify ==========

// 获取 Dify 配置
export const getDifyConfig = () => {
    return request.get('/system/integrations/dify');
};

// 保存 Dify 配置
export const saveDifyConfig = (data) => {
    return request.post('/system/integrations/dify', data);
};

// 测试 Dify 连接
export const testDifyConnection = () => {
    return request.get('/system/integrations/dify/test');
};

// 获取 Dify 应用列表
export const getDifyApps = () => {
    return request.get('/system/integrations/dify/apps');
};

// ========== RAGFlow ==========

// 获取 RAGFlow 配置
export const getRagflowConfig = () => {
    return request.get('/system/integrations/ragflow');
};

// 保存 RAGFlow 配置
export const saveRagflowConfig = (data) => {
    return request.post('/system/integrations/ragflow', data);
};

// 测试 RAGFlow 连接
export const testRagflowConnection = () => {
    return request.get('/system/integrations/ragflow/test');
};

// 获取 RAGFlow 知识库列表
export const getRagflowKnowledgeBases = () => {
    return request.get('/system/integrations/ragflow/knowledge-bases');
};

// ========== AutoGen ==========

// 获取 AutoGen 配置
export const getAutogenConfig = () => {
    return request.get('/system/integrations/autogen');
};

// 保存 AutoGen 配置
export const saveAutogenConfig = (data) => {
    return request.post('/system/integrations/autogen', data);
};

// 测试 AutoGen 连接
export const testAutogenConnection = () => {
    return request.get('/system/integrations/autogen/test');
};

// ========== CrewAI ==========

// 获取 CrewAI 配置
export const getCrewaiConfig = () => {
    return request.get('/system/integrations/crewai');
};

// 保存 CrewAI 配置
export const saveCrewaiConfig = (data) => {
    return request.post('/system/integrations/crewai', data);
};

// 测试 CrewAI 连接
export const testCrewaiConnection = () => {
    return request.get('/system/integrations/crewai/test');
};

// ========== 统一状态 ==========

// 获取所有集成状态
export const getIntegrationStatus = () => {
    return request.get('/system/integrations/status');
};