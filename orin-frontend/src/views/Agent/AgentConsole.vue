<template>
  <div class="page-shell">
    <div class="page-ambient ambient-one"></div>
    <div class="page-ambient ambient-two"></div>

    <div class="dashboard-layout">
      <aside class="config-sidebar">
        <div class="sidebar-card">
          <div class="sidebar-header">
            <div class="sidebar-header-top">
              <el-button
                link
                :icon="ArrowLeft"
                @click="$router.push(ROUTES.AGENTS.LIST)"
                class="back-btn"
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
              <div class="settings-title">智能体信息与设置</div>
              <div class="settings-actions">
                <el-button size="small" @click="settingsVisible = false">关闭</el-button>
                <el-button type="primary" size="small" @click="saveConfig" :loading="saveLoading">
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

              <div class="config-section" v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent">
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
                    <el-slider v-model="editForm.temperature" :min="0" :max="2" :step="0.1" />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Top P</span>
                        <span class="param-desc">限制采样范围，收敛输出风格</span>
                      </div>
                      <span class="value-badge">{{ editForm.topP }}</span>
                    </div>
                    <el-slider v-model="editForm.topP" :min="0" :max="1" :step="0.1" />
                  </div>

                  <div class="thinking-card">
                    <div class="thinking-main">
                      <div>
                        <div class="thinking-title">深度思考</div>
                        <div class="thinking-desc">启用推理链输出与预算控制</div>
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

              <div class="config-section" v-if="!isImageGenerationAgent && !isTTSAgent && !isTTVAgent">
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

              <div class="config-section" v-if="isImageGenerationAgent">
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
                    <el-slider v-model="editForm.guidanceScale" :min="1" :max="20" :step="0.5" />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Steps</span>
                        <span class="param-desc">影响生成质量与耗时</span>
                      </div>
                      <span class="value-badge">{{ editForm.inferenceSteps || 20 }}</span>
                    </div>
                    <el-slider v-model="editForm.inferenceSteps" :min="1" :max="50" :step="1" />
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

              <div class="config-section" v-if="isTTVAgent">
                <div class="section-title">
                  <el-icon><VideoCamera /></el-icon>
                  <span>视频生成参数</span>
                </div>
                <div class="section-panel">
                  <el-form-item label="视频比例 (Aspect Ratio)" v-if="!editForm.model?.includes('I2V')">
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

              <div class="config-section" v-if="isTTSAgent">
                <div class="section-title">
                  <el-icon><Microphone /></el-icon>
                  <span>语音合成配置</span>
                </div>
                <div class="section-panel">
                  <el-form-item label="音色 (Voice)">
                    <el-select v-model="editForm.voice" placeholder="请选择音色" filterable allow-create clearable>
                      <el-option label="Alex" value="alex" />
                      <el-option label="Anna" value="anna" />
                      <el-option label="Bella" value="bella" />
                      <el-option label="Benjamin" value="benjamin" />
                      <el-option label="Charles" value="charles" />
                      <el-option label="David" value="david" />
                    </el-select>
                    <div class="helper-text">具体可用音色取决于所选模型能力</div>
                  </el-form-item>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Speed</span>
                        <span class="param-desc">输出语速倍率</span>
                      </div>
                      <span class="value-badge">{{ editForm.speed || 1.0 }}x</span>
                    </div>
                    <el-slider v-model="editForm.speed" :min="0.5" :max="2.0" :step="0.1" />
                  </div>

                  <div class="param-group">
                    <div class="param-header">
                      <div class="param-label-wrap">
                        <span class="param-label">Gain</span>
                        <span class="param-desc">输出音量增益</span>
                      </div>
                      <span class="value-badge">{{ editForm.gain || 0 }} dB</span>
                    </div>
                    <el-slider v-model="editForm.gain" :min="-10" :max="10" :step="1" />
                  </div>
                </div>
              </div>
            </el-form>
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <section class="stage-shell">
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

      <aside class="history-sidebar">
        <div class="history-card">
          <div class="history-header">
            <div>
              <div class="history-kicker">Timeline</div>
              <h3>执行历史</h3>
            </div>
            <el-button link :icon="Refresh" @click="fetchLogs">刷新</el-button>
          </div>

          <div class="history-content">
            <div v-if="loading" class="history-empty">正在加载历史记录...</div>
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
                <el-tag size="small" effect="plain" class="history-tag">{{ log.type || 'LOG' }}</el-tag>
              </div>
              <div class="history-preview">{{ log.content || (log.response ? '[' + log.status + '] ' + log.response?.substring(0, 80) : (log.sessionId ? '会话: ' + log.sessionId : (log.duration ? '耗时: ' + log.duration + 'ms' : '无内容'))) }}</div>
            </button>
          </div>
        </div>
      </aside>
    </div>
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

