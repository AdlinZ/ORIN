<template>
  <div class="page-container">
    <PageHeader
      title="文件管理"
      description="管理服务器内部的多模态文件，包括文字转图片、文字转视频、文字转语音生成的资源文件"
      icon="Folder"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="fetchFiles" :loading="loading">刷新</el-button>
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
            <div class="stat-value">{{ stats.totalFiles }}</div>
            <div class="stat-label">总文件数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon image">
            <el-icon><Picture /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.imageCount }}</div>
            <div class="stat-label">图片</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon video">
            <el-icon><VideoCamera /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.videoCount }}</div>
            <div class="stat-label">视频</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon audio">
            <el-icon><Microphone /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.audioCount }}</div>
            <div class="stat-label">音频</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选和操作栏 -->
    <el-card shadow="never" class="filter-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="12">
          <el-segmented v-model="fileTypeFilter" :options="fileTypeOptions" @change="fetchFiles" />
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
      <el-table :data="filteredFiles" v-loading="loading" stripe>
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
              <el-icon v-else-if="row.fileType === 'IMAGE'" :size="24" class="file-icon"><Picture /></el-icon>
              <el-icon v-else-if="row.fileType === 'VIDEO'" :size="24" class="file-icon"><VideoCamera /></el-icon>
              <el-icon v-else-if="row.fileType === 'AUDIO'" :size="24" class="file-icon"><Microphone /></el-icon>
              <el-icon v-else :size="24" class="file-icon"><Document /></el-icon>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="file-name">{{ row.fileName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="fileType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getFileTypeTag(row.fileType)" size="small">{{ getFileTypeLabel(row.fileType) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="fileSize" label="大小" width="120" align="center">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>

        <el-table-column prop="uploadedBy" label="上传者" width="100" align="center">
          <template #default="{ row }">
            {{ row.uploadedBy || 'system' }}
          </template>
        </el-table-column>

        <el-table-column prop="uploadedAt" label="上传时间" width="180" sortable>
          <template #default="{ row }">
            {{ formatDateTime(row.uploadedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Download" @click="downloadFile(row)">下载</el-button>
            <el-button type="danger" link :icon="Delete" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="totalFiles > pageSize"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="totalFiles"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
        class="pagination"
      />
    </el-card>

    <!-- 删除确认对话框 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <p>确定要删除文件 <strong>{{ currentFile?.fileName }}</strong> 吗？</p>
      <p class="text-danger">此操作不可恢复</p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleting" @click="deleteFile">删除</el-button>
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

.stat-icon.total { background: var(--orin-primary-soft); color: var(--orin-primary); }
.stat-icon.image { background: var(--success-50); color: var(--success-color); }
.stat-icon.video { background: var(--warning-50); color: var(--warning-color); }
.stat-icon.audio { background: var(--primary-50); color: var(--primary-color); }

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
  color: #909399;
}

.file-name {
  font-weight: 500;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.text-danger {
  color: #f56c6c;
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
</style>
