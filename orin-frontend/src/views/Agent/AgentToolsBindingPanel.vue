<template>
  <div class="tools-binding-panel">
    <el-card class="binding-header-card">
      <div class="binding-header-top">
        <div class="binding-title-group">
          <h2 class="binding-title">工具卡片配置</h2>
          <p class="binding-desc">选择一个智能体后，直接绑定 Skills 和 MCP 服务。保存后工作台会读取同一份配置。</p>
        </div>
        <div class="binding-actions">
          <el-button :disabled="!selectedAgentId" @click="goWorkspace">
            前往该智能体工作台
          </el-button>
          <el-button type="warning" :disabled="!selectedAgentId" @click="resetBinding">
            清空绑定
          </el-button>
          <el-button type="primary" :disabled="!selectedAgentId" @click="saveBinding">
            保存绑定
          </el-button>
        </div>
      </div>

      <div class="binding-agent-row">
        <span class="binding-agent-label">目标智能体</span>
        <el-select
          v-model="selectedAgentId"
          class="agent-select"
          placeholder="请选择智能体"
          filterable
          clearable
          :loading="loadingAgents"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agent.name"
            :value="agent.id"
          >
            <div class="agent-option">
              <span>{{ agent.name }}</span>
              <span class="agent-option-meta">{{ agent.model || '未配置模型' }}</span>
            </div>
          </el-option>
        </el-select>
      </div>

    <div class="binding-summary">
      <span class="summary-pill">知识库 {{ binding.kbIds.length }}</span>
      <span class="summary-pill">Skills {{ binding.skillIds.length }}</span>
      <span class="summary-pill">MCP {{ binding.mcpIds.length }}</span>
    </div>
  </el-card>

  <div class="binding-grid">
      <el-card class="binding-card">
        <template #header>
          <div class="card-head">
            <span>内置工具</span>
            <el-tag effect="plain">{{ builtinTools.filter(item => item.active).length }}/{{ builtinTools.length }}</el-tag>
          </div>
        </template>
        <div class="selection-list">
          <div v-for="tool in builtinTools" :key="tool.name" class="selection-item">
            <div class="selection-info">
              <div class="selection-name">{{ tool.name }}</div>
              <div class="selection-meta">{{ tool.description }}</div>
            </div>
            <el-tag :type="tool.active ? 'success' : 'info'" effect="plain">
              {{ tool.active ? '激活' : '未激活' }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <el-card class="binding-card">
        <template #header>
          <div class="card-head">
            <span>知识库</span>
            <el-tag type="warning" effect="plain">{{ binding.kbIds.length }}/{{ knowledgeBases.length }}</el-tag>
          </div>
        </template>
        <el-input
          v-model="kbKeyword"
          placeholder="搜索知识库..."
          clearable
          class="search-input"
        />
        <div class="selection-list">
          <label v-for="kb in filteredKnowledgeBases" :key="kb.id" class="selection-item">
            <div class="selection-info">
              <div class="selection-name">{{ kb.name }}</div>
              <div class="selection-meta">{{ kb.documentCount || 0 }} 文档</div>
            </div>
            <el-checkbox :model-value="hasId(binding.kbIds, kb.id)" @change="toggleId('kbIds', kb.id)" />
          </label>
          <el-empty v-if="!filteredKnowledgeBases.length" :image-size="40" description="无匹配知识库" />
        </div>
      </el-card>

      <el-card class="binding-card">
        <template #header>
          <div class="card-head">
            <span>Skills</span>
            <el-tag type="success" effect="plain">{{ binding.skillIds.length }}/{{ skills.length }}</el-tag>
          </div>
        </template>
        <el-input
          v-model="skillKeyword"
          placeholder="搜索技能..."
          clearable
          class="search-input"
        />
        <div class="selection-list">
          <label v-for="skill in filteredSkills" :key="skill.id" class="selection-item">
            <div class="selection-info">
              <div class="selection-name">{{ skill.skillName || skill.name }}</div>
              <div class="selection-meta">{{ skill.skillType || skill.type || 'SKILL' }}</div>
            </div>
            <el-checkbox :model-value="hasId(binding.skillIds, skill.id)" @change="toggleId('skillIds', skill.id)" />
          </label>
          <el-empty v-if="!filteredSkills.length" :image-size="40" description="无匹配技能" />
        </div>
      </el-card>

      <el-card class="binding-card">
        <template #header>
          <div class="card-head">
            <span>MCP 服务</span>
            <el-tag type="info" effect="plain">{{ binding.mcpIds.length }}/{{ mcpServices.length }}</el-tag>
          </div>
        </template>
        <el-input
          v-model="mcpKeyword"
          placeholder="搜索 MCP 服务..."
          clearable
          class="search-input"
        />
        <div class="selection-list">
          <label v-for="service in filteredMcpServices" :key="service.id" class="selection-item">
            <div class="selection-info">
              <div class="selection-name">{{ service.name }}</div>
              <div class="selection-meta">{{ service.type || 'MCP' }} · {{ formatStatus(service.status) }}</div>
            </div>
            <el-checkbox :model-value="hasId(binding.mcpIds, service.id)" @change="toggleId('mcpIds', service.id)" />
          </label>
          <el-empty v-if="!filteredMcpServices.length" :image-size="40" description="无匹配服务" />
        </div>
      </el-card>
    </div>

    <el-alert
      type="success"
      :closable="false"
      show-icon
      class="kb-note"
      title="这里配置的是“工具卡片默认绑定”。进入工作台后会自动带入对应智能体。"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ROUTES } from '@/router/routes'
