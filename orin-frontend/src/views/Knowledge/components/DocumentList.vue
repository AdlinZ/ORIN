<template>
  <el-drawer
    v-model="visible"
    title="文档管理"
    size="650px"
    @close="handleClose"
  >
    <div class="document-manager">
      <!-- Upload Area -->
      <div class="upload-area">
        <el-upload
          class="upload-demo"
          drag
          action="#"
          :auto-upload="true"
          :http-request="handleUpload"
          :show-file-list="false"
          :disabled="uploading"
          :accept="acceptTypes"
        >
          <el-icon class="el-icon--upload">
            <upload-filled />
          </el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF、TXT、MD、图片、音频、视频等格式，单个文件不超过 10MB
            </div>
          </template>
        </el-upload>

        <!-- Upload Progress -->
        <div v-if="uploading" class="upload-progress">
          <el-progress :percentage="uploadProgress" :status="uploadStatus" />
          <div class="upload-info">
            正在上传并解析: {{ uploadingFileName }}
          </div>
        </div>
      </div>

      <!-- Document List -->
      <div v-loading="loading" class="document-list">
        <div class="list-header">
          <span>文档列表 ({{ documents.length }})</span>
          <el-button link type="primary" @click="loadDocuments">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <el-empty v-if="documents.length === 0" description="暂无文档" />

        <div v-else class="list-content">
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <!-- File Type Icon -->
            <div class="doc-icon">
              <el-icon v-if="doc.mediaType === 'pdf'" :size="24" color="#F56C6C">
                <Document />
              </el-icon>
              <el-icon v-else-if="doc.mediaType === 'image'" :size="24" color="#67C23A">
                <Picture />
              </el-icon>
              <el-icon v-else-if="doc.mediaType === 'audio'" :size="24" color="#E6A23C">
                <Headset />
              </el-icon>
              <el-icon v-else-if="doc.mediaType === 'video'" :size="24" color="#909399">
                <VideoCamera />
              </el-icon>
              <el-icon v-else-if="['txt', 'md', 'text'].includes(doc.mediaType)" :size="24" color="var(--el-color-primary)">
                <Document />
              </el-icon>
              <el-icon v-else :size="24">
                <Document />
              </el-icon>
            </div>

            <!-- Document Info -->
            <div class="doc-info">
              <div class="doc-name">
                {{ doc.fileName || doc.originalFilename }}
              </div>
              <div class="doc-meta">
                <el-tag size="small" type="info">
                  {{ getMediaTypeLabel(doc.mediaType) }}
                </el-tag>
                <el-divider direction="vertical" />
                <span>{{ formatSize(doc.fileSize) }}</span>
                <el-divider direction="vertical" />
                <span>{{ formatTime(doc.uploadTime) }}</span>
                <el-divider v-if="doc.lastModified" direction="vertical" />
                <span v-if="doc.lastModified">更新: {{ formatTime(doc.lastModified) }}</span>
              </div>
            </div>

            <!-- Parse Status -->
            <div class="doc-status">
              <el-tooltip :content="getParseStatusTooltip(doc)" placement="top">
                <el-tag :type="getParseStatusType(doc.parseStatus)" size="small">
                  {{ formatParseStatus(doc.parseStatus) }}
                </el-tag>
              </el-tooltip>
            </div>

            <!-- Vector Status -->
            <div class="doc-status">
              <el-tag :type="getVectorStatusType(doc.vectorStatus)" size="small">
                {{ formatVectorStatus(doc.vectorStatus) }}
              </el-tag>
            </div>

            <!-- Actions -->
            <div class="doc-actions">
              <el-button
                v-if="doc.parseStatus === 'SUCCESS'"
                link
                type="primary"
                size="small"
                @click="viewParsedContent(doc)"
              >
                查看
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(doc)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- Parsed Content Dialog -->
      <el-dialog v-model="showParsedContent" title="解析内容" width="600px">
        <div v-if="currentParsedContent" class="parsed-content">
          <div class="parsed-meta">
            <el-tag type="info">
              {{ currentParsedContent.fileName }}
            </el-tag>
            <el-tag type="success">
              {{ currentParsedContent.charCount }} 字符
            </el-tag>
          </div>
          <el-input
            v-model="currentParsedContent.text"
            type="textarea"
            :rows="15"
            readonly
          />
        </div>
        <template #footer>
          <el-button @click="showParsedContent = false">
            关闭
          </el-button>
        </template>
      </el-dialog>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { UploadFilled, Document, Refresh, Picture, Headset, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  modelValue: Boolean,
  kbId: String
})

const emit = defineEmits(['update:modelValue', 'change'])

const visible = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const uploadingFileName = ref('')
const loading = ref(false)
const documents = ref([])
const showParsedContent = ref(false)
const currentParsedContent = ref(null)

// 支持的文件类型
const acceptTypes = '.pdf,.txt,.md,.doc,.docx,.jpg,.jpeg,.png,.gif,.bmp,.webp,.mp3,.wav,.m4a,.ogg,.mp4,.avi,.mov,.mkv'

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.kbId) {
    loadDocuments()
  }
})

const handleClose = () => {
  emit('update:modelValue', false)
}

const loadDocuments = async () => {
  if (!props.kbId) return
  loading.value = true
  try {
    const res = await request.get(`/knowledge/${props.kbId}/documents`)
    documents.value = res || []
  } catch (error) {
    console.error(error)
    ElMessage.error('加载文档失败')
  } finally {
    loading.value = false
  }
}

