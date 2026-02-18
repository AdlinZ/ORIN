<template>
  <div class="page-container">
    <div class="dashboard-layout">
      <!-- Left Column: Config Center (Fixed Width) -->
      <div class="config-sidebar">
        <el-card shadow="never" class="sidebar-card">
          <template #header>
            <div class="sidebar-header">
              <div class="header-left">
                <el-button link :icon="ArrowLeft" @click="$router.push(ROUTES.APPLICATIONS.AGENTS)" class="back-btn" />
                <div class="agent-brand">
                  <span class="agent-name-text">{{ agentInfo.agentName || editForm.name }}</span>
                  <span class="view-tag">{{ getViewLabel(agentInfo.viewType || currentMode) }}</span>
                </div>
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


            <!-- 聊天模型参数 (仅对话模型显示) -->
            <div class="config-group" v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent">
              <h4 class="group-title">推理参数</h4>
              <div class="param-item">
                <div class="param-label">
                  <span>多样性 (Temperature)</span>
                  <span class="param-value">{{ editForm.temperature }}</span>
                </div>
                <el-slider v-model="editForm.temperature" :min="0" :max="2" :step="0.1" />
              </div>
              <div class="param-item">
                <div class="param-label">
                  <span>核采样 (Top P)</span>
                  <span class="param-value">{{ editForm.topP }}</span>
                </div>
                <el-slider v-model="editForm.topP" :min="0" :max="1" :step="0.1" />
              </div>

              <div class="thinking-box">
                <div class="thinking-header">
                  <span>启用深度思考 (Thinking)</span>
                  <el-switch v-model="editForm.enableThinking" size="small" />
                </div>
                <div v-if="editForm.enableThinking" class="thinking-budget">
                  <div class="param-label">思考预算 (Tokens)</div>
                  <el-input-number v-model="editForm.thinkingBudget" :min="0" :max="64000" :step="1024" controls-position="right" style="width: 100%" size="small" />
                </div>
              </div>
            </div>

            <!-- 图像生成参数 -->
            <div class="config-group" v-if="isImageGenerationAgent">
              <h4 class="group-title">图像生成参数</h4>
              
              <el-form-item label="图像尺寸 (Image Size)">
                <el-select v-model="editForm.imageSize" size="small" style="width: 100%">
                  <el-option label="正方形 1:1 (1328x1328)" value="1328x1328" />
                  <el-option label="横屏 16:9 (1664x928)" value="1664x928" />
                  <el-option label="竖屏 9:16 (928x1664)" value="928x1664" />
                  <el-option label="标准 4:3 (1472x1140)" value="1472x1140" />
                  <el-option label="标准 3:4 (1140x1472)" value="1140x1472" />
                  <el-option label="经典 3:2 (1584x1056)" value="1584x1056" />
                  <el-option label="经典 2:3 (1056x1584)" value="1584x1056" />
                </el-select>
              </el-form-item>

              <div class="param-item">
                <div class="param-label">
                  <span>随机种子 (Seed)</span>
                  <span class="param-value">{{ editForm.seed || '随机生成' }}</span>
                </div>
                <el-input v-model="editForm.seed" placeholder="留空则随机生成" size="small">
                  <template #append>
                    <el-button :icon="Refresh" @click="generateRandomSeed" size="small" />
                  </template>
                </el-input>
              </div>

              <div class="param-item">
                <div class="param-label">
                  <span>引导程度 (CFG Scale)</span>
                  <span class="param-value">{{ editForm.guidanceScale || 7.5 }}</span>
                </div>
                <el-slider v-model="editForm.guidanceScale" :min="1" :max="20" :step="0.5" />
              </div>

              <div class="param-item">
                <div class="param-label">
                  <span>推理步数 (Steps)</span>
                  <span class="param-value">{{ editForm.inferenceSteps || 20 }}</span>
                </div>
                <el-slider v-model="editForm.inferenceSteps" :min="1" :max="50" :step="1" />
              </div>

              <el-form-item label="反向提示词 (Negative Prompt)">
                <el-input 
                  v-model="editForm.negativePrompt" 
                  type="textarea" 
                  :rows="3" 
                  placeholder="在此处描述不希望在画面中出现的内容..."
                  size="small"
                />
              </el-form-item>
            </div>

            <!-- 视频生成参数 -->
            <div class="config-group" v-if="isTTVAgent">
              <h4 class="group-title">视频生成参数</h4>
              
              <el-form-item label="视频比例 (Aspect Ratio)" v-if="!editForm.model?.includes('I2V')">
                <el-radio-group v-model="editForm.videoSize" size="small">
                  <el-radio-button value="16:9" label="16:9" />
                  <el-radio-button value="9:16" label="9:16" />
                  <el-radio-button value="1:1" label="1:1" />
                </el-radio-group>
              </el-form-item>

              <div class="param-item">
                <div class="param-label">
                  <span>随机种子 (Seed)</span>
                  <span class="param-value">{{ editForm.seed || '随机' }}</span>
                </div>
                <el-input v-model="editForm.seed" placeholder="留空则随机" size="small">
                  <template #append>
                    <el-button :icon="Refresh" @click="generateRandomSeed" size="small" />
                  </template>
                </el-input>
              </div>

              <el-form-item label="反向提示词 (Negative Prompt)">
                <el-input 
                  v-model="editForm.negativePrompt" 
                  type="textarea" 
                  :rows="2" 
                  placeholder="不希望出现的内容..."
                  size="small"
                />
              </el-form-item>
            </div>

            <!-- TTS Parameters -->
            <div class="config-group" v-if="isTTSAgent">
              <div class="group-header">
                <h4 class="group-title">语音合成配置</h4>
              </div>
              

              <el-form-item label="音色 (Voice)">
                 <el-select v-model="editForm.voice" placeholder="请选择音色" filterable allow-create clearable>
                    <el-option label="Alex" value="alex" />
                    <el-option label="Anna" value="anna" />
                    <el-option label="Bella" value="bella" /> 
                    <el-option label="Benjamin" value="benjamin" />
                    <el-option label="Charles" value="charles" />
                    <el-option label="David" value="david" />
                 </el-select>
                 <div class="help-text text-xs text-gray-400 mt-1">具体可用音色取决于所选模型能力</div>
              </el-form-item>

              <div class="param-item">
                <div class="param-label">
                  <span>语速 (Speed)</span>
                  <span class="param-value">{{ editForm.speed || 1.0 }}x</span>
                </div>
                <el-slider v-model="editForm.speed" :min="0.5" :max="2.0" :step="0.1" />
              </div>
              
               <div class="param-item">
                <div class="param-label">
                  <span>音量增益 (Gain)</span>
                  <span class="param-value">{{ editForm.gain || 0 }} dB</span>
                </div>
                <el-slider v-model="editForm.gain" :min="-10" :max="10" :step="1" />
              </div>
              <div class="help-text text-xs text-gray-400 mt-2" v-if="editForm.model && editForm.model.includes('MOSS')">
                提示: MOSS 对话标记如 [S1] 可指定发言人。此处音色字段为可选。
              </div>
            </div>

            <!-- System Prompt (仅聊天模型显示) -->
            <div class="config-group" v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent">
              <div class="group-header">
                <h4 class="group-title">系统提示词 (System Prompt)</h4>
                <el-select v-model="selectedPromptTemplate" placeholder="模板" size="small" style="width: 70px;" @change="applyPromptTemplate">
                  <el-option v-for="t in promptTemplates" :key="t.id" :label="t.name" :value="t.content" />
                </el-select>
              </div>
              <el-input 
                v-model="editForm.systemPrompt" 
                type="textarea" 
                :rows="8" 
                placeholder="在此处输入对智能体的角色定义与回复指南..."
              />
            </div>
          </el-form>
        </el-card>
      </div>

      <!-- Right Column: Chat Window -->
      <div class="chat-main">
        <div class="chat-wrapper">
          <component 
            :is="activeRunner" 
            :agentId="agentId" 
            :agentInfo="agentInfo"
            :parameters="currentParameters"
          />
        </div>

        <div class="logs-drawer" :class="{ 'collapsed': !logsExpanded }">
          <div class="logs-header" @click="logsExpanded = !logsExpanded">
            <span class="title"><el-icon><Tickets /></el-icon> 运行时日志</span>
            <div class="logs-actions">
              <el-button link size="small" :icon="Refresh" @click.stop="fetchLogs" />
              <el-icon class="expand-icon"><ArrowUp v-if="!logsExpanded"/><ArrowDown v-else/></el-icon>
            </div>
          </div>
          <div class="log-stream" v-if="logsExpanded">
            <template v-for="(log, index) in logs" :key="index">
              <div class="log-entry" :class="log.type">
                <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                <span class="log-tag">[{{ log.type }}]</span>
                <span class="log-msg"><span class="prefix req">REQ:</span> {{ log.content }}</span>
              </div>
              <div v-if="log.response" class="log-entry resp" :class="log.type">
                <span class="log-time" style="visibility: hidden">{{ formatTime(log.timestamp) }}</span>
                <span class="log-tag" style="visibility: hidden">[{{ log.type }}]</span>
                <span class="log-msg"><span class="prefix res">RES:</span> {{ log.response }}</span>
              </div>
            </template>
            <div v-if="logs.length === 0" class="empty-logs">暂无运行日志</div>
          </div>
        </div>
      </div>

      <!-- History Sidebar (Right) for non-chat or toggle -->
      <div class="history-sidebar" v-if="showHistorySidebar">
         <div class="history-header">
           <span><el-icon><Clock /></el-icon> 执行历史</span>
           <el-button link :icon="Close" @click="showHistorySidebar = false" />
         </div>
         <div class="history-content">
           <div class="empty-history" v-if="!logs || logs.length === 0">暂无历史记录</div>
           <div v-else class="history-list">
              <div v-for="(log, idx) in logs" :key="idx" class="history-item" @click="restoreHistory(log)">
                  <div class="h-time">{{ formatTime(log.timestamp) }}</div>
                  <div class="h-preview">{{ log.content }}</div>
                  <el-tag size="small" v-if="log.type" class="h-tag">{{ log.type }}</el-tag>
              </div>
           </div>
         </div>
      </div>
    </div>
  </div>


