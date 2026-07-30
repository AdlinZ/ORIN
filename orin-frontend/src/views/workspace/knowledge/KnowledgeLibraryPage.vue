<template>
  <main class="knowledge-library-page">
    <header class="page-header">
      <div>
        <h2>知识库</h2>
        <p>整理 Agent 和工作流可检索的内容；先管理文档，再验证实际召回效果。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="createKnowledgeBase">
        创建知识库
      </el-button>
    </header>

    <el-alert
      v-if="!loading && rows.length > 0 && summary.withContent === 0"
      class="content-blocker"
      type="warning"
      :closable="false"
      show-icon
      title="这些知识库还没有内容"
    >
      <template #default>
        <span>打开一个知识库并上传文档，之后才能测试检索或提供给 Agent 使用。</span>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="知识库内容概览">
      <div>
        <span>已有内容</span>
        <strong class="success-number">{{ summary.withContent }}</strong>
        <small>个可继续验证</small>
      </div>
      <div>
        <span>待添加内容</span>
        <strong>{{ summary.empty }}</strong>
        <small>个空知识库</small>
      </div>
      <div>
        <span>已停用</span>
        <strong>{{ summary.disabled }}</strong>
        <small>个不会参与检索</small>
      </div>
    </section>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索知识库名称或说明"
        class="search-input"
      />
      <el-select v-model="typeFilter" clearable placeholder="全部内容类型" class="type-filter">
        <el-option label="文档" value="UNSTRUCTURED" />
        <el-option label="结构化数据" value="STRUCTURED" />
        <el-option label="流程知识" value="PROCEDURAL" />
        <el-option label="记忆" value="META_MEMORY" />
      </el-select>
      <el-button
        class="secondary-button"
        :icon="Refresh"
        :loading="loading"
        @click="loadKnowledgeBases"
      >
        刷新
      </el-button>
    </div>

    <OrinAsyncState
      :status="loadState.status"
      :error-text="loadErrorText"
      empty-text="还没有知识库。创建一个知识库并添加第一份内容。"
      empty-action-label="创建知识库"
      @retry="loadKnowledgeBases"
      @empty-action="createKnowledgeBase"
    >
      <OrinDataTable
        title="内容库"
        :description="`${filteredRows.length} / ${rows.length} 个知识库`"
      >
        <ResizableTable
          :data="filteredRows"
          border
          stripe
          :row-style="{ cursor: 'pointer' }"
          empty-text="没有符合当前筛选的知识库"
          @row-click="openDetail"
        >
          <el-table-column label="知识库" min-width="300">
            <template #default="{ row }">
              <div class="knowledge-identity">
                <strong>{{ row.name }}</strong>
                <small>{{ row.description || '暂未填写用途说明' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="内容" width="155">
            <template #default="{ row }">
              <div class="content-cell">
                <strong>{{ row.stats.documentCount }} 份</strong>
                <small>{{ typeLabel(row.type) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="准备状态" min-width="205">
            <template #default="{ row }">
              <div class="readiness-cell">
                <el-tag :type="readinessMeta(row).type" size="small">
                  {{ readinessMeta(row).label }}
                </el-tag>
                <small>{{ readinessMeta(row).description }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="235" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openDetail(row)">
                {{ row.stats.documentCount > 0 ? '管理内容' : '添加内容' }}
              </el-button>
              <el-button
                link
                type="success"
                size="small"
                :disabled="row.stats.documentCount === 0 || !row.enabled"
                @click.stop="openRetrieval(row)"
              >
                测试并用于 Agent
              </el-button>
            </template>
          </el-table-column>
        </ResizableTable>

        <div class="advanced-entry">
          <span>图谱、实体和同步等低频能力保留在高级资产视图。</span>
          <el-button link type="primary" @click="openAdvancedAssets">进入高级资产视图</el-button>
        </div>
      </OrinDataTable>
    </OrinAsyncState>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getKnowledgeList } from '@/api/knowledge'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { toKnowledgeListViewModel } from '@/viewmodels'

const router = useRouter()
const route = useRoute()
const rows = ref([])
const keyword = ref('')
const typeFilter = ref('')
const loading = ref(false)
const loadState = reactive({ status: 'loading', error: null })
const loadErrorText = computed(() => (
  loadState.error?.response?.data?.message
  || loadState.error?.message
  || '知识库列表加载失败，请稍后重试'
))

const summary = computed(() => rows.value.reduce((result, row) => {
  if (row.enabled && row.stats.documentCount > 0) result.withContent += 1
  else if (row.enabled) result.empty += 1
  else result.disabled += 1
  return result
}, {
  withContent: 0,
  empty: 0,
  disabled: 0,
}))

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesType = !typeFilter.value || row.type === typeFilter.value
    const matchesQuery = !query || [row.name, row.description]
      .some((value) => String(value || '').toLowerCase().includes(query))
    return matchesType && matchesQuery
  })
})

