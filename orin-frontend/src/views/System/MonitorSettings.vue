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
          <el-col :span="24">
            <el-card class="premium-card guide-card">
              <template #header>
                <div class="card-header">
                  <el-icon><QuestionFilled /></el-icon>
                  <span>入口迁移说明</span>
                </div>
              </template>
              <div class="guide-content">
                <div class="guide-step">
                  <span class="step-num">1</span>
                  <div class="step-text">
                    <strong>硬件监控数据源配置已迁移</strong>
                    <p>请前往运行时监控页面统一维护全局 Prometheus 配置与节点级 Prometheus URL。</p>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">2</span>
                  <div class="step-text">
                    <strong>新的配置位置</strong>
                    <p>访问 `/dashboard/runtime/server` 页面，在顶部的“硬件监控数据源配置”区域进行保存与连接测试。</p>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">3</span>
                  <div class="step-text">
                    <strong>迁移原因</strong>
                    <p>这样可以把“数据源配置”、“全节点总览”和“单节点面板”放在同一处，避免监控相关配置散落在多个页面。</p>
                  </div>
                </div>
              </div>

              <el-alert
                title="提示：系统环境配置页保留其他外部依赖配置；硬件监控入口已统一迁移。"
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

      <!-- 知识库配置 -->
      <el-tab-pane label="知识库配置" name="knowledge" :lazy="true">
        <SystemEnvKnowledgeTab ref="knowledgeTab" />
      </el-tab-pane>

      <!-- 模型调度中枢 -->
      <el-tab-pane label="模型调度中枢" name="model-config" :lazy="true">
        <ModelSchedulingPanel ref="modelConfigPanel" />
      </el-tab-pane>

      <!-- 外部框架集成 -->
      <el-tab-pane label="外部框架集成" name="external-frameworks" :lazy="true">
        <el-tabs v-model="frameworkActiveTab" class="frameworks-tabs">
          <!-- Dify 集成 -->
          <el-tab-pane label="Dify" name="dify" :lazy="true">
            <el-card class="framework-card">
              <template #header>
                <div class="card-header">
                  <div class="framework-info">
                    <el-icon size="24"><Connection /></el-icon>
                    <div>
                      <h3>Dify</h3>
                      <p>集成 Dify 工作流和应用</p>
                    </div>
                  </div>
                  <el-tag :type="difyConfig.enabled ? 'success' : 'info'">
                    {{ difyConfig.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </div>
              </template>
              <el-form :model="difyConfig" label-width="120px">
                <el-form-item label="API 地址">
                  <el-input v-model="difyConfig.apiUrl" placeholder="https://api.dify.ai/v1" />
                  <div class="form-tip">Dify API 服务地址</div>
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="difyConfig.apiKey" type="password" show-password placeholder="Dify API Key" />
                </el-form-item>
                <el-form-item label="启用状态">
                  <el-switch v-model="difyConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="difyLoading" @click="handleSaveDifyConfig">保存配置</el-button>
                  <el-button :disabled="!difyConfig.enabled" @click="handleTestDifyConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- RAGFlow 集成 -->
          <el-tab-pane label="RAGFlow" name="ragflow" :lazy="true">
            <el-card class="framework-card">
              <template #header>
                <div class="card-header">
                  <div class="framework-info">
                    <el-icon size="24"><Reading /></el-icon>
                    <div>
                      <h3>RAGFlow</h3>
                      <p>集成 RAGFlow 知识库检索</p>
                    </div>
                  </div>
                  <el-tag :type="ragflowConfig.enabled ? 'success' : 'info'">
                    {{ ragflowConfig.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </div>
              </template>
              <el-form :model="ragflowConfig" label-width="120px">
                <el-form-item label="API 地址">
                  <el-input v-model="ragflowConfig.apiUrl" placeholder="https://ragflow.example.com/api/v1" />
                  <div class="form-tip">RAGFlow API 服务地址</div>
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="ragflowConfig.apiKey" type="password" show-password placeholder="RAGFlow API Key" />
                </el-form-item>
                <el-form-item label="启用状态">
                  <el-switch v-model="ragflowConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="ragflowLoading" @click="handleSaveRagflowConfig">保存配置</el-button>
                  <el-button :disabled="!ragflowConfig.enabled" @click="handleTestRagflowConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- AutoGen 集成 -->
          <el-tab-pane label="AutoGen" name="autogen" :lazy="true">
            <el-card class="framework-card">
              <template #header>
                <div class="card-header">
                  <div class="framework-info">
                    <el-icon size="24"><Cpu /></el-icon>
                    <div>
                      <h3>AutoGen</h3>
                      <p>集成 Microsoft AutoGen 多智能体框架</p>
                    </div>
                  </div>
                  <div class="header-actions">
                    <el-tag type="warning">预留位</el-tag>
                    <el-tag :type="autogenConfig.enabled ? 'success' : 'info'">
                      {{ autogenConfig.enabled ? '已启用' : '未启用' }}
                    </el-tag>
                  </div>
                </div>
              </template>
              <el-alert title="AutoGen 集成预留提示" type="info" :closable="false" show-icon class="预留提示">
                AutoGen 多智能体框架集成正在规划中，暂未开放使用。
              </el-alert>
              <el-form :model="autogenConfig" label-width="120px">
                <el-form-item label="服务地址">
                  <el-input v-model="autogenConfig.serviceUrl" placeholder="http://localhost:8001" />
                  <div class="form-tip">AutoGen Agent 服务地址</div>
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="autogenConfig.apiKey" type="password" show-password placeholder="AutoGen API Key" />
                </el-form-item>
                <el-form-item label="最大并发">
                  <el-input-number v-model="autogenConfig.maxConcurrency" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="启用状态">
                  <el-switch v-model="autogenConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="autogenLoading" @click="handleSaveAutogenConfig">保存配置</el-button>
                  <el-button :disabled="!autogenConfig.enabled" @click="handleTestAutogenConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- CrewAI 集成 -->
          <el-tab-pane label="CrewAI" name="crewai" :lazy="true">
            <el-card class="framework-card">
              <template #header>
                <div class="card-header">
                  <div class="framework-info">
                    <el-icon size="24"><User /></el-icon>
                    <div>
                      <h3>CrewAI</h3>
                      <p>集成 CrewAI 多智能体协作框架</p>
                    </div>
                  </div>
                  <div class="header-actions">
                    <el-tag type="warning">预留位</el-tag>
                    <el-tag :type="crewaiConfig.enabled ? 'success' : 'info'">
                      {{ crewaiConfig.enabled ? '已启用' : '未启用' }}
                    </el-tag>
                  </div>
                </div>
              </template>
              <el-alert title="CrewAI 集成预留提示" type="info" :closable="false" show-icon class="预留提示">
                CrewAI 多智能体协作框架集成正在规划中，暂未开放使用。
              </el-alert>
              <el-form :model="crewaiConfig" label-width="120px">
                <el-form-item label="服务地址">
                  <el-input v-model="crewaiConfig.serviceUrl" placeholder="http://localhost:8002" />
                  <div class="form-tip">CrewAI Agent 服务地址</div>
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="crewaiConfig.apiKey" type="password" show-password placeholder="CrewAI API Key" />
                </el-form-item>
                <el-form-item label="默认模型">
                  <el-input v-model="crewaiConfig.defaultModel" placeholder="gpt-4" />
                </el-form-item>
                <el-form-item label="启用状态">
                  <el-switch v-model="crewaiConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="crewaiLoading" @click="handleSaveCrewaiConfig">保存配置</el-button>
                  <el-button :disabled="!crewaiConfig.enabled" @click="handleTestCrewaiConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- Neo4j 集成 -->
          <el-tab-pane label="Neo4j" name="neo4j" :lazy="true">
            <el-card class="framework-card">
              <template #header>
                <div class="card-header">
                  <div class="framework-info">
                    <el-icon size="24"><Connection /></el-icon>
                    <div>
                      <h3>Neo4j</h3>
                      <p>知识图谱图数据库连接配置（用于 Graph/RAG 图谱能力）</p>
                    </div>
                  </div>
                  <el-tag :type="neo4jConfig.enabled ? 'success' : 'info'">
                    {{ neo4jConfig.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </div>
              </template>
              <el-form :model="neo4jConfig" label-width="160px">
                <el-form-item label="连接 URI (可选)">
                  <el-input v-model="neo4jConfig.uri" placeholder="neo4j+s://xxxx.databases.neo4j.io" />
                  <div class="form-tip">优先使用 URI；如果留空则使用 Host + Port 组合。</div>
                </el-form-item>
                <el-form-item label="Host">
                  <el-input v-model="neo4jConfig.host" placeholder="localhost" />
                </el-form-item>
                <el-form-item label="Port">
                  <el-input-number v-model="neo4jConfig.port" :min="1" :max="65535" />
                </el-form-item>
                <el-form-item label="用户名">
                  <el-input v-model="neo4jConfig.username" placeholder="neo4j" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="neo4jConfig.password" type="password" show-password placeholder="Neo4j Password" />
                </el-form-item>
                <el-form-item label="Database">
                  <el-input v-model="neo4jConfig.database" placeholder="neo4j" />
                </el-form-item>
                <el-form-item label="连接池大小">
                  <el-input-number v-model="neo4jConfig.maxConnectionPoolSize" :min="1" :max="500" />
                </el-form-item>
                <el-form-item label="获取连接超时(ms)">
                  <el-input-number v-model="neo4jConfig.connectionAcquisitionTimeoutMs" :min="1000" :max="300000" />
                </el-form-item>
                <el-form-item label="启用状态">
                  <el-switch v-model="neo4jConfig.enabled" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="neo4jLoading" @click="handleSaveNeo4jConfig">保存配置</el-button>
                  <el-button :disabled="!neo4jConfig.enabled" @click="handleTestNeo4jConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import PageHeader from '@/components/PageHeader.vue';
import SystemEnvKnowledgeTab from '@/views/SystemSettings/components/SystemEnvKnowledgeTab.vue';
import ModelSchedulingPanel from '@/views/SystemSettings/components/ModelSchedulingPanel.vue';
import {
  Check, Connection,
  QuestionFilled, Cpu, Reading, User
} from '@element-plus/icons-vue';
import {
  getDifyConfig, saveDifyConfig, testDifyConnection,
  getRagflowConfig, saveRagflowConfig, testRagflowConnection,
  getAutogenConfig, saveAutogenConfig, testAutogenConnection,
  getCrewaiConfig, saveCrewaiConfig, testCrewaiConnection,
  getNeo4jConfig, saveNeo4jConfig, testNeo4jConnection
} from '@/api/integrations';

const activeTab = ref('prometheus');
const saving = ref(false);
const knowledgeTab = ref(null);
const modelConfigPanel = ref(null);
const frameworkActiveTab = ref('dify');
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
        ElMessage.success('监控配置已生效并保存');
        await loadConfig();
        // 同时保存知识库配置
        if (knowledgeTab.value?.saveConfig) {
          await knowledgeTab.value.saveConfig();
        }
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

// ==================== 外部框架集成配置 ====================
// Dify 配置
const difyConfig = reactive({ apiUrl: '', apiKey: '', enabled: false });
const difyLoading = ref(false);

const loadDifyConfig = async () => {
  try {
    const res = await getDifyConfig();
    if (res) {
      difyConfig.apiUrl = res.apiUrl || '';
      difyConfig.apiKey = res.apiKey || '';
      difyConfig.enabled = res.enabled || false;
    }
  } catch (e) { console.error('加载 Dify 配置失败:', e); }
};

const handleSaveDifyConfig = async () => {
  difyLoading.value = true;
  try {
    await saveDifyConfig(difyConfig);
    ElMessage.success('Dify 配置已保存');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { difyLoading.value = false; }
};

const handleTestDifyConnection = async () => {
  try {
    const res = await testDifyConnection();
    res.success ? ElMessage.success('Dify 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

// RAGFlow 配置
const ragflowConfig = reactive({ apiUrl: '', apiKey: '', enabled: false });
const ragflowLoading = ref(false);

const loadRagflowConfig = async () => {
  try {
    const res = await getRagflowConfig();
    if (res) {
      ragflowConfig.apiUrl = res.apiUrl || '';
      ragflowConfig.apiKey = res.apiKey || '';
      ragflowConfig.enabled = res.enabled || false;
    }
  } catch (e) { console.error('加载 RAGFlow 配置失败:', e); }
};

const handleSaveRagflowConfig = async () => {
  ragflowLoading.value = true;
  try {
    await saveRagflowConfig(ragflowConfig);
    ElMessage.success('RAGFlow 配置已保存');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { ragflowLoading.value = false; }
};

const handleTestRagflowConnection = async () => {
  try {
    const res = await testRagflowConnection();
    res.success ? ElMessage.success('RAGFlow 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

// AutoGen 配置
const autogenConfig = reactive({ serviceUrl: '', apiKey: '', maxConcurrency: 5, enabled: false });
const autogenLoading = ref(false);

const loadAutogenConfig = async () => {
  try {
    const res = await getAutogenConfig();
    if (res) {
      autogenConfig.serviceUrl = res.serviceUrl || '';
      autogenConfig.apiKey = res.apiKey || '';
      autogenConfig.maxConcurrency = res.maxConcurrency || 5;
      autogenConfig.enabled = res.enabled || false;
    }
  } catch (e) { console.error('加载 AutoGen 配置失败:', e); }
};

const handleSaveAutogenConfig = async () => {
  autogenLoading.value = true;
  try {
    await saveAutogenConfig(autogenConfig);
    ElMessage.success('AutoGen 配置已保存');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { autogenLoading.value = false; }
};

const handleTestAutogenConnection = async () => {
  try {
    const res = await testAutogenConnection();
    res.success ? ElMessage.success('AutoGen 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

// CrewAI 配置
const crewaiConfig = reactive({ serviceUrl: '', apiKey: '', defaultModel: 'gpt-4', enabled: false });
const crewaiLoading = ref(false);

const loadCrewaiConfig = async () => {
  try {
    const res = await getCrewaiConfig();
    if (res) {
      crewaiConfig.serviceUrl = res.serviceUrl || '';
      crewaiConfig.apiKey = res.apiKey || '';
      crewaiConfig.defaultModel = res.defaultModel || 'gpt-4';
      crewaiConfig.enabled = res.enabled || false;
    }
  } catch (e) { console.error('加载 CrewAI 配置失败:', e); }
};

const handleSaveCrewaiConfig = async () => {
  crewaiLoading.value = true;
  try {
    await saveCrewaiConfig(crewaiConfig);
    ElMessage.success('CrewAI 配置已保存');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { crewaiLoading.value = false; }
};

const handleTestCrewaiConnection = async () => {
  try {
    const res = await testCrewaiConnection();
    res.success ? ElMessage.success('CrewAI 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

// Neo4j 配置
const neo4jConfig = reactive({
  uri: '', host: 'localhost', port: 7687, username: 'neo4j', password: '',
  database: 'neo4j', maxConnectionPoolSize: 50, connectionAcquisitionTimeoutMs: 60000, enabled: false
});
const neo4jLoading = ref(false);

const loadNeo4jConfig = async () => {
  try {
    const res = await getNeo4jConfig();
    if (res) {
      neo4jConfig.uri = res.uri || '';
      neo4jConfig.host = res.host || 'localhost';
      neo4jConfig.port = Number(res.port) || 7687;
      neo4jConfig.username = res.username || 'neo4j';
      neo4jConfig.password = res.password || '';
      neo4jConfig.database = res.database || 'neo4j';
      neo4jConfig.maxConnectionPoolSize = Number(res.maxConnectionPoolSize) || 50;
      neo4jConfig.connectionAcquisitionTimeoutMs = Number(res.connectionAcquisitionTimeoutMs) || 60000;
      neo4jConfig.enabled = !!res.enabled;
    }
  } catch (e) { console.error('加载 Neo4j 配置失败:', e); }
};

const handleSaveNeo4jConfig = async () => {
  neo4jLoading.value = true;
  try {
    await saveNeo4jConfig({
      uri: neo4jConfig.uri, host: neo4jConfig.host, port: neo4jConfig.port,
      username: neo4jConfig.username, password: neo4jConfig.password,
      database: neo4jConfig.database, maxConnectionPoolSize: neo4jConfig.maxConnectionPoolSize,
      connectionAcquisitionTimeoutMs: neo4jConfig.connectionAcquisitionTimeoutMs, enabled: neo4jConfig.enabled
    });
    ElMessage.success('Neo4j 配置已保存');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { neo4jLoading.value = false; }
};

const handleTestNeo4jConnection = async () => {
  try {
    const res = await testNeo4jConnection();
    res.success ? ElMessage.success('Neo4j 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

onMounted(() => {
    loadConfig();
    loadEnvConfig();
    loadDifyConfig();
    loadRagflowConfig();
    loadAutogenConfig();
    loadCrewaiConfig();
    loadNeo4jConfig();
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
.status-result.success { color: var(--success-500); }
.status-result.error { color: var(--error-500); }

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
    border-top: 1px dotted var(--neutral-gray-200);
}

.text-success { color: #10b981; }
.text-danger { color: #ef4444; }

.frameworks-tabs {
  margin-top: 20px;
}

.framework-card {
  max-width: 800px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.framework-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.framework-info h3 {
  margin: 0;
  font-size: 16px;
}

.framework-info p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.预留提示 {
  margin-bottom: 16px;
}
</style>
