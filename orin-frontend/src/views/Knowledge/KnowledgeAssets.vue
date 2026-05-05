<template>
  <div class="knowledge-assets-page">
    <OrinPageShell
      title="知识资产"
      description="管理知识库、文档与图谱资产的绑定关系"
      icon="Collection"
    >
      <template #actions>
        <el-button type="primary" :icon="Refresh" @click="loadData">刷新</el-button>
        <el-button plain :icon="ArrowLeft" @click="goCenter">返回知识库</el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="keyword"
            placeholder="搜索知识库名、描述、图谱名"
            clearable
            :prefix-icon="Search"
            style="width: 320px"
          />
          <el-select
            v-model="graphFilter"
            clearable
            placeholder="图谱状态"
            style="width: 160px"
          >
            <el-option label="未关联图谱" value="NONE" />
            <el-option label="待构建" value="PENDING" />
            <el-option label="构建中" value="BUILDING" />
            <el-option label="已完成" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-select
            v-model="typeFilter"
            clearable
            placeholder="知识类型"
            style="width: 140px"
          >
            <el-option label="非结构化" value="UNSTRUCTURED" />
            <el-option label="结构化" value="STRUCTURED" />
            <el-option label="流程型" value="PROCEDURAL" />
            <el-option label="记忆型" value="META_MEMORY" />
          </el-select>
          <el-button text @click="resetFilters">重置筛选</el-button>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <section class="workbench-overview">
      <article class="overview-card primary">
        <span class="overview-label">资产总量</span>
        <strong class="overview-value">{{ rows.length }}</strong>
        <span class="overview-note">知识库与图谱 1:1 映射资产数</span>
      </article>
      <article class="overview-card success">
        <span class="overview-label">已绑定 1:1</span>
        <strong class="overview-value">{{ linkedCount }}</strong>
        <span class="overview-note">已建立知识库与图谱对应关系</span>
      </article>
      <article class="overview-card neutral">
        <span class="overview-label">图谱就绪</span>
        <strong class="overview-value">{{ readyCount }}</strong>
        <span class="overview-note">图谱构建完成，可直接查询</span>
      </article>
      <article class="overview-card warning">
        <span class="overview-label">待处理</span>
        <strong class="overview-value">{{ issueCount }}</strong>
        <span class="overview-note">未绑定、构建中或失败资产</span>
      </article>
    </section>

    <div class="split-workbench" v-loading="loading">
      <!-- ── Left: Enhanced asset list ─────────────────────────── -->
      <aside class="asset-list">
        <div class="list-head">
          <span class="list-title">资产列表</span>
          <span class="list-count">{{ filteredRows.length }} / {{ rows.length }}</span>
        </div>

        <div v-if="!filteredRows.length && !loading" class="list-empty">
          <OrinEmptyState
            description="暂无符合条件的知识资产，请调整筛选或返回知识库创建"
            action-label="返回知识库"
            @action="goCenter"
          />
        </div>

        <div v-else class="list-scroll">
          <button
            v-for="row in filteredRows"
            :key="row.kb.id || row.kb.kbId"
            class="asset-row"
            :class="{ active: isSelected(row) }"
            @click="select(row)"
          >
            <span
              class="status-bar"
              :class="`status-${statusKey(row)}`"
            />
            <div class="row-body">
              <div class="row-top">
                <span class="row-name">{{ row.kb.name || '未命名知识库' }}</span>
                <el-tag size="small" effect="plain" class="row-type">
                  {{ formatKnowledgeType(row.kb.type) }}
                </el-tag>
              </div>
              <div class="row-desc">
                {{ row.kb.description || '暂无描述' }}
              </div>
              <div class="row-meta">
                <span class="meta-dot" :class="`dot-${statusKey(row)}`" />
                <span class="meta-status">{{ statusLabel(row) }}</span>
                <span v-if="row.graph && hasErCount(row.graph)" class="meta-er">
                  {{ entityCountOf(row.graph) }}E / {{ relationCountOf(row.graph) }}R
                </span>
                <span class="meta-time">{{ formatTime(resolveUpdatedAt(row)) }}</span>
              </div>
            </div>
          </button>
        </div>
      </aside>

      <!-- ── Right: Detail panel ───────────────────────────────── -->
      <section class="asset-detail">
        <div v-if="!selected" class="detail-empty">
          <el-icon class="empty-icon"><Collection /></el-icon>
          <div class="empty-title">选择一个知识资产查看详情</div>
          <div class="empty-sub">资产以 1:1 绑定：一个知识库对应一个知识图谱</div>
        </div>

        <template v-else>
          <div class="detail-hero">
            <div class="detail-toolbar">
              <div class="detail-title-wrap">
                <div class="detail-title">
                  {{ selected.kb.name || '未命名知识库' }}
                </div>
                <div class="detail-subtitle">
                  {{ selected.graph ? '知识库已绑定图谱，可进行联动管理' : '当前资产未绑定图谱，请先完成绑定' }}
                </div>
              </div>
              <div class="detail-toolbar-actions">
                <el-button type="primary" :icon="Reading" @click="openKb(selected.kb)">
                  打开知识库
                </el-button>
                <el-button
                  v-if="selected.graph"
                  :icon="Share"
                  @click="openGraph(selected.graph)"
                >
                  打开图谱
                </el-button>
                <el-button
                  v-else
                  :icon="Plus"
                  @click="openKb(selected.kb)"
                >
                  去知识库绑定图谱
                </el-button>
              </div>
            </div>
            <div class="hero-metrics">
              <div class="hero-metric">
                <span>实体数</span>
                <strong>{{ selected.graph ? entityCountOf(selected.graph) : '—' }}</strong>
              </div>
              <div class="hero-metric">
                <span>关系数</span>
                <strong>{{ selected.graph ? relationCountOf(selected.graph) : '—' }}</strong>
              </div>
              <div class="hero-metric">
                <span>构建状态</span>
                <strong>{{ selected.graph ? graphStatusText(selected.graph.buildStatus) : '未关联' }}</strong>
              </div>
              <div class="hero-metric">
                <span>更新时间</span>
                <strong>{{ formatTime(resolveUpdatedAt(selected)) }}</strong>
              </div>
            </div>
          </div>

          <div class="kb-summary-card">
            <div class="kb-summary-meta">
              <el-tag size="small" effect="plain">{{ formatKnowledgeType(selected.kb.type) }}</el-tag>
              <span class="kb-summary-item">文档数 {{ kbDocumentCount(selected.kb) }}</span>
              <span class="kb-summary-item">状态 {{ kbStatusText(selected.kb.status) }}</span>
              <span class="kb-summary-item">最近更新 {{ formatTime(resolveUpdatedAt(selected)) }}</span>
            </div>
            <div class="kb-summary-desc">
              {{ selected.kb.description || '（暂未填写描述）' }}
            </div>
          </div>

          <!-- Binding visualization -->
          <div class="binding-block">
            <div class="binding-card kb">
              <div class="card-label">
                <el-icon><Reading /></el-icon>
                <span>知识库</span>
              </div>
              <div class="card-name">{{ selected.kb.name || '未命名知识库' }}</div>
              <div class="card-sub">
                <el-tag size="small" effect="plain">{{ formatKnowledgeType(selected.kb.type) }}</el-tag>
                <span class="card-time">更新于 {{ formatTime(resolveUpdatedAt(selected)) }}</span>
              </div>
            </div>

            <div class="binding-link" :class="selected.graph ? 'linked' : 'unlinked'">
              <span class="link-line" />
              <span class="link-chip">{{ selected.graph ? '1:1 已绑定' : '未绑定' }}</span>
              <span class="link-line" />
            </div>

            <div class="binding-card graph" :class="{ disabled: !selected.graph }">
              <div class="card-label">
                <el-icon><Share /></el-icon>
                <span>知识图谱</span>
              </div>
              <template v-if="selected.graph">
                <div class="card-name">{{ selected.graph.name || '未命名图谱' }}</div>
                <div class="card-sub">
                  <el-tag
                    size="small"
                    :type="graphTagType(selected.graph.buildStatus)"
                    effect="light"
                  >
                    {{ graphStatusText(selected.graph.buildStatus) }}
                  </el-tag>
                  <span class="card-time">{{ coverageText(selected.graph) }}</span>
                </div>
              </template>
              <template v-else>
                <div class="card-name muted">暂未绑定图谱</div>
                <div class="card-sub muted">前往知识库配置以创建关联图谱</div>
              </template>
            </div>
          </div>

          <!-- Stats grid -->
          <div class="stats-grid">
            <div class="stat-tile">
              <div class="stat-key">实体数</div>
              <div class="stat-val">{{ selected.graph ? entityCountOf(selected.graph) : '—' }}</div>
            </div>
            <div class="stat-tile">
              <div class="stat-key">关系数</div>
              <div class="stat-val">{{ selected.graph ? relationCountOf(selected.graph) : '—' }}</div>
            </div>
            <div class="stat-tile">
              <div class="stat-key">图谱状态</div>
              <div class="stat-val">
                <el-tag
                  v-if="selected.graph"
                  size="small"
                  :type="graphTagType(selected.graph.buildStatus)"
                  effect="light"
                >
                  {{ graphStatusText(selected.graph.buildStatus) }}
                </el-tag>
                <el-tag v-else size="small" type="info" effect="plain">未关联</el-tag>
              </div>
            </div>
            <div class="stat-tile">
              <div class="stat-key">知识类型</div>
              <div class="stat-val">{{ formatKnowledgeType(selected.kb.type) }}</div>
            </div>
          </div>

          <!-- Build status card -->
          <div v-if="selected.graph" class="section-card">
            <div class="section-title">构建信息</div>
            <div class="info-row">
              <span class="info-key">构建进度</span>
              <div class="info-val progress">
                <el-progress
                  :percentage="buildProgress(selected.graph)"
                  :status="progressStatus(selected.graph.buildStatus)"
                  :stroke-width="8"
                />
              </div>
            </div>
            <div class="info-row" v-if="selected.graph.errorMessage">
              <span class="info-key">错误信息</span>
              <span class="info-val error">{{ selected.graph.errorMessage }}</span>
            </div>
            <div class="info-row">
              <span class="info-key">最近更新</span>
              <span class="info-val">
                {{ formatTime(resolveUpdatedAt(selected)) }}
              </span>
            </div>
          </div>

          <!-- Action bar -->
          <div
            v-if="selected.graph && ['FAILED', 'PENDING'].includes(selected.graph.buildStatus)"
            class="action-bar"
          >
            <el-button
              :icon="VideoPlay"
              :loading="rebuilding"
              @click="rebuild(selected.graph)"
            >
              {{ selected.graph.buildStatus === 'FAILED' ? '重新构建' : '开始构建' }}
            </el-button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Collection,
  Plus,
  Reading,
  Refresh,
  Search,
  Share,
  VideoPlay
} from '@element-plus/icons-vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import { ROUTES } from '@/router/routes'
import { buildGraph, getGraphEntities, getGraphList, getGraphRelations, getKnowledgeList } from '@/api/knowledge'

