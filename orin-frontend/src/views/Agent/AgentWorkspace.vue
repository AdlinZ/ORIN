<template>
  <div class="agent-workspace">
    <!-- 左侧：智能体选择 -->
    <div class="workspace-sidebar">
      <div class="sidebar-header">
        <h3>智能体</h3>
        <el-button link :icon="Refresh" @click="loadAgents" />
      </div>
      
      <div class="agent-search">
        <el-input
          v-model="agentSearch"
          placeholder="搜索智能体..."
          :prefix-icon="Search"
          clearable
        />
      </div>

      <div class="agent-list">
        <div
          v-for="agent in filteredAgents"
          :key="agent.id"
          :class="['agent-item', { active: currentAgent?.id === agent.id }]"
          @click="selectAgent(agent)"
        >
          <div class="agent-avatar" :style="{ background: getAgentColor(agent.name) }">
            {{ agent.name?.charAt(0) || 'A' }}
          </div>
          <div class="agent-info">
            <div class="agent-name">{{ agent.name }}</div>
            <div class="agent-desc">{{ agent.description || '暂无描述' }}</div>
          </div>
        </div>
        
        <el-empty v-if="filteredAgents.length === 0" :image-size="60" description="暂无智能体" />
      </div>
    </div>

    <!-- 中间：对话区域 -->
    <div class="workspace-main">
      <div v-if="!currentAgent" class="no-agent-selected">
        <el-empty description="请先选择一个智能体开始交互" />
      </div>

      <template v-else>
        <!-- 会话头部 -->
        <div class="chat-header">
          <div class="current-agent">
            <div class="agent-avatar-lg" :style="{ background: getAgentColor(currentAgent.name) }">
              {{ currentAgent.name?.charAt(0) }}
            </div>
            <div class="agent-details">
              <h4>{{ currentAgent.name }}</h4>
              <span class="model-info">{{ currentAgent.model || '默认模型' }}</span>
            </div>
          </div>
          
          <div class="header-actions">
            <el-button :icon="Plus" @click="newSession">新建会话</el-button>
            <el-select v-model="currentSessionId" placeholder="选择会话" style="width: 200px;" @change="loadSession">
              <el-option v-for="s in sessions" :key="s.id" :label="s.title" :value="s.id" />
            </el-select>
            <el-button link :icon="Delete" @click="clearCurrentSession">清空</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesContainer" class="messages-container">
          <div v-if="messages.length === 0" class="empty-chat">
            <el-icon size="64"><ChatLineRound /></el-icon>
            <p>与 {{ currentAgent.name }} 开始对话</p>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-item', msg.role]"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><Robot /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-role">{{ msg.role === 'user' ? '你' : currentAgent.name }}</div>
              <div class="message-text" v-html="renderMarkdown(msg.content)" />
              <div v-if="msg.retrievedChunks && msg.retrievedChunks.length > 0" class="retrieved-context">
                <div class="context-header">
                  <el-icon><Document /></el-icon>
                  <span>检索到的知识 ({{ msg.retrievedChunks.length }})</span>
                </div>
                <div class="context-list">
                  <div v-for="(chunk, i) in msg.retrievedChunks.slice(0, 3)" :key="i" class="context-item">
                    <div class="chunk-source">{{ chunk.source || '未知来源' }}</div>
                    <div class="chunk-text">{{ chunk.content?.substring(0, 150) }}...</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="loading" class="loading-indicator">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>思考中...</span>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            @keydown.enter="handleEnter"
            :disabled="loading"
          />
          <el-button type="primary" :loading="loading" @click="sendMessage">
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </template>
    </div>

    <!-- 右侧：知识库 -->
    <div class="workspace-knowledge">
      <div class="knowledge-header">
        <h3>知识库</h3>
        <el-tooltip content="附加知识库后，智能体将参考知识库内容进行回答">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>

      <div class="kb-search">
        <el-input
          v-model="kbSearch"
          placeholder="搜索知识库..."
          :prefix-icon="Search"
          clearable
        />
      </div>

      <div class="kb-list">
        <div
          v-for="kb in filteredKnowledgeBases"
          :key="kb.id"
          :class="['kb-item', { attached: isKbAttached(kb.id) }]"
        >
          <el-checkbox
            :model-value="isKbAttached(kb.id)"
            @change="toggleKb(kb.id)"
          >
            <div class="kb-info">
              <div class="kb-name">{{ kb.name }}</div>
              <div class="kb-meta">{{ kb.documentCount || 0 }} 文档</div>
            </div>
          </el-checkbox>
        </div>

        <el-empty v-if="filteredKnowledgeBases.length === 0" :image-size="60" description="暂无知识库" />
      </div>

      <div v-if="attachedKbs.length > 0" class="attached-kbs">
        <div class="attached-header">已附加 ({{ attachedKbs.length }})</div>
        <el-tag
          v-for="kb in attachedKbs"
          :key="kb.id"
          closable
          @close="detachKb(kb.id)"
          class="kb-tag"
        >
          {{ kb.name }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { 
  Search, Refresh, Plus, Delete, ChatLineRound, 
  User, Robot, Document, Promotion, Loading, QuestionFilled 
} from '@element-plus/icons-vue';
import { listAgents, listKnowledgeBases, createChatSession, 
         sendChatMessage, getAttachedKnowledgeBases, 
         attachKnowledgeBase, detachKnowledgeBase,
         listChatSessions, getChatSession } from '@/api/agent-chat';

// 状态
const agentSearch = ref('');
const kbSearch = ref('');
const agents = ref([]);
const knowledgeBases = ref([]);
const currentAgent = ref(null);
const currentSessionId = ref(null);
const sessions = ref([]);
const messages = ref([]);
const inputMessage = ref('');
const loading = ref(false);
const attachedKbs = ref([]);
const messagesContainer = ref(null);

// 过滤
const filteredAgents = computed(() => {
  if (!agentSearch.value) return agents.value;
  return agents.value.filter(a => 
    a.name?.toLowerCase().includes(agentSearch.value.toLowerCase())
  );
});

const filteredKnowledgeBases = computed(() => {
  if (!kbSearch.value) return knowledgeBases.value;
  return knowledgeBases.value.filter(kb => 
    kb.name?.toLowerCase().includes(kbSearch.value.toLowerCase())
  );
});

// 方法
const getAgentColor = (name) => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#722ED1'];
  const index = name ? name.charCodeAt(0) % colors.length : 0;
  return colors[index];
};

