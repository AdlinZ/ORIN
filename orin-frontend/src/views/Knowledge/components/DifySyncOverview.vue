<template>
  <div class="dify-sync-overview">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span>Dify 数据同步</span>
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadOverview" size="small">
            刷新
          </el-button>
        </div>
      </template>

      <!-- 概览统计 -->
      <div v-if="overview" class="stats-grid">
        <div class="stat-item">
          <div class="stat-value">{{ overview.appsCount || 0 }}</div>
          <div class="stat-label">应用</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ overview.workflowsCount || 0 }}</div>
          <div class="stat-label">工作流</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ overview.datasetsCount || 0 }}</div>
          <div class="stat-label">知识库</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ overview.apiKeysCount || 0 }}</div>
          <div class="stat-label">API Keys</div>
        </div>
      </div>

      <!-- 用户信息 -->
      <div v-if="overview?.user" class="user-info">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="用户">{{ overview.user.name || overview.user.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ overview.user.email || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 同步按钮 -->
      <div class="sync-actions">
        <el-button 
          type="primary" 
          :icon="Refresh" 
          :loading="syncing" 
          @click="handleFullSync"
        >
          {{ syncing ? '同步中...' : '完整同步' }}
        </el-button>
        <el-button 
          type="success" 
          :icon="Operation" 
          :loading="wfSyncing" 
          @click="handleWorkflowSync"
        >
          {{ wfSyncing ? '同步中...' : '同步工作流' }}
        </el-button>
      </div>

      <!-- 同步结果 -->
      <el-alert
        v-if="syncResult"
        :title="syncResult.success ? '同步成功' : `同步失败: ${syncResult.message}`"
        :type="syncResult.success ? 'success' : 'error'"
        show-icon
        closable
        class="sync-result"
        @close="syncResult = null"
      >
        <template #default>
          <span v-if="syncResult.success">
            新增 {{ syncResult.added }}，更新 {{ syncResult.updated }}，删除 {{ syncResult.deleted }}
          </span>
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, Operation } from '@element-plus/icons-vue'
import { getDifySyncOverview, fullSyncDifyAll, syncDifyWorkflows } from '@/api/knowledge'

const props = defineProps({
  agentId: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const syncing = ref(false)
const wfSyncing = ref(false)
const overview = ref(null)
const syncResult = ref(null)

const loadOverview = async () => {
  loading.value = true
  try {
    const res = await getDifySyncOverview(props.agentId)
    overview.value = res
  } catch (e) {
    console.error('Failed to load overview:', e)
  } finally {
    loading.value = false
  }
}

const handleFullSync = async () => {
  syncing.value = true
  syncResult.value = null
  
  try {
    const res = await fullSyncDifyAll(props.agentId)
    syncResult.value = res
    if (res.success) {
      await loadOverview()
    }
  } catch (e) {
    syncResult.value = { success: false, message: e.message }
  } finally {
    syncing.value = false
  }
}

const handleWorkflowSync = async () => {
  wfSyncing.value = true
  syncResult.value = null
  
  try {
    const res = await syncDifyWorkflows(props.agentId)
    syncResult.value = res
  } catch (e) {
    syncResult.value = { success: false, message: e.message }
  } finally {
    wfSyncing.value = false
  }
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.dify-sync-overview {
  margin-bottom: 16px;
}

.overview-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.user-info {
  margin-bottom: 16px;
}

.sync-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.sync-result {
  margin-top: 16px;
}
</style>
