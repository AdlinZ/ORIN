<template>
  <div class="rag-lab-container">
    <PageHeader
      title="RAG 实验室"
      description="一体化向量检索调试环境。对比 Embedding 模型效果、调节 Hybrid Search 权重、诊断召回链路，一站搞定。"
      icon="Aim"
    />

    <div class="lab-layout">
      <!-- ===== Left Sidebar ===== -->
      <div class="lab-sidebar">
        <div class="config-section">
          <h3>检索参数</h3>
          <el-form label-position="top">

            <el-form-item label="目标知识库">
              <el-select v-model="selectedKbId" placeholder="选择知识库" style="width:100%">
                <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
            </el-form-item>

            <el-form-item label="Embedding 模型">
              <el-select v-model="config.embeddingModel" placeholder="默认模型" clearable style="width:100%">
                <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
              </el-select>
            </el-form-item>

            <el-divider />

            <div class="slider-group">
              <div class="slider-label">
                <span>Top-K 召回数</span>
                <span class="value-badge">{{ config.topK }}</span>
              </div>
              <el-slider v-model="config.topK" :min="1" :max="30" />
            </div>

            <div class="slider-group">
              <div class="slider-label">
                <span>Similarity Threshold</span>
                <span class="value-badge">{{ config.threshold }}</span>
              </div>
              <el-slider v-model="config.threshold" :min="0" :max="1" :step="0.05" />
            </div>

            <div class="slider-group">
              <div class="slider-label">
                <span>语义权重 Alpha</span>
                <span class="value-badge">{{ config.alpha }}</span>
              </div>
              <el-slider v-model="config.alpha" :min="0" :max="1" :step="0.1" />
              <div class="slider-hint">
                {{ (config.alpha * 100).toFixed(0) }}% 向量语义 &nbsp;+&nbsp; {{ ((1 - config.alpha) * 100).toFixed(0) }}% 关键词
              </div>
            </div>

            <el-form-item label="Rerank 模型" style="margin-top:12px">
              <el-select v-model="config.rerankModel" style="width:100%">
                <el-option label="不使用 Rerank" value="none" />
                <el-option label="BGE-Reranker-v2" value="bge-v2" />
                <el-option label="Cohere-Rerank-v3" value="cohere-v3" />
              </el-select>
            </el-form-item>

          </el-form>
        </div>

        <div class="sidebar-bottom">
          <el-button type="primary" style="width:100%" :icon="Setting" @click="applyToAgent">
            同步配置至 Agent
          </el-button>
        </div>
      </div>

      <!-- ===== Main Content ===== -->
      <div class="lab-main">

        <!-- Search Bar -->
        <div class="search-hero">
          <el-input
            v-model="query"
            placeholder="输入查询语句，开始 Hybrid Search 链路诊断..."
            class="search-input"
            size="large"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
            <template #append>
              <el-button :loading="loading" type="primary" @click="handleSearch">混合检索</el-button>
            </template>
          </el-input>
        </div>

        <!-- Results -->
        <div class="results-container" v-loading="loading">

          <!-- Stats Bar -->
          <div v-if="hasSearched && results.length > 0" class="stats-bar">
            <div class="stat-item">
              <el-icon><DataAnalysis /></el-icon>
              <span>共召回 <strong>{{ results.length }}</strong> 个 Chunk</span>
            </div>
            <div class="stat-item">
              <el-icon><Share /></el-icon>
              <span>向量召回 <strong>{{ vectorResults.length }}</strong></span>
            </div>
            <div class="stat-item">
              <el-icon><Search /></el-icon>
              <span>关键词召回 <strong>{{ keywordResults.length }}</strong></span>
            </div>
            <div class="stat-item hybrid" v-if="hybridResults.length">
              <el-icon><Aim /></el-icon>
              <span>双路命中 <strong>{{ hybridResults.length }}</strong></span>
            </div>
            <div class="stat-time">⏱ {{ executionTime }}ms</div>
          </div>

          <!-- Split View: Vector | Keyword -->
          <div v-if="hasSearched && results.length > 0" class="split-view">

            <!-- Vector Column -->
            <div class="strategy-col">
              <div class="col-header vector-header">
                <el-icon><Share /></el-icon>
                语义匹配 (Vector Recall)
                <el-tag size="small" type="success" effect="plain" style="margin-left:auto">{{ vectorResults.length }}</el-tag>
              </div>
              <div v-if="vectorResults.length === 0" class="col-empty">无向量召回结果</div>
              <div
                v-for="(item, idx) in vectorResults"
                :key="'v-'+idx"
                class="result-card"
                @click="openTrace(item)"
              >
                <div class="score-bar-wrap">
                  <div class="bar-track">
                    <div class="bar-fill vector-fill" :style="{ height: (item.score * 100) + '%' }"></div>
                  </div>
                  <div class="score-num">{{ item.score.toFixed(3) }}</div>
                </div>
                <div class="card-body">
                  <div class="content-text">{{ item.content }}</div>
                  <div class="card-footer">
                    <el-tag size="small" type="success" effect="plain">Vector</el-tag>
                    <span class="chunk-ref">{{ item.sourceDoc }} · {{ item.chunkIndex }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Keyword Column -->
            <div class="strategy-col">
              <div class="col-header keyword-header">
                <el-icon><Search /></el-icon>
                关键词匹配 (Keyword Recall)
                <el-tag size="small" type="warning" effect="plain" style="margin-left:auto">{{ keywordResults.length }}</el-tag>
              </div>
              <div v-if="keywordResults.length === 0" class="col-empty">无关键词召回结果</div>
              <div
                v-for="(item, idx) in keywordResults"
                :key="'k-'+idx"
                class="result-card"
                @click="openTrace(item)"
              >
                <div class="score-bar-wrap">
                  <div class="bar-track">
                    <div class="bar-fill keyword-fill" :style="{ height: (item.score * 100) + '%' }"></div>
                  </div>
                  <div class="score-num">{{ item.score.toFixed(3) }}</div>
                </div>
                <div class="card-body">
                  <div class="content-text">{{ item.content }}</div>
                  <div class="card-footer">
                    <el-tag size="small" type="warning" effect="plain">Keyword</el-tag>
                    <span class="chunk-ref">{{ item.sourceDoc }} · {{ item.chunkIndex }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Hybrid Column (双路命中) -->
            <div class="strategy-col" v-if="hybridResults.length > 0">
              <div class="col-header hybrid-header">
                <el-icon><Aim /></el-icon>
                双路命中 (Hybrid Match)
                <el-tag size="small" effect="plain" style="margin-left:auto">{{ hybridResults.length }}</el-tag>
              </div>
              <div
                v-for="(item, idx) in hybridResults"
                :key="'h-'+idx"
                class="result-card"
                @click="openTrace(item)"
              >
                <div class="score-bar-wrap">
                  <div class="bar-track">
                    <div class="bar-fill hybrid-fill" :style="{ height: (item.score * 100) + '%' }"></div>
                  </div>
                  <div class="score-num">{{ item.score.toFixed(3) }}</div>
                </div>
                <div class="card-body">
                  <div class="content-text">{{ item.content }}</div>
                  <div class="card-footer">
                    <el-tag size="small" effect="plain">Hybrid</el-tag>
                    <span class="chunk-ref">{{ item.sourceDoc }} · {{ item.chunkIndex }}</span>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <!-- Empty States -->
          <el-empty v-else-if="hasSearched" description="未召回任何内容，尝试放宽 Threshold 或修改 Query" />
          <div v-else class="welcome-state">
            <el-icon :size="64" color="#C8C9CC"><Aim /></el-icon>
            <h3>RAG 链路诊断中心</h3>
            <p>选择知识库，输入 Query，实时对比 Embedding 向量召回与关键词匹配结果</p>
          </div>

        </div>
      </div>
    </div>

    <!-- Trace Drawer -->
    <el-drawer v-model="drawerVisible" title="Chunk 溯源分析" direction="rtl" size="38%">
      <div v-if="selectedResult" class="trace-panel">
        <div class="trace-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedResult.sourceDoc }}</span>
          <el-tag :type="matchTagType(selectedResult.matchType)" size="small" style="margin-left:auto">
            {{ selectedResult.matchType }}
          </el-tag>
        </div>
        <div class="trace-score-row">
          <div class="trace-score-item">
            <div class="trace-score-label">综合得分</div>
            <div class="trace-score-value" :style="{ color: getScoreColor(selectedResult.score) }">
              {{ selectedResult.score.toFixed(4) }}
            </div>
          </div>
          <div class="trace-score-item">
            <div class="trace-score-label">Chunk ID</div>
            <div class="trace-score-value" style="font-size:12px; word-break:break-all">{{ selectedResult.chunkIndex }}</div>
          </div>
        </div>
        <div class="trace-content-label">Chunk 内容</div>
        <div class="trace-content-box">{{ selectedResult.content }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Search, Setting, Aim, DataAnalysis, Share, Document } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

// ── State ──────────────────────────────────────────
const selectedKbId = ref('');
const query = ref('');
const loading = ref(false);
const hasSearched = ref(false);
const executionTime = ref(0);
const results = ref([]);
const knowledgeBases = ref([]);
const embeddingModels = ref([]);
const drawerVisible = ref(false);
const selectedResult = ref(null);

const config = reactive({
  topK: 8,
  threshold: 0.5,
  alpha: 0.7,
  rerankModel: 'none',
  embeddingModel: '',
});

// ── Computed ───────────────────────────────────────
const vectorResults = computed(() => results.value.filter(r => r.matchType === 'VECTOR'));
const keywordResults = computed(() => results.value.filter(r => r.matchType === 'KEYWORD'));
const hybridResults = computed(() => results.value.filter(r => r.matchType === 'HYBRID'));

// ── Lifecycle ──────────────────────────────────────
onMounted(async () => {
  try {
    const [modelsRes, kbRes] = await Promise.all([
      request.get('/models'),
      request.get('/knowledge/list'),
    ]);
    embeddingModels.value = (modelsRes || []).filter(m => m.type?.toUpperCase() === 'EMBEDDING');
    const kbs = (kbRes || []).filter(kb => kb.status === 'ENABLED');
    knowledgeBases.value = [
      { id: 'all', name: '全局检索 (All KBs)' },
      ...kbs,
    ];
    if (knowledgeBases.value.length > 0) selectedKbId.value = knowledgeBases.value[0].id;
    if (embeddingModels.value.length > 0) config.embeddingModel = embeddingModels.value[0].modelId;
  } catch (e) {
    console.error('RAG Lab init failed', e);
  }
});

// ── Methods ────────────────────────────────────────
const handleSearch = async () => {
  if (!query.value.trim() || !selectedKbId.value) {
    ElMessage.warning('请输入查询语句并选择知识库');
    return;
  }

  loading.value = true;
  hasSearched.value = true;
  const t0 = Date.now();

  try {
    const response = await request.post('/knowledge/retrieve/test', {
      query: query.value,
      kbId: selectedKbId.value,
      topK: config.topK,
      embeddingModel: config.embeddingModel || undefined,
    });

    executionTime.value = Date.now() - t0;
    const items = Array.isArray(response) ? response : (response?.data || response?.results || []);

    results.value = items
      .map(r => ({
        score: r.score ?? 0,
        content: r.content ?? '',
        sourceDoc: r.metadata?.source || r.metadata?.doc_id || '未知文档',
        chunkIndex: r.metadata?.chunk_id || '-',
        matchType: r.matchType || (r.score > 0.4 ? 'VECTOR' : 'KEYWORD'),
      }))
      .filter(r => r.score >= config.threshold);
  } catch (e) {
    ElMessage.error('检索请求失败，请检查后端连接');
  } finally {
    loading.value = false;
  }
};

const openTrace = (item) => {
  selectedResult.value = item;
  drawerVisible.value = true;
};

const applyToAgent = () => {
  ElMessage.success('配置已同步至 Agent 生产参数（功能开发中）');
};

const getScoreColor = (score) => {
  if (score >= 0.8) return '#52c41a';
  if (score >= 0.5) return '#faad14';
  return '#bfbfbf';
};

const matchTagType = (type) => {
  if (type === 'VECTOR') return 'success';
  if (type === 'KEYWORD') return 'warning';
  return '';
};
</script>

<style scoped>
.rag-lab-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Layout ── */
.lab-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
  background: var(--app-bg);
}

/* ── Sidebar ── */
.lab-sidebar {
  width: 300px;
  flex-shrink: 0;
  background: var(--glass-bg);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(20px);
}

.config-section {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.config-section h3 {
  color: var(--text-primary);
  margin-top: 0;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
}

.sidebar-bottom {
  padding: 16px;
  border-top: 1px solid var(--border-subtle);
}

.slider-group {
  margin-bottom: 18px;
}

.slider-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.value-badge {
  background: var(--orin-primary-soft);
  color: var(--orin-primary);
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}

.slider-hint {
  font-size: 11px;
  color: var(--text-secondary);
  text-align: right;
  margin-top: 4px;
}

/* ── Main ── */
.lab-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.search-hero {
  padding: 20px 32px;
  background: var(--glass-bg);
  border-bottom: 1px solid var(--border-subtle);
}

.results-container {
  flex: 1;
  padding: 20px 28px;
  overflow-y: auto;
}

/* ── Stats Bar ── */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 16px;
  background: var(--glass-bg);
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid var(--border-subtle);
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-item.hybrid { color: var(--orin-primary); }
.stat-item strong { color: var(--text-primary); }

.stat-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-secondary);
}

