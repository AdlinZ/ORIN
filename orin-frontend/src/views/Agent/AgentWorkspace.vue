<template>
  <div class="agent-workspace" ref="containerRef" :class="{ 'is-wide': isWide, 'is-medium': isMedium, 'is-narrow': isNarrow }">
    <div v-if="isLeftDrawer && !sessionPaneCollapsed" class="d-overlay" @click="sessionPaneCollapsed = true"></div>
    <aside class="workspace-sidebar" :class="{ 'is-drawer': isLeftDrawer, 'is-collapsed': sessionPaneCollapsed }">
      <div v-if="sessionPaneCollapsed" class="collapsed-restore-panel">
        <el-tooltip content="展开会话记录" placement="right">
          <button
            type="button"
            class="collapsed-rail-handle"
            aria-label="展开会话记录"
            title="展开会话记录"
            @click="restoreSessionPane"
          >
            <el-icon class="collapsed-handle-icon">
              <ChatRound />
            </el-icon>
            <span v-if="sessions.length" class="collapsed-session-dot">
              {{ sessions.length > 9 ? '9+' : sessions.length }}
            </span>
            <el-icon class="collapsed-handle-arrow">
              <ArrowRight />
            </el-icon>
          </button>
        </el-tooltip>
      </div>
      <div v-if="!sessionPaneCollapsed" class="sidebar-tabs">
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'session' }" @click="sidebarTab = 'session'">会话记录</div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'collaboration' }" @click="sidebarTab = 'collaboration'">
          协作
          <span v-if="collaborationRuns.length" class="sidebar-tab-badge">{{ collaborationRuns.length }}</span>
        </div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'config' }" @click="sidebarTab = 'config'">工作台设置</div>
      </div>
      <div v-if="!sessionPaneCollapsed && sidebarTab === 'config'" class="sidebar-agent-switch">
        <span class="sidebar-agent-switch-label">当前智能体</span>
        <el-select
          v-model="currentAgentId"
          class="sidebar-agent-select"
          placeholder="请选择智能体"
          filterable
          :loading="agentsLoading"
          :disabled="props.lockAgent || agentsLoading || !agents.length"
          @change="onAgentSelectionChange"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agent.name"
            :value="agent.id"
          />
        </el-select>
      </div>
      <div v-show="sidebarTab === 'session'" class="workspace-session-pane">
        <template v-if="!sessionPaneCollapsed">
          <div class="session-actions">
            <el-button
              class="new-session-btn"
              type="primary"
              :icon="Plus"
              :disabled="!currentAgentId"
              @click="newSession"
            >
              新建对话
            </el-button>
          </div>

          <div class="session-list">
            <div
              v-for="session in sessions"
              :key="session.id"
              :class="['session-item', { active: currentSessionId === session.id }]"
              @click="selectSession(session)"
            >
              <div class="session-main">
                <div class="session-title">
                  {{ session.title || '未命名会话' }}
                </div>
                <div class="session-meta">
                  {{ formatSessionTime(session.createdAt) }}
                </div>
              </div>
              <el-button
                link
                class="session-delete"
                :icon="Delete"
                @click.stop="removeSession(session)"
              />
            </div>

            <el-empty v-if="currentAgentId && sessions.length === 0" :image-size="56" description="暂无会话" />
            <el-empty v-else-if="!currentAgentId" :image-size="56" description="请先选择智能体" />
          </div>

        </template>
      </div>
      <div v-show="sidebarTab === 'collaboration' && !sessionPaneCollapsed" class="workspace-collaboration-pane">
        <div class="collaboration-sidebar-head">
          <div>
            <h3>协作运行</h3>
            <p>当前会话的多智能体图谱与执行轨迹。</p>
          </div>
          <span>{{ collaborationRuns.length }}</span>
        </div>
        <div v-if="collaborationRuns.length" class="collaboration-run-list">
          <div
            v-for="run in collaborationRuns"
            :key="run.key"
            class="collaboration-run-item"
            :class="{ active: selectedCollaborationMeta === run.meta }"
          >
            <div class="collaboration-run-top">
              <strong>{{ run.title }}</strong>
              <em :class="`status-${run.meta.status || 'completed'}`">{{ formatCollaborationStatus(run.meta) }}</em>
            </div>
            <div class="collaboration-run-time">{{ formatMessageTime(run.createdAt) }}</div>
            <div class="collaboration-run-participants">
              <span
                v-for="participant in run.meta.participants || []"
                :key="participant.id || participant.name"
              >
                @{{ participant.name }}
              </span>
            </div>
            <div class="collaboration-run-actions">
              <button type="button" @click="openCollaborationInspector(run.meta, 'graph')">图谱</button>
              <button type="button" @click="openCollaborationInspector(run.meta, 'trace')">轨迹</button>
            </div>
          </div>
        </div>
        <div v-else class="collaboration-empty">
          <el-empty :image-size="56" description="暂无运行任务" />
          <el-button text class="collaboration-empty-action" @click="sidebarTab = 'session'">去发起协作</el-button>
        </div>
      </div>
      <div v-show="sidebarTab === 'config' && !sessionPaneCollapsed" class="workspace-config-pane">

        <el-tabs v-model="activeConfigTab" class="config-tabs">
          <el-tab-pane label="模型配置" name="model" />
          <el-tab-pane label="工具权限" name="tools" />
          <el-tab-pane label="高级" name="other" />
        </el-tabs>

        <div class="config-scroll">
          <template v-if="activeConfigTab === 'model'">
            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  智能体信息
                </div>
                <div
                  class="config-badge soft"
                  :class="`agent-status-${agentRuntimeStatus.level}`"
                  :title="agentRuntimeStatus.hint"
                >
                  {{ agentRuntimeStatus.label }}
                </div>
              </div>
              <div class="config-row">
                <span>智能体</span>
                <template v-if="isEditingAgentName">
                  <el-input
                    ref="agentNameInputRef"
                    v-model="editingAgentName"
                    class="agent-name-input"
                    placeholder="请输入智能体名称"
                    @keydown.enter.prevent="saveAgentName"
                    @keydown.esc.prevent="cancelEditAgentName"
                    @blur="saveAgentName"
                  />
                </template>
                <strong
                  v-else
                  class="editable-agent-name"
                  title="双击修改名称"
                  @dblclick="startEditAgentName"
                >
                  {{ currentAgent?.name || '未选择' }}
                </strong>
              </div>
              <div class="config-row">
                <span>模型</span>
                <el-select
                  v-model="selectedModelName"
                  class="model-switcher"
                  placeholder="选择模型"
                  filterable
                  :loading="modelSwitching"
                  :disabled="modelSwitching || !currentAgentId"
                  @change="handleModelChange"
                >
                  <el-option
                    v-for="model in modelOptions"
                    :key="model.value"
                    :label="model.label"
                    :value="model.value"
                  />
                </el-select>
              </div>
              <div class="config-row">
                <span>交互模式</span>
                <strong class="mode-readonly">{{ currentInteractionModeLabel }}</strong>
              </div>
              <div class="config-row">
                <span>模型类型</span>
                <strong>{{ activeModelType }}</strong>
              </div>
              <div class="config-row">
                <span>提供方</span>
                <strong>{{ currentProviderLabel }}</strong>
              </div>
              <div class="config-row">
                <span>系统密钥</span>
                <el-select
                  v-model="selectedProviderKeyId"
                  class="model-switcher"
                  placeholder="选择系统已有 Key（可选）"
                  clearable
                >
                  <el-option
                    v-for="item in filteredProviderKeyOptions"
                    :key="item.id"
                    :label="item.label"
                    :value="item.id"
                  />
                </el-select>
              </div>
              <div class="config-row">
                <span>接口地址</span>
                <el-input
                  v-model="agentAccessProfileForm.endpointUrl"
                  class="model-switcher"
                  placeholder="例如 https://api.siliconflow.cn/v1"
                />
              </div>
              <div class="config-row">
                <span>API Key</span>
                <el-input
                  v-model="agentAccessProfileForm.apiKey"
                  class="model-switcher"
                  type="password"
                  show-password
                  clearable
                  :placeholder="agentAccessProfileForm.maskedApiKey ? `当前已配置: ${agentAccessProfileForm.maskedApiKey}` : '输入新的 API Key（留空则不修改）'"
                />
              </div>
              <div class="config-row">
                <span>MCP 暴露</span>
                <el-switch
                  v-model="mcpExposureEnabled"
                  :disabled="!canManageMcpExposure || savingMcpExposure"
                  active-text="开"
                  inactive-text="关"
                  @change="saveMcpExposure"
                />
              </div>
            </section>

            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  模型参数
                </div>
                <div class="config-badge">
                  {{ activeModelTypeLabel }}
                </div>
              </div>
              <div class="config-card-desc">
                会根据模型类型自动切换可编辑参数。
              </div>

              <template v-if="isChatLikeModel">
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Temperature</span>
                      <span class="param-desc">控制回复随机性与创造力</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.temperature }}</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.temperature" :min="0" :max="2" :step="0.1" />
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Top P</span>
                      <span class="param-desc">控制采样范围与输出稳定性</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.topP }}</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.topP" :min="0" :max="1" :step="0.1" />
                </div>
                <div class="config-row">
                  <span>Max Tokens</span>
                  <el-input-number
                    v-model="agentRuntimeForm.maxTokens"
                    :min="1"
                    :max="65536"
                    :step="256"
                    controls-position="right"
                  />
                </div>
                <div class="config-row">
                  <span>深度思考</span>
                  <el-switch v-model="workspaceDeepThinking" active-text="开" inactive-text="关" />
                </div>
                <div class="prompt-editor">
                  <div class="prompt-editor-header">
                    <el-icon class="prompt-editor-icon"><ChatRound /></el-icon>
                    <div>
                      <div class="prompt-editor-title">提示</div>
                      <div class="prompt-editor-subtitle">这些消息将作为上下文在每次聊天交互前发送</div>
                    </div>
                  </div>
                  <div
                    v-for="(msg, idx) in promptMessages"
                    :key="idx"
                    class="prompt-message-block"
                  >
                    <div class="prompt-message-role-row">
                      <span class="role-dot" :class="`role-dot--${msg.role}`"></span>
                      <span class="role-label">{{ { system: 'System', user: 'User', assistant: 'Assistant' }[msg.role] }}</span>
                      <el-button v-if="idx > 0" link class="remove-msg-btn" @click="removePromptMessage(idx)">×</el-button>
                    </div>
                    <el-input
                      v-model="msg.content"
                      type="textarea"
                      :rows="msg.role === 'system' ? 4 : 2"
                      :placeholder="idx === 0 ? '定义智能体的身份、回复风格和约束条件...' : ''"
                      @input="onPromptMessagesChange"
                    />
                  </div>
                  <div class="prompt-add-row">
                    <el-button class="prompt-add-btn" @click="addPromptMessage('user')">+ User</el-button>
                    <el-button class="prompt-add-btn" @click="addPromptMessage('assistant')">+ Assistant</el-button>
                  </div>
                </div>
              </template>

              <template v-else-if="isImageModel">
                <div class="config-row">
                  <span>图像尺寸</span>
                  <el-select v-model="agentRuntimeForm.imageSize" class="model-switcher">
                    <el-option label="正方形 1:1 (1328x1328)" value="1328x1328" />
                    <el-option label="横屏 16:9 (1664x928)" value="1664x928" />
                    <el-option label="竖屏 9:16 (928x1664)" value="928x1664" />
                    <el-option label="标准 4:3 (1472x1140)" value="1472x1140" />
                    <el-option label="标准 3:4 (1140x1472)" value="1140x1472" />
                  </el-select>
                </div>
                <div class="config-row">
                  <span>随机种子</span>
                  <el-input v-model="agentRuntimeForm.seed" placeholder="留空则随机生成">
                    <template #append>
                      <el-button :icon="Refresh" @click="generateRandomSeed" />
                    </template>
                  </el-input>
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">CFG Scale</span>
                      <span class="param-desc">提示词约束强度</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.guidanceScale }}</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.guidanceScale" :min="1" :max="20" :step="0.5" />
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Steps</span>
                      <span class="param-desc">步数越高质量越好但耗时增加</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.inferenceSteps }}</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.inferenceSteps" :min="1" :max="50" :step="1" />
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Negative Prompt</span>
                      <span class="param-desc">描述不希望出现的元素</span>
                    </div>
                  </div>
                  <el-input
                    v-model="agentRuntimeForm.negativePrompt"
                    type="textarea"
                    :rows="3"
                    placeholder="例如：低清晰度、模糊、噪点"
                  />
                </div>
              </template>

              <template v-else-if="isVideoModel">
                <div class="config-row">
                  <span>视频比例</span>
                  <el-radio-group v-model="agentRuntimeForm.videoSize" size="small">
                    <el-radio-button value="16:9" label="16:9" />
                    <el-radio-button value="9:16" label="9:16" />
                    <el-radio-button value="1:1" label="1:1" />
                  </el-radio-group>
                </div>
                <div class="config-row">
                  <span>视频时长</span>
                  <el-select v-model="agentRuntimeForm.videoDuration" class="model-switcher">
                    <el-option label="5 秒" value="5" />
                    <el-option label="10 秒" value="10" />
                  </el-select>
                </div>
                <div class="config-row">
                  <span>随机种子</span>
                  <el-input v-model="agentRuntimeForm.seed" placeholder="留空则随机生成">
                    <template #append>
                      <el-button :icon="Refresh" @click="generateRandomSeed" />
                    </template>
                  </el-input>
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Negative Prompt</span>
                      <span class="param-desc">排除不希望出现的内容</span>
                    </div>
                  </div>
                  <el-input
                    v-model="agentRuntimeForm.negativePrompt"
                    type="textarea"
                    :rows="2"
                    placeholder="例如：抖动、失真、字幕遮挡"
                  />
                </div>
              </template>

              <template v-else-if="isSpeechModel">
                <div class="config-row">
                  <span>音色</span>
                  <el-select
                    v-model="agentRuntimeForm.voice"
                    class="model-switcher"
                    placeholder="请选择音色"
                    filterable
                    allow-create
                    clearable
                  >
                    <el-option
                      v-for="voice in voiceOptions"
                      :key="voice.value"
                      :label="voice.label"
                      :value="voice.value"
                    />
                  </el-select>
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Speed</span>
                      <span class="param-desc">语速倍率</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.speed }}x</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.speed" :min="0.5" :max="2" :step="0.1" />
                </div>
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">Gain</span>
                      <span class="param-desc">音量增益</span>
                    </div>
                    <span class="value-badge">{{ agentRuntimeForm.gain }} dB</span>
                  </div>
                  <el-slider v-model="agentRuntimeForm.gain" :min="-10" :max="10" :step="1" />
                </div>
              </template>

              <template v-else>
                <div class="config-card-desc">
                  该模型类型暂未定义可编辑参数，默认按通用配置运行。
                </div>
              </template>
            </section>

            <section class="config-card">
              <div class="config-card-title">
                会话状态
              </div>
              <div class="config-row">
                <span>当前会话</span>
                <strong>{{ currentSessionTitle }}</strong>
              </div>
              <div class="config-row">
                <span>消息数</span>
                <strong>{{ messages.length }}</strong>
              </div>
            </section>
          </template>

          <template v-else-if="activeConfigTab === 'tools'">
            <!-- 工具 card -->
            <section class="config-card collapsible-card">
              <div class="config-card-head card-toggle-head" @click="toggleToolsCard('builtin')">
                <div class="config-card-title">工具</div>
                <div class="card-head-right">
                  <div class="config-badge">{{ builtinTools.length }}</div>
                  <el-icon class="card-chevron" :class="{ expanded: toolsCardExpanded.has('builtin') }"><ArrowDown /></el-icon>
                </div>
              </div>
              <template v-if="toolsCardExpanded.has('builtin')">
                <div class="config-card-desc">当前 agent 可调用的内置工具。</div>
                <div class="selection-list">
                  <div v-for="tool in builtinTools" :key="tool.name" class="selection-item" style="cursor: default">
                    <div class="selection-info">
                      <div class="selection-name">{{ tool.name }}</div>
                      <div class="selection-meta">{{ tool.description }}</div>
                    </div>
                    <span :class="['tool-status-badge', tool.active ? 'tool-active' : 'tool-inactive']">
                      {{ tool.active ? '激活' : '未激活' }}
                    </span>
                  </div>
                </div>
              </template>
            </section>

            <!-- 知识库 card -->
            <section class="config-card collapsible-card">
              <div class="config-card-head card-toggle-head" @click="toggleToolsCard('kb')">
                <div class="config-card-title">知识库</div>
                <div class="card-head-right">
                  <div class="config-badge">{{ attachedKbIds.length }}/{{ knowledgeBases.length }}</div>
                  <el-icon class="card-chevron" :class="{ expanded: toolsCardExpanded.has('kb') }"><ArrowDown /></el-icon>
                </div>
              </div>
              <template v-if="toolsCardExpanded.has('kb')">
                <div class="config-card-desc">附加到当前会话后，回复会参考检索结果。</div>
                <el-input v-model="kbSearch" placeholder="搜索知识库..." :prefix-icon="Search" clearable />
                <div v-if="attachedKbIds.length" class="selection-tags">
                  <span
                    v-for="kbId in attachedKbIds"
                    :key="kbId"
                    class="selection-tag active"
                    @click="toggleKb(kbId)"
                  >
                    {{ getKnowledgeBaseName(kbId) }}
                  </span>
                </div>
                <div class="selection-list">
                  <div v-for="kb in filteredKnowledgeBases" :key="kb.id" class="kb-item-wrapper">
                    <label class="selection-item">
                      <div class="selection-info">
                        <div class="selection-name">{{ kb.name }}</div>
                        <div class="selection-meta">{{ kb.documentCount || 0 }} 文档</div>
                      </div>
                      <el-checkbox :model-value="isKbAttached(kb.id)" @change="toggleKb(kb.id)" />
                    </label>
                    <div v-if="isKbAttached(kb.id)" class="doc-filter-section">
                      <div class="doc-filter-header" @click="toggleKbExpand(kb.id)">
                        <span class="doc-filter-label">
                          <el-icon class="doc-filter-chevron" :class="{ expanded: isKbExpanded(kb.id) }"><ArrowDown /></el-icon>
                          按文档过滤
                          <span v-if="kbDocFilters[kb.id]?.length" class="doc-filter-count">
                            ({{ kbDocFilters[kb.id].length }})
                          </span>
                        </span>
                      </div>
                      <div v-if="isKbExpanded(kb.id)" class="doc-filter-list">
                        <div v-if="!kbDocuments[kb.id]?.length" class="doc-filter-loading">加载中...</div>
                        <label v-for="doc in kbDocuments[kb.id]" :key="doc.id" class="doc-filter-item">
                          <el-checkbox :model-value="isDocSelected(kb.id, doc.id)" @change="toggleDocFilter(kb.id, doc.id)" />
                          <span class="doc-filter-name">{{ doc.name || doc.fileName || doc.id }}</span>
                        </label>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="config-row" style="margin-top: 8px">
                  <span>检索策略</span>
                  <el-select v-model="agentRuntimeForm.toolCallingOverride" style="width: 180px" placeholder="自动选择（推荐）" @change="saveAgentRuntimeConfig">
                    <el-option label="自动选择（推荐）" :value="null" />
                    <el-option label="模型工具检索（Tool Calling）" :value="true" />
                    <el-option label="上下文附加（Context Injection）" :value="false" />
                  </el-select>
                </div>
              </template>
            </section>

            <!-- MCP 服务 card -->
            <section class="config-card collapsible-card">
              <div class="config-card-head card-toggle-head" @click="toggleToolsCard('mcp')">
                <div class="config-card-title">MCP 服务</div>
                <div class="card-head-right">
                  <div class="config-badge">{{ currentConfig.mcpIds.length }}/{{ mcpServices.length }}</div>
                  <el-icon class="card-chevron" :class="{ expanded: toolsCardExpanded.has('mcp') }"><ArrowDown /></el-icon>
                </div>
              </div>
              <template v-if="toolsCardExpanded.has('mcp')">
                <div class="config-card-desc">选中的服务会作为当前工作台的扩展能力。</div>
                <div v-if="currentConfig.mcpIds.length" class="selection-tags">
                  <span
                    v-for="serviceId in currentConfig.mcpIds"
                    :key="serviceId"
                    class="selection-tag"
                    @click="toggleConfigItem('mcpIds', serviceId)"
                  >
                    {{ getMcpServiceName(serviceId) }}
                  </span>
                </div>
                <div class="selection-list">
                  <label v-for="service in mcpServices" :key="service.id" class="selection-item">
                    <div class="selection-info">
                      <div class="selection-name">{{ service.name }}</div>
                      <div class="selection-meta">{{ service.type || 'MCP' }} · {{ formatMcpStatus(service.status) }}</div>
                    </div>
                    <el-checkbox :model-value="currentConfig.mcpIds.includes(service.id)" @change="toggleConfigItem('mcpIds', service.id)" />
                  </label>
                  <el-empty v-if="!mcpServices.length" :image-size="44" description="暂无可用 MCP 服务" />
                </div>
              </template>
            </section>

            <!-- Skills card -->
            <section class="config-card collapsible-card">
              <div class="config-card-head card-toggle-head" @click="toggleToolsCard('skills')">
                <div class="config-card-title">Skills</div>
                <div class="card-head-right">
                  <div class="config-badge">{{ currentConfig.skillIds.length }}/{{ skills.length }}</div>
                  <el-icon class="card-chevron" :class="{ expanded: toolsCardExpanded.has('skills') }"><ArrowDown /></el-icon>
                </div>
              </div>
              <template v-if="toolsCardExpanded.has('skills')">
                <div class="config-card-desc">把常用技能固定在当前配置里，便于连续对话时使用。</div>
                <div v-if="currentConfig.skillIds.length" class="selection-tags">
                  <span
                    v-for="skillId in currentConfig.skillIds"
                    :key="skillId"
                    class="selection-tag"
                    @click="toggleConfigItem('skillIds', skillId)"
                  >
                    {{ getSkillName(skillId) }}
                  </span>
                </div>
                <div class="selection-list">
                  <label v-for="skill in skills" :key="skill.id" class="selection-item">
                    <div class="selection-info">
                      <div class="selection-name">{{ skill.skillName || skill.name }}</div>
                      <div class="selection-meta">{{ skill.skillType || skill.type || 'SKILL' }}</div>
                    </div>
                    <el-checkbox :model-value="currentConfig.skillIds.includes(skill.id)" @change="toggleConfigItem('skillIds', skill.id)" />
                  </label>
                  <el-empty v-if="!skills.length" :image-size="44" description="暂无技能" />
                </div>
              </template>
            </section>
          </template>

          <template v-else>
            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  配置摘要
                </div>
                <div class="config-badge">
                  {{ currentConfig.name }}
                </div>
              </div>
              <div class="config-row">
                <span>配置名</span>
                <strong>{{ currentConfig.name }}</strong>
              </div>
              <div class="config-row">
                <span>知识库</span>
                <strong>{{ attachedKbIds.length }} 个</strong>
              </div>
              <div class="config-row">
                <span>Skills</span>
                <strong>{{ currentConfig.skillIds.length }} 个</strong>
              </div>
              <div class="config-row">
                <span>MCP</span>
                <strong>{{ currentConfig.mcpIds.length }} 个</strong>
              </div>
            </section>

            <section class="config-card">
              <div class="config-card-title">
                偏好设置
              </div>
              <div class="config-row">
                <span>自动生成会话标题</span>
                <el-switch v-model="currentConfig.autoRenameSession" />
              </div>
              <div class="config-row">
                <span>显示快捷建议</span>
                <el-switch v-model="currentConfig.enableSuggestions" />
              </div>
              <div class="config-row">
                <span>显示检索上下文</span>
                <el-switch v-model="currentConfig.showRetrievedContext" />
              </div>
            </section>
          </template>
        </div>

        <div class="config-footer">
          <el-button type="primary" :loading="savingModelParams" @click="saveCurrentConfig">
            {{ activeConfigTab === 'model' ? '保存模型参数' : '保存' }}
          </el-button>
        </div>
      </div>
    </aside>

    <div v-if="!sessionPaneCollapsed && sidebarTab === 'session'" class="expanded-collapse-panel">
      <el-tooltip content="收起会话记录" placement="right">
        <button
          type="button"
          class="session-edge-handle"
          aria-label="收起会话记录"
          title="收起会话记录"
          @click="sessionPaneCollapsed = true"
        >
          <el-icon>
            <ArrowLeft />
          </el-icon>
        </button>
      </el-tooltip>
    </div>

    <main class="workspace-main">
      <div v-if="!currentAgent" class="state-panel">
        <div class="welcome-panel">
          <h2>{{ greetingText }}</h2>

          <div class="composer-placeholder is-disabled">
              <div class="quick-config-row">
                <button type="button" class="quick-config-chip" disabled>
                  智能体：{{ currentAgent?.name || '未选择' }}
                </button>
                <button type="button" class="quick-config-chip" disabled>
                  模式：{{ currentInteractionLabel }}
                </button>
              <button type="button" class="quick-config-chip" disabled>
                知识库：{{ attachedKbIds.length }}
              </button>
            </div>
            <el-input
              model-value=""
              type="textarea"
              :rows="4"
              resize="none"
              placeholder="请先在左侧选择智能体后开始对话"
              disabled
            />

              <div class="composer-footer">
                <div class="composer-left-tools">
                  <button type="button" class="plus-trigger" disabled @click="triggerFilePicker">
                    +
                  </button>
                </div>
              <div class="composer-right-tools">
                <el-button
                  class="composer-send-btn"
                  type="primary"
                  circle
                  :icon="Top"
                  disabled
                />
              </div>
            </div>
          </div>

          <div class="quick-prompts">
            <el-tag
              v-for="prompt in quickPrompts"
              :key="`empty-${prompt}`"
              effect="plain"
              class="prompt-tag"
            >
              {{ prompt }}
            </el-tag>
          </div>
        </div>
      </div>

      <template v-else>
        <div ref="messagesContainer" class="messages-container" :class="{ 'is-empty': messages.length === 0 }">
          <div v-if="messages.length === 0" class="welcome-panel">
            <h2>{{ greetingText }}</h2>

            <div class="composer-placeholder">
              <div class="quick-config-row">
                <button
                  v-for="chip in composerQuickChips"
                  :key="chip.key"
                  type="button"
                  class="quick-config-chip"
                  :disabled="chip.disabled"
                  @click="handleWorkspaceChipClick(chip)"
                >
                  {{ chip.label }}
                </button>
                <button
                  type="button"
                  class="quick-config-chip is-toggle"
                  :class="{ active: collaborationRequested }"
                  :disabled="loading"
                  @click="collaborationRequested = !collaborationRequested"
                >
                  协作
                </button>
              </div>
              <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="4"
                resize="none"
                placeholder="问点什么？使用 @ 可以提及哦~"
                @keydown.enter="handleEnter"
              />

              <div class="composer-footer">
                <div class="composer-left-tools">
                  <button type="button" class="plus-trigger" :disabled="uploadingFile" @click="triggerFilePicker">
                    +
                  </button>
                </div>
                <div class="composer-right-tools">
                  <el-button
                    class="composer-send-btn"
                    type="primary"
                    circle
                    :icon="Top"
                    :disabled="loading || (!inputMessage.trim() && !selectedUploadFileId)"
                    @click="sendMessage"
                  />
                </div>
              </div>
              <div v-if="selectedUploadFileName" class="attached-file-row">
                <span class="attached-file-name">已附加：{{ selectedUploadFileName }}</span>
                <button type="button" class="attached-file-remove" @click="clearUploadedFile">移除</button>
              </div>
            </div>

            <div class="quick-prompts">
              <el-tag
                v-for="prompt in quickPrompts"
                :key="prompt"
                effect="plain"
                class="prompt-tag"
                @click="applyPrompt(prompt)"
              >
                {{ prompt }}
              </el-tag>
            </div>
          </div>

          <template v-else>
            <div
              v-for="(msg, index) in messages"
              :key="`${msg.role}-${index}`"
              :class="['message-item', msg.role]"
            >
              <div class="message-avatar">
                <el-icon v-if="msg.role === 'user'">
                  <User />
                </el-icon>
                <el-icon v-else>
                  <Cpu />
                </el-icon>
              </div>

              <div class="message-bubble">
                <div class="message-role">
                  <span>{{ msg.role === 'user' ? currentUserLabel : currentAgent.name }}</span>
                  <span v-if="msg.createdAt" class="message-time">{{ formatMessageTime(msg.createdAt) }}</span>
                  <span v-if="msg.role === 'assistant' && (msg.model || msg.provider)" class="message-meta">
                    <span v-if="msg.model">{{ msg.model }}</span>
                    <span v-if="msg.promptTokens || msg.completionTokens" class="meta-tokens">
                      ↑{{ msg.promptTokens || 0 }} ↓{{ msg.completionTokens || 0 }}
                    </span>
                  </span>
                  <span
                    v-if="msg.role === 'assistant' && getAssistantRuntimeMeta(msg).length"
                    class="message-runtime-meta"
                  >
                    <span
                      v-for="item in getAssistantRuntimeMeta(msg)"
                      :key="`${msg.createdAt || 'runtime'}-${item}`"
                      class="runtime-chip"
                    >
                      {{ item }}
                    </span>
                  </span>
                </div>
                <div
                  v-if="msg.role === 'assistant' && msg.toolTraces?.length"
                  class="reasoning-section"
                >
                  <div class="reasoning-title">检索/思考过程</div>
                  <div class="reasoning-list">
                    <div
                      v-for="(trace, traceIdx) in getVisibleToolTraces(msg.toolTraces)"
                      :key="`${index}-reason-${traceIdx}`"
                      :class="['reasoning-item', 'trace-' + (trace.status || 'pending')]"
                    >
                      <div class="reasoning-step-dot">{{ traceIdx + 1 }}</div>
                      <div class="reasoning-main">
                        <div class="reasoning-top" @click="toggleTraceDetail(msg, index, traceIdx)">
                          <span class="reasoning-name">{{ formatTraceType(trace.type) }}</span>
                          <span class="reasoning-status" :class="'status-' + (trace.status || 'pending')">
                            {{ formatTraceStatus(trace.status) }}
                          </span>
                          <span v-if="trace.durationMs != null" class="reasoning-duration">{{ trace.durationMs }}ms</span>
                          <button
                            v-if="shouldShowTraceDetail(trace)"
                            type="button"
                            class="reasoning-expand-btn"
                          >
                            {{ isTraceDetailExpanded(msg, index, traceIdx) ? '收起' : '详情' }}
                          </button>
                        </div>
                        <div class="reasoning-msg">{{ trace.message }}</div>
                        <div
                          v-if="shouldShowTraceDetail(trace) && isTraceDetailExpanded(msg, index, traceIdx)"
                          class="reasoning-detail"
                        >
                          <pre>{{ formatTraceDetail(trace) }}</pre>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-if="getMessageImageUrl(msg)" class="message-media">
                  <el-image
                    :src="getMessageImageUrl(msg)"
                    fit="contain"
                    class="generated-image"
                    :preview-src-list="[getMessageImageUrl(msg)]"
                    :preview-teleported="true"
                  />
                </div>
                <div v-else-if="getMessageAudioUrl(msg)" class="message-media">
                  <audio :src="getMessageAudioUrl(msg)" controls class="generated-audio" />
                </div>
                <div v-else-if="getMessageVideoUrl(msg)" class="message-media">
                  <video :src="getMessageVideoUrl(msg)" controls class="generated-video" />
                </div>
                <div v-else class="message-text" v-html="renderMarkdown(getMessageText(msg))" />

                <div v-if="msg.collaborationMeta" class="collaboration-process">
                  <div class="collaboration-process-header" @click="toggleCollaborationDetail(msg, index)">
                    <div class="collaboration-process-title">
                      <span>协作过程</span>
                      <strong>{{ formatCollaborationStatus(msg.collaborationMeta) }}</strong>
                    </div>
                    <span class="collaboration-process-toggle" @click.stop="toggleCollaborationDetail(msg, index)">
                      {{ isCollaborationDetailExpanded(msg, index) ? '收起' : '展开' }}
                    </span>
                  </div>
                  <div class="collaboration-participants">
                    <span
                      v-for="participant in msg.collaborationMeta.participants || []"
                      :key="participant.id || participant.name"
                      class="collaboration-participant"
                    >
                      @{{ participant.name }}
                      <em v-if="participant.ephemeral">临时</em>
                    </span>
                  </div>
                  <div v-if="isCollaborationDetailExpanded(msg, index)" class="collaboration-detail">
                    <div v-if="msg.collaborationMeta.planner" class="collaboration-report-card">
                      <div class="collaboration-report-title">Planner</div>
                      <div class="collaboration-report-body" v-html="renderMarkdown(formatCollaborationArtifact(msg.collaborationMeta.planner))" />
                    </div>
                    <div
                      v-for="(report, reportIndex) in msg.collaborationMeta.taskReports || []"
                      :key="`report-${reportIndex}`"
                      class="collaboration-report-card"
                    >
                      <div class="collaboration-report-title">
                        {{ report.agent_name || report.agentName || report.agent_id || `Task ${reportIndex + 1}` }}
                      </div>
                      <div class="collaboration-report-body" v-html="renderMarkdown(formatCollaborationArtifact(report))" />
                    </div>
                    <div v-if="getCollaborationTraceSummary(msg.collaborationMeta).length" class="collaboration-report-card">
                      <div class="collaboration-report-title">轨迹摘要</div>
                      <div class="collaboration-trace-list">
                        <div
                          v-for="(trace, traceIndex) in getCollaborationTraceSummary(msg.collaborationMeta)"
                          :key="`trace-${traceIndex}`"
                          class="collaboration-trace-item"
                        >
                          {{ trace }}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="msg.retrievedChunks?.length" class="retrieved-context">
                  <div class="context-header context-toggle" @click="toggleRetrievedContext(msg, index)">
                    <div class="context-header-left">
                      <el-icon><Document /></el-icon>
                      <span>检索依据 {{ msg.retrievedChunks.length }} 条</span>
                    </div>
                    <span class="context-toggle-text">
                      {{ isRetrievedContextExpanded(msg, index) ? '收起' : '查看依据' }}
                    </span>
                  </div>
                  <div v-if="isRetrievedContextExpanded(msg, index)" class="context-body">
                    <div
                      v-for="(chunk, chunkIndex) in msg.retrievedChunks"
                      :key="`${index}-${chunkIndex}`"
                      class="context-item"
                    >
                      <div class="chunk-source is-clickable" @click="openCitation(chunk)">
                        [{{ chunkIndex + 1 }}] {{ getChunkSourceLabel(chunk) }}
                      </div>
                      <div class="chunk-text">
                        {{ chunk.content?.substring(0, 160) }}{{ chunk.content?.length > 160 ? '...' : '' }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="loading" class="loading-indicator">
              <el-icon class="is-loading">
                <Loading />
              </el-icon>
              <span>{{ loadingHint }}</span>
            </div>
          </template>
        </div>

        <div v-if="messages.length > 0" class="input-area">
          <div class="input-area-wrapper">
            <div class="quick-config-row compact">
              <button
                v-for="chip in inputQuickChips"
                :key="chip.key"
                type="button"
                class="quick-config-chip"
                :disabled="chip.disabled"
                @click="handleWorkspaceChipClick(chip)"
              >
                {{ chip.label }}
              </button>
              <button
                type="button"
                class="quick-config-chip is-toggle"
                :class="{ active: collaborationRequested }"
                :disabled="loading"
                @click="collaborationRequested = !collaborationRequested"
              >
                协作
              </button>
            </div>
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入消息... Enter 发送，Shift+Enter 换行"
              :disabled="loading || !currentSessionId"
              @keydown.enter="handleEnter"
            />
            <div class="input-actions">
              <div class="input-hint">
                <span class="input-hint-agent" :title="currentAgent?.name || '未选择'">当前：{{ currentAgent?.name || '未选择' }}</span>
                <span v-if="collaborationRequested">协作已开启</span>
                <span v-if="totalFilteredDocs > 0">文档过滤 {{ totalFilteredDocs }} 个</span>
                <span v-if="selectedUploadFileName">文件：{{ selectedUploadFileName }}</span>
              </div>
              <button type="button" class="plus-trigger input-plus" :disabled="uploadingFile" @click="triggerFilePicker">
                +
              </button>
              <el-button
                type="primary"
                :loading="loading"
                :disabled="!currentSessionId || (!inputMessage.trim() && !selectedUploadFileId)"
                @click="sendMessage"
              >
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </main>

    <input
      ref="fileInputRef"
      type="file"
      class="hidden-file-input"
      @change="onFileSelected"
    >

    <el-drawer
      v-model="collaborationInspectorVisible"
      title="协作详情"
      size="46%"
      class="collaboration-inspector-drawer"
      append-to-body
      destroy-on-close
    >
      <div class="collaboration-inspector">
        <div class="collaboration-inspector-summary">
          <div>
            <strong>{{ selectedCollaborationMeta?.workflowName || '临时协作工作流' }}</strong>
            <span>{{ selectedCollaborationMeta?.participants?.length || 0 }} 个智能体参与</span>
          </div>
          <div class="collaboration-inspector-participants">
            <span
              v-for="participant in selectedCollaborationMeta?.participants || []"
              :key="participant.id || participant.name"
            >
              @{{ participant.name }}<em v-if="participant.ephemeral">临时</em>
            </span>
          </div>
        </div>
        <el-tabs v-model="collaborationInspectorTab" class="collaboration-inspector-tabs">
          <el-tab-pane label="工作流图" name="graph">
            <div class="playground-scope standalone-run collaboration-playground-panel">
              <GraphViewer
                :graph="selectedCollaborationGraph"
                :trace="selectedCollaborationTrace"
                :trace-playing="selectedCollaborationMeta?.status === 'running'"
              />
            </div>
          </el-tab-pane>
          <el-tab-pane label="执行轨迹" name="trace">
            <div class="playground-scope standalone-run collaboration-playground-panel">
              <TraceViewer
                :trace="selectedCollaborationTrace"
                :playing="selectedCollaborationMeta?.status === 'running'"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
    
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, provide, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { marked } from 'marked';
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ChatRound,
  Cpu,
  Delete,
  Document,
  Loading,
  Plus,
  Promotion,
  Refresh,
  Search,
  Top,
  User
} from '@element-plus/icons-vue';
import {
  chatAgent,
  getAgentAccessProfile,
  getAgentMetadata,
  uploadMultimodalFile,
  updateAgent,
} from '@/api/agent';
import { getExternalKeys } from '@/api/apiKey';
import {
  attachKnowledgeBase,
  createChatSession,
  deleteChatSession,
  detachKnowledgeBase,
  getAgentToolBinding,
  getAttachedKnowledgeBases,
  getChatSession,
  getSessionToolBinding,
  getToolCatalog,
  listAgents,
  listChatSessions,
  listKnowledgeBases,
  saveChatSessionMessages,
  saveSessionToolBinding,
  sendChatMessage,
  sendChatMessageStream,
  updateKbDocFilters
} from '@/api/agent-chat';
import { getModelList } from '@/api/model';
import { getDocuments } from '@/api/knowledge';
import { getSkillList } from '@/api/skill';
import { getMcpServices } from '@/api/mcp';
import { useInteractionShell } from '@/composables/useInteractionShell';
import { runQuickChipAction } from '@/composables/useInteractionQuickChips';
import { buildWorkspaceChipSets } from '@/composables/useInteractionChipRegistry';
import { useUserStore } from '@/stores/user';
import {
  createWorkflow,
  fetchWorkflows as fetchPlaygroundWorkflows,
  runWorkflow,
  runWorkflowStream
} from '@/views/Playground/api';
import GraphViewer from '@/views/Playground/components/GraphViewer.vue';
import TraceViewer from '@/views/Playground/components/TraceViewer.vue';
import { createUiI18n, I18N_KEY } from '@/views/Playground/i18n';
import '@/views/Playground/playground.css';

