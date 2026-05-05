<template>
  <div class="retrieval-test-page">
    <div class="page-header">
      <div class="header-title">
        <el-icon class="mr-2"><Search /></el-icon>
        <span>检索测试</span>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width: 250px" filterable>
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.kbId"
            :label="kb.name"
            :value="kb.kbId"
          />
        </el-select>
      </div>
    </div>

    <div class="page-content">
      <div class="search-section">
        <el-input
          v-model="query"
          placeholder="请输入测试问题，例如：如何接入 Dify？"
          clearable
          size="large"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :loading="loading" size="large" @click="handleSearch">
              <el-icon><Search /></el-icon> 搜索
            </el-button>
          </template>
        </el-input>

        <div class="search-options">
          <span class="option-label">Top K:</span>
          <el-input-number
            v-model="topK"
            :min="1"
            :max="20"
            size="default"
          />
          <span class="option-label ml-4">相似度阈值:</span>
          <el-slider
            v-model="similarityThreshold"
            :min="0"
            :max="1"
            :step="0.05"
            style="width: 200px"
          />
          <span class="ml-2">{{ (similarityThreshold * 100).toFixed(0) }}%</span>
        </div>
      </div>

      <div v-loading="loading" class="results-section">
        <div v-if="results.length > 0" class="result-list">
          <div class="result-header">
            <span class="result-count">找到 <strong>{{ filteredResults.length }}</strong> 个相关分片</span>
            <span class="result-info"> (显示 Top {{ topK }} 条结果)</span>
          </div>

          <div v-for="(item, index) in filteredResults" :key="index" class="result-item">
            <div class="result-top">
              <div class="result-score" :class="getScoreClass(item.score)">
                {{ formatScore(item.score) }}
              </div>
              <div class="result-index">#{{ index + 1 }}</div>
            </div>
            <div class="result-content">
              {{ item.content }}
            </div>
            <div v-if="item.metadata && Object.keys(item.metadata).length > 0" class="result-meta">
              <span v-for="(val, key) in item.metadata" :key="key" class="meta-tag">
                {{ key }}: {{ val }}
              </span>
            </div>
            <div class="result-footer">
              <span class="chunk-id" v-if="item.chunkId">Chunk ID: {{ item.chunkId }}</span>
              <span class="doc-id" v-if="item.docId">Doc ID: {{ item.docId }}</span>
            </div>
          </div>
        </div>

        <el-empty
          v-else-if="searched && !loading"
          description="未找到相关内容，请尝试调整查询词或降低相似度阈值"
          :image-size="120"
        />

        <div v-else-if="!searched" class="placeholder">
          <el-icon :size="64" color="#dcdfe6">
            <Search />
          </el-icon>
          <p class="placeholder-title">输入问题开始测试检索效果</p>
          <p class="placeholder-hint">输入与知识库内容相关的问题，系统将返回最匹配的知识分片</p>
        </div>
      </div>

      <!-- 评估面板 -->
      <div v-if="results.length > 0" class="evaluation-panel">
        <div class="panel-title">检索评估指标</div>
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value">{{ avgScore.toFixed(2) }}</div>
            <div class="metric-label">平均相似度</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ highRelevanceCount }}</div>
            <div class="metric-label">高相关性 (≥80%)</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ mediumRelevanceCount }}</div>
            <div class="metric-label">中相关性 (50-80%)</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ coverage.toFixed(1) }}%</div>
            <div class="metric-label">检索覆盖率</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const query = ref('')
const loading = ref(false)
const searched = ref(false)
const results = ref([])
const topK = ref(5)
const similarityThreshold = ref(0.3)
const selectedKbId = ref('')
const knowledgeBases = ref([])

const filteredResults = computed(() => {
  return results.value.filter(r => r.score >= similarityThreshold.value)
})

const avgScore = computed(() => {
  if (filteredResults.value.length === 0) return 0
  const sum = filteredResults.value.reduce((acc, r) => acc + r.score, 0)
  return sum / filteredResults.value.length
})

const highRelevanceCount = computed(() => {
  return filteredResults.value.filter(r => r.score >= 0.8).length
})

const mediumRelevanceCount = computed(() => {
  return filteredResults.value.filter(r => r.score >= 0.5 && r.score < 0.8).length
})

const coverage = computed(() => {
  if (topK.value === 0) return 0
  return (filteredResults.value.length / topK.value) * 100
})

onMounted(async () => {
  await loadKnowledgeBases()
})

const loadKnowledgeBases = async () => {
  try {
    const res = await request.get('/knowledge/kb/list')
    if (res && Array.isArray(res)) {
      knowledgeBases.value = res
      if (res.length > 0) {
        selectedKbId.value = res[0].kbId
      }
    }
  } catch (error) {
    console.error('Failed to load knowledge bases:', error)
  }
}

const handleSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入查询问题')
    return
  }

  if (!selectedKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }

  loading.value = true
  searched.value = true
  results.value = []

  try {
    const res = await request.post('/knowledge/retrieve/test', {
      kbId: selectedKbId.value,
      query: query.value,
      topK: topK.value
    })
    results.value = unwrapRetrievalResults(res)
    if (results.value.length === 0) {
      ElMessage.info('未找到相关结果')
    }
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

const unwrapRetrievalResults = (response) => {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.results)) return response.results
  if (Array.isArray(response?.data)) return response.data
  return []
}

const getScoreClass = (score) => {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-medium'
  return 'score-low'
}
</script>

<style scoped>
.retrieval-test-page {
  padding: 20px;
  background: #fff;
  min-height: 80vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-title {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: 600;
  color: var(--neutral-gray-800);
}

.mr-2 {
  margin-right: 8px;
}

.page-content {
  max-width: 1000px;
}

.search-section {
  margin-bottom: 24px;
}

.search-options {
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.option-label {
  font-size: 14px;
  color: #606266;
  margin-left: 16px;
}

.option-label:first-child {
  margin-left: 0;
}

.ml-2 {
  margin-left: 8px;
}

.ml-4 {
  margin-left: 16px;
}

.results-section {
  min-height: 300px;
  margin-bottom: 24px;
}

.result-header {
  margin-bottom: 16px;
  font-size: 14px;
  color: #909399;
}

.result-count strong {
  color: var(--el-color-primary);
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-item {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  position: relative;
  border-left: 4px solid var(--el-color-primary);
  transition: all 0.3s ease;
}

.result-item:hover {
  background-color: #ecf5ff;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.result-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.result-score {
  font-size: 14px;
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 4px;
}

.score-high { color: #67c23a; background-color: #e1f3d8; }
.score-medium { color: #e6a23c; background-color: #fdf6ec; }
.score-low { color: #909399; background-color: #f4f4f5; }

.result-index {
  font-size: 12px;
  color: #909399;
}

.result-content {
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  margin-bottom: 12px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.meta-tag {
  background-color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  border: 1px solid #dcdfe6;
}

.result-footer {
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: #c0c4cc;
}

.chunk-id, .doc-id {
  font-family: monospace;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 8px;
}

.placeholder-title {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
}

.placeholder-hint {
  margin-top: 8px;
  font-size: 14px;
  color: #b1b3b8;
}

.evaluation-panel {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 20px;
  color: #fff;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-card {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.metric-label {
  font-size: 12px;
  opacity: 0.9;
}
</style>
