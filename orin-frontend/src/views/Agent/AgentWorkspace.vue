<template>
  <div class="agent-workspace" :style="workspaceGridStyle">
    <aside class="workspace-sidebar">
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
          <h2>👋您好，有什么可以帮您?</h2>
          <div class="welcome-modes">
            <el-tag
              v-for="mode in interactionModes"
              :key="`empty-${mode.value}`"
              :type="interactionMode === mode.value ? 'primary' : 'info'"
              effect="plain"
              class="mode-tag"
              @click="interactionMode = mode.value"
            >
              <el-icon><component :is="mode.icon" /></el-icon>
              <span>{{ mode.label }}</span>
            </el-tag>
          </div>

          <div class="composer-placeholder is-disabled">
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
        <div class="chat-header">
          <span />
          <el-button link :icon="MoreFilled" class="more-trigger" />
        </div>

        <div ref="messagesContainer" class="messages-container">
          <div v-if="messages.length === 0" class="welcome-panel">
            <h2>👋您好，有什么可以帮您?</h2>
            <div class="welcome-modes">
              <el-tag
                v-for="mode in interactionModes"
                :key="mode.value"
                :type="interactionMode === mode.value ? 'primary' : 'info'"
                effect="plain"
                class="mode-tag"
                @click="interactionMode = mode.value"
              >
                <el-icon><component :is="mode.icon" /></el-icon>
                <span>{{ mode.label }}</span>
              </el-tag>
            </div>

            <div class="composer-placeholder">
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

    <aside class="workspace-config">
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
import { useRouter } from 'vue-router';
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
  MoreFilled,
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
const configPaneCollapsed = ref(false);
const activeConfigTab = ref('tools');
const currentConfigId = ref('default');
const interactionMode = ref('assistant');
const messagesContainer = ref(null);
const currentConfig = reactive(defaultConfig());
const expandedRetrievedContext = ref({});
const expandedTraceDetails = ref({});
const viewportWidth = ref(window.innerWidth);
const isMobile = computed(() => viewportWidth.value < 1200);

const updateViewportWidth = () => {
  viewportWidth.value = window.innerWidth;
};

onMounted(() => {
  window.addEventListener('resize', updateViewportWidth);
});

const configProfiles = [{ id: 'default', name: '初始配置（默认）' }];
const workspaceGridStyle = computed(() => {
  if (isMobile.value) {
    const left = sessionPaneCollapsed.value ? '56px' : '260px';
    return { gridTemplateColumns: `${left} minmax(0, 1fr)` };
  }
  const left = sessionPaneCollapsed.value ? '56px' : '360px';
  const right = configPaneCollapsed.value ? '56px' : '430px';
  return { gridTemplateColumns: `${left} minmax(0, 1fr) ${right}` };
});

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
    configPaneCollapsed.value = savedState.configPaneCollapsed ?? false;
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
.agent-workspace {
  display: grid;
  height: 100%;
  min-height: 0;
  background: #f5f5f6;
  border: 1px solid #dee2e8;
  border-radius: 14px;
  overflow: hidden;
}

.workspace-sidebar,
.workspace-main,
.workspace-config {
  min-width: 0;
}

.workspace-sidebar {
  display: flex;
  background: #f3f3f4;
  border-right: 1px solid #dedfe2;
}

.workspace-session-pane {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-width: 0;
  background: #f8f8f9;
  position: relative;
}

.session-collapse-handle {
  position: absolute;
  right: -14px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
}

.collapse-btn {
  border: 1px solid #d3d9e2;
  background: #f4f6f9;
  color: #677083;
}

.collapsed-pane {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.collapsed-new-btn {
  border: 1px solid #d8dde6;
  background: #f7f9fb;
  color: #5e6777;
}

.sidebar-top {
  padding: 12px 12px 10px;
  border-bottom: 1px solid #e6e8eb;
}

.sidebar-profile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.sidebar-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.sidebar-name {
  margin-right: auto;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  color: #242b38;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.refresh-btn {
  color: #626b7b;
}

.agent-switcher {
  width: 100%;
  margin-bottom: 10px;
}

.new-session-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  font-size: 15px;
  background: #f7f8fa;
  color: #056a8f;
  border-color: #dce1e8;
}

