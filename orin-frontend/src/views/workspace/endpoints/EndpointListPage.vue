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

    <el-alert type="info" :closable="false" style="margin-bottom: 16px">
      发布后会一次性展示 API Key、REST 调用示例和 MCP 配置。请在关闭交接窗口前安全保存 Key；后续不能再次查看明文。
    </el-alert>

    <OrinAsyncState :status="state.status" empty-text="暂无端点。选择已冻结的 Agent 版本发布。" @retry="loadData">
      <OrinDataTable>
        <el-table :data="endpoints" stripe border v-loading="state.status === 'loading'">
          <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="外部调用地址" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <code>{{ row.externalUrl || row.endpointPath }}</code>
            </template>
          </el-table-column>
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

    <!-- 明文 Key 只在 publish 响应中存在；关闭后立即从页面状态清除。 -->
    <el-dialog
      v-model="deliveryVisible"
      title="端点已发布"
      width="720px"
      :close-on-click-modal="false"
      @closed="clearDelivery"
    >
      <el-alert type="warning" :closable="false" show-icon>
        此 API Key 仅显示一次。请立即复制并存入安全的密钥管理工具；关闭此窗口后无法再次获取明文。
      </el-alert>

      <div class="delivery-section">
        <div class="delivery-label">API Key</div>
        <div class="delivery-row">
          <el-input :model-value="delivery?.secretKey || ''" readonly />
          <el-button @click="copyText(delivery?.secretKey, 'API Key 已复制')">复制</el-button>
        </div>
      </div>

      <div class="delivery-section">
        <div class="delivery-label">REST 调用地址</div>
        <div class="delivery-row">
          <el-input :model-value="publicEndpointUrl" readonly />
          <el-button @click="copyText(publicEndpointUrl, '调用地址已复制')">复制</el-button>
        </div>
      </div>

      <div class="delivery-section">
        <div class="delivery-label">curl 示例</div>
        <el-input type="textarea" :rows="5" :model-value="curlExample" readonly />
        <el-button class="copy-code" @click="copyText(curlExample, 'curl 示例已复制')">复制 curl</el-button>
      </div>

      <div class="delivery-section">
        <div class="delivery-label">MCP 客户端配置</div>
        <p class="delivery-hint">将此配置加入支持 Streamable HTTP 的 MCP 客户端；它会连接 ORIN 已有的 MCP 入口。</p>
        <el-input type="textarea" :rows="8" :model-value="mcpConfig" readonly />
        <el-button class="copy-code" @click="copyText(mcpConfig, 'MCP 配置已复制')">复制 MCP 配置</el-button>
      </div>

      <template #footer>
        <el-button type="primary" @click="deliveryVisible = false">我已安全保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { publishEndpoint, listEndpoints, deactivateEndpoint, activateEndpoint } from '@/domains/endpoint/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'
import { buildCurlCommand, buildMcpConfig, buildPublicUrl } from '@/domains/endpoint/publishDelivery'

const state = reactive({ status: 'loading', error: null })
const endpoints = ref([])
const dialogVisible = ref(false)
const deliveryVisible = ref(false)
const delivery = ref(null)
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
const currentOrigin = window.location.origin
const publicEndpointUrl = computed(() => buildPublicUrl(currentOrigin, delivery.value?.externalUrl))
const curlExample = computed(() => buildCurlCommand(publicEndpointUrl.value, delivery.value?.secretKey || ''))
const mcpConfig = computed(() => buildMcpConfig(currentOrigin, delivery.value?.secretKey || ''))

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
    const published = await publishEndpoint({
      name: form.name,
      endpointType: form.endpointType,
      agentId: form.agentId,
      agentVersionId: form.agentVersionId,
      description: form.description
    })
    if (!published?.secretKey || !published?.externalUrl) {
      throw new Error('发布响应缺少一次性 API Key 或外部调用地址')
    }
    delivery.value = published
    dialogVisible.value = false
    deliveryVisible.value = true
    await loadData()
    ElMessage.success('端点已发布，请保存 API Key')
  } catch (e) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

function clearDelivery() {
  delivery.value = null
}

async function copyText(text, successMessage) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successMessage)
  } catch (_) {
    ElMessage.error('复制失败，请手动复制')
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

.delivery-section {
  margin-top: 20px;
}

.delivery-label {
  margin-bottom: 8px;
  color: #303133;
  font-weight: 600;
}

.delivery-row {
  display: flex;
  gap: 8px;
}

.delivery-hint {
  margin: 0 0 8px;
  color: #909399;
  font-size: 13px;
}

.copy-code {
  margin-top: 8px;
}
</style>
