<template>
  <div class="external-frameworks-container">
    <PageHeader
      title="外部框架集成"
      description="管理 Dify、RAGFlow、AutoGen、CrewAI 等外部 AI 框架的集成配置"
      icon="Connection"
    />

    <el-tabs v-model="activeTab" class="frameworks-tabs">
      <!-- Dify 集成 -->
      <el-tab-pane label="Dify" name="dify" :lazy="true">
        <el-card class="framework-card">
          <template #header>
            <div class="card-header">
              <div class="framework-info">
                <el-icon size="24">
                  <Connection />
                </el-icon>
                <div>
                  <h3>Dify</h3>
                  <p>配置入口已迁移至“数据同步”模块</p>
                </div>
              </div>
              <el-tag type="warning">
                已迁移
              </el-tag>
            </div>
          </template>

          <el-alert
            title="Dify 配置入口已迁移"
            type="info"
            :closable="false"
            show-icon
            class="预留提示"
          >
            Dify 的 API 地址、API Key、连接测试与同步动作统一迁移到「控制台 > 数据同步 > Dify 同步」中管理。
          </el-alert>

          <el-button type="primary" @click="goToDifySync">
            前往数据同步
          </el-button>
        </el-card>
      </el-tab-pane>

      <!-- RAGFlow 集成 -->
      <el-tab-pane label="RAGFlow" name="ragflow" :lazy="true">
        <el-card class="framework-card">
          <template #header>
            <div class="card-header">
              <div class="framework-info">
                <el-icon size="24">
                  <Reading />
                </el-icon>
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
              <div class="form-tip">
                RAGFlow API 服务地址
              </div>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input
                v-model="ragflowConfig.apiKey"
                type="password"
                show-password
                placeholder="RAGFlow API Key"
              />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="ragflowConfig.enabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="ragflowLoading" @click="handleSaveRagflowConfig">
                保存配置
              </el-button>
              <el-button :disabled="!ragflowConfig.enabled" @click="handleTestRagflowConnection">
                测试连接
              </el-button>
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
                <el-icon size="24">
                  <Cpu />
                </el-icon>
                <div>
                  <h3>AutoGen</h3>
                  <p>集成 Microsoft AutoGen 多智能体框架</p>
                </div>
              </div>
              <div class="header-actions">
                <el-tag type="warning">
                  预留位
                </el-tag>
                <el-tag :type="autogenConfig.enabled ? 'success' : 'info'">
                  {{ autogenConfig.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
            </div>
          </template>

          <el-alert
            title="AutoGen 集成预留提示"
            type="info"
            :closable="false"
            show-icon
            class="预留提示"
          >
            AutoGen 多智能体框架集成正在规划中，暂未开放使用。
          </el-alert>

          <el-form :model="autogenConfig" label-width="120px">
            <el-form-item label="服务地址">
              <el-input v-model="autogenConfig.serviceUrl" placeholder="http://localhost:8001" />
              <div class="form-tip">
                AutoGen Agent 服务地址
              </div>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input
                v-model="autogenConfig.apiKey"
                type="password"
                show-password
                placeholder="AutoGen API Key"
              />
            </el-form-item>
            <el-form-item label="最大并发">
              <el-input-number v-model="autogenConfig.maxConcurrency" :min="1" :max="10" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="autogenConfig.enabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="autogenLoading" @click="handleSaveAutogenConfig">
                保存配置
              </el-button>
              <el-button :disabled="!autogenConfig.enabled" @click="handleTestAutogenConnection">
                测试连接
              </el-button>
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
                <el-icon size="24">
                  <User />
                </el-icon>
                <div>
                  <h3>CrewAI</h3>
                  <p>集成 CrewAI 多智能体协作框架</p>
                </div>
              </div>
              <div class="header-actions">
                <el-tag type="warning">
                  预留位
                </el-tag>
                <el-tag :type="crewaiConfig.enabled ? 'success' : 'info'">
                  {{ crewaiConfig.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
            </div>
          </template>

          <el-alert
            title="CrewAI 集成预留提示"
            type="info"
            :closable="false"
            show-icon
            class="预留提示"
          >
            CrewAI 多智能体协作框架集成正在规划中，暂未开放使用。
          </el-alert>

          <el-form :model="crewaiConfig" label-width="120px">
            <el-form-item label="服务地址">
              <el-input v-model="crewaiConfig.serviceUrl" placeholder="http://localhost:8002" />
              <div class="form-tip">
                CrewAI Agent 服务地址
              </div>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input
                v-model="crewaiConfig.apiKey"
                type="password"
                show-password
                placeholder="CrewAI API Key"
              />
            </el-form-item>
            <el-form-item label="默认模型">
              <el-input v-model="crewaiConfig.defaultModel" placeholder="gpt-4" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="crewaiConfig.enabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="crewaiLoading" @click="handleSaveCrewaiConfig">
                保存配置
              </el-button>
              <el-button :disabled="!crewaiConfig.enabled" @click="handleTestCrewaiConnection">
                测试连接
              </el-button>
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
                <el-icon size="24">
                  <Connection />
                </el-icon>
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
              <div class="form-tip">
                优先使用 URI；如果留空则使用 Host + Port 组合。
              </div>
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
              <el-input
                v-model="neo4jConfig.password"
                type="password"
                show-password
                placeholder="Neo4j Password"
              />
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
              <el-button type="primary" :loading="neo4jLoading" @click="handleSaveNeo4jConfig">
                保存配置
              </el-button>
              <el-button :disabled="!neo4jConfig.enabled" @click="handleTestNeo4jConnection">
                测试连接
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  getRagflowConfig,
  saveRagflowConfig,
  testRagflowConnection,
  getAutogenConfig,
  saveAutogenConfig,
  testAutogenConnection,
  getCrewaiConfig,
  saveCrewaiConfig,
  testCrewaiConnection,
  getNeo4jConfig,
  saveNeo4jConfig,
  testNeo4jConnection
} from '@/api/integrations'

const activeTab = ref('dify')
const route = useRoute()
const router = useRouter()

// RAGFlow 配置
const ragflowConfig = reactive({
  apiUrl: '',
  apiKey: '',
  enabled: false
})
const ragflowLoading = ref(false)

// AutoGen 配置
const autogenConfig = reactive({
  serviceUrl: '',
  apiKey: '',
  maxConcurrency: 5,
  enabled: false
})
const autogenLoading = ref(false)

// CrewAI 配置
const crewaiConfig = reactive({
  serviceUrl: '',
  apiKey: '',
  defaultModel: 'gpt-4',
  enabled: false
})
const crewaiLoading = ref(false)

// Neo4j 配置
const neo4jConfig = reactive({
  uri: '',
  host: 'localhost',
  port: 7687,
  username: 'neo4j',
  password: '',
  database: 'neo4j',
  maxConnectionPoolSize: 50,
  connectionAcquisitionTimeoutMs: 60000,
  enabled: false
})
const neo4jLoading = ref(false)

const goToDifySync = () => {
  router.push('/dashboard/control/client-sync?tab=dify')
}

// 加载 RAGFlow 配置
const loadRagflowConfig = async () => {
  try {
    const res = await getRagflowConfig()
    if (res) {
      ragflowConfig.apiUrl = res.apiUrl || ''
      ragflowConfig.apiKey = res.apiKey || ''
      ragflowConfig.enabled = res.enabled || false
    }
  } catch (e) {
    console.error('加载 RAGFlow 配置失败:', e)
  }
}

// 保存 RAGFlow 配置
const handleSaveRagflowConfig = async () => {
  ragflowLoading.value = true
  try {
    await saveRagflowConfig(ragflowConfig)
    ElMessage.success('RAGFlow 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    ragflowLoading.value = false
  }
}

// 测试 RAGFlow 连接
const handleTestRagflowConnection = async () => {
  try {
    const res = await testRagflowConnection()
    if (res.success) {
      ElMessage.success('RAGFlow 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

// 加载 AutoGen 配置
const loadAutogenConfig = async () => {
  try {
    const res = await getAutogenConfig()
    if (res) {
      autogenConfig.serviceUrl = res.serviceUrl || ''
      autogenConfig.apiKey = res.apiKey || ''
      autogenConfig.maxConcurrency = res.maxConcurrency || 5
      autogenConfig.enabled = res.enabled || false
    }
  } catch (e) {
    console.error('加载 AutoGen 配置失败:', e)
  }
}

// 保存 AutoGen 配置
const handleSaveAutogenConfig = async () => {
  autogenLoading.value = true
  try {
    await saveAutogenConfig(autogenConfig)
    ElMessage.success('AutoGen 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    autogenLoading.value = false
  }
}

// 测试 AutoGen 连接
const handleTestAutogenConnection = async () => {
  try {
    const res = await testAutogenConnection()
    if (res.success) {
      ElMessage.success('AutoGen 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

// 加载 CrewAI 配置
const loadCrewaiConfig = async () => {
  try {
    const res = await getCrewaiConfig()
    if (res) {
      crewaiConfig.serviceUrl = res.serviceUrl || ''
      crewaiConfig.apiKey = res.apiKey || ''
      crewaiConfig.defaultModel = res.defaultModel || 'gpt-4'
      crewaiConfig.enabled = res.enabled || false
    }
  } catch (e) {
    console.error('加载 CrewAI 配置失败:', e)
  }
}

// 保存 CrewAI 配置
const handleSaveCrewaiConfig = async () => {
  crewaiLoading.value = true
  try {
    await saveCrewaiConfig(crewaiConfig)
    ElMessage.success('CrewAI 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    crewaiLoading.value = false
  }
}

// 测试 CrewAI 连接
const handleTestCrewaiConnection = async () => {
  try {
    const res = await testCrewaiConnection()
    if (res.success) {
      ElMessage.success('CrewAI 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

// 加载 Neo4j 配置
const loadNeo4jConfig = async () => {
  try {
    const res = await getNeo4jConfig()
    if (res) {
      neo4jConfig.uri = res.uri || ''
      neo4jConfig.host = res.host || 'localhost'
      neo4jConfig.port = Number(res.port) || 7687
      neo4jConfig.username = res.username || 'neo4j'
      neo4jConfig.password = res.password || ''
      neo4jConfig.database = res.database || 'neo4j'
      neo4jConfig.maxConnectionPoolSize = Number(res.maxConnectionPoolSize) || 50
      neo4jConfig.connectionAcquisitionTimeoutMs = Number(res.connectionAcquisitionTimeoutMs) || 60000
      neo4jConfig.enabled = !!res.enabled
    }
  } catch (e) {
    console.error('加载 Neo4j 配置失败:', e)
  }
}

// 保存 Neo4j 配置
const handleSaveNeo4jConfig = async () => {
  neo4jLoading.value = true
  try {
    await saveNeo4jConfig({
      uri: neo4jConfig.uri,
      host: neo4jConfig.host,
      port: neo4jConfig.port,
      username: neo4jConfig.username,
      password: neo4jConfig.password,
      database: neo4jConfig.database,
      maxConnectionPoolSize: neo4jConfig.maxConnectionPoolSize,
      connectionAcquisitionTimeoutMs: neo4jConfig.connectionAcquisitionTimeoutMs,
      enabled: neo4jConfig.enabled
    })
    ElMessage.success('Neo4j 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    neo4jLoading.value = false
  }
}

// 测试 Neo4j 连接
const handleTestNeo4jConnection = async () => {
  try {
    const res = await testNeo4jConnection()
    if (res.success) {
      ElMessage.success('Neo4j 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

onMounted(() => {
  const tab = route.query.tab
  if (typeof tab === 'string' && ['dify', 'ragflow', 'autogen', 'crewai', 'neo4j'].includes(tab)) {
    activeTab.value = tab
  }
  loadRagflowConfig()
  loadAutogenConfig()
  loadCrewaiConfig()
  loadNeo4jConfig()
})
</script>

<style scoped>
.external-frameworks-container {
  padding: 20px;
}

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

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
}

.预留提示 {
  margin-bottom: 16px;
}
</style>
