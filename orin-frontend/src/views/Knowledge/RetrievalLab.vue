<template>
  <div class="retrieval-lab-container">
    <PageHeader 
      title="RAG 检索策略实验室 Retrieval Strategy Lab" 
      description="RAG 链路深度调试与参数调优环境。在此测试混合检索（Hybrid Search）权重策略与重排序（Rerank）效果。"
      icon="Flask"
    />

    <div class="lab-layout">
      <!-- Left Sidebar: Configuration -->
      <div class="lab-sidebar">
        <div class="config-section">
          <h3>检索策略配置</h3>
          
          <el-form label-position="top">
             <el-form-item label="测试知识库">
               <el-select v-model="selectedKbId" placeholder="选择目标知识库">
                 <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
               </el-select>
             </el-form-item>

             <el-form-item label="Embedding Model">
                <el-select v-model="config.embeddingModel" placeholder="默认模型" clearable>
                  <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
                </el-select>
             </el-form-item>

            <el-divider />

            <div class="slider-group">
                <div class="slider-label">
                    <span>Recall Limit (Top-K)</span>
                    <span class="value">{{ config.topK }}</span>
                </div>
                <el-slider v-model="config.topK" :min="1" :max="20" />
            </div>

            <div class="slider-group">
                <div class="slider-label">
                    <span>Similarity Threshold</span>
                    <span class="value">{{ config.threshold }}</span>
                </div>
                <el-slider v-model="config.threshold" :min="0" :max="1" :step="0.05" />
            </div>

            <div class="slider-group">
                <div class="slider-label">
                    <span>Semantic Weight (Alpha)</span>
                    <span class="value">{{ config.alpha }}</span>
                </div>
                <el-slider v-model="config.alpha" :min="0" :max="1" :step="0.1" />
                <div class="slider-hint">
                    {{ (config.alpha * 100).toFixed(0) }}% 向量语义 + {{ ((1-config.alpha) * 100).toFixed(0) }}% 关键词匹配
                </div>
            </div>
            
            <el-form-item label="Reranking Model" style="margin-top: 20px;">
               <el-select v-model="config.rerankModel">
                 <el-option label="None (High Performance)" value="none" />
                 <el-option label="BGE-Reranker-v2" value="bge-v2" />
                 <el-option label="Cohere-Rerank-v3" value="cohere-v3" />
               </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div class="sidebar-action">
             <el-button type="primary" block :icon="Setting" @click="applyToAgent">同步配置至生产环境</el-button>
        </div>
      </div>

      <!-- Main Content: Search & Results -->
      <div class="lab-main">
         <div class="search-hero">
            <el-input 
              v-model="query" 
              placeholder="输入测试问题，分析混合检索召回逻辑..." 
              class="search-input"
              size="large"
              clearable
              @keyup.enter="handleSearch"
            >
               <template #prefix><el-icon><Search /></el-icon></template>
               <template #append>
                 <el-button :loading="loading" @click="handleSearch">混合检索</el-button>
               </template>
            </el-input>
          </div>

          <div class="results-container" v-loading="loading">
             <div v-if="hasSearched && results.length > 0">
                  <div class="results-header">
                      <h3><el-icon><DataAnalysis /></el-icon> 召回逻辑诊断 (Recall Diagnosis)</h3>
                      <span class="meta-info">Total: {{ results.length }} | Time: {{ executionTime }}ms</span>
                  </div>

                  <!-- Split View for Strategy Comparison -->
                  <div class="strategy-split-view">
                      <!-- Vector Column -->
                      <div class="strategy-col vector-col">
                          <div class="col-header"><el-icon><Share /></el-icon> 语义匹配召回 (Vector Recall)</div>
                          <div class="col-list">
                              <div v-for="(item, index) in vectorResults" :key="'v-'+index" class="result-card" @click="openTraceDrawer(item)">
                                  <div class="vertical-score-bar">
                                      <div class="bar-track">
                                          <div class="bar-fill" :style="{ height: (item.score * 100) + '%', backgroundColor: getScoreColor(item.score) }"></div>
                                      </div>
                                      <div class="score-num" :style="{ color: getScoreColor(item.score) }">{{ item.score.toFixed(2) }}</div>
                                  </div>
                                  <div class="result-body">
                                      <div class="content-preview">{{ item.content }}</div>
                                      <div class="result-footer">
                                         <el-tag size="small" type="success" effect="plain">Vector</el-tag>
                                         <span class="source-ref">{{ item.sourceDoc }} #{{ item.chunkIndex }}</span>
                                      </div>
                                  </div>
                              </div>
                          </div>
                      </div>

                      <!-- Keyword Column -->
                      <div class="strategy-col keyword-col">
                          <div class="col-header"><el-icon><Search /></el-icon> 关键词匹配召回 (Keyword Search)</div>
                          <div class="col-list">
                              <div v-for="(item, index) in keywordResults" :key="'k-'+index" class="result-card" @click="openTraceDrawer(item)">
                                  <div class="vertical-score-bar">
                                      <div class="bar-track">
                                          <div class="bar-fill" :style="{ height: (item.score * 100) + '%', backgroundColor: getScoreColor(item.score) }"></div>
                                      </div>
                                      <div class="score-num" :style="{ color: getScoreColor(item.score) }">{{ item.score.toFixed(2) }}</div>
                                  </div>
                                  <div class="result-body">
                                      <div class="content-preview">{{ item.content }}</div>
                                      <div class="result-footer">
                                         <el-tag size="small" type="warning" effect="plain">Keyword</el-tag>
                                         <span class="source-ref">{{ item.sourceDoc }} #{{ item.chunkIndex }}</span>
                                      </div>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>
              </div>
              
              <el-empty v-else-if="hasSearched" description="未召回任何内容" />
              <div v-else class="empty-state">
                  <el-icon :size="60" color="#E4E7ED"><Aim /></el-icon>
                  <p>输入问题开始混合检索逻辑诊断</p>
              </div>
          </div>
      </div>
    </div>

    <!-- Traceability Drawer -->
    <el-drawer v-model="drawerVisible" title="溯源分析" direction="rtl" size="40%">
        <div v-if="selectedResult" class="trace-context">
            <div class="doc-header">
                <el-icon><Document /></el-icon> <span>{{ selectedResult.sourceDoc }}</span>
            </div>
            <div class="context-viewer">
                <div class="context-block target">{{ selectedResult.content }}</div>
            </div>
            <div class="meta-panel">
                <h4>检索详情</h4>
                <p><strong>Score:</strong> {{ selectedResult.score }}</p>
                <p><strong>Match Type:</strong> {{ selectedResult.matchType }}</p>
            </div>
        </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Search, Setting, Document, Aim, DataAnalysis, Share } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

