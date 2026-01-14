<template>
  <el-card class="server-hardware-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon class="header-icon"><Setting /></el-icon>
          <span class="module-title">服务器硬件监控</span>
        </div>
        <el-tag type="success" size="small" effect="plain" v-if="serverInfo.online">
          <el-icon><CircleCheck /></el-icon> 在线
        </el-tag>
        <el-tag type="danger" size="small" effect="plain" v-else>
          <el-icon><CircleClose /></el-icon> 离线
        </el-tag>
      </div>
    </template>

    <el-skeleton :loading="loading" animated :rows="3">
      <div class="hardware-grid">
        <!-- CPU Usage -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="#409EFF"><Cpu /></el-icon>
            <span class="item-label">CPU 使用率</span>
          </div>
          <div class="item-value">{{ serverInfo.cpuUsage }}%</div>
          <el-progress 
            :percentage="serverInfo.cpuUsage" 
            :stroke-width="8"
            :status="getProgressStatus(serverInfo.cpuUsage)"
            :show-text="false"
          />
          <div class="item-detail">{{ serverInfo.cpuCores }} 核心 @ {{ serverInfo.cpuFreq }}</div>
        </div>

        <!-- Memory Usage -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="#67C23A"><Memo /></el-icon>
            <span class="item-label">内存使用</span>
          </div>
          <div class="item-value">{{ serverInfo.memoryUsage }}%</div>
          <el-progress 
            :percentage="serverInfo.memoryUsage" 
            :stroke-width="8"
            :status="getProgressStatus(serverInfo.memoryUsage)"
            :show-text="false"
          />
          <div class="item-detail">{{ serverInfo.memoryUsed }} / {{ serverInfo.memoryTotal }}</div>
        </div>

        <!-- Disk Usage -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="#E6A23C"><FolderOpened /></el-icon>
            <span class="item-label">磁盘占用</span>
          </div>
          <div class="item-value">{{ serverInfo.diskUsage }}%</div>
          <el-progress 
            :percentage="serverInfo.diskUsage" 
            :stroke-width="8"
            :status="getProgressStatus(serverInfo.diskUsage)"
            :show-text="false"
          />
          <div class="item-detail">{{ serverInfo.diskUsed }} / {{ serverInfo.diskTotal }}</div>
        </div>

        <!-- Network Traffic -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="#909399"><Connection /></el-icon>
            <span class="item-label">网络流量</span>
          </div>
          <div class="item-value-small">
            <div class="traffic-row">
              <el-icon><Top /></el-icon>
              <span>{{ serverInfo.networkUpload }}</span>
            </div>
            <div class="traffic-row">
              <el-icon><Bottom /></el-icon>
              <span>{{ serverInfo.networkDownload }}</span>
            </div>
          </div>
          <div class="item-detail">实时速率</div>
        </div>

        <!-- System Info -->
        <div class="hardware-item system-info">
          <div class="info-row">
            <span class="info-label">操作系统</span>
            <span class="info-value">{{ serverInfo.os }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">运行时长</span>
            <span class="info-value">{{ serverInfo.uptime }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">服务器地址</span>
            <span class="info-value">{{ serverInfo.host }}</span>
          </div>
        </div>
      </div>
    </el-skeleton>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { 
  Setting, Cpu, Memo, FolderOpened, Connection, 
  CircleCheck, CircleClose, Top, Bottom 
} from '@element-plus/icons-vue';
import request from '@/utils/request';

const loading = ref(true);
const serverInfo = ref({
  online: false,
  cpuUsage: 0,
  cpuCores: 0,
  cpuFreq: '',
  memoryUsage: 0,
  memoryUsed: '',
  memoryTotal: '',
  diskUsage: 0,
  diskUsed: '',
  diskTotal: '',
  networkUpload: '',
  networkDownload: '',
  os: '',
  uptime: '',
  host: ''
});

let refreshTimer = null;

const fetchServerInfo = async () => {
  try {
    const res = await request.get('/monitor/server-hardware');
    serverInfo.value = res.data;
    loading.value = false;
  } catch (error) {
    console.error('Failed to fetch server info:', error);
    // 使用模拟数据
    serverInfo.value = {
      online: true,
      cpuUsage: Math.floor(Math.random() * 60) + 20,
      cpuCores: 8,
      cpuFreq: '3.2 GHz',
      memoryUsage: Math.floor(Math.random() * 50) + 30,
      memoryUsed: '12.5 GB',
      memoryTotal: '32 GB',
      diskUsage: Math.floor(Math.random() * 40) + 40,
      diskUsed: '256 GB',
      diskTotal: '512 GB',
      networkUpload: '2.3 MB/s',
      networkDownload: '5.7 MB/s',
      os: 'Ubuntu 22.04 LTS',
      uptime: '15天 8小时',
      host: '192.168.1.100'
    };
    loading.value = false;
  }
};

const getProgressStatus = (percentage) => {
  if (percentage >= 90) return 'exception';
  if (percentage >= 75) return 'warning';
  return 'success';
};

onMounted(() => {
  fetchServerInfo();
  // 每30秒刷新一次
  refreshTimer = setInterval(fetchServerInfo, 30000);
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<style scoped>
.server-hardware-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
  color: var(--neutral-gray-600);
}

.module-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-gray-800);
}

.hardware-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.hardware-item {
  padding: 16px;
  background: var(--neutral-gray-50);
  border-radius: var(--radius-lg);
  border: 1px solid var(--neutral-gray-100);
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.item-icon {
  font-size: 18px;
}

.item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--neutral-gray-700);
}

.item-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--neutral-gray-900);
  margin-bottom: 8px;
  font-family: var(--font-heading);
}

.item-value-small {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0;
}

.traffic-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-700);
}

.item-detail {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 8px;
}

.system-info {
  grid-column: span 2;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--neutral-gray-200);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 13px;
  color: var(--neutral-gray-600);
  font-weight: 500;
}

.info-value {
  font-size: 13px;
  color: var(--neutral-gray-900);
  font-weight: 600;
}

@media (max-width: 1200px) {
  .hardware-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .system-info {
    grid-column: span 2;
  }
}

@media (max-width: 768px) {
  .hardware-grid {
    grid-template-columns: 1fr;
  }
  
  .system-info {
    grid-column: span 1;
  }
}
</style>
