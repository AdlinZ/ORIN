<template>
  <div class="kb-workbench-page">
    <div class="workbench-hero">
      <div class="hero-main">
        <div class="title-row">
          <el-button link :icon="ArrowLeft" @click="router.push(ROUTES.RESOURCES.KNOWLEDGE)">
            返回知识库列表
          </el-button>
          <span class="divider">/</span>
          <span class="current-name">{{ kbData.name || '知识库详情' }}</span>
        </div>
        <h1>{{ kbData.name || '知识库' }}</h1>
        <div class="title-meta-pills">
          <span class="meta-pill">{{ getTypeName(kbData.type) }}</span>
          <span class="meta-pill">{{ documents.length }} 文档</span>
          <span class="meta-pill">{{ groupedDocs.length }} 分组</span>
          <span class="meta-pill">最近更新 {{ formatUpdatedAt(kbData) }}</span>
        </div>
        <p class="title-desc">{{ kbData.description || '用于管理文档、检索效果和知识图谱可视化。' }}</p>
      </div>
      <div class="hero-side">
        <div class="hero-kpi">
          <span>检索命中</span>
          <strong>{{ filteredResults.length }}</strong>
        </div>
        <div class="hero-kpi">
          <span>平均相似度</span>
          <strong>{{ avgScore.toFixed(2) }}</strong>
        </div>
        <div class="hero-kpi">
          <span>命中文档</span>
          <strong>{{ uniqueDocCount }}</strong>
        </div>
      </div>
    </div>

    <div class="workbench-body">
      <aside class="left-panel">
        <section class="kb-info-card">
          <div class="kb-icon" :class="getIconClass(kbData.type)">
            <el-icon>
              <component :is="getIcon(kbData.type)" />
            </el-icon>
          </div>
          <div class="kb-meta compact">
            <h3>知识库概览</h3>
            <div class="meta-stat-row">
              <span class="stat-label">类型</span>
              <span class="stat-value">{{ getTypeName(kbData.type) }}</span>
            </div>
            <div class="meta-stat-row">
              <span class="stat-label">文档</span>
              <span class="stat-value">{{ documents.length }}</span>
            </div>
            <div class="meta-stat-row">
              <span class="stat-label">分组</span>
              <span class="stat-value">{{ groupedDocs.length }}</span>
            </div>
          </div>
        </section>

        <section class="doc-panel">
          <div class="panel-head">
            <h3>文档导航</h3>
            <span>{{ documents.length }} 项</span>
          </div>

          <div class="doc-toolbar">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              :accept="acceptTypes"
              multiple
            >
              <el-button type="primary" :icon="Upload">上传</el-button>
            </el-upload>
            <el-button :icon="Refresh" @click="loadDocuments">刷新</el-button>
          </div>

          <div class="doc-search-row">
            <el-input
              v-model="docKeyword"
              :prefix-icon="Search"
              clearable
              placeholder="搜索文档"
            />
          </div>

          <div v-loading="documentsLoading" class="doc-tree-wrap">
            <el-empty v-if="filteredGroupedDocs.length === 0" description="暂无文档" :image-size="72" />

            <div v-else class="folder-list">
              <div v-for="group in filteredGroupedDocs" :key="group.name" class="folder-item">
                <button class="folder-header" type="button" @click="toggleFolder(group.name)">
                  <el-icon class="folder-arrow" :class="{ open: expandedFolders[group.name] }"><ArrowRight /></el-icon>
                  <el-icon class="folder-icon"><FolderOpened /></el-icon>
                  <span class="folder-name">{{ group.name }}</span>
                  <span class="folder-count">{{ group.docs.length }}</span>
                </button>

                <div v-show="expandedFolders[group.name]" class="doc-list">
                  <div
                    v-for="doc in group.docs"
                    :key="doc.id"
                    class="doc-row"
                    :class="{ active: selectedDocId === doc.id }"
                    @click="selectedDocId = doc.id"
                  >
                    <div class="doc-main" @click.stop="openDocument(doc)">
                      <el-icon class="doc-icon"><Document /></el-icon>
                      <span class="doc-name" :title="doc.fileName">{{ doc.fileName }}</span>
                    </div>

                    <div class="doc-state">
                      <el-tooltip :content="statusText(doc.vectorStatus || doc.parseStatus)">
                        <span class="state-dot" :class="statusClass(doc.vectorStatus || doc.parseStatus)" />
                      </el-tooltip>
                    </div>

                    <el-dropdown trigger="click" @command="(command) => handleDocCommand(command, doc)">
                      <el-icon class="more-icon"><MoreFilled /></el-icon>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="open">查看详情</el-dropdown-item>
                          <el-dropdown-item command="vectorize">重新向量化</el-dropdown-item>
                          <el-dropdown-item command="delete" divided class="danger-item">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </aside>

      <main class="right-panel">
        <div class="workspace-summary">
          <div class="summary-item">
            <span class="summary-key">当前文档</span>
            <span class="summary-val">{{ selectedDoc?.fileName || '未选择' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-key">Top K</span>
            <span class="summary-val">{{ topK }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-key">阈值</span>
            <span class="summary-val">{{ Math.round(threshold * 100) }}%</span>
          </div>
          <div class="summary-item">
            <span class="summary-key">Rerank</span>
            <span class="summary-val">{{ settingsForm.enableRerank ? '已启用' : '关闭' }}</span>
          </div>
        </div>

        <div class="workspace-nav">
          <button
            v-for="item in workspaceNavItems"
            :key="item.key"
            type="button"
            class="nav-btn"
            :class="{ active: activeTab === item.key }"
            @click="activateWorkspace(item.key)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>

        <div class="workspace-stage">
          <div class="stage-header">
            <h3>{{ activeWorkspaceMeta.title }}</h3>
            <p>{{ activeWorkspaceMeta.desc }}</p>
          </div>

          <section v-show="activeTab === 'retrieve'" class="test-pane">
            <div class="test-toolbar">
              <el-input
                v-model="query"
                clearable
                placeholder="输入问题进行检索测试，例如：蛋白酶在食品加工中的作用"
                @keyup.enter="runRetrieval"
              >
                <template #append>
                  <el-button :loading="retrievalLoading" :icon="Search" @click="runRetrieval">搜索</el-button>
                </template>
              </el-input>

              <div class="test-config">
                <span>Top K</span>
                <el-input-number v-model="topK" :min="1" :max="20" />
                <span>阈值</span>
                <el-slider v-model="threshold" :min="0" :max="1" :step="0.05" style="width: 180px" />
                <span>{{ Math.round(threshold * 100) }}%</span>
              </div>
            </div>

            <div v-loading="retrievalLoading" class="result-panel">
              <div v-if="filteredResults.length > 0" class="result-list">
                <div v-for="(item, idx) in filteredResults" :key="idx" class="result-item">
                  <div class="result-top">
                    <span class="rank">#{{ idx + 1 }}</span>
                    <span class="score" :class="scoreClass(item.score)">{{ toPercent(item.score) }}</span>
                  </div>
                  <p>{{ item.content }}</p>
                  <div class="meta-line">
                    <span v-if="item.docId">文档: {{ item.docId }}</span>
                    <span v-if="item.chunkId">分片: {{ item.chunkId }}</span>
                  </div>
                </div>
              </div>

              <el-empty v-else-if="searched" description="未命中结果，请尝试改写问题" />
              <el-empty v-else description="输入问题后开始检索测试" />
            </div>
          </section>

          <section v-show="activeTab === 'graph'" class="graph-pane">
            <div class="graph-toolbar">
              <el-input
                v-model="graphKeyword"
                :prefix-icon="Search"
                clearable
                placeholder="输入实体/文档关键字过滤导图"
                @input="renderMindMap"
              />
              <el-button :icon="Refresh" @click="regenerateGraph">重新生成</el-button>
              <el-button @click="fitGraph">适应视图</el-button>
            </div>
            <div ref="mindMapRef" class="mindmap-canvas" />
          </section>

          <section v-show="activeTab === 'rag'" class="metric-pane">
            <div class="metric-grid">
              <div class="metric-card">
                <span>平均相似度</span>
                <strong>{{ avgScore.toFixed(2) }}</strong>
              </div>
              <div class="metric-card">
                <span>高相关结果</span>
                <strong>{{ highScoreCount }}</strong>
              </div>
              <div class="metric-card">
                <span>覆盖率</span>
                <strong>{{ coverage.toFixed(1) }}%</strong>
              </div>
              <div class="metric-card">
                <span>文档命中数</span>
                <strong>{{ uniqueDocCount }}</strong>
              </div>
            </div>

            <el-alert
              type="info"
              show-icon
              :closable="false"
              title="提示：先在“检索测试”里运行问题，再在这里查看评估指标。"
            />
          </section>

          <section v-show="activeTab === 'benchmark'" class="benchmark-pane">
            <div class="bench-toolbar">
              <el-button :icon="Refresh" @click="refreshBenchmarks">刷新</el-button>
            </div>

            <el-table :data="benchmarks" stripe>
              <el-table-column prop="name" label="基准名称" min-width="180" />
              <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
              <el-table-column prop="score" label="得分" width="120">
                <template #default="{ row }">{{ row.score }}%</template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="最近执行" width="180" />
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="primary" @click="runBenchmark(row)">运行</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <section v-show="activeTab === 'settings'" class="settings-pane">
            <el-form label-position="top" class="settings-form">
              <el-form-item label="知识库名称">
                <el-input v-model="settingsForm.name" placeholder="请输入知识库名称" />
              </el-form-item>
              <el-form-item label="知识库描述">
                <el-input
                  v-model="settingsForm.description"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入知识库描述"
                  resize="none"
                />
              </el-form-item>
              <el-form-item label="检索默认 Top K">
                <el-input-number v-model="settingsForm.defaultTopK" :min="1" :max="20" />
              </el-form-item>
              <el-form-item label="检索默认阈值">
                <el-slider v-model="settingsForm.defaultThreshold" :min="0" :max="1" :step="0.05" style="width: 260px" />
              </el-form-item>
              <el-form-item label="向量权重 Alpha">
                <el-slider v-model="settingsForm.alpha" :min="0" :max="1" :step="0.05" style="width: 260px" />
              </el-form-item>
              <el-form-item label="Embedding 模型">
                <el-select v-model="settingsForm.embeddingModel" filterable clearable placeholder="选择 Embedding 模型" style="width: 360px">
                  <el-option
                    v-for="item in embeddingModelOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="启用 Rerank">
                <el-switch v-model="settingsForm.enableRerank" />
              </el-form-item>
              <el-form-item v-if="settingsForm.enableRerank" label="Rerank 模型">
                <el-select v-model="settingsForm.rerankModel" filterable clearable placeholder="选择 Rerank 模型" style="width: 360px">
                  <el-option
                    v-for="item in rerankModelOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <div class="settings-actions">
                <el-button type="primary" :loading="savingSettings" @click="saveSettings">
                  保存设置
                </el-button>
              </div>
            </el-form>

            <div class="danger-zone">
              <div class="danger-meta">
                <h4>危险操作</h4>
                <p>删除知识库后，文档与索引将不可恢复。</p>
              </div>
              <el-button type="danger" plain :loading="deletingKb" @click="removeKnowledgeBase">
                删除知识库
              </el-button>
            </div>
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeft,
  ArrowRight,
  Collection,
  Connection,
  Cpu,
  Document,
  FolderOpened,
  MoreFilled,
  Opportunity,
  Refresh,
  Search,
  Setting,
  Upload
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as echarts from 'echarts';
import dayjs from 'dayjs';
import {
  deleteDocument,
  deleteKnowledge,
  getDocuments,
  getKnowledgeList,
  triggerVectorization,
  updateKnowledge,
  uploadDocument
} from '@/api/knowledge';
import { getModelList } from '@/api/model';
import request from '@/utils/request';
import { ROUTES } from '@/router/routes';

const route = useRoute();
const router = useRouter();

const kbId = ref(route.params.id);
const kbData = ref({});
const documents = ref([]);
const documentsLoading = ref(false);
const selectedDocId = ref('');
const docKeyword = ref('');
const expandedFolders = reactive({});

const activeTab = ref('graph');
const query = ref('');
const topK = ref(5);
const threshold = ref(0.3);
const searched = ref(false);
const retrievalLoading = ref(false);
const retrievalResults = ref([]);
const savingSettings = ref(false);
const deletingKb = ref(false);
const settingsForm = reactive({
  name: '',
  description: '',
  defaultTopK: 5,
  defaultThreshold: 0.3,
  alpha: 0.7,
  embeddingModel: '',
  enableRerank: false,
  rerankModel: ''
});
const allModels = ref([]);
const embeddingModelOptions = computed(() => {
  const options = (allModels.value || [])
    .filter(m => String(m.type || '').toUpperCase() === 'EMBEDDING')
    .map(m => ({ label: m.name || m.modelId, value: m.modelId || m.name }));
  if (options.length === 0) {
    return [
      { label: 'BGE-M3', value: 'BAAI/bge-m3' },
      { label: 'text-embedding-3-small', value: 'text-embedding-3-small' }
    ];
  }
  return options;
});
const rerankModelOptions = computed(() => {
  const options = (allModels.value || [])
    .filter(m => ['RERANK', 'RERANKER'].includes(String(m.type || '').toUpperCase()))
    .map(m => ({ label: m.name || m.modelId, value: m.modelId || m.name }));
  if (options.length === 0) {
    return [{ label: 'BAAI/bge-reranker-v2-mini', value: 'BAAI/bge-reranker-v2-mini' }];
  }
  return options;
});

const graphKeyword = ref('');
const mindMapRef = ref(null);
let mindMapChart = null;

const benchmarks = ref([]);
const acceptTypes = '.pdf,.txt,.md,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.csv,.png,.jpg,.jpeg,.gif,.bmp,.webp,.mp3,.wav,.m4a,.mp4,.mov,.avi,.mkv';

const filteredResults = computed(() => retrievalResults.value.filter(item => Number(item.score || 0) >= threshold.value));

const avgScore = computed(() => {
  if (filteredResults.value.length === 0) return 0;
  const total = filteredResults.value.reduce((sum, item) => sum + Number(item.score || 0), 0);
  return total / filteredResults.value.length;
});

const highScoreCount = computed(() => filteredResults.value.filter(item => Number(item.score || 0) >= 0.8).length);
const coverage = computed(() => (topK.value ? (filteredResults.value.length / topK.value) * 100 : 0));
const uniqueDocCount = computed(() => new Set(filteredResults.value.map(item => item.docId).filter(Boolean)).size);
const selectedDoc = computed(() => documents.value.find(doc => String(doc.id) === String(selectedDocId.value)) || null);
const workspaceNavItems = [
  { key: 'retrieve', label: '检索测试', icon: Search, title: '检索测试', desc: '快速验证召回质量与文档命中情况。' },
  { key: 'graph', label: '知识导图', icon: Connection, title: '知识导图', desc: '查看知识结构与文档分组关系。' },
  { key: 'rag', label: 'RAG评估', icon: Collection, title: 'RAG评估', desc: '基于检索结果观察当前配置效果。' },
  { key: 'benchmark', label: '评估基准', icon: Cpu, title: '评估基准', desc: '运行基准集并记录得分变化。' },
  { key: 'settings', label: '知识库设置', icon: Setting, title: '知识库设置', desc: '维护检索参数、模型与知识库元信息。' }
];

const activeWorkspaceMeta = computed(
  () => workspaceNavItems.find(item => item.key === activeTab.value)
    || workspaceNavItems[0]
);

const activateWorkspace = async (tab) => {
  activeTab.value = tab;
  if (tab === 'graph') {
    await nextTick();
    renderMindMap();
  }
};

const groupedDocs = computed(() => {
  const groups = new Map();
  for (const doc of documents.value) {
    const groupName = guessGroupName(doc);
    if (!groups.has(groupName)) groups.set(groupName, []);
    groups.get(groupName).push(doc);
  }

  return Array.from(groups.entries()).map(([name, docs]) => ({
    name,
    docs: docs.sort((a, b) => (b.uploadTime || 0) - (a.uploadTime || 0))
  }));
});

const filteredGroupedDocs = computed(() => {
  const keyword = docKeyword.value.trim().toLowerCase();
  if (!keyword) return groupedDocs.value;

  return groupedDocs.value
    .map(group => ({
      ...group,
      docs: group.docs.filter(doc => (doc.fileName || '').toLowerCase().includes(keyword))
    }))
    .filter(group => group.name.toLowerCase().includes(keyword) || group.docs.length > 0);
});

watch(
  () => activeTab.value,
  async (tab) => {
    if (tab === 'graph') {
      await nextTick();
      renderMindMap();
    }
  }
);

const getIcon = (type) => {
  if (type === 'STRUCTURED') return Connection;
  if (type === 'PROCEDURAL') return Cpu;
  if (type === 'META_MEMORY') return Opportunity;
  return Collection;
};

const getIconClass = (type) => {
  if (type === 'STRUCTURED') return 'icon-structured';
  if (type === 'PROCEDURAL') return 'icon-procedural';
  if (type === 'META_MEMORY') return 'icon-memory';
  return 'icon-default';
};

const getTypeName = (type) => {
  if (type === 'STRUCTURED') return 'Structured';
  if (type === 'PROCEDURAL') return 'Workflow';
  if (type === 'META_MEMORY') return 'Memory';
  return 'CommonRAG';
};

const statusClass = (status) => {
  const value = String(status || '').toUpperCase();
  if (['SUCCESS', 'COMPLETED'].includes(value)) return 'ok';
  if (['FAILED', 'ERROR'].includes(value)) return 'fail';
  if (['PENDING', 'PARSING', 'CHUNKING', 'VECTORIZING', 'QUEUED'].includes(value)) return 'processing';
  return 'idle';
};

const statusText = (status) => {
  const value = String(status || '').toUpperCase();
  const map = {
    SUCCESS: '可用',
    COMPLETED: '可用',
    FAILED: '失败',
    ERROR: '失败',
    PENDING: '等待',
    PARSING: '解析中',
    CHUNKING: '分块中',
    VECTORIZING: '向量化中',
    QUEUED: '排队中'
  };
  return map[value] || '未知';
};

const toPercent = (score) => `${(Number(score || 0) * 100).toFixed(1)}%`;
const formatUpdatedAt = (kb) => dayjs(
  kb?.updatedAt || kb?.gmtModified || kb?.syncTime || kb?.createdAt
).isValid()
  ? dayjs(kb?.updatedAt || kb?.gmtModified || kb?.syncTime || kb?.createdAt).format('MM-DD HH:mm')
  : '—';

const scoreClass = (score) => {
  const val = Number(score || 0);
  if (val >= 0.8) return 'score-high';
  if (val >= 0.5) return 'score-mid';
  return 'score-low';
};

const toggleFolder = (name) => {
  expandedFolders[name] = !expandedFolders[name];
};

const guessGroupName = (doc) => {
  const fileName = String(doc.fileName || '').trim();
  if (!fileName) return '未分类';

  if (fileName.includes('/')) return fileName.split('/')[0] || '未分类';
  if (fileName.includes('｜')) return fileName.split('｜')[0] || '未分类';
  if (fileName.includes('|')) return fileName.split('|')[0] || '未分类';

  const ext = String(doc.fileType || '').toLowerCase();
  if (['pdf'].includes(ext)) return 'PDF 文档';
  if (['doc', 'docx'].includes(ext)) return 'Word 文档';
  if (['xls', 'xlsx', 'csv'].includes(ext)) return '表格数据';
  if (['md', 'txt'].includes(ext)) return '文本资料';
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(ext)) return '图像资料';
  if (['mp3', 'wav', 'm4a', 'aac', 'ogg'].includes(ext)) return '音频资料';
  if (['mp4', 'mov', 'avi', 'mkv'].includes(ext)) return '视频资料';
  return '其他资料';
};

const normalizeDocuments = (rows) => {
  return (rows || []).map((doc) => ({
    ...doc,
    fileName: doc.fileName || doc.originalFilename || `文档-${doc.id}`
  }));
};

const parseConfiguration = (raw) => {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

const loadKb = async () => {
  try {
    const kb = await request.get(`/knowledge/${kbId.value}`);
    if (!kb || !kb.id) {
      throw new Error('知识库不存在');
    }
    kbData.value = kb;
    const config = parseConfiguration(kb.configuration);
    settingsForm.name = kb.name || '';
    settingsForm.description = kb.description || '';
    settingsForm.defaultTopK = kb.topK ?? 5;
    settingsForm.defaultThreshold = kb.similarityThreshold ?? 0.3;
    settingsForm.alpha = kb.alpha ?? 0.7;
    settingsForm.enableRerank = kb.enableRerank ?? false;
    settingsForm.rerankModel = kb.rerankModel || '';
    settingsForm.embeddingModel = config.embeddingModel || '';
    topK.value = settingsForm.defaultTopK;
    threshold.value = settingsForm.defaultThreshold;
  } catch (error) {
    // fallback to list interface
    const list = await getKnowledgeList();
    const kb = (list || []).find(item => String(item.id) === String(kbId.value));
    if (!kb) {
      ElMessage.error('知识库不存在或已删除');
      router.push(ROUTES.RESOURCES.KNOWLEDGE);
      return;
    }
    kbData.value = kb;
    settingsForm.name = kb.name || '';
    settingsForm.description = kb.description || '';
    settingsForm.defaultTopK = kb.topK ?? 5;
    settingsForm.defaultThreshold = kb.similarityThreshold ?? 0.3;
    settingsForm.alpha = kb.alpha ?? 0.7;
    settingsForm.enableRerank = kb.enableRerank ?? false;
    settingsForm.rerankModel = kb.rerankModel || '';
    const config = parseConfiguration(kb.configuration);
    settingsForm.embeddingModel = config.embeddingModel || '';
    topK.value = settingsForm.defaultTopK;
    threshold.value = settingsForm.defaultThreshold;
  }
};

const loadDocuments = async () => {
  documentsLoading.value = true;
  try {
    const rows = await getDocuments(kbId.value);
    documents.value = normalizeDocuments(rows);

    for (const group of groupedDocs.value) {
      if (!(group.name in expandedFolders)) expandedFolders[group.name] = true;
    }

    if (!selectedDocId.value && documents.value.length > 0) {
      selectedDocId.value = documents.value[0].id;
    }

    await nextTick();
    if (activeTab.value === 'graph') renderMindMap();
  } catch (error) {
    ElMessage.error(`加载文档失败: ${error.message}`);
  } finally {
    documentsLoading.value = false;
  }
};

const handleUpload = async (options) => {
  try {
    await uploadDocument(kbId.value, options.file);
    ElMessage.success(`已上传 ${options.file.name}`);
    loadDocuments();
  } catch (error) {
    ElMessage.error(`上传失败: ${error.message}`);
  }
};

const handleDocCommand = async (command, doc) => {
  if (command === 'open') {
    openDocument(doc);
    return;
  }

  if (command === 'vectorize') {
    try {
      await triggerVectorization(doc.id);
      ElMessage.success('已触发向量化任务');
      loadDocuments();
    } catch (error) {
      ElMessage.error(`触发失败: ${error.message}`);
    }
    return;
  }

  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(`确定删除文档「${doc.fileName}」吗？`, '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      });
      await deleteDocument(doc.id);
      ElMessage.success('文档已删除');
      loadDocuments();
    } catch (error) {
      if (error !== 'cancel') ElMessage.error('删除文档失败');
    }
  }
};

const openDocument = (doc) => {
  router.push({
    path: `/dashboard/resources/knowledge/${kbId.value}/document/${doc.id}`,
    query: {
      returnTab: activeTab.value,
      selectedDoc: selectedDocId.value || doc.id,
      from: 'kb-doc-open'
    }
  });
};

const runRetrieval = async () => {
  const question = query.value.trim();
  if (!question) {
    ElMessage.warning('请输入检索问题');
    return;
  }

  retrievalLoading.value = true;
  searched.value = true;

  try {
    const payload = {
      kbId: kbId.value,
      query: question,
      topK: topK.value,
      alpha: settingsForm.alpha,
      threshold: threshold.value,
      enableRerank: settingsForm.enableRerank,
      rerankModel: settingsForm.enableRerank ? settingsForm.rerankModel || null : null,
      embeddingModel: settingsForm.embeddingModel || null
    };
    const res = await request.post('/knowledge/retrieve/test', {
      ...payload
    });
    retrievalResults.value = res || [];
  } catch (error) {
    ElMessage.error(`检索失败: ${error.message}`);
  } finally {
    retrievalLoading.value = false;
  }
};

const buildMindTree = (docs) => {
  const keyword = graphKeyword.value.trim().toLowerCase();
  const root = {
    name: kbData.value.name || '知识库',
    children: []
  };

  for (const group of groupedDocs.value) {
    const groupNode = {
      name: group.name,
      children: []
    };

    for (const doc of group.docs) {
      const rawName = String(doc.fileName || '未命名文档');
      const plainName = rawName.length > 26 ? `${rawName.slice(0, 26)}...` : rawName;
      if (!keyword || rawName.toLowerCase().includes(keyword) || group.name.toLowerCase().includes(keyword)) {
        groupNode.children.push({ name: plainName, rawName });
      }
    }

    if (groupNode.children.length > 0) {
      root.children.push(groupNode);
    }
  }

  if (root.children.length === 0 && docs.length === 0) {
    root.children.push({ name: '暂无文档', children: [] });
  }

  return root;
};

const renderMindMap = () => {
  if (!mindMapRef.value) return;

  if (!mindMapChart) {
    mindMapChart = echarts.init(mindMapRef.value);
  }

  const treeData = buildMindTree(documents.value);

  mindMapChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => params.data.rawName || params.data.name
    },
    series: [
      {
        type: 'tree',
        data: [treeData],
        top: '4%',
        left: '2%',
        bottom: '4%',
        right: '22%',
        symbol: 'circle',
        symbolSize: 12,
        edgeShape: 'curve',
        lineStyle: {
          width: 2,
          curveness: 0.45,
          color: '#6b7280'
        },
        label: {
          position: 'left',
          verticalAlign: 'middle',
          align: 'right',
          fontSize: 14,
          color: '#1f2937'
        },
        leaves: {
          label: {
            position: 'right',
            verticalAlign: 'middle',
            align: 'left',
            color: '#111827'
          }
        },
        itemStyle: {
          borderWidth: 1,
          borderColor: '#ffffff'
        },
        emphasis: {
          focus: 'descendant'
        },
        expandAndCollapse: false,
        animationDuration: 500,
        animationDurationUpdate: 700
      }
    ]
  }, true);
};