const router = useRouter()

const loading = ref(false)
const rebuilding = ref(false)
const rows = ref([])

const keyword = ref('')
const typeFilter = ref('')
const graphFilter = ref('')
const selectedId = ref(null)
const graphCountOverrides = ref({})

const linkedCount = computed(() => rows.value.filter((row) => row.graph).length)
const readyCount = computed(() => rows.value.filter((row) => row.graph?.buildStatus === 'SUCCESS').length)
const issueCount = computed(() => rows.value.length - readyCount.value)

const rowId = (row) => String(row?.kb?.id ?? row?.kb?.kbId ?? '')

const matchQuery = (row) => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return true
  const values = [
    row.kb.name,
    row.kb.description,
    row.kb.type,
    row.graph?.name,
    row.graph?.description,
    row.graph?.buildStatus
  ]
  return values.some((item) => String(item || '').toLowerCase().includes(q))
}

const matchGraphStatus = (row) => {
  if (!graphFilter.value) return true
  if (graphFilter.value === 'NONE') return !row.graph
  if (!row.graph) return false
  if (graphFilter.value === 'BUILDING') {
    return ['BUILDING', 'ENTITY_EXTRACTING', 'RELATION_EXTRACTING'].includes(row.graph.buildStatus)
  }
  return (row.graph.buildStatus || 'PENDING') === graphFilter.value
}

