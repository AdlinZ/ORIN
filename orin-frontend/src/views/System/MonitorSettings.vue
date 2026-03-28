<template>
  <div class="page-container">
    <PageHeader
      title="系统环境配置"
      description="配置系统硬件监控、环境变量以及告警阈值"
      icon="Tools"
    >
      <template #actions>
        <el-button
          type="primary"
          :loading="saving"
          :icon="Check"
          @click="saveConfig"
        >
          保存全局配置
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 硬件监控数据源 Tab -->
      <el-tab-pane label="硬件监控数据源" name="prometheus">
        <el-row :gutter="24">
          <el-col :lg="14">
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Connection /></el-icon>
                  <span>Prometheus 配置</span>
                </div>
              </template>
              
              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="启用硬件监控服务">
                  <div class="flex-between w-100">
                    <div class="form-info">
                      <div class="form-label-desc">
                        开启后，后台将尝试从指定的 Prometheus 实例拉取 CPU、内存、路盘等指标。
                      </div>
                    </div>
                    <el-switch v-model="config.enabled" />
                  </div>
                </el-form-item>

                <el-divider border-style="dashed" />

                <el-form-item v-if="config.enabled" label="Prometheus 服务器地址">
                  <el-input 
                    v-model="config.prometheusUrl" 
                    placeholder="例如: http://192.168.1.107:9090"
                    class="url-input"
                  >
                    <template #prepend>
                      http(s)://
                    </template>
                  </el-input>
                  <p class="form-tip">
                    请确保后台服务能够网络通达。如果是 Docker 环境，请使用宿主机 IP 或容器专用网络地址。
                  </p>
                </el-form-item>
                
                <el-form-item v-if="config.enabled">
                  <el-button
                    :loading="testing"
                    type="primary"
                    plain
                    size="small"
                    @click="testConnection"
                  >
                    测试连接响应
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><InfoFilled /></el-icon>
                  <span>数据采集策略</span>
                </div>
              </template>
              <div class="strategy-list">
                <div class="strategy-item">
                  <div class="item-title">
                    后端缓存周期
                  </div>
                  <el-input-number 
                    v-model="config.cacheTtl" 
                    :min="5" 
                    :max="300" 
                    :step="5"
                    size="small"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px; color: var(--neutral-gray-600);">秒</span>
                  <div class="item-desc">
                    为了降低对 Prometheus 的请求压力，后端会对硬件数据进行短时缓存。
                  </div>
                </div>
                <div class="strategy-item">
                  <div class="item-title">
                    前端刷新频率
                  </div>
                  <el-input-number 
                    v-model="config.refreshInterval" 
                    :min="5" 
                    :max="300" 
                    :step="5"
                    size="small"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px; color: var(--neutral-gray-600);">秒</span>
                  <div class="item-desc">
                    监控看板会每隔指定秒数主动请求一次后端接口。
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>

          <el-col :lg="10">
            <el-card class="premium-card guide-card">
              <template #header>
                <div class="card-header">
                  <el-icon><QuestionFilled /></el-icon>
                  <span>配置指南</span>
                </div>
              </template>
              <div class="guide-content">
                <div class="guide-step">
                  <span class="step-num">1</span>
                  <div class="step-text">
                    <strong>安装 Node Exporter (Ubuntu)</strong>
                    <pre class="install-cmd"># 下载 Node Exporter (以 v1.7.0 为例)
wget https://github.com/prometheus/node_exporter/releases/download/v1.7.0/node_exporter-1.7.0.linux-amd64.tar.gz

# 解压
tar xzf node_exporter-1.7.0.linux-amd64.tar.gz

# 移动二进制文件
sudo cp node_exporter-1.7.0.linux-amd64/node_exporter /usr/local/bin/

# 创建专用用户
sudo useradd -rs /bin/false node_exporter</pre>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">2</span>
                  <div class="step-text">
                    <strong>配置 systemd 服务</strong>
                    <pre class="install-cmd">sudo nano /etc/systemd/system/node_exporter.service

