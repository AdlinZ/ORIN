<template>
    <div class="workspace-agents">
        <header class="page-header">
            <div>
                <h2>Agent</h2>
                <p class="hint">
                    定义能力并冻结版本。可运行的 Agent 可以直接进入运行或发布。
                </p>
            </div>
            <div class="header-actions">
                <el-button type="primary" :icon="Plus" @click="onCreate">新建 Agent</el-button>
                <el-button :icon="Refresh" @click="loadData">刷新</el-button>
            </div>
        </header>

        <section class="summary-strip" aria-label="Agent 交付状态">
            <button type="button" :class="{ active: statusFilter === 'DRAFT' }" @click="toggleStatusFilter('DRAFT')">
                <strong>{{ deliveryCounts.draft }}</strong><span>待冻结</span>
            </button>
            <button type="button" :class="{ active: statusFilter === 'FROZEN' }" @click="toggleStatusFilter('FROZEN')">
                <strong>{{ deliveryCounts.ready }}</strong><span>可运行</span>
            </button>
        </section>

        <section class="action-row">
            <el-input
                v-model="search"
                placeholder="搜索 Agent"
                clearable
                style="width: 280px"
            />
        </section>

        <OrinAsyncState
            :status="state.status"
            empty-text="暂无 Agent。点击「新建 Agent」开始。"
            @retry="loadData"
        >
            <OrinDataTable
                title="Agent 能力"
                :description="`${filteredAgents.length} / ${rows.length} 个 Agent`"
            >
                <ResizableTable
                    :data="filteredAgents"
                    stripe
                    border
                    table-class="agent-table"
                    :row-style="{ cursor: 'pointer' }"
                    @row-click="openAgent"
                >
                    <el-table-column label="Agent" min-width="320">
                        <template #default="{ row }">
                            <div class="agent-identity">
                                <strong>{{ row.name }}</strong>
                                <span>{{ row.description || '暂无说明' }}</span>
                            </div>
                        </template>
                    </el-table-column>
                    <el-table-column label="模型" min-width="190">
                        <template #default="{ row }">
                            <div class="execution-config">
                                <span>{{ row.modelName || '尚未选择模型' }}</span>
                            </div>
                        </template>
                    </el-table-column>
                    <el-table-column label="交付状态" width="150" align="center">
                        <template #default="{ row }">
                            <div class="version-state">
                                <el-tag :type="deliveryState(row).type" size="small">
                                    {{ deliveryState(row).label }}
                                </el-tag>
                            </div>
                        </template>
                    </el-table-column>
                    <el-table-column label="下一步" width="220" align="center" fixed="right">
                        <template #default="{ row }">
                            <el-button
                                v-if="deliveryState(row).key === 'DRAFT'"
                                link
                                type="primary"
                                @click.stop="openDraft(row)"
                            >
                                继续配置
                            </el-button>
                            <template v-else-if="deliveryState(row).key === 'READY'">
                                <el-button link type="primary" @click.stop="runAgent(row)">运行</el-button>
                                <el-button link type="success" @click.stop="publishAgent(row)">发布</el-button>
                            </template>
                        </template>
                    </el-table-column>
                </ResizableTable>
            </OrinDataTable>
        </OrinAsyncState>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import ResizableTable from '@/components/ResizableTable.vue'
import { ROUTES } from '@/router/routes'
import { getAgentDeliveryState } from '@/views/workspace/coreLoopPresentation'
import {
    createAgent,
    listAgents
} from '@/domains/agent/api'

const router = useRouter()
const state = reactive({ status: 'loading', error: null })
const rows = ref([])
const search = ref('')
const statusFilter = ref('')
const deliveryState = getAgentDeliveryState

const deliveryCounts = computed(() => ({
    draft: rows.value.filter((row) => deliveryState(row).key === 'DRAFT').length,
    ready: rows.value.filter((row) => deliveryState(row).key === 'READY').length,
}))

const filteredAgents = computed(() => {
    const q = search.value.trim().toLowerCase()
    return rows.value.filter((r) => {
        if (statusFilter.value) {
            const expected = statusFilter.value === 'DRAFT'
                ? null  // DRAFT 表示无 active version
                : statusFilter.value
            if ((r.activeVersionStatus || null) !== expected) return false
        }
        if (!q) return true
        return (r.name || '').toLowerCase().includes(q)
            || (r.modelName || '').toLowerCase().includes(q)
            || (r.description || '').toLowerCase().includes(q)
    })
})

const loadData = async () => {
    state.status = 'loading'
    try {
        // F02 R3：列表来自 Control Plane 真实数据；不再依赖 localStorage。
        const list = await listAgents()
        const out = (list || []).map(m => ({
            agentId: m.agentId || m.id,
            name: m.name,
            description: m.description,
            modelName: m.modelName,
            activeVersionStatus: m.activeVersionStatus,
        }))
        rows.value = out
        state.status = out.length === 0 ? 'empty' : 'success'
    } catch (e) {
        state.status = 'error'
        state.error = e
    }
}

const onCreate = async () => {
    const { value: name } = await ElMessageBox.prompt(
        '为新 Agent 起个名称（创建后会跳转到草稿页）',
        '新建 Agent',
        { confirmButtonText: '创建', cancelButtonText: '取消', inputPlaceholder: '例如 prod-sales-1' }
    )
    if (!name) return
    try {
        const created = await createAgent({ name, description: '' })
        ElMessage.success(`已创建 Agent：${created.name}`)
        await loadData()
        const id = created.agentId || created.id
        router.push(ROUTES.AGENTS.WORKSPACE_DRAFT.replace(':agentId', id))
    } catch (e) {
        ElMessage.error(`创建失败: ${e?.message || '未知错误'}`)
    }
}

const openDraft = (row) => {
    router.push(ROUTES.AGENTS.WORKSPACE_DRAFT.replace(':agentId', row.agentId))
}

const openVersions = (row) => {
    router.push(ROUTES.AGENTS.WORKSPACE_VERSIONS.replace(':agentId', row.agentId))
}

const openAgent = (row) => {
    if (deliveryState(row).key === 'DRAFT') openDraft(row)
    else openVersions(row)
}

const toggleStatusFilter = (nextFilter) => {
    statusFilter.value = statusFilter.value === nextFilter ? '' : nextFilter
}

const runAgent = (row) => {
    router.push({ path: ROUTES.WORKSPACE.RUNS, query: { agentId: row.agentId, create: '1' } })
}

const publishAgent = (row) => {
    router.push({ path: ROUTES.WORKSPACE.ENDPOINTS, query: { agentId: row.agentId, publish: '1' } })
}

onMounted(loadData)
</script>

<style scoped>
.workspace-agents {
    padding: 24px;
    max-width: 1400px;
    margin: 0 auto;
}
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 16px;
}
.page-header h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: var(--text-primary, #0f172a);
}
.page-header .hint {
    margin: 0;
    color: var(--text-secondary, #64748b);
    font-size: 13px;
    max-width: 720px;
    line-height: 1.55;
}
.header-actions {
    display: flex;
    gap: 8px;
}
.action-row {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 16px;
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
.agent-identity,
.execution-config,
.version-state {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 3px;
}
.version-state {
    align-items: center;
    gap: 5px;
}
.agent-identity span {
    overflow: hidden;
    color: var(--el-text-color-secondary, #64748b);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.agent-table :deep(.el-table__cell) {
    vertical-align: middle;
}
</style>
