<template>
  <div class="console-page">
    <PageHeader
      title="应用控制台"
      description="实时调试应用行为并调整运行参数"
      icon="Monitor"
    />
    
    <div class="console-container">
      <!-- Left: Main Runner Area -->
      <div class="runner-panel">
        <!-- Mode Selector Bar -->
        <div class="mode-bar">
          <el-radio-group v-model="currentMode" size="small" class="mode-group">
            <el-radio-button
              v-for="mode in modeOptions"
              :key="mode.value"
              :label="mode.value"
            >
              <span class="mode-label">{{ mode.label }}</span>
            </el-radio-button>
          </el-radio-group>
          <div class="mode-bar-actions">
            <el-button link :icon="ArrowLeft" @click="handleBack" title="返回列表" />
          </div>
        </div>

        <!-- Runner Component Container -->
        <div class="runner-container">
          <component 
            :is="activeRunner" 
            :agentId="agentId" 
            :agentInfo="agentInfo"
            :parameters="currentParameters"
            :logs="logs"
            @refresh-logs="fetchLogs"
          />
        </div>
      </div>

      <!-- Right: Config Sidebar -->
      <aside class="config-sidebar">
        <!-- Sidebar Header -->
        <div class="sidebar-header">
          <div class="header-title">
            <el-icon><Setting /></el-icon>
            <h3>配置中心</h3>
          </div>
          <div class="header-controls">
            <el-tooltip content="配置快照">
              <el-button 
                link 
                :icon="Tickets" 
                @click="showSnapshotPanel = !showSnapshotPanel"
                :class="{ active: showSnapshotPanel }"
              />
            </el-tooltip>
            <el-button 
              type="primary" 
              size="small"
              :loading="saveLoading"
              @click="saveConfig"
            >
              <el-icon><CircleCheck /></el-icon>
              保存
            </el-button>
          </div>
        </div>

        <!-- Status Indicator -->
        <div class="status-bar">
          <div v-if="hasUnsavedChanges" class="status warning">
            <div class="status-dot"></div>
            <span>有未保存的更改</span>
          </div>
          <div v-else class="status saved">
            <div class="status-dot"></div>
            <span>已保存</span>
          </div>
        </div>

        <!-- Config Content -->
        <div class="sidebar-content">
          <!-- Snapshot Panel (Conditional) -->
          <div v-if="showSnapshotPanel" class="snapshot-panel">
            <div class="panel-title">最近快照</div>
            <div v-if="configSnapshots.length === 0" class="empty-state">
              <el-icon><Tickets /></el-icon>
              <span>暂无快照</span>
            </div>
            <div v-else class="snapshot-list">
              <div 
                v-for="snapshot in configSnapshots"
                :key="snapshot.id"
                class="snapshot-item"
                @click="applySnapshot(snapshot.id)"
              >
                <div class="snapshot-time">{{ formatSnapshotTime(snapshot.timestamp) }}</div>
                <div class="snapshot-note">{{ snapshot.label }}</div>
                <el-button size="small" type="text">恢复</el-button>
              </div>
            </div>
            <div class="panel-action">
              <el-button @click="createSnapshot" icon="Plus" size="small" block>
                新建快照
              </el-button>
            </div>
          </div>

          <!-- Config Form -->
          <el-form v-else :model="editForm" label-position="top" class="config-form">
            <!-- Basic Settings Section -->
            <div class="config-group">
              <div class="group-header">
                <el-icon><Menu /></el-icon>
                <span>基础设置</span>
              </div>
              <div class="group-content">
                <el-form-item label="名称" required>
                  <el-input 
                    v-model="editForm.name" 
                    placeholder="输入智能体名称"
                    clearable
                    maxlength="100"
                  />
                </el-form-item>
                <el-form-item label="模型" required>
                  <el-input 
                    v-model="editForm.model" 
                    placeholder="核心模型"
                    clearable
                  />
                </el-form-item>
              </div>
            </div>

            <!-- Inference Parameters Section -->
            <div v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent" class="config-group">
              <div class="group-header">
                <el-icon><Operation /></el-icon>
                <span>推理参数</span>
              </div>
              <div class="group-content">
                <!-- Temperature Slider -->
                <div class="param-item">
                  <div class="param-header">
                    <label>Temperature</label>
                    <span class="param-value">{{ editForm.temperature.toFixed(2) }}</span>
                  </div>
                  <el-slider 
                    v-model="editForm.temperature" 
                    :min="0" 
                    :max="2" 
                    :step="0.01"
                  />
                  <div class="param-hint">控制生成结果的多样性，越高越随机</div>
                </div>

                <!-- Top P Slider -->
                <div class="param-item">
                  <div class="param-header">
                    <label>Top P</label>
                    <span class="param-value">{{ editForm.topP.toFixed(2) }}</span>
                  </div>
                  <el-slider 
                    v-model="editForm.topP" 
                    :min="0" 
                    :max="1" 
                    :step="0.01"
                  />
                  <div class="param-hint">核采样，限制采样范围</div>
                </div>

                <!-- Thinking Toggle -->
                <div class="thinking-box">
                  <div class="thinking-header">
                    <span class="thinking-title">深度思考</span>
                    <el-switch v-model="editForm.enableThinking" />
                  </div>
                  <el-collapse-transition>
                    <div v-if="editForm.enableThinking" class="thinking-content">
                      <el-form-item label="思考预算 (Tokens)">
                        <el-input-number 
                          v-model="editForm.thinkingBudget" 
                          :min="0" 
                          :max="64000" 
                          :step="1024"
                        />
                      </el-form-item>
                    </div>
                  </el-collapse-transition>
                </div>
              </div>
            </div>

            <!-- System Prompt Section -->
            <div v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent" class="config-group">
              <div class="group-header">
                <el-icon><ChatDotRound /></el-icon>
                <span>人设指令</span>
              </div>
              <div class="group-content">
                <el-form-item label="System Prompt">
                  <el-input 
                    v-model="editForm.systemPrompt" 
                    type="textarea"
                    :rows="6"
                    placeholder="定义智能体的身份、角色和回复风格..."
                  />
                </el-form-item>
              </div>
            </div>

            <!-- Image Generation Section -->
            <div v-if="isImageGenerationAgent" class="config-group">
              <div class="group-header">
                <el-icon><Picture /></el-icon>
                <span>图像生成参数</span>
              </div>
              <div class="group-content">
                <el-form-item label="图像尺寸">
                  <el-select v-model="editForm.imageSize">
                    <el-option label="1:1 (1328x1328)" value="1328x1328" />
                    <el-option label="16:9 (1664x928)" value="1664x928" />
                    <el-option label="9:16 (928x1664)" value="928x1664" />
                    <el-option label="4:3 (1472x1140)" value="1472x1140" />
                    <el-option label="3:4 (1140x1472)" value="1140x1472" />
                  </el-select>
                </el-form-item>

                <div class="param-item">
                  <div class="param-header">
                    <label>引导系数 (CFG)</label>
                    <span class="param-value">{{ editForm.guidanceScale.toFixed(1) }}</span>
                  </div>
                  <el-slider 
                    v-model="editForm.guidanceScale" 
                    :min="1" 
                    :max="20" 
                    :step="0.5"
                  />
                </div>

                <div class="param-item">
                  <div class="param-header">
                    <label>推理步数</label>
                    <span class="param-value">{{ editForm.inferenceSteps }}</span>
                  </div>
                  <el-slider 
                    v-model="editForm.inferenceSteps" 
                    :min="1" 
                    :max="50"
                  />
                </div>

                <el-form-item label="随机种子">
                  <el-input 
                    v-model="editForm.seed" 
                    placeholder="留空则随机生成"
                  >
                    <template #append>
                      <el-button @click="generateRandomSeed">随机</el-button>
                    </template>
                  </el-input>
                </el-form-item>

                <el-form-item label="反向提示词">
                  <el-input 
                    v-model="editForm.negativePrompt" 
                    type="textarea"
                    :rows="3"
                    placeholder="描述不希望在图像中出现的内容..."
                  />
                </el-form-item>
              </div>
            </div>

            <!-- Video Generation Section -->
            <div v-if="isTTVAgent" class="config-group">
              <div class="group-header">
                <el-icon><VideoPlay /></el-icon>
                <span>视频生成参数</span>
              </div>
              <div class="group-content">
                <el-form-item v-if="!editForm.model?.includes('I2V')" label="视频比例">
                  <el-radio-group v-model="editForm.videoSize">
                    <el-radio-button value="16:9">16:9</el-radio-button>
                    <el-radio-button value="9:16">9:16</el-radio-button>
                    <el-radio-button value="1:1">1:1</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="随机种子">
                  <el-input 
                    v-model="editForm.seed" 
                    placeholder="留空则随机"
                  >
                    <template #append>
                      <el-button @click="generateRandomSeed">随机</el-button>
                    </template>
                  </el-input>
                </el-form-item>

                <el-form-item label="反向提示词">
                  <el-input 
                    v-model="editForm.negativePrompt" 
                    type="textarea"
                    :rows="3"
                    placeholder="不希望出现的内容..."
                  />
                </el-form-item>
              </div>
            </div>

            <!-- TTS Section -->
            <div v-if="isTTSAgent" class="config-group">
              <div class="group-header">
                <el-icon><Microphone /></el-icon>
                <span>语音合成配置</span>
              </div>
              <div class="group-content">
                <el-form-item label="音色 (Voice)">
                  <el-select v-model="editForm.voice" placeholder="选择音色">
                    <el-option label="Alex" value="alex" />
                    <el-option label="Anna" value="anna" />
                    <el-option label="Bella" value="bella" />
                    <el-option label="Benjamin" value="benjamin" />
                  </el-select>
                </el-form-item>

                <div class="param-item">
                  <div class="param-header">
                    <label>语速</label>
                    <span class="param-value">{{ editForm.speed.toFixed(1) }}x</span>
                  </div>
                  <el-slider 
                    v-model="editForm.speed" 
                    :min="0.5" 
                    :max="2.0" 
                    :step="0.1"
                  />
                </div>

                <div class="param-item">
                  <div class="param-header">
                    <label>音量增益</label>
                    <span class="param-value">{{ editForm.gain }}dB</span>
                  </div>
                  <el-slider 
                    v-model="editForm.gain" 
                    :min="-10" 
                    :max="10"
                  />
                </div>
              </div>
            </div>
          </el-form>
        </div>

        <!-- Sidebar Footer: Save Actions -->
        <div class="sidebar-footer">
          <el-button @click="handleBack" block>返回列表</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, onBeforeUnmount, reactive, computed, nextTick } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  ChatDotRound, Monitor, Setting, Position, UserFilled,
  VideoPlay, VideoPause, VideoCamera, Refresh, Cpu, ArrowLeft,
  ArrowUp, ArrowDown, Timer, CircleCheck, Tickets,
  Microphone, Picture, Connection, Clock, Close, Service, Plus,
  Menu, ElementPlus as LightningIcon, Headset
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getAgentMetadata, updateAgent, chatAgent } from '@/api/agent';
import { getAgentMetrics, getAgentList as getMonitorAgentList } from '@/api/monitor';
import { getAgentLogs, controlAgent } from '@/api/runtime';
import request from '@/utils/request';
import PageHeader from '@/components/PageHeader.vue';

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