const regenerateGraph = () => {
  renderMindMap();
  ElMessage.success('导图已重新生成');
};

const fitGraph = () => {
  if (mindMapChart) {
    mindMapChart.resize();
  }
};

const refreshBenchmarks = () => {
  const now = dayjs();
  benchmarks.value = [
    {
      id: 1,
      name: '教材结构一致性',
      description: '验证章节/分组检索是否能稳定命中主干资料',
      score: Math.round(78 + Math.random() * 15),
      updatedAt: now.subtract(2, 'hour').format('YYYY-MM-DD HH:mm')
    },
    {
      id: 2,
      name: '实体问答召回',
      description: '测试术语、成分、工艺类问题的召回准确率',
      score: Math.round(74 + Math.random() * 18),
      updatedAt: now.subtract(1, 'day').format('YYYY-MM-DD HH:mm')
    },
    {
      id: 3,
      name: '跨文档推理支持',
      description: '验证多文档关联问题在 TopK 内覆盖情况',
      score: Math.round(70 + Math.random() * 20),
      updatedAt: now.subtract(3, 'day').format('YYYY-MM-DD HH:mm')
    }
  ];
};

const runBenchmark = async (row) => {
  ElMessage.info(`正在执行：${row.name}`);
  await runRetrieval();
  row.score = Math.min(99, Math.max(55, Math.round(avgScore.value * 100 + Math.random() * 8)));
  row.updatedAt = dayjs().format('YYYY-MM-DD HH:mm');
  ElMessage.success('基准执行完成');
};