import UnifiedConversationRunner from './components/UnifiedConversationRunner.vue';

const route = useRoute();
const agentId = route.params.id;

const isMounted = ref(false);
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

const resolveModeFromTab = (tab) => {
  if (!tab) return '';
  const normalized = String(tab).toLowerCase();
  if (normalized === 'image') return 'image';
  if (normalized === 'stt' || normalized === 'audio') return 'stt';
  if (normalized === 'tts') return 'tts';
  if (normalized === 'video') return 'video';
  if (normalized === 'workflow') return 'workflow';
  if (normalized === 'completion') return 'completion';
  if (normalized === 'chat') return 'chat';
  return '';
};

const resolveModeFromViewType = (viewType) => {
  const type = (viewType || '').toUpperCase();
  if (type === 'TEXT_TO_IMAGE' || type === 'IMAGE_TO_IMAGE' || type === 'TTI') return 'image';
  if (type === 'SPEECH_TO_TEXT' || type === 'STT') return 'stt';
  if (type === 'TEXT_TO_SPEECH' || type === 'TTS') return 'tts';
  if (type === 'TEXT_TO_VIDEO' || type === 'VIDEO' || type === 'TTV') return 'video';
  if (type === 'WORKFLOW') return 'workflow';
  return '';
};

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

const activeRunner = computed(() => UnifiedConversationRunner);

const syncCurrentMode = (metaRes) => {
  const fromTab = resolveModeFromTab(route.query.tab);
  const fromMode = metaRes?.mode ? String(metaRes.mode).toLowerCase() : '';
  const fromViewType = resolveModeFromViewType(metaRes?.viewType || agentInfo.value?.viewType);
  currentMode.value = fromTab || fromMode || fromViewType || 'chat';
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

const getModeLabel = (mode) => {
  const map = {
    chat: '对话模式',
    completion: '文本补全',
    workflow: '工作流',
    image: '图像生成',
    tts: '语音合成',
    stt: '语音转写',
    video: '视频生成'
  };
  return map[String(mode || '').toLowerCase()] || '对话模式';
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
.page-shell {
  position: relative;
  min-height: calc(100vh - 64px);
  background:
    radial-gradient(circle at left top, rgba(20, 184, 166, 0.12), transparent 24%),
    radial-gradient(circle at right top, rgba(14, 165, 233, 0.11), transparent 22%),
    linear-gradient(180deg, #f2f8f8 0%, #f6f8fb 48%, #eef2f7 100%);
  overflow: hidden;
}

.page-ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(36px);
  pointer-events: none;
}

.ambient-one {
  width: 320px;
  height: 320px;
  left: -120px;
  top: 160px;
  background: rgba(20, 184, 166, 0.12);
}

.ambient-two {
  width: 260px;
  height: 260px;
  right: -90px;
  top: 80px;
  background: rgba(59, 130, 246, 0.1);
}

.dashboard-layout {
  position: relative;
  z-index: 1;
  height: calc(100vh - 64px);
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr) 300px;
  gap: 20px;
  padding: 20px;
}

.config-sidebar,
.history-sidebar,
.chat-main {
  min-height: 0;
}

.sidebar-card,
.history-card,
.stage-shell {
  height: 100%;
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.82);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
}

