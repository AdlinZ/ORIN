<template>
  <div class="page-container">
    <PageHeader
      title="知识库配置"
      description="配置向量数据库、嵌入模型及知识库核心参数"
      icon="Collection"
    >
      <template #actions>
        <el-button type="primary" :loading="saving" :icon="Check" @click="saveConfig">
          保存配置
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- Milvus 向量引擎 Tab -->
      <el-tab-pane label="Milvus 向量引擎" name="milvus">
        <el-row :gutter="24">
          <el-col :lg="14">
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Connection /></el-icon>
                  <span>Milvus 向量搜索引擎</span>
                  <el-tag :type="milvusStatus.online ? 'success' : 'danger'" size="small" style="margin-left: auto">
                    {{ milvusStatus.online ? '已连接' : '未连接' }}
                  </el-tag>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="Milvus Host">
                  <el-input v-model="config.milvusHost" placeholder="例如: 192.168.1.107 或 localhost" />
                  <p class="form-tip">Milvus 服务器 IP 地址，不需要包含 http:// 前缀</p>
                </el-form-item>

                <el-form-item label="Milvus Port">
                  <el-input-number v-model="config.milvusPort" :min="1" :max="65535" style="width: 200px" />
                  <p class="form-tip">默认端口: 19530</p>
                </el-form-item>

                <el-form-item label="Milvus Token (可选)">
                  <el-input v-model="config.milvusToken" type="password" show-password placeholder="root:Milvus" />
                  <p class="form-tip">本地开发环境通常不需要认证</p>
                </el-form-item>

                <el-form-item>
                  <el-button @click="testMilvusConnection" :loading="testingMilvus" type="success" plain>
                    测试连接
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><InfoFilled /></el-icon>
                  <span>Collection 信息</span>
                </div>
              </template>

              <div v-if="collectionInfo.exists" class="collection-info">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="Collection 名称">
                    {{ collectionInfo.collectionName }}
                  </el-descriptions-item>
                  <el-descriptions-item label="向量维度">
                    {{ collectionInfo.dimension }}
                  </el-descriptions-item>
                  <el-descriptions-item label="向量数量">
                    {{ collectionInfo.vectorCount || '未知' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="索引类型">
                    {{ collectionInfo.indexType || '未知' }}
                  </el-descriptions-item>
                </el-descriptions>

                <div style="margin-top: 16px">
                  <el-button @click="loadCollectionInfo" :loading="loadingCollection" size="small" type="primary" plain>
                    刷新统计
                  </el-button>
                  <el-button @click="recreateCollection" :loading="recreating" size="small" type="warning" plain>
                    重建 Collection
                  </el-button>
                </div>
              </div>

              <el-empty v-else description="Collection 尚未创建，请先保存配置并确保 Milvus 连接正常" />
            </el-card>
          </el-col>

          <el-col :lg="10">
            <el-card class="premium-card guide-card">
              <template #header>
                <div class="card-header">
                  <el-icon><QuestionFilled /></el-icon>
                  <span>配置指南</span>
                </div>
              </template>
              <div class="guide-content">
                <div class="guide-step">
                  <span class="step-num">1</span>
                  <div class="step-text">
                    <strong>启动 Milvus 服务</strong>
                    <p>确保 Milvus 服务已启动。可使用 Docker 快速部署：</p>
                    <pre class="install-cmd">cd /path/to/orin
docker-compose -f milvus-docker-compose.yml up -d</pre>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">2</span>
                  <div class="step-text">
                    <strong>配置连接参数</strong>
                    <p>填入 Milvus 服务的 Host 和 Port。单机部署默认端口为 19530。</p>
                  </div>
                </div>
                <div class="guide-step">
                  <span class="step-num">3</span>
                  <div class="step-text">
                    <strong>测试并保存</strong>
                    <p>点击"测试连接"验证服务可用性，然后保存配置。</p>
                  </div>
                </div>
              </div>

              <el-alert
                title="提示：ORIN 使用名为 'orin_knowledge_base' 的 Collection，每个知识库对应一个 Partition。"
                type="info"
                :closable="false"
                show-icon
                style="margin-top: 20px;"
              />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 模型配置 Tab (Embedding + Rerank + AI描述生成) -->
      <el-tab-pane label="模型配置" name="embedding">
        <el-row :gutter="24">
          <el-col :lg="14">
            <!-- Embedding 模型配置 -->
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Cpu /></el-icon>
                  <span>向量嵌入模型</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="Embedding 模型">
                  <el-select
                    v-model="config.embeddingModel"
                    style="width: 100%"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选择或输入模型名称"
                    @focus="loadEmbeddingModels"
                  >
                    <el-option
                      v-for="model in embeddingModels"
                      :key="model.id"
                      :label="model.id"
                      :value="model.id"
                    >
                      <span style="float: left">{{ model.id }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">{{ model.name || '' }}</span>
                    </el-option>
                  </el-select>
                  <p class="form-tip">用于将文档向量化，支持中文推荐 bge-base-zh-v1.5</p>
                </el-form-item>

                <el-form-item>
                  <el-button @click="testEmbeddingConnection" :loading="testingEmbedding" type="success" plain>
                    测试连接
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <!-- Rerank 模型配置 -->
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Sort /></el-icon>
                  <span>Rerank 重排序模型</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="启用 Rerank">
                  <el-switch v-model="config.enableRerank" />
                  <p class="form-tip">开启后先使用 Embedding 粗排，再用 Rerank 精排，提升检索准确率</p>
                </el-form-item>

                <el-form-item label="Rerank 模型" v-if="config.enableRerank">
                  <el-select
                    v-model="config.rerankModel"
                    style="width: 100%"
                    filterable
                    placeholder="选择 Rerank 模型"
                    clearable
                    @focus="loadRerankModels"
                  >
                    <el-option
                      v-for="model in rerankModels"
                      :key="model.id"
                      :label="model.name || model.id"
                      :value="model.id"
                    >
                      <span style="float: left">{{ model.name || model.id }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-form>
            </el-card>

            <!-- AI 描述生成 -->
            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><MagicStick /></el-icon>
                  <span>AI 描述生成</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="描述生成模型">
                  <el-select
                    v-model="config.descGenerationModel"
                    style="width: 100%"
                    filterable
                    placeholder="选择用于生成描述的大语言模型"
                    clearable
                    @focus="loadChatModelsForConfig"
                  >
                    <el-option
                      v-for="model in chatModelsForConfig"
                      :key="model.id"
                      :label="model.name || model.id"
                      :value="model.id"
                    >
                      <span style="float: left">{{ model.name || model.id }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">{{ model.type || '' }}</span>
                    </el-option>
                  </el-select>
                  <p class="form-tip">用于 AI 自动生成知识库描述，建议使用支持中文的对话模型</p>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <el-col :lg="10">
            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><InfoFilled /></el-icon>
                  <span>模型说明</span>
                </div>
              </template>
              <div class="model-list">
                <div class="model-item">
                  <div class="model-name">Embedding 模型</div>
                  <div class="model-desc">将文本转换为向量，用于语义检索</div>
                </div>
                <div class="model-item">
                  <div class="model-name">Rerank 模型</div>
                  <div class="model-desc">对初步检索结果进行重排，提升准确率（需要额外 API 消耗）</div>
                </div>
                <div class="model-item">
                  <div class="model-name">AI 描述生成</div>
                  <div class="model-desc">自动生成知识库描述，提升管理效率</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 知识库全局配置 Tab -->
      <el-tab-pane label="知识库全局配置" name="global">
        <el-row :gutter="24">
          <el-col :lg="16">
            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Setting /></el-icon>
                  <span>知识库基础配置</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="默认 Collection 名称">
                  <el-input v-model="config.defaultCollection" placeholder="orin_knowledge_base" />
                  <p class="form-tip">全局唯一的 Collection 名称，用于存储所有知识库的向量数据</p>
                </el-form-item>

                <el-form-item label="Chunk 最大字符数">
                  <el-input-number v-model="config.chunkSize" :min="100" :max="2000" :step="100" style="width: 200px" />
                  <p class="form-tip">文档分块时每个 Chunk 的最大字符数，默认 500</p>
                </el-form-item>

                <el-form-item label="Chunk 重叠字符数">
                  <el-input-number v-model="config.chunkOverlap" :min="0" :max="500" :step="50" style="width: 200px" />
                  <p class="form-tip">相邻 Chunk 之间的重叠字符数，用于保持上下文连贯性，默认 50</p>
                </el-form-item>

                <el-form-item label="检索返回结果数 (Top K)">
                  <el-input-number v-model="config.defaultTopK" :min="1" :max="20" :step="1" style="width: 200px" />
                  <p class="form-tip">向量相似度搜索返回的最大结果数，默认 5</p>
                </el-form-item>

                <el-form-item label="相似度阈值">
                  <el-slider v-model="config.similarityThreshold" :min="0" :max="1" :step="0.05" show-stops :format-tooltip="(val) => (val * 100).toFixed(0) + '%'" />
                  <p class="form-tip">只有相似度高于此阈值的文档才会被返回，默认 0.7</p>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <el-col :lg="8">
            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><DataAnalysis /></el-icon>
                  <span>知识库统计</span>
                </div>
              </template>
              <div class="stats-list">
                <div class="stat-item">
                  <div class="stat-label">知识库总数</div>
                  <div class="stat-value">{{ knowledgeStats.totalKBs }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">文档总数</div>
                  <div class="stat-value">{{ knowledgeStats.totalDocs }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">向量总数</div>
                  <div class="stat-value">{{ knowledgeStats.totalVectors }}</div>
                </div>
              </div>
              <el-button @click="loadKnowledgeStats" :loading="loadingStats" size="small" style="width: 100%; margin-top: 16px">
                刷新统计
              </el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import request from '@/utils/request';
import PageHeader from '@/components/PageHeader.vue';

const router = useRouter();
import {
  Check,
  Connection,
  InfoFilled,
  QuestionFilled,
  Cpu,
  Setting,
  DataAnalysis,
  MagicStick,
  Sort
} from '@element-plus/icons-vue';

const activeTab = ref('milvus');
const saving = ref(false);
const testingMilvus = ref(false);
const testingEmbedding = ref(false);
const loadingCollection = ref(false);
const recreating = ref(false);
const loadingStats = ref(false);
const loadingModels = ref(false);
const embeddingModels = ref([]);
const chatModels = ref([]);
const rerankModels = ref([]);
const chatModelsForConfig = ref([]);
const testingDescModel = ref(false);

const config = reactive({
  milvusHost: 'localhost',
  milvusPort: 19530,
  milvusToken: '',
  embeddingModel: 'BAAI/bge-m3',
  siliconflowApiKey: '',
  siliconflowBaseUrl: 'https://api.siliconflow.cn/v1',
  defaultCollection: 'orin_knowledge_base',
  chunkSize: 500,
  chunkOverlap: 50,
  defaultTopK: 5,
  similarityThreshold: 0.7,
  descGenerationModel: '',
  enableRerank: false,
  rerankModel: ''
});

const milvusStatus = ref({ online: false });
const collectionInfo = ref({ exists: false });
const knowledgeStats = ref({
  totalKBs: 0,
  totalDocs: 0,
  totalVectors: 0
});

// 加载配置
const loadConfig = async () => {
  try {
    // 加载 Milvus 配置
    const res = await request.get('/monitor/system/properties');
    if (res) {
      config.milvusHost = res['milvus.host'] || 'localhost';
      config.milvusPort = parseInt(res['milvus.port']) || 19530;
      config.milvusToken = res['milvus.token'] || '';
      // 加载 Rerank 配置
      config.enableRerank = res['knowledge.rerank.enabled'] === 'true';
      config.rerankModel = res['knowledge.rerank.model'] || '';
    }

    // 加载模型系统配置 (包含 embedding 模型和 API Key)
    try {
      const modelConfig = await request.get('/model-config');
      if (modelConfig) {
        config.embeddingModel = modelConfig.embeddingModel || 'BAAI/bge-m3';
        config.siliconflowApiKey = modelConfig.siliconFlowApiKey || '';
        config.siliconflowBaseUrl = modelConfig.siliconFlowEndpoint || 'https://api.siliconflow.cn/v1';
        config.descGenerationModel = modelConfig.descGenerationModel || '';
      }
    } catch (e) {
      console.error('加载模型配置失败:', e);
    }
  } catch (e) {
    console.error('加载配置失败:', e);
  }
};

// 保存配置
const saveConfig = async () => {
  saving.value = true;
  try {
    // 保存 Milvus 配置和 Rerank 配置
    const properties = {
      'milvus.host': config.milvusHost,
      'milvus.port': config.milvusPort.toString(),
      'milvus.token': config.milvusToken,
      'knowledge.rerank.enabled': config.enableRerank.toString(),
      'knowledge.rerank.model': config.rerankModel || ''
    };
    await request.post('/monitor/system/properties', properties);
    ElMessage.success('Milvus 配置已保存');

    // 保存 Embedding 模型配置和 AI 描述生成模型到模型系统配置
    await request.put('/model-config', {
      embeddingModel: config.embeddingModel,
      descGenerationModel: config.descGenerationModel,
      siliconFlowEndpoint: config.siliconflowBaseUrl,
      siliconFlowApiKey: config.siliconflowApiKey
    });
    ElMessage.success('Embedding 模型和API配置已保存');

    ElMessage.success('配置已保存！请重启服务使更改生效。');
  } catch (e) {
    console.error('保存失败:', e);
    ElMessage.error('保存失败: ' + (e.message || '未知错误'));
  } finally {
    saving.value = false;
  }
};

// 测试 Milvus 连接
const testMilvusConnection = async () => {
  if (testingMilvus.value) return; // 防止重复点击
  testingMilvus.value = true;
  milvusStatus.value.online = false;

  // 添加超时保护
  const timeout = setTimeout(() => {
    testingMilvus.value = false;
    ElMessage.warning('连接超时，请检查Milvus服务是否启动');
  }, 10000);

  try {
    console.log('Testing Milvus:', config.milvusHost, config.milvusPort);
    const res = await request.get('/monitor/milvus/test', {
      params: {
        host: config.milvusHost,
        port: config.milvusPort,
        token: config.milvusToken
      }
    });
    clearTimeout(timeout);
    console.log('Milvus test result:', res);
    milvusStatus.value.online = res.online;
    if (res.online) {
      ElMessage.success('Milvus 连接成功！');
      await loadCollectionInfo();
    } else {
      ElMessage.warning('Milvus 连接失败: ' + (res.error || '未知错误'));
    }
  } catch (e) {
    clearTimeout(timeout);
    milvusStatus.value.online = false;
    console.error('Milvus test error:', e);
    ElMessage.error('测试失败: ' + (e.response?.data?.message || e.message || '请检查Milvus服务是否启动'));
  } finally {
    testingMilvus.value = false;
  }
};

// 加载 Collection 信息
const loadCollectionInfo = async () => {
  loadingCollection.value = true;
  try {
    const res = await request.get('/knowledge/collection/info');
    if (res) {
      collectionInfo.value = res;
    }
  } catch (e) {
    console.error('加载 Collection 信息失败:', e);
    collectionInfo.value = { exists: false };
  } finally {
    loadingCollection.value = false;
  }
};

// 重建 Collection
const recreateCollection = async () => {
  try {
    await ElMessageBox.confirm(
      '此操作将删除现有的 Collection 和所有向量数据，是否继续？',
      '警告',
      {
        confirmButtonText: '确认重建',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    recreating.value = true;
    await request.post('/knowledge/collection/recreate');
    ElMessage.success('Collection 重建成功！');
    await loadCollectionInfo();
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('重建失败: ' + e.message);
    }
  } finally {
    recreating.value = false;
  }
};

// 加载 Embedding 模型列表 - 从系统模型管理中获取
const loadEmbeddingModels = async () => {
  if (embeddingModels.value.length > 0) return;

  loadingModels.value = true;
  try {
    // 从系统已配置的模型列表获取
    const res = await request.get('/models');

    if (res && Array.isArray(res)) {
      // 筛选 EMBEDDING 类型的模型
      const embeddingModelsFiltered = res.filter(m => m.type === 'EMBEDDING');

      if (embeddingModelsFiltered.length > 0) {
        embeddingModels.value = embeddingModelsFiltered.map(m => ({
          id: m.modelName || m.name || m.modelId,
          name: m.modelName || m.name || m.modelId
        }));
      } else {
        ElMessage.info('系统中暂无 Embedding 模型，请在模型管理中添加');
      }
    }
  } catch (e) {
    console.error('加载 Embedding 模型失败:', e);
  } finally {
    loadingModels.value = false;
  }
};

// 加载 Rerank 模型列表
const loadRerankModels = async () => {
  if (rerankModels.value.length > 0) return;

  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      // 筛选 RERANK 类型的模型（支持多种大小写格式）
      const rerankFiltered = res.filter(m => {
        const type = m.type?.toUpperCase();
        return type === 'RERANK' || type === 'RERANKER';
      });

      if (rerankFiltered.length > 0) {
        rerankModels.value = rerankFiltered.map(m => ({
          id: m.modelId || m.modelName || m.name,
          name: m.name || m.modelName || m.modelId
        }));
      } else {
        // 如果没有 RERANK 类型，使用默认列表
        rerankModels.value = [
          { id: 'BAAI/bge-reranker-v2-m3', name: 'BAAI/bge-reranker-v2-m3' },
          { id: 'BAAI/bge-reranker-base', name: 'BAAI/bge-reranker-base' },
          { id: 'BAAI/bge-reranker-large', name: 'BAAI/bge-reranker-large' }
        ];
      }
    }
  } catch (e) {
    console.error('加载 Rerank 模型失败:', e);
    // 使用默认值
    rerankModels.value = [
      { id: 'BAAI/bge-reranker-v2-m3', name: 'BAAI/bge-reranker-v2-m3' },
      { id: 'BAAI/bge-reranker-base', name: 'BAAI/bge-reranker-base' },
      { id: 'BAAI/bge-reranker-large', name: 'BAAI/bge-reranker-large' }
    ];
  } finally {
    loadingModels.value = false;
  }
};

// 加载 Chat 模型列表（用于知识库配置页面）
const loadChatModelsForConfig = async () => {
  if (chatModelsForConfig.value.length > 0) return;

  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      // 显示所有模型，让用户选择
      chatModelsForConfig.value = res.map(m => ({
        id: m.modelId || m.modelName || m.name,
        name: m.name || m.modelName || m.modelId,
        type: m.type || ''
      }));
    }
  } catch (e) {
    console.error('加载 Chat 模型失败:', e);
  } finally {
    loadingModels.value = false;
  }
};

// 加载 Chat 模型列表
const loadChatModels = async () => {
  if (chatModels.value.length > 0) return;

  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      // 筛选 CHAT 或 LLM 类型的模型
      chatModels.value = res.filter(m =>
        m.type === 'CHAT' || m.type === 'LLM' || m.type === 'chat' || m.type === 'llm' || !m.type
      ).map(m => ({
        id: m.modelName || m.name || m.modelId,
        name: m.name || m.modelName || m.modelId,
        provider: m.provider || ''
      }));
    }
  } catch (e) {
    console.error('加载 Chat 模型失败:', e);
  } finally {
    loadingModels.value = false;
  }
};

// 跳转到模型管理页面
const goToModelConfig = () => {
  router.push('/dashboard/applications/models');
};

// 测试 Embedding 连接
const testEmbeddingConnection = async () => {
  testingEmbedding.value = true;
  try {
    // TODO: 需要后端实现 embedding 测试接口
    ElMessage.info('Embedding 测试功能待后端实现');
    testingEmbedding.value = false;
    return;
    // const res = await request.get('/knowledge/embedding/test', {
    //   params: {
    //     provider: config.embeddingProvider,
    //     model: config.embeddingModel
    //   }
    // });
    if (res.success) {
      ElMessage.success('Embedding 模型可用！');
    } else {
      ElMessage.warning('Embedding 测试失败: ' + (res.error || '未知错误'));
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message);
  } finally {
    testingEmbedding.value = false;
  }
};

// 测试描述生成模型连接
const testDescModel = async () => {
  if (!config.descGenerationModel) {
    return ElMessage.warning('请先选择描述生成模型');
  }
  testingDescModel.value = true;
  try {
    ElMessage.info('描述生成模型测试功能待后端实现');
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message);
  } finally {
    testingDescModel.value = false;
  }
};

// 加载知识库统计
const loadKnowledgeStats = async () => {
  loadingStats.value = true;
  try {
    // TODO: 需要后端实现知识库统计接口
    // 暂时从 Collection 状态推断
    const collectionInfo = await request.get('/knowledge/collection/info');
    if (collectionInfo && collectionInfo.exists) {
      knowledgeStats.value = {
        totalKBs: 'N/A',
        totalDocs: 'N/A',
        totalVectors: collectionInfo.vectorCount || 'N/A'
      };
    } else {
      knowledgeStats.value = {
        totalKBs: 0,
        totalDocs: 0,
        totalVectors: 0
      };
    }
  } catch (e) {
    console.error('加载统计失败:', e);
    knowledgeStats.value = {
      totalKBs: 0,
      totalDocs: 0,
      totalVectors: 0
    };
  } finally {
    loadingStats.value = false;
  }
};

onMounted(() => {
  loadConfig();
  // 延迟加载 Milvus 状态，避免页面加载时卡住
  setTimeout(() => {
    testMilvusConnection();
  }, 500);
  // 延迟加载统计信息
  setTimeout(() => {
    loadKnowledgeStats();
  }, 1000);
});
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-200) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  color: var(--neutral-gray-800);
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 6px;
  line-height: 1.5;
}

.config-tabs {
  margin-top: 24px;
}

.margin-bottom-lg {
  margin-bottom: 24px;
}

.guide-step {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.step-num {
  width: 24px;
  height: 24px;
  background: var(--orin-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-text strong {
  display: block;
  font-size: 14px;
  color: var(--neutral-gray-800);
  margin-bottom: 4px;
}

.step-text p {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin: 0;
  line-height: 1.4;
}

.install-cmd {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 8px 0 0 0;
}

.collection-info {
  padding: 12px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-item {
  padding: 12px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-200);
}

.model-name {
  font-weight: 600;
  color: var(--neutral-gray-800);
  margin-bottom: 4px;
  font-family: monospace;
}

.model-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.stats-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.stat-label {
  color: var(--neutral-gray-600);
  font-size: 13px;
}

.stat-value {
  font-weight: 700;
  font-size: 18px;
  color: var(--orin-primary);
}
</style>
