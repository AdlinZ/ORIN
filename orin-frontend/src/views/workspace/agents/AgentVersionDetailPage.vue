<template>
  <div class="agent-version-detail" v-loading="loading">
    <header class="page-header">
      <div>
        <el-button :icon="Back" link @click="goBack">返回版本列表</el-button>
        <div class="title-row">
          <h2>版本 v{{ detail.versionNumber || '?' }}</h2>
          <el-tag :type="versionState.type">{{ versionState.label }}</el-tag>
          <el-tag v-if="detail.isActive" type="primary" effect="plain">当前使用</el-tag>
        </div>
        <p class="hint">这是一个不可变的运行版本，可用于真实运行和服务发布。</p>
      </div>
      <div class="header-actions">
        <el-button :disabled="!canDeliver" @click="startRun">开始运行</el-button>
        <el-button type="primary" :disabled="!canDeliver" @click="publishVersion">发布服务</el-button>
      </div>
    </header>

    <el-alert
      v-if="detail.status === 'DEPRECATED'"
      type="warning"
      :title="`该版本已退役${detail.deprecationReason ? `：${detail.deprecationReason}` : ''}`"
      :closable="false"
      show-icon
      class="status-alert"
    />

    <section class="capability-card">
      <div class="capability-copy">
        <span class="eyebrow">版本说明</span>
        <h3>{{ detail.changeDescription || '稳定冻结版本' }}</h3>
        <p>{{ capabilitySummary }}</p>
      </div>
      <div class="capability-facts">
        <div>
          <strong>{{ detail.secretRefs.length }}</strong>
          <span>凭据绑定</span>
        </div>
        <div>
          <strong>{{ formatTime(detail.frozenAt || detail.createdAt) }}</strong>
          <span>冻结时间</span>
        </div>
        <div>
          <strong>{{ detail.frozenBy || detail.createdBy || '—' }}</strong>
          <span>创建者</span>
        </div>
      </div>
    </section>

    <section class="credentials-section">
      <div class="section-heading">
        <div>
          <h3>运行凭据</h3>
          <p>仅显示绑定关系，不展示任何密钥明文。</p>
        </div>
        <el-tag effect="plain">{{ detail.secretRefs.length }} 项</el-tag>
      </div>

      <OrinDataTable v-if="detail.secretRefs.length">
        <ResizableTable :data="detail.secretRefs" stripe border>
          <el-table-column prop="alias" label="用途标识" min-width="180" />
          <el-table-column prop="source" label="来源" min-width="150" />
          <el-table-column prop="injectAs" label="注入方式" min-width="180">
            <template #default="{ row }">{{ row.injectAs || '默认' }}</template>
          </el-table-column>
          <el-table-column label="运行要求" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.required" size="small" type="warning" effect="plain">必需</el-tag>
              <span v-else>可选</span>
            </template>
          </el-table-column>
        </ResizableTable>
      </OrinDataTable>
      <div v-else class="empty-credentials">
        该版本不依赖额外凭据，可以直接交给在线 Runner 执行。
      </div>
    </section>

    <el-collapse v-model="technicalPanels" class="technical-section">
      <el-collapse-item name="technical">
        <template #title>
          <div class="technical-title">
            <strong>技术信息</strong>
            <span>版本标识、内容摘要和快照格式</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="版本 ID"><code>{{ detail.id || '—' }}</code></el-descriptions-item>
          <el-descriptions-item label="Agent ID"><code>{{ detail.agentId || '—' }}</code></el-descriptions-item>
          <el-descriptions-item label="快照格式">
            {{ detail.snapshotSchemaVersion ? `v${detail.snapshotSchemaVersion}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="内容摘要">
            <div class="digest-row">
              <code>{{ detail.contentDigest || '—' }}</code>
              <el-button v-if="detail.contentDigest" link type="primary" @click="copyDigest">
                {{ copyButtonText }}
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.deprecatedAt" label="退役时间">
            {{ formatTime(detail.deprecatedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { getAgentVersionDetail } from '@/domains/agent/api'
import {
  formatWorkspaceTime,
  normalizeAgentVersionDetail,
} from '@/views/workspace/coreLoopPresentation'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const rawVersion = ref({})
const copyButtonText = ref('复制')
const technicalPanels = ref([])

const detail = computed(() => normalizeAgentVersionDetail(rawVersion.value))
const canDeliver = computed(() => detail.value.status === 'FROZEN')
const versionState = computed(() => {
  if (detail.value.status === 'FROZEN') return { label: '可运行', type: 'success' }
  if (detail.value.status === 'DEPRECATED') return { label: '已退役', type: 'info' }
  return { label: detail.value.status || '未知', type: 'warning' }
})
const capabilitySummary = computed(() => {
  if (detail.value.status === 'DEPRECATED') return '该版本仅供历史追溯，不能再用于新的运行或发布。'
  if (detail.value.isActive) return '这是 Agent 当前默认版本，新运行会优先使用它。'
  return '该版本已冻结，可以手动选择用于运行或发布。'
})

const loadData = async () => {
  loading.value = true
  try {
    rawVersion.value = await getAgentVersionDetail(route.params.agentId, route.params.versionId)
  } catch (error) {
    ElMessage.error(formatError(error))
  } finally {
    loading.value = false
  }
}

const copyDigest = async () => {
  if (!detail.value.contentDigest) return
  try {
    await navigator.clipboard.writeText(detail.value.contentDigest)
    copyButtonText.value = '已复制'
    setTimeout(() => (copyButtonText.value = '复制'), 2000)
  } catch (_) {
    ElMessage.error('复制失败，请手动复制')
  }
}

const startRun = () => router.push({
  path: ROUTES.WORKSPACE.RUNS,
  query: {
    create: '1',
    agentId: detail.value.agentId || route.params.agentId,
    versionId: detail.value.id || route.params.versionId,
  },
})

const publishVersion = () => router.push({
  path: ROUTES.WORKSPACE.ENDPOINTS,
  query: {
    publish: '1',
    agentId: detail.value.agentId || route.params.agentId,
    versionId: detail.value.id || route.params.versionId,
  },
})

const formatTime = (value) => formatWorkspaceTime(value)

const formatError = (error) => {
  const data = error?.response?.data || error?.data || {}
  return data.message || error?.message || '版本读取失败'
}

const goBack = () => router.push(
  ROUTES.AGENTS.WORKSPACE_VERSIONS.replace(':agentId', route.params.agentId)
)

onMounted(loadData)
</script>

<style scoped>
.agent-version-detail {
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.title-row h2 {
  margin: 0;
  font-size: 22px;
}

.hint {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  padding-top: 26px;
}

.status-alert {
  margin-bottom: 16px;
}

.capability-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 28px;
  margin-bottom: 20px;
  padding: 22px 24px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.eyebrow {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.capability-copy h3 {
  margin: 5px 0 6px;
  font-size: 18px;
}

.capability-copy p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.capability-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(110px, auto));
  gap: 12px;
}

.capability-facts > div {
  display: flex;
  min-width: 110px;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.capability-facts strong {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capability-facts span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.credentials-section {
  margin-bottom: 20px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 3px;
  font-size: 16px;
}

.section-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.empty-credentials {
  padding: 22px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}

.technical-section {
  padding: 0 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}

.technical-section :deep(.el-collapse-item__header),
.technical-section :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.technical-title {
  display: flex;
  width: calc(100% - 24px);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.technical-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.digest-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.digest-row code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}

@media (max-width: 860px) {
  .page-header,
  .capability-card {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .header-actions {
    padding-top: 0;
  }

  .capability-facts {
    grid-template-columns: 1fr;
  }
}
</style>
