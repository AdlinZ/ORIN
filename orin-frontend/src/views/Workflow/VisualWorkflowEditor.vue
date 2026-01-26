<template>
  <div class="visual-workflow-editor">
    <div class="editor-header">
      <el-page-header @back="goBack">
        <template #content>
          <span class="text-large font-600">{{ isEdit ? '编辑工作流' : '创建工作流' }}</span>
        </template>
        <template #extra>
          <el-button-group>
            <el-button :disabled="!canUndo" @click="undo" title="撤销 (Ctrl+Z)">
              <el-icon><RefreshLeft /></el-icon>
            </el-button>
            <el-button :disabled="!canRedo" @click="redo" title="重做 (Ctrl+Y)">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </el-button-group>
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">
            保存
          </el-button>
        </template>
      </el-page-header>
    </div>

    <div class="editor-container">
      <!-- Left Sidebar: Node Palette -->
      <div class="node-palette">
        <h3>节点类型</h3>
        <div class="palette-section">
          <div class="palette-group-title">基础节点</div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'start')">
            <el-icon><VideoPlay /></el-icon>
            <span>开始</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'end')">
            <el-icon><CircleCheck /></el-icon>
            <span>结束</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'answer')">
            <el-icon><ChatLineSquare /></el-icon>
            <span>直接回复</span>
          </div>
          
          <div class="palette-group-title">大模型 & 智能</div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'llm')">
            <el-icon><Cpu /></el-icon>
            <span>LLM</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'agent')">
            <el-icon><User /></el-icon>
            <span>智能体</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'knowledge_retrieval')">
            <el-icon><Collection /></el-icon>
            <span>知识检索</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'question_classifier')">
            <el-icon><Connection /></el-icon>
            <span>问题分类器</span>
          </div>

          <div class="palette-group-title">逻辑 & 控制</div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'if_else')">
            <el-icon><Share /></el-icon>
            <span>条件分支</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'iteration')">
            <el-icon><Refresh /></el-icon>
            <span>迭代</span>
          </div>
           <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'loop')">
            <el-icon><RefreshRight /></el-icon>
            <span>循环</span>
          </div>

          <div class="palette-group-title">转换 & 数据</div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'code')">
            <el-icon><Monitor /></el-icon>
            <span>代码执行</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'template_transform')">
            <el-icon><DocumentCopy /></el-icon>
            <span>模板转换</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'variable_aggregator')">
            <el-icon><Files /></el-icon>
            <span>变量聚合器</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'variable_assigner')">
            <el-icon><EditPen /></el-icon>
            <span>变量赋值</span>
          </div>

          <div class="palette-group-title">外部 & 工具</div>
           <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'http_request')">
            <el-icon><Link /></el-icon>
            <span>HTTP 请求</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'tool')">
            <el-icon><Tools /></el-icon>
            <span>工具</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'document_extractor')">
            <el-icon><Document /></el-icon>
            <span>文档提取器</span>
          </div>
           <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'parameter_extractor')">
            <el-icon><Scissor /></el-icon>
            <span>参数提取器</span>
          </div>
          <div class="palette-node" draggable="true" @dragstart="onDragStart($event, 'note')">
            <el-icon><Postcard /></el-icon>
            <span>注释卡片</span>
          </div>
        </div>
      </div>

      <!-- Center: Canvas -->
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model="elements"
          :default-zoom="1"
          :min-zoom="0.2"
          :max-zoom="4"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
        >
          <Background />
          <Controls />
          
          <template #node-start="{ data }">
            <div class="custom-node start-node" :class="getStatusClass(data.status)">
              <el-icon><VideoPlay /></el-icon>
              <span>开始</span>
            </div>
          </template>

          <template #node-end="{ data }">
            <div class="custom-node end-node" :class="getStatusClass(data.status)">
              <el-icon><CircleCheck /></el-icon>
              <div class="node-content">
                  <div class="node-title">结束</div>
                  <div class="node-subtitle" v-if="data.output_type">Type: {{ data.output_type }}</div>
              </div>
            </div>
          </template>

          <template #node-answer="{ data }">
            <div class="custom-node end-node" :class="getStatusClass(data.status)">
              <el-icon><ChatLineSquare /></el-icon>
              <span>直接回复</span>
            </div>
          </template>

          <template #node-agent="{ data }">
            <div class="custom-node agent-node" :class="getStatusClass(data.status)">
              <el-icon><User /></el-icon>
              <div class="node-content">
                <div class="node-title">{{ data.label || '智能体' }}</div>
                <div class="node-subtitle" v-if="data.agentId">Agent ID: {{ data.agentId }}</div>
              </div>
            </div>
          </template>

          <template #node-llm="{ data }">
            <div class="custom-node llm-node" :class="getStatusClass(data.status)">
              <el-icon><Cpu /></el-icon>
              <div class="node-content">
                <div class="node-title">{{ data.label || 'LLM' }}</div>
                <div class="node-subtitle">Model: {{ data.model || 'Default' }}</div>
              </div>
            </div>
          </template>
          
          <template #node-knowledge_retrieval="{ data }">
            <div class="custom-node knowledge-node" :class="getStatusClass(data.status)">
              <el-icon><Collection /></el-icon>
              <span>知识检索</span>
            </div>
          </template>
          
          <template #node-question_classifier="{ data }">
            <div class="custom-node logic-node" :class="getStatusClass(data.status)">
              <el-icon><Connection /></el-icon>
              <span>问题分类器</span>
            </div>
          </template>

          <template #node-if_else="{ data }">
            <div class="custom-node logic-node" :class="getStatusClass(data.status)">
              <el-icon><Share /></el-icon>
              <span>条件分支</span>
            </div>
          </template>
          
          <template #node-iteration="{ data }">
            <div class="custom-node logic-node" :class="getStatusClass(data.status)">
              <el-icon><Refresh /></el-icon>
              <span>迭代</span>
            </div>
          </template>

           <template #node-loop="{ data }">
            <div class="custom-node logic-node" :class="getStatusClass(data.status)">
              <el-icon><RefreshRight /></el-icon>
              <span>循环</span>
            </div>
          </template>

          <template #node-code="{ data }">
            <div class="custom-node transform-node" :class="getStatusClass(data.status)">
              <el-icon><Monitor /></el-icon>
              <span>代码执行</span>
            </div>
          </template>
          
          <template #node-template_transform="{ data }">
            <div class="custom-node transform-node" :class="getStatusClass(data.status)">
              <el-icon><DocumentCopy /></el-icon>
              <span>模板转换</span>
            </div>
          </template>
          
          <template #node-variable_aggregator="{ data }">
            <div class="custom-node transform-node" :class="getStatusClass(data.status)">
              <el-icon><Files /></el-icon>
              <span>变量聚合器</span>
            </div>
          </template>
          
           <template #node-variable_assigner="{ data }">
            <div class="custom-node transform-node" :class="getStatusClass(data.status)">
              <el-icon><EditPen /></el-icon>
              <span>变量赋值</span>
            </div>
          </template>

          <template #node-http_request="{ data }">
            <div class="custom-node tool-node" :class="getStatusClass(data.status)">
              <el-icon><Link /></el-icon>
              <div class="node-content">
                <div class="node-title">{{ data.label || 'HTTP 请求' }}</div>
                <div class="node-subtitle" v-if="data.method">{{ data.method }}</div>
              </div>
            </div>
          </template>
          
          <template #node-tool="{ data }">
            <div class="custom-node tool-node" :class="getStatusClass(data.status)">
              <el-icon><Tools /></el-icon>
              <div class="node-content">
                <div class="node-title">{{ data.label || '工具' }}</div>
                <div class="node-subtitle" v-if="data.tool_name">{{ data.tool_name }}</div>
              </div>
            </div>
          </template>

          <template #node-document_extractor="{ data }">
            <div class="custom-node tool-node" :class="getStatusClass(data.status)">
              <el-icon><Document /></el-icon>
              <span>文档提取器</span>
            </div>
          </template>
          
           <template #node-parameter_extractor="{ data }">
            <div class="custom-node tool-node" :class="getStatusClass(data.status)">
              <el-icon><Scissor /></el-icon>
              <span>参数提取器</span>
            </div>
          </template>
          
          <!-- Fallback for generic skill if needed -->
           <template #node-skill="{ data }">
            <div class="custom-node tool-node" :class="getStatusClass(data.status)">
              <el-icon><Tools /></el-icon>
              <div class="node-content">
                <div class="node-title">{{ data.label || '技能' }}</div>
                 <div class="node-subtitle" v-if="data.skillId">ID: {{ data.skillId }}</div>
              </div>
            </div>
          </template>

          <template #node-note="{ data }">
            <div 
              class="custom-node note-node" 
              :style="{ backgroundColor: data.theme_color || '#fff7d1', borderColor: data.theme_color || '#e6a23c' }"
            >
              <div class="note-header">
                <el-icon><Postcard /></el-icon>
                <span>{{ data.title || '注释' }}</span>
              </div>
              <div class="note-body">
                {{ data.text || data.content || '请输入内容...' }}
              </div>
            </div>
          </template>
        </VueFlow>
      </div>

      <!-- Right Sidebar: Properties Panel -->
      <div class="properties-panel" v-if="selectedNode">
        <h3>节点属性</h3>
        <el-form label-width="80px" size="small">
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.data.label" @change="updateNode" />
          </el-form-item>

          <!-- Agent Node Properties -->
          <template v-if="selectedNode.type === 'agent'">
            <el-form-item label="智能体">
              <el-select v-model="selectedNode.data.agentId" placeholder="选择智能体" @change="updateNode">
                <el-option
                  v-for="agent in agentList"
                  :key="agent.id"
                  :label="agent.name"
                  :value="agent.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="输入映射">
              <el-input
                type="textarea"
                v-model="selectedNode.data.inputMappingStr"
                :rows="3"
                placeholder='{"message": "${previous.output}"}'
                @change="updateNode"
              />
            </el-form-item>
          </template>

          <!-- Skill Node Properties -->
          <template v-if="selectedNode.type === 'skill'">
            <el-form-item label="技能ID">
              <el-input-number v-model="selectedNode.data.skillId" @change="updateNode" />
            </el-form-item>
            <el-form-item label="输入映射">
              <el-input
                type="textarea"
                v-model="selectedNode.data.inputMappingStr"
                :rows="3"
                @change="updateNode"
              />
            </el-form-item>

          </template>

          <!-- LLM Node Properties -->
          <template v-if="selectedNode.type === 'llm'">
            <el-form-item label="模型">
              <el-input v-model="selectedNode.data.model" placeholder="Model Name" @change="updateNode" />
            </el-form-item>
             <el-form-item label="Prompt">
              <el-input
                type="textarea"
                v-model="selectedNode.data.prompt"
                :rows="3"
                @change="updateNode"
              />
            </el-form-item>
            <el-form-item label="输入映射">
              <el-input
                type="textarea"
                v-model="selectedNode.data.inputMappingStr"
                :rows="3"
                @change="updateNode"
              />
            </el-form-item>
          </template>

          <!-- Code Node Properties -->
          <template v-if="selectedNode.type === 'code'">
            <el-form-item label="代码语言">
               <el-select v-model="selectedNode.data.language" placeholder="Select Language" @change="updateNode">
                  <el-option label="Python" value="python" />
                  <el-option label="JavaScript" value="javascript" />
               </el-select>
            </el-form-item>
             <el-form-item label="代码">
               <el-input
                 type="textarea"
                 v-model="selectedNode.data.code"
                 :rows="5"
                 @change="updateNode"
               />
             </el-form-item>
          </template>
          
           <!-- HTTP Request Node Properties -->
          <template v-if="selectedNode.type === 'http_request'">
             <el-form-item label="Method">
               <el-select v-model="selectedNode.data.method" placeholder="Method" @change="updateNode">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
               </el-select>
            </el-form-item>
            <el-form-item label="URL">
               <el-input v-model="selectedNode.data.url" @change="updateNode" />
            </el-form-item>
          </template>

          <!-- Note Node Properties -->
          <template v-if="selectedNode.type === 'note'">
             <el-form-item label="标题">
               <el-input v-model="selectedNode.data.title" @change="updateNode" />
            </el-form-item>
            <el-form-item label="内容">
               <el-input 
                 type="textarea" 
                 v-model="selectedNode.data.text" 
                 :rows="4" 
                 @change="updateNode" 
               />
            </el-form-item>
             <el-form-item label="颜色">
               <el-color-picker v-model="selectedNode.data.theme_color" @change="updateNode" />
            </el-form-item>
          </template>

          <!-- Condition Node Properties -->
          <template v-if="['condition', 'if_else'].includes(selectedNode.type)">
            <el-form-item label="条件表达式">
              <el-input v-model="selectedNode.data.condition" @change="updateNode" />
            </el-form-item>
          </template>

          <el-form-item>
            <el-button type="danger" size="small" @click="deleteNode">删除节点</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- Bottom: Log Panel -->
    <div class="log-panel" v-if="showLogs">
      <div class="log-header">
        <h3>执行日志</h3>
        <div class="log-controls">
          <el-button-group size="small">
            <el-button :type="logFilter === 'all' ? 'primary' : ''" @click="logFilter = 'all'">
              全部
            </el-button>
            <el-button :type="logFilter === 'info' ? 'primary' : ''" @click="logFilter = 'info'">
              信息
            </el-button>
            <el-button :type="logFilter === 'warn' ? 'primary' : ''" @click="logFilter = 'warn'">
              警告
            </el-button>
            <el-button :type="logFilter === 'error' ? 'primary' : ''" @click="logFilter = 'error'">
              错误
            </el-button>
          </el-button-group>
          <el-button size="small" @click="clearLogs">清空</el-button>
          <el-button size="small" @click="showLogs = false">
            <el-icon><ArrowDown /></el-icon>
          </el-button>
        </div>
      </div>
      <div class="log-content" ref="logContainer">
        <div
          v-for="(log, index) in filteredLogs"
          :key="index"
          :class="['log-entry', `log-${log.level}`]"
        >
          <span class="log-time">{{ log.time }}</span>
          <span class="log-level">{{ log.level.toUpperCase() }}</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
        <div v-if="filteredLogs.length === 0" class="log-empty">
          暂无日志
        </div>
      </div>
    </div>

    <!-- Log Toggle Button -->
    <div class="log-toggle" v-if="!showLogs" @click="showLogs = true">
      <el-icon><ArrowUp /></el-icon>
      查看日志 ({{ logs.length }})
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { VueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import { VideoPlay, User, Tools, Share, CircleCheck, RefreshLeft, RefreshRight, ArrowUp, ArrowDown, Cpu, ChatLineSquare, Collection, Connection, Refresh, Monitor, DocumentCopy, Files, EditPen, Link, Document, Scissor, Postcard } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getAgentList } from '@/api/agent';
import { createWorkflow, getWorkflow } from '@/api/workflow';

