<template>
  <div class="agent-version-list" v-loading="loading">
    <header class="page-header">
      <div>
        <h2>版本历史</h2>
        <p class="hint">冻结版本不会再被修改。当前使用版本是新运行的默认选择，历史版本仍可用于回溯。</p>
      </div>
      <el-button :icon="Back" @click="goBack">返回 Agent 配置</el-button>
    </header>

    <section class="version-summary" aria-label="版本概览">
      <div>
        <strong>{{ currentVersion ? `v${getAgentVersionNumber(currentVersion)}` : '—' }}</strong>
        <span>当前使用</span>
      </div>
      <div>
        <strong>{{ readyCount }}</strong>
        <span>可运行版本</span>
      </div>
      <div>
        <strong>{{ retiredCount }}</strong>
        <span>已退役版本</span>
      </div>
    </section>

    <OrinAsyncState
      :status="state.status"
      empty-text="还没有冻结版本。返回 Agent 配置，完成核心配置后冻结第一个版本。"
      @retry="loadData"
    >
      <OrinDataTable>
        <ResizableTable
          :data="versions"
          stripe
          border
          class="version-table"
          :row-style="{ cursor: 'pointer' }"
          @row-click="openDetail"
        >
          <el-table-column label="版本" min-width="280">
            <template #default="{ row }">
              <div class="version-identity">
                <strong>v{{ getAgentVersionNumber(row) || '?' }}</strong>
                <span>{{ row.changeDescription || row.change_description || '稳定冻结版本' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="交付状态" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="versionState(row).type" size="small">
                {{ versionState(row).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="冻结时间" width="150" align="center">
            <template #default="{ row }">{{ formatTime(row.frozenAt) }}</template>
          </el-table-column>
          <el-table-column label="创建者" min-width="120">
            <template #default="{ row }">{{ row.createdBy || '—' }}</template>
          </el-table-column>
          <el-table-column label="下一步" width="250" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
              <el-button
                v-if="row.status === 'FROZEN'"
                link
                type="primary"
                @click.stop="startRun(row)"
              >运行</el-button>
              <el-button
                v-if="row.status === 'FROZEN'"
                link
                type="success"
                @click.stop="publishVersion(row)"
              >发布</el-button>
              <el-dropdown
                v-if="canManageVersions && row.status === 'FROZEN' && !row.isActive"
                trigger="click"
                @command="(command) => onManageCommand(command, row)"
                @click.stop
              >
                <el-button link type="info" @click.stop>版本管理</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="make-default">设为默认版本</el-dropdown-item>
                    <el-dropdown-item command="retire" divided>退役版本</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </ResizableTable>
      </OrinDataTable>
    </OrinAsyncState>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { useUserStore } from '@/stores/user'
import {
  deprecateAgentVersion,
  getAgentVersions,
  switchActiveAgentVersion,
} from '@/domains/agent/api'
import {
  formatWorkspaceTime,
  getAgentVersionId,
  getAgentVersionNumber,
  getAgentVersionState,
} from '@/views/workspace/coreLoopPresentation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const state = reactive({ status: 'loading', error: null })
const versions = ref([])
const loading = ref(false)

const currentVersion = computed(() => versions.value.find((version) => version.isActive) || null)
const readyCount = computed(() => versions.value.filter((version) => version.status === 'FROZEN').length)
const retiredCount = computed(() => versions.value.filter((version) => version.status === 'DEPRECATED').length)
const canManageVersions = computed(() =>
  userStore.isAdmin
  || userStore.hasRole('ROLE_OPERATOR')
  || userStore.hasRole('OPERATOR')
)
const versionState = getAgentVersionState

const loadData = async () => {
  loading.value = true
  state.status = 'loading'
  try {
    versions.value = await getAgentVersions(route.params.agentId)
    state.status = versions.value.length === 0 ? 'empty' : 'success'
  } catch (error) {
    state.status = 'error'
    state.error = error
  } finally {
    loading.value = false
  }
}

const versionRouteQuery = (row, action) => ({
  path: action === 'run' ? ROUTES.WORKSPACE.RUNS : ROUTES.WORKSPACE.ENDPOINTS,
  query: {
    [action === 'run' ? 'create' : 'publish']: '1',
    agentId: route.params.agentId,
    versionId: getAgentVersionId(row),
  },
})

const startRun = (row) => router.push(versionRouteQuery(row, 'run'))
const publishVersion = (row) => router.push(versionRouteQuery(row, 'publish'))

const openDetail = (row) => {
  router.push(
    ROUTES.AGENTS.WORKSPACE_VERSION_DETAIL
      .replace(':agentId', route.params.agentId)
      .replace(':versionId', getAgentVersionId(row))
  )
}

const onManageCommand = (command, row) => {
  if (command === 'make-default') onSwitchActive(row)
  if (command === 'retire') onDeprecate(row)
}

const onSwitchActive = async (row) => {
  try {
    await ElMessageBox.confirm(
      `将 v${getAgentVersionNumber(row)} 设为新运行的默认版本？`,
      '设为默认版本',
      {
        type: 'info',
        confirmButtonText: '设为默认',
        cancelButtonText: '取消',
      }
    )
    await switchActiveAgentVersion(route.params.agentId, getAgentVersionId(row))
    ElMessage.success(`v${getAgentVersionNumber(row)} 已设为默认版本`)
    await loadData()
  } catch (_) { /* cancelled or handled by request layer */ }
}

const onDeprecate = async (row) => {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `退役后，v${getAgentVersionNumber(row)} 不能再用于新的运行或发布。`,
      '退役版本',
      {
        inputPlaceholder: '说明退役原因',
        inputValidator: (value) => Boolean(value?.trim()) || '请填写退役原因',
        confirmButtonText: '确认退役',
        cancelButtonText: '取消',
      }
    )
    await deprecateAgentVersion(route.params.agentId, getAgentVersionId(row), reason.trim())
    ElMessage.success(`v${getAgentVersionNumber(row)} 已退役`)
    await loadData()
  } catch (_) { /* cancelled or handled by request layer */ }
}

const formatTime = (value) => formatWorkspaceTime(value)
const goBack = () => router.push(
  ROUTES.AGENTS.WORKSPACE_DRAFT.replace(':agentId', route.params.agentId)
)

onMounted(loadData)
</script>

<style scoped>
.agent-version-list {
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.page-header h2 {
  margin: 0 0 5px;
  font-size: 22px;
}

.hint {
  max-width: 720px;
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.version-summary {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.version-summary > div {
  display: flex;
  min-width: 130px;
  align-items: baseline;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 9px;
  background: var(--el-bg-color);
}

.version-summary strong {
  font-size: 18px;
}

.version-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.version-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.version-identity span {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-table :deep(.el-table__cell) {
  vertical-align: middle;
}

@media (max-width: 720px) {
  .page-header,
  .version-summary {
    flex-direction: column;
  }

  .version-summary > div {
    width: 100%;
  }
}
</style>
