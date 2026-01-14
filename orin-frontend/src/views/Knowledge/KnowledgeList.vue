<template>
  <div class="page-container">
    <div class="action-bar">
      <div>
        <h2 class="page-title" style="margin-bottom: 0;">知识库管理</h2>
        <span class="text-muted">管理智能体关联的知识库资产</span>
      </div>
      <div>
        <el-select v-model="selectedAgentId" placeholder="选择智能体" style="width: 200px; margin-right: 12px;">
          <el-option
            v-for="agent in agentList"
            :key="agent.agentId"
            :label="agent.name"
            :value="agent.agentId"
          />
        </el-select>
        <el-button type="primary" :loading="syncLoading" @click="handleSync">
          <el-icon class="el-icon--left"><Refresh /></el-icon>
          从 Dify 同步
        </el-button>
      </div>
    </div>

    <!-- Knowledge Base Table -->
    <el-card class="box-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>已绑定知识库</span>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="180">
          <template #default="scope">
            <span style="font-weight: 600">{{ scope.row.name }}</span>
            <div style="font-size: 12px; color: #999;">ID: {{ scope.row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="docCount" label="文档数量" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" type="info">{{ scope.row.docCount }} 篇</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="syncTime" label="最后同步" width="180">
            <template #default="scope">
                {{ formatTime(scope.row.syncTime) }}
            </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'" effect="dark">
              {{ scope.row.status === 'ENABLED' ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="'ENABLED'"
              :inactive-value="'DISABLED'"
              inline-prompt
              active-text="ON"
              inactive-text="OFF"
              @change="(val) => handleStatusChange(scope.row, val)"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const syncLoading = ref(false)
const selectedAgentId = ref('')
const agentList = ref([])
const tableData = ref([])

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

// Watch selection change
watch(selectedAgentId, (newVal) => {
  if(newVal) loadKnowledge()
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

// Status Toggle
const handleStatusChange = async (row, newVal) => {
    try {
        const enabled = newVal === 'ENABLED'
        await request.put(`/knowledge/${row.id}/status`, { enabled })
        ElMessage.success('状态更新成功')
    } catch (error) {
        // Revert on failure
        row.status = newVal === 'ENABLED' ? 'DISABLED' : 'ENABLED' 
        ElMessage.error('更新失败')
    }
}

const formatTime = (time) => {
    if (!time) return '-'
    return new Date(time).toLocaleString()
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
