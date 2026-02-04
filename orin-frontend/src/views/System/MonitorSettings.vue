<template>
  <div class="page-container">
    <PageHeader 
      title="监控系统配置" 
      description="配置系统硬件监控、Prometheus 数据源以及告警阈值"
      icon="Monitor"
    >
      <template #actions>
        <el-button type="primary" :loading="saving" :icon="Check" @click="saveConfig">
          保存全局配置
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 基础监控配置 Tab -->
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
                      <div class="form-label-desc">开启后，后台将尝试从指定的 Prometheus 实例拉取 CPU、内存、路盘等指标。</div>
                    </div>
                    <el-switch v-model="config.enabled" />
                  </div>
                </el-form-item>

                <el-divider border-style="dashed" />

                <el-form-item label="Prometheus 服务器地址" v-if="config.enabled">
                  <el-input 
                    v-model="config.prometheusUrl" 
                    placeholder="例如: http://192.168.1.107:9090"
                    class="url-input"
                  >
                    <template #prepend>http(s)://</template>
                  </el-input>
                  <p class="form-tip">
                    请确保后台服务能够网络通达。如果是 Docker 环境，请使用宿主机 IP 或容器专用网络地址。
                  </p>
                </el-form-item>
                
                <el-form-item v-if="config.enabled">
                   <el-button @click="testConnection" :loading="testing" type="primary" plain size="small">
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
                  <div class="item-title">后端缓存周期</div>
                  <el-input-number 
                    v-model="config.cacheTtl" 
                    :min="5" 
                    :max="300" 
                    :step="5"
                    size="small"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px; color: var(--neutral-gray-600);">秒</span>
                  <div class="item-desc">为了降低对 Prometheus 的请求压力，后端会对硬件数据进行短时缓存。</div>
                </div>
                <div class="strategy-item">
                   <div class="item-title">前端刷新频率</div>
                   <el-input-number 
                     v-model="config.refreshInterval" 
                     :min="5" 
                     :max="300" 
                     :step="5"
                     size="small"
                     style="width: 120px"
                   />
                   <span style="margin-left: 8px; color: var(--neutral-gray-600);">秒</span>
                   <div class="item-desc">监控看板会每隔指定秒数主动请求一次后端接口。</div>
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
                    <strong>安装 Exporter</strong>
                    <p>在目标服务器安装 Node Exporter (Linux) 或 Windows Exporter。</p>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">2</span>
                  <div class="step-text">
                    <strong>配置 Prometheus</strong>
                    <p>修改 prometheus.yml，添加抓取任务并重启服务。</p>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">3</span>
                  <div class="step-text">
                    <strong>填入 API 地址</strong>
                    <p>将 Prometheus 的访问 URL (默认 9090 端口) 填入左侧表单并保存。</p>
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
  Monitor, Check, Connection, InfoFilled, 
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

onMounted(() => {
    loadConfig();
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
  border: 1px solid var(--neutral-gray-100) !important;
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
  border: 1px solid var(--neutral-gray-100);
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
  background: white;
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
</style>
