<template>
  <div class="service-portal">
    <aside class="portal-sidebar">
      <div class="sidebar-brand-row">
        <div class="sidebar-brand-title" aria-label="ORIN Chat">
          <img class="sidebar-brand-logo" src="/logo.svg" alt="ORIN" />
          <strong>Chat</strong>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button
          class="nav-item"
          :class="{ active: activeWorkspace === 'chat' }"
          type="button"
          @click="startNewSession"
        >
          <span class="nav-icon"><el-icon><ChatLineRound /></el-icon></span>
          <span>新对话</span>
          <kbd>⌘ K</kbd>
        </button>
        <button
          class="nav-item"
          :class="{ active: activeWorkspace === 'creation' && !showServicePanel }"
          type="button"
          @click="openCreationStudio"
        >
          <span class="nav-icon"><el-icon><EditPen /></el-icon></span>
          <span>AI 创作</span>
        </button>
        <button
          class="nav-item"
          :class="{ active: showServicePanel }"
          type="button"
          @click="toggleServicePanel"
        >
          <span class="nav-icon"><el-icon><Connection /></el-icon></span>
          <span>可用服务</span>
          <span v-if="agents.length" class="nav-count">{{ agents.length }}</span>
          <el-icon class="nav-tail"><ArrowRight /></el-icon>
        </button>
      </nav>

      <section v-if="showServicePanel" class="sidebar-section">
        <div class="section-title">可用服务</div>
        <div class="service-list">
          <button
            v-for="agent in agents"
            :key="agent.id"
            class="service-item"
            :class="{ active: currentAgentId === agent.id }"
            type="button"
            @click="switchAgent(agent)"
          >
            <span class="service-icon"><el-icon><Cpu /></el-icon></span>
            <span class="service-copy">
              <strong>{{ agent.name }}</strong>
              <small>{{ getAgentMeta(agent) }}</small>
            </span>
          </button>
          <div v-if="!loadingAgents && agents.length === 0" class="sidebar-empty">
            暂无可用服务，请联系管理员开通智能体。
          </div>
          <div v-else-if="loadingAgents" class="sidebar-empty">正在加载服务...</div>
        </div>
      </section>

      <section v-else class="sidebar-section">
        <div class="section-title">最近对话</div>
        <div class="session-list">
          <button
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: currentSessionId === session.id }"
            type="button"
            @click="openSession(session)"
          >
            <div class="session-head">
              <el-icon class="session-icon"><ChatLineRound /></el-icon>
              <span class="session-title">{{ session.title || '未命名会话' }}</span>
              <span v-if="session.updatedAt" class="session-time">{{ formatDate(session.updatedAt) }}</span>
              <button
                class="session-delete"
                type="button"
                title="删除会话"
                @click.stop="removeSession(session)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </button>
          <div v-if="currentAgent && !loadingSessions && sessions.length === 0" class="sidebar-empty">还没有会话</div>
        </div>
      </section>

      <div class="sidebar-bottom">
        <el-dropdown trigger="click" placement="top-start" @command="handleUserCommand">
          <div class="portal-user-wrapper">
            <el-avatar :size="36" :src="userAvatar" class="portal-user-avatar">
              {{ avatarText }}
            </el-avatar>
            <div class="portal-user-info">
              <span class="portal-user-name">{{ displayName }}</span>
              <span class="portal-user-role">{{ userRoleLabel }}</span>
            </div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="workspace">ORIN 工作台</el-dropdown-item>
              <el-dropdown-item command="apiKeys">API Key 自助</el-dropdown-item>
              <el-dropdown-item command="settings">设置与帮助</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="dashboard">管理端</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <main class="portal-main">
      <section v-if="activeWorkspace === 'creation'" class="creation-stage">
        <div class="creation-workspace">
          <header class="creation-workspace-head">
            <div class="creation-mode-tabs">
              <button
                v-for="mode in creationModes"
                :key="mode.value"
                type="button"
                :class="{ active: creatorMode === mode.value }"
                @click="creatorMode = mode.value"
              >
                <el-icon><component :is="mode.icon" /></el-icon>
                <span>{{ mode.label }}</span>
              </button>
            </div>
          </header>

          <div v-if="creationServiceHint" class="creation-service-hint">
            <div class="creation-empty-hero">
              <span class="creation-empty-icon">
                <el-icon><component :is="creationServiceHint.icon" /></el-icon>
              </span>
              <span class="creation-empty-kicker">{{ creationServiceHint.modeLabel }}</span>
              <strong>{{ creationServiceHint.title }}</strong>
              <p>{{ creationServiceHint.description }}</p>
            </div>

            <div class="creation-empty-footer">
              <div class="creation-empty-status">
                <strong>{{ creationServiceHint.requiredType }}</strong>
                <span>{{ creationServiceHint.statusText }}</span>
              </div>
              <div class="creation-service-actions">
                <template v-if="userStore.isAdmin">
                  <el-button type="primary" size="small" @click="goCreationSetup">
                    {{ creationServiceHint.primaryAction }}
                  </el-button>
                  <el-button size="small" @click="router.push(ROUTES.AGENTS.MODELS)">
                    模型管理
                  </el-button>
                </template>
                <template v-else>
                  <el-button size="small" @click="activeWorkspace = 'chat'">
                    返回对话
                  </el-button>
                  <el-button size="small" @click="openServicePanel">
                    查看可用服务
                  </el-button>
                </template>
              </div>
            </div>

            <div class="creation-empty-notes">
              <span
                v-for="step in creationServiceHint.steps"
                :key="step.title"
              >
                {{ step.title }}
              </span>
            </div>
          </div>

          <div v-else class="creation-runner">
            <ImageGenerator
              v-if="creatorMode === 'image'"
              :key="creationAgent.id"
              :agent-id="creationAgent.id"
              :agent-info="creationAgent"
              :parameters="creationRuntimeParameters"
            />
            <VideoGenerator
              v-else-if="creatorMode === 'video'"
              :key="creationAgent.id"
              :agent-id="creationAgent.id"
              :agent-info="creationAgent"
              :parameters="creationRuntimeParameters"
            />
            <AudioGenerator
              v-else
              :key="creationAgent.id"
              :agent-id="creationAgent.id"
              :agent-info="creationAgent"
              :parameters="creationRuntimeParameters"
            />
          </div>
        </div>
      </section>

      <section v-else ref="messagesRef" class="chat-stage" :class="{ home: isHome }">
        <template v-if="!currentAgent">
          <div class="home-center">
            <h1>
              <span>当前没有可用的</span>
              <span class="home-title-accent">智能体服务</span>
            </h1>
            <p>请联系管理员完成智能体、模型资源与参考资料配置。</p>
          </div>
        </template>

        <template v-else-if="isHome">
          <div class="home-center">
            <div class="home-greeting">
              <h1 class="chat-home-title">你好，{{ displayName }}</h1>
              <p class="chat-home-subtitle">今天想让 <span>ORIN</span> 做什么？</p>
            </div>
            <div class="input-card">
              <input ref="fileInputRef" class="hidden-file-input" type="file" @change="onFileSelected" />
              <div v-if="uploadingFile" class="uploading-chip">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>文件上传中...</span>
              </div>
              <div v-else-if="selectedUploadFileName" class="attachment-chip">
                <el-icon><Document /></el-icon>
                <span>{{ selectedUploadFileName }}</span>
                <button type="button" @click="clearUploadedFile">移除</button>
              </div>
              <el-input
                v-model="inputMessage"
                class="composer-input"
                type="textarea"
                resize="none"
                :autosize="{ minRows: 2, maxRows: 5 }"
                :disabled="!currentAgent || sending || uploadingFile"
                :placeholder="selectedUploadFileName ? '说明如何处理这个文件...' : '输入问题...'"
                @keydown.enter.exact.prevent="handleComposerEnter"
              />
              <div v-if="showCommandMenu" class="command-menu">
                <button
                  v-for="action in filteredCommandActions"
                  :key="action.command"
                  type="button"
                  class="command-item"
                  @click="applyCommand(action)"
                >
                  <span>{{ action.command }}</span>
                  <strong>{{ action.label }}</strong>
                  <small>{{ action.desc }}</small>
                </button>
                <div v-if="filteredCommandActions.length === 0" class="command-empty">没有匹配的常用操作</div>
              </div>
              <div class="tools-row premium-tools">
                <div class="tools-left">
                  <button
                    type="button"
                    class="premium-icon-btn"
                    title="上传文件"
                    :disabled="!currentAgent || uploadingFile"
                    @click="triggerFilePicker"
                  >
                    <el-icon><component :is="uploadingFile ? Loading : Paperclip" /></el-icon>
                  </button>
                  <button
                    type="button"
                    class="premium-icon-btn"
                    :class="{ active: showToolsMenu }"
                    title="常用工具"
                    :disabled="!currentAgent || sending || uploadingFile"
                    @click="toggleToolsMenu"
                  >
                    <el-icon><Operation /></el-icon>
                  </button>
                </div>
                <div class="tools-right">
                  <button
                    v-if="currentAgent"
                    class="premium-model-picker"
                    type="button"
                    @click="openServicePanel"
                  >
                    <span>{{ currentAgent.name }}</span>
                    <el-icon><ArrowRight /></el-icon>
                  </button>
                  <button
                    class="premium-send-btn"
                    :class="{ active: inputMessage.trim() || selectedUploadFileId }"
                    type="button"
                    :disabled="uploadingFile || !currentAgent || (!inputMessage.trim() && !selectedUploadFileId)"
                    @click="sendMessage"
                  >
                    <el-icon v-if="sending" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else><Top /></el-icon>
                  </button>
                </div>
              </div>
              <div v-if="showToolsMenu" class="tool-menu">
                <button
                  v-for="tool in toolActions"
                  :key="tool.label"
                  type="button"
                  class="tool-item"
                  @click="applyTool(tool)"
                >
                  <el-icon><component :is="tool.icon" /></el-icon>
                  <span>
                    <strong>{{ tool.label }}</strong>
                    <small>{{ tool.desc }}</small>
                  </span>
                </button>
              </div>
            </div>

            <div class="home-action-chips">
              <button
                v-for="action in homeActionChips"
                :key="action.label"
                class="home-action-chip"
                type="button"
                @click="handleHomeAction(action)"
              >
                <el-icon><component :is="action.icon" /></el-icon>
                <span>{{ action.label }}</span>
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="messages-wrap">
            <div
              v-for="(message, index) in messages"
              :key="`${message.role}-${index}-${message.createdAt || ''}`"
              class="message-row"
              :class="message.role"
            >
              <div v-if="message.role === 'assistant'" class="message-avatar premium-avatar">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="message-bubble premium-bubble">
                <div class="message-role" v-if="message.role === 'assistant'">{{ currentAgent?.name || 'ORIN' }}</div>
                <div
                  v-if="message.role === 'assistant'"
                  class="message-content markdown-body"
                  v-html="renderAssistantMarkdown(message.content)"
                />
                <div v-else class="message-content">{{ message.content }}</div>
              </div>
            </div>

            <div v-if="sending" class="message-row assistant">
              <div class="message-avatar"><el-icon><Cpu /></el-icon></div>
              <div class="message-bubble">
                <div class="message-role">{{ currentAgent?.name || 'ORIN' }}</div>
                <div class="typing-line">正在处理...</div>
              </div>
            </div>
          </div>

          <footer class="composer-dock">
            <div class="input-card compact">
              <div v-if="currentAgent" class="service-context compact-context">
                <button class="current-service-button" type="button" @click="openServicePanel">
                  <el-icon><Cpu /></el-icon>
                  <span>
                    <strong>{{ currentAgent.name }}</strong>
                    <small>{{ selectedKbLabel }}</small>
                  </span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
              <input ref="fileInputRef" class="hidden-file-input" type="file" @change="onFileSelected" />
              <div v-if="uploadingFile" class="uploading-chip">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>文件上传中...</span>
              </div>
              <div v-else-if="selectedUploadFileName" class="attachment-chip">
                <el-icon><Document /></el-icon>
                <span>{{ selectedUploadFileName }}</span>
                <button type="button" @click="clearUploadedFile">移除</button>
              </div>
              <el-input
                v-model="inputMessage"
                class="composer-input"
                type="textarea"
                resize="none"
                :autosize="{ minRows: 2, maxRows: 5 }"
                :disabled="!currentAgent || sending || uploadingFile"
                :placeholder="selectedUploadFileName ? '说明如何处理这个文件...' : '输入问题...'"
                @keydown.enter.exact.prevent="handleComposerEnter"
              />
              <div v-if="showCommandMenu" class="command-menu compact-menu">
                <button
                  v-for="action in filteredCommandActions"
                  :key="action.command"
                  type="button"
                  class="command-item"
                  @click="applyCommand(action)"
                >
                  <span>{{ action.command }}</span>
                  <strong>{{ action.label }}</strong>
                  <small>{{ action.desc }}</small>
                </button>
                <div v-if="filteredCommandActions.length === 0" class="command-empty">没有匹配的常用操作</div>
              </div>
              <div class="tools-row">
                <div class="tools-left">
                  <button
                    type="button"
                    class="icon-btn ghost"
                    title="上传文件"
                    :disabled="!currentAgent || uploadingFile"
                    @click="triggerFilePicker"
                  >
                    <el-icon><component :is="uploadingFile ? Loading : Plus" /></el-icon>
                  </button>
                  <button
                    type="button"
                    class="tool-pill"
                    :class="{ active: showToolsMenu }"
                    :disabled="!currentAgent || sending || uploadingFile"
                    @click="toggleToolsMenu"
                  >
                    <el-icon><Operation /></el-icon>
                    <span>工具</span>
                  </button>
                </div>
                <div class="tools-right">
                  <el-button
                    class="send-button"
                    type="primary"
                    circle
                    :icon="Top"
                    :loading="sending"
                    :disabled="uploadingFile || !currentAgent || (!inputMessage.trim() && !selectedUploadFileId)"
                    @click="sendMessage"
                  />
                </div>
              </div>
              <div v-if="showToolsMenu" class="tool-menu compact-tool-menu">
                <button
                  v-for="tool in toolActions"
                  :key="tool.label"
                  type="button"
                  class="tool-item"
                  @click="applyTool(tool)"
                >
                  <el-icon><component :is="tool.icon" /></el-icon>
                  <span>
                    <strong>{{ tool.label }}</strong>
                    <small>{{ tool.desc }}</small>
                  </span>
                </button>
              </div>
            </div>
          </footer>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { marked } from 'marked';
