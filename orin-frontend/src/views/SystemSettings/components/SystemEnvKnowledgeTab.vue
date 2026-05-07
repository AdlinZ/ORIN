<template>
  <div class="knowledge-config-tab">
    <el-tabs v-model="activeTab" class="config-tabs" @tab-click="handleTabClick">
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
                  <el-input v-model="config.milvusHost" placeholder="例如: 192.168.1.164" />
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
                  <el-button :loading="testingMilvus" type="success" plain @click="testMilvusConnection">测试连接</el-button>
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
                  <el-descriptions-item label="Collection 名称">{{ collectionInfo.collectionName }}</el-descriptions-item>
                  <el-descriptions-item label="向量维度">{{ collectionInfo.dimension }}</el-descriptions-item>
                  <el-descriptions-item label="向量数量">{{ collectionInfo.vectorCount || '未知' }}</el-descriptions-item>
                  <el-descriptions-item label="索引类型">{{ collectionInfo.indexType || '未知' }}</el-descriptions-item>
                </el-descriptions>
                <div style="margin-top: 16px">
                  <el-button :loading="loadingCollection" size="small" type="primary" plain @click="loadCollectionInfo">刷新统计</el-button>
                  <el-button :loading="recreating" size="small" type="warning" plain @click="recreateCollection">重建 Collection</el-button>
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
              <el-alert title="提示：ORIN 使用名为 'orin_knowledge_base' 的 Collection，每个知识库对应一个 Partition。" type="info" :closable="false" show-icon style="margin-top: 20px" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 向量数据详情 Tab -->
      <el-tab-pane label="向量数据详情" name="milvus-detail">
        <el-card class="premium-card">
          <template #header>
            <div class="card-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>Milvus 向量数据详情</span>
              <el-button :loading="loadingDetail" size="small" type="primary" plain style="margin-left: auto" @click="loadCollectionDetail">刷新数据</el-button>
            </div>
          </template>

          <div v-if="collectionDetail.exists" class="detail-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="Collection 名称">{{ collectionDetail.collectionName }}</el-descriptions-item>
              <el-descriptions-item label="向量维度">{{ collectionDetail.dimension }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="collectionDetail.status === 'connected' ? 'success' : 'warning'">{{ collectionDetail.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="向量总数">{{ collectionDetail.totalVectors || 0 }}</el-descriptions-item>
              <el-descriptions-item label="文档总数">{{ collectionDetail.totalDocs || 0 }}</el-descriptions-item>
              <el-descriptions-item label="分区数量">{{ collectionDetail.partitionCount || 0 }}</el-descriptions-item>
            </el-descriptions>

            <div v-if="collectionDetail.knowledgeBases && collectionDetail.knowledgeBases.length > 0" style="margin-top: 20px">
              <h4 style="margin-bottom: 12px; color: var(--neutral-gray-600)">知识库详情</h4>
              <el-table :data="collectionDetail.knowledgeBases" size="small" border stripe>
                <el-table-column prop="name" label="知识库名称">
                  <template #default="{ row }">
                    <el-button type="primary" link @click="viewKnowledgeBaseVectors(row)">{{ row.name }}</el-button>
                  </template>
                </el-table-column>
                <el-table-column prop="docCount" label="文档数" width="100" />
                <el-table-column prop="vectorCount" label="向量数" width="100" />
              </el-table>
            </div>

            <div v-if="collectionDetail.partitions && collectionDetail.partitions.length > 0" style="margin-top: 20px">
              <h4 style="margin-bottom: 12px; color: var(--neutral-gray-600)">分区信息</h4>
              <el-table :data="collectionDetail.partitions" size="small" border stripe>
                <el-table-column prop="name" label="分区名称" />
                <el-table-column prop="vectorCount" label="向量数" width="100" />
              </el-table>
            </div>

            <el-alert v-if="collectionDetail.note" :title="collectionDetail.note" type="info" style="margin-top: 16px" show-icon />
          </div>

          <el-empty v-else description="Collection 不存在或未连接" :image-size="80" />
        </el-card>
      </el-tab-pane>

      <!-- 模型配置 Tab -->
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
                <el-form-item label="向量嵌入服务提供商">
                  <el-select v-model="config.embeddingProvider" style="width: 100%" placeholder="选择嵌入服务提供商" @change="onProviderChange">
                    <el-option v-for="provider in embeddingProviders" :key="provider.value" :label="provider.label" :value="provider.value" />
                  </el-select>
                  <p class="form-tip">选择用于文档向量化的嵌入服务提供商</p>
                </el-form-item>

                <el-form-item label="API 密钥">
                  <el-select v-model="config.embeddingApiKeyId" style="width: 100%" placeholder="选择已配置的 API 密钥" clearable @focus="loadApiKeys">
                    <el-option v-for="key in apiKeys" :key="key.id" :label="`${key.provider} - ${key.name || key.id}`" :value="key.id">
                      <span style="float: left">{{ key.provider }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">{{ key.enabled ? '已启用' : '已禁用' }}</span>
                    </el-option>
                  </el-select>
                  <p class="form-tip">
                    选择已在"API密钥管理"中配置的密钥
                    <el-button type="primary" link @click="router.push('/dashboard/control/gateway?workspace=access')">去配置</el-button>
                  </p>
                </el-form-item>

                <el-form-item label="Embedding 模型">
                  <el-select v-model="config.embeddingModel" style="width: 100%" filterable allow-create default-first-option placeholder="选择或输入模型名称" @focus="loadEmbeddingModels">
                    <el-option v-for="model in embeddingModels" :key="model.id" :label="model.id" :value="model.id">
                      <span style="float: left">{{ model.id }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">{{ model.name || '' }}</span>
                    </el-option>
                  </el-select>
                  <p class="form-tip">用于将文档向量化，支持中文推荐 bge-base-zh-v1.5</p>
                </el-form-item>

                <el-form-item>
                  <el-button :loading="testingEmbedding" type="success" plain @click="testEmbeddingConnection">测试连接</el-button>
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

                <el-form-item v-if="config.enableRerank" label="Rerank 模型">
                  <el-select v-model="config.rerankModel" style="width: 100%" filterable placeholder="选择 Rerank 模型" clearable @focus="loadRerankModels">
                    <el-option v-for="model in rerankModels" :key="model.id" :label="model.name || model.id" :value="model.id">
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
                  <el-select v-model="config.descGenerationModel" style="width: 100%" filterable placeholder="选择用于生成描述的大语言模型" clearable @focus="loadChatModelsForConfig">
                    <el-option v-for="model in chatModelsForConfig" :key="model.id" :label="model.name || model.id" :value="model.id">
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

      <!-- 默认知识库配置 Tab -->
      <el-tab-pane label="默认知识库配置" name="global">
        <el-row :gutter="24">
          <el-col :lg="16">
            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Setting /></el-icon>
                  <span>默认知识库基础配置</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-form-item label="默认 Collection 名称">
                  <el-input v-model="config.defaultCollection" placeholder="orin_knowledge_base" />
                  <p class="form-tip">全局唯一的 Collection 名称，用于存储所有知识库的向量数据</p>
                </el-form-item>

                <el-form-item label="默认 Chunk 最大字符数">
                  <el-input-number v-model="config.chunkSize" :min="100" :max="2000" :step="100" style="width: 200px" />
                  <p class="form-tip">文档分块时每个 Chunk 的最大字符数，默认 500</p>
                </el-form-item>

                <el-form-item label="默认 Chunk 重叠字符数">
                  <el-input-number v-model="config.chunkOverlap" :min="0" :max="500" :step="50" style="width: 200px" />
                  <p class="form-tip">相邻 Chunk 之间的重叠字符数，用于保持上下文连贯性，默认 50</p>
                </el-form-item>

                <el-form-item label="默认检索返回结果数 (Top K)">
                  <el-input-number v-model="config.defaultTopK" :min="1" :max="20" :step="1" style="width: 200px" />
                  <p class="form-tip">向量相似度搜索返回的最大结果数，默认 5</p>
                </el-form-item>

                <el-form-item label="默认相似度阈值">
                  <el-slider v-model="config.similarityThreshold" :min="0" :max="1" :step="0.05" show-stops :format-tooltip="(val) => (val * 100).toFixed(0) + '%'" />
                  <p class="form-tip">只有相似度高于此阈值的文档才会被返回，默认 0.7</p>
                </el-form-item>
              </el-form>
            </el-card>

            <!-- 多模态配置 -->
            <el-card class="premium-card margin-top-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Picture /></el-icon>
                  <span>多模态解析配置</span>
                </div>
              </template>

              <el-form :model="config" label-position="top" class="config-form">
                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="OCR 图片文字识别">
                      <el-select v-model="config.ocrProvider" placeholder="选择 OCR 提供商" style="width: 100%">
                        <el-option value="local" label="本地 (Tesseract)" />
                        <el-option value="cloud" label="云服务 API" />
                      </el-select>
                      <p class="form-tip">图片文字识别服务，用于从图片中提取文本内容</p>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="ASR 语音识别">
                      <el-select v-model="config.asrProvider" placeholder="选择 ASR 提供商" style="width: 100%">
                        <el-option value="local" label="本地 (Whisper)" />
                        <el-option value="cloud" label="云服务 API" />
                      </el-select>
                      <p class="form-tip">语音识别服务，用于从音频/视频中提取文本内容</p>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="OCR 模型 (云服务)">
                      <el-input v-model="config.ocrModel" placeholder="如: ocr-general-v1" />
                      <p class="form-tip">云服务 OCR 模型名称</p>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="ASR 模型 (本地)">
                      <el-select v-model="config.asrModel" placeholder="选择模型" style="width: 100%">
                        <el-option value="tiny" label="tiny - 最快，最低精度" />
                        <el-option value="base" label="base - 平衡选择" />
                        <el-option value="small" label="small - 较好精度" />
                        <el-option value="medium" label="medium - 高精度" />
                        <el-option value="large" label="large - 最高精度" />
                      </el-select>
                      <p class="form-tip">Whisper 模型大小，影响识别精度和速度</p>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-card>
          </el-col>

          <el-col :lg="8">
            <!-- AI 生成知识库名称和描述 -->
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><MagicStick /></el-icon>
                  <span>AI 生成知识库描述</span>
                </div>
              </template>
              <div class="ai-generate-form">
                <el-form label-position="top" size="small">
                  <el-form-item label="选择知识库">
                    <el-select v-model="selectedKB" placeholder="选择知识库" style="width: 100%" filterable>
                      <el-option v-for="kb in knowledgeBases" :key="kb.kbId" :label="kb.name" :value="kb.kbId">
                        <span>{{ kb.name }}</span>
                        <span style="float: right; color: #8492a6; font-size: 12px">{{ kb.docCount || 0 }} 文档</span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="选择 AI 模型">
                    <el-select v-model="selectedKBModel" placeholder="选择模型" style="width: 100%" filterable>
                      <el-option v-for="model in kbModels" :key="model.id" :label="model.name" :value="model.id">
                        <span>{{ model.name }}</span>
                        <span style="float: right; color: #8492a6; font-size: 12px">{{ model.provider }}</span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-button type="primary" :loading="generatingDesc" :disabled="!selectedKB || !selectedKBModel" style="width: 100%" @click="generateKBNameAndDesc">生成名称和描述</el-button>
                </el-form>
              </div>
              <p class="form-tip" style="margin-top: 12px">AI 将根据知识库中的文档内容自动生成名称和描述</p>
            </el-card>

            <!-- 知识库统计 -->
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
              <el-button :loading="loadingStats" size="small" style="width: 100%; margin-top: 16px" @click="loadKnowledgeStats">刷新统计</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 向量详情弹窗 -->
    <el-dialog v-model="vectorDialogVisible" :title="`向量详情 - ${vectorDialogData.knowledgeBase?.name || ''}`" width="900px" destroy-on-close>
      <div v-loading="vectorDialogData.loading">
        <el-alert :title="`共 ${vectorDialogData.totalChunks} 个向量，${vectorDialogData.totalDocs} 个文档`" type="info" :closable="false" show-icon style="margin-bottom: 16px" />
        <el-table :data="vectorDialogData.chunks" size="small" border max-height="500">
          <el-table-column prop="fileName" label="文件名" width="180" show-overflow-tooltip />
          <el-table-column prop="chunkIndex" label="索引" width="60" />
          <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="250" show-overflow-tooltip />
          <el-table-column prop="charCount" label="字符数" width="80" />
          <el-table-column prop="vectorId" label="Vector ID" width="100" show-overflow-tooltip />
        </el-table>
        <div style="margin-top: 16px; text-align: center">
          <el-button v-if="vectorDialogData.chunks.length < vectorDialogData.totalChunks" type="primary" link :loading="vectorDialogData.loading" @click="loadMoreVectors">加载更多</el-button>
          <span v-else-if="vectorDialogData.chunks.length > 0" style="color: #999">已加载全部</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import request from '@/utils/request';
import { diagnoseMilvus } from '@/api/knowledge';
import { Connection, InfoFilled, QuestionFilled, Cpu, Setting, DataAnalysis, MagicStick, Sort, Picture } from '@element-plus/icons-vue';

const router = useRouter();
const activeTab = ref('milvus');
const testingMilvus = ref(false);
const testingEmbedding = ref(false);
const loadingCollection = ref(false);
const recreating = ref(false);
const loadingStats = ref(false);
const embeddingModels = ref([]);
const rerankModels = ref([]);
const chatModelsForConfig = ref([]);
const generatingDesc = ref(false);
const apiKeys = ref([]);
const knowledgeBases = ref([]);
const selectedKB = ref(null);
const kbModels = ref([]);
const selectedKBModel = ref('');
const loadingDetail = ref(false);
const milvusStatus = ref({ online: false });
const collectionInfo = ref({ exists: false });
const collectionDetail = ref({ exists: false, partitions: [] });
const vectorDialogVisible = ref(false);
const vectorDialogData = ref({ knowledgeBase: null, chunks: [], totalChunks: 0, totalDocs: 0, page: 0, size: 20, loading: false });
const knowledgeStats = ref({ totalKBs: 0, totalDocs: 0, totalVectors: 0 });

const embeddingProviders = [
  { value: 'SiliconFlow', label: 'SiliconFlow', endpoint: 'https://api.siliconflow.cn/v1' },
  { value: 'Ollama', label: 'Ollama (本地)', endpoint: '' }
];

const config = reactive({
  milvusHost: '192.168.1.164',
  milvusPort: 19530,
  milvusToken: '',
  embeddingProvider: 'SiliconFlow',
  embeddingModel: 'Qwen/Qwen3-Embedding-8B',
  embeddingApiKeyId: '',
  siliconflowApiKey: '',
  siliconflowBaseUrl: 'https://api.siliconflow.cn/v1',
  defaultCollection: 'orin_knowledge_base',
  chunkSize: 500,
  chunkOverlap: 50,
  defaultTopK: 5,
  similarityThreshold: 0.7,
  descGenerationModel: '',
  enableRerank: false,
  rerankModel: '',
  ocrProvider: 'local',
  ocrModel: '',
  asrProvider: 'local',
  asrModel: 'base'
});

const loadApiKeys = async () => {
  try { const res = await request.get('/api-keys/external'); apiKeys.value = res || []; }
  catch (e) { apiKeys.value = []; }
};

const loadKnowledgeBases = async () => {
  try { const res = await request.get('/knowledge/list'); knowledgeBases.value = res || []; }
  catch (e) { knowledgeBases.value = []; }
};

const loadKBModels = async () => {
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      kbModels.value = res.filter(m => m.type === 'CHAT' || m.type === 'LLM' || m.type === 'chat' || m.type === 'llm' || !m.type)
        .map(m => ({ id: m.modelId || m.modelName || m.name, name: m.name || m.modelName || m.modelId, provider: m.provider || '' }));
      if (kbModels.value.length > 0 && !selectedKBModel.value) selectedKBModel.value = kbModels.value[0].id;
    }
  } catch (e) { /* ignore */ }
};

const generateKBNameAndDesc = async () => {
  if (!selectedKB.value) { ElMessage.warning('请先选择知识库'); return; }
  if (!selectedKBModel.value) { ElMessage.warning('请先选择 AI 模型'); return; }
  generatingDesc.value = true;
  try {
    const res = await request.post(`/knowledge/${selectedKB.value}/generate-description`, { model: selectedKBModel.value });
    if (res.title || res.description) {
      await request.put(`/knowledge/${selectedKB.value}`, { name: res.title || knowledgeBases.value.find(kb => kb.kbId === selectedKB.value)?.name, description: res.description || '' });
      ElMessage.success(res.title ? '名称和描述生成成功' : '描述生成成功');
      await loadKnowledgeBases();
    } else { ElMessage.warning('AI 未返回内容'); }
  } catch (e) { ElMessage.error('生成失败: ' + (e.message || '未知错误')); }
  finally { generatingDesc.value = false; }
};

const loadConfig = async () => {
  try {
    await loadApiKeys();
    const res = await request.get('/monitor/system/properties');
    if (res) {
      config.milvusHost = res['milvus.host'] || '192.168.1.164';
      config.milvusPort = parseInt(res['milvus.port']) || 19530;
      config.milvusToken = res['milvus.token'] || '';
      config.enableRerank = res['knowledge.rerank.enabled'] === 'true';
      config.rerankModel = res['knowledge.rerank.model'] || '';
    }
    try {
      const modelConfig = await request.get('/model-config');
      if (modelConfig) {
        config.embeddingProvider = modelConfig.embeddingProvider || 'SiliconFlow';
        config.embeddingModel = modelConfig.embeddingModel || 'Qwen/Qwen3-Embedding-8B';
        config.embeddingApiKeyId = modelConfig.embeddingApiKeyId || '';
        config.siliconflowApiKey = modelConfig.siliconFlowApiKey || '';
        config.siliconflowBaseUrl = modelConfig.siliconFlowEndpoint || 'https://api.siliconflow.cn/v1';
        config.descGenerationModel = modelConfig.descGenerationModel || '';
      }
    } catch (e) { /* ignore */ }
  } catch (e) { /* ignore */ }
};

const saveConfig = async () => {
  try {
    await request.post('/monitor/system/properties', {
      'milvus.host': config.milvusHost,
      'milvus.port': config.milvusPort.toString(),
      'milvus.token': config.milvusToken,
      'knowledge.rerank.enabled': config.enableRerank.toString(),
      'knowledge.rerank.model': config.rerankModel || ''
    });
    await request.put('/model-config', {
      embeddingProvider: config.embeddingProvider,
      embeddingModel: config.embeddingModel,
      embeddingApiKeyId: config.embeddingApiKeyId || null,
      descGenerationModel: config.descGenerationModel,
      siliconFlowEndpoint: config.siliconflowBaseUrl,
      siliconFlowApiKey: config.siliconflowApiKey
    });
    ElMessage.success('配置已保存！请重启服务使更改生效。');
  } catch (e) { ElMessage.error('保存失败: ' + (e.message || '未知错误')); }
};

const testMilvusConnection = async () => {
  if (testingMilvus.value) return;
  testingMilvus.value = true;
  milvusStatus.value.online = false;
  const timeout = setTimeout(() => { testingMilvus.value = false; ElMessage.warning('连接超时，请检查Milvus服务是否启动'); }, 10000);
  try {
    const res = await request.get('/monitor/milvus/test', { params: { host: config.milvusHost, port: config.milvusPort, token: config.milvusToken } });
    clearTimeout(timeout);
    milvusStatus.value.online = res.online;
    if (res.online) { ElMessage.success('Milvus 连接成功！'); await loadCollectionInfo(); }
    else { ElMessage.warning('Milvus 连接失败: ' + (res.error || '未知错误')); }
  } catch (e) { clearTimeout(timeout); milvusStatus.value.online = false; ElMessage.error('测试失败: ' + (e.response?.data?.message || e.message || '请检查Milvus服务是否启动')); }
  finally { testingMilvus.value = false; }
};

const loadCollectionInfo = async () => {
  loadingCollection.value = true;
  try { const res = await request.get('/knowledge/collection/info'); if (res) collectionInfo.value = res; }
  catch (e) { collectionInfo.value = { exists: false }; }
  finally { loadingCollection.value = false; }
};

const loadCollectionDetail = async () => {
  loadingDetail.value = true;
  try { const res = await request.get('/knowledge/collection/detail'); if (res) collectionDetail.value = res; }
  catch (e) { collectionDetail.value = { exists: false }; }
  finally { loadingDetail.value = false; }
};

const handleTabClick = (tab) => {
  if (tab.props.name === 'milvus-detail') loadCollectionDetail();
};

const viewKnowledgeBaseVectors = async (kb) => {
  vectorDialogData.value = { knowledgeBase: kb, chunks: [], totalChunks: 0, totalDocs: 0, page: 0, size: 20, loading: true };
  vectorDialogVisible.value = true;
  try {
    const res = await request.get(`/knowledge/kb/${kb.id}/vectors`, { params: { page: 0, size: 20 } });
    vectorDialogData.value.chunks = res.chunks || [];
    vectorDialogData.value.totalChunks = res.totalChunks || 0;
    vectorDialogData.value.totalDocs = res.totalDocs || 0;
  } catch (e) { ElMessage.error('加载向量详情失败: ' + e.message); }
  finally { vectorDialogData.value.loading = false; }
};

const loadMoreVectors = async () => {
  if (vectorDialogData.value.loading) return;
  vectorDialogData.value.loading = true;
  try {
    const nextPage = vectorDialogData.value.page + 1;
    const res = await request.get(`/knowledge/kb/${vectorDialogData.value.knowledgeBase.id}/vectors`, { params: { page: nextPage, size: vectorDialogData.value.size } });
    vectorDialogData.value.chunks = [...vectorDialogData.value.chunks, ...(res.chunks || [])];
    vectorDialogData.value.page = nextPage;
  } catch (e) { ElMessage.error('加载更多失败: ' + e.message); }
  finally { vectorDialogData.value.loading = false; }
};

const recreateCollection = async () => {
  try {
    await ElMessageBox.confirm('此操作将删除现有的 Collection 和所有向量数据，是否继续？', '警告', { confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning' });
    recreating.value = true;
    await request.post('/knowledge/collection/recreate');
    ElMessage.success('Collection 重建成功！');
    await loadCollectionInfo();
  } catch (e) { if (e !== 'cancel') ElMessage.error('重建失败: ' + e.message); }
  finally { recreating.value = false; }
};

const onProviderChange = (provider) => {
  const providerConfig = embeddingProviders.find(p => p.value === provider);
  if (providerConfig) config.siliconflowBaseUrl = providerConfig.endpoint || '';
  config.embeddingModel = '';
};

const loadEmbeddingModels = async () => {
  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      const filtered = res.filter(m => m.type === 'EMBEDDING');
      embeddingModels.value = filtered.map(m => ({ id: m.modelName || m.name || m.modelId, name: m.modelName || m.name || m.modelId }));
      if (filtered.length === 0) ElMessage.info('系统中暂无 Embedding 模型，请在模型管理中添加');
    }
  } catch (e) { /* ignore */ }
  finally { loadingModels.value = false; }
};

const loadingModels = ref(false);

const loadRerankModels = async () => {
  if (rerankModels.value.length > 0) return;
  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) {
      const filtered = res.filter(m => { const type = m.type?.toUpperCase(); return type === 'RERANK' || type === 'RERANKER'; });
      rerankModels.value = filtered.map(m => ({ id: m.modelId || m.modelName || m.name, name: m.name || m.modelName || m.modelId }));
      if (filtered.length === 0) rerankModels.value = [{ id: 'BAAI/bge-reranker-v2-m3', name: 'BAAI/bge-reranker-v2-m3' }, { id: 'BAAI/bge-reranker-base', name: 'BAAI/bge-reranker-base' }, { id: 'BAAI/bge-reranker-large', name: 'BAAI/bge-reranker-large' }];
    }
  } catch (e) { rerankModels.value = [{ id: 'BAAI/bge-reranker-v2-m3', name: 'BAAI/bge-reranker-v2-m3' }, { id: 'BAAI/bge-reranker-base', name: 'BAAI/bge-reranker-base' }]; }
  finally { loadingModels.value = false; }
};

const loadChatModelsForConfig = async () => {
  if (chatModelsForConfig.value.length > 0) return;
  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res && Array.isArray(res)) chatModelsForConfig.value = res.map(m => ({ id: m.modelId || m.modelName || m.name, name: m.name || m.modelName || m.modelId, type: m.type || '' }));
  } catch (e) { /* ignore */ }
  finally { loadingModels.value = false; }
};