// UI State
const isMounted = ref(false);
const loading = ref(false);
const saveLoading = ref(false);
const showSnapshotPanel = ref(false);
const currentMode = ref('chat');
const selectedSnapshotId = ref(null);

// Mode Options
const modeOptions = [
  { value: 'chat', label: '对话' },
  { value: 'workflow', label: '工作流' },
  { value: 'image', label: '图文' },
  { value: 'tts', label: '语音' },
  { value: 'stt', label: '转写' },
  { value: 'video', label: '视频' }
];

// Data
const agentInfo = ref({});
const metrics = ref({ latency: [], tokens: [] });
const logs = ref([]);
const configSnapshots = ref([]);

const editForm = ref({
  agentId: '',
  name: '',
  model: '',
  temperature: 0.7,
  topP: 0.7,
  systemPrompt: '',
  enableThinking: false,
  thinkingBudget: 4096,
  imageSize: '1328x1328',
  seed: '',
  guidanceScale: 7.5,
  inferenceSteps: 20,
  negativePrompt: '',
  voice: '',
  speed: 1.0,
  gain: 0,
  videoSize: '16:9',
  videoDuration: 6,
  fps: 24
});

// Computed Properties
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

const HasAnySnapshots = computed(() => configSnapshots.value.length > 0);

const hasUnsavedChanges = computed(() => {
  const baseline = {
    name: agentInfo.value.name || '',
    model: agentInfo.value.model || '',
    temperature: agentInfo.value.temperature ?? 0.7,
    topP: agentInfo.value.topP ?? 0.7,
    systemPrompt: agentInfo.value.systemPrompt || '',
    enableThinking: agentInfo.value.enableThinking ?? false,
    thinkingBudget: agentInfo.value.thinkingBudget ?? 4096,
    imageSize: agentInfo.value.imageSize ?? '',
    seed: agentInfo.value.seed ?? '',
    guidanceScale: agentInfo.value.guidanceScale ?? 7.5,
    inferenceSteps: agentInfo.value.inferenceSteps ?? 20,
    negativePrompt: agentInfo.value.negativePrompt ?? '',
    voice: agentInfo.value.voice ?? '',
    speed: agentInfo.value.speed ?? 1.0,
    gain: agentInfo.value.gain ?? 0
  };

  return JSON.stringify(editForm.value) !== JSON.stringify(baseline);
});

