<template>
  <div class="page-container">
    <!-- Header / Global Controls -->
    <PageHeader 
      title="实时性能监控" 
      icon="Monitor"
    >
      <template #tag-content>
        <span class="status-dot online"></span> 系统就绪
      </template>
      <template #actions>
        <el-button :icon="Setting" @click="showCardConfig = true" class="action-button">自定义面板</el-button>
        <el-divider direction="vertical" />
        <span class="text-secondary">自动刷新</span>
        <el-switch v-model="autoRefresh" active-color="var(--primary-color)" @change="handleRefreshToggle" />
        <el-divider direction="vertical" />
        <el-button :loading="refreshLoading" :icon="Refresh" @click="fetchData" class="action-button">手动同步</el-button>
      </template>
    </PageHeader>

    <!-- Card Configuration Drawer -->
    <DashboardCardConfig 
      v-model="showCardConfig" 
      @config-change="handleCardConfigChange"
      ref="cardConfigRef"
    />


    <!-- Stats Row -->
    <el-skeleton :loading="loading" animated :rows="2" v-if="enabledCards.some(c => c.startsWith('stat-'))">
      <template #template>
        <el-row :gutter="20" class="stats-row">
           <el-col :span="6" v-for="i in 4" :key="i"><el-skeleton-item variant="rect" style="height: 110px; border-radius: 8px" /></el-col>
        </el-row>
      </template>
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6" v-for="(item, index) in statItems" :key="index" v-show="isCardEnabled(item.cardId)">
          <el-card shadow="hover" class="stat-card" :body-style="{ padding: '24px' }">
            <div class="stat-card-inner">
              <div class="stat-icon" :style="{ backgroundColor: item.bgColor }">
                <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
              </div>
              <div class="stat-content">
                <div class="text-secondary" style="margin-bottom: 8px; font-weight: 500;">{{ item.label }}</div>
                <div class="stat-value" style="font-family: var(--font-heading); font-size: 24px; font-weight: 700; color: var(--neutral-gray-900);">
                   {{ summary[item.key] || item.defaultValue }}
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-skeleton>

    <!-- Main Content Area -->
    <el-row :gutter="24" class="content-row">
      <!-- Left: Agent Grid -->
      <el-col :lg="isCardEnabled('module-activity') ? 17 : 24" :md="24" v-if="isCardEnabled('module-agents')">
        <el-card class="grid-card">
          <template #header>
            <div class="card-header">
              <span class="module-title" style="margin-bottom: 0;">智能体活跃实例</span>
              <el-tag type="info" effect="plain" class="text-secondary">活跃数: {{ agents.length }}</el-tag>
            </div>
          </template>
          
          <el-skeleton :loading="loading" animated :count="3">
            <template #template>
               <div style="padding: 10px; display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                  <el-skeleton-item variant="rect" style="height: 120px; border-radius: 10px" />
                  <el-skeleton-item variant="rect" style="height: 120px; border-radius: 10px" />
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
                :class="[agent.status.toLowerCase(), { 'selected': selectedAgent?.agentId === agent.agentId }]"
                @click="openAgentDetail(agent)"
              >
                <!-- Existing Item Content... -->
                <div class="item-header">
                  <span class="agent-name">{{ agent.agentName }}</span>
                  <el-tag size="small" :type="getStatusType(agent.status)" effect="plain">{{ agent.status }}</el-tag>
                </div>
                <div class="item-body">
                  <div class="metric-mini">
                    <span>健康度</span>
                    <el-progress :percentage="agent.healthScore" :show-text="false" :stroke-width="6" :status="getHealthStatus(agent.healthScore)" />
                  </div>
                  <div class="item-footer">
                    <span class="time">{{ new Date(agent.lastHeartbeat).toLocaleTimeString() }}</span>
                    <el-icon class="arrow-right"><ArrowRight /></el-icon>
                  </div>
                </div>
              </div>
            </div>
          </el-skeleton>
        </el-card>

        <!-- New: Distribution Analytics -->
        <el-row :gutter="24" style="margin-top: 24px;" v-if="isCardEnabled('module-distribution')">
           <el-col :span="12">
              <el-card shadow="never">
                <template #header><div class="module-title" style="margin-bottom: 0;">终端类型分布</div></template>
                <div class="chart-container">
                  <PieChart :data="typeDistribution" height="280px" />
                </div>
              </el-card>
           </el-col>
           <el-col :span="12">
              <el-card shadow="never">
                <template #header><div class="module-title" style="margin-bottom: 0;">健康状态分布</div></template>
                <div class="chart-container">
                   <PieChart :data="statusDistribution" height="280px" />
                </div>
              </el-card>
           </el-col>
        </el-row>

        <!-- Server Hardware Monitoring -->
        <div style="margin-top: 24px;" v-if="isCardEnabled('module-server')">
          <ServerHardwareCard />
        </div>
      </el-col>

      <!-- Right: Global Activity -->
      <el-col :lg="7" :md="24" v-if="isCardEnabled('module-activity')">
        <el-card shadow="never" class="activity-card">
          <template #header>
            <div class="card-header">
               <span class="module-title" style="margin-bottom: 0;">系统事件流水</span>
               <el-tag size="small" type="success" effect="plain">实时</el-tag>
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
                    <div class="activity-dot" :class="log.type.toLowerCase()"></div>
                    <div class="activity-info">
                      <div class="activity-text">{{ log.content }}</div>
                      <div class="activity-time">
                        <span class="agent-ref" v-if="log.agentName">@{{ log.agentName }}</span>
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

    <!-- Refactored Detail Drawer -->
    <el-drawer
      v-model="drawerVisible"
      :title="selectedAgent ? '监控详情：' + selectedAgent.agentName : '详情'"
      size="50%"
      destroy-on-close
      class="custom-drawer"
    >
      <div v-if="selectedAgent" class="detail-content">
        <div class="detail-header-info">
          <div class="status-badge" :class="selectedAgent.status.toLowerCase()">
             <span class="dot"></span> {{ selectedAgent.status }}
          </div>
          <div class="control-btns">
            <el-button-group>
              <el-button type="success" :icon="VideoPlay" :disabled="selectedAgent.status === 'RUNNING'" @click="handleControl('start')">启动</el-button>
              <el-button type="danger" :icon="VideoPause" :disabled="selectedAgent.status === 'STOPPED'" @click="handleControl('stop')">停止</el-button>
              <el-button type="warning" :icon="Refresh" @click="handleControl('restart')">重启</el-button>
            </el-button-group>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="性能监控" name="monitor">
            <div class="chart-section">
               <LineChart title="Token 消耗 (累计)" :data="metrics.tokens" yAxisName="tokens" color="#67C23A" />
               <LineChart title="延迟 (ms)" :data="metrics.latency" yAxisName="ms" color="#E6A23C" />
               <LineChart v-if="selectedAgent.cpuUsage > 0 || selectedAgent.isLocal" title="CPU 负载" :data="metrics.cpu" yAxisName="%" :yAxisMax="100" color="#F56C6C" />
               <LineChart v-if="selectedAgent.memoryUsage > 0 || selectedAgent.isLocal" title="内存占用" :data="metrics.memory" yAxisName="MB" color="#409EFF" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="运行日志" name="logs">
             <div class="log-stream">
                <div v-for="(log, index) in logs" :key="index" class="log-entry" :class="log.type">
                   <span class="log-time">{{ new Date(log.timestamp).toLocaleTimeString() }}</span>
                   <span class="log-tag">{{ log.type }}</span>
                   <span class="log-msg">{{ log.content }}</span>
                </div>
             </div>
          </el-tab-pane>
          <el-tab-pane label="资料库" name="knowledge">
             <el-table :data="knowledgeList" size="small">
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="docCount" label="文件" width="70" align="center" />
                <el-table-column prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
             </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useDark } from '@vueuse/core';