const testEmbeddingConnection = async () => {
  testingEmbedding.value = true;
  try {
    const res = await diagnoseMilvus();
    if (res && res.embedding && res.embedding.status === 'ok') ElMessage.success('Embedding 模型可用！维度: ' + res.embedding.dimension);
    else if (res && res.embedding && res.embedding.status === 'error') ElMessage.error('Embedding 测试失败: ' + res.embedding.error);
    else ElMessage.warning('Milvus 诊断结果未知');
  } catch (e) { ElMessage.error('测试失败: ' + e.message); }
  finally { testingEmbedding.value = false; }
};

const loadKnowledgeStats = async () => {
  loadingStats.value = true;
  try {
    const res = await diagnoseMilvus();
    if (res && res.documents) knowledgeStats.value = { totalKBs: 'N/A', totalDocs: res.documents.total, totalVectors: res.collection?.vectorCount || 'N/A' };
    else knowledgeStats.value = { totalKBs: 'N/A', totalDocs: 'N/A', totalVectors: 'N/A' };
  } catch (e) { knowledgeStats.value = { totalKBs: 'N/A', totalDocs: 'N/A', totalVectors: 'N/A' }; }
  finally { loadingStats.value = false; }
};

onMounted(() => {
  loadConfig();
  setTimeout(() => { testMilvusConnection(); }, 500);
  setTimeout(() => { loadKnowledgeStats(); }, 1000);
  loadKnowledgeBases();
  loadKBModels();
});

