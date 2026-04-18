<template>
  <div class="agent-workspace" ref="containerRef" :class="{ 'is-wide': isWide, 'is-medium': isMedium, 'is-narrow': isNarrow }">
    
    <div v-if="isLeftDrawer && !sessionPaneCollapsed" class="d-overlay" @click="sessionPaneCollapsed = true"></div>
    <aside class="workspace-sidebar" :class="{ 'is-drawer': isLeftDrawer, 'is-collapsed': sessionPaneCollapsed }">
      
      <div v-if="!sessionPaneCollapsed" class="sidebar-tabs">
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'session' }" @click="sidebarTab = 'session'">会话记录</div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'config' }" @click="sidebarTab = 'config'">工作台设置</div>
      </div>
      <div v-if="!sessionPaneCollapsed" class="sidebar-agent-switch">
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
        <div class="session-collapse-handle">
          <el-button
            class="collapse-btn"
            circle
            :icon="sessionPaneCollapsed ? ArrowRight : ArrowLeft"
            @click="sessionPaneCollapsed = !sessionPaneCollapsed"
          />
        </div>

        <div v-if="sessionPaneCollapsed" class="collapsed-pane">
          <el-button
            class="collapsed-new-btn"
            circle
            :icon="Plus"
            :disabled="!currentAgentId"
            @click="newSession"
          />
        </div>

        <template v-else>
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
      <div v-show="sidebarTab === 'config' && !sessionPaneCollapsed" class="workspace-config-pane">

        <el-tabs v-model="activeConfigTab" class="config-tabs">
          <el-tab-pane label="模型" name="model" />
          <el-tab-pane label="工具" name="tools" />
          <el-tab-pane label="其他" name="other" />
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
                <div class="param-group">
                  <div class="param-header">
                    <div class="param-label-wrap">
                      <span class="param-label">System Prompt</span>
                      <span class="param-desc">设置角色、语气和输出约束</span>
                    </div>
                  </div>
                  <el-input
                    v-model="agentRuntimeForm.systemPrompt"
                    type="textarea"
                    :rows="5"
                    placeholder="定义智能体的身份、回复风格和约束条件..."
                  />
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
              <div class="config-row">
                <span>快捷建议</span>
                <el-switch v-model="currentConfig.enableSuggestions" />
              </div>
              <div class="config-row">
                <span>显示检索上下文</span>
                <el-switch v-model="currentConfig.showRetrievedContext" />
              </div>
            </section>
          </template>

          <template v-else-if="activeConfigTab === 'tools'">
            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  知识库
                </div>
                <div class="config-badge">
                  {{ attachedKbIds.length }}/{{ knowledgeBases.length }}
                </div>
              </div>
              <div class="config-card-desc">
                附加到当前会话后，回复会参考检索结果。
              </div>
              <el-input
                v-model="kbSearch"
                placeholder="搜索知识库..."
                :prefix-icon="Search"
                clearable
              />
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
                  <!-- Document filter expand button (only for attached KBs) -->
                  <div v-if="isKbAttached(kb.id)" class="doc-filter-section">
                    <div class="doc-filter-header" @click="toggleKbExpand(kb.id)">
                      <span class="doc-filter-label">
                        <el-icon><component :is="isKbExpanded(kb.id) ? ArrowUp : ArrowDown" /></el-icon>
                        {{ isKbExpanded(kb.id) ? '收起' : '按文档过滤' }}
                        <span v-if="kbDocFilters[kb.id]?.length" class="doc-filter-count">
                          ({{ kbDocFilters[kb.id].length }})
                        </span>
                      </span>
                    </div>
                    <div v-if="isKbExpanded(kb.id)" class="doc-filter-list">
                      <div v-if="!kbDocuments[kb.id]?.length" class="doc-filter-loading">
                        加载中...
                      </div>
                      <label
                        v-for="doc in kbDocuments[kb.id]"
                        :key="doc.id"
                        class="doc-filter-item"
                      >
                        <el-checkbox
                          :model-value="isDocSelected(kb.id, doc.id)"
                          @change="toggleDocFilter(kb.id, doc.id)"
                        />
                        <span class="doc-filter-name">{{ doc.name || doc.fileName || doc.id }}</span>
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  MCP 服务
                </div>
                <div class="config-badge">
                  {{ currentConfig.mcpIds.length }}/{{ mcpServices.length }}
                </div>
              </div>
              <div class="config-card-desc">
                选中的服务会作为当前工作台的扩展能力。
              </div>
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
            </section>

            <section class="config-card">
              <div class="config-card-head">
                <div class="config-card-title">
                  Skills
                </div>
                <div class="config-badge">
                  {{ currentConfig.skillIds.length }}/{{ skills.length }}
                </div>
              </div>
              <div class="config-card-desc">
                把常用技能固定在当前配置里，便于连续对话时使用。
              </div>
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
                  <span>{{ msg.role === 'user' ? '你' : currentAgent.name }}</span>
                  <span v-if="msg.createdAt" class="message-time">{{ formatMessageTime(msg.createdAt) }}</span>
                  <span v-if="msg.role === 'assistant' && (msg.model || msg.provider)" class="message-meta">
                    <span v-if="msg.model">{{ msg.model }}</span>
                    <span v-if="msg.promptTokens || msg.completionTokens" class="meta-tokens">
                      ↑{{ msg.promptTokens || 0 }} ↓{{ msg.completionTokens || 0 }}
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
                      v-for="(trace, traceIdx) in msg.toolTraces"
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
                          <span class="reasoning-expand">
                            {{ isTraceDetailExpanded(msg, index, traceIdx) ? '收起' : '详情' }}
                          </span>
                        </div>
                        <div class="reasoning-msg">{{ trace.message }}</div>
                        <div
                          v-if="trace.detail && isTraceDetailExpanded(msg, index, traceIdx)"
                          class="reasoning-detail"
                        >
                          <pre>{{ typeof trace.detail === 'object' ? JSON.stringify(trace.detail, null, 2) : trace.detail }}</pre>
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
                智能体：{{ currentAgent?.name || '未选择' }} · 模式：{{ currentInteractionLabel }} · 已附加知识库 {{ attachedKbIds.length }} 个
                <span v-if="totalFilteredDocs > 0"> · 文档过滤 {{ totalFilteredDocs }} 个</span>
                <span v-if="selectedUploadFileName"> · 文件：{{ selectedUploadFileName }}</span>
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
    
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { marked } from 'marked';
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Close,
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
  getAgentMetadata,
  uploadMultimodalFile,
  updateAgent,
} from '@/api/agent';
import {
  attachKnowledgeBase,
  createChatSession,
  deleteChatSession,
  detachKnowledgeBase,
  getAttachedKnowledgeBases,
  getChatSession,
  listAgents,
  listChatSessions,
  listKnowledgeBases,
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
marked.setOptions({
  gfm: true,
  breaks: true
});

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
const messages = ref([]);
const attachedKbIds = ref([]);
const kbDocFilters = reactive({});  // {[kbId]: string[]}
const kbDocuments = ref({});        // {[kbId]: documents[]}
const expandedKbIds = ref(new Set());
const currentAgentId = ref('');
const currentAgent = ref(null);
const currentSessionId = ref('');
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
const expandedRetrievedContext = ref({});
const expandedTraceDetails = ref({});
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
  enabled: agent.enabled
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

const restoreConfigForAgent = (agentId) => {
  Object.assign(currentConfig, defaultConfig());

  if (!agentId) return;

  const raw = localStorage.getItem(getConfigStorageKey(agentId));
  if (!raw) return;

  try {
    const parsed = JSON.parse(raw);
    Object.assign(currentConfig, defaultConfig(), parsed);
  } catch (error) {
    console.warn('Failed to parse workspace config:', error);
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
};

const loadAgentRuntimeConfig = async (agentId) => {
  if (!agentId) {
    Object.assign(agentRuntimeForm, defaultRuntimeForm());
    return;
  }
  try {
    const metadata = await getAgentMetadata(agentId);
    syncRuntimeFormFromMetadata(metadata || {});
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
        enabled: metadata.enabled ?? currentAgent.value?.enabled
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
              enabled: metadata.enabled ?? agent.enabled
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
    model: selectedModelName.value || currentAgent.value?.model || ''
  };
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
    systemPrompt: agentRuntimeForm.systemPrompt
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

const saveCurrentConfig = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    return;
  }
  if (activeConfigTab.value === 'model') {
    await saveAgentRuntimeConfig();
    return;
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
    if (presetId && agents.value.some((agent) => normalizeId(agent.id) === presetId)) {
      currentAgentId.value = presetId;
      currentAgent.value = findAgentById(presetId);
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
  restoreConfigForAgent(targetId);
  await loadAgentRuntimeConfig(targetId);
  activeConfigTab.value = 'tools';

  try {
    await loadSessions(targetId);
    if (!sessions.value.length) {
      await createSessionForAgent(targetId);
    }
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
  scrollToBottom();

  try {
    if (isImageModel.value || isVideoModel.value || isSpeechModel.value) {
      await sendMultimodalMessage(content || selectedUploadFileName.value || '');
    } else {
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
        await sendChatMessageStream(currentSessionId.value, {
          message: outboundMessage,
          kbIds: attachedKbIds.value.map(normalizeId),
          mcpIds: currentConfig.mcpIds || [],
          kbDocFilters: normalizeKbDocFilters(kbDocFilters)
        }, {
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
        const res = await sendChatMessage(currentSessionId.value, {
          message: outboundMessage,
          kbIds: attachedKbIds.value.map(normalizeId),
          mcpIds: currentConfig.mcpIds || [],
          kbDocFilters: normalizeKbDocFilters(kbDocFilters)
        });
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
      if (runtimeStatusLevel.value !== 'error') {
        runtimeStatusLevel.value = 'idle';
        runtimeStatusHint.value = '上一轮请求已完成';
      }
    }
    setCachedSessionMessages(currentSessionId.value, messages.value);
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

const formatTraceType = (type) => {
  const labelMap = {
    KB_STRUCTURE: '知识库结构检查',
    KB_SEARCH: '知识检索',
    KB_RETRIEVE: '上下文组装',
    KB_HINT: '检索提示',
    KB_PIPELINE: '检索链路',
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
    return marked.parse(escaped);
  } catch (error) {
    return escaped.replace(/\n/g, '<br>');
  }
};

const getMessageText = (msg) => {
  if (!msg) return '';
  if (typeof msg.content === 'string') return msg.content;
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
  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe(), loadModelCatalog()]);
  if (currentAgentId.value) {
    restoreConfigForAgent(currentAgentId.value);
    await loadAgentRuntimeConfig(currentAgentId.value);
    await loadSessions(currentAgentId.value, { autoSelect: !currentSessionId.value });
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

  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe(), loadModelCatalog()]);

  const presetId = props.presetAgentId ? String(props.presetAgentId) : '';
  // Restore saved agent (or use first available); preset has highest priority.
  const agentToRestore = (presetId && agents.value.find((a) => String(a.id) === presetId))
    ? presetId
    : (savedState?.currentAgentId && agents.value.find((a) => normalizeId(a.id) === normalizeId(savedState.currentAgentId))
      ? normalizeId(savedState.currentAgentId)
      : (agents.value[0]?.id || ''));

  if (agentToRestore) {
    currentAgentId.value = normalizeId(agentToRestore);
    currentAgent.value = findAgentById(agentToRestore);
    selectedModelName.value = currentAgent.value?.model || '';
    restoreConfigForAgent(agentToRestore);
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
  --left-pane-width: 320px;
  --right-pane-width: 0px;
  --drawer-left-width: 320px;
  --drawer-right-width: 320px;
  --chat-content-max-width: 900px;
  --sidebar-accent: var(--orin-primary, #0f9f95);
  --sidebar-text-strong: #0f172a;
  --sidebar-text: #334155;
  --sidebar-text-muted: #64748b;
  --sidebar-line: rgba(226, 232, 240, 0.9);
  --sidebar-soft-bg: rgba(248, 250, 252, 0.9);
  --sidebar-hover-bg: rgba(241, 245, 249, 0.9);
  --sidebar-active-bg: rgba(237, 249, 247, 0.92);
  position: relative;
  width: 100%;
  height: 100%; /* Fill the host shell height */
  display: flex;
  overflow: hidden; /* No global scrolling */
  background-color: #f6f9fb;
  font-family: "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

.agent-workspace.is-wide {
  --left-pane-width: 320px;
  --right-pane-width: 0px;
  --chat-content-max-width: 920px;
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

.sidebar-agent-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 14px 8px;
  padding: 8px 10px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid var(--sidebar-line);
  border-radius: 10px;
}

.sidebar-agent-switch-label {
  flex-shrink: 0;
  font-size: 12px;
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
.workspace-sidebar.is-collapsed:not(.is-drawer),
.workspace-config-pane.is-collapsed:not(.is-drawer) {
  width: 64px;
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

.session-collapse-handle {
  position: absolute;
  right: -14px;
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
  width: 28px !important;
  height: 28px !important;
  font-size: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04);
  color: #64748b;
  transition: 0.2s ease;
}
.collapse-btn:hover {
  color: var(--sidebar-accent);
  border-color: #99f6e4;
  box-shadow: 0 4px 12px rgba(15, 159, 149, 0.12);
  transform: scale(1.05);
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
  gap: 2px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 9px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid var(--sidebar-line);
  background: rgba(255, 255, 255, 0.65);
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

.collapsed-pane {
  display: flex;
  justify-content: center;
  padding-top: 24px;
}
.collapsed-new-btn {
  width: 40px !important;
  height: 40px !important;
  font-size: 18px;
  background: var(--sidebar-accent);
  color: #fff;
  border: none;
  box-shadow: 0 2px 8px rgba(15, 159, 149, 0.25);
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
  background: #f6f9fb;
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
}
.message-item.user .message-role {
  flex-direction: row-reverse;
}

.message-time, .message-meta {
  font-size: 11px;
  color: #cbd5e1;
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
  padding: 14px 18px;
  border-radius: 16px;
  background: #ffffff;
  color: #1e293b;
  font-size: 15px;
  line-height: 1.6;
  box-shadow: 0 2px 10px rgba(0,0,0,0.02);
  border: 1px solid rgba(226, 232, 240, 0.4);
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

.message-text :deep(p) { margin: 0 0 10px; }
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
  background: linear-gradient(180deg, rgba(248, 250, 252, 0) 0%, rgba(248, 250, 252, 0.9) 30%, #f8fafc 100%);
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

.quick-config-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.input-actions, .composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  margin-right: 6px;
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
  font-size: 12px;
  color: #94a3b8;
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
  border: 1px solid rgba(125, 211, 252, 0.45) !important;
  background: rgba(240, 249, 255, 0.9) !important;
  color: #0369a1 !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  padding: 7px 12px !important;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-tag:hover {
  background: #e0f2fe !important;
  border-color: rgba(14, 165, 233, 0.55) !important;
  color: #075985 !important;
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
  padding: 10px 14px 0;
}
.config-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.config-tabs :deep(.el-tabs__nav) {
  width: 100%;
  background: rgba(241, 245, 249, 0.92);
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  padding: 4px;
}
.config-tabs :deep(.el-tabs__item) {
  width: 33.33%;
  height: 32px;
  line-height: 32px;
  border-radius: 8px;
  font-size: 13px;
  color: #64748b;
  padding: 0;
  text-align: center;
  transition: all 0.2s ease;
}
.config-tabs :deep(.el-tabs__item.is-active) {
  background: #ffffff;
  color: var(--sidebar-text-strong);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
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
  padding: 4px 10px;
  background: #f1f5f9;
  color: #52657a;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
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

html.dark .session-delete {
  color: #475569;
}

html.dark .session-delete:hover {
  color: #ef4444;
}

html.dark .collapsed-new-btn {
  background: #26FFDF;
  color: #041010;
  box-shadow: 0 2px 8px rgba(38, 255, 223, 0.3);
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

html.dark .config-tabs :deep(.el-tabs__nav) {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .config-tabs :deep(.el-tabs__item) {
  color: #64748b;
}

html.dark .config-tabs :deep(.el-tabs__item.is-active) {
  background: #0f1c1c;
  color: #f1f5f9;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
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