import {
  ArrowRight,
  ChatLineRound,
  Connection,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  EditPen,
  Files,
  Loading,
  MagicStick,
  Microphone,
  Operation,
  Paperclip,
  Picture,
  Plus,
  Top,
  User,
  VideoCamera
} from '@element-plus/icons-vue';
import {
  attachKnowledgeBase,
  createChatSession,
  deleteChatSession,
  detachKnowledgeBase,
  getAttachedKnowledgeBases,
  getSessionMessages,
  listAgents,
  listChatSessions,
  listKnowledgeBases,
  sendChatMessage,
  sendChatMessageStream
} from '@/api/agent-chat';
import { chatAgent } from '@/api/agent';
import { uploadMultimodalFile } from '@/api/multimodal';
import { useUserStore } from '@/stores/user';
import { ROUTES } from '@/router/routes';
import { useChatSelectionPersistence } from '@/composables/useChatSelectionPersistence';
import { formatChatError } from '@/utils/formatChatError';
import ImageGenerator from '@/views/Agent/components/ImageGenerator.vue';
import VideoGenerator from '@/views/Agent/components/VideoGenerator.vue';
import AudioGenerator from '@/views/Agent/components/AudioGenerator.vue';

const router = useRouter();
const userStore = useUserStore();
const persistence = useChatSelectionPersistence();

const agents = ref([]);
const knowledgeBases = ref([]);
const sessions = ref([]);
const messages = ref([]);
const currentAgentId = ref('');
const currentSessionId = ref('');
const selectedKbIds = ref([]);
const attachedKbIds = ref([]); // 后端当前实际绑定的 KB id，用于 diff 同步
const selectedUploadFileId = ref('');
const selectedUploadFileName = ref('');
const inputMessage = ref('');
const activeWorkspace = ref('chat');
const showServicePanel = ref(false);
const creatorPrompt = ref('');
const creatorMode = ref('image');
const generatingCreation = ref(false);
const creationResult = ref(null);
const loadingAgents = ref(false);
const loadingKnowledge = ref(false);
const loadingSessions = ref(false);
const sending = ref(false);
const uploadingFile = ref(false);
const showToolsMenu = ref(false);
const messagesRef = ref(null);
const fileInputRef = ref(null);

const commandActions = [
  {
    command: '/总结资料',
    label: '总结资料',
    desc: '提炼重点、结论和待办',
    prompt: '帮我总结这份资料的关键结论，并列出需要跟进的事项。'
  },
  {
    command: '/生成方案',
    label: '生成方案',
    desc: '整理目标、步骤和风险',
    prompt: '请根据下面的信息生成一份可执行的处理方案，包含目标、步骤、风险和下一步。'
  },
  {
    command: '/写正式邮件',
    label: '写正式邮件',
    desc: '把草稿改成正式表达',
    prompt: '请把下面内容改写成一封正式、清晰、礼貌的邮件。'
  },
  {
    command: '/查询制度',
    label: '查询制度',
    desc: '基于参考资料回答问题',
    prompt: '请根据可用参考资料回答下面的问题，并说明依据来自哪些资料范围。'
  },
  {
    command: '/分析表格',
    label: '分析表格',
    desc: '找出趋势、异常和建议',
    prompt: '请分析下面的数据或表格内容，指出关键趋势、异常点和建议动作。'
  }
];

const toolActions = [
  {
    label: '总结资料',
    desc: '提炼重点、结论和待办',
    icon: Files,
    prompt: '帮我总结这份资料的关键结论，并列出需要跟进的事项。'
  },
  {
    label: '生成方案',
    desc: '整理目标、步骤和风险',
    icon: MagicStick,
    prompt: '请根据下面的信息生成一份可执行的处理方案，包含目标、步骤、风险和下一步。'
  },
  {
    label: '分析表格',
    desc: '找出趋势、异常和建议',
    icon: DataAnalysis,
    prompt: '请分析下面的数据或表格内容，指出关键趋势、异常点和建议动作。'
  },
  {
    label: '写正式邮件',
    desc: '把草稿改成正式表达',
    icon: EditPen,
    prompt: '请把下面内容改写成一封正式、清晰、礼貌的邮件。'
  }
];

const homeActionChips = [
  {
    label: '生成图片',
    desc: '进入 AI 创作',
    icon: Picture,
    action: 'creation'
  },
  {
    label: '撰写或编辑',
    desc: '改写、润色和整理文本',
    icon: EditPen,
    prompt: '请帮我撰写或编辑下面这段内容，让表达更清晰。'
  },
  {
    label: '查找资料',
    desc: '基于参考资料回答',
    icon: Files,
    prompt: '请根据可用参考资料帮我查找并回答下面的问题。'
  }
];