const isDark = useDark();
import { getGlobalSummary, getAgentList, getAgentMetrics } from '../api/monitor';
import { controlAgent, getAgentLogs } from '../api/runtime'; 
import { getAgentKnowledge } from '../api/knowledge';
import LineChart from '../components/LineChart.vue';
import PieChart from '../components/PieChart.vue';
import PageHeader from '../components/PageHeader.vue';
import DashboardCardConfig from '../components/DashboardCardConfig.vue';
import ServerHardwareCard from '../components/ServerHardwareCard.vue';
import { VideoPlay, VideoPause, Refresh, Monitor, Cpu, Connection, Tickets, ArrowRight, Setting } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

const summary = ref({});
const agents = ref([]);
const loading = ref(true);
const refreshLoading = ref(false);
const autoRefresh = ref(true);
const drawerVisible = ref(false);
const selectedAgent = ref(null);
const metrics = ref({ cpu: [], memory: [], latency: [], tokens: [] });
const logs = ref([]);
const knowledgeList = ref([]);
const activeTab = ref('monitor');
const showCardConfig = ref(false);
const cardConfigRef = ref(null);
const enabledCards = ref([]);

// Initialize enabled cards from config
const initCardConfig = () => {
  const saved = localStorage.getItem('dashboard_card_config');
  if (saved) {
    try {
      enabledCards.value = JSON.parse(saved);
    } catch (e) {
      enabledCards.value = [
        'stat-agents', 'stat-requests', 'stat-tokens', 'stat-latency',
        'module-agents', 'module-distribution', 'module-activity'
      ];
    }
  } else {
    enabledCards.value = [
      'stat-agents', 'stat-requests', 'stat-tokens', 'stat-latency',
      'module-agents', 'module-distribution', 'module-activity'
    ];
  }
};