# 写入以下内容:
[Unit]
Description=Node Exporter
After=network.target

[Service]
Type=simple
User=node_exporter
ExecStart=/usr/local/bin/node_exporter
Restart=always

[Install]
WantedBy=multi-user.target

# 启动服务
sudo systemctl daemon-reload
sudo systemctl enable node_exporter
sudo systemctl start node_exporter

# 验证 (默认端口 9100)
curl http://localhost:9100/metrics | head</pre>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">3</span>
                  <div class="step-text">
                    <strong>配置 Prometheus 抓取</strong>
                    <pre class="install-cmd">sudo nano /etc/prometheus/prometheus.yml

# 在 scrape_configs 下添加:
  - job_name: 'node_exporter'
    static_configs:
      - targets: ['localhost:9100']

# 重启 Prometheus
sudo systemctl restart prometheus</pre>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">4</span>
                  <div class="step-text">
                    <strong>填入 API 地址</strong>
                    <p>将 Prometheus 地址 (默认 http://localhost:9090) 填入左侧表单并保存。</p>
                  </div>
                </div>
              </div>
              
              <el-alert
                title="提示：系统目前支持 Linux node_exporter 和 Windows windows_exporter 的标准指标。"
                type="info"
                :closable="false"
                show-icon
                style="margin-top: 20px;"
              />

              <!-- NVIDIA GPU 监控说明 -->
              <el-collapse style="margin-top: 20px;">
                <el-collapse-item title="NVIDIA GPU 监控配置 (可选)" name="nvidia">
                  <div class="nvidia-guide">
                    <p>如需监控 NVIDIA GPU 指标，需要额外配置 GPU exporter：</p>

                    <div class="guide-step">
                      <span class="step-num">1</span>
                      <div class="step-text">
                        <strong>安装 DCGM Exporter (推荐)</strong>
                        <pre class="install-cmd"># 使用 Helm 安装 (Kubernetes)
helm repo add gpu-operator https://nvidia.github.io/gpu-operator
helm install dcgm-exporter gpu-operator/dcgm-exporter

# 或使用 Docker 直接运行
docker run -d --gpus all --rm -p 9400:9400 nvidia/dcgm-exporter:latest</pre>
                      </div>
                    </div>

                    <div class="guide-step">
                      <span class="step-num">2</span>
                      <div class="step-text">
                        <strong>或使用 nvidia_gpu_exporter</strong>
                        <pre class="install-cmd"># 下载并运行
wget https://github.com/mindprince/nvidia_gpu_exporter/releases/download/v1.0.0/nvidia_gpu_exporter_1.0.0_linux_amd64.tar.gz
tar -xzf nvidia_gpu_exporter_1.0.0_linux_amd64.tar.gz
./nvidia_gpu_exporter</pre>
                      </div>
                    </div>

                    <div class="guide-step">
                      <span class="step-num">3</span>
                      <div class="step-text">
                        <strong>在 Prometheus 中添加 GPU job</strong>
                        <pre class="install-cmd"># 添加到 prometheus.yml
  - job_name: 'nvidia_gpu'
    static_configs:
      - targets: ['localhost:9400']  # DCGM Exporter 默认端口</pre>
                      </div>
                    </div>

                    <el-alert
                      title="注意：ORIN 支持的 GPU 指标查询包括：nvidia_smi_utilization_gpu_ratio, nvidia_smi_memory_used_bytes, nvidia_smi_gpu_info 等。"
                      type="warning"
                      :closable="false"
                      show-icon
                      style="margin-top: 10px;"
                    />
                  </div>
                </el-collapse-item>
              </el-collapse>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 外部连接配置 -->
      <el-tab-pane label="外部依赖环境 (全局架构)" name="env">
        <el-row :gutter="24">
          <el-col :lg="24">
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Connection /></el-icon>
                  <span>核心中间件与外部服务地址池</span>
                </div>
              </template>
              <el-alert 
                title="高危操作警告：以下配置项是支撑 ORIN 智能体骨干通讯的关键组件参数，如无必要请勿修改。修改后需在服务器内执行 ./manage.sh restart -b 才能应用到底层连接池。" 
                type="warning"
                show-icon
                :closable="false"
                style="margin-bottom: 24px"
              />
                
              <el-form label-position="left" label-width="180px">
                <el-divider content-position="left">
                  🗄️ MySQL 关系型数据库 (核心主库)
                </el-divider>
                <el-form-item label="MySQL Host/Port" style="font-family: monospace;">
                  <div style="display:flex; gap: 10px; width: 100%">
                    <el-input v-model="envConfig['spring.datasource.url']" placeholder="例如: jdbc:mysql://localhost:3306/orindb..." style="flex:1" />
                  </div>
                </el-form-item>
                <el-form-item label="MySQL Username">
                  <el-input v-model="envConfig['spring.datasource.username']" />
                </el-form-item>
                <el-form-item label="MySQL Password">
                  <el-input v-model="envConfig['spring.datasource.password']" type="password" show-password />
                </el-form-item>

                <el-divider content-position="left">
                  ⚡ Redis 分布式高速缓存
                </el-divider>
                <el-form-item label="Redis Host">
                  <el-input v-model="envConfig['spring.data.redis.host']" />
                </el-form-item>
                <el-form-item label="Redis Port">
                  <el-input v-model="envConfig['spring.data.redis.port']" />
                </el-form-item>
                <el-form-item label="Redis Password">
                  <el-input v-model="envConfig['spring.data.redis.password']" type="password" show-password />
                </el-form-item>

                <el-divider content-position="left">
                  🌐 SiliconFlow (应急算力降级池)
                </el-divider>
                <el-form-item label="Silicon API Key">
                  <el-input v-model="envConfig['siliconflow.api.key']" type="password" show-password />
                </el-form-item>
                <el-form-item label="Silicon Base URL">
                  <el-input v-model="envConfig['siliconflow.api.base-url']" />
                </el-form-item>

                <el-divider content-position="left">
                  📖 Jina Reader (网页转Markdown)
                </el-divider>
                <el-form-item label="启用 Jina Reader">
                  <el-switch v-model="envConfig['jina.reader.enabled']" />
                </el-form-item>
                <el-form-item label="Jina API Key">
                  <el-input
                    v-model="envConfig['jina.reader.api-key']"
                    type="password"
                    show-password
                    placeholder="可选，无Key时20次/分钟"
                  />
                </el-form-item>
              </el-form>
              <div style="text-align: right">
                <el-button type="primary" :loading="envSaving" @click="saveEnvConfig">
                  执行覆盖保存
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 告警阈值配置 (占位) -->
      <el-tab-pane label="监控告警阈值" name="alerts" disabled>
        <!-- Future development -->
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Check, Connection, InfoFilled, 
  QuestionFilled
} from '@element-plus/icons-vue';