const router = useRouter();
const route = useRoute();

const isEdit = ref(false);
const saving = ref(false);
const elements = ref([]);
const selectedNode = ref(null);
const agentList = ref([]);
const workflowName = ref('');
let nodeIdCounter = 0;

// Log panel
const showLogs = ref(false);
const logs = ref([]);
const logFilter = ref('all');
const logContainer = ref(null);

const filteredLogs = computed(() => {
  if (logFilter.value === 'all') {
    return logs.value;
  }
  return logs.value.filter(log => log.level === logFilter.value);
});

// Add log entry
const addLog = (level, message) => {
  const now = new Date();
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  
  logs.value.push({
    time,
    level,
    message
  });
  
  // Auto-scroll to bottom
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight;
    }
  });
};

// Clear logs
const clearLogs = () => {
  logs.value = [];
};

// History management for undo/redo
const history = ref([]);
const historyIndex = ref(-1);
const canUndo = computed(() => historyIndex.value > 0);
const canRedo = computed(() => historyIndex.value < history.value.length - 1);

// Save current state to history
const saveToHistory = () => {
  // Remove any future states if we're not at the end
  history.value = history.value.slice(0, historyIndex.value + 1);
  
  // Deep clone current elements
  history.value.push(JSON.parse(JSON.stringify(elements.value)));
  historyIndex.value++;
  
  // Limit history to 50 states
  if (history.value.length > 50) {
    history.value.shift();
    historyIndex.value--;
  }
};

