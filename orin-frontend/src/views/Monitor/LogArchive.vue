<template>
  <div class="page-container">
    <PageHeader
      title="日志归档"
      description="系统运行日志查询、归档与管理"
      icon="Document"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
        <el-button :icon="Download" @click="handleExport">导出日志</el-button>
      </template>
    </PageHeader>

    <!-- 筛选条件 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="日志级别">
          <el-select v-model="filterForm.level" placeholder="全部" clearable style="width: 120px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filterForm.keyword" placeholder="搜索关键词" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="logs"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="150" />
        <el-table-column prop="message" label="日志内容" min-width="300" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 日志详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="时间">{{ currentLog?.timestamp }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="getLevelType(currentLog?.level)" size="small">
            {{ currentLog?.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ currentLog?.source }}</el-descriptions-item>
        <el-descriptions-item label="日志内容" :span="2">
          {{ currentLog?.message }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.stackTrace" label="堆栈信息" :span="2">
          <pre class="stack-trace">{{ currentLog.stackTrace }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download, Document } from '@element-plus/icons-vue'

const loading = ref(false)
const logs = ref([])
const detailDialogVisible = ref(false)
const currentLog = ref(null)

const filterForm = reactive({
  level: '',
  dateRange: null,
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

const getLevelType = (level) => {
  const typeMap = {
    INFO: '',
    WARN: 'warning',
    ERROR: 'danger',
    DEBUG: 'info'
  }
  return typeMap[level] || ''
}

const fetchLogs = async () => {
  loading.value = true
  try {
    // 模拟数据，实际应调用 API
    logs.value = [
      { timestamp: '2026-03-31 10:23:45', level: 'INFO', source: 'RuntimeService', message: 'System started successfully' },
      { timestamp: '2026-03-31 10:24:12', level: 'INFO', source: 'AgentManager', message: 'Agent session initialized for user 1001' },
      { timestamp: '2026-03-31 10:25:33', level: 'WARN', source: 'TokenCounter', message: 'Token usage approaching rate limit: 8500/10000' },
      { timestamp: '2026-03-31 10:26:01', level: 'ERROR', source: 'KnowledgeService', message: 'Failed to sync knowledge base: connection timeout', stackTrace: 'java.net.SocketTimeoutException: connection timeout\n\tat com.adlin.orin.knowledge.sync' },
      { timestamp: '2026-03-31 10:27:15', level: 'DEBUG', source: 'CacheManager', message: 'Cache hit for key: user_profile_1001' }
    ]
    pagination.total = logs.value.length
  } catch (error) {
    ElMessage.error('获取日志失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchLogs()
}

const handleReset = () => {
  filterForm.level = ''
  filterForm.dateRange = null
  filterForm.keyword = ''
  handleSearch()
}

const handleRefresh = () => {
  fetchLogs()
  ElMessage.success('刷新成功')
}

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

const handleSelectionChange = (selection) => {
  console.log('selection:', selection)
}

const handlePageSizeChange = () => {
  fetchLogs()
}

const handlePageChange = () => {
  fetchLogs()
}

const handleViewDetail = (row) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.stack-trace {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
}
</style>