</template>

<script setup>
import { ref, onMounted, onUnmounted, onBeforeUnmount, reactive, computed, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  ChatDotRound, Monitor, Setting, Position, UserFilled,
  VideoPlay, VideoPause, VideoCamera, Refresh, Cpu, ArrowLeft,
  ArrowUp, ArrowDown, Timer, CircleCheck, Tickets,
  Microphone, Picture, Connection, Clock, Close, Service,
  Menu, ElementPlus as LightningIcon, Headset
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getAgentMetadata, updateAgent, chatAgent } from '@/api/agent';
import { getAgentMetrics, getAgentList as getMonitorAgentList } from '@/api/monitor';
import { getAgentLogs, controlAgent } from '@/api/runtime';
import request from '@/utils/request';

// Runners
import ChatWindow from './components/ChatWindow.vue';
import WorkflowRunner from './components/WorkflowRunner.vue';
import CompletionRunner from './components/CompletionRunner.vue';
import AudioTranscriber from './components/AudioTranscriber.vue';
import ImageGenerator from './components/ImageGenerator.vue';
import AudioGenerator from './components/AudioGenerator.vue';
import VideoGenerator from './components/VideoGenerator.vue';

const route = useRoute();
const router = useRouter();
const agentId = route.params.id;

const isMounted = ref(false);
onMounted(() => {
  isMounted.value = true;
});