const STATUS_ORDER = { failed: 0, building: 1, pending: 2, unlinked: 3, ready: 4 }

const filteredRows = computed(() => {
  const result = rows.value.filter((row) => {
    const byType = !typeFilter.value || (row.kb.type || 'UNSTRUCTURED') === typeFilter.value
    return byType && matchGraphStatus(row) && matchQuery(row)
  })
  return [...result].sort((a, b) => {
    const diff = STATUS_ORDER[statusKey(a)] - STATUS_ORDER[statusKey(b)]
    if (diff !== 0) return diff
    return String(a.kb.name || '').localeCompare(String(b.kb.name || ''))
  })
})

const selected = computed(() => {
  if (!selectedId.value) return null
  return rows.value.find((row) => rowId(row) === String(selectedId.value)) || null
})

const isSelected = (row) => rowId(row) === String(selectedId.value)

const select = (row) => {
  selectedId.value = rowId(row)
}

const resetFilters = () => {
  keyword.value = ''
  typeFilter.value = ''
  graphFilter.value = ''
}

const loadData = async () => {
  loading.value = true
  try {
    const [kbs, graphs] = await Promise.all([getKnowledgeList(), getGraphList()])
    const kbList = Array.isArray(kbs) ? kbs : []
    const graphList = Array.isArray(graphs) ? graphs : []

    const graphMap = new Map()
    for (const graph of graphList) {
      if (!graph.knowledgeBaseId) continue
      graphMap.set(String(graph.knowledgeBaseId), graph)
    }

    rows.value = kbList.map((kb) => ({
      kb,
      graph: graphMap.get(String(kb.id ?? kb.kbId)) || null
    }))
    graphCountOverrides.value = {}
  } catch (error) {
    ElMessage.error('加载知识资产失败')
  } finally {
    loading.value = false
  }
}

