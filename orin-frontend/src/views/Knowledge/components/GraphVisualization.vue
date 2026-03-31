<template>
  <div class="graph-visualization">
    <!-- Graph Controls -->
    <div class="graph-controls">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索实体..."
        prefix-icon="Search"
        size="small"
        clearable
        style="width: 200px"
        @input="onSearch"
      />
      <el-button-group size="small">
        <el-button @click="zoomIn">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button @click="zoomOut">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button @click="fitView">
          <el-icon><FullScreen /></el-icon>
        </el-button>
        <el-button @click="refreshGraph">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </el-button-group>
    </div>

    <!-- Graph Legend -->
    <div class="graph-legend">
      <span class="legend-title">图例:</span>
      <div
        v-for="cat in categories"
        :key="cat"
        class="legend-item"
        :class="{ inactive: !visibleCategories.includes(cat) }"
        @click="toggleCategory(cat)"
      >
        <span class="legend-dot" :style="{ background: getCategoryColor(cat) }"></span>
        <span class="legend-label">{{ cat }}</span>
      </div>
    </div>

    <!-- Filter by Document -->
    <div class="graph-filter">
      <el-select
        v-model="filterDocId"
        placeholder="按文档过滤"
        clearable
        size="small"
        style="width: 200px"
        @change="onDocFilterChange"
      >
        <el-option
          v-for="doc in documents"
          :key="doc.id"
          :label="doc.fileName"
          :value="doc.id"
        />
      </el-select>
    </div>

    <!-- ECharts Graph -->
    <div ref="chartRef" class="graph-container"></div>

    <!-- Node Detail Panel -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="实体详情"
      direction="rtl"
      size="400px"
    >
      <div v-if="selectedNode" class="node-detail">
        <h3>{{ selectedNode.name }}</h3>
        <el-tag size="small" type="info">{{ selectedNode.type }}</el-tag>

        <div class="detail-section">
          <h4>描述</h4>
          <p>{{ selectedNode.description || '无' }}</p>
        </div>

        <div class="detail-section">
          <h4>关联关系</h4>
          <div v-if="nodeRelations.length > 0" class="relations-list">
            <div
              v-for="rel in nodeRelations"
              :key="rel.id"
              class="relation-item"
            >
              <span class="rel-type">{{ rel.relationType }}</span>
              <span class="rel-arrow">→</span>
              <span class="rel-target">{{ getEntityName(rel.targetEntityId) }}</span>
            </div>
          </div>
          <el-empty v-else description="无关联关系" :image-size="60" />
        </div>
      </div>
    </el-drawer>

    <!-- Loading State -->
    <div v-if="loading" class="loading-overlay">
      <el-icon class="spin"><Loading /></el-icon>
      <span>加载图谱数据...</span>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && nodes.length === 0" class="empty-state">
      <el-empty description="暂无图谱数据">
        <el-button type="primary" @click="buildGraph">
          构建图谱
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search,
  ZoomIn,
  ZoomOut,
  FullScreen,
  Refresh,
  Loading
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getGraphVisualization,
  getGraphEntityDetails,
  searchGraphEntities,
  buildGraph,
  getDocuments
} from '@/api/knowledge'

const props = defineProps({
  kbId: {
    type: String,
    required: true
  },
  selectedDocId: {
    type: String,
    default: null
  }
})

const chartRef = ref(null)
const loading = ref(false)
const nodes = ref([])
const edges = ref([])
const categories = ref([])
const visibleCategories = ref([])
const searchKeyword = ref('')
const filterDocId = ref(null)
const documents = ref([])
const highlightedNodes = ref(new Set())

const detailDrawerVisible = ref(false)
const selectedNode = ref(null)
const nodeRelations = ref([])

let chart = null

// Load documents for filter
const loadDocuments = async () => {
  try {
    const res = await getDocuments(props.kbId)
    documents.value = res.data || []
  } catch (err) {
    console.error('Failed to load documents:', err)
  }
}

// Load graph data
const loadGraphData = async () => {
  if (!props.kbId) return

  loading.value = true
  try {
    const res = await getGraphVisualization(props.kbId, filterDocId.value)
    const data = res.data || { nodes: [], edges: [], categories: [] }

    nodes.value = data.nodes || []
    edges.value = data.edges || []
    categories.value = data.categories || []
    visibleCategories.value = [...categories.value]

    renderChart()
  } catch (err) {
    console.error('Failed to load graph:', err)
    ElMessage.error('加载图谱数据失败')
  } finally {
    loading.value = false
  }
}

// Search entities
const onSearch = () => {
  if (!searchKeyword.value) {
    highlightedNodes.value.clear()
    return
  }

  highlightedNodes.value.clear()
  // Highlight matching nodes
  nodes.value.forEach(node => {
    if (node.name.toLowerCase().includes(searchKeyword.value.toLowerCase())) {
      highlightedNodes.value.add(node.id)
    }
  })

  // Update chart with highlighting
  updateChartHighlighting()
}

// Toggle category visibility
const toggleCategory = (cat) => {
  if (visibleCategories.value.includes(cat)) {
    visibleCategories.value = visibleCategories.value.filter(c => c !== cat)
  } else {
    visibleCategories.value.push(cat)
  }
  renderChart()
}

// Filter by document
const onDocFilterChange = () => {
  loadGraphData()
}

// Get category color
const getCategoryColor = (category) => {
  const colors = [
    '#5470c6', '#91cc75', '#fac858', '#ee6666',
    '#73c0de', '#3ba272', '#fc8452', '#9a60b4'
  ]
  const index = categories.value.indexOf(category)
  return colors[index % colors.length]
}

