<template>
  <div class="chat-playground">
    <!-- Chat Header: Model Info & Actions -->
    <div class="chat-header">
      <div class="header-left">
        <span class="model-name">
          <el-icon class="model-icon"><Cpu /></el-icon>
          {{ agentInfo.agentName || agentInfo.modelName || 'qwen3-vl:4b' }}
        </span>
      </div>
      <div class="header-right">
        <el-popover placement="bottom-end" width="350" trigger="click">
          <template #reference>
            <el-button link class="logs-btn"><el-icon><Tickets /></el-icon><span style="margin-left: 4px; font-size: 12px;">运行时日志</span></el-button>
          </template>
          <div class="log-stream-popover">
            <div class="popover-header">
              <span class="title">运行时日志</span>
              <el-button link size="small" :icon="Refresh" @click="$emit('refresh-logs')" />
            </div>
            <div class="log-content">
              <template v-for="(log, index) in logs" :key="index">
                <div class="log-entry" :class="log.type">
                  <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                  <span class="log-tag">[{{ log.type }}]</span>
                  <span class="log-msg"><span class="prefix req">REQ:</span> {{ log.content }}</span>
                </div>
                <div v-if="log.response" class="log-entry resp" :class="log.type">
                  <span class="log-msg"><span class="prefix res">RES:</span> {{ log.response }}</span>
                </div>
              </template>
              <div v-if="(!logs || logs.length === 0)" class="empty-logs">暂无运行日志</div>
            </div>
          </div>
        </el-popover>

        <el-button link class="more-btn"><el-icon><MoreFilled /></el-icon></el-button>
        <el-button link type="danger" class="clear-btn" @click="clearHistory">清空对话</el-button>
      </div>
    </div>

    <!-- Message List Section -->
    <div class="messages-container" ref="messagesContainer">
      <div v-if="chatMessages.length === 0" class="empty-stage">
        <el-empty :image-size="200" description=" ">
          <template #image>
            <div class="orin-watermark">
              <img src="/logo.svg" alt="ORIN" class="watermark-logo" />
              <div class="watermark-text">ORIN Playground</div>
            </div>
          </template>
          <div class="empty-hint">在下方输入指令，开始与智能体交互</div>
        </el-empty>
      </div>

      <div v-else v-for="(msg, i) in chatMessages" :key="i" class="message-card-wrapper">
        <div :class="['message-card', msg.role]">
          <div class="card-header">
            <div class="header-info">
              <span class="status-dot"></span>
              <span class="role-text">{{ getRoleLabel(msg.role) }}</span>
              <el-tag v-if="msg.dataType === 'DIAGNOSTIC_REPORT'" size="small" effect="plain" class="source-badge">
                <el-icon><Cpu /></el-icon> ZeroClaw
              </el-tag>
            </div>
            <div class="header-actions">
              <el-button link :icon="Delete" size="small" class="delete-msg-btn" @click="removeMessage(i)" />
            </div>
          </div>
          
          <div class="card-content">
            <!-- Thinking Animation/Content -->
            <div v-if="msg.thinking && parameters.enableThinking" class="thinking-module">
              <div class="thinking-toggle" @click="msg.showThinking = !msg.showThinking">
                <el-icon :class="{ 'is-active': msg.showThinking }"><ArrowRight /></el-icon>
                <span>思考过程</span>
              </div>
              <el-collapse-transition>
                <div v-show="msg.showThinking" class="thinking-inner">{{ msg.thinking }}</div>
              </el-collapse-transition>
            </div>

            <div v-if="msg.dataType === 'DIAGNOSTIC_REPORT'" class="diagnostic-container">
              <div class="diagnostic-header" :class="msg.content.severity?.toLowerCase()">
                <el-icon><Cpu /></el-icon>
                <span>{{ msg.content.title || '系统诊断报告' }}</span>
                <el-tag :type="getSeverityTag(msg.content.severity)" size="small" effect="dark">{{ msg.content.severity }}</el-tag>
              </div>
              <div class="diagnostic-body">
                <div class="diag-section">
                  <div class="diag-label">分析摘要</div>
                  <div class="diag-value markdown-body" v-html="renderMarkdown(msg.content.summary)"></div>
                </div>
                <div class="diag-section" v-if="msg.content.rootCause">
                  <div class="diag-label">根本原因 (Root Cause)</div>
                  <div class="diag-value error-text">{{ msg.content.rootCause }}</div>
                </div>
                <div class="diag-section highlights" v-if="msg.content.recommendations">
                  <div class="diag-label">修复建议</div>
                  <div class="diag-value success-text markdown-body" v-html="renderMarkdown(msg.content.recommendations)"></div>
                </div>
              </div>
              <div class="diagnostic-footer">
                <el-button type="primary" size="small" plain @click="executeQuickFix(msg.content)">执行自动修复 (Self-Healing)</el-button>
              </div>
            </div>

            <div v-else-if="msg.dataType === 'ZEROCLAW_STATUS'" class="status-monitor-container">
              <div class="monitor-header">
                <el-icon><Monitor /></el-icon>
                <span>ZeroClaw 运行状态自检</span>
              </div>
              <div class="monitor-grid">
                <div class="monitor-item">
                  <div class="m-label">连通性 (Connectivity)</div>
                  <div class="m-value">
                    <el-tag :type="msg.content.connected ? 'success' : 'danger'" size="small">
                      {{ msg.content.connected ? '在线 (ONLINE)' : '离线 (OFFLINE)' }}
                    </el-tag>
                  </div>
                </div>
                <div class="monitor-item">
                  <div class="m-label">引擎配置</div>
                  <div class="m-value">{{ msg.content.configName || '未配置' }}</div>
                </div>
                <div class="monitor-item">
                  <div class="m-label">分析模块</div>
                  <div class="m-value">
                    <el-icon :class="msg.content.analysisEnabled ? 'text-success' : 'text-danger'">
                      <CircleCheck v-if="msg.content.analysisEnabled" /><Close v-else />
                    </el-icon>
                  </div>
                </div>
                <div class="monitor-item">
                  <div class="m-label">自愈模块</div>
                  <div class="m-value">
                    <el-icon :class="msg.content.selfHealingEnabled ? 'text-success' : 'text-danger'">
                      <CircleCheck v-if="msg.content.selfHealingEnabled" /><Close v-else />
                    </el-icon>
                  </div>
                </div>
              </div>
              <div class="monitor-footer" v-if="!msg.content.connected">
                <div class="warning-text">警告: 无法连接到 ZeroClaw 服务端 ({{ msg.content.message }})</div>
                <el-button type="warning" size="small" @click="retryZeroClaw">重试连接</el-button>
              </div>
            </div>

            <div v-else-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
            <div v-else class="user-content">{{ msg.content }}</div>
          </div>
        </div>
      </div>

      <!-- Loading State -->
      <div v-if="chatLoading" class="message-card-wrapper">
        <div class="message-card assistant loading">
          <div class="card-header">
            <div class="header-info">
              <span class="status-dot pulse"></span>
              <span class="role-text">Assistant</span>
            </div>
          </div>
          <div class="card-content">
            <div class="typing-loader">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Bottom Input Area: Floating Card -->
    <div class="input-framer">
      <div class="input-card">
        <el-input
          v-model="chatInput"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 10 }"
          placeholder="给智能体发送指令..."
          class="chat-textarea"
          @keydown.enter="handleKeyEnter"
          resize="none"
        />
        <div class="input-footer">
          <div class="footer-left">
            <el-button link class="tool-btn"><el-icon><Operation /></el-icon></el-button>
            <el-button link class="tool-btn"><el-icon><Search /></el-icon></el-button>
            <el-divider direction="vertical" />
            <el-button link class="tool-btn"><el-icon><Paperclip /></el-icon></el-button>
          </div>
          <div class="footer-right">
            <span class="keyboard-hint">按 <b>Enter</b> 发送，<b>Shift + Enter</b> 换行</span>
            <el-button 
              type="primary" 
              circle
              class="send-circle-btn"
              @click="sendMessage"
              :loading="chatLoading"
              :icon="Position"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';
