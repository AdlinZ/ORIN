import request from '@/utils/request';

// ==================== 知识库管理 ====================

export const getAgentKnowledge = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}`);
};

export const getKnowledgeList = (params) => {
    return request.get('/knowledge/list', { params });
};

export const addKnowledge = (data) => {
    return request.post('/knowledge', data);
};

export const updateKnowledge = (id, data) => {
    return request.put(`/knowledge/${id}`, data);
};

export const deleteKnowledge = (id) => {
    return request.delete(`/knowledge/${id}`);
};

export const updateKnowledgeStatus = (id, enabled) => {
    return request.put(`/knowledge/${id}/status`, { enabled });
};

export const generateDescription = (id, model) => {
    return request.post(`/knowledge/${id}/generate-description`, { model });
};

// ==================== 文档管理 ====================

export const uploadDocument = (kbId, file, uploadedBy) => {
    const formData = new FormData();
    formData.append('file', file);
    if (uploadedBy) formData.append('uploadedBy', uploadedBy);
    return request.post(`/knowledge/${kbId}/documents/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};

export const getDocuments = (kbId) => {
    return request.get(`/knowledge/${kbId}/documents`);
};

export const getDocument = (docId) => {
    return request.get(`/knowledge/documents/${docId}`);
};

export const deleteDocument = (docId) => {
    return request.delete(`/knowledge/documents/${docId}`);
};

export const triggerVectorization = (docId) => {
    return request.post(`/knowledge/documents/${docId}/vectorize`);
};

export const triggerParsing = (docId) => {
    return request.post(`/knowledge/documents/${docId}/parse`);
};

export const updateDocument = (docId, payload) => {
    return request.put(`/knowledge/documents/${docId}`, payload);
};

export const getSupportedFileTypes = () => {
    return request.get('/knowledge/supported-file-types');
};

// ==================== 分块管理 ====================

export const getDocumentChunks = (docId) => {
    return request.get(`/knowledge/documents/${docId}/chunks`);
};

export const updateChunk = (chunkId, content) => {
    return request.put(`/knowledge/documents/chunks/${chunkId}`, { content });
};

export const deleteChunk = (chunkId) => {
    return request.delete(`/knowledge/documents/chunks/${chunkId}`);
};

export const getChunkStats = (docId) => {
    return request.get(`/knowledge/documents/${docId}/chunks/stats`);
};

// ==================== 向量检索 ====================

export const testRetrieval = (payload) => {
    return request.post('/knowledge/retrieve/test', payload);
};

export const getKnowledgeBaseVectors = (kbId, page, size) => {
    return request.get(`/knowledge/kb/${kbId}/vectors`, { params: { page, size } });
};

export const getCollectionInfo = () => {
    return request.get('/knowledge/collection/info');
};

export const getCollectionDetail = () => {
    return request.get('/knowledge/collection/detail');
};

export const recreateCollection = () => {
    return request.post('/knowledge/collection/recreate');
};

// ==================== 外部同步 ====================

export const testNotionConnection = (config) => {
    return request.post('/knowledge/sync/notion/test', config);
};

export const listNotionDatabases = (config) => {
    return request.post('/knowledge/sync/notion/databases', config);
};

export const syncFromNotion = (kbId, config) => {
    return request.post(`/knowledge/${kbId}/sync/notion`, config);
};

export const testWebUrl = (url) => {
    return request.get('/knowledge/sync/web/test', { params: { url } });
};

export const syncFromWeb = (kbId, config) => {
    return request.post(`/knowledge/${kbId}/sync/web`, config);
};

export const testDatabaseConnection = (config) => {
    return request.post('/knowledge/sync/database/test', config);
};

export const connectDatabase = (kbId, config) => {
    return request.post(`/knowledge/${kbId}/sync/database/connect`, config);
};

export const getDatabaseSchema = (kbId) => {
    return request.get(`/knowledge/${kbId}/sync/database/schema`);
};

export const syncDatabaseTable = (kbId, tableName) => {
    return request.post(`/knowledge/${kbId}/sync/database/table/${tableName}`);
};

// ==================== RAGFlow 同步 ====================

