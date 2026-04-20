<template>
  <div class="graph-page">
    <!-- ── 顶部工具栏 ── -->
    <div class="top-bar">
      <div class="top-left">
        <el-button :icon="ArrowLeft" size="small" text @click="goBack">返回</el-button>
        <span class="graph-title">{{ graph?.name || '知识图谱' }}</span>
        <el-tag :type="getStatusType(graph?.buildStatus)" size="small" effect="plain">
          {{ getStatusText(graph?.buildStatus) }}
        </el-tag>
        <el-tooltip
          v-if="graph?.buildStatus === 'FAILED' && graph?.errorMessage"
          :content="graph.errorMessage"
          placement="bottom"
          :show-after="200"
        >
          <el-icon style="color: #ef4444; cursor: help; margin-left: 2px"><WarningFilled /></el-icon>
        </el-tooltip>
      </div>

      <div class="top-center">
        <el-input
          v-model="searchKeyword"
          placeholder="输入要查询的实体 (*为全部)"
          size="small"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="clearSearch"
        >
          <template #suffix>
            <el-icon style="cursor:pointer" @click="handleSearch"><Search /></el-icon>
          </template>
        </el-input>

        <el-input-number
          v-model="nodeLimit"
          :min="10"
          :max="5000"
          :step="100"
          size="small"
          style="width: 100px"
          controls-position="right"
          @change="reloadViz"
        />
        <el-button :icon="Refresh" size="small" circle @click="reloadViz" />
      </div>

      <div class="top-right">
        <span class="status-dot" :class="{ connected: graph?.buildStatus === 'SUCCESS' }" />
        <span class="status-text">{{ graph?.buildStatus === 'SUCCESS' ? '已构建' : '未构建' }}</span>
        <el-divider direction="vertical" />
        <span class="stat-info">实体 {{ graph?.entityCount || 0 }} · 关系 {{ graph?.relationCount || 0 }}</span>
        <el-button size="small" :icon="Download" @click="exportData">导出数据</el-button>
        <el-button type="primary" size="small" :loading="building" @click="triggerBuild">构建图谱</el-button>
      </div>
    </div>

    <!-- ── 主体：画布 + 详情面板 ── -->
    <div class="main-area">
      <!-- 图谱画布 -->
      <div class="canvas-wrap" v-loading="vizLoading">
        <div v-if="!vizLoading && vizNodes.length === 0" class="empty-center">
          <el-empty description="暂无图谱数据，请先构建">
            <el-button type="primary" @click="triggerBuild">立即构建</el-button>
          </el-empty>
        </div>
        <div v-else ref="chartRef" class="echarts-canvas" />

        <!-- 底部状态栏 -->
        <div v-if="vizNodes.length > 0" class="status-bar">
          <span>节点 <b>{{ vizNodes.length }}</b> / {{ graph?.entityCount || 0 }}</span>
          <span style="margin-left: 20px">边 <b>{{ vizLinks.length }}</b> / {{ graph?.relationCount || 0 }}</span>
        </div>

        <!-- 图例 -->
        <div v-if="categories.length > 0" class="legend-box">
          <div
            v-for="cat in categories"
            :key="cat.name"
            class="legend-item"
            :class="{ hidden: hiddenCategories.has(cat.name) }"
            @click="toggleCategory(cat.name)"
          >
            <span class="legend-dot" :style="{ background: cat.color }" />
            <span>{{ cat.name }}</span>
          </div>
        </div>
      </div>

      <!-- 节点详情面板 -->
      <transition name="slide-panel">
        <div v-if="selectedNode" class="detail-panel">
          <div class="panel-header">
            <span>节点详情</span>
            <el-icon class="close-btn" @click="selectedNode = null"><Close /></el-icon>
          </div>
          <div class="panel-body">
            <div class="prop-row">
              <span class="prop-key">名称</span>
              <span class="prop-val name-val">{{ selectedNode.name }}</span>
            </div>
            <div class="prop-row">
              <span class="prop-key">ID</span>
              <span class="prop-val mono">{{ selectedNode.id }}</span>
            </div>
            <div v-if="selectedNode.filePath" class="prop-row">
              <span class="prop-key">file_path</span>
              <span class="prop-val mono small">{{ selectedNode.filePath }}</span>
            </div>
            <div class="prop-row">
              <span class="prop-key">entity_type</span>
              <span class="prop-val">
                <el-tag size="small" effect="plain">{{ selectedNode.entityType || '未分类' }}</el-tag>
              </span>
            </div>
            <div v-if="selectedNode.createdAt" class="prop-row">
              <span class="prop-key">created_at</span>
              <span class="prop-val">{{ selectedNode.createdAt }}</span>
            </div>
            <div v-if="selectedNode.description" class="prop-row">
              <span class="prop-key">description</span>
              <span class="prop-val">{{ selectedNode.description }}</span>
            </div>
            <div v-if="selectedNode.sourceId" class="prop-row">
              <span class="prop-key">source_id</span>
              <span class="prop-val mono small">{{ selectedNode.sourceId }}</span>
            </div>
            <div class="prop-row">
              <span class="prop-key">entity_id</span>
              <span class="prop-val">{{ selectedNode.name }}</span>
            </div>
            <div v-if="selectedNode.tags?.length" class="prop-row">
              <span class="prop-key">标签</span>
              <span class="prop-val">
                <el-tag v-for="t in selectedNode.tags" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
              </span>
            </div>

            <!-- 关联关系 -->
            <div class="panel-section-title">关联关系 ({{ nodeRelations.length }})</div>
            <div v-if="relationsLoading" class="rel-loading">
              <el-icon class="spin"><Loading /></el-icon>
            </div>
            <div v-else-if="nodeRelations.length === 0" class="rel-empty">暂无关联</div>
            <div v-else class="rel-list">
              <div v-for="rel in nodeRelations" :key="rel.id || rel.relationType" class="rel-item">
                <el-tag size="small" type="warning" effect="plain">{{ rel.relationType }}</el-tag>
                <el-icon><ArrowRight /></el-icon>
                <span class="rel-target">{{ rel.targetName || rel.targetEntityId }}</span>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, Refresh, Download, Close, Loading, ArrowRight, WarningFilled } from '@element-plus/icons-vue'
