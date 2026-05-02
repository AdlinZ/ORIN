<template>
  <div class="service-portal">
    <aside class="portal-sidebar">
      <div class="sidebar-brand-row">
        <img class="sidebar-brand-logo" src="/logo.svg" alt="ORIN" />
      </div>

      <nav class="sidebar-nav">
        <button
          class="nav-item"
          :class="{ active: activeWorkspace === 'chat' }"
          type="button"
          @click="startNewSession"
        >
          <el-icon><EditPen /></el-icon>
          <span>新对话</span>
          <kbd>⌘ K</kbd>
        </button>
        <button
          class="nav-item"
          :class="{ active: activeWorkspace === 'creation' }"
          type="button"
          @click="openCreationStudio"
        >
          <el-icon><MagicStick /></el-icon>
          <span>AI 创作</span>
        </button>
        <button class="nav-item" type="button">
          <el-icon><Grid /></el-icon>
          <span>更多</span>
          <el-icon class="nav-tail"><ArrowRight /></el-icon>
        </button>
      </nav>

      <section class="sidebar-section">
        <div class="section-title">历史对话</div>
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
              <el-icon class="session-icon"><ChatRound /></el-icon>
              <span class="session-title">{{ session.title || '未命名会话' }}</span>
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
            <el-avatar :size="40" :src="userAvatar" class="portal-user-avatar">
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
            <div>
              <h1>AI 创作</h1>
              <p>{{ creationWorkspaceSubtitle }}</p>
            </div>
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
            {{ creationServiceHint }}
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
            <p>请联系平台管理员完成智能体、模型资源与参考资料配置。</p>
          </div>
        </template>

        <template v-else-if="isHome">
          <div class="home-center">
            <h1>
              <span>今天想让 ORIN</span>
              <span class="home-title-accent">帮你处理什么？</span>
            </h1>
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
                :placeholder="selectedUploadFileName ? '补充你想让 ORIN 如何处理这个文件...' : '输入问题，或按 / 选择常用操作'"
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

            <div class="suggestions">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                class="suggestion-chip"
                type="button"
                @click="applyPrompt(prompt)"
              >
                {{ prompt }}
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
              <div class="message-avatar">
                <el-icon v-if="message.role === 'user'"><User /></el-icon>
                <el-icon v-else><Cpu /></el-icon>
              </div>
              <div class="message-bubble">
                <div class="message-role">{{ message.role === 'user' ? '我' : currentAgent?.name || 'ORIN' }}</div>
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
                :placeholder="selectedUploadFileName ? '补充你想让 ORIN 如何处理这个文件...' : '输入问题，或按 / 选择常用操作'"
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
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { marked } from 'marked';
import {
  ArrowRight,
  Brush,
  ChatRound,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  EditPen,
  Files,
  Grid,
  Loading,
  MagicStick,
  Microphone,
  Operation,
  Paperclip,
  Picture,
  Plus,
  ScaleToOriginal,
  Top,
  User,
  VideoCamera
} from '@element-plus/icons-vue';
import {
  createChatSession,
  deleteChatSession,
  getAttachedKnowledgeBases,
  getChatSession,
  listAgents,
  listChatSessions,
  listKnowledgeBases,
  sendChatMessage
} from '@/api/agent-chat';
import { chatAgent } from '@/api/agent';
import { uploadMultimodalFile } from '@/api/multimodal';
import { useUserStore } from '@/stores/user';
import ImageGenerator from '@/views/Agent/components/ImageGenerator.vue';
import VideoGenerator from '@/views/Agent/components/VideoGenerator.vue';
import AudioGenerator from '@/views/Agent/components/AudioGenerator.vue';

const router = useRouter();
const userStore = useUserStore();

const agents = ref([]);
const knowledgeBases = ref([]);
const sessions = ref([]);
const messages = ref([]);
const currentAgentId = ref('');
const currentSessionId = ref('');
const selectedKbIds = ref([]);
const selectedUploadFileId = ref('');
const selectedUploadFileName = ref('');
const inputMessage = ref('');
const activeWorkspace = ref('chat');
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

