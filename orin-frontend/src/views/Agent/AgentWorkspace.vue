<template>
  <div class="agent-workspace" ref="containerRef" :class="{ 'is-wide': isWide, 'is-medium': isMedium, 'is-narrow': isNarrow }">
    
    <div v-if="isLeftDrawer && !sessionPaneCollapsed" class="d-overlay" @click="sessionPaneCollapsed = true"></div>
    <aside class="workspace-sidebar" :class="{ 'is-drawer': isLeftDrawer, 'is-collapsed': sessionPaneCollapsed }">
      <div class="workspace-session-pane">
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
          <div class="sidebar-top">
            <div class="sidebar-profile">
              <div class="sidebar-avatar" :style="{ background: getAgentColor(currentAgent?.name || 'A') }">
                {{ currentAgent?.name?.charAt(0) || 'A' }}
              </div>
              <div class="sidebar-name">
                {{ currentAgent?.name || '未选择智能体' }}
              </div>
            </div>
            <el-button
              link
              :icon="Refresh"
              class="refresh-btn"
              @click="reloadWorkspace"
            />

            <el-select
              v-if="!lockAgent"
              v-model="currentAgentId"
              class="agent-switcher"
              placeholder="选择智能体"
              filterable
              @change="handleAgentChange"
            >
              <el-option
                v-for="agent in agents"
                :key="agent.id"
                :label="agent.name"
                :value="agent.id"
              />
            </el-select>

            <el-button
              type="primary"
              class="new-session-btn"
              :icon="Plus"
              :disabled="!currentAgentId"
              @click="newSession"
            >
              创建新对话
            </el-button>
          </div>

          <div class="session-search">
            <el-input
              v-model="sessionSearch"
              placeholder="搜索会话..."
              :prefix-icon="Search"
              clearable
            />
          </div>

          <div class="session-list">
            <div
              v-for="session in filteredSessions"
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

            <el-empty v-if="currentAgentId && filteredSessions.length === 0" :image-size="56" description="暂无会话" />
            <el-empty v-else-if="!currentAgentId" :image-size="56" description="请先选择智能体" />
          </div>

        </template>
      </div>
    </aside>

    <main class="workspace-main">
      <div v-if="!currentAgent" class="state-panel">
        <div class="welcome-panel">
          <h2>您好，有什么可以帮您？</h2>

          <div class="composer-placeholder is-disabled">
            <div class="quick-config-row">
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
                <button type="button" class="plus-trigger" disabled>
                  +
                </button>
              </div>
              <div class="composer-right-tools">
                <span class="composer-chip">{{ currentConfig.name }}</span>
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
        <InteractionTopBar
          :chips="workspaceTopChips"
          :settings-open="!configPaneCollapsed"
          settings-label="设置"
          @chip-click="handleWorkspaceChipClick"
          @toggle-settings="configPaneCollapsed = !configPaneCollapsed"
        />

        <div ref="messagesContainer" class="messages-container" :class="{ 'is-empty': messages.length === 0 }">
          <div v-if="messages.length === 0" class="welcome-panel">
            <h2>您好，有什么可以帮您？</h2>

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
                  <button type="button" class="plus-trigger">
                    +
                  </button>
                </div>
                <div class="composer-right-tools">
                  <span class="composer-chip">{{ currentConfig.name }}</span>
                  <el-button
                    class="composer-send-btn"
                    type="primary"
                    circle
                    :icon="Top"
                    :disabled="loading || !inputMessage.trim()"
                    @click="sendMessage"
                  />
                </div>
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
                <div
                  v-if="msg.role === 'assistant' && msg.retrievedChunks?.length"
                  class="message-citations"
                >
                  <span class="citation-label">来源:</span>
                  <button
                    v-for="(chunk, citationIdx) in msg.retrievedChunks.slice(0, 5)"
                    :key="`citation-${index}-${citationIdx}`"
                    type="button"
                    class="citation-item"
                    :title="chunk.docName || chunk.source || '未知来源'"
                    @click="openCitation(chunk)"
                  >
                    [{{ citationIdx + 1 }}] {{ getChunkSourceLabel(chunk) }}
                  </button>
                </div>
                <div class="message-text" v-html="renderMarkdown(msg.content)" />

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
                  <div v-if="isRetrievedContextExpanded(msg, index)">
                    <div
                      v-for="(chunk, chunkIndex) in msg.retrievedChunks.slice(0, 3)"
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
              <span>思考中...</span>
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
                模式：{{ currentInteractionLabel }} · 已附加知识库 {{ attachedKbIds.length }} 个
                <span v-if="totalFilteredDocs > 0"> · 文档过滤 {{ totalFilteredDocs }} 个</span>
              </div>
              <el-button
                type="primary"
                :loading="loading"
                :disabled="!currentSessionId"
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

    
    <div v-if="isRightDrawer && !configPaneCollapsed" class="d-overlay" @click="configPaneCollapsed = true"></div>
    <aside class="workspace-config" :class="{ 'is-drawer': isRightDrawer, 'is-collapsed': configPaneCollapsed }">
      <div class="config-collapse-handle">
        <el-button
          class="collapse-btn"
          circle
          :icon="configPaneCollapsed ? ArrowLeft : ArrowRight"
          @click="configPaneCollapsed = !configPaneCollapsed"
        />
      </div>

      <template v-if="!configPaneCollapsed">
        <div class="config-header">
          <el-select v-model="currentConfigId" class="config-select">
            <el-option
              v-for="config in configProfiles"
              :key="config.id"
              :label="config.name"
              :value="config.id"
            />
          </el-select>
          <div class="config-header-actions">
            <el-button class="header-icon-btn" circle :icon="Star" />
            <el-button class="header-icon-btn" circle :icon="Delete" />
            <el-button class="header-icon-btn" circle :icon="Close" />
          </div>
        </div>

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
                  模型信息
                </div>
                <div class="config-badge soft">
                  运行中
                </div>
              </div>
              <div class="config-row">
                <span>智能体</span>
                <strong>{{ currentAgent?.name || '未选择' }}</strong>
              </div>
              <div class="config-row">
                <span>模型</span>
                <strong>{{ currentAgent?.model || '默认模型' }}</strong>
              </div>
              <div class="config-row">
                <span>交互模式</span>
                <el-select v-model="interactionMode" style="width: 160px">
                  <el-option
                    v-for="mode in interactionModes"
                    :key="mode.value"
                    :label="mode.label"
                    :value="mode.value"
                  />
                </el-select>
              </div>
              <div class="config-description">
                {{ currentAgent?.description || '这个智能体暂时没有补充描述。' }}
              </div>
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
          <el-button type="primary" @click="saveCurrentConfig">
            保存
          </el-button>
        </div>
      </template>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
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
  Star,
  Top,
  User
} from '@element-plus/icons-vue';
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
  updateKbDocFilters
} from '@/api/agent-chat';
import { getDocuments } from '@/api/knowledge';
import { getSkillList } from '@/api/skill';
import { getMcpServices } from '@/api/mcp';
import { useInteractionShell } from '@/composables/useInteractionShell';
import { runQuickChipAction } from '@/composables/useInteractionQuickChips';
import { buildWorkspaceChipSets } from '@/composables/useInteractionChipRegistry';
import InteractionTopBar from '@/components/orin/InteractionTopBar.vue';

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

