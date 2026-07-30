<template>
  <main class="model-catalog-page">
    <header class="page-header">
      <div>
        <h2>模型</h2>
        <p>管理 Agent 可选择的模型，并优先处理缺少供应商凭据的配置。</p>
      </div>
      <div class="header-actions">
        <el-button class="secondary-button" :icon="Key" @click="openCredentials">
          管理供应商凭据
        </el-button>
        <el-button type="primary" :icon="Plus" @click="addModel">
          添加模型
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="!loading && credentialState === 'known' && summary.blocked > 0"
      class="readiness-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="`${summary.blocked} 个已启用模型还不能提供给 Agent`"
    >
      <template #default>
        <span>这些模型的供应商没有启用中的凭据。配置凭据后再进行 Agent 验证。</span>
        <el-button link type="warning" @click="openCredentials">去配置</el-button>
      </template>
    </el-alert>

    <section class="summary-strip" aria-label="模型可用性概览">
      <div>
        <span>可用于 Agent</span>
        <strong class="success-number">{{ summary.ready }}</strong>
        <small>已启用且具备凭据</small>
      </div>
      <div>
        <span>待配置凭据</span>
        <strong class="warning-number">{{ summary.blocked }}</strong>
        <small>启用但尚不可调用</small>
      </div>
      <div>
        <span>已停用</span>
        <strong>{{ summary.disabled }}</strong>
        <small>不会出现在新配置中</small>
      </div>
    </section>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索模型名称或 Model ID"
        class="search-input"
      />
      <el-select v-model="typeFilter" placeholder="全部用途" class="filter-control">
        <el-option label="全部用途" value="ALL" />
        <el-option
          v-for="option in typeOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select v-model="readinessFilter" placeholder="全部可用状态" class="filter-control">
        <el-option label="全部可用状态" value="ALL" />
        <el-option label="可用于 Agent" value="READY" />
        <el-option label="待配置凭据" value="BLOCKED" />
        <el-option label="已停用" value="DISABLED" />
        <el-option v-if="credentialState === 'unknown'" label="凭据状态未知" value="UNKNOWN" />
      </el-select>
      <el-select
        v-if="providerOptions.length > 1"
        v-model="providerFilter"
        placeholder="全部供应商"
        class="filter-control"
      >
        <el-option label="全部供应商" value="ALL" />
        <el-option
          v-for="provider in providerOptions"
          :key="provider"
          :label="provider"
          :value="provider"
        />
      </el-select>
      <el-button class="secondary-button" :icon="Refresh" :loading="loading" @click="loadModels">
        刷新
      </el-button>
    </div>

    <OrinAsyncState
      :status="loadState.status"
      :error-text="loadErrorText"
      empty-text="还没有模型。先添加一个 Agent 可以实际调用的模型。"
      empty-action-label="添加模型"
      @retry="loadModels"
      @empty-action="addModel"
    >
      <OrinDataTable
        title="Agent 可选模型"
        :description="`${filteredRows.length} / ${rows.length} 个模型`"
      >
        <ResizableTable
          :data="pageRows"
          border
          stripe
          :row-style="{ cursor: 'pointer' }"
          empty-text="没有符合当前筛选的模型"
          @row-click="editModel"
        >
          <el-table-column label="模型" min-width="330">
            <template #default="{ row }">
              <div class="model-identity">
                <strong>{{ row.name }}</strong>
                <small>{{ row.modelId }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="用途" width="165">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="typeMeta(row.type).tag">
                {{ typeMeta(row.type).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="150">
            <template #default="{ row }">
              <span>{{ row.provider || '未指定' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可用状态" min-width="220">
            <template #default="{ row }">
              <div class="readiness-cell">
                <el-tag :type="readinessMeta(row).tag" size="small">
                  {{ readinessMeta(row).label }}
                </el-tag>
                <small>{{ readinessMeta(row).description }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="下一步" width="210" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="readinessOf(row) === 'BLOCKED'"
                link
                type="warning"
                size="small"
                @click.stop="openCredentials"
              >
                配置凭据
              </el-button>
              <el-button v-else link type="primary" size="small" @click.stop="editModel(row)">
                编辑
              </el-button>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'info' : 'success'"
                size="small"
                :loading="togglingId === row.id"
                @click.stop="toggleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </ResizableTable>

        <div v-if="filteredRows.length > pageSize" class="pagination-row">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="filteredRows.length"
            layout="prev, pager, next"
            background
          />
        </div>

        <div class="advanced-entry">
          <span>批量删除、定价和导入等低频操作仍保留在高级管理中。</span>
          <el-button link type="primary" @click="openAdvanced">进入高级管理</el-button>
        </div>
      </OrinDataTable>
    </OrinAsyncState>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getModelList, getModelProviderCredentials, toggleModelStatus } from '@/api/model'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { toModelListViewModel } from '@/viewmodels'

const router = useRouter()
const rows = ref([])
const credentials = ref([])
const credentialState = ref('loading')
const keyword = ref('')
const typeFilter = ref('ALL')
const readinessFilter = ref('ALL')
const providerFilter = ref('ALL')
const currentPage = ref(1)
const pageSize = 20
const loading = ref(false)
const togglingId = ref(null)
const loadState = reactive({ status: 'loading', error: null })

const typeDefinitions = {
  CHAT: { label: '对话', tag: 'success' },
  LLM: { label: '语言模型', tag: 'success' },
  EMBEDDING: { label: '向量嵌入', tag: 'primary' },
  RERANKER: { label: '结果重排', tag: 'warning' },
  TEXT_TO_IMAGE: { label: '图像生成', tag: 'danger' },
  TEXT_TO_VIDEO: { label: '视频生成', tag: 'danger' },
  SPEECH_TO_TEXT: { label: '语音转文字', tag: 'info' },
  TEXT_TO_SPEECH: { label: '文字转语音', tag: 'info' },
}

const localProviderKeys = new Set(['ollama', 'local', 'localollama', 'lmstudio'])
const normalizeProvider = (value) => String(value || '').toLowerCase().replace(/[^a-z0-9]/g, '')
const isLocalProvider = (provider) => localProviderKeys.has(normalizeProvider(provider))

const activeCredentialProviders = computed(() => new Set(credentials.value
  .filter((item) => item.enabled !== false && !['DISABLED', 'REVOKED'].includes(item.status))
  .map((item) => normalizeProvider(item.provider))
  .filter(Boolean)))

const providerOptions = computed(() => Array.from(new Set(rows.value
  .map((item) => item.provider)
  .filter(Boolean))).sort())

const typeOptions = computed(() => Array.from(new Set(rows.value
  .map((item) => item.type)
  .filter(Boolean))).map((value) => ({
  value,
  label: typeMeta({ type: value }.type).label,
})).sort((left, right) => left.label.localeCompare(right.label, 'zh-CN')))

const loadErrorText = computed(() => (
  loadState.error?.response?.data?.message
  || loadState.error?.message
  || '模型列表加载失败，请稍后重试'
))

function typeMeta(type) {
  return typeDefinitions[type] || { label: type || '未分类', tag: 'info' }
}

function readinessOf(row) {
  if (row.status !== 'ENABLED') return 'DISABLED'
  if (isLocalProvider(row.provider)) return 'READY'
  if (credentialState.value !== 'known') return 'UNKNOWN'
  return activeCredentialProviders.value.has(normalizeProvider(row.provider)) ? 'READY' : 'BLOCKED'
}

function readinessMeta(row) {
  const definitions = {
    READY: {
      label: '可用于 Agent',
      tag: 'success',
      description: isLocalProvider(row.provider) ? '本地模型已启用' : '供应商凭据已启用',
    },
    BLOCKED: {
      label: '待配置凭据',
      tag: 'warning',
      description: '启用不代表可调用',
    },
    DISABLED: {
      label: '已停用',
      tag: 'info',
      description: '不会用于新的 Agent 配置',
    },
    UNKNOWN: {
      label: '凭据状态未知',
      tag: 'info',
      description: '模型清单可用，凭据读取失败',
    },
  }
  return definitions[readinessOf(row)]
}

const summary = computed(() => rows.value.reduce((result, row) => {
  const readiness = readinessOf(row)
  if (readiness === 'READY') result.ready += 1
  if (readiness === 'BLOCKED') result.blocked += 1
  if (readiness === 'DISABLED') result.disabled += 1
  if (readiness === 'UNKNOWN') result.unknown += 1
  return result
}, { ready: 0, blocked: 0, disabled: 0, unknown: 0 }))

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesQuery = !query || [row.name, row.modelId, row.provider]
      .some((value) => String(value || '').toLowerCase().includes(query))
    const matchesType = typeFilter.value === 'ALL' || row.type === typeFilter.value
    const matchesProvider = providerFilter.value === 'ALL' || row.provider === providerFilter.value
    const matchesReadiness = readinessFilter.value === 'ALL' || readinessOf(row) === readinessFilter.value
    return matchesQuery && matchesType && matchesProvider && matchesReadiness
  })
})

const pageRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})