import { 
  Position, Delete, MoreFilled, ArrowRight, 
  Operation, Search, Paperclip, Setting,
  Tickets, Refresh, Cpu
} from '@element-plus/icons-vue';
import { chatAgent } from '@/api/agent';
import { getAgentMetrics } from '@/api/monitor';
import { marked } from 'marked';
import { ElMessage } from 'element-plus';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) },
  logs: { type: Array, default: () => [] }
});

const formatTime = (ts) => {
  if (!ts) return '-';
  if (Array.isArray(ts)) {
    return new Date(ts[0], ts[1] - 1, ts[2], ts[3] || 0, ts[4] || 0, ts[5] || 0).toLocaleTimeString();
  }
  const dateStr = String(ts).replace(' ', 'T');
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '-';
  return date.toLocaleTimeString();
};

// Chat States
const chatMessages = ref([
  { role: 'assistant', content: '您好！我是您的 AI 助手，有什么可以帮您的吗？', showThinking: false }
]);
const chatInput = ref('');
const chatLoading = ref(false);
const messagesContainer = ref(null);
const metrics = ref({ latency: [], tokens: [] });

let pollTimer = null;

const renderMarkdown = (text) => {
  try {
    return marked.parse(text || '');
  } catch (e) {
    return text;
  }
};