import { getGraph, getGraphEntities, getGraphRelations, buildGraph, getGraphEntityDetails } from '@/api/knowledge'
import { useTheme } from '@/composables/useTheme'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()
const graphId = computed(() => route.params.id)
const { isDarkMode } = useTheme()

// 主题色系
const theme = computed(() => isDarkMode.value ? {
  bg: '#0f172a', surface: '#1e293b', border: '#334155',
  text: '#e2e8f0', textMuted: '#94a3b8', textDim: '#64748b',
  nodeBorder: '#1e293b', edgeColor: '#334155',
  chartBg: '#0f172a', labelColor: '#cbd5e1',
} : {
  bg: '#f8fafc', surface: '#ffffff', border: '#e2e8f0',
  text: '#0f172a', textMuted: '#475569', textDim: '#94a3b8',
  nodeBorder: '#ffffff', edgeColor: '#cbd5e1',
  chartBg: '#f8fafc', labelColor: '#334155',
})

// ── 状态 ──
const graph = ref(null)
const vizLoading = ref(false)
const building = ref(false)
const vizNodes = ref([])
const vizLinks = ref([])
const categories = ref([])
const hiddenCategories = ref(new Set())
const searchKeyword = ref('')
const nodeLimit = ref(100)
const chartRef = ref(null)
const selectedNode = ref(null)
const nodeRelations = ref([])
const relationsLoading = ref(false)
let chartInstance = null

// 颜色列表
const COLOR_PALETTE = [
  '#5470c6', '#91cc75', '#fac858', '#ee6666',
  '#73c0de', '#3ba272', '#fc8452', '#9a60b4',
  '#ea7ccc', '#0891b2', '#65a30d', '#dc2626',
]

const getColor = (idx) => COLOR_PALETTE[idx % COLOR_PALETTE.length]

