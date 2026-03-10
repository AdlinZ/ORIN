<template>
  <div class="dify-sync">
    <el-button 
      type="primary" 
      :icon="Refresh" 
      :loading="syncing"
      @click="handleSync"
    >
      {{ syncing ? '同步中...' : '从 Dify 同步' }}
    </el-button>
    
    <el-button 
      v-if="showHistory"
      :icon="Clock" 
      @click="showHistoryDialog = true"
    >
      同步历史
    </el-button>

    <!-- 同步结果提示 -->
    <el-alert
      v-if="syncResult"
      :title="syncResult.success ? '同步成功' : '同步失败'"
      :type="syncResult.success ? 'success' : 'error'"
      :description="syncResult.message"
      show-icon
      style="margin-top: 12px"
    />

    <!-- 同步历史对话框 -->
    <el-dialog v-model="showHistoryDialog" title="同步历史" width="600px">
      <el-table :data="syncHistory" v-loading="historyLoading">
        <el-table-column prop="syncType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.syncType === 'FULL' ? 'danger' : 'info'">
              {{ row.syncType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="addedCount" label="新增" width="80" />
        <el-table-column prop="updatedCount" label="更新" width="80" />
        <el-table-column prop="deletedCount" label="删除" width="80" />
        <el-table-column prop="endTime" label="时间">
          <template #default="{ row }">
            {{ row.endTime ? formatDateTime(row.endTime) : '-' }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Clock } from '@element-plus/icons-vue'
import { syncDifyKnowledge, fullSyncDify, getDifySyncHistory } from '@/api/knowledge'

const props = defineProps({
  agentId: {
    type: String,
    required: true
  },
  showHistory: {
    type: Boolean,
    default: true
  }
})

const syncing = ref(false)
const syncResult = ref(null)
const showHistoryDialog = ref(false)
const syncHistory = ref([])
const historyLoading = ref(false)

const handleSync = async (full = false) => {
  syncing.value = true
  syncResult.value = null
  
  try {
    const res = full 
      ? await fullSyncDify(props.agentId)
      : await syncDifyKnowledge(props.agentId, false)
    
    syncResult.value = res
    
    if (res.success) {
      ElMessage.success(`同步完成: 新增 ${res.added}, 更新 ${res.updated}, 删除 ${res.deleted}`)
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    syncResult.value = { success: false, message: e.message }
    ElMessage.error('同步失败: ' + e.message)
  } finally {
    syncing.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getDifySyncHistory(props.agentId, 10)
    syncHistory.value = res || []
  } catch (e) {
    ElMessage.error('加载同步历史失败')
  } finally {
    historyLoading.value = false
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

// 打开历史对话框时加载数据
const openHistory = () => {
  showHistoryDialog.value = true
  loadHistory()
}

defineExpose({
  handleSync,
  openHistory
})
</script>

<style scoped>
.dify-sync {
  display: inline-block;
}
</style>