export const testRAGFlowConnection = (config) => {
    return request.post('/knowledge/sync/ragflow/test', config);
};

export const listRAGFlowKnowledgeBases = (config) => {
    return request.post('/knowledge/sync/ragflow/list', config);
};

export const syncFromRAGFlow = (kbId, config) => {
    return request.post(`/knowledge/${kbId}/sync/ragflow`, config);
};

export const retrievalFromRAGFlow = (config, ragflowKbId, topK) => {
    return request.post('/knowledge/retrieve/ragflow', config, { params: { ragflowKbId, topK } });
};

export const uploadToRAGFlow = (kbId, fileName, fileContent, config) => {
    return request.post(`/knowledge/${kbId}/documents/ragflow/upload`, fileContent, {
        params: { fileName },
        headers: { 'Content-Type': 'application/octet-stream' }
    });
};

// ==================== Dify 同步 ====================

export const syncDifyKnowledge = (agentId, full = false) => {
    return request.post(`/knowledge/sync/dify/${agentId}`, null, { params: { full } });
};

export const fullSyncDify = (agentId) => {
    return request.post(`/knowledge/sync/dify/${agentId}/full`);
};

export const getDifySyncHistory = (agentId, limit = 10) => {
    return request.get(`/knowledge/sync/dify/${agentId}/history`, { params: { limit } });
};

export const testDifyConnection = (endpoint, apiKey) => {
    return request.post('/knowledge/sync/dify/test', { endpoint, apiKey });
};

// ==================== 端侧知识库同步 (Side Client Sync) ====================

export const getClientChanges = (agentId, params = {}, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/changes`, {
        params,
        ...config
    });
};

export const getPendingChanges = (agentId, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/pending`, config);
};

export const getPendingChangeCount = (agentId, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/pending/count`, config);
};

export const exportClientDocuments = (agentId, params = {}, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/export`, {
        params,
        ...config
    });
};

export const downloadClientDocument = (agentId, documentId, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/document/${documentId}`, config);
};

export const getClientCheckpoint = (agentId, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/checkpoint`, config);
};

export const markClientSynced = (agentId, config = {}) => {
    return request.post(`/knowledge/sync/client/${agentId}/mark-synced`, null, config);
};

export const getClientWebhooks = (agentId, config = {}) => {
    return request.get(`/knowledge/sync/client/${agentId}/webhooks`, config);
};

export const saveClientWebhook = (agentId, data, config = {}) => {
    return request.post(`/knowledge/sync/client/${agentId}/webhook`, data, config);
};

export const deleteClientWebhook = (webhookId, config = {}) => {
    return request.delete(`/knowledge/sync/client/webhook/${webhookId}`, config);
};

export const reenableClientWebhook = (webhookId, config = {}) => {
    return request.post(`/knowledge/sync/client/webhook/${webhookId}/reenable`, null, config);
};

// 手动触发全量同步
export const triggerFullSync = (agentId, config = {}) => {
    return request.post(`/knowledge/sync/client/${agentId}/sync/full`, null, config);
};

// 手动触发增量同步
export const triggerIncrementalSync = (agentId, config = {}) => {
    return request.post(`/knowledge/sync/client/${agentId}/sync/incremental`, null, config);
};

// ==================== 知识统计 ====================

export const getKnowledgeBaseStats = (kbId) => {
    return request.get(`/knowledge/${kbId}/stats`);
};

export const getPendingDocuments = () => {
    return request.get('/knowledge/documents/pending');
};

export const vectorizeAllPending = () => {
    return request.post('/knowledge/documents/vectorize-all');
};

export const getDocumentHistory = (docId) => {
    return request.get(`/knowledge/documents/${docId}/history`);
};

// ==================== 诊断 ====================

export const diagnoseMilvus = () => {
    return request.get('/knowledge/diagnose/milvus');
};

// ==================== 智力中心 (Intelligence Center) ====================

export const getUnifiedKnowledge = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}/unified`);
};

export const getBoundKnowledge = (agentId, type = 'DOCUMENT') => {
    return request.get(`/knowledge/agents/${agentId}`, { params: { type } });
};

export const getAgentPrompts = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}/meta/prompts`);
};

