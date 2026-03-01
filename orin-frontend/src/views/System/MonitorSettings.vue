<template>
  <div class="page-container">
    <PageHeader
      title="系统环境配置"
      description="配置系统硬件监控、环境变量、ZeroClaw 智能维护以及告警阈值"
      icon="Tools"
    >
      <template #actions>
        <el-button type="primary" :loading="saving" :icon="Check" @click="saveConfig">
          保存全局配置
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- ZeroClaw 智能配置 Tab -->
      <el-tab-pane label="ZeroClaw 智能维护" name="zeroclaw">
        <el-row :gutter="24">
          <el-col :lg="14">
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Cpu /></el-icon>
                  <span>ZeroClaw 核心连接</span>
                </div>
              </template>
              
              <el-form :model="zeroclawForm" label-position="top" class="config-form">
                <el-form-item label="启用 ZeroClaw 维护引擎">
                  <div class="flex-between w-100">
                    <div class="form-info">
                      <div class="form-label-desc">开启后，智能体将能够通过 ZeroClaw 获取实时系统诊断信息。</div>
                    </div>
                    <el-switch v-model="zeroclawForm.enabled" />
                  </div>
                </el-form-item>

                <el-divider border-style="dashed" />

                <el-form-item label="服务访问地址 (Endpoint URL)" v-if="zeroclawForm.enabled">
                  <el-input 
                    v-model="zeroclawForm.endpointUrl" 
                    placeholder="例如: http://localhost:8081"
                    class="url-input"
                  />
                </el-form-item>

                <el-form-item label="访问令牌 (Access Token)" v-if="zeroclawForm.enabled">
                  <el-input 
                    v-model="zeroclawForm.accessToken" 
                    type="password" 
                    show-password
                    placeholder="如果服务端有鉴权请填写"
                  />
                </el-form-item>

                <el-form-item label="功能模块开关" v-if="zeroclawForm.enabled">
                  <div class="feature-switches">
                    <el-checkbox v-model="zeroclawForm.enableAnalysis" label="智能诊断分析" border />
                    <el-checkbox v-model="zeroclawForm.enableSelfHealing" label="自愈引擎 (Self-Healing)" border />
                  </div>
                </el-form-item>

                <!-- AI 配置 -->
                <el-divider border-style="dashed" v-if="zeroclawForm.enabled" />
                <el-form-item label="绑定的智能体 (Agent)" v-if="zeroclawForm.enabled">
                  <el-select v-model="zeroclawForm.agentId" placeholder="选择已配置的智能体" style="width: 100%" filterable clearable>
                    <el-option
                      v-for="agent in agentList"
                      :key="agent.agentId"
                      :label="agent.name"
                      :value="agent.agentId"
                    >
                      <div style="display: flex; justify-content: space-between;">
                        <span>{{ agent.name }}</span>
                        <span style="color: #999; font-size: 12px;">{{ agent.providerType }} / {{ agent.modelName }}</span>
                      </div>
                    </el-option>
                  </el-select>
                  <div class="form-tip" style="margin-top: 8px;">
                    选择已配置的智能体，ZeroClaw 将使用该智能体的 AI 配置
                  </div>
                </el-form-item>

                <el-form-item v-if="zeroclawForm.enabled">
                   <el-button @click="testZeroClawConnection" :loading="zeroclawTesting" type="primary" plain size="small">
                     测试连接与状态自检
                   </el-button>
                   <span v-if="zeroclawStatus" class="status-result" :class="zeroclawStatus.connected ? 'success' : 'error'">
                     {{ zeroclawStatus.connected ? '连接成功 - 运行中' : '连接失败 - ' + zeroclawStatus.message }}
                   </span>
                </el-form-item>

                <div class="actions-row">
                    <el-button type="primary" @click="saveZeroClawConfig" :loading="zeroclawSaving">保存 ZeroClaw 配置</el-button>
                </div>
              </el-form>
            </el-card>

            <el-card class="premium-card" v-if="zeroclawStatus && zeroclawStatus.connected">
              <template #header>
                <div class="card-header">
                  <el-icon><Operation /></el-icon>
                  <span>ZeroClaw AI 提供商配置</span>
                </div>
              </template>

              <el-form label-position="top" class="config-form">
                <el-form-item label="选择 AI 提供商">
                  <div class="ai-provider-select">
                    <el-radio-group v-model="selectedAiProvider" @change="handleAiProviderChange">
                      <el-radio-button label="deepseek">
                        <span class="provider-name">DeepSeek</span>
                      </el-radio-button>
                      <el-radio-button label="siliconflow">
                        <span class="provider-name">SiliconFlow</span>
                      </el-radio-button>
                      <el-radio-button label="ollama">
                        <span class="provider-name">Ollama</span>
                      </el-radio-button>
                      <el-radio-button label="custom">
                        <span class="provider-name">自定义</span>
                      </el-radio-button>
                    </el-radio-group>
                  </div>
                  <p class="form-tip" v-if="selectedAiProvider !== 'custom'">
                    选择已配置的 AI 提供商，将自动使用 ORIN 模型配置中的 API Key
                  </p>
                </el-form-item>

                <!-- 自定义配置 -->
                <template v-if="selectedAiProvider === 'custom'">
                  <el-form-item label="API Base URL">
                    <el-input v-model="customAiConfig.baseUrl" placeholder="https://api.deepseek.com" />
                  </el-form-item>
                  <el-form-item label="API Key">
                    <el-input v-model="customAiConfig.apiKey" type="password" show-password placeholder="输入 API Key" />
                  </el-form-item>
                  <el-form-item label="模型名称">
                    <el-input v-model="customAiConfig.model" placeholder="deepseek-chat" />
                  </el-form-item>
                </template>

                <el-form-item>
                  <el-button @click="applyAiConfig" :loading="applyingAiConfig" type="success">
                    应用 AI 配置
                  </el-button>
                  <span v-if="aiConfigStatus" class="status-result" :class="aiConfigStatus.success ? 'success' : 'error'">
                    {{ aiConfigStatus.message }}
                  </span>
                </el-form-item>

                <!-- 当前 AI 状态 -->
                <div class="ai-status-card" v-if="zeroclawStatus.ai_provider">
                  <div class="ai-status-item">
                    <span class="label">当前提供商:</span>
                    <span class="value">{{ zeroclawStatus.ai_provider }}</span>
                  </div>
                  <div class="ai-status-item">
                    <span class="label">当前模型:</span>
                    <span class="value">{{ zeroclawStatus.ai_model }}</span>
                  </div>
                  <div class="ai-status-item">
                    <span class="label">AI 状态:</span>
                    <span class="value" :class="{ active: zeroclawStatus.ai_enabled }">
                      {{ zeroclawStatus.ai_enabled ? '已启用' : '未启用' }}
                    </span>
                  </div>
                </div>
              </el-form>
            </el-card>
          </el-col>

          <el-col :lg="10">
            <el-card class="premium-card guide-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Cpu /></el-icon>
                  <span>ZeroClaw 是什么？</span>
                </div>
              </template>
              <div class="guide-content">
                <p style="font-size: 13px; color: #666; line-height: 1.6;">
                  ZeroClaw 是 ORIN 专用的轻量化运维代理，能够深入操作系统底层采集传统 Prometheus 难以获取的细粒度指标（如：显存碎片、子进程耗时分析、磁盘 I/O 异常等）。
                </p>
                <el-divider />
                <strong>核心场景：</strong>
                <ul style="font-size: 13px; color: #666; padding-left: 20px;">
                  <li><strong>性能诊断</strong>：当 AI 助手回答“慢”时，ZeroClaw 会瞬间给出硬件瓶颈报告。</li>
                  <li><strong>主动自愈</strong>：检测到显存泄露时，自动触发模型重启或显卡缓存清理。</li>
                  <li><strong>趋势预测</strong>：分析过去 24 小时波动，预测即将到来的资源枯竭风险。</li>
                </ul>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

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
                type="warning" show-icon :closable="false" style="margin-bottom: 24px" />
                
              <el-form label-position="left" label-width="180px">
                <el-divider content-position="left">🗄️ MySQL 关系型数据库 (核心主库)</el-divider>
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
                
                <el-divider content-position="left">🧠 Milvus 向量搜索引擎 (AI大脑)</el-divider>
                <el-form-item label="Milvus Host">
                  <el-input v-model="envConfig['milvus.host']" />
                </el-form-item>
                <el-form-item label="Milvus Port">
                  <el-input v-model="envConfig['milvus.port']" />
                </el-form-item>
                <el-form-item label="Milvus Root Token">
                  <el-input v-model="envConfig['milvus.token']" type="password" show-password />
                </el-form-item>

                <el-divider content-position="left">⚡ Redis 分布式高速缓存</el-divider>
                <el-form-item label="Redis Host">
                  <el-input v-model="envConfig['spring.data.redis.host']" />
                </el-form-item>
                <el-form-item label="Redis Port">
                  <el-input v-model="envConfig['spring.data.redis.port']" />
                </el-form-item>
                <el-form-item label="Redis Password">
                  <el-input v-model="envConfig['spring.data.redis.password']" type="password" show-password />
                </el-form-item>

                <el-divider content-position="left">🌐 SiliconFlow (应急算力降级池)</el-divider>
                <el-form-item label="Silicon API Key">
                  <el-input v-model="envConfig['siliconflow.api.key']" type="password" show-password />
                </el-form-item>
                <el-form-item label="Silicon Base URL">
                  <el-input v-model="envConfig['siliconflow.api.base-url']" />
                </el-form-item>

              </el-form>
              <div style="text-align: right">
                <el-button type="primary" :loading="envSaving" @click="saveEnvConfig">执行覆盖保存</el-button>
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
import { configureZeroClawAi } from '@/api/zeroclaw';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Monitor, Check, Connection, InfoFilled, 
  QuestionFilled
} from '@element-plus/icons-vue';