watch(filteredRows, (list) => {
  if (!list.length) {
    selectedId.value = null
    return
  }
  const stillVisible = list.some((row) => rowId(row) === String(selectedId.value))
  if (!stillVisible) {
    selectedId.value = rowId(list[0])
  }
})

const openKb = (kb) => {
  const id = kb?.id ?? kb?.kbId
  if (!id) return
  router.push(ROUTES.KNOWLEDGE.DETAIL.replace(':id', id))
}

const openGraph = (graph) => {
  if (!graph?.id) return
  router.push(ROUTES.KNOWLEDGE.GRAPH_DETAIL.replace(':id', graph.id))
}

const rebuild = async (graph) => {
  if (!graph?.id) return
  rebuilding.value = true
  try {
    await buildGraph(graph.id)
    ElMessage.success('已触发图谱构建')
    await loadData()
  } catch {
    ElMessage.error('触发构建失败，请稍后重试')
  } finally {
    rebuilding.value = false
  }
}

const goCenter = () => router.push(ROUTES.KNOWLEDGE.CENTER)

const formatKnowledgeType = (type) => {
  if (type === 'UNSTRUCTURED' || type === 'DOCUMENT') return '非结构化'
  if (type === 'STRUCTURED') return '结构化'
  if (type === 'PROCEDURAL') return '流程型'
  if (type === 'META_MEMORY') return '记忆型'
  return type || '未知'
}

const graphStatusText = (status) => {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'BUILDING') return '构建中'
  if (status === 'ENTITY_EXTRACTING') return '实体抽取'
  if (status === 'RELATION_EXTRACTING') return '关系抽取'
  if (status === 'FAILED') return '失败'
  return '待构建'
}

const graphTagType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (['BUILDING', 'ENTITY_EXTRACTING', 'RELATION_EXTRACTING'].includes(status)) return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const statusKey = (row) => {
  if (!row.graph) return 'unlinked'
  const status = row.graph.buildStatus
  if (status === 'SUCCESS') return 'ready'
  if (status === 'FAILED') return 'failed'
  if (['BUILDING', 'ENTITY_EXTRACTING', 'RELATION_EXTRACTING'].includes(status)) return 'building'
  return 'pending'
}

const statusLabel = (row) => {
  const key = statusKey(row)
  if (key === 'unlinked') return '未绑定图谱'
  return graphStatusText(row.graph.buildStatus)
}

