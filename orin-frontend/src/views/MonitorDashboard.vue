<template>
  <div class="page-container">
    <!-- Header / Global Controls -->
    <PageHeader 
      title="实时性能监控" 
      icon="Monitor"
    >
      <template #tag-content>
        <el-tag 
          :type="systemStatus.type" 
          effect="plain"
          size="small"
        >
          <el-icon style="margin-right: 4px;">
            <component :is="systemStatus.icon" />
          </el-icon>
          {{ systemStatus.text }}
        </el-tag>
      </template>
      <template #actions>
        <el-button :icon="Setting" class="action-button" @click="showCardConfig = true">
          自定义面板
        </el-button>
        <el-divider direction="vertical" />
        <span class="text-secondary">自动刷新</span>
        <el-switch v-model="autoRefresh" active-color="var(--primary-color)" @change="handleRefreshToggle" />
      </template>
    </PageHeader>

    <!-- Card Configuration Drawer -->
    <DashboardCardConfig 
      ref="cardConfigRef" 
      v-model="showCardConfig"
      @config-change="handleCardConfigChange"
    />


    <!-- Stats Row -->
    <el-skeleton
      v-if="['stat-agents', 'stat-requests', 'stat-tokens', 'stat-latency'].some(id => isCardEnabled(id))"
      :loading="loading"
      animated
      :rows="2"
    >
      <template #template>
        <el-row :gutter="20" class="stats-row">
          <el-col v-for="i in 4" :key="i" :span="6">
            <el-skeleton-item variant="rect" style="height: 110px; border-radius: var(--radius-base)" />
          </el-col>
        </el-row>
      </template>
      <el-row :gutter="20" class="stats-row">
        <el-col
          v-for="(item, index) in statItems"
          v-show="isCardEnabled(item.cardId)"
          :key="index"
          :span="6"
        >
          <el-card 
            shadow="hover" 
            class="stat-card" 
            :class="{ 'clickable': item.clickable }"
            :body-style="{ padding: '24px' }"
            @click="handleCardClick(item)"
          >
            <div class="stat-card-inner">
              <div class="stat-icon" :style="{ backgroundColor: item.bgColor || 'rgba(var(--orin-primary-rgb), 0.1)' }">
                <el-icon :style="{ color: item.color || 'var(--orin-primary)' }">
                  <component :is="item.icon" />
                </el-icon>
              </div>
              <div class="stat-content">
                <div class="text-secondary" style="margin-bottom: 8px; font-weight: 500;">
                  {{ item.label }}
                </div>
                <div class="stat-value" style="font-family: var(--font-heading); font-size: 24px; font-weight: 700; color: var(--neutral-gray-900);">
                  {{ summary[item.key] || item.defaultValue }}
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-skeleton>

    <!-- 链路追踪查询面板 -->
    <el-card class="trace-query-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><Connection /></el-icon>
            调用链查询
          </span>
          <el-button text @click="querySuccessRate">
            <el-icon><Refresh /></el-icon> 刷新成功率
          </el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="8">
          <div class="trace-input-row">
            <el-input
              v-model="traceIdInput"
              placeholder="输入 Trace ID 查询链路"
              clearable
              @keyup.enter="queryTrace"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :loading="traceQueryLoading" @click="queryTrace">
              查询
            </el-button>
          </div>
        </el-col>
        <el-col :span="8">
          <div v-if="successRateData" class="success-rate-display">
            <span class="label">调用成功率:</span>
            <span class="value" :class="successRateData.successRate >= 90 ? 'success' : successRateData.successRate >= 60 ? 'warning' : 'danger'">
              {{ successRateData.successRate?.toFixed(2) }}%
            </span>
            <span class="detail">({{ successRateData.successCalls }}/{{ successRateData.totalCalls }})</span>
          </div>
          <div v-else class="placeholder-text">
            点击"刷新成功率"获取数据
          </div>
        </el-col>
        <el-col :span="8">
          <div v-if="errorDistributionData.length > 0" class="error-dist-display">
            <span class="label">错误分布:</span>
            <el-tag
              v-for="item in errorDistributionData.slice(0, 3)"
              :key="item.errorMessage"
              size="small"
              type="danger"
              class="error-tag"
            >
              {{ item.errorMessage?.substring(0, 20) }}... ({{ item.count }})
            </el-tag>
          </div>
          <div v-else class="placeholder-text">
            暂无错误
          </div>
        </el-col>
      </el-row>

      <!-- 链路详情 -->
      <el-divider v-if="traceResult" />
      <div v-if="traceResult && traceResult.status === 'found'" class="trace-result">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="Trace ID">
            {{ traceResult.traceId }}
          </el-descriptions-item>
          <el-descriptions-item label="总节点数">
            {{ traceResult.totalSpans }}
          </el-descriptions-item>
          <el-descriptions-item label="总耗时">
            {{ traceResult.totalDurationMs }}ms
          </el-descriptions-item>
          <el-descriptions-item label="成功率">
            <el-tag :type="traceResult.successRate >= 90 ? 'success' : traceResult.successRate >= 60 ? 'warning' : 'danger'">
              {{ traceResult.successRate?.toFixed(2) }}%
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="traceResult.spans" style="margin-top: 16px; width: 100%" max-height="300">
          <el-table-column
            prop="nodeId"
            label="节点ID"
            width="150"
            show-overflow-tooltip
          />
          <el-table-column prop="nodeType" label="节点类型" width="120" />
          <el-table-column prop="endpoint" label="端点" show-overflow-tooltip />
          <el-table-column prop="model" label="模型" width="120" />
          <el-table-column prop="duration" label="耗时(ms)" width="100">
            <template #default="{ row }">
              {{ row.duration || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="statusCode" label="状态码" width="80" />
          <el-table-column prop="timestamp" label="时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.timestamp) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-else-if="traceResult && traceResult.status === 'not_found'" class="trace-not-found">
        <el-empty description="未找到该 Trace ID 的记录" :image-size="60" />
      </div>
    </el-card>

    <!-- Langfuse 可观测性面板 -->
    <el-card v-if="langfuseStatus.enabled || langfuseStatus.configured" class="langfuse-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><DataLine /></el-icon>
            LLM 链路追踪 (Langfuse)
          </span>
          <el-tag :type="langfuseStatus.enabled ? 'success' : 'info'" size="small">
            {{ langfuseStatus.enabled ? '已启用' : '未启用' }}
          </el-tag>
        </div>
      </template>
      <div class="langfuse-content">
        <div v-if="langfuseStatus.enabled && langfuseStatus.link" class="langfuse-info">
          <p class="text-secondary">
            Langfuse 已配置并启用，可查看 LLM 调用链路详情
          </p>
          <el-button type="primary" @click="openLangfuse">
            <el-icon><Link /></el-icon>
            打开 Langfuse Dashboard
          </el-button>
        </div>
        <div v-else-if="langfuseStatus.configured && !langfuseStatus.enabled" class="langfuse-info">
          <p class="text-secondary">
            Langfuse 已配置但未启用，请在配置文件中启用
          </p>
        </div>
        <div v-else class="langfuse-info">
          <p class="text-secondary">
            请配置 Langfuse 以启用 LLM 链路追踪
          </p>
        </div>
      </div>
    </el-card>

    <!-- Main Content Area -->
    <el-row :gutter="24" class="content-row">
      <!-- Left: Agent Grid -->
      <el-col v-if="isCardEnabled('module-agents')" :lg="getModuleSize('module-agents', 17)" :md="24">
        <el-card class="grid-card">
          <template #header>
            <div class="card-header">
              <span class="module-title" style="margin-bottom: 0;">智能体活跃实例</span>
              <el-tag type="info" effect="plain" class="text-secondary">
                活跃数: {{ agents.length }}
              </el-tag>
            </div>
          </template>
          
          <el-skeleton :loading="loading" animated :count="3">
            <template #template>
              <div style="padding: 10px; display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                <el-skeleton-item variant="rect" style="height: 120px; border-radius: var(--radius-lg)" />
                <el-skeleton-item variant="rect" style="height: 120px; border-radius: var(--radius-lg)" />
              </div>
            </template>
            
            <div v-if="!agents.length" class="empty-placeholder">
              <el-empty description="暂无受监控的智能体" />
            </div>
            
            <div v-else class="agent-grid">
              <div 
                v-for="agent in agents" 
                :key="agent.agentId"
                class="agent-item"
                :class="agent.status.toLowerCase()"
                @click="openAgentDetail(agent)"
              >
                <!-- Existing Item Content... -->
                <div class="item-header">
                  <span class="agent-name">{{ agent.agentName }}</span>
                  <el-tag size="small" :type="getStatusType(agent.status)" effect="plain">
                    {{ agent.status }}
                  </el-tag>
                </div>
                <div class="item-body">
                  <div class="metric-mini">
                    <span>健康度</span>
                    <el-progress
                      :percentage="agent.healthScore"
                      :show-text="false"
                      :stroke-width="6"
                      :status="getHealthStatus(agent.healthScore)"
                    />
                  </div>
                  <div class="item-footer">
                    <span class="time">{{ formatTime(agent.lastHeartbeat) }}</span>
                    <el-icon class="arrow-right">
                      <ArrowRight />
                    </el-icon>
                  </div>
                </div>
              </div>
            </div>
          </el-skeleton>
        </el-card>

        <!-- New: Distribution Analytics -->
        <el-row v-if="isCardEnabled('module-distribution')" :gutter="24" style="margin-top: 24px;">
          <el-col :span="getModuleSize('module-distribution', 24) === 24 ? 12 : 24">
            <el-card shadow="never" class="grid-card">
              <template #header>
                <div class="module-title" style="margin-bottom: 0;">
                  终端类型分布
                </div>
              </template>
              <div class="chart-container">
                <PieChart :data="typeDistribution" height="280px" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="getModuleSize('module-distribution', 24) === 24 ? 12 : 24" :style="getModuleSize('module-distribution', 24) !== 24 ? 'margin-top: 24px' : ''">
            <el-card shadow="never" class="grid-card">
              <template #header>
                <div class="module-title" style="margin-bottom: 0;">
                  健康状态分布
                </div>
              </template>
              <div class="chart-container">
                <PieChart :data="statusDistribution" height="280px" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- Server Hardware Monitoring -->
        <div v-if="isCardEnabled('module-server')" style="margin-top: 24px;">
          <ServerHardwareCard />
        </div>
      </el-col>

      <!-- Right: Global Activity -->
      <el-col v-if="isCardEnabled('module-activity')" :lg="getModuleSize('module-activity', 7)" :md="24">
        <el-card shadow="never" class="activity-card">
          <template #header>
            <div class="card-header">
              <span class="module-title" style="margin-bottom: 0;">系统事件流水</span>
              <el-tag size="small" type="success" effect="plain">
                实时
              </el-tag>
            </div>
          </template>
          <div class="activity-list">
            <el-skeleton :loading="loading" animated :count="10">
              <template #template>
                <div style="display: flex; gap: 10px; margin-bottom: 15px">
                  <el-skeleton-item variant="circle" style="width: 10px; height: 10px" />
                  <el-skeleton-item variant="text" style="flex: 1" />
                </div>
              </template>
              <div v-for="(log, idx) in sortedLogs.slice(0, 15)" :key="idx" class="activity-item">
                <div class="activity-dot" :class="log.type.toLowerCase()" />
                <div class="activity-info">
                  <div class="activity-text">
                    {{ log.content }}
                  </div>
                  <div class="activity-time">
                    <span v-if="log.agentName" class="agent-ref">@{{ log.agentName }}</span>
                    {{ formatTime(log.timestamp) }}
                  </div>
                </div>
              </div>
              <el-empty v-if="logs.length === 0" description="暂无动态" :image-size="60" />
            </el-skeleton>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { getGlobalSummary, getAgentList, getTraceById, getCallSuccessRate, getErrorDistribution, getLangfuseStatus } from '../api/monitor';
import { getAgentLogs } from '../api/runtime'; 
import { useRefreshStore } from '@/stores/refresh';
import PieChart from '../components/PieChart.vue';
import PageHeader from '../components/PageHeader.vue';
import DashboardCardConfig from '../components/DashboardCardConfig.vue';
import ServerHardwareCard from '../components/ServerHardwareCard.vue';
import {
  Refresh, Monitor, Cpu, Connection, Tickets, ArrowRight, Setting,
  CircleCheck, CircleClose, Warning, Search, DataLine, Link
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

const router = useRouter();
const refreshStore = useRefreshStore();

const summary = ref({});
const agents = ref([]);
const loading = ref(true);
const refreshLoading = ref(false);
const autoRefresh = ref(true);
const logs = ref([]);
const showCardConfig = ref(false);
const cardConfigRef = ref(null);
const enabledCards = ref([]);
const serverConnected = ref(false); // 服务器连接状态

// 链路追踪查询状态
const traceQueryLoading = ref(false);
const traceIdInput = ref('');
const traceResult = ref(null);
const successRateData = ref(null);
const errorDistributionData = ref([]);

// Langfuse 状态
const langfuseStatus = ref({ enabled: false, configured: false, link: '' });

// 加载 Langfuse 状态
const loadLangfuseStatus = async () => {
  try {
    const res = await getLangfuseStatus();
    langfuseStatus.value = res.data || res;
  } catch (e) {
    console.error('加载 Langfuse 状态失败:', e);
  }
};

// 打开 Langfuse Dashboard
const openLangfuse = () => {
  if (langfuseStatus.value.link) {
    window.open(langfuseStatus.value.link, '_blank');
  }
};

// 系统状态计算
const systemStatus = computed(() => {
  if (!serverConnected.value) {
    return {
      text: '服务器未连接',
      type: 'danger',
      icon: CircleClose
    };
  }
  
  if (agents.value.length === 0) {
    return {
      text: '无活跃智能体',
      type: 'warning',
      icon: Warning
    };
  }
  
  return {
    text: '系统运行中',
    type: 'success',
    icon: CircleCheck
  };
});

// Initialize enabled cards from config
const initCardConfig = () => {
  const saved = localStorage.getItem('dashboard_card_config_v2');
  if (saved) {
    try {
      enabledCards.value = JSON.parse(saved);
    } catch (e) {
      enabledCards.value = {};
    }
  } else {
    // Default config if none exists
    const defaults = [
      'stat-agents', 'stat-requests', 'stat-tokens', 'stat-latency',
      'module-agents', 'module-distribution', 'module-activity', 'module-server'
    ];
    const initialConfig = {};
    defaults.forEach(id => {
      initialConfig[id] = { enabled: true };
    });
    enabledCards.value = initialConfig;
  }
};

const handleCardConfigChange = (newConfig) => {
  enabledCards.value = newConfig;
};

const isCardEnabled = (cardId) => {
  if (!enabledCards.value[cardId]) return false;
  return enabledCards.value[cardId].enabled;
};

const getModuleSize = (cardId, defaultSize = 24) => {
  return enabledCards.value[cardId]?.size || defaultSize;
};

const formatTime = (ts) => {
  if (!ts) return '-';

  // Handle Array format [yyyy, MM, dd, HH, mm, ss]
  if (Array.isArray(ts)) {
    return new Date(ts[0], ts[1] - 1, ts[2], ts[3] || 0, ts[4] || 0, ts[5] || 0).toLocaleTimeString();
  }

  const dateStr = String(ts).replace(' ', 'T');
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '-';
  return date.toLocaleTimeString();
};

const sortedLogs = computed(() => [...logs.value].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()));

const typeDistribution = computed(() => {
  const map = {};
  
  // 规范化映射：将多种 Type 代码映射到统一的 Key
  const normalizeType = (t) => {
    if (!t) return 'CHAT';
    const up = t.toUpperCase();
    if (up === 'TEXT_TO_IMAGE' || up === 'IMAGE_TO_IMAGE') return 'TTI';
    if (up === 'TEXT_TO_SPEECH') return 'TTS';
    if (up === 'SPEECH_TO_TEXT') return 'STT';
    if (up === 'TEXT_TO_VIDEO' || up === 'VIDEO_GENERATION') return 'TTV';
    return up;
  };

  agents.value.forEach(a => {
    // 优先使用 viewType, 降级使用 mode, 默认 CHAT
    const rawType = a.viewType || a.mode || 'CHAT';
    const type = normalizeType(rawType);
    map[type] = (map[type] || 0) + 1;
  });

  const nameMap = {
    'CHAT': '对话助手',
    'WORKFLOW': '工作流',
    'TTI': '绘图生成',
    'TTS': '语音合成',
    'STT': '语音识别',
    'TTV': '视频生成',
    'AGENT': '智能体',
    'COMPLETION': '文本补全'
  };

  return Object.keys(map).map(key => ({
    value: map[key],
    name: nameMap[key] || `${key}` // 未知类型直接显示代码
  }));
});

const statusDistribution = computed(() => [
  { value: agents.value.filter(a => a.status === 'RUNNING').length, name: '运行中' },
  { value: agents.value.filter(a => a.status === 'STOPPED').length, name: '已停止' },
  { value: agents.value.filter(a => a.status === 'HIGH_LOAD').length, name: '高负载' }
]);

let pollTimer = null;

// 跳转逻辑增强: 支持更多类型
const handleCardClick = (item) => {
  if (!item.clickable) return;
  
  if (item.cardId === 'stat-tokens') {
    router.push('/dashboard/stats/tokens');
  } else if (item.cardId === 'stat-latency') {
    router.push(ROUTES.MONITOR.TOKENS);
  } else if (item.cardId === 'stat-agents') {
    router.push(ROUTES.AGENTS.LIST);
  }
};

const statItems = computed(() => [
  { cardId: 'stat-agents', label: '纳管智能体', key: 'total_agents', defaultValue: '0', icon: Monitor, color: 'var(--orin-primary)', bgColor: 'var(--orin-primary-fade)', clickable: true },
  { cardId: 'stat-requests', label: '今日请求', key: 'daily_requests', defaultValue: '0', icon: Tickets, color: 'var(--success-500)', bgColor: 'var(--success-50)', clickable: true },
  { 
    cardId: 'stat-tokens', 
    label: 'Token 消耗', 
    key: 'total_tokens', 
    defaultValue: '0', 
    icon: Cpu, 
    color: 'var(--warning-500)', 
    bgColor: 'var(--warning-50)',
    clickable: true
  },
  { 
    cardId: 'stat-latency', 
    label: '平均延迟', 
    key: 'avg_latency', 
    defaultValue: '0ms', 
    icon: Connection, 
    color: 'var(--info-500)', 
    bgColor: 'var(--info-50)',
    clickable: true
  },
]);


const fetchData = async () => {
  // 注册刷新操作并获取 AbortController
  const controller = refreshStore.registerRefresh('monitor-dashboard');
  
  // Only show full loading on first fetch
  if (agents.value.length === 0) loading.value = true;
  
  // 记录是否是手动刷新
  const isManualRefresh = refreshLoading.value === false && !autoRefresh.value;
  
  refreshLoading.value = true;
  try {
    const [sumRes, listRes] = await Promise.all([
      getGlobalSummary({ signal: controller.signal }),
      getAgentList({ signal: controller.signal })
    ]);
    
    // 检查响应数据
    if (sumRes) {
      summary.value = sumRes;
      // 成功获取数据，说明服务器已连接
      serverConnected.value = true;
    }
    
    if (listRes) {
      agents.value = listRes;
      
      // Also pull some global logs for the activity feed from first agent if exists
      if (agents.value.length > 0) {
        try {
          const logRes = await getAgentLogs(agents.value[0].agentId, { signal: controller.signal });
          if (logRes) {
            logs.value = logRes;
          }
        } catch (logError) {
          // 如果是取消错误，不显示警告
          if (logError.name !== 'CanceledError' && logError.name !== 'AbortError') {
            console.warn('获取日志失败:', logError);
          }
        }
      }
    }

    queryErrorDistribution().catch(() => {});
    
    // 刷新成功提示（手动刷新时显示更明显的提示）
    if (isManualRefresh) {
      ElMessage({
        message: '监控数据已刷新',
        type: 'success',
        duration: 2000,
        showClose: true
      });
    }
  } catch (e) {
    // 如果是取消错误，不显示错误消息
    if (e.name === 'CanceledError' || e.name === 'AbortError') {
      console.log('监控数据刷新已取消');
      return;
    }
    
    // 请求失败，设置服务器未连接
    serverConnected.value = false;
    
    console.error('获取监控数据失败:', e);
    
    // 显示具体错误信息
    let errorMsg = '获取监控数据失败';
    if (e.response) {
      if (e.response.status === 403) {
        errorMsg = '权限不足，请重新登录';
      } else if (e.response.status === 401) {
        errorMsg = '登录已过期，请重新登录';
      } else if (e.response.data && e.response.data.message) {
        errorMsg = e.response.data.message;
      }
    } else if (e.message) {
      errorMsg = e.message;
    }
    
    ElMessage.error(errorMsg);
  } finally {
    loading.value = false;
    refreshLoading.value = false;
    // 注销刷新操作
    refreshStore.unregisterRefresh('monitor-dashboard');
  }
};

const handleRefreshToggle = (val) => {
  localStorage.setItem('dashboard_auto_refresh', val);
  if (val) {
    pollTimer = setInterval(fetchData, 5000);
    ElMessage.success('已开启自动刷新');
  } else {
    clearInterval(pollTimer);
    ElMessage.warning('已关闭自动刷新');
  }
};

const openAgentDetail = (agent) => {
  router.push({
    name: 'AgentConsole',
    params: { id: agent.agentId },
    query: { tab: 'chat' } // Default to chat when coming from dashboard
  });
};

// 链路追踪查询
const queryTrace = async () => {
  if (!traceIdInput.value.trim()) {
    ElMessage.warning('请输入 Trace ID');
    return;
  }

  traceQueryLoading.value = true;
  traceResult.value = null;

  try {
    const res = await getTraceById(traceIdInput.value.trim());
    traceResult.value = res.data || res;
    ElMessage.success('查询成功');
  } catch (e) {
    ElMessage.error('查询失败: ' + (e.message || '未知错误'));
  } finally {
    traceQueryLoading.value = false;
  }
};

// 查询调用成功率
const querySuccessRate = async () => {
  try {
    const now = Date.now();
    const res = await getCallSuccessRate({ startTime: now - 3600000, endTime: now });
    successRateData.value = res.data || res;
  } catch (e) {
    console.error('查询成功率失败:', e);
  }
};

// 查询错误分布
const queryErrorDistribution = async () => {
  try {
    const now = Date.now();
    const res = await getErrorDistribution({ startTime: now - 3600000, endTime: now });
    errorDistributionData.value = (res.data || res) || [];
  } catch (e) {
    console.error('查询错误分布失败:', e);
  }
};

const getStatusType = (s) => ({ 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' }[s] || 'danger');
const getHealthStatus = (s) => s >= 90 ? 'success' : (s >= 60 ? 'warning' : 'exception');

onMounted(() => {
  initCardConfig();

  // 加载 Langfuse 状态
  loadLangfuseStatus();

  // Restore auto-refresh state
  const savedAutoRefresh = localStorage.getItem('dashboard_auto_refresh');
  if (savedAutoRefresh !== null) {
    autoRefresh.value = savedAutoRefresh === 'true';
  }

  fetchData();
  if (autoRefresh.value) {
    pollTimer = setInterval(fetchData, 5000);
  }

  // 监听全局刷新事件（来自Navbar的刷新按钮）
  window.addEventListener('global-refresh', fetchData);
});

onUnmounted(() => {
  clearInterval(pollTimer);
  
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', fetchData);
  
  // 注销刷新操作（如果还在进行中）
  refreshStore.unregisterRefresh('monitor-dashboard');
});
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.stats-row { margin-bottom: 24px; }

.trace-query-card {
  margin-bottom: 24px;
  border-radius: var(--radius-xl) !important;
}

.trace-query-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.langfuse-card {
  margin-bottom: 24px;
  border-radius: var(--radius-xl) !important;
}

.langfuse-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.langfuse-content {
  padding: 10px;
}

.langfuse-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.langfuse-info p {
  margin: 0;
  flex: 1;
}

.trace-input-row {
  display: flex;
  gap: 12px;
}

.success-rate-display, .error-dist-display {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
}

.success-rate-display .label,
.error-dist-display .label {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-right: 8px;
}

.success-rate-display .value {
  font-size: 18px;
  font-weight: bold;
}

.success-rate-display .value.success { color: var(--el-color-success); }
.success-rate-display .value.warning { color: var(--el-color-warning); }
.success-rate-display .value.danger { color: var(--el-color-danger); }

.success-rate-display .detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.error-tag {
  margin-right: 4px;
}

.placeholder-text {
  color: var(--el-text-color-placeholder);
  font-size: 14px;
  line-height: 32px;
}

.trace-result {
  margin-top: 16px;
}

.trace-not-found {
  margin-top: 16px;
  text-align: center;
}

.stat-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-200) !important;
  background: var(--neutral-white) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05) !important;
}

html.dark .stat-card {
  background: var(--neutral-gray-800) !important;
  border: 1px solid var(--neutral-gray-700) !important;
}

.stat-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: var(--shadow-premium) !important;
  border-color: var(--orin-primary) !important;
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
}