const activeTab = ref('zeroclaw');
const saving = ref(false);
const testing = ref(false);

const zeroclawSaving = ref(false);
const zeroclawTesting = ref(false);
const zeroclawStatus = ref(null);

// AI 配置相关
const selectedAiProvider = ref('deepseek');
const applyingAiConfig = ref(false);
const aiConfigStatus = ref(null);
const customAiConfig = reactive({
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  model: 'deepseek-chat'
});

const zeroclawForm = reactive({
  id: '',
  configName: 'ORIN_Default_ZeroClaw',
  endpointUrl: 'http://localhost:8081',
  accessToken: '',
  enabled: true,
  enableAnalysis: true,
  enableSelfHealing: true,
  heartbeatInterval: 60,
  // 绑定的 Agent ID
  agentId: ''
});

// Agent 列表
const agentList = ref([]);

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

const loadZeroClawConfig = async () => {
  try {
    const res = await request.get('/zeroclaw/configs');
    if (res && res.length > 0) {
      const active = res.find(c => c.enabled) || res[0];
      Object.assign(zeroclawForm, active);
    }
  } catch (error) {
    console.error('Failed to load ZeroClaw config', error);
  }
};

// 加载 Agent 列表
const loadAgents = async () => {
  try {
    const res = await request.get('/agents');
    if (res) {
      agentList.value = res;
    }
  } catch (error) {
    console.error('Failed to load agents', error);
  }
};

