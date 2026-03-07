/**
 * ORIN API 服务层
 * 统一封装所有后端 API 调用
 */

import request from '@/utils/request';
import type {
  ApiResponse,
  PageResult,
  KnowledgeBase,
  KnowledgeDocument,
  RetrievalResult,
  Agent,
  AgentHealth,
  Workflow,
  ModelProvider,
  ModelConfig,
  AgentMetrics,
  BillingRecord,
  Alert,
  Skill
} from '@/types/api';

/**
 * 知识库 API
 */
export const knowledgeApi = {
  // 获取知识库列表
  list: (params?: { page?: number; pageSize?: number; type?: string }) =>
    request.get<any, ApiResponse<KnowledgeBase[]>>('/knowledge/list', { params }),

  // 获取知识库详情
  get: (id: string) =>
    request.get<any, ApiResponse<KnowledgeBase>>(`/knowledge/${id}`),

  // 创建知识库
  create: (data: { name: string; description?: string; type: string }) =>
    request.post<any, ApiResponse<KnowledgeBase>>('/knowledge', data),

  // 更新知识库
  update: (id: string, data: Partial<KnowledgeBase>) =>
    request.put<any, ApiResponse<KnowledgeBase>>(`/knowledge/${id}`, data),

  // 删除知识库
  delete: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/knowledge/${id}`),

  // 获取知识库文档列表
  getDocuments: (kbId: string, params?: { page?: number; pageSize?: number }) =>
    request.get<any, ApiResponse<PageResult<KnowledgeDocument>>>(
      `/knowledge/${kbId}/documents`,
      { params }
    ),

  // 上传文档
  uploadDocument: (kbId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<any, ApiResponse<KnowledgeDocument>>(
      `/knowledge/${kbId}/documents/upload`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
  },

  // 删除文档
  deleteDocument: (kbId: string, docId: string) =>
    request.delete<any, ApiResponse<void>>(`/knowledge/${kbId}/documents/${docId}`),

  // 检索
  retrieve: (kbId: string, query: string, params?: { topK?: number; threshold?: number }) =>
    request.post<any, ApiResponse<RetrievalResult[]>>(`/knowledge/${kbId}/retrieve`, {
      query,
      ...params
    }),

  // 混合检索
  hybridSearch: (kbId: string, query: string, params?: { topK?: number; alpha?: number }) =>
    request.post<any, ApiResponse<RetrievalResult[]>>(`/knowledge/${kbId}/hybrid-search`, {
      query,
      ...params
    })
};

/**
 * 智能体 API
 */
export const agentApi = {
  // 获取智能体列表
  list: (params?: { status?: string; page?: number; pageSize?: number }) =>
    request.get<any, ApiResponse<Agent[]>>('/agent/list', { params }),

  // 获取智能体详情
  get: (id: string) =>
    request.get<any, ApiResponse<Agent>>(`/agent/${id}`),

  // 创建智能体
  create: (data: Partial<Agent>) =>
    request.post<any, ApiResponse<Agent>>('/agent', data),

  // 更新智能体
  update: (id: string, data: Partial<Agent>) =>
    request.put<any, ApiResponse<Agent>>(`/agent/${id}`, data),

  // 删除智能体
  delete: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/agent/${id}`),

  // 启动智能体
  start: (id: string) =>
    request.post<any, ApiResponse<void>>(`/agent/${id}/start`),

  // 停止智能体
  stop: (id: string) =>
    request.post<any, ApiResponse<void>>(`/agent/${id}/stop`),

  // 获取健康状态
  getHealth: (id: string) =>
    request.get<any, ApiResponse<AgentHealth>>(`/agent/${id}/health`),

  // 获取指标
  getMetrics: (id: string, params?: { startTime?: string; endTime?: string }) =>
    request.get<any, ApiResponse<AgentMetrics>>(`/agent/${id}/metrics`, { params })
};

/**
 * 工作流 API
 */