const props = defineProps({
  presetAgentId: {
    type: [String, Number],
    default: ''
  },
  lockAgent: {
    type: Boolean,
    default: false
  }
});
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
marked.setOptions({
  gfm: true,
  breaks: true
});
provide(I18N_KEY, createUiI18n());

const CONFIG_STORAGE_PREFIX = 'agent-workspace-config:';
const WORKSPACE_STATE_KEY = 'agent-workspace-state';
const SESSION_MESSAGES_CACHE_KEY = 'agent-workspace-session-messages';
const SESSION_MESSAGES_CACHE_LIMIT = 50;

const quickPromptMap = {
  CHAT: [
    '你好，请介绍一下你自己',
    '帮我写一封商务邮件',
    '解释一下什么是机器学习',
    '创建一个冒泡排序 Python 示例'
  ],
  TEXT_TO_IMAGE: [
    '生成一张赛博朋克风格的城市夜景，霓虹灯，高细节',
    '画一只戴宇航头盔的柯基，写实风，4K',
    '设计一个极简科技风 APP 图标，蓝绿配色',
    '生成一张中国山水水墨风海报，留白，竖版'
  ],
  TEXT_TO_VIDEO: [
    '生成一个黄昏海边延时镜头，镜头缓慢推进，10 秒',
    '制作一段产品展示短视频，干净背景，16:9',
    '生成城市街头雨夜氛围视频，霓虹反光，5 秒',
    '做一个卡通角色转身挥手动画，平滑过渡'
  ],
  TEXT_TO_SPEECH: [
    '用温和语气朗读：欢迎使用 ORIN 智能体平台',
    '把这段文案转成语音：今天下午三点开会，请准时参加',
    '生成一段播报：当前系统运行正常，未发现异常告警',
    '朗读一段客服话术：您好，很高兴为您服务'
  ]
};

const voiceOptions = [
  { label: 'Alex', value: 'alex' },
  { label: 'Anna', value: 'anna' },
  { label: 'Bella', value: 'bella' },
  { label: 'Benjamin', value: 'benjamin' },
  { label: 'Charles', value: 'charles' },
  { label: 'David', value: 'david' }
];

const defaultConfig = () => ({
  id: 'default',
  name: '初始配置（默认）',
  toolIds: [],
  kbIds: [],
  skillIds: [],
  mcpIds: [],
  enableSuggestions: true,
  showRetrievedContext: true,
  autoRenameSession: true
});

const defaultRuntimeForm = () => ({
  temperature: 0.7,
  topP: 0.7,
  maxTokens: 2000,
  systemPrompt: '',
  toolCallingOverride: null,
  imageSize: '1328x1328',
  seed: '',
  guidanceScale: 7.5,
  inferenceSteps: 20,
  negativePrompt: '',
  voice: '',
  speed: 1.0,
  gain: 0,
  videoSize: '16:9',
  videoDuration: '5'
});

const agents = ref([]);
const agentsLoading = ref(false);
const knowledgeBases = ref([]);
const sessions = ref([]);
const skills = ref([]);
const mcpServices = ref([]);
const toolCatalog = ref([]);
const messages = ref([]);
const attachedKbIds = ref([]);
const kbDocFilters = reactive({});  // {[kbId]: string[]}
const kbDocuments = ref({});        // {[kbId]: documents[]}
const expandedKbIds = ref(new Set());
const currentAgentId = ref('');
const currentAgent = ref(null);
const currentSessionId = ref('');
const mcpExposureEnabled = ref(false);
const savingMcpExposure = ref(false);
const kbSearch = ref('');
const inputMessage = ref('');
const loading = ref(false);
const loadingHint = ref('思考中...');
const runtimeStatusLevel = ref('idle'); // idle | running | fallback | error
const runtimeStatusHint = ref('空闲');
const sessionPaneCollapsed = ref(false);
const sidebarTab = ref('session');
const activeConfigTab = ref('tools');
const currentConfigId = ref('default');
const selectedModelName = ref('');
const modelSwitching = ref(false);
const modelCatalog = ref([]);
const savingModelParams = ref(false);
const isEditingAgentName = ref(false);
const editingAgentName = ref('');
const agentNameInputRef = ref(null);
const fileInputRef = ref(null);
const selectedUploadFileId = ref('');
const selectedUploadFileName = ref('');
const uploadingFile = ref(false);
const messagesContainer = ref(null);
const currentConfig = reactive(defaultConfig());
const agentRuntimeForm = reactive(defaultRuntimeForm());
const agentAccessProfileForm = reactive({
  endpointUrl: '',
  apiKey: '',
  maskedApiKey: ''
});
const providerKeyOptions = ref([]);
const selectedProviderKeyId = ref(null);
const expandedRetrievedContext = ref({});
const expandedTraceDetails = ref({});
const expandedCollaborationDetails = ref({});
const workspaceDeepThinking = ref(false);
const collaborationRequested = ref(false);
const collaborationInspectorVisible = ref(false);
const collaborationInspectorTab = ref('graph');
const selectedCollaborationMeta = ref(null);
const selectedCollaborationTrace = computed(() => (
  Array.isArray(selectedCollaborationMeta.value?.trace)
    ? selectedCollaborationMeta.value.trace
    : []
));
const selectedCollaborationGraph = computed(() => (
  selectedCollaborationMeta.value?.graph || buildCollaborationFallbackGraph(selectedCollaborationMeta.value)
));
const collaborationRuns = computed(() => messages.value
  .map((message, index) => ({ message, index }))
  .filter(({ message }) => message?.role === 'assistant' && message?.collaborationMeta)
  .map(({ message, index }) => ({
    key: `${message.createdAt || index}:collaboration`,
    title: message.collaborationMeta?.workflowName || `协作运行 ${index + 1}`,
    createdAt: message.createdAt || message.collaborationMeta?.createdAt || '',
    meta: message.collaborationMeta,
  }))
  .reverse());
const {
  containerRef,
  isWide,
  isMedium,
  isNarrow,
  isLeftDrawer,
} = useInteractionShell({
  leftDrawerMode: 'narrow',
  
});

// 保留 isMobile 以便兼容某些未删干净的模板指令
const isMobile = isNarrow;

const configProfiles = [{ id: 'default', name: '初始配置（默认）' }];

const currentUserLabel = computed(() => {
  const profile = userStore.userInfo || {};
  const preferred = profile.nickname || profile.displayName || profile.realName || profile.name || userStore.username;
  const normalized = String(preferred || '').trim();
  return normalized || '我';
});

const collaborationWorkflows = ref([]);
const collaborationWorkflowsLoading = ref(false);

const filteredKnowledgeBases = computed(() => {
  if (!kbSearch.value) return knowledgeBases.value;
  const keyword = kbSearch.value.toLowerCase();
  return knowledgeBases.value.filter((kb) => (kb.name || '').toLowerCase().includes(keyword));
});

const currentSessionTitle = computed(() => {
  return sessions.value.find((item) => item.id === currentSessionId.value)?.title || '新对话';
});

const normalizeAgentViewType = (agent) => {
  const rawType = agent?.viewType || agent?.mode || agent?.type || agent?.agentType || '';
  const normalized = String(rawType || '').trim().toUpperCase();
  if (!normalized) return 'CHAT';
  if (normalized === 'TTS') return 'TEXT_TO_SPEECH';
  if (normalized === 'STT') return 'SPEECH_TO_TEXT';
  if (normalized === 'TTI') return 'TEXT_TO_IMAGE';
  if (normalized === 'TTV') return 'TEXT_TO_VIDEO';
  if (normalized === 'LLM') return 'CHAT';
  return normalized;
};

const currentInteractionLabel = computed(() => normalizeAgentViewType(currentAgent.value));
const agentRuntimeStatus = computed(() => {
  const level = runtimeStatusLevel.value || 'idle';
  const map = {
    idle: '空闲',
    running: '思考中',
    fallback: '回退中',
    error: '失败'
  };
  return {
    level,
    label: map[level] || '空闲',
    hint: runtimeStatusHint.value || map[level] || '空闲'
  };
});
const INTERACTION_MODE_LABEL_MAP = {
  CHAT: '对话',
  TEXT_TO_IMAGE: '文生图',
  IMAGE_TO_IMAGE: '图生图',
  TEXT_TO_VIDEO: '文生视频',
  TEXT_TO_SPEECH: '语音合成',
  SPEECH_TO_TEXT: '语音转写'
};
const currentInteractionModeLabel = computed(() => {
  const mode = String(currentInteractionLabel.value || '').toUpperCase();
  return INTERACTION_MODE_LABEL_MAP[mode] || mode || '未知';
});
const greetingMap = {
  CHAT: '您好，有什么可以帮您？',
  TEXT_TO_IMAGE: '想生成什么图像？描述越具体，效果越好。',
  TEXT_TO_VIDEO: '想生成什么视频？可以描述镜头、时长和风格。',
  TEXT_TO_SPEECH: '请输入要朗读的文本，我来为你生成语音。'
};
const greetingText = computed(() => {
  const mode = String(currentInteractionLabel.value || 'CHAT').toUpperCase();
  return greetingMap[mode] || greetingMap.CHAT;
});
const quickPrompts = computed(() => {
  const mode = String(currentInteractionLabel.value || 'CHAT').toUpperCase();
  return quickPromptMap[mode] || quickPromptMap.CHAT;
});

const workspaceChipSets = computed(() => buildWorkspaceChipSets({
  interactionLabel: currentInteractionLabel.value,
  attachedKbCount: attachedKbIds.value.length,
  retrievedContextEnabled: currentConfig.showRetrievedContext,
  filteredDocsCount: totalFilteredDocs.value,
  runtimeParams: {
    temperature: agentRuntimeForm.temperature,
    maxTokens: agentRuntimeForm.maxTokens,
    imageSize: agentRuntimeForm.imageSize,
    inferenceSteps: agentRuntimeForm.inferenceSteps,
    videoSize: agentRuntimeForm.videoSize,
    videoDuration: agentRuntimeForm.videoDuration,
    voice: agentRuntimeForm.voice,
    speed: agentRuntimeForm.speed
  }
}));

