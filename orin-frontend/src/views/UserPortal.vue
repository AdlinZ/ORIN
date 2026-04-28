<template>
  <div class="service-portal" :class="{ collapsed: isSidebarCollapsed }">
    <aside class="portal-sidebar">
      <div class="sidebar-tools">
        <button class="icon-btn" type="button" title="收起侧栏" @click="toggleSidebar">
          <el-icon><component :is="isSidebarCollapsed ? Expand : Fold" /></el-icon>
        </button>
        <button class="icon-btn" type="button" title="搜索会话">
          <el-icon><Search /></el-icon>
        </button>
      </div>

      <div class="sidebar-brand">
        <div class="brand-avatar">{{ avatarText }}</div>
        <div class="brand-copy">
          <strong>ORIN</strong>
          <span>{{ displayName }}</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button class="nav-item active" type="button" @click="startNewSession">
          <el-icon><EditPen /></el-icon>
          <span>新对话</span>
        </button>
        <button class="nav-item" type="button">
          <el-icon><Collection /></el-icon>
          <span>我的内容</span>
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
            <span class="session-time">{{ formatDate(session.updatedAt || session.createdAt) }}</span>
          </button>
          <div v-if="currentAgent && !loadingSessions && sessions.length === 0" class="sidebar-empty">还没有会话</div>
        </div>
      </section>

      <div class="sidebar-bottom">
        <button class="nav-item" type="button" @click="router.push('/dashboard/profile')">
          <el-icon><User /></el-icon>
          <span>设置与帮助</span>
        </button>
      </div>
    </aside>

    <main class="portal-main">
      <header class="main-header">
        <div class="header-left">
          <button class="icon-btn" type="button" @click="toggleSidebar">
            <el-icon><component :is="isSidebarCollapsed ? Expand : Fold" /></el-icon>
          </button>
          <strong>ORIN</strong>
        </div>
        <div class="header-right">
          <el-button text :icon="RefreshRight" :loading="loadingAgents" @click="refreshPortal">刷新</el-button>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-pill" type="button">
              <span>{{ userStore.isAdmin ? 'PRO' : 'USER' }}</span>
              <div class="brand-avatar mini">{{ avatarText }}</div>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="dashboard">管理端</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section ref="messagesRef" class="chat-stage" :class="{ home: isHome }">
        <template v-if="!currentAgent">
          <div class="home-center">
            <h1>你好，我是 ORIN</h1>
            <p>当前没有可用服务，请联系管理员完成模型配置。</p>
          </div>
        </template>

        <template v-else-if="isHome">
          <div class="home-center">
            <p class="home-kicker">准备好了，随时开始</p>
            <h1>{{ currentSessionTitle }}</h1>
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
                :placeholder="selectedUploadFileName ? '补充你想让 ORIN 如何处理这个文件...' : '有问题，尽管问'"
                @keydown.enter.exact.prevent="sendMessage"
              />
              <div class="tools-row">
                <div class="tools-left">
                  <button
                    type="button"
                    class="icon-btn ghost"
                    :disabled="!currentAgent || uploadingFile"
                    @click="triggerFilePicker"
                  >
                    <el-icon><component :is="uploadingFile ? Loading : Plus" /></el-icon>
                  </button>
                  <el-popover placement="top-start" :width="340" trigger="click">
                    <template #reference>
                      <button type="button" class="chip-btn" :disabled="!currentSessionId">知识库</button>
                    </template>
                    <div class="kb-popover">
                      <div class="popover-title">选择知识库</div>
                      <el-input
                        v-model="kbSearch"
                        class="popover-search"
                        placeholder="搜索知识库"
                        :prefix-icon="Search"
                        clearable
                      />
                      <label v-for="kb in filteredKnowledgeBases" :key="kb.id" class="kb-item">
                        <div>
                          <strong>{{ kb.name }}</strong>
                          <span>{{ kb.documentCount || 0 }} 个文档</span>
                        </div>
                        <el-checkbox
                          :model-value="selectedKbIds.includes(kb.id)"
                          :disabled="!currentSessionId"
                          @change="toggleKnowledgeBase(kb.id)"
                        />
                      </label>
                    </div>
                  </el-popover>
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
                :placeholder="selectedUploadFileName ? '补充你想让 ORIN 如何处理这个文件...' : '有问题，尽管问'"
                @keydown.enter.exact.prevent="sendMessage"
              />
              <div class="tools-row">
                <div class="tools-left">
                  <button
                    type="button"
                    class="icon-btn ghost"
                    :disabled="!currentAgent || uploadingFile"
                    @click="triggerFilePicker"
                  >
                    <el-icon><component :is="uploadingFile ? Loading : Plus" /></el-icon>
                  </button>
                  <el-popover placement="top-start" :width="340" trigger="click">
                    <template #reference>
                      <button type="button" class="chip-btn" :disabled="!currentSessionId">知识库</button>
                    </template>
                    <div class="kb-popover">
                      <div class="popover-title">选择知识库</div>
                      <el-input
                        v-model="kbSearch"
                        class="popover-search"
                        placeholder="搜索知识库"
                        :prefix-icon="Search"
                        clearable
                      />
                      <label v-for="kb in filteredKnowledgeBases" :key="kb.id" class="kb-item">
                        <div>
                          <strong>{{ kb.name }}</strong>
                          <span>{{ kb.documentCount || 0 }} 个文档</span>
                        </div>
                        <el-checkbox
                          :model-value="selectedKbIds.includes(kb.id)"
                          :disabled="!currentSessionId"
                          @change="toggleKnowledgeBase(kb.id)"
                        />
                      </label>
                    </div>
                  </el-popover>
                </div>
                <div class="tools-right">
                  <span class="kb-status">{{ currentKbNames }}</span>
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
            </div>
          </footer>
        </template>
      </section>
    </main>
    <aside class="portal-context">
      <section class="context-card">
        <span class="context-eyebrow">当前服务</span>
        <strong>{{ currentAgent?.name || '未选择智能体' }}</strong>
        <p>{{ currentAgent?.description || '选择一个已接入的智能体后，即可开始企业服务会话。' }}</p>
      </section>
      <section class="context-card">
        <span class="context-eyebrow">知识范围</span>
        <strong>{{ selectedKbIds.length }} 个知识库</strong>
        <p>{{ currentKbNames }}</p>
      </section>
      <section class="context-card">
        <span class="context-eyebrow">会话治理</span>
        <div class="context-list">
          <span>消息数：{{ messages.length }}</span>
          <span>历史会话：{{ sessions.length }}</span>
          <span>附件：{{ selectedUploadFileName || '未附加' }}</span>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { marked } from 'marked';