// Undo function
const undo = () => {
  if (canUndo.value) {
    historyIndex.value--;
    elements.value = JSON.parse(JSON.stringify(history.value[historyIndex.value]));
  }
};

// Redo function
const redo = () => {
  if (canRedo.value) {
    historyIndex.value++;
    elements.value = JSON.parse(JSON.stringify(history.value[historyIndex.value]));
  }
};

// Keyboard shortcuts
const handleKeydown = (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key === 'z') {
    event.preventDefault();
    undo();
  } else if ((event.ctrlKey || event.metaKey) && event.key === 'y') {
    event.preventDefault();
    redo();
  }
};

onMounted(async () => {
  if (route.params.id) {
    isEdit.value = true;
    await loadWorkflow(route.params.id);
    addLog('info', `加载工作流: ${workflowName.value}`);
  } else {
    addLog('info', '创建新工作流');
  }
  await fetchAgents();
  
  // Initialize history with current state
  saveToHistory();
  
  // Add keyboard event listener
  window.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});

const fetchAgents = async () => {
  try {
    const res = await getAgentList({ page: 0, size: 100 });
    if (res && res.content) {
      agentList.value = res.content;
    } else if (Array.isArray(res)) {
      agentList.value = res;
    }
  } catch (error) {
    console.error('Failed to fetch agents', error);
  }
};