// ── 数据加载 ──
const fetchGraph = async () => {
  try { graph.value = await getGraph(graphId.value) } catch {}
}

const fetchVizData = async () => {
  vizLoading.value = true
  try {
    const limit = nodeLimit.value
    const [entRes, relRes] = await Promise.all([
      getGraphEntities(graphId.value, { page: 0, size: limit }),
      getGraphRelations(graphId.value, { page: 0, size: limit * 3 }),
    ])

    const entityList = entRes.content || entRes || []
    const relationList = relRes.content || relRes || []

    // 统计 degree
    const degree = {}
    relationList.forEach(r => {
      degree[r.sourceEntityId] = (degree[r.sourceEntityId] || 0) + 1
      degree[r.targetEntityId] = (degree[r.targetEntityId] || 0) + 1
    })

    // 分类颜色
    const catMap = {}
    entityList.forEach(e => {
      const t = e.entityType || '未分类'
      if (!(t in catMap)) catMap[t] = Object.keys(catMap).length
    })
    categories.value = Object.keys(catMap).map((name, i) => ({ name, color: getColor(i) }))

    const nodeSet = new Set(entityList.map(e => e.id))

    vizNodes.value = entityList.map(e => {
      const t = e.entityType || '未分类'
      const deg = degree[e.id] || 1
      return {
        id: e.id,
        name: e.name,
        entityType: t,
        description: e.description,
        filePath: e.sourceDocumentId,
        sourceId: e.sourceChunkId,
        createdAt: e.createdAt,
        tags: t ? [t] : [],
        catIdx: catMap[t],
        symbolSize: Math.max(14, Math.min(60, 10 + deg * 4)),
        itemStyle: { color: getColor(catMap[t]) },
        label: { show: true },
      }
    })

    vizLinks.value = relationList
      .filter(r => nodeSet.has(r.sourceEntityId) && nodeSet.has(r.targetEntityId))
      .map((r, i) => ({
        id: r.id,
        source: r.sourceEntityId,
        target: r.targetEntityId,
        relationType: r.relationType,
        weight: r.weight,
        lineStyle: { curveness: (i % 5) * 0.06 },
      }))

    await nextTick()
    renderChart()
  } catch (e) {
    ElMessage.error('加载图谱数据失败')
  } finally {
    vizLoading.value = false
  }
}

const reloadViz = () => {
  selectedNode.value = null
  fetchVizData()
}

// ── 搜索 ──
const handleSearch = () => {
  if (!chartInstance) return
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw || kw === '*') {
    chartInstance.dispatchAction({ type: 'downplay' })
    return
  }
  chartInstance.dispatchAction({ type: 'downplay' })
  vizNodes.value.forEach((n, i) => {
    if (n.name?.toLowerCase().includes(kw)) {
      chartInstance.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: i })
    }
  })
}

const clearSearch = () => {
  if (chartInstance) chartInstance.dispatchAction({ type: 'downplay' })
}

// ── 图例切换 ──
const toggleCategory = (name) => {
  const s = new Set(hiddenCategories.value)
  s.has(name) ? s.delete(name) : s.add(name)
  hiddenCategories.value = s

  const filtered = vizNodes.value.filter(n => !s.has(n.entityType))
  const nodeSet = new Set(filtered.map(n => n.id))
  const filteredLinks = vizLinks.value.filter(l => nodeSet.has(l.source) && nodeSet.has(l.target))

  chartInstance?.setOption({
    series: [{ data: buildNodeData(filtered), links: filteredLinks }]
  })
}

const buildNodeData = (nodeList) =>
  nodeList.map(n => ({
    ...n,
    itemStyle: { color: getColor(n.catIdx) },
  }))

