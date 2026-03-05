<template>
  <div class="server-monitor-page">
    <PageHeader title="服务器监控" icon="Monitor">
      <template #tag-content>
        <el-tag :type="hwServerInfo.online ? 'success' : 'danger'" effect="plain" size="small">
          <el-icon style="margin-right: 4px;"><component :is="hwServerInfo.online ? 'CircleCheck' : 'CircleClose'" /></el-icon>
          {{ hwServerInfo.online ? '在线' : '离线' }}
        </el-tag>
      </template>
      <template #actions>
        <el-button :icon="Refresh" @click="fetchHwData" :loading="hwLoading">刷新</el-button>
        <el-button type="primary" icon="Upload" @click="collectHwData" :loading="collecting">立即采集</el-button>
      </template>
    </PageHeader>

    <!-- 服务器信息卡片 -->
    <el-card class="premium-card margin-bottom-lg hw-server-card">
      <template #header>
        <div class="card-header">
          <el-icon><Monitor /></el-icon>
          <span>服务器详细信息</span>
        </div>
      </template>
      <div class="server-info-grid">
        <div class="server-info-item">
          <div class="info-icon"><el-icon><Monitor /></el-icon></div>
          <div class="info-content">
            <div class="info-label">操作系统</div>
            <div class="info-value">{{ hwServerInfo.os || 'Unknown' }}</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon cpu"><el-icon><Cpu /></el-icon></div>
          <div class="info-content">
            <div class="info-label">CPU型号</div>
            <div class="info-value">{{ hwServerInfo.cpuModel || 'Unknown' }}</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon"><el-icon><Odometer /></el-icon></div>
          <div class="info-content">
            <div class="info-label">CPU核心</div>
            <div class="info-value">{{ hwServerInfo.cpuCores || '-' }} 核</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon memory"><el-icon><Coin /></el-icon></div>
          <div class="info-content">
            <div class="info-label">总内存</div>
            <div class="info-value">{{ formatBytes(hwServerInfo.memoryTotal) }}</div>
          </div>
        </div>
        <div class="server-info-item wide">
          <div class="info-icon gpu"><el-icon><Star /></el-icon></div>
          <div class="info-content">
            <div class="info-label">GPU型号</div>
            <div class="info-value">{{ hwServerInfo.gpuModel || 'N/A' }}</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon gpu"><el-icon><Star /></el-icon></div>
          <div class="info-content">
            <div class="info-label">GPU显存</div>
            <div class="info-value">{{ hwServerInfo.gpuMemory || 'N/A' }}</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon disk"><el-icon><Folder /></el-icon></div>
          <div class="info-content">
            <div class="info-label">总磁盘</div>
            <div class="info-value">{{ formatBytes(hwServerInfo.diskTotal) }}</div>
          </div>
        </div>
        <div class="server-info-item">
          <div class="info-icon network"><el-icon><Connection /></el-icon></div>
          <div class="info-content">
            <div class="info-label">网络吞吐</div>
            <div class="info-value">{{ hwServerInfo.networkDownload || '-' }} ↓ / {{ hwServerInfo.networkUpload || '-' }} ↑</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Stats Cards -->
    <el-row :gutter="20" class="margin-bottom-xl" style="padding: 0 4px;">
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini-card hw-stat-card">
          <div class="stat-content">
            <div class="label text-secondary">记录总数</div>
            <div class="value">{{ hwStats.totalRecords || 0 }} <small>条</small></div>
          </div>
          <el-icon class="icon" color="var(--primary-color)"><Document /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini-card hw-stat-card">
          <div class="stat-content">
            <div class="label text-secondary">最近1小时</div>
            <div class="value">{{ hwStats.lastHourRecords || 0 }} <small>条</small></div>
          </div>
          <el-icon class="icon" color="var(--success-color)"><Clock /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini-card hw-stat-card hw-usage-card">
          <div class="stat-content">
            <div class="label text-secondary">当前CPU</div>
            <div class="value">{{ (hwCurrent.cpuUsage || 0).toFixed(1) }}<small>%</small></div>
            <div class="progress-ring">
              <el-progress type="circle" :percentage="hwCurrent.cpuUsage || 0" :width="50" :stroke-width="4" :color="getUsageColor(hwCurrent.cpuUsage)" />
            </div>
          </div>
          <el-icon class="icon" color="var(--warning-color)"><TrendCharts /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-mini-card hw-stat-card hw-usage-card">
          <div class="stat-content">
            <div class="label text-secondary">当前GPU</div>
            <div class="value">{{ (hwCurrent.gpuUsage || 0).toFixed(1) }}<small>%</small></div>
            <div class="progress-ring">
              <el-progress type="circle" :percentage="hwCurrent.gpuUsage || 0" :width="50" :stroke-width="4" :color="getUsageColor(hwCurrent.gpuUsage)" />
            </div>
          </div>
          <el-icon class="icon" color="#e74c3c"><Monitor /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- Chart -->
    <el-card class="premium-card margin-bottom-lg">
      <template #header>
        <div class="card-header">
          <el-icon><TrendCharts /></el-icon>
          <span>硬件资源使用趋势</span>
        </div>
        <div>
          <el-radio-group v-model="hwPeriod" size="small" @change="fetchHwTrend">
            <el-radio-button label="5m">5分钟</el-radio-button>
            <el-radio-button label="1h">1小时</el-radio-button>
            <el-radio-button label="24h">24小时</el-radio-button>
            <el-radio-button label="7d">7天</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <div v-loading="hwLoading" style="height: 350px;">
        <LineChart v-if="hwTrendData.length > 0" :data="hwTrendData" title="硬件资源使用趋势" yAxisName="使用率 (%)" height="320px" :color="'#409EFF'" />
        <el-empty v-else description="暂无数据，请确保 Prometheus 已配置并开启监控" />
      </div>
    </el-card>

    <!-- History Table -->
    <el-card class="premium-card hw-history-card">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>历史记录</span>
          <span class="record-count">共 {{ hwTotal }} 条记录</span>
        </div>
      </template>

      <el-table :data="hwHistory" v-loading="hwLoading" style="width: 100%">
        <el-table-column label="时间" min-width="180" fixed>
          <template #default="{ row }">
            <div class="time-cell">
              <el-icon><Clock /></el-icon>
              {{ formatDateTime(row.recordedAt || row.timestamp) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="CPU" min-width="150" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <div class="usage-bar-bg">
                <div class="usage-bar-fill" :style="{ width: (row.cpuUsage || 0) + '%', background: getUsageGradient(row.cpuUsage) }"></div>
              </div>
              <span class="usage-text" :class="getUsageClass(row.cpuUsage)">{{ (row.cpuUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内存" min-width="150" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <div class="usage-bar-bg">
                <div class="usage-bar-fill" :style="{ width: (row.memoryUsage || 0) + '%', background: getUsageGradient(row.memoryUsage) }"></div>
              </div>
              <span class="usage-text" :class="getUsageClass(row.memoryUsage)">{{ (row.memoryUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="磁盘" min-width="150" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <div class="usage-bar-bg">
                <div class="usage-bar-fill" :style="{ width: (row.diskUsage || 0) + '%', background: getUsageGradient(row.diskUsage) }"></div>
              </div>
              <span class="usage-text" :class="getUsageClass(row.diskUsage)">{{ (row.diskUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="GPU" min-width="150" align="center">
          <template #default="{ row }">
            <div class="usage-cell">
              <div class="usage-bar-bg">
                <div class="usage-bar-fill" :style="{ width: (row.gpuUsage || 0) + '%', background: getUsageGradient(row.gpuUsage) }"></div>
              </div>
              <span class="usage-text" :class="getUsageClass(row.gpuUsage)">{{ (row.gpuUsage || 0).toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="hwPage"
          v-model:page-size="hwPageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="hwTotal"
          @size-change="fetchHwHistory"
          @current-change="fetchHwHistory"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Cpu, Clock, TrendCharts, Monitor, Odometer, Star, Folder, Connection, Refresh, Document, List, Coin, CircleCheck, CircleClose } from '@element-plus/icons-vue';
import LineChart from '@/components/LineChart.vue';
import PageHeader from '@/components/PageHeader.vue';
import { getServerHardwareHistory, getServerHardwareTrend, getServerHardwareStats, collectServerHardware } from '@/api/monitor';
import { ElMessage } from 'element-plus';

// State
const hwLoading = ref(false);
const collecting = ref(false);
const hwPeriod = ref('1h');
const hwTrendData = ref([]);
const hwHistory = ref([]);
const hwStats = ref({ totalRecords: 0, lastHourRecords: 0 });
const hwCurrent = ref({ cpuUsage: 0, gpuUsage: 0 });
const hwServerInfo = ref({
  os: '', cpuModel: '', cpuCores: 0, memoryTotal: 0,
  gpuModel: '', gpuMemory: '', diskTotal: 0,
  networkDownload: '', networkUpload: '', online: false
});
const hwPage = ref(1);
const hwPageSize = ref(20);
const hwTotal = ref(0);

// Fetch data
const fetchHwTrend = async () => {
  try {
    const res = await getServerHardwareTrend(hwPeriod.value);
    hwTrendData.value = res.map(item => ({
      timestamp: item.timestamp,
      value: item.cpuUsage || 0,
      memoryUsage: item.memoryUsage || 0,
      diskUsage: item.diskUsage || 0,
      gpuUsage: item.gpuUsage || 0
    }));
  } catch (e) {
    console.error('Failed to fetch hardware trend:', e);
    hwTrendData.value = [];
  }
};

const fetchHwHistory = async () => {
  hwLoading.value = true;
  try {
    const res = await getServerHardwareHistory({
      page: hwPage.value - 1,
      size: hwPageSize.value
    });
    hwHistory.value = res.content || [];
    hwTotal.value = res.totalElements || 0;
  } catch (e) {
    console.error('Failed to fetch hardware history:', e);
  } finally {
    hwLoading.value = false;
  }
};

const fetchHwStats = async () => {
  try {
    const res = await getServerHardwareStats();
    hwStats.value = res;
    if (res.current) {
      hwCurrent.value = res.current;
    }
    if (res.serverInfo) {
      hwServerInfo.value = res.serverInfo;
    }
  } catch (e) {
    console.error('Failed to fetch hardware stats:', e);
  }
};

const fetchHwData = async () => {
  hwLoading.value = true;
  try {
    await Promise.all([fetchHwTrend(), fetchHwHistory(), fetchHwStats()]);
  } finally {
    hwLoading.value = false;
  }
};

const collectHwData = async () => {
  collecting.value = true;
  try {
    await collectServerHardware();
    ElMessage.success('数据采集成功');
    await fetchHwData();
  } catch (e) {
    console.error('采集失败:', e);
    ElMessage.error('数据采集失败: ' + (e.response?.data?.message || e.message));
  } finally {
    collecting.value = false;
  }
};

// Helpers
const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '-';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
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

const getUsageGradient = (val) => {
  const v = Number(val) || 0;
  if (v > 80) return 'linear-gradient(90deg, #e74c3c, #c0392b)';
  if (v > 60) return 'linear-gradient(90deg, #f39c12, #e67e22)';
  return 'linear-gradient(90deg, #27ae60, #2ecc71)';
};

const getUsageClass = (val) => {
  const v = Number(val) || 0;
  if (v > 80) return 'danger';
  if (v > 60) return 'warning';
  return 'success';
};

// Lifecycle
onMounted(() => {
  fetchHwData();
});
</script>

<style scoped>
.server-monitor-page {
  padding: 24px;
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  background: rgba(255, 255, 255, 0.6) !important;
  backdrop-filter: blur(15px);
}

html.dark .premium-card {
  background: rgba(30, 41, 59, 0.6) !important;
  border-color: rgba(255, 255, 255, 0.05) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

/* Server Info Card */
.hw-server-card .server-info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.hw-server-card .server-info-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
}

html.dark .hw-server-card .server-info-item {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
}

.hw-server-card .server-info-item.wide {
  grid-column: span 2;
}

.hw-server-card .info-icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-color);
  border-radius: 10px;
  color: white;
  font-size: 20px;
}

.hw-server-card .info-icon.cpu { background: linear-gradient(135deg, #667eea, #764ba2); }
.hw-server-card .info-icon.memory { background: linear-gradient(135deg, #11998e, #38ef7d); }
.hw-server-card .info-icon.gpu { background: linear-gradient(135deg, #e74c3c, #c0392b); }
.hw-server-card .info-icon.disk { background: linear-gradient(135deg, #f39c12, #f1c40f); }
.hw-server-card .info-icon.network { background: linear-gradient(135deg, #3498db, #2980b9); }

.hw-server-card .info-content { flex: 1; min-width: 0; }
.hw-server-card .info-label { font-size: 12px; color: var(--neutral-gray-500); margin-bottom: 4px; }
.hw-server-card .info-value { font-size: 13px; font-weight: 600; color: var(--neutral-gray-800); }

/* Stats Card */
.hw-stat-card {
  position: relative;
  overflow: hidden;
}

.hw-stat-card .stat-content .label {
  font-size: 12px;
  margin-bottom: 8px;
}

.hw-stat-card .stat-content .value {
  font-size: 24px;
  font-weight: 700;
}

.hw-stat-card .stat-content .value small {
  font-size: 12px;
  font-weight: normal;
}

.hw-stat-card .icon {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 40px;
  opacity: 0.3;
}

.hw-stat-card.hw-usage-card .stat-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hw-stat-card.hw-usage-card .progress-ring {
  margin-top: 12px;
}

/* History Table */
.hw-history-card .record-count {
  font-size: 12px;
  color: var(--neutral-gray-500);
  font-weight: normal;
  margin-left: 8px;
}

.hw-history-card .time-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--neutral-gray-600);
  font-size: 13px;
}

.hw-history-card .time-cell .el-icon {
  color: var(--primary-color);
}

.hw-history-card .usage-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hw-history-card .usage-bar-bg {
  flex: 1;
  height: 6px;
  background: var(--neutral-gray-200);
  border-radius: 3px;
  overflow: hidden;
}

.hw-history-card .usage-bar-fill {
  height: 100%;
  border-radius: 3px;
}

.hw-history-card .usage-text {
  font-size: 12px;
  font-weight: 600;
  min-width: 45px;
  text-align: right;
}

.hw-history-card .usage-text.success { color: #27ae60; }
.hw-history-card .usage-text.warning { color: #f39c12; }
.hw-history-card .usage-text.danger { color: #e74c3c; }

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.margin-bottom-lg {
  margin-bottom: 24px;
}

.margin-bottom-xl {
  margin-bottom: 32px;
}
</style>
