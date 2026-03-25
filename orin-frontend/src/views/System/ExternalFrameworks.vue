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
              <el-button type="primary" :loading="difyLoading" @click="saveDifyConfig">保存配置</el-button>
              <el-button @click="testDifyConnection" :disabled="!difyConfig.enabled">测试连接</el-button>
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
              <el-button type="primary" :loading="ragflowLoading" @click="saveRagflowConfig">保存配置</el-button>
              <el-button @click="testRagflowConnection" :disabled="!ragflowConfig.enabled">测试连接</el-button>
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
              <el-tag :type="autogenConfig.enabled ? 'success' : 'info'">
                {{ autogenConfig.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
          </template>

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
              <el-button type="primary" :loading="autogenLoading" @click="saveAutogenConfig">保存配置</el-button>
              <el-button @click="testAutogenConnection" :disabled="!autogenConfig.enabled">测试连接</el-button>
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
              <el-tag :type="crewaiConfig.enabled ? 'success' : 'info'">
                {{ crewaiConfig.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
          </template>

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
              <el-button type="primary" :loading="crewaiLoading" @click="saveCrewaiConfig">保存配置</el-button>
              <el-button @click="testCrewaiConnection" :disabled="!crewaiConfig.enabled">测试连接</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

const activeTab = ref('dify')

// Dify 配置
const difyConfig = reactive({
  apiUrl: '',
  apiKey: '',
  enabled: false
})
const difyLoading = ref(false)

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

// 加载 Dify 配置
const loadDifyConfig = async () => {
  try {
    const res = await request.get('/system/integrations/dify')
    if (res) {
      difyConfig.apiUrl = res.apiUrl || ''
      difyConfig.apiKey = res.apiKey || ''
      difyConfig.enabled = res.enabled || false
    }
  } catch (e) {
    console.error('加载 Dify 配置失败:', e)
  }
}

// 保存 Dify 配置
const saveDifyConfig = async () => {
  difyLoading.value = true
  try {
    await request.post('/system/integrations/dify', difyConfig)
    ElMessage.success('Dify 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    difyLoading.value = false
  }
}

// 测试 Dify 连接
const testDifyConnection = async () => {
  try {
    const res = await request.get('/system/integrations/dify/test')
    if (res.success) {
      ElMessage.success('Dify 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

// 加载 RAGFlow 配置
const loadRagflowConfig = async () => {
  try {
    const res = await request.get('/system/integrations/ragflow')
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
const saveRagflowConfig = async () => {
  ragflowLoading.value = true
  try {
    await request.post('/system/integrations/ragflow', ragflowConfig)
    ElMessage.success('RAGFlow 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    ragflowLoading.value = false
  }
}

// 测试 RAGFlow 连接
const testRagflowConnection = async () => {
  try {
    const res = await request.get('/system/integrations/ragflow/test')
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
    const res = await request.get('/system/integrations/autogen')
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
const saveAutogenConfig = async () => {
  autogenLoading.value = true
  try {
    await request.post('/system/integrations/autogen', autogenConfig)
    ElMessage.success('AutoGen 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    autogenLoading.value = false
  }
}

// 测试 AutoGen 连接
const testAutogenConnection = async () => {
  try {
    const res = await request.get('/system/integrations/autogen/test')
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
    const res = await request.get('/system/integrations/crewai')
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
const saveCrewaiConfig = async () => {
  crewaiLoading.value = true
  try {
    await request.post('/system/integrations/crewai', crewaiConfig)
    ElMessage.success('CrewAI 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    crewaiLoading.value = false
  }
}

// 测试 CrewAI 连接
const testCrewaiConnection = async () => {
  try {
    const res = await request.get('/system/integrations/crewai/test')
    if (res.success) {
      ElMessage.success('CrewAI 连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

onMounted(() => {
  loadDifyConfig()
  loadRagflowConfig()
  loadAutogenConfig()
  loadCrewaiConfig()
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
  color: #909399;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