.stat-label { font-size: 13px; color: var(--neutral-gray-400); margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--neutral-gray-900); display: flex; align-items: center; gap: 8px; }

.content-row { margin-bottom: 20px; }
.header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-button {
  border-radius: var(--radius-base) !important;
  font-weight: 600;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.grid-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-200) !important;
  background: var(--neutral-white) !important;
  box-shadow: var(--shadow-lg) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

html.dark .grid-card {
  background: var(--neutral-gray-800) !important;
  border-color: var(--neutral-gray-700) !important;
}

.log-stream { 
  background: var(--neutral-gray-50);
  color: var(--neutral-gray-900);
  padding: 15px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  border-radius: var(--radius-base);
  height: 400px;
  overflow-y: auto;
  line-height: 1.6;
  border: 1px solid var(--neutral-gray-200);
}

.log-entry { 
  margin-bottom: 8px; 
  display: flex; 
  gap: 10px; 
}

.log-entry.ERROR { 
  color: var(--el-color-danger); 
}

.log-time { 
  color: var(--el-color-info); 
  flex-shrink: 0; 
}

.log-tag { 
  color: var(--el-color-primary); 
  min-width: 50px; 
}

.log-msg { 
  flex: 1; 
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: var(--neutral-gray-800);
}

.empty-placeholder {
  height: 300px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 15px;
  padding: 5px;
  min-height: 200px;
}