const hasErCount = (graph) => {
  if (!graph) return false
  return Number(entityCountOf(graph)) > 0 || Number(relationCountOf(graph)) > 0
}

const coverageText = (graph) => {
  if (!graph) return '未绑定图谱'
  if (graph.buildStatus === 'SUCCESS') {
    if (hasErCount(graph)) return `${entityCountOf(graph)} 实体 / ${relationCountOf(graph)} 关系`
    return '已构建（暂无实体/关系）'
  }
  if (['BUILDING', 'ENTITY_EXTRACTING', 'RELATION_EXTRACTING'].includes(graph.buildStatus)) return '构建进行中'
  if (graph.buildStatus === 'FAILED') return '构建失败，待处理'
  return '待构建'
}

const buildProgress = (graph) => {
  if (!graph) return 0
  if (graph.buildStatus === 'SUCCESS') return 100
  if (graph.buildStatus === 'FAILED') return 100
  if (graph.buildStatus === 'ENTITY_EXTRACTING') return 40
  if (graph.buildStatus === 'RELATION_EXTRACTING') return 75
  if (graph.buildStatus === 'BUILDING') return 60
  return 0
}

const progressStatus = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  return undefined
}

const graphKey = (graph) => String(graph?.id || '')

const entityCountOf = (graph) => {
  const key = graphKey(graph)
  const override = graphCountOverrides.value[key]
  if (override && Number.isFinite(Number(override.entityCount))) return Number(override.entityCount)
  return Number(graph?.entityCount ?? 0)
}

const relationCountOf = (graph) => {
  const key = graphKey(graph)
  const override = graphCountOverrides.value[key]
  if (override && Number.isFinite(Number(override.relationCount))) return Number(override.relationCount)
  return Number(graph?.relationCount ?? 0)
}

const resolveTotal = (result, fallback = 0) => {
  if (!result || typeof result !== 'object') return fallback
  if (Number.isFinite(Number(result.totalElements))) return Number(result.totalElements)
  if (Number.isFinite(Number(result.total))) return Number(result.total)
  if (Array.isArray(result.content)) return result.content.length
  if (Array.isArray(result.records)) return result.records.length
  return fallback
}

const ensureGraphCounts = async (graph) => {
  const key = graphKey(graph)
  if (!key) return
  if (graphCountOverrides.value[key]?.loaded) return

  const currentEntityCount = Number(graph?.entityCount ?? 0)
  const currentRelationCount = Number(graph?.relationCount ?? 0)
  if (currentEntityCount > 0 || currentRelationCount > 0) {
    graphCountOverrides.value[key] = {
      entityCount: currentEntityCount,
      relationCount: currentRelationCount,
      loaded: true
    }
    return
  }

  try {
    const [entityPage, relationPage] = await Promise.all([
      getGraphEntities(graph.id, { page: 0, size: 1 }),
      getGraphRelations(graph.id, { page: 0, size: 1 })
    ])
    graphCountOverrides.value[key] = {
      entityCount: resolveTotal(entityPage, currentEntityCount),
      relationCount: resolveTotal(relationPage, currentRelationCount),
      loaded: true
    }
  } catch {
    graphCountOverrides.value[key] = {
      entityCount: currentEntityCount,
      relationCount: currentRelationCount,
      loaded: true
    }
  }
}

watch(selected, (row) => {
  if (!row?.graph?.id) return
  if (row.graph.buildStatus !== 'SUCCESS') return
  if (Number(row.graph.entityCount || 0) > 0 || Number(row.graph.relationCount || 0) > 0) return
  ensureGraphCounts(row.graph)
})

const kbDocumentCount = (kb) => {
  const value = kb?.stats?.documentCount
  if (!Number.isFinite(Number(value))) return '—'
  return Number(value)
}

const kbStatusText = (status) => {
  if (status === 'ENABLED') return '启用'
  if (status === 'DISABLED') return '停用'
  return status || '未知'
}

const resolveUpdatedAt = (row) => {
  const kb = row?.kb || {}
  const graph = row?.graph || {}
  return kb.updatedAt
    || kb.gmtModified
    || kb.modifiedAt
    || kb.syncTime
    || kb.createdAt
    || graph.updatedAt
    || graph.gmtModified
    || graph.lastBuildAt
    || graph.lastSuccessBuildAt
    || graph.createdAt
    || null
}