const getRoleLabel = (role) => ({
  'system': 'System',
  'user': 'User',
  'assistant': 'Assistant'
}[role] || 'Unknown');

const latestTokenCount = computed(() => {
  if (metrics.value.tokens.length > 0) return metrics.value.tokens[metrics.value.tokens.length - 1].value;
  return 0;
});
const latestLatency = computed(() => {
  if (metrics.value.latency.length > 0) return Math.round(metrics.value.latency[metrics.value.latency.length - 1].value);
  return 0;
});

const handleKeyEnter = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return;
  const msg = chatInput.value;
  chatMessages.value.push({ role: 'user', content: msg });
  chatInput.value = '';
  chatLoading.value = true;
  
  nextTick(scrollToBottom);

  try {
    const res = await chatAgent(
      props.agentId, 
      msg, 
      null, 
      null, 
      null, 
      props.parameters.enableThinking, 
      props.parameters.thinkingBudget
    );
    
    let data = res;
    if (res && res.status) {
        if (res.status === 'SUCCESS' && res.data) data = res.data;
        else if (res.status === 'PROCESSING') data = { answer: "任务处理中..." };
        else if (res.data) data = res.data;
    }
    
    const resMsg = data.choices?.[0]?.message || {};
    const answer = resMsg.content || data.answer || (typeof data === 'string' ? data : JSON.stringify(data));
    const thinking = resMsg.thinking || data.thinking;
    
    chatMessages.value.push({ 
      role: 'assistant', 
      content: res.dataType === 'DIAGNOSTIC_REPORT' ? res.data : answer, 
      thinking: thinking, 
      showThinking: false,
      dataType: res.dataType
    });
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '（错误: ' + e.message + '）' });
  } finally {
    chatLoading.value = false;
    scrollToBottom();
    fetchMetrics();
  }
};

const removeMessage = (index) => {
  chatMessages.value.splice(index, 1);
};

const getSeverityTag = (s) => ({
  'CRITICAL': 'danger',
  'HIGH': 'danger',
  'WARNING': 'warning',
  'INFO': 'info'
}[s?.toUpperCase()] || 'info');

const executeQuickFix = (report) => {
  ElMessage.success('已触发自愈引擎：' + (report.title || '系统修复'));
  // Logic to call self-healing API could go here
};

const retryZeroClaw = () => {
  ElMessage.info('正在尝试重新发现 ZeroClaw 节点...');
  sendMessage(); // Re-triggering with current input could work if logic supports it, or just status check
};

const clearHistory = () => {
  chatMessages.value = [{ role: 'assistant', content: '上下文已清空。', showThinking: false }];
};