.session-main,
.selection-info {
  min-width: 0;
}

.session-title,
.selection-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-title {
  font-size: 16px;
  font-weight: 500;
  color: #2f3643;
}

.session-meta,
.selection-meta {
  margin-top: 4px;
  font-size: 11px;
  color: #a2a9b7;
}

.session-search {
  padding: 10px 10px 6px;
}

.session-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #d8dde5 inset;
  background: #f6f8fb;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 10px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 2px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: 0.18s ease;
}

.session-item:hover {
  background: #f0f2f5;
}

.session-item.active {
  background: #e9edf2;
  border-color: #d9e0e9;
}

.session-delete {
  color: #a4acb9;
}

.workspace-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #f6f6f7;
  position: relative;
}

.workspace-main::before {
  display: none;
}

.state-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.empty-workspace-state {
  width: min(520px, 100%);
  padding: 40px 32px;
  text-align: center;
}

.empty-state-orb {
  width: 88px;
  height: 88px;
  margin: 0 auto 22px;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0f2fe 0%, #dbeafe 45%, #e9d5ff 100%);
  color: #0f766e;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.06em;
  box-shadow: 0 18px 36px rgba(59, 130, 246, 0.12);
}

.empty-workspace-state h2 {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.2;
  color: #0f172a;
}

.empty-workspace-state p {
  margin: 0;
  color: #64748b;
  line-height: 1.8;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  min-height: 48px;
  border-bottom: 1px solid #e4e6ea;
  background: #fafafa;
}

.more-trigger {
  color: #5d6473;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 16px;
}

.welcome-panel {
  max-width: 780px;
  margin: 40px auto 0;
  text-align: center;
}

.welcome-panel h2 {
  margin: 0 0 12px;
  font-size: 22px;
  color: #202736;
}

.welcome-modes {
  display: flex;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.mode-tag,
.prompt-tag {
  cursor: pointer;
}

.welcome-modes :deep(.el-tag) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 10px;
  background: #f0f2f5;
  border-color: #e0e4ea;
  color: #394050;
}

.welcome-modes :deep(.el-tag.el-tag--primary) {
  background: #eceff3;
  border-color: #d9dee6;
  color: #2f3745;
}

.composer-placeholder {
  padding: 12px 14px 8px;
  border: 1px solid #e4e7eb;
  border-radius: 12px;
  background: #ffffff;
}

.composer-placeholder.is-disabled {
  opacity: 0.9;
}

.composer-placeholder.is-disabled :deep(.el-textarea__inner) {
  color: #a1a9b8;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
}

.composer-placeholder.is-disabled .plus-trigger {
  cursor: not-allowed;
}

.composer-placeholder :deep(.el-textarea__inner) {
  min-height: 96px !important;
  padding: 0;
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 16px;
  line-height: 1.55;
  color: #283142;
}

.composer-placeholder :deep(.el-textarea__inner::placeholder) {
  color: #b4bbc8;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.composer-left-tools,
.composer-right-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.plus-trigger {
  border: none;
  background: transparent;
  font-size: 30px;
  line-height: 1;
  color: #697080;
  cursor: pointer;
}

.composer-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 11px;
  background: #ebedf1;
  border: 1px solid #d8dde5;
  color: #2f3745;
  font-size: 14px;
}

.composer-send-btn {
  background: #8bbfce;
  border-color: #8bbfce;
  color: #ffffff;
}

.quick-prompts {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.quick-prompts :deep(.el-tag) {
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef1f4;
  color: #535c6d;
  border-color: #e0e6ee;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eff6ff;
  color: #2563eb;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background: #ecfeff;
  color: #0f766e;
}

.message-bubble {
  max-width: min(80%, 780px);
}

.message-role {
  margin-bottom: 6px;
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #b0b8c4;
}

.meta-tokens {
  font-family: monospace;
}

.message-time {
  font-size: 11px;
  color: #b0b8c4;
}

.reasoning-section {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #e4e9f1;
  background: #f8fafc;
}

.reasoning-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: #64748b;
}

