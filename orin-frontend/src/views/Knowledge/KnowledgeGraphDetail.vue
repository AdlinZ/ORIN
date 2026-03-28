<template>
  <div class="page-container">
    <PageHeader
      :title="graph?.name || '图谱详情'"
      :description="graph?.description || '图谱详情'"
      icon="Connection"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="fetchData">
          刷新
        </el-button>
        <el-button type="primary" :icon="Back" @click="goBack">
          返回列表
        </el-button>
      </template>
    </PageHeader>

    <!-- 图谱信息卡片 -->
    <div class="graph-info-card">
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">
              状态
            </div>
            <div class="stat-value">
              <el-tag :type="getStatusType(graph?.buildStatus)" size="small">
                {{ getStatusText(graph?.buildStatus) }}
              </el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">
              实体数量
            </div>
            <div class="stat-value">
              {{ graph?.entityCount || 0 }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">
              关系数量
            </div>
            <div class="stat-value">
              {{ graph?.relationCount || 0 }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">
              最后构建
            </div>
            <div class="stat-value">
              {{ formatDate(graph?.lastBuildAt) }}
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 标签页 -->
    <el-tabs v-model="activeTab" class="graph-tabs">
      <!-- 可视化 -->
      <el-tab-pane label="可视化" name="visualization">
        <div class="viz-toolbar">
          <el-input
            v-model="vizSearch"
            placeholder="搜索实体名称，高亮显示"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @input="handleVizSearch"
          />
          <div class="viz-controls">
            <el-button :icon="Refresh" @click="fetchVizData">
              刷新
            </el-button>
            <el-button @click="resetVizZoom">
              重置视图
            </el-button>
          </div>
        </div>

        <div v-loading="vizLoading" class="graph-chart-container">
          <div v-if="vizNodes.length === 0 && !vizLoading" class="empty-state">
            <el-empty description="暂无可视化数据，请先构建图谱">
              <el-button type="primary" @click="$emit('build')">
                触发构建
              </el-button>
            </el-empty>
          </div>
          <div v-else ref="chartRef" class="chart-wrapper" />
        </div>
      </el-tab-pane>

      <!-- 实体列表 -->
      <el-tab-pane label="实体列表" name="entities">
        <div class="tab-toolbar">
          <el-input
            v-model="entitySearch"
            placeholder="搜索实体名称"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @input="handleEntitySearch"
          />
          <el-button :icon="Refresh" @click="fetchEntities">
            刷新
          </el-button>
        </div>

        <el-table
          v-loading="entitiesLoading"
          :data="filteredEntities"
          stripe
          style="width: 100%"
          max-height="400"
        >
          <el-table-column
            prop="name"
            label="实体名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column prop="entityType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ row.entityType || '未分类' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="description"
            label="描述"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column prop="createdAt" label="创建时间" width="160">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="entityPage"
            :page-size="entitySize"
            :total="entityTotal"
            layout="total, prev, pager, next"
            @current-change="fetchEntities"
          />
        </div>
      </el-tab-pane>

      <!-- 关系列表 -->
      <el-tab-pane label="关系列表" name="relations">
        <div class="tab-toolbar">
          <el-input
            v-model="relationSearch"
            placeholder="搜索关系类型"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @input="handleRelationSearch"
          />
          <el-button :icon="Refresh" @click="fetchRelations">
            刷新
          </el-button>
        </div>

        <el-table
          v-loading="relationsLoading"
          :data="filteredRelations"
          stripe
          style="width: 100%"
          max-height="400"
        >
          <el-table-column
            prop="sourceEntityId"
            label="源实体"
            width="150"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ getEntityName(row.sourceEntityId) }}
            </template>
          </el-table-column>
          <el-table-column prop="relationType" label="关系类型" width="150">
            <template #default="{ row }">
              <el-tag size="small" type="warning">
                {{ row.relationType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="targetEntityId"
            label="目标实体"
            width="150"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ getEntityName(row.targetEntityId) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="description"
            label="描述"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column prop="weight" label="权重" width="80">
            <template #default="{ row }">
              {{ row.weight?.toFixed(2) || '1.00' }}
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="relationPage"
            :page-size="relationSize"
            :total="relationTotal"
            layout="total, prev, pager, next"
            @current-change="fetchRelations"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Back, Search } from '@element-plus/icons-vue'
import {
  getGraph,
  getGraphEntities,
  getGraphRelations,
  searchGraphEntities
} from '@/api/knowledge'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const graphId = computed(() => route.params.id)

const activeTab = ref('visualization')
const graph = ref(null)

// Entity state
const entities = ref([])
const entitiesLoading = ref(false)
const entityPage = ref(1)
const entitySize = ref(50)
const entityTotal = ref(0)
const entitySearch = ref('')
const entitySearchTimer = ref(null)

// Relation state
const relations = ref([])
const relationsLoading = ref(false)
const relationPage = ref(1)
const relationSize = ref(50)
const relationTotal = ref(0)
const relationSearch = ref('')
const relationSearchTimer = ref(null)

// Visualization state
const vizLoading = ref(false)
const vizSearch = ref('')
const vizSearchTimer = ref(null)
const vizNodes = ref([])
const vizLinks = ref([])
const chartRef = ref(null)
const chartInstance = ref(null)

// Watch tab switch to load viz data when visualization tab is selected
watch(activeTab, (tab) => {
  if (tab === 'visualization' && vizNodes.value.length === 0) {
    fetchVizData()
  }
})

const getStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'BUILDING': 'warning',
    'ENTITY_EXTRACTING': 'warning',
    'RELATION_EXTRACTING': 'warning',
    'SUCCESS': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'PENDING': '待构建',
    'BUILDING': '构建中',
    'ENTITY_EXTRACTING': '实体抽取',
    'RELATION_EXTRACTING': '关系抽取',
    'SUCCESS': '已完成',
    'FAILED': '构建失败'
  }
  return map[status] || status
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const goBack = () => {
  router.push('/dashboard/knowledge/graph')
}

