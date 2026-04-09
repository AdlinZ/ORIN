<template>
  <div class="agent-workspace" ref="containerRef" :class="{ 'is-wide': isWide, 'is-medium': isMedium, 'is-narrow': isNarrow }">
    <div v-if="isLeftDrawer && !leftPaneCollapsed" class="d-overlay" @click="leftPaneCollapsed = true"></div>
      <aside class="workspace-sidebar" :class="{ 'is-drawer': isLeftDrawer, 'is-collapsed': leftPaneCollapsed }">
        <div class="sidebar-card">
          <div class="sidebar-header">
            <div class="sidebar-header-top">
              <el-button
                link
                :icon="ArrowLeft"
                class="back-btn"
                @click="$router.push(ROUTES.AGENTS.LIST)"
              >
                返回智能体列表
              </el-button>
              <el-button
                circle
                :icon="Setting"
                class="settings-trigger"
                @click="settingsVisible = !settingsVisible"
              />
            </div>

            <div class="agent-profile">
              <div class="agent-avatar" :style="{ background: currentAccent.soft, color: currentAccent.strong }">
                <el-icon><component :is="getViewIcon(agentInfo.viewType)" /></el-icon>
              </div>
              <div class="agent-profile-info">
                <h2>{{ editForm.name || agentInfo.agentName || '智能体控制台' }}</h2>
                <p>{{ agentInfo.modelName || editForm.model || '未识别模型信息' }}</p>
              </div>
            </div>

            <div class="identity-tags">
              <el-tag size="small" effect="plain" :type="getViewTagType(agentInfo.viewType)">
                {{ getViewLabel(agentInfo.viewType) }}
              </el-tag>
              <el-tag size="small" effect="plain" :type="getProviderTagType(agentInfo.providerType)">
                {{ agentInfo.providerType || '本地运行' }}
              </el-tag>
              <el-tag size="small" effect="plain">
                ID: {{ agentId }}
              </el-tag>
            </div>

            <div v-if="!settingsVisible" class="sidebar-context">
              <div class="stage-kicker">
                <el-icon><component :is="getProviderIcon(agentInfo.providerType)" /></el-icon>
                <span>{{ agentInfo.providerType || 'ORIN Runtime' }}</span>
              </div>
              <p>当前为 {{ getModeLabel(currentMode) }}，该页面固定当前智能体能力，不提供模式切换。</p>
              <div class="context-metrics">
                <div class="context-pill primary">
                  <span>状态</span>
                  <strong>{{ runtimeStatusLabel }}</strong>
                </div>
                <div class="context-pill">
                  <span>Prompt 模板</span>
                  <strong>{{ promptTemplates.length }}</strong>
                </div>
              </div>
              <div class="agent-meta-grid">
                <div class="agent-meta-card accent">
                  <span>最近日志</span>
                  <strong>{{ latestLogTime }}</strong>
                </div>
                <div class="agent-meta-card">
                  <span>模式</span>
                  <strong>{{ getModeLabel(currentMode) }}</strong>
                </div>
                <div class="agent-meta-card">
                  <span>模型</span>
                  <strong>{{ editForm.model || agentInfo.modelName || '未设置' }}</strong>
                </div>
                <div class="agent-meta-card">
                  <span>能力类型</span>
                  <strong>{{ getViewLabel(agentInfo.viewType || currentMode) }}</strong>
                </div>
                <div class="agent-meta-card">
                  <span>提供方</span>
                  <strong>{{ agentInfo.providerType || 'Local' }}</strong>
                </div>
              </div>
            </div>
          </div>

          <div v-if="settingsVisible" class="sidebar-body">
            <div class="settings-panel-head">
              <div class="settings-title">
                智能体信息与设置
              </div>
              <div class="settings-actions">
                <el-button size="small" @click="settingsVisible = false">
                  关闭
                </el-button>
                <el-button
                  type="primary"
                  size="small"
                  :loading="saveLoading"
                  @click="saveConfig"
                >
                  保存
                </el-button>
              </div>
            </div>

            <el-form :model="editForm" label-position="top" class="playground-form">
              <div class="config-section">
                <div class="section-title">
                  <el-icon><Menu /></el-icon>
                  <span>基础设置</span>
                </div>
                <div class="section-panel">
                  <el-form-item label="名称">
                    <el-input v-model="editForm.name" placeholder="设置智能体名称" />
                  </el-form-item>
                  <el-form-item label="模型">
                    <el-input v-model="editForm.model" placeholder="核心模型架构" />
                  </el-form-item>
                </div>
              </div>

              <div v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent" class="config-section">
                <div class="section-title">
                  <el-icon><Operation /></el-icon>
                  <span>推理参数</span>
                </div>
                <div class="section-panel">
                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Temperature</span>
                        <span class="param-desc">控制回复随机性和创造力</span>
                      </div>
                      <span class="value-badge">{{ editForm.temperature }}</span>
                    </div>
                    <el-slider
                      v-model="editForm.temperature"
                      :min="0"
                      :max="2"
                      :step="0.1"
                    />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Top P</span>
                        <span class="param-desc">限制采样范围，收敛输出风格</span>
                      </div>
                      <span class="value-badge">{{ editForm.topP }}</span>
                    </div>
                    <el-slider
                      v-model="editForm.topP"
                      :min="0"
                      :max="1"
                      :step="0.1"
                    />
                  </div>

                  <div class="thinking-card">
                    <div class="thinking-main">
                      <div>
                        <div class="thinking-title">
                          深度思考
                        </div>
                        <div class="thinking-desc">
                          启用推理链输出与预算控制
                        </div>
                      </div>
                      <el-switch v-model="editForm.enableThinking" />
                    </div>
                    <div v-if="editForm.enableThinking" class="thinking-extra">
                      <span>思考预算 Tokens</span>
                      <el-input-number
                        v-model="editForm.thinkingBudget"
                        :min="0"
                        :max="64000"
                        :step="1024"
                        controls-position="right"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent" class="config-section">
                <div class="section-title">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>人设指令</span>
                </div>
                <div class="section-panel">
                  <div class="prompt-toolbar">
                    <span>System Prompt</span>
                    <el-select
                      v-model="selectedPromptTemplate"
                      placeholder="模板"
                      size="small"
                      class="template-select"
                      @change="applyPromptTemplate"
                    >
                      <el-option
                        v-for="t in promptTemplates"
                        :key="t.id"
                        :label="t.name"
                        :value="t.content"
                      />
                    </el-select>
                  </div>
                  <el-input
                    v-model="editForm.systemPrompt"
                    type="textarea"
                    :rows="8"
                    placeholder="定义智能体的身份、回复风格和约束条件..."
                  />
                </div>
              </div>

              <div v-if="isImageGenerationAgent" class="config-section">
                <div class="section-title">
                  <el-icon><Picture /></el-icon>
                  <span>图像生成参数</span>
                </div>
                <div class="section-panel">
                  <el-form-item label="图像尺寸 (Image Size)">
                    <el-select v-model="editForm.imageSize">
                      <el-option label="正方形 1:1 (1328x1328)" value="1328x1328" />
                      <el-option label="横屏 16:9 (1664x928)" value="1664x928" />
                      <el-option label="竖屏 9:16 (928x1664)" value="928x1664" />
                      <el-option label="标准 4:3 (1472x1140)" value="1472x1140" />
                      <el-option label="标准 3:4 (1140x1472)" value="1140x1472" />
                      <el-option label="经典 3:2 (1584x1056)" value="1584x1056" />
                      <el-option label="经典 2:3 (1056x1584)" value="1584x1056" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="随机种子 (Seed)">
                    <el-input v-model="editForm.seed" placeholder="留空则随机生成">
                      <template #append>
                        <el-button :icon="Refresh" @click="generateRandomSeed" />
                      </template>
                    </el-input>
                  </el-form-item>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">CFG Scale</span>
                        <span class="param-desc">提示词约束强度</span>
                      </div>
                      <span class="value-badge">{{ editForm.guidanceScale || 7.5 }}</span>
                    </div>
                    <el-slider
                      v-model="editForm.guidanceScale"
                      :min="1"
                      :max="20"
                      :step="0.5"
                    />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Steps</span>
                        <span class="param-desc">影响生成质量与耗时</span>
                      </div>
                      <span class="value-badge">{{ editForm.inferenceSteps || 20 }}</span>
                    </div>
                    <el-slider
                      v-model="editForm.inferenceSteps"
                      :min="1"
                      :max="50"
                      :step="1"
                    />
                  </div>

                  <el-form-item label="反向提示词 (Negative Prompt)">
                    <el-input
                      v-model="editForm.negativePrompt"
                      type="textarea"
                      :rows="3"
                      placeholder="描述你不希望出现在图像中的元素..."
                    />
                  </el-form-item>
                </div>
              </div>

              <div v-if="isTTVAgent" class="config-section">
                <div class="section-title">
                  <el-icon><VideoCamera /></el-icon>
                  <span>视频生成参数</span>
                </div>
                <div class="section-panel">
                  <el-form-item v-if="!editForm.model?.includes('I2V')" label="视频比例 (Aspect Ratio)">
                    <el-radio-group v-model="editForm.videoSize" size="small">
                      <el-radio-button value="16:9" label="16:9" />
                      <el-radio-button value="9:16" label="9:16" />
                      <el-radio-button value="1:1" label="1:1" />
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item label="随机种子 (Seed)">
                    <el-input v-model="editForm.seed" placeholder="留空则随机">
                      <template #append>
                        <el-button :icon="Refresh" @click="generateRandomSeed" />
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="反向提示词 (Negative Prompt)">
                    <el-input
                      v-model="editForm.negativePrompt"
                      type="textarea"
                      :rows="2"
                      placeholder="描述不希望出现的内容..."
                    />
                  </el-form-item>
                </div>
              </div>

              <div v-if="isTTSAgent" class="config-section">
                <div class="section-title">
                  <el-icon><Microphone /></el-icon>
                  <span>语音合成配置</span>
                </div>
                <div class="section-panel">
                  <el-form-item label="音色 (Voice)">
                    <el-select
                      v-model="editForm.voice"
                      placeholder="请选择音色"
                      filterable
                      allow-create
                      clearable
                    >
                      <el-option label="Alex" value="alex" />
                      <el-option label="Anna" value="anna" />
                      <el-option label="Bella" value="bella" />
                      <el-option label="Benjamin" value="benjamin" />
                      <el-option label="Charles" value="charles" />
                      <el-option label="David" value="david" />
                    </el-select>
                    <div class="helper-text">
                      具体可用音色取决于所选模型能力
                    </div>
                  </el-form-item>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Speed</span>
                        <span class="param-desc">输出语速倍率</span>
                      </div>
                      <span class="value-badge">{{ editForm.speed || 1.0 }}x</span>
                    </div>
                    <el-slider
                      v-model="editForm.speed"
                      :min="0.5"
                      :max="2.0"
                      :step="0.1"
                    />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Gain</span>
                        <span class="param-desc">输出音量增益</span>
                      </div>
                      <span class="value-badge">{{ editForm.gain || 0 }} dB</span>
                    </div>
                    <el-slider
                      v-model="editForm.gain"
                      :min="-10"
                      :max="10"
                      :step="1"
                    />
                  </div>
                </div>
              </div>
            </el-form>
          </div>
        </div>
      </aside>

      <main class="workspace-main">
        <section class="stage-shell">
          <InteractionTopBar
            :chips="consoleTopChips"
            :settings-open="!rightPaneCollapsed"
            settings-label="配置"
            @chip-click="handleConsoleChipClick"
            @toggle-settings="rightPaneCollapsed = !rightPaneCollapsed"
          />
          <div class="runner-frame">
            <component
              :is="activeRunner"
              :agent-id="agentId"
              :agent-info="agentInfo"
              :parameters="currentParameters"
              :mode="currentMode"
            />
          </div>
        </section>
      </main>

      <div v-if="isRightDrawer && !rightPaneCollapsed" class="d-overlay" @click="rightPaneCollapsed = true"></div>
    <aside class="workspace-config" :class="{ 'is-drawer': isRightDrawer, 'is-collapsed': rightPaneCollapsed }">
        <div class="history-card">
          <div class="history-header">
            <div>
              <div class="history-kicker">
                Timeline
              </div>
              <h3>执行历史</h3>
            </div>
            <el-button link :icon="Refresh" @click="fetchLogs">
              刷新
            </el-button>
          </div>

          <div class="history-content">
            <div v-if="loading" class="history-empty">
              正在加载历史记录...
            </div>
            <div v-else-if="!logs || logs.length === 0" class="history-empty">
              暂无历史记录，发起一次对话或生成任务后这里会出现时间线。
            </div>
            <button
              v-for="(log, idx) in logs"
              :key="idx"
              type="button"
              class="history-item"
              @click="restoreHistory(log)"
            >
              <div class="history-item-top">
                <span class="history-time">{{ formatTime(log.timestamp) }}</span>
                <el-tag size="small" effect="plain" class="history-tag">
                  {{ log.type || 'LOG' }}
                </el-tag>
              </div>
              <div class="history-preview">
                {{ log.content || (log.response ? '[' + log.status + '] ' + log.response?.substring(0, 80) : (log.sessionId ? '会话: ' + log.sessionId : (log.duration ? '耗时: ' + log.duration + 'ms' : '无内容'))) }}
              </div>
            </button>
          </div>
        </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, onBeforeUnmount, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ROUTES } from '@/router/routes';