import {
  Collection,
  Cpu,
  Delete,
  Document,
  EditPen,
  Expand,
  Fold,
  Loading,
  Plus,
  RefreshRight,
  Search,
  Top,
  User
} from '@element-plus/icons-vue';
import {
  attachKnowledgeBase,
  createChatSession,
  deleteChatSession,
  getAttachedKnowledgeBases,
  getChatSession,
  listAgents,
  listChatSessions,
  listKnowledgeBases,
  sendChatMessage
} from '@/api/agent-chat';
import { detachKnowledgeBase } from '@/api/agent-chat';
import { chatAgent } from '@/api/agent';
import { uploadMultimodalFile } from '@/api/multimodal';
import { useUserStore } from '@/stores/user';

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
const kbSearch = ref('');
const loadingAgents = ref(false);
const loadingKnowledge = ref(false);
const loadingSessions = ref(false);
const sending = ref(false);
const uploadingFile = ref(false);
const isSidebarCollapsed = ref(false);
const messagesRef = ref(null);
const fileInputRef = ref(null);

const quickPrompts = [
  '帮我总结这份资料的关键结论',
  '给我生成一个可执行的任务计划',
  '根据知识库回答客户这个问题',
  '把下面内容改写成正式邮件'
];

const displayName = computed(() => userStore.userInfo?.name || userStore.userInfo?.username || userStore.username || '用户');
const avatarText = computed(() => String(displayName.value || 'U').slice(0, 1).toUpperCase());
const isHome = computed(() => messages.value.length === 0);