// UI States
const loading = ref(false);
const saveLoading = ref(false);
const logsExpanded = ref(false);
const showHistorySidebar = ref(false);

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
  thinkingBudget: 4096,
  // 图像生成参数 (Qwen-Image 默认值)
  imageSize: '1328x1328',
  seed: '',
  guidanceScale: 7.5,
  inferenceSteps: 20,
  negativePrompt: '',
  // 语音合成参数
  voice: '',
  speed: 1.0,
  gain: 0,
  // 视频生成参数
  videoSize: '16:9',
  videoDuration: '5',
  fps: 30,
  useReferenceImage: false
});
const promptTemplates = ref([]);
const selectedPromptTemplate = ref('');

// Runner Logic
// Initialize currentMode from tab parameter IMMEDIATELY (before activeRunner computed runs)
const initMode = (() => {
  if (route.query.tab) {
    const tab = route.query.tab.toLowerCase();
    console.log('[AgentConsole] Initializing mode from tab parameter:', tab);
    if (tab === 'image') return 'image';
    if (tab === 'stt' || tab === 'audio') return 'stt';
    if (tab === 'tts') return 'tts';
    if (tab === 'video') return 'video';
    if (tab === 'workflow') return 'workflow';
  }
  return 'chat'; // default
})();

const currentMode = ref(initMode);