const quickPrompts = [
  '帮我总结这份资料的关键结论',
  '给我生成一个可执行的任务计划',
  '根据参考资料回答客户这个问题',
  '把下面内容改写成正式邮件'
];

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
  if (creationAgent.value) return '';
  if (loadingAgents.value) return '正在加载创作服务...';
  if (creatorMode.value === 'video') return '当前没有可用的视频生成服务，请先让管理员配置 TEXT_TO_VIDEO 智能体。';
  if (creatorMode.value === 'audio') return '当前没有可用的语音合成服务，请先让管理员配置 TEXT_TO_SPEECH 智能体。';
  return '当前没有可用的图像生成服务，请先让管理员配置 TEXT_TO_IMAGE 智能体。';
});

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

const currentSessionTitle = computed(() => {
  if (currentSessionId.value) {
    const session = sessions.value.find((item) => item.id === currentSessionId.value);
    if (session?.title) return session.title;
  }
  return '有什么可以帮你？';
});

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
  if (session.id && !sessions.value.some((item) => item.id === session.id)) {
    sessions.value.unshift(session);
  }
  return currentSessionId.value;
};

const loadAttachedKnowledgeBases = async () => {
  if (!currentSessionId.value) {
    selectedKbIds.value = [];
    return;
  }

  try {
    const res = await getAttachedKnowledgeBases(currentSessionId.value);
    selectedKbIds.value = unwrapList(res).map((kb) => normalizeId(kb.id ?? kb.kbId ?? kb.knowledgeBaseId)).filter(Boolean);
  } catch (error) {
    selectedKbIds.value = [];
  }
};

const selectAgent = async (agent) => {
  currentAgentId.value = agent.id;
  currentSessionId.value = '';
  messages.value = [];
  selectedKbIds.value = [];
  await loadSessions();
};

const startNewSession = async () => {
  activeWorkspace.value = 'chat';
  if (!currentAgentId.value) return;
  currentSessionId.value = '';
  messages.value = [];
  selectedKbIds.value = [];
  clearUploadedFile();
  await ensureSession();
  await loadSessions();
};

const openSession = async (session) => {
  activeWorkspace.value = 'chat';
  currentSessionId.value = session.id;
  selectedKbIds.value = [];
  try {
    const res = await getChatSession(session.id);
    const data = res?.data ?? res;
    const rawMessages = data?.messages || data?.conversation || data?.history || [];
    messages.value = Array.isArray(rawMessages) ? rawMessages.map(normalizeMessage).filter((msg) => msg.content) : [];
    await loadAttachedKnowledgeBases();
    await scrollToBottom();
  } catch (error) {
    ElMessage.error('会话加载失败');
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
      currentSessionId.value = '';
      const nextSession = sessions.value[0];
      if (nextSession) {
        await openSession(nextSession);
      }
    }
  } catch (error) {
    ElMessage.error(error?.message || '删除会话失败');
  }
};

const triggerFilePicker = () => {
  if (!currentAgentId.value || uploadingFile.value) return;
  showToolsMenu.value = false;
  fileInputRef.value?.click();
};

