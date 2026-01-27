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
          <a href="#" class="sub-nav-item" :class="{ active: activeTab === 'orchestrate' }" @click.prevent="activeTab = 'orchestrate'">编排</a>
          <a href="#" class="sub-nav-item" :class="{ active: activeTab === 'api' }" @click.prevent="activeTab = 'api'">访问 API</a>
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
          <el-button link class="preview-btn" @click="handlePreview"><el-icon><VideoPlay /></el-icon> 预览</el-button>
          <el-button link @click="handleSave()"><el-icon><RefreshRight /></el-icon> 保存</el-button>
          <el-button link><el-icon><Operation /></el-icon></el-button>
          <el-button plain class="func-btn">功能</el-button>
          <el-button type="primary" @click="handleSave()">保存</el-button>
          <el-button link><el-icon><Clock /></el-icon></el-button>
          
          <el-dropdown trigger="click">
            <el-button link><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="onExportWorkflow">导出工作流</el-dropdown-item>
                <el-dropdown-item>导入工作流</el-dropdown-item>
                <el-dropdown-item divided style="color: #f56c6c" @click="onDeleteWorkflow">删除工作流</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <WorkflowApiAccess v-if="activeTab === 'api'" />

    <div class="editor-container" v-show="activeTab === 'orchestrate'">
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
         <el-tooltip content="指针模式 (V)" placement="right" effect="light">
           <div class="tool-item pointer-btn" :class="{ active: interactionMode === 'pointer' }" @click="interactionMode = 'pointer'">
             <svg width="20" height="20" viewBox="0 0 24 24" :fill="interactionMode === 'pointer' ? '#155eef' : 'none'" :stroke="interactionMode === 'pointer' ? '#155eef' : '#64748b'" stroke-width="2">
               <path d="M3 3l7.07 16.97 2.51-7.39 7.39-2.51L3 3z"/>
               <path d="m13 13 6 6"/>
             </svg>
           </div>
         </el-tooltip>

         <!-- Hand Mode -->
         <el-tooltip content="手模式 (H)" placement="right" effect="light">
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
          @connect="onConnect"
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
                <div class="model-badge">
                    {{ (typeof data.model === 'object' ? data.model.name : data.model) || '选择模型' }}
                </div>
                <div class="body-text">{{ getSystemPrompt(data).slice(0, 40) + (getSystemPrompt(data).length > 40 ? '...' : '') || '配置 Prompt 指令...' }}</div>
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

          <!-- Custom Note Node -->
          <template #node-note="{ data }">
            <div class="dify-node note" :style="{ backgroundColor: data.theme === 'yellow' ? '#fef08a' : '#bfdbfe' }">
               <div class="note-header" v-if="data.showAuthor">
                 <span class="author">Dify</span>
                 <span class="date">{{ new Date().toLocaleDateString() }}</span>
               </div>
               <div class="note-content">
                 {{ getNoteText(data.text).slice(0, 100) + (getNoteText(data.text).length > 100 ? '...' : '') }}
               </div>
            </div>
          </template>

          <!-- Code Node -->
          <template #node-code="{ data }">
            <div class="dify-node code" :class="{ selected: selectedNode?.id === data.id }">
              <div class="node-header">
                <el-icon class="header-icon"><Monitor /></el-icon>
                <span class="title">{{ data.label || '代码执行' }}</span>
                <el-tag size="small" type="info" class="lang-tag">{{ data.code_language || 'python3' }}</el-tag>
              </div>
              <div class="node-body">
                <div class="code-preview" v-if="data.code">
                  {{ data.code.split('\n')[0] }}...
                </div>
                <div class="body-text" v-else>输入变量 -> 代码逻辑 -> 输出变量</div>
              </div>
              <Handle type="target" position="left" />
              <Handle type="source" position="right" />
            </div>
          </template>
        
          <!-- Variable Assigner Node -->
          <template #node-variable_assigner="{ data }">
             <div class="dify-node assigner" :class="{ selected: selectedNode?.id === data.id }">
                <div class="node-header">
                   <el-icon class="header-icon"><EditPen /></el-icon>
                   <span class="title">{{ data.label || '变量赋值' }}</span>
                </div>
                <div class="node-body">
                   <div class="assign-operation">
                      <span class="op-mode">{{ data.write_mode === 'append' ? '追加到' : '写入' }}</span>
                      <span class="target-var">{{ data.assigned_variable_selector ? data.assigned_variable_selector.join('.') : 'conversation.var' }}</span>
                   </div>
                </div>
                <Handle type="target" position="left" />
                <Handle type="source" position="right" />
             </div>
          </template>
          
          <!-- Transformation Group Nodes (Excluding code/assigner which are custom now) -->
          <template v-for="type in ['template_transform', 'variable_aggregator', 'document_extractor', 'parameter_extractor']" :key="type" #[`node-${type}`]="{ data }">
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
              <el-form-item label="名称" v-if="selectedNode.type !== 'note'">
                <el-input v-model="selectedNode.data.label" placeholder="设置节点名称" @change="updateNode" />
              </el-form-item>

              <el-divider />

              <!-- Specific LLM Form -->
              <template v-if="selectedNode.type === 'llm'">
                <el-form-item label="模型设置">
                  <el-select v-model="selectedNode.data.model" style="width: 100%">
                    <el-option 
                        v-for="m in availableModels" 
                        :key="m.value" 
                        :label="m.label" 
                        :value="m.value" 
                    />
                  </el-select>
                </el-form-item>

                <el-form-item label="上下文 (CONTEXT)">
                   <div class="context-selector">
                      <div class="context-list">
                         <div v-for="(ctx, idx) in (selectedNode.data.context?.variable_selector || [])" :key="idx" class="context-tag">
                            <span class="ctx-name">{{ ctx }}</span>
                            <el-icon class="remove-ctx" @click="removeContextVar(idx)"><Close /></el-icon>
                         </div>
                      </div>

                      <el-dropdown trigger="click" @command="addContextVar" style="width: 100%">
                        <div class="add-context-input">
                           <el-icon><Plus /></el-icon> 添加上下文变量...
                        </div>
                        <template #dropdown>
                          <el-dropdown-menu class="var-dropdown-menu">
                             <div class="dropdown-group-title">开始</div>
                             <el-dropdown-item command="query"><span class="var-option">{x} query</span></el-dropdown-item>
                             <el-dropdown-item command="files"><span class="var-option">{x} files</span></el-dropdown-item>
                             
                             <div class="dropdown-group-title">会话 (CONVERSATION)</div>
                             <el-dropdown-item command="memory"><span class="var-option"><el-icon><ChatDotSquare /></el-icon> memory</span></el-dropdown-item>
                             
                             <div class="dropdown-group-title">系统 (SYSTEM)</div>
                             <el-dropdown-item command="sys.dialogue_count"><span class="var-option">sys.dialogue_count</span></el-dropdown-item>
                             <el-dropdown-item command="sys.conversation_id"><span class="var-option">sys.conversation_id</span></el-dropdown-item>
                             <el-dropdown-item command="sys.user_id"><span class="var-option">sys.user_id</span></el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                   </div>
                </el-form-item>

                <el-form-item label="系统提示词 (SYSTEM PROMPT)">
                  <el-input 
                    type="textarea" 
                    :model-value="getSystemPrompt(selectedNode.data)"
                    @input="updateSystemPrompt"
                    :rows="6" 
                    placeholder="请输入模型指令..."
                  />
                  <!-- Variable inserter helper for Prompt -->
                  <div class="prompt-var-helper" style="margin-top: 8px; display: flex; gap: 8px;">
                     <el-tooltip content="插入变量" placement="top">
                        <el-tag size="small" type="info" class="cursor-pointer" @click="insertVarToPrompt('{x} query')">{x} query</el-tag>
                     </el-tooltip>
                     <el-tooltip content="插入变量" placement="top">
                        <el-tag size="small" type="info" class="cursor-pointer" @click="insertVarToPrompt('{{#memory#}}')">memory</el-tag>
                     </el-tooltip>
                  </div>
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

              <!-- Specific Code Form -->
              <template v-if="selectedNode.type === 'code'">
                <el-form-item label="代码语言">
                   <el-select v-model="selectedNode.data.code_language" style="width: 100%">
                     <el-option label="Python 3" value="python3" />
                     <el-option label="JavaScript" value="javascript" />
                   </el-select>
                </el-form-item>
                <el-form-item label="代码逻辑">
                   <el-input 
                     type="textarea" 
                     v-model="selectedNode.data.code" 
                     :rows="10" 
                     placeholder="def main(arg1): ..." 
                     style="font-family: monospace;"
                   />
                </el-form-item>
              </template>

              <!-- Specific Assigner Form -->
              <template v-if="selectedNode.type === 'variable_assigner'">
                 <el-form-item label="写入模式">
                   <el-radio-group v-model="selectedNode.data.write_mode" size="small">
                     <el-radio-button label="overwrite">覆盖</el-radio-button>
                     <el-radio-button label="append">追加</el-radio-button>
                   </el-radio-group>
                 </el-form-item>
                 <el-form-item label="目标变量">
                    <el-input v-model="selectedNode.data.target_variable" placeholder="例如: conversation.memory" />
                 </el-form-item>
              </template>

              <!-- Specific Note Form -->
              <template v-if="selectedNode.type === 'note'">
                 <el-form-item label="注释内容">
                   <el-input type="textarea" v-model="selectedNode.data.text" :rows="6" />
                 </el-form-item>
                 <el-form-item label="主题颜色">
                    <el-radio-group v-model="selectedNode.data.theme" size="small">
                       <el-radio-button label="blue">蓝</el-radio-button>
                       <el-radio-button label="yellow">黄</el-radio-button>
                    </el-radio-group>
                 </el-form-item>
              </template>

              <!-- Generic Input Settings -->
              <el-form-item label="输入变量" v-if="['start', 'llm', 'code', 'if_else', 'agent'].includes(selectedNode.type)">
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
    <div class="mini-map-container" v-show="activeTab === 'orchestrate' && !showPalette">
        <el-icon><MapLocation /></el-icon>
    </div>

    <!-- Preview/Run Dialog -->
    <el-dialog v-model="showPreviewDialog" title="运行预览" width="600px" append-to-body>
      <el-tabs v-model="activePreviewTab">
        <el-tab-pane label="输入" name="input">
          <el-form label-position="top">
             <div v-if="previewInputs.length === 0" class="no-inputs-hint">此工作流没有定义 Start 节点输入变量</div>
             <el-form-item v-for="input in previewInputs" :key="input.name" :label="input.label || input.name">
                <el-input v-model="input.value" :placeholder="`请输入 ${input.name}`" />
             </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="运行结果" name="result">
           <div v-if="executionLoading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 运行中...
           </div>
           <div v-else-if="executionResult" class="result-display">
              <div v-if="executionResult.error" class="result-status error"><el-icon><CircleClose /></el-icon> 运行失败</div>
              <div v-else-if="executionResult.status === 'succeeded' || executionResult.outputs" class="result-status success"><el-icon><CircleCheck /></el-icon> 运行成功</div>
              <div v-else class="result-status info"><el-icon><InfoFilled /></el-icon> 运行完成</div>
              
              <pre class="json-viewer">{{ JSON.stringify(executionResult, null, 2) }}</pre>
           </div>
           <div v-else class="empty-result">点击运行开始调试</div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="showPreviewDialog = false">关闭</el-button>
        <el-button type="primary" :loading="executionLoading" @click="runWorkflow">运行</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue';
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
import { createWorkflow, getWorkflow, executeWorkflow } from '@/api/workflow';
import { getModelList } from '@/api/model';
import WorkflowApiAccess from './WorkflowApiAccess.vue';
import { dump } from 'js-yaml';