const composerQuickChips = computed(() => workspaceChipSets.value.composer);
const inputQuickChips = computed(() => workspaceChipSets.value.input);

const handleWorkspaceChipClick = (chip) => {
  runQuickChipAction(chip, {
    openInspector: () => {
      openSettings();
    },
    toggleInspector: () => {
      openSettings();
    },
    openTab: (tab) => {
      activeConfigTab.value = tab || 'tools';
      openSettings();
    }
  });
};

const totalFilteredDocs = computed(() => {
  return Object.values(kbDocFilters).reduce((sum, docs) => sum + (docs?.length || 0), 0);
});

const normalizeModelRecord = (item = {}) => {
  const value = String(item.modelId || item.modelName || item.name || item.id || '').trim();
  return {
    value,
    label: item.name || item.modelName || item.modelId || value || '未知模型',
    provider: item.provider || item.providerType || '',
    type: normalizeAgentViewType(item),
    status: item.status || ''
  };
};

const normalizeProviderModelName = (modelName = '') => {
  const raw = String(modelName || '').trim();
  if (!raw) return '';
  const catalogMatch = modelCatalog.value.find((item) => {
    const candidates = [item.value, item.label].filter(Boolean).map((val) => String(val).trim().toLowerCase());
    return candidates.includes(raw.toLowerCase());
  });
  if (catalogMatch?.value) return catalogMatch.value;
  return raw;
};

const modelOptions = computed(() => {
  return modelCatalog.value
    .filter((item) => item.value)
    .map((item) => ({ value: item.value, label: item.label }));
});

const currentModelInfo = computed(() => {
  const current = String(selectedModelName.value || currentAgent.value?.model || '').trim().toLowerCase();
  if (!current) return null;
  return modelCatalog.value.find((item) => {
    const candidates = [item.value, item.label].filter(Boolean).map((val) => String(val).trim().toLowerCase());
    return candidates.includes(current);
  }) || null;
});

const currentProviderLabel = computed(() => {
  return (
    currentModelInfo.value?.provider ||
    currentAgent.value?.provider ||
    currentAgent.value?.providerType ||
    '未知'
  );
});
const canManageMcpExposure = computed(() => {
  const ownerId = currentAgent.value?.ownerUserId;
  return userStore.isAdmin || (ownerId != null && String(ownerId) === String(userStore.userId));
});

const filteredProviderKeyOptions = computed(() => {
  const provider = String(currentProviderLabel.value || '').trim().toLowerCase();
  if (!provider || provider === '未知') return providerKeyOptions.value;
  const matched = providerKeyOptions.value.filter((item) => item.provider.includes(provider));
  return matched.length ? matched : providerKeyOptions.value;
});

const inferModelTypeFromName = (modelName = '') => {
  const modelLower = String(modelName || '').trim().toLowerCase();
  if (!modelLower) return 'CHAT';
  if (
    modelLower.includes('wan-') ||
    modelLower.includes('-t2v') ||
    modelLower.includes('-i2v') ||
    modelLower.includes('video')
  ) {
    return 'TEXT_TO_VIDEO';
  }
  if (
    modelLower.includes('-tts') ||
    modelLower.includes('cosyvoice') ||
    modelLower.includes('speech')
  ) {
    return 'TEXT_TO_SPEECH';
  }
  if (
    modelLower.includes('flux') ||
    modelLower.includes('stable-diffusion') ||
    modelLower.includes('image')
  ) {
    return 'TEXT_TO_IMAGE';
  }
  return 'CHAT';
};

const activeModelType = computed(() => {
  const byCatalog = normalizeAgentViewType({ viewType: currentModelInfo.value?.type });
  if (byCatalog && byCatalog !== 'CHAT' && byCatalog !== 'UNKNOWN') return byCatalog;

  const byAgent = normalizeAgentViewType({ viewType: currentAgent.value?.viewType });
  if (byAgent && byAgent !== 'UNKNOWN') return byAgent;

  return inferModelTypeFromName(selectedModelName.value || currentAgent.value?.model || '');
});

const isImageModel = computed(() => activeModelType.value === 'TEXT_TO_IMAGE' || activeModelType.value === 'IMAGE_TO_IMAGE');
const isVideoModel = computed(() => activeModelType.value === 'TEXT_TO_VIDEO');
const isSpeechModel = computed(() => activeModelType.value === 'TEXT_TO_SPEECH');
const isChatLikeModel = computed(() => !isImageModel.value && !isVideoModel.value && !isSpeechModel.value);

const activeModelTypeLabel = computed(() => {
  if (isImageModel.value) return '图像生成';
  if (isVideoModel.value) return '视频生成';
  if (isSpeechModel.value) return '语音合成';
  return '对话模型';
});

const normalizeAgent = (agent) => ({
  ...agent,
  id: agent.id || agent.agentId,
  name: agent.name || agent.agentName || '未命名智能体',
  model: agent.model || agent.modelName || '',
  viewType: normalizeAgentViewType(agent),
  provider: agent.provider || agent.providerType || '',
  providerType: agent.providerType || agent.provider || '',
  status: agent.status || '',
  enabled: agent.enabled,
  ownerUserId: agent.ownerUserId,
  mcpExposed: !!agent.mcpExposed
});

const findAgentById = (agentId) => {
  const targetId = normalizeId(agentId);
  if (!targetId) return null;
  return agents.value.find((agent) => normalizeId(agent.id) === targetId) || null;
};

const getAgentColor = (name) => {
  const colors = ['#4F46E5', '#0EA5E9', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6'];
  const index = name ? name.charCodeAt(0) % colors.length : 0;
  return colors[index];
};

const getConfigStorageKey = (agentId) => `${CONFIG_STORAGE_PREFIX}${agentId}`;

const readSessionMessagesCache = () => {
  const raw = localStorage.getItem(SESSION_MESSAGES_CACHE_KEY);
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const writeSessionMessagesCache = (cache) => {
  localStorage.setItem(SESSION_MESSAGES_CACHE_KEY, JSON.stringify(cache));
};

const pruneSessionMessagesCache = (cache) => {
  const entries = Object.entries(cache || {})
    .sort((a, b) => (b?.[1]?.updatedAt || 0) - (a?.[1]?.updatedAt || 0));
  return Object.fromEntries(entries.slice(0, SESSION_MESSAGES_CACHE_LIMIT));
};

const getCachedSessionMessages = (sessionId) => {
  const target = normalizeId(sessionId);
  if (!target) return null;
  const cache = readSessionMessagesCache();
  const payload = cache[target];
  return Array.isArray(payload?.messages) ? payload.messages : null;
};

const setCachedSessionMessages = (sessionId, sessionMessages) => {
  const target = normalizeId(sessionId);
  if (!target) return;
  const cache = readSessionMessagesCache();
  cache[target] = {
    messages: Array.isArray(sessionMessages) ? sessionMessages : [],
    updatedAt: Date.now()
  };
  writeSessionMessagesCache(pruneSessionMessagesCache(cache));
};

const removeCachedSessionMessages = (sessionId) => {
  const target = normalizeId(sessionId);
  if (!target) return;
  const cache = readSessionMessagesCache();
  if (cache[target]) {
    delete cache[target];
    writeSessionMessagesCache(cache);
  }
};

const saveWorkspaceState = () => {
  const state = {
    currentAgentId: currentAgentId.value,
    currentSessionId: currentSessionId.value,
    sessionPaneCollapsed: sessionPaneCollapsed.value,
    sidebarTab: sidebarTab.value,
    activeConfigTab: activeConfigTab.value
  };
  localStorage.setItem(WORKSPACE_STATE_KEY, JSON.stringify(state));
};

const restoreWorkspaceState = () => {
  const raw = localStorage.getItem(WORKSPACE_STATE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

const restoreConfigForAgent = async (agentId) => {
  Object.assign(currentConfig, defaultConfig());

  if (!agentId) return;

  try {
    const remote = await getAgentToolBinding(agentId);
    const parsed = remote || {};
    Object.assign(currentConfig, defaultConfig(), parsed);
  } catch (error) {
    console.warn('Failed to load agent tool binding:', error);
  }

  try {
    const raw = localStorage.getItem(getConfigStorageKey(agentId));
    if (!raw) return;
    const parsed = JSON.parse(raw);
    Object.assign(currentConfig, defaultConfig(), parsed);
  } catch {
    // ignore local cache parse errors
  }
};

const parseAgentExtraParams = (raw) => {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

const syncRuntimeFormFromMetadata = (metadata = {}) => {
  const extra = parseAgentExtraParams(metadata.parameters);
  Object.assign(agentRuntimeForm, defaultRuntimeForm(), {
    temperature: metadata.temperature ?? 0.7,
    topP: metadata.topP ?? 0.7,
    maxTokens: metadata.maxTokens ?? 2000,
    systemPrompt: metadata.systemPrompt || '',
    toolCallingOverride: metadata.toolCallingOverride ?? null,
    imageSize: extra.imageSize || metadata.imageSize || '1328x1328',
    seed: extra.seed || metadata.seed || '',
    guidanceScale: extra.guidanceScale ?? metadata.guidanceScale ?? 7.5,
    inferenceSteps: extra.inferenceSteps ?? metadata.inferenceSteps ?? 20,
    negativePrompt: extra.negativePrompt || metadata.negativePrompt || '',
    voice: extra.voice || metadata.voice || '',
    speed: extra.speed ?? metadata.speed ?? 1.0,
    gain: extra.gain ?? metadata.gain ?? 0,
    videoSize: extra.videoSize || metadata.videoSize || '16:9',
    videoDuration: extra.videoDuration || metadata.videoDuration || '5'
  });
  promptMessages.value = [{ role: 'system', content: agentRuntimeForm.systemPrompt }];
};

const loadAgentRuntimeConfig = async (agentId) => {
  if (!agentId) {
    Object.assign(agentRuntimeForm, defaultRuntimeForm());
    Object.assign(agentAccessProfileForm, { endpointUrl: '', apiKey: '', maskedApiKey: '' });
    mcpExposureEnabled.value = false;
    selectedProviderKeyId.value = null;
    return;
  }
  try {
    const metadata = await getAgentMetadata(agentId);
    const accessProfile = await getAgentAccessProfile(agentId).catch(() => null);
    syncRuntimeFormFromMetadata(metadata || {});
    mcpExposureEnabled.value = !!metadata?.mcpExposed;
    Object.assign(agentAccessProfileForm, {
      endpointUrl: accessProfile?.endpointUrl || '',
      apiKey: '',
      maskedApiKey: accessProfile?.apiKey || ''
    });
    selectedProviderKeyId.value = null;
    if (metadata?.viewType || metadata?.modelName || metadata?.name) {
      const nextModel = metadata.modelName || selectedModelName.value || currentAgent.value?.model || '';
      currentAgent.value = {
        ...(currentAgent.value || {}),
        viewType: metadata.viewType ? normalizeAgentViewType(metadata) : currentAgent.value?.viewType,
        model: nextModel,
        name: metadata.name || metadata.agentName || currentAgent.value?.name || '',
        provider: metadata.provider || metadata.providerType || currentAgent.value?.provider || '',
        providerType: metadata.providerType || metadata.provider || currentAgent.value?.providerType || '',
        status: metadata.status || currentAgent.value?.status || '',
        enabled: metadata.enabled ?? currentAgent.value?.enabled,
        ownerUserId: metadata.ownerUserId ?? currentAgent.value?.ownerUserId,
        mcpExposed: !!metadata.mcpExposed
      };
      selectedModelName.value = nextModel;
      agents.value = agents.value.map((agent) => (
        agent.id === agentId
          ? {
              ...agent,
              model: nextModel,
              viewType: metadata.viewType ? normalizeAgentViewType(metadata) : agent.viewType,
              name: metadata.name || metadata.agentName || agent.name,
              provider: metadata.provider || metadata.providerType || agent.provider || '',
              providerType: metadata.providerType || metadata.provider || agent.providerType || '',
              status: metadata.status || agent.status || '',
              enabled: metadata.enabled ?? agent.enabled,
              ownerUserId: metadata.ownerUserId ?? agent.ownerUserId,
              mcpExposed: !!metadata.mcpExposed
            }
          : agent
      ));
    }
  } catch (error) {
    console.warn('Failed to load agent runtime config:', error);
  }
};

const buildRuntimePayloadByModelType = () => {
  const payload = {
    name: currentAgent.value?.name,
    model: selectedModelName.value || currentAgent.value?.model || '',
    endpointUrl: agentAccessProfileForm.endpointUrl || undefined
  };
  if (selectedProviderKeyId.value) {
    payload.providerKeyId = selectedProviderKeyId.value;
  }
  const inputApiKey = String(agentAccessProfileForm.apiKey || '').trim();
  if (inputApiKey && !inputApiKey.includes('****')) {
    payload.apiKey = inputApiKey;
  }
  if (isImageModel.value) {
    return {
      ...payload,
      imageSize: agentRuntimeForm.imageSize,
      seed: agentRuntimeForm.seed || null,
      guidanceScale: agentRuntimeForm.guidanceScale,
      inferenceSteps: agentRuntimeForm.inferenceSteps,
      negativePrompt: agentRuntimeForm.negativePrompt || null
    };
  }
  if (isVideoModel.value) {
    return {
      ...payload,
      videoSize: agentRuntimeForm.videoSize,
      videoDuration: agentRuntimeForm.videoDuration,
      seed: agentRuntimeForm.seed || null,
      negativePrompt: agentRuntimeForm.negativePrompt || null
    };
  }
  if (isSpeechModel.value) {
    return {
      ...payload,
      voice: agentRuntimeForm.voice || null,
      speed: agentRuntimeForm.speed,
      gain: agentRuntimeForm.gain
    };
  }
  return {
    ...payload,
    temperature: agentRuntimeForm.temperature,
    topP: agentRuntimeForm.topP,
    maxTokens: agentRuntimeForm.maxTokens,
    systemPrompt: agentRuntimeForm.systemPrompt,
    toolCallingOverride: agentRuntimeForm.toolCallingOverride
  };
};

const saveAgentRuntimeConfig = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    return;
  }
  savingModelParams.value = true;
  try {
    await updateAgent(currentAgentId.value, buildRuntimePayloadByModelType());
    await loadAgentRuntimeConfig(currentAgentId.value);
    ElMessage.success('模型参数已保存');
  } catch (error) {
    ElMessage.error('模型参数保存失败');
  } finally {
    savingModelParams.value = false;
  }
};

const saveMcpExposure = async (enabled) => {
  if (!currentAgentId.value || !canManageMcpExposure.value) return;
  savingMcpExposure.value = true;
  try {
    await updateAgent(currentAgentId.value, { mcpExposed: enabled });
    currentAgent.value = { ...(currentAgent.value || {}), mcpExposed: enabled };
    agents.value = agents.value.map((agent) => (
      agent.id === currentAgentId.value ? { ...agent, mcpExposed: enabled } : agent
    ));
    ElMessage.success('MCP 暴露设置已保存');
  } catch (error) {
    mcpExposureEnabled.value = !enabled;
    ElMessage.error('MCP 暴露设置保存失败');
  } finally {
    savingMcpExposure.value = false;
  }
};

const saveCurrentConfig = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    return;
  }
  if (activeConfigTab.value === 'model') {
    await saveAgentRuntimeConfig();
    return;
  }

  if (currentSessionId.value) {
    await saveSessionToolBinding(currentSessionId.value, {
      toolIds: currentConfig.toolIds || [],
      kbIds: currentConfig.kbIds || [],
      skillIds: currentConfig.skillIds || [],
      mcpIds: currentConfig.mcpIds || []
    });
  }
  localStorage.setItem(getConfigStorageKey(currentAgentId.value), JSON.stringify({ ...currentConfig }));
  ElMessage.success('当前配置已保存');
};

const formatSessionTime = (time) => {
  if (!time) return '刚刚';
  const value = new Date(time);
  if (Number.isNaN(value.getTime())) return '刚刚';
  return value.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const formatMessageTime = (time) => {
  if (!time) return '';
  const value = new Date(time);
  if (Number.isNaN(value.getTime())) return '';
  return value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  });
};

const getAssistantRuntimeMeta = (msg = {}) => {
  const chips = [];
  const prompt = Number(msg.promptTokens || 0);
  const completion = Number(msg.completionTokens || 0);
  const total = prompt + completion;
  if (total > 0) {
    chips.push(`总Token ${total}`);
  }

  const traceDuration = Array.isArray(msg.toolTraces)
    ? msg.toolTraces.reduce((sum, trace) => sum + Number(trace?.durationMs || 0), 0)
    : 0;
  const responseMs = Number(msg.responseMs || 0);
  const duration = responseMs > 0 ? responseMs : traceDuration;
  if (duration > 0) {
    chips.push(`耗时 ${duration < 1000 ? `${duration}ms` : `${(duration / 1000).toFixed(2)}s`}`);
  }

  if (Array.isArray(msg.toolTraces) && msg.toolTraces.length > 0) {
    chips.push(`步骤 ${msg.toolTraces.length}`);
  }
  return chips;
};

const formatMcpStatus = (status) => {
  if (!status) return '未知状态';
  const statusMap = {
    CONNECTED: '已连接',
    DISCONNECTED: '未连接',
    ERROR: '异常',
    TESTING: '测试中'
  };
  return statusMap[status] || status;
};

const getKnowledgeBaseName = (kbId) => {
  const targetKbId = normalizeId(kbId);
  return knowledgeBases.value.find((kb) => normalizeId(kb.id) === targetKbId)?.name || targetKbId;
};

const getSkillName = (skillId) => {
  const skill = skills.value.find((item) => item.id === skillId);
  return skill?.skillName || skill?.name || String(skillId);
};

const promptMessages = ref([{ role: 'system', content: '' }]);

const syncPromptMessagesToForm = () => {
  const sys = promptMessages.value.find(m => m.role === 'system');
  agentRuntimeForm.systemPrompt = sys ? sys.content : '';
};

const onPromptMessagesChange = () => syncPromptMessagesToForm();

const addPromptMessage = (role) => {
  promptMessages.value.push({ role, content: '' });
};

const removePromptMessage = (idx) => {
  promptMessages.value.splice(idx, 1);
  syncPromptMessagesToForm();
};

const toolsCardExpanded = ref(new Set());
const toggleToolsCard = (key) => {
  const s = new Set(toolsCardExpanded.value);
  s.has(key) ? s.delete(key) : s.add(key);
  toolsCardExpanded.value = s;
};

const builtinTools = computed(() => {
  return toolCatalog.value
    .filter((item) => item.category === 'BUILTIN_KB' || item.category === 'WORKFLOW_TOOL')
    .map((item) => ({
      name: item.toolId,
      description: item.displayName || item.toolId,
      active: (currentConfig.toolIds || []).length === 0
        ? item.enabled !== false
        : (currentConfig.toolIds || []).includes(item.toolId) && item.enabled !== false
    }));
});

const getMcpServiceName = (serviceId) => {
  return mcpServices.value.find((item) => item.id === serviceId)?.name || String(serviceId);
};

const generateRandomSeed = () => {
  agentRuntimeForm.seed = Math.floor(Math.random() * 10000000000).toString();
};

const applyPrompt = (prompt) => {
  inputMessage.value = prompt;
};

const applyRoutePromptIfExists = async () => {
  const prompt = typeof route.query?.prompt === 'string' ? route.query.prompt.trim() : '';
  if (!prompt) return;
  inputMessage.value = prompt;
  await nextTick();
  scrollToBottom();
  // 消费一次后移除 query，避免刷新重复填充
  const nextQuery = { ...route.query };
  delete nextQuery.prompt;
  router.replace({
    path: route.path,
    query: nextQuery
  });
};

const updateSessionTitleLocally = (content) => {
  const target = sessions.value.find((item) => item.id === currentSessionId.value);
  if (!target || target.title !== '新会话' || !currentConfig.autoRenameSession) return;
  target.title = content.slice(0, 18) || '新会话';
};

const normalizeSession = (session) => ({
  id: session.id,
  title: session.title || '新会话',
  createdAt: session.createdAt,
  agentId: session.agentId
});

const inferMessageDataType = (message = {}) => {
  const explicitType = String(message.dataType || message.type || '').toUpperCase();
  if (explicitType) return explicitType;
  const content = message?.content;
  if (content && typeof content === 'object') {
    if (content.image_url || content.url?.match(/\.(png|jpg|jpeg|webp|gif)(\?|$)/i)) return 'IMAGE';
    if (content.audio_url) return 'AUDIO';
    if (content.video_url) return 'VIDEO';
  }
  return 'TEXT';
};

const normalizeWorkspaceMessage = (message = {}) => {
  const dataType = inferMessageDataType(message);
  const content = message?.content;
  const isObjectContent = content && typeof content === 'object';
  const imageUrl = message?.imageUrl || message?.image_url || (isObjectContent ? (content.image_url || content.url || content.images?.[0]?.url || '') : '');
  const audioUrl = message?.audioUrl || message?.audio_url || (isObjectContent ? (content.audio_url || content.url || '') : '');
  const videoUrl = message?.videoUrl || message?.video_url || (isObjectContent ? (content.video_url || content.url || '') : '');

  let normalizedContent = content;
  if (isObjectContent) {
    normalizedContent = content.text || content.answer || content.prompt || '';
  }
  if (normalizedContent == null) normalizedContent = '';
  if (typeof normalizedContent !== 'string') {
    normalizedContent = JSON.stringify(normalizedContent, null, 2);
  }

  return {
    ...message,
    dataType,
    imageUrl,
    audioUrl,
    videoUrl,
    content: normalizedContent
  };
};

const normalizeId = (value) => (value == null ? '' : String(value));

const clipText = (value = '', maxLength = 1200) => {
  const text = String(value || '');
  if (text.length <= maxLength) return text;
  return `${text.slice(0, maxLength)}...`;
};

const compactCollaborationMetaForHistory = (meta = {}) => {
  if (!meta || typeof meta !== 'object') return null;
  return {
    status: meta.status || '',
    trigger: meta.trigger || '',
    participants: Array.isArray(meta.participants) ? meta.participants.slice(0, 4).map((participant) => ({
      id: participant.id || '',
      name: participant.name || '',
      base_name: participant.base_name || participant.baseName || participant.name || '',
      role: participant.role || '',
      ephemeral: Boolean(participant.ephemeral)
    })) : [],
    ephemeral: Boolean(meta.ephemeral),
    ephemeralAgentSpecs: Array.isArray(meta.ephemeralAgentSpecs)
      ? meta.ephemeralAgentSpecs.slice(0, 4).map((agent) => ({
          base_name: agent.base_name || agent.baseName || agent.name || '',
          name: agent.name || '',
          model: agent.model || '',
          description: clipText(agent.description || '', 360),
          system_prompt: clipText(agent.system_prompt || agent.systemPrompt || '', 700),
          role: agent.role || 'SPECIALIST',
          max_tokens: Number(agent.max_tokens || agent.maxTokens || 900),
          temperature: agent.temperature ?? 0.45,
          planning_slot: Boolean(agent.planning_slot || agent.planningSlot),
          ephemeral: true
        }))
      : [],
    workflowId: meta.workflowId || '',
    workflowName: meta.workflowName || '',
    planner: meta.planner ? clipText(formatCollaborationArtifact(meta.planner), 1200) : null,
    taskReports: Array.isArray(meta.taskReports)
      ? meta.taskReports.slice(0, 4).map((report) => clipText(formatCollaborationArtifact(report), 1200))
      : [],
    trace: Array.isArray(meta.trace)
      ? meta.trace.slice(-12).map((item) => ({
          event_type: item.event_type || item.type || '',
          title: item.title || item.event || '',
          detail: clipText(item.detail || item.message || '', 280),
          status: item.status || ''
        }))
      : [],
    conversationId: meta.conversationId || '',
    error: meta.error ? clipText(meta.error, 800) : ''
  };
};

const toPersistableMessage = (message = {}) => {
  const normalized = normalizeWorkspaceMessage(message);
  const persistable = {
    ...normalized,
    content: clipText(typeof normalized.content === 'string' ? normalized.content : String(normalized.content || ''), 12000)
  };
  if (persistable.collaborationMeta) {
    persistable.collaborationMeta = compactCollaborationMetaForHistory(persistable.collaborationMeta);
  }
  delete persistable.graph;
  return persistable;
};

const persistCurrentSessionMessages = async () => {
  if (!currentSessionId.value) return;
  try {
    await saveChatSessionMessages(
      currentSessionId.value,
      messages.value.map((message) => toPersistableMessage(message))
    );
  } catch (error) {
    console.warn('Failed to persist chat session messages:', error);
  }
};

const normalizeKbDocFilters = (filters = {}) => {
  const result = {};
  Object.entries(filters || {}).forEach(([kbId, docIds]) => {
    result[normalizeId(kbId)] = Array.isArray(docIds) ? docIds.map(normalizeId) : [];
  });
  return result;
};

const isKbAttached = (kbId) => attachedKbIds.value.includes(normalizeId(kbId));