const creationModes = [
  { value: 'image', label: '图像', icon: Picture },
  { value: 'video', label: '视频', icon: VideoCamera },
  { value: 'audio', label: '音频', icon: Microphone }
];

const creationActions = [
  {
    label: 'AI 抠图',
    prompt: '帮我把图片主体抠出来，保留干净透明背景。',
    previewClass: 'cutout'
  },
  {
    label: '擦除',
    prompt: '帮我擦除图片里多余的元素，并自然补全背景。',
    previewClass: 'erase'
  },
  {
    label: '区域重绘',
    prompt: '帮我重绘指定区域，保持整体光影和风格一致。',
    previewClass: 'redraw'
  },
  {
    label: '扩图',
    prompt: '帮我扩展画面边界，让构图更完整。',
    previewClass: 'expand'
  },
  {
    label: '变清晰',
    prompt: '帮我提升图片清晰度，保留自然质感。',
    previewClass: 'enhance'
  }
];

const creationShowcases = [
  {
    title: 'T cat 新品首发',
    desc: '现代社媒拼贴风海报，适合新品预热、活动发布和轻营销内容。',
    className: 'poster-cat'
  },
  {
    title: '4 min sol',
    desc: '信息图拼贴与工业视觉结合，适合报告封面、科普内容和数据表达。',
    className: 'poster-industrial'
  },
  {
    title: '毛孩派对',
    desc: '明亮活动视觉，适合社区运营、线下活动和节日促销。',
    className: 'poster-party'
  },
  {
    title: '春日玻璃字',
    desc: '清透 3D 字效与自然元素，适合品牌海报和节气视觉。',
    className: 'poster-spring'
  }
];

const displayName = computed(() => userStore.userInfo?.name || userStore.userInfo?.username || userStore.username || '用户');
const avatarText = computed(() => String(displayName.value || 'U').slice(0, 1).toUpperCase());
const userAvatar = computed(() => userStore.userInfo?.avatar || '');
const userRoleLabel = computed(() => (userStore.isAdmin ? '管理员' : '普通用户'));
const isHome = computed(() => messages.value.length === 0);

const currentAgent = computed(() => agents.value.find((agent) => agent.id === currentAgentId.value));

const selectedKbLabel = computed(() => {
  if (!knowledgeBases.value.length) return '无参考资料';
  if (!selectedKbIds.value.length) return '全部参考资料';
  if (selectedKbIds.value.length === 1) {
    const kb = knowledgeBases.value.find((item) => item.id === selectedKbIds.value[0]);
    return kb?.name || '1 个资料范围';
  }
  return `${selectedKbIds.value.length} 个资料范围`;
});

const matchesAgentCapability = (agent, keywords) => {
  const haystack = [
    agent.viewType,
    agent.modelType,
    agent.type,
    agent.category,
    agent.providerType,
    agent.name,
    agent.description,
    agent.modelName
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  return keywords.some((keyword) => haystack.includes(keyword));
};

const imageCreationAgents = computed(() => agents.value.filter((agent) => matchesAgentCapability(agent, [
  'text_to_image',
  'image_to_image',
  'tti',
  '文生图',
  '图像生成',
  '图片生成',
  '绘图',
  'seedream',
  'image'
])));

const videoCreationAgents = computed(() => agents.value.filter((agent) => matchesAgentCapability(agent, [
  'text_to_video',
  'ttv',
  '视频生成',
  'video'
])));

const audioCreationAgents = computed(() => agents.value.filter((agent) => matchesAgentCapability(agent, [
  'text_to_speech',
  'tts',
  '语音合成',
  '文字转语音',
  '音频生成',
  'speech',
  'audio'
])));

const creationAgent = computed(() => {
  if (creatorMode.value === 'video') {
    return videoCreationAgents.value[0] || null;
  }
  if (creatorMode.value === 'audio') {
    return audioCreationAgents.value[0] || null;
  }
  return imageCreationAgents.value[0] || null;
});

const canGenerateCreation = computed(() => {
  return Boolean(creatorPrompt.value.trim() && creationAgent.value && !generatingCreation.value);
});

const creationServiceHint = computed(() => {
  if (creationAgent.value) return null;
  const commonSteps = (agentType, modelType) => [
    {
      index: '01',
      title: `需要 ${agentType} 智能体`,
      description: `创建或启用一个 ${agentType} 智能体，并确认当前用户有权限访问。`
    },
    {
      index: '02',
      title: `绑定 ${modelType} 模型`,
      description: `在模型管理中添加可用的 ${modelType} 模型，配置 provider 凭据。`
    },
    {
      index: '03',
      title: '配置后自动出现在这里',
      description: '保存配置后刷新服务列表，即可在这里直接生成内容。'
    }
  ];
  if (loadingAgents.value) {
    return {
      icon: Loading,
      modeLabel: '正在检查服务',
      title: '正在加载创作服务...',
      description: '正在检查当前账号可用的图像、视频和音频智能体。',
      primaryAction: '智能体接入',
      requiredType: 'CREATION_AGENT',
      statusText: '正在读取当前账号的服务权限',
      steps: commonSteps('创作型', '多模态')
    };
  }
  if (creatorMode.value === 'video') {
    return {
      icon: VideoCamera,
      modeLabel: '视频生成',
      title: '还没有视频生成服务',
      description: '当前账号能用的服务里还没有视频生成能力。你可以先回到对话，或让管理员接入一个视频创作智能体。',
      primaryAction: '接入视频智能体',
      requiredType: 'TEXT_TO_VIDEO',
      statusText: agents.value.length ? `已发现 ${agents.value.length} 个服务，但没有视频生成能力` : '当前账号暂无任何可用服务',
      steps: commonSteps('TEXT_TO_VIDEO', '视频生成')
    };
  }
  if (creatorMode.value === 'audio') {
    return {
      icon: Microphone,
      modeLabel: '音频生成',
      title: '还没有语音合成服务',
      description: '当前账号能用的服务里还没有语音合成能力。你可以先回到对话，或让管理员接入一个语音创作智能体。',
      primaryAction: '接入语音智能体',
      requiredType: 'TEXT_TO_SPEECH',
      statusText: agents.value.length ? `已发现 ${agents.value.length} 个服务，但没有语音合成能力` : '当前账号暂无任何可用服务',
      steps: commonSteps('TEXT_TO_SPEECH', '语音合成')
    };
  }
  return {
    icon: Picture,
    modeLabel: '图像生成',
    title: '还没有图像生成服务',
    description: '当前账号能用的服务里还没有图像生成能力。你可以先回到对话，或让管理员接入一个图像创作智能体。',
    primaryAction: '接入图像智能体',
    requiredType: 'TEXT_TO_IMAGE',
    statusText: agents.value.length ? `已发现 ${agents.value.length} 个服务，但没有图像生成能力` : '当前账号暂无任何可用服务',
    steps: commonSteps('TEXT_TO_IMAGE', '图像生成')
  };
});

const goCreationSetup = () => {
  router.push(ROUTES.AGENTS.ONBOARD);
};

const creationWorkspaceSubtitle = computed(() => {
  if (creatorMode.value === 'video') return '生成视频、首帧参考和动态视觉内容';
  if (creatorMode.value === 'audio') return '把文字转成可播放的语音音频';
  return '生成图片、海报和视觉创意内容';
});

const creationRuntimeParameters = computed(() => ({
  imageSize: '1328x1328',
  guidanceScale: 7.5,
  inferenceSteps: 20,
  videoSize: '16:9',
  videoDuration: '5',
  speed: 1,
  gain: 0
}));

const showCommandMenu = computed(() => {
  return inputMessage.value.trimStart().startsWith('/') && !sending.value && !uploadingFile.value;
});

const filteredCommandActions = computed(() => {
  const keyword = inputMessage.value.trimStart().slice(1).toLowerCase();
  if (!keyword) return commandActions;
  return commandActions.filter((action) => {
    return [action.command, action.label, action.desc]
      .some((value) => value.toLowerCase().includes(keyword));
  });
});

const unwrapList = (res) => {
  const data = res?.data ?? res;
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  if (Array.isArray(data?.records)) return data.records;
  if (Array.isArray(data?.list)) return data.list;
  if (Array.isArray(data?.items)) return data.items;
  return [];
};

const normalizeId = (value) => String(value ?? '');

const normalizeAgent = (agent) => ({
  ...agent,
  id: normalizeId(agent.id ?? agent.agentId),
  name: agent.name || agent.agentName || agent.appName || `智能体 ${agent.id ?? agent.agentId}`,
  description: agent.description || agent.desc || agent.remark || '',
  modelName: agent.modelName || agent.model || '',
  modelType: agent.modelType || agent.type || '',
  viewType: agent.viewType || agent.view_type || agent.modelType || agent.type || '',
  providerType: agent.providerType || agent.provider || ''
});

const normalizeKb = (kb) => ({
  ...kb,
  id: normalizeId(kb.id ?? kb.kbId ?? kb.knowledgeBaseId),
  name: kb.name || kb.kbName || kb.title || `资料范围 ${kb.id ?? kb.kbId}`,
  documentCount: kb.documentCount ?? kb.docCount ?? kb.documentsCount ?? 0
});

const normalizeSession = (session) => ({
  ...session,
  id: normalizeId(session.id ?? session.sessionId),
  title: session.title || session.name || '未命名会话',
  createdAt: session.createdAt || session.createTime || session.created_at,
  updatedAt: session.updatedAt || session.updateTime || session.updated_at || session.lastMessageAt
});

const normalizeMessage = (message) => ({
  role: message.role || message.sender || (message.isUser ? 'user' : 'assistant'),
  content: message.content || message.message || message.answer || message.text || '',
  createdAt: message.createdAt || message.createTime || message.time
});

const scrollToBottom = async () => {
  await nextTick();
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
  }
};