const saveZeroClawConfig = async () => {
  zeroclawSaving.value = true;
  try {
    if (zeroclawForm.id) {
      await request.put(`/zeroclaw/configs/${zeroclawForm.id}`, zeroclawForm);
    } else {
      const res = await request.post('/zeroclaw/configs', zeroclawForm);
      zeroclawForm.id = res.id;
    }
    ElMessage.success('ZeroClaw 配置已更新');
    testZeroClawConnection();
  } catch (error) {
    ElMessage.error('保存失败: ' + error.message);
  } finally {
    zeroclawSaving.value = false;
  }
};

const testZeroClawConnection = async () => {
  zeroclawTesting.value = true;
  try {
    const res = await request.post('/zeroclaw/configs/test-connection', {
      endpointUrl: zeroclawForm.endpointUrl,
      accessToken: zeroclawForm.accessToken
    });
    
    if (res.connected) {
      // If basic connection works, get full status
      const statusRes = await request.get('/zeroclaw/status');
      zeroclawStatus.value = statusRes;
      ElMessage.success('ZeroClaw 连接成功并已准备就绪！');
    } else {
      zeroclawStatus.value = { connected: false, message: '无法触达服务端' };
      ElMessage.warning('连接测试失败，请检查 Endpoint URL。');
    }
  } catch (e) {
    zeroclawStatus.value = { connected: false, message: e.message };
    ElMessage.error('连接失败: ' + e.message);
  } finally {
    zeroclawTesting.value = false;
  }
};

// 处理 AI 提供商变化
const handleAiProviderChange = (provider) => {
  if (provider === 'deepseek') {
    customAiConfig.baseUrl = 'https://api.deepseek.com';
    customAiConfig.model = 'deepseek-chat';
  } else if (provider === 'siliconflow') {
    customAiConfig.baseUrl = 'https://api.siliconflow.cn/v1';
    customAiConfig.model = 'Qwen/Qwen2-7B-Instruct';
  } else if (provider === 'ollama') {
    customAiConfig.baseUrl = 'http://localhost:11434/v1';
    customAiConfig.model = 'llama3';
  }
};

// 应用 AI 配置
const applyAiConfig = async () => {
  applyingAiConfig.value = true;
  aiConfigStatus.value = null;

  try {
    let requestData = {};

    if (selectedAiProvider.value === 'custom') {
      // 使用自定义配置 - 直接传递 API Key
      requestData = {
        provider: 'openai',
        apiKey: customAiConfig.apiKey,
        baseUrl: customAiConfig.baseUrl,
        model: customAiConfig.model
      };
    } else {
      // 使用 ORIN 已配置的模型
      requestData = {
        provider: selectedAiProvider.value
      };
    }

    // 通过后端 API 代理配置 ZeroClaw AI
    const res = await configureZeroClawAi(requestData);
    aiConfigStatus.value = res;

    if (res.success) {
      ElMessage.success(`已配置使用 ${selectedAiProvider.value === 'custom' ? '自定义' : selectedAiProvider.value} AI`);
    } else {
      ElMessage.warning(res.message || '配置应用失败');
    }

    // 刷新状态
    await testZeroClawConnection();
  } catch (e) {
    aiConfigStatus.value = { success: false, message: e.message };
    ElMessage.error('配置应用失败: ' + e.message);
  } finally {
    applyingAiConfig.value = false;
  }
};

onMounted(() => {
    loadZeroClawConfig();
    loadAgents();
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