const filteredEntities = computed(() => {
  if (!entitySearch.value) return entities.value
  const keyword = entitySearch.value.toLowerCase()
  return entities.value.filter(e =>
    e.name?.toLowerCase().includes(keyword) ||
    e.entityType?.toLowerCase().includes(keyword)
  )
})

const filteredRelations = computed(() => {
  if (!relationSearch.value) return relations.value
  const keyword = relationSearch.value.toLowerCase()
  return relations.value.filter(r =>
    r.relationType?.toLowerCase().includes(keyword)
  )
})

const entityMap = computed(() => {
  const map = {}
  entities.value.forEach(e => {
    map[e.id] = e.name
  })
  return map
})

const getEntityName = (entityId) => {
  return entityMap.value[entityId] || entityId
}

const fetchData = async () => {
  await Promise.all([fetchGraph(), fetchEntities(), fetchRelations()])
}

const fetchGraph = async () => {
  try {
    graph.value = await getGraph(graphId.value)
  } catch (error) {
    console.error('获取图谱详情失败:', error)
    ElMessage.error('获取图谱详情失败')
  }
}

const fetchEntities = async () => {
  entitiesLoading.value = true
  try {
    const result = await getGraphEntities(graphId.value, {
      page: entityPage.value - 1,
      size: entitySize.value
    })
    entities.value = result.content || result || []
    entityTotal.value = result.totalElements || entities.value.length
  } catch (error) {
    console.error('获取实体列表失败:', error)
    ElMessage.error('获取实体列表失败')
  } finally {
    entitiesLoading.value = false
  }
}

const fetchRelations = async () => {
  relationsLoading.value = true
  try {
    const result = await getGraphRelations(graphId.value, {
      page: relationPage.value - 1,
      size: relationSize.value
    })
    relations.value = result.content || result || []
    relationTotal.value = result.totalElements || relations.value.length
  } catch (error) {
    console.error('获取关系列表失败:', error)
    ElMessage.error('获取关系列表失败')
  } finally {
    relationsLoading.value = false
  }
}

const handleEntitySearch = () => {
  clearTimeout(entitySearchTimer.value)
  entitySearchTimer.value = setTimeout(() => {
    entityPage.value = 1
    fetchEntities()
  }, 300)
}

const handleRelationSearch = () => {
  clearTimeout(relationSearchTimer.value)
  relationSearchTimer.value = setTimeout(() => {
    relationPage.value = 1
    fetchRelations()
  }, 300)
}