// 动态参数 - 包含当前 editForm 的值
const currentParameters = computed(() => {
  if (isImageGenerationAgent.value) {
    // 图像生成参数
    return {
      imageSize: editForm.value.imageSize,
      seed: editForm.value.seed,
      guidanceScale: editForm.value.guidanceScale,
      inferenceSteps: editForm.value.inferenceSteps,
      negativePrompt: editForm.value.negativePrompt
    };
  }
  
  if (isTTSAgent.value) {
    return {
       voice: editForm.value.voice,
       speed: editForm.value.speed,
       gain: editForm.value.gain,
       model: editForm.value.model
    };
  }
  if (isTTVAgent.value) {
    return {
      videoSize: editForm.value.videoSize,
      videoDuration: editForm.value.videoDuration,
      fps: editForm.value.fps,
      seed: editForm.value.seed,
      negativePrompt: editForm.value.negativePrompt,
      model: editForm.value.model
    };
  }
  // 其他类型的参数
  return {
    temperature: editForm.value.temperature,
    topP: editForm.value.topP,
    systemPrompt: editForm.value.systemPrompt
  };
});

// 判断是否为图像生成智能体
const isImageGenerationAgent = computed(() => {
  if (currentMode.value === 'image') return true;
  const viewType = (agentInfo.value.viewType || '').toUpperCase();
  return viewType === 'TEXT_TO_IMAGE' || viewType === 'IMAGE_TO_IMAGE' || viewType === 'TTI';
});

const isTTSAgent = computed(() => {
  if (currentMode.value === 'tts') return true;
  const viewType = (agentInfo.value.viewType || '').toUpperCase();
  return viewType === 'TEXT_TO_SPEECH' || viewType === 'TTS';
});

const isTTVAgent = computed(() => {
  if (currentMode.value === 'video') return true;
  const viewType = (agentInfo.value.viewType || '').toUpperCase();
  return viewType === 'TEXT_TO_VIDEO' || viewType === 'TTV' || viewType === 'VIDEO';
});

const activeRunner = computed(() => {
  console.log('[ActiveRunner] Determining component:', {
    currentMode: currentMode.value,
    viewType: agentInfo.value.viewType,
    agentInfo: agentInfo.value
  });
  
  // Check Mode FIRST (from tab parameter or manual selection)
  // This allows URL-based navigation to override viewType
  if (currentMode.value === 'tts') {
    console.log('[ActiveRunner] → AudioGenerator (TTS from mode)');
    return AudioGenerator;
  }
  if (currentMode.value === 'stt') {
    console.log('[ActiveRunner] → AudioTranscriber (STT from mode)');
    return AudioTranscriber;
  }
  if (currentMode.value === 'image') {
    console.log('[ActiveRunner] → ImageGenerator (from mode)');
    return ImageGenerator;
  }
  if (currentMode.value === 'video') {
    console.log('[ActiveRunner] → VideoGenerator (from mode)');
    return VideoGenerator;
  }
  if (currentMode.value === 'workflow') {
    console.log('[ActiveRunner] → WorkflowRunner (from mode)');
    return WorkflowRunner;
  }
  if (currentMode.value === 'completion') {
    console.log('[ActiveRunner] → CompletionRunner (from mode)');
    return CompletionRunner;
  }
  
  // Then check View Type (from agent metadata) as fallback
  const viewType = (agentInfo.value.viewType || '').toUpperCase();
  if (viewType === 'STT' || viewType === 'SPEECH_TO_TEXT') {
    console.log('[ActiveRunner] → AudioTranscriber (STT from viewType)');
    return AudioTranscriber;
  }
  if (viewType === 'TTS' || viewType === 'TEXT_TO_SPEECH') {
    console.log('[ActiveRunner] → AudioGenerator (TTS from viewType)');
    return AudioGenerator;
  }
  if (viewType === 'TEXT_TO_IMAGE' || viewType === 'IMAGE_TO_IMAGE' || viewType === 'TTI') {
    console.log('[ActiveRunner] → ImageGenerator (from viewType)');
    return ImageGenerator;
  }
  if (viewType === 'TEXT_TO_VIDEO' || viewType === 'TTV' || viewType === 'VIDEO') {
    console.log('[ActiveRunner] → VideoGenerator (from viewType)');
    return VideoGenerator;
  }
  
  // Default to ChatWindow
  console.log('[ActiveRunner] → ChatWindow (default)');
  return ChatWindow;
});

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
      let extraParams = {};
      if (metaRes.parameters) {
        try {
          extraParams = JSON.parse(metaRes.parameters);
        } catch (e) { console.warn('Failed to parse extra parameters'); }
      }

      editForm.value = { 
        agentId: agentId,
        name: metaRes.name || metaRes.agentName || '',
        model: metaRes.modelName || '',
        temperature: metaRes.temperature || 0.7,
        topP: metaRes.topP || 0.7,
        systemPrompt: metaRes.systemPrompt || '',
        enableThinking: metaRes.enableThinking || false,
        thinkingBudget: metaRes.thinkingBudget || 4096,
        // Image parameters
        imageSize: extraParams.imageSize || metaRes.imageSize || '1328x1328',
        seed: extraParams.seed || metaRes.seed || '',
        guidanceScale: extraParams.guidanceScale || metaRes.guidanceScale || 7.5,
        inferenceSteps: extraParams.inferenceSteps || metaRes.inferenceSteps || 20,
        negativePrompt: extraParams.negativePrompt || metaRes.negativePrompt || '',
        // TTS parameters
        voice: extraParams.voice || '',
        speed: extraParams.speed !== undefined ? extraParams.speed : 1.0,
        gain: extraParams.gain !== undefined ? extraParams.gain : 0
      };
      
      currentMode.value = metaRes.mode || 'chat';
      
      // Update agentInfo viewType if present in meta
      if (metaRes.viewType) {
         agentInfo.value = { ...agentInfo.value, viewType: metaRes.viewType, providerType: metaRes.providerType };
      }
      
      // Debug: Log the viewType to help diagnose routing issues
      console.log('[AgentConsole] Agent loaded:', {
        agentId,
        viewType: agentInfo.value.viewType,
        currentMode: currentMode.value,
        activeRunner: activeRunner.value?.$options?.name || activeRunner.value?.name || 'Unknown'
      });
    }
    promptTemplates.value = promptRes || [];

    // fetchMetrics(); // Metrics now handled by ChatWindow (or we can keep it here for logs?)
    // AgentConsole logic cleanup: We don't need fetchMetrics here unless we show global stats.
    // For now we removed the global monitor bar in favor of component-specific bars.
    
    fetchLogs();
  } catch (error) {
    ElMessage.error('加载智能体数据失败');
  } finally {
    loading.value = false;
  }
};

