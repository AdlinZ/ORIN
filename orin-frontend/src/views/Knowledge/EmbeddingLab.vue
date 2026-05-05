<template>
  <div class="retrieval-workbench">
    <aside class="retrieval-sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">
      <button class="sidebar-toggle" type="button" @click="toggleSidebar">
        <el-icon><component :is="sidebarCollapsed ? ArrowRight : ArrowLeft" /></el-icon>
      </button>

      <template v-if="!sidebarCollapsed">
        <div class="sidebar-head">
          <div>
            <h2>检索工作台</h2>
            <p>选择知识库、调整召回策略并复盘历史查询。</p>
          </div>
          <el-button :icon="Plus" type="primary" @click="newRetrieval">新建</el-button>
        </div>

        <div class="sidebar-tabs">
          <button
            type="button"
            :class="{ active: sidebarTab === 'history' }"
            @click="sidebarTab = 'history'"
          >
            历史
          </button>
          <button
            type="button"
            :class="{ active: sidebarTab === 'params' }"
            @click="sidebarTab = 'params'"
          >
            参数
          </button>
        </div>

        <section class="kb-panel">
          <label>知识库范围</label>
          <el-select
            v-model="selectedKbId"
            placeholder="选择知识库"
            filterable
            :loading="knowledgeLoading"
            @visible-change="handleKbDropdownVisible"
          >
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
          <div class="kb-meta">
            <span>{{ activeKbName }}</span>
            <span>{{ knowledgeBases.length }} 个可用范围</span>
          </div>
        </section>

        <section v-if="sidebarTab === 'history'" class="history-panel">
          <div class="panel-title-row">
            <span>最近查询</span>
            <el-button link size="small" :icon="Search" @click="focusSearch">聚焦搜索</el-button>
          </div>

          <div class="history-list">
            <button
              v-for="item in historyList"
              :key="item.id"
              type="button"
              :class="['history-item', { active: activeHistoryId === item.id }]"
              @click="loadHistory(item)"
            >
              <span class="history-query">{{ item.query }}</span>
              <span class="history-meta">
                {{ item.kbName }} · {{ item.resultCount }} 条 · {{ formatTime(item.timestamp) }}
              </span>
              <el-button
                link
                class="history-delete"
                :icon="Delete"
                @click.stop="removeHistory(item.id)"
              />
            </button>
            <el-empty
              v-if="historyList.length === 0"
              :image-size="54"
              description="暂无检索历史"
            />
          </div>
        </section>

        <section v-else class="params-panel">
          <el-form label-position="top" size="small">
            <el-form-item label="Embedding 模型">
              <el-select v-model="config.embeddingModel" placeholder="使用知识库默认模型" clearable>
                <el-option
                  v-for="model in embeddingModels"
                  :key="model.modelId"
                  :label="model.name"
                  :value="model.modelId"
                />
              </el-select>
            </el-form-item>

            <div class="param-block">
              <div class="param-label">
                <span>Top-K 召回数</span>
                <strong>{{ config.topK }}</strong>
              </div>
              <el-slider v-model="config.topK" :min="1" :max="30" />
            </div>

            <div class="param-block">
              <div class="param-label">
                <span>相似度阈值</span>
                <strong>{{ config.threshold.toFixed(2) }}</strong>
              </div>
              <el-slider v-model="config.threshold" :min="0" :max="1" :step="0.05" />
            </div>

            <div class="param-block">
              <div class="param-label">
                <span>语义权重 Alpha</span>
                <strong>{{ config.alpha.toFixed(1) }}</strong>
              </div>
              <p>{{ vectorWeight }} 向量 / {{ keywordWeight }} 关键词</p>
              <el-slider v-model="config.alpha" :min="0" :max="1" :step="0.1" />
            </div>

            <el-form-item label="Rerank 模型">
              <el-select v-model="config.rerankModel">
                <el-option label="不使用 Rerank" value="none" />
                <el-option
                  v-for="model in rerankModels"
                  :key="model.modelId"
                  :label="model.name"
                  :value="model.modelId"
                />
                <el-option
                  v-if="rerankModels.length === 0"
                  label="暂无可用 Rerank 模型"
                  value="none"
                  disabled
                />
              </el-select>
            </el-form-item>

            <el-form-item label="目标 Agent">
              <el-select
                v-model="selectedAgentId"
                placeholder="选择要应用配置的 Agent"
                filterable
              >
                <el-option
                  v-for="agent in agents"
                  :key="agent.agentId"
                  :label="agent.name || agent.agentId"
                  :value="agent.agentId"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <el-button
            :icon="Setting"
            type="primary"
            :loading="savingAgentConfig"
            :disabled="!selectedAgentId"
            @click="applyToAgent"
          >
            保存到 Agent
          </el-button>
        </section>
      </template>

      <div v-else class="collapsed-actions">
        <el-button circle :icon="Plus" type="primary" @click="newRetrieval" />
        <el-button circle :icon="Search" @click="focusSearch" />
        <el-button circle :icon="Setting" @click="expandParams" />
      </div>
    </aside>

    <main class="retrieval-main">
      <header class="hero-band">
        <div class="hero-copy">
          <span class="eyebrow">Knowledge Retrieval</span>
          <h1>知识库检索</h1>
          <p>用同一套检索参数验证召回质量、来源覆盖和片段可用性，便于调试 Agent 的 RAG 上下文。</p>
        </div>
        <div class="hero-metrics">
          <div v-for="metric in summaryMetrics" :key="metric.label" class="metric-cell">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.meta }}</small>
          </div>
        </div>
      </header>

      <section v-if="retrievalNotice" :class="['retrieval-notice', retrievalNotice.type]">
        <el-icon><WarningFilled /></el-icon>
        <div>
          <strong>{{ retrievalNotice.title }}</strong>
          <p>{{ retrievalNotice.message }}</p>
        </div>
      </section>

      <section class="query-console">
        <div class="query-context">
          <span>{{ activeKbName }}</span>
          <span>Top {{ config.topK }}</span>
          <span>Threshold {{ config.threshold.toFixed(2) }}</span>
          <span>{{ rerankStatus }}</span>
        </div>
        <el-input
          ref="searchInputRef"
          v-model="query"
          class="query-input"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 7 }"
          placeholder="输入业务问题、关键词或一段需要匹配的描述，按 Ctrl + Enter 开始检索。"
          @keydown.ctrl.enter.prevent="handleSearch"
        />
        <div class="query-actions">
          <div class="example-row">
            <button
              v-for="example in queryExamples"
              :key="example"
              type="button"
              @click="useExample(example)"
            >
              {{ example }}
            </button>
          </div>
          <el-button
            type="primary"
            :icon="Search"
            :loading="loading"
            @click="handleSearch"
          >
            开始检索
          </el-button>
        </div>
      </section>

      <div :class="['content-grid', { 'is-empty': !hasSearched }]">
        <section v-loading="loading" class="results-pane">
          <div class="section-head">
            <div>
              <h2>召回结果</h2>
              <p>{{ resultSubtitle }}</p>
            </div>
            <el-button :icon="Refresh" :disabled="!query.trim()" @click="handleSearch">
              重新检索
            </el-button>
          </div>

          <div v-if="hasSearched && retrievalMeta.fallback" class="fallback-banner">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ retrievalMeta.fallbackReason || '本次检索已降级为文本索引/关键词兜底。' }}</span>
          </div>

          <div v-if="hasSearched && sortedResults.length > 0" class="result-list">
            <article
              v-for="(item, index) in sortedResults"
              :key="`${item.chunkIndex}-${index}`"
              class="result-card"
              @click="openTrace(item)"
            >
              <div class="rank-column">
                <span>#{{ index + 1 }}</span>
                <strong :style="{ color: getScoreColor(item.score) }">
                  {{ formatPercent(item.score) }}
                </strong>
              </div>
              <div class="result-body">
                <div class="result-topline">
                  <div class="doc-title">
                    <el-icon><Document /></el-icon>
                    <span>{{ item.sourceDoc }}</span>
                  </div>
                  <el-tag :type="matchTagType(item.matchType)" size="small" effect="plain">
                    {{ matchLabel(item.matchType) }}
                  </el-tag>
                </div>
                <p
                  class="result-content"
                  style="display: block; max-height: none; overflow: visible; -webkit-line-clamp: unset; line-clamp: unset; -webkit-box-orient: initial;"
                >
                  {{ item.content }}
                </p>
                <div class="result-footer">
                  <span>Chunk {{ item.chunkIndex }}</span>
                  <span>相关度 {{ formatPercent(item.score) }}</span>
                </div>
              </div>
            </article>
          </div>

          <el-empty
            v-else-if="hasSearched"
            :image-size="92"
            description="未找到达到阈值的内容，尝试降低阈值或改写查询。"
          />

          <div v-else class="empty-guide">
            <el-icon><Search /></el-icon>
            <h3>还没有检索结果</h3>
            <p>先选择知识库范围，再输入问题。结果会按照相关度排序，并保留到左侧历史中。</p>
          </div>
        </section>

        <aside class="insight-pane">
          <section class="quality-panel">
            <div class="section-head compact">
              <div>
                <h2>质量概览</h2>
                <p>基于当前结果实时计算</p>
              </div>
            </div>
            <div class="quality-gauge">
              <div class="gauge-ring" :style="{ '--score': qualityScore }">
                <span>{{ qualityScore }}%</span>
              </div>
              <div>
                <strong>{{ qualityLabel }}</strong>
                <p>{{ qualityHint }}</p>
              </div>
            </div>
            <div class="quality-bars">
              <div v-for="item in qualityBreakdown" :key="item.label" class="quality-row">
                <span>{{ item.label }}</span>
                <div><i :style="{ width: item.width }" /></div>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section class="strategy-panel">
            <div class="section-head compact">
              <div>
                <h2>检索策略</h2>
                <p>本次请求参数</p>
              </div>
            </div>
            <dl>
              <div>
                <dt>知识库</dt>
                <dd>{{ activeKbName }}</dd>
              </div>
              <div>
                <dt>召回数</dt>
                <dd>Top {{ config.topK }}</dd>
              </div>
              <div>
                <dt>过滤阈值</dt>
                <dd>{{ config.threshold.toFixed(2) }}</dd>
              </div>
              <div>
                <dt>混合检索</dt>
                <dd>{{ vectorWeight }} / {{ keywordWeight }}</dd>
              </div>
              <div>
                <dt>Rerank</dt>
                <dd>{{ rerankStatus }}</dd>
              </div>
              <div>
                <dt>向量库</dt>
                <dd>{{ vectorStatusLabel }}</dd>
              </div>
              <div>
                <dt>召回模式</dt>
                <dd>{{ retrievalModeLabel }}</dd>
              </div>
              <div>
                <dt>结果来源</dt>
                <dd>{{ sourceBreakdown }}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
    </main>

    <el-drawer v-model="drawerVisible" title="片段详情" direction="rtl" size="420px">
      <div v-if="selectedResult" class="trace-panel">
        <div class="trace-title">
          <el-icon><Document /></el-icon>
          <span>{{ selectedResult.sourceDoc }}</span>
        </div>
        <div class="trace-stats">
          <div>
            <span>相关度</span>
            <strong :style="{ color: getScoreColor(selectedResult.score) }">
              {{ formatPercent(selectedResult.score) }}
            </strong>
          </div>
          <div>
            <span>匹配方式</span>
            <strong>{{ matchLabel(selectedResult.matchType) }}</strong>
          </div>
        </div>
        <dl class="trace-meta">
          <div>
            <dt>Chunk ID</dt>
            <dd>{{ selectedResult.chunkIndex }}</dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>{{ selectedResult.sourceDoc }}</dd>
          </div>
        </dl>
        <div class="trace-content">
          {{ selectedResult.content }}
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import {
  Search,
  Setting,
  Document,
  ArrowLeft,
  ArrowRight,
  Plus,
  Delete,
  Refresh,
  WarningFilled,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import { getAgentList, updateAgent } from '@/api/agent';
import { getAgentToolBinding, saveAgentToolBinding } from '@/api/agent-chat';

const HISTORY_KEY = 'orin-retrieval-history';
const MAX_HISTORY = 50;

const sidebarCollapsed = ref(false);
const sidebarTab = ref('history');
const selectedKbId = ref('');
const query = ref('');
const loading = ref(false);
const hasSearched = ref(false);
const executionTime = ref(0);
const results = ref([]);
const knowledgeBases = ref([]);
const knowledgeLoading = ref(false);
const initialDataLoading = ref(false);
const embeddingModels = ref([]);
const rerankModels = ref([]);
const agents = ref([]);
const selectedAgentId = ref('');
const savingAgentConfig = ref(false);
const vectorStatus = ref({ healthy: null, connection: 'UNKNOWN' });
const retrievalMeta = reactive({
  retrievalMode: 'IDLE',
  vectorHealthy: null,
  vectorConnection: 'UNKNOWN',
  fallback: false,
  fallbackReason: '',
  kbVectorStats: {},
});
const drawerVisible = ref(false);
const selectedResult = ref(null);
const searchInputRef = ref(null);
const activeHistoryId = ref(null);
const historyList = ref([]);

const config = reactive({
  topK: 8,
  threshold: 0.3,
  alpha: 0.7,
  rerankModel: 'none',
  embeddingModel: '',
});

const queryExamples = [
  '如何接入 Dify？',
  '知识库同步失败怎么排查？',
  'Agent 使用检索上下文的流程',
];

const sortedResults = computed(() => [...results.value].sort((a, b) => b.score - a.score));
const activeKbName = computed(() => knowledgeBases.value.find(kb => kb.id === selectedKbId.value)?.name || '未选择知识库');
const vectorWeight = computed(() => `${Math.round(config.alpha * 100)}%`);
const keywordWeight = computed(() => `${Math.round((1 - config.alpha) * 100)}%`);
const rerankStatus = computed(() => config.rerankModel && config.rerankModel !== 'none' ? 'Rerank 已启用' : '未使用 Rerank');
const vectorStatusLabel = computed(() => {
  const healthy = retrievalMeta.vectorHealthy ?? vectorStatus.value.healthy;
  if (healthy === true) return 'Milvus 正常';
  if (healthy === false) return 'Milvus 不可用';
  return '状态未知';
});
const retrievalModeLabel = computed(() => {
  const labels = {
    IDLE: '待检索',
    EMPTY: '无结果',
    VECTOR: '向量召回',
    HYBRID: '混合召回',
    KEYWORD_FALLBACK: '关键词兜底',
    TEXT_INDEX_FALLBACK: '文本索引兜底',
    TEXT_INDEX_KEYWORD_FALLBACK: '文本/关键词兜底',
    UNKNOWN: '未知',
  };
  return labels[retrievalMeta.retrievalMode] || retrievalMeta.retrievalMode || '未知';
});
const resultSourceCounts = computed(() => sortedResults.value.reduce((acc, item) => {
  const type = item.matchType || 'UNKNOWN';
  acc[type] = (acc[type] || 0) + 1;
  return acc;
}, {}));
const sourceBreakdown = computed(() => {
  if (!hasSearched.value) return '待检索';
  const entries = Object.entries(resultSourceCounts.value);
  if (entries.length === 0) return '无命中';
  return entries.map(([type, count]) => `${matchLabel(type)} ${count}`).join(' · ');
});
const retrievalNotice = computed(() => {
  if (retrievalMeta.fallback && hasSearched.value) {
    return {
      type: 'warning',
      title: '当前检索已降级',
      message: retrievalMeta.fallbackReason || '本次未命中向量召回，结果来自文本索引/关键词兜底。',
    };
  }
  if (vectorStatus.value.healthy === false) {
    return {
      type: 'danger',
      title: 'Milvus 向量服务不可用',
      message: `连接状态：${vectorStatus.value.connection || 'UNKNOWN'}。当前查询会退化为文本索引/关键词检索。`,
    };
  }
  if (hasKbVectorWarning.value) {
    return {
      type: 'warning',
      title: '知识库向量数据不完整',
      message: '部分知识库没有可用向量分区或向量数量为 0，语义召回可能无法生效。',
    };
  }
  return null;
});
const hasKbVectorWarning = computed(() => Object.values(retrievalMeta.kbVectorStats || {}).some(stat => {
  if (!stat || typeof stat !== 'object') return false;
  return stat.exists === false || Number(stat.vectorCount) === 0;
}));

const averageScore = computed(() => {
  if (sortedResults.value.length === 0) return 0;
  return sortedResults.value.reduce((sum, item) => sum + item.score, 0) / sortedResults.value.length;
});

const highScoreCount = computed(() => sortedResults.value.filter(item => item.score >= 0.8).length);
const mediumScoreCount = computed(() => sortedResults.value.filter(item => item.score >= 0.5 && item.score < 0.8).length);
const lowScoreCount = computed(() => sortedResults.value.filter(item => item.score < 0.5).length);
const coverage = computed(() => (config.topK ? Math.min(100, (sortedResults.value.length / config.topK) * 100) : 0));
const qualityScore = computed(() => Math.round((averageScore.value * 0.72 + (coverage.value / 100) * 0.28) * 100));

const qualityLabel = computed(() => {
  if (!hasSearched.value) return '等待检索';
  if (qualityScore.value >= 80) return '召回质量较高';
  if (qualityScore.value >= 55) return '结果可用，建议复核';
  return '召回偏弱';
});

const qualityHint = computed(() => {
  if (!hasSearched.value) return '运行一次查询后，这里会展示质量判断。';
  if (retrievalMeta.fallback) return '当前结果来自兜底检索，建议先检查 Milvus 连接和知识库向量数据。';
  if (qualityScore.value >= 80) return '高相关片段占比稳定，可作为 RAG 上下文继续验证。';
  if (sortedResults.value.length === 0) return '没有命中阈值，优先降低阈值或扩大 Top-K。';
  return '建议检查来源覆盖，并尝试启用 Rerank。';
});

const summaryMetrics = computed(() => [
  { label: '命中片段', value: sortedResults.value.length, meta: hasSearched.value ? `Top ${config.topK}` : '待检索' },
  { label: '平均相关度', value: formatPercent(averageScore.value), meta: `${highScoreCount.value} 条高相关` },
  { label: '执行耗时', value: hasSearched.value ? formatDuration(executionTime.value) : '-', meta: rerankStatus.value },
]);

const qualityBreakdown = computed(() => [
  { label: '高相关', value: highScoreCount.value, width: barWidth(highScoreCount.value) },
  { label: '中相关', value: mediumScoreCount.value, width: barWidth(mediumScoreCount.value) },
  { label: '低相关', value: lowScoreCount.value, width: barWidth(lowScoreCount.value) },
]);

const resultSubtitle = computed(() => {
  if (!hasSearched.value) return '输入问题后展示按相关度排序的知识片段。';
  if (sortedResults.value.length === 0) return `当前阈值 ${config.threshold.toFixed(2)} 下没有可展示结果。`;
  return `${activeKbName.value} · ${formatDuration(executionTime.value)} · 过滤阈值 ${config.threshold.toFixed(2)}`;
});

onMounted(async () => {
  loadHistoryFromStorage();
  await loadInitialData();
});

const loadInitialData = async () => {
  if (initialDataLoading.value) return;

  initialDataLoading.value = true;
  try {
    const [modelsRes, kbRes, vectorRes, agentsRes] = await Promise.allSettled([
      request.get('/models'),
      request.get('/knowledge/list'),
      request.get('/knowledge/vector/status'),
      getAgentList(),
    ]);

    const models = unwrapList(modelsRes.status === 'fulfilled' ? modelsRes.value : []);
    embeddingModels.value = models.filter(model => model.type?.toUpperCase() === 'EMBEDDING');
    rerankModels.value = models.filter(model => {
      const type = model.type?.toUpperCase();
      return type === 'RERANK' || type === 'RERANKER';
    });

    if (modelsRes.status === 'rejected') {
      console.warn('Failed to load retrieval models', modelsRes.reason);
    }

    if (kbRes.status === 'fulfilled') {
      applyKnowledgeBases(kbRes.value);
    } else {
      console.warn('Failed to load knowledge bases', kbRes.reason);
      await loadKnowledgeBases({ silent: true });
    }

    if (vectorRes.status === 'fulfilled') {
      vectorStatus.value = normalizeVectorStatus(vectorRes.value);
    } else {
      vectorStatus.value = normalizeVectorStatus({ healthy: false, connection: vectorRes.reason?.message });
    }

    if (!config.embeddingModel) {
      config.embeddingModel = embeddingModels.value[0]?.modelId || '';
    }

    if (agentsRes.status === 'fulfilled') {
      agents.value = unwrapList(agentsRes.value);
      selectedAgentId.value = selectedAgentId.value || agents.value[0]?.agentId || '';
    } else {
      console.warn('Failed to load agents for retrieval config', agentsRes.reason);
      agents.value = [];
      selectedAgentId.value = '';
    }
  } catch (error) {
    console.error('Knowledge retrieval init failed', error);
    ElMessage.error('检索工作台初始化失败');
  } finally {
    initialDataLoading.value = false;
  }
};

const unwrapResponse = (response) => {
  if (response && typeof response === 'object' && !Array.isArray(response)) {
    if (Array.isArray(response.data)) return response.data;
    if (response.data && typeof response.data === 'object') return response.data;
    if (Array.isArray(response.content)) return response.content;
    if (Array.isArray(response.records)) return response.records;
    if (Array.isArray(response.list)) return response.list;
    if (Array.isArray(response.rows)) return response.rows;
  }
  return response;
};

const unwrapList = (response) => {
  const unwrapped = unwrapResponse(response);
  if (Array.isArray(unwrapped)) return unwrapped;
  if (unwrapped && typeof unwrapped === 'object') {
    if (Array.isArray(unwrapped.content)) return unwrapped.content;
    if (Array.isArray(unwrapped.records)) return unwrapped.records;
    if (Array.isArray(unwrapped.list)) return unwrapped.list;
    if (Array.isArray(unwrapped.rows)) return unwrapped.rows;
  }
  return [];
};

const applyKnowledgeBases = (response) => {
  const kbs = unwrapList(response)
    .map(normalizeKb)
    .filter(kb => kb.id && (kb.status === 'ENABLED' || kb.status === undefined || kb.status === null));

  knowledgeBases.value = [{ id: 'all', name: '全局检索 (All KBs)', status: 'ENABLED' }, ...kbs];
  if (!selectedKbId.value || !knowledgeBases.value.some(kb => kb.id === selectedKbId.value)) {
    selectedKbId.value = knowledgeBases.value[0]?.id || '';
  }
};

const loadKnowledgeBases = async ({ silent = false } = {}) => {
  if (knowledgeLoading.value) return;

  knowledgeLoading.value = true;
  try {
    const response = await request.get('/knowledge/list');
    applyKnowledgeBases(response);
  } catch (error) {
    console.error('Failed to load knowledge bases', error);
    if (!silent) {
      ElMessage.error('知识库列表加载失败，请稍后重试');
    }
  } finally {
    knowledgeLoading.value = false;
  }
};

const handleKbDropdownVisible = (visible) => {
  if (visible && knowledgeBases.value.length === 0) {
    loadKnowledgeBases();
  }
};

const normalizeVectorStatus = (status) => ({
  healthy: status?.healthy ?? null,
  connection: status?.connection || status?.error || 'UNKNOWN',
  collection: status?.collection || null,
});

const normalizeKb = (kb) => ({
  id: kb.id || kb.kbId || kb.knowledgeBaseId,
  name: kb.name || kb.title || kb.kbName || '未命名知识库',
  status: kb.status,
});

const handleSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入查询语句');
    return;
  }
  if (!selectedKbId.value) {
    ElMessage.warning('请选择知识库');
    return;
  }

  loading.value = true;
  hasSearched.value = true;
  activeHistoryId.value = null;
  const startedAt = Date.now();

  try {
    const response = await request.post('/knowledge/retrieve/test', {
      query: query.value.trim(),
      kbId: selectedKbId.value,
      topK: config.topK,
      threshold: config.threshold,
      alpha: config.alpha,
      embeddingModel: config.embeddingModel || undefined,
      enableRerank: config.rerankModel !== 'none',
      rerankModel: config.rerankModel !== 'none' ? config.rerankModel : undefined,
    });

    executionTime.value = Date.now() - startedAt;
    const items = Array.isArray(response) ? response : (response?.data || response?.results || []);
    results.value = items.map(normalizeResult).filter(item => item.score >= config.threshold);
    applyRetrievalMeta(response);

    const entry = {
      id: Date.now().toString(),
      query: query.value.trim(),
      kbId: selectedKbId.value,
      kbName: activeKbName.value,
      resultCount: results.value.length,
      executionTime: executionTime.value,
      timestamp: Date.now(),
      results: results.value,
      retrievalMeta: { ...retrievalMeta },
    };
    saveHistory(entry);
    activeHistoryId.value = entry.id;
  } catch (error) {
    console.error('Retrieval error:', error);
    ElMessage.error('检索请求失败，请检查后端连接');
  } finally {
    loading.value = false;
  }
};

