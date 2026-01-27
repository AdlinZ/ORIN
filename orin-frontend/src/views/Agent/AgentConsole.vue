<template>
  <div class="page-container">
    <div class="dashboard-layout">
      <!-- Left Column: Config Center (Fixed Width) -->
      <div class="config-sidebar">
        <el-card shadow="never" class="sidebar-card">
          <template #header>
            <div class="sidebar-header">
              <div class="header-left">
                <el-button link :icon="ArrowLeft" @click="$router.push('/dashboard/agent/list')" class="back-btn" />
                <span>配置中心</span>
              </div>
              <div class="header-actions">
                <el-button type="primary" size="small" @click="saveConfig" :loading="saveLoading">保存</el-button>
              </div>
            </div>
          </template>
          
          <el-form :model="editForm" label-position="top" size="small" class="compact-form">
            <div class="config-group">
              <h4 class="group-title">基础定义</h4>
              <el-form-item label="智能体名称">
                <el-input v-model="editForm.name" />
              </el-form-item>
              <el-form-item label="核心模型架构">
                <el-input v-model="editForm.model" />
              </el-form-item>
            </div>

            <div class="config-group">
              <h4 class="group-title">推理参数</h4>
              <div class="param-item">
                <div class="param-label">
                  <span>Temperature</span>
                  <span class="param-value">{{ editForm.temperature }}</span>
                </div>
                <el-slider v-model="editForm.temperature" :min="0" :max="2" :step="0.1" />
              </div>
              <div class="param-item">
                <div class="param-label">
                  <span>Top P</span>
                  <span class="param-value">{{ editForm.topP }}</span>
                </div>
                <el-slider v-model="editForm.topP" :min="0" :max="1" :step="0.1" />
              </div>

              <div class="thinking-box">
                <div class="thinking-header">
                  <span>Enable Thinking</span>
                  <el-switch v-model="editForm.enableThinking" size="small" />
                </div>
                <div v-if="editForm.enableThinking" class="thinking-budget">
                  <div class="param-label">Budget</div>
                  <el-input-number v-model="editForm.thinkingBudget" :min="0" :max="64000" :step="1024" controls-position="right" style="width: 100%" size="small" />
                </div>
              </div>
            </div>

            <div class="config-group">
              <div class="group-header">
                <h4 class="group-title">System Prompt</h4>
                <el-select v-model="selectedPromptTemplate" placeholder="模板" size="small" style="width: 70px;" @change="applyPromptTemplate">
                  <el-option v-for="t in promptTemplates" :key="t.id" :label="t.name" :value="t.content" />
                </el-select>
              </div>
              <el-input 
                v-model="editForm.systemPrompt" 
                type="textarea" 
                :rows="8" 
                placeholder="角色描述..."
              />
            </div>
          </el-form>
        </el-card>
      </div>

      <!-- Right Column: Chat Window -->
      <div class="chat-main">
        <div class="chat-wrapper">
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

          <div class="chat-history" ref="messagesContainer">
            <div v-for="(msg, i) in chatMessages" :key="i" class="chat-bubble" :class="msg.role">
              <div class="bubble-content">
                <div v-if="msg.role === 'assistant'" class="bot-info">
                  <el-avatar :size="18" icon="UserFilled" />
                  <span class="name">{{ agentInfo.agentName }}</span>
                </div>
                {{ msg.content }}
              </div>
            </div>
            <div v-if="chatLoading" class="chat-bubble assistant">
               <div class="bubble-content typing">...</div>
            </div>
          </div>

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

        <div class="logs-drawer" :class="{ 'collapsed': !logsExpanded }">
          <div class="logs-header" @click="logsExpanded = !logsExpanded">
            <span class="title"><el-icon><Tickets /></el-icon> Runtime Logs</span>
            <div class="logs-actions">
              <el-button link size="small" :icon="Refresh" @click.stop="fetchLogs" />
              <el-icon class="expand-icon"><ArrowUp v-if="!logsExpanded"/><ArrowDown v-else/></el-icon>
            </div>
          </div>
          <div class="log-stream" v-if="logsExpanded">
            <template v-for="(log, index) in logs" :key="index">
              <div class="log-entry" :class="log.type">
                <span class="log-time">{{ new Date(log.timestamp).toLocaleTimeString() }}</span>
                <span class="log-tag">[{{ log.type }}]</span>
                <span class="log-msg"><span class="prefix req">REQ:</span> {{ log.content }}</span>
              </div>
              <div v-if="log.response" class="log-entry resp" :class="log.type">
                <span class="log-time" style="visibility: hidden">{{ new Date(log.timestamp).toLocaleTimeString() }}</span>
                <span class="log-tag" style="visibility: hidden">[{{ log.type }}]</span>
                <span class="log-msg"><span class="prefix res">RES:</span> {{ log.response }}</span>
              </div>
            </template>
            <div v-if="logs.length === 0" class="empty-logs">暂无运行日志</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, computed, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
  ChatDotRound, Monitor, Setting, Position, UserFilled,
  VideoPlay, VideoPause, Refresh, Cpu, ArrowLeft,
  ArrowUp, ArrowDown, Timer, CircleCheck, Tickets
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getAgentMetadata, updateAgent, chatAgent } from '@/api/agent';
import { getAgentMetrics, getAgentList as getMonitorAgentList } from '@/api/monitor';
import { getAgentLogs, controlAgent } from '@/api/runtime';
import request from '@/utils/request';