.reasoning-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.reasoning-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e7edf5;
}

.reasoning-step-dot {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.reasoning-main {
  min-width: 0;
  flex: 1;
}

.reasoning-top {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.reasoning-item.trace-success {
  border-color: #d1fae5;
  background: #f0fdf4;
}

.reasoning-item.trace-success .reasoning-step-dot {
  background: #dcfce7;
  color: #166534;
}

.reasoning-item.trace-warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.reasoning-item.trace-warning .reasoning-step-dot {
  background: #fef3c7;
  color: #92400e;
}

.reasoning-item.trace-error {
  border-color: #fecaca;
  background: #fef2f2;
}

.reasoning-item.trace-error .reasoning-step-dot {
  background: #fee2e2;
  color: #991b1b;
}

.reasoning-name {
  font-size: 12px;
  color: #0f766e;
  font-weight: 600;
}

.reasoning-status {
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  border: 1px solid #cbd5e1;
  color: #475569;
  background: #f8fafc;
}

.reasoning-status.status-success {
  border-color: #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.reasoning-status.status-warning {
  border-color: #fde68a;
  color: #92400e;
  background: #fffbeb;
}

.reasoning-status.status-error {
  border-color: #fecaca;
  color: #991b1b;
  background: #fef2f2;
}

.reasoning-duration {
  font-size: 11px;
  color: #64748b;
}

.reasoning-expand {
  margin-left: auto;
  font-size: 11px;
  color: #2563eb;
}

.reasoning-msg {
  margin-top: 4px;
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reasoning-detail {
  margin-top: 6px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
}

.reasoning-detail pre {
  margin: 0;
  padding: 10px;
  font-size: 11px;
  line-height: 1.45;
}

.message-citations {
  margin-bottom: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.citation-label {
  font-size: 12px;
  color: #7b8495;
}

.citation-item {
  max-width: 240px;
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f3f6fa;
  border: 1px solid #d8e0eb;
  color: #3d4a60;
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  font: inherit;
}

.citation-item:hover {
  background: #eaf1fb;
  border-color: #c8d7eb;
}

.message-text {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.95);
  color: #1f2937;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.message-item.user .message-text {
  background: linear-gradient(180deg, #eef6ff 0%, #e5f0ff 100%);
}

.message-text :deep(pre) {
  margin: 12px 0 0;
  padding: 12px;
  overflow-x: auto;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
}

.message-text :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.08);
}

.message-text :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.message-text :deep(p) {
  margin: 0 0 8px;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0 0 8px;
  padding-left: 20px;
}

.message-text :deep(li + li) {
  margin-top: 4px;
}

.message-text :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #cbd5e1;
  color: #475569;
  background: #f8fafc;
  border-radius: 8px;
}

.retrieved-context {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #fde68a;
  border-radius: 14px;
  background: #fffbeb;
}

.context-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #b45309;
}

.context-header-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.context-toggle {
  cursor: pointer;
  margin-bottom: 0;
}

.context-toggle-text {
  color: #1d4ed8;
  font-size: 12px;
}

.context-item + .context-item {
  margin-top: 10px;
}

.chunk-source {
  font-size: 12px;
  color: #92400e;
}

.chunk-source.is-clickable {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: dotted;
}

.chunk-text {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}

