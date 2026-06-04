<template>
  <div class="rag-evaluation">
    <!-- Test Query Section -->
    <div class="test-section">
      <div class="section-header">
        <h4>检索测试</h4>
        <el-select v-model="selectedKbId" placeholder="选择知识库" size="small" style="width: 200px">
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
      </div>
      <el-input
        v-model="testQuery"
        type="textarea"
        :rows="3"
        placeholder="输入测试问题，例如：如何配置 Dify 连接？"
      />
      <div class="test-actions">
        <el-button type="primary" :loading="testing" @click="runTest">
          运行测试
        </el-button>
        <span class="hint">测试 RAG 检索效果</span>
      </div>
    </div>

    <!-- Results Section -->
    <div v-if="testResults.length > 0" class="results-section">
      <div class="section-header">
        <h4>检索结果</h4>
        <span class="result-summary">Top {{ testResults.length }} 条结果</span>
      </div>
      <div class="result-list">
        <div v-for="(result, idx) in testResults" :key="idx" class="result-item">
          <div class="result-header">
            <span class="result-rank">#{{ idx + 1 }}</span>
            <span class="result-score" :class="getScoreClass(result.score)">
              {{ formatScore(result.score) }}
            </span>
          </div>
          <div class="result-content">{{ result.content }}</div>
        </div>
      </div>
    </div>

    <!-- Evaluation Benchmarks -->
    <div class="benchmarks-section">
      <div class="section-header">
        <h4>评估基准</h4>
        <el-button size="small" @click="loadBenchmarks">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <OrinDataTable compact>
        <el-table :data="benchmarks" stripe size="small">
          <el-table-column prop="name" label="基准名称" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column prop="score" label="平均得分" width="100">
            <template #default="{ row }">
              <span :class="getBenchmarkScoreClass(row.score)">
                {{ formatBenchmarkScore(row.score) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="testCount" label="测试次数" width="100" />
          <el-table-column prop="lastRun" label="上次运行" width="160">
            <template #default="{ row }">
              {{ formatBenchmarkTime(row.lastRun) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="runBenchmark(row)">
                运行
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </OrinDataTable>
    </div>

    <!-- Retrieval Summary -->
    <div class="metrics-section">
      <div class="section-header">
        <h4>检索摘要</h4>
      </div>
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-title">命中数量</div>
          <div class="metric-value">{{ metrics.hitCount }}</div>
          <div class="metric-desc">当前查询返回的结果数</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">平均相关度</div>
          <div class="metric-value">{{ formatMetric(metrics.averageScore) }}</div>
          <div class="metric-desc">返回结果 score 的平均值</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">最高相关度</div>
          <div class="metric-value">{{ formatMetric(metrics.maxScore) }}</div>
          <div class="metric-desc">当前结果中的最高 score</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">最低相关度</div>
          <div class="metric-value">{{ formatMetric(metrics.minScore) }}</div>
          <div class="metric-desc">当前结果中的最低 score</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getEvaluationBenchmarks, getKnowledgeKbList, testRetrieval } from '@/api/knowledge'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { toKnowledgeListViewModel, toRetrievalResultViewModel } from '@/viewmodels'

const selectedKbId = ref('')
const knowledgeBases = ref([])
const testQuery = ref('')
const testing = ref(false)
const testResults = ref([])
const benchmarks = ref([])
const metrics = reactive({
  hitCount: 0,
  averageScore: 0,
  maxScore: 0,
  minScore: 0
})

onMounted(async () => {
  await loadKnowledgeBases()
  await loadBenchmarks()
})

const loadKnowledgeBases = async () => {
  try {
    const res = await getKnowledgeKbList()
    const rows = toKnowledgeListViewModel(res)
    if (rows.length > 0) {
      knowledgeBases.value = rows
      if (!selectedKbId.value) {
        selectedKbId.value = rows[0].id
      }
    }
  } catch (error) {
    console.error('Failed to load knowledge bases:', error)
  }
}

const loadBenchmarks = async () => {
  try {
    const res = await getEvaluationBenchmarks()
    benchmarks.value = normalizeBenchmarks(res)
  } catch (error) {
    console.error('Failed to load benchmarks:', error)
    benchmarks.value = []
  }
}

const runTest = async () => {
  if (!testQuery.value.trim()) {
    ElMessage.warning('请输入测试问题')
    return
  }
  if (!selectedKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }

  testing.value = true
  try {
    const res = await testRetrieval({
      kbId: selectedKbId.value,
      query: testQuery.value,
      topK: 5
    })
    testResults.value = toRetrievalResultViewModel(res)
    calculateMetrics()
  } catch (error) {
    console.error('Test failed:', error)
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

const runBenchmark = async (benchmark) => {
  ElMessage.warning(`当前仅展示基准「${benchmark.name}」的后端结果；运行基准需要后端提供执行接口。`)
  await loadBenchmarks()
}

const normalizeBenchmarks = (payload) => {
  const list = Array.isArray(payload?.data)
    ? payload.data
    : Array.isArray(payload?.records)
      ? payload.records
      : Array.isArray(payload)
        ? payload
        : []

  return list.map((item, index) => ({
    id: item.id ?? item.benchmarkId ?? `benchmark-${index}`,
    name: item.name || item.benchmarkName || `评估基准-${index + 1}`,
    description: item.description || item.remark || '-',
    score: item.score ?? item.avgScore ?? item.averageScore ?? null,
    testCount: Number(item.testCount ?? item.runCount ?? item.executionCount ?? 0),
    lastRun: item.lastRun || item.lastRunAt || item.updatedAt || item.executedAt || null,
    raw: item
  }))
}

const calculateMetrics = () => {
  if (testResults.value.length === 0) {
    resetMetrics()
    return
  }

  const scores = testResults.value
    .map(result => Number(result.score))
    .filter(score => Number.isFinite(score))
  if (scores.length === 0) {
    resetMetrics()
    return
  }
  const avgScore = scores.reduce((a, b) => a + b, 0) / scores.length

  metrics.hitCount = testResults.value.length
  metrics.averageScore = avgScore
  metrics.maxScore = Math.max(...scores)
  metrics.minScore = Math.min(...scores)
}

const resetMetrics = () => {
  metrics.hitCount = 0
  metrics.averageScore = 0
  metrics.maxScore = 0
  metrics.minScore = 0
}

const formatScore = (score) => {
  return (score * 100).toFixed(1) + '%'
}

const formatMetric = (score) => {
  return Number(score || 0).toFixed(3)
}

const getScoreClass = (score) => {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-medium'
  return 'score-low'
}

const getBenchmarkScoreClass = (score) => {
  const value = Number(score)
  if (!Number.isFinite(value)) return ''
  return getScoreClass(value <= 1 ? value : value / 100)
}

const formatBenchmarkScore = (score) => {
  if (score === null || score === undefined || score === '') return '-'
  const value = Number(score)
  if (!Number.isFinite(value)) return '-'
  return value <= 1 ? `${(value * 100).toFixed(1)}%` : `${value.toFixed(1)}%`
}

const formatBenchmarkTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}
</script>

<style scoped>
.rag-evaluation {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.test-section {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
}

.test-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.hint {
  font-size: 12px;
  color: #909399;
}

.results-section {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.result-summary {
  font-size: 12px;
  color: #909399;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  border-left: 3px solid var(--el-color-primary);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-rank {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.result-score {
  font-size: 12px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 4px;
}

.score-high { color: #67c23a; background-color: #e1f3d8; }
.score-medium { color: #e6a23c; background-color: #fdf6ec; }
.score-low { color: #909399; background-color: #f4f4f5; }

.result-content {
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
}

.benchmarks-section {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.metrics-section {
  background: #667eea;
  border-radius: 8px;
  padding: 20px;
  color: #fff;
}

.metrics-section .section-header h4 {
  color: #fff;
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

.metric-title {
  font-size: 12px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.metric-desc {
  font-size: 11px;
  opacity: 0.8;
}
</style>
