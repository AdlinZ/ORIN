<template>
  <el-drawer
    v-model="visible"
    title="文档管理"
    size="600px"
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
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF, TXT, MD, Markdown 等格式，单个文件不超过 10MB
            </div>
          </template>
        </el-upload>
      </div>

      <!-- Document List -->
      <div class="document-list" v-loading="loading">
        <div class="list-header">
          <span>文档列表 ({{ documents.length }})</span>
          <el-button link type="primary" @click="loadDocuments"><el-icon><Refresh /></el-icon></el-button>
        </div>

        <el-empty v-if="documents.length === 0" description="暂无文档" />

        <div v-else class="list-content">
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <div class="doc-icon">
              <el-icon v-if="doc.fileType === 'pdf'" :size="24" color="#F56C6C"><Document /></el-icon>
              <el-icon v-else-if="['txt', 'md'].includes(doc.fileType)" :size="24" color="var(--orin-primary)"><Document /></el-icon>
              <el-icon v-else :size="24"><Document /></el-icon>
            </div>
            <div class="doc-info">
              <div class="doc-name">{{ doc.fileName }}</div>
              <div class="doc-meta">
                <span>{{ formatSize(doc.fileSize) }}</span>
                <el-divider direction="vertical" />
                <span>{{ formatTime(doc.uploadTime) }}</span>
              </div>
            </div>
            <div class="doc-status">
              <el-tag :type="getStatusType(doc.vectorStatus)" size="small">
                {{ formatStatus(doc.vectorStatus) }}
              </el-tag>
            </div>
            <div class="doc-actions">
              <el-button link type="danger" @click="handleDelete(doc)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { UploadFilled, Document, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  modelValue: Boolean,
  kbId: String
})

const emit = defineEmits(['update:modelValue', 'change'])

const visible = ref(false)
const uploading = ref(false)
const loading = ref(false)
const documents = ref([])

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

  const formData = new FormData()
  formData.append('file', file)

  uploading.value = true
  try {
    await request.post(`/knowledge/${props.kbId}/documents/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    ElMessage.success('上传成功')
    loadDocuments()
    // 触发知识库列表刷新（文档数量变化）
    emit('change')
    
    // 模拟自动触发向量化 (如果是 Mock 模式)
    simulateVectorization(file.name)
    
  } catch (error) {
    console.error(error)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

// 简单的轮询模拟，实际应该根据后端真实状态
const simulateVectorization = (fileName) => {
    // 3秒后刷新一次，模拟状态变更
    setTimeout(() => {
        loadDocuments()
    }, 3000)
}

const handleDelete = (doc) => {
  ElMessageBox.confirm(
    `确定要删除文档 "${doc.fileName}" 吗？`,
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

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatTime = (time) => {
    if (!time) return '-'
    // 简单格式化
    return new Date(time).toLocaleDateString()
}

const FormatStatusMap = {
    'PENDING': '待处理',
    'INDEXING': '向量化中',
    'SUCCESS': '已完成',
    'FAILED': '失败'
}

const formatStatus = (status) => {
    return FormatStatusMap[status] || status
}

const getStatusType = (status) => {
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
    max-height: 400px;
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
}
.doc-status {
    margin: 0 15px;
}
</style>