const loadWorkflow = async (id) => {
  try {
    const workflow = await getWorkflow(id);
    workflowName.value = workflow.workflowName;
    
    if (workflow.workflowDefinition && workflow.workflowDefinition.nodes) {
      elements.value = [
        ...workflow.workflowDefinition.nodes.map(node => ({
          id: node.id,
          type: node.type,
          position: node.position,
          data: node.data || {}
        })),
        ...(workflow.workflowDefinition.edges || [])
      ];
    }
  } catch (error) {
    ElMessage.error('加载工作流失败');
  }
};

const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/vueflow', nodeType);
  event.dataTransfer.effectAllowed = 'move';
};

const onDrop = (event) => {
  const type = event.dataTransfer.getData('application/vueflow');
  
  const { left, top } = event.currentTarget.getBoundingClientRect();
  const position = {
    x: event.clientX - left - 75,
    y: event.clientY - top - 25
  };

  const newNode = {
    id: `node_${++nodeIdCounter}`,
    type,
    position,
    data: {
      label: getDefaultLabel(type)
    }
  };

  elements.value.push(newNode);
  saveToHistory(); // Save to history
  addLog('info', `添加节点: ${getDefaultLabel(type)}`);
};

const getDefaultLabel = (type) => {
  const labels = {
    start: '开始',
    end: '结束',
    answer: '直接回复',
    llm: 'LLM',
    knowledge_retrieval: '知识检索',
    question_classifier: '问题分类器',
    if_else: '条件分支',
    iteration: '迭代',
    loop: '循环',
    code: '代码执行',
    template_transform: '模板转换',
    variable_aggregator: '变量聚合器',
    variable_assigner: '变量赋值',
    http_request: 'HTTP 请求',
    tool: '工具',
    document_extractor: '文档提取器',
    parameter_extractor: '参数提取器',
    agent: '智能体',
    skill: '技能',
    note: '注释'
  };
  return labels[type] || type;
};

