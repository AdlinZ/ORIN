<template>
  <div class="embedding-lab-container">
    <PageHeader 
      title="向量匹配实验室 Embedding Lab" 
      description="专注于对比不同 Embedding 模型的语义召回能力。输入一段话，查看其在指定知识库中的向量匹配精度。"
      icon="Aim"
    />

    <div class="lab-layout">
      <!-- Left Sidebar -->
      <div class="lab-sidebar">
        <div class="config-section">
          <h3>测试参数</h3>
          <el-form label-position="top">
             <el-form-item label="测试知识库">
               <el-select v-model="selectedKbId" placeholder="选择目标知识库">
                 <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
               </el-select>
             </el-form-item>

             <el-form-item label="Embedding Model">
                <el-select v-model="selectedEmbeddingModel" placeholder="选择 Embedding 模型">
                  <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
                </el-select>
             </el-form-item>

             <el-form-item label="召回数量 (Top-K)">
                <el-slider v-model="topK" :min="1" :max="50" show-input />
             </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- Main Content -->
      <div class="lab-main">
         <div class="search-hero">
            <el-input 
              v-model="query" 
              placeholder="输入查询语句，查看匹配结果..." 
              class="search-input"
              size="large"
              clearable
              @keyup.enter="handleSearch"
            >
               <template #prefix><el-icon><Search /></el-icon></template>
               <template #append>
                 <el-button :loading="loading" @click="handleSearch">执行匹配</el-button>
               </template>
            </el-input>
         </div>

         <div class="results-container" v-loading="loading">
            <div v-if="results.length > 0" class="results-grid">
               <div v-for="(item, index) in results" :key="index" class="chunk-card">
                  <div class="chunk-rank">#{{ index + 1 }}</div>
                  <div class="chunk-header">
                     <span class="score-badge" :style="{ background: getScoreColor(item.score) }">
                        Score: {{ item.score.toFixed(4) }}
                     </span>
                     <span class="source-info">{{ item.sourceDoc }}</span>
                  </div>
                  <div class="chunk-content">
                     {{ item.content }}
                  </div>
                  <div class="chunk-footer">
                     <span>Chunk ID: {{ item.chunkIndex }}</span>
                     <el-link type="primary" :underline="false" @click="viewFullDoc(item)">查看全文</el-link>
                  </div>
               </div>
            </div>

            <el-empty v-else description="请输入查询语句并执行匹配" />
         </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Search, Aim } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

const query = ref('');
const selectedKbId = ref('');
const selectedEmbeddingModel = ref('');
const topK = ref(10);
const loading = ref(false);
const knowledgeBases = ref([]);
const embeddingModels = ref([]);
const results = ref([]);

onMounted(async () => {
    try {
        const [modelsRes, kbRes] = await Promise.all([
            request.get('/models'),
            request.get('/knowledge/list')
        ]);
        
        embeddingModels.value = (modelsRes || []).filter(m => m.type === 'EMBEDDING' || m.type?.toUpperCase() === 'EMBEDDING');
        knowledgeBases.value = (kbRes || []).filter(kb => kb.status === 'ENABLED');
        
        if (embeddingModels.value.length > 0) selectedEmbeddingModel.value = embeddingModels.value[0].modelId;
        if (knowledgeBases.value.length > 0) selectedKbId.value = knowledgeBases.value[0].id;
    } catch (e) {
        ElMessage.error('加载失败');
    }
});

const handleSearch = async () => {
    if (!query.value.trim() || !selectedKbId.value) {
        ElMessage.warning('请输入查询语句并选择知识库');
        return;
    }

    loading.value = true;
    try {
        const response = await request.post('/knowledge/retrieve/test', {
            query: query.value,
            kbId: selectedKbId.value,
            topK: topK.value,
            embeddingModel: selectedEmbeddingModel.value
            // We don't send vlmModel or imageUrl here to test pure embedding
        });
        
        const data = response || {};
        results.value = (data.results || []).map(r => ({
            score: r.score,
            content: r.content,
            sourceDoc: r.metadata?.source || 'Unknown',
            chunkIndex: r.metadata?.chunk_id || '0'
        }));
    } catch (e) {
        ElMessage.error('检索失败');
    } finally {
        loading.value = false;
    }
};

const getScoreColor = (score) => {
    if (score > 0.8) return '#52c41a';
    if (score > 0.6) return '#faad14';
    return '#bfbfbf';
};

const viewFullDoc = (item) => {
    ElMessage.info('查看全文功能开发中...');
};
</script>

<style scoped>
.embedding-lab-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
}

.lab-layout {
    flex: 1;
    display: flex;
    overflow: hidden;
    background: #f5f7fa;
}

.lab-sidebar {
    width: 320px;
    background: white;
    border-right: 1px solid #ebeef5;
    padding: 24px;
}

.lab-main {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.search-hero {
    padding: 30px 5%;
    background: white;
    border-bottom: 1px solid #ebeef5;
}

.results-container {
    flex: 1;
    padding: 24px;
    overflow-y: auto;
}

.results-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
    gap: 20px;
}

.chunk-card {
    background: white;
    border-radius: 12px;
    padding: 20px;
    position: relative;
    box-shadow: 0 2px 12px rgba(0,0,0,0.04);
    border: 1px solid #f0f0f0;
}

.chunk-rank {
    position: absolute;
    top: -10px;
    left: -10px;
    width: 30px;
    height: 30px;
    background: #1890ff;
    color: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 12px;
}

.chunk-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.score-badge {
    padding: 2px 10px;
    color: white;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
}

.source-info {
    font-size: 12px;
    color: #909399;
}

.chunk-content {
    font-size: 14px;
    line-height: 1.6;
    color: #333;
    margin-bottom: 15px;
    display: -webkit-box;
    -webkit-line-clamp: 6;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.chunk-footer {
    border-top: 1px solid #f5f5f5;
    padding-top: 12px;
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: #909399;
}
</style>