const currentParameters = computed(() => {
  if (isTTSAgent.value) {
    return { voice: editForm.value.voice, speed: editForm.value.speed, gain: editForm.value.gain, model: editForm.value.model };
  }
  if (isImageGenerationAgent.value) {
    return {
      imageSize: editForm.value.imageSize,
      seed: editForm.value.seed,
      guidanceScale: editForm.value.guidanceScale,
      inferenceSteps: editForm.value.inferenceSteps,
      negativePrompt: editForm.value.negativePrompt
    };
  }
  if (isTTVAgent.value) {
    return {
      videoSize: editForm.value.videoSize,
      seed: editForm.value.seed,
      negativePrompt: editForm.value.negativePrompt
    };
  }
  return {
    temperature: editForm.value.temperature,
    topP: editForm.value.topP,
    systemPrompt: editForm.value.systemPrompt,
    enableThinking: editForm.value.enableThinking,
    thinkingBudget: editForm.value.thinkingBudget
  };
});

const activeRunner = computed(() => {
  if (currentMode.value === 'tts') return AudioGenerator;
  if (currentMode.value === 'stt') return AudioTranscriber;
  if (currentMode.value === 'image') return ImageGenerator;
  if (currentMode.value === 'video') return VideoGenerator;
  if (currentMode.value === 'workflow') return WorkflowRunner;
  if (currentMode.value === 'completion') return CompletionRunner;
  
  const viewType = (agentInfo.value.viewType || '').toUpperCase();
  if (viewType === 'STT') return AudioTranscriber;
  if (viewType === 'TTS') return AudioGenerator;
  if (viewType === 'TEXT_TO_IMAGE' || viewType === 'IMAGE_TO_IMAGE') return ImageGenerator;
  if (viewType === 'TEXT_TO_VIDEO' || viewType === 'TTV') return VideoGenerator;
  if (viewType === 'WORKFLOW') return WorkflowRunner;
  
  return ChatWindow;
});

