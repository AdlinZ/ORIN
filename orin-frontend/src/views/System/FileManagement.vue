<template>
  <div class="page-container">
    <OrinEntityHeader
      domain="系统设置"
      title="文件管理"
      description="管理多模态生成文件、文件类型、存储占用与下载入口"
      :summary="fileHeaderSummary"
    >
      <template #actions>
        <a-button class="quiet-action" :loading="loading" @click="fetchFiles">
          刷新
        </a-button>
      </template>

      <template #filters>
        <OrinArcoFilterGrid>
          <a-select
            v-model="fileTypeFilter"
            placeholder="文件类型"
            allow-clear
            @change="handleTypeChange"
          >
            <a-option
              v-for="option in fileTypeOptions"
              :key="option.value || 'all'"
              :label="`${option.label} ${getTypeCount(option.value)}`"
              :value="option.value"
            />
          </a-select>
          <template #search>
            <a-input-search
              v-model="searchKeyword"
              placeholder="搜索文件名..."
              allow-clear
              @input="handleSearch"
            />
          </template>
          <template #reset>
            <a-button class="quiet-action" @click="resetFilters">
              重置
            </a-button>
          </template>
        </OrinArcoFilterGrid>
      </template>
    </OrinEntityHeader>

    <OrinArcoDataTable
      class="table-card"
      :columns="fileColumns"
      :data="filteredFiles"
      :loading="loading"
      row-key="id"
    >
      <template #header>
        <div class="table-title">
          <strong>文件清单</strong>
          <span>按文件类型、名称和上传时间维护生成资源</span>
        </div>
      </template>

      <template #preview="{ record }">
            <div class="file-preview">
              <el-image
                v-if="record.fileType === 'IMAGE' && record.thumbnailPath"
                :src="`/api/v1/multimodal/files/${record.id}/thumbnail`"
                :preview-src-list="[`/api/v1/multimodal/files/${record.id}/download`]"
                fit="cover"
                class="preview-img"
              />
              <el-icon v-else-if="record.fileType === 'IMAGE'" :size="24" class="file-icon">
                <Picture />
              </el-icon>
              <el-icon v-else-if="record.fileType === 'VIDEO'" :size="24" class="file-icon">
                <VideoCamera />
              </el-icon>
              <el-icon v-else-if="record.fileType === 'AUDIO'" :size="24" class="file-icon">
                <Microphone />
              </el-icon>
              <el-icon v-else :size="24" class="file-icon">
                <Document />
              </el-icon>
            </div>
      </template>

      <template #fileName="{ record }">
        <span class="file-name">{{ record.fileName }}</span>
      </template>

      <template #fileType="{ record }">
        <OrinArcoSemanticTag family="file" :value="record.fileType">
          {{ getFileTypeLabel(record.fileType) }}
        </OrinArcoSemanticTag>
      </template>

      <template #fileSize="{ record }">
        <span>{{ formatFileSize(record.fileSize) }}</span>
      </template>

      <template #uploadedBy="{ record }">
        <span>{{ record.uploadedBy || 'system' }}</span>
      </template>

      <template #uploadedAt="{ record }">
        <span class="time-text">{{ formatDateTime(record.uploadedAt) }}</span>
      </template>

      <template #actions="{ record }">
        <OrinArcoRowActions
          :actions="fileRowActions"
          @select="action => handleRowAction(action, record)"
        />
      </template>

      <template #empty>
        <OrinEmptyState
          description="暂无文件资源，请调整筛选或等待生成任务产出"
          action-label="刷新文件"
          @action="fetchFiles"
        />
      </template>

      <template #footer>
        <a-pagination
          v-if="totalFiles > pageSize"
          v-model:current="currentPage"
          :page-size="pageSize"
          :total="totalFiles"
          show-total
          size="small"
          @change="handlePageChange"
        />
      </template>
    </OrinArcoDataTable>

    <OrinArcoConfirmDialog
      v-model="deleteDialogVisible"
      title="确认删除"
      ok-text="删除"
      status="danger"
      @confirm="deleteFile"
    >
      <p>确定要删除文件 <strong>{{ currentFile?.fileName }}</strong> 吗？</p>
      <p class="text-danger">
        此操作不可恢复
      </p>
    </OrinArcoConfirmDialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, VideoCamera, Microphone, Document } from '@element-plus/icons-vue'
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import OrinArcoConfirmDialog from '@/ui/arco/OrinArcoConfirmDialog.vue'
import OrinArcoDataTable from '@/ui/arco/OrinArcoDataTable.vue'
import OrinArcoFilterGrid from '@/ui/arco/OrinArcoFilterGrid.vue'
import OrinArcoRowActions from '@/ui/arco/OrinArcoRowActions.vue'
import OrinArcoSemanticTag from '@/ui/arco/OrinArcoSemanticTag.vue'
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

const fileColumns = [
  { title: '预览', dataIndex: 'preview', width: 76, align: 'center', slotName: 'preview' },
  { title: '文件名', dataIndex: 'fileName', width: 280, ellipsis: true, tooltip: true, slotName: 'fileName' },
  { title: '类型', dataIndex: 'fileType', width: 110, slotName: 'fileType' },
  { title: '大小', dataIndex: 'fileSize', width: 110, slotName: 'fileSize' },
  { title: '上传者', dataIndex: 'uploadedBy', width: 120, slotName: 'uploadedBy' },
  { title: '上传时间', dataIndex: 'uploadedAt', width: 180, slotName: 'uploadedAt' },
  { title: '操作', dataIndex: 'actions', width: 120, fixed: 'right', slotName: 'actions' }
]

const fileRowActions = [
  { key: 'download', label: '下载' },
  { key: 'delete', label: '删除', danger: true }
]

const fileHeaderSummary = computed(() => [
  { label: '文件总数', value: stats.value.totalFiles || 0 },
  { label: '图片', value: stats.value.imageCount || 0 },
  { label: '视频', value: stats.value.videoCount || 0 },
  { label: '音频', value: stats.value.audioCount || 0 },
  { label: '文档', value: stats.value.documentCount || 0 }
])

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

const resetFilters = () => {
  fileTypeFilter.value = ''
  searchKeyword.value = ''
  currentPage.value = 1
  fetchFiles()
}

const handleRowAction = (action, row) => {
  const handlers = {
    download: downloadFile,
    delete: confirmDelete
  }
  handlers[action]?.(row)
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
  padding: 0;
  color: #243244;
}

.table-card {
  border-radius: 8px;
}

.table-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.table-title strong {
  color: #243244;
  font-weight: 680;
}

.table-title span {
  color: #6b7a90;
  font-size: 12px;
}

.quiet-action {
  border-color: #e3e9ef;
  color: #3f4d63;
  background: #ffffff;
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
  color: #243244;
  font-weight: 560;
}

.text-danger {
  color: var(--error-500);
  font-size: 14px;
}
</style>