const loadAgents = async () => {
  agentsLoading.value = true;
  try {
    const res = await listAgents({ page: 1, size: 100 });
    const list = Array.isArray(res?.data?.records)
      ? res.data.records
      : Array.isArray(res?.data)
        ? res.data
        : Array.isArray(res)
          ? res
          : [];

    agents.value = list.map(normalizeAgent).filter((agent) => agent.id);

    const presetId = props.presetAgentId ? String(props.presetAgentId) : '';
    const routeAgentId = typeof route.query?.agentId === 'string' ? route.query.agentId : '';
    const preferredAgentId = routeAgentId || presetId;
    if (preferredAgentId) {
      currentAgentId.value = preferredAgentId;
      currentAgent.value = findAgentById(preferredAgentId);
      if (!currentAgent.value && agents.value.length) {
        currentAgent.value = agents.value[0];
        currentAgentId.value = normalizeId(currentAgent.value.id);
      }
      selectedModelName.value = currentAgent.value?.model || '';
      return;
    }

    if (!currentAgentId.value && agents.value.length) {
      currentAgentId.value = normalizeId(agents.value[0].id);
      currentAgent.value = agents.value[0];
    } else {
      currentAgent.value = findAgentById(currentAgentId.value);
    }
    selectedModelName.value = currentAgent.value?.model || '';
  } finally {
    agentsLoading.value = false;
  }
};

const loadKnowledgeBases = async () => {
  const res = await listKnowledgeBases({ page: 1, size: 100 });
  const list = Array.isArray(res?.data?.records)
    ? res.data.records
    : Array.isArray(res?.data)
      ? res.data
      : Array.isArray(res)
        ? res
        : [];

  knowledgeBases.value = list.map((kb) => ({
    ...kb,
    id: normalizeId(kb.id || kb.kbId),
    documentCount: Number(
      kb.documentCount
        ?? kb.docCount
        ?? kb.stats?.documentCount
        ?? kb.stats?.docCount
        ?? 0
    )
  })).filter((kb) => kb.id);
};

const loadSkills = async () => {
  try {
    const res = await getSkillList();
    skills.value = Array.isArray(res) ? res : res?.data || [];
  } catch (error) {
    console.warn('Failed to load skills:', error);
    skills.value = [];
  }
};

const loadProviderKeys = async () => {
  try {
    const res = await getExternalKeys();
    const list = Array.isArray(res) ? res : res?.data || [];
    providerKeyOptions.value = list
      .filter((item) => item?.id && item?.enabled !== false)
      .map((item) => ({
        id: item.id,
        provider: String(item.provider || '').toLowerCase(),
        label: `${item.name || '未命名'} · ${item.provider || 'provider'} · ${item.apiKey || '****'}`
      }));
  } catch (error) {
    console.warn('Failed to load provider keys:', error);
    providerKeyOptions.value = [];
  }
};

const loadMcpServicesSafe = async () => {
  try {
    const res = await getMcpServices();
    const list = Array.isArray(res) ? res : res?.data || [];
    mcpServices.value = list.filter((item) => item?.id);
  } catch (error) {
    console.warn('Failed to load MCP services:', error);
    mcpServices.value = [];
  }
};

const loadToolCatalogSafe = async () => {
  try {
    const res = await getToolCatalog({ includeDisabled: true });
    toolCatalog.value = Array.isArray(res) ? res : res?.data || [];
  } catch (error) {
    console.warn('Failed to load tool catalog:', error);
    toolCatalog.value = [];
  }
};

const loadModelCatalog = async () => {
  try {
    const res = await getModelList();
    const list = Array.isArray(res)
      ? res
      : Array.isArray(res?.data)
        ? res.data
        : [];
    modelCatalog.value = list.map(normalizeModelRecord).filter((item) => item.value);
  } catch (error) {
    console.warn('Failed to load model catalog:', error);
    modelCatalog.value = [];
  }
};

const loadCollaborationWorkflows = async () => {
  collaborationWorkflowsLoading.value = true;
  try {
    const list = await fetchPlaygroundWorkflows();
    collaborationWorkflows.value = Array.isArray(list) ? list : [];
  } catch (error) {
    console.warn('Failed to load collaboration workflows:', error);
    collaborationWorkflows.value = [];
  } finally {
    collaborationWorkflowsLoading.value = false;
  }
};

const getChatCapableAgents = () => agents.value.filter((agent) => {
  if (!agent?.id) return false;
  if (agent.enabled === false) return false;
  const type = normalizeAgentViewType(agent);
  return !['TEXT_TO_IMAGE', 'IMAGE_TO_IMAGE', 'TEXT_TO_VIDEO', 'TEXT_TO_SPEECH', 'SPEECH_TO_TEXT'].includes(type);
});

const parseMentionedAgents = (text) => {
  const source = String(text || '');
  if (!source.includes('@')) return [];
  const candidates = getChatCapableAgents()
    .filter((agent) => agent.name || agent.id)
    .sort((a, b) => String(b.name || '').length - String(a.name || '').length);
  const found = [];
  const used = new Set();
  candidates.forEach((agent) => {
    const names = [agent.name, agent.id].filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
    const matched = names.some((name) => source.includes(`@${name}`));
    if (matched && !used.has(normalizeId(agent.id))) {
      used.add(normalizeId(agent.id));
      found.push(agent);
    }
  });
  return found;
};

const hasCollaborationIntent = (text) => {
  const source = String(text || '').trim();
  if (!source) return false;
  const retrospectiveOnly = /(刚才|上面|前面|之前|上一轮|前文).{0,8}(讨论|协作|分工)/.test(source)
    && !/(让|请|帮|安排|组织|启动|开启|调用|用).{0,12}(几个智能体|多个智能体|多智能体|分工|协作|讨论|评审|复核|交叉检查)/.test(source);
  if (retrospectiveOnly) return false;
  return /(让|请|帮|安排|组织|启动|开启|调用|用).{0,12}(几个智能体|多个智能体|多智能体|分工|协作|讨论|评审|复核|交叉检查)/.test(source)
    || /(几个智能体|多个智能体|多智能体).{0,12}(分工|讨论|协作|评审|复核|分析)/.test(source)
    || /(多来几个|多找几个|更多智能体|多几个专家|多个专家)/.test(source)
    || /(分别分析|一起分析|一起看|互相评审|方案对比|交叉检查)/.test(source);
};

const wantsPreviousEphemeralAgents = (text) => /(刚才|上面|前面|之前|上一轮|上轮|上次|前一次|同一组|那两个|这些|那几个|原来的|继续).{0,12}(专家|智能体|临时智能体|角色|他们|它们|协作|复核|评审|分析)/.test(String(text || ''));

const inferEphemeralAgentCount = (text = '', fallback = 2) => {
  const source = String(text || '');
  const digitMatch = source.match(/(?:让|请|用|找|调用|安排)?\s*(\d)\s*(?:个|位|名)?(?:临时)?(?:智能体|专家|角色)/);
  if (digitMatch) {
    return Math.max(2, Math.min(4, Number(digitMatch[1])));
  }
  const cnMap = { 两: 2, 二: 2, 三: 3, 四: 4 };
  const cnMatch = source.match(/(?:让|请|用|找|调用|安排)?\s*([两二三四])\s*(?:个|位|名)?(?:临时)?(?:智能体|专家|角色)/);
  if (cnMatch) {
    return Math.max(2, Math.min(4, cnMap[cnMatch[1]] || fallback));
  }
  if (/(多来几个|多找几个|更多智能体|多几个专家|多个专家)/.test(source)) {
    return 3;
  }
  return Math.max(2, Math.min(4, fallback));
};

const hashText = (value = '') => {
  const source = String(value || '');
  let hash = 0;
  for (let index = 0; index < source.length; index += 1) {
    hash = ((hash << 5) - hash + source.charCodeAt(index)) >>> 0;
  }
  return hash.toString(36).slice(0, 8);
};

const isChatModelRecord = (model = {}) => {
  const type = normalizeAgentViewType(model);
  return !['TEXT_TO_IMAGE', 'IMAGE_TO_IMAGE', 'TEXT_TO_VIDEO', 'TEXT_TO_SPEECH', 'SPEECH_TO_TEXT'].includes(type);
};

const selectEphemeralModel = () => {
  const currentModel = normalizeProviderModelName(selectedModelName.value || currentAgent.value?.model || '');
  if (currentModel) return currentModel;
  const preferredProvider = String(currentModelInfo.value?.provider || currentAgent.value?.provider || currentAgent.value?.providerType || '').trim().toLowerCase();
  const models = modelCatalog.value.filter((item) => item.value && isChatModelRecord(item));
  const sameProvider = models.find((item) => preferredProvider && String(item.provider || '').trim().toLowerCase() === preferredProvider);
  return normalizeProviderModelName(sameProvider?.value || models[0]?.value || currentModel || 'default');
};

const stripRunLabel = (name = '') => String(name || '').replace(/\s+[A-Z0-9]{3}$/i, '').trim();

const clampTokenValue = (value, fallback, min = 256, max = 16000) => {
  const parsed = Number(value);
  const safeValue = Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  return Math.max(min, Math.min(max, Math.round(safeValue)));
};

const getCollaborationAgentMaxTokens = () => clampTokenValue(agentRuntimeForm.maxTokens, 2400, 1200, 8000);

const getCollaborationMergeMaxTokens = () => clampTokenValue(
  Math.max(Number(agentRuntimeForm.maxTokens || 0), 6000),
  6000,
  2400,
  12000
);

const buildEphemeralAgents = (taskText = '', count = 2, previousSpecs = null) => {
  const model = selectEphemeralModel();
  const baseHash = hashText(`${taskText}:${model}:${Date.now()}`);
  const previous = Array.isArray(previousSpecs) ? previousSpecs : [];
  const defaultMaxTokens = getCollaborationAgentMaxTokens();
  return Array.from({ length: count }).map((_, index) => {
    const previousSpec = previous[index] || null;
    const previousName = previousSpec ? stripRunLabel(previousSpec.base_name || previousSpec.baseName || previousSpec.name || '') : '';
    const planningHint = previousName
      ? `上一轮相同席位角色是“${previousName}”。请先判断当前任务是否仍需要保留这个角色，也可以调整成更合适的协作角色。`
      : '请由 Planner 根据当前任务和会话上下文决定这个席位的角色、名称、职责和提示词。';
    return {
      id: `ephemeral:${baseHash}:${index + 1}`,
      name: `协作角色 ${index + 1}`,
      base_name: `协作角色 ${index + 1}`,
      description: planningHint,
      model: normalizeProviderModelName(previousSpec?.model || model),
      system_prompt: `你是一个待规划的临时协作席位。${planningHint} 在 Planner 完成角色规划前，不要假设自己一定是专家或固定职业角色。`,
      role: 'ORCHESTRATED_SLOT',
      max_tokens: Math.max(Number(previousSpec?.max_tokens || previousSpec?.maxTokens || 0), defaultMaxTokens),
      temperature: previousSpec?.temperature ?? 0.45,
      planning_slot: true,
      ephemeral: true
    };
  });
};

const inferEphemeralSpecFromParticipant = (participant = {}) => {
  const baseName = stripRunLabel(participant.base_name || participant.baseName || participant.name || '临时角色');
  let role = 'SPECIALIST';
  let systemPrompt = '你是临时协作角色。延续上一轮协作职责，基于当前请求和会话上下文给出清晰、可执行的结果。';
  if (/评审|复核|质量|审查/.test(baseName)) {
    role = 'REVIEWER';
    systemPrompt = '你是方案评审者。延续上一轮协作角色职责，从目标一致性、可行性、风险、遗漏和改进建议评审当前请求。';
  } else if (/拆解|实施|执行/.test(baseName)) {
    role = 'SPECIALIST';
    systemPrompt = '你是实施拆解者。延续上一轮协作角色职责，把当前请求落到执行步骤、依赖、优先级和边界条件。';
  } else if (/分析/.test(baseName)) {
    role = 'SPECIALIST';
    systemPrompt = '你是分析者。延续上一轮协作角色职责，拆解问题、识别关键变量，并给出结构化判断。';
  }
  return {
    base_name: baseName,
    name: baseName,
    description: `上一轮临时协作角色：${baseName}`,
    model: selectEphemeralModel(),
    system_prompt: systemPrompt,
    role,
    max_tokens: getCollaborationAgentMaxTokens(),
    temperature: role === 'REVIEWER' ? 0.35 : 0.45,
    ephemeral: true
  };
};

const getLastEphemeralAgentSpecs = () => {
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    const meta = messages.value[index]?.collaborationMeta;
    if (!meta?.ephemeral) continue;
    if (Array.isArray(meta.ephemeralAgentSpecs) && meta.ephemeralAgentSpecs.length >= 2) {
      return meta.ephemeralAgentSpecs;
    }
    if (Array.isArray(meta.participants) && meta.participants.filter((item) => item?.ephemeral).length >= 2) {
      return meta.participants
        .filter((item) => item?.ephemeral)
        .slice(0, 4)
        .map(inferEphemeralSpecFromParticipant);
    }
  }
  return [];
};

const buildCollaborationContextMessages = (currentContent = '') => {
  const current = String(currentContent || '').trim();
  const limitChars = 1600;
  let usedChars = 0;
  const context = [];
  const priorMessages = messages.value
    .slice(0, -1)
    .filter((message) => ['user', 'assistant'].includes(message?.role));

  for (let index = priorMessages.length - 1; index >= 0 && context.length < 4; index -= 1) {
    const message = priorMessages[index];
    const role = message.role === 'user' ? 'user' : 'assistant';
    let text = getMessageText(message).trim();
    if (!text || text === current) continue;
    if (message.collaborationMeta) {
      text = `协作最终回复：${text}`;
    }
    const clipped = clipText(text, role === 'user' ? 520 : 360);
    if (usedChars + clipped.length > limitChars) break;
    usedChars += clipped.length;
    context.unshift({
      role,
      content: clipped,
      createdAt: message.createdAt || ''
    });
  }
  return context;
};

const hasConversationReference = (content = '') => {
  const text = String(content || '').trim();
  if (!text) return false;
  return /(刚才|上面|前面|之前|上一轮|上轮|上次|前一次|上述|基于刚才|基于上面|继续|接着|沿用|复核刚才|刚刚|那几个|那两个|这些|这个结论|这个方案|该方案|最终裁剪方案|开发计划)/.test(text);
};

const buildSingleAgentContextMessages = (currentContent = '') => {
  if (!hasConversationReference(currentContent) || selectedUploadFileId.value) {
    return [];
  }
  return buildCollaborationContextMessages(currentContent);
};

const resolveCollaborationDecision = (content) => {
  if (!isChatLikeModel.value || selectedUploadFileId.value) {
    return { enabled: false, participants: [], trigger: '' };
  }
  const mentioned = parseMentionedAgents(content);
  if (mentioned.length >= 2) {
    return { enabled: true, participants: mentioned.slice(0, 4), ephemeralAgents: [], trigger: '@' };
  }
  if (collaborationRequested.value || hasCollaborationIntent(content)) {
    const requestedCount = inferEphemeralAgentCount(content, 2);
    const previousSpecs = wantsPreviousEphemeralAgents(content) ? getLastEphemeralAgentSpecs() : [];
    const ephemeralAgents = previousSpecs.length >= 2
      ? buildEphemeralAgents(content, Math.min(Math.max(previousSpecs.length, requestedCount), 4), previousSpecs)
      : buildEphemeralAgents(content, requestedCount);
    if (ephemeralAgents.length >= 2) {
      return {
        enabled: true,
        participants: ephemeralAgents,
        ephemeralAgents,
        trigger: previousSpecs.length >= 2 ? 'reuse' : (collaborationRequested.value ? 'manual' : 'intent')
      };
    }
  }
  return { enabled: false, participants: [], ephemeralAgents: [], trigger: '' };
};

const getWorkflowSignature = (agentIds = []) => agentIds.map(normalizeId).filter(Boolean).sort().join('|');

const getShortWorkflowSignature = (agentIds = []) => {
  const signature = getWorkflowSignature(agentIds);
  let hash = 0;
  for (let index = 0; index < signature.length; index += 1) {
    hash = ((hash << 5) - hash + signature.charCodeAt(index)) >>> 0;
  }
  return hash.toString(36).slice(0, 8);
};

const findChatWorkflow = (agentIds = []) => {
  const signature = getWorkflowSignature(agentIds);
  return collaborationWorkflows.value.find((workflow) => (
    String(workflow?.name || '').startsWith('[Chat]') &&
    workflow?.type === 'planner_executor' &&
    getWorkflowSignature(workflow.specialist_agent_ids || []) === signature
  )) || null;
};

const ensureChatWorkflow = async (participants) => {
  const participantIds = participants.map((agent) => normalizeId(agent.id)).filter(Boolean);
  if (participantIds.length < 2) {
    throw new Error('协作至少需要两个可用智能体');
  }
  if (!collaborationWorkflows.value.length && !collaborationWorkflowsLoading.value) {
    await loadCollaborationWorkflows();
  }
  const existing = findChatWorkflow(participantIds);
  if (existing) return existing;

  const workflowName = `[Chat] 协作 ${getShortWorkflowSignature(participantIds)}`;
  const created = await createWorkflow({
    name: workflowName,
    type: 'planner_executor',
    specialist_agent_ids: participantIds,
    finalizer_enabled: true,
    router_prompt: 'Chat-triggered collaboration workflow. Keep the plan compact, assign essential subtasks to the selected agents, and synthesize a complete final answer following the user requested length.',
    execution_mode: 'DYNAMIC',
    dag_subtasks: [],
    agent_max_tokens: getCollaborationAgentMaxTokens()
  });
  const refreshed = await fetchPlaygroundWorkflows().catch(() => null);
  if (Array.isArray(refreshed)) {
    collaborationWorkflows.value = refreshed;
  } else if (created?.id) {
    collaborationWorkflows.value = [...collaborationWorkflows.value, created];
  }
  return collaborationWorkflows.value.find((workflow) => workflow.id === created?.id) || findChatWorkflow(participantIds) || created;
};

const ensureEphemeralChatWorkflow = async () => {
  const current = currentAgent.value || getChatCapableAgents()[0];
  const fallback = getChatCapableAgents().find((agent) => normalizeId(agent.id) !== normalizeId(current?.id));
  const workflowParticipants = [current, fallback].filter(Boolean);
  if (workflowParticipants.length >= 2) {
    return ensureChatWorkflow(workflowParticipants);
  }
  throw new Error('临时协作至少需要一个当前智能体和一个可用工作流容器智能体');
};

const normalizeRunResult = (result = {}, fallbackTrace = []) => {
  const artifacts = result.artifacts || {};
  return {
    content: result.assistant_message || result.content || result.answer || '协作已完成，但未返回最终正文。',
    ephemeralAgents: Array.isArray(artifacts.ephemeral_agents)
      ? artifacts.ephemeral_agents
      : Array.isArray(result.ephemeral_agents)
        ? result.ephemeral_agents
        : [],
    planner: artifacts.planner || result.planner || null,
    taskReports: Array.isArray(artifacts.task_reports)
      ? artifacts.task_reports
      : Array.isArray(result.taskReports)
        ? result.taskReports
        : [],
    trace: Array.isArray(result.trace) ? result.trace : fallbackTrace,
    graph: result.graph || artifacts.graph || null,
    conversationId: result.conversation_id || '',
    workflowId: result.workflow_id || ''
  };
};

const buildCollaborationGraphFromPlanner = (planner = null) => {
  const subtasks = Array.isArray(planner?.subtasks) ? planner.subtasks : [];
  if (!subtasks.length) return null;
  const nodes = [
    { id: 'start', label: 'Start', kind: 'start' },
    { id: 'planner', label: 'Planner', kind: 'logic' }
  ];
  const edges = [{ source: 'start', target: 'planner' }];
  const taskIds = new Set();
  subtasks.forEach((task, index) => {
    const id = normalizeId(task.id) || `task_${index + 1}`;
    taskIds.add(id);
    const label = task.preferred_agent_name || task.logical_role || `Task ${index + 1}`;
    nodes.push({ id, label, kind: 'agent' });
  });
  subtasks.forEach((task, index) => {
    const id = normalizeId(task.id) || `task_${index + 1}`;
    const depends = Array.isArray(task.depends_on) ? task.depends_on.map(normalizeId).filter((dep) => taskIds.has(dep)) : [];
    if (depends.length) {
      depends.forEach((dep) => edges.push({ source: dep, target: id }));
    } else {
      edges.push({ source: 'planner', target: id });
    }
  });
  const dependedOn = new Set();
  subtasks.forEach((task) => {
    (Array.isArray(task.depends_on) ? task.depends_on : []).forEach((dep) => {
      const id = normalizeId(dep);
      if (taskIds.has(id)) dependedOn.add(id);
    });
  });
  const leaves = [...taskIds].filter((id) => !dependedOn.has(id));
  if (taskIds.size > 1) {
    nodes.push({ id: 'merge', label: 'Merge', kind: 'merge' });
    leaves.forEach((id) => edges.push({ source: id, target: 'merge' }));
    nodes.push({ id: 'end', label: 'End', kind: 'end' });
    edges.push({ source: 'merge', target: 'end' });
  } else {
    nodes.push({ id: 'end', label: 'End', kind: 'end' });
    leaves.forEach((id) => edges.push({ source: id, target: 'end' }));
  }
  return { nodes, edges };
};

const applyCollaborationResult = (assistantMessage, result, fallbackTrace = [], workflowId = '') => {
  const normalized = normalizeRunResult(result, fallbackTrace);
  const plannedEphemeralAgents = normalized.ephemeralAgents.map((agent) => ({
    id: agent.id,
    name: agent.name,
    base_name: agent.base_name || agent.baseName || stripRunLabel(agent.name),
    role: agent.role || 'SPECIALIST',
    model: agent.model,
    description: agent.description || '',
    system_prompt: agent.system_prompt || agent.systemPrompt || '',
    max_tokens: agent.max_tokens || agent.maxTokens || 900,
    temperature: agent.temperature ?? 0.45,
    ephemeral: true
  }));
  assistantMessage.content = normalized.content;
  if (!assistantMessage.collaborationMeta) {
    assistantMessage.collaborationMeta = {};
  }
  Object.assign(assistantMessage.collaborationMeta, {
    status: 'completed',
    ...(plannedEphemeralAgents.length
      ? {
          participants: plannedEphemeralAgents.map((agent) => ({
            id: agent.id,
            name: agent.name,
            base_name: agent.base_name,
            role: agent.role,
            ephemeral: true
          })),
          ephemeralAgentSpecs: plannedEphemeralAgents
        }
      : {}),
    planner: normalized.planner,
    taskReports: normalized.taskReports,
    trace: normalized.trace,
    graph: buildCollaborationGraphFromPlanner(normalized.planner)
      || normalized.graph
      || assistantMessage.collaborationMeta?.graph
      || buildCollaborationFallbackGraph(assistantMessage.collaborationMeta),
    conversationId: normalized.conversationId,
    workflowId: normalized.workflowId || workflowId
  });
  runtimeStatusLevel.value = 'idle';
  runtimeStatusHint.value = '上一轮协作已完成';
  collaborationRequested.value = false;
};