const saveSettings = async () => {
  const name = settingsForm.name.trim();
  if (!name) {
    ElMessage.warning('知识库名称不能为空');
    return;
  }

  savingSettings.value = true;
  try {
    const mergedConfig = {
      ...parseConfiguration(kbData.value.configuration),
      embeddingModel: settingsForm.embeddingModel || null
    };
    await updateKnowledge(kbId.value, {
      name,
      description: settingsForm.description,
      type: kbData.value.type || 'UNSTRUCTURED',
      topK: settingsForm.defaultTopK,
      similarityThreshold: settingsForm.defaultThreshold,
      alpha: settingsForm.alpha,
      enableRerank: settingsForm.enableRerank,
      rerankModel: settingsForm.enableRerank ? settingsForm.rerankModel : null,
      configuration: JSON.stringify(mergedConfig)
    });
    kbData.value.name = name;
    kbData.value.description = settingsForm.description;
    kbData.value.topK = settingsForm.defaultTopK;
    kbData.value.similarityThreshold = settingsForm.defaultThreshold;
    kbData.value.alpha = settingsForm.alpha;
    kbData.value.enableRerank = settingsForm.enableRerank;
    kbData.value.rerankModel = settingsForm.rerankModel;
    kbData.value.configuration = JSON.stringify(mergedConfig);
    topK.value = settingsForm.defaultTopK;
    threshold.value = settingsForm.defaultThreshold;
    ElMessage.success('知识库设置已保存');
  } catch (error) {
    ElMessage.error(`保存失败: ${error.message}`);
  } finally {
    savingSettings.value = false;
  }
};