function readinessMeta(row) {
  if (!row.enabled) {
    return {
      label: '已停用',
      type: 'info',
      description: '不会参与新的检索',
    }
  }
  if (row.stats.documentCount > 0) {
    return {
      label: '已有内容',
      type: 'success',
      description: '可进入检索测试确认效果',
    }
  }
  return {
    label: '待添加内容',
    type: 'warning',
    description: '上传内容后才能检索',
  }
}

function typeLabel(type) {
  const labels = {
    UNSTRUCTURED: '文档',
    STRUCTURED: '结构化数据',
    PROCEDURAL: '流程知识',
    META_MEMORY: '记忆',
  }
  return labels[type] || type || '未分类'
}

async function loadKnowledgeBases() {
  loading.value = true
  loadState.status = 'loading'
  loadState.error = null
  try {
    rows.value = toKnowledgeListViewModel(await getKnowledgeList())
    loadState.status = rows.value.length > 0 ? 'success' : 'empty'
  } catch (error) {
    loadState.status = 'error'
    loadState.error = error
  } finally {
    loading.value = false
  }
}

function createKnowledgeBase() {
  router.push(ROUTES.KNOWLEDGE.CREATE)
}

function openDetail(row) {
  if (!row?.id) return
  router.push(ROUTES.KNOWLEDGE.DETAIL.replace(':id', row.id))
}

function openRetrieval(row) {
  if (!row?.id || row.stats.documentCount === 0 || !row.enabled) return
  const query = { kbId: String(row.id) }
  if (route.query.agentId) query.agentId = String(route.query.agentId)
  router.push({ path: ROUTES.KNOWLEDGE.RETRIEVAL_LAB, query })
}

function openAdvancedAssets() {
  router.push(ROUTES.KNOWLEDGE.ASSETS)
}

onMounted(loadKnowledgeBases)
</script>

<style scoped>
.knowledge-library-page {
  max-width: 1400px;
  padding: 24px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.page-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-bar,
.advanced-entry {
  display: flex;
  align-items: center;
  gap: 10px;
}

.secondary-button {
  color: var(--el-text-color-regular) !important;
  border-color: var(--el-border-color) !important;
  background: transparent !important;
}

.secondary-button:hover {
  color: var(--el-color-primary) !important;
  border-color: var(--el-color-primary) !important;
}

.content-blocker {
  margin-bottom: 16px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.summary-strip > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 16px 20px;
}

.summary-strip > div + div {
  border-left: 1px solid var(--el-border-color);
}

.summary-strip span,
.summary-strip small {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-strip strong {
  margin-left: auto;
  font-size: 24px;
}

.summary-strip .success-number {
  color: var(--el-color-success);
}

.filter-bar {
  margin-bottom: 16px;
}

.search-input {
  width: min(360px, 100%);
}

.type-filter {
  width: 170px;
}

.advanced-entry {
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.knowledge-identity,
.content-cell,
.readiness-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.knowledge-identity small,
.content-cell small,
.readiness-cell small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.readiness-cell {
  align-items: flex-start;
}

@media (max-width: 880px) {
  .knowledge-library-page { padding: 16px; }
  .page-header,
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }
  .summary-strip { grid-template-columns: repeat(2, 1fr); }
  .summary-strip > div:nth-child(3) {
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
  .search-input,
  .type-filter { width: 100%; }
}
</style>