// 暴露 saveConfig 给父组件调用
defineExpose({ saveConfig });
</script>

<style scoped>
.knowledge-config-tab { padding: 0; }

.card-header { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--neutral-gray-800); }
.form-tip { font-size: 12px; color: var(--neutral-gray-500); margin-top: 6px; line-height: 1.5; }
.config-tabs { margin-top: 24px; }
.margin-bottom-lg { margin-bottom: 24px; }
.margin-top-lg { margin-top: 24px; }
.premium-card { border-radius: var(--radius-xl) !important; border: 1px solid var(--neutral-gray-200) !important; }

.guide-step { display: flex; gap: 12px; margin-bottom: 20px; }
.step-num { width: 24px; height: 24px; background: var(--orin-primary-50); color: white; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.step-text strong { display: block; font-size: 14px; color: var(--neutral-gray-800); margin-bottom: 4px; }
.step-text p { font-size: 12px; color: var(--neutral-gray-500); margin: 0; line-height: 1.4; }
.install-cmd { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; font-size: 11px; line-height: 1.5; overflow-x: auto; white-space: pre-wrap; word-break: break-all; margin: 8px 0 0 0; }
.collection-info { padding: 12px; background: var(--neutral-gray-50); border-radius: 8px; }

.model-list { display: flex; flex-direction: column; gap: 12px; }
.model-item { padding: 12px; background: var(--neutral-gray-50); border-radius: 8px; border: 1px solid var(--neutral-gray-200); }
.model-name { font-weight: 600; color: var(--neutral-gray-800); margin-bottom: 4px; font-family: monospace; }
.model-desc { font-size: 12px; color: var(--neutral-gray-500); }

.stats-list { display: flex; flex-direction: column; gap: 16px; }
.stat-item { display: flex; justify-content: space-between; align-items: center; padding: 12px; background: var(--neutral-gray-50); border-radius: 8px; }
.stat-label { color: var(--neutral-gray-600); font-size: 13px; }
.stat-value { font-weight: 700; font-size: 18px; color: var(--orin-primary); }
.ai-generate-form { padding: 8px 0; }
</style>