const applyRetrievalMeta = (response) => {
  if (Array.isArray(response)) {
    const fallback = results.value.length > 0 && !results.value.some(item => item.matchType === 'VECTOR');
    retrievalMeta.retrievalMode = fallback ? resolveLocalFallbackMode() : 'VECTOR';
    retrievalMeta.vectorHealthy = vectorStatus.value.healthy;
    retrievalMeta.vectorConnection = vectorStatus.value.connection;
    retrievalMeta.fallback = fallback;
    retrievalMeta.fallbackReason = fallback ? '本次未命中向量召回，结果来自文本索引/关键词兜底。' : '';
    retrievalMeta.kbVectorStats = {};
    return;
  }
  retrievalMeta.retrievalMode = response?.retrievalMode || resolveLocalFallbackMode();
  retrievalMeta.vectorHealthy = response?.vectorHealthy ?? vectorStatus.value.healthy;
  retrievalMeta.vectorConnection = response?.vectorConnection || vectorStatus.value.connection;
  retrievalMeta.fallback = Boolean(response?.fallback)
    || (results.value.length > 0 && !results.value.some(item => item.matchType === 'VECTOR'));
  retrievalMeta.fallbackReason = response?.fallbackReason || (retrievalMeta.fallback ? '本次未命中向量召回，结果来自文本索引/关键词兜底。' : '');
  retrievalMeta.kbVectorStats = response?.kbVectorStats || {};
};

