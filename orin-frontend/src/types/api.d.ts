/**
 * ORIN API 类型定义
 * 统一管理所有 API 响应的类型
 */

// ========== 通用类型 ==========

export interface ApiResponse<T = any> {
  code?: number;
  data?: T;
  message?: string;
  success?: boolean;
}

export interface PageResult<T = any> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ApiError {
  code: number;
  message: string;
  details?: any;
}

// ========== 用户相关 ==========

export interface User {
  id: string;
  username: string;
  email?: string;
  role: 'admin' | 'user' | 'guest';
  avatar?: string;
  createdAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: User;
  expiresIn: number;
}

// ========== 知识库相关 ==========

export type KnowledgeType = 'UNSTRUCTURED' | 'STRUCTURED' | 'PROCEDURAL' | 'META_MEMORY' | 'MULTIMODAL';

export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string;
  type: KnowledgeType;
  status: 'ENABLED' | 'DISABLED';
  stats: {
    documentCount?: number;
    tableCount?: number;
    skillCount?: number;
    memoryEntryCount?: number;
  };
  createdAt?: string;
  updatedAt?: string;
}

export interface KnowledgeDocument {
  id: string;
  kbId: string;
  name: string;
  size?: number;
  type?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  chunkCount?: number;
  createdAt?: string;
}

export interface DocumentChunk {
  id: string;
  documentId: string;
  content: string;
  index: number;
  metadata?: Record<string, any>;
}

export interface RetrievalResult {
  id: string;
  content: string;
  score: number;
  sourceDoc?: string;
  chunkIndex?: number;
  matchType?: 'VECTOR' | 'KEYWORD' | 'HYBRID';
  metadata?: Record<string, any>;
}

// ========== 智能体相关 ==========

export type AgentStatus = 'IDLE' | 'RUNNING' | 'ERROR' | 'STOPPED';

export interface Agent {
  id: string;
  name: string;
  description?: string;
  model?: string;
  provider?: string;
  status: AgentStatus;
  config?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

export interface AgentHealth {
  agentId: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  cpuUsage?: number;
  memoryUsage?: number;
  avgResponseTime?: number;
  errorRate?: number;
  lastActiveAt?: string;
}

// ========== 工作流相关 ==========

export interface Workflow {
  id: string;
  name: string;
  description?: string;
  type: 'DAG' | 'LINEAR';
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  definition?: any;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, any>;
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  type?: string;
  data?: Record<string, any>;
}

// ========== 模型配置相关 ==========

export type ProviderType = 'openai' | 'anthropic' | 'azure' | 'dify' | 'siliconflow' | 'ollama' | 'local';

export interface ModelProvider {
  id: string;
  name: string;
  type: ProviderType;
  baseUrl?: string;
  apiKey?: string;
  enabled: boolean;
  models?: string[];
  config?: Record<string, any>;
}

export interface ModelConfig {
  id: string;
  providerId: string;
  modelName: string;
  displayName?: string;
  type: 'chat' | 'embedding' | 'image' | 'audio';
  enabled: boolean;
  config?: {
    temperature?: number;
    maxTokens?: number;
    topP?: number;
  };
}

// ========== 监控相关 ==========

export interface MetricData {
  timestamp: string;
  value: number;
}

export interface AgentMetrics {
  agentId: string;
  cpuUsage: MetricData[];
  memoryUsage: MetricData[];
  requestCount: MetricData[];
  tokenUsage: {
    prompt: number;
    completion: number;
    total: number;
  };
  avgLatency: number;
  errorRate: number;
}

export interface PricingConfig {
  id: string;
  providerId: string;
  billingMode: 'PER_TOKEN' | 'PER_REQUEST' | 'PER_SECOND';
  inputCostUnit?: number;
  outputCostUnit?: number;
  currency: string;
}

export interface BillingRecord {
  id: string;
  providerId: string;
  providerName: string;
  billingDate: string;
  totalCost?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  currency: string;
}

// ========== 告警相关 ==========

export type AlertLevel = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';

export interface Alert {
  id: string;
  type: string;
  level: AlertLevel;
  message: string;
  source?: string;
  acknowledged: boolean;
  createdAt: string;
}

// ========== 技能相关 ==========

export interface Skill {
  id: string;
  name: string;
  description?: string;
  type: 'prompt' | 'tool' | 'workflow';
  definition: any;
  enabled: boolean;
  tags?: string[];
  createdAt?: string;
}