const openCreationStudio = () => {
  activeWorkspace.value = 'creation';
  showToolsMenu.value = false;
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
    ElMessage.warning(creationServiceHint.value || '当前没有可用的创作服务');
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

  try {
    const res = hasAttachment
      ? await chatAgent(
        currentAgentId.value,
        outboundMessage || `请帮我处理这个文件：${selectedUploadFileName.value || '已上传附件'}`,
        selectedUploadFileId.value
      )
      : await sendChatMessage(sessionId, {
        message: outboundMessage,
        kbIds: selectedKbIds.value
      });

    messages.value.push({
      role: 'assistant',
      content: extractAssistantReply(res),
      createdAt: new Date().toISOString()
    });
    clearUploadedFile();
    await loadSessions();
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: '请求失败，请稍后重试或联系管理员检查智能体服务状态。',
      createdAt: new Date().toISOString()
    });
  } finally {
    sending.value = false;
    await scrollToBottom();
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

const handleComposerEnter = () => {
  if (showCommandMenu.value && filteredCommandActions.value.length) {
    applyCommand(filteredCommandActions.value[0]);
    return;
  }
  sendMessage();
};

const handleUserCommand = (command) => {
  if (command === 'profile') {
    router.push('/dashboard/profile');
    return;
  }
  if (command === 'settings') {
    router.push('/dashboard/profile');
    return;
  }
  if (command === 'dashboard') {
    router.push('/dashboard');
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
  await refreshPortal();
  if (agents.value.length) {
    await selectAgent(agents.value[0]);
  }
});
</script>

<style scoped>
.service-portal {
  --sidebar-width: 280px;
  --portal-primary: #00bfa5;
  --portal-primary-dark: #0f766e;
  --portal-ink: #101828;
  --portal-muted: #667085;
  --portal-line: rgba(0, 191, 165, 0.14);
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  background:
    radial-gradient(circle at 52% 12%, rgba(0, 191, 165, 0.12), transparent 32%),
    linear-gradient(180deg, #f7fffd 0%, #f8fafc 44%, #ffffff 100%);
  color: var(--portal-ink);
}

.portal-sidebar {
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-right: 1px solid var(--portal-line);
  box-sizing: border-box;
}

.sidebar-brand-row {
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 4px;
  color: var(--portal-ink);
}

.sidebar-brand-logo {
  width: 112px;
  height: 58px;
  display: block;
  object-fit: contain;
  flex: 0 0 auto;
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
  background: #ecfdf5;
  color: var(--portal-primary-dark);
}

.icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.icon-btn.ghost {
  background: #ecfdf5;
  color: var(--portal-primary-dark);
}

.portal-user-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.portal-user-wrapper:hover {
  background: #f2f4f7;
}

.portal-user-avatar {
  flex-shrink: 0;
  border: 2px solid #eef2f6;
  background: linear-gradient(135deg, var(--portal-primary), var(--portal-primary-dark));
  box-shadow: 0 2px 8px rgba(15, 118, 110, 0.12);
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
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portal-user-role {
  width: fit-content;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--portal-primary-dark);
  background: #ecfdf5;
  font-size: 11px;
  font-weight: 700;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  width: 100%;
  min-height: 38px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #20242c;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  font-size: 14px;
  font-weight: 650;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.nav-item:hover {
  background: rgba(0, 191, 165, 0.08);
  color: var(--portal-primary-dark);
  transform: translateX(2px);
}

.nav-item.active {
  background: rgba(0, 191, 165, 0.1);
  box-shadow: inset 0 0 0 1px rgba(0, 191, 165, 0.14);
  color: var(--portal-primary-dark);
}

.nav-item kbd {
  margin-left: auto;
  min-width: 42px;
  height: 20px;
  border: 1px solid rgba(0, 191, 165, 0.2);
  border-radius: 6px;
  color: #6b7280;
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

.sidebar-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.section-title {
  padding: 22px 10px 8px;
  color: var(--portal-muted);
  font-size: 12px;
  font-weight: 800;
}

.session-list {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-item {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.session-item:hover {
  background: #f0fdfa;
  transform: translateX(2px);
}

.session-item.active {
  background: #ccfbf1;
}

.session-head {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-icon {
  color: #98a2b3;
  font-size: 16px;
  flex: 0 0 auto;
}

.session-title {
  min-width: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  margin-left: auto;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
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
  color: #8a8d95;
  font-size: 11px;
}

.sidebar-empty {
  color: #8a8d95;
  font-size: 12px;
  padding: 8px 10px;
}

.sidebar-bottom {
  display: grid;
  gap: 8px;
  padding: 10px 4px 4px;
  border-top: 1px solid var(--portal-line);
  background: transparent;
}

.portal-main {
  min-width: 0;
  height: 100vh;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
}

.creation-stage {
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px;
  background:
    radial-gradient(circle at 50% 12%, rgba(0, 191, 165, 0.1), transparent 26%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.64), rgba(248, 250, 252, 0.94));
}

.creation-workspace {
  height: calc(100vh - 48px);
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
}

.creation-workspace-head {
  min-height: 54px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.creation-workspace-head h1 {
  margin: 0;
  color: var(--portal-ink);
  font-size: 26px;
  font-weight: 900;
  letter-spacing: 0;
}

.creation-workspace-head p {
  margin: 6px 0 0;
  color: #7c8491;
  font-size: 13px;
}

.creation-mode-tabs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(0, 191, 165, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  padding: 4px;
  box-shadow: 0 12px 34px rgba(15, 118, 110, 0.08);
}

.creation-mode-tabs button {
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #4b5563;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.creation-mode-tabs button.active {
  background: var(--portal-primary);
  color: #fff;
  box-shadow: 0 12px 24px rgba(0, 191, 165, 0.24);
}

.creation-runner {
  min-height: 0;
  border: 1px solid rgba(0, 191, 165, 0.12);
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 22px 70px rgba(15, 118, 110, 0.1);
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
  margin: -10px auto 20px;
  border: 1px solid rgba(245, 158, 11, 0.18);
  border-radius: 12px;
  background: rgba(255, 251, 235, 0.74);
  color: #92400e;
  padding: 10px 12px;
  font-size: 13px;
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
  background:
    radial-gradient(circle at 55% 58%, #f7c9a5 0 22%, transparent 23%),
    radial-gradient(circle at 48% 38%, #111827 0 9%, transparent 10%),
    linear-gradient(135deg, #f7fafc 0 45%, #dbeafe 45% 100%);
}

.action-preview.erase {
  background:
    linear-gradient(135deg, transparent 0 36%, rgba(255, 255, 255, 0.88) 36% 54%, transparent 54%),
    radial-gradient(circle at 55% 42%, #4f46e5 0 18%, transparent 19%),
    linear-gradient(135deg, #fde68a, #f9a8d4);
}

.action-preview.redraw {
  background:
    radial-gradient(circle at 62% 58%, #a16207 0 18%, transparent 19%),
    radial-gradient(circle at 42% 44%, #fbbf24 0 14%, transparent 15%),
    linear-gradient(135deg, #bae6fd, #fef3c7);
}

.action-preview.expand {
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.62) 0 20%, transparent 20% 80%, rgba(255, 255, 255, 0.62) 80%),
    radial-gradient(circle at 50% 54%, #60a5fa 0 22%, transparent 23%),
    linear-gradient(135deg, #dcfce7, #dbeafe);
}

.action-preview.enhance {
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.3) 0 44%, transparent 44%),
    radial-gradient(circle at 63% 40%, #93c5fd 0 26%, transparent 27%),
    linear-gradient(135deg, #e0f2fe, #f8fafc);
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
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.58));
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
  background:
    radial-gradient(circle at 48% 54%, #fed7aa 0 18%, transparent 19%),
    radial-gradient(circle at 54% 48%, #fff7ed 0 28%, transparent 29%),
    repeating-linear-gradient(45deg, transparent 0 26px, rgba(0, 0, 0, 0.18) 26px 30px),
    linear-gradient(135deg, #fb923c, #ea580c 56%, #111827 57%);
}

.poster-industrial::before {
  background:
    radial-gradient(circle at 62% 31%, #a16207 0 18%, transparent 19%),
    radial-gradient(circle at 38% 64%, #bef264 0 15%, transparent 16%),
    linear-gradient(90deg, rgba(0, 0, 0, 0.12) 0 12%, transparent 12% 100%),
    linear-gradient(135deg, #fb5a14, #f97316 44%, #22d3ee 45% 64%, #84cc16 65%);
}

.poster-party::before {
  background:
    radial-gradient(circle at 56% 58%, #f9a8d4 0 24%, transparent 25%),
    radial-gradient(circle at 70% 34%, #fbbf24 0 12%, transparent 13%),
    linear-gradient(135deg, #fde68a, #f9a8d4 62%, #f59e0b);
}

.poster-spring::before {
  background:
    radial-gradient(circle at 55% 50%, rgba(34, 197, 94, 0.55) 0 22%, transparent 23%),
    radial-gradient(circle at 62% 34%, rgba(255, 255, 255, 0.85) 0 8%, transparent 9%),
    radial-gradient(circle at 42% 68%, rgba(255, 255, 255, 0.7) 0 10%, transparent 11%),
    linear-gradient(135deg, #ecfccb, #86efac 52%, #f7fee7);
}

.chat-stage {
  overflow-y: auto;
  padding: 16px 24px 24px;
  scroll-behavior: smooth;
}

.chat-stage.home {
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 50% 28%, rgba(0, 191, 165, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.24), rgba(248, 250, 252, 0.78));
}

.home-center {
  width: min(860px, 100%);
  text-align: center;
  animation: portalHomeIn 0.72s cubic-bezier(.16, 1, .3, 1) both;
}

.home-center h1 {
  margin: 8px 0 24px;
  color: var(--portal-ink);
  font-size: clamp(34px, 4.8vw, 58px);
  line-height: 1.08;
  font-weight: 900;
}

.home-center h1 span {
  display: block;
}

.home-title-accent {
  color: var(--portal-primary);
  text-shadow: 0 18px 48px rgba(0, 191, 165, 0.18);
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
  border: 1px solid rgba(0, 191, 165, 0.18);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  padding: 16px 18px 12px;
  box-shadow: 0 26px 70px rgba(15, 118, 110, 0.12);
  text-align: left;
  backdrop-filter: blur(16px);
  transition: border-color 0.24s ease, box-shadow 0.24s ease, transform 0.24s ease;
}

.input-card:hover,
.input-card:focus-within {
  border-color: rgba(0, 191, 165, 0.34);
  box-shadow: 0 30px 84px rgba(15, 118, 110, 0.16);
  transform: translateY(-2px);
}

.input-card.compact {
  border-radius: 12px;
}

.hidden-file-input {
  display: none;
}

.composer-input :deep(.el-textarea__inner) {
  min-height: 56px !important;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--portal-ink);
  font-size: 16px;
  line-height: 1.34;
  padding: 2px 4px 8px;
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
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: #f3f4f6;
  color: #3f4652;
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
  background: #e6fffa;
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
  width: 40px;
  height: 40px;
  border: 0;
  background: var(--portal-primary);
  box-shadow: 0 12px 28px rgba(0, 191, 165, 0.28);
}

.send-button.is-disabled {
  background: #c7ccd4;
}

.suggestions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}

.suggestion-chip {
  border: 0;
  border-radius: 8px;
  padding: 10px 14px;
  background: rgba(236, 253, 245, 0.8);
  color: var(--portal-primary-dark);
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.suggestion-chip:hover {
  background: #ccfbf1;
  transform: translateY(-2px);
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

  .composer-dock {
    right: 20px;
  }

  .home-kicker {
    font-size: 30px;
  }

  .composer-input :deep(.el-textarea__inner) {
    font-size: 20px;
  }
}

@media (max-width: 880px) {
  .service-portal {
    grid-template-columns: 1fr;
  }

  .portal-sidebar {
    display: none;
  }

  .composer-dock {
    left: 12px;
    right: 12px;
    bottom: 12px;
  }

  .chat-stage {
    padding: 12px 12px 18px;
  }
}
</style>
