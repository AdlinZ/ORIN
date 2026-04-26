import request from '@/utils/request';
import Cookies from 'js-cookie';
import { useUserStore } from '@/stores/user';

export function listAgents(params) {
  return request({
    url: '/agents',
    method: 'get',
    params,
  });
}

export function getAgentDetail(agentId) {
  return request({
    url: `/agents/${agentId}`,
    method: 'get',
  });
}

// 获取知识库列表（用于智能体对话时附加）
export function listKnowledgeBases(params) {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params,
  });
}

// 智能体对话会话管理
export function listChatSessions(params) {
  return request({
    url: '/agents/chat/sessions',
    method: 'get',
    params,
  });
}

export function createChatSession(data) {
  return request({
    url: '/agents/chat/sessions',
    method: 'post',
    data,
  });
}

export function deleteChatSession(sessionId) {
  return request({
    url: `/agents/chat/sessions/${sessionId}`,
    method: 'delete',
  });
}

export function getChatSession(sessionId) {
  return request({
    url: `/agents/chat/sessions/${sessionId}`,
    method: 'get',
  });
}

export function saveChatSessionMessages(sessionId, messages) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/messages/history`,
    method: 'put',
    data: { messages },
  });
}

// 发送对话消息（支持知识库检索）
export function sendChatMessage(sessionId, data) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/messages`,
    method: 'post',
    data,
    timeout: 180000, // Chat + KB retrieval may exceed default 60s under fallback paths
    noRetry: true, // Avoid duplicate question submission on timeout/retry
  });
}

// 发送对话消息（SSE 流式）
export async function sendChatMessageStream(sessionId, data, handlers = {}) {
  const userStore = useUserStore();
  const token = userStore?.token || Cookies.get('orin_token');
  const userId = userStore?.userId || '';
  const response = await fetch(`/api/v1/agents/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(userId ? { 'X-User-Id': userId } : {})
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });

  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '');
    throw new Error(text || `SSE 请求失败 (${response.status})`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  const dispatch = (eventName, payload) => {
    const fn = handlers[eventName];
    if (typeof fn === 'function') {
      fn(payload);
    }
  };

  const parseEventBlock = (block) => {
    const lines = block.split('\n');
    let eventName = 'message';
    const dataLines = [];

    lines.forEach((line) => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    });

    if (!dataLines.length) return;
    const raw = dataLines.join('\n');
    let payload = raw;
    try {
      payload = JSON.parse(raw);
    } catch (e) {
      // keep raw string if not JSON
    }
    dispatch(eventName, payload);
  };

  let streamDone = false;
  while (!streamDone) {
    const { value, done } = await reader.read();
    streamDone = done;
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    let separatorIndex = -1;
    let separatorLength = 0;
    let hasSeparator = true;
    while (hasSeparator) {
      const idxN = buffer.indexOf('\n\n');
      const idxRN = buffer.indexOf('\r\n\r\n');
      if (idxN === -1 && idxRN === -1) {
        hasSeparator = false;
        continue;
      }
      if (idxRN !== -1 && (idxN === -1 || idxRN < idxN)) {
        separatorIndex = idxRN;
        separatorLength = 4;
      } else {
        separatorIndex = idxN;
        separatorLength = 2;
      }

      const chunk = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + separatorLength);
      if (chunk.trim()) {
        parseEventBlock(chunk);
      }
    }
  }
}

// 为会话附加/解绑知识库
export function attachKnowledgeBase(sessionId, kbId) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/attach-kb`,
    method: 'post',
    data: { kbId },
  });
}

export function detachKnowledgeBase(sessionId, kbId) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/detach-kb`,
    method: 'post',
    data: { kbId },
  });
}

// 获取会话已附加的知识库
export function getAttachedKnowledgeBases(sessionId) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/kbs`,
    method: 'get',
  });
}

// 更新知识库文档过滤配置
export function updateKbDocFilters(sessionId, kbDocFilters) {
  return request({
    url: `/agents/chat/sessions/${sessionId}/kb-doc-filters`,
    method: 'put',
    data: kbDocFilters,
  });
}

export function getToolCatalog(params) {
  return request({
    url: '/agent-tools/catalog',
    method: 'get',
    params
  });
}

export function updateToolCatalogItem(toolId, data) {
  return request({
    url: `/agent-tools/catalog/${toolId}`,
    method: 'put',
    data
  });
}

export function getAgentToolBinding(agentId) {
  return request({
    url: `/agent-tools/bindings/agents/${agentId}`,
    method: 'get'
  });
}

export function saveAgentToolBinding(agentId, data) {
  return request({
    url: `/agent-tools/bindings/agents/${agentId}`,
    method: 'put',
    data
  });
}

export function getSessionToolBinding(sessionId) {
  return request({
    url: `/agent-tools/bindings/sessions/${sessionId}`,
    method: 'get'
  });
}

export function saveSessionToolBinding(sessionId, data) {
  return request({
    url: `/agent-tools/bindings/sessions/${sessionId}`,
    method: 'put',
    data
  });
}
