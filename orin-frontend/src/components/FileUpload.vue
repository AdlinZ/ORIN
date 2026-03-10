<template>
  <div class="file-upload-wrapper">
    <el-upload
      v-model:file-list="fileList"
      :action="uploadUrl"
      :headers="headers"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :limit="limit"
      :accept="accept"
      :multiple="multiple"
      drag
    >
      <div class="upload-content">
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <div class="upload-hint">
          支持 {{ acceptText }} 格式，单个文件不超过 {{ maxSize }}MB
        </div>
      </div>
    </el-upload>

    <!-- 上传进度 -->
    <div v-if="uploading" class="upload-progress">
      <el-progress :percentage="progress" :status="progressStatus" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import Cookies from 'js-cookie'

const props = defineProps({
  modelValue: {
    type: [String, Array],
    default: ''
  },
  limit: {
    type: Number,
    default: 5
  },
  accept: {
    type: String,
    default: '.pdf,.doc,.docx,.txt,.md'
  },
  maxSize: {
    type: Number,
    default: 10
  },
  multiple: {
    type: Boolean,
    default: true
  },
  uploadUrl: {
    type: String,
    default: '/api/v1/files/upload'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const fileList = ref([])
const uploading = ref(false)
const progress = ref(0)

const acceptText = computed(() => {
  return props.accept.replace(/\./g, '').toUpperCase()
})

const headers = computed(() => ({
  'Authorization': `Bearer ${Cookies.get('orin_token')}`
}))

const progressStatus = computed(() => {
  if (progress.value === 100) return 'success'
  return ''
})

const handleBeforeUpload = (file) => {
  const fileSize = file.size / 1024 / 1024
  if (fileSize > props.maxSize) {
    ElMessage.error(`文件大小不能超过 ${props.maxSize}MB`)
    return false
  }
  uploading.value = true
  progress.value = 0
  return true
}

const handleSuccess = (response, file) => {
  uploading.value = false
  progress.value = 100
  ElMessage.success(`${file.name} 上传成功`)
  
  const urls = fileList.value.map(f => f.response?.url || f.url).filter(Boolean)
  emit('update:modelValue', props.multiple ? urls : urls[0])
  emit('change', urls)
}

const handleError = (error) => {
  uploading.value = false
  ElMessage.error(`上传失败: ${error.message}`)
}

const handleRemove = (file) => {
  const urls = fileList.value.map(f => f.response?.url || f.url).filter(Boolean)
  emit('update:modelValue', props.multiple ? urls : urls[0])
  emit('change', urls)
}
</script>

<style scoped>
.file-upload-wrapper {
  width: 100%;
}

.upload-content {
  padding: 20px;
  text-align: center;
}

.upload-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 10px;
}

.upload-text {
  color: #606266;
  margin-bottom: 10px;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}

.upload-hint {
  font-size: 12px;
  color: #909399;
}

.upload-progress {
  margin-top: 15px;
}
</style>