const isKbAttached = (kbId) => {
  return attachedKbs.value.some(kb => kb.id === kbId);
};

const loadAgents = async () => {
  try {
    const res = await listAgents({ page: 1, size: 100 });
    agents.value = res.data?.records || res.data || [];
  } catch (e) {
    ElMessage.error('加载智能体失败');
  }
};

const loadKnowledgeBases = async () => {
  try {
    const res = await listKnowledgeBases({ page: 1, size: 100 });
    knowledgeBases.value = res.data?.records || res.data || [];
  } catch (e) {
    ElMessage.error('加载知识库失败');
  }
};

const selectAgent = async (agent) => {
  currentAgent.value = agent;
  // 创建新会话
  try {
    const res = await createChatSession({ 
      agentId: agent.id,
      title: '新会话'
    });
    currentSessionId.value = res.data?.id || res.id;
    sessions.value = [{ id: currentSessionId.value, title: '新会话' }];
    messages.value = [];
    // 加载已附加的知识库
    if (currentSessionId.value) {
      loadAttachedKbs();
    }
  } catch (e) {
    ElMessage.error('创建会话失败');
  }
};

const newSession = async () => {
  if (!currentAgent.value) return;
  await selectAgent(currentAgent.value);
};

const loadSession = async (sessionId) => {
  if (!sessionId) return;
  try {
    const res = await getChatSession(sessionId);
    messages.value = res.data?.messages || [];
    loadAttachedKbs();
  } catch (e) {
    ElMessage.error('加载会话失败');
  }
};

const loadAttachedKbs = async () => {
  if (!currentSessionId.value) return;
  try {
    const res = await getAttachedKnowledgeBases(currentSessionId.value);
    attachedKbs.value = res.data || [];
  } catch (e) {
    // 忽略
  }
};

const toggleKb = async (kbId) => {
  if (isKbAttached(kbId)) {
    await detachKb(kbId);
  } else {
    await attachKb(kbId);
  }
};

