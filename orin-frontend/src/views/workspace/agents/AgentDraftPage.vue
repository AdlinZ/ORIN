<template>
    <div class="agent-draft-page" v-loading="loading">
        <header class="page-header">
            <div class="title-block">
                <h2>{{ form.name || '（未命名 Agent）' }}</h2>
                <div class="state-row">
                    <el-tag v-if="stateLabel === 'FROZEN'" type="success" size="small">
                        FROZEN · v{{ form.activeVersionNumber || '?' }} · digest={{ digestShort }}
                    </el-tag>
                    <el-tag v-else-if="stateLabel === 'DEPRECATED'" type="info" size="small">
                        DEPRECATED
                    </el-tag>
                    <el-tag v-else type="warning" size="small" effect="plain">DRAFT（未冻结）</el-tag>
                    <el-button
                        v-if="stateLabel !== 'DRAFT'"
                        link
                        type="primary"
                        size="small"
                        @click="goVersions"
                    >查看版本</el-button>
                </div>
                <p class="hint">
                    草稿是唯一可变真相。可多次保存并继续编辑以生成 v2 / v3；
                    点击「冻结」会读取当前 pendingSecretRefs 提交一个不可变 AgentVersion。
                </p>
            </div>
            <div class="header-actions">
                <el-button :icon="Back" @click="goBack">返回列表</el-button>
            </div>
        </header>

        <el-alert
            v-if="errorMessage"
            type="error"
            :title="errorMessage.title"
            :description="errorMessage.detail"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
        />

        <el-alert
            v-if="validationWarnings.length"
            type="warning"
            :title="`校验提示（${validationWarnings.length} 项）`"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
        >
            <ul>
                <li v-for="(w, idx) in validationWarnings" :key="idx">{{ w }}</li>
            </ul>
        </el-alert>

        <el-alert
            v-if="lastSaveMessage"
            type="success"
            :title="lastSaveMessage"
            show-icon
            :closable="true"
            @close="lastSaveMessage = ''"
            style="margin-bottom: 16px"
        />

        <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="agent-form"
        >
            <section class="form-section">
                <h3>基础信息</h3>
                <el-form-item label="名称" prop="name">
                    <el-input v-model="form.name" placeholder="例如 prod-sales-1" />
                </el-form-item>
                <el-form-item label="说明" prop="description">
                    <el-input
                        v-model="form.description"
                        type="textarea"
                        :rows="3"
                        placeholder="Agent 用途与边界"
                    />
                </el-form-item>
            </section>

            <section class="form-section">
                <h3>执行配置</h3>
                <el-form-item label="系统 Prompt" prop="systemPrompt">
                    <el-input
                        v-model="form.systemPrompt"
                        type="textarea"
                        :rows="6"
                        placeholder="You are a helpful assistant..."
                    />
                </el-form-item>
                <el-form-item label="Mode" prop="mode">
                    <el-select v-model="form.mode" placeholder="选择模式" clearable>
                        <el-option label="agent" value="agent" />
                        <el-option label="chat" value="chat" />
                        <el-option label="completion" value="completion" />
                        <el-option label="workflow" value="workflow" />
                    </el-select>
                </el-form-item>
                <el-form-item label="模型" prop="modelName">
                    <el-input v-model="form.modelName" placeholder="gpt-4o / claude-3-5-sonnet 等" />
                </el-form-item>
                <el-form-item label="Provider" prop="providerType">
                    <el-input v-model="form.providerType" placeholder="OPENAI / ANTHROPIC / LOCAL 等" />
                </el-form-item>
                <el-row :gutter="16">
                    <el-col :span="8">
                        <el-form-item label="Temperature">
                            <el-input-number
                                v-model="form.temperature"
                                :step="0.05"
                                :min="0"
                                :max="2"
                                :precision="2"
                            />
                        </el-form-item>
                    </el-col>
                    <el-col :span="8">
                        <el-form-item label="Top P">
                            <el-input-number
                                v-model="form.topP"
                                :step="0.05"
                                :min="0"
                                :max="1"
                                :precision="2"
                            />
                        </el-form-item>
                    </el-col>
                    <el-col :span="8">
                        <el-form-item label="Max Tokens">
                            <el-input-number
                                v-model="form.maxTokens"
                                :step="64"
                                :min="1"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>
            </section>

            <section class="form-section">
                <h3>模型凭据（CONTROL_PLANE 引用）</h3>
                <p class="section-hint">
                    点击右侧新增按钮添加 SecretReference；alphanum alias，
                    secret_id 选自已存的 GatewaySecret；freeze 时一起落库（alias 升序），
                    草稿可随时保存而不必立刻冻结。
                </p>
                <SecretReferenceEditor
                    v-model="secretRefs"
                    :available-secrets="availableSecrets"
                />
            </section>

            <section class="form-section sticky-actions">
                <div class="freeze-summary">
                    <h3>冻结摘要</h3>
                    <ul>
                        <li>
                            Schema：<code>snapshotSchemaVersion=1 (RFC 8785 JCS)</code>
                        </li>
                        <li>
                            字段数：{{ editedFieldCount }}（name/desc/system prompt/model 等）
                        </li>
                        <li>
                            Secret refs：{{ secretRefs.length }}（CONTROL_PLANE 来自草稿持久化列）
                        </li>
                        <li>
                            digest 公式：<code>SHA-256(JCS(canonical_envelope))</code>
                        </li>
                    </ul>
                </div>
                <div class="actions">
                    <el-button
                        :icon="Document"
                        @click="onSaveDraft"
                        :loading="saving"
                    >保存草稿</el-button>
                    <el-button
                        type="primary"
                        :icon="Lock"
                        :loading="freezing"
                        @click="onFreeze"
                    >校验并冻结</el-button>
                </div>
            </section>
        </el-form>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Document, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SecretReferenceEditor from '@/domains/agent/components/SecretReferenceEditor.vue'