// Methods
const fetchData = async () => {
  try {
    loading.value = true;
    const response = await getAgentMetadata(agentId);
    agentInfo.value = response.data || {};
    Object.assign(editForm.value, agentInfo.value);
  } catch (error) {
    ElMessage.error('加载智能体元数据失败');
  } finally {
    loading.value = false;
  }
};

const saveConfig = async () => {
  try {
    saveLoading.value = true;
    await updateAgent(agentId, editForm.value);
    agentInfo.value = { ...editForm.value };
    ElMessage.success('配置已保存');
  } catch (error) {
    ElMessage.error('保存失败');
  } finally {
    saveLoading.value = false;
  }
};

const loadSnapshots = () => {
  const key = `agent-snapshots-${agentId}`;
  const stored = localStorage.getItem(key);
  configSnapshots.value = stored ? JSON.parse(stored) : [];
};

const createSnapshot = () => {
  const snapshot = {
    id: Date.now(),
    timestamp: new Date(),
    label: `快照 ${configSnapshots.value.length + 1}`,
    data: { ...editForm.value }
  };
  
  configSnapshots.value.unshift(snapshot);
  if (configSnapshots.value.length > 5) configSnapshots.value.pop();
  
  const key = `agent-snapshots-${agentId}`;
  localStorage.setItem(key, JSON.stringify(configSnapshots.value));
  ElMessage.success('快照已保存');
};

const applySnapshot = (snapshotId) => {
  const snapshot = configSnapshots.value.find(s => s.id === snapshotId);
  if (snapshot) {
    Object.assign(editForm.value, snapshot.data);
    ElMessage.success('配置已恢复');
  }
};

const formatSnapshotTime = (date) => {
  const d = new Date(date);
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
};

const generateRandomSeed = () => {
  editForm.value.seed = Math.floor(Math.random() * 1000000).toString();
};

