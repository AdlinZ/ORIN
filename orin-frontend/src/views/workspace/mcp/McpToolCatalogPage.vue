<template>
  <main class="mcp-tool-page">
    <header class="page-header">
      <div>
        <h2>MCP 工具</h2>
        <p>Agent 只使用已启用且通过连接验证的 MCP 服务；先处理故障，再扩充工具。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="addService">
        添加服务
      </el-button>
    </header>

    <el-alert
      v-if="!loading && summary.error > 0"
      class="issue-alert"
      type="error"
      :closable="false"
      show-icon
      :title="`${summary.error} 个 MCP 服务连接失败`"
    >
      <template #default>
        <span>失败的服务不会提供给 Agent。请先重试连接；持续失败时检查命令、URL、密钥和 AI Engine。</span>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="MCP 服务可用性概览">
      <div>
        <span>可用于 Agent</span>
        <strong class="success-number">{{ summary.ready }}</strong>
        <small>已启用并通过 tools/list</small>
      </div>
      <div>
        <span>待验证</span>
        <strong class="warning-number">{{ summary.needsTest }}</strong>
        <small>需要运行连接测试</small>
      </div>
      <div>
        <span>故障 / 停用</span>
        <strong :class="{ 'danger-number': summary.error > 0 }">
          {{ summary.error + summary.disabled }}
        </strong>
        <small>{{ summary.error }} 个故障，{{ summary.disabled }} 个停用</small>
      </div>
    </section>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索服务名称、命令或 URL"
        class="search-input"
      />
      <el-select v-model="readinessFilter" class="filter-control">
        <el-option label="全部可用状态" value="ALL" />
        <el-option label="可用于 Agent" value="READY" />
        <el-option label="待验证" value="NEEDS_TEST" />
        <el-option label="连接故障" value="ERROR" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-select v-model="typeFilter" class="filter-control">
        <el-option label="全部连接方式" value="ALL" />
        <el-option label="本机命令（STDIO）" value="STDIO" />
        <el-option label="远程服务（SSE）" value="SSE" />
      </el-select>
      <el-button class="secondary-button" :icon="Refresh" :loading="loading" @click="loadServices">
        刷新
      </el-button>
    </div>

    <OrinAsyncState
      :status="loadState.status"
      :error-text="loadErrorText"
      empty-text="还没有 MCP 服务。可以从工具模板安装，或添加自定义服务。"
      empty-action-label="添加服务"
      @retry="loadServices"
      @empty-action="addService"
    >
      <OrinDataTable
        title="Agent 工具服务"
        :description="`${filteredRows.length} / ${rows.length} 个服务`"
      >
        <ResizableTable
          :data="filteredRows"
          border
          stripe
          :row-style="{ cursor: 'pointer' }"
          empty-text="没有符合当前筛选的 MCP 服务"
          @row-click="editService"
        >
          <el-table-column label="服务" min-width="300">
            <template #default="{ row }">
              <div class="service-identity">
                <strong>{{ row.name }}</strong>
                <small>{{ row.description || '暂未填写用途说明' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="连接方式" width="170">
            <template #default="{ row }">
              <div class="connection-cell">
                <el-tag size="small" effect="plain">
                  {{ row.type === 'STDIO' ? '本机命令' : '远程服务' }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Agent 可用状态" min-width="230">
            <template #default="{ row }">
              <div class="readiness-cell">
                <el-tag :type="readinessMeta(row).tag" size="small">
                  {{ readinessMeta(row).label }}
                </el-tag>
                <small>{{ readinessMeta(row).description }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近验证" min-width="250">
            <template #default="{ row }">
              <div class="verification-cell">
                <strong v-if="mcpServiceReadiness(row) === 'ERROR'">
                  {{ compactError(row.lastError) }}
                </strong>
                <span v-else>{{ formatTime(row.lastConnected) }}</span>
                <small v-if="mcpServiceReadiness(row) === 'ERROR'">查看配置或重试连接</small>
                <small v-else>{{ row.lastConnected ? '最近一次连接成功' : '还没有成功记录' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.enabled"
                link
                :type="mcpServiceReadiness(row) === 'ERROR' ? 'danger' : 'primary'"
                size="small"
                :loading="testingId === row.id"
                @click.stop="testService(row)"
              >
                {{ row.status === 'CONNECTED' ? '重新测试' : '测试连接' }}
              </el-button>
              <el-button
                v-else
                link
                type="success"
                size="small"
                :loading="togglingId === row.id"
                @click.stop="setEnabled(row, true)"
              >
                启用
              </el-button>
              <el-button link type="primary" size="small" @click.stop="editService(row)">
                编辑配置
              </el-button>
              <el-button
                v-if="row.enabled"
                link
                type="info"
                size="small"
                :loading="togglingId === row.id"
                @click.stop="setEnabled(row, false)"
              >
                停用
              </el-button>
            </template>
          </el-table-column>
        </ResizableTable>

        <div class="advanced-entry">
          <span>安装模板、密钥引用和删除服务等低频操作保留在高级管理页。</span>
          <el-button link type="primary" @click="openAdvanced">进入高级管理</el-button>
        </div>
      </OrinDataTable>
    </OrinAsyncState>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMcpServices, setMcpServiceEnabled, testMcpConnection } from '@/api/mcp'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { mcpServiceReadiness, toMcpServiceListViewModel } from '@/viewmodels'

const router = useRouter()
const rows = ref([])
const keyword = ref('')
const readinessFilter = ref('ALL')
const typeFilter = ref('ALL')
const loading = ref(false)
const testingId = ref(null)
const togglingId = ref(null)
const loadState = reactive({ status: 'loading', error: null })

const loadErrorText = computed(() => (
  loadState.error?.response?.data?.message
  || loadState.error?.message
  || 'MCP 服务加载失败，请稍后重试'
))

const summary = computed(() => rows.value.reduce((result, row) => {
  const readiness = mcpServiceReadiness(row)
  if (readiness === 'READY') result.ready += 1
  if (['NEEDS_TEST', 'TESTING'].includes(readiness)) result.needsTest += 1
  if (readiness === 'ERROR') result.error += 1
  if (readiness === 'DISABLED') result.disabled += 1
  return result
}, { ready: 0, needsTest: 0, error: 0, disabled: 0 }))

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const readiness = mcpServiceReadiness(row)
    const normalizedReadiness = readiness === 'TESTING' ? 'NEEDS_TEST' : readiness
    const matchesQuery = !query || [row.name, row.description, row.endpoint]
      .some((value) => String(value || '').toLowerCase().includes(query))
    const matchesReadiness = readinessFilter.value === 'ALL'
      || normalizedReadiness === readinessFilter.value
    const matchesType = typeFilter.value === 'ALL' || row.type === typeFilter.value
    return matchesQuery && matchesReadiness && matchesType
  })
})

function readinessMeta(row) {
  const definitions = {
    READY: {
      label: '可用于 Agent',
      tag: 'success',
      description: '已启用并通过 MCP tools/list',
    },
    NEEDS_TEST: {
      label: '待验证',
      tag: 'warning',
      description: '运行连接测试后才能确认可用',
    },
    TESTING: {
      label: '测试中',
      tag: 'warning',
      description: '正在验证 MCP 协议和工具列表',
    },
    ERROR: {
      label: '连接故障',
      tag: 'danger',
      description: '不会提供给 Agent',
    },
    DISABLED: {
      label: '已停用',
      tag: 'info',
      description: '不会提供给新的 Agent 任务',
    },
  }
  return definitions[mcpServiceReadiness(row)]
}

function compactError(value) {
  const text = String(value || '连接测试失败')
  return text.length > 48 ? `${text.slice(0, 45)}…` : text
}

function formatTime(value) {
  if (!value) return '尚未验证'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '尚未验证'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function loadServices() {
  loading.value = true
  loadState.status = 'loading'
  loadState.error = null
  try {
    rows.value = toMcpServiceListViewModel(await getMcpServices())
    loadState.status = rows.value.length > 0 ? 'success' : 'empty'
  } catch (error) {
    loadState.status = 'error'
    loadState.error = error
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

async function testService(row) {
  testingId.value = row.id
  try {
    const result = await testMcpConnection(row.id)
    if (result?.success) {
      ElMessage.success(`${row.name} 连接成功`)
    } else {
      ElMessage.error(result?.message || `${row.name} 连接失败`)
    }
    await loadServices()
  } catch (error) {
    ElMessage.error(error?.message || `${row.name} 连接测试失败`)
  } finally {
    testingId.value = null
  }
}

async function setEnabled(row, enabled) {
  togglingId.value = row.id
  try {
    const updated = await setMcpServiceEnabled(row.id, enabled)
    row.enabled = updated?.enabled ?? enabled
    row.status = updated?.status || (enabled ? 'DISCONNECTED' : 'DISCONNECTED')
    ElMessage.success(enabled ? 'MCP 服务已启用，请继续测试连接' : 'MCP 服务已停用')
  } catch (error) {
    ElMessage.error(error?.message || 'MCP 服务状态更新失败')
  } finally {
    togglingId.value = null
  }
}

function advancedUrl(query = '') {
  return `${ROUTES.AGENTS.MCP_ADVANCED}${query ? `&${query}` : ''}`
}

function addService() {
  router.push(advancedUrl('action=add'))
}

function editService(row) {
  if (!row?.id) return
  router.push(advancedUrl(`action=edit&serviceId=${encodeURIComponent(row.id)}`))
}

function openAdvanced() {
  router.push(ROUTES.AGENTS.MCP_ADVANCED)
}

onMounted(loadServices)
</script>

<style scoped>
.mcp-tool-page {
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
  padding: 28px 32px 40px;
}

.page-header,
.filter-bar,
.advanced-entry {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
}

.page-header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.filter-bar {
  gap: 10px;
}

.issue-alert {
  margin-bottom: 16px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.summary-strip > div {
  padding: 16px 18px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.summary-strip > div:last-child {
  border-right: 0;
}

.summary-strip span,
.summary-strip small {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-strip strong {
  display: inline-block;
  margin: 7px 0 5px;
  color: var(--el-text-color-primary);
  font-size: 24px;
}

.summary-strip .success-number {
  color: var(--el-color-success);
}

.summary-strip .warning-number {
  color: var(--el-color-warning);
}

.summary-strip .danger-number {
  color: var(--el-color-danger);
}

.filter-bar {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.search-input {
  width: min(380px, 100%);
}

.filter-control {
  width: 185px;
}

.service-identity,
.connection-cell,
.readiness-cell,
.verification-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.connection-cell,
.readiness-cell {
  align-items: flex-start;
}

.service-identity strong,
.service-identity small,
.connection-cell small,
.verification-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-identity strong,
.verification-cell strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.service-identity small,
.connection-cell small,
.readiness-cell small,
.verification-cell small,
.advanced-entry {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.verification-cell strong {
  color: var(--el-color-danger);
  font-weight: 600;
}

.advanced-entry {
  justify-content: flex-end;
  gap: 6px;
  padding-top: 14px;
}

.secondary-button {
  border-color: var(--el-border-color);
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

@media (max-width: 900px) {
  .mcp-tool-page {
    padding: 20px 16px 32px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-strip > div:nth-child(2) {
    border-right: 0;
  }

  .summary-strip > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