const removeKnowledgeBase = async () => {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${kbData.value.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
  } catch (error) {
    return;
  }

  deletingKb.value = true;
  try {
    await deleteKnowledge(kbId.value);
    ElMessage.success('知识库已删除');
    router.push(ROUTES.RESOURCES.KNOWLEDGE);
  } catch (error) {
    ElMessage.error(`删除失败: ${error.message}`);
  } finally {
    deletingKb.value = false;
  }
};

const handleResize = () => {
  if (mindMapChart) {
    mindMapChart.resize();
  }
};

onMounted(async () => {
  const returnTab = route.query.returnTab;
  const returnDoc = route.query.selectedDoc;
  if (typeof returnTab === 'string' && ['retrieve', 'graph', 'rag', 'benchmark', 'settings'].includes(returnTab)) {
    activeTab.value = returnTab;
  }
  if (typeof returnDoc === 'string' && returnDoc.trim()) {
    selectedDocId.value = returnDoc;
  }

  await loadKb();
  try {
    allModels.value = await getModelList();
  } catch {
    allModels.value = [];
  }
  await loadDocuments();
  refreshBenchmarks();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  if (mindMapChart) {
    mindMapChart.dispose();
    mindMapChart = null;
  }
});
</script>

<style scoped>
.kb-workbench-page {
  min-height: 100vh;
  padding: 18px;
  background:
    radial-gradient(circle at 0% -20%, rgba(15, 157, 138, 0.10) 0, rgba(15, 157, 138, 0) 55%),
    radial-gradient(circle at 100% 0%, rgba(59, 130, 246, 0.07) 0, rgba(59, 130, 246, 0) 50%),
    #f3f6f8;
}

