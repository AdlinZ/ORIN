<template>
  <div class="document-tree">
    <!-- Tree Header -->
    <div class="tree-toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索文档..."
        prefix-icon="Search"
        size="small"
        clearable
        style="width: 100%"
      />
    </div>

    <!-- Document List -->
    <div class="tree-content">
      <div
        v-for="doc in filteredDocuments"
        :key="doc.id"
        class="tree-item"
        :class="{ selected: selectedDocId === doc.id }"
        @click="$emit('select', doc)"
        @contextmenu.prevent="showContextMenu($event, doc)"
      >
        <div class="item-icon">
          <el-icon :color="getDocTypeColor(doc.fileType)">
            <component :is="getDocTypeIcon(doc.fileType)" />
          </el-icon>
        </div>
        <div class="item-content">
          <div class="item-name" :title="doc.fileName">{{ doc.fileName }}</div>
          <div class="item-meta">
            <span class="status-badge" :class="getStatusClass(doc.parseStatus)">
              {{ getStatusText(doc.parseStatus) }}
            </span>
          </div>
        </div>
        <div class="item-status">
          <el-icon v-if="isProcessing(doc.parseStatus)" class="spin"><Loading /></el-icon>
        </div>
      </div>

      <el-empty v-if="filteredDocuments.length === 0" description="暂无文档" />
    </div>

    <!-- Context Menu -->
    <div
      v-if="contextMenuVisible"
      class="context-menu"
      :style="{ top: contextMenuY + 'px', left: contextMenuX + 'px' }"
      @click.stop
    >
      <div class="menu-item" @click="handleRetry">
        <el-icon><RefreshRight /></el-icon>
        <span>重试</span>
      </div>
      <div class="menu-item danger" @click="handleDelete">
        <el-icon><Delete /></el-icon>
        <span>删除</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document,
  Picture,
  Film,
  Microphone,
  Files,
  Search,
  RefreshRight,
  Delete,
  Loading
} from '@element-plus/icons-vue'

const props = defineProps({
  kbId: {
    type: String,
    required: true
  },
  documents: {
    type: Array,
    default: () => []
  },
  selectedDocId: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['select', 'retry', 'delete'])

// State
const searchKeyword = ref('')
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuDoc = ref(null)

// Computed
const filteredDocuments = computed(() => {
  if (!searchKeyword.value) return props.documents
  const keyword = searchKeyword.value.toLowerCase()
  return props.documents.filter(doc =>
    doc.fileName.toLowerCase().includes(keyword)
  )
})

// Methods
const getDocTypeIcon = (fileType) => {
  const type = (fileType || '').toLowerCase()
  if (['pdf'].includes(type)) return Document
  if (['doc', 'docx', 'txt', 'md'].includes(type)) return Files
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(type)) return Picture
  if (['mp4', 'mov', 'avi', 'mkv'].includes(type)) return Film
  if (['mp3', 'wav', 'm4a', 'aac', 'ogg'].includes(type)) return Microphone
  return Document
}

const getDocTypeColor = (fileType) => {
  const type = (fileType || '').toLowerCase()
  if (['pdf'].includes(type)) return '#E54D00'
  if (['doc', 'docx'].includes(type)) return '#2B579A'
  if (['txt', 'md'].includes(type)) return '#717171'
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(type)) return '#43A047'
  if (['mp4', 'mov', 'avi', 'mkv'].includes(type)) return '#E53935'
  if (['mp3', 'wav', 'm4a', 'aac', 'ogg'].includes(type)) return '#8E24AA'
  return '#717171'
}

const getStatusClass = (status) => {
  const statusMap = {
    'PENDING': 'status-pending',
    'QUEUED': 'status-queued',
    'PARSING': 'status-processing',
    'CHUNKING': 'status-processing',
    'VECTORIZING': 'status-processing',
    'SUCCESS': 'status-success',
    'COMPLETED': 'status-success',
    'FAILED': 'status-failed',
    'ERROR': 'status-failed'
  }
  return statusMap[status] || 'status-pending'
}

const getStatusText = (status) => {
  const textMap = {
    'PENDING': '等待',
    'QUEUED': '排队中',
    'PARSING': '解析中',
    'CHUNKING': '分块中',
    'VECTORIZING': '向量化',
    'SUCCESS': '已完成',
    'COMPLETED': '已完成',
    'FAILED': '失败',
    'ERROR': '失败'
  }
  return textMap[status] || '未知'
}

const isProcessing = (status) => {
  return ['PENDING', 'QUEUED', 'PARSING', 'CHUNKING', 'VECTORIZING'].includes(status)
}

const showContextMenu = (event, doc) => {
  contextMenuDoc.value = doc
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuVisible.value = true
}

const hideContextMenu = () => {
  contextMenuVisible.value = false
  contextMenuDoc.value = null
}

const handleRetry = () => {
  if (contextMenuDoc.value) {
    emit('retry', contextMenuDoc.value)
  }
  hideContextMenu()
}

const handleDelete = async () => {
  if (!contextMenuDoc.value) return

  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${contextMenuDoc.value.fileName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    emit('delete', contextMenuDoc.value)
  } catch {
    // User cancelled
  }
  hideContextMenu()
}

// Close context menu on click outside
const handleClickOutside = (event) => {
  if (contextMenuVisible.value) {
    hideContextMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.document-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tree-toolbar {
  padding: 8px 4px;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
}

.tree-item {
  display: flex;
  align-items: center;
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  gap: 10px;
}

.tree-item:hover {
  background: #f5f7fa;
}

.tree-item.selected {
  background: #e6f0ff;
  border: 1px solid #409eff;
}

.item-icon {
  flex-shrink: 0;
  font-size: 20px;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  margin-top: 4px;
}

.status-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
}

.status-pending {
  background: #f4f4f5;
  color: #909399;
}

.status-queued {
  background: #fdf6ec;
  color: #E6A23C;
}

.status-processing {
  background: #ecf5ff;
  color: #409EFF;
}

.status-success {
  background: #f0f9eb;
  color: #67C23A;
}

.status-failed {
  background: #fef0f0;
  color: #F56C6C;
}

.item-status {
  flex-shrink: 0;
  color: #409EFF;
}

.spin {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.context-menu {
  position: fixed;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  min-width: 120px;
  padding: 4px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

.menu-item:hover {
  background: #f5f7fa;
  color: #409EFF;
}

.menu-item.danger:hover {
  background: #fef0f0;
  color: #F56C6C;
}
</style>