watch([keyword, typeFilter, readinessFilter, providerFilter], () => {
  currentPage.value = 1
})

async function loadModels() {
  loading.value = true
  loadState.status = 'loading'
  loadState.error = null
  credentialState.value = 'loading'

  const [modelsResult, credentialsResult] = await Promise.allSettled([
    getModelList(),
    getModelProviderCredentials(),
  ])

  if (modelsResult.status === 'fulfilled') {
    rows.value = toModelListViewModel(modelsResult.value)
    loadState.status = rows.value.length > 0 ? 'success' : 'empty'
  } else {
    loadState.status = 'error'
    loadState.error = modelsResult.reason
  }

  if (credentialsResult.status === 'fulfilled') {
    credentials.value = Array.isArray(credentialsResult.value) ? credentialsResult.value : []
    credentialState.value = 'known'
  } else {
    credentials.value = []
    credentialState.value = 'unknown'
  }

  loading.value = false
  window.dispatchEvent(new Event('page-refresh-done'))
}

async function toggleStatus(row) {
  togglingId.value = row.id
  try {
    const updated = await toggleModelStatus(row.id)
    row.status = updated?.status || (row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
    ElMessage.success(row.status === 'ENABLED' ? '模型已启用' : '模型已停用')
  } catch {
    ElMessage.error('模型状态更新失败')
  } finally {
    togglingId.value = null
  }
}

function addModel() {
  router.push(ROUTES.AGENTS.MODEL_ADD)
}

function editModel(row) {
  if (!row?.id) return
  router.push(ROUTES.AGENTS.MODEL_EDIT.replace(':id', row.id))
}

function openCredentials() {
  router.push(`${ROUTES.SYSTEM.GATEWAY}?workspace=access&credentialTab=provider`)
}

function openAdvanced() {
  router.push(ROUTES.AGENTS.MODEL_ADVANCED)
}

onMounted(loadModels)
</script>

<style scoped>
.model-catalog-page {
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
  padding: 28px 32px 40px;
}

.page-header,
.header-actions,
.filter-bar,
.pagination-row,
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

.header-actions,
.filter-bar {
  gap: 10px;
}

.readiness-alert {
  margin-bottom: 16px;
}

.readiness-alert :deep(.el-alert__content) {
  width: 100%;
}

.readiness-alert :deep(.el-alert__description) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
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

.filter-bar {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.search-input {
  width: min(360px, 100%);
}

.filter-control {
  width: 175px;
}

.model-identity,
.readiness-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.model-identity strong,
.model-identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-identity strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.model-identity small,
.readiness-cell small,
.advanced-entry {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.readiness-cell {
  align-items: flex-start;
}

.pagination-row {
  justify-content: flex-end;
  padding: 18px 4px 4px;
}

.advanced-entry {
  justify-content: flex-end;
  gap: 6px;
  padding-top: 12px;
}

.secondary-button {
  border-color: var(--el-border-color);
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

@media (max-width: 900px) {
  .model-catalog-page {
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