const onNodeClick = (event) => {
  selectedNode.value = event.node;
};

const onEdgeClick = (event) => {
  selectedNode.value = null;
};

const updateNode = () => {
  // Force reactivity update
  elements.value = [...elements.value];
  saveToHistory(); // Save to history
};

const deleteNode = () => {
  if (selectedNode.value) {
    const nodeName = selectedNode.value.data.label || selectedNode.value.type;
    elements.value = elements.value.filter(el => el.id !== selectedNode.value.id);
    selectedNode.value = null;
    saveToHistory(); // Save to history
    addLog('warn', `删除节点: ${nodeName}`);
  }
};

// Get status class for node
const getStatusClass = (status) => {
  if (!status) return '';
  return `node-status-${status}`;
};

const handleSave = async () => {
  if (!workflowName.value) {
    workflowName.value = await ElMessage.prompt('请输入工作流名称', '保存工作流', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }).then(({ value }) => value).catch(() => null);
    
    if (!workflowName.value) return;
  }

  saving.value = true;
  addLog('info', '开始保存工作流...');
  
  try {
    const nodes = elements.value.filter(el => !el.source);
    const edges = elements.value.filter(el => el.source);

    const payload = {
      workflowName: workflowName.value,
      workflowType: 'DAG',
      workflowDefinition: {
        nodes: nodes.map(node => ({
          id: node.id,
          type: node.type,
          position: node.position,
          data: {
            ...node.data,
            agentId: node.data.agentId,
            skillId: node.data.skillId,
            inputMapping: node.data.inputMappingStr ? JSON.parse(node.data.inputMappingStr) : {}
          }
        })),
        edges: edges.map(edge => ({
          id: edge.id,
          source: edge.source,
          target: edge.target
        }))
      }
    };

    await createWorkflow(payload);
    addLog('info', `工作流保存成功: ${workflowName.value}`);
    ElMessage.success('保存成功');
    goBack();
  } catch (error) {
    console.error(error);
    addLog('error', `保存失败: ${error.message || '未知错误'}`);
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
};

