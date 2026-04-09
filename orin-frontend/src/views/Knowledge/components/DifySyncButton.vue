<template>
  <div class="dify-sync">
    <!-- 同步按钮组 -->
    <el-button-group>
      <el-button type="primary" :icon="Refresh" :loading="syncing" @click="handleSync(false)">
        {{ syncing ? '同步中...' : '增量同步' }}
      </el-button>
      <el-button type="danger" :icon="Download" :loading="fullSyncing" @click="handleSync(true)">
        {{ fullSyncing ? '全量同步中...' : '全量同步' }}
      </el-button>
      <el-button :icon="Connection" @click="showTestDialog = true" :disabled="syncing">
        测试连接
      </el-button>
      <el-button :icon="Clock" @click="openHistory" :disabled="syncing">
        历史
      </el-button>
    </el-button-group>

    <!-- 同步结果提示（带失败原因） -->
    <el-alert
      v-if="syncResult"
      :title="syncResult.success ? '同步成功' : `同步失败: ${syncResult.message}`"
      :type="syncResult.success ? 'success' : 'error'"
      show-icon
      closable
      class="sync-alert"
      @close="syncResult = null"
    >
      <template #default>
        <div v-if="syncResult.success">
          <span>新增 {{ syncResult.added }}, 更新 {{ syncResult.updated }}, 删除 {{ syncResult.deleted }}</span>
        </div>
        <div v-if="!syncResult.success">
          <span class="failure-reason">{{ syncResult.message }}</span>
          <el-button size="small" type="warning" class="retry-btn" @click="handleRetry">
            重试
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- 连接测试对话框 -->
    <el-dialog v-model="showTestDialog" title="测试 Dify 连接" width="440px">
      <el-form label-width="90px">
        <el-form-item label="Endpoint">
          <el-input v-model="testForm.endpoint" placeholder="http://localhost:3000" clearable />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="testForm.apiKey" type="password" placeholder="app-xxx" show-password clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestDialog = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="handleTestConnection">
          测试
        </el-button>
      </template>
    </el-dialog>

    <!-- 同步历史对话框 -->
    <el-dialog v-model="showHistoryDialog" title="同步历史" width="680px">
      <div v-if="lastSyncRecord" class="last-sync-summary">
        <el-tag type="info">最近同步</el-tag>
        <span class="summary-text">
          {{ lastSyncRecord.syncType }} · {{ lastSyncRecord.status }} ·
          +{{ lastSyncRecord.addedCount }} ~{{ lastSyncRecord.updatedCount }} -{{ lastSyncRecord.deletedCount }}
        </span>
      </div>
      <el-table v-loading="historyLoading" :data="syncHistory" style="margin-top: 8px">
        <el-table-column prop="syncType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.syncType === 'FULL' ? 'danger' : 'info'" size="small">
              {{ row.syncType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="addedCount" label="新增" width="70" />
        <el-table-column prop="updatedCount" label="更新" width="70" />
        <el-table-column prop="deletedCount" label="删除" width="70" />
        <el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip />
        <el-table-column prop="endTime" label="时间" width="160">
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
import { Refresh, Clock, Download, Connection } from '@element-plus/icons-vue'
import {
  syncDifyKnowledge,
  fullSyncDify,
  getDifySyncHistory,
  testDifyConnection
} from '@/api/knowledge'

const props = defineProps({
  agentId: {
    type: String,
    required: true
  }
})

const syncing = ref(false)
const fullSyncing = ref(false)
const syncResult = ref(null)
const showHistoryDialog = ref(false)
const syncHistory = ref([])
const historyLoading = ref(false)
const showTestDialog = ref(false)
const testing = ref(false)
const lastSyncRecord = ref(null)

const testForm = ref({
  endpoint: '',
  apiKey: ''
})

let lastFailedFull = false

const handleSync = async (full = false) => {
  const isFull = full
  if (isFull) {
    fullSyncing.value = true
  } else {
    syncing.value = true
  }
  syncResult.value = null
  lastFailedFull = isFull

  try {
    const res = isFull
      ? await fullSyncDify(props.agentId)
      : await syncDifyKnowledge(props.agentId, false)

    syncResult.value = res

    if (res.success) {
      ElMessage.success(`同步完成: +${res.added} ~${res.updated} -${res.deleted}`)
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    syncResult.value = { success: false, message: e.message }
    ElMessage.error('同步失败: ' + e.message)
  } finally {
    syncing.value = false
    fullSyncing.value = false
  }
}

const handleRetry = () => {
  handleSync(lastFailedFull)
}

const handleTestConnection = async () => {
  if (!testForm.value.endpoint || !testForm.value.apiKey) {
    ElMessage.warning('请填写 endpoint 和 apiKey')
    return
  }
  testing.value = true
  try {
    const res = await testDifyConnection(testForm.value.endpoint, testForm.value.apiKey)
    if (res.success) {
      ElMessage.success('连接成功')
      showTestDialog.value = false
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('连接失败: ' + e.message)
  } finally {
    testing.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getDifySyncHistory(props.agentId, 20)
    syncHistory.value = res || []
    if (syncHistory.value.length > 0) {
      lastSyncRecord.value = syncHistory.value[0]
    }
  } catch (e) {
    ElMessage.error('加载同步历史失败')
  } finally {
    historyLoading.value = false
  }
}

const openHistory = () => {
  showHistoryDialog.value = true
  loadHistory()
}

const statusTagType = (status) => {
  const map = {
    COMPLETED: 'success',
    RUNNING: 'warning',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

defineExpose({
  handleSync: (full) => handleSync(full),
  openHistory
})
</script>

<style scoped>
.dify-sync {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.sync-alert {
  max-width: 480px;
}

.failure-reason {
  font-size: 13px;
  color: var(--el-color-danger);
  margin-right: 8px;
}

.retry-btn {
  margin-left: 4px;
}

.last-sync-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.summary-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