const activeTab = ref('prometheus');
const saving = ref(false);
const testing = ref(false);
const config = reactive({
  prometheusUrl: '',
  enabled: false,
  cacheTtl: 10,  // 后端缓存周期（秒）
  refreshInterval: 15  // 前端刷新频率（秒）
});

const loadConfig = async () => {
  try {
    const res = await request.get('/monitor/prometheus/config');
    // Note: Use object itself as fixed in investigation
    if (res) {
      config.prometheusUrl = res.prometheusUrl || '';
      config.enabled = res.enabled || false;
      config.cacheTtl = res.cacheTtl || 10;
      config.refreshInterval = res.refreshInterval || 15;
    }
  } catch (error) {
    ElMessage.error('加载监控配置失败');
  }
};

const saveConfig = async () => {
    saving.value = true;
    try {
        await request.post('/monitor/prometheus/config', config);
        ElMessage.success('配置已生效并保存');
        // Force reload to confirm persistence
        await loadConfig();
    } catch (error) {
        if (error.response && error.response.status === 401) {
             ElMessage.error('保存失败：登录已过期，请重新登录');
        } else {
             ElMessage.error('保存失败: ' + error.message);
        }
    } finally {
        saving.value = false;
    }
};

  const testConnection = async () => {
  testing.value = true;
  try {
    // Use the dedicated test endpoint
    const res = await request.get('/monitor/prometheus/test');
    console.log('Test Connection Response:', res);
    if (res.probedUrl) {
        console.log('Backend actually probed this URL:', res.probedUrl);
    }
    
    if (res.online) {
      ElMessage.success('连接成功！Prometheus 响应正常。');
    } else {
      ElMessage.warning('连接测试失败: ' + (res.error || '无法解析数据'));
    }
  } catch (e) {
    console.error('Test Connection Error:', e);
    ElMessage.error('测试失败: ' + e.message);
  } finally {
    testing.value = false;
  }
};

