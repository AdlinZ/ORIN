<template>
  <div class="server-monitor-page">
    <PageHeader title="服务器监控" icon="Monitor">
      <template #actions>
        <el-button :icon="Refresh" @click="fetchAllData" :loading="loading">刷新</el-button>
      </template>
    </PageHeader>

    <!-- Prometheus 状态卡片 -->
    <el-row :gutter="20" class="margin-bottom-lg">
      <el-col :span="24">
        <el-card shadow="never" class="status-card">
          <div class="status-content">
            <div class="status-item">
              <div class="status-label">
                <el-icon><Connection /></el-icon>
                <span>Prometheus</span>
              </div>
              <el-tag :type="prometheusStatus.connected ? 'success' : 'danger'" effect="dark" size="small">
                <el-icon style="margin-right: 4px;"><component :is="prometheusStatus.connected ? 'CircleCheck' : 'CircleClose'" /></el-icon>
                {{ prometheusStatus.connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
            <div class="status-item">
              <div class="status-label">
                <el-icon><Monitor /></el-icon>
                <span>服务器状态</span>
              </div>
              <el-tag v-if="serverOnline === null" type="info" effect="dark" size="small">
                <el-icon style="margin-right: 4px;"><Loading /></el-icon>
                加载中
              </el-tag>
              <el-tooltip v-else-if="!serverOnline && serverError" :content="serverError" placement="bottom">
                <el-tag type="danger" effect="dark" size="small">
                  <el-icon style="margin-right: 4px;"><CircleClose /></el-icon>
                  离线
                </el-tag>
              </el-tooltip>
              <el-tag v-else :type="serverOnline ? 'success' : 'danger'" effect="dark" size="small">
                <el-icon style="margin-right: 4px;"><component :is="serverOnline ? 'CircleCheck' : 'CircleClose'" /></el-icon>
                {{ serverOnline ? '在线' : '离线' }}
              </el-tag>
            </div>
            <div class="status-item">
              <div class="status-label">
                <el-icon><Clock /></el-icon>
                <span>运行时间</span>
              </div>
              <span class="status-value">{{ localServerInfo.uptime || '-' }}</span>
            </div>
            <div class="status-item">
              <div class="status-label">
                <el-icon><Cpu /></el-icon>
                <span>CPU 核心</span>
              </div>
              <span class="status-value">{{ localServerInfo.cpuCores || 0 }} 核 / {{ localServerInfo.cpuLogicalCores || 0 }} 线程</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 核心指标卡片 -->
    <el-row :gutter="20" class="margin-bottom-lg">
      <!-- CPU 使用率 -->
      <el-col :span="6">
        <el-card shadow="never" class="metric-card cpu-card">
          <div class="metric-header">
            <el-icon><Cpu /></el-icon>
            <span>CPU 使用率</span>
          </div>
          <div class="metric-value">{{ cpuUsagePercent.toFixed(1) }}%</div>
          <div class="metric-sub">
            <span>核心数: {{ localServerInfo.cpuCores || 0 }}</span>
          </div>
          <div class="metric-gauge">
            <el-progress :percentage="cpuUsagePercent" :stroke-width="6" :show-text="false" :color="getUsageColor(cpuUsagePercent)" />
          </div>
          <div class="metric-label">型号: {{ localServerInfo.cpuModel || 'Unknown' }}</div>
        </el-card>
      </el-col>

      <!-- 内存使用 -->
      <el-col :span="6">
        <el-card shadow="never" class="metric-card memory-card">
          <div class="metric-header">
            <el-icon><Coin /></el-icon>
            <span>内存使用</span>
          </div>
          <div class="metric-value">{{ formatBytes(memoryInfo.used) }}</div>
          <div class="metric-sub">
            <span>已用</span>
            <span class="divider">/</span>
            <span>{{ formatBytes(memoryInfo.total) }}</span>
          </div>
          <div class="metric-gauge">
            <el-progress :percentage="memoryInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(memoryInfo.percent)" />
          </div>
          <div class="metric-label">可用: {{ formatBytes(memoryInfo.available) }} ({{ (100 - memoryInfo.percent).toFixed(1) }}%)</div>
        </el-card>
      </el-col>

      <!-- 磁盘使用 -->
      <el-col :span="6">
        <el-card shadow="never" class="metric-card disk-card">
          <div class="metric-header">
            <el-icon><Folder /></el-icon>
            <span>磁盘使用</span>
          </div>
          <div class="metric-value">{{ formatBytes(diskInfo.used) }}</div>
          <div class="metric-sub">
            <span>已用</span>
            <span class="divider">/</span>
            <span>{{ formatBytes(diskInfo.total) }}</span>
          </div>
          <div class="metric-gauge">
            <el-progress :percentage="diskInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(diskInfo.percent)" />
          </div>
          <div class="metric-label">可用: {{ formatBytes(diskInfo.available) }}</div>
        </el-card>
      </el-col>

      <!-- GPU 使用 -->
      <el-col :span="6">
        <el-card shadow="never" class="metric-card gpu-card">
          <div class="metric-header">
            <el-icon><Star /></el-icon>
            <span>GPU 使用</span>
          </div>
          <div class="metric-value">{{ gpuInfo.used || 0 }}%</div>
          <div class="metric-sub">
            <span>显存: {{ formatBytes(gpuInfo.memoryUsed) }}</span>
            <span class="divider">/</span>
            <span>{{ formatBytes(gpuInfo.memoryTotal) }}</span>
          </div>
          <div class="metric-gauge">
            <el-progress :percentage="gpuInfo.used || 0" :stroke-width="6" :show-text="false" :color="getUsageColor(gpuInfo.used)" />
          </div>
          <div class="metric-label">型号: {{ localServerInfo.gpuModel || 'N/A' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务器信息 -->
    <el-row :gutter="20" class="margin-bottom-lg">
      <el-col :span="24">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <el-icon><Monitor /></el-icon>
              <span>服务器信息</span>
            </div>
          </template>
          <div class="server-info-grid">
            <div class="info-item">
              <span class="info-label">操作系统</span>
              <span class="info-value">{{ localServerInfo.os || 'Unknown' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">CPU 型号</span>
              <span class="info-value">{{ localServerInfo.cpuModel || 'Unknown' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">CPU 物理核心</span>
              <span class="info-value">{{ localServerInfo.cpuCores || 0 }} 核</span>
            </div>
            <div class="info-item">
              <span class="info-label">CPU 逻辑核心</span>
              <span class="info-value">{{ localServerInfo.cpuLogicalCores || 0 }} 线程</span>
            </div>
            <div class="info-item">
              <span class="info-label">总内存</span>
              <span class="info-value">{{ formatBytes(memoryInfo.total) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">进程数</span>
              <span class="info-value">{{ localServerInfo.processCount || 0 }}</span>
            </div>
            <div class="info-item wide">
              <span class="info-label">GPU</span>
              <span class="info-value">{{ localServerInfo.gpuModel || 'N/A' }}</span>
            </div>
            <div class="info-item wide">
              <span class="info-label">磁盘</span>
              <span class="info-value">{{ diskInfo.devices || 'N/A' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图表 -->
    <el-row :gutter="20" class="margin-bottom-lg">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <el-icon><TrendCharts /></el-icon>
              <span>CPU & 内存使用趋势</span>
            </div>
          </template>
          <div v-loading="loading" style="height: 280px;">
            <LineChart v-if="trendData.length > 0" :data="trendData" title="" yAxisName="使用率 (%)" height="260px" :colors="['#667eea', '#11998e']" />
            <el-empty v-else description="暂无趋势数据" :image-size="80" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <el-icon><DataLine /></el-icon>
              <span>历史记录</span>
              <el-select v-model="period" size="small" style="margin-left: auto; width: 100px;" @change="fetchTrendData">
                <el-option label="5分钟" value="5m" />
                <el-option label="1小时" value="1h" />
                <el-option label="24小时" value="24h" />
                <el-option label="7天" value="7d" />
              </el-select>
            </div>
          </template>
          <div v-loading="loading" style="height: 280px;">
            <LineChart v-if="diskTrendData.length > 0" :data="diskTrendData" title="" yAxisName="使用率 (%)" height="260px" :colors="['#f39c12', '#e74c3c']" />
            <el-empty v-else description="暂无磁盘数据" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 历史数据表格 -->
    <el-card shadow="never" class="history-card">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>采集历史记录</span>
          <span class="record-count">共 {{ historyTotal }} 条</span>
          <el-button type="primary" size="small" style="margin-left: auto;" @click="collectNow" :loading="collecting">
            <el-icon style="margin-right: 4px;"><Refresh /></el-icon>
            立即采集
          </el-button>
        </div>
      </template>

      <el-table :data="historyData" v-loading="loading" style="width: 100%">
        <el-table-column label="时间" min-width="180" fixed>
          <template #default="{ row }">
            <div class="time-cell">
              <el-icon><Clock /></el-icon>
              {{ formatDateTime(row.recordedAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="CPU" min-width="140" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress :percentage="row.cpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.cpuUsage)" />
              <span class="usage-text">{{ (row.cpuUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内存" min-width="140" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress :percentage="row.memoryUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.memoryUsage)" />
              <span class="usage-text">{{ (row.memoryUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="磁盘" min-width="140" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress :percentage="row.diskUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.diskUsage)" />
              <span class="usage-text">{{ (row.diskUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="GPU" min-width="140" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress :percentage="row.gpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.gpuUsage)" />
              <span class="usage-text">{{ (row.gpuUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="historyTotal"
          @size-change="fetchHistoryData"
          @current-change="fetchHistoryData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { Cpu, Coin, Folder, Connection, Refresh, TrendCharts, List, Clock, Monitor, CircleCheck, CircleClose, DataLine, Star, Loading } from '@element-plus/icons-vue';
import LineChart from '@/components/LineChart.vue';
import PageHeader from '@/components/PageHeader.vue';
import { getPrometheusConfig, getServerHardwareTrend, getServerHardwareHistory, collectServerHardware } from '@/api/monitor';
import { ElMessage } from 'element-plus';

// Loading states
const loading = ref(false);
const collecting = ref(false);
const period = ref('1h');

// Data
const prometheusStatus = ref({ connected: false });
const serverOnline = ref(null); // 服务器在线状态: null=loading, true=online, false=offline
const serverError = ref(''); // 服务器错误信息
const localServerInfo = ref({});
const memoryInfo = ref({ total: 0, used: 0, available: 0, percent: 0 });
const diskInfo = ref({ total: 0, used: 0, available: 0, percent: 0, devices: '' });
const gpuInfo = ref({ used: 0, memoryUsed: 0, memoryTotal: 0 });
const cpuUsagePercent = ref(0);

// Trend data
const trendData = ref([]);
const diskTrendData = ref([]);

// History data
const historyData = ref([]);
const page = ref(1);
const pageSize = ref(10);
const historyTotal = ref(0);

// Auto refresh timer
let refreshTimer = null;

// Fetch Prometheus status
const fetchPrometheusStatus = async () => {
  try {
    const config = await getPrometheusConfig();
    // 只检查是否启用，不测试连接（因为数据来自数据库）
    prometheusStatus.value.connected = config && config.enabled;
  } catch (e) {
    prometheusStatus.value.connected = false;
  }
};

// Fetch server status from database (latest record)
const fetchServerStatusFromDB = async () => {
  try {
    // 获取历史数据的第一条作为当前状态
    const data = await getServerHardwareHistory({ page: 0, size: 1 });
    if (data.content && data.content.length > 0) {
      const latest = data.content[0];
      serverOnline.value = latest.online === true;
      serverError.value = '';

      localServerInfo.value = {
        os: latest.os || 'Unknown',
        cpuModel: latest.cpuModel || 'Unknown',
        cpuCores: latest.cpuCores || 0,
        gpuModel: latest.gpuModel || 'N/A'
      };

      // CPU usage
      cpuUsagePercent.value = latest.cpuUsage || 0;

      // Memory info
      if (latest.memoryTotal) {
        const total = latest.memoryTotal;
        const used = latest.memoryUsed || 0;
        memoryInfo.value = {
          total,
          used,
          available: total - used,
          percent: latest.memoryUsage || 0
        };
      }

      // Disk info
      if (latest.diskTotal) {
        const total = latest.diskTotal;
        const used = latest.diskUsed || 0;
        diskInfo.value = {
          total,
          used,
          available: total - used,
          percent: latest.diskUsage || 0,
          devices: 'Root Disk'
        };
      }

      // GPU info
      gpuInfo.value = {
        used: latest.gpuUsage || 0,
        memoryUsed: 0,
        memoryTotal: 0
      };
    } else {
      serverOnline.value = false;
      serverError.value = '暂无数据';
    }
  } catch (e) {
    console.error('Failed to fetch server status:', e);
    serverOnline.value = false;
    serverError.value = e.message || '请求失败';
  }
};

// Legacy function - kept for compatibility
const fetchLocalServerInfo = async () => {
  await fetchServerStatusFromDB();
};

// Fetch system load - now uses database (kept for auto-refresh)
const fetchSystemLoad = async () => {
  await fetchServerStatusFromDB();
};

// Fetch trend data
const fetchTrendData = async () => {
  try {
    const data = await getServerHardwareTrend(period.value);
    trendData.value = data.map(item => ({
      timestamp: item.timestamp,
      value: item.cpuUsage || 0,
      memoryUsage: item.memoryUsage || 0
    }));
    diskTrendData.value = data.map(item => ({
      timestamp: item.timestamp,
      value: item.diskUsage || 0,
      gpuUsage: item.gpuUsage || 0
    }));

    // Update disk info with latest data
    if (data && data.length > 0) {
      const latest = data[data.length - 1];
      if (diskInfo.value.total > 0) {
        const used = (latest.diskUsage / 100) * diskInfo.value.total;
        diskInfo.value = {
          ...diskInfo.value,
          used: used,
          percent: latest.diskUsage || 0,
          available: diskInfo.value.total - used
        };
      }
    }
  } catch (e) {
    console.error('Failed to fetch trend data:', e);
    trendData.value = [];
    diskTrendData.value = [];
  }
};

// Fetch history data
const fetchHistoryData = async () => {
  try {
    const data = await getServerHardwareHistory({
      page: page.value - 1,
      size: pageSize.value
    });
    historyData.value = data.content || [];
    historyTotal.value = data.totalElements || 0;
  } catch (e) {
    console.error('Failed to fetch history data:', e);
  }
};

// Collect now
const collectNow = async () => {
  collecting.value = true;
  try {
    await collectServerHardware();
    ElMessage.success('数据采集成功');
    await Promise.all([fetchTrendData(), fetchHistoryData()]);
  } catch (e) {
    ElMessage.error('采集失败: ' + (e.response?.data?.message || e.message));
  } finally {
    collecting.value = false;
  }
};

// Fetch all data
const fetchAllData = async () => {
  loading.value = true;
  try {
    await Promise.all([
      fetchPrometheusStatus(),
      fetchLocalServerInfo(),
      fetchTrendData(),
      fetchHistoryData()
    ]);
  } finally {
    loading.value = false;
  }
};

// Helpers
const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatUptime = (seconds) => {
  if (!seconds) return '-';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}天 ${hours}小时`;
  if (hours > 0) return `${hours}小时 ${mins}分钟`;
  return `${mins}分钟`;
};

const formatDateTime = (val) => {
  if (!val) return '-';
  let d;
  if (typeof val === 'number' || /^\d+$/.test(String(val))) {
    d = new Date(parseInt(val));
  } else if (typeof val === 'string') {
    d = new Date(val.replace('T', ' '));
  } else {
    d = new Date(val);
  }
  if (isNaN(d.getTime())) return val;
  return d.toLocaleString('zh-CN');
};

const getUsageColor = (val) => {
  const v = Number(val) || 0;
  if (v > 80) return '#e74c3c';
  if (v > 60) return '#f39c12';
  return '#27ae60';
};

// Lifecycle
onMounted(() => {
  fetchAllData();
  // Auto refresh every 30 seconds
  refreshTimer = setInterval(() => {
    fetchSystemLoad();
  }, 30000);
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<style scoped>
.server-monitor-page {
  padding: 24px;
}

.margin-bottom-lg {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

/* Status Card */
.status-card {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  border-radius: 12px;
  border: none !important;
}

.status-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #94a3b8;
  font-size: 13px;
}

.status-value {
  color: #f1f5f9;
  font-weight: 500;
  font-size: 13px;
}

/* Metric Cards */
.metric-card {
  border-radius: 12px !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.metric-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
  margin-bottom: 12px;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

html.dark .metric-value {
  color: #f1f5f9;
}

.metric-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 12px;
}

.metric-sub .divider {
  margin: 0 6px;
  color: #cbd5e1;
}

.metric-gauge {
  margin-bottom: 8px;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
}

.cpu-card { border-top: 3px solid #667eea; }
.memory-card { border-top: 3px solid #11998e; }
.disk-card { border-top: 3px solid #f39c12; }
.gpu-card { border-top: 3px solid #e74c3c; }

.network-value {
  display: flex;
  gap: 16px;
}

.network-down {
  color: #27ae60;
  display: flex;
  align-items: center;
  gap: 4px;
}

.network-up {
  color: #e74c3c;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Info Card */
.info-card {
  border-radius: 12px !important;
}

.server-info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

html.dark .info-item {
  background: #1e293b;
}

.info-item.wide {
  grid-column: span 2;
}

.info-label {
  font-size: 12px;
  color: #64748b;
}

.info-value {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

html.dark .info-value {
  color: #f1f5f9;
}

/* Chart Card */
.chart-card {
  border-radius: 12px !important;
}

/* History Card */
.history-card {
  border-radius: 12px !important;
}

.record-count {
  font-size: 12px;
  color: #94a3b8;
  font-weight: normal;
  margin-left: 8px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 13px;
}

.usage-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.usage-text {
  font-size: 12px;
  font-weight: 600;
  min-width: 45px;
  text-align: right;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

html.dark .status-card {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

html.dark .metric-card,
html.dark .info-card,
html.dark .chart-card,
html.dark .history-card {
  background: #1e293b;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

html.dark .info-item {
  background: #334155;
}

@media (max-width: 1200px) {
  .server-info-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-item.wide {
    grid-column: span 2;
  }
}

@media (max-width: 768px) {
  .server-info-grid {
    grid-template-columns: 1fr;
  }

  .info-item.wide {
    grid-column: span 1;
  }

  .status-content {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