const formatTime = (value) => value ? dayjs(value).format('MM-DD HH:mm') : '—'

onMounted(loadData)
</script>

<style scoped>
.knowledge-assets-page {
  --ka-accent: #0f9d8a;
  --ka-accent-strong: #0a8b79;
  --ka-accent-soft: #ecfaf6;
  --ka-text-main: #0f172a;
  --ka-text-sub: #334155;
  --ka-text-faint: #64748b;
  --ka-border: #d6e5e1;
  --ka-border-soft: #e6efec;
  --ka-bg-soft: #f4f9f8;

  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workbench-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.overview-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid var(--ka-border-soft);
  border-radius: 10px;
  background: #fff;
  padding: 10px 14px;
  min-height: 78px;
}

.overview-card.primary {
  background: #ffffff;
}

.overview-card.success {
  background: #ffffff;
}

.overview-card.neutral {
  background: #ffffff;
}

.overview-card.warning {
  background: #ffffff;
}

.overview-label {
  font-size: 12px;
  color: #73838f;
}

.overview-value {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  color: var(--ka-text-main);
  font-variant-numeric: tabular-nums;
}

.overview-note {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ka-text-faint);
}

.split-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 34%) minmax(0, 1fr);
  gap: 12px;
  min-height: clamp(520px, calc(100vh - 360px), 640px);
  align-items: stretch;
}

.asset-list {
  border: 1px solid var(--ka-border);
  border-radius: 12px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.list-head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--ka-border-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
}

.list-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ka-text-main);
}

.list-count {
  font-size: 12px;
  color: #7a8c97;
  font-variant-numeric: tabular-nums;
}

.list-empty {
  padding: 40px 16px;
  display: flex;
  justify-content: center;
}