const handleCardConfigChange = (newConfig) => {
  enabledCards.value = newConfig;
};

const isCardEnabled = (cardId) => {
  return enabledCards.value.includes(cardId);
};

const formatTime = (ts) => new Date(ts).toLocaleTimeString();

const sortedLogs = computed(() => [...logs.value].sort((a, b) => b.timestamp - a.timestamp));

const typeDistribution = computed(() => [
  { value: agents.value.filter(a => a.mode === 'chat' || !a.mode).length, name: '对话型 (Chat)' },
  { value: agents.value.filter(a => a.mode === 'workflow').length, name: '工作流 (Workflow)' }
]);

const statusDistribution = computed(() => [
  { value: agents.value.filter(a => a.status === 'RUNNING').length, name: '运行中' },
  { value: agents.value.filter(a => a.status === 'STOPPED').length, name: '已停止' },
  { value: agents.value.filter(a => a.status === 'HIGH_LOAD').length, name: '高负载' }
]);

const statItems = computed(() => [
  { cardId: 'stat-agents', label: '纳管智能体', key: 'total_agents', defaultValue: '0', icon: Monitor, color: 'var(--primary-color)', bgColor: 'var(--primary-light-1)' },
  { cardId: 'stat-requests', label: '今日请求', key: 'daily_requests', defaultValue: '0', icon: Tickets, color: '#05c1af', bgColor: 'rgba(5, 193, 175, 0.1)' },
  { cardId: 'stat-tokens', label: 'Token 消耗', key: 'total_tokens', defaultValue: '0', icon: Cpu, color: 'var(--warning-color)', bgColor: 'rgba(250, 173, 20, 0.1)' },
  { cardId: 'stat-latency', label: '平均延迟', key: 'avg_latency', defaultValue: '0ms', icon: Connection, color: 'var(--success-color)', bgColor: 'rgba(76, 175, 80, 0.1)' },
]);

let pollTimer = null;
let detailPollTimer = null;