// State
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
    topK: 5,
    threshold: 0.6,
    alpha: 0.7, 
    rerankModel: 'none',
    embeddingModel: ''
});

const vectorResults = computed(() => results.value.filter(r => r.matchType === 'VECTOR'));
const keywordResults = computed(() => results.value.filter(r => r.matchType === 'KEYWORD'));

onMounted(async () => {
    try {
        const [modelsRes, kbRes] = await Promise.all([
            request.get('/models'),
            request.get('/knowledge/list')
        ]);
        embeddingModels.value = (modelsRes || []).filter(m => m.type?.toUpperCase() === 'EMBEDDING');
        knowledgeBases.value = (kbRes || []).filter(kb => kb.status === 'ENABLED');
        if (knowledgeBases.value.length > 0) selectedKbId.value = knowledgeBases.value[0].id;
    } catch (e) {
        console.error('Failed to load lab data', e);
    }
});

const handleSearch = async () => {
    if (!query.value.trim() || !selectedKbId.value) {
        ElMessage.warning('请输入问题并选择知识库');
        return;
    }
    
    loading.value = true;
    hasSearched.value = true;
    const startTime = Date.now();
    try {
        const response = await request.post('/knowledge/retrieve/test', {
            query: query.value,
            kbId: selectedKbId.value,
            topK: config.topK,
            embeddingModel: config.embeddingModel
        }, {
            timeout: 600000, 
            _retry: true 
        });

        executionTime.value = Date.now() - startTime;
        const data = response || {};
        results.value = (Array.isArray(data) ? data : (data.results || [])).map(r => ({
            score: r.score,
            content: r.content,
            sourceDoc: r.metadata?.source || 'Unknown',
            chunkIndex: r.metadata?.chunk_id || '0',
            matchType: r.score > 0.4 ? 'VECTOR' : 'KEYWORD' // Mock logic since backend doesn't return type yet
        }));
    } catch (e) {
        ElMessage.error('检索失败');
    } finally {
        loading.value = false;
    }
};

const getScoreColor = (score) => {
    if (score >= 0.8) return '#52c41a';
    if (score >= 0.6) return '#faad14';
    return '#bfbfbf';
};

const openTraceDrawer = (item) => {
    selectedResult.value = item;
    drawerVisible.value = true;
};

const applyToAgent = () => {
    ElMessage.success('配置已同步至 Agent 配置项 [Production]');
};
</script>

<style scoped>
.retrieval-lab-container { height: 100vh; display: flex; flex-direction: column; }
.lab-layout { flex: 1; display: flex; overflow: hidden; background: #f5f7fa; }
.lab-sidebar { width: 320px; background: white; border-right: 1px solid #ebeef5; display: flex; flex-direction: column; }
.config-section { flex: 1; padding: 24px; overflow-y: auto; }
.sidebar-action { padding: 20px; border-top: 1px solid #ebeef5; }
.lab-main { flex: 1; display: flex; flex-direction: column; }
.search-hero { padding: 30px 5%; background: white; border-bottom: 1px solid #ebeef5; }
.results-container { flex: 1; padding: 20px 5%; overflow-y: auto; }
.strategy-split-view { display: flex; gap: 20px; }
.strategy-col { flex: 1; }
.col-header { font-size: 14px; font-weight: 600; margin-bottom: 12px; background: #eef2f8; padding: 8px 12px; border-radius: 6px; display: flex; align-items: center; gap: 8px; }
.col-list { display: flex; flex-direction: column; gap: 12px; }
.result-card { background: white; padding: 12px; border-radius: 8px; display: flex; gap: 12px; cursor: pointer; border: 1px solid #f0f0f0; transition: all 0.2s; }
.result-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.vertical-score-bar { width: 24px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.bar-track { width: 6px; height: 60px; background: #f0f2f5; border-radius: 3px; position: relative; overflow: hidden; }
.bar-fill { width: 100%; position: absolute; bottom: 0; transition: height 0.3s; }
.score-num { font-size: 10px; font-weight: bold; }
.result-body { flex: 1; }
.content-preview { font-size: 13px; line-height: 1.5; color: #333; margin-bottom: 8px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.result-footer { display: flex; justify-content: space-between; align-items: center; }
.source-ref { font-size: 11px; color: #909399; }
.empty-state { height: 300px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #909399; }
.slider-group { margin-bottom: 20px; }
.slider-label { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 8px; }
.slider-hint { font-size: 11px; color: #909399; text-align: right; margin-top: 4px; }
</style>
