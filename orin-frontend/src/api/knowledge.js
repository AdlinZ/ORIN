import request from '@/utils/request';

// Use real backend if available, mostly mock for list page since backend only had detail API

// Original API
export const getAgentKnowledge = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}`);
};

// New List API
export const getKnowledgeList = (params) => {
    return request.get('/knowledge/list', { params });
};

export const addKnowledge = (data) => {
    return Promise.resolve({ code: 0, msg: '创建成功' });
};

export const deleteKnowledge = (id) => {
    return Promise.resolve({ code: 0, msg: '删除成功' });
};
// --- Intelligence Center API ---

export const getMemories = (agentId) => {
    return request.get('/intelligence/memories', { params: { agentId } });
};

export const saveMemory = (agentId, key, value) => {
    return request.post('/intelligence/memories', { key, value }, { params: { agentId } });
};

export const deleteMemory = (id) => {
    return request.delete(`/intelligence/memories/${id}`);
};

export const getSkills = (agentId) => {
    return request.get('/intelligence/skills', { params: { agentId } });
};

export const saveSkill = (skill) => {
    return request.post('/intelligence/skills', skill);
};

export const deleteSkill = (id) => {
    return request.delete(`/intelligence/skills/${id}`);
};

export const getPrompts = (agentId, userId) => {
    return request.get('/intelligence/prompts', { params: { agentId, userId } });
};

export const savePrompt = (template) => {
    return request.post('/intelligence/prompts', template);
};

export const deletePrompt = (id) => {
    return request.delete(`/intelligence/prompts/${id}`);
};