import { listAgents } from '@/api/agent-chat'
import { getSkillList } from '@/api/skill'
import { getMcpServices } from '@/api/mcp'
import { getKnowledgeList } from '@/api/knowledge'

const CONFIG_STORAGE_PREFIX = 'agent-workspace-config:'

const defaultBinding = () => ({
  id: 'default',
  name: '初始配置（默认）',
  kbIds: [],
  skillIds: [],
  mcpIds: [],
  enableSuggestions: true,
  showRetrievedContext: true,
  autoRenameSession: true
})

const router = useRouter()

const loadingAgents = ref(false)
const selectedAgentId = ref('')
const agents = ref([])
const skills = ref([])
const mcpServices = ref([])
const knowledgeBases = ref([])
const kbKeyword = ref('')
const skillKeyword = ref('')
const mcpKeyword = ref('')
const binding = reactive(defaultBinding())

const filteredSkills = computed(() => {
  const keyword = skillKeyword.value.trim().toLowerCase()
  if (!keyword) return skills.value
  return skills.value.filter((item) => {
    const name = String(item.skillName || item.name || '').toLowerCase()
    const type = String(item.skillType || item.type || '').toLowerCase()
    return name.includes(keyword) || type.includes(keyword)
  })
})

const filteredMcpServices = computed(() => {
  const keyword = mcpKeyword.value.trim().toLowerCase()
  if (!keyword) return mcpServices.value
  return mcpServices.value.filter((item) => {
    const name = String(item.name || '').toLowerCase()
    const type = String(item.type || '').toLowerCase()
    return name.includes(keyword) || type.includes(keyword)
  })
})

const filteredKnowledgeBases = computed(() => {
  const keyword = kbKeyword.value.trim().toLowerCase()
  if (!keyword) return knowledgeBases.value
  return knowledgeBases.value.filter((item) => {
    const name = String(item.name || '').toLowerCase()
    return name.includes(keyword)
  })
})

const builtinTools = computed(() => {
  const hasKb = binding.kbIds.length > 0
  return [
    { name: 'query_kb', description: '在知识库中语义检索相关文档片段', active: hasKb },
    { name: 'read_document', description: '读取指定文档的完整内容（由 query_kb 返回的 doc_id 触发）', active: hasKb }
  ]
})

const hasId = (list, id) => list.some((item) => String(item) === String(id))

const getStorageKey = (agentId) => `${CONFIG_STORAGE_PREFIX}${agentId}`

const toKnownId = (id, sourceList) => {
  const matched = sourceList.find((item) => String(item.id) === String(id))
  return matched ? matched.id : id
}

const normalizeBindingIds = () => {
  binding.kbIds = [...new Set(binding.kbIds.map((id) => toKnownId(id, knowledgeBases.value)))]
  binding.skillIds = [...new Set(binding.skillIds.map((id) => toKnownId(id, skills.value)))]
  binding.mcpIds = [...new Set(binding.mcpIds.map((id) => toKnownId(id, mcpServices.value)))]
}

const readBinding = () => {
  Object.assign(binding, defaultBinding())
  if (!selectedAgentId.value) return
  try {
    const raw = localStorage.getItem(getStorageKey(selectedAgentId.value))
    if (!raw) return
    const parsed = JSON.parse(raw)
    Object.assign(binding, defaultBinding(), parsed || {})
    binding.kbIds = Array.isArray(binding.kbIds) ? binding.kbIds : []
    binding.skillIds = Array.isArray(binding.skillIds) ? binding.skillIds : []
    binding.mcpIds = Array.isArray(binding.mcpIds) ? binding.mcpIds : []
    normalizeBindingIds()
  } catch (error) {
    console.warn('Failed to load tools binding from storage:', error)
  }
}