const handleBack = () => {
  if (hasUnsavedChanges.value) {
    if (window.confirm('有未保存的更改，确定要离开吗？')) {
      router.push(ROUTES.AGENTS.LIST);
    }
  } else {
    router.push(ROUTES.AGENTS.LIST);
  }
};

const fetchLogs = async () => {
  try {
    const response = await getAgentLogs(agentId);
    logs.value = response.data || [];
  } catch (error) {
    console.error('获取日志失败', error);
  }
};

const handleBeforeUnload = (event) => {
  if (hasUnsavedChanges.value) {
    event.preventDefault();
    event.returnValue = '';
  }
};

onBeforeRouteLeave((_to, _from, next) => {
  if (!hasUnsavedChanges.value) {
    next();
    return;
  }
  if (window.confirm('有未保存的更改，确定要离开吗？')) {
    next();
  } else {
    next(false);
  }
});

let pollTimer;

onMounted(() => {
  isMounted.value = true;
  fetchData();
  loadSnapshots();
  window.addEventListener('beforeunload', handleBeforeUnload);
  pollTimer = setInterval(() => {
    fetchLogs();
  }, 5000);
});

onUnmounted(() => {
  isMounted.value = false;
  window.removeEventListener('beforeunload', handleBeforeUnload);
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
/* Page Layout */
.console-page {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: #f6f8fa;
}

.console-container {
  flex: 1;
  display: flex;
  overflow: hidden;
  gap: 0;
}

/* Left Panel: Runner Area */
.runner-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fafbfc;
}

.mode-group {
  display: flex;
  gap: 4px;
}

.mode-group :deep(.el-radio-button__inner) {
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
}

.mode-bar-actions {
  display: flex;
  gap: 8px;
}

.runner-container {
  flex: 1;
  overflow: auto;
  position: relative;
}

/* Right Panel: Config Sidebar */
.config-sidebar {
  width: 360px;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-left: 1px solid #e5e7eb;
  box-shadow: -2px 0 12px rgba(0, 0, 0, 0.04);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.header-title :deep(.el-icon) {
  font-size: 18px;
  color: #6366f1;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-controls :deep(.el-button.active) {
  color: #6366f1;
  background: #eef2ff;
}

/* Status Bar */
.status-bar {
  padding: 8px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #f0f1f3;
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.status.warning .status-dot {
  background: #f59e0b;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* Sidebar Content */
.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
}

.sidebar-content::-webkit-scrollbar {
  width: 6px;
}

.sidebar-content::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.sidebar-content::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

/* Snapshot Panel */
.snapshot-panel {
  padding: 16px;
  border-bottom: 1px solid #f0f1f3;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #1f2937;
}

.empty-state {
  text-align: center;
  padding: 20px 0;
  color: #9ca3af;
  font-size: 12px;
}

.empty-state :deep(.el-icon) {
  font-size: 24px;
  margin-bottom: 8px;
  opacity: 0.5;
}

.snapshot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.snapshot-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.snapshot-item:hover {
  background: #f3f4f6;
}

.snapshot-time {
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
}

.snapshot-note {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.panel-action {
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}

/* Config Form */
.config-form {
  padding: 0 16px;
}

.config-group {
  margin-bottom: 20px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #f0f1f3;
}

.group-header :deep(.el-icon) {
  font-size: 16px;
  color: #6366f1;
}

.group-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.config-form :deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
}

.config-form :deep(.el-input__wrapper),
.config-form :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  transition: all 0.2s;
}

.config-form :deep(.el-input__wrapper:hover),
.config-form :deep(.el-textarea__inner:hover) {
  border-color: #d1d5db;
}

.config-form :deep(.el-input__wrapper:focus-within) {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

/* Parameter Controls */
.param-item {
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.param-header label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
}

.param-value {
  font-size: 12px;
  font-weight: 700;
  color: #6366f1;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 6px;
}

.param-hint {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 6px;
}

/* Thinking Box */
.thinking-box {
  padding: 12px;
  background: #f0fdf4;
  border: 1px solid #d1fae5;
  border-radius: 8px;
}

.thinking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.thinking-title {
  font-size: 13px;
  font-weight: 600;
  color: #047857;
}

.thinking-content {
  padding-top: 8px;
  border-top: 1px solid #d1fae5;
}

/* Sidebar Footer */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
  background: #fafbfc;
}

/* Utilities */
.mode-label {
  display: inline-block;
}

/* Responsive */
@media (max-width: 1400px) {
  .config-sidebar {
    width: 320px;
  }
}
</style>