// ── ECharts 渲染 ──
const renderChart = () => {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, null, { renderer: 'canvas' })
    chartInstance.on('click', ({ dataType, data }) => {
      if (dataType === 'node') showNodeDetail(data)
    })
  }

  const t = theme.value
  const filtered = vizNodes.value.filter(n => !hiddenCategories.value.has(n.entityType))
  const nodeSet = new Set(filtered.map(n => n.id))
  const filteredLinks = vizLinks.value.filter(l => nodeSet.has(l.source) && nodeSet.has(l.target))

  chartInstance.setOption({
    backgroundColor: t.chartBg,
    tooltip: {
      trigger: 'item',
      formatter: ({ dataType, data }) =>
        dataType === 'node'
          ? `<b>${data.name}</b><br/>类型: ${data.entityType}`
          : `<span style="color:#fbbf24">${data.relationType}</span>`,
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      label: {
        show: true,
        position: 'bottom',
        fontSize: 11,
        color: t.labelColor,
        overflow: 'truncate',
        width: 70,
        formatter: ({ data }) => data.name?.length > 8 ? data.name.slice(0, 8) + '…' : data.name,
      },
      itemStyle: { borderColor: t.nodeBorder, borderWidth: 2 },
      lineStyle: { color: t.edgeColor, width: 1, opacity: 0.6 },
      emphasis: {
        focus: 'adjacency',
        label: { fontSize: 13, color: '#fff' },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        lineStyle: { width: 2, opacity: 1 },
      },
      force: {
        repulsion: 180,
        gravity: 0.08,
        edgeLength: [60, 160],
        layoutAnimation: true,
        friction: 0.6,
      },
      data: buildNodeData(filtered),
      links: filteredLinks,
    }],
  }, { notMerge: true })
}

// ── 节点详情 ──
const showNodeDetail = async (node) => {
  selectedNode.value = node
  nodeRelations.value = []
  relationsLoading.value = true
  try {
    const res = await getGraphEntityDetails(graphId.value, node.id)
    if (res?.relations) {
      const entityMap = {}
      vizNodes.value.forEach(n => { entityMap[n.id] = n.name })
      nodeRelations.value = res.relations.map(r => ({
        ...r,
        targetName: entityMap[r.targetEntityId] || r.targetEntityId,
      }))
    }
  } catch {}
  finally { relationsLoading.value = false }
}

// ── 构建 & 导出 ──
let pollTimer = null

const triggerBuild = async () => {
  building.value = true
  clearInterval(pollTimer)
  try {
    await buildGraph(graphId.value)
    ElMessage.info('图谱构建已启动，正在监听进度...')
    pollTimer = setInterval(async () => {
      await fetchGraph()
      const status = graph.value?.buildStatus
      if (status === 'SUCCESS' || status === 'FAILED') {
        clearInterval(pollTimer)
        building.value = false
        if (status === 'SUCCESS') {
          ElMessage.success('图谱构建完成')
          fetchVizData()
        } else {
          const reason = graph.value?.errorMessage
          ElMessage.error(reason ? `图谱构建失败：${reason}` : '图谱构建失败，请查看后端日志')
        }
      }
    }, 3000)
  } catch {
    ElMessage.error('构建失败')
    building.value = false
  }
}