const currentAgent = computed(() => agents.value.find((agent) => agent.id === currentAgentId.value));

const currentSessionTitle = computed(() => {
  if (currentSessionId.value) {
    const session = sessions.value.find((item) => item.id === currentSessionId.value);
    if (session?.title) return session.title;
  }
  return '有什么可以帮你？';
});

const filteredKnowledgeBases = computed(() => {
  const keyword = kbSearch.value.trim().toLowerCase();
  if (!keyword) return knowledgeBases.value;
  return knowledgeBases.value.filter((kb) => {
    return [kb.name, kb.description, kb.type]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
});

const currentKbNames = computed(() => {
  if (!selectedKbIds.value.length) return '未绑定知识库';
  const names = selectedKbIds.value
    .map((id) => knowledgeBases.value.find((kb) => kb.id === id)?.name)
    .filter(Boolean);
  return names.length ? `已绑定：${names.join('、')}` : `已绑定 ${selectedKbIds.value.length} 个知识库`;
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
  modelType: agent.modelType || agent.type || ''
});

const normalizeKb = (kb) => ({
  ...kb,
  id: normalizeId(kb.id ?? kb.kbId ?? kb.knowledgeBaseId),
  name: kb.name || kb.kbName || kb.title || `知识库 ${kb.id ?? kb.kbId}`,
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
  if (!currentAgentId.value) return;
  currentSessionId.value = '';
  messages.value = [];
  selectedKbIds.value = [];
  clearUploadedFile();
  await ensureSession();
  await loadSessions();
};

const openSession = async (session) => {
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

const toggleKnowledgeBase = async (kbId) => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体');
    return;
  }
  const sessionId = await ensureSession();
  if (!sessionId) return;

  const id = normalizeId(kbId);
  const exists = selectedKbIds.value.includes(id);
  if (exists) {
    await detachKnowledgeBase(sessionId, id);
    selectedKbIds.value = selectedKbIds.value.filter((item) => item !== id);
  } else {
    await attachKnowledgeBase(sessionId, id);
    selectedKbIds.value.push(id);
  }
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
        return '请求失败：当前智能体模型不支持文件直读。请切换为支持文件/视觉的模型（VLM），或先将文档导入知识库再提问。';
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

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
};

const applyPrompt = (prompt) => {
  inputMessage.value = prompt;
};

const handleUserCommand = (command) => {
  if (command === 'profile') {
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
  --context-width: 300px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr) var(--context-width);
  background: #f8f9fb;
  color: #171717;
}

.service-portal.collapsed {
  --sidebar-width: 78px;
}

.portal-sidebar {
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.84);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-right: 1px solid #e1e6ec;
  box-sizing: border-box;
}

.sidebar-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.icon-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #5b5d63;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-btn:hover {
  background: #e7e8eb;
}

.icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.icon-btn.ghost {
  background: #eff0f2;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
}

.brand-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  background: #0f766e;
}