const envConfig = ref({});
const envSaving = ref(false);

const loadEnvConfig = async () => {
    try {
        const res = await request.get('/monitor/system/properties');
        if (res) {
            envConfig.value = res;
        }
    } catch(e) { 
        console.error("Failed to load environment system properties:", e);
    }
};

const saveEnvConfig = async () => {
    envSaving.value = true;
    try {
        await request.post('/monitor/system/properties', envConfig.value);
        ElMessage.success('外部依赖环境配置已成功注入底层 properties 文件！请记得适时重启系统生效！');
    } catch(e) {
        ElMessage.error('配置注入写入失败: ' + e.message);
    } finally {
        envSaving.value = false;
    }
};

onMounted(() => {
    loadConfig();
    loadEnvConfig();
});
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-200) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  color: var(--neutral-gray-800);
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 10px;
  line-height: 1.5;
}

.form-info {
  flex: 1;
  padding-right: 20px;
}

.form-label-desc {
  font-size: 13px;
  color: var(--neutral-gray-600);
  line-height: 1.4;
}

.w-100 { width: 100%; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.margin-bottom-lg { margin-bottom: 24px; }

.config-tabs {
  margin-top: 24px;
}

.url-input :deep(.el-input-group__prepend) {
  background-color: var(--neutral-gray-50);
  color: var(--neutral-gray-500);
  font-weight: 600;
}

.strategy-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.strategy-item {
  padding: 12px;
  background: var(--neutral-gray-50);
  border-radius: var(--radius-lg);
  border: 1px solid var(--neutral-gray-200);
}

.item-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-gray-800);
  margin-bottom: 4px;
}

.item-value {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  color: var(--primary-color);
  background: var(--neutral-white);
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  border: 1px solid var(--neutral-gray-200);
}

.item-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
  line-height: 1.4;
}

.guide-step {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.step-num {
  width: 24px;
  height: 24px;
  background: var(--orin-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-text strong {
  display: block;
  font-size: 14px;
  color: var(--neutral-gray-800);
  margin-bottom: 4px;
}

.step-text p {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin: 0;
  line-height: 1.4;
}

.install-cmd {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 8px 0 0 0;
}

.feature-switches {
    display: flex;
    gap: 12px;
    margin-top: 8px;
}

.status-result {
    margin-left: 12px;
    font-size: 12px;
    font-weight: 700;
}
.status-result.success { color: #10b981; }
.status-result.error { color: #ef4444; }

.ai-provider-select {
  margin: 12px 0;
}

.provider-name {
  font-weight: 600;
}

.ai-status-card {
  margin-top: 16px;
  padding: 16px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-200);
}

.ai-status-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--neutral-gray-100);
}

.ai-status-item:last-child {
  border-bottom: none;
}

.ai-status-item .label {
  color: var(--neutral-gray-600);
  font-size: 13px;
}

.ai-status-item .value {
  font-weight: 600;
  font-size: 13px;
}

.ai-status-item .value.active {
  color: #10b981;
}

.actions-row {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px dotted #eee;
}

.text-success { color: #10b981; }
.text-danger { color: #ef4444; }
</style>
