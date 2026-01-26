<template>
  <div class="visual-workflow-editor">


    <!-- Sub Header (Workflow Logic Toolbar) -->
    <div class="dify-sub-header">
      <div class="sub-left">
        <div class="breadcrumb">
          <el-icon class="app-icon"><TrendCharts /></el-icon>
          <span class="app-name">{{ workflowName || '未命名工作流' }}</span>
          <el-icon class="edit-icon"><Edit /></el-icon>
        </div>
        <nav class="sub-nav">
          <a href="#" class="sub-nav-item active">编排</a>
          <a href="#" class="sub-nav-item">访问 API</a>
          <a href="#" class="sub-nav-item">日志与标注</a>
          <a href="#" class="sub-nav-item">监测</a>
        </nav>
      </div>

      <div class="sub-right">
        <div class="save-status">
          <span class="status-dot"></span>
          自动保存 {{ lastSavedTime }}
        </div>
        <el-divider direction="vertical" />
        <div class="action-group">
          <el-button link class="preview-btn"><el-icon><VideoPlay /></el-icon> 预览</el-button>
          <el-button link><el-icon><RefreshRight /></el-icon></el-button>
          <el-button link><el-icon><Operation /></el-icon></el-button>
          <el-button plain class="func-btn">功能</el-button>
          <el-dropdown split-button type="primary" @click="handleSave">
            发布
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>仅保存草稿</el-dropdown-item>
                <el-dropdown-item>发布并运行</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link><el-icon><Clock /></el-icon></el-button>
          
          <el-dropdown trigger="click">
            <el-button link><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>导出工作流</el-dropdown-item>
                <el-dropdown-item>导入工作流</el-dropdown-item>
                <el-dropdown-item divided style="color: #f56c6c" @click="onDeleteWorkflow">删除工作流</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <div class="editor-container">
      <!-- Left Toolbar Rail (Floating Style) -->
      <div class="tool-rail">
         <!-- Add Node -->
         <el-tooltip content="添加节点" placement="right" effect="light">
           <div class="tool-item add-node" @click="showPalette = !showPalette">
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
               <circle cx="12" cy="12" r="10" fill="#64748b"/>
               <path d="M12 8V16M8 12H16" stroke="white" stroke-width="2" stroke-linecap="round"/>
             </svg>
           </div>
         </el-tooltip>

         <!-- Add Note -->
         <el-tooltip content="添加注释" placement="right" effect="light">
           <div class="tool-item" @click="onAddNote">
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#64748b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
               <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/>
             </svg>
           </div>
         </el-tooltip>

         <el-divider />

         <!-- Pointer Mode -->
         <el-tooltip content="指针模式" placement="right" effect="light">
           <div class="tool-item pointer-btn" :class="{ active: interactionMode === 'pointer' }" @click="interactionMode = 'pointer'">
             <svg width="20" height="20" viewBox="0 0 24 24" :fill="interactionMode === 'pointer' ? '#155eef' : 'none'" :stroke="interactionMode === 'pointer' ? '#155eef' : '#64748b'" stroke-width="2">
               <path d="M3 3l7.07 16.97 2.51-7.39 7.39-2.51L3 3z"/>
               <path d="m13 13 6 6"/>
             </svg>
           </div>
         </el-tooltip>

         <!-- Hand Mode -->
         <el-tooltip content="手模式" placement="right" effect="light">
           <div class="tool-item" :class="{ active: interactionMode === 'hand' }" @click="interactionMode = 'hand'">
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#64748b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
               <path d="M18 11V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v0"/><path d="M14 10V4a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v0"/><path d="M10 10.5V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v0"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-1.2-5-4.5L4.5 12a2 2 0 1 1 2.8-2.8L10 12"/>
             </svg>
           </div>
         </el-tooltip>

         <el-divider />

         <!-- Organize Nodes -->
         <el-tooltip content="整理节点" placement="right" effect="light">
           <div class="tool-item">
             <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#64748b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
               <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 17.5h7M17.5 14v7"/>
             </svg>
           </div>
         </el-tooltip>

         <!-- Maximize / Fit View -->
         <el-tooltip content="最大化画布" placement="right" effect="light">
           <div class="tool-item" @click="onFitView">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#64748b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
              </svg>
           </div>
         </el-tooltip>

         <!-- More Actions -->
         <el-tooltip content="更多操作" placement="right" effect="light">
           <div class="tool-item">
             <el-icon><MoreFilled /></el-icon>
           </div>
         </el-tooltip>
      </div>

      <!-- Node Palette (Collapsible) -->
      <transition name="slide-fade">
        <div class="node-palette" v-if="showPalette">
          <div class="palette-header">
            <h3>节点库</h3>
            <el-icon class="close-btn" @click="showPalette = false"><Close /></el-icon>
          </div>
          <div class="palette-content">
            <div class="palette-group" v-for="(group, gIdx) in nodeGroups" :key="gIdx">
              <div class="group-title">{{ group.title }}</div>
              <div class="group-items">
                <div 
                  v-for="node in group.items" 
                  :key="node.type" 
                  class="palette-node-card"
                  draggable="true" 
                  @dragstart="onDragStart($event, node.type)"
                >
                  <div class="node-icon-wrapper" :style="{ backgroundColor: node.color }">
                    <el-icon><component :is="node.icon" /></el-icon>
                  </div>
                  <span class="node-label">{{ node.label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <!-- Center: Canvas Area -->
      <div class="canvas-area" :class="interactionMode" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model="elements"
          :default-zoom="1"
          :min-zoom="0.1"
          :max-zoom="4"
          :pan-on-drag="interactionMode === 'hand'"
          :selection-key="interactionMode === 'pointer' ? null : 'Shift'"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @keydown="onKeyDown"
          tabindex="0"
        >
          <!-- Custom Background (Dotted) -->
          <Background pattern-color="#f0f0f0" :gap="20" />
          
          <!-- Custom Node Templates (Dify Accent Style) -->
          <template #node-start="{ data }">
            <div class="dify-node start" :class="{ selected: selectedNode?.id === data.id }">
              <div class="node-header">
                <el-icon class="header-icon"><VideoPlay /></el-icon>
                <span class="title">开始</span>
              </div>
              <div class="node-body">
                <div class="body-text">输入变量与触发条件</div>
              </div>
              <Handle type="source" position="right" />
            </div>
          </template>

          <template #node-end="{ data }">
            <div class="dify-node end">
              <div class="node-header">
                <el-icon class="header-icon"><CircleCheck /></el-icon>
                <span class="title">结束</span>
              </div>
              <div class="node-body">
                <div class="body-text">输出结果</div>
              </div>
              <Handle type="target" position="left" />
            </div>
          </template>

          <template #node-llm="{ data }">
            <div class="dify-node llm">
              <div class="node-header">
                <el-icon class="header-icon"><Cpu /></el-icon>
                <span class="title">{{ data.label || 'LLM' }}</span>
                <el-icon class="more-icon"><MoreFilled /></el-icon>
              </div>
              <div class="node-body">
                <div class="model-badge">gpt-4o</div>
                <div class="body-text">{{ data.prompt ? data.prompt.slice(0, 40) + '...' : '配置 Prompt 指令...' }}</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
              <!-- Status Progress -->
              <div class="run-status" v-if="data.status === 'running'">
                <el-icon class="is-loading"><Loading /></el-icon>
              </div>
            </div>
          </template>

          <template #node-agent="{ data }">
            <div class="dify-node agent">
              <div class="node-header">
                <el-icon class="header-icon"><User /></el-icon>
                <span class="title">{{ data.label || '智能体' }}</span>
              </div>
              <div class="node-body">
                <div class="agent-ref">关联: {{ data.agentName || '选择智能体' }}</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <template #node-if_else="{ data }">
            <div class="dify-node logic">
              <div class="node-header">
                <el-icon class="header-icon"><Share /></el-icon>
                <span class="title">条件分支</span>
              </div>
              <div class="node-body">
                <div class="condition-list">
                  <div class="cond-item">IF 包含 "查询" <el-icon><Right /></el-icon></div>
                  <div class="cond-item">ELSE <el-icon><Right /></el-icon></div>
                </div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" id="if" style="top: 40%" />
              <Handle type="source" position="right" id="else" style="top: 70%" />
            </div>
          </template>

          <template #node-answer="{ data }">
            <div class="dify-node answer" :class="{ selected: selectedNode?.id === data.id }">
                <div class="node-header">
                    <el-icon class="header-icon"><ChatDotSquare /></el-icon>
                    <span class="title">直接回复</span>
                </div>
                <div class="node-body">
                    <div class="body-text">向用户输出最终答案</div>
                </div>
                <Handle type="target" position="left" />
            </div>
          </template>

          <template #node-knowledge_retrieval="{ data }">
            <div class="dify-node knowledge" :class="{ selected: selectedNode?.id === data.id }">
              <div class="node-header">
                <el-icon class="header-icon"><Collection /></el-icon>
                <span class="title">知识检索</span>
              </div>
              <div class="node-body">
                <div class="body-text">从现有知识库召回相关内容</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <template #node-question_classifier="{ data }">
            <div class="dify-node classifier" :class="{ selected: selectedNode?.id === data.id }">
              <div class="node-header">
                <el-icon class="header-icon"><Connection /></el-icon>
                <span class="title">问题分类器</span>
              </div>
              <div class="node-body">
                <div class="body-text">对输入问题进行意图分类</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <template #node-question_understanding="{ data }">
            <div class="dify-node understanding" :class="{ selected: selectedNode?.id === data.id }">
              <div class="node-header">
                <el-icon class="header-icon"><Sunny /></el-icon>
                <span class="title">问题理解</span>
              </div>
              <div class="node-body">
                <div class="body-text">解析查询参数并提取实体</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <!-- Transformation Group Nodes -->
          <template v-for="type in ['code', 'template_transform', 'variable_aggregator', 'document_extractor', 'variable_assigner', 'parameter_extractor']" :key="type" #[`node-${type}`]="{ data }">
            <div class="dify-node transform" :class="[type, { selected: selectedNode?.id === data.id }]">
              <div class="node-header">
                <el-icon class="header-icon"><component :is="getNodeIcon(type)" /></el-icon>
                <span class="title">{{ getDefaultLabel(type) }}</span>
              </div>
              <div class="node-body">
                <div class="body-text">执行{{ getDefaultLabel(type) }}逻辑...</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <!-- Tool Group Nodes -->
          <template v-for="type in ['http_request', 'list_operator']" :key="type" #[`node-${type}`]="{ data }">
            <div class="dify-node tool" :class="[type, { selected: selectedNode?.id === data.id }]">
              <div class="node-header">
                <el-icon class="header-icon"><component :is="getNodeIcon(type)" /></el-icon>
                <span class="title">{{ getDefaultLabel(type) }}</span>
              </div>
              <div class="node-body">
                <div class="body-text">调用外部{{ getDefaultLabel(type) }}...</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <!-- Logic Iteration Nodes -->
          <template v-for="type in ['iteration', 'loop']" :key="type" #[`node-${type}`]="{ data }">
            <div class="dify-node logic" :class="[type, { selected: selectedNode?.id === data.id }]">
              <div class="node-header">
                <el-icon class="header-icon"><component :is="getNodeIcon(type)" /></el-icon>
                <span class="title">{{ getDefaultLabel(type) }}</span>
              </div>
              <div class="node-body">
                <div class="body-text">对列表进行{{ getDefaultLabel(type) }}处理...</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>

          <!-- Standard Fallback for other node types -->
          <template #node-generic="{ type, data }">
            <div class="dify-node generic" :class="type">
               <div class="node-header">
                  <el-icon class="header-icon"><Grid /></el-icon>
                  <span class="title">{{ data.label || type }}</span>
               </div>
               <div class="node-body">
                  <div class="body-text">配置节点参数...</div>
               </div>
               <Handle type="target" position="left" />
               <Handle type="source" position="right" />
            </div>
          </template>
        </VueFlow>
      </div>

      <!-- Right Sidebar: Properties Panel (Dify Style) -->
      <transition name="slide-left">
        <div class="properties-panel" v-if="selectedNode">
          <div class="panel-header">
            <div class="title-with-icon">
              <el-icon :style="{ color: getNodeColor(selectedNode.type) }">
                <component :is="getNodeIcon(selectedNode.type)" />
              </el-icon>
              <h3>{{ selectedNode.type.toUpperCase() }} 设置</h3>
            </div>
            <div class="panel-header-actions">
              <el-tooltip content="删除节点" placement="top">
                <el-icon class="delete-node-btn" @click="deleteNode"><Delete /></el-icon>
              </el-tooltip>
              <el-icon class="close-btn" @click="selectedNode = null"><Close /></el-icon>
            </div>
          </div>
          
          <div class="panel-content">
            <el-form label-position="top">
              <el-form-item label="名称">
                <el-input v-model="selectedNode.data.label" placeholder="设置节点名称" @change="updateNode" />
              </el-form-item>

              <el-divider />

              <!-- Specific LLM Form -->
              <template v-if="selectedNode.type === 'llm'">
                <el-form-item label="模型设置">
                  <el-select v-model="selectedNode.data.model" style="width: 100%">
                    <el-option label="gpt-4o" value="gpt-4o" />
                    <el-option label="claude-3-opus" value="claude-3" />
                  </el-select>
                </el-form-item>
                <el-form-item label="系统提示词 (SYSTEM PROMPT)">
                  <el-input 
                    type="textarea" 
                    v-model="selectedNode.data.prompt" 
                    :rows="6" 
                    placeholder="请输入模型指令..."
                  />
                </el-form-item>
              </template>

              <!-- Specific Agent Form -->
              <template v-if="selectedNode.type === 'agent'">
                <el-form-item label="选择智能体">
                  <el-select v-model="selectedNode.data.agentId" style="width: 100%" @change="onAgentChange">
                    <el-option
                      v-for="agent in agentList"
                      :key="agent.id"
                      :label="agent.name"
                      :value="agent.id"
                    />
                  </el-select>
                </el-form-item>
              </template>

              <!-- Generic Input Settings -->
              <el-form-item label="输入变量">
                <div class="variable-list">
                  <div class="var-item">
                     <span class="var-name">{{ `{` + `{` }} sys.query {{ `}` + `}` }}</span>
                     <span class="var-type">String</span>
                  </div>
                  <el-button link type="primary" :icon="Plus" size="small">添加变量</el-button>
                </div>
              </el-form-item>
            </el-form>
          </div>

          <div class="panel-footer">
            <el-button link type="danger" :icon="Delete" @click="deleteNode">删除此节点</el-button>
          </div>
        </div>
      </transition>
    </div>

    <!-- Mini Map -->
    <div class="mini-map-container" v-show="!showPalette">
        <el-icon><MapLocation /></el-icon>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { VueFlow, Handle, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import '@vue-flow/core/dist/style.css';
import { 
  VideoPlay, User, Tools, Share, CircleCheck, Search, Grid, Collection, 
  CaretBottom, Connection, TrendCharts, Edit, Operation, VideoPause,
  RefreshRight, MoreFilled, Loading, Plus, Close, Pointer, FullScreen, Aim,
  Right, MapLocation, Delete, Clock, Cpu, ChatDotSquare, Monitor, DocumentCopy,
  Files, EditPen, Link, Document, Scissor, Postcard, Sunny, Refresh,
  CirclePlusFilled, DocumentAdd
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAgentList } from '@/api/agent';
import { createWorkflow, getWorkflow } from '@/api/workflow';

// HandIcon placeholder for Pointer (can use Pointer icon instead)
const HandIcon = Pointer;

const router = useRouter();
const route = useRoute();

const workflowName = ref('');
const isEdit = ref(false);
const saving = ref(false);
const elements = ref([]);
const selectedNode = ref(null);
const showPalette = ref(true);
const interactionMode = ref('pointer'); // 'pointer' or 'hand'
const agentList = ref([]);
const lastSavedTime = ref('16:12:45');
let nodeIdCounter = Date.now();

const { fitView } = useVueFlow();

const onFitView = () => {
    fitView();
};

const onAddNote = () => {
    const newNode = {
        id: `node_note_${Date.now()}`,
        type: 'note',
        position: { x: 400, y: 300 },
        data: { label: '新注释', text: '在此输入备注...' }
    };
    elements.value.push(newNode);
};

const onKeyDown = (event) => {
    if ((event.key === 'Backspace' || event.key === 'Delete') && selectedNode.value) {
        deleteNode();
    }
};

const onDeleteWorkflow = () => {
    ElMessageBox.confirm('确定要删除此工作流吗？此操作不可撤销。', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(() => {
        // Implement actual deletion logic here
        ElMessage.success('工作流已删除');
        router.push('/dashboard/workflow');
    }).catch(() => {});
};

const nodeGroups = [
  {
    title: '智能',
    items: [
      { type: 'llm', label: 'LLM', icon: Cpu, color: '#f0fdf4' },
      { type: 'knowledge_retrieval', label: '知识检索', icon: Collection, color: '#fffbeb' },
      { type: 'answer', label: '直接回复', icon: ChatDotSquare, color: '#eff6ff' },
      { type: 'agent', label: 'Agent', icon: User, color: '#f5f3ff' },
      { type: 'question_classifier', label: '问题分类器', icon: Connection, color: '#fdf4ff' },
      { type: 'question_understanding', label: '问题理解', icon: Sunny, color: '#ecfdf5' },
    ]
  },
  {
    title: '逻辑',
    items: [
      { type: 'if_else', label: '条件分支', icon: Share, color: '#f8fafc' },
      { type: 'iteration', label: '迭代', icon: RefreshRight, color: '#f8fafc' },
      { type: 'loop', label: '循环', icon: Refresh, color: '#f8fafc' },
    ]
  },
  {
    title: '转换',
    items: [
      { type: 'code', label: '代码执行', icon: Monitor, color: '#f2e6ff' },
      { type: 'template_transform', label: '模板转换', icon: DocumentCopy, color: '#f2e6ff' },
      { type: 'variable_aggregator', label: '变量聚合器', icon: Files, color: '#f2e6ff' },
      { type: 'document_extractor', label: '文档提取器', icon: Document, color: '#f2e6ff' },
      { type: 'variable_assigner', label: '变量赋值', icon: EditPen, color: '#f2e6ff' },
      { type: 'parameter_extractor', label: '参数提取器', icon: Scissor, color: '#f2e6ff' },
    ]
  },
  {
    title: '工具',
    items: [
      { type: 'http_request', label: 'HTTP 请求', icon: Link, color: '#fdf6ec' },
      { type: 'list_operator', label: '列表操作', icon: Operation, color: '#fdf6ec' },
    ]
  }
];

onMounted(async () => {
  if (route.params.id) {
    isEdit.value = true;
    await loadWorkflow(route.params.id);
  } else {
    // Default nodes for new workflow
    elements.value = [
      { id: 'start_1', type: 'start', position: { x: 100, y: 300 }, data: { id: 'start_1' } },
      { id: 'end_1', type: 'end', position: { x: 1000, y: 300 }, data: { id: 'end_1' } }
    ];
  }
  await fetchAgents();
  
  // Timer for "last saved"
  setInterval(() => {
    const now = new Date();
    lastSavedTime.value = `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  }, 60000);
});

const fetchAgents = async () => {
  try {
    const res = await getAgentList();
    agentList.value = res || [];
  } catch (e) { console.error(e); }
};

const loadWorkflow = async (id) => {
  try {
    const workflow = await getWorkflow(id);
    workflowName.value = workflow.workflowName;
    if (workflow.workflowDefinition?.nodes) {
        elements.value = [
          ...workflow.workflowDefinition.nodes,
          ...(workflow.workflowDefinition.edges || [])
        ];
    }
  } catch (e) { ElMessage.error('加载失败'); }
};

const onDragStart = (event, type) => {
  event.dataTransfer.setData('application/vueflow', type);
};

const onDrop = (event) => {
  const type = event.dataTransfer.getData('application/vueflow');
  const { left, top } = event.currentTarget.getBoundingClientRect();
  const position = {
    x: event.clientX - left,
    y: event.clientY - top
  };

  const newNode = {
    id: `node_${++nodeIdCounter}`,
    type,
    position,
    data: { 
      id: `node_${nodeIdCounter}`, 
      label: getDefaultLabel(type)
    }
  };

  elements.value.push(newNode);
};

const getDefaultLabel = (type) => {
  const labels = {
    start: '开始',
    end: '结束',
    answer: '直接回复',
    llm: 'LLM',
    knowledge_retrieval: '知识检索',
    question_classifier: '问题分类器',
    question_understanding: '问题理解',
    if_else: '条件分支',
    iteration: '迭代',
    loop: '循环',
    code: '代码执行',
    template_transform: '模板转换',
    variable_aggregator: '变量聚合器',
    document_extractor: '文档提取器',
    variable_assigner: '变量赋值',
    parameter_extractor: '参数提取器',
    agent: 'Agent',
    http_request: 'HTTP 请求',
    list_operator: '列表操作',
    skill: '技能',
    note: '注释'
  };
  return labels[type] || type;
};

const onNodeClick = (event) => {
  selectedNode.value = event.node;
};

const onPaneClick = () => {
  selectedNode.value = null;
};

const updateNode = () => {
  elements.value = [...elements.value];
};

const deleteNode = () => {
  if (selectedNode.value) {
    elements.value = elements.value.filter(el => el.id !== selectedNode.value.id);
    selectedNode.value = null;
  }
};

const handleSave = async () => {
  saving.value = true;
  try {
    const nodes = elements.value.filter(el => !el.source);
    const edges = elements.value.filter(el => el.source);
    
    await createWorkflow({
      workflowName: workflowName.value || '新建工作流',
      workflowDefinition: { nodes, edges }
    });
    ElMessage.success('已存为草稿并发布');
  } catch (e) { ElMessage.error('保存失败'); }
  finally { saving.value = false; }
};

const getNodeIcon = (type) => {
  const map = { 
    llm: Cpu, 
    agent: User, 
    start: VideoPlay, 
    end: CircleCheck, 
    if_else: Share, 
    knowledge_retrieval: Collection,
    question_classifier: Connection,
    question_understanding: Sunny,
    iteration: RefreshRight,
    loop: Refresh,
    code: Monitor,
    template_transform: DocumentCopy,
    variable_aggregator: Files,
    document_extractor: Document,
    variable_assigner: EditPen,
    parameter_extractor: Scissor,
    http_request: Link,
    list_operator: Operation,
    answer: ChatDotSquare
  };
  return map[type] || Grid;
};

const getNodeColor = (type) => {
    const map = { 
        llm: '#10b981', 
        agent: '#6366f1', 
        start: '#10b981', 
        end: '#ef4444', 
        if_else: '#64748b',
        knowledge_retrieval: '#f59e0b',
        question_classifier: '#8b5cf6',
        question_understanding: '#ec4899',
        iteration: '#6366f1',
        loop: '#6366f1',
        code: '#8b5cf6',
        template_transform: '#8b5cf6',
        variable_aggregator: '#8b5cf6',
        document_extractor: '#8b5cf6',
        variable_assigner: '#8b5cf6',
        parameter_extractor: '#8b5cf6',
        http_request: '#f97316',
        list_operator: '#f97316',
        answer: '#3b82f6'
    };
    return map[type] || '#334155';
};

const onAgentChange = (val) => {
    const agent = agentList.value.find(a => a.id === val);
    if (agent && selectedNode.value) {
        selectedNode.value.data.agentName = agent.name;
    }
};

</script>

<style scoped>
.visual-workflow-editor {
  /* Fits exactly in screen: 100vh - Navbar(72px) - AppMain Padding(40px) */
  height: calc(100vh - 115px); 
  width: calc(100% + 40px);
  margin: -20px; /* Bleed into app-main padding for flush look */
  display: flex;
  flex-direction: column;
  background-color: #f7f8fa;
  color: #334155;
  font-family: 'Inter', -apple-system, sans-serif;
  overflow: hidden;
}

/* --- Dify Sub Header --- */
.dify-sub-header {
  height: 52px;
  min-height: 52px;
  background: #fcfcfd;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 90;
}

.sub-left, .sub-right {
  display: flex;
  align-items: center;
  height: 100%;
}

.sub-right {
  gap: 12px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 48px;
}

.app-icon { color: #d97706; padding: 4px; background: #fffbeb; border-radius: 4px; }
.app-name { font-weight: 600; font-size: 14px; }
.edit-icon { font-size: 14px; color: #94a3b8; cursor: pointer; }

.sub-nav { display: flex; gap: 32px; }
.sub-nav-item {
  font-size: 13px;
  text-decoration: none;
  color: #64748b;
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 4px;
  position: relative;
}

.sub-nav-item.active {
  color: #1e293b;
  font-weight: 700;
}

.sub-nav-item.active::after {
  content: '';
  position: absolute;
  bottom: 0px;
  left: 0;
  width: 100%;
  height: 2px;
  background: #155eef;
}

.save-status { 
  font-size: 12px; 
  color: #94a3b8; 
  display: flex; 
  align-items: center; 
  gap: 6px;
  white-space: nowrap;
}

.status-dot { width: 6px; height: 6px; background: #10b981; border-radius: 50%; }

.action-group { 
  display: flex; 
  align-items: center; 
  gap: 4px; 
  height: 100%;
}

.action-group .el-button, 
.action-group .el-dropdown {
  vertical-align: middle;
  display: inline-flex;
  align-items: center;
  height: 32px !important;
}

.func-btn { 
  border-color: #d1d5db !important; 
  color: #374151 !important; 
  padding: 0 12px !important;
}

.preview-btn {
  color: #64748b !important;
  font-weight: 500 !important;
}

/* --- Editor Layout --- */
.editor-container {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

.tool-rail {
  position: absolute;
  left: 16px;
  top: 16px;
  width: 48px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 4px;
  gap: 4px;
  z-index: 80;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06);
}

.tool-item {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
  font-size: 18px;
}

.tool-item:hover { background: #f3f4f6; color: #1e293b; }

.tool-item.active { 
  background: #eff6ff; 
  color: #155eef; 
}

.tool-item.highlight {
  color: #64748b;
  margin-top: 4px;
}

.tool-item.highlight:hover {
  background: #f1f5f9;
}

.el-divider--horizontal {
  margin: 8px 0 !important;
  width: 24px !important;
  min-width: 24px !important;
}

.node-palette {
  position: absolute;
  left: 72px;
  top: 16px;
  width: 280px;
  max-height: calc(100% - 32px);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  z-index: 80;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.palette-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.palette-header h3 { font-size: 14px; font-weight: 700; margin: 0; }
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.delete-node-btn {
  cursor: pointer;
  color: #94a3b8;
  font-size: 16px;
  transition: color 0.2s;
}

.delete-node-btn:hover {
  color: #ef4444;
}

.close-btn { cursor: pointer; color: #94a3b8; }

.palette-content { padding: 12px; overflow-y: auto; }
.group-title { font-size: 12px; font-weight: 600; color: #94a3b8; margin: 12px 0 8px; }

.group-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.palette-node-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.palette-node-card:hover { border-color: #155eef; box-shadow: 0 1px 2px rgba(21, 94, 239, 0.1); }

.node-icon-wrapper {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.node-label { font-size: 12px; font-weight: 500; }

.canvas-area { flex: 1; position: relative; }
.canvas-area.hand { cursor: grab; }
.canvas-area.hand:active { cursor: grabbing; }
.canvas-area.pointer { cursor: default; }

/* --- Dify style Nodes --- */
.dify-node {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  width: 240px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);
  transition: border-color 0.2s;
  pointer-events: auto;
}

.dify-node.selected { border-color: #155eef; border-width: 2px; }

.node-header {
  padding: 8px 12px;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #f3f4f6;
  position: relative;
}

/* Colors for headers */
.start .node-header { background: #ecfdf5; border-bottom-color: #d1fae5; }
.end .node-header { background: #fef2f2; border-bottom-color: #fee2e2; }
.llm .node-header { background: #f0fdf4; border-bottom-color: #dcfce7; }
.agent .node-header { background: #f5f3ff; border-bottom-color: #ede9fe; }
.logic .node-header { background: #f8fafc; border-bottom-color: #f1f5f9; }

.header-icon { font-size: 16px; }
.start .header-icon { color: #10b981; }
.end .header-icon { color: #ef4444; }
.llm .header-icon { color: #0ebf9a; }

.node-header .title { font-size: 13px; font-weight: 700; flex: 1; }
.more-icon { color: #94a3b8; cursor: pointer; }

.node-body { padding: 12px; }
.body-text { font-size: 12px; color: #64748b; line-height: 1.5; }

.model-badge {
    display: inline-block;
    background: #eff6ff;
    color: #155eef;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 700;
    margin-bottom: 6px;
}

.cond-item { font-size: 11px; padding: 4px 6px; background: #f8fafc; border-radius: 4px; display: flex; justify-content: space-between; margin-bottom: 4px; }

/* Handle Overrides */
.vue-flow__handle {
  width: 8px;
  height: 8px;
  background: #fff;
  border: 2px solid #cbd5e1;
}

.vue-flow__handle:hover { border-color: #155eef; background: #eff6ff; }

/* --- Properties Panel --- */
.properties-panel {
  width: 420px;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-with-icon { display: flex; align-items: center; gap: 10px; }
.panel-header h3 { margin: 0; font-size: 14px; font-weight: 800; }

.panel-content { flex: 1; padding: 20px; overflow-y: auto; }

.variable-list { margin-top: 8px; }
.var-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
  margin-bottom: 4px;
  font-size: 12px;
}
.var-name { font-family: monospace; color: #155eef; }
.var-type { color: #94a3b8; }

.panel-footer {
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;
}

.mini-map-container {
    position: absolute;
    right: 20px;
    bottom: 20px;
    width: 40px;
    height: 40px;
    background: #fff;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);
    cursor: pointer;
}

/* Animations */
.slide-fade-enter-active, .slide-fade-leave-active { transition: all 0.3s ease; }
.slide-fade-enter-from, .slide-fade-leave-to { opacity: 0; transform: translateX(-20px); }

.slide-left-enter-active, .slide-left-leave-active { transition: all 0.3s cubic-bezier(0.165, 0.84, 0.44, 1); }
.slide-left-enter-from, .slide-left-leave-to { transform: translateX(100%); }

</style>