.tool-traces {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace-card {
  padding: 8px 12px;
  border-radius: 10px;
  background: #f8f9fb;
  border-left: 3px solid #d1d5db;
  font-size: 12px;
}

.trace-card.trace-success {
  border-left-color: #10b981;
}

.trace-card.trace-error {
  border-left-color: #ef4444;
}

.trace-card.trace-pending {
  border-left-color: #9ca3af;
}

.trace-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.trace-icon {
  font-size: 14px;
  color: #5e6878;
}

.trace-type {
  font-weight: 600;
  color: #2c3443;
}

.trace-duration {
  margin-left: auto;
  color: #9ca3af;
  font-size: 11px;
}

.trace-message {
  color: #475569;
  line-height: 1.5;
}

.trace-detail {
  margin-top: 6px;
  padding: 6px 8px;
  background: #f1f5f9;
  border-radius: 6px;
  overflow-x: auto;
}

.trace-detail pre {
  margin: 0;
  font-size: 11px;
  color: #64748b;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  color: #94a3b8;
}

.input-area {
  padding: 12px 14px 8px;
  border-top: none;
}

.input-area :deep(.el-textarea__inner) {
  min-height: 96px !important;
  padding: 0;
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 16px;
  line-height: 1.55;
  color: #283142;
}

.input-area-wrapper {
  padding: 12px 14px 8px;
  border: 1px solid #dfe3ea;
  border-radius: 12px;
  background: #ffffff;
}

.input-area-wrapper :deep(.el-textarea__inner) {
  min-height: 96px !important;
  padding: 0;
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 16px;
  line-height: 1.55;
  color: #283142;
}

.input-area-wrapper :deep(.el-textarea__inner::placeholder) {
  color: #b4bbc8;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.input-hint {
  font-size: 12px;
  color: #9ca3af;
}

.workspace-config {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f8f9fa;
  border-left: 1px solid #dedfe2;
  position: relative;
}

.config-collapse-handle {
  position: absolute;
  left: -14px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
}

.config-header {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-select {
  flex: 1;
}

.config-header :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #d6dce5 inset;
  background: #f6f8fb;
}

.config-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-icon-btn {
  border: 1px solid #d5dbe4;
  background: #f5f7fa;
  color: #6b7382;
}

.config-tabs {
  padding: 0 12px 6px;
}

.config-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.config-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.config-tabs :deep(.el-tabs__nav) {
  width: 100%;
  padding: 3px;
  border-radius: 12px;
  background: #eceef2;
}

.config-tabs :deep(.el-tabs__item) {
  width: 33.33%;
  height: 34px;
  border-radius: 9px;
  color: #5a6373;
  font-size: 14px;
}

.config-tabs :deep(.el-tabs__item.is-active) {
  background: #f9fafb;
  color: #2c3443;
  box-shadow: inset 0 0 0 1px #d9dee6;
}

.config-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.config-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 10px;
}

.config-card {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.config-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.config-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #222a39;
}

.config-badge {
  min-width: 52px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f6f7f9;
  color: #5e6878;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.config-badge.soft {
  background: #e8edf2;
  color: #2e3644;
}

.config-card-desc,
.config-description {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #6f7685;
}

.selection-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.selection-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f1f4f8;
  border: 1px solid #dbe1ea;
  font-size: 12px;
  color: #4f5868;
  cursor: pointer;
  transition: 0.2s ease;
}

.selection-tag:hover,
.selection-tag.active {
  background: #e8eef5;
  border-color: #cfd7e2;
  color: #2f3645;
}

.kb-item-wrapper {
  margin-bottom: 4px;
}

.doc-filter-section {
  margin-top: 6px;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 2px solid #dbe1ea;
}

.doc-filter-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  cursor: pointer;
  font-size: 12px;
  color: #5e6878;
}

.doc-filter-header:hover {
  color: #2f3745;
}

.doc-filter-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

.doc-filter-count {
  color: #2563eb;
  font-weight: 600;
}

.doc-filter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  padding-right: 4px;
  max-height: 160px;
  overflow-y: auto;
}

.doc-filter-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: #475569;
}

.doc-filter-item:hover {
  background: #f0f2f5;
}

.doc-filter-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-filter-loading {
  font-size: 12px;
  color: #9ca3af;
  padding: 4px 0;
}

.selection-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow-y: auto;
  padding-right: 2px;
}

.selection-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  background: #fafbfc;
  transition: 0.2s ease;
}

.selection-item:hover {
  border-color: #c7d0db;
  background: #fbfcfe;
  box-shadow: none;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  font-size: 13px;
  color: #475569;
}

.config-row strong {
  color: #111827;
}

.config-footer {
  padding: 10px 12px;
  display: flex;
  gap: 12px;
  border-top: 1px solid #dee0e4;
  background: #f4f5f7;
}

.config-footer .el-button {
  flex: 1;
  height: 38px;
  border-radius: 9px;
}

@media (max-width: 1440px) {
  .sidebar-name {
    font-size: 14px;
  }
}
</style>
