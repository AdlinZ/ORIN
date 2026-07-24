<!--
  Endpoints 发布页（Workspace vNext — F05）
  功能：发布已冻结 Agent 为 REST API 或 MCP Server 端点
-->
<template>
  <div class="endpoints-page">
    <div class="page-header">
      <div>
        <h2>Endpoints</h2>
        <p class="subtitle">将已冻结的 Agent 版本发布为 REST API 或 MCP Server</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openPublishDialog">发布端点</el-button>
    </div>

    <OrinAsyncState :status="state.status" empty-text="暂无端点。选择已冻结的 Agent 版本发布。" @retry="loadData">
      <OrinDataTable>
        <el-table :data="endpoints" stripe border v-loading="state.status === 'loading'">
          <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="endpointPath" label="路径" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.endpointType === 'MCP_SERVER' ? 'success' : ''" size="small" effect="plain">
                {{ row.endpointType === 'MCP_SERVER' ? 'MCP' : 'REST' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="agentId" label="Agent" width="160" show-overflow-tooltip />
          <el-table-column prop="agentVersionId" label="Version" width="180" show-overflow-tooltip />
          <el-table-column label="发布时间" width="170" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" link type="warning" size="small"
                @click="handleDeactivate(row)">下线</el-button>
              <el-button v-if="row.status === 'INACTIVE'" link type="success" size="small"
                @click="handleActivate(row)">激活</el-button>
            </template>
          </el-table-column>
        </el-table>
      </OrinDataTable>
    </OrinAsyncState>

    <!-- 发布对话框 -->
    <el-dialog v-model="dialogVisible" title="发布端点" width="540px" @closed="resetForm">
      <el-form :model="form" label-position="top">
        <el-form-item label="端点名称" required>
          <el-input v-model="form.name" placeholder="如：客服 Agent v1" />
        </el-form-item>

        <el-form-item label="端点类型" required>
          <el-radio-group v-model="form.endpointType">
            <el-radio value="REST_API">REST API</el-radio>
            <el-radio value="MCP_SERVER">MCP Server</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="Agent（已冻结）" required>
          <el-select v-model="form.agentId" placeholder="选择 Agent" filterable
            @change="onAgentChange" style="width: 100%">
            <el-option v-for="a in frozenAgents" :key="a.agentId"
              :label="`${a.name} (v${a.activeVersionNumber})`" :value="a.agentId" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="agentVersions.length" label="版本">
          <el-select v-model="form.agentVersionId" placeholder="选择版本" style="width: 100%">
            <el-option v-for="v in agentVersions" :key="v.id"
              :label="`v${v.versionNumber} — ${v.changeDescription || '冻结版本'}`" :value="v.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="端点用途说明…" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="doPublish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { publishEndpoint, listEndpoints, deactivateEndpoint, activateEndpoint } from '@/domains/endpoint/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'

const state = reactive({ status: 'loading', error: null })
const endpoints = ref([])
const dialogVisible = ref(false)
const publishing = ref(false)

const form = reactive({
  name: '',
  endpointType: 'REST_API',
  agentId: '',
  agentVersionId: '',
  description: ''
})

const frozenAgents = ref([])
const agentVersions = ref([])

async function loadData() {
  state.status = 'loading'
  try {
    const page = await listEndpoints({ size: 100 })
    endpoints.value = page.content || []
    state.status = 'success'
  } catch (e) {
    state.status = 'error'
  }
}

async function loadFrozenAgents() {
  try {
    const agents = await listAgents()
    frozenAgents.value = (agents || []).filter(a => a.activeVersionStatus === 'FROZEN')
  } catch (_) { /* ignore */ }
}

async function onAgentChange(agentId) {
  form.agentVersionId = ''
  agentVersions.value = []
  if (!agentId) return
  try {
    const list = await getAgentVersions(agentId)
    agentVersions.value = (list || []).filter(v => v.status === 'FROZEN')
    if (agentVersions.value.length === 1) {
      form.agentVersionId = agentVersions.value[0].id
    }
  } catch (_) { /* ignore */ }
}

function openPublishDialog() {
  loadFrozenAgents()
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.endpointType = 'REST_API'
  form.agentId = ''
  form.agentVersionId = ''
  form.description = ''
  agentVersions.value = []
}

async function doPublish() {
  if (!form.name || !form.agentId || !form.agentVersionId) {
    ElMessage.warning('请填写名称、选择 Agent 和版本')
    return
  }
  publishing.value = true
  try {
    await publishEndpoint({
      name: form.name,
      endpointType: form.endpointType,
      agentId: form.agentId,
      agentVersionId: form.agentVersionId,
      description: form.description
    })
    ElMessage.success('端点已发布')
    dialogVisible.value = false
    resetForm()
    loadData()
  } catch (e) {
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

async function handleDeactivate(row) {
  try {
    await deactivateEndpoint(row.id)
    ElMessage.success('已下线')
    loadData()
  } catch (_) { /* ignore */ }
}

async function handleActivate(row) {
  try {
    await activateEndpoint(row.id)
    ElMessage.success('已激活')
    loadData()
  } catch (_) { /* ignore */ }
}

function statusTag(s) {
  return s === 'ACTIVE' ? 'success' : s === 'INACTIVE' ? 'info' : 'danger'
}

function formatTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.endpoints-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  h2 { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
  .subtitle { margin: 0; color: #909399; font-size: 13px; }
}
</style>