const sendCollaborationMessage = async (content, decision) => {
  const participants = decision.participants || [];
  const ephemeralAgents = decision.ephemeralAgents || [];
  const assistantMessage = normalizeWorkspaceMessage({
    role: 'assistant',
    content: ephemeralAgents.length ? '正在规划协作角色...' : '正在协作处理...',
    retrievedChunks: [],
    toolTraces: [],
    model: 'Collaboration',
    provider: 'Playground',
    createdAt: new Date().toISOString(),
    collaborationMeta: {
      status: 'running',
      trigger: decision.trigger,
      participants: participants.map((agent) => ({
        id: agent.id,
        name: agent.planning_slot ? '规划中' : agent.name,
        ephemeral: Boolean(agent.ephemeral),
        planning: Boolean(agent.planning_slot)
      })),
      ephemeral: ephemeralAgents.length > 0,
      ephemeralAgentSpecs: ephemeralAgents.map((agent) => ({
        base_name: agent.base_name || stripRunLabel(agent.name),
        name: agent.name,
        model: agent.model,
        description: agent.description,
        system_prompt: agent.system_prompt,
        role: agent.role,
        max_tokens: agent.max_tokens,
        temperature: agent.temperature,
        planning_slot: Boolean(agent.planning_slot),
        ephemeral: true
      })),
      workflowId: '',
      workflowName: '',
      graph: null,
      planner: null,
      taskReports: [],
      trace: [],
      conversationId: ''
    }
  });
  messages.value.push(assistantMessage);
  scrollToBottom();
  await persistCurrentSessionMessages();

  const updateMeta = (patch) => {
    if (!assistantMessage.collaborationMeta) {
      assistantMessage.collaborationMeta = {};
    }
    Object.assign(assistantMessage.collaborationMeta, patch);
  };

  try {
    loadingHint.value = '协作中...';
    runtimeStatusHint.value = '正在准备协作工作流';
    const workflow = ephemeralAgents.length ? await ensureEphemeralChatWorkflow() : await ensureChatWorkflow(participants);
    updateMeta({
      workflowId: workflow?.id || '',
      workflowName: workflow?.name || '',
      graph: workflow?.graph || buildCollaborationFallbackGraph(assistantMessage.collaborationMeta)
    });

    const runPayload = {
      workflow_id: workflow.id,
      user_input: content,
      context_messages: buildCollaborationContextMessages(content),
      agent_max_tokens: getCollaborationAgentMaxTokens(),
      merge_max_tokens: getCollaborationMergeMaxTokens(),
      ...(ephemeralAgents.length ? { ephemeral_agents: ephemeralAgents, ephemeral_only: true } : {})
    };
    let streamResult = null;
    let streamError = '';
    let transportFailed = false;
    const streamedTrace = [];

    try {
      await runWorkflowStream(runPayload, {
        onTrace: (event) => {
          streamedTrace.push(event);
          const roles = Array.isArray(event?.roles)
            ? event.roles
            : Array.isArray(event?.payload?.roles)
              ? event.payload.roles
              : [];
          const roleParticipants = roles
            .map((role) => ({
              id: role.id || role.agent_id || '',
              name: role.name || role.agent_name || '',
              role: role.role || '',
              ephemeral: true
            }))
            .filter((role) => role.id && role.name);
          const roleGraphMeta = roleParticipants.length
            ? { ...(assistantMessage.collaborationMeta || {}), participants: roleParticipants }
            : null;
          const traceSubtasks = Array.isArray(event?.subtasks)
            ? event.subtasks
            : Array.isArray(event?.payload?.subtasks)
              ? event.payload.subtasks
              : [];
          const dynamicPlanner = traceSubtasks.length
            ? {
                subtasks: traceSubtasks.map((task, index) => {
                  const preferredId = task.preferred_agent_id || task.preferredAgentId || '';
                  const participant = roleParticipants.find((item) => normalizeId(item.id) === normalizeId(preferredId))
                    || (assistantMessage.collaborationMeta?.participants || []).find((item) => normalizeId(item.id) === normalizeId(preferredId));
                  return {
                    id: task.id || `task_${index + 1}`,
                    description: task.description || '',
                    depends_on: Array.isArray(task.depends_on) ? task.depends_on : [],
                    logical_role: task.logical_role || task.logicalRole || '',
                    preferred_agent_id: preferredId,
                    preferred_agent_name: participant?.name || task.preferred_agent_name || task.preferredAgentName || ''
                  };
                })
              }
            : null;
          updateMeta({
            trace: [...streamedTrace],
            ...(roleParticipants.length
              ? {
                  participants: roleParticipants,
                  graph: buildCollaborationFallbackGraph(roleGraphMeta)
                }
              : {}),
            ...(dynamicPlanner
              ? {
                  planner: {
                    ...(assistantMessage.collaborationMeta?.planner || {}),
                    ...dynamicPlanner
                  },
                  graph: buildCollaborationGraphFromPlanner(dynamicPlanner)
                }
              : {})
          });
          const detail = String(event?.detail || event?.message || '').trim();
          if (detail) {
            loadingHint.value = `协作中...（${detail}）`;
            runtimeStatusHint.value = detail;
          }
        },
        onFinal: (result) => {
          streamResult = result;
          applyCollaborationResult(assistantMessage, result, streamedTrace, workflow.id);
        },
        onError: (error) => {
          streamError = error?.message || String(error || '');
        }
      });
    } catch (error) {
      transportFailed = true;
      console.warn('Collaboration stream unavailable, fallback to regular workflow run:', error);
    }

    if (streamResult && assistantMessage.collaborationMeta?.status !== 'completed') {
      applyCollaborationResult(assistantMessage, streamResult, streamedTrace, workflow.id);
    }

    let result = streamResult;
    if (!result) {
      if (streamError && !transportFailed) {
        throw new Error(streamError);
      }
      result = await runWorkflow(runPayload);
    }

    const normalized = normalizeRunResult(result, streamedTrace);
    if (assistantMessage.collaborationMeta?.status !== 'completed') {
      applyCollaborationResult(assistantMessage, normalized, streamedTrace, workflow.id);
    }
    await persistCurrentSessionMessages();
  } catch (error) {
    const message = formatRequestError(error);
    assistantMessage.content = `（协作失败：${message}）`;
    updateMeta({ status: 'error', error: message });
    runtimeStatusLevel.value = 'error';
    runtimeStatusHint.value = `协作失败：${message}`;
    collaborationRequested.value = false;
    await persistCurrentSessionMessages();
    ElMessage.error(`协作失败：${message}`);
  }
};

const handleModelChange = async (nextModel) => {
  const modelName = String(nextModel || '').trim();
  if (!modelName || !currentAgentId.value || !currentAgent.value) return;
  if (modelName === currentAgent.value.model) return;

  const previousModel = currentAgent.value.model || '';
  modelSwitching.value = true;
  try {
    await updateAgent(currentAgentId.value, { model: modelName });
    const meta = await getAgentMetadata(currentAgentId.value).catch(() => null);
    const resolvedModel = meta?.modelName || modelName;

    currentAgent.value = {
      ...currentAgent.value,
      model: resolvedModel,
      viewType: meta?.viewType ? normalizeAgentViewType(meta) : currentAgent.value.viewType
    };

    agents.value = agents.value.map((agent) => (
      agent.id === currentAgentId.value
        ? { ...agent, model: resolvedModel, viewType: currentAgent.value.viewType }
        : agent
    ));

    selectedModelName.value = resolvedModel;
    if (meta) {
      syncRuntimeFormFromMetadata(meta);
    } else {
      await loadAgentRuntimeConfig(currentAgentId.value);
    }
    await loadModelCatalog();
    ElMessage.success('模型已切换');
  } catch (error) {
    selectedModelName.value = previousModel;
    ElMessage.error('模型切换失败');
  } finally {
    modelSwitching.value = false;
  }
};

const startEditAgentName = async () => {
  if (!currentAgentId.value || !currentAgent.value) return;
  isEditingAgentName.value = true;
  editingAgentName.value = currentAgent.value.name || '';
  await nextTick();
  const inputEl = agentNameInputRef.value?.input || agentNameInputRef.value?.$el?.querySelector('input');
  inputEl?.focus?.();
  inputEl?.select?.();
};

const cancelEditAgentName = () => {
  isEditingAgentName.value = false;
  editingAgentName.value = '';
};

const saveAgentName = async () => {
  if (!isEditingAgentName.value || !currentAgentId.value || !currentAgent.value) return;
  const trimmedName = String(editingAgentName.value || '').trim();
  if (!trimmedName) {
    ElMessage.warning('名称不能为空');
    editingAgentName.value = currentAgent.value.name || '';
    return;
  }
  if (trimmedName === currentAgent.value.name) {
    cancelEditAgentName();
    return;
  }

  try {
    await updateAgent(currentAgentId.value, { name: trimmedName });
    const meta = await getAgentMetadata(currentAgentId.value).catch(() => null);
    const resolvedName = meta?.name || meta?.agentName || trimmedName;

    currentAgent.value = {
      ...currentAgent.value,
      name: resolvedName
    };
    agents.value = agents.value.map((agent) => (
      agent.id === currentAgentId.value ? { ...agent, name: resolvedName } : agent
    ));
    ElMessage.success('名称已更新');
    cancelEditAgentName();
  } catch (error) {
    ElMessage.error('名称更新失败');
  }
};

const loadSessions = async (agentId, options = {}) => {
  if (!agentId) {
    sessions.value = [];
    currentSessionId.value = '';
    messages.value = [];
    attachedKbIds.value = [];
    return;
  }

  const { autoSelect = true } = options;
  const res = await listChatSessions({ agentId });
  const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
  sessions.value = list.map(normalizeSession);

  if (autoSelect && sessions.value.length) {
    await selectSession(sessions.value[0]);
  } else if (!sessions.value.length) {
    currentSessionId.value = '';
    messages.value = [];
    attachedKbIds.value = [];
  }
};

const loadAttachedKbs = async (sessionId) => {
  if (!sessionId) {
    attachedKbIds.value = [];
    return;
  }

  try {
    const res = await getAttachedKnowledgeBases(sessionId);
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
    attachedKbIds.value = list.map(normalizeId);
  } catch (error) {
    attachedKbIds.value = [];
  }
};

const ensureConfiguredKbsAttached = async () => {
  if (!currentSessionId.value) return;
  const configured = Array.isArray(currentConfig.kbIds) ? currentConfig.kbIds.map(normalizeId) : [];
  const missing = configured.filter((kbId) => kbId && !attachedKbIds.value.includes(kbId));
  if (!missing.length) return;

  for (const kbId of missing) {
    try {
      await attachKnowledgeBase(currentSessionId.value, kbId);
      attachedKbIds.value = [...new Set([...attachedKbIds.value, kbId])];
    } catch (error) {
      console.warn('Failed to attach default kb:', kbId, error);
    }
  }
};

const loadKbDocFilters = async (sessionId) => {
  if (!sessionId) {
    Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
    return;
  }

  try {
    const res = await getChatSession(sessionId);
    const data = res?.data || res || {};
    const filters = normalizeKbDocFilters(data.kbDocFilters || {});
    Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
    Object.assign(kbDocFilters, filters);
  } catch (error) {
    Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
  }
};

const selectSession = async (session) => {
  if (!session?.id) return;

  currentSessionId.value = session.id;
  const cachedMessages = getCachedSessionMessages(session.id);
  if (cachedMessages?.length) {
    messages.value = cachedMessages.map((message) => normalizeWorkspaceMessage(message));
    scrollToBottom();
  }

  try {
    const res = await getChatSession(session.id);
    const data = res?.data || res || {};
    messages.value = (data.messages || []).map((message) => normalizeWorkspaceMessage({
      ...message,
      retrievedChunks: currentConfig.showRetrievedContext ? message.retrievedChunks || [] : []
    }));
    setCachedSessionMessages(session.id, messages.value);
    await loadAttachedKbs(session.id);
    await loadKbDocFilters(session.id);
    try {
      const sessionBinding = await getSessionToolBinding(session.id);
      if (sessionBinding && typeof sessionBinding === 'object') {
        if (Array.isArray(sessionBinding.toolIds)) currentConfig.toolIds = sessionBinding.toolIds;
        if (Array.isArray(sessionBinding.kbIds)) currentConfig.kbIds = sessionBinding.kbIds;
        if (Array.isArray(sessionBinding.skillIds)) currentConfig.skillIds = sessionBinding.skillIds;
        if (Array.isArray(sessionBinding.mcpIds)) currentConfig.mcpIds = sessionBinding.mcpIds;
      }
    } catch (error) {
      console.warn('Failed to load session tool binding:', error);
    }
    scrollToBottom();
  } catch (error) {
    if (!cachedMessages?.length) {
      ElMessage.error('加载会话失败');
    } else {
      ElMessage.warning('网络波动：已显示本地缓存消息');
    }
  }
};

const createSessionForAgent = async (agentId) => {
  const res = await createChatSession({
    agentId,
    title: '新会话'
  });

  const session = normalizeSession(res?.data || res || {});
  sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)];
  currentSessionId.value = session.id;
  messages.value = [];
  setCachedSessionMessages(session.id, []);
  attachedKbIds.value = [];
  Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
  return session;
};

const newSession = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    return;
  }

  try {
    await createSessionForAgent(currentAgentId.value);
  } catch (error) {
    ElMessage.error('创建会话失败');
  }
};

const handleAgentChange = async (agentId) => {
  const targetId = normalizeId(agentId);
  if (!targetId) return;
  currentAgentId.value = targetId;
  currentAgent.value = findAgentById(targetId);
  selectedModelName.value = currentAgent.value?.model || '';
  await restoreConfigForAgent(targetId);
  await loadAgentRuntimeConfig(targetId);
  activeConfigTab.value = 'tools';

  try {
    await loadSessions(targetId);
    if (!sessions.value.length) {
      await createSessionForAgent(targetId);
    }
    await ensureConfiguredKbsAttached();
  } catch (error) {
    ElMessage.error('加载智能体工作台失败');
  }
};

const onAgentSelectionChange = async (agentId) => {
  if (props.lockAgent) return;
  const targetId = normalizeId(agentId);
  if (!targetId) return;
  if (normalizeId(currentAgent.value?.id) === targetId) return;
  await handleAgentChange(targetId);
};

const removeSession = async (session) => {
  try {
    await ElMessageBox.confirm(`确认删除会话“${session.title || '未命名会话'}”吗？`, '删除会话', {
      type: 'warning'
    });
  } catch (error) {
    return;
  }

  try {
    await deleteChatSession(session.id);
    removeCachedSessionMessages(session.id);
    sessions.value = sessions.value.filter((item) => item.id !== session.id);

    if (currentSessionId.value === session.id) {
      if (sessions.value.length) {
        await selectSession(sessions.value[0]);
      } else {
        // In locked console mode, keep the workspace "live" by auto-creating
        // a fresh session after deleting the last one.
        if (props.lockAgent && currentAgentId.value) {
          await createSessionForAgent(currentAgentId.value);
        } else {
          messages.value = [];
          currentSessionId.value = '';
          attachedKbIds.value = [];
          Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
        }
      }
    }

    ElMessage.success('会话已删除');
  } catch (error) {
    ElMessage.error('删除会话失败');
  }
};

const toggleKb = async (kbId) => {
  const targetKbId = normalizeId(kbId);
  if (!currentSessionId.value) {
    ElMessage.warning('请先创建会话');
    return;
  }

  if (isKbAttached(targetKbId)) {
    await detachKb(targetKbId);
  } else {
    await attachKb(targetKbId);
  }
};

const attachKb = async (kbId) => {
  const targetKbId = normalizeId(kbId);
  try {
    await attachKnowledgeBase(currentSessionId.value, targetKbId);
    attachedKbIds.value = [...new Set([...attachedKbIds.value, targetKbId])];
    currentConfig.kbIds = [...new Set([...attachedKbIds.value])];
    ElMessage.success('已附加知识库');
  } catch (error) {
    ElMessage.error('附加知识库失败');
  }
};

const detachKb = async (kbId) => {
  const targetKbId = normalizeId(kbId);
  try {
    await detachKnowledgeBase(currentSessionId.value, targetKbId);
    attachedKbIds.value = attachedKbIds.value.filter((id) => id !== targetKbId);
    currentConfig.kbIds = [...new Set([...attachedKbIds.value])];
    if (kbDocFilters[targetKbId]) {
      delete kbDocFilters[targetKbId];
    }
    ElMessage.success('已移除知识库');
  } catch (error) {
    ElMessage.error('移除知识库失败');
  }
};

const toggleConfigItem = (field, itemId) => {
  const exists = currentConfig[field].includes(itemId);
  currentConfig[field] = exists
    ? currentConfig[field].filter((id) => id !== itemId)
    : [...currentConfig[field], itemId];
};

const loadKbDocuments = async (kbId) => {
  const targetKbId = normalizeId(kbId);
  if (kbDocuments.value[targetKbId]) return;
  try {
    const res = await getDocuments(targetKbId);
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
    kbDocuments.value = { ...kbDocuments.value, [targetKbId]: list };
  } catch (error) {
    kbDocuments.value = { ...kbDocuments.value, [targetKbId]: [] };
  }
};

const toggleKbExpand = async (kbId) => {
  const targetKbId = normalizeId(kbId);
  if (expandedKbIds.value.has(targetKbId)) {
    expandedKbIds.value.delete(targetKbId);
  } else {
    expandedKbIds.value.add(targetKbId);
    if (!kbDocuments.value[targetKbId]) {
      await loadKbDocuments(targetKbId);
    }
  }
};

const isKbExpanded = (kbId) => expandedKbIds.value.has(normalizeId(kbId));

const isDocSelected = (kbId, docId) => {
  const targetKbId = normalizeId(kbId);
  const targetDocId = normalizeId(docId);
  return kbDocFilters[targetKbId]?.includes(targetDocId) || false;
};

const toggleDocFilter = async (kbId, docId) => {
  const targetKbId = normalizeId(kbId);
  const targetDocId = normalizeId(docId);
  if (!kbDocFilters[targetKbId]) {
    kbDocFilters[targetKbId] = [];
  }
  const idx = kbDocFilters[targetKbId].indexOf(targetDocId);
  if (idx >= 0) {
    kbDocFilters[targetKbId].splice(idx, 1);
  } else {
    kbDocFilters[targetKbId].push(targetDocId);
  }
  if (currentSessionId.value) {
    await updateKbDocFilters(currentSessionId.value, normalizeKbDocFilters(kbDocFilters));
  }
};

const syncKbDocFilters = async () => {
  if (currentSessionId.value) {
    await updateKbDocFilters(currentSessionId.value, normalizeKbDocFilters(kbDocFilters));
  }
};


const openSettings = () => {
  sidebarTab.value = 'config';
  sessionPaneCollapsed.value = false;
};

const restoreSessionPane = () => {
  sidebarTab.value = 'session';
  sessionPaneCollapsed.value = false;
};

const triggerFilePicker = () => {
  if (!currentAgentId.value || uploadingFile.value) return;
  fileInputRef.value?.click();
};

const clearUploadedFile = () => {
  selectedUploadFileId.value = '';
  selectedUploadFileName.value = '';
  if (fileInputRef.value) {
    fileInputRef.value.value = '';
  }
};