const fetchData = async () => {
  // Only show full loading on first fetch
  if (agents.value.length === 0) loading.value = true;
  refreshLoading.value = true;
  try {
    const [sumRes, listRes] = await Promise.all([getGlobalSummary(), getAgentList()]);
    summary.value = sumRes.data;
    agents.value = listRes.data;
    
    // Add Dify connection status
    summary.value.dify_connection = '已连接';
    
    // Also pull some global logs for the activity feed from first agent if exists
    if (agents.value.length > 0) {
       const logRes = await getAgentLogs(agents.value[0].agentId);
       logs.value = logRes.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
    refreshLoading.value = false;
  }
};

const handleRefreshToggle = (val) => {
  if (val) {
    pollTimer = setInterval(fetchData, 5000);
    ElMessage.success('已开启自动刷新');
  } else {
    clearInterval(pollTimer);
    ElMessage.warning('已关闭自动刷新');
  }
};

const fetchDetail = async () => {
  if (!selectedAgent.value) return;
  const end = Date.now();
  const start = end - 5 * 60 * 1000;
  try {
    const res = await getAgentMetrics(selectedAgent.value.agentId, start, end);
    const data = res.data;
    metrics.value = {
      cpu: data.map(d => ({ timestamp: d.timestamp, value: d.cpuUsage })),
      memory: data.map(d => ({ timestamp: d.timestamp, value: d.memoryUsage })),
      latency: data.map(d => ({ timestamp: d.timestamp, value: d.responseLatency })),
      tokens: data.map(d => ({ timestamp: d.timestamp, value: d.tokenCost }))
    };
    
    const logRes = await getAgentLogs(selectedAgent.value.agentId);
    logs.value = logRes.data;
    
    if (activeTab.value === 'knowledge' && knowledgeList.value.length === 0) {
        const kbRes = await getAgentKnowledge(selectedAgent.value.agentId);
        knowledgeList.value = kbRes.data;
    }
  } catch (e) { console.error(e); }
};

const handleControl = async (action) => {
    try {
        await controlAgent(selectedAgent.value.agentId, action);
        ElMessage.success(`操作成功：${action}`);
        fetchData();
        fetchDetail();
    } catch (e) { ElMessage.error('失败: ' + e.message); }
};

const openAgentDetail = (agent) => {
  console.log('Opening agent detail:', agent);
  selectedAgent.value = agent;
  drawerVisible.value = true;
  activeTab.value = 'monitor';
  fetchDetail();
  if (detailPollTimer) clearInterval(detailPollTimer);
  detailPollTimer = setInterval(fetchDetail, 3000);
};

const getStatusType = (s) => ({ 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' }[s] || 'danger');
const getHealthStatus = (s) => s >= 90 ? 'success' : (s >= 60 ? 'warning' : 'exception');

onMounted(() => {
  initCardConfig();
  fetchData();
  pollTimer = setInterval(fetchData, 5000);
});

onUnmounted(() => {
  clearInterval(pollTimer);
  clearInterval(detailPollTimer);
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

.stat-card {
  border-radius: var(--border-radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
  background: var(--neutral-white) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-premium) !important;
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: inset 0 0 10px rgba(255,255,255,0.2);
}

.stat-label { font-size: 13px; color: var(--neutral-gray-4); margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--neutral-black); display: flex; align-items: center; gap: 8px; }

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
  border-radius: var(--border-radius-base) !important;
  font-weight: 600;
}

.grid-card {
  border-radius: var(--border-radius-lg) !important;
  border: 1px solid var(--neutral-gray-100) !important;
  background: var(--neutral-white) !important;
  box-shadow: var(--shadow-sm) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.grid-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
  border-color: var(--primary-light) !important;
}

.log-stream { 
  background: var(--neutral-gray-50);
  color: var(--neutral-gray-900);
  padding: 15px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  border-radius: var(--border-radius-base);
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
  color: var(--error-color); 
}

.log-time { 
  color: var(--info-color); 
  flex-shrink: 0; 
}

.log-tag { 
  color: var(--primary-color); 
  min-width: 50px; 
}

.log-msg { 
  flex: 1; 
}

.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 15px; }

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
  background: var(--neutral-white);
  border: 1px solid var(--neutral-gray-2);
  border-radius: 10px;
  padding: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  pointer-events: auto;
  z-index: 1;
}
.agent-item:hover { transform: translateY(-3px); border-color: var(--primary-color); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.agent-item.selected { border-color: var(--primary-color); background: var(--primary-light-1); }

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
.activity-dot.conversation { background: #409EFF; }
.activity-dot.error { background: #F56C6C; }
.activity-dot.system { background: #909399; }
.activity-info { flex: 1; }
.activity-text { font-size: 13px; line-height: 1.4; color: var(--neutral-gray-6); }
.activity-time { font-size: 11px; color: var(--neutral-gray-4); margin-top: 4px; }

.detail-header-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; background: var(--neutral-bg); padding: 15px; border-radius: 8px; }
.status-badge { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 14px; }
.status-badge .dot { width: 8px; height: 8px; border-radius: 50%; }
.status-badge.running { color: #67C23A; }
.status-badge.running .dot { background: #67C23A; }
.status-badge.stopped { color: #909399; }
.status-badge.stopped .dot { background: #909399; }

.log-stream { background: #1e1e1e; color: #d4d4d4; padding: 15px; font-family: 'Courier New', Courier, monospace; font-size: 12px; border-radius: 4px; height: 400px; overflow-y: auto; line-height: 1.6; }
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
</style>
