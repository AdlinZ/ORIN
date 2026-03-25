<template>
  <div class="visual-workflow-editor">
    <PageHeader
      title="可视化工作流编辑器"
      description="拖拽节点完成编排、调试与发布"
      icon="Share"
    />


    <!-- Sub Header (Workflow Logic Toolbar) -->
    <div class="dify-sub-header">
      <div class="sub-left">
        <div class="breadcrumb">
          <el-icon class="app-icon"><TrendCharts /></el-icon>
          <div v-if="isEditingName" class="name-edit-wrapper">
            <el-input 
              v-model="workflowName" 
              size="small" 
              ref="nameInput"
              @blur="isEditingName = false"
              @keyup.enter="isEditingName = false"
            />
          </div>
          <span v-else class="app-name" @click="startEditName">{{ workflowName || '未命名工作流' }}</span>
          <el-icon class="edit-icon" @click="startEditName"><Edit /></el-icon>
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
        
        <div class="dify-button-group-wrapper">
          <!-- Divider -->
          <div class="dify-divider"></div>

          <!-- Main Actions (Preview, etc) -->
          <div class="dify-action-bar">
            <!-- Preview Button -->
            <div class="dify-bar-item preview-btn" @click="handlePreview">
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"><path d="M8 18.3915V5.60846L18.2264 12L8 18.3915ZM6 3.80421V20.1957C6 20.9812 6.86395 21.46 7.53 21.0437L20.6432 12.848C21.2699 12.4563 21.2699 11.5436 20.6432 11.152L7.53 2.95621C6.86395 2.53993 6 3.01878 6 3.80421Z"></path></svg>
              <span>预览</span>
            </div>
            

          </div>



          <!-- Feature & Publish -->
          <button class="dify-features-btn" @click="showAIDialog = true">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="mr-1"><path d="M2.5 7C2.5 9.48528 4.51472 11.5 7 11.5C9.48528 11.5 11.5 9.48528 11.5 7C11.5 4.51472 9.48528 2.5 7 2.5C4.51472 2.5 2.5 4.51472 2.5 7ZM2.5 17C2.5 19.4853 4.51472 21.5 7 21.5C9.48528 21.5 11.5 19.4853 11.5 17C11.5 14.5147 9.48528 12.5 7 12.5C4.51472 12.5 2.5 14.5147 2.5 17ZM12.5 17C12.5 19.4853 14.5147 21.5 17 21.5C19.4853 21.5 21.5 19.4853 21.5 17C21.5 14.5147 19.4853 12.5 17 12.5C14.5147 12.5 12.5 14.5147 12.5 17ZM9.5 7C9.5 8.38071 8.38071 9.5 7 9.5C5.61929 9.5 4.5 8.38071 4.5 7C4.5 5.61929 5.61929 4.5 7 4.5C8.38071 4.5 9.5 5.61929 9.5 7ZM9.5 17C9.5 18.3807 8.38071 19.5 7 19.5C5.61929 19.5 4.5 18.3807 4.5 17C4.5 15.6193 5.61929 14.5 7 14.5C8.38071 14.5 9.5 15.6193 9.5 17ZM19.5 17C19.5 18.3807 18.3807 19.5 17 19.5C15.6193 19.5 14.5 18.3807 14.5 17C14.5 15.6193 15.6193 14.5 17 14.5C18.3807 14.5 19.5 15.6193 19.5 17ZM16 11V8H13V6H16V3H18V6H21V8H18V11H16Z"></path></svg>
            功能
          </button>
          
          <button class="dify-publish-btn" @click="handleSave()" :disabled="saving">
            发布
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="ml-1"><path d="M11.9999 13.1714L16.9497 8.22168L18.3639 9.63589L11.9999 15.9999L5.63599 9.63589L7.0502 8.22168L11.9999 13.1714Z"></path></svg>
          </button>


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
          <!-- 自定义边模板 - 带数据流动效果 -->
          <template #edge-default="{ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, selected }">
            <DataFlowEdge
              :id="id"
              :source-x="sourceX"
              :source-y="sourceY"
              :target-x="targetX"
              :target-y="targetY"
              :source-position="sourcePosition"
              :target-position="targetPosition"
              :data="data"
              :selected="selected"
              :animated="selected || data?.active"
            />
          </template>
          
          <!-- Custom Background (Dotted) -->
          <Background :pattern-color="isDark ? '#334155' : '#f0f0f0'" :gap="20" />
          
          <!-- Real MiniMap (Dynamic) -->
          <MiniMap 
            v-if="showMiniMap" 
            :node-color="n => getNodeColor(n.type)" 
            :mask-color="isDark ? 'rgba(15, 23, 42, 0.7)' : 'rgba(248, 250, 252, 0.7)'"
          />
          
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
                      :key="agent.agentId"
                      :label="agent.name"
                      :value="agent.agentId"
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

    <!-- Mini Map Toggle Button -->
    <div class="mini-map-container" v-show="activeTab === 'orchestrate'" @click="toggleMiniMap" :class="{ active: showMiniMap }">
        <el-icon><MapLocation /></el-icon>
    </div>
    


    <!-- Preview/Run Drawer (Chat UI) -->
    <el-drawer
      v-model="showPreviewDialog"
      title="预览"
      size="500px"
      append-to-body
      class="preview-drawer"
      :with-header="true"
    >
      <div class="chat-preview-container">
        <!-- Main Chat/Result Area -->
        <div class="chat-messages-area">
          <div v-if="executionLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在思考中...
          </div>
          
          <div v-else-if="executionResult" class="result-display">
            <!-- 最终结果 (Final Output) -->
            <div v-if="finalOutputs && (typeof finalOutputs === 'string' || Object.keys(finalOutputs).length > 0)" class="final-output-section">
              <div class="message-bubble bot" style="white-space: pre-wrap;">
                {{ finalOutputText || '执行完成 (无主要文本输出)' }}
              </div>
              <el-collapse class="parsed-raw-collapse" style="margin-bottom: 20px;">
                 <el-collapse-item title="原始输出 (Raw Data)" name="raw_outputs">
                    <JsonViewer :data="finalOutputs" :expand-all="true" :dark="isDark" />
                 </el-collapse-item>
              </el-collapse>
            </div>

            <el-collapse v-if="executionResult" class="trace-collapse">
               <el-collapse-item title="运行详情 (Trace)" name="trace">
                 <!-- Trace Timeline -->
                 <div class="trace-list" v-if="executionResult.trace">
                    <div v-for="item in executionResult.trace" :key="item.node_id" class="trace-item" :class="item.status">
                      <div class="trace-item-header">
                         <div class="node-info">
                            <span class="node-id-badge">{{ item.node_id }}</span>
                            <span class="node-type-label">{{ getNodeLabelById(item.node_id) }}</span>
                         </div>
                         <div class="node-status-badge">
                            <el-icon v-if="item.status === 'completed'"><CircleCheck /></el-icon>
                            <el-icon v-else-if="item.status === 'failed'"><CircleClose /></el-icon>
                            <el-icon v-else-if="item.status === 'skipped'"><Remove /></el-icon>
                            {{ formatStatus(item.status) }}
                         </div>
                      </div>
                      
                      <div class="trace-item-content" v-if="item.outputs || item.error">
                         <div v-if="getNodeTypeById(item.node_id) === 'code'" class="code-execution-section">
                            <div class="sub-section-title">执行代码</div>
                            <pre class="code-preview">{{ getCodePayload(item.node_id) }}</pre>
                         </div>
                         <div v-if="item.outputs" class="outputs-section">
                            <div class="sub-section-title">节点输出</div>
                            <JsonViewer :data="item.outputs" :dark="isDark" />
                         </div>
                         <div v-if="item.error" class="error-msg">{{ item.error }}</div>
                      </div>
                      
                      <div class="trace-item-footer">
                         <span class="duration">{{ (item.duration || 0).toFixed(3) }}s</span>
                         <span class="usage" v-if="item.outputs?.tokens_used">
                            {{ item.outputs.tokens_used }} tokens
                         </span>
                      </div>
                    </div>
                 </div>
                 <div class="raw-response-section" style="margin-top: 10px;">
                   <JsonViewer :data="lastExecutionDsl" title="Workflow DSL" :dark="isDark" />
                 </div>
               </el-collapse-item>
            </el-collapse>
          </div>

          <div v-else class="empty-chat-state">
            <el-icon size="48" color="#dcdfe6"><ChatDotSquare /></el-icon>
            <p>在下面的查询框中输入内容开始调试工作流</p>
          </div>
        </div>

        <!-- Chat Input Area (查询框) -->
        <div class="chat-input-area">
          <!-- Extra Inputs if there are other variables besides query -->
          <div class="extra-inputs" v-if="previewInputs.length > 1">
             <el-form label-position="left" inline size="small">
                <el-form-item v-for="input in previewInputs.filter(i => i.name !== 'query')" :key="input.name" :label="input.label || input.name">
                   <el-input v-model="input.value" :placeholder="`输入 ${input.name}`" />
                </el-form-item>
             </el-form>
          </div>
          
          <div class="query-box-wrapper">
             <el-input 
                v-model="(previewInputs.find(i => i.name === 'query') || {value: ''}).value" 
                type="textarea"
                :rows="2"
                resize="none"
                placeholder="查询框 (Type your query here...)" 
                class="query-input"
                @keydown.enter.prevent="runWorkflow"
                :disabled="executionLoading"
             />
             <el-button 
                type="primary" 
                :icon="Promotion" 
                class="send-btn" 
                circle 
                :loading="executionLoading" 
                @click="runWorkflow"
             ></el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- AI Generation Dialog -->
    <el-dialog v-model="showAIDialog" title="AI 辅助生成工作流" width="500px">
      <el-form label-position="top">
        <el-form-item label="描述您想要的工作流需求">
          <el-input 
            type="textarea" 
            v-model="aiPrompt" 
            :rows="4" 
            placeholder="例如：创建一个翻译流程，接收中文输入，使用 LLM 翻译成英文，然后直接回复。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAIDialog = false">取消</el-button>
        <el-button type="primary" :loading="aiGenerating" @click="onGenerateAIWorkflow">
          开始生成
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { VueFlow, Handle, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { MiniMap } from '@vue-flow/minimap';
import { Controls } from '@vue-flow/controls';
import { MarkerType } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/minimap/dist/style.css';

// 导入自定义边组件
import DataFlowEdge from './components/DataFlowEdge.vue';
import { 
  VideoPlay, User, Tools, Share, CircleCheck, Search, Grid, Collection, 
  CaretBottom, Connection, TrendCharts, Edit, Operation, VideoPause,
  RefreshRight, MoreFilled, Loading, Plus, Close, Pointer, FullScreen, Aim,
  Right, MapLocation, Delete, Clock, Cpu, ChatDotSquare, Monitor, DocumentCopy,
  Files, EditPen, Link, Document, Scissor, Postcard, Sunny, Refresh,
  CirclePlusFilled, DocumentAdd, MagicStick, Promotion, Remove, CircleClose, InfoFilled
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAgentList } from '@/api/agent';
import { createWorkflow, getWorkflow, executeWorkflow, runWorkflowPreview, generateAIWorkflow } from '@/api/workflow';
import { getModelList } from '@/api/model';
import WorkflowApiAccess from './WorkflowApiAccess.vue';
import JsonViewer from '@/components/JsonViewer.vue';
import PageHeader from '@/components/PageHeader.vue';
import { dump } from 'js-yaml';
import { useDark } from '@vueuse/core';

// Dark mode support
const isDark = useDark();

// HandIcon placeholder for Pointer (can use Pointer icon instead)
const HandIcon = Pointer;

const router = useRouter();
const route = useRoute();

const workflowName = ref('');
const isEditingName = ref(false);
const nameInput = ref(null);
const activeTab = ref('orchestrate');
const isEdit = ref(false);
const saving = ref(false);
const elements = ref([]);
const selectedNode = ref(null);
const showPalette = ref(false); // 默认隐藏节点库
const showMiniMap = ref(false);
const interactionMode = ref('pointer'); // 'pointer' or 'hand'
const agentList = ref([]);
const availableModels = ref([]);
const lastSavedTime = ref('未保存');
let nodeIdCounter = Date.now();

// Preview/Run state
const showPreviewDialog = ref(false);
const activePreviewTab = ref('input');
const previewInputs = ref([]);
const executionResult = ref(null);
const lastExecutionDsl = ref(null);
const executionLoading = ref(false);

const finalOutputs = computed(() => {
    if (!executionResult.value) return null;
    return executionResult.value.outputs || (executionResult.value.data && executionResult.value.data.outputs) || executionResult.value;
});

const finalOutputText = computed(() => {
    if (!finalOutputs.value) return '';
    if (typeof finalOutputs.value === 'string') return finalOutputs.value;
    
    // Direct matches
    if (finalOutputs.value.text) return finalOutputs.value.text;
    if (finalOutputs.value.output) return finalOutputs.value.output;
    if (finalOutputs.value.result) return finalOutputs.value.result;
    
    // If it's an object with node IDs as keys, try to find the text in the values (usually the LLM or Answer node)
    const values = Object.values(finalOutputs.value);
    for (const val of values.reverse()) { // Reverse to prefer later nodes
        if (typeof val === 'object' && val !== null) {
            if (val.text) return val.text;
            if (val.output) return val.output;
            if (val.result) return val.result;
        }
    }
    
    return JSON.stringify(finalOutputs.value, null, 2);
});

// AI Generation state
const showAIDialog = ref(false);
const aiGenerating = ref(false);
const aiPrompt = ref('');

const { fitView } = useVueFlow();

const onFitView = () => {
    fitView();
};

const startEditName = () => {
    isEditingName.value = true;
    nextTick(() => {
        nameInput.value?.focus();
    });
};

const toggleMiniMap = () => {
    showMiniMap.value = !showMiniMap.value;
    ElMessage.info(showMiniMap.value ? '小地图已开启' : '小地图已关闭');
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

const onGenerateAIWorkflow = async () => {
    if (!aiPrompt.value) {
        ElMessage.warning('请输入您的需求描述');
        return;
    }
    aiGenerating.value = true;
    try {
        const res = await generateAIWorkflow(aiPrompt.value);
        if (res && res.nodes) {
            // Replace or Merge? Let's Replace for now for simplicity, or we could merge and layout.
            // But usually AI generation is for a fresh start.
            elements.value = res.nodes.map(n => ({
                ...n,
                // Ensure nodes have standard types as expected by our templates
                // and data property is present
                data: n.data || {}
            }));
            
            if (res.edges) {
                elements.value.push(...res.edges);
            }
            
            showAIDialog.value = false;
            ElMessage.success('工作流已生成');
            nextTick(() => {
                onFitView();
            });
        }
    } catch (e) {
        console.error(e);
        ElMessage.error('AI 生成失败: ' + (e.response?.data?.error || e.message));
    } finally {
        aiGenerating.value = false;
    }
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
    
    // Handle Pointer Mode (V key)
    if (event.key === 'v' || event.key === 'V') {
        // Don't trigger if typing in input/textarea
        if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
        event.preventDefault();
        interactionMode.value = 'pointer';
        ElMessage.info('切换到指针模式');
    }
    
    // Handle Hand Mode (H key)
    if (event.key === 'h' || event.key === 'H') {
        // Don't trigger if typing in input/textarea
        if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
        event.preventDefault();
        interactionMode.value = 'hand';
        ElMessage.info('切换到手模式');
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
        router.push(ROUTES.AGENTS.WORKFLOWS);
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
        type: e.type || 'smoothstep',
        animated: e.animated !== undefined ? e.animated : true,
        style: e.style || { stroke: '#94a3b8', strokeWidth: 2 },
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
  
  // 真正的自动保存 - 每2分钟保存一次
  setInterval(async () => {
    if (route.params.id && elements.value.length > 0) {
      try {
        await handleSave(true); // true 表示自动保存，不显示成功提示
        console.log('工作流已自动保存');
      } catch (e) {
        console.error('自动保存失败:', e);
      }
    }
  }, 120000); // 2分钟 = 120000ms
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
    if (!workflow) {
      ElMessage.warning('工作流不存在');
      return;
    }
    
    workflowName.value = workflow.workflowName || '未命名工作流';
    
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

    if (rawNodes && rawNodes.length > 0) {
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
        
        const mappedEdges = (rawEdges || []).map(e => ({
            ...e,
            type: e.type || 'smoothstep',
            animated: e.animated !== undefined ? e.animated : true,
            style: e.style || { stroke: '#94a3b8', strokeWidth: 2 }
        }));
        
        elements.value = [
          ...nodes,
          ...mappedEdges
        ];
    } else {
      // Empty workflow - add default start/end nodes
      elements.value = [
        { id: 'start_1', type: 'start', position: { x: 100, y: 300 }, data: { id: 'start_1' } },
        { id: 'end_1', type: 'end', position: { x: 1000, y: 300 }, data: { id: 'end_1' } }
      ];
    }
    
    // 加载成功后更新保存时间
    const now = new Date();
    lastSavedTime.value = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  } catch (e) { 
      console.error('加载工作流失败:', e);
      ElMessage.error('加载失败: ' + (e.message || '未知错误'));
      // Set default empty workflow on error
      elements.value = [
        { id: 'start_1', type: 'start', position: { x: 100, y: 300 }, data: { id: 'start_1' } },
        { id: 'end_1', type: 'end', position: { x: 1000, y: 300 }, data: { id: 'end_1' } }
      ];
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
        type: 'smoothstep',
        animated: true,
        style: { stroke: '#94a3b8', strokeWidth: 2 },
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
    lastSavedTime.value = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
    
    if (res && res.id && !route.params.id) {
        // New workflow created, navigate to it
        router.push(`${ROUTES.AGENTS.WORKFLOWS}/${res.id}`);
        if (!isAuto) ElMessage.success('保存成功');
    } else {
        if (!isAuto) ElMessage.success('保存成功');
    }
  } catch (e) {
    console.error('保存失败:', e);
    if (!isAuto) ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message || '未知错误'));
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
    const agent = agentList.value.find(a => a.agentId === val);
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


const getNodeLabelById = (id) => {
   const node = elements.value.find(el => el.id === id);
   if (!node) return id;
   return node.data?.label || node.data?.title || node.type || id;
};

const getNodeTypeById = (id) => {
    const node = elements.value.find(el => el.id === id);
    return node?.type || '';
};

const getCodePayload = (id) => {
    const node = elements.value.find(el => el.id === id);
    return node?.data?.code || '# No code provided';
};

const formatStatus = (status) => {
   const MAP = {
      'completed': '完成',
      'failed': '失败',
      'skipped': '跳过',
      'running': '进行中'
   };
   return MAP[status] || status;
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

        // 2. Construct DSL for Execution
        // We need to transform VueFlow elements to clean WorkflowDSL
        const nodes = elements.value.filter(el => !el.source).map(n => {
            return {
                id: n.id,
                type: n.type === 'custom-note' ? 'note' : n.type,
                position: n.position,
                data: n.data // Pass full data, backend ignores extra fields partially but clean schema is better
            };
        });
        
        const edges = elements.value.filter(el => el.source).map(e => ({
            id: e.id,
            source: e.source,
            target: e.target,
            sourceHandle: e.sourceHandle,
            targetHandle: e.targetHandle
        }));
        
        const payload = {
            dsl: {
                nodes,
                edges
            },
            context: {
                inputs
            }
        };

        lastExecutionDsl.value = payload.dsl;
        const res = await runWorkflowPreview(payload);
        executionResult.value = res;
        
        if (res.status === 'error') {
             ElMessage.error(res.error || '执行出错');
        } else if (res.status === 'partial') {
             ElMessage.warning('部分执行成功');
        } else {
             ElMessage.success('执行成功');
        }
    } catch (e) {
        console.error("Execution Error:", e);
        executionResult.value = { error: '执行失败', details: e.message || e };
        ElMessage.error('执行失败');
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
  height: 56px;
  min-height: 56px;
  background: #f7f8fa; /* Matching .visual-workflow-editor background */
  border-bottom: none; /* Seamless blend */
  padding: 0 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 100;
}

.sub-left, .sub-right {
  display: flex;
  align-items: center;
  height: 100%;
}

.sub-right {
  gap: 8px;
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

.status-dot { width: 6px; height: 6px; background: #12b76a; border-radius: 50%; }

/* --- Dify Button Groups --- */
.dify-button-group-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dify-divider {
  width: 1px;
  height: 14px;
  background: #f2f4f7;
  margin: 0 4px;
}

.dify-action-bar {
  display: flex;
  height: 32px;
  align-items: center;
  border: 0.5px solid #d0d5dd;
  background: #ffffff; /* Keep buttons white for contrast against gray header */
  border-radius: 8px;
  padding: 0 2px;
  box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
}

.dify-bar-item {
  display: flex;
  height: 28px;
  align-items: center;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: #344054;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.2s;
}

.dify-bar-item:hover {
  background: #f9fafb;
}

.dify-bar-item svg {
  margin-right: 4px;
  width: 16px;
  height: 16px;
}

.dify-bar-divider {
  width: 1px;
  height: 14px;
  background: #f2f4f7;
  margin: 0 2px;
}

.dify-bar-icon-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 6px;
  color: #667085;
  transition: all 0.2s;
}

.dify-bar-icon-btn:hover {
  background: #f9fafb;
  color: #344054;
}

.dify-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  height: 18px;
  min-width: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f79009;
  border: 1px solid #ffffff;
  border-radius: 50%;
  color: white;
  font-size: 11px;
  font-weight: 600;
}

.dify-toolset {
  display: flex;
  background: #ffffff;
  border: 0.5px solid #d0d5dd;
  border-radius: 8px;
  padding: 0 2px;
  box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
}

.dify-tool-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667085;
  cursor: pointer;
  border-radius: 6px;
  padding: 0;
  transition: all 0.2s;
}

.dify-tool-btn:hover {
  background: #f9fafb;
  color: #344054;
}

.dify-features-btn {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  background: #ffffff;
  border: 0.5px solid #d0d5dd;
  border-radius: 8px;
  color: #344054;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
}

.dify-features-btn:hover {
  background: #f9fafb;
}

.dify-publish-btn {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 12px 0 14px;
  background: #155eef;
  border: none;
  border-radius: 8px;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
}

.dify-publish-btn:hover {
  background: #1546cb;
}

.dify-publish-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.dify-history-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border: 0.5px solid #d0d5dd;
  border-radius: 8px;
  color: #667085;
  cursor: pointer;
  box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
}