export const workflowApi = {
  // 获取工作流列表
  list: (params?: { type?: string; status?: string; page?: number; pageSize?: number }) =>
    request.get<any, ApiResponse<Workflow[]>>('/workflow/list', { params }),

  // 获取工作流详情
  get: (id: string) =>
    request.get<any, ApiResponse<Workflow>>(`/workflow/${id}`),

  // 创建工作流
  create: (data: { name: string; type?: string; definition?: any }) =>
    request.post<any, ApiResponse<Workflow>>('/workflow', data),

  // 更新工作流
  update: (id: string, data: Partial<Workflow>) =>
    request.put<any, ApiResponse<Workflow>>(`/workflow/${id}`, data),

  // 删除工作流
  delete: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/workflow/${id}`),

  // 执行工作流
  execute: (id: string, inputs?: Record<string, any>) =>
    request.post<any, ApiResponse<any>>(`/workflow/${id}/execute`, inputs),

  // 发布工作流
  publish: (id: string) =>
    request.post<any, ApiResponse<Workflow>>(`/workflow/${id}/publish`)
};

/**
 * 模型配置 API
 */
export const modelApi = {
  // 获取提供商列表
  listProviders: () =>
    request.get<any, ApiResponse<ModelProvider[]>>('/model/providers'),

  // 添加提供商
  addProvider: (data: Partial<ModelProvider>) =>
    request.post<any, ApiResponse<ModelProvider>>('/model/providers', data),

  // 更新提供商
  updateProvider: (id: string, data: Partial<ModelProvider>) =>
    request.put<any, ApiResponse<ModelProvider>>(`/model/providers/${id}`, data),

  // 删除提供商
  deleteProvider: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/model/providers/${id}`),

  // 获取模型配置列表
  listModels: (params?: { providerId?: string; type?: string }) =>
    request.get<any, ApiResponse<ModelConfig[]>>('/model/configs', { params }),

  // 添加模型配置
  addModel: (data: Partial<ModelConfig>) =>
    request.post<any, ApiResponse<ModelConfig>>('/model/configs', data),

  // 更新模型配置
  updateModel: (id: string, data: Partial<ModelConfig>) =>
    request.put<any, ApiResponse<ModelConfig>>(`/model/configs/${id}`, data),

  // 删除模型配置
  deleteModel: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/model/configs/${id}`)
};

/**
 * 监控 API
 */
export const monitorApi = {
  // 获取系统健康状态
  getSystemHealth: () =>
    request.get<any, ApiResponse<any>>('/monitor/health'),

  // 获取指标数据
  getMetrics: (params?: { type?: string; startTime?: string; endTime?: string }) =>
    request.get<any, ApiResponse<any>>('/monitor/metrics', { params }),

  // 获取计费记录
  getBillingRecords: (params?: { providerId?: string; startDate?: string; endDate?: string }) =>
    request.get<any, ApiResponse<BillingRecord[]>>('/monitor/billing', { params }),

  // 手动触发计费同步
  syncBilling: () =>
    request.post<any, ApiResponse<void>>('/monitor/billing/sync')
};

/**
 * 告警 API
 */
export const alertApi = {
  // 获取告警列表
  list: (params?: { level?: string; acknowledged?: boolean; page?: number; pageSize?: number }) =>
    request.get<any, ApiResponse<Alert[]>>('/alert/list', { params }),

  // 确认告警
  acknowledge: (id: string) =>
    request.post<any, ApiResponse<Alert>>(`/alert/${id}/acknowledge`),

  // 删除告警
  delete: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/alert/${id}`)
};

/**
 * 技能 API
 */
export const skillApi = {
  // 获取技能列表
  list: (params?: { type?: string; enabled?: boolean }) =>
    request.get<any, ApiResponse<Skill[]>>('/skill/list', { params }),

  // 获取技能详情
  get: (id: string) =>
    request.get<any, ApiResponse<Skill>>(`/skill/${id}`),

  // 创建技能
  create: (data: Partial<Skill>) =>
    request.post<any, ApiResponse<Skill>>('/skill', data),

  // 更新技能
  update: (id: string, data: Partial<Skill>) =>
    request.put<any, ApiResponse<Skill>>(`/skill/${id}`, data),

  // 删除技能
  delete: (id: string) =>
    request.delete<any, ApiResponse<void>>(`/skill/${id}`),

  // 启用/禁用技能
  toggle: (id: string, enabled: boolean) =>
    request.post<any, ApiResponse<Skill>>(`/skill/${id}/toggle`, { enabled })
};

// 默认导出所有 API
export default {
  knowledge: knowledgeApi,
  agent: agentApi,
  workflow: workflowApi,
  model: modelApi,
  monitor: monitorApi,
  alert: alertApi,
  skill: skillApi
};