export const createPromptTemplate = (agentId, template) => {
    return request.post(`/knowledge/agents/${agentId}/meta/prompts`, template);
};

export const deletePromptTemplate = (agentId, id) => {
    return request.delete(`/knowledge/agents/${agentId}/meta/prompts/${id}`);
};

export const getAgentMemory = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}/meta/memory`);
};

export const deleteMemoryEntry = (agentId, id) => {
    return request.delete(`/knowledge/agents/${agentId}/meta/memory/${id}`);
};

export const clearLongTermMemory = (agentId) => {
    return request.delete(`/knowledge/agents/${agentId}/meta/memory`);
};

export const getShortTermSessions = (agentId) => {
    return request.get(`/knowledge/agents/${agentId}/meta/memory/sessions`);
};

export const getShortTermMemory = (agentId, sessionId, limit = 50) => {
    return request.get(`/knowledge/agents/${agentId}/meta/memory/sessions/${sessionId}`, { params: { limit } });
};

export const clearShortTermMemory = (agentId, sessionId) => {
    return request.delete(`/knowledge/agents/${agentId}/meta/memory/sessions/${sessionId}`);
};

export const extractMemory = (agentId, content) => {
    return request.post(`/knowledge/agents/${agentId}/meta/extract_memory`, { content });
};

// ==================== 文件解析 ====================

export const parseText = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post('/knowledge/documents/parse-text', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};

// ==================== 语义联想 ====================

export const getSemanticKeywords = (params) => {
    return request.get('/knowledge/semantic/keywords', { params });
};

export const getSearchSuggestions = (keyword, limit = 10) => {
    return request.get('/knowledge/semantic/search-suggest', { params: { keyword, limit } });
};

// ==================== 智力资产中心 (Intelligence Center) ====================

// --- 长期记忆 ---
export const getAgentMemories = (agentId) => {
    return request.get('/intelligence/memories', { params: { agentId } });
};

export const saveAgentMemory = (agentId, key, value) => {
    return request.post('/intelligence/memories', { key, value }, { params: { agentId } });
};

export const deleteAgentMemory = (id) => {
    return request.delete(`/intelligence/memories/${id}`);
};

// --- 技能管理 ---
export const getAgentSkills = (agentId) => {
    return request.get('/intelligence/skills', { params: { agentId } });
};

export const saveAgentSkill = (skill) => {
    return request.post('/intelligence/skills', skill);
};

export const deleteAgentSkill = (id) => {
    return request.delete(`/intelligence/skills/${id}`);
};

// --- Prompt 模板 (Intelligence Center) ---
export const getIntelligencePrompts = (agentId, userId) => {
    return request.get('/intelligence/prompts', { params: { agentId, userId } });
};

export const saveIntelligencePrompt = (template) => {
    return request.post('/intelligence/prompts', template);
};

export const deleteIntelligencePrompt = (id) => {
    return request.delete(`/intelligence/prompts/${id}`);
};

// ==================== 知识图谱管理 ====================

export const getGraphList = () => {
    return request.get('/knowledge/graphs');
};

export const getGraph = (graphId) => {
    return request.get(`/knowledge/graphs/${graphId}`);
};

export const createGraph = (data) => {
    return request.post('/knowledge/graphs', data);
};

export const updateGraph = (graphId, data) => {
    return request.put(`/knowledge/graphs/${graphId}`, data);
};

export const deleteGraph = (graphId) => {
    return request.delete(`/knowledge/graphs/${graphId}`);
};

export const buildGraph = (graphId) => {
    return request.post(`/knowledge/graphs/${graphId}/build`);
};

export const getGraphEntities = (graphId, params) => {
    return request.get(`/knowledge/graphs/${graphId}/entities`, { params });
};

export const getGraphRelations = (graphId, params) => {
    return request.get(`/knowledge/graphs/${graphId}/relations`, { params });
};

export const searchGraphEntities = (graphId, keyword) => {
    return request.get(`/knowledge/graphs/${graphId}/entities/search`, { params: { q: keyword } });
};
