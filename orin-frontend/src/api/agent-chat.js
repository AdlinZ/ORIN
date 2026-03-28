import request from '@/utils/request';

export function listAgents(params) {
  return request({
    url: '/api/v1/agents',
    method: 'get',
    params,
  });
}

export function getAgentDetail(agentId) {
  return request({
    url: `/api/v1/agents/${agentId}`,
    method: 'get',
  });
}

// 获取知识库列表（用于智能体对话时附加）
export function listKnowledgeBases(params) {
  return request({
    url: '/api/v1/knowledge/bases',
    method: 'get',
    params,
  });
}

// 智能体对话会话管理
export function listChatSessions(params) {
  return request({
    url: '/api/v1/agents/chat/sessions',
    method: 'get',
    params,
  });
}

export function createChatSession(data) {
  return request({
    url: '/api/v1/agents/chat/sessions',
    method: 'post',
    data,
  });
}

export function deleteChatSession(sessionId) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}`,
    method: 'delete',
  });
}

export function getChatSession(sessionId) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}`,
    method: 'get',
  });
}

// 发送对话消息（支持知识库检索）
export function sendChatMessage(sessionId, data) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}/messages`,
    method: 'post',
    data,
  });
}

// 为会话附加/解绑知识库
export function attachKnowledgeBase(sessionId, kbId) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}/attach-kb`,
    method: 'post',
    data: { kbId },
  });
}

export function detachKnowledgeBase(sessionId, kbId) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}/detach-kb`,
    method: 'post',
    data: { kbId },
  });
}

// 获取会话已附加的知识库
export function getAttachedKnowledgeBases(sessionId) {
  return request({
    url: `/api/v1/agents/chat/sessions/${sessionId}/kbs`,
    method: 'get',
  });
}