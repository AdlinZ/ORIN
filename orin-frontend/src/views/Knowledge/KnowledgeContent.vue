<template>
  <div class="page-container">
    <div class="action-bar">
      <div>
        <h2 class="page-title" style="margin-bottom: 0;">{{ pageTitle }}</h2>
        <span class="text-muted">{{ pageSubtitle }}</span>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedAgentId" placeholder="选择智能体" style="width: 200px; margin-right: 12px;">
          <el-option
            v-for="agent in agentList"
            :key="agent.agentId"
            :label="agent.name"
            :value="agent.agentId"
          />
        </el-select>
        
        <el-button type="primary" :loading="syncLoading" @click="handleSync" v-if="activeType === 'DOCUMENT'">
          <el-icon class="el-icon--left"><Refresh /></el-icon>
          同步 Dify
        </el-button>
        
        <el-dropdown v-if="activeType === 'META'" split-button type="success" @click="handleCreateAction" @command="handleMetaCommand">
          <span style="display: flex; align-items: center;">
             <el-icon class="el-icon--left" style="margin-right: 5px"><Plus /></el-icon>
             新建 Prompt
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="prompt">新建 Prompt</el-dropdown-item>
              <el-dropdown-item command="memory_extract">提取记忆 (测试)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button v-else type="success" @click="handleCreateAction">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          新建{{ resourceName }}
        </el-button>
      </div>
    </div>

    <!-- Content Area -->
    <div class="content-wrapper" v-loading="loading">
       <el-alert v-if="description" :title="description" type="info" show-icon style="margin-bottom: 20px;" :closable="false" />
       
       <knowledge-table 
          v-if="activeType !== 'META'"
          :data="filteredData" 
          @status-change="handleStatusChange" 
          @view-chunks="viewChunks"
          @test-retrieve="testRetrieve" 
          :type="tableType"
        />

        <knowledge-meta
            v-else
            ref="knowledgeMetaRef"
            :agent-id="selectedAgentId"
        />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="createVisible" :title="'新建' + resourceName" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="createForm.name" />
        </el-form-item>
        
        <!-- Hidden Type Selection because it's determined by page -->
        
        <el-form-item label="配置" v-if="activeType === 'STRUCTURED' || activeType === 'SQL'">
          <el-input v-model="createForm.configuration" type="textarea" placeholder="JDBC Connection String or Graph API Endpoint..." />
        </el-form-item>
        
         <el-form-item label="定义" v-if="activeType === 'API'">
          <el-input v-model="createForm.configuration" type="textarea" rows="4" placeholder="OpenAPI Spec JSON/YAML..." />
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCreate">确认创建</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import KnowledgeTable from './components/KnowledgeTable.vue'
import KnowledgeMeta from './components/KnowledgeMeta.vue'

const route = useRoute()
const loading = ref(false)
const syncLoading = ref(false)
const selectedAgentId = ref('')
const agentList = ref([])
const tableData = ref([])

const createVisible = ref(false)
const createForm = ref({
  name: '',
  description: '',
  configuration: ''
})

// Determine Type from Route
const activeType = computed(() => route.meta.type || 'DOCUMENT')

const pageTitle = computed(() => route.meta.title)
const pageSubtitle = computed(() => {
    switch(activeType.value) {
        case 'DOCUMENT': return '管理文档、PDF、多模态文件 (RAG)'
        case 'STRUCTURED': return '管理 SQL 连接配置与知识图谱'
        case 'API': return '定义外部工具与 API 插件'
        case 'META': return 'Prompt 模板与用户画像'
        default: return ''
    }
})

const resourceName = computed(() => {
     switch(activeType.value) {
        case 'DOCUMENT': return '知识库'
        case 'STRUCTURED': return '数据源'
        case 'API': return '工具'
        default: return '资产'
    }
})

const description = computed(() => {
    switch(activeType.value) {
        case 'DOCUMENT': return '包括 PDF、Word、文本以及多模态文件 (图片/音频) 的向量索引管理。'
        case 'STRUCTURED': return '管理 MySQL、Oracle 连接配置或知识图谱实体关系。'
        case 'API': return '定义外部 API 插件和自定义函数 (Function Calling)。'
        default: return ''
    }
})