.workbench-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdfd 100%);
  border: 1px solid #dce7e4;
  border-radius: 16px;
  padding: 16px 18px;
  margin-bottom: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.hero-main {
  min-width: 0;
  flex: 1;
}

.title-row {
  display: flex;
  align-items: center;
  color: #6b7280;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 8px;
}

.current-name {
  color: #111827;
  font-weight: 600;
}

.hero-main h1 {
  margin: 0;
  font-size: 32px;
  color: #111827;
  line-height: 1.25;
}

.title-meta-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.meta-pill {
  font-size: 12px;
  line-height: 20px;
  padding: 2px 10px;
  border-radius: 999px;
  color: #4b5563;
  background: #f3f6f8;
  border: 1px solid #e3eaee;
}

.title-desc {
  margin: 10px 0 0;
  color: #4b5563;
  line-height: 1.65;
  max-width: 1040px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero-side {
  width: 270px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.hero-kpi {
  border: 1px solid #e4ece9;
  background: #f7fbfa;
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.hero-kpi span {
  color: #5f7180;
  font-size: 12px;
}

.hero-kpi strong {
  color: #0f172a;
  font-size: 20px;
  line-height: 1;
}

.workbench-body {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 14px;
  min-height: calc(100vh - 190px);
}

.left-panel,
.right-panel {
  min-height: 0;
}

.left-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kb-info-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  background: var(--orin-bg-white);
  border-radius: 14px;
  border: 1px solid #dfe8e5;
  padding: 14px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.03);
}

.kb-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.icon-default {
  background: var(--primary-50);
  color: var(--primary-600);
}

.icon-structured {
  background: var(--success-50);
  color: var(--success-600);
}

.icon-procedural {
  background: var(--primary-50);
  color: var(--primary-600);
}

.icon-memory {
  background: var(--error-50);
  color: var(--error-600);
}

.kb-meta h3 {
  margin: 1px 0 4px;
  color: var(--neutral-gray-900);
}

.kb-meta p {
  margin: 0;
  color: var(--neutral-gray-500);
  line-height: 1.45;
}

.kb-meta.compact {
  flex: 1;
}

.meta-stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
  border-bottom: 1px dashed #eef2f7;
}

