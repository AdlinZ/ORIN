/**
 * ORIN Store 模板
 * 使用 Pinia 的最佳实践
 */

import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { KnowledgeBase, Agent, Workflow } from '@/types/api';

// ========== 知识库 Store ==========
export const useKnowledgeStore = defineStore('knowledge', () => {
  // State
  const knowledgeBases = ref<KnowledgeBase[]>([]);
  const currentKB = ref<KnowledgeBase | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Getters
  const kbCount = computed(() => knowledgeBases.value.length);
  const enabledKBs = computed(() => 
    knowledgeBases.value.filter(kb => kb.status === 'ENABLED')
  );
  const kbMap = computed(() => 
    new Map(knowledgeBases.value.map(kb => [kb.id, kb]))
  );

  // Actions
  const setKnowledgeBases = (kbs: KnowledgeBase[]) => {
    knowledgeBases.value = kbs;
  };

  const addKB = (kb: KnowledgeBase) => {
    knowledgeBases.value.push(kb);
  };

  const updateKB = (id: string, updates: Partial<KnowledgeBase>) => {
    const index = knowledgeBases.value.findIndex(kb => kb.id === id);
    if (index !== -1) {
      knowledgeBases.value[index] = { ...knowledgeBases.value[index], ...updates };
    }
  };

  const removeKB = (id: string) => {
    knowledgeBases.value = knowledgeBases.value.filter(kb => kb.id !== id);
  };

  const setCurrentKB = (kb: KnowledgeBase | null) => {
    currentKB.value = kb;
  };

  const setLoading = (val: boolean) => {
    loading.value = val;
  };

  const setError = (err: string | null) => {
    error.value = err;
  };

  return {
    // State
    knowledgeBases,
    currentKB,
    loading,
    error,
    // Getters
    kbCount,
    enabledKBs,
    kbMap,
    // Actions
    setKnowledgeBases,
    addKB,
    updateKB,
    removeKB,
    setCurrentKB,
    setLoading,
    setError
  };
});

// ========== 智能体 Store ==========
export const useAgentStore = defineStore('agent', () => {
  // State
  const agents = ref<Agent[]>([]);
  const currentAgent = ref<Agent | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Getters
  const agentCount = computed(() => agents.value.length);
  const runningAgents = computed(() => 
    agents.value.filter(a => a.status === 'RUNNING')
  );
  const agentMap = computed(() => 
    new Map(agents.value.map(a => [a.id, a]))
  );

  // Actions
  const setAgents = (list: Agent[]) => {
    agents.value = list;
  };

  const addAgent = (agent: Agent) => {
    agents.value.push(agent);
  };

  const updateAgent = (id: string, updates: Partial<Agent>) => {
    const index = agents.value.findIndex(a => a.id === id);
    if (index !== -1) {
      agents.value[index] = { ...agents.value[index], ...updates };
    }
  };

  const removeAgent = (id: string) => {
    agents.value = agents.value.filter(a => a.id !== id);
  };

  const setCurrentAgent = (agent: Agent | null) => {
    currentAgent.value = agent;
  };

  const setLoading = (val: boolean) => {
    loading.value = val;
  };

  const setError = (err: string | null) => {
    error.value = err;
  };

  return {
    // State
    agents,
    currentAgent,
    loading,
    error,
    // Getters
    agentCount,
    runningAgents,
    agentMap,
    // Actions
    setAgents,
    addAgent,
    updateAgent,
    removeAgent,
    setCurrentAgent,
    setLoading,
    setError
  };
});

// ========== 工作流 Store ==========
export const useWorkflowStore = defineStore('workflow', () => {
  // State
  const workflows = ref<Workflow[]>([]);
  const currentWorkflow = ref<Workflow | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const draftDefinition = ref<any>(null);

  // Getters
  const workflowCount = computed(() => workflows.value.length);
  const publishedWorkflows = computed(() => 
    workflows.value.filter(w => w.status === 'PUBLISHED')
  );
  const workflowMap = computed(() => 
    new Map(workflows.value.map(w => [w.id, w]))
  );

  // Actions
  const setWorkflows = (list: Workflow[]) => {
    workflows.value = list;
  };

  const addWorkflow = (workflow: Workflow) => {
    workflows.value.push(workflow);
  };

  const updateWorkflow = (id: string, updates: Partial<Workflow>) => {
    const index = workflows.value.findIndex(w => w.id === id);
    if (index !== -1) {
      workflows.value[index] = { ...workflows.value[index], ...updates };
    }
  };

  const removeWorkflow = (id: string) => {
    workflows.value = workflows.value.filter(w => w.id !== id);
  };

  const setCurrentWorkflow = (workflow: Workflow | null) => {
    currentWorkflow.value = workflow;
  };

  const setDraftDefinition = (def: any) => {
    draftDefinition.value = def;
  };

  const clearDraft = () => {
    draftDefinition.value = null;
  };

  const setLoading = (val: boolean) => {
    loading.value = val;
  };

  const setError = (err: string | null) => {
    error.value = err;
  };

  return {
    // State
    workflows,
    currentWorkflow,
    loading,
    error,
    draftDefinition,
    // Getters
    workflowCount,
    publishedWorkflows,
    workflowMap,
    // Actions
    setWorkflows,
    addWorkflow,
    updateWorkflow,
    removeWorkflow,
    setCurrentWorkflow,
    setDraftDefinition,
    clearDraft,
    setLoading,
    setError
  };
});

// ========== UI Store ==========
export const useUIStore = defineStore('ui', () => {
  // State
  const sidebarCollapsed = ref(false);
  const theme = ref<'light' | 'dark'>('light');
  const loading = ref(false);
  const loadingText = ref('');
  const breadcrumbs = ref<{ title: string; path?: string }[]>([]);

  // Getters
  const isDark = computed(() => theme.value === 'dark');

  // Actions
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  };

  const setSidebarCollapsed = (val: boolean) => {
    sidebarCollapsed.value = val;
  };

  const setTheme = (newTheme: 'light' | 'dark') => {
    theme.value = newTheme;
    // 应用主题
    if (newTheme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const toggleTheme = () => {
    setTheme(theme.value === 'light' ? 'dark' : 'light');
  };

  const startLoading = (text = '加载中...') => {
    loading.value = true;
    loadingText.value = text;
  };

  const stopLoading = () => {
    loading.value = false;
    loadingText.value = '';
  };

  const setBreadcrumbs = (items: { title: string; path?: string }[]) => {
    breadcrumbs.value = items;
  };

  return {
    // State
    sidebarCollapsed,
    theme,
    loading,
    loadingText,
    breadcrumbs,
    // Getters
    isDark,
    // Actions
    toggleSidebar,
    setSidebarCollapsed,
    setTheme,
    toggleTheme,
    startLoading,
    stopLoading,
    setBreadcrumbs
  };
});

export default {
  useKnowledgeStore,
  useAgentStore,
  useWorkflowStore,
  useUIStore
};