// HandIcon placeholder for Pointer (can use Pointer icon instead)
const HandIcon = Pointer;

const router = useRouter();
const route = useRoute();

const workflowName = ref('');
const activeTab = ref('orchestrate');
const isEdit = ref(false);
const saving = ref(false);
const elements = ref([]);
const selectedNode = ref(null);
const showPalette = ref(true);
const interactionMode = ref('pointer'); // 'pointer' or 'hand'
const agentList = ref([]);
const availableModels = ref([]);
const lastSavedTime = ref('16:12:45');
let nodeIdCounter = Date.now();

// Preview/Run state
const showPreviewDialog = ref(false);
const activePreviewTab = ref('input');
const previewInputs = ref([]);
const executionResult = ref(null);
const executionLoading = ref(false);

const { fitView } = useVueFlow();

const onFitView = () => {
    fitView();
};

const onAddNote = () => {
    const newNode = {
        id: `node_note_${Date.now()}`,
        type: 'note',
        position: { x: 400, y: 300 },
        data: { 
            label: '新注释', 
            text: '在此输入备注...',
            theme: 'blue',
            showAuthor: true
        }
    };
    elements.value.push(newNode);
};

const onKeyDown = (event) => {
    if ((event.key === 'Backspace' || event.key === 'Delete') && selectedNode.value) {
        // Prevent deleting if typing in input
        if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
        deleteNode();
    }
};