.brand-avatar.mini {
  width: 30px;
  height: 30px;
  font-size: 12px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-copy strong {
  font-size: 20px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: 0;
}

.brand-copy span {
  margin-top: 2px;
  color: #7a7d84;
  font-size: 13px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  width: 100%;
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #2f3137;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  font-size: 15px;
  cursor: pointer;
}

.nav-item:hover {
  background: #e8eaef;
}

.nav-item.active {
  background: #ecfdf5;
  color: #0f766e;
}

.sidebar-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.section-title {
  padding: 6px 10px;
  color: #7a7d84;
  font-size: 13px;
  font-weight: 600;
}

.session-list {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-item {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
}

.session-item:hover {
  background: #e8eaef;
}

.session-item.active {
  background: #e5ebff;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.session-title {
  color: #2f3137;
  font-size: 13px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
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
  padding-top: 8px;
  border-top: 1px solid #e6e7ea;
}

.portal-main {
  min-width: 0;
  height: 100vh;
  display: grid;
  grid-template-rows: 62px minmax(0, 1fr);
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-bottom: 1px solid #ececef;
  background: rgba(255, 255, 255, 0.86);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-left strong {
  font-size: 20px;
  color: #1f2329;
  font-weight: 600;
}

.user-pill {
  border: 1px solid #e3e5e9;
  background: #fff;
  border-radius: 999px;
  padding: 2px 4px 2px 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-pill span {
  color: #5c606a;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
}

.chat-stage {
  overflow-y: auto;
  padding: 16px 24px 24px;
}

.chat-stage.home {
  display: flex;
  align-items: center;
  justify-content: center;
}

.home-center {
  width: min(860px, 100%);
  text-align: center;
}

.home-center h1 {
  margin: 6px 0 18px;
  color: #242628;
  font-size: clamp(24px, 3vw, 36px);
  line-height: 1.16;
  font-weight: 600;
}

.home-kicker {
  margin: 0;
  color: #575b63;
  font-size: 15px;
  font-weight: 700;
}

.input-card {
  border: 1px solid #e7e8eb;
  border-radius: 12px;
  background: #fff;
  padding: 14px 16px 10px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.05);
  text-align: left;
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
  color: #1f2329;
  font-size: 16px;
  line-height: 1.34;
  padding: 2px 4px 8px;
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
  background: #f2f3f5;
  color: #41444c;
  padding: 0 14px;
  font-size: 13px;
  cursor: pointer;
}

.chip-btn:hover {
  background: #eaebef;
}

.chip-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.send-button {
  width: 40px;
  height: 40px;
  border: 0;
  background: #0f766e;
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
  background: #f2f3f5;
  color: #3f434b;
  font-size: 13px;
  cursor: pointer;
}

.suggestion-chip:hover {
  background: #e9ebef;
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
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #eceef2;
  color: #4b4f57;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.message-bubble {
  max-width: min(820px, 82%);
  border: 1px solid #e8e9ed;
  border-radius: 10px;
  background: #fff;
  padding: 12px 14px;
}

.message-row.user .message-bubble {
  border-color: transparent;
  color: #fff;
  background: #0f766e;
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
  right: calc(var(--context-width) + 20px);
  bottom: 16px;
}

.portal-context {
  height: 100vh;
  padding: 14px;
  border-left: 1px solid #e1e6ec;
  background: rgba(255, 255, 255, 0.78);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.context-card {
  padding: 14px;
  border: 1px solid #e1e6ec;
  border-radius: 8px;
  background: #fff;
}

.context-eyebrow {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.context-card strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.context-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.context-list {
  display: grid;
  gap: 8px;
  color: #475569;
  font-size: 13px;
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

.kb-popover {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.popover-title {
  font-weight: 600;
  color: #2b2f37;
}

.kb-item {
  border: 1px solid #e9ebef;
  border-radius: 10px;
  background: #fff;
  min-height: 50px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.kb-item strong,
.kb-item span {
  display: block;
}

.kb-item span {
  color: #8b9099;
  font-size: 12px;
}

.service-portal.collapsed .brand-copy,
.service-portal.collapsed .nav-item span,
.service-portal.collapsed .section-title,
.service-portal.collapsed .session-title,
.service-portal.collapsed .session-time,
.service-portal.collapsed .sidebar-bottom {
  display: none;
}

.service-portal.collapsed .session-item,
.service-portal.collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

@media (max-width: 1080px) {
  .service-portal {
    --sidebar-width: 240px;
    grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  }

  .portal-context {
    display: none;
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