import {
  ChatDotRound, VideoCamera, Refresh, Cpu, ArrowLeft,
  Microphone, Picture, Connection, Service,
  Menu, ElementPlus as LightningIcon, Headset, Setting
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getAgentMetadata, updateAgent } from '@/api/agent';
import { getAgentList as getMonitorAgentList } from '@/api/monitor';
import { getAgentLogs } from '@/api/runtime';
import request from '@/utils/request';
import { useInteractionShell } from '@/composables/useInteractionShell';
import { runQuickChipAction } from '@/composables/useInteractionQuickChips';
import { buildConsoleTopChips } from '@/composables/useInteractionChipRegistry';
import {
  getModeLabel,
  isMode,
  isViewTypeInMode,
  resolveModeFromMeta,
  resolveModeFromTab
} from '@/composables/useInteractionModeRegistry';
import InteractionTopBar from '@/components/orin/InteractionTopBar.vue';

import UnifiedConversationRunner from './components/UnifiedConversationRunner.vue';

const route = useRoute();
const agentId = route.params.id;

const isMounted = ref(false);
const {
  containerRef,
  isWide,
  isMedium,
  isNarrow,
  isLeftDrawer,
  isRightDrawer
} = useInteractionShell({
  leftDrawerMode: 'narrow',
  rightDrawerMode: 'always'
});