.agent-ref {
  color: var(--primary-color);
  font-weight: 600;
  margin-right: 5px;
  font-size: 11px;
}


.agent-item {
  background: rgba(255, 255, 255, 0.5) !important;
  backdrop-filter: blur(5px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  padding: 18px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  position: relative;
  overflow: hidden;
}

html.dark .agent-item {
  background: rgba(30, 41, 59, 0.4) !important;
  border-color: rgba(255, 255, 255, 0.05);
}

.agent-item:hover { 
  transform: translateY(-5px) scale(1.02); 
  border-color: var(--orin-primary); 
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1); 
}

.agent-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--neutral-gray-300);
}

.agent-item.running::before { background: var(--orin-primary); }
.agent-item.stopped::before { background: var(--neutral-gray-400); }
.agent-item.high_load::before { background: var(--error-500); }

.agent-item.running .agent-name {
  position: relative;
}

.agent-item.running .agent-name::after {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--orin-primary);
  border-radius: 50%;
  margin-left: 8px;
  box-shadow: 0 0 10px var(--orin-primary);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 191, 165, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(0, 191, 165, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 191, 165, 0); }
}

.item-header { display: flex; align-items: center; margin-bottom: 12px; gap: 8px; }
.agent-icon { font-size: 20px; }
.agent-name { flex: 1; font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.metric-mini { margin-bottom: 10px; font-size: 12px; color: var(--neutral-gray-5); }
.metric-mini span { display: block; margin-bottom: 6px; }

.item-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--neutral-gray-1); padding-top: 10px; margin-top: 5px; }
.item-footer .time { font-size: 11px; color: var(--neutral-gray-4); }
.arrow-right { font-size: 14px; color: var(--neutral-gray-3); }

