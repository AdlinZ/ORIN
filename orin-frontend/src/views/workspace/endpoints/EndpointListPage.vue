<!--
  Endpoints 发布页（Workspace vNext — F05）
  功能：发布已冻结 Agent 为 REST API 或 MCP Server 端点
-->
<template>
  <div class="endpoints-page">
    <div class="page-header">
      <div>
        <h2>发布中心</h2>
        <p class="subtitle">把已经验证的 Agent 交付给应用或 AI 客户端，并随时查看调用方式。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openPublishDialog">发布服务</el-button>
    </div>

    <el-alert
      v-if="catalogLoaded && frozenAgents.length === 0"
      type="info"
      :closable="false"
      show-icon
      class="blocking-alert"
      title="还没有可发布的 Agent"
    >
      <template #default>
        <div class="alert-action">
          <span>先完成 Agent 配置并冻结版本，再发布服务。</span>
          <el-button size="small" @click="router.push(ROUTES.WORKSPACE.AGENTS)">前往 Agent</el-button>
        </div>
      </template>
    </el-alert>
    <el-alert
      v-else-if="state.status === 'success' && endpoints.length > 0 && activeCount === 0"
      type="warning"
      :closable="false"
      show-icon
      class="blocking-alert"
      title="当前没有对外服务"
    >
      <template #default>
        <span>已有服务都处于下线状态；重新上线后，外部客户端才能继续调用。</span>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="发布状态">
      <button type="button" :class="{ active: statusFilter === '' }" @click="statusFilter = ''">
        <strong>{{ endpoints.length }}</strong><span>全部交付</span>
      </button>
      <button type="button" :class="{ active: statusFilter === 'ACTIVE' }" @click="statusFilter = 'ACTIVE'">
        <strong>{{ activeCount }}</strong><span>可调用</span>
      </button>
      <button type="button" :class="{ active: statusFilter === 'INACTIVE' }" @click="statusFilter = 'INACTIVE'">
        <strong>{{ inactiveCount }}</strong><span>已下线</span>
      </button>
    </section>

    <OrinAsyncState :status="state.status" empty-text="暂无发布服务。先运行验证一个 Agent，再发布给外部调用。" @retry="loadData">
      <OrinDataTable title="已交付服务" :description="`${filteredEndpoints.length} / ${endpoints.length} 个服务`">
        <ResizableTable :data="filteredEndpoints" stripe border v-loading="state.status === 'loading'">
          <el-table-column label="服务" min-width="330">
            <template #default="{ row }">
              <div class="service-identity">
                <strong>{{ row.name }}</strong>
                <span>{{ row.description || '暂未填写用途说明' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="调用方式" min-width="210">
            <template #default="{ row }">
              <div class="delivery-type">
                <el-tag :type="row.endpointType === 'MCP_SERVER' ? 'success' : ''" size="small" effect="plain">
                  {{ row.endpointType === 'MCP_SERVER' ? 'MCP' : 'REST' }}
                </el-tag>
                <span>{{ row.endpointType === 'MCP_SERVER' ? '供 AI 客户端发现和调用' : '供应用和脚本通过 HTTP 调用' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Agent / 版本" min-width="210">
            <template #default="{ row }">
              <div class="service-identity">
                <strong>{{ agentName(row.agentId) }}</strong>
                <span>{{ endpointVersionLabel(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="交付状态" min-width="170">
            <template #default="{ row }">
              <div class="delivery-status">
                <el-tag :type="endpointStatus(row.status).type" size="small">
                  {{ row.status === 'ACTIVE' ? '可调用' : '已下线' }}
                </el-tag>
                <span>{{ row.status === 'ACTIVE' ? '外部调用入口已开放' : '外部调用已停止' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="135" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDeliveryGuide(row)">
                查看调用方式
              </el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="warning" size="small"
                @click="handleDeactivate(row)">下线</el-button>
              <el-button v-if="row.status === 'INACTIVE'" link type="success" size="small"
                @click="handleActivate(row)">重新上线</el-button>
            </template>
          </el-table-column>
        </ResizableTable>
      </OrinDataTable>
    </OrinAsyncState>

    <el-dialog v-model="dialogVisible" title="发布服务" width="640px" @closed="resetForm">
      <p class="publish-intro">选择已经验证的 Agent，并决定外部系统如何调用它。</p>

      <section class="publish-section">
        <div class="publish-step"><span>1</span>选择要发布的 Agent</div>
        <el-select
          v-model="form.agentId"
          placeholder="选择可发布 Agent"
          filterable
          style="width: 100%"
          @change="onAgentChange"
        >
          <el-option
            v-for="agent in frozenAgents"
            :key="agent.agentId"
            :label="agent.name"
            :value="agent.agentId"
          />
        </el-select>
        <div v-if="selectedAgent" class="publish-selection">
          <div>
            <strong>{{ selectedAgent.name }}</strong>
            <span>{{ selectedAgent.description || '已冻结，可交付给外部调用' }}</span>
          </div>
          <el-tag type="success" effect="plain">{{ selectedVersionLabel }}</el-tag>
        </div>

        <el-collapse v-if="agentVersions.length > 1" v-model="publishSettingPanels" class="publish-settings">
          <el-collapse-item name="version">
            <template #title>
              <div class="publish-settings-title">
                <strong>发布设置</strong>
                <span>{{ selectedVersionLabel }}</span>
              </div>
            </template>
            <el-form :model="form" label-position="top">
              <el-form-item label="发布版本" required>
                <el-select v-model="form.agentVersionId" placeholder="选择冻结版本" style="width: 100%">
                  <el-option
                    v-for="version in agentVersions"
                    :key="getAgentVersionId(version)"
                    :label="versionOptionLabel(version)"
                    :value="getAgentVersionId(version)"
                  />
                </el-select>
              </el-form-item>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </section>

      <section class="publish-section">
        <div class="publish-step"><span>2</span>选择调用方式</div>
        <el-radio-group v-model="form.endpointType" class="delivery-method-options">
          <el-radio value="REST_API" border>
            <div>
              <strong>REST API</strong>
              <span>供应用和脚本通过 HTTP 调用</span>
            </div>
          </el-radio>
          <el-radio value="MCP_SERVER" border>
            <div>
              <strong>MCP 工具</strong>
              <span>供 AI 客户端发现并调用</span>
            </div>
          </el-radio>
        </el-radio-group>
      </section>

      <section class="publish-section">
        <div class="publish-step"><span>3</span>设置服务信息</div>
        <el-form :model="form" label-position="top">
          <el-form-item label="服务名称" required>
            <el-input v-model="form.name" placeholder="例如：客服问答服务" />
          </el-form-item>
          <el-form-item label="用途说明">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="2"
              placeholder="说明谁会使用这个服务，以及它解决什么问题"
            />
          </el-form-item>
        </el-form>
      </section>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="publishing"
          :disabled="!publishReady"
          @click="doPublish"
        >发布服务</el-button>
      </template>
    </el-dialog>

    <!-- 明文 Key 只在 publish 响应中存在；关闭后立即从页面状态清除。 -->
    <el-dialog
      v-model="deliveryVisible"
      title="服务已发布"
      width="720px"
      :close-on-click-modal="false"
      @closed="clearDelivery"
    >
      <el-steps :active="deliveryStep" simple finish-status="success" class="delivery-steps">
        <el-step title="保存密钥" />
        <el-step title="接入服务" />
      </el-steps>

      <template v-if="deliveryStep === 0">
        <el-alert type="warning" :closable="false" show-icon>
          此 API Key 仅显示一次。关闭窗口后无法再次获取明文。
        </el-alert>
        <div class="delivery-section key-section">
          <div class="delivery-label">先保存 API Key</div>
          <p class="delivery-hint">复制后存入密码管理器或密钥管理服务，不要写入代码仓库。</p>
          <div class="delivery-row">
            <el-input :model-value="delivery?.secretKey || ''" readonly />
            <el-button type="primary" @click="copyDeliveryKey">复制密钥</el-button>
          </div>
          <el-checkbox v-model="deliveryKeySaved" class="saved-confirmation">
            我已将密钥保存到安全位置
          </el-checkbox>
        </div>
      </template>

      <template v-else>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          :title="`密钥已确认保存，现在复制 ${deliveryProtocolLabel} 调用方式。`"
        />
        <template v-if="delivery?.endpointType === 'MCP_SERVER'">
          <div class="delivery-section">
            <div class="delivery-label">MCP 客户端配置</div>
            <p class="delivery-hint">适用于支持 Streamable HTTP 的 MCP 客户端。</p>
            <el-input type="textarea" :rows="9" :model-value="mcpConfig" readonly />
            <el-button class="copy-code" @click="copyText(mcpConfig, 'MCP 配置已复制')">复制 MCP 配置</el-button>
          </div>
        </template>
        <template v-else>
          <div class="delivery-section">
            <div class="delivery-label">调用地址</div>
            <div class="delivery-row">
              <el-input :model-value="publicEndpointUrl" readonly />
              <el-button @click="copyText(publicEndpointUrl, '调用地址已复制')">复制</el-button>
            </div>
          </div>
          <div class="delivery-section">
            <div class="delivery-label">curl 示例</div>
            <el-input type="textarea" :rows="6" :model-value="curlExample" readonly />
            <el-button class="copy-code" @click="copyText(curlExample, 'curl 示例已复制')">复制 curl</el-button>
          </div>
        </template>
      </template>

      <template #footer>
        <el-button v-if="deliveryStep === 1" @click="deliveryStep = 0">返回密钥</el-button>
        <el-button
          v-if="deliveryStep === 0"
          type="primary"
          :disabled="!deliveryKeySaved"
          @click="deliveryStep = 1"
        >查看调用方式</el-button>
        <el-button v-else type="primary" @click="deliveryVisible = false">完成交付</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="guideVisible" title="使用服务" width="720px" @closed="guideEndpoint = null">
      <template v-if="guideEndpoint">
        <div class="guide-heading">
          <div>
            <strong>{{ guideEndpoint.name }}</strong>
            <span>{{ guideEndpoint.endpointType === 'MCP_SERVER' ? 'MCP 工具' : 'REST API' }}</span>
          </div>
          <el-tag :type="endpointStatus(guideEndpoint.status).type">
            {{ guideEndpoint.status === 'ACTIVE' ? '可调用' : '已下线' }}
          </el-tag>
        </div>

        <el-alert
          v-if="guideEndpoint.status !== 'ACTIVE'"
          type="warning"
          :closable="false"
          show-icon
          title="服务当前已下线，重新上线后才能调用。"
        />

        <template v-if="guideEndpoint.endpointType === 'MCP_SERVER'">
          <div class="delivery-section">
            <div class="delivery-label">MCP 客户端配置</div>
            <p class="delivery-hint">将 YOUR_API_KEY 替换为发布时保存的平台访问密钥。</p>
            <el-input type="textarea" :rows="9" :model-value="guideMcpConfig" readonly />
            <el-button class="copy-code" @click="copyText(guideMcpConfig, 'MCP 配置已复制')">复制 MCP 配置</el-button>
          </div>
        </template>
        <template v-else>
          <div class="delivery-section">
            <div class="delivery-label">调用地址</div>
            <div class="delivery-row">
              <el-input :model-value="guideEndpointUrl" readonly />
              <el-button @click="copyText(guideEndpointUrl, '调用地址已复制')">复制</el-button>
            </div>
          </div>
          <div class="delivery-section">
            <div class="delivery-label">curl 示例</div>
            <p class="delivery-hint">将 YOUR_API_KEY 替换为发布时保存的平台访问密钥。</p>
            <el-input type="textarea" :rows="6" :model-value="guideCurlExample" readonly />
            <el-button class="copy-code" @click="copyText(guideCurlExample, 'curl 示例已复制')">复制 curl</el-button>
          </div>
        </template>
      </template>

      <template #footer>
        <el-button @click="goManageAccessKeys">高级密钥管理</el-button>
        <el-button type="primary" @click="guideVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { publishEndpoint, listEndpoints, deactivateEndpoint, activateEndpoint } from '@/domains/endpoint/api'
import { listAgents, getAgentVersions } from '@/domains/agent/api'
import {
  buildCurlCommand,
  buildMcpConfig,
  buildPublicUrl,
  resolvePublicOrigin,
} from '@/domains/endpoint/publishDelivery'
import { ROUTES } from '@/router/routes'
import {
  chooseDeliverableVersion,
  compactId,
  formatWorkspaceTime,
  getAgentVersionId,
  getAgentVersionNumber,
  getEndpointStatusMeta,
} from '@/views/workspace/coreLoopPresentation'

const router = useRouter()
const route = useRoute()
const state = reactive({ status: 'loading', error: null })
const endpoints = ref([])
const dialogVisible = ref(false)
const deliveryVisible = ref(false)
const delivery = ref(null)
const deliveryStep = ref(0)
const deliveryKeySaved = ref(false)
const guideVisible = ref(false)
const guideEndpoint = ref(null)
const publishing = ref(false)
const publishSettingPanels = ref([])
const statusFilter = ref('')
const catalogLoaded = ref(false)
const agentCatalog = ref([])
const endpointStatus = getEndpointStatusMeta

const form = reactive({
  name: '',
  endpointType: 'REST_API',
  agentId: '',
  agentVersionId: '',
  description: ''
})

const frozenAgents = ref([])
const agentVersions = ref([])
const currentOrigin = resolvePublicOrigin(
  window.location.origin,
  import.meta.env.VITE_ORIN_PUBLIC_ORIGIN,
)
const publicEndpointUrl = computed(() => buildPublicUrl(currentOrigin, delivery.value?.externalUrl))
const curlExample = computed(() => buildCurlCommand(publicEndpointUrl.value, delivery.value?.secretKey || ''))
const mcpConfig = computed(() => buildMcpConfig(currentOrigin, delivery.value?.secretKey || ''))
const deliveryProtocolLabel = computed(() => delivery.value?.endpointType === 'MCP_SERVER' ? 'MCP' : 'REST API')
const guideEndpointUrl = computed(() => buildPublicUrl(currentOrigin, guideEndpoint.value?.externalUrl))
const guideCurlExample = computed(() => buildCurlCommand(guideEndpointUrl.value, 'YOUR_API_KEY'))
const guideMcpConfig = computed(() => buildMcpConfig(currentOrigin, 'YOUR_API_KEY'))
const filteredEndpoints = computed(() => statusFilter.value
  ? endpoints.value.filter((item) => item.status === statusFilter.value)
  : endpoints.value)
const activeCount = computed(() => endpoints.value.filter((item) => item.status === 'ACTIVE').length)
const inactiveCount = computed(() => endpoints.value.filter((item) => item.status === 'INACTIVE').length)
const selectedAgent = computed(() =>
  frozenAgents.value.find((agent) => agent.agentId === form.agentId) || null
)
const selectedVersion = computed(() =>
  agentVersions.value.find((version) => getAgentVersionId(version) === form.agentVersionId) || null
)
const selectedVersionLabel = computed(() => selectedVersion.value
  ? `版本 v${getAgentVersionNumber(selectedVersion.value) || '?'}`
  : '正在读取版本')
const publishReady = computed(() =>
  Boolean(form.name.trim() && form.agentId && form.agentVersionId && form.endpointType)
)

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
    agentCatalog.value = agents || []
    frozenAgents.value = agentCatalog.value.filter(a => a.activeVersionStatus === 'FROZEN')
  } catch (_) { /* ignore */ }
  finally {
    catalogLoaded.value = true
  }
}

async function onAgentChange(agentId, preferredVersionId = '') {
  form.agentVersionId = ''
  agentVersions.value = []
  if (!agentId) return
  try {
    const list = await getAgentVersions(agentId)
    agentVersions.value = (list || []).filter(v => v.status === 'FROZEN')
    const agent = frozenAgents.value.find((item) => item.agentId === agentId)
    form.agentVersionId = chooseDeliverableVersion(agent, agentVersions.value, preferredVersionId)
    if (!form.name.trim() && agent?.name) {
      form.name = `${agent.name} 服务`
    }
  } catch (_) { /* ignore */ }
}

async function openPublishDialog() {
  if (!catalogLoaded.value) await loadFrozenAgents()
  if (frozenAgents.value.length === 0) {
    router.push(ROUTES.WORKSPACE.AGENTS)
    return
  }
  if (!form.agentId && frozenAgents.value.length === 1) {
    form.agentId = frozenAgents.value[0].agentId
    await onAgentChange(form.agentId)
  }
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.endpointType = 'REST_API'
  form.agentId = ''
  form.agentVersionId = ''
  form.description = ''
  agentVersions.value = []
  publishSettingPanels.value = []
}

async function doPublish() {
  if (!form.name || !form.agentId || !form.agentVersionId) {
    ElMessage.warning('请选择 Agent 并填写服务名称')
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
    deliveryStep.value = 0
    deliveryKeySaved.value = false
    dialogVisible.value = false
    deliveryVisible.value = true
    await loadData()
    ElMessage.success('服务已发布，请保存 API Key')
  } catch (e) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

function clearDelivery() {
  delivery.value = null
  deliveryStep.value = 0
  deliveryKeySaved.value = false
}

function openDeliveryGuide(endpoint) {
  guideEndpoint.value = endpoint
  guideVisible.value = true
}

function goManageAccessKeys() {
  guideVisible.value = false
  router.push(ROUTES.SYSTEM.API_KEYS)
}

async function copyDeliveryKey() {
  await copyText(delivery.value?.secretKey, 'API Key 已复制')
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

function formatTime(ts) {
  return formatWorkspaceTime(ts)
}

function agentName(agentId) {
  return agentCatalog.value.find((agent) => agent.agentId === agentId)?.name || compactId(agentId)
}

function endpointVersionLabel(row) {
  const agent = agentCatalog.value.find((item) => item.agentId === row.agentId)
  if (agent?.activeVersionId === row.agentVersionId && agent.activeVersionNumber) {
    return `v${agent.activeVersionNumber}`
  }
  return '固定冻结版本'
}

function versionOptionLabel(version) {
  const number = getAgentVersionNumber(version) || '?'
  const description = version.changeDescription || version.change_description || '冻结版本'
  return `v${number} · ${description}`
}

onMounted(async () => {
  await Promise.all([loadData(), loadFrozenAgents()])
  const guideId = String(route.query.guide || '')
  if (guideId) {
    const endpoint = endpoints.value.find((item) => item.id === guideId)
    if (endpoint) openDeliveryGuide(endpoint)
  }
  if (route.query.publish === '1') {
    await openPublishDialog()
    const selected = frozenAgents.value.find((agent) => agent.agentId === String(route.query.agentId || ''))
    if (selected) {
      form.agentId = selected.agentId
      await onAgentChange(selected.agentId, String(route.query.versionId || ''))
    }
  }
})
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

.blocking-alert {
  margin-bottom: 16px;
}

.alert-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-strip {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.summary-strip button {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 14px;
  border: 1px solid var(--el-border-color-light, #dbe2ea);
  border-radius: 10px;
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-secondary, #64748b);
  cursor: pointer;
}

.summary-strip button.active {
  border-color: var(--el-color-primary, #155eef);
  color: var(--el-color-primary, #155eef);
  background: var(--el-color-primary-light-9, #eff4ff);
}

.summary-strip strong {
  font-size: 18px;
}

.service-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.service-identity code,
.service-identity span {
  overflow: hidden;
  color: var(--el-text-color-secondary, #64748b);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delivery-type,
.delivery-status {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.delivery-type span,
.delivery-status span {
  overflow: hidden;
  max-width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-intro {
  margin: -6px 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.publish-section {
  margin-bottom: 22px;
}

.publish-step {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.publish-step > span {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
}

.publish-selection {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--el-color-success-light-7);
  border-radius: 8px;
  background: var(--el-color-success-light-9);
}

.publish-selection > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.publish-selection span {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-settings {
  margin-top: 10px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.publish-settings :deep(.el-collapse-item__header),
.publish-settings :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.publish-settings-title {
  display: flex;
  width: calc(100% - 22px);
  align-items: center;
  justify-content: space-between;
}

.publish-settings-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.delivery-method-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.delivery-method-options :deep(.el-radio) {
  width: 100%;
  height: auto;
  min-height: 70px;
  margin: 0;
  padding: 12px 14px;
}

.delivery-method-options :deep(.el-radio__label) {
  width: 100%;
}

.delivery-method-options :deep(.el-radio__label > div) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.delivery-method-options span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.delivery-section {
  margin-top: 20px;
}

.delivery-steps {
  margin-bottom: 20px;
}

.key-section {
  padding: 18px;
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 10px;
  background: var(--el-color-warning-light-9);
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

.saved-confirmation {
  margin-top: 14px;
}

.copy-code {
  margin-top: 8px;
}

.guide-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.guide-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.guide-heading strong {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guide-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .delivery-method-options {
    grid-template-columns: 1fr;
  }
}
</style>
