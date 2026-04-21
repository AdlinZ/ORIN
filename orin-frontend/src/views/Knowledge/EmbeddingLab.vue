<template>
  <div class="retrieval-workspace">

    <!-- Left Sidebar -->
    <aside class="workspace-sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">

      <div v-if="!sidebarCollapsed" class="sidebar-tabs">
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'history' }" @click="sidebarTab = 'history'">检索历史</div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'params' }" @click="sidebarTab = 'params'">检索参数</div>
      </div>

      <!-- KB selector (always visible when expanded) -->
      <div v-if="!sidebarCollapsed" class="sidebar-kb-switch">
        <span class="sidebar-kb-label">知识库</span>
        <el-select
          v-model="selectedKbId"
          class="sidebar-kb-select"
          placeholder="选择知识库"
          filterable
        >
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <!-- History Tab -->
      <div v-show="sidebarTab === 'history'" class="workspace-history-pane">
        <div class="session-collapse-handle">
          <el-button
            class="collapse-btn"
            circle
            :icon="sidebarCollapsed ? ArrowRight : ArrowLeft"
            @click="sidebarCollapsed = !sidebarCollapsed"
          />
        </div>

        <div v-if="sidebarCollapsed" class="collapsed-pane">
          <el-button class="collapsed-new-btn" circle :icon="Search" @click="focusSearch" />
        </div>

        <template v-else>
          <div class="session-actions">
            <el-button
              class="new-session-btn"
              type="primary"
              :icon="Plus"
              @click="newRetrieval"
            >
              新建检索
            </el-button>
          </div>

          <div class="session-list">
            <div
              v-for="item in historyList"
              :key="item.id"
              :class="['session-item', { active: activeHistoryId === item.id }]"
              @click="loadHistory(item)"
            >
              <div class="session-main">
                <div class="session-title">{{ item.query }}</div>
                <div class="session-meta">
                  <span>{{ item.kbName }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ item.resultCount }} 条</span>
                  <span class="meta-dot">·</span>
                  <span>{{ formatTime(item.timestamp) }}</span>
                </div>
              </div>
              <el-button
                link
                class="session-delete"
                :icon="Delete"
                @click.stop="removeHistory(item.id)"
              />
            </div>
            <el-empty v-if="historyList.length === 0" :image-size="48" description="暂无检索历史" />
          </div>
        </template>
      </div>

      <!-- Params Tab -->
      <div v-show="sidebarTab === 'params' && !sidebarCollapsed" class="workspace-params-pane">
        <div class="params-scroll">
          <el-form label-position="top" size="small">
            <el-form-item label="Embedding 模型">
              <el-select v-model="config.embeddingModel" placeholder="默认模型" clearable style="width:100%">
                <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
              </el-select>
            </el-form-item>

            <el-divider />

            <div class="param-group">
              <div class="param-header">
                <div class="param-label-wrap">
                  <span class="param-label">Top-K 召回数</span>
                </div>
                <span class="value-badge">{{ config.topK }}</span>
              </div>
              <el-slider v-model="config.topK" :min="1" :max="30" />
            </div>

            <div class="param-group">
              <div class="param-header">
                <div class="param-label-wrap">
                  <span class="param-label">Similarity Threshold</span>
                </div>
                <span class="value-badge">{{ config.threshold }}</span>
              </div>
              <el-slider v-model="config.threshold" :min="0" :max="1" :step="0.05" />
            </div>

            <div class="param-group">
              <div class="param-header">
                <div class="param-label-wrap">
                  <span class="param-label">语义权重 Alpha</span>
                  <span class="param-desc">
                    {{ (config.alpha * 100).toFixed(0) }}% 向量 + {{ ((1 - config.alpha) * 100).toFixed(0) }}% 关键词
                  </span>
                </div>
                <span class="value-badge">{{ config.alpha }}</span>
              </div>
              <el-slider v-model="config.alpha" :min="0" :max="1" :step="0.1" />
            </div>

            <el-form-item label="Rerank 模型" style="margin-top:8px">
              <el-select v-model="config.rerankModel" style="width:100%">
                <el-option label="不使用 Rerank" value="none" />
                <el-option v-for="m in rerankModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
                <el-option v-if="rerankModels.length === 0" label="暂无可用 Rerank 模型" value="none" disabled />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div class="params-bottom">
          <el-button type="primary" style="width:100%" :icon="Setting" @click="applyToAgent">
            同步配置至 Agent
          </el-button>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="workspace-main">

      <!-- Header Bar -->
      <div class="main-header">
        <div class="header-left">
          <h2 class="main-title">知识中心</h2>
          <p class="main-subtitle">检索知识库内容并验证召回效果</p>
          <span v-if="hasSearched && results.length > 0" class="result-count-badge">
            {{ results.length }} 条结果 · {{ executionTime }}ms
          </span>
        </div>
        <div class="header-right">
          <el-button-group>
            <el-button
              :type="viewMode === 'simple' ? 'primary' : ''"
              size="small"
              @click="viewMode = 'simple'"
            >
              <el-icon><List /></el-icon>
              检索
            </el-button>
            <el-button
              :type="viewMode === 'debug' ? 'primary' : ''"
              size="small"
              @click="viewMode = 'debug'"
            >
              <el-icon><DataAnalysis /></el-icon>
              调试
            </el-button>
          </el-button-group>
        </div>
      </div>

      <!-- Search Bar -->
      <div class="search-hero">
        <el-input
          ref="searchInputRef"
          v-model="query"
          placeholder="输入关键词或问题..."
          size="large"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button :loading="loading" type="primary" @click="handleSearch">
              {{ viewMode === 'debug' ? '混合检索' : '搜索' }}
            </el-button>
          </template>
        </el-input>
      </div>

      <!-- Results Area -->
      <div v-loading="loading" class="results-container">

        <!-- Stats (debug mode) -->
        <div v-if="viewMode === 'debug' && hasSearched && results.length > 0" class="stats-bar">
          <div class="stat-item">
            <el-icon><Share /></el-icon>
            <span>向量 <strong>{{ vectorResults.length }}</strong></span>
          </div>
          <div class="stat-item">
            <el-icon><Search /></el-icon>
            <span>关键词 <strong>{{ keywordResults.length }}</strong></span>
          </div>
          <div v-if="hybridResults.length" class="stat-item hybrid">
            <el-icon><Aim /></el-icon>
            <span>双路命中 <strong>{{ hybridResults.length }}</strong></span>
          </div>
        </div>

        <!-- ══ Simple View ══ -->
        <div v-if="viewMode === 'simple' && hasSearched && results.length > 0" class="simple-results">
          <div
            v-for="(item, idx) in sortedResults"
            :key="'s-'+idx"
            class="simple-card"
            @click="openTrace(item)"
          >
            <div class="simple-card-header">
              <div class="simple-doc-name">
                <el-icon><Document /></el-icon>
                {{ item.sourceDoc }}
              </div>
              <div class="simple-meta">
                <el-tag :type="matchTagType(item.matchType)" size="small" effect="plain">
                  {{ matchLabel(item.matchType) }}
                </el-tag>
                <span class="score-pill" :style="{ background: scoreBackground(item.score) }">
                  {{ (item.score * 100).toFixed(0) }}%
                </span>
              </div>
            </div>
            <div class="simple-content">{{ item.content }}</div>
            <div class="simple-card-footer">
              <span class="chunk-ref">{{ item.chunkIndex }}</span>
            </div>
          </div>
        </div>

        <!-- ══ Debug View ══ -->
        <div v-else-if="viewMode === 'debug' && hasSearched && results.length > 0" class="split-view">
          <div class="strategy-col">
            <div class="col-header vector-header">
              <el-icon><Share /></el-icon> 语义匹配
              <el-tag size="small" type="success" effect="plain" style="margin-left:auto">{{ vectorResults.length }}</el-tag>
            </div>
            <div v-if="vectorResults.length === 0" class="col-empty">无向量召回结果</div>
            <div v-for="(item, idx) in vectorResults" :key="'v-'+idx" class="result-card" @click="openTrace(item)">
              <div class="score-bar-wrap">
                <div class="bar-track"><div class="bar-fill vector-fill" :style="{ height: (item.score * 100) + '%' }" /></div>
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

          <div class="strategy-col">
            <div class="col-header keyword-header">
              <el-icon><Search /></el-icon> 关键词匹配
              <el-tag size="small" type="warning" effect="plain" style="margin-left:auto">{{ keywordResults.length }}</el-tag>
            </div>
            <div v-if="keywordResults.length === 0" class="col-empty">无关键词召回结果</div>
            <div v-for="(item, idx) in keywordResults" :key="'k-'+idx" class="result-card" @click="openTrace(item)">
              <div class="score-bar-wrap">
                <div class="bar-track"><div class="bar-fill keyword-fill" :style="{ height: (item.score * 100) + '%' }" /></div>
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

          <div v-if="hybridResults.length > 0" class="strategy-col">
            <div class="col-header hybrid-header">
              <el-icon><Aim /></el-icon> 双路命中
              <el-tag size="small" effect="plain" style="margin-left:auto">{{ hybridResults.length }}</el-tag>
            </div>
            <div v-for="(item, idx) in hybridResults" :key="'h-'+idx" class="result-card" @click="openTrace(item)">
              <div class="score-bar-wrap">
                <div class="bar-track"><div class="bar-fill hybrid-fill" :style="{ height: (item.score * 100) + '%' }" /></div>
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

        <!-- Empty -->
        <el-empty v-else-if="hasSearched" description="未找到相关内容，尝试换个关键词或调低 Threshold">
          <template #image><el-icon :size="64" color="#C8C9CC"><Search /></el-icon></template>
          <div v-if="debugInfo && viewMode === 'debug'" class="debug-info">
            <p>后端返回 {{ debugInfo.totalResults }} 条，向量 {{ debugInfo.vectorCount }} / 关键词 {{ debugInfo.keywordCount }}</p>
            <p v-if="debugInfo.vectorCount === 0" class="debug-warning">⚠️ 语义检索失败：Embedding 服务未配置或 API Key 无效</p>
            <p>得分区间：{{ debugInfo.minScore }} ~ {{ debugInfo.maxScore }}</p>
            <p class="debug-hint">降低 Threshold 可查看更多结果</p>
          </div>
        </el-empty>

        <!-- Welcome -->
        <div v-else class="welcome-state">
          <el-icon :size="56" color="#C8C9CC"><Search /></el-icon>
          <h3>搜索知识库</h3>
          <p>输入问题或关键词，从知识库中精准召回相关片段</p>
        </div>
      </div>
    </div>

    <!-- Trace Drawer -->
    <el-drawer v-model="drawerVisible" title="片段详情" direction="rtl" size="38%">
      <div v-if="selectedResult" class="trace-panel">
        <div class="trace-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedResult.sourceDoc }}</span>
          <el-tag :type="matchTagType(selectedResult.matchType)" size="small" style="margin-left:auto">
            {{ matchLabel(selectedResult.matchType) }}
          </el-tag>
        </div>
        <div class="trace-score-row">
          <div class="trace-score-item">
            <div class="trace-score-label">相关度</div>
            <div class="trace-score-value" :style="{ color: getScoreColor(selectedResult.score) }">
              {{ (selectedResult.score * 100).toFixed(1) }}%
            </div>
          </div>
          <div class="trace-score-item">
            <div class="trace-score-label">Chunk ID</div>
            <div class="trace-score-value" style="font-size:12px; word-break:break-all">{{ selectedResult.chunkIndex }}</div>
          </div>
        </div>
        <div class="trace-content-label">内容</div>
        <div class="trace-content-box">{{ selectedResult.content }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import {
  Search, Setting, Aim, DataAnalysis, Share, Document, List,
  ArrowLeft, ArrowRight, Plus, Delete,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

const HISTORY_KEY = 'orin-retrieval-history';
const MAX_HISTORY = 50;

// ── State ──────────────────────────────────────────
const sidebarCollapsed = ref(false);
const sidebarTab = ref('history');
const viewMode = ref('simple');

const selectedKbId = ref('');
const query = ref('');
const loading = ref(false);
const hasSearched = ref(false);
const executionTime = ref(0);
const results = ref([]);
const knowledgeBases = ref([]);
const embeddingModels = ref([]);
const rerankModels = ref([]);
const drawerVisible = ref(false);
const selectedResult = ref(null);
const debugInfo = ref(null);
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

// ── Computed ───────────────────────────────────────
const vectorResults = computed(() => results.value.filter(r => r.matchType === 'VECTOR'));
const keywordResults = computed(() => results.value.filter(r => r.matchType === 'KEYWORD'));
const hybridResults = computed(() => results.value.filter(r => r.matchType === 'HYBRID'));
const sortedResults = computed(() => [...results.value].sort((a, b) => b.score - a.score));

// ── History helpers ────────────────────────────────
const loadHistoryFromStorage = () => {
  try {
    historyList.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
  } catch {
    historyList.value = [];
  }
};

const saveHistory = (entry) => {
  historyList.value.unshift(entry);
  if (historyList.value.length > MAX_HISTORY) historyList.value = historyList.value.slice(0, MAX_HISTORY);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(historyList.value));
};

const removeHistory = (id) => {
  historyList.value = historyList.value.filter(h => h.id !== id);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(historyList.value));
  if (activeHistoryId.value === id) {
    activeHistoryId.value = null;
    results.value = [];
    hasSearched.value = false;
    query.value = '';
  }
};

