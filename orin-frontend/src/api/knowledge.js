import request from '@/utils/request';

// Use real backend if available, mostly mock for list page since backend only had detail API

// Original API
export const getAgentKnowledge = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}`);
};

// New List API (Mocked for full management view)
export const getKnowledgeList = (params) => {
    // Mock response
    return Promise.resolve({
        data: {
            list: [
                { id: 'kb-001', name: '企业产品手册 v2.0', docCount: 124, charCount: 452000, createTime: '2025-12-10 10:00:00' },
                { id: 'kb-002', name: 'Java 开发规范', docCount: 12, charCount: 56000, createTime: '2026-01-05 14:30:00' },
                { id: 'kb-003', name: '2025 客服话术库', docCount: 500, charCount: 1205000, createTime: '2026-01-08 09:15:00' },
            ],
            total: 3
        }
    });
};

export const addKnowledge = (data) => {
    return Promise.resolve({ code: 0, msg: '创建成功' });
};

export const deleteKnowledge = (id) => {
    return Promise.resolve({ code: 0, msg: '删除成功' });
};