const goBack = () => {
  router.push('/dashboard/workflow/list');
};
</script>

<style scoped>
.visual-workflow-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.editor-header {
  background: white;
  padding: 16px 24px;
  border-bottom: 1px solid #e0e0e0;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-palette {
  width: 200px;
  background: white;
  border-right: 1px solid #e0e0e0;
  padding: 16px;
  overflow-y: auto;
}

.node-palette h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
}

.palette-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.palette-node:hover {
  background: #e0e0e0;
  transform: translateX(4px);
}

.palette-node:active {
  cursor: grabbing;
}

.canvas-area {
  flex: 1;
  position: relative;
}

.properties-panel {
  width: 300px;
  background: white;
  border-left: 1px solid #e0e0e0;
  padding: 16px;
  overflow-y: auto;
}

.properties-panel h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
}

/* Custom Node Styles */
.custom-node {
  padding: 12px 16px;
  border-radius: 8px;
  background: white;
  border: 2px solid var(--orin-primary);
  min-width: 150px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.start-node {
  border-color: #67c23a;
  background: #f0f9ff;
}

.agent-node {
  border-color: var(--orin-primary);
}

.skill-node {
  border-color: #e6a23c;
}

.llm-node {
  border-color: #90d3e6;
}

.condition-node {
  border-color: #909399;
}

.end-node {
  border-color: #f56c6c;
  background: #fef0f0;
}

.knowledge-node {
  border-color: #79bbff;
  background: #ecf5ff;
}

.logic-node {
  border-color: #909399;
  background: #f4f4f5;
}

.transform-node {
  border-color: #b886f8;
  background: #f2e6ff;
}

.tool-node {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.note-node {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 12px;
  min-width: 200px;
  min-height: 100px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.note-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
  width: 100%;
  border-bottom: 1px solid rgba(0,0,0,0.05);
  padding-bottom: 4px;
}

.note-body {
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  line-height: 1.4;
  width: 100%;
}

.node-content {
  flex: 1;
}

.node-title {
  font-weight: 600;
  font-size: 14px;
}

.node-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* Node Status Styles */
.node-status-running {
  border-color: var(--orin-primary) !important;
  animation: pulse 2s infinite;
}

.node-status-success {
  border-color: #67c23a !important;
  background: #f0f9ff !important;
}

.node-status-error {
  border-color: #f56c6c !important;
  background: #fef0f0 !important;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.7);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(64, 158, 255, 0);
  }
}

/* Log Panel Styles */
.log-panel {
  height: 200px;
  background: white;
  border-top: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid #e0e0e0;
}

.log-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.log-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}

.log-entry {
  padding: 4px 8px;
  margin-bottom: 2px;
  border-radius: 4px;
  display: flex;
  gap: 12px;
}

.log-time {
  color: #909399;
  min-width: 60px;
}

.log-level {
  font-weight: 600;
  min-width: 50px;
}

.log-message {
  flex: 1;
}

.log-info .log-level {
  color: var(--orin-primary);
}

.log-warn .log-level {
  color: #e6a23c;
}

.log-error {
  background: #fef0f0;
}

.log-error .log-level {
  color: #f56c6c;
}

.log-empty {
  text-align: center;
  color: #909399;
  padding: 32px;
}

.log-toggle {
  position: fixed;
  bottom: 16px;
  right: 16px;
  background: white;
  padding: 8px 16px;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s;
  z-index: 100;
}

.log-toggle:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  transform: translateY(-2px);
}
</style>
