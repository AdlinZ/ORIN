<template>
  <div class="document-manager">
    <div class="action-bar">
      <h3>文档管理</h3>
      <el-button type="primary" :icon="Upload" @click="uploadDialog = true">
        上传文档
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="12">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="文档总数" :value="stats.documentCount">
            <template #suffix>个</template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="总字符数" :value="stats.totalCharCount">
            <template #suffix>字符</template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 文档列表 -->
    <el-card class="premium-card">
      <el-table :data="documents" v-loading="loading" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-info">
              <el-icon><Document /></el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="vectorStatus" label="向量化状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.vectorStatus)" size="small">
              {{ getStatusText(row.vectorStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" />
        <el-table-column prop="uploadTime" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :icon="View"
              @click="viewDocument(row)"
            >
              预览
            </el-button>
            <el-button
              v-if="row.vectorStatus === 'PENDING'"
              size="small"
              type="primary"
              :icon="Connection"
              @click="vectorizeDocument(row)"
            >
              向量化
            </el-button>
            <el-button
              size="small"
              type="danger"
              :icon="Delete"
              @click="deleteDocument(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && documents.length === 0" description="暂无文档" />
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialog" title="上传文档" width="500px">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF, TXT, MD, DOCX 等格式，单个文件不超过 50MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button
          type="primary"
          @click="uploadDocument"
          :loading="uploading"
          :disabled="!selectedFile"
        >
          上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 文档预览对话框 -->
    <el-dialog v-model="previewDialog" title="文档预览" width="700px">
      <div v-if="currentDocument">
        <el-descriptions border :column="2">
          <el-descriptions-item label="文件名">
            {{ currentDocument.fileName }}
          </el-descriptions-item>
          <el-descriptions-item label="文件类型">
            {{ currentDocument.fileType }}
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ formatFileSize(currentDocument.fileSize) }}
          </el-descriptions-item>
          <el-descriptions-item label="字符数">
            {{ currentDocument.charCount }}
          </el-descriptions-item>
          <el-descriptions-item label="分块数">
            {{ currentDocument.chunkCount }}
          </el-descriptions-item>
          <el-descriptions-item label="向量化状态">
            <el-tag :type="getStatusType(currentDocument.vectorStatus)" size="small">
              {{ getStatusText(currentDocument.vectorStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="currentDocument.contentPreview" class="content-preview">
          <h4>内容预览</h4>
          <pre>{{ currentDocument.contentPreview }}</pre>
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
  Upload, Document, View, Connection, Delete, UploadFilled
} from '@element-plus/icons-vue'

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  knowledgeBaseId: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const uploading = ref(false)
const documents = ref([])
const stats = ref({
  documentCount: 0,
  totalCharCount: 0
})
const uploadDialog = ref(false)
const previewDialog = ref(false)
const selectedFile = ref(null)
const currentDocument = ref(null)
const uploadRef = ref(null)

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await request.get(`/knowledge/${props.knowledgeBaseId}/documents`)
    documents.value = res.data || []
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await request.get(`/knowledge/${props.knowledgeBaseId}/stats`)
    stats.value = res.data || { documentCount: 0, totalCharCount: 0 }
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const uploadDocument = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('uploadedBy', userStore.username || 'unknown')

  try {
    await request.post(
      `/knowledge/${props.knowledgeBaseId}/documents/upload`,
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' }
      }
    )
    ElMessage.success('文档上传成功')
    uploadDialog.value = false
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadDocuments()
    await loadStats()
  } catch (error) {
    ElMessage.error('文档上传失败')
  } finally {
    uploading.value = false
  }
}

const viewDocument = (doc) => {
  currentDocument.value = doc
  previewDialog.value = true
}

const vectorizeDocument = async (doc) => {
  try {
    await request.post(`/knowledge/documents/${doc.id}/vectorize`)
    ElMessage.success('已触发向量化，请稍后查看状态')
    await loadDocuments()
  } catch (error) {
    ElMessage.error('触发向量化失败')
  }
}

const deleteDocument = async (doc) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${doc.fileName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/knowledge/documents/${doc.id}`)
    ElMessage.success('文档删除成功')
    await loadDocuments()
    await loadStats()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除文档失败')
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

const getStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'INDEXING': 'warning',
    'INDEXED': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'PENDING': '待处理',
    'INDEXING': '处理中',
    'INDEXED': '已完成',
    'FAILED': '失败'
  }
  return map[status] || status
}

onMounted(() => {
  loadDocuments()
  loadStats()
})
</script>

<style scoped>
.document-manager {
  padding: 20px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.action-bar h3 {
  margin: 0;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: var(--radius-lg);
}

.premium-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--neutral-gray-100);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-preview {
  margin-top: 20px;
}

.content-preview h4 {
  margin-bottom: 12px;
}

.content-preview pre {
  background: var(--neutral-gray-50);
  padding: 15px;
  border-radius: 8px;
  overflow-x: auto;
  max-height: 300px;
  line-height: 1.6;
  font-size: 13px;
}
</style>
