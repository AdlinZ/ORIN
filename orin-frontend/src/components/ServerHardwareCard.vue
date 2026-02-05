<template>
  <el-card class="server-hardware-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon class="header-icon"><Cpu /></el-icon>
          <span class="module-title">服务器硬件监控</span>
        </div>
        <div class="header-right">
          <span v-if="serverInfo.lastUpdated" class="last-updated">
            更新于 {{ new Date(serverInfo.lastUpdated).toLocaleTimeString() }}
          </span>
          
          <el-tag 
            v-if="serverInfo.online" 
            type="success" 
            size="small" 
            effect="plain" 
            class="status-tag"
          >
            在线
          </el-tag>
          
          <el-tag 
            v-else 
            type="info" 
            size="small" 
            effect="plain" 
            class="status-tag"
          >
            离线
          </el-tag>

          <div class="actions">
            <el-button 
              :icon="Setting" 
              circle 
              size="small" 
              @click="openConfig"
            />
            <el-button 
              :icon="Refresh" 
              circle 
              size="small" 
              :loading="loading"
              @click="fetchServerInfo"
            />
          </div>
        </div>
      </div>
    </template>

    <el-skeleton :loading="loading" animated :rows="3">
      <div v-if="!serverInfo.online && !loading" class="offline-placeholder">
        <el-empty description="Prometheus 未配置或无法连接">
           <p v-if="serverInfo.error" class="error-text">{{ serverInfo.error }}</p>
           <el-button type="primary" @click="openConfig">去配置</el-button>
        </el-empty>
      </div>
      <div v-else class="hardware-grid">
        <!-- CPU Usage -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="var(--orin-primary)"><Cpu /></el-icon>
            <span class="item-label">CPU 使用率</span>
          </div>
          <div class="item-value">{{ serverInfo.cpuUsage }}%</div>
          <el-progress 
            :percentage="serverInfo.cpuUsage" 
            :stroke-width="8"
            :status="getProgressStatus(serverInfo.cpuUsage)"
            :show-text="false"
          />
          <div class="item-detail">{{ serverInfo.cpuCores }} 核心 @ {{ serverInfo.cpuModel || 'N/A' }}</div>
        </div>

        <!-- GPU Usage (Optional) -->
        <!-- GPU Usage -->
        <div class="hardware-item">
          <div class="item-header">
            <el-icon class="item-icon" color="#7B1FA2"><VideoPlay /></el-icon>
            <span class="item-label">GPU 使用率</span>
          </div>
          <div class="item-value">{{ serverInfo.gpuUsage }}%</div>
          <el-progress 
            :percentage="serverInfo.gpuUsage" 
            :stroke-width="8"
            :status="getProgressStatus(serverInfo.gpuUsage)"
            :show-text="false"
          />
          <div class="item-detail">
            <div>{{ (serverInfo.gpuModel && serverInfo.gpuModel !== 'N/A' && serverInfo.gpuModel !== 'Unknown') ? serverInfo.gpuModel : '无 GPU 数据' }}</div>
            <div v-if="serverInfo.gpuMemory && serverInfo.gpuMemory !== 'N/A'">{{ serverInfo.gpuMemory }}</div>
          </div>
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
          <div class="item-detail">{{ serverInfo.memoryUsed || '0' }} / {{ serverInfo.memoryTotal || '0' }}</div>
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
          <div class="item-detail">{{ serverInfo.diskUsed || '0' }} / {{ serverInfo.diskTotal || '0' }}</div>
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
              <span>{{ serverInfo.networkUpload || '0 KB/s' }}</span>
            </div>
            <div class="traffic-row">
              <el-icon><Bottom /></el-icon>
              <span>{{ serverInfo.networkDownload || '0 KB/s' }}</span>
            </div>
          </div>
          <div class="item-detail">实时速率</div>
        </div>

        <!-- System Info -->
        <div class="hardware-item system-info">
          <div class="info-row">
            <span class="info-label">操作系统</span>
            <span class="info-value">{{ serverInfo.os || 'Unknown' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">采集来源</span>
            <span class="info-value">Prometheus ({{ getExporterType() }})</span>
          </div>
          <div class="info-row">
            <span class="info-label">监控实例</span>
            <span class="info-value">{{ serverInfo.host || 'Not set' }}</span>
          </div>
        </div>
      </div>
    </el-skeleton>

  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { 
  Setting, Cpu, Memo, FolderOpened, Connection, 
  SuccessFilled, CircleCloseFilled, Top, Bottom, Refresh, VideoPlay 
} from '@element-plus/icons-vue';
import request from '@/utils/request';
import { ElMessage } from 'element-plus';

const router = useRouter();
const loading = ref(true);
const serverInfo = ref({
  online: false,
  cpuUsage: 0,
  cpuCores: 0,
  cpuModel: '', 
  gpuUsage: 0,
  gpuMemoryUsage: 0,
  gpuModel: '',
  memoryUsage: 0,
  diskUsage: 0,
  os: '',
  host: '',
  lastUpdated: null,
  error: null
});

let refreshTimer = null;

const openConfig = () => {
  router.push('/dashboard/system/monitor-config');
};

// Helper function to extract concise error message
const getErrorMessage = (error) => {
  let msg = '';
  
  // Get the raw message
  if (error.response && error.response.data) {
    msg = typeof error.response.data === 'string' ? error.response.data : JSON.stringify(error.response.data);
  } else if (error.message) {
    msg = error.message;
  }
  
  // Check for HTTP status codes from response
  if (error.response) {
    const status = error.response.status;
    if (status === 401) return '登录凭证已失效';
    if (status === 403) return '无权限访问';
    if (status === 404) return 'Prometheus 接口未找到';
    if (status === 503) return 'Prometheus 服务不可用';
    if (status >= 500) return '服务器错误';
  }
  
  // Extract meaningful part from backend error messages
  // Format: "连接失败: Probe failed: 503 Service Unavailable: "<!DOCTYPE html>..."
  if (msg.includes('连接失败:') || msg.includes('Probe failed:')) {
    // Extract the part before HTML content
    const parts = msg.split(':');
    if (parts.length >= 2) {
      // Get first two parts: "连接失败" and "Probe failed"
      const meaningful = parts.slice(0, 2).join(':').trim();
      if (meaningful.includes('Probe failed')) {
        return 'Prometheus 连接失败，请检查配置';
      }
      return meaningful;
    }
  }
  
  // Check error message for common patterns
  if (msg.includes('401')) return '登录凭证已失效';
  if (msg.includes('503') || msg.includes('Service Unavailable')) return 'Prometheus 服务不可用';
  if (msg.includes('Network Error') || msg.includes('timeout')) return '网络连接超时';
  
  // For HTML responses or very long messages, show generic error
  if (msg.includes('<!DOCTYPE') || msg.includes('<html') || msg.length > 150) {
    return 'Prometheus 连接失败，请检查配置';
  }
  
  // Return original message if it's short and meaningful
  if (msg.length > 0 && msg.length <= 100) {
    return msg;
  }
  
  return 'Prometheus 未配置或无法连接';
};

const fetchServerInfo = async () => {
  try {
    const res = await request.get('/monitor/server-hardware');
    // Note: res is already the data object because of axios interceptor
    if (res) {
      serverInfo.value = {
        ...res,
        lastUpdated: Date.now()
      };
      // Save local cache
      localStorage.setItem('orin_server_hardware', JSON.stringify(serverInfo.value));
    }
  } catch (error) {
    console.error('Failed to fetch server info:', error);
    serverInfo.value.online = false;
    serverInfo.value.error = getErrorMessage(error);
  } finally {
    loading.value = false;
  }
};

const getExporterType = () => {
  const os = serverInfo.value.os || '';
  if (os.toLowerCase().includes('windows')) {
    return 'Windows Exporter';
  } else if (os.toLowerCase().includes('linux') || os.toLowerCase().includes('ubuntu') || os.toLowerCase().includes('debian') || os.toLowerCase().includes('centos')) {
    return 'Node Exporter';
  }
  return 'Exporter';
};

const getProgressStatus = (percentage) => {
  if (percentage >= 90) return 'exception';
  if (percentage >= 75) return 'warning';
  return 'success';
};

onMounted(async () => {
  // Load cache first
  const cached = localStorage.getItem('orin_server_hardware');
  if (cached) {
    try {
      serverInfo.value = JSON.parse(cached);
      loading.value = false; // Show cached data immediately
    } catch (e) { /* ignore */ }
  }

  // Fetch config to get refresh interval
  let refreshIntervalMs = 15000; // Default 15 seconds
  try {
    const config = await request.get('/monitor/prometheus/config');
    if (config && config.refreshInterval) {
      refreshIntervalMs = config.refreshInterval * 1000;
    }
  } catch (e) {
    console.warn('Failed to load refresh interval config, using default 15s');
  }

  fetchServerInfo();
  // Use configured refresh interval
  refreshTimer = setInterval(fetchServerInfo, refreshIntervalMs);
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

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
  line-height: 1.4;
}

.offline-placeholder {
  padding: 40px 0;
  background: var(--neutral-gray-50);
  border-radius: var(--radius-lg);
  border: 1px dashed var(--neutral-gray-300);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-updated {
  font-size: 12px;
  color: var(--neutral-gray-400);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.actions {
  display: flex;
  gap: 8px;
}


.error-text {
  color: var(--error-color, #F56C6C);
  font-size: 13px;
  margin-bottom: 12px;
  max-width: 300px;
  text-align: center;
}
</style>