.dify-history-btn:hover {
  background: #f9fafb;
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
  top: 50%;
  transform: translateY(-50%);
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
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  width: 250px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: auto;
}

.dify-node:hover {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.08);
}

.dify-node.selected { 
  border-color: #155eef; 
  box-shadow: 0 0 0 4px rgba(21, 94, 239, 0.1), 0 10px 15px -3px rgba(0, 0, 0, 0.1); 
}

.node-header {
  padding: 12px 14px 8px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: none;
  border-top-left-radius: 12px;
  border-top-right-radius: 12px;
  background: transparent !important;
}

/* Colors for icon wrappers (in Dify, the icon has the background color, not the header) */
.header-icon { 
  font-size: 16px; 
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: white !important; /* Icons inside are white */
}

/* Icon specific background colors matching Dify */
.start .header-icon { background: #3b82f6; } /* Blue */
.end .header-icon { background: #ef4444; }   /* Red */
.llm .header-icon { background: #8b5cf6; }   /* Purple/Indigo */
.agent .header-icon { background: #6366f1; } /* Indigo */
.logic .header-icon { background: #0ea5e9; } /* Cyan */
.knowledge .header-icon { background: #10b981; } /* Emerald */
.assigner .header-icon { background: #ec4899; } /* Pink */
.answer .header-icon { background: #f97316; } /* Orange */
.code .header-icon { background: #d946ef; } /* Fuchsia */

.node-header .title { font-size: 14px; font-weight: 700; flex: 1; color: #1e293b; }
.more-icon { color: #94a3b8; cursor: pointer; }

.node-body { padding: 4px 14px 16px 14px; }
.body-text { font-size: 12px; color: #64748b; line-height: 1.5; background: #f8fafc; padding: 8px 10px; border-radius: 6px; }

.model-badge {
    display: inline-flex;
    align-items: center;
    background: #f1f5f9;
    color: #475569;
    padding: 4px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    margin-bottom: 10px;
    border: 1px solid #e2e8f0;
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
  width: 12px;
  height: 12px;
  background: #fff;
  border: 2px solid #cbd5e1;
  transition: all 0.2s;
  border-radius: 50%;
}

.vue-flow__handle:hover { 
  border-color: #3b82f6; 
  background: #eff6ff;
  transform: scale(1.1);
}

.vue-flow__handle-right {
  right: -6px;
}
.vue-flow__handle-left {
  left: -6px;
}

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
    transition: all 0.2s;
    z-index: 90;
}

.mini-map-container:hover {
    background: #f8fafc;
    box-shadow: 0 6px 8px -1px rgba(0,0,0,0.15);
}

.mini-map-container.active {
    background: #155eef;
    color: white;
}

.mini-map-panel {
    position: absolute;
    right: 20px;
    bottom: 70px;
    width: 220px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
    z-index: 90;
    overflow: hidden;
}

.mini-map-header {
    padding: 12px 16px;
    border-bottom: 1px solid #f3f4f6;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    font-weight: 600;
}

.mini-map-content {
    padding: 12px 16px;
}

.mini-map-info {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.info-item {
    display: flex;
    justify-content: space-between;
    padding: 8px 12px;
    background: #f8fafc;
    border-radius: 6px;
    font-size: 12px;
}

.info-item .label {
    color: #64748b;
}

.info-item .value {
    font-weight: 600;
    color: #155eef;
}

/* --- Real MiniMap Styling --- */
.vue-flow__minimap {
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  right: 20px;
  bottom: 70px;
  width: 200px;
  height: 150px;
}

.dark .vue-flow__minimap {
  background-color: #1e293b;
  border-color: #334155;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.4);
}

.vue-flow__minimap-mask {
  fill: rgba(0, 0, 0, 0.05);
}

.dark .vue-flow__minimap-mask {
  fill: rgba(255, 255, 255, 0.05);
}

.vue-flow__minimap-node {
  rx: 4;
  ry: 4;
}

/* Animations */
.slide-fade-enter-active, .slide-fade-leave-active { transition: all 0.3s ease; }
.slide-fade-enter-from, .slide-fade-leave-to { opacity: 0; transform: translateX(-20px); }

.slide-left-enter-active, .slide-left-leave-active { transition: all 0.3s cubic-bezier(0.165, 0.84, 0.44, 1); }
.slide-left-enter-from, .slide-left-leave-to { transform: translateX(100%); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

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
.result-display { background: #f8fafc; border-radius: 12px; padding: 16px; border: 1px solid #e5e7eb; }
.result-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; margin-bottom: 16px; }
.result-status { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 700; }
.result-status.success { color: #059669; }
.result-status.error { color: #dc2626; }
.result-status.info { color: #2563eb; }
.result-meta { font-size: 12px; color: #64748b; }

.trace-list { display: flex; flex-direction: column; gap: 12px; max-height: 400px; overflow-y: auto; margin-bottom: 16px; padding-right: 4px; }
.trace-item { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; transition: all 0.2s; }
.trace-item:hover { border-color: #cbd5e1; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }
.trace-item.failed { border-left: 4px solid #ef4444; }
.trace-item.completed { border-left: 4px solid #10b981; }
.trace-item.skipped { border-left: 4px solid #94a3b8; opacity: 0.8; }

.trace-item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.node-info { display: flex; align-items: center; gap: 8px; }
.node-id-badge { font-family: monospace; font-size: 10px; background: #f1f5f9; color: #475569; padding: 2px 6px; border-radius: 4px; }
.node-type-label { font-size: 13px; font-weight: 600; color: #1e293b; }

.node-status-badge { display: flex; align-items: center; gap: 4px; font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
.completed .node-status-badge { background: #ecfdf5; color: #059669; }
.failed .node-status-badge { background: #fef2f2; color: #dc2626; }
.skipped .node-status-badge { background: #f8fafc; color: #64748b; }

.trace-item-content { background: #f8fafc; border-radius: 6px; padding: 10px; margin-bottom: 8px; font-size: 12px; }
.output-row { display: flex; gap: 8px; margin-bottom: 4px; }
.output-key { color: #64748b; font-weight: 600; min-width: 60px; }
.output-val { color: #334155; word-break: break-all; white-space: pre-wrap; flex: 1; }
.error-msg { color: #dc2626; font-family: monospace; font-size: 11px; }

.trace-item-footer { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #94a3b8; }
.duration { font-family: monospace; }
.usage { background: #f1f5f9; padding: 1px 6px; border-radius: 4px; }

.json-viewer { font-family: 'JetBrains Mono', monospace; white-space: pre-wrap; font-size: 11px; color: #475569; background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 8px; }

.empty-result { color: #94a3b8; text-align: center; padding: 40px; font-size: 13px; }

/* --- Dark Mode Styles --- */
.dark .visual-workflow-editor {
  background-color: #0f172a;
  color: #e2e8f0;
}

.dark .dify-sub-header {
  background: #1e293b;
  border-bottom-color: #334155;
}

.dark .breadcrumb .app-icon {
  background: #451a03;
  color: #fbbf24;
}

.dark .app-name {
  color: #f1f5f9;
}

.dark .sub-nav-item {
  color: #94a3b8;
}

.dark .sub-nav-item:hover {
  color: #f1f5f9;
}

.dark .sub-nav-item.active {
  color: #3b82f6;
}

.dark .sub-nav-item.active::after {
  background: #3b82f6;
}

.dark .tool-rail {
  background: #1e293b;
  border-color: #334155;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
}

.dark .tool-item:hover {
  background: #334155;
  color: #f1f5f9;
}

.dark .tool-item.active {
  background: #1e293b;
  color: #3b82f6;
}

.dark .node-palette {
  background: #1e293b;
  border-color: #334155;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.4);
}

.dark .palette-header {
  border-bottom-color: #334155;
}

.dark .palette-header h3 {
  color: #f1f5f9;
}

.dark .palette-node-card {
  background: #334155;
  border-color: #475569;
  color: #f1f5f9;
}

.dark .palette-node-card:hover {
  border-color: #3b82f6;
  background: #1e293b;
}

.dark .group-title {
  color: #64748b;
}

.dark .dify-node {
  background: rgba(30, 41, 59, 0.85);
  border-color: rgba(51, 65, 85, 0.8);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.4);
}

.dark .dify-node:hover {
  box-shadow: 0 12px 20px -5px rgba(0, 0, 0, 0.6);
}

.dark .dify-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.15), 0 10px 15px -3px rgba(0, 0, 0, 0.4);
}

.dark .node-body .body-text {
  color: #94a3b8;
}

.dark .start .node-header { background: #064e3b; border-bottom-color: #065f46; color: #10b981; }
.dark .end .node-header { background: #7f1d1d; border-bottom-color: #991b1b; color: #f87171; }
.dark .llm .node-header { background: #064e3b; border-bottom-color: #065f46; color: #34d399; }
.dark .agent .node-header { background: #4c1d95; border-bottom-color: #5b21b6; color: #c084fc; }
.dark .logic .node-header { background: #334155; border-bottom-color: #475569; color: #94a3b8; }

.dark .node-header {
  border-bottom-color: #334155;
}

.dark .node-header .title {
  color: #f1f5f9;
}

.dark .properties-panel {
  background: #1e293b;
  border-left-color: #334155;
}

.dark .panel-header {
  border-bottom-color: #334155;
  color: #f1f5f9;
}

.dark .panel-header h3 {
  color: #f1f5f9;
}

.dark .var-item {
  background: #334155;
  color: #f1f5f9;
}

.dark .var-name {
  color: #60a5fa;
}

.dark .var-type {
  color: #64748b;
}

.dark .panel-footer {
  border-top-color: #334155;
}

.dark .mini-map-container {
  background: #1e293b;
  border-color: #334155;
  color: #f1f5f9;
}

.dark .mini-map-container:hover {
  background: #334155;
}

.dark .info-item {
  background: #334155;
  border-color: #475569;
}

.dark .info-item .label {
  color: #94a3b8;
}

.dark .info-item .value {
  color: #60a5fa;
}

.dark .context-selector {
  background: #334155;
  border-color: #475569;
}

.dark .context-tag {
  background: #1e3a8a;
  color: #93c5fd;
}

.dark .add-context-input:hover {
  background: #1e293b;
}

.dark .code-preview {
  background: #0f172a;
  color: #cbd5e1;
}

.dark .assign-operation {
  background: #334155;
}

.dark .vue-flow__handle {
  background: #1e293b;
  border-color: #475569;
}

.dark .result-display {
  background: #1e293b;
  border-color: #334155;
}

.dark .trace-item {
  background: #334155;
  border-color: #475569;
}

.dark .trace-item-header .node-type-label {
  color: #f1f5f9;
}

.dark .trace-item-content {
  background: #1e293b;
}

.dark .output-val {
  color: #e2e8f0;
}

.final-output-section {
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
  border: 1px solid #e2e8f0;
}

.dark .final-output-section {
  background: #1e293b;
  border-color: #334155;
}

.section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e293b;
}

.dark .section-title {
  color: #f1f5f9;
}

.sub-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin: 12px 0 6px 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.code-execution-section {
  margin-bottom: 16px;
}

.code-preview {
  background: #f1f5f9;
  padding: 12px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  overflow-x: auto;
  color: #334155;
  border: 1px solid #e2e8f0;
}

.dark .code-preview {
  background: #0f172a;
  border-color: #334155;
}

/* Chat Preview Styles */
.chat-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background-color: #f8fafc;
}

.dark .chat-messages-area {
  background-color: #0f172a;
}

.empty-chat-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;
  text-align: center;
}

.empty-chat-state p {
  margin-top: 16px;
  font-size: 14px;
}

.message-bubble.bot {
  background-color: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.5;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.dark .message-bubble.bot {
  background-color: #1e293b;
  border-color: #334155;
  color: #e2e8f0;
}

.trace-collapse {
  border-top: none;
}

.chat-input-area {
  padding: 16px;
  background: white;
  border-top: 1px solid #e2e8f0;
}

.dark .chat-input-area {
  background: #1e293b;
  border-top-color: #334155;
}

.query-box-wrapper {
  position: relative;
  display: flex;
  align-items: flex-end;
}

.query-input :deep(.el-textarea__inner) {
  padding-right: 50px;
  border-radius: 8px;
  background-color: #f1f5f9;
  border: 1px solid transparent;
  box-shadow: none;
}

.query-input :deep(.el-textarea__inner:focus) {
  border-color: #409eff;
  background-color: white;
}

.dark .query-input :deep(.el-textarea__inner) {
  background-color: #334155;
  color: #e2e8f0;
}

.dark .query-input :deep(.el-textarea__inner:focus) {
  background-color: #1e293b;
}

.send-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
}

.extra-inputs {
  margin-bottom: 12px;
}

.dark .node-id-badge {
  background: #1e293b;
  color: #94a3b8;
}

.dark .el-dialog,
.dark .el-drawer {
  background-color: #1e293b !important;
}

.dark .el-tabs__nav {
  color: #94a3b8;
}

.dark .el-form-item__label {
  color: #e2e8f0 !important;
}

.dark .json-viewer {
  background: #0f172a;
}

.parsed-raw-collapse {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  background: white;
}

.dark .parsed-raw-collapse {
  border-color: #334155;
  background: #1e293b;
}

.parsed-raw-collapse :deep(.el-collapse-item__header) {
  padding: 0 16px;
  background: #f8fafc;
  font-size: 13px;
  color: #64748b;
}

.dark .parsed-raw-collapse :deep(.el-collapse-item__header) {
  background: #0f172a;
  color: #94a3b8;
}

.parsed-raw-collapse :deep(.el-collapse-item__wrap) {
  border-top: 1px solid #e2e8f0;
}

.dark .parsed-raw-collapse :deep(.el-collapse-item__wrap) {
  border-top-color: #334155;
}

.parsed-raw-collapse :deep(.el-collapse-item__content) {
  padding: 12px;
}
</style>