const resolveLocalFallbackMode = () => {
  const types = new Set(results.value.map(item => item.matchType));
  if (results.value.length === 0) return 'EMPTY';
  if (types.has('VECTOR') && types.size > 1) return 'HYBRID';
  if (types.has('VECTOR')) return 'VECTOR';
  if (types.has('TEXT_INDEX') && types.has('KEYWORD')) return 'TEXT_INDEX_KEYWORD_FALLBACK';
  if (types.has('TEXT_INDEX')) return 'TEXT_INDEX_FALLBACK';
  if (types.has('KEYWORD')) return 'KEYWORD_FALLBACK';
  return 'UNKNOWN';
};

const normalizeResult = (result) => {
  const metadata = result.metadata || {};
  return {
    score: Number(result.score ?? 0),
    content: result.content || result.text || '',
    sourceDoc: metadata.source || metadata.fileName || metadata.doc_name || metadata.doc_id || result.sourceDoc || '未知文档',
    chunkIndex: metadata.chunk_id || metadata.chunkIndex || result.chunkId || result.id || '-',
    matchType: result.matchType || result.type || (Number(result.score ?? 0) >= 0.45 ? 'VECTOR' : 'KEYWORD'),
  };
};

const loadHistoryFromStorage = () => {
  try {
    historyList.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
  } catch {
    historyList.value = [];
  }
};