const exportData = () => {
  const data = {
    nodes: vizNodes.value.map(n => ({ id: n.id, name: n.name, type: n.entityType, description: n.description })),
    edges: vizLinks.value.map(l => ({ source: l.source, target: l.target, relationType: l.relationType })),
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${graph.value?.name || 'graph'}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// ── 工具 ──
const goBack = () => router.push('/dashboard/resources/graph')

const getStatusType = (s) => ({ PENDING: 'info', BUILDING: 'warning', ENTITY_EXTRACTING: 'warning', RELATION_EXTRACTING: 'warning', SUCCESS: 'success', FAILED: 'danger' }[s] || 'info')
const getStatusText = (s) => ({ PENDING: '待构建', BUILDING: '构建中', ENTITY_EXTRACTING: '实体抽取', RELATION_EXTRACTING: '关系抽取', SUCCESS: '已完成', FAILED: '失败' }[s] || s)

const handleResize = () => chartInstance?.resize()

watch(isDarkMode, () => {
  if (chartInstance && vizNodes.value.length > 0) renderChart()
})

onMounted(async () => {
  await fetchGraph()
  fetchVizData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  clearInterval(pollTimer)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
/* ── CSS 变量（亮/暗主题） ── */
.graph-page {
  --gp-bg:         #f8fafc;
  --gp-surface:    #ffffff;
  --gp-border:     #e2e8f0;
  --gp-text:       #0f172a;
  --gp-muted:      #475569;
  --gp-dim:        #94a3b8;
  --gp-overlay:    rgba(248,250,252,0.75);
}

:global(.dark) .graph-page {
  --gp-bg:         #0f172a;
  --gp-surface:    #1e293b;
  --gp-border:     #334155;
  --gp-text:       #e2e8f0;
  --gp-muted:      #94a3b8;
  --gp-dim:        #64748b;
  --gp-overlay:    rgba(15,23,42,0.75);
}

.graph-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--gp-bg);
  color: var(--gp-text);
  overflow: hidden;
}

/* ── 顶部工具栏 ── */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--gp-surface);
  border-bottom: 1px solid var(--gp-border);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.top-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.graph-title {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gp-dim);
  flex-shrink: 0;
}
.status-dot.connected { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.status-text { font-size: 12px; color: var(--gp-muted); }
.stat-info { font-size: 12px; color: var(--gp-dim); white-space: nowrap; }

/* ── 主体 ── */
.main-area {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

/* ── 画布 ── */
.canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.echarts-canvas {
  width: 100%;
  height: 100%;
}

.empty-center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 底部状态栏 ── */
.status-bar {
  position: absolute;
  bottom: 12px;
  left: 16px;
  font-size: 13px;
  color: var(--gp-muted);
  background: var(--gp-overlay);
  padding: 4px 12px;
  border-radius: 20px;
  backdrop-filter: blur(4px);
  pointer-events: none;
  border: 1px solid var(--gp-border);
}

/* ── 图例 ── */
.legend-box {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  background: var(--gp-overlay);
  padding: 8px 10px;
  border-radius: 8px;
  backdrop-filter: blur(4px);
  border: 1px solid var(--gp-border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-size: 12px;
  color: var(--gp-text);
  transition: opacity 0.2s;
  user-select: none;
}
.legend-item:hover { opacity: 0.8; }
.legend-item.hidden { opacity: 0.3; }

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ── 详情面板 ── */
.detail-panel {
  width: 320px;
  flex-shrink: 0;
  background: var(--gp-surface);
  border-left: 1px solid var(--gp-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--gp-border);
  flex-shrink: 0;
}

.close-btn {
  cursor: pointer;
  color: var(--gp-dim);
  font-size: 16px;
  transition: color 0.2s;
}
.close-btn:hover { color: var(--gp-text); }

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-track { background: transparent; }
.panel-body::-webkit-scrollbar-thumb { background: var(--gp-border); border-radius: 2px; }

.prop-row {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.prop-key {
  flex-shrink: 0;
  width: 88px;
  color: var(--gp-dim);
  font-size: 12px;
  padding-top: 1px;
}

.prop-val {
  flex: 1;
  color: var(--gp-text);
  word-break: break-all;
}

.prop-val.name-val {
  font-weight: 600;
  font-size: 15px;
}

.prop-val.mono {
  font-family: 'Menlo', 'Monaco', monospace;
  font-size: 11px;
  color: var(--gp-muted);
}

.prop-val.small { font-size: 11px; }

.panel-section-title {
  font-size: 12px;
  color: var(--gp-dim);
  font-weight: 500;
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--gp-border);
}

.rel-loading, .rel-empty {
  font-size: 12px;
  color: var(--gp-dim);
  text-align: center;
  padding: 8px 0;
}

.rel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rel-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--gp-text);
}

.rel-target {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spin {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 面板滑入动画 */
.slide-panel-enter-active, .slide-panel-leave-active {
  transition: width 0.25s ease, opacity 0.25s ease;
}
.slide-panel-enter-from, .slide-panel-leave-to {
  width: 0;
  opacity: 0;
}
</style>