// Chart controls
const zoomIn = () => {
  if (chart) {
    chart.dispatchAction({ type: 'zoom', rate: 1.2 })
  }
}

const zoomOut = () => {
  if (chart) {
    chart.dispatchAction({ type: 'zoom', rate: 0.8 })
  }
}

const fitView = () => {
  if (chart) {
    chart.dispatchAction({ type: 'focusNode', seriesIndex: 0 })
  }
}

const refreshGraph = () => {
  loadGraphData()
}

// Build graph
const handleBuildGraph = async () => {
  try {
    await buildGraph(props.kbId)
    ElMessage.success('图谱构建已启动')
    setTimeout(loadGraphData, 2000)
  } catch (err) {
    ElMessage.error('启动图谱构建失败')
  }
}

// Show node detail
const showNodeDetail = async (nodeId) => {
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return

  selectedNode.value = node

  try {
    const res = await getGraphEntityDetails(props.kbId, nodeId)
    if (res.data) {
      nodeRelations.value = res.data.relations || []
    }
  } catch (err) {
    nodeRelations.value = []
  }

  detailDrawerVisible.value = true
}

const getEntityName = (entityId) => {
  const entity = nodes.value.find(n => n.id === entityId)
  return entity?.name || entityId
}

// Update chart highlighting
const updateChartHighlighting = () => {
  if (!chart) return

  chart.setOption({
    series: [{
      data: nodes.value.map(node => ({
        ...node,
        itemStyle: {
          color: highlightedNodes.value.has(node.id)
            ? '#FFD700' // Gold for highlighted
            : getCategoryColor(node.type)
        }
      })
    }]
  })
}

// Render chart
const renderChart = () => {
  if (!chartRef.value || nodes.value.length === 0) return

  if (!chart) {
    chart = echarts.init(chartRef.value)

    // Click handler
    chart.on('click', (params) => {
      if (params.dataType === 'node') {
        showNodeDetail(params.data.id)
      }
    })
  }

  const filteredNodes = nodes.value.filter(node =>
    visibleCategories.value.includes(node.type)
  )

  const nodeIds = new Set(filteredNodes.map(n => n.id))
  const filteredEdges = edges.value.filter(edge =>
    nodeIds.has(edge.source) && nodeIds.has(edge.target)
  )

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<strong>${params.data.name}</strong><br/>类型: ${params.data.type}`
        }
        return `${params.data.relationType}`
      }
    },
    legend: {
      show: false // We use custom legend
    },
    series: [{
      type: 'graph',
      layout: 'force',
      force: {
        repulsion: 200,
        gravity: 0.1,
        edgeLength: 100,
        layoutAnimation: true
      },
      roam: true,
      draggable: true,
      label: {
        show: true,
        position: 'bottom',
        formatter: '{b}',
        fontSize: 12
      },
      itemStyle: {
        borderColor: '#fff',
        borderWidth: 2
      },
      lineStyle: {
        color: '#ccc',
        width: 1,
        curveness: 0.2
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 },
        itemStyle: { borderWidth: 3 }
      },
      data: filteredNodes.map(node => ({
        id: node.id,
        name: node.name,
        type: node.type,
        description: node.description,
        symbolSize: Math.min(60, 30 + node.description?.length / 5 || 20),
        itemStyle: {
          color: highlightedNodes.value.has(node.id)
            ? '#FFD700'
            : getCategoryColor(node.type)
        }
      })),
      links: filteredEdges.map(edge => ({
        source: edge.source,
        target: edge.target,
        relationType: edge.relationType,
        lineStyle: { curveness: Math.random() * 0.3 }
      })),
      categories: categories.value.map(cat => ({
        name: cat,
        itemStyle: { color: getCategoryColor(cat) }
      }))
    }]
  }

  chart.setOption(option)
}

// Resize handler
const handleResize = () => {
  chart?.resize()
}

watch(() => props.kbId, () => {
  loadDocuments()
  loadGraphData()
})

watch(() => props.selectedDocId, (newId) => {
  if (newId) {
    filterDocId.value = newId
    loadGraphData()
  }
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadDocuments()
  loadGraphData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.graph-visualization {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

.graph-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 12px;
}

.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 12px;
  background: white;
  border-radius: 6px;
  margin-bottom: 12px;
  align-items: center;
}

.legend-title {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.legend-item:hover {
  background: #f5f7fa;
}

.legend-item.inactive {
  opacity: 0.4;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.legend-label {
  font-size: 12px;
  color: #606266;
}

.graph-filter {
  padding: 0 12px 12px;
}

.graph-container {
  flex: 1;
  min-height: 400px;
  background: white;
  border-radius: 8px;
  border: 1px solid #eee;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  z-index: 10;
}

.spin {
  font-size: 32px;
  color: #409EFF;
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.empty-state {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.node-detail {
  padding: 0 16px;
}

.node-detail h3 {
  margin: 0 0 12px;
  font-size: 18px;
  color: #303133;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h4 {
  font-size: 13px;
  color: #909399;
  margin: 0 0 8px;
  font-weight: 500;
}

.detail-section p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin: 0;
}

.relations-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.relation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #f9f9f9;
  border-radius: 4px;
  font-size: 13px;
}

.rel-type {
  color: #409EFF;
  font-weight: 500;
}

.rel-arrow {
  color: #909399;
}

.rel-target {
  color: #606266;
}
</style>
