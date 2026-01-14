<template>
  <div class="multimodal-upload">
    <el-card class="premium-card">
      <template #header>
        <div class="card-header">
          <div>
            <el-icon><Picture /></el-icon>
            <span>多模态文件管理</span>
          </div>
          <el-button type="primary" :icon="Upload" @click="uploadDialog = true">
            上传文件
          </el-button>
        </div>
      </template>

      <!-- 统计信息 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-statistic title="总文件数" :value="stats.totalFiles" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="图片" :value="stats.imageCount">
            <template #suffix>
              <el-icon color="var(--primary-color)"><Picture /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="音频" :value="stats.audioCount">
            <template #suffix>
              <el-icon color="var(--success-color)"><Headset /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="视频" :value="stats.videoCount">
            <template #suffix>
              <el-icon color="var(--warning-color)"><VideoCamera /></el-icon>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 文件类型筛选 -->
      <el-radio-group v-model="filterType" @change="loadFiles" class="filter-group">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="IMAGE">图片</el-radio-button>
        <el-radio-button label="AUDIO">音频</el-radio-button>
        <el-radio-button label="VIDEO">视频</el-radio-button>
        <el-radio-button label="DOCUMENT">文档</el-radio-button>
      </el-radio-group>

      <!-- 文件网格 -->
      <div v-loading="loading" class="file-grid">
        <div
          v-for="file in files"
          :key="file.id"
          class="file-card"
          @click="viewFile(file)"
        >
          <div class="file-preview">
            <img
              v-if="file.fileType === 'IMAGE' && file.thumbnailPath"
              :src="`/api/v1/multimodal/files/${file.id}/thumbnail`"
              :alt="file.fileName"
            />
            <el-icon v-else class="file-icon" :size="60">
              <Picture v-if="file.fileType === 'IMAGE'" />
              <Headset v-else-if="file.fileType === 'AUDIO'" />
              <VideoCamera v-else-if="file.fileType === 'VIDEO'" />
              <Document v-else />
            </el-icon>
          </div>
          <div class="file-info">
            <div class="file-name" :title="file.fileName">{{ file.fileName }}</div>
            <div class="file-meta">
              <el-tag size="small">{{ file.fileType }}</el-tag>
              <span>{{ formatFileSize(file.fileSize) }}</span>
            </div>
          </div>
          <div class="file-actions" @click.stop>
            <el-button
              size="small"
              :icon="Download"
              @click="downloadFile(file)"
            />
            <el-button
              size="small"
              type="danger"
              :icon="Delete"
              @click="deleteFile(file)"
            />
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && files.length === 0" description="暂无文件" />
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialog" title="上传多模态文件" width="500px">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        drag
        accept="image/*,audio/*,video/*,.pdf,.doc,.docx"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持图片、音频、视频、文档等多种格式
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button
          type="primary"
          @click="uploadFile"
          :loading="uploading"
          :disabled="!selectedFile"
        >
          上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 文件详情对话框 -->
    <el-dialog v-model="detailDialog" title="文件详情" width="700px">
      <div v-if="currentFile">
        <!-- 文件预览 -->
        <div class="file-detail-preview">
          <img
            v-if="currentFile.fileType === 'IMAGE'"
            :src="`/api/v1/multimodal/files/${currentFile.id}/download`"
            style="max-width: 100%; border-radius: 8px;"
          />
          <audio
            v-else-if="currentFile.fileType === 'AUDIO'"
            controls
            style="width: 100%"
          >
            <source :src="`/api/v1/multimodal/files/${currentFile.id}/download`">
          </audio>
          <video
            v-else-if="currentFile.fileType === 'VIDEO'"
            controls
            style="max-width: 100%; border-radius: 8px;"
          >
            <source :src="`/api/v1/multimodal/files/${currentFile.id}/download`">
          </video>
        </div>

        <!-- 文件信息 -->
        <el-descriptions border :column="2" style="margin-top: 20px">
          <el-descriptions-item label="文件名">
            {{ currentFile.fileName }}
          </el-descriptions-item>
          <el-descriptions-item label="文件类型">
            {{ currentFile.fileType }}
          </el-descriptions-item>
          <el-descriptions-item label="MIME类型">
            {{ currentFile.mimeType }}
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ formatFileSize(currentFile.fileSize) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentFile.width" label="尺寸">
            {{ currentFile.width }} × {{ currentFile.height }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentFile.duration" label="时长">
            {{ currentFile.duration }} 秒
          </el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">
            {{ formatTime(currentFile.uploadedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- OCR 或转录结果 -->
        <div v-if="currentFile.ocrText || currentFile.transcription" style="margin-top: 20px">
          <h4>{{ currentFile.ocrText ? 'OCR 识别结果' : '音频转录' }}</h4>
          <pre class="result-text">{{ currentFile.ocrText || currentFile.transcription }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  Upload, Picture, Headset, VideoCamera, Document,
  Download, Delete, UploadFilled
} from '@element-plus/icons-vue'

const loading = ref(false)
const uploading = ref(false)
const files = ref([])
const stats = ref({
  totalFiles: 0,
  imageCount: 0,
  audioCount: 0,
  videoCount: 0,
  documentCount: 0
})
const filterType = ref('')
const uploadDialog = ref(false)
const detailDialog = ref(false)
const selectedFile = ref(null)
const currentFile = ref(null)
const uploadRef = ref(null)

const loadFiles = async () => {
  loading.value = true
  try {
    const url = filterType.value
      ? `/multimodal/files/type/${filterType.value}`
      : '/multimodal/files'
    const res = await request.get(url)
    files.value = res.data || []
  } catch (error) {
    ElMessage.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await request.get('/multimodal/stats')
    stats.value = res.data || {}
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const uploadFile = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('uploadedBy', 'admin')

  try {
    await request.post('/multimodal/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('文件上传成功')
    uploadDialog.value = false
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadFiles()
    await loadStats()
  } catch (error) {
    ElMessage.error('文件上传失败')
  } finally {
    uploading.value = false
  }
}

const viewFile = (file) => {
  currentFile.value = file
  detailDialog.value = true
}

const downloadFile = (file) => {
  window.open(`/api/v1/multimodal/files/${file.id}/download`, '_blank')
}

const deleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${file.fileName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/multimodal/files/${file.id}`)
    ElMessage.success('文件删除成功')
    await loadFiles()
    await loadStats()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除文件失败')
    }
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadFiles()
  loadStats()
})
</script>

<style scoped>
.multimodal-upload {
  padding: 20px;
}

.premium-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--neutral-gray-100);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.stats-row {
  margin-bottom: 20px;
}

.filter-group {
  margin-bottom: 20px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.file-card {
  border: 1px solid var(--neutral-gray-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.file-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.file-card:hover .file-actions {
  opacity: 1;
}

.file-preview {
  height: 150px;
  background: var(--neutral-gray-50);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.file-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-icon {
  color: var(--neutral-gray-400);
}

.file-info {
  padding: 12px;
}

.file-name {
  font-weight: 500;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.file-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.3s;
}

.file-detail-preview {
  text-align: center;
}

.result-text {
  background: var(--neutral-gray-50);
  padding: 15px;
  border-radius: 8px;
  max-height: 300px;
  overflow-y: auto;
  line-height: 1.6;
  font-size: 13px;
}
</style>