const fetchVizData = async () => {
  vizLoading.value = true
  try {
    const [entitiesResult, relationsResult] = await Promise.all([
      getGraphEntities(graphId.value, { page: 0, size: 200 }),
      getGraphRelations(graphId.value, { page: 0, size: 200 })
    ])

    const entityList = entitiesResult.content || entitiesResult || []
    const relationList = relationsResult.content || relationsResult || []

    vizNodes.value = entityList.map(e => ({
      id: e.id,
      name: e.name,
      entityType: e.entityType,
      description: e.description
    }))

    vizLinks.value = relationList
      .filter(r => vizNodes.value.some(n => n.id === r.sourceEntityId) && vizNodes.value.some(n => n.id === r.targetEntityId))
      .map(r => ({
        source: r.sourceEntityId,
        target: r.targetEntityId,
        relationType: r.relationType,
        weight: r.weight
      }))

    await nextTick()
    renderChart()
  } catch (error) {
    console.error('获取可视化数据失败:', error)
    ElMessage.error('获取可视化数据失败')
  } finally {
    vizLoading.value = false
  }
}

const renderChart = () => {
  if (!chartRef.value) return

  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
  }

  const categoryMap = {}
  vizNodes.value.forEach(n => {
    if (n.entityType && !categoryMap[n.entityType]) {
      categoryMap[n.entityType] = Object.keys(categoryMap).length
    }
  })

  const categories = Object.keys(categoryMap).map(name => ({ name }))

  const option = {
    title: {
      text: '',
      top: 10,
      left: 10
    },
    tooltip: {
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<b>${params.data.name}</b><br/>类型: ${params.data.entityType || '未分类'}<br/>描述: ${params.data.description || '无'}`
        } else {
          return `<b>${params.data.relationType}</b><br/>权重: ${params.data.weight?.toFixed(2) || '1.00'}`
        }
      }
    },
    legend: {
      data: Object.keys(categoryMap),
      top: 10,
      right: 10
    },
    animation: true,
    series: [{
      type: 'graph',
      layout: 'force',
      symbolSize: 40,
      roam: true,
      draggable: true,
      label: {
        show: true,
        position: 'bottom',
        formatter: '{b}',
        fontSize: 11,
        color: '#333',
        overflow: 'truncate',
        width: 80
      },
      edgeSymbol: ['circle', 'arrow'],
      edgeSymbolSize: [4, 8],
      data: vizNodes.value.map(n => ({
        ...n,
        category: categoryMap[n.entityType] ?? categories.length,
        itemStyle: {
          color: getNodeColor(categoryMap[n.entityType] ?? categories.length)
        }
      })),
      links: vizLinks.value,
      lineStyle: {
        color: 'source',
        curveness: 0.1,
        width: 1.5,
        opacity: 0.7
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 },
        itemStyle: { borderWidth: 3, borderColor: '#fff' }
      },
      categories: categories.length > 0 ? categories : [{ name: '未分类' }],
      force: {
        repulsion: 120,
        gravity: 0.1,
        edgeLength: [60, 150],
        layoutAnimation: true
      }
    }]
  }

  chartInstance.value.setOption(option, { notMerge: true })
}

const getNodeColor = (category) => {
  const colors = ['#7c3aed', '#2563eb', '#059669', '#d97706', '#dc2626', '#db2777', '#0891b2', '#65a30d']
  return colors[category % colors.length]
}

const handleVizSearch = () => {
  clearTimeout(vizSearchTimer.value)
  vizSearchTimer.value = setTimeout(() => {
    if (!chartInstance.value) return
    const keyword = vizSearch.value.trim().toLowerCase()
    if (!keyword) {
      chartInstance.value.dispatchAction({ type: 'downplay' })
      return
    }
    const matched = []
    vizNodes.value.forEach(n => {
      if (n.name?.toLowerCase().includes(keyword)) {
        matched.push(n.id)
      }
    })
    chartInstance.value.dispatchAction({ type: 'downplay' })
    matched.forEach(id => {
      chartInstance.value.dispatchAction({
        type: 'highlight',
        dataIndex: vizNodes.value.findIndex(n => n.id === id)
      })
    })
  }, 300)
}

const resetVizZoom = () => {
  if (chartInstance.value) {
    chartInstance.value.dispatchAction({ type: 'restore' })
  }
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})

const handleResize = () => {
  if (chartInstance.value) {
    chartInstance.value.resize()
  }
}
</script>

<style scoped>
.graph-info-card {
  background: var(--el-bg-color);
  border-radius: var(--radius-base);
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-light);
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.graph-tabs {
  background: var(--el-bg-color);
  border-radius: var(--radius-base);
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.viz-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.viz-controls {
  display: flex;
  gap: 8px;
}

.graph-chart-container {
  background: var(--el-bg-color);
  border-radius: var(--radius-base);
  min-height: 500px;
  position: relative;
}

.chart-wrapper {
  width: 100%;
  height: 500px;
}

.viz-toolbar .el-input {
  max-width: 240px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 500px;
}
</style>