const onFileSelected = async (event) => {
  const file = event?.target?.files?.[0];
  if (!file) return;
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    clearUploadedFile();
    return;
  }

  uploadingFile.value = true;
  try {
    const uploadRes = await uploadMultimodalFile(file);
    const fileId = uploadRes?.id || uploadRes?.data?.id || '';
    if (!fileId) {
      throw new Error('上传成功但未返回文件ID');
    }
    selectedUploadFileId.value = fileId;
    selectedUploadFileName.value = file.name || `文件-${fileId.slice(0, 8)}`;
    ElMessage.success('文件已上传并附加');
  } catch (error) {
    clearUploadedFile();
    ElMessage.error(error?.message || '文件上传失败');
  } finally {
    uploadingFile.value = false;
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const normalizeChatResponse = (res) => {
  if (res?.status === 'SUCCESS' && res?.data) return { data: res.data, dataType: res.dataType };
  if (res?.status === 'PROCESSING') {
    return { data: { answer: '任务处理中，请稍候...' }, dataType: res.dataType };
  }
  if (res?.status === 'FAILED' || res?.status === 'ERROR') {
    const errorMessage = res?.errorMessage || res?.message || '请求失败，请稍后重试';
    throw new Error(errorMessage);
  }
  return { data: res, dataType: res?.dataType };
};

const normalizeTtsVoice = (voice, modelName) => {
  const rawVoice = String(voice || '').trim();
  const model = String(modelName || '').trim();
  if (!rawVoice) return '';
  if (!model) return rawVoice;
  if (rawVoice.includes(':')) return rawVoice;
  const systemVoices = ['alex', 'anna', 'bella', 'benjamin', 'charles', 'david', 'claire', 'diana'];
  if (systemVoices.includes(rawVoice.toLowerCase())) {
    return `${model}:${rawVoice.toLowerCase()}`;
  }
  return rawVoice;
};

const formatRequestError = (error) => {
  const backendMessage = error?.response?.data?.message || error?.response?.data?.error || '';
  const base = String(backendMessage || error?.message || '');
  if (base.includes('20052') || base.includes('Voice or reference audio should be set')) {
    return '语音生成失败：当前模型需要有效音色或参考音频，请在“模型参数”里重新选择音色。';
  }
  if (!base) return '请求失败，请稍后重试';
  return base.replace(/^SiliconFlow API Error:\s*/i, '');
};

const appendAssistantResult = (payload, dataType = '') => {
  const resolveMediaUrl = (obj = {}, kind = 'audio') => {
    const direct = obj?.url || obj?.download_url || obj?.downloadUrl || '';
    if (direct) return direct;

    const typed =
      kind === 'image'
        ? (obj?.image_url || obj?.imageUrl || obj?.images?.[0]?.url || '')
        : kind === 'video'
          ? (obj?.video_url || obj?.videoUrl || '')
          : (obj?.audio_url || obj?.audioUrl || '');
    if (typed) return typed;

    const fileId = obj?.file_id || obj?.fileId || '';
    if (fileId) return `/api/v1/multimodal/files/${fileId}/download`;
    return '';
  };

  const inferredDataType = String(dataType || payload?.dataType || payload?.type || '').toUpperCase();
  if (inferredDataType === 'IMAGE' || payload?.image_url || payload?.url?.match(/\.(png|jpg|jpeg|webp|gif)(\?|$)/i)) {
    const url = resolveMediaUrl(payload, 'image');
    messages.value.push(normalizeWorkspaceMessage({
      role: 'assistant',
      dataType: 'IMAGE',
      imageUrl: url || '',
      content: payload?.text || payload?.answer || (url ? '图像已生成' : '（图像生成成功，但未返回图像地址）'),
      retrievedChunks: [],
      toolTraces: [],
      model: currentAgent.value?.model || '',
      provider: currentModelInfo.value?.provider || '',
      promptTokens: payload?.usage?.prompt_tokens || 0,
      completionTokens: payload?.usage?.completion_tokens || 0,
      createdAt: new Date().toISOString()
    }));
    return;
  }

  if (inferredDataType === 'VIDEO' || payload?.video_url) {
    const url = resolveMediaUrl(payload, 'video');
    messages.value.push(normalizeWorkspaceMessage({
      role: 'assistant',
      dataType: 'VIDEO',
      videoUrl: url || '',
      content: url ? '视频已生成' : '（视频任务已提交，请稍后在日志中查看结果）',
      retrievedChunks: [],
      toolTraces: [],
      model: currentAgent.value?.model || '',
      provider: currentModelInfo.value?.provider || '',
      promptTokens: payload?.usage?.prompt_tokens || 0,
      completionTokens: payload?.usage?.completion_tokens || 0,
      createdAt: new Date().toISOString()
    }));
    return;
  }

  if (inferredDataType === 'AUDIO' || payload?.audio_url) {
    const url = resolveMediaUrl(payload, 'audio');
    messages.value.push(normalizeWorkspaceMessage({
      role: 'assistant',
      dataType: 'AUDIO',
      audioUrl: url || '',
      content: url ? '语音已生成' : '（语音生成成功，但未返回可播放地址）',
      retrievedChunks: [],
      toolTraces: [],
      model: currentAgent.value?.model || '',
      provider: currentModelInfo.value?.provider || '',
      promptTokens: payload?.usage?.prompt_tokens || 0,
      completionTokens: payload?.usage?.completion_tokens || 0,
      createdAt: new Date().toISOString()
    }));
    return;
  }

  const text = payload?.answer || payload?.text || payload?.choices?.[0]?.message?.content || (typeof payload === 'string' ? payload : JSON.stringify(payload));
  messages.value.push(normalizeWorkspaceMessage({
    role: 'assistant',
    dataType: inferredDataType || 'TEXT',
    content: text || '（模型未返回正文）',
    retrievedChunks: [],
    toolTraces: [],
    model: currentAgent.value?.model || '',
    provider: currentModelInfo.value?.provider || '',
    promptTokens: payload?.usage?.prompt_tokens || 0,
    completionTokens: payload?.usage?.completion_tokens || 0,
    createdAt: new Date().toISOString()
  }));
};

const sendMultimodalMessage = async (content) => {
  const model = selectedModelName.value || currentAgent.value?.model || '';
  if (isImageModel.value) {
    const payload = {
      prompt: content,
      model,
      image_size: agentRuntimeForm.imageSize || '1328x1328',
      negative_prompt: agentRuntimeForm.negativePrompt || undefined,
      guidance_scale: agentRuntimeForm.guidanceScale,
      num_inference_steps: agentRuntimeForm.inferenceSteps,
      seed: agentRuntimeForm.seed ? parseInt(agentRuntimeForm.seed, 10) : undefined
    };
    const rawRes = await chatAgent(currentAgentId.value, JSON.stringify(payload));
    const { data, dataType } = normalizeChatResponse(rawRes);
    appendAssistantResult(data, dataType);
    return;
  }

  if (isVideoModel.value) {
    const payload = {
      prompt: content,
      model,
      videoSize: agentRuntimeForm.videoSize || '16:9',
      negative_prompt: agentRuntimeForm.negativePrompt || undefined,
      seed: agentRuntimeForm.seed ? parseInt(agentRuntimeForm.seed, 10) : undefined
    };
    const rawRes = await chatAgent(currentAgentId.value, JSON.stringify(payload));
    const { data, dataType } = normalizeChatResponse(rawRes);
    appendAssistantResult(data, dataType);
    return;
  }

  if (isSpeechModel.value) {
    const normalizedVoice = normalizeTtsVoice(agentRuntimeForm.voice, model);
    const payload = {
      input: content,
      model,
      voice: normalizedVoice || undefined,
      speed: agentRuntimeForm.speed,
      gain: agentRuntimeForm.gain
    };
    const rawRes = await chatAgent(currentAgentId.value, JSON.stringify(payload));
    const { data, dataType } = normalizeChatResponse(rawRes);
    appendAssistantResult(data, dataType);
  }
};

const sendMessage = async () => {
  if ((!inputMessage.value.trim() && !selectedUploadFileId.value) || !currentSessionId.value || loading.value) return;

  const content = inputMessage.value.trim();
  const outboundMessage = selectedUploadFileId.value
    ? `${content}${content ? '\n\n' : ''}file_id=${selectedUploadFileId.value}`
    : content;
  const userMsg = {
    role: 'user',
    content: selectedUploadFileName.value
      ? `${content || '[已附加文件]'}\n\n[文件] ${selectedUploadFileName.value}`
      : content
  };
  messages.value.push(normalizeWorkspaceMessage(userMsg));
  setCachedSessionMessages(currentSessionId.value, messages.value);
  updateSessionTitleLocally(content || selectedUploadFileName.value || '新会话');

  inputMessage.value = '';
  loading.value = true;
  loadingHint.value = '思考中...';
  runtimeStatusLevel.value = 'running';
  runtimeStatusHint.value = '请求已发出，正在处理';
  const requestStartedAt = Date.now();
  scrollToBottom();

  try {
    const collaborationDecision = resolveCollaborationDecision(content);
    if (collaborationDecision.enabled) {
      await sendCollaborationMessage(content, collaborationDecision);
    } else if (isImageModel.value || isVideoModel.value || isSpeechModel.value) {
      if (collaborationRequested.value) {
        ElMessage.warning('当前模型类型不支持协作执行，已按当前智能体处理');
        collaborationRequested.value = false;
      }
      await sendMultimodalMessage(content || selectedUploadFileName.value || '');
    } else {
      if (collaborationRequested.value) {
        ElMessage.warning(selectedUploadFileId.value ? '带文件消息暂不支持协作执行，已按当前智能体处理' : '未找到足够的可用智能体，已按当前智能体处理');
        collaborationRequested.value = false;
      }
      const conversationContextMessages = buildSingleAgentContextMessages(content);
      const chatPayload = {
        message: outboundMessage,
        toolIds: currentConfig.toolIds || [],
        kbIds: attachedKbIds.value.map(normalizeId),
        skillIds: currentConfig.skillIds || [],
        mcpIds: currentConfig.mcpIds || [],
        kbDocFilters: normalizeKbDocFilters(kbDocFilters),
        conversationContextMessages,
        enableThinking: workspaceDeepThinking.value,
        thinkingBudget: workspaceDeepThinking.value ? Math.max(1024, Math.min(8192, Number(agentRuntimeForm.maxTokens || 2000))) : null,
        maxTokens: Number(agentRuntimeForm.maxTokens || 2000)
      };
      const assistantMessage = normalizeWorkspaceMessage({
        role: 'assistant',
        content: '',
        retrievedChunks: [],
        toolTraces: [],
        model: '',
        provider: '',
        promptTokens: 0,
        completionTokens: 0,
        createdAt: new Date().toISOString()
      });
      messages.value.push(assistantMessage);
      let streamDonePayload = null;
      const defaultEmptyText = attachedKbIds.value.length > 0
        ? '（模型未返回正文。你当前启用了知识库，请重试；若持续出现，请检查模型/网关配置。）'
        : '（模型未返回正文，请重试或检查模型/网关配置。）';

      try {
        await sendChatMessageStream(currentSessionId.value, chatPayload, {
          start: () => {
            assistantMessage.content = '正在处理...';
            loadingHint.value = '思考中...（流式连接已建立）';
            runtimeStatusLevel.value = 'running';
            runtimeStatusHint.value = '流式连接已建立';
          },
          progress: (payload) => {
            const message = String(payload?.message || '').trim();
            if (message) {
              loadingHint.value = `思考中...（${message}）`;
              runtimeStatusLevel.value = 'running';
              runtimeStatusHint.value = message;
            }
          },
          trace: (trace) => {
            assistantMessage.toolTraces = upsertAssistantTrace(assistantMessage.toolTraces || [], trace);
          },
          retrieved: (payload) => {
            const chunks = payload?.retrievedChunks || [];
            assistantMessage.retrievedChunks = currentConfig.showRetrievedContext ? chunks : [];
          },
          done: (payload) => {
            streamDonePayload = payload || {};
            const content = payload?.content || '';
            const isBackendError = content.startsWith('（请求失败：')
              || content.startsWith('（模型未返回正文')
              || content.startsWith('（检索流程已完成')
              || content.startsWith('（模型调用失败：')
              || content.startsWith('（智能体调用失败：')
              || content.startsWith('（智能体返回为空');
            if (isBackendError) {
              loadingHint.value = '思考失败';
              runtimeStatusLevel.value = 'error';
              runtimeStatusHint.value = content.replace(/^（|）$/g, '');
            } else {
              loadingHint.value = '思考完成';
              runtimeStatusLevel.value = 'idle';
              runtimeStatusHint.value = '上一轮请求已完成';
            }
          },
          error: (payload) => {
            const message = payload?.message || '流式请求异常';
            assistantMessage.content = `（请求失败：${message}）`;
            loadingHint.value = '思考失败';
            runtimeStatusLevel.value = 'error';
            runtimeStatusHint.value = `流式异常：${message}`;
          }
        });
      } catch (streamError) {
        console.warn('SSE streaming unavailable, fallback to regular request:', streamError);
        loadingHint.value = '思考中...（流式不可用，已回退普通请求）';
        runtimeStatusLevel.value = 'fallback';
        runtimeStatusHint.value = `流式不可用：${streamError?.message || '未知错误'}，已回退`;
        assistantMessage.toolTraces = [
          ...(assistantMessage.toolTraces || []),
          {
            type: 'STREAM_FALLBACK',
            kbId: 'system',
            message: '流式通道不可用，已自动回退为普通请求',
            status: 'warning',
            durationMs: 0
          }
        ];
        // fallback to non-streaming if SSE is unavailable
      const res = await sendChatMessage(currentSessionId.value, chatPayload);
        streamDonePayload = res?.data || res || {};
      }

      const data = streamDonePayload || {};
      const assistantContent = (data.content || '').trim();
      assistantMessage.content = assistantContent || defaultEmptyText;
      assistantMessage.retrievedChunks = currentConfig.showRetrievedContext
        ? (data.retrievedChunks || assistantMessage.retrievedChunks || [])
        : [];
      assistantMessage.toolTraces = data.toolTraces || assistantMessage.toolTraces || [];
      assistantMessage.model = data.model || assistantMessage.model || '';
      assistantMessage.provider = data.provider || assistantMessage.provider || '';
      assistantMessage.promptTokens = data.promptTokens || 0;
      assistantMessage.completionTokens = data.completionTokens || 0;
      assistantMessage.createdAt = data.createdAt || assistantMessage.createdAt || new Date().toISOString();
      assistantMessage.responseMs = Number(data.responseMs || data.durationMs || 0) || Math.max(0, Date.now() - requestStartedAt);
      if (runtimeStatusLevel.value !== 'error') {
        runtimeStatusLevel.value = 'idle';
        runtimeStatusHint.value = '上一轮请求已完成';
      }
    }
    setCachedSessionMessages(currentSessionId.value, messages.value);
    await persistCurrentSessionMessages();
    clearUploadedFile();
  } catch (error) {
    const status = error?.response?.status;
    const backendMessage = formatRequestError(error);
    let failureReason = '网络异常';
    if (error?.code === 'ECONNABORTED' || String(error?.message || '').toLowerCase().includes('timeout')) {
      failureReason = '请求超时（超过 180 秒）';
    } else if (status) {
      failureReason = `服务错误 (${status})`;
    }
    const finalReason = backendMessage || failureReason;
    messages.value.push(normalizeWorkspaceMessage({
      role: 'assistant',
      content: `（请求失败：${finalReason}）`,
      retrievedChunks: [],
      toolTraces: [],
      model: '',
      provider: '',
      promptTokens: 0,
      completionTokens: 0,
      createdAt: new Date().toISOString()
    }));
    setCachedSessionMessages(currentSessionId.value, messages.value);
    await persistCurrentSessionMessages();
    ElMessage.error(`发送消息失败：${finalReason}`);
    runtimeStatusLevel.value = 'error';
    runtimeStatusHint.value = `请求失败：${finalReason}`;
  } finally {
    loading.value = false;
    scrollToBottom();
  }
};

const handleEnter = (event) => {
  if (!event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }
};

const traceIcon = (type) => {
  const iconMap = {
    'KB_STRUCTURE': 'Folder',
    'KB_SEARCH': 'Search',
    'KB_RETRIEVE': 'Document'
  };
  return iconMap[type] || 'Setting';
};

const getVisibleToolTraces = (traces = []) => {
  if (!Array.isArray(traces) || traces.length === 0) return [];
  const toolBindTraces = traces.filter((trace) => trace?.type === 'TOOL_BIND');
  const others = traces.filter((trace) => trace?.type !== 'TOOL_BIND');
  if (toolBindTraces.length <= 1) return traces;
  const warningCount = toolBindTraces.filter((trace) => trace.status === 'warning').length;
  const errorCount = toolBindTraces.filter((trace) => trace.status === 'error').length;
  const status = errorCount ? 'error' : warningCount ? 'warning' : 'success';
  const summary = {
    type: 'TOOL_BIND_SUMMARY',
    kbId: 'tools',
    message: `已绑定 ${toolBindTraces.length} 个工具${warningCount ? `，${warningCount} 个降级/跳过` : ''}${errorCount ? `，${errorCount} 个失败` : ''}`,
    status,
    durationMs: toolBindTraces.reduce((sum, trace) => sum + Number(trace.durationMs || 0), 0),
    detail: {
      tools: toolBindTraces.map((trace) => ({
        id: trace.kbId || trace.toolId || '',
        status: trace.status || '',
        message: trace.message || '',
        detail: trace.detail || {}
      }))
    }
  };
  return [summary, ...others];
};

const formatTraceType = (type) => {
  const labelMap = {
    TOOL_BIND: '工具绑定',
    TOOL_BIND_SUMMARY: '工具绑定',
    KB_STRATEGY: '检索策略判定',
    KB_STRUCTURE: '知识库结构检查',
    KB_SEARCH: '知识检索',
    KB_RETRIEVE: '上下文组装',
    KB_HINT: '检索提示',
    KB_PIPELINE: '检索链路',
    KB_MODEL_TOOL_CALL: '模型工具调用',
    KB_MODEL_TOOL_FINAL: '模型工具调用总结',
    MODEL_REASONING: '模型思考',
    STREAM_FALLBACK: '流式回退'
  };
  return labelMap[type] || type || '处理步骤';
};

const formatTraceStatus = (status) => {
  const s = (status || '').toLowerCase();
  if (s === 'success') return '成功';
  if (s === 'running') return '进行中';
  if (s === 'warning') return '提醒';
  if (s === 'error') return '失败';
  return '处理中';
};

const shouldShowTraceDetail = (trace = {}) => {
  if (!trace?.detail) return false;
  return true;
};

const formatTraceDetail = (trace = {}) => {
  const detail = trace?.detail;
  if (!detail) return '';
  if (trace.type !== 'MODEL_REASONING') {
    return typeof detail === 'object' ? JSON.stringify(detail, null, 2) : String(detail);
  }

  const getReasonContext = (payload = {}) => {
    const candidates = [
      payload.reasonContext,
      payload.reason_context,
      payload['reason-context'],
      payload.reasoningContext,
      payload.reasoning_context,
      payload['reasoning-context']
    ];
    for (const candidate of candidates) {
      if (candidate == null) continue;
      if (typeof candidate === 'string') {
        if (candidate.trim()) return candidate;
        continue;
      }
      if (typeof candidate === 'object') {
        return JSON.stringify(candidate, null, 2);
      }
      return String(candidate);
    }
    return '';
  };
  const reasonContext = getReasonContext(detail);
  return reasonContext || '未返回 reason-context';
};

const upsertAssistantTrace = (existing, incoming) => {
  if (!incoming || !incoming.type) return existing;
  const traces = Array.isArray(existing) ? [...existing] : [];
  const incomingStatus = String(incoming.status || '').toLowerCase();

  // If a running trace exists for same type, replace it with latest status.
  const runningIndex = traces.findIndex((t) => {
    const sameType = String(t?.type || '') === String(incoming.type || '');
    const isRunning = String(t?.status || '').toLowerCase() === 'running';
    return sameType && isRunning;
  });

  if (runningIndex >= 0) {
    traces[runningIndex] = { ...traces[runningIndex], ...incoming };
    return traces;
  }

  // Deduplicate same terminal status message
  const duplicate = traces.find((t) => {
    return String(t?.type || '') === String(incoming.type || '')
      && String(t?.status || '').toLowerCase() === incomingStatus
      && String(t?.message || '') === String(incoming.message || '');
  });
  if (duplicate) return traces;

  traces.push(incoming);
  return traces;
};

const getTraceDetailKey = (msg, index, traceIdx) => {
  return `${normalizeId(currentSessionId.value)}:${msg?.createdAt || ''}:${index}:${traceIdx}`;
};

const isTraceDetailExpanded = (msg, index, traceIdx) => {
  return !!expandedTraceDetails.value[getTraceDetailKey(msg, index, traceIdx)];
};

const toggleTraceDetail = (msg, index, traceIdx) => {
  const key = getTraceDetailKey(msg, index, traceIdx);
  expandedTraceDetails.value[key] = !expandedTraceDetails.value[key];
};

const renderMarkdown = (text) => {
  if (!text) return '';
  const normalized = String(text)
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t');
  const escaped = normalized
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  try {
    return String(marked.parse(escaped)).trim();
  } catch (error) {
    return escaped.replace(/\n/g, '<br>');
  }
};

const normalizeModelResponseError = (rawText) => {
  const raw = String(rawText || '').trim();
  if (!raw) return '';

  const isModelParseError = raw.includes('模型响应解析失败');
  const lower = raw.toLowerCase();
  const has401 = /http\s*=\s*401\b/i.test(raw) || /\b401\s+unauthorized\b/i.test(raw) || lower.includes('invalid token');
  const has429 = /http\s*=\s*429\b/i.test(raw) || lower.includes('rate limit');
  const has5xx = /http\s*=\s*5\d\d\b/i.test(raw);
  const ollamaNoResponse = lower.includes('no successful response from ollama');

  if (!isModelParseError && !has401 && !has429 && !has5xx && !ollamaNoResponse) {
    return raw;
  }

  let friendly = '';
  if (has401) {
    friendly = '模型鉴权失败（401）。请在工作台设置中更新模型提供方 API Key，并确认该 Key 对当前模型有调用权限。';
  } else if (has429) {
    friendly = '模型请求被限流（429）。请稍后重试，或降低并发/提升配额。';
  } else if (has5xx) {
    friendly = '模型服务端暂时异常（5xx）。请稍后重试。';
  } else if (ollamaNoResponse) {
    friendly = '模型服务未返回成功响应（Ollama）。请检查模型服务状态与连接配置。';
  } else {
    friendly = '模型响应解析失败，请稍后重试或切换模型。';
  }

  return `${friendly}\n\n原始错误：${raw}`;
};

const getMessageText = (msg) => {
  if (!msg) return '';
  if (typeof msg.content === 'string') {
    if (String(msg.role || '').toLowerCase() === 'assistant') {
      return normalizeModelResponseError(msg.content);
    }
    return msg.content;
  }
  if (msg.content == null) return '';
  return JSON.stringify(msg.content, null, 2);
};

const getMessageImageUrl = (msg) => {
  if (!msg) return '';
  if (msg.imageUrl) return msg.imageUrl;
  if (String(msg.dataType || '').toUpperCase() !== 'IMAGE') return '';
  return '';
};

const getMessageAudioUrl = (msg) => {
  if (!msg) return '';
  if (msg.audioUrl) return msg.audioUrl;
  if (String(msg.dataType || '').toUpperCase() !== 'AUDIO') return '';
  return '';
};

const getMessageVideoUrl = (msg) => {
  if (!msg) return '';
  if (msg.videoUrl) return msg.videoUrl;
  if (String(msg.dataType || '').toUpperCase() !== 'VIDEO') return '';
  return '';
};

const getChunkSourceLabel = (chunk) => {
  return chunk?.docName || chunk?.source || chunk?.docId || '未知来源';
};

const getRetrievedContextKey = (msg, index) => {
  return `${normalizeId(currentSessionId.value)}:${msg?.createdAt || ''}:${index}`;
};

const isRetrievedContextExpanded = (msg, index) => {
  return !!expandedRetrievedContext.value[getRetrievedContextKey(msg, index)];
};

const toggleRetrievedContext = (msg, index) => {
  const key = getRetrievedContextKey(msg, index);
  expandedRetrievedContext.value[key] = !expandedRetrievedContext.value[key];
};

const getCollaborationDetailKey = (msg, index) => `${normalizeId(currentSessionId.value)}:${msg?.createdAt || ''}:collab:${index}`;

const isCollaborationDetailExpanded = (msg, index) => !!expandedCollaborationDetails.value[getCollaborationDetailKey(msg, index)];

const toggleCollaborationDetail = (msg, index) => {
  const key = getCollaborationDetailKey(msg, index);
  expandedCollaborationDetails.value[key] = !expandedCollaborationDetails.value[key];
};

const openCollaborationInspector = (meta = {}, tab = 'graph') => {
  selectedCollaborationMeta.value = meta || {};
  collaborationInspectorTab.value = tab;
  collaborationInspectorVisible.value = true;
};

const buildCollaborationFallbackGraph = (meta = {}) => {
  const participants = Array.isArray(meta?.participants) ? meta.participants : [];
  const nodes = [
    { id: 'start', label: 'Start', kind: 'start' },
    { id: 'planner_core', label: 'Planner Core', kind: 'logic' },
    { id: 'planner_validator', label: 'Plan Validator', kind: 'logic' },
    { id: 'task_dispatcher', label: 'Task Dispatcher', kind: 'logic' },
    ...participants.map((participant, index) => ({
      id: normalizeId(participant.id) || `agent_${index + 1}`,
      label: participant.name || `Agent ${index + 1}`,
      kind: 'agent'
    })),
    { id: 'finalize', label: 'Merge', kind: 'merge' },
    { id: 'end', label: 'End', kind: 'end' }
  ];
  const edges = [
    { source: 'start', target: 'planner_core' },
    { source: 'planner_core', target: 'planner_validator' },
    { source: 'planner_validator', target: 'task_dispatcher' },
    ...participants.flatMap((participant, index) => {
      const id = normalizeId(participant.id) || `agent_${index + 1}`;
      return [
        { source: 'task_dispatcher', target: id, label: 'dispatch' },
        { source: id, target: 'task_dispatcher', label: 'report' }
      ];
    }),
    { source: 'task_dispatcher', target: 'finalize' },
    { source: 'finalize', target: 'end' }
  ];
  return { nodes, edges };
};

const formatCollaborationStatus = (meta = {}) => {
  if (meta.status === 'running') return '执行中';
  if (meta.status === 'error') return '失败';
  const count = Array.isArray(meta.participants) ? meta.participants.length : 0;
  return count ? `${count} 个智能体` : '已完成';
};

const formatCollaborationArtifact = (artifact) => {
  if (!artifact) return '';
  if (typeof artifact === 'string') return artifact;
  const text = artifact.content || artifact.final_answer || artifact.answer || artifact.summary || artifact.report || artifact.result || '';
  if (text) return String(text);
  return `\`\`\`json\n${JSON.stringify(artifact, null, 2)}\n\`\`\``;
};

const getCollaborationTraceSummary = (meta = {}) => {
  const trace = Array.isArray(meta.trace) ? meta.trace : [];
  return trace
    .map((event) => String(event?.detail || event?.message || event?.payload?.node_id || event?.event || '').trim())
    .filter(Boolean)
    .slice(-6);
};

const openCitation = (chunk) => {
  const docId = normalizeId(chunk?.docId);
  if (!docId) {
    ElMessage.warning('该来源暂无文档标识，无法打开详情');
    return;
  }

  let kbId = normalizeId(chunk?.kbId);
  if (!kbId && attachedKbIds.value.length === 1) {
    kbId = normalizeId(attachedKbIds.value[0]);
  }

  if (!kbId) {
    ElMessage.warning('该来源缺少知识库标识，请在单知识库会话中重试');
    return;
  }

  const target = `/dashboard/resources/knowledge/${kbId}/document/${docId}`;
  const resolved = router.resolve(target);
  const opened = window.open(resolved.href, '_blank', 'noopener,noreferrer');
  if (!opened) {
    // 浏览器拦截弹窗时降级为当前页跳转
    router.push(target);
  }
};

const reloadWorkspace = async () => {
  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe(), loadToolCatalogSafe(), loadModelCatalog(), loadCollaborationWorkflows(), loadProviderKeys()]);
  if (currentAgentId.value) {
    await restoreConfigForAgent(currentAgentId.value);
    await loadAgentRuntimeConfig(currentAgentId.value);
    await loadSessions(currentAgentId.value, { autoSelect: !currentSessionId.value });
    await ensureConfiguredKbsAttached();
  }
};

onMounted(async () => {
  // Restore workspace state from localStorage first
  const savedState = restoreWorkspaceState();
  if (savedState) {
    sessionPaneCollapsed.value = savedState.sessionPaneCollapsed ?? false;
    sidebarTab.value = savedState.sidebarTab ?? 'session';
    activeConfigTab.value = savedState.activeConfigTab ?? 'tools';
  }

  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe(), loadToolCatalogSafe(), loadModelCatalog(), loadCollaborationWorkflows(), loadProviderKeys()]);

  const presetId = props.presetAgentId ? String(props.presetAgentId) : '';
  const routeAgentId = typeof route.query?.agentId === 'string' ? route.query.agentId : '';
  // Restore saved agent (or use first available); preset has highest priority.
  const agentToRestore = routeAgentId
    || presetId
    || (savedState?.currentAgentId && agents.value.find((a) => normalizeId(a.id) === normalizeId(savedState.currentAgentId))
      ? normalizeId(savedState.currentAgentId)
      : (agents.value[0]?.id || ''));

  if (agentToRestore) {
    currentAgentId.value = normalizeId(agentToRestore);
    currentAgent.value = findAgentById(agentToRestore);
    selectedModelName.value = currentAgent.value?.model || '';
    await restoreConfigForAgent(agentToRestore);
    await loadAgentRuntimeConfig(agentToRestore);

    // Load sessions and try to restore the saved session
    await loadSessions(agentToRestore, { autoSelect: false });

    if (savedState?.currentSessionId && sessions.value.some(s => s.id === savedState.currentSessionId)) {
      await selectSession({ id: savedState.currentSessionId });
    } else if (sessions.value.length) {
      await selectSession(sessions.value[0]);
    } else {
      await createSessionForAgent(agentToRestore);
    }
    await ensureConfiguredKbsAttached();
  }
  await applyRoutePromptIfExists();
});

// Save workspace state on changes
watch(
  [currentAgentId, currentSessionId, sessionPaneCollapsed, sidebarTab, activeConfigTab],
  () => saveWorkspaceState(),
  { immediate: false }
);

watch(
  () => currentAgent.value?.model,
  (value) => {
    if (!modelSwitching.value) {
      selectedModelName.value = value || '';
    }
  },
  { immediate: true }
);

watch(
  () => props.presetAgentId,
  async (value) => {
    const presetId = String(value || '').trim();
    if (!presetId || presetId === String(currentAgentId.value || '')) return;
    if (!agents.value.some((agent) => String(agent.id) === presetId)) return;
    await handleAgentChange(presetId);
  }
);
</script>

<style scoped>
/* =========================================================================
   AgentWorkspace - Redesign (Glassmorphism, Hierarchy, Fluid Layout)
   ========================================================================= */

/* 1. Global & Layout 
-------------------------------------------------- */
.agent-workspace {
  --left-pane-width: 380px;
  --right-pane-width: 0px;
  --drawer-left-width: 380px;
  --collapsed-pane-width: 64px;
  --drawer-right-width: 320px;
  --chat-content-max-width: 900px;
  --sidebar-accent: #0f766e;
  --sidebar-text-strong: #0f172a;
  --sidebar-text: #334155;
  --sidebar-text-muted: #64748b;
  --sidebar-line: #dce3ea;
  --sidebar-soft-bg: #f8fafc;
  --sidebar-hover-bg: #f1f5f9;
  --sidebar-active-bg: #ecfdf5;
  position: relative;
  width: 100%;
  height: 100%; /* Fill the host shell height */
  display: flex;
  overflow: hidden; /* No global scrolling */
  background-color: #f6f9fb;
  font-family: "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  box-sizing: border-box;
}

.agent-workspace.is-wide {
  --left-pane-width: 400px;
  --drawer-left-width: 400px;
  --right-pane-width: 0px;
  --chat-content-max-width: 920px;
}

.agent-workspace.is-medium {
  --left-pane-width: 340px;
  --drawer-left-width: 340px;
  --collapsed-pane-width: 56px;
  --chat-content-max-width: 820px;
}

.agent-workspace.is-narrow {
  --left-pane-width: 300px;
  --drawer-left-width: min(360px, calc(100vw - 24px));
  --collapsed-pane-width: 50px;
  --chat-content-max-width: 740px;
}