const leftPaneCollapsed = ref(false);
const rightPaneCollapsed = ref(true);

onMounted(() => {
  isMounted.value = true;
});


const loading = ref(false);
const saveLoading = ref(false);
const settingsVisible = ref(false);

const agentInfo = ref({});
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
  imageSize: '1328x1328',
  seed: '',
  guidanceScale: 7.5,
  inferenceSteps: 20,
  negativePrompt: '',
  voice: '',
  speed: 1.0,
  gain: 0,
  videoSize: '16:9',
  videoDuration: '5',
  fps: 30,
  useReferenceImage: false
});
const promptTemplates = ref([]);
const selectedPromptTemplate = ref('');

const currentMode = ref(resolveModeFromTab(route.query.tab) || 'chat');

const currentParameters = computed(() => {
  if (isImageGenerationAgent.value) {
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
  return {
    temperature: editForm.value.temperature,
    topP: editForm.value.topP,
    systemPrompt: editForm.value.systemPrompt,
    enableThinking: editForm.value.enableThinking,
    thinkingBudget: editForm.value.thinkingBudget
  };
});

const isImageGenerationAgent = computed(() => {
  return isMode(currentMode.value, 'image') || isViewTypeInMode(agentInfo.value.viewType, 'image');
});

const isTTSAgent = computed(() => {
  return isMode(currentMode.value, 'tts') || isViewTypeInMode(agentInfo.value.viewType, 'tts');
});

const isTTVAgent = computed(() => {
  return isMode(currentMode.value, 'video') || isViewTypeInMode(agentInfo.value.viewType, 'video');
});

const activeRunner = computed(() => UnifiedConversationRunner);

const syncCurrentMode = (metaRes) => {
  currentMode.value = resolveModeFromMeta({
    tab: route.query.tab,
    metaMode: metaRes?.mode,
    viewType: metaRes?.viewType || agentInfo.value?.viewType,
    fallback: 'chat'
  });
};

const currentAccent = computed(() => {
  const type = (agentInfo.value.viewType || currentMode.value || '').toUpperCase();
  if (type === 'WORKFLOW') return { soft: 'rgba(14, 165, 233, 0.14)', strong: '#0369a1' };
  if (type.includes('TTS') || type.includes('TEXT_TO_SPEECH')) return { soft: 'rgba(217, 70, 239, 0.14)', strong: '#a21caf' };
  if (type.includes('STT') || type.includes('SPEECH_TO_TEXT')) return { soft: 'rgba(34, 197, 94, 0.14)', strong: '#15803d' };
  if (type.includes('TTI') || type.includes('IMAGE')) return { soft: 'rgba(249, 115, 22, 0.14)', strong: '#c2410c' };
  if (type.includes('TTV') || type.includes('VIDEO')) return { soft: 'rgba(239, 68, 68, 0.14)', strong: '#b91c1c' };
  return { soft: 'rgba(20, 184, 166, 0.14)', strong: '#0f766e' };
});

const runtimeStatusLabel = computed(() => {
  if (loading.value) return '加载中';
  return agentInfo.value.status || '已就绪';
});

const consoleTopChips = computed(() => buildConsoleTopChips(currentMode.value, {
  modeLabel: getModeLabel(currentMode.value),
  runtimeStatus: runtimeStatusLabel.value,
  promptTemplatesCount: promptTemplates.value.length,
  parameters: currentParameters.value
}));

const handleConsoleChipClick = (chip) => {
  runQuickChipAction(chip, {
    openInspector: () => {
      rightPaneCollapsed.value = false;
    },
    toggleInspector: () => {
      rightPaneCollapsed.value = !rightPaneCollapsed.value;
    }
  });
};

const latestLogTime = computed(() => {
  if (!logs.value.length) return '暂无';
  return formatTime(logs.value[0]?.timestamp);
});

let pollTimer = null;

const fetchData = async () => {
  loading.value = true;
  try {
    const listRes = await getMonitorAgentList();
    const list = listRes?.data || listRes || [];
    const current = list.find((a) => a.agentId === agentId || a.id === agentId);
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
        } catch (e) {
          console.warn('Failed to parse extra parameters');
        }
      }

      editForm.value = {
        agentId,
        name: metaRes.name || metaRes.agentName || '',
        model: metaRes.modelName || '',
        temperature: metaRes.temperature || 0.7,
        topP: metaRes.topP || 0.7,
        systemPrompt: metaRes.systemPrompt || '',
        enableThinking: metaRes.enableThinking || false,
        thinkingBudget: metaRes.thinkingBudget || 4096,
        imageSize: extraParams.imageSize || metaRes.imageSize || '1328x1328',
        seed: extraParams.seed || metaRes.seed || '',
        guidanceScale: extraParams.guidanceScale || metaRes.guidanceScale || 7.5,
        inferenceSteps: extraParams.inferenceSteps || metaRes.inferenceSteps || 20,
        negativePrompt: extraParams.negativePrompt || metaRes.negativePrompt || '',
        voice: extraParams.voice || '',
        speed: extraParams.speed !== undefined ? extraParams.speed : 1.0,
        gain: extraParams.gain !== undefined ? extraParams.gain : 0,
        videoSize: extraParams.videoSize || metaRes.videoSize || '16:9',
        videoDuration: extraParams.videoDuration || metaRes.videoDuration || '5',
        fps: extraParams.fps || metaRes.fps || 30
      };

      syncCurrentMode(metaRes);

      if (metaRes.viewType) {
        agentInfo.value = {
          ...agentInfo.value,
          viewType: metaRes.viewType,
          providerType: metaRes.providerType,
          agentName: metaRes.name || metaRes.agentName,
          modelName: metaRes.modelName
        };
      }
    }
    promptTemplates.value = promptRes?.data || promptRes || [];
    fetchLogs();
  } catch (error) {
    ElMessage.error('加载智能体数据失败');
  } finally {
    loading.value = false;
  }
};

