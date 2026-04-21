<template>
  <div class="tooling-panel">
    <el-card class="header-card">
      <h2 class="title">模型工具（Tools）</h2>
      <p class="desc">这里管理可被模型通过 function/tool calling 调用的工具能力，不做智能体绑定。</p>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索工具 ID / 名称..." clearable class="search" />
        <el-button :loading="loading" @click="loadCatalog">刷新</el-button>
      </div>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="list-head">
          <span>工具目录（Tools）</span>
          <el-tag effect="plain">{{ filteredTools.length }}</el-tag>
        </div>
      </template>

      <el-table :data="filteredTools" v-loading="loading" stripe>
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
.tooling-panel { padding: 0; }
.header-card { margin-bottom: 14px; border-radius: 12px; }
.title { margin: 0; font-size: 22px; color: #0f172a; }
.desc { margin: 8px 0 0; color: #64748b; font-size: 14px; }
.toolbar { margin-top: 14px; display: flex; gap: 10px; align-items: center; }
.search { max-width: 360px; }
.list-card { border-radius: 12px; }
.list-head { display: flex; justify-content: space-between; align-items: center; }
</style>