const onGlobalKeyDown = async (event) => {
    // Handle Save Shortcut (Cmd+S or Ctrl+S)
    if ((event.metaKey || event.ctrlKey) && event.key === 's') {
        event.preventDefault();
        await handleSave();
        ElMessage.success('已保存');
    }
};

const onDeleteWorkflow = () => {
    ElMessageBox.confirm('确定要删除此工作流吗？此操作不可撤销。', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(() => {
        // Implement actual deletion logic here
        // API call to delete...
        ElMessage.success('工作流已删除');
        router.push('/dashboard/workflow');
    }).catch(() => {});
};

const getDifyWorkflowData = () => {
    const nodes = elements.value.filter(el => !el.source).map(n => {
        // Clean up React Flow internal fields
        const { 
            position, id, type, data, 
            width, height, measured, 
            selected, dragging, resizing, initialized, isParent,
            events, hover, zIndex, handleBounds
        } = n;

        const cleanData = { ...data };
        delete cleanData.selected;
        delete cleanData.dragging;
        
        const difyType = type === 'note' ? 'custom-note' : type;

        if (type === 'llm' && cleanData.model && typeof cleanData.model === 'string') {
             const modelName = cleanData.model;
             let provider = 'openai'; 
             if (modelName.includes('claude')) provider = 'anthropic';
             if (modelName.includes('grok')) provider = 'xai'; 
             
             cleanData.model = {
                 provider: provider,
                 name: modelName,
                 mode: 'chat',
                 completion_params: { temperature: 0.7 }
             };
        }

        return {
            id,
            position,
            type: difyType,
            width: measured?.width || width || 240,
            height: measured?.height || height || 60,
            data: {
               ...cleanData,
               title: cleanData.label || cleanData.title
            },
            positionAbsolute: position, 
            sourcePosition: 'right',
            targetPosition: 'left'
        };
    });

    const edges = elements.value.filter(el => el.source).map(e => ({
        id: e.id,
        source: e.source,
        sourceHandle: e.sourceHandle,
        target: e.target,
        targetHandle: e.targetHandle,
        type: e.type || 'custom',
        data: {
            sourceType: e.sourceHandle,
            targetType: e.targetHandle,
            isInerationStyle: false,
            ...e.data
        },
        zIndex: 0 
    }));

    return {
        kind: 'app',
        version: '0.1.0',
        app: {
            name: workflowName.value || '个性化记忆助手',
            icon: '🤖',
            mode: 'advanced-chat', 
            description: '',
            use_icon_as_answer_icon: false
        },
        workflow: {
            version: '0.1.0',
            features: {
                opening_statement: "",
                suggested_questions: [],
                speech_to_text: { enabled: false },
                text_to_speech: { enabled: false },
                file_upload: {
                    image: { enabled: false, number_limits: 3, transfer_methods: ["local_file", "remote_url"] }
                },
                retriever_resource: { enabled: true },
                sensitive_word_avoidance: { enabled: false }
            },
            graph: {
                viewport: { x: 0, y: 0, zoom: 1 },
                nodes,
                edges
            }
        }
    };
};

const onExportWorkflow = () => {
    try {
        const data = getDifyWorkflowData();
        
        // Convert to YAML
        const yamlStr = dump(data, {
            indent: 2,
            lineWidth: -1, 
            noRefs: true
        });

        const blob = new Blob([yamlStr], { type: 'application/x-yaml' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${workflowName.value || 'workflow'}-${new Date().getTime()}.yml`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        
        ElMessage.success('工作流已导出为 YAML');
    } catch (e) {
        console.error(e);
        ElMessage.error('导出失败');
    }
};

const nodeGroups = [
  {
    title: '基础节点 (Basic)',
    items: [
      { type: 'start', label: '开始 (Start)', icon: VideoPlay, color: '#eff6ff' },
      { type: 'end', label: '结束 (End)', icon: CircleCheck, color: '#eff6ff' },
      { type: 'llm', label: '大模型 (LLM)', icon: Cpu, color: '#f0fdf4' },
      { type: 'answer', label: '直接回复 (Answer)', icon: ChatDotSquare, color: '#eff6ff' },
      { type: 'agent', label: '代理 (Agent)', icon: User, color: '#f5f3ff' },
    ]
  },
  {
    title: '逻辑控制 (Logic)',
    items: [
      { type: 'code', label: '代码 (Code)', icon: Monitor, color: '#f2e6ff' },
      { type: 'if_else', label: '条件分支 (Condition)', icon: Share, color: '#f8fafc' },
      { type: 'iteration', label: '迭代 (Iteration)', icon: RefreshRight, color: '#f8fafc' },
      { type: 'loop', label: '循环 (Loop)', icon: Refresh, color: '#f8fafc' },
      { type: 'question_classifier', label: '意图识别 (Intent Recognition)', icon: Connection, color: '#fdf4ff' },
      { type: 'variable_aggregator', label: '变量聚合 (Variable Merit)', icon: Files, color: '#f2e6ff' },
    ]
  },
  {
    title: '数据处理 (Data Processing)',
    items: [
      { type: 'knowledge_retrieval', label: '知识检索 (Knowledge Retrieval)', icon: Collection, color: '#fffbeb' },
      { type: 'variable_assigner', label: '变量赋值 (Variable Assign)', icon: EditPen, color: '#f2e6ff' },
      { type: 'template_transform', label: '模板转换 (Template Transform)', icon: DocumentCopy, color: '#f2e6ff' },
      { type: 'parameter_extractor', label: '参数提取 (Parameter Extractor)', icon: Scissor, color: '#f2e6ff' },
      { type: 'document_extractor', label: '文档提取 (Document Extractor)', icon: Document, color: '#f2e6ff' },
    ]
  },
  {
    title: '工具 (Tools)',
    items: [
      { type: 'http_request', label: 'HTTP 请求', icon: Link, color: '#fdf6ec' },
      { type: 'tool', label: '插件工具 (Tool)', icon: Tools, color: '#fdf6ec' },
      { type: 'list_operator', label: '列表操作', icon: Operation, color: '#fdf6ec' },
    ]
  }
];

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeyDown);

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
  await fetchModelsList();
  
  // Timer for "last saved"
  setInterval(() => {
    const now = new Date();
    lastSavedTime.value = `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  }, 60000);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeyDown);
});