const route = useRoute();
const router = useRouter();
const agentId = route.params.id;

// UI States
const loading = ref(false);
const saveLoading = ref(false);
const logsExpanded = ref(false);

// Data
const agentInfo = ref({});
const metrics = ref({ latency: [], tokens: [] });
const logs = ref([]);
const editForm = ref({
  agentId: '',
  name: '',
  model: '',
  temperature: 0.7,
  topP: 0.7,
  systemPrompt: '',
  enableThinking: false,
  thinkingBudget: 4096
});
const promptTemplates = ref([]);
const selectedPromptTemplate = ref('');

// Computed Metrics for tags
const latestTokenCount = computed(() => {
  if (metrics.value.tokens.length > 0) {
    return metrics.value.tokens[metrics.value.tokens.length - 1].value;
  }
  return 0;
});
const latestLatency = computed(() => {
  if (metrics.value.latency.length > 0) {
    return Math.round(metrics.value.latency[metrics.value.latency.length - 1].value);
  }
  return 0;
});

// Chat States
const chatMessages = ref([]);
const chatInput = ref('');
const chatLoading = ref(false);
const messagesContainer = ref(null);

let pollTimer = null;

const fetchData = async () => {
  loading.value = true;
  try {
    const listRes = await getMonitorAgentList();
    const current = listRes.find(a => a.agentId === agentId);
    if (current) agentInfo.value = current;

    const [metaRes, promptRes] = await Promise.all([
      getAgentMetadata(agentId),
      request.get(`/knowledge/agents/${agentId}/meta/prompts`).catch(() => [])
    ]);

    if (metaRes) {
      editForm.value = { 
        agentId: agentId,
        name: metaRes.name || metaRes.agentName || '',
        model: metaRes.modelName || '',
        temperature: metaRes.temperature || 0.7,
        topP: metaRes.topP || 0.7,
        systemPrompt: metaRes.systemPrompt || '',
        enableThinking: metaRes.enableThinking || false,
        thinkingBudget: metaRes.thinkingBudget || 4096
      };
    }
    promptTemplates.value = promptRes || [];

    fetchMetrics();
    fetchLogs();
  } catch (error) {
    ElMessage.error('加载智能体数据失败');
  } finally {
    loading.value = false;
  }
};

const fetchMetrics = async () => {
  const end = Date.now();
  const start = end - 60 * 60 * 1000; // 扩大到1小时
  try {
    const res = await getAgentMetrics(agentId, start, end);
    const data = res || [];
    metrics.value = {
      latency: data.map(d => ({ timestamp: d.timestamp, value: d.responseLatency || 0 })),
      tokens: data.map(d => ({ timestamp: d.timestamp, value: d.tokenCost || 0 }))
    };
  } catch (e) { console.warn('Metrics error'); }
};

const fetchLogs = async () => {
  try {
    const res = await getAgentLogs(agentId);
    logs.value = res.data || res || [];
  } catch (e) { console.warn('Logs error'); }
};

const handleControl = async (action) => {
    try {
        await controlAgent(agentId, action);
        ElMessage.success(`操作成功：${action}`);
        setTimeout(fetchData, 1000);
    } catch (e) { ElMessage.error('操作失败: ' + e.message); }
};

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return;
  
  const msg = chatInput.value;
  chatMessages.value.push({ role: 'user', content: msg });
  chatInput.value = '';
  chatLoading.value = true;
  
  try {
    const res = await chatAgent(agentId, msg);
    const answer = res.answer || res.choices?.[0]?.message?.content || JSON.stringify(res);
    chatMessages.value.push({ role: 'assistant', content: answer || '处理完成' });
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '（异常）' });
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

