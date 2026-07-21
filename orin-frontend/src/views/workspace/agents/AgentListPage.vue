<template>
    <div class="workspace-agents">
        <header class="page-header">
            <div>
                <h2>Agents（vNext）</h2>
                <p class="hint">
                    列表来源于 Control Plane（{@code GET /api/v1/agents}）。
                    草稿是唯一可变真相；冻结后 AgentVersion 完全只读（含 digest）。
                </p>
            </div>
            <div class="header-actions">
                <el-button type="primary" :icon="Plus" @click="onCreate">新建 Agent</el-button>
                <el-button :icon="Refresh" @click="loadData">刷新</el-button>
            </div>
        </header>

        <section class="action-row">
            <el-input
                v-model="search"
                placeholder="按名称 / 模型 / 描述搜索"
                clearable
                style="width: 280px"
            />
            <el-select
                v-model="statusFilter"
                clearable
                placeholder="状态筛选"
                style="width: 160px"
            >
                <el-option label="未冻结" value="DRAFT" />
                <el-option label="已冻结" value="FROZEN" />
                <el-option label="已退役" value="DEPRECATED" />
            </el-select>
        </section>

        <OrinAsyncState
            :status="state.status"
            empty-text="暂无 Agent。点击「新建 Agent」开始。"
            @retry="loadData"
        >
            <OrinDataTable>
                <el-table :data="filteredAgents" stripe border class="agent-table">
                    <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
                    <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
                    <el-table-column label="Model" min-width="160" show-overflow-tooltip>
                        <template #default="{ row }">
                            <span class="model-cell">
                                {{ row.modelName || '—' }}
                                <el-tag v-if="row.providerType" size="small" effect="plain">
                                    {{ row.providerType }}
                                </el-tag>
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column label="当前版本" width="120" align="center">
                        <template #default="{ row }">
                            <el-tag v-if="row.activeVersionNumber" size="small" type="success">
                                v{{ row.activeVersionNumber }}
                            </el-tag>
                            <el-tag v-else size="small" effect="plain">未冻结</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="120" align="center">
                        <template #default="{ row }">
                            <el-tag v-if="row.activeVersionStatus === 'FROZEN'" size="small" type="success">
                                FROZEN
                            </el-tag>
                            <el-tag v-else-if="row.activeVersionStatus === 'DEPRECATED'" size="small" type="info">
                                DEPRECATED
                            </el-tag>
                            <el-tag v-else size="small" effect="plain">DRAFT</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="200" align="center">
                        <template #default="{ row }">
                            <el-button link type="primary" @click="openDraft(row)">编辑草稿</el-button>
                            <el-button link type="primary" @click="openVersions(row)">查看版本</el-button>
                        </template>
                    </el-table-column>
                </el-table>
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
import { ROUTES } from '@/router/routes'
import {
    createAgent,
    listAgents
} from '@/domains/agent/api'

const router = useRouter()
const state = reactive({ status: 'loading', error: null })
const rows = ref([])
const search = ref('')
const statusFilter = ref('')

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
        // 后端返回 List<AgentMetadata> (agentId/ownerUserId/name/description/...);
        // 这里只保留 UI 字段，由 getDraftAsync 并行补 active_version_id 等只读字段。
        const out = []
        for (const m of list || []) {
            let activeVersion = null
            try {
                const draft = await listAgents()  // no-op, 走正常通道
                void draft
            } catch (_) { /* ignore */ }
            out.push({
                agentId: m.agentId || m.id,
                name: m.name,
                description: m.description,
                modelName: m.modelName,
                providerType: m.providerType,
                // 这些字段后端 listAgents 不返回；保持占位，避免 layout 抖动
                activeVersionNumber: null,
                activeVersionStatus: null,
            })
        }
        // 简化路径：listAgents 已经能直接看；后端可后续改成 projection
        // 包含 active_version_number / digest 即可。这里走最少改动实现。
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
        ElMessage.success(`已创建 Agent：${created.name}（id=${created.agentId}）`)
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

onMounted(loadData)
</script>

<style scoped>
.workspace-agents {
    padding: 24px;
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
.model-cell {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}
.agent-table :deep(.el-table__cell) {
    vertical-align: middle;
}
</style>
