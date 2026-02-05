<template>
  <div class="light-datawall">
    <!-- Background -->
    <div class="wall-bg">
      <div class="bg-pattern"></div>
    </div>

    <!-- Header Bar -->
    <header class="wall-header">
      <div class="header-left">
        <img src="/logo.png" class="header-logo" />
        <div class="header-title">
          <h1>ORIN 智能平台监控中心</h1>
          <span class="subtitle">AI Platform Monitoring Center</span>
        </div>
      </div>
      <div class="header-center">
        <div class="status-badge" :class="systemOnline ? 'online' : 'offline'">
          <span class="dot"></span>
          <span>{{ systemStatusText }}</span>
        </div>
      </div>
      <div class="header-right">
        <div class="datetime-display">
          <div class="time">{{ currentTime }}</div>
          <div class="date">{{ currentDate }}</div>
        </div>
      </div>
    </header>

    <!-- Main Grid -->
    <div class="wall-content">
      <!-- Left Column -->
      <div class="left-column">
        <!-- Top Stats -->
        <div class="panel mini-stats">
          <div class="mini-stat" v-for="stat in miniStats" :key="stat.key">
            <div class="mini-label">{{ stat.label }}</div>
            <div class="mini-value" :style="{ color: stat.color }">
              <AnimatedNumber :value="stat.value" />{{ stat.unit }}
            </div>
            <div class="mini-change" :class="stat.trend">{{ stat.change }}%</div>
          </div>
        </div>

        <!-- System Performance -->
        <div class="panel perf-panel">
          <div class="panel-title">系统性能监控</div>
          <div class="perf-grid">
            <div class="perf-gauge" v-for="perf in performanceMetrics" :key="perf.key">
              <div class="gauge-ring">
                <svg viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="40" fill="none" stroke="#e5e7eb" stroke-width="8" />
                  <circle 
                    cx="50" cy="50" r="40" 
                    fill="none" 
                    :stroke="perf.color"
                    stroke-width="8"
                    stroke-linecap="round"
                    :stroke-dasharray="251.2"
                    :stroke-dashoffset="251.2 - (251.2 * perf.value / 100)"
                    transform="rotate(-90 50 50)"
                  />
                </svg>
                <div class="gauge-text">
                  <div class="gauge-val"><AnimatedNumber :value="perf.value" />%</div>
                  <div class="gauge-lbl">{{ perf.label }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Agent Distribution -->
        <div class="panel dist-panel">
          <div class="panel-title">智能体类型分布</div>
          <div class="dist-list">
            <div class="dist-row" v-for="(dist, idx) in agentDistribution" :key="idx">
              <span class="dist-label">{{ dist.name }}</span>
              <div class="dist-bar-bg">
                <div class="dist-bar-fill" :style="{ width: dist.percent + '%', background: dist.color }"></div>
              </div>
              <span class="dist-val">{{ dist.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Center Column -->
      <div class="center-column">
        <!-- Big Stats -->
        <div class="big-stats">
          <div class="big-stat" v-for="stat in bigStats" :key="stat.key">
            <div class="big-icon" :style="{ background: stat.gradient }">
              <el-icon><component :is="stat.icon" /></el-icon>
            </div>
            <div class="big-content">
              <div class="big-label">{{ stat.label }}</div>
              <div class="big-value">
                <AnimatedNumber :value="stat.value" />
                <span class="big-unit">{{ stat.unit }}</span>
              </div>
            </div>
            <div class="big-sparkline">
              <svg viewBox="0 0 80 30" preserveAspectRatio="none">
                <path :d="generateLine(stat.history)" :stroke="stat.color" fill="none" stroke-width="2" />
              </svg>
            </div>
          </div>
        </div>

        <!-- Main Chart -->
        <div class="panel main-chart">
          <div class="panel-title">
            <span>请求趋势分析</span>
            <el-tag size="small">24H</el-tag>
          </div>
          <div class="chart-wrapper">
            <LineChart :data="requestTrend" height="100%" color="#3b82f6" yAxisName="" />
          </div>
        </div>

        <!-- Secondary Charts -->
        <div class="secondary-charts">
          <div class="panel chart-small">
            <div class="panel-title">Token 消耗趋势</div>
            <div class="chart-wrapper">
              <LineChart :data="tokenTrend" height="100%" color="#8b5cf6" yAxisName="" />
            </div>
          </div>
          <div class="panel chart-small">
            <div class="panel-title">智能体分布</div>
            <div class="chart-wrapper">
              <BarChart :data="agentTypeChart" height="100%" color="#10b981" />
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column -->
      <div class="right-column">
        <!-- Agent Status -->
        <div class="panel agents-panel">
          <div class="panel-title">
            <span>运行中的智能体</span>
            <el-tag size="small" type="success">{{ runningAgents }}</el-tag>
          </div>
          <div class="agents-list">
            <div class="agent-row" v-for="agent in agents.slice(0, 10)" :key="agent.agentId" @click="navigateToAgent(agent.agentId)">
              <div class="agent-dot" :style="{ background: getAgentColor(agent.status) }"></div>
              <div class="agent-name">{{ agent.agentName }}</div>
              <el-tag :type="getStatusType(agent.status)" size="small">{{ agent.status }}</el-tag>
              <div class="agent-health">
                <div class="health-bar">
                  <div class="health-fill" :style="{ width: agent.healthScore + '%', background: getHealthColor(agent.healthScore) }"></div>
                </div>
                <span class="health-num">{{ agent.healthScore }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import LineChart from '@/components/LineChart.vue';
import BarChart from '@/components/BarChart.vue';
import AnimatedNumber from '@/components/AnimatedNumber.vue';
import { Monitor, Tickets, Cpu, Connection, ArrowUp, ArrowDown } from '@element-plus/icons-vue';
import { getGlobalSummary, getAgentList } from '@/api/monitor';
import request from '@/utils/request';

const router = useRouter();
const loading = ref(false);
const summary = ref({});
const agents = ref([]);
const currentTime = ref('');
const currentDate = ref('');

const updateDateTime = () => {
  const now = new Date();
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  currentDate.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
};

const systemOnline = computed(() => agents.value.filter(a => a.status === 'RUNNING').length > 0);
const systemStatusText = computed(() => {
  const running = agents.value.filter(a => a.status === 'RUNNING').length;
  return `系统运行正常 · ${running} 个智能体在线`;
});
const runningAgents = computed(() => agents.value.filter(a => a.status === 'RUNNING').length);

const miniStats = computed(() => [
  { key: 'agents', label: '活跃智能体', value: summary.value.total_agents || 0, unit: '', color: '#3b82f6', trend: 'up', change: 12 },
  { key: 'requests', label: '今日请求', value: summary.value.daily_requests || 0, unit: '', color: '#8b5cf6', trend: 'up', change: 8 },
  { key: 'tokens', label: 'Token消耗', value: summary.value.total_tokens || 0, unit: 'K', color: '#10b981', trend: 'down', change: 3 },
  { key: 'latency', label: '平均延迟', value: parseInt(summary.value.avg_latency) || 0, unit: 'ms', color: '#f59e0b', trend: 'down', change: 15 }
]);

const bigStats = computed(() => [
  { 
    key: 'total', label: '总请求数', value: summary.value.daily_requests || 0, unit: '次',
    icon: Tickets, gradient: 'linear-gradient(135deg, #3b82f6, #2563eb)', color: '#3b82f6',
    history: [10, 15, 12, 18, 20, 22, 19, 25]
  },
  { 
    key: 'agents', label: '活跃智能体', value: summary.value.total_agents || 0, unit: '个',
    icon: Monitor, gradient: 'linear-gradient(135deg, #10b981, #059669)', color: '#10b981',
    history: [3, 4, 3, 5, 4, 6, 5, 6]
  },
  { 
    key: 'tokens', label: 'Token消耗', value: summary.value.total_tokens || 0, unit: 'K',
    icon: Cpu, gradient: 'linear-gradient(135deg, #8b5cf6, #7c3aed)', color: '#8b5cf6',
    history: [100, 95, 98, 92, 90, 88, 85, 82]
  },
  { 
    key: 'latency', label: '平均延迟', value: parseInt(summary.value.avg_latency) || 0, unit: 'ms',
    icon: Connection, gradient: 'linear-gradient(135deg, #f59e0b, #d97706)', color: '#f59e0b',
    history: [150, 140, 135, 130, 125, 120, 115, 110]
  }
]);

const performanceMetrics = ref([
  { key: 'cpu', label: 'CPU', value: 0, color: '#3b82f6' },
  { key: 'memory', label: '内存', value: 0, color: '#8b5cf6' },
  { key: 'gpu', label: 'GPU', value: 0, color: '#10b981' },
  { key: 'disk', label: '磁盘', value: 0, color: '#f59e0b' }
]);

const agentDistribution = computed(() => {
  const map = {};
  agents.value.forEach(a => {
    const type = a.viewType || a.mode || 'CHAT';
    map[type] = (map[type] || 0) + 1;
  });
  
  const colors = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444'];
  const nameMap = {
    'CHAT': '对话助手', 'WORKFLOW': '工作流', 'TEXT_TO_IMAGE': '图像生成',
    'TTI': '图像生成', 'TTS': '语音合成', 'AGENT': '智能体'
  };
  
  const total = Object.values(map).reduce((a, b) => a + b, 0) || 1;
  
  return Object.keys(map).map((key, index) => ({
    name: nameMap[key] || key,
    value: map[key],
    percent: (map[key] / total) * 100,
    color: colors[index % colors.length]
  }));
});

const agentTypeChart = computed(() => {
  return agentDistribution.value.map(d => ({
    name: d.name,
    value: d.value
  }));
});

const requestTrend = computed(() => {
  const data = [];
  const now = Date.now();
  for (let i = 23; i >= 0; i--) {
    data.push({
      timestamp: new Date(now - i * 60 * 60 * 1000).toISOString(),
      value: Math.floor(Math.random() * 50) + 20
    });
  }
  return data;
});

const tokenTrend = computed(() => {
  const data = [];
  const now = Date.now();
  for (let i = 23; i >= 0; i--) {
    data.push({
      timestamp: new Date(now - i * 60 * 60 * 1000).toISOString(),
      value: Math.floor(Math.random() * 30) + 10
    });
  }
  return data;
});

const generateLine = (data) => {
  if (!data || data.length === 0) return '';
  const max = Math.max(...data);
  const min = Math.min(...data);
  const range = max - min || 1;
  const points = data.map((val, i) => {
    const x = (i / (data.length - 1)) * 80;
    const y = 30 - ((val - min) / range) * 25;
    return `${x},${y}`;
  }).join(' ');
  return `M ${points}`;
};

const fetchData = async () => {
  try {
    const [sumRes, listRes] = await Promise.all([
      getGlobalSummary(),
      getAgentList()
    ]);
    if (sumRes) summary.value = sumRes;
    if (listRes) agents.value = listRes;
    
    try {
      const hwRes = await request.get('/monitor/server-hardware');
      if (hwRes) {
        performanceMetrics.value[0].value = hwRes.cpuUsage || 0;
        performanceMetrics.value[1].value = hwRes.memoryUsage || 0;
        performanceMetrics.value[2].value = hwRes.gpuUsage || 0;
        performanceMetrics.value[3].value = hwRes.diskUsage || 0;
      }
    } catch (e) {
      console.warn('Hardware metrics unavailable');
    }
  } catch (e) {
    console.error('Failed to fetch data:', e);
  }
};

const navigateToAgent = (agentId) => {
  router.push({ name: 'AgentConsole', params: { id: agentId } });
};

const getStatusType = (status) => {
  const map = { 'RUNNING': 'success', 'HIGH_LOAD': 'warning', 'STOPPED': 'info' };
  return map[status] || 'danger';
};

const getAgentColor = (status) => {
  const map = { 'RUNNING': '#10b981', 'HIGH_LOAD': '#f59e0b', 'STOPPED': '#6b7280' };
  return map[status] || '#ef4444';
};

const getHealthColor = (health) => {
  if (health >= 80) return '#10b981';
  if (health >= 50) return '#f59e0b';
  return '#ef4444';
};

let timeInterval = null;
let refreshTimer = null;

onMounted(() => {
  updateDateTime();
  timeInterval = setInterval(updateDateTime, 1000);
  fetchData();
  refreshTimer = setInterval(fetchData, 15000);
});

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval);
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<style scoped>
.light-datawall {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
}

/* Background */
.wall-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.bg-pattern {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(59, 130, 246, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}

/* Header */
.wall-header {
  height: 60px;
  padding: 0 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  border-bottom: 2px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-logo {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.header-title h1 {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #1e293b;
}

.subtitle {
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

.status-badge.online {
  background: #d1fae5;
  border-color: #6ee7b7;
  color: #059669;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.datetime-display {
  text-align: right;
}

.time {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  font-variant-numeric: tabular-nums;
}

.date {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

/* Main Content */
.wall-content {
  height: calc(100vh - 60px);
  padding: 15px;
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 15px;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

/* Columns */
.left-column,
.center-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 15px;
  overflow-y: auto;
}

.left-column::-webkit-scrollbar,
.center-column::-webkit-scrollbar,
.right-column::-webkit-scrollbar {
  width: 4px;
}

.left-column::-webkit-scrollbar-thumb,
.center-column::-webkit-scrollbar-thumb,
.right-column::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}

/* Panel Base */
.panel {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* Mini Stats */
.mini-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding: 10px;
}

.mini-stat {
  text-align: center;
  padding: 10px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.mini-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
}

.mini-value {
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 4px;
}

.mini-change {
  font-size: 10px;
  font-weight: 600;
}

.mini-change.up { color: #10b981; }
.mini-change.down { color: #ef4444; }

/* Performance Panel */
.perf-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.perf-gauge {
  position: relative;
}

.gauge-ring {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
}

.gauge-ring svg {
  width: 100%;
  height: 100%;
}

.gauge-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.gauge-val {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.gauge-lbl {
  font-size: 11px;
  color: #64748b;
}

/* Distribution Panel */
.dist-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dist-label {
  font-size: 12px;
  color: #475569;
  width: 70px;
  flex-shrink: 0;
}

.dist-bar-bg {
  flex: 1;
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}

.dist-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 1s ease-out;
}

.dist-val {
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
  width: 40px;
  text-align: right;
}

/* Big Stats */
.big-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 15px;
}

.big-stat {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.big-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.big-label {
  font-size: 12px;
  color: #64748b;
}

.big-value {
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.big-unit {
  font-size: 12px;
  color: #64748b;
}

.big-sparkline {
  height: 30px;
  margin-top: auto;
}

.big-sparkline svg {
  width: 100%;
  height: 100%;
}

/* Main Chart */
.main-chart {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chart-wrapper {
  flex: 1;
  min-height: 0;
}

/* Secondary Charts */
.secondary-charts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  height: 200px;
}

.chart-small {
  display: flex;
  flex-direction: column;
}

/* Agents Panel */
.agents-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.agent-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.agent-row:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
  transform: translateX(4px);
}

.agent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.agent-name {
  flex: 1;
  font-size: 12px;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-health {
  display: flex;
  align-items: center;
  gap: 6px;
}

.health-bar {
  width: 40px;
  height: 4px;
  background: #f1f5f9;
  border-radius: 2px;
  overflow: hidden;
}

.health-fill {
  height: 100%;
  border-radius: 2px;
}

.health-num {
  font-size: 10px;
  font-weight: 600;
  color: #1e293b;
  width: 32px;
  text-align: right;
}
</style>
