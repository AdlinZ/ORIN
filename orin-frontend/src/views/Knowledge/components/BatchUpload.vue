<template>
  <div class="batch-upload">
    <!-- Drop Zone -->
    <div
      class="drop-zone"
      :class="{ 'drag-over': isDragOver }"
      @dragover.prevent="isDragOver = true"
      @dragleave="isDragOver = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <input
        ref="fileInput"
        type="file"
        multiple
        :accept="acceptedTypes"
        style="display: none"
        @change="handleFileSelect"
      />
      <el-icon class="upload-icon"><Upload /></el-icon>
      <div class="upload-text">
        <span>拖拽文件到此处</span>
        <span class="upload-hint">或点击选择文件</span>
      </div>
      <div class="upload-types">
        支持 PDF、Word、Markdown、TXT
      </div>
    </div>

    <!-- Upload Queue -->
    <div v-if="uploadQueue.length > 0" class="upload-queue">
      <div class="queue-header">
        <span>上传队列 ({{ uploadQueue.length }})</span>
        <el-button text size="small" @click="clearQueue">清空</el-button>
      </div>
      <div class="queue-list">
        <div
          v-for="item in uploadQueue"
          :key="item.id"
          class="queue-item"
        >
          <div class="item-info">
            <el-icon><Document /></el-icon>
            <span class="item-name" :title="item.file.name">{{ item.file.name }}</span>
          </div>
          <div class="item-status">
            <el-progress
              v-if="item.status === 'uploading'"
              :percentage="item.progress"
              :stroke-width="4"
              :show-text="false"
            />
            <el-tag v-else-if="item.status === 'success'" type="success" size="small">
              成功
            </el-tag>
            <el-tag v-else-if="item.status === 'error'" type="danger" size="small">
              失败
            </el-tag>
            <el-tag v-else-if="item.status === 'queued'" size="small">
              等待
            </el-tag>
          </div>
          <el-button
            v-if="item.status === 'error'"
            text
            size="small"
            @click="retryUpload(item)"
          >
            重试
          </el-button>
        </div>
      </div>
    </div>

    <!-- Upload Button -->
    <el-button
      v-if="!dialogMode && uploadQueue.length > 0"
      type="primary"
      :loading="isUploading"
      style="width: 100%; margin-top: 12px"
      @click="startUpload"
    >
      开始上传 ({{ uploadQueue.filter(u => u.status === 'queued').length }})
    </el-button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Document } from '@element-plus/icons-vue'
import { uploadDocument, triggerVectorization } from '@/api/knowledge'

const props = defineProps({
  kbId: {
    type: String,
    required: true
  },
  dialogMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['upload-start', 'upload-success', 'upload-error'])

const fileInput = ref(null)
const isDragOver = ref(false)
const uploadQueue = ref([])
const isUploading = ref(false)

const acceptedTypes = '.pdf,.doc,.docx,.md,.markdown,.txt'

// Computed
const hasFilesToUpload = computed(() => {
  return uploadQueue.value.some(u => u.status === 'queued')
})

// Methods
const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event) => {
  const files = Array.from(event.target.files)
  addFilesToQueue(files)
  event.target.value = '' // Reset input
}

const handleDrop = (event) => {
  isDragOver.value = false
  const files = Array.from(event.dataTransfer.files)
  addFilesToQueue(files)
}

const addFilesToQueue = (files) => {
  const validTypes = ['pdf', 'doc', 'docx', 'md', 'markdown', 'txt']
  const maxSize = 50 * 1024 * 1024 // 50MB

  for (const file of files) {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!validTypes.includes(ext)) {
      ElMessage.warning(`不支持的文件类型: ${file.name}`)
      continue
    }
    if (file.size > maxSize) {
      ElMessage.warning(`文件过大: ${file.name} (最大 50MB)`)
      continue
    }

    uploadQueue.value.push({
      id: Date.now() + Math.random(),
      file,
      status: 'queued',
      progress: 0,
      error: null
    })
  }
}

const clearQueue = () => {
  uploadQueue.value = uploadQueue.value.filter(u => u.status === 'uploading')
}

const startUpload = async () => {
  const items = uploadQueue.value.filter(u => u.status === 'queued')
  if (items.length === 0) return

  isUploading.value = true

  for (const item of items) {
    await uploadSingleFile(item)
  }

  isUploading.value = false
}

const uploadSingleFile = async (item) => {
  item.status = 'uploading'
  emit('upload-start', item.file)

  try {
    const formData = new FormData()
    formData.append('file', item.file)

    // Upload file
    const uploadRes = await uploadDocument(props.kbId, item.file)
    const docId = uploadRes.data?.id

    if (docId) {
      // Trigger vectorization
      await triggerVectorization(docId)
    }

    item.status = 'success'
    item.progress = 100
    emit('upload-success', { file: item.file, docId })

  } catch (err) {
    item.status = 'error'
    item.error = err.message || '上传失败'
    emit('upload-error', err)
    ElMessage.error(`${item.file.name} 上传失败`)
  }
}

const retryUpload = (item) => {
  item.status = 'queued'
  item.progress = 0
  item.error = null
  if (!isUploading.value) {
    startUpload()
  }
}

// Auto-upload when dialog mode
if (props.dialogMode) {
  // In dialog mode, watch for new files and auto-upload
}
</script>

<style scoped>
.batch-upload {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.drop-zone:hover,
.drop-zone.drag-over {
  border-color: #409EFF;
  background: #ecf5ff;
}

.upload-icon {
  font-size: 32px;
  color: #909399;
  margin-bottom: 8px;
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}

.upload-hint {
  color: #909399;
  font-size: 12px;
}

.upload-types {
  margin-top: 8px;
  font-size: 11px;
  color: #c0c4cc;
}

.upload-queue {
  background: #f9f9f9;
  border-radius: 6px;
  padding: 12px;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: white;
  border-radius: 4px;
}

.item-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.item-info .el-icon {
  flex-shrink: 0;
  color: #909399;
}

.item-name {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-status {
  width: 60px;
  flex-shrink: 0;
}
</style>