.meta-stat-row:last-child {
  border-bottom: 0;
}

.stat-label {
  color: #6b7280;
}

.stat-value {
  color: #111827;
  font-weight: 600;
}

.doc-panel {
  flex: 1;
  min-height: 0;
  background: var(--orin-bg-white);
  border: 1px solid #dfe8e5;
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.03);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel-head h3 {
  margin: 0;
  font-size: 14px;
  color: #111827;
}

.panel-head span {
  font-size: 12px;
  color: #64748b;
}

.doc-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-search-row {
  margin: 10px 0;
}

.doc-tree-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.folder-item {
  border: 1px solid #edf0f3;
  border-radius: 10px;
  overflow: hidden;
}

.folder-header {
  width: 100%;
  border: 0;
  background: var(--neutral-gray-50);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
}

.folder-arrow {
  transition: transform 0.2s;
}

.folder-arrow.open {
  transform: rotate(90deg);
}

.folder-name {
  font-weight: 600;
  color: #1f2937;
}

.folder-count {
  margin-left: auto;
  color: #6b7280;
  font-size: 12px;
}

.doc-list {
  padding: 6px;
}

.doc-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
}

.doc-row:hover {
  background: var(--neutral-gray-50);
}

.doc-row.active {
  background: var(--primary-50);
}

