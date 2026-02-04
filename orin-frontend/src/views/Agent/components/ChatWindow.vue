<template>
  <div class="chat-window">
      <!-- Monitor Bar -->
      <div class="monitor-bar">
        <div class="monitor-left">
          <el-tag size="small" :type="getStatusType(agentInfo.status)" effect="dark" class="status-tag">
            {{ agentInfo.status === 'RUNNING' ? 'ACTIVE' : 'IDLE' }}
          </el-tag>
          <el-tag size="small" :type="getHealthStatusType(agentInfo.healthScore)" effect="plain" class="monitor-tag">
            健康度: {{ agentInfo.healthScore || 100 }}
          </el-tag>
        </div>
        <div class="monitor-right">
          <el-tag size="small" type="info" effect="plain" class="monitor-tag">
            Tokens: {{ latestTokenCount }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain" class="monitor-tag">
            {{ latestLatency }}ms
          </el-tag>
        </div>
      </div>

      <!-- Messages -->
      <div class="chat-history" ref="messagesContainer">
        <div v-for="(msg, i) in chatMessages" :key="i" class="chat-bubble" :class="msg.role">
          <div class="bubble-content">
            <div v-if="msg.role === 'assistant'" class="bot-info">
              <el-avatar :size="18" icon="UserFilled" />
              <span class="name">{{ agentInfo.agentName }}</span>
            </div>
            <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
        </div>
        <div v-if="chatLoading" class="chat-bubble assistant">
           <div class="bubble-content typing">...</div>
        </div>
      </div>

      <!-- Input -->
      <div class="chat-input-area">
        <el-input 
          v-model="chatInput" 
          placeholder="输入指令..." 
          @keyup.enter.exact="sendMessage"
          :disabled="chatLoading"
          size="default"
        >
          <template #suffix>
            <el-icon class="icon-send" @click="sendMessage"><Position /></el-icon>
          </template>
        </el-input>
      </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';
import { Position, UserFilled } from '@element-plus/icons-vue';
import { chatAgent } from '@/api/agent';
import { getAgentMetrics } from '@/api/monitor';
import { marked } from 'marked';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) }
});

// Chat States
const chatMessages = ref([{ role: 'assistant', content: '你好，我是 ' + (props.agentInfo.agentName || 'AI') + '。' }]);
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

const latestTokenCount = computed(() => {
  if (metrics.value.tokens.length > 0) return metrics.value.tokens[metrics.value.tokens.length - 1].value;
  return 0;
});
const latestLatency = computed(() => {
  if (metrics.value.latency.length > 0) return Math.round(metrics.value.latency[metrics.value.latency.length - 1].value);
  return 0;
});

const getStatusType = (s) => ({ 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' }[s] || 'danger');
const getHealthStatusType = (s) => (s || 100) >= 90 ? 'success' : (s >= 60 ? 'warning' : 'danger');

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

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return;
  const msg = chatInput.value;
  chatMessages.value.push({ role: 'user', content: msg });
  chatInput.value = '';
  chatLoading.value = true;
  
  try {
    const res = await chatAgent(props.agentId, msg);
    // Unwrap if wrapped response
    let data = res;
    if (res && res.status) {
        if (res.status === 'SUCCESS' && res.data) data = res.data;
        else if (res.status === 'PROCESSING') data = { answer: "Processing... (Job ID: " + res.jobId + ")" };
        else if (res.data) data = res.data;
    }
    
    const answer = data.answer || data.choices?.[0]?.message?.content || (typeof data === 'string' ? data : JSON.stringify(data));
    chatMessages.value.push({ role: 'assistant', content: answer });
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '（异常: ' + e.message + '）' });
  } finally {
    chatLoading.value = false;
    scrollToBottom();
  }
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
    pollTimer = setInterval(fetchMetrics, 5000);
});

onUnmounted(() => {
    if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.chat-window {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: white;
}

.monitor-bar {
  padding: 6px 12px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  background: white;
}

.monitor-left, .monitor-right { display: flex; gap: 4px; }
.status-tag { border-radius: 3px; scale: 0.9; transform-origin: left; }
.monitor-tag { border: none !important; background: #f5f5f5 !important; font-size: 11px; }

.chat-history {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: #fcfcfc;
  display: flex;
  flex-direction: column;
}

.chat-bubble {
  max-width: 90%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  margin-bottom: 12px;
}
.chat-bubble.user { align-self: flex-end; background: var(--orin-primary, #409EFF); color: white; }
.chat-bubble.assistant { align-self: flex-start; background: white; border: 1px solid #efefef; }

.bot-info { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; font-weight: 700; font-size: 10px; color: #666; }

.chat-input-area { padding: 12px; border-top: 1px solid #eee; }
.icon-send { cursor: pointer; color: var(--orin-primary, #409EFF); }

/* Markdown Styles */
.markdown-body :deep(p) { margin-bottom: 8px; line-height: 1.5; }
.markdown-body :deep(pre) { background: #f6f8fa; padding: 10px; border-radius: 6px; overflow-x: auto; font-family: monospace; }
.markdown-body :deep(code) { background: #f0f0f0; padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin-bottom: 8px; }
</style>