const loadAgents = async () => {
  loadingAgents.value = true;
  try {
    const res = await listAgents({ status: 'active' });
    agents.value = unwrapList(res).map(normalizeAgent).filter((agent) => agent.id);
  } finally {
    loadingAgents.value = false;
  }
};

const loadKnowledgeBases = async () => {
  loadingKnowledge.value = true;
  try {
    const res = await listKnowledgeBases();
    knowledgeBases.value = unwrapList(res).map(normalizeKb).filter((kb) => kb.id);
  } finally {
    loadingKnowledge.value = false;
  }
};

const loadSessions = async () => {
  if (!currentAgentId.value) {
    sessions.value = [];
    return;
  }

  loadingSessions.value = true;
  try {
    const res = await listChatSessions({ agentId: currentAgentId.value });
    sessions.value = unwrapList(res).map(normalizeSession).filter((session) => session.id);
  } finally {
    loadingSessions.value = false;
  }
};

const ensureSession = async () => {
  if (currentSessionId.value) return currentSessionId.value;
  if (!currentAgentId.value) return '';

  const res = await createChatSession({
    agentId: currentAgentId.value,
    title: `${currentAgent.value?.name || '智能体'} 对话`
  });
  const session = normalizeSession(res?.data ?? res);
  currentSessionId.value = session.id;
  persistence.persistSession(session.id);
  if (session.id && !sessions.value.some((item) => item.id === session.id)) {
    sessions.value.unshift(session);
  }
  return currentSessionId.value;
};

const loadAttachedKnowledgeBases = async () => {
  if (!currentSessionId.value) {
    selectedKbIds.value = [];
    attachedKbIds.value = [];
    return;
  }

  try {
    const res = await getAttachedKnowledgeBases(currentSessionId.value);
    const ids = unwrapList(res).map((kb) => normalizeId(kb.id ?? kb.kbId ?? kb.knowledgeBaseId)).filter(Boolean);
    attachedKbIds.value = ids;
    selectedKbIds.value = ids.slice();
  } catch (error) {
    selectedKbIds.value = [];
    attachedKbIds.value = [];
  }
};

const syncSelectedKbIds = async () => {
  const sessionId = currentSessionId.value;
  if (!sessionId) {
    attachedKbIds.value = selectedKbIds.value.slice();
    return;
  }
  const target = new Set(selectedKbIds.value);
  const current = new Set(attachedKbIds.value);
  const toAdd = [...target].filter((id) => !current.has(id));
  const toRemove = [...current].filter((id) => !target.has(id));
  if (toAdd.length === 0 && toRemove.length === 0) return;
  try {
    await Promise.all([
      ...toAdd.map((kbId) => attachKnowledgeBase(sessionId, kbId)),
      ...toRemove.map((kbId) => detachKnowledgeBase(sessionId, kbId))
    ]);
    attachedKbIds.value = [...target];
  } catch (error) {
    // 失败时回滚本地视图，让 UI 状态与后端保持一致
    selectedKbIds.value = attachedKbIds.value.slice();
    ElMessage.error(formatChatError(error) || '参考资料范围同步失败');
  }
};

watch(selectedKbIds, () => {
  // 仅在已存在会话时把 KB 变化写回后端；选 agent 阶段 attach 暂不调用
  if (currentSessionId.value) {
    syncSelectedKbIds()
  }
});

const selectAgent = async (agent) => {
  currentAgentId.value = agent.id;
  currentSessionId.value = '';
  messages.value = [];
  selectedKbIds.value = [];
  attachedKbIds.value = [];
  persistence.persistAgent(agent.id);
  persistence.clearSession();
  await loadSessions();
};

const switchAgent = async (agent) => {
  if (!agent?.id) return;
  activeWorkspace.value = 'chat';
  showServicePanel.value = false;
  clearUploadedFile();
  await selectAgent(agent);
};

const startNewSession = async () => {
  activeWorkspace.value = 'chat';
  showServicePanel.value = false;
  if (!currentAgentId.value) return;
  currentSessionId.value = '';
  messages.value = [];
  selectedKbIds.value = [];
  attachedKbIds.value = [];
  persistence.clearSession();
  clearUploadedFile();
  await ensureSession();
  await loadSessions();
};

const openSession = async (session) => {
  activeWorkspace.value = 'chat';
  showServicePanel.value = false;
  currentSessionId.value = session.id;
  selectedKbIds.value = [];
  attachedKbIds.value = [];
  persistence.persistSession(session.id);
  try {
    const res = await getSessionMessages(session.id);
    const rawMessages = (res?.data || res || {}).messages || [];
    messages.value = Array.isArray(rawMessages) ? rawMessages.map(normalizeMessage).filter((msg) => msg.content) : [];
    await loadAttachedKnowledgeBases();
    await scrollToBottom();
  } catch (error) {
    persistence.clearSession();
    ElMessage.error(formatChatError(error) || '会话加载失败');
  }
};

