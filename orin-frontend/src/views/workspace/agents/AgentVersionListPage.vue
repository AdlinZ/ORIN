<template>
    <div class="agent-version-list" v-loading="loading">
        <header class="page-header">
            <div>
                <h2>Agent 版本列表</h2>
                <p class="hint">
                    所有 AgentVersion 完全只读；切换 active 不会 deprecate 旧版本，需要 Operator 手动 deprecate。
                </p>
            </div>
            <el-button-group>
                <el-button :icon="Back" @click="goBack">返回草稿</el-button>
            </el-button-group>
        </header>

        <OrinAsyncState :status="state.status" empty-text="尚无版本；先在草稿页点击「校验并冻结」" @retry="loadData">
            <OrinDataTable>
            <el-table :data="versions" stripe border class="version-table">
                <el-table-column label="#" width="60" align="center">
                    <template #default="{ row }">v{{ row.versionNumber }}</template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                    <template #default="{ row }">
                        <el-tag v-if="row.status === 'FROZEN'" size="small" type="success">FROZEN</el-tag>
                        <el-tag v-else-if="row.status === 'DEPRECATED'" size="small" type="info">DEPRECATED</el-tag>
                        <el-tag v-else size="small" effect="plain">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="digest" min-width="320" show-overflow-tooltip>
                    <template #default="{ row }">
                        <code class="digest">{{ row.contentDigest }}</code>
                    </template>
                </el-table-column>
                <el-table-column label="创建时间" width="180">
                    <template #default="{ row }">{{ formatTime(row.frozenAt) }}</template>
                </el-table-column>
                <el-table-column label="active" width="120" align="center">
                    <template #default="{ row }">
                        <el-tag v-if="row.isActive" size="small" type="warning">ACTIVE</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="280" align="center">
                    <template #default="{ row }">
                        <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                        <el-button
                            v-if="row.status === 'FROZEN' && !row.isActive"
                            link
                            type="primary"
                            @click="onSwitchActive(row)"
                        >切到 active</el-button>
                        <el-button
                            v-if="row.status === 'FROZEN' && !row.isActive"
                            link
                            type="danger"
                            @click="onDeprecate(row)"
                        >deprecate</el-button>
                    </template>
                </el-table-column>
            </el-table>
            </OrinDataTable>
        </OrinAsyncState>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { ROUTES } from '@/router/routes'
import {
    deprecateAgentVersion,
    getAgentVersions,
    switchActiveAgentVersion
} from '@/domains/agent/api'

const route = useRoute()
const router = useRouter()
const state = reactive({ status: 'loading', error: null })
const versions = ref([])
const loading = ref(false)

const formatTime = (t) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '-')

const loadData = async () => {
    loading.value = true
    state.status = 'loading'
    try {
        versions.value = await getAgentVersions(route.params.agentId)
        state.status = versions.value.length === 0 ? 'empty' : 'success'
    } catch (e) {
        state.status = 'error'
        state.error = e
    } finally {
        loading.value = false
    }
}

const openDetail = (row) => {
    router.push(ROUTES.AGENTS.WORKSPACE_VERSION_DETAIL
        .replace(':agentId', route.params.agentId)
        .replace(':versionId', row.agentVersionId))
}

const onSwitchActive = async (row) => {
    try {
        await switchActiveAgentVersion(route.params.agentId, row.agentVersionId)
        ElMessage.success(`已切到 v${row.versionNumber}`)
        await loadData()
    } catch (e) {
        ElMessage.error(formatError(e))
    }
}

const onDeprecate = async (row) => {
    const { value: reason } = await ElMessageBox.prompt(
        `确认 deprecate v${row.versionNumber}？该版本将转为 DEPRECATED 状态，新 Run 不再被引用。`,
        'Deprecate Version',
        { inputPlaceholder: 'reason（必填）', inputValidator: (v) => !!v?.trim() }
    )
    if (!reason) return
    try {
        await deprecateAgentVersion(route.params.agentId, row.agentVersionId, reason)
        ElMessage.success(`v${row.versionNumber} 已 DEPRECATED`)
        await loadData()
    } catch (e) {
        ElMessage.error(formatError(e))
    }
}

const formatError = (e) => {
    const data = e?.response?.data || e?.data || {}
    return `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`
}

const goBack = () => router.push(ROUTES.AGENTS.WORKSPACE_DRAFT.replace(':agentId', route.params.agentId))

onMounted(loadData)
</script>

<style scoped>
.agent-version-list {
    padding: 24px;
    max-width: 1120px;
    margin: 0 auto;
}
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 16px;
}
.page-header h2 {
    margin: 0 0 6px;
    font-size: 22px;
}
.page-header .hint {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary, #64748b);
    max-width: 720px;
    line-height: 1.55;
}
.digest {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    color: var(--text-primary, #0f172a);
}
.version-table :deep(.el-table__cell) {
    vertical-align: middle;
}
</style>