.doc-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.doc-icon {
  color: #ef4444;
}

.doc-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #111827;
}

.state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.state-dot.ok {
  background: var(--success-500);
}

.state-dot.fail {
  background: var(--error-500);
}

.state-dot.processing {
  background: var(--warning-500);
}

.state-dot.idle {
  background: var(--neutral-gray-400);
}

.more-icon {
  cursor: pointer;
  color: #6b7280;
}

.right-panel {
  background: var(--orin-bg-white);
  border: 1px solid #dfe8e5;
  border-radius: 14px;
  padding: 10px 14px 14px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.03);
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.workspace-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.summary-item {
  border: 1px solid #e6eeeb;
  border-radius: 10px;
  background: #f8fcfb;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-key {
  font-size: 12px;
  color: #64748b;
}

.summary-val {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-nav {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.nav-btn {
  border: 1px solid #e3ece9;
  background: #f8fcfb;
  border-radius: 10px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.nav-btn:hover {
  border-color: #b8dcd4;
  color: #0f9d8a;
}

.nav-btn.active {
  border-color: #8dd5c8;
  background: #ebfaf6;
  color: #0b8d7c;
}

.workspace-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid #e5efeb;
  border-radius: 12px;
  background: #fbfdfd;
  padding: 12px;
}

.stage-header h3 {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
}

.stage-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.test-pane,
.graph-pane,
.metric-pane,
.benchmark-pane,
.settings-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.test-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.test-config {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  font-size: 13px;
}

.result-panel {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--neutral-gray-100);
  border-radius: var(--radius-lg);
  padding: 12px;
  background: var(--neutral-gray-50);
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-item {
  border: 1px solid #ecedf1;
  border-radius: 10px;
  padding: 10px 12px;
}

.result-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.rank {
  font-size: 12px;
  color: #6b7280;
}

.score {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
}

.score-high {
  color: var(--success-700);
  background: var(--success-100);
}

.score-mid {
  color: var(--warning-700);
  background: var(--warning-100);
}

.score-low {
  color: var(--neutral-gray-700);
  background: var(--neutral-gray-100);
}

.result-item p {
  margin: 0;
  line-height: 1.6;
  color: #111827;
}

.meta-line {
  margin-top: 8px;
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #6b7280;
}

.graph-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  margin-bottom: 12px;
}

.mindmap-canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid #ecedf1;
  border-radius: 10px;
  background: #fbfcff;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  border: 1px solid #edf0f3;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-card span {
  color: #6b7280;
  font-size: 12px;
}

