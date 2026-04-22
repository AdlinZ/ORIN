<template>
  <div class="page-container">
    <PageHeader
      title="文件管理"
      description="管理服务器内部的多模态文件，包括文字转图片、文字转视频、文字转语音生成的资源文件"
      icon="Folder"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="fetchFiles">
          刷新
        </el-button>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon total">
            <el-icon><Files /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.totalFiles }}
            </div>
            <div class="stat-label">
              总文件数
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon image">
            <el-icon><Picture /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.imageCount }}
            </div>
            <div class="stat-label">
              图片
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon video">
            <el-icon><VideoCamera /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.videoCount }}
            </div>
            <div class="stat-label">
              视频
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon audio">
            <el-icon><Microphone /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.audioCount }}
            </div>
            <div class="stat-label">
              音频
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选和操作栏 -->
    <el-card shadow="never" class="filter-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="12">
          <div class="media-filter-tabs" role="tablist" aria-label="文件类型筛选">
            <button
              v-for="option in fileTypeOptions"
              :key="option.value || 'all'"
              type="button"
              class="media-filter-tab"
              :class="{ active: fileTypeFilter === option.value }"
              @click="handleTypeChange(option.value)"
            >
              <span class="tab-label">{{ option.label }}</span>
              <span class="tab-count">{{ getTypeCount(option.value) }}</span>
            </button>
          </div>
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名..."
            :prefix-icon="Search"
            clearable
            style="width: 250px;"
            @input="handleSearch"
          />
        </el-col>
      </el-row>
    </el-card>

    <!-- 文件列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredFiles" stripe>
        <el-table-column label="预览" width="80" align="center">
          <template #default="{ row }">
            <div class="file-preview">
              <el-image
                v-if="row.fileType === 'IMAGE' && row.thumbnailPath"
                :src="`/api/v1/multimodal/files/${row.id}/thumbnail`"
                :preview-src-list="[`/api/v1/multimodal/files/${row.id}/download`]"
                fit="cover"
                class="preview-img"
              />
              <el-icon v-else-if="row.fileType === 'IMAGE'" :size="24" class="file-icon">
                <Picture />
              </el-icon>
              <el-icon v-else-if="row.fileType === 'VIDEO'" :size="24" class="file-icon">
                <VideoCamera />
              </el-icon>
              <el-icon v-else-if="row.fileType === 'AUDIO'" :size="24" class="file-icon">
                <Microphone />
              </el-icon>
              <el-icon v-else :size="24" class="file-icon">
                <Document />
              </el-icon>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="fileName"
          label="文件名"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="file-name">{{ row.fileName }}</span>
          </template>
        </el-table-column>

        <el-table-column
          prop="fileType"
          label="类型"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="getFileTypeTag(row.fileType)" size="small">
              {{ getFileTypeLabel(row.fileType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="fileSize"
          label="大小"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>

        <el-table-column
          prop="uploadedBy"
          label="上传者"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            {{ row.uploadedBy || 'system' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="uploadedAt"
          label="上传时间"
          width="180"
          sortable
        >
          <template #default="{ row }">
            {{ formatDateTime(row.uploadedAt) }}
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          width="150"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="Download"
              @click="downloadFile(row)"
            >
              下载
            </el-button>
            <el-button
              type="danger"
              link
              :icon="Delete"
              @click="confirmDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="totalFiles > pageSize"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="totalFiles"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 删除确认对话框 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <p>确定要删除文件 <strong>{{ currentFile?.fileName }}</strong> 吗？</p>
      <p class="text-danger">
        此操作不可恢复
      </p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">
          取消
        </el-button>
        <el-button type="danger" :loading="deleting" @click="deleteFile">
          删除
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Download, Delete, Files, Picture, VideoCamera, Microphone, Document } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

// 文件类型过滤选项
const fileTypeOptions = [
  { label: '全部', value: '' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: '文档', value: 'DOCUMENT' }
]

// 状态
const loading = ref(false)
const deleting = ref(false)
const files = ref([])
const stats = ref({ totalFiles: 0, imageCount: 0, videoCount: 0, audioCount: 0, documentCount: 0 })
const fileTypeFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalFiles = ref(0)
const deleteDialogVisible = ref(false)
const currentFile = ref(null)

// 获取文件列表
const fetchFiles = async () => {
  loading.value = true
  try {
    const url = fileTypeFilter.value
      ? `/multimodal/files/type/${fileTypeFilter.value}`
      : '/multimodal/files'
    const data = await request.get(url)
    files.value = Array.isArray(data) ? data : []
    totalFiles.value = files.value.length

    // 获取统计信息
    await fetchStats()
  } catch (error) {
    ElMessage.error('获取文件列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 获取统计信息
const fetchStats = async () => {
  try {
    const data = await request.get('/multimodal/stats')
    stats.value = data
  } catch (error) {
    console.error('获取统计信息失败', error)
  }
}

// 筛选文件
const filteredFiles = computed(() => {
  let result = files.value

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(f => f.fileName?.toLowerCase().includes(keyword))
  }

  // 分页
  const start = (currentPage.value - 1) * pageSize.value
  return result.slice(start, start + pageSize.value)
})

// 搜索
const handleSearch = () => {
  currentPage.value = 1
}

// 分页
const handlePageChange = (page) => {
  currentPage.value = page
}

const handleTypeChange = (type) => {
  fileTypeFilter.value = type
  currentPage.value = 1
  fetchFiles()
}

// 下载文件
const downloadFile = (row) => {
  window.open(`/api/v1/multimodal/files/${row.id}/download`, '_blank')
}

// 确认删除
const confirmDelete = (row) => {
  currentFile.value = row
  deleteDialogVisible.value = true
}

// 删除文件
const deleteFile = async () => {
  if (!currentFile.value) return

  deleting.value = true
  try {
    await request.delete(`/multimodal/files/${currentFile.value.id}`)
    ElMessage.success('文件删除成功')
    deleteDialogVisible.value = false
    await fetchFiles()
  } catch (error) {
    ElMessage.error('删除文件失败')
    console.error(error)
  } finally {
    deleting.value = false
  }
}

// 工具函数
const getFileTypeTag = (type) => {
  const map = {
    IMAGE: 'success',
    VIDEO: 'warning',
    AUDIO: 'info',
    DOCUMENT: ''
  }
  return map[type] || ''
}

const getFileTypeLabel = (type) => {
  const map = {
    IMAGE: '图片',
    VIDEO: '视频',
    AUDIO: '音频',
    DOCUMENT: '文档'
  }
  return map[type] || type
}

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getTypeCount = (type) => {
  if (!type) return stats.value.totalFiles || 0
  const map = {
    IMAGE: stats.value.imageCount || 0,
    VIDEO: stats.value.videoCount || 0,
    AUDIO: stats.value.audioCount || 0,
    DOCUMENT: stats.value.documentCount || 0
  }
  return map[type] ?? 0
}

// 初始化
onMounted(() => {
  fetchFiles()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid var(--border-color, #e2e8f0);
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 22px;
}

.stat-icon.total { background: var(--primary-light); color: var(--orin-primary); }
.stat-icon.image { background: var(--success-light); color: var(--success-600); }
.stat-icon.video { background: var(--warning-light); color: var(--warning-600); }
.stat-icon.audio { background: var(--info-light); color: var(--info-600); }

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  margin-top: 4px;
}

.filter-card {
  margin-bottom: 20px;
}

.media-filter-tabs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border-radius: 12px;
  border: 1px solid var(--border-color, #e2e8f0);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  max-width: 100%;
  overflow-x: auto;
}

.media-filter-tab {
  border: 0;
  background: transparent;
  color: var(--text-secondary, #64748b);
  border-radius: 10px;
  height: 36px;
  padding: 0 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.media-filter-tab:hover {
  background: rgba(15, 159, 149, 0.08);
  color: var(--orin-primary, #0f9f95);
}

.media-filter-tab.active {
  background: linear-gradient(135deg, #0fa89d 0%, #0f8f86 100%);
  color: #ffffff;
  box-shadow: 0 6px 16px rgba(15, 159, 149, 0.22);
}

.tab-count {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  padding: 0 6px;
  background: rgba(100, 116, 139, 0.14);
  color: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
}

.media-filter-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.24);
}

.table-card {
  margin-bottom: 20px;
}

.file-preview {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-img {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  object-fit: cover;
  cursor: pointer;
}

.file-icon {
  color: var(--neutral-gray-400);
}

.file-name {
  font-weight: 500;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.text-danger {
  color: var(--error-500);
  font-size: 14px;
}

/* Dark mode */
html.dark .stat-card {
  border-color: var(--border-color);
}

html.dark .stat-value {
  color: var(--text-primary);
}

html.dark .stat-label {
  color: var(--text-secondary);
}

html.dark .media-filter-tabs {
  background: rgba(30, 41, 59, 0.72);
  border-color: rgba(71, 85, 105, 0.5);
}

html.dark .media-filter-tab {
  color: #94a3b8;
}

html.dark .media-filter-tab .tab-count {
  background: rgba(15, 23, 42, 0.72);
}

html.dark .media-filter-tab.active {
  background: rgba(45, 212, 191, 0.18);
  color: #5eead4;
}
</style>
