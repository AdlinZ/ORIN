<template>
  <div class="unified-knowledge-view">
    <!-- Header / Type Switcher -->
    <div class="view-header">
      <div class="header-title">
        <el-icon class="mr-2"><component :is="activeTabIcon" /></el-icon>
        <span>{{ activeTabTitle }}</span>
      </div>
      
      <div class="actions">
        <!-- Agent Selector -->
        <el-select v-model="selectedAgent" placeholder="Select Agent" style="width: 200px">
           <el-option v-for="agent in agentList" :key="agent.agentId" :label="agent.name" :value="agent.agentId" />
        </el-select>
        
        <!-- Upload Button for Unstructured -->
        <el-upload
            v-if="activeTab === 'UNSTRUCTURED'"
            class="upload-demo"
            action="#"
            :show-file-list="false"
            :http-request="customUpload"
        >
            <el-button type="primary">
               <el-icon><Upload /></el-icon> 上传文档
            </el-button>
        </el-upload>
      </div>
    </div>

    <!-- Content Area -->
    <div class="view-content" v-loading="knowledgeStore.loading">
      
      <!-- 1. Unstructured View -->
      <div v-if="activeTab === 'UNSTRUCTURED'">
          <!-- Async Upload Progress Bar (Amber) -->
          <div v-for="[taskId, info] in knowledgeStore.uploadingMap" :key="taskId" class="upload-progress-item">
               <div class="file-name">{{ info.fileName }}</div>
               <div class="progress-bar-wrapper">
                   <!-- Amber color for vectorizing -->
                   <el-progress 
                      :percentage="info.progress" 
                      :color="getStatusColor(info.status)"
                      :striped="info.status === 'VECTORIZING'"
                      :striped-flow="info.status === 'VECTORIZING'"
                   /> 
               </div>
               <div class="status-text">{{ getStatusText(info.status) }}</div>
          </div>
      
          <knowledge-table type="default" :data="knowledgeStore.knowledgeList" />
      </div>

      <!-- 2. Structured View -->
      <div v-if="activeTab === 'STRUCTURED'">
          <knowledge-table type="structured" :data="knowledgeStore.knowledgeList" />
      </div>

      <!-- 3. Procedural View -->
      <div v-if="activeTab === 'PROCEDURAL'">
          <div class="mb-4 flex justify-between">
              <el-button type="primary" @click="$router.push('/dashboard/workflow/create')">创建新技能</el-button>
          </div>
          <knowledge-table type="api" :data="knowledgeStore.knowledgeList" />
      </div>

      <!-- 4. Meta View -->
      <div v-if="activeTab === 'META'">
          <knowledge-meta :agent-id="selectedAgent" />
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKnowledgeStore } from '@/stores/knowledgeStore'
import { Document, DataLine, Cpu, User, Upload } from '@element-plus/icons-vue'
import KnowledgeTable from './components/KnowledgeTable.vue'
import KnowledgeMeta from './components/KnowledgeMeta.vue'
import { getAgentList } from '@/api/agent'

const route = useRoute()
const router = useRouter()
const knowledgeStore = useKnowledgeStore()
const activeTab = ref('UNSTRUCTURED')
const selectedAgent = ref('')
const agentList = ref([])

const activeTabTitle = computed(() => {
    const map = {
        'UNSTRUCTURED': '文档知识',
        'STRUCTURED': '数据资产',
        'PROCEDURAL': '程序化技能',
        'META': '元知识与记忆'
    }
    return map[activeTab.value] || '知识管理'
})

const activeTabIcon = computed(() => {
    const map = {
        'UNSTRUCTURED': Document,
        'STRUCTURED': DataLine,
        'PROCEDURAL': Cpu,
        'META': User
    }
    return map[activeTab.value] || Document
})

onMounted(async () => {
    // 1. Sync Tab from Route Meta (Initial Load)
    if (route.meta.type) {
        syncTabFromMeta(route.meta.type)
    }

    // 2. Load Agents
    const res = await getAgentList({ size: 100 })
    if (res) {
        agentList.value = res
        
        // 3. Priority: Query Param > First Agent
        if (route.query.agentId) {
            selectedAgent.value = route.query.agentId
        } else if (agentList.value.length > 0) {
            selectedAgent.value = agentList.value[0].agentId
        }
    }
})

// Helper for syncing
const syncTabFromMeta = (type) => {
    const typeMap = {
        'DOCUMENT': 'UNSTRUCTURED',
        'STRUCTURED': 'STRUCTURED',
        'API': 'PROCEDURAL',
        'META': 'META'
    }
    if (typeMap[type]) {
        activeTab.value = typeMap[type]
    }
}

// Watch Route Meta Change (Fix for Sidebar Navigation)
watch(() => route.meta.type, (newType) => {
    if (newType) {
        syncTabFromMeta(newType)
    }
}, { immediate: true })

watch([activeTab, selectedAgent], ([newTab, newAgent]) => {
    if (newAgent) {
        // Map Tab to API Type
        const apiTypeMap = {
            'UNSTRUCTURED': 'DOCUMENT',
            'STRUCTURED': 'STRUCTURED',
            'PROCEDURAL': 'API',
            'META': 'META'
        }
        knowledgeStore.loadKnowledge(newAgent, apiTypeMap[newTab] || 'DOCUMENT')
    }
})

const customUpload = async (options) => {
    const { file } = options
    // Assuming default KB ID for now or selector
    const defaultKbId = 'default_kb_' + selectedAgent.value 
    await knowledgeStore.uploadFile(selectedAgent.value, file, defaultKbId)
}

const getStatusColor = (status) => {
    if (status === 'VECTORIZING') return '#F59E0B' // Amber
    if (status === 'COMPLETED') return '#67C23A' // Green
    if (status === 'ERROR') return '#F56C6C' // Red
    return 'var(--orin-primary)'
}

const getStatusText = (status) => {
    if (status === 'VECTORIZING') return '向量化中...'
    if (status === 'COMPLETED') return '已完成'
    return status
}
</script>

<style scoped>
.unified-knowledge-view {
    padding: 20px;
    background: #fff;
    min-height: 80vh;
}

.view-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.actions {
    display: flex;
    gap: 12px;
}

.custom-tab-label {
    display: flex;
    align-items: center;
    gap: 6px;
}

.header-title {
    display: flex;
    align-items: center;
    font-size: 20px;
    font-weight: 600;
    color: var(--neutral-gray-800);
}

.mr-2 {
    margin-right: 8px;
}

.upload-progress-item {
    padding: 15px;
    background: #f9f9f9;
    border-radius: 8px;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 15px;
}

.file-name {
    width: 200px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.progress-bar-wrapper {
    flex: 1;
}

.status-text {
    width: 100px;
    text-align: right;
    font-size: 0.9em;
    color: #606266;
}
</style>
