<template>
  <div class="file-management page-container fade-in">
    <section class="file-shell">
      <header class="file-topbar">
        <div class="topbar-copy">
          <span class="topbar-eyebrow">系统设置</span>
          <h1>文件管理</h1>
          <p>管理多模态生成文件、文件类型、存储占用与下载入口。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Refresh" :loading="loading" @click="fetchFiles">
            刷新
          </el-button>
        </div>
      </header>

      <section class="summary-grid">
        <article class="summary-card primary">
          <span>文件总数</span>
          <strong>{{ stats.totalFiles || 0 }}</strong>
          <p>当前存储中的全部资源</p>
        </article>
        <article class="summary-card">
          <span>图片</span>
          <strong>{{ stats.imageCount || 0 }}</strong>
          <p>图像生成和上传资源</p>
        </article>
        <article class="summary-card">
          <span>视频</span>
          <strong>{{ stats.videoCount || 0 }}</strong>
          <p>视频生成与转码产物</p>
        </article>
        <article class="summary-card">
          <span>音频 / 文档</span>
          <strong>{{ (stats.audioCount || 0) + (stats.documentCount || 0) }}</strong>
          <p>音频 {{ stats.audioCount || 0 }} · 文档 {{ stats.documentCount || 0 }}</p>
        </article>
      </section>

      <section class="file-workspace">
        <div class="workspace-head">
          <div>
            <h2>文件清单</h2>
            <p>按文件类型、名称和上传时间维护生成资源。</p>
          </div>
          <div class="workspace-actions">
            <span>{{ totalFiles }} 个文件</span>
          </div>
        </div>

        <div class="filter-panel">
          <el-select
            v-model="fileTypeFilter"
            placeholder="文件类型"
            class="filter-control"
            @change="handleTypeChange"
          >
            <el-option
              v-for="option in fileTypeOptions"
              :key="option.value || 'all'"
              :label="`${option.label} ${getTypeCount(option.value)}`"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名"
            clearable
            class="filter-search"
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="filteredFiles"
          row-key="id"
          class="file-table"
          empty-text="暂无文件资源，请调整筛选或等待生成任务产出"
        >
          <el-table-column label="预览" width="86" align="center">
            <template #default="{ row }">
              <div class="file-preview">
                <el-image
                  v-if="row.fileType === 'IMAGE' && row.thumbnailPath"
                  :src="`/api/v1/multimodal/files/${row.id}/thumbnail`"
                  :preview-src-list="[`/api/v1/multimodal/files/${row.id}/download`]"
                  fit="cover"
                  class="preview-img"
                />
                <div v-else class="file-icon-card" :class="getFileTypeClass(row.fileType)">
                  <el-icon :size="22">
                    <component :is="getFileIcon(row.fileType)" />
                  </el-icon>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="文件" min-width="300">
            <template #default="{ row }">
              <div class="file-copy">
                <strong>{{ row.fileName }}</strong>
                <span>{{ row.id }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="getFileTypeTag(row.fileType)">
                {{ getFileTypeLabel(row.fileType) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              <span class="muted-text">{{ formatFileSize(row.fileSize) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="上传者" width="140">
            <template #default="{ row }">
              <span class="muted-text">{{ row.uploadedBy || 'system' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ formatDateTime(row.uploadedAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="130"
            align="right"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="action-buttons">
                <el-tooltip content="下载文件" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="Download"
                    @click="downloadFile(row)"
                  />
                </el-tooltip>
                <el-tooltip content="删除文件" placement="top">
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    :loading="deleting && currentFile?.id === row.id"
                    @click="confirmDelete(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <span>第 {{ currentPage }} 页 · 每页 {{ pageSize }} 条</span>
          <el-pagination
            v-if="totalFiles > pageSize"
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="totalFiles"
            layout="prev, pager, next"
            small
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Document,
  Download,
  Microphone,
  Picture,
  Refresh,
  Search,
  VideoCamera
} from '@element-plus/icons-vue'
import request from '@/utils/request'

const fileTypeOptions = [
  { label: '全部', value: '' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: '文档', value: 'DOCUMENT' }
]

const loading = ref(false)
const deleting = ref(false)
const files = ref([])
const stats = ref({ totalFiles: 0, imageCount: 0, videoCount: 0, audioCount: 0, documentCount: 0 })
const fileTypeFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalFiles = ref(0)
const currentFile = ref(null)

const fetchFiles = async () => {
  loading.value = true
  try {
    const url = fileTypeFilter.value
      ? `/multimodal/files/type/${fileTypeFilter.value}`
      : '/multimodal/files'
    const data = await request.get(url)
    files.value = Array.isArray(data) ? data : []
    totalFiles.value = files.value.length

    await fetchStats()
  } catch (error) {
    ElMessage.error('获取文件列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try {
    const data = await request.get('/multimodal/stats')
    stats.value = data
  } catch (error) {
    console.error('获取统计信息失败', error)
  }
}

const filteredFiles = computed(() => {
  let result = files.value

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(file => file.fileName?.toLowerCase().includes(keyword))
  }

  const start = (currentPage.value - 1) * pageSize.value
  return result.slice(start, start + pageSize.value)
})

const handleSearch = () => {
  currentPage.value = 1
}

const handlePageChange = (page) => {
  currentPage.value = page
}

const handleTypeChange = (type) => {
  fileTypeFilter.value = type
  currentPage.value = 1
  fetchFiles()
}

const resetFilters = () => {
  fileTypeFilter.value = ''
  searchKeyword.value = ''
  currentPage.value = 1
  fetchFiles()
}

const downloadFile = (row) => {
  window.open(`/api/v1/multimodal/files/${row.id}/download`, '_blank')
}

const confirmDelete = async (row) => {
  currentFile.value = row
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${row.fileName}" 吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteFile()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const deleteFile = async () => {
  if (!currentFile.value) return

  deleting.value = true
  try {
    await request.delete(`/multimodal/files/${currentFile.value.id}`)
    ElMessage.success('文件删除成功')
    currentFile.value = null
    await fetchFiles()
  } catch (error) {
    ElMessage.error('删除文件失败')
    console.error(error)
  } finally {
    deleting.value = false
  }
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

const getFileIcon = (type) => {
  const map = {
    IMAGE: Picture,
    VIDEO: VideoCamera,
    AUDIO: Microphone,
    DOCUMENT: Document
  }
  return map[type] || Document
}

const getFileTypeTag = (type) => {
  const map = {
    IMAGE: 'success',
    VIDEO: 'danger',
    AUDIO: 'warning',
    DOCUMENT: 'primary'
  }
  return map[type] || 'info'
}

const getFileTypeClass = (type) => {
  return `type-${String(type || 'document').toLowerCase()}`
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

onMounted(() => {
  fetchFiles()
})
</script>

<style scoped>
.file-management {
  min-height: 100vh;
  padding: 32px;
  max-width: 1600px;
  margin: 0 auto;
  color: var(--el-text-color-primary);
}

.fade-in {
  animation: fadeIn 0.35s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.file-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.file-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.topbar-copy {
  min-width: 0;
}

.topbar-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.topbar-copy h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-weight: 760;
  line-height: 1.2;
}

.topbar-copy p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.topbar-actions,
.workspace-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.workspace-actions {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.04);
}

.summary-card.primary {
  border-color: var(--el-color-primary-light-7);
  background: linear-gradient(180deg, var(--el-color-primary-light-9), var(--el-bg-color));
}

.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  line-height: 1;
}

.summary-card p {
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-workspace {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.05);
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.workspace-head h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 720;
}

.workspace-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.filter-control {
  width: 180px;
}

.filter-search {
  flex: 1 1 280px;
  min-width: 220px;
}

.file-table {
  width: 100%;
}

:deep(.file-table .el-table__header th) {
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.file-preview {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-img,
.file-icon-card {
  width: 42px;
  height: 42px;
  border-radius: 8px;
}

.preview-img {
  object-fit: cover;
  cursor: pointer;
}

.file-icon-card {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
}

.file-icon-card.type-image {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.file-icon-card.type-video {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.file-icon-card.type-audio {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.file-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.file-copy strong {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-copy span,
.muted-text,
.time-text {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
  padding: 14px 18px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

html.dark .summary-card,
html.dark .file-workspace {
  box-shadow: none;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .file-management {
    padding: 20px;
  }

  .file-topbar,
  .workspace-head,
  .table-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .filter-control,
  .filter-search {
    width: 100%;
  }
}
</style>
