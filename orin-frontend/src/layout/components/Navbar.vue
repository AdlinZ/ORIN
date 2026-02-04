<template>
  <div class="header-container">
    <div class="left-panel">
      <el-breadcrumb separator="/" class="dynamic-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/dashboard/monitor' }">智能看板</el-breadcrumb-item>
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index" :to="item.path">
          {{ item.meta.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="right-panel">
      <!-- Teleport target for page-specific actions -->
      <div id="navbar-actions" class="navbar-actions"></div>

      <div class="action-items">
        <el-tooltip :content="isDark ? '切换亮色模式' : '切换暗黑模式'" placement="bottom">
          <el-icon class="action-icon" @click="toggleDarkMode">
            <Moon v-if="!isDark" />
            <Sunny v-else />
          </el-icon>
        </el-tooltip>

        <el-tooltip content="全屏切换" placement="bottom">
          <el-icon class="action-icon" @click="toggleFullScreen"><FullScreen /></el-icon>
        </el-tooltip>

        <el-tooltip content="刷新当前页面" placement="bottom">
          <el-icon class="action-icon" @click="handleRefresh">
            <Refresh />
          </el-icon>
        </el-tooltip>

        <div class="divider"></div>

        <el-tooltip content="系统 AI 评估 (System Evaluation)" placement="bottom">
            <div class="system-ai-trigger" @click="showSystemEval = true">
                <el-icon><DataAnalysis /></el-icon>
                <span class="trigger-text">AI Eval</span>
            </div>
        </el-tooltip>
      </div>
    </div>
    <!-- System AI Evaluation Dialog -->
    <el-dialog 
        v-model="showSystemEval" 
        :show-close="false"
        :header="null"
        width="900px" 
        class="orin-core-dialog"
        :close-on-click-modal="false"
        append-to-body
        @open="initSystemAI"
        align-center
    >
        <template #header="{ close, titleId, titleClass }">
            <div class="core-header">
                <div class="header-left">
                    <div class="core-logo">
                        <el-icon><Cpu /></el-icon>
                    </div>
                    <div class="core-title">ORIN CORE</div>
                    <div class="header-tags">
                        <div class="core-tag">SYSADMIN MODE</div>
                        <div class="core-tag da">V.ALPHA</div>
                    </div>
                </div>
                <div class="header-right">
                    <div class="sys-status">
                        <div class="status-dot"></div>
                        <span>SYSTEM_OK</span>
                    </div>
                    <el-icon class="close-icon" @click="showSystemEval = false"><Close /></el-icon>
                </div>
            </div>
        </template>

        <div class="orin-terminal-container">
            <!-- Main Chat Area -->
            <div class="terminal-body" ref="chatScrollRef">
                
                <!-- Welcome / AI Message Block -->
                <div v-for="(msg, index) in systemMessages" :key="index" class="terminal-msg-row" :class="msg.role">
                    <div class="t-avatar" v-if="msg.role === 'ai'">
                        <el-icon><help-filled /></el-icon> <!-- Robot/Help icon -->
                    </div>
                    
                    <div class="t-content-group">
                        <div class="t-sender-name" v-if="msg.role === 'ai'">ORIN AI ASSISTANT</div>
                        
                        <div class="t-msg-card">
                            <div class="marketing-text" v-if="msg.role === 'ai' && index === 0">
                                系统初始化已完成。我是您的系统级 AI 管理助手。<br/><br/>
                                您可以询问关于系统硬件、软件配置的问题，或者要求我执行脚本、清理日志等操作。
                            </div>
                            <div class="markdown-body" v-else v-html="renderMarkdown(msg.content)"></div>

                            <!-- Quick Actions (Only for first message) -->
                            <div class="quick-actions" v-if="msg.role === 'ai' && index === 0">
                                <div class="q-btn" @click="quickCommand('查看系统资源占用')">
                                    <el-icon><Odometer /></el-icon>
                                    <span>查看系统资源占用</span>
                                </div>
                                <div class="q-btn" @click="quickCommand('分析最近的错误日志')">
                                    <el-icon><DocumentChecked /></el-icon>
                                    <span>分析最近的错误日志</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Loading Indicator -->
                <div v-if="systemLoading" class="terminal-msg-row ai">
                    <div class="t-avatar"><el-icon class="is-loading"><Loading /></el-icon></div>
                    <div class="t-content-group">
                        <div class="t-sender-name">ORIN AI ASSISTANT</div>
                        <div class="t-msg-card typing">
                            _COMPUTING...
                        </div>
                    </div>
                </div>

            </div>

             <!-- Floating Input Command Bar -->
             <div class="terminal-footer">
                <div class="command-bar">
                    <div class="cmd-prompt">$</div>
                    <input 
                        v-model="systemInput" 
                        class="cmd-input"
                        placeholder="输入管理指令或询问系统状态 ..." 
                        @keyup.enter="sendSystemMessage"
                        :disabled="systemLoading"
                    />
                    <div class="cmd-actions">
                        <span class="cmd-hint">ENTER</span>
                        <button class="run-btn" @click="sendSystemMessage" :disabled="systemLoading">
                            RUN
                        </button>
                    </div>
                </div>
                <div class="cmd-statusbar">
                    <span>READY_FOR_COMMAND</span>
                    <span class="secure"><el-icon><Lock /></el-icon> ENCRYPTED</span>
                    <span>ROOT_ACCESS</span>
                </div>
             </div>
        </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAppStore } from '@/stores/app';
import { useDark, useFullscreen } from '@vueuse/core';
import { 
  Refresh, Moon, Sunny, FullScreen, DataAnalysis, Cpu, Aim, EditPen, User, Loading,
  Close, HelpFilled, Odometer, DocumentChecked, Lock
} from '@element-plus/icons-vue';
import { chatAgent, getAgentList } from '@/api/agent';
import { getServerHardware, getTokenHistory } from '@/api/monitor';
import { getModelConfig } from '@/api/modelConfig';
import { getKnowledgeList } from '@/api/knowledge';
import { nextTick } from 'vue';
import { v4 as uuidv4 } from 'uuid';
import { marked } from 'marked';

const router = useRouter();
const route = useRoute();

const appStore = useAppStore();

const breadcrumbs = computed(() => {
  return route.matched.filter(item => 
    item.meta && 
    item.meta.title && 
    item.path !== '/dashboard' && 
    item.path !== '/dashboard/monitor'
  );
});

// Dark mode logic
const isDark = useDark({
  onChanged(dark) {
    if (dark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }
});

const toggleDarkMode = () => {
  isDark.value = !isDark.value;
};

// Fullscreen logic
const { isFullscreen, toggle: toggleFullScreen } = useFullscreen();

// 刷新当前页面
const handleRefresh = () => {
  // 触发当前页面的刷新事件
  window.dispatchEvent(new Event('page-refresh'));
  
  ElMessage({
    message: '正在刷新页面数据...',
    type: 'info',
    duration: 1500
  });
};

// System AI Logic
const showSystemEval = ref(false);
const systemInput = ref('');
const systemLoading = ref(false);
const chatScrollRef = ref(null);
const systemMessages = ref([
    { role: 'ai', content: '系统内核已就绪。我是 ORIN 全局监控 AI，已接入实时硬件监控、日志系统与知识库索引。请问有什么可以帮您？', time: 'Now' }
]);
const currentKernelAgent = ref(null);
const currentConversationId = ref(null);

const renderMarkdown = (text) => {
    try {
        return marked.parse(text || '');
    } catch (e) {
        return text;
    }
};

const scrollToBottom = async () => {
    await nextTick();
    if (chatScrollRef.value) {
        chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight;
    }
};

const initSystemAI = async () => {
    // Scroll to bottom
    scrollToBottom();
    // Load available agents to act as "Kernel"
    if (!currentKernelAgent.value) {
        try {
            // 1. Fetch System Configuration
            const configRes = await getModelConfig().catch(() => ({}));
            const preferredModel = configRes.systemModel;

            // 2. Fetch Agents
            const res = await getAgentList();
            const agents = res.data || res;
            
            if (agents.length > 0) {
                let foundAgent = null;

                // 3. Try to match preferred model
                if (preferredModel) {
                    foundAgent = agents.find(a => a.modelName === preferredModel || a.modelId === preferredModel);
                }

                // 4. If not found, fallback to 'System' name, then first available
                if (!foundAgent) {
                     foundAgent = agents.find(a => a.name.includes('System')) || agents[0];
                }
                
                currentKernelAgent.value = foundAgent;
            }
        } catch (e) {
            console.error('Failed to load kernel agent', e);
        }
    }
};

const sendSystemMessage = async () => {
    if (!systemInput.value.trim() || !currentKernelAgent.value) return;

    const userMsg = systemInput.value;
    systemInput.value = '';
    systemMessages.value.push({ role: 'user', content: userMsg, time: new Date().toLocaleTimeString() });
    scrollToBottom();
    systemLoading.value = true;

    try {
        // [PHASE] 1. Gather Real System Data
        const [hardwareRes, agentsRes, kbRes, logsRes] = await Promise.allSettled([
            getServerHardware().catch(e => ({ error: 'Hardware fetch failed' })),
            getAgentList().catch(e => []),
            getKnowledgeList().catch(e => ({ list: [] })),
            getTokenHistory({ size: 5 }).catch(e => ({ content: [] })) // Last 5 tasks
        ]);

        // Format Hardware
        const hdw = hardwareRes.status === 'fulfilled' ? hardwareRes.value : { cpuUser: 'N/A', memoryAvailable: 'N/A' };
        const cpuInfo = `Process CPU: ${(hdw.processCpuLoad * 100).toFixed(1)}%, System CPU: ${(hdw.systemCpuLoad * 100).toFixed(1)}%`;
        const memInfo = `Available Memory: ${(hdw.memoryAvailable / 1024 / 1024 / 1024).toFixed(2)} GB / ${(hdw.memoryTotal / 1024 / 1024 / 1024).toFixed(2)} GB`;
        
        // Format Agents
        const agents = agentsRes.status === 'fulfilled' ? (agentsRes.value.data || agentsRes.value) : [];
        const agentSummary = agents.map(a => `- [${a.enabled ? 'ON' : 'OFF'}] ${a.name} (${a.modelName})`).join('\n').slice(0, 500); // Truncate

        // Format Knowledge Bases
        const kbs = kbRes.status === 'fulfilled' ? (kbRes.value.data?.list || []) : [];
        const kbSummary = kbs.map(k => `- ${k.name} (${k.docCount} docs)`).join('\n').slice(0, 500);

        // Format Recent Logs
        const logs = logsRes.status === 'fulfilled' ? (logsRes.value.content || []) : [];
        const logSummary = logs.map(l => {
            const time = l.createdAt || 'N/A';
            const action = l.endpoint ? l.endpoint.split('/').pop() : 'Request'; // Simple action name
            let detail = l.errorMessage || (l.success ? 'Success' : 'Failed');
            if (detail.length > 30) detail = detail.substring(0, 30) + '...';
            return `- [${time}] ${action}: ${detail}`;
        }).join('\n').slice(0, 500);

        // Construct System Persona Prompt
        const systemPersona = `
You are the **ORIN System Core AI** (ORIN 系统中枢 AI).
You have access to REAL-TIME system metrics. Use them to answer user questions.

### REAL-TIME SYSTEM STATUS (DO NOT HALUCINATE, USE THIS DATA):

**1. Hardware Topology:**
- CPU Status: ${cpuInfo}
- Memory Status: ${memInfo}

**2. Active Agents (${agents.length} Total):**
${agentSummary}

**3. Knowledge Bases:**
${kbSummary}

**4. Recent System Events (logs):**
${logSummary}

### Your Persona:
- **Role**: System Administrator & DevOps AI.
- **Tone**: Professional, precise, technical (Cyberpunk style preferred).
- **Language**: Chinese (Simplified).

### User Query:
${userMsg}
`;

        // Ensure we have a conversation ID for this session
        if (!currentConversationId.value) {
            currentConversationId.value = uuidv4();
        }

        const res = await chatAgent(currentKernelAgent.value.agentId, userMsg, null, systemPersona, currentConversationId.value);
        
        let aiResponse = 'System Busy...';
        
        // request.js interceptor returns response.data directly.
        // Backend returns raw OpenAI-compatible JSON or Direct String.
        if (res) {
             // 1. OpenAI Compatible format (SiliconFlow / Dify)
             if (res.choices && res.choices.length > 0 && res.choices[0].message) {
                 aiResponse = res.choices[0].message.content;
             } 
             // 2. Nested data structure (Sometimes backend wrappers do this)
             else if (res.data && res.data.choices && res.data.choices[0]) {
                 aiResponse = res.data.choices[0].message.content;
             }
             // 3. Direct String data
             else if (typeof res === 'string') {
                 aiResponse = res;
             }
             // 4. String inside data
             else if (res.data && typeof res.data === 'string') {
                 aiResponse = res.data;
             }
        }
        
        systemMessages.value.push({ 
            role: 'ai', 
            content: aiResponse, 
            time: new Date().toLocaleTimeString() 
        });
        scrollToBottom();

    } catch (e) {
        systemMessages.value.push({ role: 'ai', content: 'System Internal Error: ' + e.message });
    } finally {
        systemLoading.value = false;
        scrollToBottom();
    }
};

const quickCommand = (cmd) => {
    systemInput.value = cmd;
    sendSystemMessage();
};

</script>

<style scoped>
/* System AI Styles */
.divider {
    width: 1px;
    height: 16px;
    background-color: var(--neutral-gray-200);
    margin: 0 4px;
}

.system-ai-trigger {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    padding: 6px 12px;
    background: #e1f3d8; /* Light Green */
    color: #67C23A;
    border-radius: 16px;
    font-size: 13px;
    font-weight: 600;
    transition: all 0.2s;
}

.system-ai-trigger:hover {
    background: #67C23A;
    color: white;
}

/* --- ORIN CORE (Adaptive Style) --- */

/* 1. Global Dialog Overrides */
:global(.orin-core-dialog.el-dialog) {
    background: var(--el-bg-color) !important;
    border-radius: 16px !important;
    overflow: hidden;
    box-shadow: var(--el-box-shadow-dark);
}
:global(.orin-core-dialog .el-dialog__header) {
    padding: 0;
    margin: 0;
    background: transparent;
}
:global(.orin-core-dialog .el-dialog__body) {
    padding: 0 !important;
    color: var(--el-text-color-primary);
    background: var(--el-bg-color);
}

/* 2. Custom Header */
.core-header {
    height: 72px; /* Slightly taller for better spacing */
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 24px;
    border-bottom: 1px solid var(--el-border-color-light);
    background: var(--el-bg-color-overlay);
}

.header-left {
    display: flex;
    align-items: center;
    gap: 16px; /* Increase gap */
}

.core-logo {
    width: 40px; /* Larger logo */
    height: 40px;
    background: var(--el-color-primary-light-9);
    border: 1px solid var(--el-color-primary-light-5);
    border-radius: 8px;
    color: var(--el-color-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
}

.core-title {
    font-family: 'Inter', ui-monospace, monospace;
    font-weight: 800;
    font-size: 20px; /* Larger title */
    color: var(--el-text-color-primary);
    letter-spacing: -0.5px;
    margin-right: 8px;
}

.header-tags {
    display: flex;
    gap: 8px;
    align-items: center;
}

.core-tag {
    font-family: monospace;
    font-size: 11px;
    background: var(--el-fill-color);
    color: var(--el-text-color-secondary);
    padding: 4px 8px;
    border-radius: 4px;
    border: 1px solid var(--el-border-color);
    font-weight: 600;
}
.core-tag.da {
    background: var(--el-fill-color-lighter);
}

.header-right {
    display: flex;
    align-items: center;
    gap: 20px;
}

.sys-status {
    display: flex;
    align-items: center;
    gap: 8px;
    font-family: monospace;
    font-size: 11px;
    color: #484b51;
}
.status-dot {
    width: 6px;
    height: 6px;
    background: #00ff9d;
    border-radius: 50%;
    box-shadow: 0 0 5px #00ff9d;
}

.close-icon {
    font-size: 20px;
    color: #484b51;
    cursor: pointer;
    transition: color 0.2s;
}
.close-icon:hover { color: #fff; }

/* 3. Terminal Body */
.orin-terminal-container {
    height: 550px;
    display: flex;
    flex-direction: column;
    position: relative;
    background: var(--el-bg-color);
}

.terminal-body {
    flex: 1;
    padding: 30px 40px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 30px;
    scrollbar-width: none; 
}
.terminal-body::-webkit-scrollbar { display: none; }

/* Message Rows */
.terminal-msg-row {
    display: flex;
    gap: 16px;
    animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.terminal-msg-row.user {
    flex-direction: row-reverse;
}

.t-avatar {
    width: 36px;
    height: 36px;
    background: var(--el-color-info-light-9);
    color: var(--el-color-info);
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
}
.terminal-msg-row.user .t-avatar {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
}

.t-sender-name {
    font-size: 11px;
    font-weight: 700;
    color: var(--el-text-color-secondary);
    margin-bottom: 6px;
    letter-spacing: 0.5px;
}
.terminal-msg-row.user .t-sender-name { text-align: right; }

.t-content-group {
    max-width: 80%;
}

.t-msg-card {
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 12px;
    border-top-left-radius: 2px;
    padding: 16px 20px;
    color: var(--el-text-color-primary);
    font-size: 14px;
    line-height: 1.6;
}
.terminal-msg-row.user .t-msg-card {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-8);
    border-top-left-radius: 12px;
    border-top-right-radius: 2px;
}

.quick-actions {
    display: flex;
    gap: 12px;
    margin-top: 16px;
    flex-wrap: wrap; /* Allow wrapping if space is tight */
}

.q-btn {
    display: inline-flex; /* Use inline-flex */
    align-items: center;
    gap: 8px;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    padding: 8px 16px;
    border-radius: 6px;
    font-size: 13px; /* Slightly larger text */
    cursor: pointer;
    transition: all 0.2s;
    color: var(--el-text-color-regular);
    white-space: nowrap; /* Prevent text wrapping */
}
.q-btn:hover {
    background: var(--el-fill-color);
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
}

/* 4. Footer Input Bar */
.terminal-footer {
    padding: 20px 40px 30px 40px;
    background: linear-gradient(to top, var(--el-bg-color) 60%, transparent);
    border-top: 1px solid var(--el-border-color-lighter);
}

.command-bar {
    background: var(--el-fill-color-darker);
    border: 1px solid var(--el-border-color);
    border-radius: 12px;
    display: flex;
    align-items: center;
    padding: 10px 16px;
    transition: border-color 0.2s;
}
.command-bar:focus-within {
    border-color: var(--el-color-primary);
}

.cmd-prompt {
    font-family: monospace;
    font-weight: bold;
    color: var(--el-color-primary);
    font-size: 18px;
    margin-right: 12px;
}

.cmd-input {
    flex: 1;
    background: transparent;
    border: none;
    outline: none;
    color: var(--el-text-color-primary);
    font-family: 'Menlo', 'Consolas', monospace;
    font-size: 14px;
}
.cmd-input::placeholder { color: var(--el-text-color-placeholder); }

.cmd-hint {
    font-size: 10px;
    color: var(--el-text-color-secondary);
    font-weight: 700;
    border: 1px solid var(--el-border-color);
    padding: 2px 6px;
    border-radius: 4px;
}

.run-btn {
    background: var(--el-color-primary);
    border: none;
    border-radius: 6px;
    padding: 6px 16px;
    color: white;
    font-weight: 800;
    font-size: 12px;
    cursor: pointer;
    transition: opacity 0.1s;
}
.run-btn:active { opacity: 0.8; }
.run-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.cmd-statusbar {
    display: flex;
    gap: 20px;
    margin-top: 10px;
    padding-left: 10px;
    font-family: monospace;
    font-size: 10px;
    color: var(--el-text-color-secondary);
    letter-spacing: 1px;
}
.cmd-statusbar .secure {
    display: flex;
    align-items: center;
    gap: 4px;
    color: var(--el-color-success);
}

.typing-text {
   color: var(--el-color-primary);
   animation: pulse 1.5s infinite;
}

@keyframes slideIn {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}

/* Navbar Base Styles */
.header-container {
  height: var(--header-height);
  background: var(--neutral-white);
  border-bottom: 1px solid var(--neutral-gray-2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.left-panel {
  display: flex;
  align-items: center;
}

.dynamic-breadcrumb {
  flex-shrink: 0;
}

.right-panel {
  display: flex;
  align-items: center;
  gap: 16px;
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-items {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 16px;
  border-left: 1px solid var(--neutral-gray-100);
}

.action-icon {
  font-size: 20px;
  cursor: pointer;
  color: var(--neutral-gray-600);
  transition: all 0.3s;
}

.action-icon:hover {
  color: var(--primary-color);
  transform: translateY(-1px);
}

.refresh-icon.is-refreshing {
  color: var(--error-color);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

/* Dark mode support */
html.dark .header-container {
  /* No specific override needed, base class uses var(--neutral-white) which flips */
}

html.dark .action-items {
  /* var(--neutral-gray-100) flips to dark */
}

html.dark .action-icon {
  /* var(--neutral-gray-600) flips to light-ish */
}

html.dark .action-icon:hover {
  color: var(--orin-primary);
}

/* Markdown Styles */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin-top: 12px; margin-bottom: 8px; font-weight: 700; }
.markdown-body :deep(p) { margin-bottom: 8px; line-height: 1.5; }
.markdown-body :deep(pre) { background: var(--el-fill-color-dark); padding: 10px; border-radius: 6px; overflow-x: auto; font-family: monospace; margin-bottom: 8px; }
.markdown-body :deep(code) { background: var(--el-fill-color); padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin-bottom: 8px; }
.markdown-body :deep(strong) { font-weight: 700; color: var(--el-color-primary); }
</style>