const saveConfig = async () => {
  saveLoading.value = true;
  try {
    await updateAgent(agentId, editForm.value);
    ElMessage.success('配置已保存');
    const metaRes = await getAgentMetadata(agentId);
    if(metaRes) {
       agentInfo.value.agentName = metaRes.name || metaRes.agentName;
       agentInfo.value.modelName = metaRes.modelName;
    }
  } catch (e) {
    ElMessage.error('保存失败');
  } finally {
    saveLoading.value = false;
  }
};

const applyPromptTemplate = (content) => {
  if (content) editForm.value.systemPrompt = content;
};

const getStatusType = (s) => ({ 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' }[s] || 'danger');
const getHealthStatusType = (s) => (s || 100) >= 90 ? 'success' : (s >= 60 ? 'warning' : 'danger');

onMounted(() => {
  fetchData();
  chatMessages.value = [{ role: 'assistant', content: '控制台已就绪。' }];
  pollTimer = setInterval(() => {
    fetchMetrics();
    if (logsExpanded.value) fetchLogs();
  }, 5000);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.page-container {
  padding: 0;
  height: calc(100vh - 100px); /* Account for navbar + margin/padding */
  background: var(--neutral-gray-50);
}

.dashboard-layout {
  height: 100%;
  display: flex;
  gap: 8px;
  padding: 8px;
}

/* Sidebar Styling - More Compact */
.config-sidebar {
  width: 300px;
  height: 100%;
}

.sidebar-card {
  height: 100%;
  border-radius: 8px;
  border: none;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.sidebar-card :deep(.el-card__header) {
  padding: 8px 12px;
}

.sidebar-card :deep(.el-card__body) {
  padding: 12px;
  overflow-y: auto;
  height: calc(100% - 50px);
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 700;
  font-size: 13px;
}

.back-btn { font-size: 16px; margin-right: -4px; }

.header-actions { display: flex; align-items: center; gap: 4px; }

.compact-form :deep(.el-form-item) {
  margin-bottom: 8px;
}
.compact-form :deep(.el-form-item__label) {
  margin-bottom: 0 !important;
  font-size: 12px;
  color: #666;
}

.config-group { margin-bottom: 16px; }

.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.group-title {
  font-size: 11px;
  color: var(--orin-primary);
  margin: 0;
  text-transform: uppercase;
  font-weight: 800;
}

.param-item { margin-bottom: 8px; }

.param-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  margin-bottom: 2px;
}

.param-value { color: var(--orin-primary); font-weight: 700; }

.thinking-box {
  background: #f8f9fa;
  border-radius: 4px;
  padding: 8px;
  border: 1px solid #eee;
}

.thinking-header { display: flex; justify-content: space-between; font-size: 11px; }

.thinking-budget { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #eee; }

/* Chat Main - Balanced Height */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 8px;
}

.chat-wrapper {
  flex: 1;
  background: white;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #eee;
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

.monitor-tag {
  border: none !important;
  background: #f5f5f5 !important;
  font-size: 11px;
}

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

.chat-bubble.user { align-self: flex-end; background: var(--orin-primary); color: white; }
.chat-bubble.assistant { align-self: flex-start; background: white; border: 1px solid #efefef; }

.bot-info { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; font-weight: 700; font-size: 10px; color: var(--orin-primary); }

.chat-input-area { padding: 12px; border-top: 1px solid #eee; }

/* Logs - Minimalist */
.logs-drawer {
  background: #111;
  border-radius: 8px;
  overflow: hidden;
}

.logs-drawer.collapsed { height: 32px; }

.logs-header {
  height: 32px;
  padding: 0 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  background: #1a1a1a;
  color: #777;
  font-size: 10px;
}

.log-stream {
  height: 120px;
  padding: 8px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 11px;
}

.log-entry { margin-bottom: 4px; display: flex; gap: 8px; line-height: 1.4; }
.log-entry.resp { margin-top: -2px; opacity: 0.85; }
.log-entry.ERROR { color: #f44747; }
.log-time { color: #aaa; margin-right: 8px; white-space: nowrap; }
.log-tag { color: #5555ff; margin-right: 8px; min-width: 60px; }
.log-msg { color: #eee; flex: 1; word-break: break-all; }
.prefix { font-weight: bold; margin-right: 4px; font-size: 10px; }
.prefix.req { color: #4fc1ff; }
.prefix.res { color: #9cdcfe; }
</style>