const attachKb = async (kbId) => {
  if (!currentSessionId.value) return;
  try {
    await attachKnowledgeBase(currentSessionId.value, kbId);
    const kb = knowledgeBases.value.find(k => k.id === kbId);
    if (kb) attachedKbs.value.push(kb);
    ElMessage.success('已附加知识库');
  } catch (e) {
    ElMessage.error('附加知识库失败');
  }
};

const detachKb = async (kbId) => {
  if (!currentSessionId.value) return;
  try {
    await detachKnowledgeBase(currentSessionId.value, kbId);
    attachedKbs.value = attachedKbs.value.filter(kb => kb.id !== kbId);
    ElMessage.success('已解绑知识库');
  } catch (e) {
    ElMessage.error('解绑知识库失败');
  }
};

const sendMessage = async () => {
  if (!inputMessage.value.trim() || !currentSessionId.value || loading.value) return;
  
  const userMsg = { role: 'user', content: inputMessage.value };
  messages.value.push(userMsg);
  
  const msgText = inputMessage.value;
  inputMessage.value = '';
  loading.value = true;

  try {
    const res = await sendChatMessage(currentSessionId.value, {
      message: msgText,
      kbIds: attachedKbs.value.map(kb => kb.id)
    });
    
    const aiMsg = { 
      role: 'assistant', 
      content: res.data?.content || res.content || '',
      retrievedChunks: res.data?.retrievedChunks || []
    };
    messages.value.push(aiMsg);
  } catch (e) {
    ElMessage.error('发送消息失败');
    messages.value.pop();
  } finally {
    loading.value = false;
    scrollToBottom();
  }
};

const handleEnter = (e) => {
  if (!e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

const clearCurrentSession = () => {
  messages.value = [];
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const renderMarkdown = (text) => {
  if (!text) return '';
  // 简单渲染
  return text
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>');
};

// 初始化
onMounted(() => {
  loadAgents();
  loadKnowledgeBases();
});
</script>

<style scoped>
.agent-workspace {
  display: flex;
  height: calc(100vh - 60px);
  background: #f5f7fa;
}

/* 左侧 */
.workspace-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.agent-search {
  padding: 12px 16px;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.agent-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.agent-item:hover {
  background: #f5f7fa;
}

.agent-item.active {
  background: #ecf5ff;
  border: 1px solid #409eff;
}

.agent-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  margin-right: 12px;
}

.agent-info {
  flex: 1;
  overflow: hidden;
}

.agent-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-desc {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 中间 */
.workspace-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.no-agent-selected {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.current-agent {
  display: flex;
  align-items: center;
}

.agent-avatar-lg {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  margin-right: 12px;
}

.agent-details h4 {
  margin: 0 0 4px;
}

.model-info {
  font-size: 12px;
  color: #909399;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-chat p {
  margin-top: 16px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 12px;
}

.message-item.assistant .message-avatar {
  background: #409eff;
  color: #fff;
}

.message-content {
  max: 70%;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-text {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
}

.message-item.user .message-text {
  background: #ecf5ff;
}

.retrieved-context {
  margin-top: 12px;
  padding: 12px;
  background: #fdf6ec;
  border-radius: 8px;
  border: 1px solid #faecd8;
}

.context-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #e6a23c;
  margin-bottom: 8px;
}

.chunk-source {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.chunk-text {
  font-size: 12px;
  color: #666;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  padding: 12px;
}

.input-area {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #e4e7ed;
}

.input-area .el-input {
  flex: 1;
}

/* 右侧 */
.workspace-knowledge {
  width: 300px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.knowledge-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.kb-search {
  padding: 12px 16px;
}

.kb-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}

.kb-item {
  padding: 12px;
  border-radius: 8px;
  transition: all 0.2s;
}

.kb-item:hover {
  background: #f5f7fa;
}

.kb-item.attached {
  background: #f0f9eb;
}

.kb-info {
  margin-left: 8px;
}

.kb-name {
  font-weight: 500;
}

.kb-meta {
  font-size: 12px;
  color: #909399;
}

.attached-kbs {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
}

.attached-header {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.kb-tag {
  margin: 0 8px 8px 0;
}
</style>