.activity-list { max-height: 500px; overflow-y: auto; }
.activity-item { display: flex; padding: 12px 0; border-bottom: 1px solid var(--neutral-gray-1); }
.activity-dot { width: 6px; height: 6px; border-radius: 50%; margin-top: 6px; margin-right: 12px; flex-shrink: 0; }
.activity-dot.conversation { background: var(--orin-primary); }
.activity-dot.error { background: var(--error-500); }
.activity-dot.system { background: var(--neutral-gray-400); }
.activity-info { flex: 1; }
.activity-text { font-size: 13px; line-height: 1.4; color: var(--neutral-gray-6); }
.activity-time { font-size: 11px; color: var(--neutral-gray-4); margin-top: 4px; }

.detail-header-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; background: var(--neutral-bg); padding: 15px; border-radius: var(--radius-base); }
.status-badge { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 14px; }
.status-badge .dot { width: 8px; height: 8px; border-radius: 50%; }
.status-badge.running { color: var(--orin-primary); }
.status-badge.running .dot { background: var(--orin-primary); }
.status-badge.stopped { color: #94a3b8; }
.status-badge.stopped .dot { background: #94a3b8; }

.log-stream { background: #1e1e1e; color: #d4d4d4; padding: 15px; font-family: 'Courier New', Courier, monospace; font-size: 12px; border-radius: var(--radius-xs); height: 400px; overflow-y: auto; line-height: 1.6; }
.log-entry { margin-bottom: 8px; display: flex; gap: 10px; }
.log-entry.ERROR { color: #f44747; }
.log-time { color: #c586c0; flex-shrink: 0; }
.log-tag { color: #4fc1ff; min-width: 50px; }

.detail-tabs { margin-top: 10px; }

.chart-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 10px 0;
}

.detail-content {
  padding: 0 20px;
}

/* Dark mode tweaks */
html.dark .agent-item { background: var(--neutral-white); border-color: var(--neutral-gray-500); }
html.dark .stat-card { border: 1px solid var(--neutral-gray-500); }
html.dark .log-stream { background: var(--neutral-gray-900); color: var(--neutral-gray-100); border-color: var(--neutral-gray-500); }

.activity-list { max-height: 500px; overflow-y: auto; }
.activity-item { display: flex; padding: 12px 0; border-bottom: 1px solid var(--neutral-gray-100); }
.activity-dot { width: 6px; height: 6px; border-radius: 50%; margin-top: 6px; margin-right: 12px; flex-shrink: 0; }
.activity-dot.conversation { background: var(--info-color); }
.activity-dot.error { background: var(--error-color); }
.activity-dot.system { background: var(--neutral-gray-400); }
.activity-info { flex: 1; }
.activity-text { font-size: 13px; line-height: 1.4; color: var(--neutral-gray-600); }
.activity-time { font-size: 11px; color: var(--neutral-gray-400); margin-top: 4px; }

/* Dark mode for activity */
html.dark .activity-item { border-bottom: 1px solid var(--neutral-gray-600); }
html.dark .activity-text { color: var(--neutral-gray-300); }
html.dark .activity-time { color: var(--neutral-gray-500); }

/* Clickable stat card */
.stat-card.clickable {
  cursor: pointer;
  transition: all 0.3s ease;
}

.stat-card.clickable:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 12px 24px rgba(0,0,0,0.12) !important;
}


</style>