const saveBinding = () => {
  if (!selectedAgentId.value) {
    ElMessage.warning('请先选择智能体')
    return
  }
  normalizeBindingIds()
  localStorage.setItem(getStorageKey(selectedAgentId.value), JSON.stringify({ ...binding }))
  ElMessage.success('工具卡片配置已保存')
}

const resetBinding = () => {
  if (!selectedAgentId.value) return
  Object.assign(binding, defaultBinding())
  saveBinding()
}

const toggleId = (field, targetId) => {
  const list = binding[field] || []
  if (hasId(list, targetId)) {
    binding[field] = list.filter((id) => String(id) !== String(targetId))
  } else {
    binding[field] = [...list, targetId]
  }
}

const goWorkspace = () => {
  if (!selectedAgentId.value) return
  router.push({
    path: ROUTES.AGENTS.WORKSPACE,
    query: { tab: 'tools', agentId: selectedAgentId.value }
  })
}

const formatStatus = (status) => {
  const map = {
    CONNECTED: '已连接',
    DISCONNECTED: '未连接',
    ERROR: '异常',
    TESTING: '测试中'
  }
  return map[status] || status || '未知'
}

const loadAgents = async () => {
  loadingAgents.value = true
  try {
    const res = await listAgents({ page: 1, size: 100 })
    const list = Array.isArray(res?.data?.records)
      ? res.data.records
      : Array.isArray(res?.data)
        ? res.data
        : Array.isArray(res)
          ? res
          : []
    agents.value = list.map((agent) => ({
      id: agent.id || agent.agentId,
      name: agent.name || agent.agentName || '未命名智能体',
      model: agent.model || agent.modelName || ''
    })).filter((agent) => agent.id != null)

    if (agents.value.length && !selectedAgentId.value) {
      selectedAgentId.value = agents.value[0].id
    }
  } finally {
    loadingAgents.value = false
  }
}

const loadSkills = async () => {
  const data = await getSkillList({})
  skills.value = Array.isArray(data) ? data : []
}

const loadMcpServices = async () => {
  const data = await getMcpServices()
  mcpServices.value = Array.isArray(data) ? data : []
}

const loadKnowledgeBases = async () => {
  const res = await getKnowledgeList({ page: 1, size: 100 })
  const list = Array.isArray(res?.data?.records)
    ? res.data.records
    : Array.isArray(res?.data)
      ? res.data
      : Array.isArray(res)
        ? res
        : []
  knowledgeBases.value = list.map((kb) => ({
    id: kb.id || kb.kbId,
    name: kb.name || '未命名知识库',
    documentCount: Number(kb.documentCount ?? kb.docCount ?? kb.stats?.documentCount ?? 0)
  })).filter((kb) => kb.id != null)
}

watch(selectedAgentId, () => {
  readBinding()
})

onMounted(async () => {
  await Promise.all([loadAgents(), loadSkills(), loadMcpServices(), loadKnowledgeBases()])
  readBinding()
})
</script>

<style scoped>
.tools-binding-panel {
  padding: 0;
}

.binding-header-card {
  border-radius: 12px;
  border: 1px solid var(--orin-border);
  margin-bottom: 14px;
}

.binding-header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}

.binding-title {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}

.binding-desc {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.binding-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.binding-agent-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.binding-agent-label {
  font-weight: 600;
  color: #334155;
}

.agent-select {
  min-width: 320px;
}

.binding-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-pill {
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: #0f766e;
  background: #ccfbf1;
}

.binding-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.binding-card {
  border-radius: 12px;
  border: 1px solid var(--orin-border);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-input {
  margin-bottom: 10px;
}

.selection-list {
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--orin-border);
  border-radius: 10px;
  background: #fff;
}

.selection-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}

.selection-item:last-child {
  border-bottom: none;
}

.selection-info {
  min-width: 0;
}

.selection-name {
  font-weight: 600;
  color: #0f172a;
}

.selection-meta {
  color: #64748b;
  font-size: 12px;
  margin-top: 2px;
}

.agent-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.agent-option-meta {
  color: #94a3b8;
  font-size: 12px;
}

.kb-note {
  margin-top: 14px;
}

@media (max-width: 1080px) {
  .binding-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .binding-header-top {
    flex-direction: column;
  }

  .binding-agent-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .agent-select {
    width: 100%;
    min-width: 0;
  }
}
</style>