const handleUpload = async (options) => {
  const { file } = options

  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 10MB')
    return
  }

  uploadingFileName.value = file.name
  uploadProgress.value = 0
  uploadStatus.value = ''

  const formData = new FormData()
  formData.append('file', file)

  uploading.value = true
  try {
    // 模拟上传进度
    const progressInterval = setInterval(() => {
      if (uploadProgress.value < 90) {
        uploadProgress.value += 10
      }
    }, 200)

    await request.post(`/knowledge/${props.kbId}/documents/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    clearInterval(progressInterval)
    uploadProgress.value = 100
    uploadStatus.value = 'success'

    ElMessage.success('上传成功，文件正在解析中...')
    loadDocuments()
    emit('change')

    // 轮询检查解析状态
    startPollingParseStatus()

  } catch (error) {
    console.error(error)
    uploadStatus.value = 'exception'
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
    setTimeout(() => {
      uploadProgress.value = 0
      uploadStatus.value = ''
      uploadingFileName.value = ''
    }, 2000)
  }
}

// 轮询解析状态
const startPollingParseStatus = () => {
  let pollCount = 0
  const maxPolls = 30 // 最多轮询 30 次 (约 1 分钟)

  const poll = setInterval(() => {
    pollCount++
    loadDocuments()

    // 检查是否有正在解析的文档
    const hasProcessing = documents.value.some(doc =>
      doc.parseStatus === 'PARSING' || doc.vectorStatus === 'INDEXING'
    )

    if (!hasProcessing || pollCount >= maxPolls) {
      clearInterval(poll)
    }
  }, 2000)
}

const handleDelete = (doc) => {
  ElMessageBox.confirm(
    `确定要删除文档 "${doc.fileName || doc.originalFilename}" 吗？`,
    '删除确认',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(async () => {
      try {
        await request.delete(`/knowledge/documents/${doc.id}`)
        ElMessage.success('删除成功')
        loadDocuments()
        emit('change')
      } catch (error) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

// 查看解析内容
const viewParsedContent = async (doc) => {
  try {
    // 尝试获取解析后的文本内容
    const res = await request.get(`/knowledge/documents/${doc.id}/content`)
    currentParsedContent.value = {
      fileName: doc.fileName,
      text: res?.content || res?.text || '无法获取内容',
      charCount: res?.charCount || res?.text?.length || 0
    }
    showParsedContent.value = true
  } catch (error) {
    ElMessage.error('无法获取解析内容')
  }
}

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatTime = (time) => {
    if (!time) return '-'
    return new Date(time).toLocaleDateString()
}

// Media Type
const getMediaTypeLabel = (type) => {
  const labels = {
    'pdf': 'PDF',
    'image': '图片',
    'audio': '音频',
    'video': '视频',
    'text': '文本',
    'txt': '文本',
    'md': 'Markdown'
  }
  return labels[type?.toLowerCase()] || type || '未知'
}

// Parse Status
const ParseStatusMap = {
  'PENDING': '待解析',
  'PARSING': '解析中',
  'SUCCESS': '已解析',
  'FAILED': '解析失败'
}

const formatParseStatus = (status) => {
  return ParseStatusMap[status] || status || '未知'
}

const getParseStatusType = (status) => {
  switch(status) {
    case 'SUCCESS': return 'success'
    case 'PARSING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

const getParseStatusTooltip = (doc) => {
  if (doc.parseStatus === 'FAILED' && doc.parseError) {
    return `错误: ${doc.parseError}`
  }
  return ''
}

// Vector Status
const VectorStatusMap = {
  'PENDING': '待向量化',
  'INDEXING': '向量化中',
  'SUCCESS': '已完成',
  'FAILED': '失败'
}

const formatVectorStatus = (status) => {
  return VectorStatusMap[status] || status || '未知'
}

const getVectorStatusType = (status) => {
  switch(status) {
    case 'SUCCESS': return 'success'
    case 'INDEXING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}
</script>

<style scoped>
.document-manager {
    padding: 0 20px;
}
.upload-area {
    margin-bottom: 20px;
}
.upload-progress {
    margin-top: 15px;
}
.upload-info {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
    text-align: center;
}
.list-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    font-size: 14px;
    font-weight: 500;
    color: #606266;
}
.list-content {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    max-height: 450px;
    overflow-y: auto;
}
.doc-item {
    display: flex;
    align-items: center;
    padding: 12px;
    border-bottom: 1px solid #ebeef5;
    transition: background-color 0.2s;
}
.doc-item:last-child {
    border-bottom: none;
}
.doc-item:hover {
    background-color: #f5f7fa;
}
.doc-icon {
    margin-right: 12px;
    display: flex;
    align-items: center;
}
.doc-info {
    flex: 1;
    overflow: hidden;
}
.doc-name {
    font-size: 14px;
    color: #303133;
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}
.doc-meta {
    font-size: 12px;
    color: #909399;
    display: flex;
    align-items: center;
}
.doc-status {
    margin: 0 8px;
}
.doc-actions {
    display: flex;
    gap: 8px;
}
.parsed-content {
    max-height: 500px;
    overflow-y: auto;
}
.parsed-meta {
    display: flex;
    gap: 10px;
    margin-bottom: 15px;
}
</style>