/* Ambient Glass Background */
.agent-workspace::before { content: none; }

/* Overlay for Drawers */
.d-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.2);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  z-index: 90;
  animation: fadeIn 0.2s ease-out forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 2. Sidebars (Left & Right)
-------------------------------------------------- */
.workspace-sidebar,
.workspace-config-pane {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  flex-shrink: 0;
}


.sidebar-tabs {
  display: flex;
  margin: 14px 14px 6px;
  padding: 4px;
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  gap: 4px;
}
.sidebar-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text-muted);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: transparent;
}
.sidebar-tab:hover {
  background: rgba(226, 232, 240, 0.7);
  color: var(--sidebar-text);
}
.sidebar-tab.active {
  background: #ffffff;
  color: var(--sidebar-text-strong);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
.sidebar-tab.active:hover {
  background: #ffffff;
}

.sidebar-tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 5px;
  padding: 0 5px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  line-height: 1;
}

.sidebar-agent-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 14px 8px;
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
}

.sidebar-agent-switch-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text-muted);
}

.sidebar-agent-select {
  flex: 1;
}

.workspace-config-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.workspace-sidebar {
  width: var(--left-pane-width);
  border-right: 1px solid var(--sidebar-line);
}



/* Drawer Modes */
.workspace-sidebar.is-drawer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  width: var(--drawer-left-width);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 8px 0 32px rgba(0,0,0,0.06);
  transform: translateX(-100%);
}
.workspace-sidebar.is-drawer:not(.is-collapsed) {
  transform: translateX(0);
}

.DELETED_SELECTOR {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  width: var(--drawer-right-width);
  display: flex;
  pointer-events: auto;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: -8px 0 32px rgba(0,0,0,0.06);
  transform: translateX(100%);
}
.DELETED_SELECTOR:not(.is-collapsed) {
  transform: translateX(0);
}

/* Collapsed Modes (Desktop only) */
.workspace-sidebar.is-collapsed:not(.is-drawer) {
  width: 0;
  overflow: visible;
  background: transparent;
  border-right: 0;
}

.workspace-config-pane.is-collapsed:not(.is-drawer) {
  width: var(--collapsed-pane-width);
}

/* Custom Scrollbars for Sidebars */
.workspace-sidebar ::-webkit-scrollbar,
.workspace-config-pane ::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}
.workspace-sidebar ::-webkit-scrollbar-thumb,
.workspace-config-pane ::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.6);
  border-radius: 4px;
}
.workspace-sidebar ::-webkit-scrollbar-thumb:hover,
.workspace-config-pane ::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.8);
}

/* 3. Left Sidebar Details
-------------------------------------------------- */
.workspace-session-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.collapsed-restore-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 0;
  padding: 0;
  pointer-events: none;
  background: transparent;
}

.expanded-collapse-panel {
  position: absolute;
  left: var(--left-pane-width);
  top: 0;
  bottom: 0;
  width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  pointer-events: none;
  z-index: 80;
}

.collapsed-rail-handle {
  width: 24px;
  min-height: 72px;
  appearance: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 9px 0;
  border: 1px solid rgba(203, 213, 225, 0.86);
  border-left: 0;
  border-radius: 0 12px 12px 0;
  background: #ffffff;
  color: #64748b;
  cursor: pointer;
  box-shadow: 8px 12px 24px -22px rgba(15, 23, 42, 0.55);
  transition: border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.collapsed-rail-handle {
  position: absolute;
  top: 50%;
  right: -24px;
  pointer-events: auto;
  transform: translateY(-50%);
}

.session-edge-handle {
  width: 32px;
  height: 32px;
  min-height: 0;
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 999px;
  background: #ffffff;
  color: #64748b;
  cursor: pointer;
  pointer-events: auto;
  position: relative;
  z-index: 1;
  box-shadow: 0 0 0 4px #f6f9fb, 0 10px 22px -16px rgba(15, 23, 42, 0.5);
  transition: border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.collapsed-rail-handle:hover,
.collapsed-rail-handle:focus-visible,
.session-edge-handle:hover,
.session-edge-handle:focus-visible {
  border-color: rgba(15, 118, 110, 0.36);
  color: var(--sidebar-accent);
  outline: none;
  box-shadow: 10px 14px 28px -22px rgba(15, 118, 110, 0.5);
}

.collapsed-rail-handle:hover,
.collapsed-rail-handle:focus-visible {
  transform: translate(1px, -50%);
}

.session-edge-handle:hover,
.session-edge-handle:focus-visible {
  transform: translateX(1px);
  box-shadow: 0 0 0 4px #f6f9fb, 0 12px 24px -16px rgba(15, 118, 110, 0.45);
}

.collapsed-handle-icon,
.collapsed-handle-arrow {
  font-size: 14px;
}

.collapsed-session-dot {
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ecfdf5;
  color: var(--sidebar-accent);
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}

.workspace-collaboration-pane {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 14px;
  background: #f8fafc;
}

.collaboration-sidebar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  background: #ffffff;
}

.collaboration-sidebar-head h3 {
  margin: 0 0 4px;
  color: var(--sidebar-text-strong);
  font-size: 14px;
  line-height: 1.2;
}

.collaboration-sidebar-head p {
  margin: 0;
  color: var(--sidebar-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.collaboration-sidebar-head > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 26px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.collaboration-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 8px;
  border: 1px dashed var(--sidebar-line);
  border-radius: 12px;
  background: #ffffff;
}

.collaboration-empty-action {
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}

.collaboration-run-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.collaboration-run-item {
  padding: 12px;
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  background: #ffffff;
}

.collaboration-run-item.active {
  border-color: #99f6e4;
  box-shadow: 0 0 0 1px rgba(20, 184, 166, 0.12);
}

.collaboration-run-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.collaboration-run-top strong {
  color: var(--sidebar-text-strong);
  font-size: 13px;
  line-height: 1.35;
}

.collaboration-run-top em {
  flex-shrink: 0;
  color: #475569;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.collaboration-run-top em.status-running {
  color: #2563eb;
}

.collaboration-run-top em.status-error {
  color: #dc2626;
}

.collaboration-run-time {
  margin-top: 4px;
  color: var(--sidebar-text-muted);
  font-size: 12px;
}

.collaboration-run-participants {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 10px;
}

.collaboration-run-participants span {
  padding: 3px 7px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.collaboration-run-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.collaboration-run-actions button {
  appearance: none;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #ffffff;
  color: #4338ca;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  padding: 8px 10px;
}

.collaboration-run-actions button:hover {
  background: #eef2ff;
  border-color: #818cf8;
}

.session-collapse-handle {
  position: absolute;
  right: -24px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
}

.config-collapse-handle {
  position: absolute;
  left: -14px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
}

.collapse-btn {
  width: 30px !important;
  height: 46px !important;
  border-radius: 14px !important;
  font-size: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #f1f7ff 100%);
  border: 1px solid #d7e4f3;
  box-shadow: 0 8px 16px -12px rgba(15, 23, 42, 0.4);
  color: #5b6b80;
  transition: all 0.2s ease;
}
.collapse-btn:hover {
  color: #0f766e;
  border-color: rgba(15, 159, 149, 0.42);
  background: linear-gradient(180deg, #f2fffc 0%, #e9fbf7 100%);
  box-shadow: 0 12px 20px -12px rgba(15, 159, 149, 0.3);
  transform: translateX(-1px);
}

/* Sidebar Top */
.sidebar-top {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
  border-bottom: 1px solid #e8e8e8;
  background: transparent;
}

.sidebar-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.sidebar-avatar {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  color: #ffffff;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: none;
}

.sidebar-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--sidebar-text-strong);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-switcher {
  width: 100%;
}
.agent-switcher :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: var(--sidebar-soft-bg);
  box-shadow: 0 0 0 1px var(--sidebar-line) inset !important;
  min-height: 42px;
}
.agent-switcher :deep(.el-input__inner) {
  font-size: 15px;
  color: #4a5568;
}

.new-session-btn {
  width: 100%;
  border-radius: 10px;
  height: 38px;
  font-weight: 600;
  background: var(--sidebar-accent);
  border: none;
  box-shadow: 0 3px 10px rgba(15, 159, 149, 0.26);
  transition: all 0.2s ease;
}
.new-session-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(15, 159, 149, 0.32);
}

.session-actions {
  padding: 12px 14px 0;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 11px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid var(--sidebar-line);
  background: rgba(255, 255, 255, 0.82);
}

.session-item:hover {
  background: var(--sidebar-hover-bg);
  border-color: rgba(203, 213, 225, 0.95);
}

.session-item.active {
  background: var(--sidebar-active-bg);
  border-color: #99f6e4;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.03);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  font-size: 11px;
  color: #8b9bb0;
}

.session-delete {
  font-size: 16px;
  color: #cbd5e1;
  opacity: 0;
  transition: 0.2s ease;
}
.session-item:hover .session-delete {
  opacity: 1;
}
.session-delete:hover {
  color: #ef4444;
}

/* 4. Main Workspace (Chat Area)
-------------------------------------------------- */
.workspace-main {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: #ffffff;
}

.state-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 24px 36px;
}

/* Scrolling messages container */
.messages-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* Smooth scroll behavior */
  scroll-behavior: smooth;
}

.messages-container.is-empty {
  justify-content: center;
  padding-top: 0;
  padding-bottom: 0;
}

.messages-container.is-empty > .welcome-panel {
  margin-top: 0;
  padding-bottom: 0;
  transform: translateY(-4%);
}

.messages-container:not(.is-empty) > .message-item:first-child {
  margin-top: auto;
}

.messages-container:not(.is-empty) > .message-item:last-of-type {
  margin-bottom: 0;
}

/* Content wrapper to center and limit width */
.messages-container > .welcome-panel,
.messages-container > .message-item,
.messages-container > .loading-indicator {
  width: 100%;
  max-width: var(--chat-content-max-width);
  margin-left: auto;
  margin-right: auto;
}

/* Custom Scrollbar for Main Area */
.messages-container::-webkit-scrollbar {
  width: 6px;
}
.messages-container::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.8);
  border-radius: 6px;
}

.welcome-panel {
  margin-top: 40px;
  padding-bottom: 40px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: floatIn 0.5s ease-out;
  width: 100%;
  max-width: var(--chat-content-max-width);
  margin-left: auto;
  margin-right: auto;
}
@keyframes floatIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.welcome-panel h2 {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 22px;
  letter-spacing: -0.02em;
}

.welcome-modes {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.mode-tag {
  height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #e2e8f0 !important;
  background: rgba(255, 255, 255, 0.8) !important;
  color: #475569 !important;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
}
.mode-tag:hover {
  background: #ffffff !important;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transform: translateY(-1px);
}
.mode-tag.el-tag--primary {
  border-color: #3b82f6 !important;
  background: rgba(239, 246, 255, 0.8) !important;
  color: #1d4ed8 !important;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.composer-placeholder {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  text-align: left;
}

.composer-right-tools {
  display: inline-flex;
  align-items: center;
}

/* Message Items */
.message-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}
.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: rgba(241, 245, 249, 0.8);
  color: #64748b;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.message-item.user .message-avatar {
  background: var(--orin-primary, #3b82f6);
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}

.message-bubble {
  max-width: min(85%, 760px);
  min-width: 0;
  overflow: hidden;
}
.message-item.user .message-bubble {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-role {
  margin-bottom: 8px;
  font-size: 13px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  overflow-wrap: anywhere;
}
.message-item.user .message-role {
  flex-direction: row-reverse;
}

.message-time, .message-meta {
  font-size: 11px;
  color: #cbd5e1;
}

.message-runtime-meta {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.runtime-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 159, 149, 0.26);
  background: rgba(237, 249, 247, 0.92);
  color: #0f766e;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

/* Tool Traces (Nested Cards) */
.reasoning-section {
  margin-bottom: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
}
.reasoning-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
}
.reasoning-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
  margin-bottom: 6px;
  transition: all 0.2s ease;
}
.reasoning-item:last-child {
  margin-bottom: 0;
}
.reasoning-item:hover {
  border-color: #e2e8f0;
  box-shadow: 0 2px 6px rgba(0,0,0,0.03);
}
.reasoning-step-dot {
  width: 22px;
  height: 22px;
  background: #f8fafc;
  color: #475569;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Trace Status colors */
.trace-success .reasoning-step-dot { background: #dcfce7; color: #166534; }
.trace-running .reasoning-step-dot { background: #dbeafe; color: #1d4ed8; }
.trace-warning .reasoning-step-dot { background: #fef9c3; color: #854d0e; }
.trace-error .reasoning-step-dot   { background: #fee2e2; color: #991b1b; }

.reasoning-msg {
  font-size: 12px;
  color: #475569;
  margin-top: 4px;
}

.reasoning-main {
  flex: 1;
  min-width: 0;
}

.reasoning-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  cursor: pointer;
  width: 100%;
}

.reasoning-name {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.reasoning-status {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  background: #f1f5f9;
  color: #475569;
}

.reasoning-status.status-success {
  background: #dcfce7;
  color: #166534;
}

.reasoning-status.status-running {
  background: #dbeafe;
  color: #1d4ed8;
}

.reasoning-status.status-warning {
  background: #fef9c3;
  color: #854d0e;
}

.reasoning-status.status-error {
  background: #fee2e2;
  color: #991b1b;
}

.reasoning-duration {
  font-size: 11px;
  color: #64748b;
}

.reasoning-expand-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  min-width: 52px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #94a3b8;
  background: #ffffff;
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  transition: all 0.18s ease;
  user-select: none;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  outline: none;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.reasoning-top:hover .reasoning-expand-btn {
  border-color: #64748b;
  background: #eef2ff;
  box-shadow: 0 2px 6px rgba(30, 64, 175, 0.14);
}

.reasoning-detail {
  margin-top: 8px;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  background: #f8fafc;
  overflow: hidden;
}

.reasoning-detail pre {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Citations */
.message-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.citation-item {
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
  transition: 0.2s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}
.citation-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

/* Retrieved Context */
.retrieved-context {
  margin-top: 10px;
  border: 1px solid #dbe7f2;
  border-radius: 14px;
  background: #f8fbff;
  overflow: hidden;
}

.context-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
}

.context-toggle {
  cursor: pointer;
  user-select: none;
  transition: background 0.18s ease;
}

.context-toggle:hover {
  background: #f2f7ff;
}

.context-header-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.context-header-left :deep(.el-icon) {
  color: #64748b;
  font-size: 14px;
}

.context-toggle-text {
  color: #2563eb;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.context-body {
  border-top: 1px solid #e2e8f0;
  padding: 10px 12px 12px;
}

.context-item {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.context-item:last-child {
  margin-bottom: 0;
}

.chunk-source {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #0f766e;
  font-weight: 600;
}

.chunk-source.is-clickable {
  cursor: pointer;
}

.chunk-source.is-clickable:hover {
  color: #0f9f95;
}

.chunk-text {
  font-size: 12px;
  line-height: 1.55;
  color: #475569;
}

/* Message Box */
.message-text {
  display: inline-block;
  max-width: 100%;
  padding: 10px 16px;
  border-radius: 16px;
  background: #ffffff;
  color: #1e293b;
  font-size: 15px;
  line-height: 1.5;
  box-shadow: 0 2px 10px rgba(0,0,0,0.02);
  border: 1px solid rgba(226, 232, 240, 0.4);
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.message-item.user .message-text {
  background: #3b82f6; /* Modern Blue */
  color: #ffffff;
  border: none;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
  border-top-right-radius: 4px; /* classic chat bubble tweak */
}
.message-item:not(.user) .message-text {
  border-top-left-radius: 4px;
}

.message-text :deep(p) {
  margin: 0 0 10px;
  white-space: pre-wrap;
}
.message-text :deep(p:last-child) { margin-bottom: 0; }
.message-text :deep(pre) {
  margin: 12px 0;
  padding: 16px;
  background: #0f172a;
  color: #f8fafc;
  border-radius: 12px;
  overflow-x: auto;
  font-size: 13px;
}
.message-item.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 6px;
  border-radius: 6px;
}
.message-item:not(.user) .message-text :deep(code) {
  background: rgba(15, 23, 42, 0.05);
  padding: 2px 6px;
  border-radius: 6px;
  color: #0f172a;
}
.message-text :deep(pre code) {
  background: transparent !important;
  color: inherit !important;
  padding: 0;
}

.collaboration-process {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #c7d2fe;
  border-radius: 14px;
  background: #f8fafc;
}

.collaboration-process-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.collaboration-process-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1e293b;
  font-size: 13px;
  font-weight: 800;
}

.collaboration-process-title strong {
  color: #4f46e5;
  font-size: 12px;
}

.collaboration-process-toggle {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.collaboration-process-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.collaboration-process-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}

.collaboration-detail-link {
  appearance: none;
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  background: #ffffff;
  color: #4338ca;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  padding: 5px 9px;
}

.collaboration-detail-link:hover {
  border-color: #818cf8;
  background: #eef2ff;
}

.collaboration-inspector {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.collaboration-inspector-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.collaboration-inspector-summary strong {
  display: block;
  margin-bottom: 4px;
  color: #0f172a;
  font-size: 14px;
}

.collaboration-inspector-summary span {
  color: #64748b;
  font-size: 12px;
}

.collaboration-inspector-participants {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  max-width: 56%;
}

.collaboration-inspector-participants span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-weight: 700;
}

.collaboration-inspector-participants em {
  color: #0f766e;
  font-style: normal;
}

.collaboration-inspector-tabs {
  min-height: 0;
  flex: 1;
}

.collaboration-playground-panel {
  height: calc(100vh - 230px);
  min-height: 520px;
}

.collaboration-playground-panel :deep(.graph-shell),
.collaboration-playground-panel :deep(.trace-shell) {
  height: 100%;
  min-height: 520px;
}

.collaboration-playground-panel :deep(.graph-canvas-wrap) {
  min-height: 420px;
}

.collaboration-participants {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.collaboration-participant {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 700;
}

.collaboration-participant em {
  font-style: normal;
  color: #0f766e;
  font-size: 11px;
}

.collaboration-detail {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.collaboration-report-card {
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
}

.collaboration-report-title {
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
}

.collaboration-report-body {
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.collaboration-report-body :deep(p) {
  margin: 0 0 8px;
}

.collaboration-report-body :deep(p:last-child) {
  margin-bottom: 0;
}

.collaboration-trace-list {
  display: grid;
  gap: 6px;
}

.collaboration-trace-item {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.message-media {
  padding: 12px;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(226, 232, 240, 0.4);
}
.generated-image {
  width: min(100%, 460px);
  border-radius: 12px;
  overflow: hidden;
}
.generated-audio {
  width: min(100%, 460px);
}
.generated-video {
  width: min(100%, 520px);
  border-radius: 12px;
  background: #0f172a;
}

:deep(.el-image-viewer__wrapper) {
  z-index: 4000 !important;
}

/* Input Area Fixed to Bottom */
.input-area {
  padding: 16px 24px 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.92) 30%, #ffffff 100%);
  z-index: 5;
  width: 100%;
  max-width: calc(var(--chat-content-max-width) + 48px);
  margin: 0 auto;
}

.input-area-wrapper, .composer-placeholder {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(203, 213, 225, 0.8);
  border-radius: 20px;
  padding: 14px 16px;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.06);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.input-area-wrapper:focus-within, .composer-placeholder:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 8px 32px rgba(59, 130, 246, 0.12), 0 0 0 1px #93c5fd;
}

.input-area-wrapper :deep(.el-textarea__inner),
.composer-placeholder :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  font-size: 15px;
  line-height: 1.6;
  color: #1e293b;
  padding: 0;
  resize: none;
}
.input-area-wrapper :deep(.el-textarea__inner::placeholder),
.composer-placeholder :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}

.quick-config-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.quick-config-row.compact {
  margin-bottom: 10px;
}

.quick-config-chip {
  border: 1px solid #d7e3ef;
  background: #f8fbff;
  color: #334155;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.18s ease;
}

.quick-config-chip:hover {
  border-color: #93c5fd;
  color: #0f766e;
  background: #eff6ff;
}

.quick-config-chip.is-toggle.active {
  border-color: #14b8a6;
  color: #0f766e;
  background: #ccfbf1;
}

.quick-config-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.input-actions, .composer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.composer-left-tools {
  display: flex;
  gap: 8px;
}
.plus-trigger {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #f1f5f9;
  color: #64748b;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: 0.2s ease;
}
.plus-trigger:hover {
  background: #e2e8f0;
  color: #0f172a;
}
.plus-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.input-plus {
  margin-right: 0;
}

.collaboration-toggle-chip {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #d7e3ef;
  border-radius: 999px;
  background: #f8fbff;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: 0.18s ease;
}

.collaboration-toggle-chip:hover {
  border-color: #5eead4;
  color: #0f766e;
  background: #f0fdfa;
}

.collaboration-toggle-chip.active {
  border-color: #14b8a6;
  color: #0f766e;
  background: #ccfbf1;
}

.collaboration-toggle-chip:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.attached-file-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.attached-file-name {
  font-size: 12px;
  color: #0f766e;
  background: rgba(237, 249, 247, 0.92);
  border: 1px solid #99f6e4;
  border-radius: 999px;
  padding: 3px 10px;
}
.attached-file-remove {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}
.attached-file-remove:hover {
  color: #ef4444;
}

.hidden-file-input {
  display: none;
}

.composer-chip {
  background: rgba(241, 245, 249, 0.8);
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: #475569;
  margin-right: 12px;
}

.composer-send-btn,
.input-actions .el-button--primary {
  border-radius: 999px;
  padding: 8px 20px;
  font-weight: 600;
  background: var(--orin-primary, #3b82f6);
  border: none;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.composer-send-btn:not(:disabled):hover,
.input-actions .el-button--primary:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.35);
}

.input-hint {
  margin-right: auto;
  max-width: min(100%, 520px);
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.input-hint span + span {
  margin-left: 8px;
}

.input-hint-agent {
  color: #334155;
  font-weight: 600;
}

.quick-prompts {
  margin: 16px auto 0;
  max-width: 760px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.prompt-tag {
  border: 1px solid rgba(15, 159, 149, 0.42) !important;
  background: rgba(237, 249, 247, 0.95) !important;
  color: #0f766e !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  padding: 7px 12px !important;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-tag:hover {
  background: #dff8f2 !important;
  border-color: rgba(15, 159, 149, 0.6) !important;
  color: #0d6b63 !important;
  transform: translateY(-1px);
}

/* 5. Right Config Sidebar
-------------------------------------------------- */
.config-header {
  padding: 12px 14px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #e8e8e8;
}
.config-select {
  flex: 1;
}
.config-header :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #efefef;
  box-shadow: 0 0 0 1px #e6e6e6 inset !important;
  min-height: 42px;
}
.config-header :deep(.el-input__inner) {
  font-size: 15px;
  color: #4a5568;
}

.config-tabs {
  margin: 14px 14px 6px;
  padding: 4px;
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
}
.config-tabs :deep(.el-tabs__header) {
  width: 100%;
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0 !important;
  border-radius: 0;
}
.config-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.config-tabs :deep(.el-tabs__header),
.config-tabs :deep(.el-tabs__nav-wrap),
.config-tabs :deep(.el-tabs__nav-scroll),
.config-tabs :deep(.el-tabs__nav) {
  width: 100%;
}
.config-tabs :deep(.el-tabs__nav) {
  display: flex;
  gap: 4px;
  background: transparent;
  border: 0;
  border-radius: 0;
  padding: 0 !important;
  box-shadow: none;
}
.config-tabs :deep(.el-tabs__item) {
  width: 33.33%;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0 !important;
  border-radius: 8px;
  font-size: 13px;
  color: var(--sidebar-text-muted);
  padding: 0 !important;
  line-height: 32px;
  transform: translateY(0);
  text-align: center;
  font-weight: 600;
  letter-spacing: 0;
  transition: all 0.2s ease;
  background: transparent;
}
.config-tabs :deep(.el-tabs__item:hover) {
  background: rgba(226, 232, 240, 0.7);
  color: var(--sidebar-text);
}
.config-tabs :deep(.el-tabs__item.is-active) {
  background: #ffffff;
  color: var(--sidebar-text-strong) !important;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
.config-tabs :deep(.el-tabs__item.is-active:hover) {
  background: #ffffff;
}
.config-tabs :deep(.el-tabs__active-bar) { display: none; }

.config-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px 14px;
}

.config-card {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--sidebar-line);
  border-radius: 14px;
  padding: 12px;
  margin-bottom: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.config-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.config-card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--sidebar-text-strong);
}

.config-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-width: 28px;
  height: 22px;
  padding: 0 8px;
  background: #f1f5f9;
  color: #52657a;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  transform: translateY(-1px);
}
.config-badge.soft {
  background: rgba(220, 252, 245, 0.9);
  color: #0f766e;
}
.config-badge.soft.agent-status-idle {
  background: rgba(220, 252, 245, 0.9);
  color: #0f766e;
}
.config-badge.soft.agent-status-running {
  background: rgba(219, 234, 254, 0.92);
  color: #1d4ed8;
}
.config-badge.soft.agent-status-fallback {
  background: rgba(254, 249, 195, 0.95);
  color: #854d0e;
}
.config-badge.soft.agent-status-error {
  background: rgba(254, 226, 226, 0.95);
  color: #991b1b;
}

.prompt-editor {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  margin-top: 4px;
}
.prompt-editor-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid #f1f5f9;
}
.prompt-editor-icon {
  font-size: 18px;
  color: #64748b;
  margin-top: 1px;
  flex-shrink: 0;
}
.prompt-editor-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text-strong);
}
.prompt-editor-subtitle {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.prompt-message-block {
  border-bottom: 1px solid #f1f5f9;
  padding: 10px 12px;
}
.prompt-message-block :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 12px;
  color: #475569;
  background: transparent;
  resize: none;
}
.prompt-message-role-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.role-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.role-dot--system { background: #6366f1; }
.role-dot--user   { background: #10b981; }
.role-dot--assistant { background: #f59e0b; }
.role-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}
.remove-msg-btn {
  margin-left: auto;
  color: #94a3b8 !important;
  font-size: 14px;
}
.prompt-add-row {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
}
.prompt-add-btn {
  flex: 1;
  border: 1px dashed #cbd5e1 !important;
  color: #64748b !important;
  background: transparent !important;
  border-radius: 6px !important;
  font-size: 12px !important;
}
.prompt-add-btn:hover {
  border-color: #94a3b8 !important;
  color: #334155 !important;
}

html.dark .prompt-editor {
  border-color: rgba(255,255,255,0.08);
}
html.dark .prompt-editor-header,
html.dark .prompt-message-block {
  border-bottom-color: rgba(255,255,255,0.06);
}
html.dark .prompt-editor-icon { color: #94a3b8; }
html.dark .prompt-editor-subtitle { color: #64748b; }
html.dark .role-label { color: #94a3b8; }
html.dark .prompt-add-btn {
  border-color: rgba(255,255,255,0.1) !important;
  color: #94a3b8 !important;
}

.card-toggle-head {
  cursor: pointer;
  user-select: none;
  margin-bottom: 0;
  min-height: 40px;
  align-items: center;
}
.card-toggle-head:hover .config-card-title {
  opacity: 0.75;
}
.card-toggle-head .config-card-title {
  line-height: 1;
  transform: translateY(-1px);
}
.card-toggle-head .config-badge {
  line-height: 1;
  transform: translateY(-1px);
}
.card-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  line-height: 1;
  color: #94a3b8;
  transform: translateY(-1px);
  transition: transform 0.2s ease;
}
.card-chevron.expanded {
  transform: translateY(-1px) rotate(180deg);
}
.collapsible-card {
  padding-bottom: 12px;
}
.collapsible-card .config-row:last-child {
  padding-bottom: 8px;
}

.tool-status-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.tool-status-badge.tool-active {
  background: rgba(220, 252, 245, 0.9);
  color: #0f766e;
}
.tool-status-badge.tool-inactive {
  background: #f1f5f9;
  color: #94a3b8;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  color: var(--sidebar-text);
  border-bottom: 1px solid #eef3f8;
}
.config-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.config-row span {
  color: var(--sidebar-text-muted);
}
.param-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #eef3f8;
}
.param-group:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.param-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.param-label-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.param-label {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.param-desc {
  font-size: 12px;
  color: #64748b;
}
.value-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
}
.config-row :deep(.el-input),
.config-row :deep(.el-select),
.config-row :deep(.el-input-number) {
  width: 190px;
}
.param-group :deep(.el-textarea__inner) {
  border-radius: 10px;
}
.editable-agent-name {
  cursor: text;
  border-bottom: 1px dashed rgba(15, 159, 149, 0.45);
}
.agent-name-input {
  width: 220px;
}
.agent-name-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #99f6e4 inset !important;
}
.model-switcher {
  width: 190px;
}
.model-switcher :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: var(--sidebar-soft-bg);
  box-shadow: 0 0 0 1px var(--sidebar-line) inset !important;
}
.mode-readonly {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #0f766e;
  background: rgba(237, 249, 247, 0.92);
  border: 1px solid #99f6e4;
  border-radius: 999px;
  padding: 2px 10px;
}

.config-description, .config-card-desc {
  font-size: 12px;
  color: var(--sidebar-text-muted);
  line-height: 1.6;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #eef3f8;
}

.selection-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.selection-tag {
  padding: 4px 12px;
  background: var(--sidebar-soft-bg);
  border: 1px solid var(--sidebar-line);
  border-radius: 999px;
  font-size: 12px;
  color: #52657a;
  cursor: pointer;
  transition: 0.2s ease;
}
.selection-tag:hover, .selection-tag.active {
  background: rgba(237, 249, 247, 0.92);
  border-color: #99f6e4;
  color: #0f766e;
}

.selection-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  max-height: 240px;
  overflow-y: auto;
}
.selection-list::-webkit-scrollbar {
  width: 4px;
}
.selection-list::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.selection-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 11px;
  background: var(--sidebar-soft-bg);
  border-radius: 12px;
  border: 1px solid var(--sidebar-line);
  cursor: pointer;
  transition: 0.2s ease;
}
.selection-item:hover {
  background: var(--sidebar-hover-bg);
  border-color: rgba(203, 213, 225, 0.95);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.selection-info {
  flex: 1;
  min-width: 0;
}
.selection-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text);
  margin-bottom: 2px;
}
.selection-meta {
  font-size: 11px;
  color: #94a3b8;
}

