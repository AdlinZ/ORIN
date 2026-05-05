<template>
  <div :class="['tooling-panel', { 'is-embedded': embedded }]">
    <el-card class="header-card">
      <div class="header-main">
        <div>
          <h2 class="title">模型工具（Tools）</h2>
          <p class="desc">管理可被模型通过 function/tool calling 调用的工具能力，不做智能体绑定。</p>
        </div>
        <el-button :loading="loading" type="primary" @click="loadCatalog">
          刷新
        </el-button>
      </div>
      <div class="tool-stats">
        <div class="tool-stat">
          <span>可用工具</span>
          <strong>{{ filteredTools.length }}</strong>
        </div>
        <div class="tool-stat">
          <span>已启用</span>
          <strong>{{ toolStats.enabled }}</strong>
        </div>
        <div class="tool-stat">
          <span>函数调用</span>
          <strong>{{ toolStats.functionCall }}</strong>
        </div>
      </div>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索工具 ID / 名称..." clearable class="search" />
      </div>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="list-head">
          <span>工具目录（Tools）</span>
          <el-tag effect="plain">{{ filteredTools.length }}</el-tag>
        </div>
      </template>

      <div v-if="embedded && filteredTools.length" v-loading="loading" class="tool-card-grid">
        <article v-for="tool in filteredTools" :key="tool.toolId" class="tool-card-item">
          <div class="tool-card-head">
            <div class="tool-title-wrap">
              <h3>{{ tool.displayName || tool.toolId }}</h3>
              <span>{{ tool.toolId }}</span>
            </div>
            <el-switch
              :model-value="tool.enabled !== false"
              :loading="savingToolId === tool.toolId"
              @change="(val) => setToolEnabled(tool, val)"
            />
          </div>
          <div class="tool-card-meta">
            <el-tag :type="tool.runtimeMode === 'function_call' ? 'success' : 'info'" effect="plain">
              {{ tool.runtimeMode || 'context_only' }}
            </el-tag>
            <el-tag effect="plain">
              {{ tool.category || '未分类' }}
            </el-tag>
            <el-tag effect="plain">
              {{ tool.source || 'system' }}
            </el-tag>
          </div>
          <p class="tool-health">健康状态：{{ tool.healthStatus || '-' }}</p>
        </article>
      </div>

      <el-empty
        v-else-if="embedded && !loading"
        :image-size="72"
        description="暂无可用模型工具"
      />

      <el-table v-else-if="!embedded" :data="filteredTools" v-loading="loading" stripe>
        <el-table-column prop="toolId" label="Tool ID" min-width="220" />
        <el-table-column prop="displayName" label="名称" min-width="180" />
        <el-table-column prop="category" label="类别" width="140" />
        <el-table-column label="运行模式" width="150">
          <template #default="{ row }">
            <el-tag :type="row.runtimeMode === 'function_call' ? 'success' : 'info'" effect="plain">
              {{ row.runtimeMode || 'context_only' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== false"
              :loading="savingToolId === row.toolId"
              @change="(val) => setToolEnabled(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="healthStatus" label="健康状态" width="140" />
        <el-table-column prop="source" label="来源" width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getToolCatalog, updateToolCatalogItem } from '@/api/agent-chat'

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const loading = ref(false)
const savingToolId = ref('')
const keyword = ref('')
const tools = ref([])

const filteredTools = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const functionTools = tools.value.filter((t) => {
    const toolId = String(t.toolId || '').toLowerCase()
    const category = String(t.category || '').toUpperCase()
    const runtimeMode = String(t.runtimeMode || '').toLowerCase()
    if (toolId.startsWith('skill:') || toolId.startsWith('mcp:')) return false
    if (category === 'SKILL' || category === 'MCP') return false
    return runtimeMode === 'function_call'
  })
  if (!k) return functionTools
  return functionTools.filter((t) => {
    const id = String(t.toolId || '').toLowerCase()
    const name = String(t.displayName || '').toLowerCase()
    return id.includes(k) || name.includes(k)
  })
})

const toolStats = computed(() => ({
  enabled: filteredTools.value.filter((tool) => tool.enabled !== false).length,
  functionCall: filteredTools.value.filter((tool) => tool.runtimeMode === 'function_call').length
}))

const loadCatalog = async () => {
  loading.value = true
  try {
    const res = await getToolCatalog({ includeDisabled: true })
    tools.value = Array.isArray(res) ? res : []
  } catch (error) {
    ElMessage.error('加载工具目录失败')
    tools.value = []
  } finally {
    loading.value = false
  }
}

const setToolEnabled = async (tool, enabled) => {
  savingToolId.value = tool.toolId
  const previous = tool.enabled
  tool.enabled = !!enabled
  try {
    await updateToolCatalogItem(tool.toolId, { enabled: !!enabled })
    ElMessage.success(`已${enabled ? '启用' : '停用'} ${tool.toolId}`)
  } catch (error) {
    tool.enabled = previous
    ElMessage.error('更新工具状态失败')
  } finally {
    savingToolId.value = ''
  }
}

onMounted(loadCatalog)
</script>

<style scoped>
.tooling-panel {
  padding: 0;
}

.header-card,
.list-card {
  border-radius: var(--orin-card-radius, 8px);
  border: 1px solid var(--orin-border);
}

.header-card {
  margin-bottom: 12px;
  background:
    linear-gradient(135deg, rgba(250, 245, 255, 0.74), rgba(255, 255, 255, 0.96) 54%),
    var(--neutral-white);
}

.header-card :deep(.el-card__body) {
  padding: 18px;
}

.header-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: #0f172a;
  letter-spacing: 0;
}

.desc {
  margin: 7px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.tool-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(112px, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.tool-stat {
  padding: 10px 12px;
  border: 1px solid rgba(124, 58, 237, 0.14);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(255, 255, 255, 0.72);
}

.tool-stat span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
}

.tool-stat strong {
  display: block;
  margin-top: 4px;
  color: #7c3aed;
  font-size: 19px;
  line-height: 1.1;
}

.toolbar {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.search {
  max-width: 360px;
}

.list-card :deep(.el-card__body) {
  padding: 14px;
}

.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tool-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
  min-height: 260px;
}

.tool-card-item {
  padding: 14px;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(255, 255, 255, 0.86);
}

.tool-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.tool-title-wrap {
  min-width: 0;
}

.tool-title-wrap h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: 0;
}

.tool-title-wrap span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.tool-health {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
}

html.dark .header-card {
  background:
    linear-gradient(135deg, rgba(124, 58, 237, 0.12), rgba(15, 23, 42, 0.94) 56%),
    var(--neutral-gray-900, #0f172a);
}

html.dark .title,
html.dark .tool-title-wrap h3 {
  color: #f8fafc;
}

html.dark .desc,
html.dark .tool-stat span,
html.dark .tool-title-wrap span,
html.dark .tool-health {
  color: #94a3b8;
}

html.dark .tool-stat,
html.dark .tool-card-item {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.72);
}

html.dark .tool-stat strong {
  color: #c4b5fd;
}

@media (max-width: 720px) {
  .header-main,
  .toolbar {
    flex-direction: column;
  }

  .search {
    max-width: none;
    width: 100%;
  }
}
</style>