const tableType = computed(() => {
    if (activeType.value === 'STRUCTURED') return 'structured'
    if (activeType.value === 'API') return 'api'
    return 'default'
})

// Load Agent List
const loadAgents = async () => {
  try {
    const res = await request.get('/agents')
    if (res.data && res.data.length > 0) {
      agentList.value = res.data
      selectedAgentId.value = res.data[0].agentId
      loadKnowledge() 
    }
  } catch (error) {
    console.error(error)
  }
}

// Load Knowledge
const loadKnowledge = async () => {
  if (!selectedAgentId.value) return
  loading.value = true
  try {
    const res = await request.get(`/knowledge/agents/${selectedAgentId.value}`)
    tableData.value = res.data
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

// Computed filters
const filteredData = computed(() => {
    const type = activeType.value
    if (type === 'STRUCTURED') {
         return tableData.value.filter(item => item.type === 'SQL' || item.type === 'GRAPH')
    }
    return tableData.value.filter(item => item.type === type)
})


// Watch selection change
watch(selectedAgentId, (newVal) => {
  if(newVal) loadKnowledge()
})

// Watch route change to reload if needed (though usually Component reloads)
watch(() => route.path, () => {
    // If we want to reload generic data, but usually data is same just filtered
})

// Sync Action
const handleSync = async () => {
    if (!selectedAgentId.value) {
        ElMessage.warning('请先选择一个智能体')
        return
    }
    syncLoading.value = true
    try {
        const res = await request.post(`/knowledge/agents/${selectedAgentId.value}/sync`)
        tableData.value = res.data
        ElMessage.success('同步成功')
    } catch (error) {
        ElMessage.error('同步失败')
    } finally {
        syncLoading.value = false
    }
}

const knowledgeMetaRef = ref(null)

// Create Logic
const handleCreateAction = () => {
    if (activeType.value === 'META') {
        // Delegate to KnowledgeMeta component
        knowledgeMetaRef.value?.openPromptDialog()
    } else {
        openCreateDialog()
    }
}

const handleMetaCommand = (command) => {
    if (command === 'prompt') {
        knowledgeMetaRef.value?.openPromptDialog()
    } else if (command === 'memory_extract') {
        knowledgeMetaRef.value?.openExtractDialog()
    }
}

const openCreateDialog = () => {
  createForm.value = { name: '', description: '', configuration: '' }
  createVisible.value = true
}

const submitCreate = async () => {
    try {
        // Determine backend type from activeType page
        let backendType = activeType.value
        if (activeType.value === 'STRUCTURED') {
             // Default to SQL for now, could add switcher in dialog if needed
             backendType = 'SQL' 
        }

        const payload = {
            ...createForm.value,
            type: backendType,
            sourceAgentId: selectedAgentId.value,
            status: 'ENABLED',
            docCount: 0,
            totalSizeMb: 0
        }
        await request.post('/knowledge', payload)
        ElMessage.success('创建成功')
        createVisible.value = false
        loadKnowledge()
    } catch (e) {
        ElMessage.error('创建失败: ' + e.message)
    }
}

// Status Toggle
const handleStatusChange = async (row, newVal) => {
    try {
        const enabled = newVal === 'ENABLED'
        await request.put(`/knowledge/${row.id}/status`, { enabled })
        ElMessage.success('状态更新成功')
    } catch (error) {
        row.status = newVal === 'ENABLED' ? 'DISABLED' : 'ENABLED' 
        ElMessage.error('更新失败')
    }
}

const viewChunks = (row) => {
    ElMessage.info('查看分片功能即将上线: ' + row.name)
}

const testRetrieve = (row) => {
    ElMessage.info('检索测试功能即将上线: ' + row.name)
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.header-actions {
    display: flex;
    align-items: center;
}
.content-wrapper {
    /* min-height: 400px; */
}
</style>