const saveHistory = (entry) => {
  historyList.value = [entry, ...historyList.value.filter(item => item.query !== entry.query)];
  if (historyList.value.length > MAX_HISTORY) historyList.value = historyList.value.slice(0, MAX_HISTORY);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(historyList.value));
};

const loadHistory = (item) => {
  activeHistoryId.value = item.id;
  query.value = item.query;
  results.value = item.results || [];
  Object.assign(retrievalMeta, {
    retrievalMode: item.retrievalMeta?.retrievalMode || resolveLocalFallbackMode(),
    vectorHealthy: item.retrievalMeta?.vectorHealthy ?? vectorStatus.value.healthy,
    vectorConnection: item.retrievalMeta?.vectorConnection || vectorStatus.value.connection,
    fallback: Boolean(item.retrievalMeta?.fallback),
    fallbackReason: item.retrievalMeta?.fallbackReason || '',
    kbVectorStats: item.retrievalMeta?.kbVectorStats || {},
  });
  hasSearched.value = true;
  executionTime.value = item.executionTime || 0;
  if (item.kbId) selectedKbId.value = item.kbId;
};

const removeHistory = (id) => {
  historyList.value = historyList.value.filter(item => item.id !== id);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(historyList.value));
  if (activeHistoryId.value === id) newRetrieval();
};

