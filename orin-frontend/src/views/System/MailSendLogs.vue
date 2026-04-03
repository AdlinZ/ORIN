<template>
  <div class="mail-logs-container">
    <PageHeader
      title="邮件发送日志"
      description="查看邮件发送历史记录"
      icon="Message"
    />

    <el-card class="logs-card">
      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            placeholder="全部"
            clearable
            @change="loadLogs"
          >
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="待发送" value="PENDING" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">
            查询
          </el-button>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 日志列表 -->
      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column
          prop="subject"
          label="主题"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="recipients"
          label="收件人"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column prop="mailerType" label="发送方式" width="100">
          <template #default="{ row }">
            <el-tag :type="row.mailerType === 'mailersend' ? 'primary' : 'info'">
              {{ row.mailerType === 'mailersend' ? 'MailerSend' : 'SMTP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="errorMessage"
          label="错误信息"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发送时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

const loading = ref(false)
const logs = ref([])

const filters = reactive({
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      status: filters.status || undefined
    }
    const res = await request.get('/system/mail-logs', { params })
    if (res) {
      logs.value = res.content || []
      pagination.total = res.totalElements || 0
    }
  } catch (e) {
    console.error('加载日志失败:', e)
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.status = ''
  pagination.page = 1
  loadLogs()
}

const getStatusType = (status) => {
  const map = {
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'PENDING': 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'SUCCESS': '成功',
    'FAILED': '失败',
    'PENDING': '待发送'
  }
  return map[status] || status
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.mail-logs-container {
  padding: 20px;
}

.logs-card {
  margin-top: 20px;
}

.filter-form {
  margin-bottom: 20px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.error-text {
  color: var(--error-500);
  font-size: 12px;
}

.placeholder {
  color: var(--neutral-gray-400);
}
</style>