const removeSession = async (session) => {
  const sessionId = normalizeId(session?.id);
  if (!sessionId) return;

  try {
    await ElMessageBox.confirm(
      `确认删除会话「${session.title || '未命名会话'}」吗？该操作不可恢复。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
  } catch (error) {
    return;
  }

  try {
    await deleteChatSession(sessionId);
    sessions.value = sessions.value.filter((item) => item.id !== sessionId);
    ElMessage.success('会话已删除');

    if (currentSessionId.value === sessionId) {
      messages.value = [];
      selectedKbIds.value = [];
      attachedKbIds.value = [];
      currentSessionId.value = '';
      persistence.clearSession();
      const nextSession = sessions.value[0];
      if (nextSession) {
        await openSession(nextSession);
      }
    }
  } catch (error) {
    ElMessage.error(formatChatError(error) || '删除会话失败');
  }
};

const triggerFilePicker = () => {
  if (!currentAgentId.value || uploadingFile.value) return;
  showToolsMenu.value = false;
  fileInputRef.value?.click();
};

const openCreationStudio = () => {
  activeWorkspace.value = 'creation';
  showServicePanel.value = false;
  showToolsMenu.value = false;
};

const toggleServicePanel = () => {
  showServicePanel.value = !showServicePanel.value;
  showToolsMenu.value = false;
};

const openServicePanel = () => {
  showServicePanel.value = true;
  showToolsMenu.value = false;
};

const clearKnowledgeSelection = () => {
  selectedKbIds.value = [];
};

const getAgentMeta = (agent) => {
  if (!agent) return '智能体服务';
  return agent.description || agent.modelName || agent.modelType || agent.viewType || '智能体服务';
};

const applyCreationAction = (action) => {
  creatorPrompt.value = action.prompt;
  creationResult.value = null;
};

const normalizeBackendMediaUrl = (url) => {
  if (!url || typeof url !== 'string') return '';
  if (url.startsWith('http') || url.startsWith('data:')) return url;
  return url.startsWith('/') ? url : `/${url}`;
};

const findFirstMediaUrl = (value, keys) => {
  if (!value) return '';
  if (typeof value === 'string') {
    return value.startsWith('http') || value.startsWith('/') || value.startsWith('data:') ? value : '';
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findFirstMediaUrl(item, keys);
      if (found) return found;
    }
    return '';
  }
  if (typeof value === 'object') {
    for (const key of keys) {
      if (typeof value[key] === 'string' && value[key]) return value[key];
    }
    for (const item of Object.values(value)) {
      const found = findFirstMediaUrl(item, keys);
      if (found) return found;
    }
  }
  return '';
};

const extractCreationResult = (res, type) => {
  const payload = (res?.status || res?.dataType || res?.errorMessage) ? res : (res?.data ?? res);
  const keys = type === 'video'
    ? ['video_url', 'videoUrl', 'url', 'download_url', 'downloadUrl']
    : ['image_url', 'imageUrl', 'url', 'download_url', 'downloadUrl'];
  const url = findFirstMediaUrl(payload, keys);
  return {
    status: payload?.status,
    errorMessage: payload?.errorMessage || payload?.error || payload?.message || '',
    url: normalizeBackendMediaUrl(url)
  };
};

const generateCreation = async () => {
  const prompt = creatorPrompt.value.trim();
  if (!prompt || generatingCreation.value) return;

  const agent = creationAgent.value;
  if (!agent) {
    ElMessage.warning(creationServiceHint.value?.title || '当前没有可用的创作服务');
    return;
  }

  generatingCreation.value = true;
  creationResult.value = null;

  try {
    const requestPayload = {
      prompt,
      mode: creatorMode.value,
      image_size: '1024x1024',
      aspect_ratio: '1:1',
      guidance_scale: 7.5,
      num_inference_steps: 20
    };
    const res = await chatAgent(agent.id, JSON.stringify(requestPayload));
    const parsed = extractCreationResult(res, creatorMode.value);

    if (parsed.status === 'FAILED' || parsed.status === 'ERROR') {
      throw new Error(parsed.errorMessage || '创作服务返回失败');
    }
    if (!parsed.url) {
      throw new Error(parsed.errorMessage || '创作服务没有返回可展示的媒体地址');
    }

    creationResult.value = {
      type: creatorMode.value,
      url: parsed.url,
      prompt,
      agentName: agent.name || '创作服务',
      createdAt: new Date().toISOString()
    };
    ElMessage.success('创作完成');
  } catch (error) {
    ElMessage.error(error?.message || '创作失败，请检查后端智能体配置');
  } finally {
    generatingCreation.value = false;
  }
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
    ElMessage.warning('当前没有可用服务');
    clearUploadedFile();
    return;
  }

  uploadingFile.value = true;
  try {
    const uploadRes = await uploadMultimodalFile(file);
    const fileId = uploadRes?.id || uploadRes?.data?.id || '';
    if (!fileId) {
      throw new Error('上传成功但未返回文件 ID');
    }
    selectedUploadFileId.value = fileId;
    selectedUploadFileName.value = file.name || `文件-${fileId.slice(0, 8)}`;
    ElMessage.success('文件已附加');
  } catch (error) {
    clearUploadedFile();
    ElMessage.error(error?.message || '文件上传失败');
  } finally {
    uploadingFile.value = false;
  }
};

const normalizeReplyText = (value) => {
  if (value == null) return '';
  if (typeof value === 'string') return value.trim();
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item === 'string') return item;
        if (item && typeof item === 'object') {
          return normalizeReplyText(
            item.text
            ?? item.content
            ?? item.output_text
            ?? item.reasoning_content
          );
        }
        return '';
      })
      .filter(Boolean)
      .join('\n')
      .trim();
  }
  if (typeof value === 'object') {
    const maybeText = value.text
      ?? value.content
      ?? value.output_text
      ?? value.reasoning_content
      ?? value.message?.content;
    return normalizeReplyText(maybeText);
  }
  return String(value).trim();
};

const extractAssistantReply = (res) => {
  const data = res?.data ?? res;
  const directText = normalizeReplyText(data);
  if (directText) return directText;

  const directCandidates = [
    data?.answer,
    data?.content,
    data?.message,
    data?.assistantMessage,
    data?.reply,
    data?.text,
    data?.output_text,
    data?.choices?.[0]?.message?.content,
    data?.choices?.[0]?.message?.reasoning_content,
    data?.choices?.[0]?.delta?.content,
    data?.data?.answer,
    data?.data?.content,
    data?.data?.text,
    data?.data?.message?.content
  ];

  for (const candidate of directCandidates) {
    const text = normalizeReplyText(candidate);
    if (text) return text;
  }

  if (String(data?.status || '').toUpperCase() === 'ERROR') {
    const errorText = normalizeReplyText(data?.error || data?.message || data?.data?.error);
    if (errorText) {
      const normalized = errorText.toLowerCase();
      if (
        normalized.includes('model is not a vlm')
        || normalized.includes('vision language model')
        || normalized.includes('code=20041')
      ) {
        return '请求失败：当前服务不支持文件直读。请切换为支持文件处理的服务，或先将文档导入参考资料后再提问。';
      }
      return `请求失败：${errorText}`;
    }
  }

  return '已完成处理，但服务没有返回可展示内容。';
};

marked.setOptions({
  breaks: true,
  gfm: true
});

const renderAssistantMarkdown = (content) => {
  const text = normalizeReplyText(content);
  if (!text) return '';
  return marked.parse(text);
};

const sendMessage = async () => {
  const content = inputMessage.value.trim();
  if ((!content && !selectedUploadFileId.value) || !currentAgentId.value || sending.value) return;

  const sessionId = await ensureSession();
  if (!sessionId) {
    ElMessage.error('无法创建会话');
    return;
  }

  const displayContent = selectedUploadFileName.value
    ? `${content || '[已附加文件]'}\n\n[文件] ${selectedUploadFileName.value}`
    : content;
  const hasAttachment = Boolean(selectedUploadFileId.value);
  const outboundMessage = content;

  messages.value.push({ role: 'user', content: displayContent, createdAt: new Date().toISOString() });
  inputMessage.value = '';
  sending.value = true;
  await scrollToBottom();

  // 附件走 chatAgent（多模态通道）；纯文本优先尝试 SSE 流式，失败则回退 blocking。
  if (hasAttachment) {
    try {
      const res = await chatAgent(
        currentAgentId.value,
        outboundMessage || `请帮我处理这个文件：${selectedUploadFileName.value || '已上传附件'}`,
        selectedUploadFileId.value
      );
      messages.value.push({
        role: 'assistant',
        content: extractAssistantReply(res),
        createdAt: new Date().toISOString()
      });
    } catch (error) {
      messages.value.push({
        role: 'assistant',
        content: formatChatError(error),
        createdAt: new Date().toISOString()
      });
    } finally {
      clearUploadedFile();
      sending.value = false;
      await loadSessions();
      await scrollToBottom();
    }
    return;
  }

  // 纯文本：SSE 流式优先；网络 / 解析失败自动回退 blocking。
  const assistantIndex = messages.value.length;
  let assistantContent = '';
  let assistantCreatedAt = new Date().toISOString();
  let usedStream = false;
  const renderStreamAssistant = (content) => {
    if (!content) return
    usedStream = true
    assistantContent = content
    const assistantMessage = {
      role: 'assistant',
      content: assistantContent,
      createdAt: assistantCreatedAt
    }
    if (messages.value[assistantIndex]?.role !== 'assistant') {
      messages.value.push(assistantMessage)
    } else {
      messages.value[assistantIndex] = assistantMessage
    }
    scrollToBottom()
  }

  try {
    await sendChatMessageStream(
      sessionId,
      { message: outboundMessage, kbIds: selectedKbIds.value },
      {
        message: (payload) => {
          // 单条 message 事件：当后端不发送 delta 时使用，作为"已完成的整段回复"
          if (!usedStream && payload && typeof payload === 'object') {
            const text = extractAssistantReply({ data: payload });
            if (text) {
              renderStreamAssistant(text)
            }
          } else if (typeof payload === 'string' && payload) {
            // 纯文本事件：把片段追加到当前 assistant 气泡
            renderStreamAssistant(`${assistantContent}${payload}`)
          }
        },
        delta: (payload) => {
          // 增量片段（增量流）
          const piece = typeof payload === 'string'
            ? payload
            : (payload && typeof payload === 'object'
              ? (payload.delta || payload.content || payload.text || '')
              : '')
          if (!piece) return
          renderStreamAssistant(`${assistantContent}${piece}`)
        },
        error: (payload) => {
          const err = new Error(
            (payload && typeof payload === 'object' && payload.message) || 'SSE 通道返回错误'
          )
          err.response = { data: payload || {} }
          throw err
        },
        done: (payload) => {
          const text = normalizeReplyText(payload)
          if (text) {
            renderStreamAssistant(text)
          }
        }
      }
    )
    // 流式通道成功但没有返回最终内容时才用 blocking 兜底。
    if (!usedStream) {
      const res = await sendChatMessage(sessionId, {
        message: outboundMessage,
        kbIds: selectedKbIds.value
      })
      messages.value.push({
        role: 'assistant',
        content: extractAssistantReply(res),
        createdAt: new Date().toISOString()
      })
    } else if (messages.value[assistantIndex]?.role === 'assistant') {
      // 保留流式结果（createdAt 已被 stream 设置）
    } else if (assistantContent) {
      messages.value.push({
        role: 'assistant',
        content: assistantContent,
        createdAt: assistantCreatedAt
      })
    }
  } catch (streamError) {
    // SSE 抛错：尝试回退到 blocking；如 blocking 不可用再写入错误气泡
    try {
      const res = await sendChatMessage(sessionId, {
        message: outboundMessage,
        kbIds: selectedKbIds.value
      })
      // 如果流式已经有部分内容，丢弃（因为完整 blocking 响应已拿到）
      if (messages.value[assistantIndex]?.role === 'assistant') {
        messages.value.splice(assistantIndex, 1)
      }
      messages.value.push({
        role: 'assistant',
        content: extractAssistantReply(res),
        createdAt: new Date().toISOString()
      })
    } catch (fallbackError) {
      const finalError = fallbackError || streamError
      const formatted = formatChatError(finalError)
      if (messages.value[assistantIndex]?.role === 'assistant') {
        messages.value[assistantIndex] = {
          role: 'assistant',
          content: assistantContent ? `${assistantContent}\n\n${formatted}` : formatted,
          createdAt: assistantCreatedAt
        }
      } else {
        messages.value.push({
          role: 'assistant',
          content: formatted,
          createdAt: new Date().toISOString()
        })
      }
    }
  } finally {
    sending.value = false
    await loadSessions()
    await scrollToBottom()
  }
};

const refreshPortal = async () => {
  await Promise.all([loadAgents(), loadKnowledgeBases()]);
  if (currentAgentId.value) {
    await loadSessions();
  }
};

const applyPrompt = (prompt) => {
  showToolsMenu.value = false;
  inputMessage.value = prompt;
};

const applyCommand = (action) => {
  showToolsMenu.value = false;
  inputMessage.value = action.prompt;
};

const toggleToolsMenu = () => {
  showToolsMenu.value = !showToolsMenu.value;
};

const applyTool = (tool) => {
  showToolsMenu.value = false;
  inputMessage.value = tool.prompt;
};

const handleHomeAction = (action) => {
  if (action.action === 'creation') {
    openCreationStudio();
    return;
  }
  applyTool(action);
};

const handleComposerEnter = () => {
  if (showCommandMenu.value && filteredCommandActions.value.length) {
    applyCommand(filteredCommandActions.value[0]);
    return;
  }
  sendMessage();
};

const handleUserCommand = (command) => {
  if (command === 'profile') {
    router.push(ROUTES.CHAT_PROFILE);
    return;
  }
  if (command === 'settings') {
    router.push(ROUTES.CHAT_PROFILE);
    return;
  }
  if (command === 'apiKeys') {
    router.push(ROUTES.PLATFORM);
    return;
  }
  if (command === 'workspace') {
    router.push(ROUTES.WORKSPACE);
    return;
  }
  if (command === 'dashboard') {
    router.push(ROUTES.ADMIN);
    return;
  }
  if (command === 'logout') {
    userStore.logout();
    router.push('/login');
  }
};

const formatDate = (value) => {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

onMounted(async () => {
  const persisted = persistence.restore();
  await refreshPortal();

  if (!agents.value.length) {
    return;
  }

  // 恢复智能体选择：被持久化的 agent 必须仍存在
  const persistedAgent = persisted.agentId
    ? agents.value.find((a) => a.id === persisted.agentId)
    : null;
  const fallbackAgent = persistedAgent || agents.value[0];

  await selectAgent(fallbackAgent);

  // 恢复最近会话：调用方需捕获失败并清掉失效 id
  if (persisted.sessionId && persistedAgent) {
    try {
      await openSession({ id: persisted.sessionId, title: '上次对话' });
    } catch (error) {
      persistence.clearSession();
    }
  }
});
</script>

<style scoped>
.service-portal {
  --sidebar-width: 260px;
  --portal-primary: #0d9488;
  --portal-primary-dark: #0f766e;
  --portal-primary-soft: #e6f5f2;
  --portal-ink: #0f172a;
  --portal-muted: #6b7280;
  --portal-line: rgba(15, 23, 42, 0.08);
  --portal-surface: #ffffff;
  --portal-bg: #ffffff;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  background: var(--portal-bg);
  color: var(--portal-ink);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.portal-sidebar {
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 12px 12px;
  background: #f7f9f9;
  border-right: 1px solid #e4ecea;
  box-sizing: border-box;
}

.sidebar-brand-row {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 8px 2px;
  color: var(--portal-ink);
}

.sidebar-brand-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 17px;
  font-weight: 760;
  line-height: 1;
}

.sidebar-brand-logo {
  display: block;
  width: 65px;
  height: 25px;
  object-fit: contain;
}

.icon-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #5b5d63;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-btn:hover {
  background: #f1f5f9;
  color: var(--portal-primary-dark);
}

.icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.icon-btn.ghost {
  background: transparent;
  color: var(--portal-primary-dark);
}

.portal-user-wrapper {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.portal-user-wrapper:hover {
  background: #ececef;
}

.portal-user-avatar {
  flex-shrink: 0;
  border: 0;
  background: var(--portal-primary-dark);
  box-shadow: none;
}

.portal-user-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.portal-user-name {
  color: var(--portal-ink);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portal-user-role {
  width: fit-content;
  padding: 2px 6px;
  border-radius: 4px;
  padding: 0;
  color: #8a8a8e;
  background: transparent;
  font-size: 12px;
  font-weight: 500;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ececf0;
}

.nav-item {
  width: 100%;
  min-height: 38px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 9px;
  font-size: 14px;
  font-weight: 560;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.nav-item:hover {
  background: #edf2f1;
  color: #030712;
}

.nav-item.active {
  background: var(--portal-primary-soft);
  color: #0f5f58;
}

.nav-icon {
  width: 20px;
  height: 20px;
  border-radius: 0;
  background: transparent;
  color: currentColor;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  box-shadow: none;
}

.nav-icon .el-icon {
  font-size: 17px;
}

.nav-item:hover .nav-icon,
.nav-item.active .nav-icon {
  background: transparent;
  color: currentColor;
  box-shadow: none;
}

.nav-item kbd {
  margin-left: auto;
  min-width: 36px;
  height: 20px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 7px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.72);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
}

.nav-tail {
  margin-left: auto;
  color: #9ca3af;
  font-size: 14px;
}

.nav-tail.subtle {
  transform: rotate(-45deg);
}

.nav-count {
  margin-left: auto;
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #d9efeb;
  color: #0f766e;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 7px;
  font-size: 11px;
  font-weight: 850;
}

.sidebar-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.section-title {
  padding: 10px 9px 6px;
  color: #111827;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.session-list:hover {
  scrollbar-color: #d5d8de transparent;
}

.session-list::-webkit-scrollbar,
.service-list::-webkit-scrollbar {
  width: 6px;
}

.session-list::-webkit-scrollbar-track,
.service-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-list::-webkit-scrollbar-thumb,
.service-list::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
}

.session-list:hover::-webkit-scrollbar-thumb,
.service-list:hover::-webkit-scrollbar-thumb {
  background: #d5d8de;
}

.service-list {
  min-height: 0;
  overflow-y: auto;
  display: grid;
  gap: 4px;
  padding: 0 2px 8px 0;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.service-list:hover {
  scrollbar-color: #d5d8de transparent;
}

.service-item {
  width: 100%;
  min-height: 52px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--portal-ink);
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.service-item:hover {
  background: #edf2f1;
  border-color: transparent;
}

.service-item.active {
  background: var(--portal-primary-soft);
  border-color: transparent;
}

.service-icon {
  width: 24px;
  height: 24px;
  border-radius: 0;
  background: transparent;
  color: var(--portal-primary-dark);
  display: inline-grid;
  place-items: center;
}

.service-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.service-copy strong {
  overflow: hidden;
  color: #26313f;
  font-size: 14px;
  font-weight: 850;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-copy small {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  padding: 8px 9px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: background-color 0.16s ease;
}

.session-item:hover {
  background: #edf2f1;
}

.session-item.active {
  background: var(--portal-primary-soft);
}

.session-head {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
}

.session-icon {
  color: #7a808c;
  font-size: 14px;
  flex: 0 0 auto;
}

.session-title {
  min-width: 0;
  flex: 1 1 auto;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  margin-left: auto;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #8a8d95;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  cursor: pointer;
  flex: 0 0 auto;
}

.session-item:hover .session-delete,
.session-item.active .session-delete {
  opacity: 1;
}

.session-delete:hover {
  background: #eceef2;
  color: #e23d3d;
}

.session-time {
  display: none;
}

.sidebar-empty {
  color: #8a8d95;
  font-size: 12px;
  padding: 8px 10px;
}

.sidebar-bottom {
  display: grid;
  gap: 8px;
  padding: 10px 2px 2px;
  border-top: 1px solid #ececf0;
  background: transparent;
}

.portal-main {
  min-width: 0;
  height: 100vh;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
}

.creation-stage {
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  padding: 22px 28px;
  box-sizing: border-box;
  background: #fff;
}

.creation-workspace {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
}

.creation-workspace-head {
  position: absolute;
  top: 20px;
  right: 22px;
  z-index: 5;
  width: auto;
  min-height: 0;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0;
  box-sizing: border-box;
  pointer-events: none;
}

.creation-mode-tabs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  padding: 3px;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.1);
  pointer-events: auto;
  -webkit-backdrop-filter: blur(14px);
  backdrop-filter: blur(14px);
}

.creation-mode-tabs button {
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.creation-mode-tabs button.active {
  background: var(--portal-primary);
  color: #fff;
  box-shadow: 0 8px 16px rgba(13, 148, 136, 0.2);
}

.creation-runner {
  flex: 1;
  min-height: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.04);
}

.creation-runner :deep(.playground-stage) {
  height: 100%;
  min-height: 0;
}

.creation-shell {
  width: min(1180px, 100%);
  margin: 0 auto;
  animation: portalHomeIn 0.58s cubic-bezier(.16, 1, .3, 1) both;
}

.creation-header {
  text-align: center;
  margin: 0 0 28px;
}

.creation-header h1 {
  margin: 0;
  color: var(--portal-ink);
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 0;
}

.creation-header p {
  margin: 12px 0 0;
  color: #a0a7b2;
  font-size: 15px;
}

.creation-composer {
  width: min(780px, 100%);
  margin: 0 auto 24px;
  border: 1px solid rgba(59, 130, 246, 0.28);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 22px 64px rgba(37, 99, 235, 0.12);
  padding: 16px;
}

.creation-input :deep(.el-textarea__inner) {
  min-height: 78px !important;
  border: 0;
  box-shadow: none;
  resize: none;
  background: transparent;
  color: var(--portal-ink);
  font-size: 16px;
  line-height: 1.45;
  padding: 0 2px 14px;
}

.creation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.creation-toolbar > .creation-mic,
.creation-toolbar > .creation-generate {
  margin-left: 0;
}

.creation-left-tools {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.creation-tabs {
  height: 34px;
  padding: 3px;
  border-radius: 12px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
}

.creation-tabs button,
.creation-tool-button,
.creation-generate,
.creation-mic {
  border: 0;
  font-family: inherit;
  cursor: pointer;
}

.creation-tabs button {
  height: 28px;
  border-radius: 9px;
  background: transparent;
  color: #818895;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 800;
}

.creation-tabs button.active {
  background: #fff;
  color: var(--portal-ink);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.creation-tool-button {
  height: 34px;
  border-radius: 10px;
  background: transparent;
  color: #20242c;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 750;
}

.creation-tool-button:hover {
  background: #f3f4f6;
}

.creation-tool-button .select-icon {
  font-size: 11px;
  transform: rotate(90deg);
}

.creation-mic {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #1f2937;
  display: inline-grid;
  place-items: center;
  font-size: 18px;
  flex: 0 0 auto;
}

.creation-generate {
  height: 38px;
  border-radius: 12px;
  background: var(--portal-primary-dark);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 850;
  box-shadow: 0 12px 26px rgba(0, 150, 136, 0.22);
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.creation-generate:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 34px rgba(0, 150, 136, 0.28);
}

.creation-generate:disabled {
  cursor: not-allowed;
  opacity: 0.48;
  box-shadow: none;
}

.creation-mic:hover {
  background: #e5e7eb;
}

.creation-service-hint {
  width: min(780px, 100%);
  margin: auto;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--portal-ink);
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  justify-items: center;
  gap: 18px;
  padding: 18px 0;
  text-align: center;
  box-shadow: none;
}

.creation-empty-hero,
.creation-service-hint-copy {
  display: grid;
  gap: 10px;
  justify-items: center;
  text-align: center;
}

.creation-empty-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: var(--portal-primary);
  color: #fff;
  display: inline-grid;
  place-items: center;
  box-shadow: 0 14px 30px rgba(13, 148, 136, 0.18);
}

.creation-empty-icon .el-icon {
  font-size: 24px;
}

.creation-empty-kicker {
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.06em;
}

.creation-empty-hero strong,
.creation-service-hint-copy strong {
  color: var(--portal-ink);
  font-size: 26px;
  font-weight: 650;
  letter-spacing: 0;
}

.creation-empty-hero p,
.creation-service-hint-copy span {
  max-width: 560px;
  margin: 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.creation-empty-grid {
  display: none;
}

.creation-empty-step {
  min-height: 122px;
  border: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.75);
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 16px;
}

.creation-empty-step span {
  color: var(--portal-primary);
  font-size: 12px;
  font-weight: 900;
}

.creation-empty-step strong {
  color: #111827;
  font-size: 15px;
  font-weight: 850;
}

.creation-empty-step small {
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

.creation-empty-footer {
  width: min(620px, 100%);
  min-height: 54px;
  border: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 999px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 8px 10px 8px 18px;
  box-shadow: 0 16px 42px rgba(15, 23, 42, 0.06);
}

.creation-empty-status {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  text-align: left;
}

.creation-empty-status strong {
  color: #0f172a;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.creation-empty-status span {
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.creation-service-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.creation-empty-notes {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.creation-empty-notes span {
  min-height: 26px;
  border-radius: 999px;
  background: #f2f2f3;
  color: #64748b;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
}

.creation-result {
  width: min(780px, 100%);
  margin: 0 auto 24px;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  border: 1px solid rgba(0, 191, 165, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  padding: 12px;
  box-shadow: 0 18px 46px rgba(15, 118, 110, 0.1);
}

.creation-result-media {
  min-height: 168px;
  border-radius: 12px;
  overflow: hidden;
  background: #f3f4f6;
}

.creation-result-media img,
.creation-result-media video {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.creation-result-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  color: var(--portal-ink);
}

.creation-result-meta span {
  color: var(--portal-primary-dark);
  font-size: 13px;
  font-weight: 850;
}

.creation-result-meta strong {
  font-size: 18px;
}

.creation-result-meta p {
  margin: 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.55;
}

.creation-actions {
  width: min(780px, 100%);
  margin: 0 auto 28px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.creation-action-card {
  min-height: 66px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  color: #20242c;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  align-items: center;
  gap: 8px;
  padding: 10px 10px 10px 14px;
  font-size: 14px;
  font-weight: 800;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.creation-action-card:hover {
  border-color: rgba(0, 191, 165, 0.28);
  box-shadow: 0 14px 30px rgba(15, 118, 110, 0.12);
  transform: translateY(-2px);
}

.action-preview {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  background: #f3f4f6;
}

.action-preview.cutout {
  background: #dbeafe;
}

.action-preview.erase {
  background: #fde68a;
}

.action-preview.redraw {
  background: #bae6fd;
}

.action-preview.expand {
  background: #dcfce7;
}

.action-preview.enhance {
  background: #e0f2fe;
}

.creation-gallery {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px;
  border-radius: 14px;
  overflow: hidden;
}

.creation-showcase {
  position: relative;
  min-height: 360px;
  padding: 22px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  overflow: hidden;
}

.creation-showcase::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.98;
}

.creation-showcase::after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 48%;
  background: rgba(0, 0, 0, 0.42);
}

.creation-showcase strong,
.creation-showcase p {
  position: relative;
  z-index: 1;
}

.creation-showcase strong {
  font-size: 24px;
  line-height: 1.1;
}

.creation-showcase p {
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  line-height: 1.5;
}

.poster-cat::before {
  background: #fb923c;
}

.poster-industrial::before {
  background: #f97316;
}

.poster-party::before {
  background: #f9a8d4;
}

.poster-spring::before {
  background: #86efac;
}

.chat-stage {
  overflow-y: auto;
  padding: 24px 32px 34px;
  scroll-behavior: smooth;
  background: #fff;
}

.chat-stage.home {
  display: grid;
  align-items: center;
  justify-items: center;
  background: #fff;
}

.home-center {
  width: min(760px, 100%);
  text-align: center;
  animation: portalHomeIn 0.42s cubic-bezier(.16, 1, .3, 1) both;
  margin-top: -24px;
}

.service-context {
  width: min(620px, 100%);
  margin: 0 auto 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.chat-stage.home .service-context {
  display: none;
}

.service-context.compact-context {
  width: 100%;
  margin: 0 0 10px;
  justify-content: flex-start;
}

.current-service-button,
.context-chip {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #334155;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.current-service-button {
  max-width: min(460px, 100%);
  min-height: 38px;
  padding: 5px 11px;
  text-align: left;
}

.context-chip {
  height: 38px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 700;
}

.current-service-button:hover,
.context-chip:hover {
  background: #fff;
  border-color: rgba(15, 23, 42, 0.14);
  color: var(--portal-primary-dark);
}

.current-service-button > span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.current-service-button strong {
  overflow: hidden;
  color: var(--portal-ink);
  font-size: 13px;
  font-weight: 800;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-service-button small {
  overflow: hidden;
  max-width: 360px;
  color: #667085;
  font-size: 11px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-context .current-service-button {
  min-height: 36px;
  padding: 5px 10px;
}

.compact-context .current-service-button small {
  max-width: 300px;
}

.kb-picker {
  display: grid;
  gap: 10px;
}

.kb-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-picker-head strong {
  color: var(--portal-ink);
  font-size: 14px;
}

.kb-picker-head button {
  border: 0;
  background: transparent;
  color: var(--portal-primary-dark);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.kb-picker :deep(.el-checkbox-group) {
  display: grid;
  gap: 6px;
  max-height: 280px;
  overflow-y: auto;
}

.kb-picker :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
  align-items: flex-start;
  white-space: normal;
}

.kb-option {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.kb-option strong {
  color: #26313f;
  font-size: 13px;
  line-height: 1.25;
}

.kb-option small {
  color: #667085;
  font-size: 12px;
}

.home-center h1 {
  margin: 0 0 28px;
  color: var(--portal-ink);
  font-size: 28px;
  line-height: 1.14;
  font-weight: 520;
  letter-spacing: 0;
}

.chat-home-title {
  margin: 0 0 28px;
  color: #111827;
  font-size: 28px;
  font-weight: 520;
  letter-spacing: 0;
}

.home-center h1 span {
  display: inline;
}

.home-title-accent {
  color: var(--portal-ink);
}

.home-kicker {
  margin: 0;
  color: var(--portal-primary-dark);
  font-size: 15px;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.input-card {
  position: relative;
  width: min(760px, 100%);
  margin: 0 auto;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.98);
  padding: 10px 12px;
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.08);
  text-align: left;
  backdrop-filter: blur(16px);
  box-sizing: border-box;
  transition: border-color 0.22s ease, box-shadow 0.22s ease;
}

.chat-stage.home .input-card {
  width: min(760px, 100%);
  min-height: 58px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 999px;
  background: #fff;
  padding: 7px 8px 7px 12px;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.chat-stage.home .input-card:hover,
.chat-stage.home .input-card:focus-within {
  border-color: rgba(13, 148, 136, 0.34);
  box-shadow: 0 22px 54px rgba(13, 148, 136, 0.1);
}

.input-card:hover,
.input-card:focus-within {
  border-color: rgba(15, 23, 42, 0.18);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.09);
}

.input-card.compact {
  border-radius: 12px;
}

.hidden-file-input {
  display: none;
}

.composer-input :deep(.el-textarea__inner) {
  min-height: 50px !important;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--portal-ink);
  font-size: 15px;
  line-height: 1.42;
  padding: 2px 4px 10px;
}

.chat-stage.home .composer-input {
  grid-column: 2;
  grid-row: 1;
  min-width: 0;
}

.chat-stage.home .composer-input :deep(.el-textarea__inner) {
  min-height: 36px !important;
  max-height: 36px !important;
  padding: 8px 4px 6px;
  font-size: 16px;
  line-height: 1.25;
  overflow: hidden !important;
}

.chat-stage.home .uploading-chip,
.chat-stage.home .attachment-chip,
.chat-stage.home .command-menu,
.chat-stage.home .tool-menu {
  grid-column: 1 / -1;
}

.chat-stage.home .tools-row {
  display: contents;
}

.chat-stage.home .tools-left {
  grid-column: 1;
  grid-row: 1;
  gap: 6px;
}

.chat-stage.home .tools-right {
  grid-column: 3;
  grid-row: 1;
  gap: 8px;
  min-width: 0;
}

.chat-stage.home .icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #111827;
}

.chat-stage.home .tool-pill {
  width: 34px;
  height: 34px;
  padding: 0;
  color: #111827;
  justify-content: center;
}

.home-model-button {
  max-width: 120px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #111827;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 6px 0 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 760;
  cursor: pointer;
}

.home-model-button:hover {
  background: #f4f4f5;
}

.home-model-button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-model-button .el-icon {
  flex: 0 0 auto;
  color: #6b7280;
  font-size: 13px;
}

.command-menu {
  margin: 0 0 12px;
  border: 1px solid rgba(0, 191, 165, 0.16);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 46px rgba(15, 118, 110, 0.12);
  overflow: hidden;
  animation: commandMenuIn 0.18s ease both;
}

.command-item {
  width: 100%;
  min-height: 54px;
  border: 0;
  border-bottom: 1px solid rgba(0, 191, 165, 0.09);
  display: grid;
  grid-template-columns: 108px minmax(0, 128px) 1fr;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.command-item:last-child {
  border-bottom: 0;
}

.command-item:hover {
  background: #ecfdf5;
}

.command-item span {
  color: var(--portal-primary);
  font-size: 13px;
  font-weight: 900;
}

.command-item strong {
  color: var(--portal-ink);
  font-size: 14px;
}

.command-item small {
  color: var(--portal-muted);
  font-size: 12px;
}

.command-empty {
  padding: 14px;
  color: var(--portal-muted);
  font-size: 13px;
}

.tools-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tools-left,
.tools-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chip-btn {
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: #ecfdf5;
  color: var(--portal-primary-dark);
  padding: 0 14px;
  font-size: 13px;
  cursor: pointer;
}

.chip-btn:hover {
  background: #ccfbf1;
}

.chip-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tool-pill {
  height: 32px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #334155;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.tool-pill:hover,
.tool-pill.active {
  background: #f8fafc;
  color: var(--portal-primary-dark);
  transform: translateY(-1px);
}

.tool-pill:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.tool-menu {
  position: absolute;
  left: 72px;
  top: calc(100% - 8px);
  z-index: 8;
  width: min(310px, calc(100% - 92px));
  padding: 8px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.98);
  box-shadow: 0 22px 56px rgba(15, 23, 42, 0.16);
  animation: commandMenuIn 0.18s ease both;
}

.compact-tool-menu {
  top: auto;
  bottom: 54px;
}

.tool-item {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #303642;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
}

.tool-item:hover {
  background: rgba(0, 191, 165, 0.1);
}

.tool-item > .el-icon {
  font-size: 18px;
  color: #4b5563;
}

.tool-item span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.tool-item strong {
  color: var(--portal-ink);
  font-size: 14px;
  line-height: 1.2;
}

.tool-item small {
  color: var(--portal-muted);
  font-size: 12px;
  line-height: 1.25;
}

.send-button {
  width: 38px;
  height: 38px;
  border: 0;
  background: #050505 !important;
  border-color: #050505 !important;
  color: #fff !important;
  box-shadow: none !important;
}

.chat-stage.home .send-button {
  width: 42px;
  height: 42px;
  background: #050505 !important;
  color: #fff;
  box-shadow: none;
}

.chat-stage.home .send-button.is-disabled {
  background: #050505 !important;
  opacity: 0.9;
}

.send-button.is-disabled {
  background: #050505 !important;
  border-color: #050505 !important;
  opacity: 0.9;
}

.suggestions {
  width: min(760px, 100%);
  margin: 12px auto 0;
  display: flex;
  gap: 6px;
  justify-content: center;
  flex-wrap: wrap;
}

.chat-stage.home .suggestions {
  display: none;
}

.home-action-chips {
  width: min(520px, 100%);
  margin: 22px auto 0;
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.home-action-chip {
  height: 38px;
  border: 1px solid rgba(15, 23, 42, 0.11);
  border-radius: 999px;
  background: #fff;
  color: #8a8a8e;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 520;
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.home-action-chip:hover {
  border-color: rgba(13, 148, 136, 0.28);
  background: #f2faf8;
  color: var(--portal-primary-dark);
}

.home-action-chip .el-icon {
  font-size: 17px;
  color: var(--portal-primary);
}

.suggestion-chip {
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 7px 10px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.suggestion-chip:hover {
  border-color: rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
  color: var(--portal-primary-dark);
}

.messages-wrap {
  width: min(980px, 100%);
  margin: 0 auto;
  padding-bottom: 180px;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: messageIn 0.28s ease both;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #ecfdf5;
  color: var(--portal-primary-dark);
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.message-bubble {
  max-width: min(820px, 82%);
  border: 1px solid rgba(0, 191, 165, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  padding: 12px 14px;
}

.message-row.user .message-bubble {
  border-color: transparent;
  color: #fff;
  background: var(--portal-primary-dark);
}

.message-role {
  color: #8a8f98;
  font-size: 12px;
  margin-bottom: 5px;
}

.message-row.user .message-role {
  color: rgba(255, 255, 255, 0.78);
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.68;
}

.typing-line {
  color: #6d7280;
}

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 0.3em 0 0.55em;
}

.markdown-body :deep(p) {
  margin: 0.52em 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.5em 0 0.7em;
  padding-left: 1.2em;
}

.markdown-body :deep(code) {
  padding: 0.08em 0.3em;
  border-radius: 6px;
  background: rgba(100, 116, 139, 0.18);
}

.markdown-body :deep(pre) {
  margin: 0.75em 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e5e7eb;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.composer-dock {
  position: fixed;
  left: calc(var(--sidebar-width) + 20px);
  right: 20px;
  bottom: 16px;
}

.composer-dock .input-card {
  width: min(980px, 100%);
  margin: 0 auto;
}

.kb-status {
  max-width: 220px;
  color: #8a8d95;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uploading-chip,
.attachment-chip {
  width: fit-content;
  max-width: 100%;
  margin-bottom: 8px;
  border-radius: 12px;
  border: 1px solid #e3e5e9;
  background: #f4f5f7;
  color: #3e424b;
  min-height: 34px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.attachment-chip span {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-chip button {
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

@keyframes portalHomeIn {
  from {
    opacity: 0;
    transform: translateY(24px);
    filter: blur(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@keyframes portalPanelIn {
  from {
    opacity: 0;
    transform: translateX(16px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes messageIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes commandMenuIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 1080px) {
  .service-portal {
    --sidebar-width: 240px;
    grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  }

  .creation-workspace-head {
    right: 14px;
  }

  .composer-dock {
    right: 20px;
  }

  .home-center {
    width: min(820px, 100%);
  }

  .composer-input :deep(.el-textarea__inner) {
    font-size: 15px;
  }
}

@media (max-width: 880px) {
  .service-portal {
    grid-template-columns: 1fr;
  }

  .portal-sidebar {
    display: none;
  }

  .creation-stage {
    padding: 18px;
  }

  .creation-service-hint {
    width: 100%;
    padding: 22px;
    gap: 16px;
  }

  .creation-empty-hero strong {
    font-size: 20px;
  }

  .creation-empty-grid {
    grid-template-columns: 1fr;
  }

  .creation-empty-step {
    min-height: 0;
  }

  .creation-empty-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .creation-service-actions {
    justify-content: stretch;
  }

  .creation-service-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .creation-workspace-head {
    position: static;
    width: 100%;
    margin: 0 0 12px;
  }

  .creation-mode-tabs {
    width: 100%;
    justify-content: space-between;
  }

  .creation-mode-tabs button {
    flex: 1;
    justify-content: center;
  }

  .composer-dock {
    left: 12px;
    right: 12px;
    bottom: 12px;
  }

  .chat-stage {
    padding: 12px 12px 18px;
  }

  .chat-stage.home .premium-model-picker {
    display: none;
  }
}

/* Premium Enhancements */
.home-greeting {
  text-align: center;
  margin-bottom: 30px;
  animation: portalHomeIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.chat-home-title {
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 680;
  color: var(--portal-ink);
  margin-bottom: 10px;
  letter-spacing: 0;
}
.chat-home-subtitle {
  font-size: 18px;
  color: #64748b;
  font-weight: 400;
  letter-spacing: 0;
}

.chat-home-subtitle span {
  color: var(--portal-primary);
  font-weight: 750;
}

.premium-tools {
  margin-top: 12px;
}
.premium-icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  font-size: 20px;
}
.premium-icon-btn:hover {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-dark);
}

.premium-icon-btn.active {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-dark);
}

.premium-model-picker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}
.premium-model-picker:hover {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-dark);
}

.premium-send-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #e7efed;
  color: #8ba9a4;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 18px;
}
.premium-send-btn.active {
  background: var(--portal-primary);
  color: #fff;
}
.premium-send-btn.active:hover {
  background: var(--portal-primary-dark);
  transform: translateY(-1px);
}
.premium-send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.message-row.user {
  flex-direction: row-reverse;
}
.message-row.user .premium-bubble {
  background: #f1f5f9;
  color: var(--portal-ink);
  border: none;
  border-radius: 16px;
  border-bottom-right-radius: 4px;
  padding: 14px 20px;
  font-size: 15px;
}
.message-row.assistant .premium-bubble {
  background: transparent;
  border: none;
  padding: 4px 10px;
}
.premium-avatar {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
}

@media (max-width: 880px) {
  .home-center {
    width: 100%;
    margin-top: 0;
  }

  .home-center h1 {
    font-size: 28px;
    margin-bottom: 18px;
  }

  .service-context {
    margin-bottom: 14px;
  }

  .input-card,
  .suggestions {
    width: 100%;
  }
}
</style>
