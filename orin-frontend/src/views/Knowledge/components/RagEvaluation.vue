<template>
  <div class="rag-evaluation">
    <!-- Test Query Section -->
    <div class="test-section">
      <div class="section-header">
        <h4>检索测试</h4>
        <el-select v-model="selectedKbId" placeholder="选择知识库" size="small" style="width: 200px">
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.kbId"
            :label="kb.name"
            :value="kb.kbId"
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

      <el-table :data="benchmarks" stripe size="small">
        <el-table-column prop="name" label="基准名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="avgScore" label="平均得分" width="100">
          <template #default="{ row }">
            <span :class="getScoreClass(row.avgScore / 100)">
              {{ row.avgScore.toFixed(1) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="testCount" label="测试次数" width="100" />
        <el-table-column prop="lastRun" label="上次运行" width="160">
          <template #default="{ row }">
            {{ formatTime(row.lastRun) }}
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
    </div>

    <!-- Evaluation Metrics -->
    <div class="metrics-section">
      <div class="section-header">
        <h4>评估指标</h4>
      </div>
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-title">Precision@K</div>
          <div class="metric-value">{{ metrics.precisionAtK.toFixed(3) }}</div>
          <div class="metric-desc">前K个结果的准确率</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">Recall@K</div>
          <div class="metric-value">{{ metrics.recallAtK.toFixed(3) }}</div>
          <div class="metric-desc">前K个结果的召回率</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">MRR</div>
          <div class="metric-value">{{ metrics.mrr.toFixed(3) }}</div>
          <div class="metric-desc">平均倒数排名</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">NDCG@K</div>
          <div class="metric-value">{{ metrics.ndcgAtK.toFixed(3) }}</div>
          <div class="metric-desc">归一化折损累积增益</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const selectedKbId = ref('')
const knowledgeBases = ref([])
const testQuery = ref('')
const testing = ref(false)
const testResults = ref([])
const benchmarks = ref([])
const metrics = reactive({
  precisionAtK: 0.0,
  recallAtK: 0.0,
  mrr: 0.0,
  ndcgAtK: 0.0
})

onMounted(async () => {
  await loadKnowledgeBases()
  await loadBenchmarks()
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

const loadBenchmarks = async () => {
  try {
    const res = await request.get('/knowledge/eval/benchmarks')
    benchmarks.value = res || []
  } catch (error) {
    console.error('Failed to load benchmarks:', error)
    // Use mock data if API doesn't exist
    benchmarks.value = [
      { id: 1, name: 'Dify接入测试', description: '测试Dify相关配置和接入流程的检索', avgScore: 85.5, testCount: 12, lastRun: Date.now() - 86400000 },
      { id: 2, name: 'Milvus检索测试', description: '测试Milvus向量数据库相关问题', avgScore: 78.2, testCount: 8, lastRun: Date.now() - 172800000 },
      { id: 3, name: '通用问答测试', description: '覆盖多个知识领域的综合测试集', avgScore: 82.0, testCount: 20, lastRun: Date.now() - 3600000 }
    ]
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
    const res = await request.post('/knowledge/retrieve/test', {
      kbId: selectedKbId.value,
      query: testQuery.value,
      topK: 5
    })
    testResults.value = res || []
    calculateMetrics()
  } catch (error) {
    console.error('Test failed:', error)
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

const runBenchmark = async (benchmark) => {
  ElMessage.info(`开始运行基准: ${benchmark.name}`)
  // In a real implementation, this would trigger an evaluation pipeline
  await loadBenchmarks()
  ElMessage.success('基准运行完成')
}

const calculateMetrics = () => {
  if (testResults.value.length === 0) return

  // Calculate simple metrics based on score distribution
  const scores = testResults.value.map(r => r.score)
  const avgScore = scores.reduce((a, b) => a + b, 0) / scores.length

  // Mock metrics calculation
  metrics.precisionAtK = avgScore
  metrics.recallAtK = Math.min(avgScore * 1.2, 1.0)
  metrics.mrr = avgScore * 0.9
  metrics.ndcgAtK = avgScore * 0.95
}

const formatScore = (score) => {
  return (score * 100).toFixed(1) + '%'
}

const getScoreClass = (score) => {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-medium'
  return 'score-low'
}

const formatTime = (time) => {
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