const interactionModes = [
  { label: '智能助手', value: 'assistant', icon: Cpu },
  { label: '深度分析', value: 'analysis', icon: Search }
];

const quickPrompts = [
  '你好，请介绍一下你自己',
  '帮我写一封商务邮件',
  '解释一下什么是机器学习',
  '创建一个冒泡排序 Python 示例'
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

const agents = ref([]);
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
const sessionSearch = ref('');
const kbSearch = ref('');
const inputMessage = ref('');
const loading = ref(false);
const sessionPaneCollapsed = ref(false);
const configPaneCollapsed = ref(true);
const activeConfigTab = ref('tools');
const currentConfigId = ref('default');
const interactionMode = ref('assistant');
const messagesContainer = ref(null);
const currentConfig = reactive(defaultConfig());
const expandedRetrievedContext = ref({});
const expandedTraceDetails = ref({});
const {
  containerRef,
  isWide,
  isMedium,
  isNarrow,
  isLeftDrawer,
  isRightDrawer
} = useInteractionShell({
  leftDrawerMode: 'narrow',
  rightDrawerMode: 'always'
});

// 保留 isMobile 以便兼容某些未删干净的模板指令
const isMobile = isNarrow;

const configProfiles = [{ id: 'default', name: '初始配置（默认）' }];


const filteredSessions = computed(() => {
  if (!sessionSearch.value) return sessions.value;
  const keyword = sessionSearch.value.toLowerCase();
  return sessions.value.filter((session) => (session.title || '').toLowerCase().includes(keyword));
});

const filteredKnowledgeBases = computed(() => {
  if (!kbSearch.value) return knowledgeBases.value;
  const keyword = kbSearch.value.toLowerCase();
  return knowledgeBases.value.filter((kb) => (kb.name || '').toLowerCase().includes(keyword));
});

const currentSessionTitle = computed(() => {
  return sessions.value.find((item) => item.id === currentSessionId.value)?.title || '新对话';
});

const currentInteractionLabel = computed(() => {
  return interactionModes.find((mode) => mode.value === interactionMode.value)?.label || '智能助手';
});

const workspaceChipSets = computed(() => buildWorkspaceChipSets({
  interactionLabel: currentInteractionLabel.value,
  attachedKbCount: attachedKbIds.value.length,
  retrievedContextEnabled: currentConfig.showRetrievedContext,
  filteredDocsCount: totalFilteredDocs.value
}));

const workspaceTopChips = computed(() => workspaceChipSets.value.top);
const composerQuickChips = computed(() => workspaceChipSets.value.composer);
const inputQuickChips = computed(() => workspaceChipSets.value.input);

const handleWorkspaceChipClick = (chip) => {
  runQuickChipAction(chip, {
    openInspector: () => {
      configPaneCollapsed.value = false;
    },
    toggleInspector: () => {
      configPaneCollapsed.value = !configPaneCollapsed.value;
    },
    openTab: (tab) => {
      activeConfigTab.value = tab || 'tools';
      configPaneCollapsed.value = false;
    }
  });
};

const totalFilteredDocs = computed(() => {
  return Object.values(kbDocFilters).reduce((sum, docs) => sum + (docs?.length || 0), 0);
});

const normalizeAgent = (agent) => ({
  ...agent,
  id: agent.id || agent.agentId,
  name: agent.name || agent.agentName || '未命名智能体',
  model: agent.model || agent.modelName || ''
});

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
    configPaneCollapsed: configPaneCollapsed.value,
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

const saveCurrentConfig = () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
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
  if (presetId && agents.value.some((agent) => String(agent.id) === presetId)) {
    currentAgentId.value = presetId;
    currentAgent.value = agents.value.find((agent) => String(agent.id) === presetId) || null;
    return;
  }

  if (!currentAgentId.value && agents.value.length) {
    currentAgentId.value = agents.value[0].id;
    currentAgent.value = agents.value[0];
  } else {
    currentAgent.value = agents.value.find((agent) => agent.id === currentAgentId.value) || null;
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
    id: normalizeId(kb.id || kb.kbId)
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
    messages.value = cachedMessages;
    scrollToBottom();
  }

  try {
    const res = await getChatSession(session.id);
    const data = res?.data || res || {};
    messages.value = (data.messages || []).map((message) => ({
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
  currentAgent.value = agents.value.find((agent) => agent.id === agentId) || null;
  restoreConfigForAgent(agentId);
  activeConfigTab.value = 'tools';

  try {
    await loadSessions(agentId);
    if (!sessions.value.length) {
      await createSessionForAgent(agentId);
    }
  } catch (error) {
    ElMessage.error('加载智能体工作台失败');
  }
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
        messages.value = [];
        currentSessionId.value = '';
        attachedKbIds.value = [];
        Object.keys(kbDocFilters).forEach(key => delete kbDocFilters[key]);
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

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const sendMessage = async () => {
  if (!inputMessage.value.trim() || !currentSessionId.value || loading.value) return;

  const content = inputMessage.value.trim();
  const userMsg = { role: 'user', content };
  messages.value.push(userMsg);
  setCachedSessionMessages(currentSessionId.value, messages.value);
  updateSessionTitleLocally(content);

  inputMessage.value = '';
  loading.value = true;
  scrollToBottom();

  try {
    const res = await sendChatMessage(currentSessionId.value, {
      message: content,
      kbIds: attachedKbIds.value.map(normalizeId),
      kbDocFilters: normalizeKbDocFilters(kbDocFilters)
    });

    const data = res?.data || res || {};
    const assistantContent = (data.content || '').trim();
    messages.value.push({
      role: 'assistant',
      content: assistantContent || '（检索流程已完成，但模型未返回正文。请重试，或检查模型/网关配置。）',
      retrievedChunks: currentConfig.showRetrievedContext ? data.retrievedChunks || [] : [],
      toolTraces: data.toolTraces || [],
      model: data.model || '',
      provider: data.provider || '',
      promptTokens: data.promptTokens || 0,
      completionTokens: data.completionTokens || 0,
      createdAt: data.createdAt || new Date().toISOString()
    });
    setCachedSessionMessages(currentSessionId.value, messages.value);
  } catch (error) {
    const status = error?.response?.status;
    const backendMessage = error?.response?.data?.message || error?.response?.data?.error || '';
    let failureReason = '网络异常';
    if (error?.code === 'ECONNABORTED' || String(error?.message || '').toLowerCase().includes('timeout')) {
      failureReason = '请求超时（超过 180 秒）';
    } else if (status) {
      failureReason = `服务错误 (${status})`;
    }
    const finalReason = backendMessage || failureReason;
    messages.value.push({
      role: 'assistant',
      content: `（请求失败：${finalReason}）`,
      retrievedChunks: [],
      toolTraces: [],
      model: '',
      provider: '',
      promptTokens: 0,
      completionTokens: 0,
      createdAt: new Date().toISOString()
    });
    setCachedSessionMessages(currentSessionId.value, messages.value);
    ElMessage.error(`发送消息失败：${finalReason}`);
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
    KB_PIPELINE: '检索链路'
  };
  return labelMap[type] || type || '处理步骤';
};

const formatTraceStatus = (status) => {
  const s = (status || '').toLowerCase();
  if (s === 'success') return '成功';
  if (s === 'warning') return '提醒';
  if (s === 'error') return '失败';
  return '处理中';
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
  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe()]);
  if (currentAgentId.value) {
    restoreConfigForAgent(currentAgentId.value);
    await loadSessions(currentAgentId.value, { autoSelect: !currentSessionId.value });
  }
};

onMounted(async () => {
  // Restore workspace state from localStorage first
  const savedState = restoreWorkspaceState();
  if (savedState) {
    sessionPaneCollapsed.value = savedState.sessionPaneCollapsed ?? false;
    configPaneCollapsed.value = savedState.configPaneCollapsed ?? true;
    activeConfigTab.value = savedState.activeConfigTab ?? 'tools';
  }

  await Promise.allSettled([loadAgents(), loadKnowledgeBases(), loadSkills(), loadMcpServicesSafe()]);

  // Restore saved agent (or use first available)
  const agentToRestore = savedState?.currentAgentId && agents.value.find(a => a.id === savedState.currentAgentId)
    ? savedState.currentAgentId
    : (agents.value[0]?.id || '');

  if (agentToRestore) {
    currentAgentId.value = agentToRestore;
    currentAgent.value = agents.value.find(a => a.id === agentToRestore) || null;
    restoreConfigForAgent(agentToRestore);

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

onUnmounted(() => {
  window.removeEventListener('resize', updateViewportWidth);
});

// Save workspace state on changes
watch(
  [currentAgentId, currentSessionId, sessionPaneCollapsed, configPaneCollapsed, activeConfigTab],
  () => saveWorkspaceState(),
  { immediate: false }
);
</script>

<style scoped>
/* =========================================================================
   AgentWorkspace - Redesign (Glassmorphism, Hierarchy, Fluid Layout)
   ========================================================================= */

/* 1. Global & Layout 
-------------------------------------------------- */
.agent-workspace {
  --left-pane-width: 268px;
  --right-pane-width: 0px;
  --drawer-left-width: 272px;
  --drawer-right-width: 320px;
  --chat-content-max-width: 900px;
  position: relative;
  width: 100%;
  height: 100%; /* Fill the host shell height */
  display: flex;
  overflow: hidden; /* No global scrolling */
  background-color: #f6f9fb;
  font-family: "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

.agent-workspace.is-wide {
  --left-pane-width: 260px;
  --right-pane-width: 0px;
  --chat-content-max-width: 920px;
}

/* Ambient Glass Background */
.agent-workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 10% 40%, rgba(20, 184, 166, 0.05) 0%, transparent 50%),
              radial-gradient(circle at 90% 60%, rgba(14, 165, 233, 0.05) 0%, transparent 50%);
  z-index: 0;
  pointer-events: none;
}

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
.workspace-config {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  flex-shrink: 0;
}

.workspace-sidebar {
  width: var(--left-pane-width);
  border-right: 1px solid rgba(226, 232, 240, 0.8);
}

.workspace-config {
  width: var(--right-pane-width);
  pointer-events: none;
  border-left: 1px solid rgba(226, 232, 240, 0.8);
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

.workspace-config.is-drawer {
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
.workspace-config.is-drawer:not(.is-collapsed) {
  transform: translateX(0);
}

/* Collapsed Modes (Desktop only) */
.workspace-sidebar.is-collapsed:not(.is-drawer),
.workspace-config.is-collapsed:not(.is-drawer) {
  width: 64px;
}

/* Custom Scrollbars for Sidebars */
.workspace-sidebar ::-webkit-scrollbar,
.workspace-config ::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}
.workspace-sidebar ::-webkit-scrollbar-thumb,
.workspace-config ::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.6);
  border-radius: 4px;
}
.workspace-sidebar ::-webkit-scrollbar-thumb:hover,
.workspace-config ::-webkit-scrollbar-thumb:hover {
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
  color: #3b82f6;
  border-color: #bfdbfe;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
  transform: scale(1.05);
}

/* Sidebar Top */
.sidebar-top {
  padding: 20px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.sidebar-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: #ffffff;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}

.sidebar-name {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.refresh-btn {
  position: absolute;
  top: 24px;
  right: 16px;
  color: #94a3b8;
}

.refresh-btn:hover {
  color: #3b82f6;
}

.agent-switcher {
  width: 100%;
}
.agent-switcher :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.6);
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.new-session-btn {
  width: 100%;
  border-radius: 10px;
  height: 40px;
  font-weight: 600;
  background: var(--orin-primary, #3b82f6);
  border: none;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.25);
  transition: all 0.2s ease;
}
.new-session-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.35);
}

.session-search {
  padding: 0 16px 12px;
}
.session-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.6);
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px; /* Default finding preference: tight */
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.session-item:hover {
  background: rgba(241, 245, 249, 0.6);
}

.session-item.active {
  background: #ffffff;
  border-color: #e2e8f0;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  font-size: 11px;
  color: #94a3b8;
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
  background: var(--orin-primary, #3b82f6);
  color: #fff;
  border: none;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.25);
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
  background: transparent; /* Rely on root bg */
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
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.config-header :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.8);
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.header-icon-btn {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #64748b;
}
.header-icon-btn:hover {
  background: #f8fafc;
  color: #0f172a;
}

.config-tabs {
  padding: 0 16px;
}
.config-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.config-tabs :deep(.el-tabs__nav) {
  width: 100%;
  background: rgba(241, 245, 249, 0.8);
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
  color: #0f172a;
  font-weight: 600;
  box-shadow: 0 2px 4px rgba(0,0,0,0.04);
}
.config-tabs :deep(.el-tabs__active-bar) { display: none; }

.config-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

.config-card {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
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
  color: #0f172a;
}

.config-badge {
  padding: 4px 10px;
  background: #f1f5f9;
  color: #475569;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.config-badge.soft {
  background: #e0e7ff;
  color: #3730a3;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
}
.config-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.config-row span {
  color: #64748b;
}

.config-description, .config-card-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.selection-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.selection-tag {
  padding: 4px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: 0.2s ease;
}
.selection-tag:hover, .selection-tag.active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
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
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: 0.2s ease;
}
.selection-item:hover {
  background: #ffffff;
  border-color: #e2e8f0;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}

.selection-info {
  flex: 1;
  min-width: 0;
}
.selection-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}
.selection-meta {
  font-size: 11px;
  color: #94a3b8;
}

.config-footer {
  padding: 16px;
  border-top: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.config-footer .el-button {
  width: 100%;
  border-radius: 10px;
  height: 40px;
  font-weight: 600;
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
</style>
