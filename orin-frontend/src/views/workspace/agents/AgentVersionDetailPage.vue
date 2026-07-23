<template>
    <div class="agent-version-detail" v-loading="loading">
        <header class="page-header">
            <div>
                <h2>
                    AgentVersion v{{ version?.versionNumber || '?' }}
                    <el-tag v-if="version?.status === 'FROZEN'" type="success" size="small">FROZEN</el-tag>
                    <el-tag v-else-if="version?.status === 'DEPRECATED'" type="info" size="small">DEPRECATED</el-tag>
                    <el-tag v-if="version?.isActive" type="warning" size="small">ACTIVE</el-tag>
                </h2>
                <p class="hint">
                    FROZEN 后内容（含 digest 与 secretRefs）完全不可修改；切 active 与 deprecate 受控。
                </p>
            </div>
            <el-button-group>
                <el-button :icon="Back" @click="goBack">返回版本列表</el-button>
                <el-button :icon="CopyDocument" @click="copyDigest">{{ copyButtonText }}</el-button>
            </el-button-group>
        </header>

        <el-alert
            v-if="version?.status === 'FROZEN'"
            type="info"
            :title="`FROZEN · snapshotSchemaVersion=${version.snapshotSchemaVersion} · 不可修改`"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
        />

        <section class="meta">
            <div class="meta-row">
                <span class="meta-label">content_digest</span>
                <code class="digest-full">{{ version?.contentDigest }}</code>
            </div>
            <div class="meta-row">
                <span class="meta-label">snapshotSchemaVersion</span>
                <span>v{{ version?.snapshotSchemaVersion }}</span>
            </div>
            <div class="meta-row">
                <span class="meta-label">frozen_at</span>
                <span>{{ formatTime(version?.frozenAt) }}</span>
            </div>
            <div class="meta-row">
                <span class="meta-label">frozen_by</span>
                <span>{{ version?.frozenBy || '-' }}</span>
            </div>
            <div v-if="version?.deprecatedAt" class="meta-row">
                <span class="meta-label">deprecated_at</span>
                <span>{{ formatTime(version.deprecatedAt) }} (reason: {{ version.deprecationReason }})</span>
            </div>
        </section>

        <section class="secret-refs">
            <h3>Secret References</h3>
            <OrinDataTable>
                <el-table :data="version?.secretRefs || []" stripe border class="refs-table">
                <el-table-column prop="alias" label="alias" width="160" />
                <el-table-column prop="source" label="source" width="140" />
                <el-table-column label="secret_id / local_key" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">
                        <code>{{ row.secretId || row.localKey || '—' }}</code>
                    </template>
                </el-table-column>
                <el-table-column prop="injectAs" label="inject_as" width="180" />
                <el-table-column label="key_prefix / last4" width="200">
                    <template #default="{ row }">
                        <code>{{ row.keyPrefix || '—' }}…{{ row.last4 || '' }}</code>
                    </template>
                </el-table-column>
                <el-table-column prop="required" label="required" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag v-if="row.required" size="small" type="success">true</el-tag>
                        <el-tag v-else size="small" effect="plain">false</el-tag>
                    </template>
                </el-table-column>
            </el-table>
            </OrinDataTable>
        </section>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { ROUTES } from '@/router/routes'
import { getAgentVersionDetail } from '@/domains/agent/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const version = ref(null)
const copyButtonText = ref('复制 digest')

const formatTime = (t) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '-')

const loadData = async () => {
    loading.value = true
    try {
        version.value = await getAgentVersionDetail(route.params.agentId, route.params.versionId)
    } catch (e) {
        ElMessage.error(formatError(e))
    } finally {
        loading.value = false
    }
}

const copyDigest = async () => {
    if (!version.value?.contentDigest) return
    try {
        if (navigator.clipboard) {
            await navigator.clipboard.writeText(version.value.contentDigest)
        } else {
            // fallback
            const ta = document.createElement('textarea')
            ta.value = version.value.contentDigest
            document.body.appendChild(ta)
            ta.select()
            document.execCommand('copy')
            document.body.removeChild(ta)
        }
        copyButtonText.value = '已复制 ✓'
        setTimeout(() => (copyButtonText.value = '复制 digest'), 2000)
    } catch (_) {
        // 静默；用户可手动复制
    }
}

const formatError = (e) => {
    const data = e?.response?.data || e?.data || {}
    return `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`
}

const goBack = () => router.push(ROUTES.AGENTS.WORKSPACE_VERSIONS.replace(':agentId', route.params.agentId))

onMounted(loadData)
</script>

<style scoped>
.agent-version-detail {
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
    display: flex;
    gap: 8px;
    align-items: center;
}
.page-header .hint {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary, #64748b);
}
.meta {
    padding: 16px 20px;
    background: #fff;
    border-radius: 10px;
    border: 1px solid var(--orin-border, #e2e8f0);
    margin-bottom: 16px;
}
.meta-row {
    display: flex;
    gap: 16px;
    padding: 8px 0;
    border-bottom: 1px dashed var(--orin-border, #e2e8f0);
    font-size: 13px;
    color: var(--text-primary, #0f172a);
}
.meta-row:last-child {
    border-bottom: none;
}
.meta-label {
    flex: 0 0 200px;
    color: var(--text-secondary, #64748b);
    font-weight: 600;
}
.digest-full {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    word-break: break-all;
}
.secret-refs h3 {
    margin: 0 0 12px;
    font-size: 16px;
}
.refs-table :deep(.el-table__cell) {
    vertical-align: middle;
}
.refs-table code {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
}
</style>