.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.asset-row {
  all: unset;
  display: flex;
  gap: 10px;
  width: 100%;
  box-sizing: border-box;
  padding: 11px 10px 11px 8px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  position: relative;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.asset-row + .asset-row {
  margin-top: 6px;
}

.asset-row:hover {
  background: #f7fbfa;
  border-color: #d3e7e1;
}

.asset-row.active {
  background: var(--ka-accent-soft);
  border-color: #b7dfd3;
  box-shadow: 0 10px 22px rgba(12, 147, 130, 0.14);
  transform: translateY(-1px);
}

.status-bar {
  width: 3px;
  align-self: stretch;
  border-radius: 2px;
  flex-shrink: 0;
}

.status-bar.status-ready { background: #16a34a; }
.status-bar.status-building { background: #f59e0b; }
.status-bar.status-pending { background: #94a3b8; }
.status-bar.status-failed { background: #dc2626; }
.status-bar.status-unlinked { background: #cbd5e1; }

.row-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.row-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.asset-row.active .row-name {
  color: #0c7f71;
}

.row-type {
  flex-shrink: 0;
  font-size: 11px;
}

.row-desc {
  font-size: 13px;
  color: var(--ka-text-faint);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.row-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8193a0;
  margin-top: 2px;
}

.meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.meta-dot.dot-ready { background: #16a34a; }
.meta-dot.dot-building { background: #f59e0b; box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.15); }
.meta-dot.dot-pending { background: #94a3b8; }
.meta-dot.dot-failed { background: #dc2626; }
.meta-dot.dot-unlinked { background: #cbd5e1; }

.meta-status {
  color: var(--ka-text-sub);
  font-weight: 500;
}

.meta-er {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: #4b5e6a;
}

.meta-time {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.meta-er + .meta-time {
  margin-left: 0;
}

.asset-detail {
  border: 1px solid var(--ka-border);
  border-radius: 12px;
  background: #ffffff;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.detail-empty {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 48px;
  color: #cbd5e1;
}

.empty-title {
  font-size: 15px;
  color: var(--ka-text-sub);
  font-weight: 600;
}

.empty-sub {
  font-size: 12px;
  color: #94a3b8;
}

.detail-hero {
  border: 1px solid var(--ka-border-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-title-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-title {
  font-size: 24px;
  line-height: 1.18;
  font-weight: 700;
  color: var(--ka-text-main);
  min-width: 0;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.detail-subtitle {
  font-size: 13px;
  color: var(--ka-text-faint);
}

.detail-toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  flex-shrink: 0;
  max-width: 252px;
}

.detail-toolbar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.hero-metric {
  border: 1px solid #d9ebe6;
  border-radius: 10px;
  padding: 10px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 66px;
}

.hero-metric span {
  font-size: 11px;
  color: #6d808d;
}

.hero-metric strong {
  font-size: 17px;
  color: #0f172a;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.kb-summary-card {
  border: 1px solid var(--ka-border-soft);
  border-radius: 12px;
  background: #fff;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-summary-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.kb-summary-item {
  font-size: 12px;
  color: var(--ka-text-faint);
}

.kb-summary-desc {
  font-size: 13px;
  line-height: 1.7;
  color: var(--ka-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.binding-block {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px minmax(0, 1fr);
  align-items: stretch;
  gap: 8px;
}

.binding-card {
  border: 1px solid var(--ka-border-soft);
  border-radius: 12px;
  padding: 14px 16px;
  background: #f8fcfb;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 98px;
  min-width: 0;
}

.binding-card.kb {
  background: #ffffff;
  border-color: #cbece4;
}

.binding-card.graph {
  background: #ffffff;
  border-color: #d8ece8;
}

.binding-card.disabled {
  background: #ffffff;
  border-color: #e5eaf3;
  border-style: dashed;
}

.card-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ka-text-faint);
  font-weight: 500;
}

.card-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--ka-text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.card-name.muted,
.card-sub.muted {
  color: #94a3b8;
  font-weight: 500;
}

.card-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ka-text-faint);
  flex-wrap: wrap;
}

.card-time {
  color: #94a3b8;
}

.binding-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  pointer-events: none;
}

.link-line {
  display: none;
}

.binding-link.linked .link-line {
  background: var(--ka-accent);
}

.link-chip {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid var(--ka-border-soft);
  color: #475569;
  font-weight: 600;
  white-space: nowrap;
}

.binding-link.linked .link-chip {
  background: #e8faf6;
  color: #0b7e70;
}

.binding-link.unlinked .link-chip {
  background: #fef3f2;
  color: #b91c1c;
}

.binding-link.unlinked .link-line {
  background: #cbd5e1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.stat-tile {
  border: 1px solid #dbe8e4;
  border-radius: 12px;
  padding: 10px;
  background: #ffffff;
}

.stat-key {
  font-size: 11px;
  color: #6f8491;
  margin-bottom: 4px;
}

.stat-val {
  font-size: 18px;
  font-weight: 700;
  color: var(--ka-text-main);
  font-variant-numeric: tabular-nums;
  min-height: 24px;
  display: flex;
  align-items: center;
}

.section-card {
  border: 1px solid #dbe8e4;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fff;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ka-text-main);
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--ka-border-soft);
}

.info-row {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 12px;
  padding: 6px 0;
  align-items: center;
  font-size: 13px;
}

.info-row + .info-row {
  border-top: 1px dashed var(--ka-border-soft);
}

.info-key {
  color: #6f8491;
  font-size: 12px;
}

.info-val {
  color: #334155;
}

.info-val.error {
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.5;
}

.info-val.progress {
  display: flex;
  align-items: center;
}

.info-val.progress :deep(.el-progress) {
  flex: 1;
}

.desc-body {
  font-size: 13px;
  color: var(--ka-text-sub);
  line-height: 1.7;
  white-space: pre-wrap;
  max-height: 176px;
  overflow-y: auto;
  padding-right: 4px;
}

.action-bar {
  display: flex;
  gap: 10px;
  padding-top: 4px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .workbench-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .split-workbench {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .asset-list {
    min-height: 320px;
    max-height: 420px;
  }

  .hero-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .binding-block {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .binding-link {
    position: static;
    transform: none;
    flex-direction: row;
    justify-content: center;
  }

  .binding-link .link-line {
    display: block;
    width: 48px;
  }

  .detail-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .workbench-overview {
    grid-template-columns: 1fr;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