const fetchMetrics = async () => {
  const end = Date.now();
  const start = end - 60 * 60 * 1000;
  try {
    const res = await getAgentMetrics(props.agentId, start, end);
    const data = res || [];
    metrics.value = {
      latency: data.map(d => ({ timestamp: d.timestamp, value: d.responseLatency || 0 })),
      tokens: data.map(d => ({ timestamp: d.timestamp, value: d.tokenCost || 0 }))
    };
  } catch (e) { console.warn('Metrics error'); }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

onMounted(() => {
    fetchMetrics();
    pollTimer = setInterval(fetchMetrics, 30000);
});

onUnmounted(() => {
    if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.log-stream-popover {
  display: flex;
  flex-direction: column;
  height: 400px;
}
.popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
  margin-bottom: 8px;
}
.log-content {
  flex: 1;
  overflow-y: auto;
  font-family: 'Fira Code', monospace;
  font-size: 11px;
}
.log-entry { margin-bottom: 8px; line-height: 1.4; }
.log-entry.INFO { color: #52c41a; }
.log-entry.ERROR { color: #f5222d; }
.log-tag { font-weight: 700; margin-right: 4px; }
.prefix { font-weight: 700; margin-right: 4px; border-radius: 2px; padding: 0 2px; }
.prefix.req { background: #e6f7ff; color: #1890ff; }
.prefix.res { background: #f6ffed; color: #52c41a; }
.empty-logs { text-align: center; color: #999; margin-top: 20px; }

.chat-playground {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f9fafb;
  font-family: 'Inter', -apple-system, sans-serif;
  position: relative;
}

/* Chat Window Header */
.chat-header {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #f0f0f0;
  z-index: 50;
}
.model-name {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.model-icon { color: #0d9488; }
.clear-btn { font-size: 12px; font-weight: 600; }
.more-btn { color: #9ca3af; font-size: 18px; }

/* Empty Stage: Watermark Feel */
.empty-stage {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: -60px; /* Offset for better balance */
}
.orin-watermark {
  display: flex;
  flex-direction: column;
  align-items: center;
  opacity: 0.15;
  filter: grayscale(1);
}
.watermark-logo { width: 80px; margin-bottom: 12px; }
.watermark-text {
  font-size: 24px;
  font-weight: 900;
  letter-spacing: 4px;
  text-transform: uppercase;
  color: #000;
}
.empty-hint {
  margin-top: 24px;
  color: #9ca3af;
  font-size: 14px;
}

/* Messages Area */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0 180px 0;
  scroll-behavior: smooth;
}

.message-card-wrapper {
  max-width: 860px;
  margin: 0 auto 24px auto;
  padding: 0 20px;
}

.message-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}
.message-card:hover { 
  border-color: #d1d5db; 
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

/* Message Header */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f3f4f6;
  border-radius: 12px 12px 0 0;
}
.header-info { display: flex; align-items: center; gap: 8px; }
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fbbf24;
}
.user .status-dot { background: #3b82f6; }
.status-dot.pulse { animation: status-pulse 1.5s infinite; }

@keyframes status-pulse {
  0% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
  100% { transform: scale(0.9); opacity: 0.7; }
}

.role-text {
  font-size: 11px;
  font-weight: 700;
  color: #4b5563;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.source-badge {
  margin-left: 8px;
  background: #f0fdfa !important;
  color: #0d9488 !important;
  border-color: #99f6e4 !important;
  font-weight: 700;
  font-size: 10px;
}

/* Content */
.card-content { padding: 16px 20px; }
.user-content {
  font-size: 15px;
  line-height: 1.6;
  color: #1f2937;
  white-space: pre-wrap;
}

/* Thinking Style */
.thinking-module {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 16px;
}
.thinking-toggle {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
  cursor: pointer;
}
.thinking-toggle .el-icon { transition: transform 0.2s; font-size: 10px; }
.thinking-toggle .el-icon.is-active { transform: rotate(90deg); }
.thinking-inner {
  padding: 14px;
  font-size: 13px;
  color: #64748b;
  border-top: 1px solid #e2e8f0;
  white-space: pre-wrap;
  line-height: 1.6;
}

/* Input Console: Floating Card */
.input-framer {
  position: absolute;
  bottom: 24px;
  left: 0;
  right: 0;
  padding: 0 20px;
  z-index: 100;
}
.input-card {
  max-width: 860px;
  margin: 0 auto;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 16px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: border-color 0.2s;
}
.input-card:focus-within { border-color: #0d9488; }

.chat-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 16px 20px;
  font-size: 15px;
  background: transparent;
  color: #111827;
  line-height: 1.6;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 12px 16px;
  background: #fff;
}
.footer-left, .footer-right { display: flex; align-items: center; gap: 8px; }
.tool-btn { color: #9ca3af; font-size: 18px; padding: 6px; }
.tool-btn:hover { color: #4b5563; background: #f3f4f6; border-radius: 8px; }

.keyboard-hint {
  font-size: 11px;
  color: #9ca3af;
  margin-right: 12px;
}
.keyboard-hint b { color: #6b7280; font-weight: 700; margin: 0 2px; }

.send-circle-btn {
  width: 40px;
  height: 40px;
  background: #0d9488 !important;
  border: none !important;
  box-shadow: 0 4px 10px rgba(13, 148, 136, 0.3);
  transition: transform 0.2s, background 0.2s;
}
.send-circle-btn:hover { transform: scale(1.05); background: #0f766e !important; }

/* Typing Animation */
.typing-loader { display: flex; gap: 5px; padding: 4px 0; }
.typing-loader span {
  width: 7px; height: 7px; background: #cbd5e1; border-radius: 50%;
  animation: typing 1s infinite ease-in-out;
}
.typing-loader span:nth-child(2) { animation-delay: 0.2s; }
.typing-loader span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0% { transform: translateY(0px); opacity: 0.5; }
  50% { transform: translateY(-5px); opacity: 1; }
  100% { transform: translateY(0px); opacity: 0.5; }
}

/* Markdown UI */
.markdown-body { font-size: 15px; line-height: 1.7; color: #1f2937; }
.markdown-body :deep(p) { margin-bottom: 1.25em; }
.markdown-body :deep(pre) { 
  background: #f1f5f9 !important; 
  padding: 16px; 
  border-radius: 10px; 
  border: 1px solid #e2e8f0;
  margin: 1.5em 0;
}
.markdown-body :deep(code) { 
  font-family: 'Fira Code', monospace; 
  font-size: 0.9em;
  background: #f1f5f9;
  padding: 2px 4px;
  border-radius: 4px;
}
/* Diagnostic Report Styles */
.diagnostic-container {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}
.diagnostic-header {
  padding: 10px 16px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  border-bottom: 1px solid #e2e8f0;
}
.diagnostic-header.critical { background: #fef2f2; color: #991b1b; }
.diagnostic-header.warning { background: #fffbeb; color: #92400e; }
.diagnostic-body { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.diag-section { display: flex; flex-direction: column; gap: 8px; }
.diag-label { font-size: 11px; font-weight: 800; text-transform: uppercase; color: #64748b; letter-spacing: 0.05em; }
.diag-value { font-size: 14px; line-height: 1.6; color: #1e293b; }
.diag-section.highlights { background: #f0fdf4; padding: 12px; border-radius: 6px; border-left: 4px solid #22c55e; }
.error-text { color: #dc2626; font-weight: 600; }
.success-text { color: #166534; }
.diagnostic-footer { padding: 12px 16px; background: #f8fafc; border-top: 1px solid #e2e8f0; display: flex; justify-content: flex-end; }

/* Status Monitor Styles */
.status-monitor-container {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}
.monitor-header {
  padding: 12px 16px;
  background: #111827;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  font-size: 14px;
}
.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: #f1f5f9;
}
.monitor-item {
  background: #fff;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.m-label { font-size: 11px; color: #64748b; font-weight: 600; text-transform: uppercase; }
.m-value { font-size: 15px; font-weight: 700; color: #1e293b; display: flex; align-items: center; }
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }
.monitor-footer {
  padding: 12px 16px;
  background: #fef2f2;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.warning-text { font-size: 12px; color: #991b1b; font-weight: 600; }
</style>