const fetchAgents = async () => {
  try {
    const res = await getAgentList();
    agentList.value = res || [];
  } catch (e) { console.error(e); }
};

const fetchModelsList = async () => {
    try {
        const res = await getModelList();
        // Assuming API returns array of model objects { id, name, ... } 
        // Need to adapt based on actual API response structure
        availableModels.value = res.map(m => ({
            label: m.modelName || m.name,
            value: m.modelName || m.name // Use name as value for Dify compatibility
        }));
    } catch (e) {
        console.error('Failed to fetch models', e);
        // Fallback or empty
    }
};

const loadWorkflow = async (id) => {
  try {
    const workflow = await getWorkflow(id);
    workflowName.value = workflow.workflowName;
    
    let rawNodes = [];
    let rawEdges = [];
    
    // Support both Dify nested structure and flat legacy structure
    if (workflow.workflowDefinition?.workflow?.graph) {
        rawNodes = workflow.workflowDefinition.workflow.graph.nodes || [];
        rawEdges = workflow.workflowDefinition.workflow.graph.edges || [];
    } else if (workflow.workflowDefinition?.nodes) {
        rawNodes = workflow.workflowDefinition.nodes || [];
        rawEdges = workflow.workflowDefinition.edges || [];
    }

    if (rawNodes.length > 0) {
        const nodes = rawNodes.map(n => {
            const data = n.data || {};
            
            // Normalize data from Dify YAML/JSON structure
            
            // 1. Map 'title' to 'label' if explicit label is missing
            if (data.title && !data.label) {
                data.label = data.title;
            }
            
            // 2. Map model object to simple model string
            // YAML: model: { name: 'gpt-4o', provider: 'openai' ... }
            if (data.model && typeof data.model === 'object' && data.model.name) {
                // Keep the full object for data but maybe add a helper prop for display if needed
                // data.model is object now, which is supported by our updated template
            } else if (data.model && typeof data.model === 'object') {
                 // handle partial object if necessary
            }
            
            return {
                ...n,
                type: n.type === 'custom-note' ? 'note' : n.type,
                data
            };
        });
        
        elements.value = [
          ...nodes,
          ...rawEdges
        ];
    }
  } catch (e) { 
      console.error(e);
      ElMessage.error('加载失败'); 
  }
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

const onConnect = (params) => {
    // Add new edge
    const newEdge = {
        id: `e-${params.source}-${params.target}-${Date.now()}`,
        source: params.source,
        target: params.target,
        sourceHandle: params.sourceHandle,
        targetHandle: params.targetHandle,
        type: 'custom', // or default
        data: { sourceType: params.sourceHandle, targetType: params.targetHandle }
    };
    elements.value.push(newEdge);
};

// Helper to serialize current workflow elements into Dify's nested DSL format
// (Implemented above as getDifyWorkflowData)

const handleSave = async (isAuto = false) => {
  if (!isAuto) saving.value = true;
  try {
    // Default name if empty
    if (!workflowName.value || workflowName.value.trim() === '') {
        workflowName.value = '未命名工作流';
    }

    const workflowData = getDifyWorkflowData();
    // Assuming createWorkflow can update if ID exists, or we use update endpoint
    const res = await createWorkflow({
        id: route.params.id, // Pass ID if updating
        workflowName: workflowName.value,
        workflowType: 'DAG', // Explicitly set type to ensure it opens in visual editor
        workflowDefinition: workflowData 
    });
    
    // Update last saved time
    const now = new Date();
    lastSavedTime.value = `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
    
    if (res && res.id && !route.params.id) {
        // New workflow created, navigate to it
        router.push(`/dashboard/workflow/visual/${res.id}`); // Fix redirect path
        // If it was manual save, show success
        if (!isAuto) ElMessage.success('保存成功');
    } else {
        if (!isAuto) ElMessage.success('保存成功');
    }
  } catch (e) {
    console.error(e);
    if (!isAuto) ElMessage.error('保存失败');
  } finally {
    if (!isAuto) saving.value = false;
  }
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

// Helper to parse Dify note text which is often stringified JSON
const getNoteText = (rawText) => {
    if (!rawText) return '此处填写注释...';
    if (typeof rawText !== 'string') return rawText;
    
    // Try to parse Dify's rich text JSON format
    // e.g. {"root":{"children":[{"children":[{"text":"..."}]}]}}
    if (rawText.trim().startsWith('{') && rawText.includes('"root"')) {
        try {
            const obj = JSON.parse(rawText);
            let extracted = '';
            
            const traverse = (node) => {
                if (node.text) {
                    extracted += node.text;
                }
                if (node.children && Array.isArray(node.children)) {
                    node.children.forEach(child => traverse(child));
                    // Add newline for paragraphs if needed
                    if (node.type === 'paragraph') extracted += '\n';
                }
            };
            
            if (obj.root) traverse(obj.root);
            return extracted.trim() || rawText;
        } catch (e) {
            // parsing failed, return raw
            return rawText; 
        }
    }
    return rawText;
};

const getSystemPrompt = (data) => {
    // Handle structured prompt_template from YAML
    if (Array.isArray(data.prompt_template)) {
        const sys = data.prompt_template.find(p => p.role === 'system');
        if (sys) return sys.text;
    }
    // Fallback to simple string
    return data.prompt || '';
};

const addContextVar = (val) => {
    if (!selectedNode.value) return;
    const data = selectedNode.value.data;
    if (!data.context) data.context = { enabled: true, variable_selector: [] };
    if (!data.context.variable_selector) data.context.variable_selector = [];
    
    // Avoid duplicates
    if (!data.context.variable_selector.includes(val)) {
        data.context.variable_selector.push(val);
        updateNode();
    }
};

const removeContextVar = (index) => {
    if (!selectedNode.value?.data?.context?.variable_selector) return;
    selectedNode.value.data.context.variable_selector.splice(index, 1);
    updateNode();
};

const insertVarToPrompt = (val) => {
   // Append to system prompt
   const current = getSystemPrompt(selectedNode.value.data);
   updateSystemPrompt(current + ' ' + val);
};

const updateSystemPrompt = (newVal) => {
    if (!selectedNode.value) return;
    const data = selectedNode.value.data;
    
    // Initialize prompt_template if missing
    if (!Array.isArray(data.prompt_template)) {
        data.prompt_template = [
            { id: Date.now().toString(), role: 'system', text: newVal }
        ];
        // Clean up legacy simple prompt if it existed to avoid confusion
        if (data.prompt) delete data.prompt; 
    } else {
        const sysIndex = data.prompt_template.findIndex(p => p.role === 'system');
        if (sysIndex >= 0) {
            data.prompt_template[sysIndex].text = newVal;
        } else {
            // Insert at beginning if System role missing
            data.prompt_template.unshift({ 
                id: Date.now().toString(), 
                role: 'system', 
                text: newVal 
            });
        }
    }
    // Trigger reactivity
    updateNode();
};

const handlePreview = () => {
    // 1. Identify Start node inputs
    const startNode = elements.value.find(el => el.type === 'start');
    previewInputs.value = [];
    
    // Check if start node has variables defined
    if (startNode && startNode.data && Array.isArray(startNode.data.variables) && startNode.data.variables.length > 0) {
        previewInputs.value = startNode.data.variables.map(v => ({
            name: v.variable,
            label: v.label || v.variable,
            value: '' // init empty
        }));
    } 
    
    // Always ensure at least one 'query' or 'sys.query' input if explicitly defined inputs are missing or empty
    // But usually Dify workflows rely on 'sys.query' which is implicitly passed or explicitly defined.
    // If the workflow is strictly LLM based without start vars, we might not need inputs?
    // Let's add 'query' by default if list is empty to be safe for chat apps.
    if (previewInputs.value.length === 0) {
        previewInputs.value.push({ name: 'query', label: 'Query / Sys Query', value: '' });
    }

    executionResult.value = null;
    activePreviewTab.value = 'input';
    showPreviewDialog.value = true;
};

const runWorkflow = async () => {
    activePreviewTab.value = 'result';
    executionLoading.value = true;
    executionResult.value = null;
    
    try {
        // Construct inputs map
        const inputs = {};
        previewInputs.value.forEach(p => {
           inputs[p.name] = p.value;
        });

        // 1. Auto-save first (optional but safer)
        if (route.params.id) {
             const workflowData = getDifyWorkflowData();
             await createWorkflow({
                 id: route.params.id, 
                 workflowName: workflowName.value,
                 workflowDefinition: workflowData
             });
        }

        // 2. Execute
        // Add response_mode and user for Dify compatibility
        const payload = {
            inputs,
            response_mode: 'blocking',
            user: 'user-preview' 
        };
        const res = await executeWorkflow(route.params.id, payload);
        executionResult.value = res;
    } catch (e) {
        executionResult.value = { error: '执行失败', details: e.message || e };
    } finally {
        executionLoading.value = false;
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

/* New Node Styles */
.note {
    border: none;
    padding: 16px;
    width: 280px;
    min-height: 160px;
}
.note-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 11px;
    color: #64748b;
}
.note-content {
    font-size: 13px;
    line-height: 1.6;
    color: #334155;
    white-space: pre-wrap;
}

.code .node-header { background: #fdf4ff; border-bottom-color: #f0abfc; }
.code .header-icon { color: #d946ef; }
.code-preview {
    font-family: 'JetBrains Mono', monospace;
    background: #f8fafc;
    padding: 8px;
    border-radius: 6px;
    font-size: 11px;
    color: #475569;
}
.lang-tag { margin-left: auto; }

.assigner .node-header { background: #fdf2f8; border-bottom-color: #fce7f3; }
.assigner .header-icon { color: #db2777; }
.assign-operation {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    background: #f8fafc;
    border-radius: 6px;
}
.op-mode { font-size: 11px; color: #64748b; font-weight: 600; }
.target-var { 
    font-family: monospace; 
    font-size: 11px; 
    color: #db2777; 
    background: #fce7f3; 
    padding: 2px 6px; 
    border-radius: 4px; 
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

/* Context Selector Styles */
.context-selector {
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 2px;
    background: #fff;
}

.context-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    padding: 4px;
}

.context-tag {
    display: flex;
    align-items: center;
    gap: 4px;
    background: #eff6ff;
    color: #155eef;
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    font-family: monospace;
}

.context-tag .remove-ctx {
    cursor: pointer;
    font-size: 10px;
    color: #93c5fd;
}
.context-tag .remove-ctx:hover { color: #155eef; }

.add-context-input {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #94a3b8;
    font-size: 12px;
    padding: 8px;
    cursor: pointer;
    transition: background 0.2s;
    border-radius: 4px;
}
.add-context-input:hover {
    background: #f8fafc;
    color: #64748b;
}

.dropdown-group-title {
    padding: 6px 12px;
    font-size: 11px;
    font-weight: 700;
    color: #94a3b8;
    background: #f8fafc;
}
.var-option {
    font-family: monospace;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 6px;
}
.cursor-pointer { cursor: pointer; }

/* Preview Dialog Styles */
.no-inputs-hint { color: #94a3b8; font-size: 13px; padding: 20px 0; text-align: center; }
.loading-state { display: flex; align-items: center; justify-content: center; gap: 8px; color: #155eef; padding: 40px; }
.result-display { background: #f8fafc; border-radius: 8px; padding: 12px; }
.result-status { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.result-status.success { color: #10b981; }
.result-status.error { color: #ef4444; }
.result-status.info { color: #3b82f6; }
.json-viewer { font-family: monospace; white-space: pre-wrap; font-size: 12px; color: #334155; margin: 0; }
.empty-result { color: #94a3b8; text-align: center; padding: 40px; font-size: 13px; }

</style>
