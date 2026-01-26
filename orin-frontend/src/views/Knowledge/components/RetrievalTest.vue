<template>
  <el-dialog
    v-model="visible"
    title="检索测试"
    width="600px"
    @close="handleClose"
  >
    <div class="retrieval-test">
      <div class="search-box">
        <el-input
          v-model="query"
          placeholder="请输入测试问题，例如：如何接入 Dify？"
          @keyup.enter="handleSearch"
          clearable
        >
          <template #append>
            <el-button :loading="loading" @click="handleSearch">
              <el-icon><Search /></el-icon> 搜索
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="result-area" v-loading="loading">
        <div v-if="results.length > 0" class="result-list">
          <div class="result-header">
            找到 {{ results.length }} 个相关分片 (Top {{ topK }})
          </div>
          
          <div v-for="(item, index) in results" :key="index" class="result-item">
            <div class="result-score" :class="getScoreClass(item.score)">
              {{ formatScore(item.score) }}
            </div>
            <div class="result-content">
              {{ item.content }}
            </div>
            <div class="result-meta" v-if="item.metadata && Object.keys(item.metadata).length > 0">
              <span v-for="(val, key) in item.metadata" :key="key" class="meta-tag">
                {{ key }}: {{ val }}
              </span>
            </div>
          </div>
        </div>
        
        <el-empty 
            v-else-if="searched" 
            description="未找到相关内容" 
            :image-size="100"
        />
        
        <div v-else class="placeholder">
          <el-icon :size="48" color="#dcdfe6"><Search /></el-icon>
          <p>输入问题开始测试检索效果</p>
        </div>
      </div>
    </div>
    
    <template #footer>
        <div class="dialog-footer">
            <span>Top K: </span>
            <el-input-number v-model="topK" :min="1" :max="10" size="small" style="width: 100px; margin-right: 20px" />
            <el-button @click="visible = false">关闭</el-button>
        </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  kbId: String
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const query = ref('')
const loading = ref(false)
const searched = ref(false)
const results = ref([])
const topK = ref(3)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
      // Reset state on open
      query.value = ''
      results.value = []
      searched.value = false
  }
})

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSearch = async () => {
  if (!query.value.trim()) return
  
  loading.value = true
  searched.value = true
  results.value = []
  
  try {
    const res = await request.post('/knowledge/retrieve/test', {
        kbId: props.kbId,
        query: query.value,
        topK: topK.value
    })
    results.value = res || []
  } catch (error) {
    console.error(error)
    ElMessage.error('检索失败')
  } finally {
    loading.value = false
  }
}

const formatScore = (score) => {
    return (score * 100).toFixed(1) + '%'
}

const getScoreClass = (score) => {
    if (score >= 0.8) return 'score-high'
    if (score >= 0.5) return 'score-medium'
    return 'score-low'
}
</script>

<style scoped>
.retrieval-test {
    padding: 0 10px;
}
.search-box {
    margin-bottom: 20px;
}
.result-area {
    min-height: 200px;
}
.result-header {
    margin-bottom: 12px;
    font-size: 13px;
    color: #909399;
}
.result-item {
    background-color: #f5f7fa;
    border-radius: 4px;
    padding: 12px;
    margin-bottom: 12px;
    position: relative;
    border-left: 4px solid transparent;
}
.result-item:hover {
    background-color: #f0f2f5;
}
.result-score {
    position: absolute;
    top: 12px;
    right: 12px;
    font-size: 12px;
    font-weight: bold;
    padding: 2px 6px;
    border-radius: 4px;
}
.score-high { color: #67c23a; background-color: #e1f3d8; }
.score-medium { color: #e6a23c; background-color: #fdf6ec; }
.score-low { color: #909399; background-color: #f4f4f5; }

.result-content {
    font-size: 14px;
    line-height: 1.5;
    color: #303133;
    white-space: pre-wrap;
    margin-right: 50px; /* Space for score */
}
.result-meta {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
}
.meta-tag {
    background-color: #fff;
    padding: 2px 4px;
    border-radius: 2px;
    margin-right: 6px;
    border: 1px solid #dcdfe6;
}
.placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 150px;
    color: #909399;
}
.placeholder p {
    margin-top: 10px;
}
.dialog-footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
}
</style>