// fetchMetrics Removed from parent as it is specific to ChatWindow or Runners
// If we need global metrics, we can add it back. But currently Monitor Bar is inside ChatWindow.

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

// sendMessage logic removed as it is moved to ChatWindow

// scrollToBottom removed

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

const generateRandomSeed = () => {
  // 生成 0 到 9999999999 之间的随机整数
  editForm.value.seed = Math.floor(Math.random() * 10000000000).toString();
};


const getStatusType = (s) => ({ 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' }[s] || 'danger');
const getHealthStatusType = (s) => (s || 100) >= 90 ? 'success' : (s >= 60 ? 'warning' : 'danger');

// Identity Helpers
const getProviderIcon = (p) => {
  if (!p) return Cpu;
  const type = p.toUpperCase();
  if (type === 'DIFY') return Service;
  if (type.includes('SILICON')) return LightningIcon;
  if (type.includes('OPENAI')) return Connection;
  return Cpu;
};

const getProviderTagType = (p) => {
  if (!p) return 'info';
  const type = p.toUpperCase();
  if (type === 'DIFY') return 'primary';
  if (type.includes('SILICON')) return 'warning';
  return 'info';
};

const getViewIcon = (v) => {
  if (!v) return ChatDotRound;
  const type = v.toUpperCase();
  if (type === 'CHAT') return ChatDotRound;
  if (type === 'WORKFLOW') return Connection;
  if (type === 'STT' || type === 'SPEECH_TO_TEXT') return Microphone;
  if (type === 'TTS' || type === 'TEXT_TO_SPEECH') return Headset; 
  if (type === 'TTI' || type === 'TEXT_TO_IMAGE' || type === 'IMAGE_TO_IMAGE') return Picture;
  if (type === 'TTV' || type === 'TEXT_TO_VIDEO') return VideoCamera;
  return Menu;
};

const getProviderColor = (p) => {
    if (!p) return '#999';
    if (p.toLowerCase().includes('silicon')) return '#fb923c';
    if (p.toLowerCase().includes('dify')) return '#155eef';
    return '#666';
};

const formatTime = (ts) => {
  if (!ts) return '-';
  
  // Handle Array format [yyyy, MM, dd, HH, mm, ss]
  if (Array.isArray(ts)) {
    return new Date(ts[0], ts[1] - 1, ts[2], ts[3] || 0, ts[4] || 0, ts[5] || 0).toLocaleTimeString();
  }

  // Handle String format
  const dateStr = String(ts).replace(' ', 'T');
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '-';
  return date.toLocaleTimeString();
};

const getViewTagType = (v) => {
    if (!v) return 'info';
    const type = v.toUpperCase();
    if (type === 'CHAT') return 'success';
    if (type === 'WORKFLOW') return 'primary';
    if (type.includes('TTS') || type.includes('SPEECH')) return 'warning';
    if (type.includes('TTI') || type.includes('IMAGE')) return 'danger';
    if (type.includes('TTV') || type.includes('VIDEO')) return 'primary';
    return 'info';
};

const getViewLabel = (v) => {
    if (!v) return '未知类型';
    const type = v.toUpperCase();
    if (type === 'CHAT') return '智能对话';
    if (type === 'WORKFLOW') return '工作流';
    if (type === 'STT' || type === 'SPEECH_TO_TEXT') return '语音转文字';
    if (type === 'TTS' || type === 'TEXT_TO_SPEECH') return '语音合成';
    if (type === 'TTI' || type === 'TEXT_TO_IMAGE') return '文生图';
    if (type === 'IMAGE_TO_IMAGE') return '图生图';
    if (type === 'TTV' || type === 'TEXT_TO_VIDEO') return '视频生成';
    return type;
};

const restoreHistory = (log) => {
    // Just show detail for now
    ElMessage.info("History item clicked: " + log.content);
};

onMounted(() => {
  fetchData();
  // chatMessages init moved to ChatWindow
  pollTimer = setInterval(() => {
    if (logsExpanded.value) fetchLogs();
  }, 5000);
});

onBeforeUnmount(() => {
  isMounted.value = false;
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.page-container {
  padding: 0;
  height: calc(100vh - 88px); /* 100vh - 64px(nav) - 24px(layout padding) */
  background: var(--neutral-gray-50);
}

.dashboard-layout {
  height: 100%;
  display: flex;
  gap: 0; 
  padding: 0;
  width: 100%;
}

/* Sidebar Styling - More Compact */
.config-sidebar {
  width: 280px;
  height: 100%;
  border-right: 1px solid #eee;
  background: white;
  flex-shrink: 0;
}

.sidebar-card {
  height: 100%;
  height: 100%;
  border-radius: 0;
  border: none;
  box-shadow: none;
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
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.agent-brand {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agent-name-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.view-tag {
  font-size: 11px;
  color: var(--neutral-gray-500);
  font-weight: 500;
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
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 8px;
  padding: 12px;
  overflow: hidden;
}

.chat-wrapper {
  flex: 1;
  background: white;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  flex: 1;
  background: white;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #eaeaea;
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
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

/* History Sidebar */
.history-sidebar {
  width: 260px;
  background: white;
  border-left: 1px solid #eee;
  display: flex;
  flex-direction: column;
}
.history-header {
  height: 40px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.history-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.history-item {
    padding: 8px;
    border-bottom: 1px solid #f5f5f5;
    cursor: pointer;
    transition: background 0.2s;
}
.history-item:hover { background: #f9f9f9; }
.h-time { color: #999; font-size: 11px; margin-bottom: 2px; }
.h-preview { font-size: 12px; color: #333; margin-bottom: 4px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.h-tag { scale: 0.8; transform-origin: left; }

.empty-history { text-align: center; color: #999; font-size: 12px; margin-top: 40px; }

/* Simplified Navbar Identity */
.agent-identity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.agent-info-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-mini-icon {
  font-size: 16px;
  opacity: 0.8;
}

.agent-name {
  font-weight: 600;
  font-size: 14px;
  color: #374151;
}

.info-divider {
  color: #d1d5db;
  font-size: 12px;
}

.view-label-minimal {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}

.view-label-minimal :deep(.el-icon) {
  font-size: 14px;
}

.history-trigger-minimal {
  border: none;
  background: transparent;
  color: #9ca3af;
  transition: all 0.2s;
}

.history-trigger-minimal:hover {
  background: #f3f4f6;
  color: var(--orin-primary);
}
</style>