const fetchLogs = async () => {
  try {
    const res = await getAgentLogs(agentId);
    logs.value = res?.data || res || [];
  } catch (e) {
    console.warn('Logs error');
  }
};

const saveConfig = async () => {
  saveLoading.value = true;
  try {
    await updateAgent(agentId, editForm.value);
    ElMessage.success('配置已保存');
    const metaRes = await getAgentMetadata(agentId);
    if (metaRes) {
      agentInfo.value.agentName = metaRes.name || metaRes.agentName;
      agentInfo.value.modelName = metaRes.modelName;
      agentInfo.value.viewType = metaRes.viewType || agentInfo.value.viewType;
      agentInfo.value.providerType = metaRes.providerType || agentInfo.value.providerType;
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
  editForm.value.seed = Math.floor(Math.random() * 10000000000).toString();
};

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
  if (type === 'COMPLETION') return '文本补全';
  return type;
};

const restoreHistory = (log) => {
  console.log('历史记录详情:', log);
  const content = log.content || (log.response ? '[' + log.status + '] ' + log.response.substring(0, 200) : (log.sessionId ? '会话: ' + log.sessionId : null));
  ElMessage.info(`历史记录: ${content || '无内容'}`);
};

watch(
  () => route.query.tab,
  () => {
    syncCurrentMode(agentInfo.value);
  }
);

watch(
  () => agentInfo.value.viewType,
  () => {
    syncCurrentMode(agentInfo.value);
  }
);

onMounted(() => {
  fetchData();
  pollTimer = setInterval(() => {
    if (isMounted.value) fetchLogs();
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
/* =========================================================================
   AgentConsole - Redesign (Glassmorphism, Hierarchy, Fluid Layout)
   ========================================================================= */

.agent-workspace {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  background-color: #f8fafc;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}
.agent-workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 10% 40%, rgba(79, 70, 229, 0.04) 0%, transparent 50%),
              radial-gradient(circle at 90% 60%, rgba(139, 92, 246, 0.04) 0%, transparent 50%);
  z-index: 0;
  pointer-events: none;
}

.d-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.2);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  z-index: 90;
  animation: fadeIn 0.2s ease-out forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.workspace-sidebar,
.workspace-config {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow-y: auto;
  overflow-x: hidden;
  flex-shrink: 0;
}

.workspace-sidebar {
  width: 320px;
  border-right: 1px solid rgba(226, 232, 240, 0.8);
}
.workspace-config {
  width: 0;
  pointer-events: none;
  border-left: 1px solid rgba(226, 232, 240, 0.8);
}

.workspace-sidebar.is-drawer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  width: 320px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 8px 0 32px rgba(0,0,0,0.06);
  transform: translateX(-100%);
}
.workspace-sidebar.is-drawer:not(.is-collapsed) {
  transform: translateX(0);
}

.workspace-config.is-drawer {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  width: 320px;
  display: flex;
  pointer-events: auto;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: -8px 0 32px rgba(0,0,0,0.06);
  transform: translateX(100%);
}
.workspace-config.is-drawer:not(.is-collapsed) {
  transform: translateX(0);
}

.workspace-sidebar.is-collapsed:not(.is-drawer),
.workspace-config.is-collapsed:not(.is-drawer) {
  width: 0px;
}

.workspace-sidebar::-webkit-scrollbar,
.workspace-config::-webkit-scrollbar {
  width: 4px;
}
.workspace-sidebar::-webkit-scrollbar-thumb,
.workspace-config::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.6);
  border-radius: 4px;
}