.doc-filter-section {
  margin: 8px 2px 0;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid #e4edf6;
  background: #f8fbff;
}
.doc-filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}
.doc-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  line-height: 1.2;
}
.doc-filter-chevron {
  font-size: 14px;
  color: #64748b;
  transition: transform 0.2s ease;
}
.doc-filter-chevron.expanded {
  transform: rotate(180deg);
}
.doc-filter-count {
  font-size: 12px;
  font-weight: 600;
  color: #0f766e;
}
.doc-filter-list {
  margin-top: 8px;
  max-height: 180px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}
.doc-filter-list::-webkit-scrollbar {
  width: 4px;
}
.doc-filter-list::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.doc-filter-loading {
  font-size: 12px;
  color: #94a3b8;
  padding: 8px 6px;
}
.doc-filter-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  cursor: pointer;
}
.doc-filter-item:hover {
  border-color: #cbd5e1;
  background: #fdfefe;
}
.doc-filter-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.45;
  color: #334155;
  word-break: break-word;
}

.config-footer {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--sidebar-line);
  background: rgba(255, 255, 255, 0.82);
}
.config-footer .el-button {
  width: 100%;
  border-radius: 10px;
  height: 38px;
  font-weight: 600;
  border: none;
  background: var(--sidebar-accent);
  box-shadow: 0 3px 10px rgba(15, 159, 149, 0.26);
}

.loading-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  color: #64748b;
  font-size: 13px;
}

/* Quick prompt fix */
.quick-prompts :deep(.el-tag) {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(4px);
}

@media (max-width: 1100px) {
  .welcome-panel {
    margin-top: 24px;
  }

  .messages-container.is-empty > .welcome-panel {
    transform: none;
  }

}

@media (max-width: 820px) {
  .messages-container {
    padding: 18px 14px;
  }

  .welcome-panel h2 {
    font-size: 24px;
  }

  .composer-placeholder {
    max-width: 100%;
  }

  .quick-config-row {
    margin-bottom: 10px;
  }

  .quick-prompts {
    justify-content: center;
  }
}

/* =========================================================================
   Dark Mode Overrides — html.dark
   ========================================================================= */
html.dark .agent-workspace {
  --sidebar-text-strong: #f1f5f9;
  --sidebar-text: #94a3b8;
  --sidebar-text-muted: #64748b;
  --sidebar-line: rgba(255, 255, 255, 0.1);
  --sidebar-soft-bg: rgba(15, 28, 28, 0.8);
  --sidebar-hover-bg: rgba(30, 41, 59, 0.9);
  --sidebar-active-bg: rgba(38, 255, 223, 0.08);
  background-color: #0f172a;
}

html.dark .workspace-sidebar,
html.dark .workspace-config-pane {
  background: rgba(10, 22, 22, 0.95);
}

html.dark .d-overlay {
  background: rgba(0, 0, 0, 0.4);
}

html.dark .sidebar-tabs {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .sidebar-tab {
  color: #94a3b8;
}

html.dark .sidebar-tab:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #f1f5f9;
}

html.dark .sidebar-tab.active {
  background: #0f1c1c;
  color: #f1f5f9;
}

html.dark .sidebar-tab-badge,
html.dark .collaboration-sidebar-head > span {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}

html.dark .sidebar-agent-switch {
  background: rgba(15, 28, 28, 0.72);
  border-color: rgba(255, 255, 255, 0.08);
}

html.dark .collaboration-sidebar-head,
html.dark .collaboration-run-item {
  background: rgba(15, 28, 28, 0.72);
  border-color: rgba(255, 255, 255, 0.08);
}

html.dark .workspace-collaboration-pane {
  background: rgba(9, 16, 24, 0.82);
}

html.dark .collaboration-empty {
  background: rgba(15, 28, 28, 0.72);
  border-color: rgba(255, 255, 255, 0.12);
}

html.dark .collaboration-empty-action {
  color: #26ffdf;
}

html.dark .collaboration-sidebar-head h3,
html.dark .collaboration-run-top strong {
  color: #e2e8f0;
}

html.dark .collaboration-run-participants span {
  background: rgba(255, 255, 255, 0.08);
  color: #cbd5e1;
}

html.dark .collaboration-run-actions button {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(38, 255, 223, 0.22);
  color: #26FFDF;
}

html.dark .workspace-sidebar ::-webkit-scrollbar-thumb,
html.dark .workspace-config-pane ::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
}

html.dark .workspace-sidebar ::-webkit-scrollbar-thumb:hover,
html.dark .workspace-config-pane ::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.25);
}

html.dark .session-item {
  background: rgba(15, 28, 28, 0.6);
  border-color: rgba(255, 255, 255, 0.08);
}

html.dark .session-item:hover {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.12);
}

html.dark .session-item.active {
  background: rgba(38, 255, 223, 0.08);
  border-color: rgba(38, 255, 223, 0.3);
  box-shadow: 0 1px 0 rgba(38, 255, 223, 0.1);
}

html.dark .session-title {
  color: #cbd5e1;
}

html.dark .session-meta {
  color: #64748b;
}

html.dark .collapsed-restore-panel {
  background: transparent;
}

html.dark .collapsed-rail-handle,
html.dark .session-edge-handle {
  background: rgba(15, 28, 28, 0.96);
  border-color: rgba(38, 255, 223, 0.16);
  color: #8cd7cf;
  box-shadow: 10px 14px 28px -22px rgba(0, 0, 0, 0.75);
}

html.dark .session-edge-handle {
  box-shadow: 0 0 0 4px #050d12, 0 10px 22px -16px rgba(0, 0, 0, 0.78);
}

html.dark .collapsed-rail-handle:hover,
html.dark .collapsed-rail-handle:focus-visible,
html.dark .session-edge-handle:hover,
html.dark .session-edge-handle:focus-visible {
  border-color: rgba(38, 255, 223, 0.38);
  color: #26ffdf;
  box-shadow: 10px 14px 28px -22px rgba(38, 255, 223, 0.35);
}

html.dark .session-edge-handle:hover,
html.dark .session-edge-handle:focus-visible {
  box-shadow: 0 0 0 4px #050d12, 0 12px 24px -16px rgba(38, 255, 223, 0.35);
}

html.dark .collapsed-session-dot {
  background: rgba(38, 255, 223, 0.12);
  color: #26ffdf;
}

html.dark .session-delete {
  color: #475569;
}

html.dark .session-delete:hover {
  color: #ef4444;
}

html.dark .collapse-btn {
  background: linear-gradient(180deg, rgba(20, 36, 36, 0.96) 0%, rgba(15, 28, 28, 0.96) 100%);
  border-color: rgba(38, 255, 223, 0.24);
  color: #8cd7cf;
  box-shadow: 0 10px 18px -14px rgba(0, 0, 0, 0.65);
}

html.dark .collapse-btn:hover {
  background: linear-gradient(180deg, rgba(30, 56, 56, 0.96) 0%, rgba(21, 46, 46, 0.96) 100%);
  border-color: rgba(38, 255, 223, 0.45);
  color: #26ffdf;
  box-shadow: 0 12px 22px -14px rgba(38, 255, 223, 0.35);
}

html.dark .workspace-main {
  background: #0f172a;
}

html.dark .messages-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
}

html.dark .welcome-panel h2 {
  color: #f1f5f9;
}

html.dark .mode-tag {
  border-color: rgba(255, 255, 255, 0.15) !important;
  background: rgba(15, 28, 28, 0.8) !important;
  color: #94a3b8 !important;
  box-shadow: none;
}

html.dark .mode-tag:hover {
  background: rgba(30, 41, 59, 0.9) !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

html.dark .mode-tag.el-tag--primary {
  border-color: rgba(38, 255, 223, 0.3) !important;
  background: rgba(38, 255, 223, 0.1) !important;
  color: #26FFDF !important;
  box-shadow: 0 2px 8px rgba(38, 255, 223, 0.15);
}

html.dark .message-avatar {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

html.dark .message-item.user .message-avatar {
  background: #26FFDF;
  color: #041010;
  box-shadow: 0 4px 12px rgba(38, 255, 223, 0.25);
}

html.dark .message-role {
  color: #64748b;
}

html.dark .message-time,
html.dark .message-meta {
  color: #64748b;
}

html.dark .runtime-chip {
  background: rgba(38, 255, 223, 0.1);
  border-color: rgba(38, 255, 223, 0.34);
  color: #5fffe5;
}

html.dark .reasoning-section {
  background: rgba(15, 28, 28, 0.6);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

html.dark .reasoning-title {
  color: #94a3b8;
}

html.dark .reasoning-item {
  background: #0f1c1c;
  border-color: rgba(255, 255, 255, 0.08);
}

html.dark .reasoning-item:hover {
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

html.dark .reasoning-step-dot {
  background: #1e293b;
  color: #94a3b8;
}

html.dark .trace-success .reasoning-step-dot { background: rgba(38, 255, 223, 0.15); color: #26FFDF; }
html.dark .trace-running .reasoning-step-dot { background: rgba(96, 165, 250, 0.2); color: #93c5fd; }
html.dark .trace-warning .reasoning-step-dot { background: rgba(251, 191, 36, 0.15); color: #fbbf24; }
html.dark .trace-error .reasoning-step-dot { background: rgba(248, 113, 113, 0.15); color: #f87171; }

html.dark .reasoning-msg {
  color: #94a3b8;
}

html.dark .reasoning-name {
  color: #cbd5e1;
}

html.dark .reasoning-status {
  background: rgba(148, 163, 184, 0.15);
  color: #cbd5e1;
}

html.dark .reasoning-status.status-success {
  background: rgba(38, 255, 223, 0.15);
  color: #5fffe5;
}

html.dark .reasoning-status.status-running {
  background: rgba(96, 165, 250, 0.2);
  color: #93c5fd;
}

html.dark .reasoning-status.status-warning {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
}

html.dark .reasoning-status.status-error {
  background: rgba(248, 113, 113, 0.15);
  color: #f87171;
}

html.dark .reasoning-duration {
  color: #94a3b8;
}

html.dark .reasoning-expand-btn {
  border-color: rgba(148, 163, 184, 0.3);
  background: rgba(30, 41, 59, 0.7);
  color: #e2e8f0;
}

html.dark .reasoning-top:hover .reasoning-expand-btn {
  border-color: rgba(38, 255, 223, 0.5);
  background: rgba(38, 255, 223, 0.12);
  color: #5fffe5;
}

html.dark .reasoning-detail {
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(15, 23, 42, 0.7);
}

html.dark .reasoning-detail pre {
  color: #cbd5e1;
}

html.dark .citation-item {
  background: rgba(15, 28, 28, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

html.dark .citation-item:hover {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(38, 255, 223, 0.3);
}

html.dark .retrieved-context {
  background: rgba(15, 28, 28, 0.82);
  border-color: rgba(255, 255, 255, 0.12);
}

html.dark .context-toggle:hover {
  background: rgba(148, 163, 184, 0.08);
}

html.dark .context-header-left {
  color: #e2e8f0;
}

html.dark .context-header-left :deep(.el-icon) {
  color: #94a3b8;
}

html.dark .context-toggle-text {
  color: #5eead4;
}

html.dark .context-body {
  border-top-color: rgba(255, 255, 255, 0.1);
}

html.dark .context-item {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .chunk-source {
  color: #26FFDF;
}

html.dark .chunk-source.is-clickable:hover {
  color: #5fffe5;
}

html.dark .chunk-text {
  color: #cbd5e1;
}

html.dark .message-text {
  background: #1e293b;
  color: #f1f5f9;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}

html.dark .message-item.user .message-text {
  background: linear-gradient(135deg, #26FFDF, #0f9f95);
  color: #041010;
  border: none;
  box-shadow: 0 4px 12px rgba(38, 255, 223, 0.2);
}

html.dark .message-text :deep(pre) {
  background: #0a1616;
  color: #f1f5f9;
}

html.dark .message-item:not(.user) .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.1);
  color: #26FFDF;
}

html.dark .collaboration-process {
  background: rgba(15, 28, 28, 0.82);
  border-color: rgba(38, 255, 223, 0.22);
}

html.dark .collaboration-process-title {
  color: #e2e8f0;
}

html.dark .collaboration-process-title strong,
html.dark .collaboration-process-toggle {
  color: #26FFDF;
}

html.dark .collaboration-detail-link {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(38, 255, 223, 0.22);
  color: #26FFDF;
}

html.dark .collaboration-process-footer {
  border-top-color: rgba(255, 255, 255, 0.1);
}

html.dark .collaboration-inspector-summary {
  background: rgba(15, 28, 28, 0.82);
  border-color: rgba(38, 255, 223, 0.18);
}

html.dark .collaboration-inspector-summary strong {
  color: #e2e8f0;
}

html.dark .collaboration-inspector-participants span {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}

html.dark .collaboration-participant {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}

html.dark .collaboration-report-card {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .collaboration-report-title {
  color: #f1f5f9;
}

html.dark .collaboration-report-body,
html.dark .collaboration-trace-item {
  color: #cbd5e1;
}

html.dark .message-media {
  background: #1e293b;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}

html.dark .generated-video {
  background: #0a1616;
}

html.dark .input-area {
  background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.9) 30%, #0f172a 100%);
}

html.dark .input-area-wrapper,
html.dark .composer-placeholder {
  background: rgba(10, 22, 22, 0.9);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

html.dark .input-area-wrapper:focus-within,
html.dark .composer-placeholder:focus-within {
  border-color: #26FFDF;
  box-shadow: 0 8px 32px rgba(38, 255, 223, 0.15), 0 0 0 1px rgba(38, 255, 223, 0.3);
}

html.dark .input-area-wrapper :deep(.el-textarea__inner),
html.dark .composer-placeholder :deep(.el-textarea__inner) {
  color: #f1f5f9;
}

html.dark .input-area-wrapper :deep(.el-textarea__inner::placeholder),
html.dark .composer-placeholder :deep(.el-textarea__inner::placeholder) {
  color: #64748b;
}

html.dark .quick-config-chip {
  border-color: rgba(255, 255, 255, 0.15);
  background: rgba(15, 28, 28, 0.8);
  color: #94a3b8;
}

html.dark .quick-config-chip:hover {
  border-color: rgba(38, 255, 223, 0.4);
  color: #26FFDF;
  background: rgba(38, 255, 223, 0.08);
}

html.dark .plus-trigger {
  background: #1e293b;
  color: #94a3b8;
}

html.dark .plus-trigger:hover {
  background: #334155;
  color: #26FFDF;
}

html.dark .collaboration-toggle-chip {
  border-color: rgba(255, 255, 255, 0.15);
  background: rgba(15, 28, 28, 0.8);
  color: #94a3b8;
}

html.dark .collaboration-toggle-chip:hover,
html.dark .collaboration-toggle-chip.active {
  border-color: rgba(38, 255, 223, 0.4);
  color: #26FFDF;
  background: rgba(38, 255, 223, 0.1);
}

html.dark .attached-file-name {
  color: #26FFDF;
  background: rgba(38, 255, 223, 0.1);
  border-color: rgba(38, 255, 223, 0.3);
}

html.dark .attached-file-remove {
  color: #64748b;
}

html.dark .attached-file-remove:hover {
  color: #f87171;
}

html.dark .composer-chip {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
}

html.dark .composer-send-btn,
html.dark .input-actions .el-button--primary {
  background: #26FFDF;
  color: #041010;
  box-shadow: 0 4px 12px rgba(38, 255, 223, 0.3);
}

html.dark .composer-send-btn:not(:disabled):hover,
html.dark .input-actions .el-button--primary:not(:disabled):hover {
  box-shadow: 0 6px 16px rgba(38, 255, 223, 0.4);
}

html.dark .input-hint {
  color: #64748b;
}

html.dark .prompt-tag {
  border-color: rgba(38, 255, 223, 0.3) !important;
  background: rgba(38, 255, 223, 0.08) !important;
  color: #26FFDF !important;
}

html.dark .prompt-tag:hover {
  background: rgba(38, 255, 223, 0.15) !important;
  border-color: rgba(38, 255, 223, 0.5) !important;
  color: #5fffe5 !important;
}

html.dark .config-header {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

html.dark .config-header :deep(.el-input__wrapper) {
  background: #1e293b !important;
  box-shadow: 0 0 0 1px #334155 inset !important;
}

html.dark .config-header :deep(.el-input__inner) {
  color: #f1f5f9;
}

html.dark .config-tabs {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .config-tabs :deep(.el-tabs__nav) {
  background: transparent;
  border-color: transparent;
  box-shadow: none;
}

html.dark .config-tabs :deep(.el-tabs__item) {
  color: #94a3b8;
}

html.dark .config-tabs :deep(.el-tabs__item:hover) {
  background: rgba(255, 255, 255, 0.05);
  color: #f1f5f9;
}

html.dark .config-tabs :deep(.el-tabs__item.is-active) {
  background: #0f1c1c;
  color: #f1f5f9 !important;
  box-shadow: none;
}

html.dark .config-tabs :deep(.el-tabs__item.is-active:hover) {
  background: #0f1c1c;
}

html.dark .config-card {
  background: rgba(10, 22, 22, 0.8);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

html.dark .config-card-title {
  color: #f1f5f9;
}

html.dark .config-badge {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
}

html.dark .config-badge.soft {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}
html.dark .config-badge.soft.agent-status-idle {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}
html.dark .config-badge.soft.agent-status-running {
  background: rgba(96, 165, 250, 0.2);
  color: #93c5fd;
}
html.dark .config-badge.soft.agent-status-fallback {
  background: rgba(251, 191, 36, 0.2);
  color: #fcd34d;
}
html.dark .config-badge.soft.agent-status-error {
  background: rgba(248, 113, 113, 0.2);
  color: #fca5a5;
}

html.dark .tool-status-badge.tool-active {
  background: rgba(38, 255, 223, 0.1);
  color: #26FFDF;
}
html.dark .tool-status-badge.tool-inactive {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
}

html.dark .config-row {
  color: #94a3b8;
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .config-row span {
  color: #64748b;
}

html.dark .param-group {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .param-label {
  color: #cbd5e1;
}

html.dark .param-desc {
  color: #64748b;
}

html.dark .value-badge {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
}

html.dark .mode-readonly {
  color: #26FFDF;
  background: rgba(38, 255, 223, 0.1);
  border-color: rgba(38, 255, 223, 0.3);
}

html.dark .config-description,
html.dark .config-card-desc {
  color: #64748b;
  border-top-color: rgba(255, 255, 255, 0.06);
}

html.dark .config-footer {
  border-top-color: rgba(255, 255, 255, 0.1);
  background: rgba(10, 22, 22, 0.82);
}

html.dark .loading-indicator {
  color: #64748b;
}

html.dark .quick-prompts :deep(.el-tag) {
  background: rgba(15, 28, 28, 0.8);
}

html.dark .selection-tag {
  background: rgba(15, 28, 28, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .selection-name {
  color: #94a3b8;
}

html.dark .selection-meta {
  color: #64748b;
}

html.dark .agent-name-input :deep(.el-input__wrapper) {
  background: #0f1c1c !important;
  box-shadow: 0 0 0 1px rgba(38, 255, 223, 0.3) inset !important;
}

html.dark .model-switcher :deep(.el-input__wrapper) {
  background: rgba(15, 28, 28, 0.8) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
}

html.dark .state-panel {
  color: #64748b;
}

html.dark .empty-state-title {
  color: #94a3b8;
}

html.dark .empty-state-desc {
  color: #64748b;
}

/* Global scrollbar in dark mode for messages container */
html.dark .messages-container::-webkit-scrollbar {
  width: 6px;
}

html.dark .messages-container::-webkit-scrollbar-track {
  background: transparent;
}

html.dark .messages-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 6px;
}

html.dark .messages-container::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.25);
}
</style>