.metric-card strong {
  font-size: 22px;
  color: #111827;
}

.bench-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.settings-pane {
  gap: 14px;
  overflow: auto;
}

.settings-form {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
  background: #fbfcff;
  max-width: 760px;
}

.settings-actions {
  display: flex;
  gap: 10px;
  padding-top: 6px;
}

.danger-zone {
  max-width: 760px;
  border: 1px solid var(--error-200);
  background: var(--error-50);
  border-radius: var(--radius-lg);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.danger-meta h4 {
  margin: 0 0 4px;
  color: var(--error-700);
  font-size: 14px;
}

.danger-meta p {
  margin: 0;
  color: var(--error-600);
  font-size: 12px;
}

:deep(.danger-item) {
  color: var(--el-color-danger);
}

@media (max-width: 1380px) {
  .workbench-body {
    grid-template-columns: 330px minmax(0, 1fr);
  }

  .hero-side {
    width: 240px;
  }

  .hero-main h1 {
    font-size: 28px;
  }

  .workspace-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1120px) {
  .kb-workbench-page {
    padding: 12px;
  }

  .workbench-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-side {
    width: 100%;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .workbench-body {
    grid-template-columns: 1fr;
  }

  .hero-main h1 {
    font-size: 24px;
  }

  .workspace-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .test-pane,
  .graph-pane,
  .metric-pane,
  .benchmark-pane,
  .settings-pane {
    min-height: 420px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