const loadHistory = (item) => {
  activeHistoryId.value = item.id;
  query.value = item.query;
  results.value = item.results || [];
  hasSearched.value = true;
  executionTime.value = item.executionTime || 0;
  if (item.kbId) selectedKbId.value = item.kbId;
};

const newRetrieval = () => {
  activeHistoryId.value = null;
  query.value = '';
  results.value = [];
  hasSearched.value = false;
  debugInfo.value = null;
  focusSearch();
};

const focusSearch = async () => {
  await nextTick();
  searchInputRef.value?.focus();
};

const formatTime = (ts) => {
  const d = new Date(ts);
  const now = new Date();
  const diffMs = now - d;
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH} 小时前`;
  return `${d.getMonth() + 1}/${d.getDate()}`;
};

// ── Lifecycle ──────────────────────────────────────
onMounted(async () => {
  loadHistoryFromStorage();
  try {
    const [modelsRes, kbRes] = await Promise.all([
      request.get('/models'),
      request.get('/knowledge/list'),
    ]);
    embeddingModels.value = (modelsRes || []).filter(m => m.type?.toUpperCase() === 'EMBEDDING');
    rerankModels.value = (modelsRes || []).filter(m =>
      m.type?.toUpperCase() === 'RERANK' || m.type?.toUpperCase() === 'RERANKER'
    );
    const kbs = (kbRes || []).filter(kb => kb.status === 'ENABLED');
    knowledgeBases.value = [{ id: 'all', name: '全局检索 (All KBs)' }, ...kbs];
    if (knowledgeBases.value.length > 0) selectedKbId.value = knowledgeBases.value[0].id;
    if (embeddingModels.value.length > 0) config.embeddingModel = embeddingModels.value[0].modelId;
  } catch (e) {
    console.error('Knowledge retrieval init failed', e);
  }
});

// ── Search ─────────────────────────────────────────
const handleSearch = async () => {
  if (!query.value.trim() || !selectedKbId.value) {
    ElMessage.warning('请输入查询语句并选择知识库');
    return;
  }

  loading.value = true;
  hasSearched.value = true;
  debugInfo.value = null;
  activeHistoryId.value = null;
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

    const allResults = items.map(r => ({
      score: r.score ?? 0,
      content: r.content ?? '',
      sourceDoc: r.metadata?.source || r.metadata?.doc_id || '未知文档',
      chunkIndex: r.metadata?.chunk_id || '-',
      matchType: r.matchType || (r.score > 0.4 ? 'VECTOR' : 'KEYWORD'),
    }));

    results.value = allResults.filter(r => r.score >= config.threshold);

    const vectorCount = allResults.filter(r => r.matchType === 'VECTOR').length;
    const keywordCount = allResults.filter(r => r.matchType === 'KEYWORD').length;

    if (allResults.length > 0 && results.value.length === 0) {
      debugInfo.value = {
        totalResults: allResults.length,
        maxScore: Math.max(...allResults.map(r => r.score)).toFixed(3),
        minScore: Math.min(...allResults.map(r => r.score)).toFixed(3),
        vectorCount,
        keywordCount,
      };
    } else if (vectorCount === 0 && keywordCount > 0) {
      ElMessage.warning('语义检索返回0条，仅有关键词匹配结果。请检查Embedding服务配置。');
    }

    // Save to history
    const kbName = knowledgeBases.value.find(k => k.id === selectedKbId.value)?.name || selectedKbId.value;
    const entry = {
      id: Date.now().toString(),
      query: query.value,
      kbId: selectedKbId.value,
      kbName,
      resultCount: results.value.length,
      executionTime: executionTime.value,
      timestamp: Date.now(),
      results: results.value,
    };
    saveHistory(entry);
    activeHistoryId.value = entry.id;
  } catch (e) {
    ElMessage.error('检索请求失败，请检查后端连接');
    console.error('Retrieval error:', e);
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

const scoreBackground = (score) => {
  if (score >= 0.8) return 'rgba(82,196,26,0.12)';
  if (score >= 0.5) return 'rgba(250,173,20,0.12)';
  return 'rgba(0,0,0,0.06)';
};

const matchTagType = (type) => {
  if (type === 'VECTOR') return 'success';
  if (type === 'KEYWORD') return 'warning';
  return '';
};

const matchLabel = (type) => {
  if (type === 'VECTOR') return '语义';
  if (type === 'KEYWORD') return '关键词';
  if (type === 'HYBRID') return '双路';
  return type;
};
</script>

<style scoped>
/* ══ Workspace Shell ══════════════════════════════ */
.retrieval-workspace {
  --left-pane-width: 300px;
  --sidebar-accent: var(--orin-primary, #0f9f95);
  --sidebar-text-strong: #0f172a;
  --sidebar-text: #334155;
  --sidebar-text-muted: #64748b;
  --sidebar-line: rgba(226, 232, 240, 0.9);
  --sidebar-hover-bg: rgba(241, 245, 249, 0.9);
  --sidebar-active-bg: rgba(237, 249, 247, 0.92);

  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  background: var(--app-bg, #f6f9fb);
  font-family: "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* ══ Left Sidebar ═════════════════════════════════ */
.workspace-sidebar {
  width: var(--left-pane-width);
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border-right: 1px solid var(--sidebar-line);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  position: relative;
  z-index: 10;
}

.workspace-sidebar.is-collapsed {
  width: 64px;
}

.workspace-sidebar ::-webkit-scrollbar { width: 4px; }
.workspace-sidebar ::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.6);
  border-radius: 4px;
}

.sidebar-tabs {
  display: flex;
  margin: 14px 14px 6px;
  padding: 4px;
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  gap: 4px;
  flex-shrink: 0;
}

.sidebar-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-text-muted);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.sidebar-tab:hover { background: rgba(226, 232, 240, 0.7); color: var(--sidebar-text); }
.sidebar-tab.active {
  background: #ffffff;
  color: var(--sidebar-text-strong);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.sidebar-kb-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 14px 8px;
  padding: 8px 10px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid var(--sidebar-line);
  border-radius: 10px;
  flex-shrink: 0;
}

.sidebar-kb-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--sidebar-text-muted);
}

.sidebar-kb-select { flex: 1; }

/* History Pane */
.workspace-history-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.session-collapse-handle {
  position: absolute;
  right: -14px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
}

.collapse-btn {
  width: 28px !important;
  height: 28px !important;
  font-size: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04);
  color: #64748b;
  transition: 0.2s ease;
}

.collapse-btn:hover {
  color: var(--sidebar-accent);
  border-color: #99f6e4;
  box-shadow: 0 4px 12px rgba(15, 159, 149, 0.12);
  transform: scale(1.05);
}

.collapsed-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 12px;
}

.collapsed-new-btn {
  width: 36px !important;
  height: 36px !important;
  background: var(--orin-primary-soft);
  border-color: transparent;
  color: var(--orin-primary);
}

.session-actions {
  padding: 10px 14px 8px;
  flex-shrink: 0;
}

.new-session-btn {
  width: 100%;
  border-radius: 10px;
  font-weight: 600;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 16px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
  margin-bottom: 2px;
  gap: 8px;
}

.session-item:hover { background: var(--sidebar-hover-bg); }
.session-item.active { background: var(--sidebar-active-bg); }

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--sidebar-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 3px;
}

.session-meta {
  font-size: 11px;
  color: var(--sidebar-text-muted);
  display: flex;
  align-items: center;
  gap: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-dot { opacity: 0.5; }

.session-delete {
  opacity: 0;
  transition: opacity 0.15s;
  color: #94a3b8;
  flex-shrink: 0;
}
.session-item:hover .session-delete { opacity: 1; }

/* Params Pane */
.workspace-params-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.params-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 14px;
}

.params-bottom {
  padding: 12px 14px;
  border-top: 1px solid var(--sidebar-line);
  flex-shrink: 0;
}

.param-group {
  margin-bottom: 20px;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.param-label-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--sidebar-text);
}

.param-desc {
  font-size: 11px;
  color: var(--sidebar-text-muted);
}

.value-badge {
  background: var(--orin-primary-soft);
  color: var(--orin-primary);
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ══ Main Area ════════════════════════════════════ */
.workspace-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px 12px;
  border-bottom: 1px solid var(--sidebar-line);
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.main-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--sidebar-text-strong);
}

.main-subtitle {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--sidebar-text-muted);
}

.result-count-badge {
  font-size: 12px;
  color: var(--sidebar-text-muted);
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 20px;
  padding: 2px 10px;
}

.search-hero {
  padding: 16px 28px;
  background: rgba(255,255,255,0.6);
  border-bottom: 1px solid var(--sidebar-line);
  flex-shrink: 0;
}

.search-hero :deep(.el-input__wrapper) {
  border-radius: 12px 0 0 12px;
  box-shadow: 0 0 0 1px var(--sidebar-line) inset;
}

.search-hero :deep(.el-input__wrapper:hover),
.search-hero :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--orin-primary) inset;
}

.search-hero :deep(.el-input__inner) { height: 42px; }

.search-hero :deep(.el-button--primary) {
  border-radius: 0 12px 12px 0;
  padding: 0 24px;
  height: 42px;
  font-weight: 500;
}

.results-container {
  flex: 1;
  padding: 20px 28px;
  overflow-y: auto;
}

/* Stats Bar */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 14px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--sidebar-text-muted);
}

.stat-item.hybrid { color: var(--orin-primary); }
.stat-item strong { color: var(--sidebar-text-strong); }

/* Simple View */
.simple-results { display: flex; flex-direction: column; gap: 10px; }

.simple-card {
  background: #ffffff;
  border: 1px solid var(--sidebar-line);
  border-radius: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.simple-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(0,0,0,0.06);
  border-color: var(--orin-primary);
}

.simple-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.simple-doc-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sidebar-accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.simple-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.score-pill {
  font-size: 12px;
  font-weight: 700;
  color: var(--sidebar-text-strong);
  padding: 2px 10px;
  border-radius: 20px;
}

.simple-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--sidebar-text);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
}

.simple-card-footer { display: flex; justify-content: flex-end; }

/* Debug View */
.split-view { display: flex; gap: 14px; align-items: flex-start; }
.strategy-col { flex: 1; min-width: 0; }

.col-header {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--sidebar-text-strong);
}

.vector-header  { background: rgba(82, 196, 26, 0.08); border: 1px solid rgba(82, 196, 26, 0.2); }
.keyword-header { background: rgba(250, 173, 20, 0.08); border: 1px solid rgba(250, 173, 20, 0.2); }
.hybrid-header  { background: var(--orin-primary-soft); border: 1px solid var(--orin-primary-light, #a0cfff); }

.col-empty { text-align: center; color: var(--sidebar-text-muted); font-size: 13px; padding: 24px 0; }

.result-card {
  background: #ffffff;
  border: 1px solid var(--sidebar-line);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  gap: 10px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: all 0.2s;
}

.result-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(0,0,0,0.06);
  border-color: var(--orin-primary);
}

.score-bar-wrap { width: 26px; display: flex; flex-direction: column; align-items: center; gap: 4px; flex-shrink: 0; }
.bar-track { width: 6px; height: 60px; background: var(--orin-primary-soft); border-radius: 3px; position: relative; overflow: hidden; }
.bar-fill { width: 100%; position: absolute; bottom: 0; border-radius: 3px; transition: height 0.4s ease; }
.vector-fill  { background: #52c41a; }
.keyword-fill { background: #faad14; }
.hybrid-fill  { background: var(--orin-primary); }
.score-num { font-size: 10px; font-weight: 700; color: var(--sidebar-text-muted); }

.card-body { flex: 1; min-width: 0; }
.content-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--sidebar-text);
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-footer { display: flex; justify-content: space-between; align-items: center; }
.chunk-ref { font-size: 11px; color: var(--sidebar-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 140px; }

/* Welcome */
.welcome-state {
  height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--sidebar-text-muted);
  gap: 12px;
}
.welcome-state h3 { color: var(--sidebar-text-strong); margin: 0; font-size: 17px; }
.welcome-state p { max-width: 340px; text-align: center; font-size: 14px; line-height: 1.6; margin: 0; }

/* Debug info */
.debug-info { text-align: center; color: var(--sidebar-text-muted); font-size: 13px; margin-top: 10px; }
.debug-info p { margin: 4px 0; }
.debug-hint { color: var(--orin-primary); font-weight: 500; }
.debug-warning { color: #e6a23c; font-weight: 600; padding: 6px 12px; background: rgba(230,162,60,0.08); border-radius: 6px; }

/* Trace Drawer */
.trace-panel { padding: 4px 0; }

.trace-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--orin-primary);
  margin-bottom: 20px;
}

.trace-score-row { display: flex; gap: 16px; margin-bottom: 20px; }

.trace-score-item {
  flex: 1;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid var(--sidebar-line);
  border-radius: 8px;
  padding: 12px;
  text-align: center;
}

.trace-score-label { font-size: 11px; color: var(--sidebar-text-muted); margin-bottom: 6px; }
.trace-score-value { font-size: 20px; font-weight: 700; color: var(--sidebar-text-strong); }

.trace-content-label {
  font-size: 11px;
  color: var(--sidebar-text-muted);
  margin-bottom: 8px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.trace-content-box {
  background: var(--orin-primary-soft);
  border: 1px solid var(--sidebar-line);
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--sidebar-text);
}

/* Dark mode */
html.dark .retrieval-workspace { background: var(--app-bg); }
html.dark .workspace-sidebar { background: rgba(30, 41, 59, 0.92); }
html.dark .main-header { background: rgba(30, 41, 59, 0.8); }
html.dark .search-hero { background: rgba(30, 41, 59, 0.6); }
html.dark .simple-card,
html.dark .result-card { background: rgba(30, 41, 59, 0.8); }
html.dark .sidebar-tabs { background: rgba(15, 23, 42, 0.6); }
html.dark .sidebar-tab.active { background: rgba(30, 41, 59, 0.9); color: #e2e8f0; }
html.dark .session-item:hover { background: rgba(51, 65, 85, 0.6); }
html.dark .session-item.active { background: rgba(15, 159, 149, 0.15); }
html.dark .session-title { color: #e2e8f0; }
html.dark .main-title { color: #e2e8f0; }
html.dark .main-subtitle { color: #94a3b8; }
</style>