.sidebar-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(247, 250, 252, 0.92));
}

.sidebar-header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.back-btn {
  padding: 0;
  font-weight: 600;
}

.settings-trigger {
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
}

.agent-profile {
  display: flex;
  gap: 14px;
  margin-top: 18px;
}

.agent-avatar {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.agent-profile-info h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.2;
  color: #0f172a;
}

.agent-profile-info p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
  word-break: break-word;
}

.identity-tags {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sidebar-context {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  padding-top: 14px;
}

.sidebar-context p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.context-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.context-pill {
  min-width: 0;
  border-radius: 14px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), #f8fafc);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.context-pill.primary {
  background: linear-gradient(135deg, #0f766e, #155e75);
  color: #f8fafc;
  border-color: transparent;
}

.context-pill span {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  opacity: 0.78;
}

.context-pill strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  color: inherit;
}

.agent-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.agent-meta-card {
  min-width: 0;
  border-radius: 14px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), #f8fafc);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.agent-meta-card.accent {
  background: linear-gradient(135deg, #0f766e, #155e75);
  border-color: transparent;
}

.agent-meta-card span {
  display: block;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.agent-meta-card strong {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  color: #0f172a;
  word-break: break-word;
}

.agent-meta-card.accent span,
.agent-meta-card.accent strong {
  color: #f8fafc;
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 18px 26px;
}

.settings-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.settings-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.settings-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.playground-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.section-panel {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  padding: 16px;
}

.section-panel :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.param-group + .param-group {
  margin-top: 18px;
}

.param-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.param-label-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-label {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.param-desc,
.helper-text {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.value-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.thinking-card {
  margin-top: 18px;
  border-radius: 18px;
  padding: 14px;
  background: #f7fbfb;
  border: 1px solid rgba(20, 184, 166, 0.14);
}

.thinking-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.thinking-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.thinking-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.thinking-extra {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: #475569;
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.template-select {
  width: 128px;
}

.stage-shell {
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: hidden;
}

.stage-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.runner-frame {
  flex: 1;
  min-height: 0;
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.8);
  background: rgba(255, 255, 255, 0.6);
}

.runner-frame :deep(.unified-runner) {
  height: 100%;
}

.history-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 18px;
}

.history-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.history-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}

.history-header h3 {
  margin: 6px 0 0;
  font-size: 24px;
  color: #0f172a;
}

.history-content {
  flex: 1;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-empty {
  border-radius: 18px;
  padding: 18px;
  background: rgba(248, 250, 252, 0.8);
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.history-item {
  width: 100%;
  text-align: left;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  padding: 14px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.history-item:hover {
  transform: translateY(-2px);
  border-color: rgba(15, 118, 110, 0.28);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.history-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-time {
  font-size: 12px;
  color: #94a3b8;
}

.history-preview {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sidebar-body::-webkit-scrollbar,
.history-content::-webkit-scrollbar,
.sidebar-body::-webkit-scrollbar-thumb,
.history-content::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.55);
  border-radius: 999px;
}

@media (max-width: 1440px) {
  .dashboard-layout {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .history-sidebar {
    display: none;
  }
}

@media (max-width: 1080px) {
  .dashboard-layout {
    height: auto;
    min-height: calc(100vh - 64px);
    grid-template-columns: 1fr;
  }

  .config-sidebar {
    order: 2;
  }

  .chat-main {
    order: 1;
  }
}

@media (max-width: 768px) {
  .dashboard-layout {
    padding: 14px;
    gap: 14px;
  }

  .stage-shell,
  .sidebar-card {
    border-radius: 22px;
  }

  .context-metrics {
    grid-template-columns: 1fr;
  }

  .agent-meta-grid {
    grid-template-columns: 1fr;
  }

}
</style>