import { ROUTES } from '@/router/routes'
import {
    freezeAgentVersion,
    getAgentDraft,
    listActiveGatewaySecrets,
    upsertAgentDraft
} from '@/domains/agent/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const freezing = ref(false)
const availableSecrets = ref([])
const secretRefs = ref([])
const errorMessage = ref(null)
const validationWarnings = ref([])
const lastSaveMessage = ref('')

const formRef = ref(null)
const form = reactive({
    name: '',
    description: '',
    systemPrompt: '',
    mode: 'agent',
    modelName: '',
    providerType: '',
    temperature: 0.7,
    topP: 1.0,
    maxTokens: 2048,
    activeVersionId: null,
    activeVersionNumber: null,
    activeVersionStatus: null,
    contentDigest: null,
})

const rules = {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const stateLabel = computed(() => form.activeVersionStatus || 'DRAFT')
const digestShort = computed(() => (form.contentDigest ? form.contentDigest.slice(0, 12) + '…' : '—'))

const editedFieldCount = computed(() => {
    return [
        form.name, form.description, form.systemPrompt, form.mode,
        form.modelName, form.providerType, form.temperature, form.topP, form.maxTokens,
    ].filter((x) => x !== null && x !== undefined && x !== '').length
})

const loadDraft = async () => {
    loading.value = true
    errorMessage.value = null
    try {
        const draft = await getAgentDraft(route.params.agentId)
        Object.assign(form, {
            name: draft.name,
            description: draft.description,
            systemPrompt: draft.system_prompt || draft.systemPrompt,
            mode: draft.mode,
            modelName: draft.modelName,
            providerType: draft.providerType,
            temperature: draft.temperature,
            topP: draft.topP,
            maxTokens: draft.maxTokens,
            activeVersionId: draft.activeVersionId || draft.active_version_id,
            activeVersionNumber: draft.activeVersionNumber || draft.active_version_number,
            activeVersionStatus: draft.activeVersionStatus || draft.active_version_status,
            contentDigest: draft.activeVersionDigest || draft.active_version_digest,
        })
        // 反序列化草稿上的 secret refs
        const refsJson = draft.pendingSecretRefs || draft.pending_secret_refs
        if (refsJson) {
            try {
                const parsed = JSON.parse(refsJson)
                secretRefs.value = parsed.map((r) => ({
                    alias: r.alias,
                    source: r.source || 'CONTROL_PLANE',
                    secretId: r.secret_id || r.secretId,
                    injectAs: r.inject_as || r.injectAs,
                    required: r.required !== false,
                }))
            } catch (e) {
                console.error('parse pendingSecretRefs failed', e)
            }
        }
        await validateForm()
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
        throw e
    } finally {
        loading.value = false
    }
}

const loadAvailableSecrets = async () => {
    try {
        const list = await listActiveGatewaySecrets()
        availableSecrets.value = list || []
    } catch (_) {
        availableSecrets.value = []
    }
}

const validateForm = async () => {
    const warnings = []
    if (!form.name?.trim()) warnings.push('名称不能为空')
    if (!form.modelName?.trim()) warnings.push('Model 名称不能为空（freeze 后不可更改）')
    if (!form.systemPrompt?.trim()) warnings.push('建议为 Agent 填写系统 Prompt')
    if (!secretRefs.value.length) warnings.push('MVP 暂无 secret refs；用户允许无 Secret 合法 Agent 冻结')
    validationWarnings.value = warnings
}

const onSaveDraft = async () => {
    saving.value = true
    errorMessage.value = null
    try {
        await validateForm()
        await upsertAgentDraft(route.params.agentId, {
            name: form.name,
            description: form.description,
            systemPrompt: form.systemPrompt,
            mode: form.mode,
            modelName: form.modelName,
            providerType: form.providerType,
            temperature: form.temperature,
            topP: form.topP,
            maxTokens: form.maxTokens,
            changeDescription: '手动保存草稿',
            pendingSecretRefs: secretRefs.value.map((r) => ({
                alias: r.alias,
                source: r.source,
                secret_id: r.secretId,
                required: r.required,
                inject_as: r.injectAs,
            })),
        })
        await loadDraft()
        lastSaveMessage.value = `草稿已保存（${new Date().toLocaleTimeString()}）`
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
        ElMessage.error(formatErrorShort(e))
    } finally {
        saving.value = false
    }
}

const onFreeze = async () => {
    freezing.value = true
    errorMessage.value = null
    try {
        // 先把当前表单（包括 secret refs）持久化到草稿，保证 freeze 看到的就是用户最新输入
        await onSaveDraft()
        if (errorMessage.value) {
            // 保存失败，不能 freeze
            return
        }
        const resp = await freezeAgentVersion(route.params.agentId)
        ElMessage.success(
            `已冻结 v${resp.versionNumber}（${resp.status}，digest=${(resp.contentDigest || '').slice(0, 12)}…）`
        )
        router.push(ROUTES.AGENTS.WORKSPACE_VERSION_DETAIL
            .replace(':agentId', route.params.agentId)
            .replace(':versionId', resp.agent_version_id))
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
    } finally {
        freezing.value = false
    }
}

const formatErrorTitle = (e) => {
    const data = e?.response?.data || e?.data || {}
    return {
        title: `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`,
        detail: data.detail || JSON.stringify(data).slice(0, 280),
    }
}

const formatErrorShort = (e) => {
    const data = e?.response?.data || e?.data || {}
    return `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`
}

const goBack = () => router.push(ROUTES.AGENTS.WORKSPACE_LIST)
const goVersions = () => router.push(ROUTES.AGENTS.WORKSPACE_VERSIONS.replace(':agentId', route.params.agentId))

onMounted(async () => {
    await Promise.all([loadDraft(), loadAvailableSecrets()])
})
</script>

<style scoped>
.agent-draft-page {
    padding: 24px;
    max-width: 920px;
    margin: 0 auto;
}
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    gap: 16px;
}
.title-block h2 {
    margin: 0 0 6px;
    font-size: 22px;
    color: var(--text-primary, #0f172a);
}
.state-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
    flex-wrap: wrap;
}
.hint {
    margin: 0;
    color: var(--text-secondary, #64748b);
    font-size: 13px;
    line-height: 1.55;
    max-width: 720px;
}
.header-actions {
    flex: 0 0 auto;
}
.form-section {
    margin-bottom: 24px;
    padding: 16px 20px;
    background: #fff;
    border-radius: 10px;
    border: 1px solid var(--orin-border, #e2e8f0);
}
.form-section h3 {
    margin: 0 0 12px;
    font-size: 16px;
    color: var(--text-primary, #0f172a);
}
.section-hint {
    margin: 0 0 12px;
    color: var(--text-secondary, #64748b);
    font-size: 12px;
}
.sticky-actions {
    position: sticky;
    bottom: 0;
    background: #f8fafc;
    border-color: var(--orin-border-strong, #d8e0e8);
    box-shadow: 0 -2px 8px rgba(15, 23, 42, 0.04);
}
.freeze-summary {
    margin-bottom: 12px;
}
.freeze-summary ul {
    margin: 0;
    padding-left: 22px;
    font-size: 13px;
    color: var(--text-secondary, #475569);
}
.freeze-summary code {
    background: #f1f5f9;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 12px;
}
.actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
}
</style>
