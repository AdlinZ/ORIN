<template>
  <div class="knowledge-workspace" v-loading="loading">
    <aside class="workspace-sidebar">
      <div class="sidebar-block">
        <div class="block-title">检索设置</div>
        <div class="field-label">知识库范围</div>
        <el-select v-model="selectedKbId" filterable class="full-width">
          <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>

        <div class="field-row">
          <span class="field-label">返回条数</span>
          <el-input-number v-model="topK" :min="1" :max="30" controls-position="right" class="topk-input" />
        </div>

        <div class="field-row compact">
          <span class="meta-text">知识库 {{ knowledgeRows.length }}</span>
          <span class="meta-text">图谱 {{ graphRows.length }}</span>
        </div>

        <el-button class="full-width" type="primary" plain :icon="Grid" @click="goAssets">进入知识资产</el-button>
      </div>

      <div class="sidebar-block">
        <div class="block-title">知识库</div>
        <div class="mini-list" v-if="knowledgeRows.length">
          <button
            v-for="kb in knowledgeRows.slice(0, 6)"
            :key="kb.id || kb.kbId"
            class="mini-item"
            :class="{ active: String(kb.id || kb.kbId) === String(selectedKbId) }"
            @click="selectedKbId = String(kb.id || kb.kbId)"
          >
            <span class="mini-main">{{ kb.name || '未命名知识库' }}</span>
            <span class="mini-sub">{{ shortText(kb.description || '暂无描述') }}</span>
          </button>
        </div>
        <el-empty v-else description="暂无知识库" :image-size="52" />
      </div>

      <div class="sidebar-block">
        <div class="block-title">图谱概览</div>
        <div class="mini-list" v-if="graphRows.length">
          <button
            v-for="graph in graphRows.slice(0, 5)"
            :key="graph.id"
            class="mini-item"
            @click="openGraph(graph)"
          >
            <span class="mini-main">{{ graph.name || '未命名图谱' }}</span>
            <span class="mini-sub">{{ graphStatusText(graph.buildStatus) }} · {{ graph.entityCount || 0 }}E / {{ graph.relationCount || 0 }}R</span>
          </button>
        </div>
        <el-empty v-else description="暂无图谱" :image-size="52" />
      </div>

      <el-button class="full-width" :icon="Refresh" @click="loadData">刷新数据</el-button>
    </aside>

    <main class="workspace-main">
      <div class="main-hero">您好，有什么可以帮您？</div>

      <div class="composer">
        <div class="composer-meta">
          <span class="meta-pill">模式：检索</span>
          <span class="meta-pill">知识库：{{ selectedKbName }}</span>
          <span class="meta-pill">TopK：{{ topK }}</span>
        </div>

        <el-input
          v-model="query"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="问点什么？输入问题或关键词，按 Ctrl + Enter 检索"
          class="composer-input"
          @keydown.ctrl.enter.prevent="runSearch"
        />

        <div class="composer-actions">
          <el-button type="primary" :loading="retrievalLoading" @click="runSearch">检索</el-button>
        </div>
      </div>

      <div class="result-panel">
        <div class="result-header">
          <span>检索结果</span>
          <span class="result-meta" v-if="hasSearched">{{ retrievalResults.length }} 条 · {{ executionTime }} ms</span>
        </div>

        <el-empty v-if="!hasSearched" description="输入问题后开始检索" />
        <el-empty v-else-if="retrievalResults.length === 0 && !retrievalLoading" description="没有命中结果" />

        <div class="result-list" v-else v-loading="retrievalLoading">
          <article v-for="(item, idx) in retrievalResults" :key="`${item.sourceDoc}-${idx}`" class="result-item">
            <div class="item-head">
              <div class="item-left">
                <span class="item-index">{{ String(idx + 1).padStart(2, '0') }}</span>
                <el-tag size="small" :type="item.matchType === 'VECTOR' ? 'success' : 'warning'" effect="plain">
                  {{ item.matchType === 'VECTOR' ? '语义' : '关键词' }}
                </el-tag>
                <span class="item-source">{{ item.sourceDoc }}</span>
              </div>
              <span class="item-score">{{ (item.score * 100).toFixed(1) }}%</span>
            </div>
            <p class="item-content">{{ item.content }}</p>
          </article>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Grid, Refresh } from '@element-plus/icons-vue'
import { ROUTES } from '@/router/routes'
import { getGraphList, getKnowledgeList, testRetrieval } from '@/api/knowledge'

const router = useRouter()

const loading = ref(false)
const retrievalLoading = ref(false)
const hasSearched = ref(false)
const executionTime = ref(0)

const query = ref('')
const topK = ref(8)
const selectedKbId = ref('all')

const knowledgeRows = ref([])
const graphRows = ref([])
const retrievalResults = ref([])

const kbOptions = computed(() => {
  const options = [{ id: 'all', name: '全部知识库' }]
  for (const kb of knowledgeRows.value) {
    const id = kb.id ?? kb.kbId
    if (id) options.push({ id: String(id), name: kb.name || `知识库 ${id}` })
  }
  return options
})

const selectedKbName = computed(() => kbOptions.value.find((item) => String(item.id) === String(selectedKbId.value))?.name || '全部知识库')

const loadData = async () => {
  loading.value = true
  try {
    const [kbs, graphs] = await Promise.all([getKnowledgeList(), getGraphList()])
    knowledgeRows.value = Array.isArray(kbs) ? kbs : []
    graphRows.value = Array.isArray(graphs) ? graphs : []
  } catch {
    ElMessage.error('加载知识中心数据失败')
  } finally {
    loading.value = false
  }
}

const runSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }

  retrievalLoading.value = true
  hasSearched.value = true
  const startedAt = Date.now()

  try {
    const response = await testRetrieval({
      query: query.value.trim(),
      kbId: selectedKbId.value,
      topK: topK.value
    })

    const items = Array.isArray(response) ? response : (response?.data || response?.results || [])
    retrievalResults.value = items.map((item) => ({
      score: item.score ?? 0,
      content: item.content ?? '',
      sourceDoc: item.metadata?.source || item.metadata?.doc_id || '未知文档',
      matchType: item.matchType || (item.score > 0.4 ? 'VECTOR' : 'KEYWORD')
    }))

    executionTime.value = Date.now() - startedAt
  } catch {
    retrievalResults.value = []
    ElMessage.error('检索失败，请检查后端连接')
  } finally {
    retrievalLoading.value = false
  }
}

const shortText = (text) => (text.length > 34 ? `${text.slice(0, 34)}...` : text)

const graphStatusText = (status) => {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'BUILDING') return '构建中'
  if (status === 'ENTITY_EXTRACTING') return '实体抽取中'
  if (status === 'RELATION_EXTRACTING') return '关系抽取中'
  if (status === 'FAILED') return '失败'
  return '待构建'
}

const goAssets = () => router.push(ROUTES.KNOWLEDGE.ASSETS)

const openGraph = (graph) => {
  if (graph?.id) router.push(ROUTES.KNOWLEDGE.GRAPH_DETAIL.replace(':id', graph.id))
}

onMounted(loadData)
</script>

<style scoped>
.knowledge-workspace {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  min-height: calc(100vh - 180px);
}

.workspace-sidebar {
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: #f8fafc;
  padding: 12px;
  display: grid;
  gap: 12px;
  align-content: start;
}

.sidebar-block {
  border: 1px solid #e6ebf5;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 10px;
}

.field-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
  display: block;
}

.field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
}

.field-row.compact {
  justify-content: flex-start;
  gap: 12px;
}

.meta-text {
  font-size: 12px;
  color: #64748b;
}

.topk-input {
  width: 120px;
}

.full-width {
  width: 100%;
}

.mini-list {
  display: grid;
  gap: 8px;
}

.mini-item {
  width: 100%;
  text-align: left;
  border: 1px solid #e7ecf6;
  border-radius: 10px;
  background: #f8fafc;
  padding: 10px;
  cursor: pointer;
}

.mini-item.active,
.mini-item:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.mini-main {
  display: block;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.mini-sub {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.workspace-main {
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: #f8fafc;
  padding: 22px;
  display: grid;
  gap: 14px;
  align-content: start;
}

.main-hero {
  text-align: center;
  font-size: 46px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  margin: 4px 0 10px;
}

.composer {
  border: 1px solid #d9e2ef;
  background: #fff;
  border-radius: 18px;
  padding: 14px;
}

.composer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.meta-pill {
  border: 1px solid #dbe4f2;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
}

.composer-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  font-size: 20px;
  line-height: 1.5;
  padding: 6px 0;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.result-panel {
  border: 1px solid #e3e9f4;
  border-radius: 14px;
  background: #fff;
  padding: 12px;
  min-height: 260px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-header > span:first-child {
  color: #0f172a;
  font-weight: 600;
}

.result-meta {
  color: #64748b;
  font-size: 12px;
}

.result-list {
  display: grid;
  gap: 10px;
  max-height: 380px;
  overflow: auto;
}

.result-item {
  border: 1px solid #e7ecf6;
  border-radius: 12px;
  background: #f8fafc;
  padding: 10px;
}

.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.item-index {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.item-source {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-score {
  font-size: 12px;
  color: #0f172a;
  font-weight: 600;
}

.item-content {
  margin: 8px 0 0;
  color: #334155;
  line-height: 1.6;
}

@media (max-width: 1200px) {
  .knowledge-workspace {
    grid-template-columns: 1fr;
  }

  .main-hero {
    font-size: 34px;
  }

  .composer-input :deep(.el-textarea__inner) {
    font-size: 16px;
  }
}

html.dark .knowledge-workspace {
  background: transparent;
}

html.dark .workspace-sidebar,
html.dark .workspace-main {
  background: rgba(15, 23, 42, 0.84);
  border-color: rgba(71, 85, 105, 0.5);
}

html.dark .sidebar-block,
html.dark .composer,
html.dark .result-panel,
html.dark .result-item,
html.dark .mini-item {
  background: rgba(30, 41, 59, 0.76);
  border-color: rgba(71, 85, 105, 0.5);
}

html.dark .mini-item.active,
html.dark .mini-item:hover {
  background: rgba(15, 23, 42, 0.9);
  border-color: rgba(45, 212, 191, 0.36);
}

html.dark .main-hero,
html.dark .result-header > span:first-child,
html.dark .item-score,
html.dark .mini-main,
html.dark .block-title {
  color: #e2e8f0;
}

html.dark .field-label,
html.dark .meta-text,
html.dark .mini-sub,
html.dark .result-meta,
html.dark .item-index,
html.dark .item-source,
html.dark .item-content {
  color: #94a3b8;
}

html.dark .composer-input :deep(.el-textarea__inner) {
  background: transparent;
  color: #e2e8f0;
}

html.dark .workspace-sidebar :deep(.el-input__wrapper),
html.dark .workspace-sidebar :deep(.el-select__wrapper),
html.dark .workspace-sidebar :deep(.el-input-number),
html.dark .workspace-sidebar :deep(.el-input-number__decrease),
html.dark .workspace-sidebar :deep(.el-input-number__increase) {
  background: rgba(15, 23, 42, 0.78) !important;
  color: #e2e8f0 !important;
  box-shadow: inset 0 0 0 1px rgba(71, 85, 105, 0.58) !important;
}
</style>