/* ── Split View ── */
.split-view {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.strategy-col {
  flex: 1;
  min-width: 0;
}

.col-header {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
}

.vector-header  { background: rgba(82, 196, 26, 0.08); border: 1px solid rgba(82, 196, 26, 0.2); }
.keyword-header { background: rgba(250, 173, 20, 0.08); border: 1px solid rgba(250, 173, 20, 0.2); }
.hybrid-header  { background: var(--orin-primary-soft); border: 1px solid var(--orin-primary-light, #a0cfff); }

.col-empty {
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  padding: 30px 0;
}

/* ── Result Card ── */
.result-card {
  background: var(--glass-bg);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  gap: 10px;
  cursor: pointer;
  margin-bottom: 10px;
  transition: all 0.2s;
}

.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  border-color: var(--orin-primary);
}

.score-bar-wrap {
  width: 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.bar-track {
  width: 6px;
  height: 64px;
  background: var(--orin-primary-soft);
  border-radius: 3px;
  position: relative;
  overflow: hidden;
}

.bar-fill {
  width: 100%;
  position: absolute;
  bottom: 0;
  border-radius: 3px;
  transition: height 0.4s ease;
}

.vector-fill  { background: #52c41a; }
.keyword-fill { background: #faad14; }
.hybrid-fill  { background: var(--orin-primary); }

.score-num {
  font-size: 10px;
  font-weight: 700;
  color: var(--text-secondary);
}

.card-body { flex: 1; min-width: 0; }

.content-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chunk-ref {
  font-size: 11px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}

/* ── Welcome ── */
.welcome-state {
  height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  gap: 12px;
}

.welcome-state h3 {
  color: var(--text-primary);
  margin: 0;
  font-size: 18px;
}

.welcome-state p {
  max-width: 380px;
  text-align: center;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}

/* ── Trace Drawer ── */
.trace-panel { padding: 4px 0; color: var(--text-primary); }

.trace-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--orin-primary);
  margin-bottom: 20px;
}

.trace-score-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.trace-score-item {
  flex: 1;
  background: var(--glass-bg);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 12px;
  text-align: center;
}

.trace-score-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.trace-score-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.trace-content-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.trace-content-box {
  background: var(--orin-primary-soft);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
}
</style>