const newRetrieval = () => {
  activeHistoryId.value = null;
  query.value = '';
  results.value = [];
  hasSearched.value = false;
  executionTime.value = 0;
  Object.assign(retrievalMeta, {
    retrievalMode: 'IDLE',
    vectorHealthy: vectorStatus.value.healthy,
    vectorConnection: vectorStatus.value.connection,
    fallback: false,
    fallbackReason: '',
    kbVectorStats: {},
  });
  focusSearch();
};

const focusSearch = async () => {
  sidebarCollapsed.value = false;
  await nextTick();
  searchInputRef.value?.focus();
};

const useExample = (example) => {
  query.value = example;
  focusSearch();
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const expandParams = () => {
  sidebarCollapsed.value = false;
  sidebarTab.value = 'params';
};

const openTrace = (item) => {
  selectedResult.value = item;
  drawerVisible.value = true;
};

const applyToAgent = async () => {
  if (!selectedAgentId.value) {
    ElMessage.warning('请选择目标 Agent');
    return;
  }

  savingAgentConfig.value = true;
  try {
    const kbIds = selectedKbId.value === 'all'
      ? knowledgeBases.value.filter(kb => kb.id !== 'all').map(kb => kb.id)
      : [selectedKbId.value];
    const currentBinding = await getAgentToolBinding(selectedAgentId.value).catch(() => ({}));
    await saveAgentToolBinding(selectedAgentId.value, {
      ...currentBinding,
      kbIds,
    });
    await updateAgent(selectedAgentId.value, {
      retrievalTopK: config.topK,
      retrievalThreshold: config.threshold,
      retrievalAlpha: config.alpha,
      retrievalEmbeddingModel: config.embeddingModel || null,
      retrievalEnableRerank: config.rerankModel !== 'none',
      retrievalRerankModel: config.rerankModel !== 'none' ? config.rerankModel : null,
    });

    const agentName = agents.value.find(agent => agent.agentId === selectedAgentId.value)?.name || selectedAgentId.value;
    ElMessage.success(`已保存到 ${agentName}，新会话将使用这套检索配置`);
  } catch (error) {
    console.error('Failed to apply retrieval config to agent', error);
    ElMessage.error('保存 Agent 检索配置失败');
  } finally {
    savingAgentConfig.value = false;
  }
};

const barWidth = (value) => {
  if (sortedResults.value.length === 0) return '0%';
  return `${Math.round((value / sortedResults.value.length) * 100)}%`;
};

const formatTime = (timestamp) => {
  const diffMin = Math.floor((Date.now() - new Date(timestamp).getTime()) / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} 小时前`;
  const date = new Date(timestamp);
  return `${date.getMonth() + 1}/${date.getDate()}`;
};

const formatPercent = (value) => `${Math.round((Number(value) || 0) * 100)}%`;

const formatDuration = (value) => {
  const ms = Number(value) || 0;
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
  return `${ms}ms`;
};

const getScoreColor = (score) => {
  if (score >= 0.8) return '#0f9f95';
  if (score >= 0.5) return '#d97706';
  return '#64748b';
};

const matchTagType = (type) => {
  if (type === 'VECTOR') return 'success';
  if (type === 'KEYWORD') return 'warning';
  if (type === 'HYBRID') return 'primary';
  return 'info';
};

const matchLabel = (type) => {
  if (type === 'VECTOR') return '语义';
  if (type === 'KEYWORD') return '关键词';
  if (type === 'HYBRID') return '双路';
  if (type === 'TEXT_INDEX') return '文本索引';
  return type || '未知';
};
</script>

<style scoped>
.retrieval-workbench {
  --panel-border: var(--orin-border-strong, #d8e0e8);
  --panel-bg: #ffffff;
  --muted: var(--text-secondary, #64748b);
  --strong: var(--text-primary, #102033);
  --primary: var(--orin-primary, #0f9f95);
  --primary-soft: var(--orin-primary-soft, #e6f7f5);
  display: flex;
  min-height: calc(100vh - 86px);
  background: var(--app-bg, #f6f9fb);
  color: var(--strong);
  overflow: visible;
}

.retrieval-sidebar {
  position: relative;
  z-index: 2;
  width: 300px;
  flex: 0 0 300px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 14px;
  border-right: 1px solid var(--panel-border);
  background: rgba(255, 255, 255, 0.94);
  transition: width 0.22s ease, flex-basis 0.22s ease;
}

.retrieval-sidebar.is-collapsed {
  width: 70px;
  flex-basis: 70px;
  align-items: center;
}

.sidebar-toggle {
  position: absolute;
  top: 22px;
  right: -14px;
  width: 28px;
  height: 28px;
  border: 1px solid var(--panel-border);
  border-radius: 999px;
  background: #fff;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sidebar-head h2,
.hero-copy h1,
.section-head h2 {
  margin: 0;
  letter-spacing: 0;
}

.sidebar-head h2 {
  font-size: 16px;
}

.sidebar-head p,
.section-head p,
.hero-copy p,
.quality-gauge p,
.param-block p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.sidebar-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--panel-border);
  border-radius: 10px;
  background: rgba(241, 245, 249, 0.82);
}

.sidebar-tabs button,
.example-row button,
.history-item {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.sidebar-tabs button {
  height: 32px;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font-weight: 700;
}

.sidebar-tabs button.active {
  background: #fff;
  color: var(--strong);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.kb-panel,
.history-panel,
.params-panel,
.query-console,
.results-pane,
.quality-panel,
.strategy-panel {
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.kb-panel {
  padding: 12px;
}

.kb-panel label {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.kb-panel :deep(.el-select) {
  width: 100%;
}

.kb-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}

.kb-meta span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--strong);
  font-weight: 700;
}

.history-panel,
.params-panel {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.panel-title-row,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.panel-title-row {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 800;
}

.history-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.history-item {
  position: relative;
  width: 100%;
  display: block;
  padding: 11px 34px 11px 10px;
  margin-bottom: 4px;
  border-radius: 8px;
  background: transparent;
  text-align: left;
}

.history-item:hover,
.history-item.active {
  background: rgba(15, 159, 149, 0.08);
}

.history-query {
  display: block;
  overflow: hidden;
  color: var(--strong);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-meta {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-delete {
  position: absolute;
  top: 10px;
  right: 6px;
  opacity: 0;
}

.history-item:hover .history-delete {
  opacity: 1;
}

.params-panel {
  overflow-y: auto;
}

.params-panel :deep(.el-select) {
  width: 100%;
}

.param-block {
  margin-bottom: 18px;
}

.param-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--strong);
  font-size: 13px;
  font-weight: 700;
}

.param-label strong {
  color: var(--primary);
}

.collapsed-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 46px;
}

.retrieval-main {
  flex: 1;
  min-width: 0;
  padding: 18px 22px 24px;
  overflow: visible;
}

.hero-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(330px, 38%);
  gap: 14px;
  align-items: stretch;
  margin-bottom: 14px;
}

.hero-copy {
  padding: 16px 18px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
}

.eyebrow {
  display: inline-block;
  margin-bottom: 6px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0;
}

.hero-copy h1 {
  font-size: 22px;
  line-height: 1.18;
}

.hero-copy p {
  max-width: 680px;
  font-size: 13px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-border);
  gap: 1px;
}

.metric-cell {
  min-width: 0;
  padding: 14px;
  background: var(--panel-bg);
}

.metric-cell span,
.metric-cell small {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.metric-cell strong {
  display: block;
  margin: 7px 0 4px;
  font-size: 22px;
  line-height: 1;
}

.query-console {
  margin-bottom: 14px;
  padding: 14px;
}

.retrieval-notice,
.fallback-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border-radius: 8px;
}

.retrieval-notice {
  margin: -2px 0 18px;
  padding: 12px 14px;
  border: 1px solid #f3d19e;
  background: #fdf6ec;
  color: #7c4a03;
}

.retrieval-notice.danger {
  border-color: #f5b8b8;
  background: #fef0f0;
  color: #9f1d1d;
}

.retrieval-notice strong,
.fallback-banner span {
  font-size: 13px;
  font-weight: 800;
}

.retrieval-notice p {
  margin: 3px 0 0;
  color: inherit;
  font-size: 12px;
  line-height: 1.5;
}

.fallback-banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #f3d19e;
  background: #fff8ed;
  color: #8a5600;
}

.query-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.query-context span,
.result-footer span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid rgba(15, 159, 149, 0.18);
  border-radius: 999px;
  background: rgba(15, 159, 149, 0.08);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}

.query-input :deep(.el-textarea__inner) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--panel-border) inset;
  font-size: 15px;
  line-height: 1.6;
}

.query-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 10px;
}

.query-actions > .el-button {
  flex-shrink: 0;
}

.example-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.example-row button {
  max-width: 230px;
  height: 30px;
  padding: 0 10px;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 999px;
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.example-row button:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 14px;
  align-items: start;
}

.content-grid.is-empty {
  grid-template-columns: minmax(0, 1fr);
}

.content-grid.is-empty .insight-pane,
.content-grid:has(> .results-pane .empty-guide) .insight-pane,
.content-grid:has(> .results-pane .el-empty) .insight-pane {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
}

.content-grid:has(> .results-pane .empty-guide),
.content-grid:has(> .results-pane .el-empty) {
  grid-template-columns: minmax(0, 1fr);
}

.results-pane,
.quality-panel,
.strategy-panel {
  padding: 14px;
}

.section-head {
  margin-bottom: 12px;
}

.section-head.compact {
  margin-bottom: 12px;
}

.section-head h2 {
  font-size: 16px;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-card {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: #fff;
  overflow: visible;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.result-card:hover {
  border-color: var(--primary);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.rank-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--panel-border);
}

.rank-column span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.rank-column strong {
  margin-top: 6px;
  font-size: 18px;
}

.result-body {
  min-width: 0;
  overflow: visible;
}

.result-topline,
.doc-title,
.result-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-topline {
  justify-content: space-between;
}

.doc-title {
  min-width: 0;
  color: var(--primary);
  font-size: 13px;
  font-weight: 800;
}

.doc-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-body p {
  display: block !important;
  margin: 8px 0;
  max-height: none !important;
  overflow: visible !important;
  color: #334155;
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  line-clamp: unset !important;
  white-space: pre-wrap;
  -webkit-box-orient: initial !important;
  -webkit-line-clamp: unset !important;
}

.result-footer {
  flex-wrap: wrap;
}

.empty-guide {
  min-height: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  text-align: center;
}

.empty-guide .el-icon {
  margin-bottom: 14px;
  color: #cbd5e1;
  font-size: 54px;
}

.empty-guide h3 {
  margin: 0;
  color: var(--strong);
}

.empty-guide p {
  max-width: 360px;
  margin: 8px 0 0;
  line-height: 1.6;
}

.insight-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.quality-gauge {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 10px;
  border-radius: 8px;
  background: rgba(241, 245, 249, 0.72);
}

.gauge-ring {
  --score: 0;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at center, #fff 0 57%, transparent 58%),
    conic-gradient(var(--primary) calc(var(--score) * 1%), #dbe5ea 0);
}

.gauge-ring span {
  color: var(--strong);
  font-size: 18px;
  font-weight: 900;
}

.quality-gauge strong {
  display: block;
  font-size: 15px;
}

.quality-bars {
  margin-top: 14px;
}

.quality-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) 36px;
  align-items: center;
  gap: 9px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}

.quality-row div {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.quality-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--primary);
}

.quality-row strong {
  color: var(--strong);
  text-align: right;
}

.strategy-panel dl,
.trace-meta {
  margin: 0;
}

.strategy-panel dl div,
.trace-meta div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-top: 1px solid var(--panel-border);
}

.strategy-panel dt,
.trace-meta dt {
  color: var(--muted);
  font-size: 12px;
}

.strategy-panel dd,
.trace-meta dd {
  margin: 0;
  color: var(--strong);
  font-size: 13px;
  font-weight: 800;
  text-align: right;
  word-break: break-all;
}

.trace-panel {
  color: var(--strong);
}

.trace-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--primary);
  font-weight: 800;
}

.trace-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 18px 0;
}

.trace-stats div {
  padding: 14px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: rgba(241, 245, 249, 0.72);
}

.trace-stats span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.trace-stats strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
}

.trace-content {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid rgba(15, 159, 149, 0.18);
  border-radius: 8px;
  background: rgba(15, 159, 149, 0.08);
  color: #334155;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

html.dark .retrieval-workbench {
  --panel-bg: rgba(15, 23, 42, 0.78);
  --panel-border: rgba(148, 163, 184, 0.22);
  --strong: #e2e8f0;
  --muted: #94a3b8;
  background: #081313;
}

html.dark .retrieval-sidebar,
html.dark .hero-copy,
html.dark .metric-cell,
html.dark .result-card,
html.dark .example-row button,
html.dark .sidebar-toggle,
html.dark .gauge-ring {
  background: rgba(15, 23, 42, 0.9);
}

html.dark .result-body p,
html.dark .trace-content {
  color: #cbd5e1;
}

html.dark .query-input :deep(.el-textarea__inner) {
  background: rgba(15, 23, 42, 0.86);
  color: #e2e8f0;
}

@media (max-width: 1180px) {
  .hero-band,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .insight-pane {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .retrieval-workbench {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 86px);
    overflow: visible;
  }

  .retrieval-sidebar,
  .retrieval-sidebar.is-collapsed {
    width: 100%;
    flex-basis: auto;
    border-right: 0;
    border-bottom: 1px solid var(--panel-border);
  }

  .sidebar-toggle {
    display: none;
  }

  .retrieval-sidebar.is-collapsed {
    align-items: stretch;
  }

  .collapsed-actions {
    flex-direction: row;
    margin-top: 0;
  }

  .retrieval-main {
    padding: 14px;
  }

  .hero-metrics,
  .insight-pane {
    grid-template-columns: 1fr;
  }

  .query-actions,
  .panel-title-row,
  .section-head {
    align-items: stretch;
    flex-direction: column;
  }

  .result-card {
    grid-template-columns: 1fr;
  }

  .rank-column {
    flex-direction: row;
    justify-content: space-between;
    border-right: 0;
    border-bottom: 1px solid var(--panel-border);
    padding-bottom: 10px;
  }
}
</style>
