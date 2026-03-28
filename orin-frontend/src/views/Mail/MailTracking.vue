<template>
  <div class="mail-tracking-container">
    <PageHeader
      title="追踪与回执"
      description="查看发送日志、追踪邮件状态、处理失败发送"
      icon="List"
    />

    <div class="tracking-content">
      <!-- 视图切换和筛选 -->
      <div class="tracking-toolbar">
        <div class="view-tabs">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="list">
              <el-icon><List /></el-icon> 列表
            </el-radio-button>
            <el-radio-button value="timeline">
              <el-icon><Clock /></el-icon> 时间线
            </el-radio-button>
            <el-radio-button value="aggregate">
              <el-icon><DataAnalysis /></el-icon> 聚合
            </el-radio-button>
          </el-radio-group>
        </div>

        <div class="filters">
          <el-select
            v-model="filters.status"
            placeholder="状态筛选"
            clearable
            style="width: 140px;"
          >
            <el-option label="全部" value="" />
            <el-option label="发送中" value="sending" />
            <el-option label="发送成功" value="success" />
            <el-option label="发送失败" value="failed" />
          </el-select>

          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px;"
          />

          <el-input
            v-model="filters.keyword"
            placeholder="搜索关键词"
            :prefix-icon="Search"
            clearable
            style="width: 180px;"
          />
        </div>

        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="loadLogs">
            刷新
          </el-button>
          <el-button
            :icon="Download"
            :disabled="selectedLogs.length === 0"
            @click="exportSelected"
          >
            导出选中
          </el-button>
          <el-button
            type="warning"
            :icon="RefreshRight"
            :disabled="failedLogs.length === 0"
            @click="retryFailed"
          >
            重试失败 ({{ failedLogs.length }})
          </el-button>
        </div>
      </div>

      <!-- 统计摘要 -->
      <div class="stats-summary">
        <div class="stat-item" :class="{ active: filters.status === 'failed' }">
          <span class="stat-value">{{ stats.failed }}</span>
          <span class="stat-label">失败</span>
        </div>
        <div class="stat-item" :class="{ active: filters.status === 'sending' }">
          <span class="stat-value">{{ stats.sending }}</span>
          <span class="stat-label">发送中</span>
        </div>
        <div class="stat-item" :class="{ active: filters.status === 'success' }">
          <span class="stat-value">{{ stats.success }}</span>
          <span class="stat-label">成功</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.total }}</span>
          <span class="stat-label">总计</span>
        </div>
      </div>

      <!-- 日志列表视图 -->
      <el-card v-if="viewMode === 'list'" class="log-card">
        <el-table
          v-loading="loading"
          :data="filteredLogs"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="to"
            label="收件人"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="subject"
            label="主题"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="发送时间" width="170">
            <template #default="{ row }">
              {{ formatDate(row.sentAt) }}
            </template>
          </el-table-column>
          <el-table-column v-if="filters.status === 'failed'" label="失败原因" min-width="150">
            <template #default="{ row }">
              <el-tooltip :content="row.errorMessage" placement="top">
                <span class="error-message">{{ row.errorMessage }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column v-if="filters.status === 'failed'" label="建议" width="120">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                text
                @click="showSuggestion(row)"
              >
                查看建议
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'failed'"
                size="small"
                type="warning"
                text
                @click="retrySingle(row)"
              >
                重试
              </el-button>
              <el-button size="small" text @click="viewDetail(row)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

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

      <!-- 时间线视图 -->
      <el-card v-else-if="viewMode === 'timeline'" class="timeline-card">
        <el-timeline>
          <el-timeline-item
            v-for="log in filteredLogs"
            :key="log.id"
            :timestamp="formatDate(log.sentAt)"
            :type="getTimelineType(log.status)"
            placement="top"
          >
            <div class="timeline-content">
              <div class="timeline-header">
                <el-tag :type="getStatusType(log.status)" size="small">
                  {{ getStatusText(log.status) }}
                </el-tag>
                <span class="timeline-to">{{ log.to }}</span>
              </div>
              <div class="timeline-subject">
                {{ log.subject }}
              </div>
              <div v-if="log.status === 'failed'" class="timeline-error">
                {{ log.errorMessage }}
              </div>
              <div class="timeline-actions">
                <el-button v-if="log.status === 'failed'" size="small" @click="retrySingle(log)">
                  重试
                </el-button>
                <el-button size="small" @click="viewDetail(log)">
                  详情
                </el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>

        <el-empty v-if="filteredLogs.length === 0" description="暂无记录" />
      </el-card>

      <!-- 聚合视图 -->
      <el-card v-else class="aggregate-card">
        <div class="aggregate-grid">
          <!-- 按状态聚合 -->
          <div class="aggregate-item">
            <h4>按状态统计</h4>
            <div class="aggregate-chart">
              <el-progress
                type="circle"
                :percentage="stats.total > 0 ? Math.round(stats.success / stats.total * 100) : 0"
                :color="successColors"
              >
                <template #default>
                  <span class="progress-label">成功率</span>
                  <span class="progress-value">{{ stats.total > 0 ? Math.round(stats.success / stats.total * 100) : 0 }}%</span>
                </template>
              </el-progress>
            </div>
            <div class="aggregate-stats">
              <div class="agg-stat">
                <span class="dot success" />
                成功: {{ stats.success }}
              </div>
              <div class="agg-stat">
                <span class="dot failed" />
                失败: {{ stats.failed }}
              </div>
              <div class="agg-stat">
                <span class="dot sending" />
                发送中: {{ stats.sending }}
              </div>
            </div>
          </div>

          <!-- 按错误类型聚合 -->
          <div class="aggregate-item">
            <h4>失败原因分析</h4>
            <div v-if="errorAggregation.length > 0" class="error-list">
              <div
                v-for="err in errorAggregation"
                :key="err.error"
                class="error-item"
                @click="filterByError(err.error)"
              >
                <span class="error-name">{{ err.error }}</span>
                <span class="error-count">{{ err.count }} 次</span>
              </div>
            </div>
            <el-empty v-else description="暂无失败记录" :image-size="60" />
          </div>
        </div>
      </el-card>
    </div>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="发送详情" width="600px">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="收件人">
          {{ currentLog.to }}
        </el-descriptions-item>
        <el-descriptions-item label="主题">
          {{ currentLog.subject }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentLog.status)">
            {{ getStatusText(currentLog.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送时间">
          {{ formatDate(currentLog.sentAt) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.status === 'failed'" label="失败原因">
          {{ currentLog.errorMessage }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.status === 'failed'" label="建议操作">
          {{ getSuggestion(currentLog) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
        <el-button
          v-if="currentLog?.status === 'failed'"
          type="primary"
          @click="retrySingle(currentLog); detailVisible = false"
        >
          重试发送
        </el-button>
      </template>
    </el-dialog>

    <!-- 建议对话框 -->
    <el-dialog v-model="suggestionVisible" title="操作建议" width="500px">
      <div v-if="currentLog" class="suggestion-content">
        <el-alert
          :title="getSuggestionTitle(currentLog)"
          :type="getSuggestionType(currentLog)"
          :description="getSuggestion(currentLog)"
          show-icon
          :closable="false"
        />
        <div class="suggestion-actions">
          <el-button type="primary" @click="applySuggestion(currentLog)">
            立即修复
          </el-button>
          <el-button @click="suggestionVisible = false">
            关闭
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  List, Clock, DataAnalysis, Search, Refresh, Download,
  RefreshRight
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageHeader from '@/components/PageHeader.vue'

// 视图模式
const viewMode = ref('list')
const loading = ref(false)

// 日志数据
const logs = ref([])
const selectedLogs = ref([])

// 筛选条件
const filters = reactive({
  status: 'failed',  // 默认显示失败
  dateRange: null,
  keyword: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 统计数据
const stats = reactive({
  total: 0,
  success: 0,
  failed: 0,
  sending: 0
})

// 详情
const detailVisible = ref(false)
const suggestionVisible = ref(false)
const currentLog = ref(null)

// 成功进度颜色
const successColors = [
  { color: '#67c23a', percentage: 100 }
]

// 计算属性
const filteredLogs = computed(() => {
  let result = logs.value

  if (filters.keyword) {
    const keyword = filters.keyword.toLowerCase()
    result = result.filter(log =>
      log.to?.toLowerCase().includes(keyword) ||
      log.subject?.toLowerCase().includes(keyword)
    )
  }

  return result
})

const failedLogs = computed(() => {
  return logs.value.filter(log => log.status === 'failed')
})

const errorAggregation = computed(() => {
  const errors = {}
  logs.value.filter(log => log.status === 'failed').forEach(log => {
    const errorKey = log.errorMessage || '未知错误'
    errors[errorKey] = (errors[errorKey] || 0) + 1
  })

  return Object.entries(errors)
    .map(([error, count]) => ({ error, count }))
    .sort((a, b) => b.count - a.count)
})

// 方法
const getStatusType = (status) => {
  const map = {
    success: 'success',
    failed: 'danger',
    sending: 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    success: '成功',
    failed: '失败',
    sending: '发送中'
  }
  return map[status] || status
}

const getTimelineType = (status) => {
  const map = {
    success: 'success',
    failed: 'danger',
    sending: 'warning'
  }
  return map[status] || 'primary'
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const getSuggestionTitle = (log) => {
  if (!log.errorMessage) return '发送失败'

  const msg = log.errorMessage.toLowerCase()
  if (msg.includes('auth') || msg.includes('401') || msg.includes('unauthorized')) {
    return '授权失败'
  }
  if (msg.includes('timeout') || msg.includes('connection')) {
    return '连接超时'
  }
  if (msg.includes('recipient') || msg.includes('invalid')) {
    return '收件人无效'
  }
  if (msg.includes('quota') || msg.includes('limit')) {
    return '配额超限'
  }

  return '发送失败'
}

const getSuggestionType = (log) => {
  const title = getSuggestionTitle(log)
  const map = {
    '授权失败': 'warning',
    '连接超时': 'warning',
    '收件人无效': 'error',
    '配额超限': 'error'
  }
  return map[title] || 'warning'
}

const getSuggestion = (log) => {
  if (!log.errorMessage) return '请检查网络连接后重试'

  const msg = log.errorMessage.toLowerCase()
  if (msg.includes('auth') || msg.includes('401') || msg.includes('unauthorized')) {
    return 'API 密钥可能已过期或权限不足，请前往"配置与联通"页面重新配置'
  }
  if (msg.includes('timeout') || msg.includes('connection')) {
    return '网络连接超时，请检查网络环境或 SMTP 服务器配置是否正确'
  }
  if (msg.includes('recipient') || msg.includes('invalid')) {
    return '收件人邮箱地址无效，请检查邮箱格式是否正确'
  }
  if (msg.includes('quota') || msg.includes('limit')) {
    return '邮件发送配额已用完，请联系邮件服务商提升配额或等待配额重置'
  }

  return '请检查配置后重试，如问题持续存在请联系管理员'
}

const showSuggestion = (log) => {
  currentLog.value = log
  suggestionVisible.value = true
}

const applySuggestion = (log) => {
  suggestionVisible.value = false
  // 根据建议类型导航到对应页面
  const title = getSuggestionTitle(log)
  if (title === '授权失败') {
    // 跳转到配置页面
  }
}

// 加载日志
const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      status: filters.status || undefined,
      startDate: filters.dateRange?.[0],
      endDate: filters.dateRange?.[1]
    }

    const res = await request.get('/system/mail-logs', { params })

    // 处理返回数据
    const content = res?.content || res?.data?.content || []
    logs.value = content

    // 更新统计数据
    stats.total = res?.totalElements || content.length
    stats.success = content.filter(l => l.status === 'success').length
    stats.failed = content.filter(l => l.status === 'failed').length
    stats.sending = content.filter(l => l.status === 'sending').length

    pagination.total = res?.totalElements || 0
  } catch (e) {
    console.error('加载日志失败:', e)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSelectionChange = (selection) => {
  selectedLogs.value = selection
}

// 重试单个
const retrySingle = async (log) => {
  try {
    await request.post(`/system/mail-logs/${log.id}/retry`)
    ElMessage.success('已添加到重试队列')
    loadLogs()
  } catch (e) {
    ElMessage.error('重试失败: ' + (e.message || '未知错误'))
  }
}

// 重试所有失败
const retryFailed = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要重试 ${failedLogs.value.length} 条失败记录吗？`,
      '批量重试',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const ids = failedLogs.value.map(l => l.id)
    await request.post('/system/mail-logs/batch-retry', { ids })
    ElMessage.success('已添加到重试队列')
    loadLogs()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('重试失败')
    }
  }
}

// 导出选中
const exportSelected = () => {
  ElMessage.info('导出功能开发中')
}

const viewDetail = (log) => {
  currentLog.value = log
  detailVisible.value = true
}

const filterByError = (error) => {
  filters.keyword = error
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.mail-tracking-container {
  padding: 20px;
}

.tracking-content {
  max-width: 1400px;
  margin: 0 auto;
}

/* 工具栏 */
.tracking-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.view-tabs {
  display: flex;
  gap: 12px;
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

/* 统计摘要 */
.stats-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 24px;
  border-radius: 8px;
  transition: all 0.2s;
  cursor: pointer;
}

.stat-item:hover {
  background: var(--el-fill-color-light);
}

.stat-item.active {
  background: var(--el-color-primary-light-9);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 日志卡片 */
.log-card {
  margin-bottom: 16px;
}

.error-message {
  color: var(--el-color-danger);
  cursor: pointer;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 时间线 */
.timeline-card {
  padding: 20px;
}

.timeline-content {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.timeline-to {
  font-weight: 500;
}

.timeline-subject {
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.timeline-error {
  color: var(--el-color-danger);
  font-size: 13px;
  margin-bottom: 8px;
}

.timeline-actions {
  display: flex;
  gap: 8px;
}

/* 聚合视图 */
.aggregate-card {
  padding: 20px;
}

.aggregate-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.aggregate-item {
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.aggregate-item h4 {
  margin: 0 0 16px 0;
}

.aggregate-chart {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.progress-label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.progress-value {
  display: block;
  font-size: 20px;
  font-weight: 600;
}

.aggregate-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agg-stat {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.success { background: var(--el-color-success); }
.dot.failed { background: var(--el-color-danger); }
.dot.sending { background: var(--el-color-warning); }

.error-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.error-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}

.error-item:hover {
  background: var(--el-color-primary-light-9);
}

.error-count {
  color: var(--el-text-color-secondary);
}

/* 建议对话框 */
.suggestion-content {
  padding: 16px 0;
}

.suggestion-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