.workspace-main {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: transparent;
}

/* Internal Components specific to Console */
.sidebar-card, .history-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
}

.sidebar-header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.back-btn { font-weight: 600; color: #475569; }
.back-btn:hover { color: #3b82f6; }

.agent-profile {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.agent-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.agent-profile-info h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.agent-profile-info p {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}

.identity-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.sidebar-context p {
  font-size: 12px;
  color: #64748b;
  margin: 12px 0 20px;
  line-height: 1.5;
}

.context-metrics {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.context-pill {
  flex: 1;
  background: rgba(241, 245, 249, 0.8);
  padding: 12px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.context-pill.primary {
  background: rgba(239, 246, 255, 0.8);
}
.context-pill span { font-size: 11px; color: #64748b; }
.context-pill strong { font-size: 14px; color: #0f172a; font-weight: 700; }

.agent-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.agent-meta-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
}
.agent-meta-card span { font-size: 11px; color: #64748b; display: block; margin-bottom: 4px; }
.agent-meta-card strong { font-size: 13px; color: #1e293b; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* Settings Form */
.settings-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.settings-title { font-size: 16px; font-weight: 700; color: #0f172a; }

.config-section { margin-bottom: 24px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 12px;
}
.section-panel {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px;
}
.section-panel :deep(.el-form-item__label) {
  font-weight: 600;
  color: #475569;
}
.section-panel :deep(.el-input__wrapper),
.section-panel :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.6);
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.param-group { margin-bottom: 16px; }
.param-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.param-label { font-size: 13px; font-weight: 600; color: #334155; display: block; }
.param-desc { font-size: 11px; color: #94a3b8; }
.value-badge {
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

/* History Timeline */
.history-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}
.history-kicker { font-size: 12px; font-weight: 600; color: #3b82f6; text-transform: uppercase; margin-bottom: 4px; }
.history-header h3 { margin: 0; font-size: 18px; font-weight: 700; color: #0f172a; }

.history-item {
  width: 100%;
  text-align: left;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #e2e8f0;
  padding: 14px;
  border-radius: 12px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
}
.history-item:hover {
  background: #ffffff;
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transform: translateY(-1px);
}
.history-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.history-time { font-size: 11px; color: #94a3b8; }
.history-preview { font-size: 13px; color: #334155; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

/* Runner shell wrapper */
.stage-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.runner-frame {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
